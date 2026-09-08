package com.luminex_studios.lxac.checks.nuker;

import com.luminex_studios.lxac.LXAC;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.luminex_studios.lxac.checks.Check;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * DigDebug - Logs all PLAYER_DIGGING packets for anticheat research.
 * Enable in config only while testing (e.g. against Doomsday).
 * Does NOT flag / punish – only prints to console and optional admin chat.
 */
public class DigDebugChecker extends Check {

    private long lastLogMs = 0;

    public DigDebugChecker(LXAC plugin) {
        super(plugin, "DigDebug");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!isEnabled()) return;
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;

        Player player = (Player) event.getPlayer();
        if (player == null || !player.isOnline()) return;

        try {
            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
            DiggingAction action = dig.getAction();
            Vector3i pos = dig.getBlockPosition();
            long now = System.currentTimeMillis();

            String line = String.format(
                    "[DigDebug] %s action=%s pos=%d,%d,%d face=%s t=%d",
                    player.getName(),
                    action.name(),
                    pos.getX(), pos.getY(), pos.getZ(),
                    dig.getBlockFace() != null ? dig.getBlockFace().name() : "?",
                    now
            );

            plugin.getLogger().info(line);

            // Also notify online admins (throttled slightly to reduce spam)
            if (now - lastLogMs > 0) {
                lastLogMs = now;
                String msg = LXAC.color(plugin.getConfig().getString("settings.prefix", "&c[LXAC] &7")
                        + "&8DigDebug &7" + player.getName() + " &f" + action.name()
                        + " &7@ &f" + pos.getX() + "," + pos.getY() + "," + pos.getZ());
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.hasPermission("lxac.admin")) {
                        online.sendMessage(msg);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
