package com.luminex_studios.lxac.checks;

import com.luminex_studios.lxac.LXAC;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Basic FlightA check using PacketEvents.
 * Detects suspicious flying packets when the player is not allowed to fly.
 * This is a simple example – production checks need more sophisticated analysis
 * (buffer, ground status, velocity, elytra, vehicles, etc.).
 */
public class FlightAChecker extends Check {

    public FlightAChecker(LXAC plugin) {
        super(plugin, "FlightA");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_FLYING
                && event.getPacketType() != PacketType.Play.Client.PLAYER_POSITION
                && event.getPacketType() != PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION
                && event.getPacketType() != PacketType.Play.Client.PLAYER_ROTATION) {
            return;
        }

        User user = event.getUser();
        if (user == null) return;

        Player player = (Player) event.getPlayer();
        if (player == null || !player.isOnline()) return;

        // Ignore creative / spectator / allowed flight / flying with elytra / in vehicle / levitation
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || player.getAllowFlight()
                || player.isFlying()
                || player.isGliding()
                || player.isInsideVehicle()
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || player.isRiptiding()) {
            return;
        }

        // Only process flying packets that claim onGround=false while player is in air
        try {
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            boolean onGround = flying.isOnGround();

            // Simple heuristic: if client claims not on ground and player is actually high above ground
            if (!onGround) {
                if (player.getLocation().getY() > player.getWorld().getHighestBlockYAt(player.getLocation()) + 3) {
                    flag(player, "airborne without permission");
                }
            }
        } catch (Exception ignored) {
            // Packet wrapper can fail on some packet types – ignore
        }
    }
}
