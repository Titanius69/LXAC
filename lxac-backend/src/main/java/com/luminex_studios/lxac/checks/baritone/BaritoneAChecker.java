package com.luminex_studios.lxac.checks.baritone;

import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.checks.Check;
import com.luminex_studios.lxac.data.PlayerData;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * BaritoneA - Basic detection of highly consistent robotic dig timing.
 */
public class BaritoneAChecker extends Check {

    private static final int SAMPLE_SIZE = 16;
    private static final double MAX_VARIANCE = 4.0;
    private static final double MAX_AVG_MS = 70;

    public BaritoneAChecker(LXAC plugin) {
        super(plugin, "BaritoneA");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;

        Player player = (Player) event.getPlayer();
        if (player == null || !player.isOnline()) return;

        try {
            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
            if (dig.getAction() != DiggingAction.START_DIGGING
                    && dig.getAction() != DiggingAction.FINISHED_DIGGING) {
                return;
            }

            PlayerData data = getData(player);
            long now = System.currentTimeMillis();

            Long last = data.getCustomData("baritone_last_dig");
            data.setCustomData("baritone_last_dig", now);
            if (last == null) return;

            long delta = now - last;
            if (delta < 20 || delta > 150) return;

            @SuppressWarnings("unchecked")
            List<Long> deltas = data.getCustomData("baritone_dig_deltas");
            if (deltas == null) {
                deltas = new ArrayList<>();
                data.setCustomData("baritone_dig_deltas", deltas);
            }

            deltas.add(delta);
            if (deltas.size() > SAMPLE_SIZE) {
                deltas.remove(0);
            }

            if (deltas.size() >= SAMPLE_SIZE) {
                double avg = deltas.stream().mapToLong(Long::longValue).average().orElse(0);
                double variance = 0;
                for (long d : deltas) {
                    variance += (d - avg) * (d - avg);
                }
                variance /= deltas.size();

                if (variance < MAX_VARIANCE && avg < MAX_AVG_MS) {
                    flag(player, String.format("dig variance=%.1f avg=%.0fms", variance, avg));
                    deltas.clear();
                }
            }
        } catch (Exception ignored) {
        }
    }
}
