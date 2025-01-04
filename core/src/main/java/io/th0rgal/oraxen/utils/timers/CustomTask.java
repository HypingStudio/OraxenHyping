package io.th0rgal.oraxen.utils.timers;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import io.th0rgal.oraxen.OraxenPlugin;
import org.bukkit.entity.Player;

public abstract class CustomTask {

    private WrappedTask task;

    public WrappedTask runAtFixedRate(long delayTicks, long periodTicks) {
        task = OraxenPlugin.getScheduler().runTimer(this::run, delayTicks, periodTicks);
        return task;
    }

    public WrappedTask runAtFixedRate(Player player, int delay, int period) {
        task = OraxenPlugin.getScheduler().runAtEntityTimer(player, this::run, delay, period);
        return task;
    }

    public void cancel() {
        task.cancel();
    }

    public abstract void run();

    public WrappedTask runTimer(int delay, int period) {
        task = OraxenPlugin.getScheduler().runTimer(this::run, delay, period);
        return task;
    }

    public WrappedTask runAtFixedRateAsync(long delay, long period) {
        task = OraxenPlugin.getScheduler().runTimerAsync(this::run, delay, period);
        return task;
    }

}
