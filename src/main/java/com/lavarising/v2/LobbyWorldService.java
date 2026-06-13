package com.lavarising.v2;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

public final class LobbyWorldService {
    private final LavaRisingPlugin plugin;

    public LobbyWorldService(LavaRisingPlugin plugin) {
        this.plugin = plugin;
    }

    public World ensureLobbyWorld() {
        LavaConfig.Lobby lobby = plugin.settings().lobby();
        World world = Bukkit.getWorld(lobby.world());
        if (world == null) {
            world = createLobbyWorld(lobby);
        }
        if (world == null) {
            plugin.getLogger().warning("[LavaRising] Lobby world '" + lobby.world()
                    + "' could not be created; the lobby will fall back to the arena world spawn.");
            return null;
        }

        // Pure void: no platform is generated. Build your own spawn and set it with /lava setlobby.
        configureLobbyWorld(world);
        return world;
    }

    private World createLobbyWorld(LavaConfig.Lobby lobby) {
        VoidLobbyChunkGenerator generator = new VoidLobbyChunkGenerator();
        // Preferred: a dedicated dimension key like cr_lava:lobby.
        try {
            NamespacedKey key = lobby.dimensionKey();
            World world = new WorldCreator(lobby.world(), key)
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.FLAT)
                    .generateStructures(false)
                    .generator(generator)
                    .createWorld();
            if (world != null) {
                plugin.logGame("Created lobby dimension: name=" + lobby.world() + ", key=" + key + ".");
                return world;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[LavaRising] Custom lobby dimension key '" + lobby.dimensionKey()
                    + "' was rejected (" + t.getMessage() + "); falling back to a standard world key.");
        }

        // Fallback: standard minecraft:<name> world key (also matches an existing lobby folder).
        try {
            World world = new WorldCreator(lobby.world())
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.FLAT)
                    .generateStructures(false)
                    .generator(generator)
                    .createWorld();
            if (world != null) {
                plugin.logGame("Created lobby world with default key: name=" + lobby.world() + ".");
            }
            return world;
        } catch (Throwable t) {
            plugin.getLogger().warning("[LavaRising] Failed to create lobby world '" + lobby.world()
                    + "': " + t);
            return null;
        }
    }

    public void configureLobbyWorld(World world) {
        LavaConfig.Lobby lobby = plugin.settings().lobby();
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setSpawnFlags(false, false);
        world.setPVP(false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setSpawnLocation((int) Math.floor(lobby.x()), (int) Math.floor(lobby.y()), (int) Math.floor(lobby.z()));
    }
}
