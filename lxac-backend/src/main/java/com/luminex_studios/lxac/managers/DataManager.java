package com.luminex_studios.lxac.managers;

import com.luminex_studios.lxac.LXAC;
import com.luminex_studios.lxac.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages PlayerData instances.
 * Automatically creates data on join and cleans up on quit.
 */
public class DataManager implements Listener {

    private final LXAC plugin;
    private final Map<UUID, PlayerData> dataMap = new ConcurrentHashMap<>();

    public DataManager(LXAC plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public PlayerData getData(Player player) {
        return dataMap.computeIfAbsent(player.getUniqueId(), uuid -> new PlayerData(player));
    }

    public PlayerData getData(UUID uuid) {
        return dataMap.get(uuid);
    }

    public void removeData(UUID uuid) {
        dataMap.remove(uuid);
    }

    public Map<UUID, PlayerData> getAllData() {
        return dataMap;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        getData(event.getPlayer()); // ensure data exists
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeData(event.getPlayer().getUniqueId());
    }
}
