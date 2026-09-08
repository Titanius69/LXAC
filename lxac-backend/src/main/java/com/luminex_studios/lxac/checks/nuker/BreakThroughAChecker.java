package com.luminex_studios.lxac.checks.nuker;

import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.checks.Check;
import com.luminex_studios.lxac.data.PlayerData;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * BreakThroughA - Only flags when digging a block that is clearly behind
 * a full opaque solid, with a high buffer to avoid angle/desync FPs.
 *
 * Also flags extreme dig distance (block reach).
 */
public class BreakThroughAChecker extends Check {

    private static final double MAX_DIG_DISTANCE = 6.0;
    private static final int BUFFER_FLAG = 6; // need many consistent hits

    public BreakThroughAChecker(LXAC plugin) {
        super(plugin, "BreakThroughA");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;

        Player player = (Player) event.getPlayer();
        if (player == null || !player.isOnline()) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;

        try {
            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
            if (dig.getAction() != DiggingAction.FINISHED_DIGGING
                    && dig.getAction() != DiggingAction.START_DIGGING) {
                return;
            }

            Vector3i pos = dig.getBlockPosition();
            Location blockCenter = new Location(player.getWorld(),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Location eye = player.getEyeLocation();
            double distance = eye.distance(blockCenter);

            PlayerData data = getData(player);
            Integer buffer = data.getCustomData("bt_buffer");
            if (buffer == null) buffer = 0;

            // 1) Extreme dig reach
            if (distance > MAX_DIG_DISTANCE) {
                buffer += 2;
                data.setCustomData("bt_buffer", buffer);
                if (buffer >= BUFFER_FLAG) {
                    flag(player, String.format("digReach=%.2f", distance));
                    data.setCustomData("bt_buffer", 2);
                }
                return;
            }

            // 2) Line-of-sight through full opaque block
            // Only on FINISHED to reduce noise
            if (dig.getAction() != DiggingAction.FINISHED_DIGGING) {
                return;
            }

            Vector dir = blockCenter.toVector().subtract(eye.toVector());
            if (dir.lengthSquared() < 0.25) {
                decay(data, buffer);
                return;
            }
            dir = dir.normalize();

            // Ray slightly shorter so we don't always hit the target itself
            double rayLen = Math.max(0.1, distance - 0.3);
            RayTraceResult result = player.getWorld().rayTraceBlocks(eye, dir, rayLen);

            if (result == null || result.getHitBlock() == null) {
                decay(data, buffer);
                return;
            }

            Block hit = result.getHitBlock();

            // If first hit is the target → clear LOS
            if (hit.getX() == pos.getX() && hit.getY() == pos.getY() && hit.getZ() == pos.getZ()) {
                decay(data, buffer);
                return;
            }

            // Must be a full opaque cube between player and target
            if (!isFullOpaqueCube(hit.getType())) {
                decay(data, buffer);
                return;
            }

            // Extra: target must actually exist / be solid (player is "breaking" something)
            Block target = player.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            if (target.getType().isAir()) {
                // Breaking air through a wall is very suspicious
                buffer += 2;
            } else {
                buffer += 1;
            }

            data.setCustomData("bt_buffer", buffer);
            if (buffer >= BUFFER_FLAG) {
                flag(player, "through=" + hit.getType().name() + " buf=" + buffer);
                data.setCustomData("bt_buffer", 2);
            }
        } catch (Exception ignored) {
        }
    }

    private void decay(PlayerData data, int buffer) {
        if (buffer > 0) {
            data.setCustomData("bt_buffer", Math.max(0, buffer - 1));
        }
    }

    private boolean isFullOpaqueCube(Material type) {
        if (!type.isSolid() || !type.isOccluding() || type.isAir()) return false;
        String n = type.name();
        // Exclude anything that is not a full cube or is transparent-ish
        return !(n.contains("GLASS") || n.contains("LEAVES") || n.contains("FENCE")
                || n.contains("WALL") || n.contains("BARS") || n.contains("PANE")
                || n.contains("DOOR") || n.contains("TRAPDOOR") || n.contains("SLAB")
                || n.contains("STAIR") || n.contains("CARPET") || n.contains("SNOW")
                || n.contains("SIGN") || n.contains("BANNER") || n.contains("TORCH")
                || n.contains("CHAIN") || n.contains("CHEST") || n.contains("ANVIL")
                || n.contains("HOPPER") || n.contains("CAULDRON") || n.contains("PISTON")
                || n.contains("FENCE_GATE") || n.contains("CANDLE")
                || type == Material.COBWEB || type == Material.SCAFFOLDING
                || type == Material.LADDER || type == Material.VINE
                || type == Material.DIRT_PATH || type == Material.FARMLAND
                || type == Material.SOUL_SAND || type == Material.SOUL_SOIL
                || type == Material.MUD || type == Material.HONEY_BLOCK);
    }
}
