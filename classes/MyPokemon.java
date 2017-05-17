/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package walker.classes;

import POGOProtos.Enums.PokemonFamilyIdOuterClass.PokemonFamilyId;
import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;
import POGOProtos.Settings.Master.Pokemon.EvolutionBranchOuterClass.EvolutionBranch;
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
    }

    public void listPokemon(PokemonGo api) {
        for (Pokemon pokemon : api.getInventories().getPokebank().getPokemons()) {

            System.out.println("My Pokemon: " + pokemon.getPokemonId());
        }
    }

    public void printBagStats() {
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
        List<Pokemon> pokemonEvos = new ArrayList<>();
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
    // Includes duplicates
    public List<Pokemon> getFullFamily(PokemonId pokemonID) {
        List<Pokemon> pokemonFamily = new ArrayList<>();

        PokemonId currentPokemon = evolutionMeta.getBasic(pokemonID).get(0);
        boolean canEvolve = evolutionMeta.canEvolve(currentPokemon);
        List<PokemonId> evoList = new ArrayList<>();
        evoList.add(currentPokemon);
        while (canEvolve) {
            List<PokemonId> evos = evolutionMeta.getEvolutions(currentPokemon);
            currentPokemon = evos.get(0);
            canEvolve = evolutionMeta.canEvolve(currentPokemon);
            evoList.addAll(evos);
        }

        for (Pokemon pokemon : pokemons) {
            for (PokemonId evolution : evoList) {
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
        List<Pokemon> transferList = new ArrayList<>();
        transferList.add(pokemon);
        transferPokemonList(transferList);
    }

    public void transferInsuperior(Pokemon superiorPkmn) {
        List<Pokemon> transferList = new ArrayList<>();

        for (Pokemon pokemon : pokemons) {
            if (pokemon.getPokemonId().equals(superiorPkmn.getPokemonId()) && !pokemon.equals(superiorPkmn)) {
                transferList.add(pokemon);
            }
        }
        transferPokemonList(transferList);
    }

    /**
     * Transfers Pokemon which are not a top evolution and also not the highest
     * IV lower-evo.
     *
     * @param pokemonID
     */
    public void transferInsuperior(PokemonId pokemonID) {
        List<Pokemon> evolutions = orderByIVsDesc(getFullFamily(pokemonID));
        // Unique list of highest IV evos. No duplications.
        List<Pokemon> topEvos = getTopEvolutions(pokemonID);
        // Best bottom Evo (to keep)
        List<Pokemon> lowerEvos = orderByIVsDesc(getLowerEvolutions(pokemonID));
        Pokemon bottomEvo = null;
        if (lowerEvos.size() > 0) {
            bottomEvo = lowerEvos.get(0);
        }

        List<Pokemon> transferList = new ArrayList<>();

        // If FullFamily pokemon not highest-evo or highest lower-evo, add to transferList.
        for (Pokemon pokemon : evolutions) {
            Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "Judging Pokemon: " + pokemon.getPokemonId() + " (" + pokemon.getIvInPercentage() + "%)");
            if (!topEvos.contains(pokemon) && (bottomEvo != null && !pokemon.equals(bottomEvo))) {
                Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "Transfer List!");
                transferList.add(pokemon);
            } else {
                Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "Safe List!");

            }
        }
        transferPokemonList(transferList);
    }

    public void evolveMyBest(PokemonId pokemonID, MyPokedex pokedex, Inventory inv) throws RequestFailedException, InterruptedException {
        PokemonId chosenPokemon;
        boolean canEvolve = true;

        EvolutionBranch branchToPursue = null;
        EvolutionBranch betterIVEvo = null;

        chosenPokemon = pokemonID;
        while (canEvolve) {
            canEvolve = false;
            List<Pokemon> evolist = getLowerEvolutions(chosenPokemon);
            if (evolist.size() < 1) {
                Logger.INSTANCE.Log(Logger.TYPE.INFO, "No pokemon to evolve");

            } else {
                Pokemon pokemonToEvolve = evolist.get(0);
                Logger.INSTANCE.Log(Logger.TYPE.INFO, "Looking to evolve: " + pokemonToEvolve.getPokemonId() + " (" + pokemonToEvolve.getIvInPercentage() + "%)");

                List<EvolutionBranch> evolutionBranches = evolutionMeta.getEvolution(chosenPokemon).getEvolutionBranch();

                // Cycle through evolutions to determine which is best to evolve into.
                for (EvolutionBranch evoBranch : evolutionBranches) {
                    PokemonId evoID = evoBranch.getEvolution();

                    Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "Potentional Evolution: " + evoID);

                    boolean needToCatch = pokedex.doINeed(evoID);
                    Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "Do I Need? " + needToCatch);

                    int itemrequired = evoBranch.getEvolutionItemRequirementValue();
                    ItemId requiredItem = evoBranch.getEvolutionItemRequirement();
                    Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "Item Required? " + (itemrequired > 0 ? "Yes: " + requiredItem : "No."));
                    boolean itemCheck = true;
                    if (itemrequired > 0) {
                        itemCheck = inv.checkHasItem(requiredItem);
                        Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "Do I Have this Item? " + itemCheck);
                    }
                    if (itemCheck) {
                        if (needToCatch) {

                            branchToPursue = evoBranch;
                            break;
                        } else {
                            Pokemon myEvo = getPokemon(evoID);
                            if (myEvo != null) {
                                Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "I have a " + myEvo.getPokemonId() + " (" + myEvo.getIvInPercentage() + "%) VS "
                                        + pokemonToEvolve.getPokemonId() + " (" + pokemonToEvolve.getIvInPercentage() + "%)");
                                // If I have a copy of this evo - compare IVS
                                if (myEvo.getIvInPercentage() < pokemonToEvolve.getIvInPercentage()) {
                                    betterIVEvo = evoBranch;
                                }
                            } else {
                                // Don't currently have this evo in bag. Catch.
                                betterIVEvo = evoBranch;
                            }
                        }
                    }
                }
                boolean newCatch = false; // Keep track if event to see if it's a new Pokedex Entry!
                EvolutionBranch targetEvolution;
                if (branchToPursue != null) {
                    targetEvolution = branchToPursue;
                    newCatch = true;
                } else {
                    targetEvolution = betterIVEvo;
                }

                if (targetEvolution != null) {
                    Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "Looking to evolve: " + pokemonToEvolve.getPokemonId() + " (" + pokemonToEvolve.getIvInPercentage() + "%) into " + targetEvolution);
                    // Does it require an item?
                    EvolutionResult result;

                    if (targetEvolution.getEvolutionItemRequirementValue() > 0) {
                        // Requires an item!
                        Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "Evolving with item! ");
                        result = pokemonToEvolve.evolve(targetEvolution.getEvolutionItemRequirement());
                    } else {
                        result = pokemonToEvolve.evolve();
                    }

                    if (result.isSuccessful()) {

                        Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "---- Evolution Success! ---- ");
                        Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "-- New Pokemon: " + result.getEvolvedPokemon().getPokemonId());
                        Logger.INSTANCE.Log(Logger.TYPE.EVENT, "Evolution: " + pokemonToEvolve.getPokemonId() + " (" + pokemonToEvolve.getIvInPercentage() + "%) To " + result.getEvolvedPokemon().getPokemonId() + " (" + result.getEvolvedPokemon().getIvInPercentage() + "%)");
                        if (newCatch) {
                            Logger.INSTANCE.Log(Logger.TYPE.EVENT, "New Pokedex Entry! " + result.getEvolvedPokemon().getPokemonId());
                        }

                        canEvolve = result.getEvolvedPokemon().canEvolve();
                        chosenPokemon = result.getEvolvedPokemon().getPokemonId();

                    } else {
                        Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "---- Evolution Failed! ---- ");
                    }

                } else {
                    Logger.INSTANCE.Log(Logger.TYPE.DEBUG, "No target Evoltions to do!");
                }
            }
            Thread.sleep(5000);
        }

    }

    public int getCandiesToEvolve(PokemonId pokemonID) {
        PokemonId currentPokemon = evolutionMeta.getBasic(pokemonID).get(0);
        boolean canEvolve = evolutionMeta.canEvolve(currentPokemon);
        System.out.println(currentPokemon);
        int candyCount = 0;
        if (canEvolve) {
            candyCount = evolutionMeta.getEvolution(currentPokemon).getEvolutionBranch().get(0).getCandyCost();

            while (canEvolve) {
                List<PokemonId> evos = evolutionMeta.getEvolutions(currentPokemon);
                currentPokemon = evos.get(0);
                System.out.println(currentPokemon);
                canEvolve = evolutionMeta.canEvolve(currentPokemon);
                if (canEvolve) {
                    candyCount = candyCount + evolutionMeta.getEvolution(currentPokemon).getEvolutionBranch().get(0).getCandyCost();
                }
            }
        }
        return candyCount;
    }

    public int getCandiesFromFamily(PokemonId pokemonID) {
        List<Pokemon> myFamily = getFullFamily(pokemonID);
        if (myFamily.isEmpty()) {
            return 0;
        } else {
            return myFamily.get(0).getCandy();
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

    /**
     * @param pokemonID
     * @param transferList
     * @return List of top evolution pokemon from bag - highest IVs for
     * duplicates
     */
    public List<Pokemon> getTopEvolutions(PokemonId pokemonID) {
        List<PokemonId> refList = new ArrayList<>();
        List<Pokemon> familyList = orderByIVsDesc(getFullFamily(pokemonID));
        List<PokemonId> topEvos = evolutionMeta.getHighest(pokemonID);
        List<Pokemon> topEvoPokemon = new ArrayList<>();
        // If pokemon is one of the top-evos 
        for (Pokemon pokemon : familyList) {
            if (topEvos.contains(pokemon.getPokemonId()) && !refList.contains(pokemon.getPokemonId())) {
                topEvoPokemon.add(pokemon);
                refList.add(pokemon.getPokemonId());
            }
        }
        return topEvoPokemon;
    }

    /**
     *
     * @param pokemonID
     * @return List of bottom (all non-top) evos Pokemon from bag
     */
    public List<Pokemon> getLowerEvolutions(PokemonId pokemonID) {
        List<Pokemon> topevos = getTopEvolutions(pokemonID);
        List<Pokemon> fullfamily = orderByIVsDesc(getFullFamily(pokemonID));
        List<Pokemon> lowerEvos = new ArrayList<>();

        for (Pokemon pokemon : fullfamily) {
            if (!topevos.contains(pokemon)) {
                lowerEvos.add(pokemon);
            }
        }

        return lowerEvos;
    }

    public boolean checkIfHighestEvo(PokemonId pokemonID) {
        List<PokemonId> topEvos = evolutionMeta.getHighest(pokemonID);
        return topEvos.contains(pokemonID);
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
}
