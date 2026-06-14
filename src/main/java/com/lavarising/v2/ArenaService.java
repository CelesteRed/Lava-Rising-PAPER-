package com.lavarising.v2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
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
import org.bukkit.generator.structure.Structure;
import org.bukkit.scheduler.BukkitTask;

public final class ArenaService {
    private static final int SAFE_SURFACE_SEARCH_RADIUS = 8;
    private static final int MAX_SURFACE_SCAN_DEPTH = 12;
    private static final int MAX_SAMPLE_HEIGHT_DELTA = 18;

    private final LavaRisingPlugin plugin;
    private final ArenaStore arenaStore;
    private final Random random = new Random();
    private Set<Biome> extraAcceptedBiomes = java.util.Set.of();
    // Async arena search state. Only one search runs at a time (guarded by the game state).
    // searchGeneration is a per-launch epoch: any in-flight search whose captured gen no
    // longer equals it has been superseded or cancelled and must no-op (prevents a stale
    // search from a previous /lava start committing into a freshly started round).
    private BukkitTask searchTask;
    private int searchGeneration;

    public ArenaService(LavaRisingPlugin plugin, ArenaStore arenaStore) {
        this.plugin = plugin;
        this.arenaStore = arenaStore;
    }

    public World mainWorld() {
        String configuredWorld = plugin.settings().round().world();
        World world = Bukkit.getWorld(configuredWorld);
        if (world != null) {
            return world;
        }
        return Bukkit.getWorlds().stream()
                .filter(candidate -> candidate.getEnvironment() == Environment.NORMAL)
                .filter(candidate -> !candidate.getName().equals(plugin.settings().lobby().world()))
                .findFirst()
                .orElse(Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst());
    }

    public World lobbyWorld() {
        World world = Bukkit.getWorld(plugin.settings().lobby().world());
        if (world != null) {
            return world;
        }
        if (plugin.lobbyWorld() != null) {
            return plugin.lobbyWorld().ensureLobbyWorld();
        }
        return mainWorld();
    }

    public Location lobbyLocation() {
        World world = lobbyWorld();
        if (world == null) {
            return null;
        }

        LavaConfig.Lobby lobby = plugin.settings().lobby();
        if (lobby.configured()) {
            return new Location(world, lobby.x(), lobby.y(), lobby.z(), lobby.yaw(), lobby.pitch());
        }
        // The lobby is a pure void dimension with no spawn set yet: send players to the arena world
        // spawn so nobody drops into the void. Build a spawn and run /lava setlobby to use the lobby.
        World arena = mainWorld();
        return arena != null ? arena.getSpawnLocation() : null;
    }

    public ArenaSelection selectArena() {
        return selectArena(false, java.util.Set.of());
    }

    public ArenaSelection selectArena(boolean forceVillage, Set<Biome> extraBiomes) {
        World world = mainWorld();
        if (world == null) {
            return null;
        }

        this.extraAcceptedBiomes = extraBiomes == null ? java.util.Set.of() : extraBiomes;
        try {
            LavaConfig.Round round = plugin.settings().round();
            int roll = random.nextInt(100);
            boolean tryVillage = forceVillage || roll < round.villageStartChance();
            boolean preferDesert = this.extraAcceptedBiomes.contains(Biome.DESERT);
            plugin.logGame("Arena selection: forceVillage=" + forceVillage + ", village chance="
                    + round.villageStartChance() + "%, roll=" + roll + ", preferDesert=" + preferDesert + ".");
            if (tryVillage) {
                ArenaSelection village = selectVillageArena(world, preferDesert);
                if (village != null) {
                    return village;
                }
                plugin.logGame("Arena selection: no valid village found, falling back to surface search.");
            }
            return selectSurfaceArena(world);
        } finally {
            this.extraAcceptedBiomes = java.util.Set.of();
        }
    }

    // Soft, time-sliced arena search. Instead of freezing the main thread by generating
    // chunks inline at random far-away origins, this pre-loads each candidate's chunks
    // asynchronously (off-thread) and validates one candidate per tick on the main thread.
    // The callback is always invoked on the main thread with the chosen arena, or null.
    public void selectArenaAsync(boolean forceVillage, Set<Biome> extraBiomes, Consumer<ArenaSelection> done) {
        World world = mainWorld();
        if (world == null) {
            done.accept(null);
            return;
        }
        Set<Biome> accepted = extraBiomes == null ? java.util.Set.of() : extraBiomes;
        LavaConfig.Round round = plugin.settings().round();
        int roll = random.nextInt(100);
        boolean tryVillage = forceVillage || roll < round.villageStartChance();
        boolean preferDesert = accepted.contains(Biome.DESERT);
        // Open a new epoch; this supersedes (and thereby cancels) any prior in-flight search.
        int gen = ++searchGeneration;
        plugin.logGame("Async arena search: forceVillage=" + forceVillage + ", village chance="
                + round.villageStartChance() + "%, roll=" + roll + ", preferDesert=" + preferDesert + ".");
        new ArenaSearch(world, tryVillage, preferDesert, accepted, gen, done).start();
    }

    // Aborts any in-flight async search. Safe to call when none is running.
    public void cancelSearch() {
        // Bump the epoch so any pending future/continuation from the live search no-ops.
        searchGeneration++;
        if (searchTask != null) {
            searchTask.cancel();
            searchTask = null;
        }
        this.extraAcceptedBiomes = java.util.Set.of();
        // Safety net for a hot reload mid-search: release any pre-warm chunk tickets still
        // held, since the whenComplete cleanup may never run if the plugin is being disabled.
        // forceLoadArena uses force-loaded flags (not plugin tickets), so this only frees
        // search tickets and never the active round's force-loaded arena chunks.
        World world = mainWorld();
        if (world != null) {
            world.removePluginChunkTickets(plugin);
        }
    }

    // Runs the action on the main server thread: inline if already there, otherwise scheduled.
    // getChunkAtAsync resolves its callback on the main thread on success, but an EXCEPTIONAL
    // completion can land on a worker thread, so world/game-state access must go through this.
    private void runOnMain(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, action);
        } catch (RuntimeException disabled) {
            // Plugin disabled mid-flight (shutdown/reload); ticket cleanup falls to onDisable.
            plugin.logGame("Async search continuation dropped (plugin disabling): " + disabled);
        }
    }

    // How far (in blocks) from the arena center validateArenaCenter ever reads, so the
    // async pre-warm covers every chunk those reads touch (no chunk is generated inline).
    private int validationReach() {
        int sampleDistance = Math.max(8, Math.min(48, plugin.settings().round().arenaDiameter() / 3));
        int treeRadius = plugin.settings().arenaSelection().treeCheckRadius();
        return Math.max(sampleDistance + SAFE_SURFACE_SEARCH_RADIUS, treeRadius);
    }

    // Loads every chunk the validation will touch off-thread (each pinned with a plugin ticket
    // the instant it loads so none unload mid-check), then runs the normal synchronous
    // validation on the main thread and releases the tickets. The reads are cheap because the
    // chunks are resident. gen guards against a superseded search committing a stale result.
    private void prewarmAndValidate(World world, ArenaCenter center, String source, int gen,
            Set<Biome> accepted, Consumer<ArenaSelection> callback) {
        int reach = validationReach();
        int chunkRadius = (15 + reach) >> 4;
        int centerChunkX = center.x() >> 4;
        int centerChunkZ = center.z() >> 4;
        List<int[]> ticketed = new ArrayList<>();
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                ticketed.add(new int[]{chunkX, chunkZ});
                // thenApply only runs on successful load, which Paper delivers on the main
                // thread, so pinning here is always a safe main-thread mutation.
                futures.add(world.getChunkAtAsync(chunkX, chunkZ, true).thenApply(chunk -> {
                    world.addPluginChunkTicket(chunkX, chunkZ, plugin);
                    return chunk;
                }));
            }
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((ignored, throwable) -> runOnMain(() -> {
                    try {
                        if (gen != searchGeneration) {
                            return; // search superseded/cancelled; tickets freed in finally
                        }
                        if (throwable != null) {
                            plugin.logGame("Async pre-warm failed at " + center.x() + "," + center.z()
                                    + ": " + throwable);
                            callback.accept(null);
                            return;
                        }
                        // Validate against this search's own accepted-biome set.
                        extraAcceptedBiomes = accepted;
                        callback.accept(validateArenaCenter(world, center, source));
                    } finally {
                        for (int[] key : ticketed) {
                            world.removePluginChunkTicket(key[0], key[1], plugin);
                        }
                    }
                }));
    }

    // Drives the search: one candidate per tick, village attempts first (if requested),
    // then surface attempts. Stops as soon as a candidate validates, or reports failure.
    private final class ArenaSearch {
        private final World world;
        private final boolean preferDesert;
        private final Set<Biome> accepted;
        private final int gen;
        private final Consumer<ArenaSelection> done;
        private final LavaConfig.ArenaSelectionSettings settings = plugin.settings().arenaSelection();
        private boolean villagePhase;
        private int attempt;
        private boolean finished;

        ArenaSearch(World world, boolean tryVillage, boolean preferDesert, Set<Biome> accepted,
                int gen, Consumer<ArenaSelection> done) {
            this.world = world;
            this.preferDesert = preferDesert;
            this.accepted = accepted;
            this.gen = gen;
            this.done = done;
            this.villagePhase = tryVillage && world.canGenerateStructures()
                    && settings.villageSearchAttempts() > 0;
        }

        void start() {
            scheduleNext();
        }

        // True once this search is done or has been superseded/cancelled by a newer epoch.
        private boolean stale() {
            return finished || gen != searchGeneration;
        }

        private void scheduleNext() {
            if (stale()) {
                return;
            }
            searchTask = Bukkit.getScheduler().runTaskLater(plugin, this::runAttempt, 1L);
        }

        private void runAttempt() {
            if (stale()) {
                return;
            }
            if (villagePhase) {
                runVillageAttempt();
            } else {
                runSurfaceAttempt();
            }
        }

        private void runVillageAttempt() {
            if (attempt >= settings.villageSearchAttempts()) {
                plugin.logGame("Async search: village phase exhausted, switching to surface.");
                villagePhase = false;
                attempt = 0;
                scheduleNext();
                return;
            }
            attempt++;
            Location origin = randomSearchOrigin(world);
            // locateNearestStructure has no async form; running it once per tick keeps the
            // main thread from doing a burst of structure searches back-to-back.
            Location village = preferDesert
                    ? locateDesertVillage(world, origin, settings.villageSearchRadiusChunks())
                    : world.locateNearestStructure(origin, StructureType.VILLAGE,
                            settings.villageSearchRadiusChunks(), false);
            if (village == null) {
                plugin.logGame("Async village search " + attempt + ": none found"
                        + (preferDesert ? " (desert)." : "."));
                scheduleNext();
                return;
            }
            tryCandidate(new ArenaCenter(village.getBlockX(), village.getBlockZ()), "village");
        }

        private void runSurfaceAttempt() {
            if (attempt >= settings.maxAttempts()) {
                plugin.logGame("Async surface search failed after " + settings.maxAttempts() + " attempts.");
                finish(null);
                return;
            }
            attempt++;
            Location origin = randomSearchOrigin(world);
            tryCandidate(new ArenaCenter(origin.getBlockX(), origin.getBlockZ()), "surface");
        }

        private void tryCandidate(ArenaCenter center, String source) {
            // Cheap, no-chunk rejects first.
            if (tooCloseToLobby(center) || tooCloseToUsedArena(center)) {
                scheduleNext();
                return;
            }
            prewarmAndValidate(world, center, source, gen, accepted, selection -> {
                if (stale()) {
                    return;
                }
                if (selection != null) {
                    plugin.logGame("Async " + source + " arena accepted: center=" + center.x() + ","
                            + center.z() + ", biome=" + selection.biome().name()
                            + ", attempt=" + attempt + ".");
                    finish(selection);
                } else {
                    scheduleNext();
                }
            });
        }

        private void finish(ArenaSelection selection) {
            if (finished) {
                return;
            }
            finished = true;
            // Close this epoch so any straggler future from this search no-ops.
            searchGeneration++;
            if (searchTask != null) {
                searchTask.cancel();
                searchTask = null;
            }
            extraAcceptedBiomes = java.util.Set.of();
            done.accept(selection);
        }
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

    private ArenaSelection selectVillageArena(World world, boolean preferDesert) {
        LavaConfig.ArenaSelectionSettings settings = plugin.settings().arenaSelection();
        if (!world.canGenerateStructures() || settings.villageSearchAttempts() <= 0) {
            plugin.logGame("Village search skipped: structures disabled or attempts set to 0.");
            return null;
        }

        for (int attempt = 0; attempt < settings.villageSearchAttempts(); attempt++) {
            Location origin = randomSearchOrigin(world);
            // Sand Mayhem (preferDesert) targets a desert village specifically so it
            // doesn't land in the nearest taiga/plains village instead.
            Location village = preferDesert
                    ? locateDesertVillage(world, origin, settings.villageSearchRadiusChunks())
                    : world.locateNearestStructure(
                            origin,
                            StructureType.VILLAGE,
                            settings.villageSearchRadiusChunks(),
                            false);
            if (village == null) {
                plugin.logGame("Village search " + (attempt + 1) + ": none found"
                        + (preferDesert ? " (desert)." : "."));
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

    private Location locateDesertVillage(World world, Location origin, int radiusChunks) {
        var result = world.locateNearestStructure(origin, Structure.VILLAGE_DESERT, radiusChunks, false);
        return result == null ? null : result.getLocation();
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
        if (!acceptedBiome(biome)) {
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
            if (!acceptedBiome(biome)) {
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

    private boolean acceptedBiome(Biome biome) {
        // Whitelist disabled: every biome is a valid arena (e.g. for a pre-generated world).
        if (!plugin.settings().arenaSelection().biomeWhitelistEnabled()) {
            return true;
        }
        return plugin.settings().arenaSelection().biomeWhitelist().contains(biome)
                || extraAcceptedBiomes.contains(biome);
    }

    private double horizontalDistanceSquared(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return dx * dx + dz * dz;
    }
}
