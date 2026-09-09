package com.luminex_studios.lxac.checks.baritone;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.checks.Check;
import com.luminex_studios.lxac.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;

public class RoboticAChecker extends Check {

    private static final int SAMPLE_SIZE = 30;

    /*
     * Extremely large repeated rotations.
     */
    private static final float SNAP_YAW = 135.0f;
    private static final float SNAP_PITCH = 70.0f;
    private static final int SNAP_REQUIRED = 3;
    private static final long SNAP_WINDOW = 1200L;

    /*
     * Rotation deltas repeated with extremely similar values.
     */
    private static final float SAME_EPSILON = 0.015f;
    private static final int SAME_REQUIRED = 10;

    /*
     * Detects very artificial rotation sequences.
     */
    private static final double LOW_VARIANCE_YAW = 0.025;
    private static final double LOW_VARIANCE_PITCH = 0.025;
    private static final int LOW_VARIANCE_REQUIRED = 3;

    /*
     * VL prevents one accidental pattern from immediately flagging.
     */
    private static final int FLAG_VL = 5;

    public RoboticAChecker(LXAC plugin) {
        super(plugin, "RoboticA");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {

        if (event.getPacketType() != PacketType.Play.Client.PLAYER_ROTATION
                && event.getPacketType()
                != PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            return;
        }

        Player player = (Player) event.getPlayer();

        if (player == null || !player.isOnline()) {
            return;
        }

        try {

            WrapperPlayClientPlayerFlying flying =
                    new WrapperPlayClientPlayerFlying(event);

            if (!flying.hasRotationChanged()) {
                return;
            }

            float yaw = flying.getLocation().getYaw();
            float pitch = flying.getLocation().getPitch();

            PlayerData data = getData(player);

            Float lastYaw =
                    data.getCustomData("robot_last_yaw");

            Float lastPitch =
                    data.getCustomData("robot_last_pitch");

            data.setCustomData(
                    "robot_last_yaw",
                    yaw
            );

            data.setCustomData(
                    "robot_last_pitch",
                    pitch
            );

            if (lastYaw == null || lastPitch == null) {
                return;
            }

            float deltaYaw =
                    Math.abs(normalizeYaw(yaw - lastYaw));

            float deltaPitch =
                    Math.abs(pitch - lastPitch);

            if (deltaYaw < 0.0001f
                    && deltaPitch < 0.0001f) {
                return;
            }

            /*
             * =========================================================
             * SNAP CHECK
             * =========================================================
             */

            long now = System.currentTimeMillis();

            if (deltaYaw >= SNAP_YAW
                    || deltaPitch >= SNAP_PITCH) {

                Integer snaps =
                        data.getCustomData("robot_snaps");

                Long start =
                        data.getCustomData("robot_snap_start");

                if (snaps == null) {
                    snaps = 0;
                }

                if (start == null
                        || now - start > SNAP_WINDOW) {

                    snaps = 0;
                    start = now;
                }

                snaps++;

                data.setCustomData(
                        "robot_snaps",
                        snaps
                );

                data.setCustomData(
                        "robot_snap_start",
                        start
                );

                if (snaps >= SNAP_REQUIRED) {

                    violation(
                            data,
                            player,
                            String.format(
                                    "repeated snap dy=%.2f dp=%.2f",
                                    deltaYaw,
                                    deltaPitch
                            )
                    );

                    data.setCustomData(
                            "robot_snaps",
                            0
                    );
                }
            }

            /*
             * =========================================================
             * IDENTICAL DELTA CHECK
             * =========================================================
             */

            Float previousYawDelta =
                    data.getCustomData("robot_previous_dy");

            Float previousPitchDelta =
                    data.getCustomData("robot_previous_dp");

            data.setCustomData(
                    "robot_previous_dy",
                    deltaYaw
            );

            data.setCustomData(
                    "robot_previous_dp",
                    deltaPitch
            );

            if (previousYawDelta != null
                    && previousPitchDelta != null) {

                boolean same =
                        Math.abs(
                                deltaYaw - previousYawDelta
                        ) <= SAME_EPSILON
                                &&
                                Math.abs(
                                        deltaPitch - previousPitchDelta
                                ) <= SAME_EPSILON;

                Integer streak =
                        data.getCustomData("robot_same_streak");

                if (streak == null) {
                    streak = 0;
                }

                if (same) {
                    streak++;
                } else {
                    streak = Math.max(
                            0,
                            streak - 2
                    );
                }

                data.setCustomData(
                        "robot_same_streak",
                        streak
                );

                if (streak >= SAME_REQUIRED) {

                    violation(
                            data,
                            player,
                            String.format(
                                    "repeated rotation dy=%.4f dp=%.4f streak=%d",
                                    deltaYaw,
                                    deltaPitch,
                                    streak
                            )
                    );

                    data.setCustomData(
                            "robot_same_streak",
                            0
                    );
                }
            }

            /*
             * =========================================================
             * LOW VARIANCE ROTATION
             * =========================================================
             */

            /*
             * Ignore huge human flicks for this part.
             */
            if (deltaYaw > 45.0f
                    || deltaPitch > 30.0f) {

                clearSamples(data);
                return;
            }

            @SuppressWarnings("unchecked")
            Deque<Float> yawSamples =
                    data.getCustomData("robot_yaw_samples");

            @SuppressWarnings("unchecked")
            Deque<Float> pitchSamples =
                    data.getCustomData("robot_pitch_samples");

            if (yawSamples == null) {
                yawSamples = new ArrayDeque<>();
                data.setCustomData(
                        "robot_yaw_samples",
                        yawSamples
                );
            }

            if (pitchSamples == null) {
                pitchSamples = new ArrayDeque<>();
                data.setCustomData(
                        "robot_pitch_samples",
                        pitchSamples
                );
            }

            yawSamples.addLast(deltaYaw);
            pitchSamples.addLast(deltaPitch);

            while (yawSamples.size() > SAMPLE_SIZE) {
                yawSamples.removeFirst();
            }

            while (pitchSamples.size() > SAMPLE_SIZE) {
                pitchSamples.removeFirst();
            }

            if (yawSamples.size() < SAMPLE_SIZE) {
                return;
            }

            double yawMean =
                    average(yawSamples);

            double pitchMean =
                    average(pitchSamples);

            /*
             * Do not detect a player who is basically standing still.
             */
            if (yawMean < 0.03
                    && pitchMean < 0.03) {

                clearSamples(data);
                return;
            }

            double yawStd =
                    standardDeviation(
                            yawSamples,
                            yawMean
                    );

            double pitchStd =
                    standardDeviation(
                            pitchSamples,
                            pitchMean
                    );

            boolean lowVariance =
                    yawStd <= LOW_VARIANCE_YAW
                            && pitchStd <= LOW_VARIANCE_PITCH;

            Integer lowVarianceStreak =
                    data.getCustomData(
                            "robot_lowvar_streak"
                    );

            if (lowVarianceStreak == null) {
                lowVarianceStreak = 0;
            }

            if (lowVariance) {
                lowVarianceStreak++;
            } else {
                lowVarianceStreak =
                        Math.max(
                                0,
                                lowVarianceStreak - 1
                        );
            }

            data.setCustomData(
                    "robot_lowvar_streak",
                    lowVarianceStreak
            );

            if (lowVarianceStreak >= LOW_VARIANCE_REQUIRED) {

                violation(
                        data,
                        player,
                        String.format(
                                "low rotation variance yaw=%.5f pitch=%.5f",
                                yawStd,
                                pitchStd
                        )
                );

                clearSamples(data);
            }

        } catch (Exception ignored) {
        }
    }

    private void violation(
            PlayerData data,
            Player player,
            String reason
    ) {

        Integer vl =
                data.getCustomData("robot_vl");

        if (vl == null) {
            vl = 0;
        }

        vl++;

        data.setCustomData(
                "robot_vl",
                vl
        );

        if (vl >= FLAG_VL) {

            flag(
                    player,
                    reason + " vl=" + vl
            );

            data.setCustomData(
                    "robot_vl",
                    Math.max(0, vl - 2)
            );
        }
    }

    private void clearSamples(PlayerData data) {

        @SuppressWarnings("unchecked")
        Deque<Float> yaw =
                data.getCustomData("robot_yaw_samples");

        @SuppressWarnings("unchecked")
        Deque<Float> pitch =
                data.getCustomData("robot_pitch_samples");

        if (yaw != null) {
            yaw.clear();
        }

        if (pitch != null) {
            pitch.clear();
        }

        data.setCustomData(
                "robot_lowvar_streak",
                0
        );
    }

    private double average(
            Deque<Float> values
    ) {

        if (values.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;

        for (float value : values) {
            total += value;
        }

        return total / values.size();
    }

    private double standardDeviation(
            Deque<Float> values,
            double mean
    ) {

        if (values.isEmpty()) {
            return 0.0;
        }

        double variance = 0.0;

        for (float value : values) {

            double difference =
                    value - mean;

            variance +=
                    difference * difference;
        }

        variance /= values.size();

        return Math.sqrt(variance);
    }

    private float normalizeYaw(float yaw) {

        yaw %= 360.0f;

        if (yaw > 180.0f) {
            yaw -= 360.0f;
        }

        if (yaw < -180.0f) {
            yaw += 360.0f;
        }

        return yaw;
    }
}