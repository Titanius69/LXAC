package com.luminex_studios.lxac.checks;

import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.data.PlayerData;
import com.github.retrooper.packetevents.event.PacketListener;
import org.bukkit.entity.Player;

/**
 * Base class for all checks.
 * Extend this and implement your detection logic.
 * Then register it with CheckManager.register(...)
 */
public abstract class Check implements PacketListener {

    protected final LXAC plugin;
    protected final String name;

    public Check(LXAC plugin, String name) {
        this.plugin = plugin;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return plugin.getCheckManager().isEnabled(name);
    }

    public int getThreshold() {
        return plugin.getCheckManager().getThreshold(name);
    }

    /**
     * Call this when the check detects a violation.
     */
    protected void flag(Player player, String extraInfo) {
        if (!isEnabled()) return;
        if (player == null || !player.isOnline()) return;
        if (player.hasPermission("lxac.bypass")) return;

        int violations = plugin.getViolationManager().addViolation(player, name);
        int threshold = getThreshold();

        plugin.getFlagManager().flag(player, name, violations, threshold, extraInfo);
    }

    protected void flag(Player player) {
        flag(player, null);
    }

    /**
     * Helper to get the player's data object (useful for buffers, timestamps, etc.)
     */
    protected PlayerData getData(Player player) {
        return plugin.getDataManager().getData(player);
    }
}
