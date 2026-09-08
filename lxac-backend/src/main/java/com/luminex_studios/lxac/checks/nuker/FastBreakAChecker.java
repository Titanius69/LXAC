package com.luminex_studios.lxac.checks.nuker;

import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.checks.Check;
import com.luminex_studios.lxac.data.PlayerData;
import com.luminex_studios.lxac.util.BreakSpeedUtil;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * FastBreakA – refined continuous single-block break time check.
 *
 * START_DIGGING-nél elmenti a blockot, toolt, haste-et, ground/liquid állapotot
 * és kiszámolja a vanilla-szerű expected break time-ot (BreakSpeedUtil).
 * FINISHED / BlockBreakEvent-nél újraszámolja a feltételeket (ha változott a tool/haste/movement),
 * összeveti az elapsed időt az expected-del, és ha kisebb → flag.
 *
 * Hard flag azonnal, soft flag folyamatosan (buffer) hogy egyfolytában jelezzen.
 */
public class FastBreakAChecker extends Check implements Listener {

    /** Kis hálózati/tick slack */
    private static final long SLACK_MS = 50L;

    /** Egyértelmű cheat – azonnal flag */
    private static final double HARD_RATIO = 0.53;

    /** Gyanús – bufferrel flagel (folyamatos jelzés) */
    private static final double SOFT_RATIO = 0.75;

    /** Soft buffer ennyi után flag */
    private static final float SOFT_NEED = 2.2f;

    /** Soft max, hogy ne nőjön a végtelenbe */
    private static final int SOFT_MAX = 12;

    public FastBreakAChecker(LXAC plugin) {
        super(plugin, "FastBreakA");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;

        Player player = (Player) event.getPlayer();
        if (player == null || !player.isOnline()) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        try {
            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
            PlayerData data = getData(player);
            long now = System.currentTimeMillis();

            if (dig.getAction() == DiggingAction.START_DIGGING) {
                Vector3i pos = dig.getBlockPosition();
                Block block = player.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
                if (block.getType().isAir()) return;

                BreakSpeedUtil.BreakEstimate est = BreakSpeedUtil.estimate(player, block);
                long expected = est.expectedMs;

                ItemStack hand = player.getInventory().getItemInMainHand();
                Material toolMat = (hand == null || hand.getType().isAir()) ? Material.AIR : hand.getType();

                data.setCustomData("fb_start", now);
                data.setCustomData("fb_expected", expected);
                data.setCustomData("fb_x", pos.getX());
                data.setCustomData("fb_y", pos.getY());
                data.setCustomData("fb_z", pos.getZ());
                data.setCustomData("fb_used", false);
                data.setCustomData("fb_tool", toolMat.name());
                data.setCustomData("fb_haste", est.haste);
                data.setCustomData("fb_eff", est.efficiency);
                data.setCustomData("fb_hardness", est.hardness);
                data.setCustomData("fb_onground", player.isOnGround());
                data.setCustomData("fb_liquid", player.getEyeLocation().getBlock().isLiquid());
                return;
            }

            if (dig.getAction() == DiggingAction.CANCELLED_DIGGING) {
                // Reset dig state – ne flageljen félbemaradt dig után
                data.setCustomData("fb_used", true);
                data.setCustomData("fb_start", null);
                return;
            }

            if (dig.getAction() == DiggingAction.FINISHED_DIGGING) {
                evaluate(player, data, now, dig.getBlockPosition());
            }
        } catch (Exception ignored) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!isEnabled()) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        PlayerData data = getData(player);
        evaluate(player, data, System.currentTimeMillis(),
                new Vector3i(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ()));
    }

    private void evaluate(Player player, PlayerData data, long now, Vector3i pos) {
        if (Boolean.TRUE.equals(data.getCustomData("fb_used"))) return;

        Long start = data.getCustomData("fb_start");
        Long storedExpected = data.getCustomData("fb_expected");
        Integer sx = data.getCustomData("fb_x");
        Integer sy = data.getCustomData("fb_y");
        Integer sz = data.getCustomData("fb_z");

        if (start == null || storedExpected == null) return;

        // Prefer matching the started block
        if (sx != null && sy != null && sz != null) {
            if (pos.getX() != sx || pos.getY() != sy || pos.getZ() != sz) {
                // Másik block – csak ha még a dig ablakon belül vagyunk, egyébként skip
                if (now - start > storedExpected + 600) return;
            }
        }

        Block block = player.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
        // Újraszámolás a finish pillanatában (tool / haste / movement változhatott)
        BreakSpeedUtil.BreakEstimate est = BreakSpeedUtil.estimate(player, block);
        long expected = Math.max(storedExpected, est.expectedMs); // a biztonságosabb (nagyobb) érték

        // Insta / nagyon soft blockok ignorálása
        if (expected < 120) {
            data.setCustomData("fb_used", true);
            return;
        }

        long elapsed = Math.max(0, now - start);
        double ratio = (elapsed + SLACK_MS) / (double) expected;

        data.setCustomData("fb_used", true);

        Integer soft = data.getCustomData("fb_soft");
        if (soft == null) soft = 0;

        String toolName = data.getCustomData("fb_tool");
        if (toolName == null) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            toolName = (hand == null || hand.getType().isAir()) ? "AIR" : hand.getType().name();
        }

        int hasteNow = 0;
        PotionEffect hasteFx = player.getPotionEffect(PotionEffectType.HASTE);
        if (hasteFx != null) hasteNow = hasteFx.getAmplifier() + 1;

        boolean onGround = player.isOnGround();
        boolean inLiquid = player.getEyeLocation().getBlock().isLiquid();

        String detail = String.format(
                "time=%dms expected=%dms ratio=%.2f tool=%s eff=%d haste=%d hard=%.1f ground=%s liquid=%s",
                elapsed, expected, ratio,
                toolName,
                est.efficiency,
                Math.max(est.haste, hasteNow),
                est.hardness,
                onGround,
                inLiquid
        );

        if (ratio < HARD_RATIO) {
            // Egyértelműen túl gyors → hard flag + soft reset
            data.setCustomData("fb_soft", 0);
            flag(player, "hard " + detail);
            return;
        }

        if (ratio < SOFT_RATIO) {
            // Gyanús → soft buffer, folyamatosan jelez
            soft = Math.min(SOFT_MAX, soft + 1);
            data.setCustomData("fb_soft", soft);
            if (soft >= SOFT_NEED) {
                flag(player, "soft " + detail + " n=" + soft);
                // Ne nullázza le teljesen, hogy egyfolytában tudjon flagelni
                data.setCustomData("fb_soft", Math.max(1, soft - 1));
            }
            return;
        }

        // Legit break – soft buffer lassan lecsökken
        if (soft > 0) {
            data.setCustomData("fb_soft", soft - 1);
        }
    }
}
