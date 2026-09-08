package com.luminex_studios.lxac.checks.baritone;

import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.checks.Check;
import com.luminex_studios.lxac.data.PlayerData;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;

/**
 * RoboticA - Detects aimbot / robotic rotation patterns.
 *
 * Does NOT flag normal aggressive turning / flicking.
 * Flags:
 * 1) Many nearly-identical rotation deltas in a row (machine-like consistency)
 * 2) Extreme impossible snaps repeated in a very short window
 */
public class RoboticAChecker extends Check {

    // Single large flick is normal in PvP – only care about spam of huge snaps
    private static final float HUGE_SNAP_YAW = 140.0f;
    private static final float HUGE_SNAP_PITCH = 80.0f;
    private static final int HUGE_SNAP_COUNT = 4;
    private static final long HUGE_SNAP_WINDOW_MS = 800;

    // Identical micro deltas (aimbot smooth)
    private static final int IDENTICAL_STREAK = 25;
    private static final float DELTA_EPSILON = 0.0008f;

    public RoboticAChecker(LXAC plugin) {
        super(plugin, "RoboticA");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_ROTATION
                && event.getPacketType() != PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            return;
        }

        Player player = (Player) event.getPlayer();
        if (player == null || !player.isOnline()) return;

        try {
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            if (!flying.hasRotationChanged()) return;

            float yaw = flying.getLocation().getYaw();
            float pitch = flying.getLocation().getPitch();

            PlayerData data = getData(player);
            Float lastYaw = data.getCustomData("rob_last_yaw");
            Float lastPitch = data.getCustomData("rob_last_pitch");
            data.setCustomData("rob_last_yaw", yaw);
            data.setCustomData("rob_last_pitch", pitch);

            if (lastYaw == null || lastPitch == null) return;

            float deltaYaw = Math.abs(normalizeYaw(yaw - lastYaw));
            float deltaPitch = Math.abs(pitch - lastPitch);

            // --- 1) Repeated huge snaps (not a single flick) ---
            long now = System.currentTimeMillis();
            if (deltaYaw > HUGE_SNAP_YAW || deltaPitch > HUGE_SNAP_PITCH) {
                Integer count = data.getCustomData("rob_huge_count");
                Long windowStart = data.getCustomData("rob_huge_start");
                if (count == null) count = 0;
                if (windowStart == null || now - windowStart > HUGE_SNAP_WINDOW_MS) {
                    count = 0;
                    windowStart = now;
                }
                count++;
                data.setCustomData("rob_huge_count", count);
                data.setCustomData("rob_huge_start", windowStart);

                if (count >= HUGE_SNAP_COUNT) {
                    flag(player, String.format("hugeSnaps=%d yaw=%.1f pitch=%.1f", count, deltaYaw, deltaPitch));
                    data.setCustomData("rob_huge_count", 0);
                }
            }

            // --- 2) Near-identical consecutive deltas (robotic smooth aim) ---
            // Skip when almost no rotation or when clearly human large moves
            if (deltaYaw < 0.01f && deltaPitch < 0.01f) {
                data.setCustomData("rob_ident_streak", 0);
                return;
            }
            // Large human flicks break the identical streak
            if (deltaYaw > 15.0f || deltaPitch > 10.0f) {
                data.setCustomData("rob_ident_streak", 0);
                data.setCustomData("rob_prev_dy", null);
                data.setCustomData("rob_prev_dp", null);
                return;
            }

            Float prevDy = data.getCustomData("rob_prev_dy");
            Float prevDp = data.getCustomData("rob_prev_dp");
            data.setCustomData("rob_prev_dy", deltaYaw);
            data.setCustomData("rob_prev_dp", deltaPitch);

            if (prevDy == null || prevDp == null) return;

            boolean same = Math.abs(deltaYaw - prevDy) < DELTA_EPSILON
                    && Math.abs(deltaPitch - prevDp) < DELTA_EPSILON;

            Integer streak = data.getCustomData("rob_ident_streak");
            if (streak == null) streak = 0;

            if (same) {
                streak++;
                data.setCustomData("rob_ident_streak", streak);
                if (streak >= IDENTICAL_STREAK) {
                    flag(player, String.format("identicalDeltas streak=%d dy=%.4f", streak, deltaYaw));
                    data.setCustomData("rob_ident_streak", 0);
                }
            } else {
                data.setCustomData("rob_ident_streak", 0);
            }
        } catch (Exception ignored) {
        }
    }

    private float normalizeYaw(float yaw) {
        yaw %= 360f;
        if (yaw > 180f) yaw -= 360f;
        if (yaw < -180f) yaw += 360f;
        return yaw;
    }
}
