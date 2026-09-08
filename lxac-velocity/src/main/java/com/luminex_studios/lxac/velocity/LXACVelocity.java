package com.luminex_studios.lxac.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

@Plugin(
        id = "lxac",
        name = "LXAC-Velocity",
        version = "1.0.0",
        description = "LXAC Velocity bridge for network-wide flags and kicks",
        authors = {"Luminex Studios"}
)
public class LXACVelocity {

    private final ProxyServer server;
    private final Logger logger;

    // Must match the channel in backend config
    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from("lxac:flag");

    @Inject
    public LXACVelocity(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(CHANNEL);
        logger.info("LXAC-Velocity enabled. Listening on channel: lxac:flag");
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }

        // Only accept from backend servers (not from players)
        if (!(event.getSource() instanceof com.velocitypowered.api.proxy.ServerConnection)) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
            String subChannel = in.readUTF();

            if (!"FLAG".equals(subChannel)) {
                return;
            }

            String uuidStr = in.readUTF();
            String playerName = in.readUTF();
            String checkName = in.readUTF();
            String serverName = in.readUTF();
            String reason = in.readUTF(); // "Unfair Advantage"

            UUID uuid = UUID.fromString(uuidStr);

            Optional<Player> optionalPlayer = server.getPlayer(uuid);
            if (optionalPlayer.isEmpty()) {
                logger.warn("Received FLAG for offline/unknown player: " + playerName + " (" + uuidStr + ")");
                return;
            }

            Player player = optionalPlayer.get();

            // Log with server origin
            logger.info("[LXAC] Player " + playerName + " failed " + checkName +
                    " on server '" + serverName + "'. Kicking with reason: " + reason);

            // Kick with hardcoded English message
            Component kickMessage = Component.text("Unfair Advantage", NamedTextColor.RED);
            player.disconnect(kickMessage);

        } catch (Exception e) {
            logger.error("Error processing LXAC flag message", e);
        }
    }
}
