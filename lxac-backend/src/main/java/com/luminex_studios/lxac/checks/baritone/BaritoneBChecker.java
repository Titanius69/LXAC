package com.luminex_studios.lxac.checks.baritone;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.checks.Check;
import com.luminex_studios.lxac.data.PlayerData;
import com.luminex_studios.lxac.util.RayLine;
import com.luminex_studios.lxac.util.RayUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects Baritone-style pathing by looking for characteristic angle
 * "spike" patterns and impossibly precise repeated rotation deltas
 * across a rolling window of moves.
 * <p>
 * This is a faithful, close-to-line-for-line port of AntiBaritoneX's
 * PatternCheck, by Kireiko-dev:
 * https://github.com/Kireiko-dev/AntiBaritoneX
 * <p>
 * The previous attempt at this check ran the same analysis on raw
 * packet-level rotation packets, which never reliably fired (packet
 * timing/order does not line up with Bukkit's processed move data the
 * way the original algorithm expects, and the packetevents position
 * accessor used there didn't behave as assumed). This version instead
 * hooks Bukkit's PlayerMoveEvent directly - exactly like the original -
 * so the detection math runs on the same kind of data it was designed
 * and tuned for. It only borrows LXAC's flag()/threshold/VL machinery
 * from {@link Check} instead of AntiBaritoneX's own Punish/VL system.
 */
public class BaritoneBChecker extends Check implements Listener {

    /**
     * Same buffer size as the original (25 moves per analysis window).
     */
    private static final int BUFFER_SIZE = 25;

    /**
     * Same "impossible" rotation-delta fingerprints as the original.
     * The original only matched these four exact scaleVal() outputs,
     * which is an extremely narrow band and rarely hit in practice.
     * Widened here to a small tolerance window around each fingerprint
     * so nearby "suspiciously round" deltas count too.
     */
    private static final double[] INVALID_VALUES = {
            9.0E-4, 6.0E-4, 7.0E-4
    };
    private static final double INVALID_VALUE_TOLERANCE = 2.0E-4;

    /**
     * Same angle-spike table patterns as the original.
     */
    private static final List<String> SPIKE_PATTERNS = List.of(
            "532", "542", "7432"
    );

    /**
     * The original required EVERY sample in the window to show yaw
     * movement and ZERO samples to show pitch movement before flagging.
     * That is unrealistically strict on real move data (a single tick
     * of incidental pitch movement, e.g. from stepping or looking at a
     * mined block, zeroes the whole window out) and almost never fires.
     * Use "almost all yaw, almost no pitch" ratios instead.
     */
    private static final double YAW_ONLY_MIN_RATIO = 0.88;
    private static final double PITCH_ONLY_MAX_RATIO = 0.12;

    public BaritoneBChecker(LXAC plugin) {
        super(plugin, "BaritoneB");
    }

    /**
     * This check does not use packet listening; detection runs on
     * PlayerMoveEvent (see {@link #onMove}). CheckManager still requires
     * every Check to implement PacketListener, so this is intentionally
     * a no-op.
     */
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Intentionally unused - see onMove(PlayerMoveEvent) instead.
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {

        if (!isEnabled()) {
            return;
        }

        Player player = event.getPlayer();

        if (event.getTo() == null) {
            return;
        }

        PlayerData data = getData(player);

        @SuppressWarnings("unchecked")
        List<PlayerMoveEvent> buffer =
                (List<PlayerMoveEvent>) data.getCustomData("baritoneb_buffer");

        if (buffer == null) {
            buffer = new ArrayList<>();
            data.setCustomData("baritoneb_buffer", buffer);
        }

        buffer.add(event);

        if (buffer.size() == 1) {
            plugin.getLogger().info("[BaritoneB-debug] " + player.getName() + " buffer started (onMove is firing)");
        }

        if (buffer.size() >= BUFFER_SIZE) {
            analyze(player, data, buffer);
            buffer.clear();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerData data = getData(event.getPlayer());
        data.setCustomData("baritoneb_buffer", null);
    }

    /**
     * Direct port of AntiBaritoneX's PatternCheck#analysis, adapted to
     * call LXAC's flag() instead of AntiBaritoneX's Punish.use().
     */
    private void analyze(Player player, PlayerData data, List<PlayerMoveEvent> moves) {

        RayLine oldRayLine = null;

        int cYaw = 0;
        int cPitch = 0;

        boolean foundInvalid = false;
        double invSens = 0;

        double oldDeltaYaw = 0;

        StringBuilder pattern = new StringBuilder();

        for (PlayerMoveEvent event : moves) {

            Location to = event.getTo();
            Location from = event.getFrom();

            if (to == null) {
                continue;
            }

            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();

            RayLine rayLine = new RayLine(dx, dz);

            if (oldRayLine == null) {
                oldRayLine = rayLine;
            }

            double deltaYaw = RayUtils.calculateRayLines(rayLine, oldRayLine);
            double deltaRotYaw = Math.abs(RayUtils.wrapYaw(to.getYaw()) - RayUtils.wrapYaw(from.getYaw()));
            double deltaRotPitch = Math.abs(to.getPitch() - from.getPitch());

            if (oldDeltaYaw == 0) {
                oldDeltaYaw = deltaYaw;
            }

            double spike = Math.round(Math.abs(deltaYaw - oldDeltaYaw));
            double rounded = RayUtils.scaleVal(deltaRotYaw, 4);

            pattern.append((int) spike);

            if (isNearInvalidValue(rounded) && deltaRotPitch < 0.000000001D) {
                invSens = rounded;
                foundInvalid = true;
            }

            cYaw += (deltaRotYaw > 0.000000001D) ? 1 : 0;
            cPitch += (deltaRotPitch > 0.000000001D) ? 1 : 0;

            oldRayLine = rayLine;
            oldDeltaYaw = deltaYaw;
        }

        int sampleCount = moves.size();
        double yawRatio = (double) cYaw / sampleCount;
        double pitchRatio = (double) cPitch / sampleCount;

        /*
         * Temporary diagnostic logging: prints the computed values for
         * every closed 25-move window regardless of whether it flags,
         * so real Baritone traffic can be observed directly instead of
         * guessing at thresholds. Safe to remove once tuned.
         */
        plugin.getLogger().info(String.format(
                "[BaritoneB-debug] %s window: yawRatio=%.2f pitchRatio=%.2f pattern=%s invalid=%s",
                player.getName(), yawRatio, pitchRatio, pattern, foundInvalid
        ));

        if (yawRatio >= YAW_ONLY_MIN_RATIO && pitchRatio <= PITCH_ONLY_MAX_RATIO) {
            flag(player, String.format("invalid delta (yaw=%.2f pitch=%.2f)", yawRatio, pitchRatio));
        }

        if (foundInvalid) {
            flag(player, "predicted " + invSens);
        }

        String finalPattern = pattern.toString();

        for (String spikePattern : SPIKE_PATTERNS) {
            if (finalPattern.contains(spikePattern)) {
                flag(player, "spike on " + BUFFER_SIZE + "x table #" + spikePattern);
            }
        }
    }

    private boolean isNearInvalidValue(double value) {
        for (double invalid : INVALID_VALUES) {
            if (Math.abs(value - invalid) <= INVALID_VALUE_TOLERANCE) {
                return true;
            }
        }
        return false;
    }
}
