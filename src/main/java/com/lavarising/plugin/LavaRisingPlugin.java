package com.lavarising.plugin;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class LavaRisingPlugin extends JavaPlugin {
    private LavaRisingManager lavaManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.lavaManager = new LavaRisingManager(this);

        LavaRisingCommand command = new LavaRisingCommand(lavaManager);
        setCommand("lavarising", command);
        setCommand("lavastart", command);
        setCommand("lavastop", command);

        PluginCommand resetCommand = getCommand("reset");
        if (resetCommand != null) {
            resetCommand.setExecutor(new ResetCommand(lavaManager));
        }

        getServer().getPluginManager().registerEvents(new RespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftListener(lavaManager), this);
        getServer().getPluginManager().registerEvents(new WaitingListener(lavaManager), this);
        getServer().getPluginManager().registerEvents(new BuildHeightListener(lavaManager), this);

        getLogger().info("LavaRising plugin enabled for Paper API 26.1.2.");
    }

    private void setCommand(String name, LavaRisingCommand handler) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
    }

    @Override
    public void onDisable() {
        if (lavaManager != null) {
            lavaManager.stopLava();
        }
        getLogger().info("LavaRising plugin disabled.");
    }

    public LavaRisingManager getLavaManager() {
        return lavaManager;
    }
}
