package com.luminex_studios.lxac;

import com.luminex_studios.lxac.checks.FlightAChecker;
import com.luminex_studios.lxac.managers.CheckManager;
import com.luminex_studios.lxac.managers.DataManager;
import com.luminex_studios.lxac.managers.FlagManager;
import com.luminex_studios.lxac.managers.ViolationManager;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * LXAC - Main plugin class (full anticheat base)
 *
 * To add new checks later:
 * 1. Create a class extending Check (see FlightAChecker as example)
 * 2. Register it here in onEnable():
 *      checkManager.register("YourCheckName", 20, new YourChecker(this));
 * 3. Add the check to config.yml under "checks:"
 */
public class LXAC extends JavaPlugin implements PluginMessageListener {

    private static LXAC instance;

    private DataManager dataManager;
    private CheckManager checkManager;
    private ViolationManager violationManager;
    private FlagManager flagManager;

    private boolean velocityBridge;
    private String channel;
    private String serverName;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadConfigValues();

        // Core managers
        this.dataManager = new DataManager(this);
        this.violationManager = new ViolationManager(this);
        this.flagManager = new FlagManager(this);
        this.checkManager = new CheckManager(this);

        // ============================================================
        // REGISTER YOUR CHECKS HERE
        // Example (you can remove FlightA later and add your own):
        // ============================================================
        checkManager.register("FlightA", 30, new FlightAChecker(this));

        // Example of how you will add more later:
        // checkManager.register("SpeedA", 25, new SpeedAChecker(this));
        // checkManager.register("KillAuraA", 15, new KillAuraAChecker(this));

        PacketEvents.getAPI().init();

        // Plugin messaging for Velocity bridge
        if (velocityBridge) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, channel);
            getServer().getMessenger().registerIncomingPluginChannel(this, channel, this);
        }

        getLogger().info("LXAC enabled.");
        getLogger().info("Velocity bridge: " + velocityBridge);
        getLogger().info("Violation decay: every " + getConfig().getInt("violations.decay-interval-seconds") + "s");
    }

    @Override
    public void onDisable() {
        if (violationManager != null) {
            violationManager.stopDecayTask();
        }
        if (PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().terminate();
        }
        if (velocityBridge) {
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, channel);
            getServer().getMessenger().unregisterIncomingPluginChannel(this, channel);
        }
        getLogger().info("LXAC disabled.");
    }

    public void reloadConfigValues() {
        reloadConfig();
        this.velocityBridge = getConfig().getBoolean("settings.velocity-bridge", true);
        this.channel = getConfig().getString("settings.channel", "lxac:flag");
        this.serverName = getConfig().getString("settings.server-name", "");
        if (serverName == null || serverName.isEmpty()) {
            this.serverName = Bukkit.getServer().getName();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("lxac")) return false;
        if (!sender.hasPermission("lxac.admin")) {
            sender.sendMessage(color(getConfig().getString("settings.prefix") + "&cNo permission."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(color(getConfig().getString("settings.prefix") + "&eUsage: /lxac <reload|info|violations>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfigValues();
            checkManager.reloadFromConfig();
            violationManager.reload();
            sender.sendMessage(color(getConfig().getString("settings.prefix") + "&aConfig reloaded."));
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(color(getConfig().getString("settings.prefix") + "&e=== LXAC ==="));
            sender.sendMessage(color("&7Checks:"));
            for (String name : checkManager.getRegisteredChecks()) {
                boolean enabled = checkManager.isEnabled(name);
                int threshold = checkManager.getThreshold(name);
                sender.sendMessage(color("  &f" + name + " &7enabled=&f" + enabled + " &7threshold=&f" + threshold));
            }
            sender.sendMessage(color("&7Velocity bridge: &f" + velocityBridge));
            sender.sendMessage(color("&7Server name: &f" + serverName));
            sender.sendMessage(color("&7Decay interval: &f" + getConfig().getInt("violations.decay-interval-seconds") + "s"));
            sender.sendMessage(color("&7Full reset after: &f" + getConfig().getInt("violations.full-reset-after-seconds") + "s"));
            return true;
        }

        if (args[0].equalsIgnoreCase("violations")) {
            if (args.length < 2) {
                sender.sendMessage(color(getConfig().getString("settings.prefix") + "&eUsage: /lxac violations <player>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(color(getConfig().getString("settings.prefix") + "&cPlayer not found."));
                return true;
            }
            sender.sendMessage(color(getConfig().getString("settings.prefix") + "&eViolations for &f" + target.getName() + "&e:"));
            boolean any = false;
            for (String check : checkManager.getRegisteredChecks()) {
                int vl = violationManager.getViolations(target, check);
                if (vl > 0) {
                    sender.sendMessage(color("  &7" + check + ": &f" + vl + "&7/&f" + checkManager.getThreshold(check)));
                    any = true;
                }
            }
            if (!any) {
                sender.sendMessage(color("  &7None"));
            }
            return true;
        }

        sender.sendMessage(color(getConfig().getString("settings.prefix") + "&eUsage: /lxac <reload|info|violations>"));
        return true;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // Backend currently does not process incoming messages
    }

    // ==================== Getters ====================

    public static LXAC getInstance() {
        return instance;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public CheckManager getCheckManager() {
        return checkManager;
    }

    public ViolationManager getViolationManager() {
        return violationManager;
    }

    public FlagManager getFlagManager() {
        return flagManager;
    }

    public boolean isVelocityBridge() {
        return velocityBridge;
    }

    public String getChannel() {
        return channel;
    }

    public String getServerName() {
        return serverName;
    }

    public static String color(String msg) {
        return msg == null ? "" : msg.replace('&', '§');
    }
}
