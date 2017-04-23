/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package walker.classes;

import POGOProtos.Enums.PokemonIdOuterClass;
import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import com.pokegoapi.api.PokemonGo;
import java.util.HashMap;
import walker.utils.*;

/**
 *
 * @author Shiner
 */
public class MyPokedex {

    private com.pokegoapi.api.inventory.Pokedex pokedex;
    private HashMap<PokemonId, Boolean> pokedexList = new HashMap<>();
    private DAO database;
    private int pokemonCaught = 0;

    public MyPokedex(PokemonGo api, DAO indatabase) {
        database = indatabase;
        update(api);
    }

    public void update(PokemonGo api) {
        pokedex = api.getInventories().getPokedex();
        System.out.println("Updating Database Pokedex..");
        // Update hashmap with Pokedex values
        for (int i = 1; i < 251; i++) {
            PokemonId pkmn = PokemonIdOuterClass.PokemonId.forNumber(i);
            boolean doINeed = doINeed(pkmn);
            pokedexList.put(pkmn, doINeed);
            if (!doINeed) {
                database.updatePokedex(pkmn.name(), true);
                pokemonCaught++;
            }
        }
    }


    public boolean doINeed(PokemonId pkmnID) {
        try {
            return pokedex.getPokedexEntry(pkmnID).getTimesCaptured() == 0;
        } catch (NullPointerException ex) {
            return true;
        }
    }
    
    public int getPokemonCaught(){
        return pokemonCaught;
    }

}
