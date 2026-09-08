package com.luminex_studios.lxac.data;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds per-player data for LXAC.
 * Extend this class later if you need custom buffers, timestamps, etc. for new checks.
 */
public class PlayerData {

    private final UUID uuid;
    private final String name;

    // checkName -> current violation level
    private final Map<String, Integer> violations = new ConcurrentHashMap<>();

    // checkName -> last time this check flagged the player (ms)
    private final Map<String, Long> lastFlagTime = new ConcurrentHashMap<>();

    // Generic storage for check-specific data (optional)
    private final Map<String, Object> customData = new ConcurrentHashMap<>();

    public PlayerData(Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public int getViolations(String checkName) {
        return violations.getOrDefault(checkName, 0);
    }

    public int addViolation(String checkName) {
        int current = getViolations(checkName) + 1;
        violations.put(checkName, current);
        lastFlagTime.put(checkName, System.currentTimeMillis());
        return current;
    }

    public void setViolations(String checkName, int amount) {
        if (amount <= 0) {
            violations.remove(checkName);
            lastFlagTime.remove(checkName);
        } else {
            violations.put(checkName, amount);
        }
    }

    public void reduceViolations(String checkName, int amount) {
        int current = getViolations(checkName);
        setViolations(checkName, Math.max(0, current - amount));
    }

    public void resetViolations(String checkName) {
        violations.remove(checkName);
        lastFlagTime.remove(checkName);
    }

    public void resetAllViolations() {
        violations.clear();
        lastFlagTime.clear();
    }

    public long getLastFlagTime(String checkName) {
        return lastFlagTime.getOrDefault(checkName, 0L);
    }

    public Map<String, Integer> getAllViolations() {
        return violations;
    }

    @SuppressWarnings("unchecked")
    public <T> T getCustomData(String key) {
        return (T) customData.get(key);
    }

    public void setCustomData(String key, Object value) {
        if (value == null) {
            customData.remove(key);
        } else {
            customData.put(key, value);
        }
    }
}
