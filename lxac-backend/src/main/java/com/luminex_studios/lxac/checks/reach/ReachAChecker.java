package com.luminex_studios.lxac.checks.reach;

import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.checks.Check;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class ReachAChecker extends Check {

    private static final double MAX_REACH_SURVIVAL = 3.21;
    private static final double MAX_REACH_CREATIVE = 4.5;

    /*
     * Packet -> Bukkit scheduling can introduce a small position difference.
     * Keep this conservative to avoid false positives.
     */
    private static final double BUFFER = 0.35;

    public ReachAChecker(LXAC plugin) {
        super(plugin, "ReachA");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }

        Player player = (Player) event.getPlayer();

        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            WrapperPlayClientInteractEntity interact =
                    new WrapperPlayClientInteractEntity(event);

            if (interact.getAction()
                    != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                return;
            }

            final int entityId = interact.getEntityId();

            /*
             * PacketEvents runs on Netty.
             * Bukkit entity/world operations must run on the main thread.
             */
            Bukkit.getScheduler().runTask(
                    plugin,
                    () -> checkReach(player, entityId)
            );

        } catch (Exception ignored) {
        }
    }

    private void checkReach(Player player, int entityId) {

        if (!player.isOnline()) {
            return;
        }

        Entity target = null;

        /*
         * Do not use a very small nearby radius.
         * A legitimate target can temporarily be slightly outside it
         * because the attack packet and Bukkit position can be out of sync.
         */
        for (Entity entity : player.getNearbyEntities(8.0, 8.0, 8.0)) {

            if (entity.getEntityId() == entityId) {
                target = entity;
                break;
            }
        }

        if (target == null || !target.isValid()) {
            return;
        }

        /*
         * IMPORTANT:
         *
         * Do not measure from the eye to the entity CENTER.
         *
         * The player can legitimately hit the closest edge of an entity.
         * Measuring to the center causes false positives, especially against
         * large mobs and players viewed from an angle.
         *
         * We calculate the closest point of the entity's approximate
         * bounding box to the player's eye.
         */

        double px = player.getEyeLocation().getX();
        double py = player.getEyeLocation().getY();
        double pz = player.getEyeLocation().getZ();

        double ex = target.getLocation().getX();
        double ey = target.getLocation().getY();
        double ez = target.getLocation().getZ();

        double width = target.getWidth();
        double height = target.getHeight();

        /*
         * Bukkit's entity location is approximately the center of the
         * horizontal bounding box.
         */
        double halfWidth = width * 0.5;

        double minX = ex - halfWidth;
        double maxX = ex + halfWidth;

        double minY = ey;
        double maxY = ey + height;

        double minZ = ez - halfWidth;
        double maxZ = ez + halfWidth;

        double closestX = clamp(px, minX, maxX);
        double closestY = clamp(py, minY, maxY);
        double closestZ = clamp(pz, minZ, maxZ);

        double dx = px - closestX;
        double dy = py - closestY;
        double dz = pz - closestZ;

        double distance = Math.sqrt(
                dx * dx +
                        dy * dy +
                        dz * dz
        );

        double maxReach =
                player.getGameMode() == GameMode.CREATIVE
                        ? MAX_REACH_CREATIVE
                        : MAX_REACH_SURVIVAL;

        /*
         * Only flag clearly impossible reach.
         *
         * The buffer is deliberately conservative because the Bukkit
         * position is sampled on the next server tick.
         */
        if (distance > maxReach + BUFFER) {

            flag(
                    player,
                    String.format(
                            "dist=%.2f max=%.2f",
                            distance,
                            maxReach
                    )
            );
        }
    }

    private double clamp(
            double value,
            double min,
            double max
    ) {
        return Math.max(min, Math.min(max, value));
    }
}