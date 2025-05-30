package io.th0rgal.oraxen.mechanics.provided.farming.oresmelting;

import org.bukkit.event.Listener;

public class OreSmeltingMechanicListener implements Listener {

    private final OreSmeltingMechanicFactory factory;

    public OreSmeltingMechanicListener(OreSmeltingMechanicFactory factory) {
        this.factory = factory;
    }

    // This listener is minimal for now since the main functionality
    // is recipe generation during plugin load, not runtime events
}
