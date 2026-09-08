package com.luminex_studios.lxac.managers;

import com.luminex_studios.lxac.LXAC;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Handles flagging: notifies admins and sends to Velocity proxy if enabled.
 */
public class FlagManager {

    private final LXAC plugin;

    public FlagManager(LXAC plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when a player is flagged by a check.
     * Notifies online admins and, if velocity-bridge is enabled, sends data to the proxy.
     */
    public void flag(Player player, String checkName, int violations, int threshold, String extraInfo) {
        String prefix = plugin.getConfig().getString("settings.prefix", "&c[LXAC] &7");
        String message = LXAC.color(prefix + "&f" + player.getName() +
                " &7failed &c" + checkName +
                " &7(&f" + violations + "&7/&f" + threshold + "&7)" +
                (extraInfo != null && !extraInfo.isEmpty() ? " &8| &7" + extraInfo : ""));

        // Notify admins
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("lxac.admin")) {
                online.sendMessage(message);
            }
        }
        // Also log to console
        plugin.getLogger().info(player.getName() + " failed " + checkName + " (" + violations + "/" + threshold + ")"
                + (extraInfo != null ? " | " + extraInfo : ""));

        // If threshold reached -> punish (kick via proxy or locally)
        if (violations >= threshold) {
            punish(player, checkName);
        }
    }

    private void punish(Player player, String checkName) {
        if (plugin.isVelocityBridge()) {
            // Send to Velocity proxy for network-wide kick
            sendToProxy(player, checkName);
            // Do NOT kick locally – proxy will handle it
        } else {
            // Single-server mode: kick locally
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.kickPlayer(LXAC.color("&cUnfair Advantage"));
                }
            });
        }
        // Reset violations after punishment
        plugin.getViolationManager().resetViolations(player, checkName);
    }

    private void sendToProxy(Player player, String checkName) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("FLAG");
            out.writeUTF(player.getUniqueId().toString());
            out.writeUTF(player.getName());
            out.writeUTF(checkName);
            out.writeUTF(plugin.getServerName());
            out.writeUTF("Unfair Advantage"); // kick reason (hardcoded)

            player.sendPluginMessage(plugin, plugin.getChannel(), out.toByteArray());
            plugin.getLogger().info("Sent FLAG to Velocity for " + player.getName() + " from server " + plugin.getServerName());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send flag to Velocity: " + e.getMessage());
            // Fallback local kick
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.kickPlayer(LXAC.color("&cUnfair Advantage"));
                }
            });
        }
    }
}
