package com.lavarising.v2;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.StructureType;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

public final class ArenaService {
    private static final int SAFE_SURFACE_SEARCH_RADIUS = 8;
    private static final int MAX_SURFACE_SCAN_DEPTH = 12;
    private static final int MAX_SAMPLE_HEIGHT_DELTA = 18;

    private final LavaRisingPlugin plugin;
    private final ArenaStore arenaStore;
    private final Random random = new Random();

    public ArenaService(LavaRisingPlugin plugin, ArenaStore arenaStore) {
        this.plugin = plugin;
        this.arenaStore = arenaStore;
    }

    public World mainWorld() {
        String configuredWorld = plugin.settings().lobby().world();
        World world = Bukkit.getWorld(configuredWorld);
        if (world != null) {
            return world;
        }
        return Bukkit.getWorlds().stream()
                .filter(candidate -> candidate.getEnvironment() == Environment.NORMAL)
                .findFirst()
                .orElse(Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst());
    }

    public Location lobbyLocation() {
        World world = mainWorld();
        if (world == null) {
            return null;
        }

        LavaConfig.Lobby lobby = plugin.settings().lobby();
        Location configured = safeSurfaceLocation(world, lobby.x(), lobby.z());
        if (configured != null) {
            return configured;
        }

        Location spawn = world.getSpawnLocation();
        Location spawnSurface = safeSurfaceLocation(world, spawn.getX(), spawn.getZ());
        if (spawnSurface != null) {
            plugin.logGame("Lobby fallback: configured lobby was unsafe, using world spawn surface "
                    + formatLocation(spawnSurface) + ".");
            return spawnSurface;
        }

        Location emergency = emergencyAboveGround(world, lobby.x(), lobby.z());
        plugin.logGame("Lobby emergency fallback used at " + formatLocation(emergency) + ".");
        return emergency;
    }

    public boolean isInLobby(Location location) {
        Location lobby = lobbyLocation();
        if (lobby == null || location == null || location.getWorld() == null
                || !location.getWorld().equals(lobby.getWorld())) {
            return false;
        }
        int radius = plugin.settings().lobby().radius();
        return horizontalDistanceSquared(location.getX(), location.getZ(), lobby.getX(), lobby.getZ())
                <= (double) radius * radius;
    }

    public ArenaSelection selectArena() {
        World world = mainWorld();
        if (world == null) {
            return null;
        }

        LavaConfig.Round round = plugin.settings().round();
        int roll = random.nextInt(100);
        plugin.logGame("Arena selection: village chance=" + round.villageStartChance() + "%, roll=" + roll + ".");
        if (roll < round.villageStartChance()) {
            ArenaSelection village = selectVillageArena(world);
            if (village != null) {
                return village;
            }
            plugin.logGame("Arena selection: no valid village found, falling back to surface search.");
        }
        return selectSurfaceArena(world);
    }

    public List<Location> participantSpawns(World world, ArenaCenter center, int count) {
        double spawnRadius = Math.max(4.0D, Math.min(16.0D, plugin.settings().round().arenaDiameter() / 8.0D));
        Location centerSurface = safeSurfaceLocation(world, center.x() + 0.5D, center.z() + 0.5D);
        if (centerSurface == null) {
            centerSurface = emergencyAboveGround(world, center.x() + 0.5D, center.z() + 0.5D);
        }

        Location finalCenterSurface = centerSurface;
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> {
                    double angle = (Math.PI * 2.0D * index) / Math.max(1, count);
                    double x = center.x() + 0.5D + Math.cos(angle) * spawnRadius;
                    double z = center.z() + 0.5D + Math.sin(angle) * spawnRadius;
                    Location spawn = safeSurfaceLocation(world, x, z);
                    if (spawn == null) {
                        spawn = finalCenterSurface.clone();
                    }
                    spawn.setYaw((float) Math.toDegrees(Math.atan2(center.z() + 0.5D - spawn.getZ(),
                            center.x() + 0.5D - spawn.getX())) - 90.0F);
                    spawn.setPitch(0.0F);
                    return spawn;
                })
                .toList();
    }

    public Set<ChunkKey> forceLoadArena(World world, ArenaCenter center) {
        Set<ChunkKey> loaded = new HashSet<>();
        if (!plugin.settings().performance().forceLoadArenaChunks()) {
            return loaded;
        }

        int halfDiameter = plugin.settings().round().arenaDiameter() / 2;
        int radiusByArena = Math.max(1, (int) Math.ceil((halfDiameter + 16) / 16.0D));
        int radius = Math.max(radiusByArena, plugin.settings().performance().forceLoadedChunkRadius());
        int centerChunkX = center.x() >> 4;
        int centerChunkZ = center.z() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                world.getChunkAt(chunkX, chunkZ).load(true);
                world.setChunkForceLoaded(chunkX, chunkZ, true);
                loaded.add(new ChunkKey(world.getName(), chunkX, chunkZ));
            }
        }
        plugin.logGame("Force-loaded arena chunks: radius=" + radius + ", chunks=" + loaded.size() + ".");
        return loaded;
    }

    public void releaseForceLoadedChunks(Set<ChunkKey> chunkKeys) {
        for (ChunkKey key : chunkKeys) {
            World world = Bukkit.getWorld(key.worldName());
            if (world != null && world.isChunkForceLoaded(key.x(), key.z())) {
                world.setChunkForceLoaded(key.x(), key.z(), false);
            }
        }
        if (!chunkKeys.isEmpty()) {
            plugin.logGame("Released force-loaded arena chunks: chunks=" + chunkKeys.size() + ".");
        }
        chunkKeys.clear();
    }

    public Location safeSurfaceLocation(World world, double x, double z) {
        Location exact = safeSurfaceLocationAt(world, x, z);
        if (exact != null) {
            return exact;
        }

        int baseX = (int) Math.floor(x);
        int baseZ = (int) Math.floor(z);
        for (int radius = 1; radius <= SAFE_SURFACE_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    Location nearby = safeSurfaceLocationAt(world, baseX + dx + 0.5D, baseZ + dz + 0.5D);
                    if (nearby != null) {
                        return nearby;
                    }
                }
            }
        }
        return null;
    }

    public int safeGroundY(World world, int x, int z) {
        int highest = surfaceTopY(world, x, z);
        int minY = Math.max(plugin.settings().round().minSpawnY() - 1, highest - MAX_SURFACE_SCAN_DEPTH);
        for (int y = Math.min(highest, world.getMaxHeight() - 3); y >= minY; y--) {
            Block ground = world.getBlockAt(x, y, z);
            if (!isSafeSpawnSurface(ground.getType())) {
                continue;
            }
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);
            if (feet.isPassable() && head.isPassable()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    public Location emergencyAboveGround(World world, double x, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int y = Math.max(plugin.settings().round().minSpawnY() + 1, surfaceTopY(world, blockX, blockZ) + 1);
        y = Math.min(y, world.getMaxHeight() - 2);
        return new Location(world, x, y, z);
    }

    private ArenaSelection selectVillageArena(World world) {
        LavaConfig.ArenaSelectionSettings settings = plugin.settings().arenaSelection();
        if (!world.canGenerateStructures() || settings.villageSearchAttempts() <= 0) {
            plugin.logGame("Village search skipped: structures disabled or attempts set to 0.");
            return null;
        }

        for (int attempt = 0; attempt < settings.villageSearchAttempts(); attempt++) {
            Location origin = randomSearchOrigin(world);
            Location village = world.locateNearestStructure(
                    origin,
                    StructureType.VILLAGE,
                    settings.villageSearchRadiusChunks(),
                    false);
            if (village == null) {
                plugin.logGame("Village search " + (attempt + 1) + ": none found.");
                continue;
            }

            ArenaCenter center = new ArenaCenter(village.getBlockX(), village.getBlockZ());
            ArenaSelection selection = validateArenaCenter(world, center, "village");
            if (selection != null) {
                plugin.logGame("Village arena accepted: center=" + center.x() + "," + center.z()
                        + ", biome=" + selection.biome().name() + ".");
                return selection;
            }
            plugin.logGame("Village arena rejected: center=" + center.x() + "," + center.z() + ".");
        }
        return null;
    }

    private ArenaSelection selectSurfaceArena(World world) {
        LavaConfig.ArenaSelectionSettings settings = plugin.settings().arenaSelection();
        for (int attempt = 0; attempt < settings.maxAttempts(); attempt++) {
            Location origin = randomSearchOrigin(world);
            ArenaCenter center = new ArenaCenter(origin.getBlockX(), origin.getBlockZ());
            ArenaSelection selection = validateArenaCenter(world, center, "surface");
            if (selection != null) {
                plugin.logGame("Surface arena accepted: center=" + center.x() + "," + center.z()
                        + ", biome=" + selection.biome().name()
                        + ", attempt=" + (attempt + 1) + "/" + settings.maxAttempts() + ".");
                return selection;
            }
        }
        plugin.logGame("Surface arena search failed after " + settings.maxAttempts() + " attempts.");
        return null;
    }

    private Location randomSearchOrigin(World world) {
        LavaConfig.ArenaSelectionSettings settings = plugin.settings().arenaSelection();
        LavaConfig.Lobby lobby = plugin.settings().lobby();
        int radiusRange = Math.max(0, settings.searchMaxRadius() - settings.searchMinRadius());
        int radius = settings.searchMinRadius() + random.nextInt(radiusRange + 1);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int x = (int) Math.floor(lobby.x() + Math.cos(angle) * radius);
        int z = (int) Math.floor(lobby.z() + Math.sin(angle) * radius);
        return new Location(world, x + 0.5D, world.getMinHeight() + 5, z + 0.5D);
    }

    private ArenaSelection validateArenaCenter(World world, ArenaCenter center, String source) {
        if (tooCloseToLobby(center) || tooCloseToUsedArena(center)) {
            return null;
        }

        Location surface = safeSurfaceLocation(world, center.x() + 0.5D, center.z() + 0.5D);
        if (surface == null) {
            return null;
        }

        Biome biome = world.getBlockAt(surface.getBlockX(), surface.getBlockY() - 1, surface.getBlockZ()).getBiome();
        if (!plugin.settings().arenaSelection().biomeWhitelist().contains(biome)) {
            return null;
        }
        if (!playableArenaSurface(world, center, surface.getBlockY())) {
            return null;
        }
        return new ArenaSelection(center, biome, source);
    }

    private boolean playableArenaSurface(World world, ArenaCenter center, int centerY) {
        int sampleDistance = Math.max(8, Math.min(48, plugin.settings().round().arenaDiameter() / 3));
        int[][] samples = {
                {0, 0},
                {sampleDistance, 0},
                {-sampleDistance, 0},
                {0, sampleDistance},
                {0, -sampleDistance}
        };

        for (int[] sample : samples) {
            Location surface = safeSurfaceLocation(world, center.x() + sample[0] + 0.5D,
                    center.z() + sample[1] + 0.5D);
            if (surface == null || Math.abs(surface.getBlockY() - centerY) > MAX_SAMPLE_HEIGHT_DELTA) {
                return false;
            }
            Biome biome = world.getBlockAt(surface.getBlockX(), surface.getBlockY() - 1, surface.getBlockZ())
                    .getBiome();
            if (!plugin.settings().arenaSelection().biomeWhitelist().contains(biome)) {
                return false;
            }
        }
        return hasTreeNearby(world, center, centerY);
    }

    private boolean hasTreeNearby(World world, ArenaCenter center, int centerY) {
        int treeRadius = plugin.settings().arenaSelection().treeCheckRadius();
        if (treeRadius <= 0) {
            return true;
        }

        for (int dx = -treeRadius; dx <= treeRadius; dx += 4) {
            for (int dz = -treeRadius; dz <= treeRadius; dz += 4) {
                if ((dx * dx) + (dz * dz) > treeRadius * treeRadius) {
                    continue;
                }
                int x = center.x() + dx;
                int z = center.z() + dz;
                int y = safeGroundY(world, x, z);
                if (y == Integer.MIN_VALUE) {
                    y = centerY;
                }
                int minY = Math.max(world.getMinHeight(), y - 6);
                int maxY = Math.min(world.getMaxHeight() - 1, y + 14);
                for (int blockY = minY; blockY <= maxY; blockY++) {
                    if (isTreeMaterial(world.getBlockAt(x, blockY, z).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean tooCloseToLobby(ArenaCenter center) {
        LavaConfig.Lobby lobby = plugin.settings().lobby();
        double minDistance = plugin.settings().arenaSelection().minDistanceFromLobby();
        return horizontalDistanceSquared(center.x(), center.z(), lobby.x(), lobby.z()) < minDistance * minDistance;
    }

    private boolean tooCloseToUsedArena(ArenaCenter center) {
        int minimumDistance = Math.max(plugin.settings().arenaSelection().minDistanceFromUsedArenas(),
                plugin.settings().round().arenaDiameter() + 64);
        double minimumDistanceSquared = (double) minimumDistance * minimumDistance;
        for (ArenaCenter used : arenaStore.usedCenters()) {
            if (center.distanceSquared(used) < minimumDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private Location safeSurfaceLocationAt(World world, double x, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int groundY = safeGroundY(world, blockX, blockZ);
        if (groundY == Integer.MIN_VALUE) {
            return null;
        }
        return new Location(world, x, groundY + 1.0D, z);
    }

    private int surfaceTopY(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y <= world.getMinHeight()) {
            y = world.getHighestBlockYAt(x, z);
        }
        return Math.min(y, world.getMaxHeight() - 3);
    }

    private boolean isSafeSpawnSurface(Material material) {
        if (!material.isSolid() || material == Material.LAVA || material == Material.WATER) {
            return false;
        }
        String name = material.name();
        return !name.endsWith("_LEAVES")
                && !name.endsWith("_LOG")
                && !name.endsWith("_WOOD")
                && !name.endsWith("_STEM")
                && !name.endsWith("_HYPHAE")
                && !name.endsWith("_TRAPDOOR")
                && !name.endsWith("_DOOR")
                && !name.endsWith("_FENCE")
                && !name.endsWith("_WALL");
    }

    private boolean isTreeMaterial(Material material) {
        String name = material.name();
        return name.endsWith("_LEAVES")
                || name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE")
                || name.equals("MANGROVE_ROOTS");
    }

    private double horizontalDistanceSquared(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + " "
                + String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f",
                location.getX(), location.getY(), location.getZ());
    }
}
