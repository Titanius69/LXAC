package com.luminex_studios.lxac.checks.reach;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.checks.Check;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class ReachBChecker extends Check {

    private static final double MAX_REACH_SURVIVAL = 4.5;
    private static final double MAX_REACH_CREATIVE = 5.0;
    private static final double BUFFER = 0.15;

    public ReachBChecker(LXAC plugin) {
        super(plugin, "ReachB");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        Player player = (Player) event.getPlayer();

        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {

                WrapperPlayClientPlayerDigging digging =
                        new WrapperPlayClientPlayerDigging(event);

                // PacketEvents verziófüggő enum helyett ordinal/name ellenőrzés.
                if (digging.getAction() == null ||
                        !digging.getAction().name().equals("START_DIGGING")) {
                    return;
                }

                Vector3i pos = digging.getBlockPosition();

                checkBlockReach(
                        player,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()
                );

                return;
            }

            if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {

                WrapperPlayClientPlayerBlockPlacement placement =
                        new WrapperPlayClientPlayerBlockPlacement(event);

                Vector3i pos = placement.getBlockPosition();

                checkBlockReach(
                        player,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()
                );
            }

        } catch (Exception ignored) {
        }
    }

    private void checkBlockReach(
            Player player,
            int x,
            int y,
            int z
    ) {
        Block block = player.getWorld().getBlockAt(x, y, z);

        Location eye = player.getEyeLocation();

        double closestX = clamp(eye.getX(), x, x + 1.0);
        double closestY = clamp(eye.getY(), y, y + 1.0);
        double closestZ = clamp(eye.getZ(), z, z + 1.0);

        double dx = eye.getX() - closestX;
        double dy = eye.getY() - closestY;
        double dz = eye.getZ() - closestZ;

        double distance = Math.sqrt(
                dx * dx +
                        dy * dy +
                        dz * dz
        );

        double maxReach = player.getGameMode() == GameMode.CREATIVE
                ? MAX_REACH_CREATIVE
                : MAX_REACH_SURVIVAL;

        if (distance > maxReach + BUFFER) {
            flag(
                    player,
                    String.format(
                            "block=%d,%d,%d dist=%.2f max=%.2f",
                            x,
                            y,
                            z,
                            distance,
                            maxReach
                    )
            );
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}