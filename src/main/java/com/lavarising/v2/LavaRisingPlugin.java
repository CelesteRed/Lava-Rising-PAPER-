package com.lavarising.v2;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LavaRisingPlugin extends JavaPlugin {
    private LavaConfig settings;
    private ArenaStore arenaStore;
    private LobbyWorldService lobbyWorldService;
    private ArenaService arenaService;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        migrateConfig();
        settings = LavaConfig.load(getConfig());
        arenaStore = new ArenaStore(this);
        arenaStore.load();
        lobbyWorldService = new LobbyWorldService(this);
        lobbyWorldService.ensureLobbyWorld();
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
        migrateConfig();
        settings = LavaConfig.load(getConfig());
        if (lobbyWorldService != null) {
            lobbyWorldService.ensureLobbyWorld();
        }
        logGame("Config reloaded.");
    }

    public LavaConfig settings() {
        return settings;
    }

    public ArenaService arena() {
        return arenaService;
    }

    public LobbyWorldService lobbyWorld() {
        return lobbyWorldService;
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

    private void migrateConfig() {
        int configVersion = getConfig().getInt("configVersion", 1);
        if (configVersion >= 3) {
            return;
        }

        String previousLobbyWorld = getConfig().getString("lobby.world", "world");
        if (!getConfig().contains("round.world")) {
            getConfig().set("round.world", previousLobbyWorld == null || previousLobbyWorld.isBlank()
                    ? "world"
                    : previousLobbyWorld);
        }

        getConfig().set("lobby.world", "lobby");
        setIfMissing("lobby.voidWorld", true);
        setIfMissing("lobby.lockDaylightCycle", true);
        setIfMissing("lobby.time", 6000L);
        setIfMissing("lobby.platform.y", 64);
        setIfMissing("lobby.platform.radius", 36);
        setIfMissing("lobby.platform.material", "SMOOTH_STONE");
        getConfig().set("configVersion", 3);
        saveConfig();
        getLogger().info("[LavaRising] Migrated config to version 3 with lobby world 'lobby' and game world '"
                + getConfig().getString("round.world", "world") + "'.");
    }

    private void setIfMissing(String path, Object value) {
        if (!getConfig().contains(path)) {
            getConfig().set(path, value);
        }
    }
}
