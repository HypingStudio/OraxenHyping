package io.th0rgal.oraxen.mechanics.provided.farming.oresmelting;

import io.th0rgal.oraxen.mechanics.Mechanic;
import io.th0rgal.oraxen.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public class OreSmeltingMechanic extends Mechanic {

    private final Map<String, Integer> loots;
    private final int furnaceDuration;
    private final int blastFurnaceDuration;
    private final float experience;

    public OreSmeltingMechanic(MechanicFactory mechanicFactory, ConfigurationSection section) {
        super(mechanicFactory, section);
        
        // Parse loots configuration
        this.loots = new HashMap<>();
        ConfigurationSection lootsSection = section.getConfigurationSection("loots");
        if (lootsSection != null) {
            for (String lootId : lootsSection.getKeys(false)) {
                int amount = lootsSection.getInt(lootId, 1);
                this.loots.put(lootId, amount);
            }
        }
        
        // Parse duration configuration
        ConfigurationSection durationSection = section.getConfigurationSection("duration");
        if (durationSection != null) {
            this.furnaceDuration = durationSection.getInt("furnace", 200);
            this.blastFurnaceDuration = durationSection.getInt("blast_furnace", 100);
        } else {
            this.furnaceDuration = 200;
            this.blastFurnaceDuration = 100;
        }
        
        // Parse experience (optional, defaults to 0.1)
        this.experience = (float) section.getDouble("experience", 0.1);
    }

    public Map<String, Integer> getLoots() {
        return loots;
    }

    public int getFurnaceDuration() {
        return furnaceDuration;
    }

    public int getBlastFurnaceDuration() {
        return blastFurnaceDuration;
    }

    public float getExperience() {
        return experience;
    }

    public boolean hasLoots() {
        return !loots.isEmpty();
    }
}
