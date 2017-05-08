/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package walker;

import POGOProtos.Data.PokemonDataOuterClass;
import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import POGOProtos.Inventory.Item.ItemIdOuterClass;
import POGOProtos.Networking.Responses.CatchPokemonResponseOuterClass;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.*;
import com.pokegoapi.api.listener.PlayerListener;
import com.pokegoapi.api.map.MapObjects;
import com.pokegoapi.api.map.Point;
import com.pokegoapi.api.map.fort.Pokestop;
import com.pokegoapi.api.map.fort.PokestopLootResult;
import com.pokegoapi.api.map.pokemon.*;
import com.pokegoapi.api.player.*;
import com.pokegoapi.api.pokemon.Pokemon;
import com.pokegoapi.api.settings.PokeballSelector;
import com.pokegoapi.auth.PtcCredentialProvider;
import com.pokegoapi.exceptions.NoSuchItemException;
import com.pokegoapi.exceptions.request.RequestFailedException;
import com.pokegoapi.util.MapUtil;
import com.pokegoapi.util.PokeDictionary;
import com.pokegoapi.util.hash.HashProvider;
import com.pokegoapi.util.hash.pokehash.PokeHashKey;
import com.pokegoapi.util.hash.pokehash.PokeHashProvider;
import com.pokegoapi.util.path.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import okhttp3.OkHttpClient;
import walker.classes.*;
import walker.utils.*;

/**
 *
 * @author mshiner
 */
public class Main {

    private static final OkHttpClient HTTPCLIENT = new OkHttpClient();
    private static final Random RANDOM = new Random();
    private static final DAO DATABASE = new DAO();

    private static PokemonGo api;
    private static Double latitude = 0.0;
    private static Double longitude = 0.0;
    private static int level = 0;
    private static final DecimalFormat f = new DecimalFormat("##.00");

    public static void main(String[] args) throws InterruptedException {
        latitude = Config.getLATITUDE();
        longitude = Config.getLONGITUDE();
        startLooper();
    }

    private static void startLooper() {
        Logger.INSTANCE.Log(Logger.TYPE.INFO, "START: Logging in...");
        boolean looper = true;

        while (looper) {
            api = null; // Reconnection reset
            try {
                api = login(Config.getHASH_KEY(), Config.getLOGIN(), Config.getPASSWORD(), longitude, latitude);
                Inventory inv = new Inventory(api);
                inv.printStock();
                inv.clearItems();
                MyPokedex myPokedex = new MyPokedex(api, DATABASE);
                MyPokemon myPokemon = new MyPokemon(api, DATABASE);
                printStats(inv, myPokemon, api.getPlayerProfile());
                myPokemon.printMyPokemon();
                catchArea(myPokedex, myPokemon, api);
                while (true) {
                    List<Pokestop> pokestopList = getNearbyPokestops(api, myPokedex);
                    walkToPokestops(pokestopList, myPokedex, myPokemon, inv, api);
                    Logger.INSTANCE.Log(Logger.TYPE.INFO, "Loop complete.. Waiting some.");
                    requestChill("long");
                }
                // Clear itms?
//                looper = false;
            } catch (NoSuchItemException | RequestFailedException | InterruptedException ex) {
                Logger.INSTANCE.Log(Logger.TYPE.ERROR, "Main exception thrown! " + ex.toString());
                ex.printStackTrace();
                try {
                    requestChill("long");
                } catch (InterruptedException ex2) {
                    Logger.INSTANCE.Log(Logger.TYPE.ERROR, "Thread can't wait.. " + ex2.toString());
                }
            }
        }
    }

    /**
     * Finds all nearby Pokestops and organises them in priority order if the
     * Pokemon is need for Pokedex or candy
     */
    private static List<Pokestop> getNearbyPokestops(PokemonGo api, MyPokedex pokedex) {
        MapObjects mapObjects = api.getMap().getMapObjects();
        List<Pokestop> priorityStops = new ArrayList<>();
        List<Pokestop> otherStops = new ArrayList<>();

        //Find all pokestops with pokemon nearby
        Set<NearbyPokemon> nearby = mapObjects.getNearby();
        for (NearbyPokemon nearbyPokemon : nearby) {
            String fortId = nearbyPokemon.getFortId();
            //Check if nearby pokemon is near a pokestop
            if (fortId != null && fortId.length() > 0) {
                //Find the pokestop with the fort id of the nearby pokemon
                Pokestop pokestop = mapObjects.getPokestop(fortId);
                if (pokestop != null && !priorityStops.contains(pokestop) && pokedex.doINeed(nearbyPokemon.getPokemonId())) {
                    Logger.INSTANCE.Log(Logger.TYPE.INFO, "There's a " + nearbyPokemon.getPokemonId() + " nearby and I need it for my pokedex!");
                    priorityStops.add(pokestop);
                } else if (pokestop != null && !otherStops.contains(pokestop)) {
                    Logger.INSTANCE.Log(Logger.TYPE.INFO, "There's a " + nearbyPokemon.getPokemonId() + " nearby. I might go get it.");
                    otherStops.add(pokestop);
                }
            }
        }
        Comparator<Pokestop> comparator = (Pokestop primary, Pokestop secondary) -> {
            double lat = api.getLatitude();
            double lng = api.getLongitude();
            double distance1 = MapUtil.distFrom(primary.getLatitude(), primary.getLongitude(), lat, lng);
            double distance2 = MapUtil.distFrom(secondary.getLatitude(), secondary.getLongitude(), lat, lng);
            return Double.compare(distance1, distance2);
        };
        Collections.sort(priorityStops, comparator);
        Collections.sort(otherStops, comparator);
        priorityStops.addAll(otherStops);
        return priorityStops;
    }

    // First call
    // Fetches 9 Nearby pokestops and walks between them. Bot searches for pokemon at each stop.
    private static void walkToPokestops(List<Pokestop> travelPokestops, MyPokedex pokedex, MyPokemon myPkmn, Inventory inv, PokemonGo api) {
        try {
            for (Pokestop pokestop : travelPokestops) {
                Point destination = new Point(pokestop.getLatitude(), pokestop.getLongitude());
                //Use the current player position as the source and the pokestop position as the destination
                //Travel to Pokestop at 20KMPH
                Path path = new Path(api.getPoint(), destination, 20.0);
                System.out.println("Traveling to " + destination + " at 20KMPH! It'll take me.. " + path.getTotalTime());
                path.start(api);
                int counter = 0;
                try {
                    while (!path.isComplete()) {
                        //Calculate the desired intermediate point for the current time
                        Point point = path.calculateIntermediate(api);
                        //Set the API location to that point
                        api.setLatitude(point.getLatitude());
                        // Update current bot location for re-logon
                        latitude = point.getLatitude();
                        longitude = point.getLongitude();
                        api.setLongitude(point.getLongitude());
                        //Sleep for 2 seconds before setting the location again
                        requestChill("short");
                        if (counter % 4 == 0 && inv.getBalls_total() > 10) {
                            catchArea(pokedex, myPkmn, api);
                        }
                        counter++;
                    }
                } catch (InterruptedException e) {
                    Logger.INSTANCE.Log(Logger.TYPE.ERROR, e.toString());
                }
                System.out.println("Finished traveling to pokestop.");
                if (pokestop.inRange() && pokestop.canLoot()) {
                    PokestopLootResult result = pokestop.loot();
                    System.out.println("Looting pokestop: " + result.getResult());
                }
                inv.update(api);
                requestChill("short");
                if (inv.getBalls_total() > 20) {
                    catchArea(pokedex, myPkmn, api);
                }
            }
        } catch (Exception ex) {
            Logger.INSTANCE.Log(Logger.TYPE.ERROR, "WalkingToNearbyPokestops: " + ex.toString());
            ex.printStackTrace();
        }
    }

    private static void catchArea(MyPokedex pokedex, MyPokemon myPkmn, PokemonGo api) throws RequestFailedException, InterruptedException, NoSuchItemException {
        Set<CatchablePokemon> catchable = api.getMap().getMapObjects().getPokemon();
        Logger.INSTANCE.Log(Logger.TYPE.INFO, "There's " + catchable.size() + " pokemon around me.");

        for (CatchablePokemon cp : catchable) {
            // Do I need in my Pokedex?
            boolean newPokedexEntry = pokedex.doINeed(cp.getPokemonId());
            requestChill("short");
            Encounter encounter = cp.encounter();
            if (encounter.isSuccessful()) {

                Logger.INSTANCE.Log(Logger.TYPE.INFO, "Encounted a " + cp.getPokemonId());
                if (!newPokedexEntry) {
                    processPokemon(encounter, myPkmn, api);
                } else {
                    Logger.INSTANCE.Log(Logger.TYPE.INFO, "There's a " + cp.getPokemonId() + " and I need it for my Pokedex!");
                    catchPokemon(encounter, api);
                    requestChill("long");
                }
            } else {
                Logger.INSTANCE.Log(Logger.TYPE.ERROR, "Encounter Unsuccessful! (" + cp.getPokemonId() + ")" + encounter.getEncounterResult().toString());

            }
            requestChill("long");
        }
    }

    /**
     * Sorts though encounter and determines if I need candies and whether the
     * Pokemon is better IVs than my current one.
     *
     * @param encounter
     * @param myPokemon
     */
    private static void processPokemon(Encounter encounter, MyPokemon myPokemon, PokemonGo api) throws RequestFailedException, InterruptedException, NoSuchItemException {
        boolean haveIGotEnoughCandies = false;
        boolean isItBetterIvs = false;
        int currentCandyCount;
        int candiesNeeded;
        // Do I Have Enough Candies
        // Determine whether I have enough candies for final form evolution.
        PokemonId pokemonID = encounter.getEncounteredPokemon().getPokemonId();

        candiesNeeded = myPokemon.getCandiesToEvolve(pokemonID);
        currentCandyCount = myPokemon.getCandiesFromFamily(pokemonID);
        if (candiesNeeded == 0) {
            Logger.INSTANCE.Log(Logger.TYPE.INFO, "This Pokemon can't be evoled any further");
            haveIGotEnoughCandies = true;
        } else {

            if (currentCandyCount >= candiesNeeded) {
                haveIGotEnoughCandies = true;
            }

            Logger.INSTANCE.Log(Logger.TYPE.INFO, "Do I need candy? " + !haveIGotEnoughCandies + " " + currentCandyCount + "/" + candiesNeeded);
        }
        // Is It Better IVs?
        // Determine whether it's a better evolution by first checking if it's a top-evo pokemon.
        // If true, check IVs for top-evo (getTopEvolutions)
        // If false, check IVs for lower-evo (getBottomEvolutions)
        double encounterIV = getPercentageIV(encounter.getEncounteredPokemon());

        // Is it top evo?
        Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "IS IT TOP EVO?");
        if (myPokemon.checkIfHighestEvo(pokemonID)) {
            Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "Yes. Checking top evos..");
            List<Pokemon> topEvos = myPokemon.getTopEvolutions(pokemonID);
            if (topEvos.isEmpty()) {
                Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "I don't have any top evos. ");
                isItBetterIvs = true;
            } else {
                for (Pokemon pokemon : topEvos) {
                    Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "Is " + pokemon.getPokemonId() + "(" + pokemon.getIvInPercentage() + "%) better than " + encounterIV + "% ?");
                    if (pokemon.getIvInPercentage() < encounterIV) {
                        Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "Yes it is.. TRUE!");
                        isItBetterIvs = true;
                        break;
                    }
                }
            }

        } else {
            Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "Not top evo. Checking lowers..");
            // Get Lower Evos

        }
        Pokemon myFamilyBest = myPokemon.orderByIVsDesc(myPokemon.getFullFamily(pokemonID)).get(0);
        if (encounterIV > myFamilyBest.getIvInPercentage()) {
            isItBetterIvs = true;
        }
        Logger.INSTANCE.Log(Logger.TYPE.INFO, "Is it better than the one I've got? " + isItBetterIvs + " Mines: " + myFamilyBest.getPokemonId() + "(" + myFamilyBest.getIvInPercentage() + "%) Vs. " + f.format(encounterIV) + "%");

        // Decision logic //
        if (haveIGotEnoughCandies && isItBetterIvs) {
            Logger.INSTANCE.Log(Logger.TYPE.INFO, "I can evolve this to max & it's the best IV one i've seen! .. I should catch it and evolve it!");
            Pokemon caughtPokemon = catchPokemon(encounter, api); // Transfer old copy of pokemon

            if (caughtPokemon != null) {
                myPokemon.update(api);
                myPokemon.evolveMyBest(caughtPokemon.getPokemonId());
            }
        } else if (!haveIGotEnoughCandies && isItBetterIvs) {
            Logger.INSTANCE.Log(Logger.TYPE.INFO, "I need the candy and it's a better one than I have.. I will catch it and transfer the old one.");
            Pokemon caughtPokemon = catchPokemon(encounter, api); // Transfer old copy of pokemon
            if (caughtPokemon != null) {
                myPokemon.update(api);
                myPokemon.transferPokemon(myFamilyBest);
            }
        } else if (!haveIGotEnoughCandies && !isItBetterIvs) {
            Logger.INSTANCE.Log(Logger.TYPE.INFO, "I need the candy and it's not as good. I'l catch it and transfer it.");
            // Transfer this one
            Pokemon caughtPokemon = catchPokemon(encounter, api); // Transfer this pokemon
            if (caughtPokemon != null) {
                myPokemon.update(api);
                myPokemon.transferPokemon(caughtPokemon);
            }
        } else if (haveIGotEnoughCandies && !isItBetterIvs) {
            Logger.INSTANCE.Log(Logger.TYPE.INFO, "Don't need the candy and it's worse IV one. I'l leave it I reckon.");
            // Don't bother catching - don't need it. - But spend your candy evolving the type!
            myPokemon.evolveMyBest(myFamilyBest.getPokemonId());
            myPokemon.update(api);
        }
        requestChill("short");

    }

    private static Pokemon catchPokemon(Encounter encounter, PokemonGo api) throws RequestFailedException, InterruptedException, NoSuchItemException {
        ItemBag bag = api.getInventories().getItemBag();
        PokeBank pokebank = api.getInventories().getPokebank();
        Pokemon caughtPokemon = null;
        if (encounter.isSuccessful()) {
            Logger.INSTANCE.Log(Logger.TYPE.INFO, "Encountered: " + encounter.getEncounteredPokemon().getPokemonId());

            List<Pokeball> usablePokeballs = bag.getUsablePokeballs();
            if (usablePokeballs.size() > 0) {
                //Select pokeball with smart selector to print what pokeball is used
                double probability = encounter.getCaptureProbability();
                Pokeball pokeball = PokeballSelector.SMART.select(usablePokeballs, probability);
                System.out.println("Attempting to catch: " + encounter.getEncounteredPokemon().getPokemonId() + " with " + pokeball
                        + " (" + probability + ")");
                // Throw pokeballs until capture or flee
                while (encounter.isActive()) {
                    // Wait between Pokeball throws
                    requestChill("short");

                    // If no item is active, use a razzberry
                    int razzberryCount = bag.getItem(ItemIdOuterClass.ItemId.ITEM_RAZZ_BERRY).getCount();
                    if (encounter.getActiveItem() == null && razzberryCount > 0) {
                        encounter.useItem(ItemIdOuterClass.ItemId.ITEM_RAZZ_BERRY);
                    }

                    // Throw pokeball with random properties
                    encounter.throwPokeball(PokeballSelector.SMART, ThrowProperties.random());

                    if (encounter.getStatus() == CatchPokemonResponseOuterClass.CatchPokemonResponse.CatchStatus.CATCH_SUCCESS) {

                        // Print pokemon stats
                        caughtPokemon = pokebank.getPokemonById(encounter.getCapturedPokemon());
                        Pokemon pokemon = caughtPokemon;
                        if (pokemon != null) {
                            double iv = pokemon.getIvInPercentage();
                            int number = pokemon.getPokemonId().getNumber();
                            String name = PokeDictionary.getDisplayName(number, Locale.ENGLISH);
                            Logger.INSTANCE.Log(Logger.TYPE.EVENT, "Caught pokemon: " + name + " " + pokemon.getCp() + " CP (" + iv + "%)");
                            System.out.println("====" + name + "====");
                            System.out.println("CP: " + pokemon.getCp());
                            System.out.println("IV: " + iv + "%");
//                            System.out.println("Height: " + pokemon.getHeightM() + "m");
//                            System.out.println("Weight: " + pokemon.getWeightKg() + "kg");
                            System.out.println("Move 1: " + pokemon.getMove1());
                            System.out.println("Move 2: " + pokemon.getMove2());
                            //Rename the pokemon to <Name> IV%
                            pokemon.renamePokemon(name + " " + iv + "%");
                            //Set pokemon with IV above 90% as favorite
//                            if (iv > 90) {
//                                pokemon.setFavoritePokemon(true);
//                            }
                        }
                    } else {
                        System.out.println("It broke free!");
                    }
                }
            } else {
                System.out.println("Skipping Pokemon, we have no Pokeballs!");
            }

            // Wait for animation before catching next pokemon
            requestChill("long");
        } else {
            System.out.println("Failed to encounter pokemon: " + encounter.getEncounterResult());
        }
        return caughtPokemon;
    }

    private static PokemonGo login(String hash_key, String login, String password,
            Double longitude, Double latitude) throws RequestFailedException, InterruptedException {

        boolean loggedIn = false;
        HashProvider provider = new PokeHashProvider(PokeHashKey.from(hash_key), true);
        PokemonGo api = new PokemonGo(HTTPCLIENT);
        while (!loggedIn) {
            try {
                api.login(new PtcCredentialProvider(HTTPCLIENT, login, password), provider);
                api.setLocation(latitude, longitude, 0);
                loggedIn = true;
                api.addListener(new PlayerListener() {
                    @Override
                    public void onLevelUp(PokemonGo pg, int i, int i1) {
                        Logger.INSTANCE.Log(Logger.TYPE.EVENT, "Level Up! " + (level + 1));
                        Logger.INSTANCE.Log(Logger.TYPE.ERROR, "Level Up Detected! Rebooting bot to accept rewards..");
                        startLooper();
                    }

                    @Override
                    public void onMedalAwarded(PokemonGo pg, PlayerProfile pp, Medal medal) {
                        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                    }

                    @Override
                    public void onWarningReceived(PokemonGo pg) {
                        //  throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                    }

                });
            } catch (Exception ex) {
                Logger.INSTANCE.Log(Logger.TYPE.ERROR, "Error Logging in. " + ex.toString());
                requestChill("long");
            }
        }
        return api;
    }

    private static void requestChill(String length) throws InterruptedException {
        switch (length) {
            case "short":
                // Wait anywhere between 1 and 3 seconds
                Thread.sleep(1000 + RANDOM.nextInt(3000));
                break;
            case "long":
                Thread.sleep(3000 + RANDOM.nextInt(2000));
        }
    }

    private static void printStats(Inventory inventory, MyPokemon myPkmn, PlayerProfile profile) throws RequestFailedException {
        level = profile.getLevel();
        System.out.println("====== Player 1 Start! =======");
        System.out.println("==== Level: " + level + " ====");
        System.out.println("==== Exp: " + profile.getStats().getExperience() + " ====");
        System.out.println("==== Km Walked: " + profile.getStats().getKmWalked() + " ====");
        inventory.printStock();
        myPkmn.printBagStats();
        System.out.println("----------------------------------------------------------");
    }

    public static double getPercentageIV(PokemonDataOuterClass.PokemonData pkmn) {
        double ivStamina = pkmn.getIndividualStamina();
        double ivAttack = pkmn.getIndividualAttack();
        double ivDefense = pkmn.getIndividualDefense();
        return (ivAttack + ivDefense + ivStamina) * 100 / 45.0;
    }
}
