package com.lavarising.v2;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Difficulty;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;

public final class LavaConfig {
    private final boolean consoleLogging;
    private final Lobby lobby;
    private final Start start;
    private final Round round;
    private final EnumMap<LavaPhase, Double> lavaSpeeds;
    private final Deathmatch deathmatch;
    private final Celebration celebration;
    private final ArenaSelectionSettings arenaSelection;
    private final Performance performance;
    private final BuildLimits buildLimits;

    private LavaConfig(boolean consoleLogging,
                       Lobby lobby,
                       Start start,
                       Round round,
                       EnumMap<LavaPhase, Double> lavaSpeeds,
                       Deathmatch deathmatch,
                       Celebration celebration,
                       ArenaSelectionSettings arenaSelection,
                       Performance performance,
                       BuildLimits buildLimits) {
        this.consoleLogging = consoleLogging;
        this.lobby = lobby;
        this.start = start;
        this.round = round;
        this.lavaSpeeds = lavaSpeeds;
        this.deathmatch = deathmatch;
        this.celebration = celebration;
        this.arenaSelection = arenaSelection;
        this.performance = performance;
        this.buildLimits = buildLimits;
    }

    public static LavaConfig load(FileConfiguration config) {
        boolean consoleLogging = config.getBoolean("logging.console", true);
        Lobby lobby = new Lobby(
                config.getString("lobby.world", "world"),
                config.getDouble("lobby.x", 0.5D),
                config.getDouble("lobby.z", 0.5D),
                clamp(config.getInt("lobby.radius", 32), 4, 512));

        Start start = new Start(
                clamp(config.getInt("start.minPlayers", 2), 1, 200),
                config.getBoolean("start.publicStartWhenNoAdminOnline", true),
                clamp(config.getInt("start.countdownSeconds", 10), 3, 60));

        int startLavaY = clamp(config.getInt("round.startLavaY", -64), -256, 319);
        int maxLavaY = clamp(config.getInt("round.maxLavaY", 319), startLavaY + 1, 319);
        int deathmatchStartY = clamp(config.getInt("round.deathmatchStartY", 314), startLavaY + 1, maxLavaY);
        Material defaultBlock = material(config.getString("round.defaultBlock", "DIRT"), Material.DIRT);
        Material sandBlock = material(config.getString("round.sandMayhemBlock", "SAND"), Material.SAND);
        Round round = new Round(
                clamp(config.getInt("round.arenaDiameter", 100), 32, 2000),
                clamp(config.getInt("round.minSpawnY", 60), -64, 300),
                startLavaY,
                maxLavaY,
                deathmatchStartY,
                difficulty(config.getString("round.countdownDifficulty", "EASY"), Difficulty.EASY),
                difficulty(config.getString("round.surfaceDifficulty", "HARD"), Difficulty.HARD),
                config.getBoolean("round.giveBlocks", true),
                clamp(config.getInt("round.blockGiveRate", 16), 0, 2304),
                clamp(config.getInt("round.maxGivenBlocks", 256), 0, 2304),
                clamp(config.getInt("round.sandMayhemChance", 10), 0, 100),
                clamp(config.getInt("round.villageStartChance", 10), 0, 100),
                defaultBlock,
                sandBlock,
                config.getBoolean("round.milestoneMessages", true),
                config.getBoolean("round.sounds", true),
                config.getBoolean("round.clearItemsOnLavaRise", false));

        EnumMap<LavaPhase, Double> lavaSpeeds = new EnumMap<>(LavaPhase.class);
        for (LavaPhase phase : LavaPhase.values()) {
            double defaultSpeed = phase == LavaPhase.DEATHMATCH_TO_TOP ? 10.0D : phase == LavaPhase.Y60_TO_Y100
                    || phase == LavaPhase.Y100_TO_DEATHMATCH ? 1.0D : 2.0D;
            lavaSpeeds.put(phase, clamp(config.getDouble("lava.speeds." + phase.configKey(), defaultSpeed),
                    0.05D,
                    600.0D));
        }

        Deathmatch deathmatch = new Deathmatch(
                clamp(config.getInt("deathmatch.borderRadius", 1), 1, 500),
                clamp(config.getInt("deathmatch.borderShrinkSeconds", 300), 1, 3600),
                config.getBoolean("deathmatch.borderDamage", true));

        Celebration celebration = new Celebration(
                clamp(config.getInt("celebration.seconds", 15), 1, 120),
                clamp(config.getInt("celebration.fireworkIntervalTicks", 10), 1, 200),
                clamp(config.getInt("celebration.minFireworks", 1), 1, 8),
                clamp(config.getInt("celebration.maxFireworks", 8), 1, 32));

        ArenaSelectionSettings arenaSelection = new ArenaSelectionSettings(
                clamp(config.getInt("arenaSelection.minDistanceFromLobby", 512), 0, 50000),
                clamp(config.getInt("arenaSelection.minDistanceFromUsedArenas", 512), 0, 50000),
                clamp(config.getInt("arenaSelection.searchMinRadius", 700), 0, 100000),
                clamp(config.getInt("arenaSelection.searchMaxRadius", 5000), 1, 200000),
                clamp(config.getInt("arenaSelection.maxAttempts", 96), 1, 2000),
                clamp(config.getInt("arenaSelection.treeCheckRadius", 32), 0, 256),
                clamp(config.getInt("arenaSelection.villageSearchRadiusChunks", 320), 1, 2000),
                clamp(config.getInt("arenaSelection.villageSearchAttempts", 12), 0, 200),
                biomes(config.getStringList("arenaSelection.biomeWhitelist")));

        Performance performance = new Performance(
                config.getBoolean("performance.forceLoadArenaChunks", true),
                clamp(config.getInt("performance.forceLoadedChunkRadius", 4), 0, 32),
                clamp(config.getInt("performance.lavaChunksPerTick", 2), 1, 64),
                clamp(config.getDouble("performance.fastLavaSpeedThreshold", 0.15D), 0.0D, 5.0D),
                clamp(config.getInt("performance.fastLavaChunksPerTick", 256), 1, 2048),
                clamp(config.getInt("performance.integrityChunksPerTick", 1), 1, 64),
                clamp(config.getInt("performance.integrityVerticalBatch", 24), 1, 384));

        BuildLimits buildLimits = new BuildLimits(
                config.getBoolean("buildLimits.enabled", true),
                clamp(config.getInt("buildLimits.lockY", 160), -64, 319),
                clamp(config.getInt("buildLimits.unlockWhenLavaY", 60), -64, 319),
                clamp(config.getInt("buildLimits.warningY", 200), -64, 319));

        return new LavaConfig(consoleLogging, lobby, start, round, lavaSpeeds, deathmatch,
                celebration.normalized(), arenaSelection.normalized(), performance, buildLimits);
    }

    public boolean consoleLogging() {
        return consoleLogging;
    }

    public Lobby lobby() {
        return lobby;
    }

    public Start start() {
        return start;
    }

    public Round round() {
        return round;
    }

    public double lavaSpeed(LavaPhase phase) {
        return lavaSpeeds.getOrDefault(phase, 2.0D);
    }

    public Deathmatch deathmatch() {
        return deathmatch;
    }

    public Celebration celebration() {
        return celebration;
    }

    public ArenaSelectionSettings arenaSelection() {
        return arenaSelection;
    }

    public Performance performance() {
        return performance;
    }

    public BuildLimits buildLimits() {
        return buildLimits;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static Difficulty difficulty(String raw, Difficulty fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Difficulty.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static Material material(String raw, Material fallback) {
        if (raw == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.trim());
        return material == null || !material.isBlock() ? fallback : material;
    }

    private static Set<Biome> biomes(List<String> rawBiomes) {
        Set<Biome> parsed = new HashSet<>();
        for (String rawBiome : rawBiomes) {
            if (rawBiome == null || rawBiome.isBlank()) {
                continue;
            }
            try {
                parsed.add(Biome.valueOf(rawBiome.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Bad biome names are ignored so a single typo cannot break startup.
            }
        }
        if (parsed.isEmpty()) {
            Collections.addAll(parsed,
                    Biome.FOREST,
                    Biome.BIRCH_FOREST,
                    Biome.OLD_GROWTH_BIRCH_FOREST,
                    Biome.FLOWER_FOREST,
                    Biome.PLAINS,
                    Biome.SUNFLOWER_PLAINS);
        }
        return Set.copyOf(parsed);
    }

    public record Lobby(String world, double x, double z, int radius) {
    }

    public record Start(int minPlayers, boolean publicStartWhenNoAdminOnline, int countdownSeconds) {
    }

    public record Round(int arenaDiameter,
                        int minSpawnY,
                        int startLavaY,
                        int maxLavaY,
                        int deathmatchStartY,
                        Difficulty countdownDifficulty,
                        Difficulty surfaceDifficulty,
                        boolean giveBlocks,
                        int blockGiveRate,
                        int maxGivenBlocks,
                        int sandMayhemChance,
                        int villageStartChance,
                        Material defaultBlock,
                        Material sandMayhemBlock,
                        boolean milestoneMessages,
                        boolean sounds,
                        boolean clearItemsOnLavaRise) {
    }

    public record Deathmatch(int borderRadius, int borderShrinkSeconds, boolean borderDamage) {
        public int borderDiameter() {
            return Math.max(2, borderRadius * 2);
        }
    }

    public record Celebration(int seconds, int fireworkIntervalTicks, int minFireworks, int maxFireworks) {
        Celebration normalized() {
            return new Celebration(seconds, fireworkIntervalTicks, minFireworks, Math.max(minFireworks, maxFireworks));
        }
    }

    public record ArenaSelectionSettings(int minDistanceFromLobby,
                                         int minDistanceFromUsedArenas,
                                         int searchMinRadius,
                                         int searchMaxRadius,
                                         int maxAttempts,
                                         int treeCheckRadius,
                                         int villageSearchRadiusChunks,
                                         int villageSearchAttempts,
                                         Set<Biome> biomeWhitelist) {
        ArenaSelectionSettings normalized() {
            return new ArenaSelectionSettings(minDistanceFromLobby,
                    minDistanceFromUsedArenas,
                    Math.min(searchMinRadius, searchMaxRadius),
                    Math.max(searchMinRadius + 1, searchMaxRadius),
                    maxAttempts,
                    treeCheckRadius,
                    villageSearchRadiusChunks,
                    villageSearchAttempts,
                    biomeWhitelist);
        }
    }

    public record Performance(boolean forceLoadArenaChunks,
                              int forceLoadedChunkRadius,
                              int lavaChunksPerTick,
                              double fastLavaSpeedThreshold,
                              int fastLavaChunksPerTick,
                              int integrityChunksPerTick,
                              int integrityVerticalBatch) {
    }

    public record BuildLimits(boolean enabled, int lockY, int unlockWhenLavaY, int warningY) {
    }
}
