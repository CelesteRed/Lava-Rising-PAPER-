package com.lavarising.v2;

import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LavaRisingPlugin extends JavaPlugin {
    private LavaConfig settings;
    private ArenaStore arenaStore;
    private LobbyWorldService lobbyWorldService;
    private GamemodeService gamemodeService;
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
        gamemodeService = new GamemodeService(this);
        gamemodeService.load();
        arenaService = new ArenaService(this, arenaStore);
        gameManager = new GameManager(this, arenaService, arenaStore);

        // Register the command and listeners first so they always work, even if the
        // optional lobby-dimension setup below runs into trouble.
        LavaCommand lavaCommand = new LavaCommand(this);
        PluginCommand command = getCommand("lava");
        if (command != null) {
            command.setExecutor(lavaCommand);
            command.setTabCompleter(lavaCommand);
        }
        PluginCommand startCommand = getCommand("start");
        if (startCommand != null) {
            startCommand.setExecutor(lavaCommand);
            startCommand.setTabCompleter(lavaCommand);
        }

        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        // World creation must never disable the plugin; do it last and swallow any failure.
        try {
            lobbyWorldService.ensureLobbyWorld();
        } catch (Throwable t) {
            getLogger().warning("[LavaRising] Lobby dimension setup failed; plugin stays enabled. " + t);
        }

        logGame("Enabled LavaRising 2.5.30. Lobby dimension=" + settings.lobby().dimensionKey()
                + ", minPlayers=" + settings.start().minPlayers() + ".");
    }

    @Override
    public void onDisable() {
        if (gameManager != null && !gameManager.isWaiting()) {
            gameManager.stopRound(false);
        }
        // Release any lingering async arena-search chunk tickets so a hot reload (where the
        // world stays loaded) can't orphan chunks pinned against this plugin instance.
        if (arenaService != null) {
            World world = arenaService.mainWorld();
            if (world != null) {
                world.removePluginChunkTickets(this);
            }
        }
    }

    public void reloadSettings() {
        reloadConfig();
        migrateConfig();
        settings = LavaConfig.load(getConfig());
        if (gamemodeService != null) {
            gamemodeService.load();
        }
        if (lobbyWorldService != null) {
            try {
                lobbyWorldService.ensureLobbyWorld();
            } catch (Throwable t) {
                getLogger().warning("[LavaRising] Lobby dimension setup failed during reload: " + t);
            }
        }
        logGame("Config reloaded.");
    }

    public LavaConfig settings() {
        return settings;
    }

    public GamemodeService gamemodes() {
        return gamemodeService;
    }

    public LobbyWorldService lobbyWorld() {
        return lobbyWorldService;
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

    private void migrateConfig() {
        int configVersion = getConfig().getInt("configVersion", 1);
        boolean changed = false;

        if (configVersion < 3) {
            String previousLobbyWorld = getConfig().getString("lobby.world", "world");
            if (!getConfig().contains("round.world")) {
                getConfig().set("round.world", previousLobbyWorld == null || previousLobbyWorld.isBlank()
                        ? "world"
                        : previousLobbyWorld);
            }
            changed = true;
        }

        if (configVersion < 4) {
            setIfMissing("round.pvpEnableY", 60);
            changed = true;
        }

        if (configVersion < 5) {
            // Interim spawn-point-only step; superseded by the dedicated lobby dimension below.
            getConfig().set("lobby.voidWorld", null);
            getConfig().set("lobby.lockDaylightCycle", null);
            getConfig().set("lobby.time", null);
            getConfig().set("lobby.radius", null);
            changed = true;
        }

        if (configVersion < 6) {
            // The lobby is a dedicated void dimension keyed cr_lava:lobby, separate from the arena world.
            getConfig().set("lobby.world", "lobby");
            setIfMissing("lobby.namespace", "cr_lava");
            setIfMissing("lobby.platform.y", 64);
            setIfMissing("lobby.platform.radius", 16);
            setIfMissing("lobby.platform.material", "SMOOTH_STONE");
            getConfig().set("lobby.configured", false);
            changed = true;
        }

        if (configVersion < 7) {
            // Tone down winner fireworks; only adjust servers still on the old aggressive defaults.
            if (getConfig().getInt("celebration.maxFireworks", 8) >= 8) {
                getConfig().set("celebration.maxFireworks", 3);
            }
            if (getConfig().getInt("celebration.fireworkIntervalTicks", 10) <= 10) {
                getConfig().set("celebration.fireworkIntervalTicks", 20);
            }
            changed = true;
        }

        if (configVersion < 8) {
            // Difficulty now follows PVP (peaceful off, easy on); only flip the old defaults.
            if ("EASY".equalsIgnoreCase(getConfig().getString("round.countdownDifficulty", "EASY"))) {
                getConfig().set("round.countdownDifficulty", "PEACEFUL");
            }
            if ("HARD".equalsIgnoreCase(getConfig().getString("round.surfaceDifficulty", "HARD"))) {
                getConfig().set("round.surfaceDifficulty", "EASY");
            }
            // Move legacy lava.speeds.* to round.phase.<1-5> so /lava set round.phase.# works.
            String[] legacyKeys = {"startToY0", "y0ToY60", "y60ToY100", "y100ToDeathmatch", "deathmatchToTop"};
            for (int i = 0; i < legacyKeys.length; i++) {
                String legacyPath = "lava.speeds." + legacyKeys[i];
                String phasePath = "round.phase." + (i + 1);
                if (getConfig().contains(legacyPath) && !getConfig().contains(phasePath)) {
                    getConfig().set(phasePath, getConfig().getDouble(legacyPath));
                }
            }
            getConfig().set("lava", null);
            changed = true;
        }

        if (configVersion < 9) {
            // Lobby is now a pure void dimension; the auto platform (and its config) is gone.
            getConfig().set("lobby.platform", null);
            changed = true;
        }

        if (changed) {
            getConfig().set("configVersion", 9);
            saveConfig();
            getLogger().info("[LavaRising] Migrated config to version 9; lobby is a pure void dimension"
                    + " (build your own spawn and run /lava setlobby).");
        }
    }

    private void setIfMissing(String path, Object value) {
        if (!getConfig().contains(path)) {
            getConfig().set(path, value);
        }
    }
}
