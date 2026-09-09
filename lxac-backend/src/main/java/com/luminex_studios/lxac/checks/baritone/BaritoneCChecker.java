package com.luminex_studios.lxac.checks.baritone;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.checks.Check;
import com.luminex_studios.lxac.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * BaritoneC - "mine" command block-selection fingerprint.
 * <p>
 * Baritone's MineProcess always walks to whichever matching block is
 * cheapest to reach next (nearest / lowest path-cost first), then moves
 * on. Chained over many blocks this produces a route that is close to
 * the theoretical shortest path between the blocks actually mined:
 * almost no backtracking, almost no direction reversals, and a very
 * uniform step size. Real players mining ore veins/tunnels look at
 * blocks, glance around, occasionally step back, and vary their pace -
 * even a careful, efficient human strip-miner introduces small
 * deviations that a pure nearest-cost search does not.
 * <p>
 * This does NOT try to reimplement or embed Baritone's own pathing
 * code (it's a client-side mod built on client world/chunk classes
 * that don't exist server-side, and pulling it in would be a large,
 * fragile, per-MC-version dependency for no real benefit). Instead it
 * measures the resulting movement signature, purely from server-known
 * data (break locations + player position), the same way BaritoneA/B
 * do for timing/rotation.
 * <p>
 * Tuned hard for NO FALSE POSITIVES: every one of several independent
 * signals must hold, over a large window, repeated across several
 * consecutive windows before flagging (a single human-like deviation
 * anywhere resets the streak). This intentionally means it will miss
 * some real Baritone users (false negatives are acceptable here) in
 * exchange for not tagging legitimate strip miners.
 */
public class BaritoneCChecker extends Check implements Listener {

    /** Blocks mined per analysis window. */
    private static final int WINDOW_SIZE = 30;

    /** Ignore break events with no meaningful movement between them (e.g. reaching AFK-mining one spot). */
    private static final double MIN_STEP_DISTANCE = 0.6;

    /** Ignore obviously non-consecutive mining (huge teleport-like gap between breaks). */
    private static final double MAX_STEP_DISTANCE = 6.0;

    /**
     * path efficiency = straight-line(first,last) / sum(consecutive segment lengths).
     * A pure nearest-cost walk is close to 1.0. Real players, even efficient
     * strip miners, rarely sustain this over 30 consecutive blocks because
     * of look-around jitter, side-branch pauses, corners, etc.
     */
    private static final double MIN_PATH_EFFICIENCY = 0.985;

    /** Coefficient of variation (stddev/mean) of step distances. Robotic movement is unnaturally uniform. */
    private static final double MAX_STEP_CV = 0.10;

    /** A "reversal" is a step whose direction is >90 degrees from the previous step. Must be near zero. */
    private static final int MAX_REVERSALS = 0;

    /** Consecutive perfect windows required before flagging - any miss resets this to 0. */
    private static final int REQUIRED_WINDOWS = 6;

    public BaritoneCChecker(LXAC plugin) {
        super(plugin, "BaritoneC");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {

        if (!isEnabled()) {
            return;
        }

        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (player.hasPermission("lxac.bypass")) {
            return;
        }

        PlayerData data = getData(player);

        @SuppressWarnings("unchecked")
        List<Location> path = data.getCustomData("baritonec_path");

        if (path == null) {
            path = new ArrayList<>();
            data.setCustomData("baritonec_path", path);
        }

        Location loc = event.getBlock().getLocation().add(0.5, 0.5, 0.5);

        if (!path.isEmpty()) {
            Location last = path.get(path.size() - 1);

            if (!last.getWorld().equals(loc.getWorld())) {
                path.clear();
            } else {
                double dist = last.distance(loc);

                // Too small (mining the exact same spot repeatedly) or too
                // large (teleport / world change / unrelated break) - both
                // break the "continuous route" assumption, so restart.
                if (dist < MIN_STEP_DISTANCE || dist > MAX_STEP_DISTANCE) {
                    path.clear();
                }
            }
        }

        path.add(loc);

        if (path.size() < WINDOW_SIZE) {
            return;
        }

        boolean clean = analyzeWindow(path);

        Integer windows = data.getCustomData("baritonec_windows");
        if (windows == null) {
            windows = 0;
        }

        if (clean) {
            windows++;
        } else {
            windows = 0;
        }

        data.setCustomData("baritonec_windows", windows);

        if (windows >= REQUIRED_WINDOWS) {
            flag(player, "near-optimal mining route over " + (WINDOW_SIZE * REQUIRED_WINDOWS) + " blocks");
            data.setCustomData("baritonec_windows", 0);
        }

        path.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerData data = getData(event.getPlayer());
        data.setCustomData("baritonec_path", null);
        data.setCustomData("baritonec_windows", 0);
    }

    /**
     * Returns true only if EVERY signal in this window looks robotic.
     * Any single human-like trait fails the whole window.
     */
    private boolean analyzeWindow(List<Location> path) {

        int n = path.size();

        double totalPathLength = 0.0;
        List<Double> stepLengths = new ArrayList<>(n - 1);
        List<Vector> stepDirs = new ArrayList<>(n - 1);

        for (int i = 1; i < n; i++) {
            Vector step = path.get(i).toVector().subtract(path.get(i - 1).toVector());
            double len = step.length();

            stepLengths.add(len);
            stepDirs.add(len > 1.0E-6 ? step.clone().normalize() : new Vector(0, 0, 0));
            totalPathLength += len;
        }

        if (totalPathLength < 1.0E-6) {
            return false;
        }

        double straightLine = path.get(0).distance(path.get(n - 1));
        double pathEfficiency = straightLine / totalPathLength;

        if (pathEfficiency < MIN_PATH_EFFICIENCY) {
            return false;
        }

        double mean = 0.0;
        for (double len : stepLengths) {
            mean += len;
        }
        mean /= stepLengths.size();

        double variance = 0.0;
        for (double len : stepLengths) {
            double diff = len - mean;
            variance += diff * diff;
        }
        variance /= stepLengths.size();

        double stddev = Math.sqrt(variance);
        double cv = mean > 1.0E-6 ? stddev / mean : Double.MAX_VALUE;

        if (cv > MAX_STEP_CV) {
            return false;
        }

        int reversals = 0;
        for (int i = 1; i < stepDirs.size(); i++) {
            Vector a = stepDirs.get(i - 1);
            Vector b = stepDirs.get(i);

            if (a.lengthSquared() < 1.0E-6 || b.lengthSquared() < 1.0E-6) {
                continue;
            }

            double dot = a.dot(b);

            // dot < 0 means the two consecutive steps point more than 90
            // degrees apart from each other - a direction reversal.
            if (dot < 0) {
                reversals++;
            }
        }

        return reversals <= MAX_REVERSALS;
    }

    /**
     * Not used - detection runs on {@link #onBreak(BlockBreakEvent)}.
     * Required override since Check implements PacketListener.
     */
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
    }
}
