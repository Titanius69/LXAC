package com.luminex_studios.lxac.managers;

import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.checks.Check;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central registry for all checks.
 *
 * Usage (in your main plugin or a bootstrap class):
 *
 *   checkManager.register("FlightA", 30, new FlightAChecker(plugin));
 *   checkManager.register("SpeedA", 25, new SpeedAChecker(plugin));
 *
 * The threshold and enabled state can be overridden in config.yml under checks.<name>
 */
public class CheckManager {

    private final LXAC plugin;
    private final Map<String, Check> checks = new HashMap<>();
    private final Map<String, Integer> thresholds = new HashMap<>();
    private final Map<String, Boolean> enabled = new HashMap<>();

    public CheckManager(LXAC plugin) {
        this.plugin = plugin;
    }

    /**
     * Register a new check and automatically register it as a PacketEvents listener.
     *
     * @param name             Unique check name (must match config key)
     * @param defaultThreshold Default flags needed to punish (overridden by config)
     * @param check            Your Check implementation
     */
    public void register(String name, int defaultThreshold, Check check) {
        if (checks.containsKey(name)) {
            plugin.getLogger().warning("Check already registered: " + name);
            return;
        }

        checks.put(name, check);
        thresholds.put(name, defaultThreshold);
        enabled.put(name, true);

        // Load overrides from config
        applyConfig(name, defaultThreshold);

        // Register with PacketEvents
        PacketEvents.getAPI().getEventManager().registerListener(check, PacketListenerPriority.NORMAL);

        plugin.getLogger().info("Registered check: " + name +
                " (threshold=" + thresholds.get(name) + ", enabled=" + enabled.get(name) + ")");
    }

    private void applyConfig(String name, int defaultThreshold) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("checks." + name);
        if (section != null) {
            if (section.contains("threshold")) {
                thresholds.put(name, section.getInt("threshold", defaultThreshold));
            }
            if (section.contains("enabled")) {
                enabled.put(name, section.getBoolean("enabled", true));
            }
        }
    }

    public void reloadFromConfig() {
        for (String name : checks.keySet()) {
            applyConfig(name, thresholds.getOrDefault(name, 30));
        }
    }

    public Check getChecker(String name) {
        return checks.get(name);
    }

    public boolean isEnabled(String name) {
        return enabled.getOrDefault(name, false);
    }

    public int getThreshold(String name) {
        return thresholds.getOrDefault(name, 30);
    }

    public Set<String> getRegisteredChecks() {
        return Collections.unmodifiableSet(checks.keySet());
    }

    public void setEnabled(String name, boolean value) {
        enabled.put(name, value);
    }

    public void setThreshold(String name, int value) {
        thresholds.put(name, value);
    }
}
