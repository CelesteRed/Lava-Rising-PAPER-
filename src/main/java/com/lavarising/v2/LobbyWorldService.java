package com.lavarising.v2;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;

public final class LobbyWorldService {
    private final LavaRisingPlugin plugin;

    public LobbyWorldService(LavaRisingPlugin plugin) {
        this.plugin = plugin;
    }

    public World ensureLobbyWorld() {
        LavaConfig.Lobby lobby = plugin.settings().lobby();
        World world = Bukkit.getWorld(lobby.world());
        if (world == null) {
            WorldCreator creator = new WorldCreator(lobby.world())
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.FLAT)
                    .generateStructures(false);
            if (lobby.voidWorld()) {
                creator.generator(new VoidLobbyChunkGenerator());
            }
            world = creator.createWorld();
            plugin.logGame("Created lobby world: name=" + lobby.world()
                    + ", voidWorld=" + lobby.voidWorld() + ".");
        }
        if (world == null) {
            return null;
        }

        configureLobbyWorld(world);
        buildLobbyPlatform(world);
        return world;
    }

    public void configureLobbyWorld(World world) {
        LavaConfig.Lobby lobby = plugin.settings().lobby();
        world.setTime(lobby.time());
        world.setStorm(false);
        world.setThundering(false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setSpawnFlags(false, false);
        world.setPVP(false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, !lobby.lockDaylightCycle());
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setSpawnLocation((int) Math.floor(lobby.x()), lobby.platformY() + 1, (int) Math.floor(lobby.z()));
    }

    public void buildLobbyPlatform(World world) {
        LavaConfig.Lobby lobby = plugin.settings().lobby();
        int centerX = (int) Math.floor(lobby.x());
        int centerZ = (int) Math.floor(lobby.z());
        int radius = lobby.platformRadius();
        int y = lobby.platformY();
        Material material = lobby.platformMaterial();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() != material) {
                    block.setType(material, false);
                }
            }
        }

        int minY = Math.max(world.getMinHeight(), y + 1);
        int maxY = Math.min(world.getMaxHeight() - 1, y + 4);
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                for (int clearY = minY; clearY <= maxY; clearY++) {
                    Block block = world.getBlockAt(x, clearY, z);
                    if (!block.getType().isAir()) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }

        world.getChunkAt(centerX >> 4, centerZ >> 4).load(true);
        plugin.logGame("Lobby platform ready: world=" + world.getName()
                + ", center=" + centerX + "," + centerZ
                + ", y=" + y
                + ", radius=" + radius
                + ", material=" + material + ".");
    }
}
