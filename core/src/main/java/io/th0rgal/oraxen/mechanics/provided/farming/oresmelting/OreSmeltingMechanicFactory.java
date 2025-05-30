package io.th0rgal.oraxen.mechanics.provided.farming.oresmelting;

import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.api.events.OraxenItemsLoadedEvent;
import io.th0rgal.oraxen.mechanics.Mechanic;
import io.th0rgal.oraxen.mechanics.MechanicFactory;
import io.th0rgal.oraxen.mechanics.MechanicsManager;
import io.th0rgal.oraxen.utils.logs.Logs;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.Map;

public class OreSmeltingMechanicFactory extends MechanicFactory implements Listener {

    private static OreSmeltingMechanicFactory instance;

    public OreSmeltingMechanicFactory(ConfigurationSection section) {
        super(section);
        instance = this;
        MechanicsManager.registerListeners(OraxenPlugin.get(), getMechanicID(), new OreSmeltingMechanicListener(this), this);
    }

    @Override
    public Mechanic parse(ConfigurationSection itemMechanicConfiguration) {
        OreSmeltingMechanic mechanic = new OreSmeltingMechanic(this, itemMechanicConfiguration);
        addToImplemented(mechanic);

        // Don't generate recipes here - wait for OraxenItemsLoadedEvent

        return mechanic;
    }

    @EventHandler
    public void onOraxenItemsLoaded(OraxenItemsLoadedEvent event) {
        // Generate all ore smelting recipes after items are loaded
        generateAllOreSmeltingRecipes();
    }

    private void generateAllOreSmeltingRecipes() {
        Logs.logInfo("Generating ore smelting recipes...");
        int recipeCount = 0;

        for (String itemId : getItems()) {
            OreSmeltingMechanic mechanic = (OreSmeltingMechanic) getMechanic(itemId);
            if (mechanic != null && mechanic.hasLoots()) {
                recipeCount += generateRecipesForOre(mechanic);
            }
        }

        if (recipeCount > 0) {
            Logs.logSuccess("Generated " + recipeCount + " ore smelting recipes");
        }
    }

    private int generateRecipesForOre(OreSmeltingMechanic mechanic) {
        String oreItemId = mechanic.getItemID();
        int generatedCount = 0;

        // Get the ore item
        ItemStack oreItem = OraxenItems.getItemById(oreItemId).build();
        if (oreItem == null) {
            Logs.logError("Could not find ore item with ID: " + oreItemId);
            return 0;
        }

        // Generate recipes for each loot
        for (Map.Entry<String, Integer> lootEntry : mechanic.getLoots().entrySet()) {
            String lootItemId = lootEntry.getKey();
            int amount = lootEntry.getValue();

            // Get the result item
            ItemStack resultItem = OraxenItems.getItemById(lootItemId).build();
            if (resultItem == null) {
                Logs.logError("Could not find result item with ID: " + lootItemId + " for ore: " + oreItemId);
                continue;
            }

            // Set the amount
            resultItem.setAmount(amount);

            // Create furnace recipe
            if (generateFurnaceRecipe(oreItemId, lootItemId, oreItem, resultItem, mechanic)) {
                generatedCount++;
            }

            // Create blast furnace recipe
            if (generateBlastFurnaceRecipe(oreItemId, lootItemId, oreItem, resultItem, mechanic)) {
                generatedCount++;
            }
        }

        return generatedCount;
    }

    private boolean generateFurnaceRecipe(String oreItemId, String lootItemId, ItemStack oreItem, ItemStack resultItem, OreSmeltingMechanic mechanic) {
        try {
            NamespacedKey key = new NamespacedKey(OraxenPlugin.get(), "ore_smelting_furnace_" + oreItemId + "_to_" + lootItemId);

            // Check if recipe already exists
            if (Bukkit.getRecipe(key) != null) {
                Logs.logWarning("Furnace recipe already exists: " + key.getKey() + " - skipping");
                return false;
            }

            RecipeChoice.ExactChoice input = new RecipeChoice.ExactChoice(oreItem);
            FurnaceRecipe recipe = new FurnaceRecipe(key, resultItem, input, mechanic.getExperience(), mechanic.getFurnaceDuration());

            Bukkit.addRecipe(recipe);
            Logs.logSuccess("Generated furnace recipe: " + oreItemId + " -> " + lootItemId + " (x" + resultItem.getAmount() + ")");
            return true;
        } catch (Exception e) {
            Logs.logError("Failed to generate furnace recipe for " + oreItemId + " -> " + lootItemId + ": " + e.getMessage());
            return false;
        }
    }

    private boolean generateBlastFurnaceRecipe(String oreItemId, String lootItemId, ItemStack oreItem, ItemStack resultItem, OreSmeltingMechanic mechanic) {
        try {
            NamespacedKey key = new NamespacedKey(OraxenPlugin.get(), "ore_smelting_blast_" + oreItemId + "_to_" + lootItemId);

            // Check if recipe already exists
            if (Bukkit.getRecipe(key) != null) {
                Logs.logWarning("Blast furnace recipe already exists: " + key.getKey() + " - skipping");
                return false;
            }

            RecipeChoice.ExactChoice input = new RecipeChoice.ExactChoice(oreItem);
            BlastingRecipe recipe = new BlastingRecipe(key, resultItem, input, mechanic.getExperience(), mechanic.getBlastFurnaceDuration());

            Bukkit.addRecipe(recipe);
            Logs.logSuccess("Generated blast furnace recipe: " + oreItemId + " -> " + lootItemId + " (x" + resultItem.getAmount() + ")");
            return true;
        } catch (Exception e) {
            Logs.logError("Failed to generate blast furnace recipe for " + oreItemId + " -> " + lootItemId + ": " + e.getMessage());
            return false;
        }
    }

    public static OreSmeltingMechanicFactory getInstance() {
        return instance;
    }

    // Debug method to check if ore smelting mechanics are loaded
    public void debugOreSmeltingMechanics() {
        Logs.logInfo("=== Ore Smelting Debug Info ===");
        Logs.logInfo("Total ore smelting items: " + getItems().size());

        for (String itemId : getItems()) {
            OreSmeltingMechanic mechanic = (OreSmeltingMechanic) getMechanic(itemId);
            if (mechanic != null) {
                Logs.logInfo("Item: " + itemId);
                Logs.logInfo("  - Has loots: " + mechanic.hasLoots());
                Logs.logInfo("  - Loots: " + mechanic.getLoots());
                Logs.logInfo("  - Furnace duration: " + mechanic.getFurnaceDuration());
                Logs.logInfo("  - Blast furnace duration: " + mechanic.getBlastFurnaceDuration());
                Logs.logInfo("  - Experience: " + mechanic.getExperience());
            }
        }
        Logs.logInfo("=== End Debug Info ===");
    }
}
