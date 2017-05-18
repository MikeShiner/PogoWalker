/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package walker.classes;

import POGOProtos.Inventory.Item.ItemIdOuterClass;
import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;
import static POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId.*;
import POGOProtos.Networking.Responses.UseItemEggIncubatorResponseOuterClass.UseItemEggIncubatorResponse.Result;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.EggIncubator;
import com.pokegoapi.api.inventory.Item;
import com.pokegoapi.api.inventory.ItemBag;
import com.pokegoapi.api.inventory.Pokeball;
import com.pokegoapi.api.pokemon.EggPokemon;
import com.pokegoapi.exceptions.request.RequestFailedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import walker.utils.DAO;
import walker.utils.Logger;

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
    int maxpotions = 0;
    int potions_total = 0;

    int razzberries = 0;
    int nanabberries = 0;
    int pinapberries = 0;

    int incubators_count = 0;
    int incubators_free = 0;
    int egg_count = 0;
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
        incubators = go.getInventories().getIncubators();
        eggs = go.getInventories().getHatchery().getEggs();
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
        maxpotions = bag.getItem(ITEM_MAX_POTION).getCount();
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

        incubators_count = incubators.size();
        egg_count = eggs.size();
        luckyeggs = bag.getItem(ITEM_LUCKY_EGG).getCount();

        usablePokeballs = bag.getUsablePokeballs();

    }

    public void printStock() throws RequestFailedException {
        System.out.println("----- My Inventory -------");
        System.out.println("Bag Space: " + bag.getItemsCount() + "/" + bag.getMaxStorage());
        System.out.println("Pokeballs(" + balls_total + ") P/G/U: " + pokeballs + "/" + greatballs + "/" + ultraballs);
        System.out.println("Incense: " + incense_count);
        System.out.println("Potions/Super/Hyper/Max: " + potions + "/" + superpotions + "/" + hyperpotions + "/" + maxpotions);
        System.out.println("Razz/Nanab/Pinap: " + razzberries + "/" + nanabberries + "/" + pinapberries);
        System.out.println("Revives: " + revives_count);
        System.out.println(" --- Special Items: ");
        System.out.println("Metal Coat: " + metal_coat);
        System.out.println("Dragon Scale: " + dragon_scale);
        System.out.println("Kings Rock: " + kings_rock);
        System.out.println("Upgrades: " + upgrade);
        System.out.println("Sun Stone: " + sun_stone);
        System.out.println(" --- Others: ");
        System.out.println("Incubators: " + incubators_count);
        System.out.println("Lucky Eggs: " + luckyeggs);
        System.out.println("Eggs: " + eggs.size());
        for (EggIncubator incubator : incubators) {
            if (incubator.isInUse()) {
                System.out.println("Egg (" + incubator.getHatchDistance() + ") with " + incubator.getKmCurrentlyWalked() + "/" + incubator.getHatchDistance() + "km");
            }
        }
    }

    /**
     * Checks through incubators for free ones. Organises eggs in order of
     * distance incubates any free eggs into any free incubators.
     */
    public void assignNewEggs() throws RequestFailedException {
        //
        List<EggIncubator> freeIncubators = new ArrayList<>();
        List<EggPokemon> freeEggs = new ArrayList<>();

        incubators.stream().filter((incubator) -> (!incubator.isInUse())).forEachOrdered((incubator) -> {
            freeIncubators.add(incubator);
        });

        eggs.stream().filter((egg) -> (!egg.isIncubate())).forEachOrdered((egg) -> {
            freeEggs.add(egg);
        });

        int incubator_size = freeIncubators.size();
        int egg_size = freeEggs.size();
        Logger.INSTANCE.Log(Logger.TYPE.INFO, incubator_size + " free incubators.");;
        Logger.INSTANCE.Log(Logger.TYPE.INFO, egg_size + " free eggs.");

        if (egg_size > 0) {
            // Order Eggs by lowest to highest
            Comparator<EggPokemon> comparator = (EggPokemon primary, EggPokemon secondary) -> {
                Logger.INSTANCE.Log(Logger.TYPE.INFO, freeIncubators.size() + " free eggs.");
                double distance1 = primary.getEggKmWalkedTarget();
                double distance2 = secondary.getEggKmWalkedTarget();
                return Double.compare(distance1, distance2);
            };
            Collections.sort(freeEggs, comparator);
        }
        
        // Whilst I can still make a pair
        while (incubator_size > 0 && egg_size > 0) {
            EggPokemon egg = freeEggs.get(0);
            Result result = egg.incubate(freeIncubators.get(0));
            Logger.INSTANCE.Log(Logger.TYPE.EVENT, "Incubating egg (" + egg.getEggKmWalkedTarget() + ") " + result.toString());
            // Remove from list - update new counts
            freeIncubators.remove(0);
            incubator_size = freeIncubators.size();
            freeEggs.remove(0);
            egg_size = freeEggs.size();

        }
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
        if (maxpotions > 1) {
            System.out.println("Thorwing away " + maxpotions + " Hyper Potions..");
            bag.removeItem(ITEM_MAX_POTION, maxpotions);
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
        if (bagCount > (bagCount * 0.90)) {
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