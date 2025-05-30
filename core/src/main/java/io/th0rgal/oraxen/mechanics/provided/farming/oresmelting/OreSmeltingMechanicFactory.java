package io.th0rgal.oraxen.mechanics.provided.farming.oresmelting;

import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.mechanics.Mechanic;
import io.th0rgal.oraxen.mechanics.MechanicFactory;
import io.th0rgal.oraxen.mechanics.MechanicsManager;
import io.th0rgal.oraxen.utils.logs.Logs;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.Map;

public class OreSmeltingMechanicFactory extends MechanicFactory {

    private static OreSmeltingMechanicFactory instance;

    public OreSmeltingMechanicFactory(ConfigurationSection section) {
        super(section);
        instance = this;
        MechanicsManager.registerListeners(OraxenPlugin.get(), getMechanicID(), new OreSmeltingMechanicListener(this));
    }

    @Override
    public Mechanic parse(ConfigurationSection itemMechanicConfiguration) {
        OreSmeltingMechanic mechanic = new OreSmeltingMechanic(this, itemMechanicConfiguration);
        addToImplemented(mechanic);
        
        // Generate recipes for this ore
        generateRecipesForOre(mechanic);
        
        return mechanic;
    }

    private void generateRecipesForOre(OreSmeltingMechanic mechanic) {
        String oreItemId = mechanic.getItemID();
        
        // Get the ore item
        ItemStack oreItem = OraxenItems.getItemById(oreItemId).build();
        if (oreItem == null) {
            Logs.logError("Could not find ore item with ID: " + oreItemId);
            return;
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
            generateFurnaceRecipe(oreItemId, lootItemId, oreItem, resultItem, mechanic);
            
            // Create blast furnace recipe
            generateBlastFurnaceRecipe(oreItemId, lootItemId, oreItem, resultItem, mechanic);
        }
    }

    private void generateFurnaceRecipe(String oreItemId, String lootItemId, ItemStack oreItem, ItemStack resultItem, OreSmeltingMechanic mechanic) {
        try {
            NamespacedKey key = new NamespacedKey(OraxenPlugin.get(), "ore_smelting_furnace_" + oreItemId + "_to_" + lootItemId);
            RecipeChoice.ExactChoice input = new RecipeChoice.ExactChoice(oreItem);
            
            FurnaceRecipe recipe = new FurnaceRecipe(key, resultItem, input, mechanic.getExperience(), mechanic.getFurnaceDuration());
            
            Bukkit.addRecipe(recipe);
            Logs.logSuccess("Generated furnace recipe: " + oreItemId + " -> " + lootItemId + " (x" + resultItem.getAmount() + ")");
        } catch (Exception e) {
            Logs.logError("Failed to generate furnace recipe for " + oreItemId + " -> " + lootItemId + ": " + e.getMessage());
        }
    }

    private void generateBlastFurnaceRecipe(String oreItemId, String lootItemId, ItemStack oreItem, ItemStack resultItem, OreSmeltingMechanic mechanic) {
        try {
            NamespacedKey key = new NamespacedKey(OraxenPlugin.get(), "ore_smelting_blast_" + oreItemId + "_to_" + lootItemId);
            RecipeChoice.ExactChoice input = new RecipeChoice.ExactChoice(oreItem);
            
            BlastingRecipe recipe = new BlastingRecipe(key, resultItem, input, mechanic.getExperience(), mechanic.getBlastFurnaceDuration());
            
            Bukkit.addRecipe(recipe);
            Logs.logSuccess("Generated blast furnace recipe: " + oreItemId + " -> " + lootItemId + " (x" + resultItem.getAmount() + ")");
        } catch (Exception e) {
            Logs.logError("Failed to generate blast furnace recipe for " + oreItemId + " -> " + lootItemId + ": " + e.getMessage());
        }
    }

    public static OreSmeltingMechanicFactory getInstance() {
        return instance;
    }
}
