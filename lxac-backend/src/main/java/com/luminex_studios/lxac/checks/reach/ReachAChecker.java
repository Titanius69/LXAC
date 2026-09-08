package com.luminex_studios.lxac.checks.reach;

import com.luminex_studios.lxac.LXAC;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.luminex_studios.lxac.checks.Check;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * ReachA - Detects attacking entities from too far away.
 * Vanilla survival reach is ~3.0 blocks. Small buffer for latency.
 */
public class ReachAChecker extends Check {

    private static final double MAX_REACH_SURVIVAL = 3.4;  // raised buffer for latency
    private static final double MAX_REACH_CREATIVE = 5.5;

    public ReachAChecker(LXAC plugin) {
        super(plugin, "ReachA");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        Player player = (Player) event.getPlayer();
        if (player == null || !player.isOnline()) return;

        try {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

            int entityId = interact.getEntityId();
            Entity target = null;
            for (Entity e : player.getNearbyEntities(6, 6, 6)) {
                if (e.getEntityId() == entityId) {
                    target = e;
                    break;
                }
            }
            if (target == null) return;

            // Use eye to body center distance
            double distance = player.getEyeLocation().distance(
                    target.getLocation().add(0, target.getHeight() * 0.5, 0));

            double max = player.getGameMode().name().equals("CREATIVE")
                    ? MAX_REACH_CREATIVE : MAX_REACH_SURVIVAL;

            // Only flag clearly excessive reach
            if (distance > max) {
                flag(player, String.format("dist=%.2f max=%.2f", distance, max));
            }
        } catch (Exception ignored) {
        }
    }
}
