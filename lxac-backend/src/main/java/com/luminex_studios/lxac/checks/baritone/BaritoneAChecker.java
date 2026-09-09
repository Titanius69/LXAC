package com.luminex_studios.lxac.checks.baritone;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.checks.Check;
import com.luminex_studios.lxac.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;

public class BaritoneAChecker extends Check {

    private static final int SAMPLE_SIZE = 20;

    private static final long MIN_INTERVAL_MS = 45L;
    private static final long MAX_INTERVAL_MS = 2000L;

    private static final double MAX_TIMING_STDDEV = 7.0;
    private static final double MAX_TIMING_RANGE = 20.0;

    private static final int REQUIRED_WINDOWS = 4;

    public BaritoneAChecker(LXAC plugin) {
        super(plugin, "BaritoneA");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {

        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) {
            return;
        }

        Player player = (Player) event.getPlayer();

        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            WrapperPlayClientPlayerDigging digging =
                    new WrapperPlayClientPlayerDigging(event);

            /*
             * START_DIGGING is the useful packet for mining timing.
             *
             * Do NOT mix START and FINISHED packets together.
             * Their timing depends heavily on block hardness and would
             * destroy the timing pattern we are trying to detect.
             */
            if (digging.getAction() != DiggingAction.START_DIGGING) {
                return;
            }

            PlayerData data = getData(player);

            long now = System.nanoTime();

            Long last =
                    data.getCustomData("baritone_last_start_ns");

            data.setCustomData(
                    "baritone_last_start_ns",
                    now
            );

            if (last == null) {
                return;
            }

            long delta =
                    (now - last) / 1_000_000L;

            if (delta < MIN_INTERVAL_MS || delta > MAX_INTERVAL_MS) {
                reset(data);
                return;
            }

            @SuppressWarnings("unchecked")
            Deque<Long> samples =
                    data.getCustomData("baritone_samples");

            if (samples == null) {
                samples = new ArrayDeque<>();
                data.setCustomData("baritone_samples", samples);
            }

            samples.addLast(delta);

            while (samples.size() > SAMPLE_SIZE) {
                samples.removeFirst();
            }

            if (samples.size() < SAMPLE_SIZE) {
                return;
            }

            double mean = 0.0;

            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;

            for (long value : samples) {
                mean += value;
                min = Math.min(min, value);
                max = Math.max(max, value);
            }

            mean /= samples.size();

            double variance = 0.0;

            for (long value : samples) {
                double difference = value - mean;
                variance += difference * difference;
            }

            variance /= samples.size();

            double stddev = Math.sqrt(variance);
            double range = max - min;

            /*
             * Baritone tends to generate an unusually stable sequence
             * of mining packets compared with normal player input.
             */
            boolean stable =
                    stddev <= MAX_TIMING_STDDEV
                            && range <= MAX_TIMING_RANGE;

            Integer windows =
                    data.getCustomData("baritone_stable_windows");

            if (windows == null) {
                windows = 0;
            }

            if (stable) {
                windows++;
            } else {
                windows = Math.max(0, windows - 1);
            }

            data.setCustomData(
                    "baritone_stable_windows",
                    windows
            );

            if (windows >= REQUIRED_WINDOWS) {

                flag(
                        player,
                        String.format(
                                "stable mining timing avg=%.1fms std=%.2f range=%.0f",
                                mean,
                                stddev,
                                range
                        )
                );

                reset(data);
            }

        } catch (Exception ignored) {
        }
    }

    private void reset(PlayerData data) {

        @SuppressWarnings("unchecked")
        Deque<Long> samples =
                data.getCustomData("baritone_samples");

        if (samples != null) {
            samples.clear();
        }

        data.setCustomData(
                "baritone_stable_windows",
                0
        );

        data.setCustomData(
                "baritone_last_start_ns",
                null
        );
    }
}