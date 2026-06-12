package com.lavarising.v2;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LavaRisingPlugin extends JavaPlugin {
    private LavaConfig settings;
    private ArenaStore arenaStore;
    private ArenaService arenaService;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        settings = LavaConfig.load(getConfig());
        arenaStore = new ArenaStore(this);
        arenaStore.load();
        arenaService = new ArenaService(this, arenaStore);
        gameManager = new GameManager(this, arenaService, arenaStore);

        LavaCommand lavaCommand = new LavaCommand(this);
        PluginCommand command = getCommand("lava");
        if (command != null) {
            command.setExecutor(lavaCommand);
            command.setTabCompleter(lavaCommand);
        }

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (gameManager != null) {
                gameManager.keepWaitingPlayersInLobby();
            }
        }, 20L, 40L);

        logGame("Enabled LavaRising 2.0.0. Lobby=("
                + settings.lobby().x() + "," + settings.lobby().z()
                + ") radius=" + settings.lobby().radius()
                + ", minPlayers=" + settings.start().minPlayers() + ".");
    }

    @Override
    public void onDisable() {
        if (gameManager != null && !gameManager.isWaiting()) {
            gameManager.stopRound(false);
        }
    }

    public void reloadSettings() {
        reloadConfig();
        settings = LavaConfig.load(getConfig());
        logGame("Config reloaded.");
    }

    public LavaConfig settings() {
        return settings;
    }

    public ArenaService arena() {
        return arenaService;
    }

    public GameManager game() {
        return gameManager;
    }

    public ArenaStore arenaStore() {
        return arenaStore;
    }

    public void logGame(String message) {
        if (settings == null || settings.consoleLogging()) {
            getLogger().info("[LavaRising] " + message);
        }
    }
}
