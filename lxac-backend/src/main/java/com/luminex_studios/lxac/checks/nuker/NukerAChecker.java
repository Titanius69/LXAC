package com.luminex_studios.lxac.checks.nuker;

import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.checks.Check;
import com.luminex_studios.lxac.data.PlayerData;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * NukerA - multi-break burst (hard blocks only).
 * 3 hard blocks in 180ms is not sequential human mining.
 */
public class NukerAChecker extends Check implements Listener {

    private static final int NEED = 3;
    private static final long WINDOW_MS = 180;
    private static final float MIN_H = 1.0f;

    public NukerAChecker(LXAC plugin) {
        super(plugin, "NukerA");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!isEnabled()) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (event.getBlock().getType().getHardness() < MIN_H) return;

        long now = System.currentTimeMillis();
        PlayerData data = getData(player);

        @SuppressWarnings("unchecked")
        List<Long> t = data.getCustomData("na_t");
        if (t == null) {
            t = new ArrayList<>();
            data.setCustomData("na_t", t);
        }
        t.removeIf(x -> now - x > WINDOW_MS);
        t.add(now);

        if (t.size() >= NEED) {
            flag(player, "burst=" + t.size() + "/" + WINDOW_MS + "ms");
            t.clear();
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
    }
}
