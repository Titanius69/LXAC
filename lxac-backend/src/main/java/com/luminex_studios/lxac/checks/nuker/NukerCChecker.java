package com.luminex_studios.lxac.checks.nuker;

import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.checks.Check;
import com.luminex_studios.lxac.data.PlayerData;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * NukerC - nuker AIM: snap rotation then dig / rapid retarget between blocks.
 * This is the main check for Doomsday sequential nuker (no multi-break packets).
 */
public class NukerCChecker extends Check {

    private static final float MIN_SNAP = 8.0f;
    private static final long SNAP_DIG_MS = 150;
    private static final int FLAG_AT = 8;

    public NukerCChecker(LXAC plugin) {
        super(plugin, "NukerC");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        Player player = (Player) event.getPlayer();
        if (player == null || !player.isOnline()) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;

        try {
            PlayerData data = getData(player);
            long now = System.currentTimeMillis();

            if (event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION
                    || event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
                WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
                if (!flying.hasRotationChanged()) return;

                float yaw = flying.getLocation().getYaw();
                float pitch = flying.getLocation().getPitch();
                Float ly = data.getCustomData("nc_y");
                Float lp = data.getCustomData("nc_p");
                data.setCustomData("nc_y", yaw);
                data.setCustomData("nc_p", pitch);
                if (ly == null || lp == null) return;

                float snap = Math.max(Math.abs(norm(yaw - ly)), Math.abs(pitch - lp));
                if (snap >= MIN_SNAP) {
                    data.setCustomData("nc_snap_t", now);
                    data.setCustomData("nc_snap", snap);
                }
                return;
            }

            if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;

            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
            DiggingAction action = dig.getAction();
            Vector3i pos = dig.getBlockPosition();
            String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();

            int buf = data.getCustomData("nc_b") instanceof Integer ? (Integer) data.getCustomData("nc_b") : 0;

            if (action == DiggingAction.CANCELLED_DIGGING) {
                data.setCustomData("nc_c_t", now);
                data.setCustomData("nc_c_p", key);
                return;
            }

            if (action != DiggingAction.START_DIGGING) {
                if (action == DiggingAction.FINISHED_DIGGING && buf > 0) {
                    Long snapT = data.getCustomData("nc_snap_t");
                    if (snapT == null || now - snapT > 800) {
                        data.setCustomData("nc_b", buf - 1);
                    }
                }
                return;
            }

            Long cT = data.getCustomData("nc_c_t");
            String cP = data.getCustomData("nc_c_p");
            Long snapT = data.getCustomData("nc_snap_t");
            Float snap = data.getCustomData("nc_snap");
            String lastStart = data.getCustomData("nc_last");

            boolean retarget = cT != null && now - cT <= 50 && cP != null && !cP.equals(key);
            boolean snapDig = snapT != null && now - snapT <= SNAP_DIG_MS && snap != null && snap >= MIN_SNAP;
            boolean newTarget = lastStart != null && !lastStart.equals(key);

            Float yaw = data.getCustomData("nc_y");
            Float pitch = data.getCustomData("nc_p");
            boolean aimed = yaw != null && pitch != null && isAimed(player, yaw, pitch, pos);

            if (retarget && snapDig) {
                buf += 2;
            } else if (snapDig && newTarget && aimed) {
                buf += 2;
            } else if (retarget || (snapDig && newTarget)) {
                buf += 1;
            } else if (buf > 0) {
                buf -= 1;
            }

            data.setCustomData("nc_b", buf);
            data.setCustomData("nc_last", key);

            if (buf >= FLAG_AT) {
                flag(player, String.format("aim buf=%d retarget=%s snap=%.1f aimed=%s",
                        buf, retarget, snap != null ? snap : 0f, aimed));
                data.setCustomData("nc_b", 1);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isAimed(Player player, float yaw, float pitch, Vector3i pos) {
        try {
            double yawR = Math.toRadians(yaw);
            double pitchR = Math.toRadians(pitch);
            Vector dir = new Vector(
                    -Math.sin(yawR) * Math.cos(pitchR),
                    -Math.sin(pitchR),
                    Math.cos(yawR) * Math.cos(pitchR)
            ).normalize();
            Vector to = new Vector(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    .subtract(player.getEyeLocation().toVector());
            if (to.lengthSquared() < 1e-6) return true;
            to.normalize();
            double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dir.dot(to)))));
            return ang <= 30.0;
        } catch (Exception e) {
            return false;
        }
    }

    private float norm(float y) {
        y %= 360f;
        if (y > 180f) y -= 360f;
        if (y < -180f) y += 360f;
        return y;
    }
}
