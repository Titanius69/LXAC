package com.luminex_studios.lxac.managers;

import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Handles violation tracking and timed decay / full reset.
 */
public class ViolationManager {

    private final LXAC plugin;
    private BukkitTask decayTask;

    // Loaded from config
    private int decayIntervalSeconds = 10;
    private int decayAmount = 1;
    private int fullResetAfterSeconds = 60;
    private int maxViolations = 100;

    public ViolationManager(LXAC plugin) {
        this.plugin = plugin;
        loadConfig();
        startDecayTask();
    }

    public void loadConfig() {
        this.decayIntervalSeconds = plugin.getConfig().getInt("violations.decay-interval-seconds", 10);
        this.decayAmount = plugin.getConfig().getInt("violations.decay-amount", 1);
        this.fullResetAfterSeconds = plugin.getConfig().getInt("violations.full-reset-after-seconds", 60);
        this.maxViolations = plugin.getConfig().getInt("violations.max-violations", 100);
    }

    public void startDecayTask() {
        stopDecayTask();
        long ticks = decayIntervalSeconds * 20L;
        decayTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::runDecay, ticks, ticks);
    }

    public void stopDecayTask() {
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
    }

    private void runDecay() {
        long now = System.currentTimeMillis();
        long fullResetMs = fullResetAfterSeconds * 1000L;

        for (PlayerData data : plugin.getDataManager().getAllData().values()) {
            for (String checkName : data.getAllViolations().keySet().toArray(new String[0])) {
                int current = data.getViolations(checkName);
                if (current <= 0) continue;

                long lastFlag = data.getLastFlagTime(checkName);

                // Full reset if no flags for a long time
                if (fullResetAfterSeconds > 0 && (now - lastFlag) >= fullResetMs) {
                    data.resetViolations(checkName);
                    continue;
                }

                // Gradual decay
                if (decayAmount > 0) {
                    data.reduceViolations(checkName, decayAmount);
                }
            }
        }
    }

    /**
     * Adds one violation and returns the new total (capped).
     */
    public int addViolation(Player player, String checkName) {
        if (player.hasPermission("lxac.bypass")) {
            return 0;
        }
        PlayerData data = plugin.getDataManager().getData(player);
        int current = data.addViolation(checkName);
        if (current > maxViolations) {
            data.setViolations(checkName, maxViolations);
            return maxViolations;
        }
        return current;
    }

    public int getViolations(Player player, String checkName) {
        PlayerData data = plugin.getDataManager().getData(player);
        return data.getViolations(checkName);
    }

    public void resetViolations(Player player, String checkName) {
        PlayerData data = plugin.getDataManager().getData(player);
        data.resetViolations(checkName);
    }

    public void resetAll(Player player) {
        PlayerData data = plugin.getDataManager().getData(player);
        data.resetAllViolations();
    }

    public void clearAll() {
        for (PlayerData data : plugin.getDataManager().getAllData().values()) {
            data.resetAllViolations();
        }
    }

    public void reload() {
        loadConfig();
        startDecayTask();
    }
}
