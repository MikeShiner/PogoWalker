/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package walker.classes;

import POGOProtos.Enums.PokemonFamilyIdOuterClass.PokemonFamilyId;
import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import walker.utils.*;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.PokeBank;
import com.pokegoapi.api.map.pokemon.EvolutionResult;
import com.pokegoapi.api.pokemon.Evolutions;
import com.pokegoapi.api.pokemon.Pokemon;
import com.pokegoapi.exceptions.request.RequestFailedException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Shiner
 */
public class MyPokemon {

    private List<Pokemon> pokemons;
    private final Evolutions evolutionMeta;
    private final DAO database;
    
    PokeBank pokebag;

    DecimalFormat df = new DecimalFormat("#0.00");

    public MyPokemon(PokemonGo api, DAO indatabase) {
        database = indatabase;
        evolutionMeta = api.getItemTemplates().getEvolutions();
        update(api);
    }

    public void update(PokemonGo api) {
        pokebag = api.getInventories().getPokebank();
        pokemons = pokebag.getPokemons();

        Logger.INSTANCE.Log(Logger.TYPE.INFO, "Updating Database PokeBag..");
        database.clearPokebag();
        database.updatePokebag(pokemons);
//        for (Pokemon pokemon : pokemons) {
//            database.updatePokebag(pokemon.getPokemonId().name(), pokemon.getIvInPercentage(),
//                    pokemon.getCp(), pokemon.getMove1().name(), pokemon.getMove2().name());
//        }
    }

    public void printBagStats() {
        System.out.println("My Pokemon: " + pokemons.size() + "/" + pokebag.getMaxStorage());
    }

    public void printMyPokemon() {
        System.out.println("Your pokemon: " + pokemons.size() + "/" + pokebag.getMaxStorage());

    }

    public List<Pokemon> getPokemons() {
        return pokemons;
    }

    // Search through pokebag and returns example Pokemon of target pokemon
    public Pokemon getPokemon(PokemonId pokemonToFind) {
        Pokemon foundPokemon = null;
        for (Pokemon pokemon : pokemons) {
            if (pokemon.getPokemonId().equals(pokemonToFind)) {
                foundPokemon = pokemon;
                break;
            }
        }
        return foundPokemon;
    }

    // Search through Pokebag and find all evolutions
    public List<Pokemon> getPokemonEvolutions(PokemonId pokemonID) {
        List<Pokemon> pokemonEvos = new ArrayList<Pokemon>();
        List<PokemonId> evos = evolutionMeta.getEvolutions(pokemonID);

        for (Pokemon pokemon : pokemons) {
            for (PokemonId evolution : evos) {
                if (pokemon.getPokemonId().equals(evolution)) {
                    pokemonEvos.add(pokemon);
                }
            }
        }
        return pokemonEvos;
    }

    // getPokemonEvolutions with the original pokemon included
    public List<Pokemon> getFullFamily(PokemonId pokemonID) {
        List<Pokemon> pokemonFamily = new ArrayList<Pokemon>();
        List<PokemonId> evos = evolutionMeta.getEvolutions(pokemonID);

        for (Pokemon pokemon : pokemons) {
            if (pokemon.getPokemonId().equals(pokemonID)) {
                pokemonFamily.add(pokemon);
            }
            for (PokemonId evolution : evos) {
                if (pokemon.getPokemonId().equals(evolution)) {
                    pokemonFamily.add(pokemon);
                }
            }
        }
        return pokemonFamily;
    }

    // Need better way to get candy result then to us a 1 element array
    public void transferPokemon(Pokemon pokemon) {
        Logger.INSTANCE.Log(Logger.TYPE.INFO, "Transfering " + pokemon.getPokemonId());
        List<Pokemon> transferList = new ArrayList<Pokemon>();
        transferList.add(pokemon);
        transferPokemonList(transferList);
    }

    public void transferInsuperior(Pokemon superiorPkmn) {
        List<Pokemon> transferList = new ArrayList<Pokemon>();

        for (Pokemon pokemon : pokemons) {
            if (pokemon.getPokemonId().equals(superiorPkmn.getPokemonId()) && !pokemon.equals(superiorPkmn)) {
                transferList.add(pokemon);
            }
        }
        transferPokemonList(transferList);
    }

    public void evolveMyBest(PokemonId pokemonID) throws RequestFailedException, InterruptedException {
        List<Pokemon> evolutions = orderByIVsDesc(getFullFamily(pokemonID));
        Pokemon pokemonToEvolve = evolutions.get(0);
        boolean canIEvolve = pokemonToEvolve.canEvolve();

        for (Pokemon pokemon : evolutions) {
            System.out.println(pokemon.getPokemonId() + " (" + pokemon.getIvInPercentage() + "%)");
        }

        // Remove Highest IV from the transfer list
        evolutions.remove(0);
        System.out.println("I've picked to evolve: " + pokemonToEvolve.getPokemonId() + " (" + pokemonToEvolve.getIvInPercentage() + "%)");
        transferPokemonList(evolutions);
        Thread.sleep(4000);
        if (canIEvolve) {
            System.out.println("Evolving..");
            EvolutionResult result = pokemonToEvolve.evolve();
            if (result.isSuccessful()) {
                Thread.sleep(6000);
                Pokemon evolved = result.getEvolvedPokemon();
                canIEvolve = evolved.canEvolve();
                System.out.println("Success: " + evolved.getPokemonId());
                System.out.println("Can I evolve? " + canIEvolve);
            } else {
                System.out.println("Evolve unsuccessfull.");
            }
        }
    }

    public List<Pokemon> orderByIVsDesc(List<Pokemon> sortingList) {
        Comparator<Pokemon> ivSort = (Pokemon primary, Pokemon secondary) -> {
            double iv1 = primary.getIvInPercentage();
            double iv2 = secondary.getIvInPercentage();
            return Double.compare(iv2, iv1);
        };
        Collections.sort(sortingList, ivSort);
        return sortingList;
    }

    public void transferPokemonList(List<Pokemon> transferList) {
        if (transferList.size() > 0) {
            try {
                Pokemon[] transferArray = transferList.toArray(new Pokemon[transferList.size()]);
                Map<PokemonFamilyId, Integer> responses = pokebag.releasePokemon(transferArray);

                // Get all candies for pokemon transfered
                Map<PokemonFamilyId, Integer> candies = new HashMap<>();
                for (Map.Entry<PokemonFamilyId, Integer> entry : responses.entrySet()) {
                    int candyAwarded = entry.getValue();
                    PokemonFamilyId family = entry.getKey();
                    Integer candy = candies.get(family);
                    if (candy == null) {
                        //candies map does not yet contain the amount if null, so set it to 0
                        candy = 0;
                    }
                    //Add the awarded candies from this request
                    candy += candyAwarded;
                    candies.put(family, candy);
                }
                for (Map.Entry<PokemonFamilyId, Integer> entry : candies.entrySet()) {
                    System.out.println(entry.getKey() + ": " + entry.getValue() + " candies awarded");

                }
            } catch (RequestFailedException ex) {
                Logger.INSTANCE.Log(Logger.TYPE.ERROR, "Transfering insuperiors: " + ex.toString());
            } catch (NullPointerException ex) {
                Logger.INSTANCE.Log(Logger.TYPE.ERROR, "No pokemon to transfer" + ex.toString());
            }
        }
    }

//    public void EvolvesMyPokemon() throws RequestFailedException, InterruptedException {
//        // Go through each pokemon in the bag, see if I can evolve it.
//        //    If so, see if I have the evolution. If not, evolve this one.
//        //
//        // Duplicate list to avoid concurrent modification
//        final List<Pokemon> pokemonToEvolve = new ArrayList<Pokemon>();
//        for (Pokemon pokemon : pokemons) {
//            Pokemon highestPokemon = pokemon;
//            boolean next = false;
//
//            while (!next) {
//                boolean canEvolve = highestPokemon.canEvolve();
//                Logger.INSTANCE.Log(Logger.TYPE.INFO, "Can I evolve a " + highestPokemon.getPokemonId().name());
//                Logger.INSTANCE.Log(Logger.TYPE.INFO, ".. " + canEvolve + " " + highestPokemon.getCandy() + "/" + highestPokemon.getCandiesToEvolve());
//                if (canEvolve) {
//                    PokemonId upgraded = evolutionMeta.getEvolutions(highestPokemon.getPokemonId()).get(0);
//                    Pokemon myPokemon = findAPokemonLike(upgraded);
//                    if (myPokemon == null) {
//                        Logger.INSTANCE.Log(Logger.TYPE.INFO, "I don't have a " + upgraded.name() + "!");
//                    }
//                    boolean penultimateForm = true;
//                    if (myPokemon == null || (highestPokemon.getIvInPercentage() > myPokemon.getIvInPercentage())) {
//                        Logger.INSTANCE.Log(Logger.TYPE.INFO, "Upgraded: " + upgraded.name());
//                        next = true;
//                        pokemonToEvolve.add(highestPokemon);
//                        Thread.sleep(4000);
//                    } else {
//                        highestPokemon = myPokemon;
//                    }
//                } else {
//                    next = true;
//                }
//            }
//
//        }
//    }
//
//    public void evolvePokemon(Pokemon pokemon) throws RequestFailedException { 
//        Logger.INSTANCE.Log(Logger.TYPE.INFO, "Evolving" + pokemon.getPokemonId());
//        EvolutionResult result = pokemon.evolve();
//        Logger.INSTANCE.Log(Logger.TYPE.INFO, result.getResult().toString());
//
//    }
//
//    // Returns a list of all pokemon of a certain pokemonID supplied
//    public List<Pokemon> getCertainPokemon(PokemonId pokemonID) {
//        List<Pokemon> listofpokemon = null;
//        for (Pokemon pokemon : pokemons) {
//            if (pokemon.getPokemonId().equals(pokemonID)) {
//                listofpokemon.add(pokemon);
//            }
//        }
//        return listofpokemon;
//    }
//
//    //Returns whether the supplied pokemon exceeds ones I already have    
//    public boolean isItSuperior(PokemonId pokemonID, double contendIVs, int contendCPs) {
//        boolean isItBetter = false;
//        boolean doINeedCandies = false;
//        double bestIV = 0.0;
//        double contenderIV = 0.0;
//        try {
//            Pokemon bestPokemon = findSuperior(pokemonID);
//
//            bestIV = bestPokemon.getIvInPercentage();
//            contenderIV = contendIVs;
//
//            // If pokemon has better IVs OR same IVs but better CP
//            isItBetter = (contendIVs > bestIV)
//                    || (contendIVs == bestPokemon.getIvInPercentage()
//                    && contendCPs > bestPokemon.getCp());
//
//        } catch (Exception ex) {
//            Logger.INSTANCE.Log(Logger.TYPE.ERROR, "[doIWant] can't find pokemon in your bag (" + pokemonID + ")..");
//        }
//        // Logging
//        if (isItBetter) {
//            Logger.INSTANCE.Log(Logger.TYPE.INFO, "This " + pokemonID + " is better than mine! Mine:" + bestIV + "% Vs. Encountered:" + df.format(contenderIV) + "%");
//        }
//
//        return isItBetter;
//    }
//
//    public int getCandies(PokemonId pokemonID) {
//        List<Pokemon> targetPokemon = pokebag.getPokemonByPokemonId(pokemonID);
//        int myCandies = targetPokemon.get(0).getCandy();
//        return myCandies;
//    }
//
//    public int getCandiesNeeded(PokemonId pokemonID) {
//        List<Pokemon> targetPokemon = pokebag.getPokemonByPokemonId(pokemonID);
//        int evolutionCandies = targetPokemon.get(0).getCandiesToEvolve();
//
//        // The total candies you need is the highest evolution pokemon's evolvecost
//        List<Pokemon> basic = pokebag.getPokemonByPokemonId(evolutionMeta.getEvolutions(pokemonID).get(0));
//        if (basic.size() > 1) {
//            evolutionCandies = basic.get(0).getCandiesToEvolve();
//            System.out.println("You have this evolution in your bag: " + basic.get(1).getPokemonId().name());
//        }
//
//        return evolutionCandies;
//    }
//
//    public boolean doINeedCandies(PokemonId pokemonID) {
////        List<PokemonId> base = evolutionsMeta.
//        try {
//
//            int myCandies = getCandies(pokemonID);
//            int evolutionCandies = getCandiesNeeded(pokemonID);
//            if (myCandies < evolutionCandies) {
//                Logger.INSTANCE.Log(Logger.TYPE.INFO, "There's a " + pokemonID + "! I need it for the candy. (" + myCandies + "/" + evolutionCandies + ")");
//            }
//            return (myCandies < evolutionCandies);
//        } catch (Exception ex) {
//            Logger.INSTANCE.Log(Logger.TYPE.ERROR, "Can't fetch candies (Non pokemon found) for : " + pokemonID);
//            return false;
//        }
//    }
//
//    // Find the best pokemon 
//    public Pokemon findSuperior(PokemonId pokemonID) {
//        double highestIV = 0.0;
//        int fallbackCP = 0;
//        Pokemon bestPokemon = null;
//        for (Pokemon pokemon : pokemons) {
//            //   System.out.println("DEBUG: DOES " + pokemon.getPokemonId() + " EQUAL " + pokemonID);
//            if (pokemon.getPokemonId().equals(pokemonID)) {
//                // If pokemon is better IVs or equal with the better CP - make bestPokemon
//                if (pokemon.getIvInPercentage() > highestIV
//                        || (pokemon.getIvInPercentage() == highestIV && pokemon.getCp() > fallbackCP)) {
//
//                    bestPokemon = pokemon;
//                    highestIV = pokemon.getIvInPercentage();
//                    fallbackCP = pokemon.getCp();
//                }
//            }
//        }
//        return bestPokemon;
//    }
//    // Transfers all pokemon of a certain ID except the highest IV one.
//
//    private String caninvolve() {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//    }
}
