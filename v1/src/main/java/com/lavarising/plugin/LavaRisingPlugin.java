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
        setCommand("start", command);
        setCommand("lavastart", command);
        setCommand("lavastop", command);
        setCommand("lavaspeedbypass", command);

        PluginCommand resetCommand = getCommand("reset");
        if (resetCommand != null) {
            resetCommand.setExecutor(new ResetCommand(lavaManager));
        }

        PluginCommand reviveCommand = getCommand("revive");
        if (reviveCommand != null) {
            ReviveCommand revive = new ReviveCommand(this);
            reviveCommand.setExecutor(revive);
            reviveCommand.setTabCompleter(revive);
        }

        PluginCommand setLobbyCommand = getCommand("setlobby");
        if (setLobbyCommand != null) {
            SetLobbyCommand setLobby = new SetLobbyCommand(lavaManager);
            setLobbyCommand.setExecutor(setLobby);
            setLobbyCommand.setTabCompleter(setLobby);
        }

        getServer().getPluginManager().registerEvents(new RespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftListener(lavaManager), this);
        getServer().getPluginManager().registerEvents(new WaitingListener(this), this);
        getServer().getPluginManager().registerEvents(new BuildHeightListener(lavaManager), this);
        getServer().getPluginManager().registerEvents(new LavaIntegrityListener(this), this);
        getServer().getPluginManager().registerEvents(new RoundCombatListener(this), this);

        getServer().getScheduler().runTaskTimer(this, () -> {
            if (!lavaManager.isInGame()) {
                lavaManager.keepWaitingPlayersInLobby();
            }
        }, 20L, 40L);

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
