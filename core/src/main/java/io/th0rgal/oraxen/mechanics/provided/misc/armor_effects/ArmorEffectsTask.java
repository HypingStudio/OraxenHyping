package io.th0rgal.oraxen.mechanics.provided.misc.armor_effects;

import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.utils.timers.CustomTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ArmorEffectsTask extends CustomTask {

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            OraxenPlugin.getScheduler().runAtEntity(player, schedulerTaskInter -> {
                ArmorEffectsMechanic.addEffects(player);
            });
        }
    }
}
