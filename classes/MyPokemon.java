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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    
    public void transferInsuperior(PokemonId pokemonID){
        List<Pokemon> evolutions = orderByIVsDesc(getFullFamily(pokemonID));

        for (Pokemon pokemon : evolutions) {
            System.out.println(pokemon.getPokemonId() + " (" + pokemon.getIvInPercentage() + "%)");
        }

        // Remove Highest IV from the transfer list
        evolutions.remove(0);
        transferPokemonList(evolutions);
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
        transferPokemonList(evolutions);
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
    
    public int getCandiesToEvolve(PokemonId pokemonID){
        int candy = 0;
        List<Pokemon> myFamily = getFullFamily(pokemonID);
        // Remove duplicates by using HashSet
        HashSet<PokemonId> seen = new HashSet<>();
        myFamily.removeIf(e->!seen.add(e.getPokemonId()));
        
        for (Pokemon pokemon : myFamily){
            candy = candy + pokemon.getCandiesToEvolve();
            System.out.println("Adding " + pokemon.getPokemonId());
            System.out.println("Adding candy.. " + pokemon.getCandiesToEvolve());
        }
        return candy;
    }
    public int getCandiesFromFamily(PokemonId pokemonID){
        List<Pokemon> myFamily = getFullFamily(pokemonID);
        return myFamily.get(0).getCandy();
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
}
