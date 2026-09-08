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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * NukerB - area: 3+ unique hard blocks in 150ms.
 */
public class NukerBChecker extends Check implements Listener {

    private static final int NEED = 3;
    private static final long WINDOW_MS = 150;
    private static final float MIN_H = 1.0f;

    public NukerBChecker(LXAC plugin) {
        super(plugin, "NukerB");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!isEnabled()) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (event.getBlock().getType().getHardness() < MIN_H) return;

        long now = System.currentTimeMillis();
        String key = event.getBlock().getX() + "," + event.getBlock().getY() + "," + event.getBlock().getZ();
        PlayerData data = getData(player);

        @SuppressWarnings("unchecked")
        List<Object[]> e = data.getCustomData("nb_e");
        if (e == null) {
            e = new ArrayList<>();
            data.setCustomData("nb_e", e);
        }
        e.removeIf(o -> now - (Long) o[0] > WINDOW_MS);
        e.add(new Object[]{now, key});

        Set<String> u = new HashSet<>();
        for (Object[] o : e) u.add((String) o[1]);

        if (u.size() >= NEED) {
            flag(player, "area=" + u.size() + "/" + WINDOW_MS + "ms");
            e.clear();
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
    }
}
