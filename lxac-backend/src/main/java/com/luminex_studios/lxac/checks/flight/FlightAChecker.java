package com.luminex_studios.lxac.checks.flight;

import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.checks.Check;
import com.luminex_studios.lxac.data.PlayerData;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * FlightA - Detects sustained illegal flight / hovering.
 * Does NOT flag normal jumping or falling.
 *
 * Logic:
 * - Tracks consecutive air packets where the player is not descending.
 * - Only flags after staying airborne + non-descending for a longer period.
 * - Resets immediately on ground / legitimate flight states.
 */
public class FlightAChecker extends Check {

    // How many consecutive suspicious air packets before flagging
    private static final int AIR_THRESHOLD = 28; // ~1.4s at 20 tps
    // Minimum height above solid ground to even consider (blocks)
    private static final double MIN_HEIGHT_ABOVE_GROUND = 1.6;

    public FlightAChecker(LXAC plugin) {
        super(plugin, "FlightA");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_FLYING
                && event.getPacketType() != PacketType.Play.Client.PLAYER_POSITION
                && event.getPacketType() != PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            return;
        }

        Player player = (Player) event.getPlayer();
        if (player == null || !player.isOnline()) return;

        // Legit flight / special states → ignore + reset buffer
        if (isExempt(player)) {
            resetAir(player);
            return;
        }

        try {
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            boolean onGround = flying.isOnGround();
            PlayerData data = getData(player);

            if (onGround || isNearGround(player)) {
                resetAir(player);
                data.setCustomData("flight_last_y", player.getLocation().getY());
                return;
            }

            // Airborne
            Double lastY = data.getCustomData("flight_last_y");
            double currentY = player.getLocation().getY();
            data.setCustomData("flight_last_y", currentY);

            if (lastY == null) {
                return;
            }

            double deltaY = currentY - lastY;

            // Falling (descending) → completely normal, reset
            if (deltaY < -0.08) {
                resetAir(player);
                return;
            }

            // Small upward movement from jump is OK for a short time.
            // We only accumulate when the player is hovering or ascending without ground.
            Integer airTicks = data.getCustomData("flight_air_ticks");
            if (airTicks == null) airTicks = 0;

            // Hovering (almost no vertical change) or ascending while already high
            boolean suspicious = deltaY > -0.03; // not really falling

            if (suspicious && isHighEnough(player)) {
                airTicks++;
                data.setCustomData("flight_air_ticks", airTicks);

                if (airTicks >= AIR_THRESHOLD) {
                    flag(player, "airTicks=" + airTicks + " deltaY=" + String.format("%.3f", deltaY));
                    // Soft reset so it doesn't spam every packet
                    data.setCustomData("flight_air_ticks", AIR_THRESHOLD / 2);
                }
            } else {
                // Mild decay instead of full reset when briefly not suspicious
                if (airTicks > 0) {
                    data.setCustomData("flight_air_ticks", Math.max(0, airTicks - 2));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isExempt(Player player) {
        return player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || player.getAllowFlight()
                || player.isFlying()
                || player.isGliding()
                || player.isInsideVehicle()
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || player.hasPotionEffect(PotionEffectType.JUMP_BOOST)
                || player.isRiptiding()
                || player.getVelocity().getY() > 0.4; // strong upward velocity (launch, etc.)
    }

    private void resetAir(Player player) {
        PlayerData data = getData(player);
        data.setCustomData("flight_air_ticks", 0);
    }

    private boolean isNearGround(Player player) {
        Location loc = player.getLocation();
        // Check a few blocks below
        for (int i = 0; i <= 2; i++) {
            Block b = loc.clone().subtract(0, i, 0).getBlock();
            if (b.getType().isSolid() || b.getType() == Material.WATER || b.getType() == Material.LAVA
                    || b.getType().name().contains("SLAB") || b.getType().name().contains("STAIR")
                    || b.getType().name().contains("FENCE") || b.getType().name().contains("WALL")
                    || b.getType().name().contains("CARPET") || b.getType().name().contains("SNOW")) {
                return true;
            }
        }
        return false;
    }

    private boolean isHighEnough(Player player) {
        Location loc = player.getLocation();
        // Simple ground distance check
        for (int i = 1; i <= 4; i++) {
            Block b = loc.clone().subtract(0, i, 0).getBlock();
            if (b.getType().isSolid()) {
                return i >= MIN_HEIGHT_ABOVE_GROUND;
            }
        }
        return true; // nothing solid below → high enough
    }
}
