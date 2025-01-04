package io.th0rgal.oraxen.mechanics.provided.cosmetic.aura.aura;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.mechanics.MechanicsManager;
import io.th0rgal.oraxen.mechanics.provided.cosmetic.aura.AuraMechanic;
import io.th0rgal.oraxen.utils.timers.CustomTask;
import org.bukkit.entity.Player;

public abstract class Aura {

    protected final AuraMechanic mechanic;
    private CustomTask runnable;

    protected Aura(AuraMechanic mechanic) {
        this.mechanic = mechanic;
    }

    CustomTask getRunnable() {
        return new CustomTask() {
            @Override
            public void run() {
                mechanic.players.forEach(Aura.this::spawnParticles);
            }
        };
    }

    protected abstract void spawnParticles(Player player);

    protected abstract long getDelay();

    public void start() {
        runnable = getRunnable();
        WrappedTask task = runnable.runAtFixedRateAsync(0L, getDelay());
        MechanicsManager.registerTask(mechanic.getFactory().getMechanicID(), task);
    }

    public void stop() {
        runnable.cancel();
    }


}
