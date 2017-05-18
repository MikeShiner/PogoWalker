/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package walker.classes;

import POGOProtos.Inventory.Item.ItemIdOuterClass;
import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;
import static POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId.*;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.EggIncubator;
import com.pokegoapi.api.inventory.Item;
import com.pokegoapi.api.inventory.ItemBag;
import com.pokegoapi.api.inventory.Pokeball;
import com.pokegoapi.api.pokemon.EggPokemon;
import com.pokegoapi.exceptions.request.RequestFailedException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import walker.utils.DAO;

/**
 *
 * @author Shiner
 */
public class Inventory {

    ItemBag bag;
    private DAO database;
    
    int pokeballs = 0;
    int greatballs = 0;
    int ultraballs = 0;
    int balls_total = 0;

    int potions = 0;
    int superpotions = 0;
    int hyperpotions = 0;
    int potions_total = 0;

    int razzberries = 0;
    int nanabberries = 0;
    int pinapberries = 0;

    int incubators_count = 0;
    int incubators_free = 0;
    int luckyeggs = 0;

    int incense_count = 0;
    int revives_count = 0;
    int metal_coat = 0;
    int dragon_scale = 0;
    int kings_rock = 0;
    int upgrade = 0;
    int sun_stone = 0;

    int bagCount = 0;
    int bagMax = 0;
    List<Pokeball> usablePokeballs;
    
    Set<EggPokemon> eggs;
    List<EggIncubator> incubators;

    public Inventory(PokemonGo go, DAO indatabase) {
        update(go);
        database = indatabase;
        saveToDB();
    }

    public int getBalls_total() {
        return balls_total;
    }

    public void update(PokemonGo go) {
        bag = go.getInventories().getItemBag();
        eggs = go.getInventories().getHatchery().getEggs();
        incubators = go.getInventories().getIncubators();
        bagCount = bag.getItemsCount();

        // Cache collection of items
        Collection<Item> items = bag.getItems();

        // Cache pokeballs
        pokeballs = bag.getItem(ITEM_POKE_BALL).getCount();
        greatballs = bag.getItem(ITEM_GREAT_BALL).getCount();
        ultraballs = bag.getItem(ITEM_ULTRA_BALL).getCount();
        balls_total = pokeballs + greatballs + ultraballs;

        // Cache Potions
        potions = bag.getItem(ITEM_POTION).getCount();
        superpotions = bag.getItem(ITEM_SUPER_POTION).getCount();
        hyperpotions = bag.getItem(ITEM_HYPER_POTION).getCount();
        potions_total = potions + superpotions + hyperpotions;

        // Cache other items
        incense_count = bag.getItem(ITEM_INCENSE_ORDINARY).getCount();
        revives_count = bag.getItem(ITEM_REVIVE).getCount();
        metal_coat = bag.getItem(ITEM_METAL_COAT).getCount();
        dragon_scale = bag.getItem(ITEM_DRAGON_SCALE).getCount();
        kings_rock = bag.getItem(ITEM_KINGS_ROCK).getCount();
        upgrade = bag.getItem(ITEM_UP_GRADE).getCount();
        sun_stone = bag.getItem(ITEM_SUN_STONE).getCount();

        razzberries = bag.getItem(ITEM_RAZZ_BERRY).getCount();
        nanabberries = bag.getItem(ITEM_NANAB_BERRY).getCount();
        pinapberries = bag.getItem(ITEM_PINAP_BERRY).getCount();

        incubators = incubators.size();
        luckyeggs = bag.getItem(ITEM_LUCKY_EGG).getCount();

        usablePokeballs = bag.getUsablePokeballs();

    }

    public void printStock() throws RequestFailedException {
        System.out.println("----- My Inventory -------");
        System.out.println("Bag Space: " + bag.getItemsCount() + "/" + bag.getMaxStorage());
        System.out.println("Pokeballs(" + balls_total + ") P/G/U: " + pokeballs + "/" + greatballs + "/" + ultraballs);
        System.out.println("Incense: " + incense_count);
        System.out.println("Potions/Super/Max: " + potions + "/" + superpotions + "/" + hyperpotions);
        System.out.println("Razz/Nanab/Pinap: " + razzberries + "/" + nanabberries + "/" + pinapberries);
        System.out.println("Revives: " + revives_count);
        System.out.println(" --- Special Items: ");
        System.out.println("Metal Coat: " + metal_coat);
        System.out.println("Dragon Scale: " + dragon_scale);
        System.out.println("Kings Rock: " + kings_rock);
        System.out.println("Upgrades: " + upgrade);
        System.out.println("Sun Stone: " + sun_stone);
        System.out.println(" --- Others: ");
        System.out.println("Incubators: " + incubators);
        System.out.println("Lucky Eggs: " + luckyeggs);
    }

    public void clearItems() throws RequestFailedException {

        if (potions > 1) {
            System.out.println("Thorwing away " + potions + " Potions..");
            bag.removeItem(ITEM_POTION, potions);
        }
        if (superpotions > 1) {
            System.out.println("Thorwing away " + superpotions + " Super Potions..");
            bag.removeItem(ITEM_SUPER_POTION, superpotions);
        }
        if (hyperpotions > 1) {
            System.out.println("Thorwing away " + hyperpotions + " Hyper Potions..");
            bag.removeItem(ITEM_HYPER_POTION, hyperpotions);
        }
        if (revives_count > 1) {
            System.out.println("Thorwing away " + revives_count + " Revives..");
            bag.removeItem(ITEM_REVIVE, revives_count);
        }
        if (razzberries > 50) {
            System.out.println("Thorwing away " + razzberries + " Razz Berries..");
            bag.removeItem(ITEM_RAZZ_BERRY, razzberries);
        }
        if (pinapberries > 1) {
            System.out.println("Thorwing away " + pinapberries + " Pinap Berries..");
            bag.removeItem(ITEM_PINAP_BERRY, pinapberries);
        }
        if (nanabberries > 1) {
            System.out.println("Thorwing away " + nanabberries + " Nanab Berries..");
            bag.removeItem(ITEM_NANAB_BERRY, nanabberries);
        }
        // Only keep max 200 balls on hand
        if (bagCount > (bagCount * 0.95)) {
            if (pokeballs > 50) {
                System.out.println("Thorwing away " + (pokeballs - 50) + " Pokeballs..");
                bag.removeItem(ITEM_POKE_BALL, (pokeballs - 50));
            } else if (greatballs > 100) {
                System.out.println("Thorwing away " + (greatballs - 100) + " Greatballs..");
                bag.removeItem(ITEM_GREAT_BALL, (greatballs - 100));
            } else if (ultraballs > 100) {
                System.out.println("Thorwing away " + (ultraballs - 100) + " Ultraballs..");
                bag.removeItem(ITEM_ULTRA_BALL, (ultraballs - 100));
            }
        }
    }

    public void saveToDB() {

    }

    public List<Pokeball> getUsablePokeballs() {
        return usablePokeballs;
    }

    public boolean checkHasItem(ItemId item) {
        return bag.getItem(item).getCount() > 0;
    }

}
