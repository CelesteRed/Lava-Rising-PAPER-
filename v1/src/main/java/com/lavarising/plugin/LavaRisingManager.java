package com.lavarising.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.StructureType;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class LavaRisingManager {
    public static final int SURFACE_Y = 60;

    private static final int START_LAVA_Y = -64;
    private static final int MAX_LAVA_Y = 319;
    private static final int FINAL_SLOW_START_Y = 314;
    private static final int SLOWDOWN_Y = FINAL_SLOW_START_Y - 1;
    private static final int EARLY_BUILD_LOCK_Y = 100;
    private static final int MIN_SAFE_SURFACE_SPAWN_Y = SURFACE_Y;
    private static final int SAFE_SURFACE_SEARCH_RADIUS = 10;
    private static final int MAX_SURFACE_SCAN_DEPTH = 12;
    private static final int DEFAULT_ARENA_DIAMETER = 100;
    private static final double DEFAULT_LAVA_RISING_SPEED_SECONDS = 2.0D;
    private static final int DEFAULT_SUDDEN_DEATH_DELAY_SECONDS = 300;
    private static final int DEFAULT_SUDDEN_DEATH_SPEED_SECONDS = 300;
    private static final int DEFAULT_SUDDEN_DEATH_RADIUS = 1;
    private static final boolean DEFAULT_SOUNDS_ENABLED = true;
    private static final boolean DEFAULT_MILESTONE_MESSAGES = true;
    private static final boolean DEFAULT_GIVE_DIRT = true;
    private static final boolean DEFAULT_PVP_AT_SURFACE = true;
    private static final boolean DEFAULT_BORDER_DAMAGE = true;
    private static final boolean DEFAULT_CONSOLE_LOGGING = true;
    private static final int DEFAULT_CELEBRATION_TIME_SECONDS = 15;
    private static final int DEFAULT_NO_MORE_BOTTOM_DWELLERS_SECONDS = 0;
    private static final int DEFAULT_BUILDING_BLOCK_GIVE_RATE = 16;
    private static final int MIN_ARENA_DIAMETER = 3;
    private static final int MAX_ARENA_DIAMETER = 1000;
    private static final double MIN_LAVA_RISING_SPEED_SECONDS = 0.1D;
    private static final double MAX_LAVA_RISING_SPEED_SECONDS = 60.0D;
    private static final int MIN_SUDDEN_DEATH_DELAY_SECONDS = 0;
    private static final int MAX_SUDDEN_DEATH_DELAY_SECONDS = 3600;
    private static final int MIN_SUDDEN_DEATH_SPEED_SECONDS = 1;
    private static final int MAX_SUDDEN_DEATH_SPEED_SECONDS = 3600;
    private static final int MIN_SUDDEN_DEATH_RADIUS = 1;
    private static final int MAX_SUDDEN_DEATH_RADIUS = 500;
    private static final int MIN_CELEBRATION_TIME_SECONDS = 0;
    private static final int MAX_CELEBRATION_TIME_SECONDS = 300;
    private static final int MIN_NO_MORE_BOTTOM_DWELLERS_SECONDS = 0;
    private static final int MAX_NO_MORE_BOTTOM_DWELLERS_SECONDS = 3600;
    private static final int MIN_BUILDING_BLOCK_GIVE_RATE = 0;
    private static final int MAX_BUILDING_BLOCK_GIVE_RATE = 64;
    private static final int DEFAULT_MAX_GIVEN_BLOCKS = 256;
    private static final int MIN_MAX_GIVEN_BLOCKS = 0;
    private static final int MAX_MAX_GIVEN_BLOCKS = 4096;
    private static final int DEFAULT_SAND_MAYHEM_CHANCE_PERCENT = 10;
    private static final int DEFAULT_VILLAGE_START_CHANCE_PERCENT = 10;
    private static final int MIN_SAND_MAYHEM_CHANCE_PERCENT = 0;
    private static final int MAX_SAND_MAYHEM_CHANCE_PERCENT = 100;
    private static final int MIN_VILLAGE_START_CHANCE_PERCENT = 0;
    private static final int MAX_VILLAGE_START_CHANCE_PERCENT = 100;
    private static final double DEFAULT_LOBBY_X = 0.5D;
    private static final double DEFAULT_LOBBY_Z = 0.5D;
    private static final int DEFAULT_LOBBY_RADIUS = 32;
    private static final int DEFAULT_MIN_PLAYERS_TO_START = 2;
    private static final int DEFAULT_ARENA_MIN_DISTANCE_FROM_LOBBY = 512;
    private static final int DEFAULT_ARENA_MIN_DISTANCE_FROM_USED = 512;
    private static final int DEFAULT_ARENA_SEARCH_MIN_RADIUS = 700;
    private static final int DEFAULT_ARENA_SEARCH_MAX_RADIUS = 5000;
    private static final int DEFAULT_ARENA_SEARCH_ATTEMPTS = 96;
    private static final int DEFAULT_ARENA_TREE_CHECK_RADIUS = 32;
    private static final int DEFAULT_VILLAGE_SEARCH_RADIUS_CHUNKS = 320;
    private static final int DEFAULT_VILLAGE_SEARCH_ATTEMPTS = 12;
    private static final int MIN_LOBBY_RADIUS = 4;
    private static final int MAX_LOBBY_RADIUS = 256;
    private static final int MIN_PLAYERS_TO_START = 1;
    private static final int MAX_PLAYERS_TO_START = 100;
    private static final int MIN_ARENA_DISTANCE = 128;
    private static final int MAX_ARENA_DISTANCE = 100000;
    private static final int MIN_ARENA_SEARCH_ATTEMPTS = 1;
    private static final int MAX_ARENA_SEARCH_ATTEMPTS = 1000;
    private static final int MIN_ARENA_TREE_CHECK_RADIUS = 0;
    private static final int MAX_ARENA_TREE_CHECK_RADIUS = 128;
    private static final int MIN_VILLAGE_SEARCH_RADIUS_CHUNKS = 16;
    private static final int MAX_VILLAGE_SEARCH_RADIUS_CHUNKS = 2000;
    private static final int MIN_VILLAGE_SEARCH_ATTEMPTS = 1;
    private static final int MAX_VILLAGE_SEARCH_ATTEMPTS = 100;
    private static final long CELEBRATION_FIREWORK_PERIOD_TICKS = 10L;
    private static final int MIN_CELEBRATION_FIREWORKS_PER_BURST = 1;
    private static final int MAX_CELEBRATION_FIREWORKS_PER_BURST = 8;
    private static final double CELEBRATION_FIREWORK_RADIUS = 4.0D;
    private static final List<String> DEFAULT_ARENA_BIOME_WHITELIST = List.of(
            "FOREST",
            "BIRCH_FOREST",
            "OLD_GROWTH_BIRCH_FOREST",
            "FLOWER_FOREST",
            "DARK_FOREST",
            "PLAINS",
            "SUNFLOWER_PLAINS",
            "SAVANNA",
            "SAVANNA_PLATEAU",
            "TAIGA",
            "OLD_GROWTH_PINE_TAIGA",
            "OLD_GROWTH_SPRUCE_TAIGA",
            "SPARSE_JUNGLE",
            "JUNGLE",
            "CHERRY_GROVE",
            "MEADOW",
            "WINDSWEPT_FOREST");
    private static final double BORDER_DAMAGE_BUFFER = 0.0D;
    private static final double BORDER_DAMAGE_AMOUNT = 1.0D;
    private static final String CONFIG_ARENA_DIAMETER = "arenaDiameter";
    private static final String CONFIG_LAVA_RISING_SPEED = "lavaRisingSpeed";
    private static final String CONFIG_SUDDEN_DEATH_DELAY = "suddenDeathDelay";
    private static final String CONFIG_SUDDEN_DEATH_SPEED = "suddenDeathSpeed";
    private static final String CONFIG_SUDDEN_DEATH_RADIUS = "suddenDeathRadius";
    private static final String CONFIG_SOUNDS_ENABLED = "soundsEnabled";
    private static final String CONFIG_MILESTONE_MESSAGES = "milestoneMessages";
    private static final String CONFIG_GIVE_DIRT = "giveDirt";
    private static final String CONFIG_PVP_AT_SURFACE = "pvpAtSurface";
    private static final String CONFIG_BORDER_DAMAGE = "borderDamage";
    private static final String CONFIG_CONSOLE_LOGGING = "consoleLogging";
    private static final String CONFIG_CELEBRATION_TIME = "celebrationTime";
    private static final String CONFIG_NO_MORE_BOTTOM_DWELLERS = "noMoreBottomDwellers";
    private static final String CONFIG_BUILDING_BLOCK_GIVE_RATE = "buildingBlockGiveRate";
    private static final String CONFIG_MAX_GIVEN_BLOCKS = "maxGivenBlocks";
    private static final String CONFIG_SAND_MAYHEM_CHANCE = "sandMayhemChance";
    private static final String CONFIG_VILLAGE_START_CHANCE = "villageStartChance";
    private static final String CONFIG_LOBBY_X = "lobby.x";
    private static final String CONFIG_LOBBY_Z = "lobby.z";
    private static final String CONFIG_LOBBY_RADIUS = "lobby.radius";
    private static final String CONFIG_MIN_PLAYERS_TO_START = "minPlayersToStart";
    private static final String CONFIG_ARENA_SELECTION = "arenaSelection";
    private static final String CONFIG_ARENA_BIOME_WHITELIST = CONFIG_ARENA_SELECTION + ".biomeWhitelist";
    private static final String CONFIG_ARENA_MIN_DISTANCE_FROM_LOBBY =
            CONFIG_ARENA_SELECTION + ".minDistanceFromLobby";
    private static final String CONFIG_ARENA_MIN_DISTANCE_FROM_USED =
            CONFIG_ARENA_SELECTION + ".minDistanceFromUsedArenas";
    private static final String CONFIG_ARENA_SEARCH_MIN_RADIUS = CONFIG_ARENA_SELECTION + ".searchMinRadius";
    private static final String CONFIG_ARENA_SEARCH_MAX_RADIUS = CONFIG_ARENA_SELECTION + ".searchMaxRadius";
    private static final String CONFIG_ARENA_SEARCH_ATTEMPTS = CONFIG_ARENA_SELECTION + ".maxAttempts";
    private static final String CONFIG_ARENA_TREE_CHECK_RADIUS = CONFIG_ARENA_SELECTION + ".treeCheckRadius";
    private static final String CONFIG_VILLAGE_SEARCH_RADIUS_CHUNKS =
            CONFIG_ARENA_SELECTION + ".villageSearchRadiusChunks";
    private static final String CONFIG_VILLAGE_SEARCH_ATTEMPTS =
            CONFIG_ARENA_SELECTION + ".villageSearchAttempts";
    private static final String CONFIG_USED_ARENA_CENTERS = "usedArenaCenters";

    private GameState state = GameState.WAITING;
    private int currentY = START_LAVA_Y;
    private final Random random = new Random();
    private final List<BukkitTask> activeTasks = new ArrayList<>();
    private final Set<UUID> activePlayers = new HashSet<>();
    private final Set<UUID> waitingPlayers = new HashSet<>();
    private final Map<UUID, FlightState> celebrationFlightStates = new HashMap<>();
    private final LavaRisingPlugin plugin;
    private Double savedBorderSize;
    private Location savedBorderCenter;
    private Double savedBorderDamageBuffer;
    private Double savedBorderDamageAmount;
    private Integer savedBorderWarningTimeTicks;
    private Integer savedBorderWarningDistance;
    private Difficulty savedDifficulty;
    private Boolean savedImmediateRespawn;
    private int arenaDiameter;
    private final int[] lavaPhaseSpeedTicks = new int[LavaPhase.values().length];
    private final int[] lavaPhaseSpeedOverrideTicks = new int[LavaPhase.values().length];
    private BukkitTask lavaRiseTask;
    private int suddenDeathDelaySeconds;
    private int suddenDeathSpeedSeconds;
    private int suddenDeathRadius;
    private int celebrationTimeSeconds;
    private int noMoreBottomDwellersSeconds;
    private int noMoreBottomDwellersClearSeconds;
    private int buildingBlockGiveRate;
    private int maxGivenBlocks;
    private int sandMayhemChancePercent;
    private int villageStartChancePercent;
    private double lobbyX;
    private double lobbyZ;
    private int lobbyRadius;
    private int minPlayersToStart;
    private int arenaMinDistanceFromLobby;
    private int arenaMinDistanceFromUsed;
    private int arenaSearchMinRadius;
    private int arenaSearchMaxRadius;
    private int arenaSearchAttempts;
    private int arenaTreeCheckRadius;
    private int villageSearchRadiusChunks;
    private int villageSearchAttempts;
    private boolean noMoreBottomDwellersActive;
    private boolean sandMayhemRound;
    private Material buildingBlockMaterial = Material.DIRT;
    private boolean soundsEnabled;
    private boolean milestoneMessages;
    private boolean giveDirt;
    private boolean pvpAtSurface;
    private boolean borderDamage;
    private boolean consoleLogging;
    private final Set<Biome> arenaBiomeWhitelist = new HashSet<>();
    private final List<ArenaCenter> usedArenaCenters = new ArrayList<>();
    private ArenaCenter currentArenaCenter;

    public LavaRisingManager(LavaRisingPlugin plugin) {
        this.plugin = plugin;
        loadSettings();
    }

    public GameState getState() {
        return state;
    }

    public int getCurrentY() {
        return currentY;
    }

    public boolean isRunning() {
        return state == GameState.RUNNING || state == GameState.SUDDEN_DEATH;
    }

    public boolean isLavaActivelyRising() {
        return isLavaAdvancing();
    }

    public boolean isCountdown() {
        return state == GameState.COUNTDOWN;
    }

    public boolean isCelebrating() {
        return state == GameState.CELEBRATION;
    }

    public boolean isInGame() {
        return state != GameState.WAITING;
    }

    public String getSpeedLabel() {
        LavaPhase phase = getActiveLavaPhase();
        if (phase == LavaPhase.HEIGHT_LIMIT) {
            return phase.getDisplayName();
        }
        return phase.getDisplayName() + " (1 Lava/" + formatSeconds(getEffectiveLavaRisingSpeedSeconds(phase)) + "sec)";
    }

    public int getArenaDiameter() {
        return arenaDiameter;
    }

    public double getLavaRisingSpeedSeconds() {
        return getLavaRisingSpeedSeconds(LavaPhase.START);
    }

    public double getLavaRisingSpeedSeconds(LavaPhase phase) {
        return lavaPhaseSpeedTicks[phase.ordinal()] / 20.0D;
    }

    public double getEffectiveLavaRisingSpeedSeconds(LavaPhase phase) {
        return getEffectiveTicksForPhase(phase) / 20.0D;
    }

    public LavaPhase getCurrentLavaPhase() {
        return getLavaPhaseForY(currentY);
    }

    public LavaPhase getActiveLavaPhase() {
        if (isLavaAdvancing()) {
            return getLavaPhaseForNextLayer();
        }
        return getCurrentLavaPhase();
    }

    public LavaPhase[] getLavaPhases() {
        return LavaPhase.values().clone();
    }

    public int getEarlyBuildLockY() {
        return EARLY_BUILD_LOCK_Y;
    }

    public boolean isEarlyBuildHeightLocked() {
        return state == GameState.RUNNING && currentY < SURFACE_Y;
    }

    public double getBaseSpeedSeconds() {
        return getLavaRisingSpeedSeconds();
    }

    public int getSuddenDeathDelaySeconds() {
        return suddenDeathDelaySeconds;
    }

    public int getSuddenDeathSpeedSeconds() {
        return suddenDeathSpeedSeconds;
    }

    public int getSuddenDeathRadius() {
        return suddenDeathRadius;
    }

    public int getSuddenDeathDiameter() {
        return suddenDeathRadius * 2 + 1;
    }

    public int getCelebrationTimeSeconds() {
        return celebrationTimeSeconds;
    }

    public boolean isSoundsEnabled() {
        return soundsEnabled;
    }

    public boolean isMilestoneMessagesEnabled() {
        return milestoneMessages;
    }

    public boolean isGiveDirtEnabled() {
        return giveDirt;
    }

    public boolean isPvpAtSurfaceEnabled() {
        return pvpAtSurface;
    }

    public boolean isBorderDamageEnabled() {
        return borderDamage;
    }

    public boolean isConsoleLoggingEnabled() {
        return consoleLogging;
    }

    public int getNoMoreBottomDwellersSeconds() {
        return noMoreBottomDwellersSeconds;
    }

    public int getBuildingBlockGiveRate() {
        return buildingBlockGiveRate;
    }

    public int getMaxGivenBlocks() {
        return maxGivenBlocks;
    }

    public Material getBuildingBlockMaterial() {
        return buildingBlockMaterial;
    }

    public boolean isSandMayhemRound() {
        return sandMayhemRound;
    }

    public int getSandMayhemChancePercent() {
        return sandMayhemChancePercent;
    }

    public int getVillageStartChancePercent() {
        return villageStartChancePercent;
    }

    public int getLobbyPlayerCount() {
        return getLobbyPlayers().size();
    }

    public int getLobbyRadius() {
        return lobbyRadius;
    }

    public double getLobbyX() {
        return lobbyX;
    }

    public double getLobbyZ() {
        return lobbyZ;
    }

    public int getMinPlayersToStart() {
        return minPlayersToStart;
    }

    public String getArenaBiomeWhitelistLabel() {
        return arenaBiomeWhitelist.stream()
                .map(Biome::name)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    public boolean isActivePlayer(UUID uuid) {
        return activePlayers.contains(uuid);
    }

    public boolean isActiveCombatPlayer(Player player) {
        return isRunning() && isAliveRoundPlayer(player);
    }

    public boolean shouldManageGameMode(Player player) {
        return !player.isOp();
    }

    public boolean hasOnlineAdmin() {
        return plugin.getServer().getOnlinePlayers().stream()
                .anyMatch(player -> player.isOp() || player.hasPermission("lavarising.use"));
    }

    public LavaPhase setCurrentPhaseSpeedBypassSeconds(double seconds) {
        LavaPhase phase = getActiveLavaPhase();
        lavaPhaseSpeedOverrideTicks[phase.ordinal()] = secondsToTicks(seconds);
        rescheduleNextLavaTick();
        return phase;
    }

    public LavaPhase clearCurrentPhaseSpeedBypass() {
        LavaPhase phase = getActiveLavaPhase();
        lavaPhaseSpeedOverrideTicks[phase.ordinal()] = 0;
        rescheduleNextLavaTick();
        return phase;
    }

    public boolean hasLavaSpeedBypass(LavaPhase phase) {
        return lavaPhaseSpeedOverrideTicks[phase.ordinal()] > 0;
    }

    public void setArenaDiameter(int diameter) {
        arenaDiameter = clamp(diameter, MIN_ARENA_DIAMETER, MAX_ARENA_DIAMETER);
        suddenDeathRadius = clampSuddenDeathRadius(suddenDeathRadius);
        plugin.getConfig().set(CONFIG_ARENA_DIAMETER, arenaDiameter);
        plugin.getConfig().set(CONFIG_SUDDEN_DEATH_RADIUS, suddenDeathRadius);
        plugin.saveConfig();
        logConsole("Setting changed: arenaDiameter=" + arenaDiameter
                + ", suddenDeathRadius=" + suddenDeathRadius + ".");
    }

    public void setLavaRisingSpeedSeconds(double seconds) {
        double clamped = Math.max(MIN_LAVA_RISING_SPEED_SECONDS, Math.min(MAX_LAVA_RISING_SPEED_SECONDS, seconds));
        for (LavaPhase phase : LavaPhase.values()) {
            lavaPhaseSpeedTicks[phase.ordinal()] = secondsToTicks(clamped);
        }
        saveLavaPhaseSpeedSettings();
        plugin.saveConfig();
        logConsole("Setting changed: all lava phase speeds=" + formatAllPhaseSpeedsForLog() + ".");
    }

    public void setLavaRisingSpeedSeconds(LavaPhase phase, double seconds) {
        double clamped = Math.max(MIN_LAVA_RISING_SPEED_SECONDS, Math.min(MAX_LAVA_RISING_SPEED_SECONDS, seconds));
        lavaPhaseSpeedTicks[phase.ordinal()] = secondsToTicks(clamped);
        plugin.getConfig().set(CONFIG_LAVA_RISING_SPEED + "." + phase.getConfigKey(),
                getLavaRisingSpeedSeconds(phase));
        plugin.saveConfig();
        logConsole("Setting changed: lavaRisingSpeed." + phase.getConfigKey()
                + "=" + formatSeconds(getLavaRisingSpeedSeconds(phase)) + "s.");
    }

    public void setBaseSpeedSeconds(double seconds) {
        setLavaRisingSpeedSeconds(seconds);
    }

    public void setSuddenDeathDelaySeconds(int seconds) {
        suddenDeathDelaySeconds = clamp(seconds, MIN_SUDDEN_DEATH_DELAY_SECONDS, MAX_SUDDEN_DEATH_DELAY_SECONDS);
        plugin.getConfig().set(CONFIG_SUDDEN_DEATH_DELAY, suddenDeathDelaySeconds);
        plugin.saveConfig();
        logConsole("Setting changed: suddenDeathDelay=" + suddenDeathDelaySeconds + ".");
    }

    public void setSuddenDeathSpeedSeconds(int seconds) {
        suddenDeathSpeedSeconds = clamp(seconds, MIN_SUDDEN_DEATH_SPEED_SECONDS, MAX_SUDDEN_DEATH_SPEED_SECONDS);
        plugin.getConfig().set(CONFIG_SUDDEN_DEATH_SPEED, suddenDeathSpeedSeconds);
        plugin.saveConfig();
        logConsole("Setting changed: suddenDeathSpeed=" + suddenDeathSpeedSeconds + ".");
    }

    public void setSuddenDeathRadius(int radius) {
        suddenDeathRadius = clampSuddenDeathRadius(radius);
        plugin.getConfig().set(CONFIG_SUDDEN_DEATH_RADIUS, suddenDeathRadius);
        plugin.saveConfig();
        logConsole("Setting changed: suddenDeathRadius=" + suddenDeathRadius + ".");
    }

    public void setCelebrationTimeSeconds(int seconds) {
        celebrationTimeSeconds = clamp(seconds,
                MIN_CELEBRATION_TIME_SECONDS,
                MAX_CELEBRATION_TIME_SECONDS);
        plugin.getConfig().set(CONFIG_CELEBRATION_TIME, celebrationTimeSeconds);
        plugin.saveConfig();
        logConsole("Setting changed: celebrationTime=" + celebrationTimeSeconds + ".");
    }

    public void setSoundsEnabled(boolean enabled) {
        soundsEnabled = enabled;
        plugin.getConfig().set(CONFIG_SOUNDS_ENABLED, soundsEnabled);
        plugin.saveConfig();
        logConsole("Setting changed: soundsEnabled=" + soundsEnabled + ".");
    }

    public void setMilestoneMessagesEnabled(boolean enabled) {
        milestoneMessages = enabled;
        plugin.getConfig().set(CONFIG_MILESTONE_MESSAGES, milestoneMessages);
        plugin.saveConfig();
        logConsole("Setting changed: milestoneMessages=" + milestoneMessages + ".");
    }

    public void setGiveDirtEnabled(boolean enabled) {
        giveDirt = enabled;
        plugin.getConfig().set(CONFIG_GIVE_DIRT, giveDirt);
        plugin.saveConfig();
        logConsole("Setting changed: giveDirt=" + giveDirt + ".");
    }

    public void setPvpAtSurfaceEnabled(boolean enabled) {
        pvpAtSurface = enabled;
        plugin.getConfig().set(CONFIG_PVP_AT_SURFACE, pvpAtSurface);
        plugin.saveConfig();
        logConsole("Setting changed: pvpAtSurface=" + pvpAtSurface + ".");
    }

    public void setBorderDamageEnabled(boolean enabled) {
        borderDamage = enabled;
        plugin.getConfig().set(CONFIG_BORDER_DAMAGE, borderDamage);
        plugin.saveConfig();
        logConsole("Setting changed: borderDamage=" + borderDamage);
    }

    public void setConsoleLoggingEnabled(boolean enabled) {
        consoleLogging = enabled;
        plugin.getConfig().set(CONFIG_CONSOLE_LOGGING, consoleLogging);
        plugin.saveConfig();
        logConsole("Setting changed: consoleLogging=" + consoleLogging);
    }

    public void setNoMoreBottomDwellersSeconds(int seconds) {
        noMoreBottomDwellersSeconds = 0;
        plugin.getConfig().set(CONFIG_NO_MORE_BOTTOM_DWELLERS, noMoreBottomDwellersSeconds);
        plugin.saveConfig();
        logConsole("Setting changed: noMoreBottomDwellers=0 forced disabled.");
    }

    public void setBuildingBlockGiveRate(int blocksPerLavaRise) {
        buildingBlockGiveRate = clamp(blocksPerLavaRise,
                MIN_BUILDING_BLOCK_GIVE_RATE,
                MAX_BUILDING_BLOCK_GIVE_RATE);
        plugin.getConfig().set(CONFIG_BUILDING_BLOCK_GIVE_RATE, buildingBlockGiveRate);
        plugin.saveConfig();
        logConsole("Setting changed: buildingBlockGiveRate=" + buildingBlockGiveRate + ".");
    }

    public void setMaxGivenBlocks(int blocks) {
        maxGivenBlocks = clamp(blocks, MIN_MAX_GIVEN_BLOCKS, MAX_MAX_GIVEN_BLOCKS);
        plugin.getConfig().set(CONFIG_MAX_GIVEN_BLOCKS, maxGivenBlocks);
        plugin.saveConfig();
        logConsole("Setting changed: maxGivenBlocks=" + maxGivenBlocks + ".");
    }

    public void setSandMayhemChancePercent(int chancePercent) {
        sandMayhemChancePercent = clamp(chancePercent,
                MIN_SAND_MAYHEM_CHANCE_PERCENT,
                MAX_SAND_MAYHEM_CHANCE_PERCENT);
        plugin.getConfig().set(CONFIG_SAND_MAYHEM_CHANCE, sandMayhemChancePercent);
        plugin.saveConfig();
        logConsole("Setting changed: sandMayhemChance=" + sandMayhemChancePercent + "%.");
    }

    public void setVillageStartChancePercent(int chancePercent) {
        villageStartChancePercent = clamp(chancePercent,
                MIN_VILLAGE_START_CHANCE_PERCENT,
                MAX_VILLAGE_START_CHANCE_PERCENT);
        plugin.getConfig().set(CONFIG_VILLAGE_START_CHANCE, villageStartChancePercent);
        plugin.saveConfig();
        logConsole("Setting changed: villageStartChance=" + villageStartChancePercent + "%.");
    }

    public void setMinPlayersToStart(int players) {
        minPlayersToStart = clamp(players, MIN_PLAYERS_TO_START, MAX_PLAYERS_TO_START);
        plugin.getConfig().set(CONFIG_MIN_PLAYERS_TO_START, minPlayersToStart);
        plugin.saveConfig();
        logConsole("Setting changed: minPlayersToStart=" + minPlayersToStart + ".");
    }

    public void setLobbyRadius(int radius) {
        lobbyRadius = clamp(radius, MIN_LOBBY_RADIUS, MAX_LOBBY_RADIUS);
        plugin.getConfig().set(CONFIG_LOBBY_RADIUS, lobbyRadius);
        plugin.saveConfig();
        logConsole("Setting changed: lobbyRadius=" + lobbyRadius + ".");
    }

    public void setLobbyX(double x) {
        if (!Double.isFinite(x)) {
            throw new IllegalArgumentException("lobbyX must be a finite number.");
        }
        lobbyX = x;
        plugin.getConfig().set(CONFIG_LOBBY_X, lobbyX);
        plugin.saveConfig();
        logConsole("Setting changed: lobbyX=" + formatCoordinate(lobbyX) + ".");
    }

    public void setLobbyZ(double z) {
        if (!Double.isFinite(z)) {
            throw new IllegalArgumentException("lobbyZ must be a finite number.");
        }
        lobbyZ = z;
        plugin.getConfig().set(CONFIG_LOBBY_Z, lobbyZ);
        plugin.saveConfig();
        logConsole("Setting changed: lobbyZ=" + formatCoordinate(lobbyZ) + ".");
    }

    public void setLobbyLocation(Location location, Integer radius) {
        World world = getMainWorld();
        if (world == null || location == null || !location.getWorld().equals(world)) {
            throw new IllegalArgumentException("Lobby must be set in the normal overworld.");
        }

        setLobbyLocation(location.getX(), location.getZ(), radius);
    }

    public void setLobbyLocation(double x, double z, Integer radius) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Lobby coordinates must be finite numbers.");
        }

        lobbyX = x;
        lobbyZ = z;
        if (radius != null) {
            lobbyRadius = clamp(radius, MIN_LOBBY_RADIUS, MAX_LOBBY_RADIUS);
        }
        plugin.getConfig().set(CONFIG_LOBBY_X, lobbyX);
        plugin.getConfig().set(CONFIG_LOBBY_Z, lobbyZ);
        plugin.getConfig().set(CONFIG_LOBBY_RADIUS, lobbyRadius);
        plugin.saveConfig();
        logConsole("Setting changed: lobby=(" + formatCoordinate(lobbyX)
                + "," + formatCoordinate(lobbyZ) + ") radius=" + lobbyRadius + ".");
    }

    public void setArenaBiomeWhitelistFromInput(String rawValue) {
        Set<Biome> parsed = parseBiomeWhitelist(List.of(rawValue.split(",")));
        arenaBiomeWhitelist.clear();
        arenaBiomeWhitelist.addAll(parsed);
        plugin.getConfig().set(CONFIG_ARENA_BIOME_WHITELIST,
                arenaBiomeWhitelist.stream().map(Biome::name).sorted().toList());
        plugin.saveConfig();
        logConsole("Setting changed: arenaBiomeWhitelist=" + getArenaBiomeWhitelistLabel());
    }

    public void logConsole(String message) {
        if (consoleLogging) {
            plugin.getLogger().info("[LavaRising] " + message);
        }
    }

    public Set<UUID> getWaitingPlayers() {
        return Collections.unmodifiableSet(waitingPlayers);
    }

    public void addWaitingPlayer(UUID uuid) {
        waitingPlayers.add(uuid);
    }

    public ReviveResult revivePlayer(Player player, Player requestedAnchor) {
        if (!isRunning()) {
            return new ReviveResult(ReviveStatus.NOT_RUNNING, null);
        }
        if (!player.isOnline()) {
            return new ReviveResult(ReviveStatus.PLAYER_NOT_ONLINE, null);
        }
        if (player.isDead()) {
            return new ReviveResult(ReviveStatus.PLAYER_NOT_RESPAWNED, null);
        }
        if (isAliveRoundPlayer(player)) {
            return new ReviveResult(ReviveStatus.ALREADY_ALIVE, null);
        }

        Player anchor = requestedAnchor;
        if (anchor != null && !isAliveRoundPlayer(anchor)) {
            return new ReviveResult(ReviveStatus.ANCHOR_NOT_ALIVE, anchor);
        }
        if (anchor == null) {
            List<Player> anchors = getAlivePlayers().stream()
                    .filter(alive -> !alive.getUniqueId().equals(player.getUniqueId()))
                    .toList();
            if (anchors.isEmpty()) {
                return new ReviveResult(ReviveStatus.NO_ALIVE_ANCHOR, null);
            }
            anchor = anchors.get(random.nextInt(anchors.size()));
        }

        activePlayers.add(player.getUniqueId());
        waitingPlayers.remove(player.getUniqueId());
        if (shouldManageGameMode(player) || player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setFallDistance(0.0F);
        player.setFireTicks(0);
        player.teleport(anchor.getLocation());
        player.sendMessage(ChatColor.GREEN + "You were revived by an admin.");
        logConsole("Player revived: player=" + player.getName()
                + ", anchor=" + anchor.getName()
                + ", mode=" + player.getGameMode()
                + ", loc=" + formatLocation(anchor.getLocation()) + ".");
        return new ReviveResult(ReviveStatus.REVIVED, anchor);
    }

    public StartResult startLava() {
        if (state != GameState.WAITING) {
            return state == GameState.COUNTDOWN ? StartResult.COUNTDOWN_ACTIVE : StartResult.ALREADY_RUNNING;
        }

        World world = getMainWorld();
        if (world == null) {
            return StartResult.NO_WORLD;
        }

        List<Player> participants = getLobbyPlayers();
        if (participants.isEmpty()) {
            logConsole("Round start failed: no lobby players found.");
            return StartResult.NO_LOBBY_PLAYERS;
        }
        logConsole("Round start accepted: participants=" + participants.size()
                + " [" + formatPlayerNames(participants) + "], world=" + world.getName() + ".");

        ArenaSelection selection = selectFreshArena(world);
        if (selection == null) {
            logConsole("Round start failed: no fresh arena found after configured search attempts.");
            return StartResult.NO_ARENA_FOUND;
        }

        stopAllTasks();
        state = GameState.COUNTDOWN;
        activePlayers.clear();
        for (Player player : participants) {
            activePlayers.add(player.getUniqueId());
        }
        waitingPlayers.clear();
        currentY = START_LAVA_Y;
        noMoreBottomDwellersActive = false;
        noMoreBottomDwellersClearSeconds = 0;
        clearLavaSpeedBypasses();
        sandMayhemRound = false;
        buildingBlockMaterial = Material.DIRT;
        chooseBuildingBlockMaterial();
        logConsole("Round entering countdown: blockMaterial=" + buildingBlockMaterial
                + ", sandMayhem=" + sandMayhemRound
                + ", maxGivenBlocks=" + maxGivenBlocks
                + ", giveRate=" + buildingBlockGiveRate + ".");

        prepareArenaForCountdown(world, selection, participants);
        announceSandMayhemRound();
        scheduleCountdownAnnouncements();
        return StartResult.STARTED;
    }

    public List<Player> getLobbyPlayers() {
        World world = getMainWorld();
        if (world == null) {
            return Collections.emptyList();
        }

        return plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                .filter(this::isInLobby)
                .collect(Collectors.toList());
    }

    public void sendPlayerToLobby(Player player) {
        World world = getMainWorld();
        if (world == null || !player.isOnline()) {
            return;
        }

        Location lobby = getLobbyLocation(world);
        Chunk chunk = world.getChunkAt(lobby);
        if (!chunk.isLoaded()) {
            chunk.load(true);
        }

        GameMode beforeMode = player.getGameMode();
        if (shouldManageGameMode(player)) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        player.teleport(lobby);
        logConsole("Lobby teleport: player=" + player.getName()
                + ", op=" + player.isOp()
                + ", modeBefore=" + beforeMode
                + ", modeAfter=" + player.getGameMode()
                + ", loc=" + formatLocation(lobby) + ".");
    }

    public void sendAllPlayersToLobby() {
        World world = getMainWorld();
        if (world == null) {
            return;
        }

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            sendPlayerToLobby(player);
        }
    }

    public void keepWaitingPlayersInLobby() {
        if (isInGame()) {
            return;
        }

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (shouldEnforceLobbyBoundary(player)
                    && (!isInLobby(player) || player.getGameMode() == GameMode.SPECTATOR)) {
                sendPlayerToLobby(player);
            } else if (shouldManageGameMode(player) && player.getGameMode() != GameMode.ADVENTURE) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    public boolean isInLobby(Player player) {
        return isLocationInLobby(player.getLocation());
    }

    public boolean isLocationInLobby(Location location) {
        World world = getMainWorld();
        if (world == null || location == null || !location.getWorld().equals(world)) {
            return false;
        }

        Location lobby = getLobbyLocation(world);
        double radiusSquared = (double) lobbyRadius * lobbyRadius;
        return horizontalDistanceSquared(location, lobby) <= radiusSquared;
    }

    public boolean shouldEnforceLobbyBoundary(Player player) {
        return !player.isOp() && !player.hasPermission("lavarising.use");
    }

    private void prepareArenaForCountdown(World world, ArenaSelection selection, List<Player> participants) {
        if (savedBorderSize != null) {
            restoreBorderState(world);
        }

        WorldBorder border = world.getWorldBorder();
        saveBorderState(border);
        saveDifficultyState(world);
        saveImmediateRespawnState(world);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, false);
        world.setDifficulty(Difficulty.EASY);
        border.setCenter(selection.center().x() + 0.5D, selection.center().z() + 0.5D);
        border.setSize(arenaDiameter);
        world.setPVP(false);

        currentArenaCenter = selection.center();
        rememberUsedArenaCenter(selection.center());
        teleportParticipantsToArena(world, participants, selection.center());

        logConsole("Arena prepared: center=" + selection.center().x() + "," + selection.center().z()
                + ", source=" + selection.sourceLabel()
                + ", biome=" + selection.biome().name()
                + ", border=" + arenaDiameter + "x" + arenaDiameter
                + ", difficulty=EASY"
                + ", participants=" + participants.size() + ".");
        broadcastAll(ChatColor.YELLOW + "Selected arena center: "
                + ChatColor.WHITE + selection.center().x() + ", " + selection.center().z()
                + ChatColor.GRAY + " (" + selection.sourceLabel() + ", " + selection.biome().name() + ")");
        broadcastAll(ChatColor.GRAY + "Players may move during the countdown.");
    }

    private void teleportParticipantsToArena(World world, List<Player> participants, ArenaCenter center) {
        double spawnRadius = Math.max(3.0D, Math.min(8.0D, arenaDiameter / 8.0D));
        int count = Math.max(1, participants.size());

        for (int i = 0; i < participants.size(); i++) {
            Player player = participants.get(i);
            double angle = (Math.PI * 2.0D * i) / count;
            double x = center.x() + 0.5D + Math.cos(angle) * spawnRadius;
            double z = center.z() + 0.5D + Math.sin(angle) * spawnRadius;
            Location spawn = getSafeSurfaceLocation(world, x, z);
            if (spawn == null) {
                spawn = getSafeSurfaceLocation(world, center.x() + 0.5D, center.z() + 0.5D);
            }
            if (spawn == null) {
                spawn = getEmergencyAboveGroundLocation(world, center.x() + 0.5D, center.z() + 0.5D);
                logConsole("Arena spawn fallback used for " + player.getName()
                        + ": no nearby safe surface found, using above-ground emergency location "
                        + formatLocation(spawn) + ".");
            }
            spawn.setYaw((float) Math.toDegrees(Math.atan2(center.z() + 0.5D - spawn.getZ(),
                    center.x() + 0.5D - spawn.getX())) - 90.0F);

            if (shouldManageGameMode(player)) {
                player.setGameMode(GameMode.ADVENTURE);
            }
            player.setFallDistance(0.0F);
            player.setFireTicks(0);
            player.teleport(spawn);
            logConsole("Arena spawn: player=" + player.getName()
                    + ", op=" + player.isOp()
                    + ", gamemode=" + player.getGameMode()
                    + ", loc=" + formatLocation(spawn) + ".");
        }
    }

    private ArenaSelection selectFreshArena(World world) {
        int villageRoll = random.nextInt(100);
        logConsole("Arena selection: villageStartChance=" + villageStartChancePercent
                + "%, roll=" + villageRoll + ".");
        if (villageRoll < villageStartChancePercent) {
            ArenaSelection villageSelection = selectVillageArena(world);
            if (villageSelection != null) {
                return villageSelection;
            }
            logConsole("Arena selection: no valid village found, falling back to surface search.");
        }

        return selectSurfaceArena(world);
    }

    private ArenaSelection selectVillageArena(World world) {
        if (!world.canGenerateStructures()) {
            logConsole("Village arena search skipped: world structures are disabled.");
            return null;
        }

        for (int attempt = 0; attempt < villageSearchAttempts; attempt++) {
            Location origin = getRandomArenaSearchOrigin(world);
            logConsole("Village arena search attempt " + (attempt + 1) + "/" + villageSearchAttempts
                    + ": origin=" + formatLocation(origin) + ".");
            Location village = world.locateNearestStructure(
                    origin,
                    StructureType.VILLAGE,
                    villageSearchRadiusChunks,
                    false);
            if (village == null) {
                logConsole("Village arena search attempt " + (attempt + 1) + ": no village found.");
                continue;
            }

            ArenaCenter center = new ArenaCenter(village.getBlockX(), village.getBlockZ());
            ArenaSelection selection = validateArenaCenter(world, center, "village");
            if (selection != null) {
                logConsole("Village arena accepted: center=" + center.x() + "," + center.z()
                        + ", biome=" + selection.biome().name()
                        + ", attempt=" + (attempt + 1) + ".");
                return selection;
            }
            logConsole("Village arena rejected: center=" + center.x() + "," + center.z()
                    + " failed distance/biome/tree/surface checks.");
        }

        return null;
    }

    private ArenaSelection selectSurfaceArena(World world) {
        int minRadius = Math.min(arenaSearchMinRadius, arenaSearchMaxRadius);
        int maxRadius = Math.max(arenaSearchMinRadius, arenaSearchMaxRadius);
        int radiusRange = Math.max(0, maxRadius - minRadius);

        for (int attempt = 0; attempt < arenaSearchAttempts; attempt++) {
            Location origin = getRandomArenaSearchOrigin(world, minRadius, radiusRange);
            ArenaCenter center = new ArenaCenter(origin.getBlockX(), origin.getBlockZ());
            ArenaSelection selection = validateArenaCenter(world, center, "surface");
            if (selection != null) {
                logConsole("Surface arena accepted: center=" + center.x() + "," + center.z()
                        + ", biome=" + selection.biome().name()
                        + ", attempt=" + (attempt + 1) + "/" + arenaSearchAttempts + ".");
                return selection;
            }
        }

        logConsole("Surface arena search failed after " + arenaSearchAttempts
                + " attempts. Radius range=" + minRadius + "-" + maxRadius + ".");
        return null;
    }

    private Location getRandomArenaSearchOrigin(World world) {
        int minRadius = Math.min(arenaSearchMinRadius, arenaSearchMaxRadius);
        int maxRadius = Math.max(arenaSearchMinRadius, arenaSearchMaxRadius);
        int radiusRange = Math.max(0, maxRadius - minRadius);
        return getRandomArenaSearchOrigin(world, minRadius, radiusRange);
    }

    private Location getRandomArenaSearchOrigin(World world, int minRadius, int radiusRange) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int radius = minRadius + random.nextInt(radiusRange + 1);
        int x = (int) Math.floor(lobbyX + Math.cos(angle) * radius);
        int z = (int) Math.floor(lobbyZ + Math.sin(angle) * radius);
        return new Location(world, x + 0.5D, world.getMinHeight() + 5, z + 0.5D);
    }

    private ArenaSelection validateArenaCenter(World world, ArenaCenter center, String sourceLabel) {
        if (isTooCloseToLobby(center) || isTooCloseToUsedArena(center)) {
            return null;
        }

        Location surface = getSafeSurfaceLocation(world, center.x() + 0.5D, center.z() + 0.5D);
        if (surface == null) {
            return null;
        }

        Biome biome = world.getBlockAt(surface.getBlockX(), surface.getBlockY() - 1, surface.getBlockZ()).getBiome();
        if (!arenaBiomeWhitelist.contains(biome)) {
            return null;
        }

        if (!isPlayableArenaSurface(world, center, surface.getBlockY())) {
            return null;
        }

        return new ArenaSelection(center, biome, sourceLabel);
    }

    private boolean isPlayableArenaSurface(World world, ArenaCenter center, int centerY) {
        int sampleDistance = Math.max(8, Math.min(48, arenaDiameter / 3));
        int[][] samples = {
                {0, 0},
                {sampleDistance, 0},
                {-sampleDistance, 0},
                {0, sampleDistance},
                {0, -sampleDistance}
        };

        for (int[] sample : samples) {
            Location surface = getSafeSurfaceLocation(world, center.x() + sample[0] + 0.5D,
                    center.z() + sample[1] + 0.5D);
            if (surface == null || Math.abs(surface.getBlockY() - centerY) > 18) {
                return false;
            }

            Biome biome = world.getBlockAt(surface.getBlockX(), surface.getBlockY() - 1, surface.getBlockZ())
                    .getBiome();
            if (!arenaBiomeWhitelist.contains(biome)) {
                return false;
            }
        }

        return hasTreeNearby(world, center, centerY);
    }

    private boolean hasTreeNearby(World world, ArenaCenter center, int centerY) {
        if (arenaTreeCheckRadius <= 0) {
            return true;
        }

        int step = 4;
        for (int dx = -arenaTreeCheckRadius; dx <= arenaTreeCheckRadius; dx += step) {
            for (int dz = -arenaTreeCheckRadius; dz <= arenaTreeCheckRadius; dz += step) {
                if ((dx * dx) + (dz * dz) > arenaTreeCheckRadius * arenaTreeCheckRadius) {
                    continue;
                }

                int x = center.x() + dx;
                int z = center.z() + dz;
                int y = getSafeGroundY(world, x, z);
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

    private boolean isTooCloseToLobby(ArenaCenter center) {
        double distanceSquared = distanceSquared(center.x(), center.z(), lobbyX, lobbyZ);
        return distanceSquared < (double) arenaMinDistanceFromLobby * arenaMinDistanceFromLobby;
    }

    private boolean isTooCloseToUsedArena(ArenaCenter center) {
        int minimumDistance = Math.max(arenaMinDistanceFromUsed, arenaDiameter + 64);
        double minimumDistanceSquared = (double) minimumDistance * minimumDistance;
        for (ArenaCenter used : usedArenaCenters) {
            if (distanceSquared(center.x(), center.z(), used.x(), used.z()) < minimumDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private void rememberUsedArenaCenter(ArenaCenter center) {
        usedArenaCenters.add(center);
        plugin.getConfig().set(CONFIG_USED_ARENA_CENTERS,
                usedArenaCenters.stream()
                        .map(used -> used.x() + "," + used.z())
                        .toList());
        plugin.saveConfig();
    }

    private void scheduleCountdownAnnouncements() {
        int[] announcements = {10, 5, 4, 3, 2, 1};
        int countdownSeconds = 10;
        logConsole("Countdown scheduled: " + countdownSeconds + " seconds.");

        for (int secondsLeft : announcements) {
            long delay = (long) (countdownSeconds - secondsLeft) * 20L;
            BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (state == GameState.COUNTDOWN) {
                    broadcastAll(ChatColor.YELLOW + "" + ChatColor.BOLD + "Game starting in "
                            + ChatColor.WHITE + secondsLeft + ChatColor.YELLOW + " second"
                            + (secondsLeft == 1 ? "" : "s") + "!");
                    playWorldSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, secondsLeft <= 3 ? 1.6F : 1.0F);
                }
            }, delay);
            activeTasks.add(task);
        }

        BukkitTask begin = plugin.getServer().getScheduler().runTaskLater(plugin, this::beginGame, countdownSeconds * 20L);
        activeTasks.add(begin);
    }

    private void beginGame() {
        if (state != GameState.COUNTDOWN) {
            return;
        }

        state = GameState.RUNNING;

        releaseWaitingPlayers();
        enableRoundCombat();
        logConsole("Round live: lavaStartY=" + START_LAVA_Y
                + ", maxY=" + MAX_LAVA_Y
                + ", activePlayers=" + activePlayers.size()
                + ", pvp=true"
                + ", phase=" + getActiveLavaPhase().getConfigKey() + ".");
        broadcastAll(ChatColor.DARK_RED + "" + ChatColor.BOLD + "LAVA RISING HAS STARTED!");
        broadcastAll(ChatColor.RED + "Lava starts at Y=" + START_LAVA_Y
                + " and rises to Y=" + MAX_LAVA_Y + " inside the " + arenaDiameter + "x" + arenaDiameter
                + " arena.");
        showSandMayhemTitleForActivePlayers();

        startActionBarLoop();
        scheduleTick();
    }

    private void scheduleTick() {
        if (!isLavaAdvancing()) {
            return;
        }

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            lavaRiseTask = null;
            if (!isLavaAdvancing()) {
                return;
            }

            currentY++;
            int filledBlocks = fillLavaLayer(currentY);
            logLavaLayer(currentY, filledBlocks);
            broadcastMilestone(currentY);
            giveBuildingBlocksForLavaRise();
            checkWinCondition();

            if (!isLavaGameState()) {
                return;
            }

            if (state == GameState.RUNNING && currentY >= FINAL_SLOW_START_Y) {
                startFinalPhase();
                if (!isLavaGameState()) {
                    return;
                }
            }

            if (currentY >= MAX_LAVA_Y) {
                broadcastAll(ChatColor.YELLOW + "Lava reached Y=" + MAX_LAVA_Y + ".");
                if (state == GameState.RUNNING) {
                    enterSuddenDeath();
                }
                return;
            }

            scheduleTick();
        }, getTicksPerLayer());
        lavaRiseTask = task;
        activeTasks.add(task);
    }

    private int getTicksPerLayer() {
        return getEffectiveTicksForPhase(getLavaPhaseForNextLayer());
    }

    private int getEffectiveTicksForPhase(LavaPhase phase) {
        int overrideTicks = lavaPhaseSpeedOverrideTicks[phase.ordinal()];
        return overrideTicks > 0 ? overrideTicks : lavaPhaseSpeedTicks[phase.ordinal()];
    }

    private void rescheduleNextLavaTick() {
        if (!isLavaAdvancing()) {
            return;
        }
        if (lavaRiseTask != null) {
            lavaRiseTask.cancel();
            activeTasks.remove(lavaRiseTask);
            lavaRiseTask = null;
        }
        scheduleTick();
    }

    private void startActionBarLoop() {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (state == GameState.WAITING || state == GameState.COUNTDOWN) {
                return;
            }

            World world = getMainWorld();
            if (world == null) {
                return;
            }

            String pvpLabel = getActionBarPvpLabel(world);
            String message;
            if (state == GameState.SUDDEN_DEATH) {
                message = ChatColor.DARK_RED + "" + ChatColor.BOLD + "SUDDEN DEATH"
                        + ChatColor.GRAY + " | "
                        + ChatColor.RED + "Lava Y: " + ChatColor.WHITE + currentY
                        + ChatColor.GRAY + " | "
                        + pvpLabel
                        + ChatColor.GRAY + " | "
                        + (isLavaAdvancing()
                                ? ChatColor.GOLD + "1 Lava/" + formatSeconds(getTicksPerLayer() / 20.0D) + "sec"
                                + ChatColor.GRAY + " | Border closing"
                                : ChatColor.DARK_RED + "Height Limit"
                                + ChatColor.GRAY + " | Border closing");
            } else {
                message = ChatColor.RED + "Lava Y: " + ChatColor.WHITE + currentY
                        + ChatColor.GRAY + " | "
                        + pvpLabel
                        + ChatColor.GRAY + " | "
                        + ChatColor.GOLD + "1 Lava/" + formatSeconds(getTicksPerLayer() / 20.0D) + "sec"
                        + ChatColor.GRAY;
            }

            for (Player player : world.getPlayers()) {
                player.sendActionBar(message);
            }
        }, 0L, 20L);
        activeTasks.add(task);
    }

    private String getActionBarPvpLabel(World world) {
        return (world.getPVP() ? ChatColor.GREEN + "PVP: ON" : ChatColor.RED + "PVP: OFF");
    }

    private String getActionBarPhaseName() {
        return getActiveLavaPhase().getDisplayName();
    }

    private ChatColor getActionBarPhaseColor() {
        LavaPhase phase = getActiveLavaPhase();
        if (phase == LavaPhase.REACH_314 || phase == LavaPhase.HEIGHT_LIMIT) {
            return ChatColor.DARK_RED;
        }
        if (phase == LavaPhase.REACH_60 || phase == LavaPhase.REACH_100) {
            return ChatColor.GOLD;
        }
        return ChatColor.YELLOW;
    }

    private void startFinalPhase() {
        if (state != GameState.RUNNING) {
            return;
        }

        setHardDifficulty();
        logConsole("Phase 5 started: currentY=" + currentY
                + ", lava continues until Y=" + MAX_LAVA_Y + ".");
        broadcastAll(ChatColor.DARK_RED + "Phase 5 has started at Y=" + currentY + ".");
        enterSuddenDeath();
    }

    private void scheduleSuddenDeathWarning() {
        if (state != GameState.RUNNING) {
            return;
        }

        broadcastAll(ChatColor.DARK_RED + "" + ChatColor.BOLD + "SUDDEN DEATH WARNING!");
        broadcastAll(ChatColor.RED + "The border starts shrinking in " + ChatColor.WHITE
                + suddenDeathDelaySeconds + ChatColor.RED + " seconds.");
        playWorldSound(Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.0F, 0.7F);

        if (suddenDeathDelaySeconds <= 0) {
            enterSuddenDeath();
            return;
        }

        int[] warnings = {240, 180, 120, 60, 30, 10, 5, 4, 3, 2, 1};
        for (int secondsLeft : warnings) {
            if (secondsLeft >= suddenDeathDelaySeconds) {
                continue;
            }

            long delay = (long) (suddenDeathDelaySeconds - secondsLeft) * 20L;
            BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (state == GameState.RUNNING) {
                    broadcastAll(ChatColor.RED + "Sudden death in " + ChatColor.WHITE + secondsLeft
                            + ChatColor.RED + " second" + (secondsLeft == 1 ? "" : "s") + "!");
                    playWorldSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, secondsLeft <= 3 ? 1.6F : 1.0F);
                }
            }, delay);
            activeTasks.add(task);
        }

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(
                plugin,
                this::enterSuddenDeath,
                suddenDeathDelaySeconds * 20L);
        activeTasks.add(task);
    }

    private void enterSuddenDeath() {
        if (state != GameState.RUNNING) {
            return;
        }

        state = GameState.SUDDEN_DEATH;
        logConsole("Sudden death started: currentY=" + currentY
                + ", borderTarget=" + getSuddenDeathDiameter() + "x" + getSuddenDeathDiameter()
                + ", shrinkSeconds=" + suddenDeathSpeedSeconds + ".");
        broadcastAll(ChatColor.DARK_RED + "" + ChatColor.BOLD + "SUDDEN DEATH!");
        broadcastAll(ChatColor.RED + "The world border is shrinking slowly. No potion damage is applied.");
        playWorldSound(Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.0F, 0.7F);

        World world = getMainWorld();
        if (world == null) {
            return;
        }

        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        border.setCenter(center.getX(), center.getZ());
        border.setWarningTimeTicks(15 * 20);
        border.setWarningDistance(5);
        border.setDamageBuffer(borderDamage ? BORDER_DAMAGE_BUFFER : 1000000.0D);
        border.setDamageAmount(borderDamage ? BORDER_DAMAGE_AMOUNT : 0.0D);
        border.setSize(getSuddenDeathDiameter(), TimeUnit.SECONDS, suddenDeathSpeedSeconds);

        broadcastAll(ChatColor.GOLD + "Border shrinking from " + arenaDiameter + "x" + arenaDiameter
                + " to " + getSuddenDeathDiameter() + "x" + getSuddenDeathDiameter()
                + " over " + suddenDeathSpeedSeconds + " seconds.");
    }

    private boolean isLavaAdvancing() {
        return isLavaGameState() && currentY < MAX_LAVA_Y;
    }

    private boolean isLavaGameState() {
        return state == GameState.RUNNING || state == GameState.SUDDEN_DEATH;
    }

    private void chooseBuildingBlockMaterial() {
        sandMayhemRound = random.nextInt(100) < sandMayhemChancePercent;
        buildingBlockMaterial = sandMayhemRound ? Material.SAND : Material.DIRT;
    }

    private void announceSandMayhemRound() {
        if (!sandMayhemRound) {
            return;
        }

        broadcastAll(ChatColor.GOLD + "" + ChatColor.BOLD + "Sand Mayhem!"
                + ChatColor.YELLOW + " This whole round gives sand instead of dirt.");
        playWorldSound(Sound.BLOCK_SAND_PLACE, 1.0F, 0.7F);

        World world = getMainWorld();
        if (world == null) {
            return;
        }

        showSandMayhemTitleForActivePlayers();

        BukkitTask titleReminder = plugin.getServer().getScheduler()
                .runTaskLater(plugin, this::showSandMayhemTitleForActivePlayers, 20L);
        activeTasks.add(titleReminder);
    }

    private void showSandMayhemTitleForActivePlayers() {
        if (!sandMayhemRound) {
            return;
        }

        World world = getMainWorld();
        if (world == null) {
            return;
        }

        for (Player player : world.getPlayers()) {
            if (!activePlayers.contains(player.getUniqueId())) {
                continue;
            }

            player.sendTitle(ChatColor.YELLOW + "" + ChatColor.BOLD + "Sand Mayhem",
                    ChatColor.GOLD + "This round gives sand instead of dirt.",
                    5,
                    90,
                    20);
        }
    }

    private void giveBuildingBlocksForLavaRise() {
        if (!giveDirt) {
            return;
        }
        if (buildingBlockGiveRate <= 0) {
            return;
        }
        if (maxGivenBlocks <= 0) {
            return;
        }

        for (Player player : getAlivePlayers()) {
            int held = capInventoryMaterial(player, buildingBlockMaterial);
            int missing = maxGivenBlocks - held;
            if (missing <= 0) {
                continue;
            }

            int amount = Math.min(buildingBlockGiveRate, missing);
            player.getInventory().addItem(new ItemStack(buildingBlockMaterial, amount));
        }
    }

    private int capInventoryMaterial(Player player, Material material) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        int kept = 0;

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) {
                continue;
            }

            int allowed = maxGivenBlocks - kept;
            if (allowed <= 0) {
                contents[i] = null;
                continue;
            }

            int amount = Math.min(item.getAmount(), allowed);
            item.setAmount(amount);
            kept += amount;
        }

        player.getInventory().setStorageContents(contents);
        return kept;
    }

    private int fillLavaLayer(int y) {
        World world = getMainWorld();
        if (world == null || y < world.getMinHeight() || y >= world.getMaxHeight() || y > MAX_LAVA_Y) {
            return 0;
        }

        ArenaBounds bounds = getArenaBounds(world);
        int minChunkX = Math.floorDiv(bounds.minX(), 16);
        int maxChunkX = Math.floorDiv(bounds.maxXExclusive() - 1, 16);
        int minChunkZ = Math.floorDiv(bounds.minZ(), 16);
        int maxChunkZ = Math.floorDiv(bounds.maxZExclusive() - 1, 16);
        int changed = 0;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }

                int startX = Math.max(bounds.minX(), chunkX * 16);
                int endX = Math.min(bounds.maxXExclusive(), chunkX * 16 + 16);
                int startZ = Math.max(bounds.minZ(), chunkZ * 16);
                int endZ = Math.min(bounds.maxZExclusive(), chunkZ * 16 + 16);
                changed += fillLavaBlocks(world, y, startX, endX, startZ, endZ);
            }
        }
        return changed;
    }

    private int fillLavaBlocks(World world, int y, int startX, int endX, int startZ, int endZ) {
        int changed = 0;
        for (int x = startX; x < endX; x++) {
            for (int z = startZ; z < endZ; z++) {
                Block block = world.getBlockAt(x, y, z);
                if (isLavaFillTarget(block.getType())) {
                    setCoveredBlockToLava(block);
                    changed++;
                }
            }
        }
        return changed;
    }

    public boolean isCoveredByActiveLava(Block block) {
        if (!isLavaGameState()) {
            return false;
        }
        World world = getMainWorld();
        if (world == null || !block.getWorld().equals(world)) {
            return false;
        }
        if (block.getY() < world.getMinHeight() || block.getY() > Math.min(currentY, MAX_LAVA_Y)) {
            return false;
        }

        ArenaBounds bounds = getArenaBounds(world);
        int x = block.getX();
        int z = block.getZ();
        return x >= bounds.minX()
                && x < bounds.maxXExclusive()
                && z >= bounds.minZ()
                && z < bounds.maxZExclusive();
    }

    public boolean forceLavaAtCoveredBlock(Block block) {
        if (!isCoveredByActiveLava(block) || !isLavaFillTarget(block.getType())) {
            return false;
        }

        setCoveredBlockToLava(block);
        return true;
    }

    public int repairCoveredLavaInChunk(Chunk chunk) {
        if (!isLavaGameState()) {
            return 0;
        }

        World world = getMainWorld();
        if (world == null || !chunk.getWorld().equals(world)) {
            return 0;
        }

        int topY = Math.min(currentY, Math.min(MAX_LAVA_Y, world.getMaxHeight() - 1));
        if (topY < world.getMinHeight()) {
            return 0;
        }

        ArenaBounds bounds = getArenaBounds(world);
        int chunkMinX = chunk.getX() * 16;
        int chunkMinZ = chunk.getZ() * 16;
        int startX = Math.max(bounds.minX(), chunkMinX);
        int endX = Math.min(bounds.maxXExclusive(), chunkMinX + 16);
        int startZ = Math.max(bounds.minZ(), chunkMinZ);
        int endZ = Math.min(bounds.maxZExclusive(), chunkMinZ + 16);
        if (startX >= endX || startZ >= endZ) {
            return 0;
        }

        int repaired = 0;
        for (int x = startX; x < endX; x++) {
            for (int z = startZ; z < endZ; z++) {
                for (int y = world.getMinHeight(); y <= topY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (isLavaFillTarget(block.getType())) {
                        setCoveredBlockToLava(block);
                        repaired++;
                    }
                }
            }
        }
        return repaired;
    }

    private ArenaBounds getArenaBounds(World world) {
        Location center = world.getWorldBorder().getCenter();
        int minX = (int) Math.floor(center.getX() - (arenaDiameter / 2.0D));
        int minZ = (int) Math.floor(center.getZ() - (arenaDiameter / 2.0D));
        return new ArenaBounds(minX, minZ, minX + arenaDiameter, minZ + arenaDiameter);
    }

    private void broadcastMilestone(int y) {
        if (y == -30) {
            if (milestoneMessages) {
                broadcastAll(ChatColor.RED + "The lava is climbing out of deepslate.");
            }
        } else if (y == 0) {
            if (milestoneMessages) {
                broadcastAll(ChatColor.RED + "Lava has reached Y=0.");
            }
            playWorldSound(Sound.ENTITY_WARDEN_ROAR, 1.0F, 0.8F);
        } else if (y == 30) {
            if (milestoneMessages) {
                broadcastAll(ChatColor.YELLOW + "Lava has reached Y=30.");
            }
        } else if (y == SURFACE_Y) {
            setHardDifficulty();
            enablePvp();
            playWorldSound(Sound.ENTITY_WITHER_SPAWN, 1.0F, 1.0F);
        } else if (milestoneMessages && y % 20 == 0 && y != SLOWDOWN_Y) {
            broadcastAll(ChatColor.YELLOW + "Lava height: " + ChatColor.WHITE + "Y=" + y);
        }
    }

    private void enablePvp() {
        if (!pvpAtSurface) {
            return;
        }

        enableRoundCombat();
    }

    private void enableRoundCombat() {
        World world = getMainWorld();
        if (world != null) {
            world.setPVP(true);
            logConsole("Round combat enabled: worldPvp=true.");
        }
    }

    private void disableRoundCombat() {
        World world = getMainWorld();
        if (world != null) {
            world.setPVP(false);
            logConsole("Round combat reset: worldPvp=false.");
        }
    }

    public void checkWinCondition() {
        if (state == GameState.WAITING || state == GameState.COUNTDOWN || state == GameState.CELEBRATION) {
            return;
        }

        List<Player> alive = getAlivePlayers();
        if (alive.size() <= 1) {
            logConsole("Win condition met: alivePlayers=" + alive.size()
                    + (alive.isEmpty() ? "" : ", winner=" + alive.get(0).getName()) + ".");
            endGame(alive.isEmpty() ? null : alive.get(0));
        }
    }

    private void endGame(Player winner) {
        logConsole("Ending round: winner=" + (winner == null ? "none" : winner.getName())
                + ", finalY=" + currentY
                + ", eliminated=" + waitingPlayers.size()
                + ", activePlayers=" + activePlayers.size()
                + ", celebrationTime=" + celebrationTimeSeconds + ".");
        stopAllTasks();
        state = GameState.CELEBRATION;

        World world = getMainWorld();
        if (world != null) {
            world.setPVP(false);
        }

        Player spectateTarget = winner;
        if (spectateTarget == null) {
            List<Player> alive = getAlivePlayers();
            if (!alive.isEmpty()) {
                spectateTarget = alive.get(new Random().nextInt(alive.size()));
            }
        }

        for (UUID uuid : waitingPlayers) {
            Player dead = plugin.getServer().getPlayer(uuid);
            if (dead != null && dead.isOnline()) {
                if (!dead.isDead() && shouldManageGameMode(dead)) {
                    dead.setGameMode(GameMode.SPECTATOR);
                }
                if (spectateTarget != null) {
                    dead.teleport(spectateTarget.getLocation());
                    dead.sendMessage(ChatColor.GRAY + "Spectating " + ChatColor.WHITE
                            + spectateTarget.getName() + ChatColor.GRAY + ".");
                }
            }
        }

        if (winner != null) {
            if (shouldManageGameMode(winner)) {
                winner.setGameMode(GameMode.ADVENTURE);
            }
            broadcastAll(ChatColor.GRAY + "Winner: " + ChatColor.WHITE + winner.getName() + ChatColor.GRAY + "!");
        } else {
            broadcastAll(ChatColor.GRAY + "Game ended with no winner.");
        }

        preparePlayersForCelebration(spectateTarget);
        enableCelebrationFlight();
        startWinnerCelebration(winner);
    }

    public void prepareRespawnedPlayerForCelebration(Player player) {
        if (!isCelebrating() || !player.isOnline() || player.isDead()) {
            return;
        }

        Player focus = getCelebrationFocus(player);
        putPlayerInCelebrationMode(player);
        if (focus != null && focus.isOnline()) {
            player.teleport(focus.getLocation());
        }
        player.sendMessage(ChatColor.YELLOW + "The round ended. Joining the winner celebration.");
        logConsole("Respawned player moved into celebration: " + player.getName()
                + ", focus=" + (focus == null ? "none" : focus.getName()) + ".");
    }

    private void preparePlayersForCelebration(Player focus) {
        int prepared = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.isDead()) {
                continue;
            }

            putPlayerInCelebrationMode(player);
            if (focus != null
                    && focus.isOnline()
                    && !player.getUniqueId().equals(focus.getUniqueId())
                    && (waitingPlayers.contains(player.getUniqueId())
                    || player.getGameMode() == GameMode.SPECTATOR)) {
                player.teleport(focus.getLocation());
            }
            prepared++;
        }
        logConsole("Celebration player state prepared: players=" + prepared
                + ", focus=" + (focus == null ? "none" : focus.getName()) + ".");
    }

    private Player getCelebrationFocus(Player excluded) {
        List<Player> alive = getAlivePlayers().stream()
                .filter(player -> excluded == null || !player.getUniqueId().equals(excluded.getUniqueId()))
                .toList();
        if (!alive.isEmpty()) {
            return alive.get(random.nextInt(alive.size()));
        }

        return plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> !player.isDead())
                .filter(player -> excluded == null || !player.getUniqueId().equals(excluded.getUniqueId()))
                .findFirst()
                .orElse(null);
    }

    private void putPlayerInCelebrationMode(Player player) {
        celebrationFlightStates.putIfAbsent(player.getUniqueId(),
                new FlightState(player.getAllowFlight(), player.isFlying()));
        player.setGameMode(GameMode.ADVENTURE);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    private void startWinnerCelebration(Player winner) {
        if (winner == null || celebrationTimeSeconds <= 0) {
            finishRoundAfterCelebration(winner);
            return;
        }

        int totalBursts = Math.max(1, celebrationTimeSeconds * 2);
        int[] bursts = {0};
        broadcastAll(ChatColor.GOLD + "" + ChatColor.BOLD + winner.getName()
                + ChatColor.YELLOW + " wins! Celebrating for "
                + ChatColor.WHITE + celebrationTimeSeconds + ChatColor.YELLOW + " seconds.");
        logConsole("Winner celebration started: winner=" + winner.getName()
                + ", seconds=" + celebrationTimeSeconds
                + ", fireworkBursts=" + totalBursts
                + ", periodTicks=" + CELEBRATION_FIREWORK_PERIOD_TICKS + ".");

        BukkitTask fireworkTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (state != GameState.CELEBRATION || !winner.isOnline() || bursts[0] >= totalBursts) {
                return;
            }

            int fireworkCount = random.nextInt(MAX_CELEBRATION_FIREWORKS_PER_BURST
                    - MIN_CELEBRATION_FIREWORKS_PER_BURST + 1) + MIN_CELEBRATION_FIREWORKS_PER_BURST;
            spawnWinnerFireworks(winner, fireworkCount);
            bursts[0]++;
        }, 0L, CELEBRATION_FIREWORK_PERIOD_TICKS);
        activeTasks.add(fireworkTask);

        BukkitTask finishTask = plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> finishRoundAfterCelebration(winner), celebrationTimeSeconds * 20L);
        activeTasks.add(finishTask);
    }

    private void spawnWinnerFireworks(Player winner, int fireworkCount) {
        Location center = winner.getLocation();
        World world = center.getWorld();
        double angleOffset = random.nextDouble() * Math.PI * 2.0D;

        for (int i = 0; i < fireworkCount; i++) {
            double angle = angleOffset + (Math.PI * 2.0D * i / fireworkCount);
            Location fireworkLocation = center.clone().add(
                    Math.cos(angle) * CELEBRATION_FIREWORK_RADIUS,
                    0.6D,
                    Math.sin(angle) * CELEBRATION_FIREWORK_RADIUS);
            Firework firework = world.spawn(fireworkLocation, Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.setPower(1);
            meta.addEffect(randomFireworkEffect());
            firework.setFireworkMeta(meta);
        }
    }

    private FireworkEffect randomFireworkEffect() {
        FireworkEffect.Type[] types = FireworkEffect.Type.values();
        return FireworkEffect.builder()
                .with(types[random.nextInt(types.length)])
                .withColor(randomFireworkColor(), randomFireworkColor())
                .withFade(randomFireworkColor())
                .flicker(random.nextBoolean())
                .trail(true)
                .build();
    }

    private Color randomFireworkColor() {
        return Color.fromRGB(
                80 + random.nextInt(176),
                80 + random.nextInt(176),
                80 + random.nextInt(176));
    }

    private void enableCelebrationFlight() {
        int enabled = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            celebrationFlightStates.putIfAbsent(player.getUniqueId(),
                    new FlightState(player.getAllowFlight(), player.isFlying()));
            player.setAllowFlight(true);
            player.setFlying(true);
            enabled++;
        }
        logConsole("Celebration flight enabled for " + enabled + " players.");
    }

    private void restoreCelebrationFlightForOnlinePlayers() {
        int restored = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            FlightState flightState = celebrationFlightStates.remove(player.getUniqueId());
            if (flightState != null) {
                if (!flightState.allowFlight()) {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                } else {
                    player.setAllowFlight(true);
                    player.setFlying(flightState.flying());
                }
                restored++;
                continue;
            }

            if (shouldManageGameMode(player)
                    && player.getGameMode() != GameMode.CREATIVE
                    && player.getGameMode() != GameMode.SPECTATOR) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
        celebrationFlightStates.clear();
        logConsole("Celebration flight restored for " + restored + " players.");
    }

    private void cleanupPlayersAfterLobbyReturn() {
        int cleaned = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.setGameMode(GameMode.ADVENTURE);
            player.setFlying(false);
            player.setAllowFlight(false);
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }
            player.setFireTicks(0);
            player.setFallDistance(0.0F);
            cleaned++;
        }
        logConsole("Post-round lobby player cleanup completed: players=" + cleaned + ".");
    }

    private void removeNonPlayerEntities(World world) {
        if (world == null) {
            return;
        }

        int removed = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) {
                continue;
            }

            entity.remove();
            removed++;
        }
        logConsole("Post-round entity cleanup completed: removedNonPlayers=" + removed + ".");
    }

    private void finishRoundAfterCelebration(Player winner) {
        if (state != GameState.CELEBRATION) {
            return;
        }

        logConsole("Winner celebration finished: winner=" + (winner == null ? "none" : winner.getName()) + ".");
        stopAllTasks();

        World world = getMainWorld();
        if (world != null) {
            restoreBorderState(world);
            restoreDifficultyState(world);
            restoreImmediateRespawnState(world);
            world.setPVP(false);
        }

        state = GameState.WAITING;
        sendAllPlayersToLobby();
        restoreCelebrationFlightForOnlinePlayers();
        cleanupPlayersAfterLobbyReturn();
        removeNonPlayerEntities(world);

        waitingPlayers.clear();
        activePlayers.clear();
        resetRoundState();
        currentArenaCenter = null;
    }

    public void stopLava() {
        logConsole("Stopping round manually: state=" + state
                + ", currentY=" + currentY
                + ", activePlayers=" + activePlayers.size()
                + ", waitingPlayers=" + waitingPlayers.size() + ".");
        stopAllTasks();
        state = GameState.WAITING;
        activePlayers.clear();
        waitingPlayers.clear();
        resetRoundState();
        currentArenaCenter = null;
        World world = getMainWorld();
        if (world != null) {
            restoreBorderState(world);
            restoreDifficultyState(world);
            restoreImmediateRespawnState(world);
            world.setPVP(false);
        }
        sendAllPlayersToLobby();
        restoreCelebrationFlightForOnlinePlayers();
        broadcastAll(ChatColor.YELLOW + "Lava rising has been stopped.");
    }

    public void manualReset() {
        logConsole("Manual reset requested: state=" + state
                + ", currentY=" + currentY
                + ", activePlayers=" + activePlayers.size()
                + ", waitingPlayers=" + waitingPlayers.size() + ".");
        stopAllTasks();
        state = GameState.WAITING;
        activePlayers.clear();
        waitingPlayers.clear();
        resetRoundState();
        currentArenaCenter = null;

        World world = getMainWorld();
        if (world == null) {
            return;
        }

        restoreBorderState(world);
        restoreDifficultyState(world);
        restoreImmediateRespawnState(world);
        world.setPVP(false);

        sendAllPlayersToLobby();
        restoreCelebrationFlightForOnlinePlayers();
        broadcastAll(ChatColor.YELLOW + "The lava rising game has been reset. Players returned to lobby.");
    }

    private void releaseWaitingPlayers() {
        int survivalSet = 0;
        int opExempt = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (activePlayers.contains(player.getUniqueId()) && shouldManageGameMode(player)) {
                player.setGameMode(GameMode.SURVIVAL);
                survivalSet++;
            } else if (activePlayers.contains(player.getUniqueId())) {
                opExempt++;
            }
        }
        logConsole("Countdown finished: survivalSet=" + survivalSet
                + ", opGamemodeExempt=" + opExempt + ".");
        waitingPlayers.clear();
    }

    public List<Player> getAlivePlayers() {
        return plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> !isInGame() || activePlayers.contains(player.getUniqueId()))
                .filter(player -> !waitingPlayers.contains(player.getUniqueId()))
                .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                .collect(Collectors.toList());
    }

    private boolean isAliveRoundPlayer(Player player) {
        return player != null
                && player.isOnline()
                && activePlayers.contains(player.getUniqueId())
                && !waitingPlayers.contains(player.getUniqueId())
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    public World getMainWorld() {
        return plugin.getServer().getWorlds().stream()
                .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                .findFirst()
                .orElse(null);
    }

    private int getSafeSurfaceY(World world, int x, int z) {
        int groundY = getSafeGroundY(world, x, z);
        if (groundY != Integer.MIN_VALUE) {
            return groundY + 1;
        }

        return getEmergencyAboveGroundLocation(world, x + 0.5D, z + 0.5D).getBlockY();
    }

    private Location getLobbyLocation(World world) {
        Location location = getSafeSurfaceLocation(world, lobbyX, lobbyZ);
        if (location != null) {
            return location;
        }

        Location spawnLocation = world.getSpawnLocation();
        location = getSafeSurfaceLocation(world, spawnLocation.getX(), spawnLocation.getZ());
        if (location != null) {
            logConsole("Lobby location fallback used: configured lobby was unsafe, using safe world spawn surface "
                    + formatLocation(location) + ".");
            return location;
        }

        Location fallback = getEmergencyAboveGroundLocation(world, lobbyX, lobbyZ);
        logConsole("Lobby emergency fallback used: configured lobby and world spawn were unsafe, using "
                + formatLocation(fallback) + ".");
        return fallback;
    }

    private Location getSafeSurfaceLocation(World world, double x, double z) {
        Location exact = getSafeSurfaceLocationAt(world, x, z);
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

                    Location nearby = getSafeSurfaceLocationAt(world,
                            baseX + dx + 0.5D,
                            baseZ + dz + 0.5D);
                    if (nearby != null) {
                        return nearby;
                    }
                }
            }
        }

        return null;
    }

    private Location getSafeSurfaceLocationAt(World world, double x, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int groundY = getSafeGroundY(world, blockX, blockZ);
        if (groundY == Integer.MIN_VALUE) {
            return null;
        }

        return new Location(world, x, groundY + 1.0D, z);
    }

    private int getSafeGroundY(World world, int x, int z) {
        int highest = getSurfaceTopY(world, x, z);
        int minY = Math.max(MIN_SAFE_SURFACE_SPAWN_Y - 1, highest - MAX_SURFACE_SCAN_DEPTH);
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

    private int getSurfaceTopY(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y <= world.getMinHeight()) {
            y = world.getHighestBlockYAt(x, z);
        }
        return Math.min(y, world.getMaxHeight() - 3);
    }

    private Location getEmergencyAboveGroundLocation(World world, double x, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int y = Math.max(MIN_SAFE_SURFACE_SPAWN_Y + 1, getSurfaceTopY(world, blockX, blockZ) + 1);
        y = Math.min(y, world.getMaxHeight() - 2);
        return new Location(world, x, y, z);
    }

    private boolean isSafeSpawnSurface(Material material) {
        if (!material.isSolid()) {
            return false;
        }

        String name = material.name();
        return !name.endsWith("_LEAVES")
                && !name.endsWith("_LOG")
                && !name.endsWith("_WOOD")
                && !name.endsWith("_STEM")
                && !name.endsWith("_HYPHAE")
                && !name.contains("CACTUS")
                && !name.contains("MAGMA")
                && !name.contains("FIRE")
                && material != Material.LAVA
                && material != Material.WATER;
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

    private double horizontalDistanceSquared(Location first, Location second) {
        return distanceSquared(first.getX(), first.getZ(), second.getX(), second.getZ());
    }

    private double distanceSquared(double firstX, double firstZ, double secondX, double secondZ) {
        double dx = firstX - secondX;
        double dz = firstZ - secondZ;
        return dx * dx + dz * dz;
    }

    private boolean isReplaceable(Material material) {
        return material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR
                || material == Material.WATER
                || material.name().endsWith("_LEAVES")
                || material.name().endsWith("_WOOD");
    }

    private boolean isLavaFillTarget(Material material) {
        return isReplaceable(material) || isDoorOrTrapdoor(material);
    }

    private boolean isDoorOrTrapdoor(Material material) {
        String name = material.name();
        return name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR");
    }

    private void setCoveredBlockToLava(Block block) {
        Material material = block.getType();
        if (isDoorOrTrapdoor(material)) {
            clearMatchingDoorHalf(block.getRelative(BlockFace.UP), material);
            clearMatchingDoorHalf(block.getRelative(BlockFace.DOWN), material);
        }

        block.setType(Material.LAVA, false);
    }

    private void clearMatchingDoorHalf(Block block, Material material) {
        if (block.getType() != material) {
            return;
        }

        block.setType(isCoveredByActiveLava(block) ? Material.LAVA : Material.AIR, false);
    }

    private void logLavaLayer(int y, int filledBlocks) {
        if (!consoleLogging) {
            return;
        }
        if (y == -30 || y == 0 || y == 30 || y == SURFACE_Y || y == 100
                || y == FINAL_SLOW_START_Y || y == MAX_LAVA_Y || y % 20 == 0) {
            logConsole("Lava layer filled: y=" + y
                    + ", changedBlocks=" + filledBlocks
                    + ", phase=" + getCurrentLavaPhase().getConfigKey()
                    + ", alive=" + getAlivePlayers().size() + ".");
        }
    }

    private String formatPlayerNames(List<Player> players) {
        return players.stream()
                .map(Player::getName)
                .collect(Collectors.joining(", "));
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName()
                + " " + formatCoordinate(location.getX())
                + "," + formatCoordinate(location.getY())
                + "," + formatCoordinate(location.getZ());
    }

    private String formatCoordinate(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void clearDroppedItems() {
        World world = getMainWorld();
        if (world == null) {
            return;
        }

        for (Item item : world.getEntitiesByClass(Item.class)) {
            item.remove();
        }
    }

    private void broadcastAll(String message) {
        plugin.getServer().broadcastMessage(message);
    }

    private void playWorldSound(Sound sound, float volume, float pitch) {
        if (!soundsEnabled) {
            return;
        }

        World world = getMainWorld();
        if (world == null) {
            return;
        }

        for (Player player : world.getPlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private void setHardDifficulty() {
        World world = getMainWorld();
        if (world != null) {
            saveDifficultyState(world);
            world.setDifficulty(Difficulty.HARD);
        }
    }

    private void stopAllTasks() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
        lavaRiseTask = null;
    }

    private void resetRoundState() {
        noMoreBottomDwellersActive = false;
        noMoreBottomDwellersClearSeconds = 0;
        clearLavaSpeedBypasses();
        sandMayhemRound = false;
        buildingBlockMaterial = Material.DIRT;
    }

    private void clearLavaSpeedBypasses() {
        for (int i = 0; i < lavaPhaseSpeedOverrideTicks.length; i++) {
            lavaPhaseSpeedOverrideTicks[i] = 0;
        }
    }

    private void loadSettings() {
        arenaDiameter = clamp(getIntSetting(CONFIG_ARENA_DIAMETER, "arena-diameter", DEFAULT_ARENA_DIAMETER),
                MIN_ARENA_DIAMETER,
                MAX_ARENA_DIAMETER);
        loadLavaPhaseSpeeds();
        suddenDeathDelaySeconds = clamp(getIntSetting(CONFIG_SUDDEN_DEATH_DELAY,
                null,
                DEFAULT_SUDDEN_DEATH_DELAY_SECONDS),
                MIN_SUDDEN_DEATH_DELAY_SECONDS,
                MAX_SUDDEN_DEATH_DELAY_SECONDS);
        suddenDeathSpeedSeconds = clamp(getIntSetting(CONFIG_SUDDEN_DEATH_SPEED,
                null,
                DEFAULT_SUDDEN_DEATH_SPEED_SECONDS),
                MIN_SUDDEN_DEATH_SPEED_SECONDS,
                MAX_SUDDEN_DEATH_SPEED_SECONDS);
        suddenDeathRadius = clampSuddenDeathRadius(getIntSetting(CONFIG_SUDDEN_DEATH_RADIUS,
                null,
                DEFAULT_SUDDEN_DEATH_RADIUS));
        celebrationTimeSeconds = clamp(getIntSetting(CONFIG_CELEBRATION_TIME,
                null,
                DEFAULT_CELEBRATION_TIME_SECONDS),
                MIN_CELEBRATION_TIME_SECONDS,
                MAX_CELEBRATION_TIME_SECONDS);
        soundsEnabled = getBooleanSetting(CONFIG_SOUNDS_ENABLED, DEFAULT_SOUNDS_ENABLED);
        milestoneMessages = getBooleanSetting(CONFIG_MILESTONE_MESSAGES, DEFAULT_MILESTONE_MESSAGES);
        giveDirt = getBooleanSetting(CONFIG_GIVE_DIRT, DEFAULT_GIVE_DIRT);
        pvpAtSurface = getBooleanSetting(CONFIG_PVP_AT_SURFACE, DEFAULT_PVP_AT_SURFACE);
        borderDamage = getBooleanSetting(CONFIG_BORDER_DAMAGE, DEFAULT_BORDER_DAMAGE);
        consoleLogging = getBooleanSetting(CONFIG_CONSOLE_LOGGING, DEFAULT_CONSOLE_LOGGING);
        noMoreBottomDwellersSeconds = DEFAULT_NO_MORE_BOTTOM_DWELLERS_SECONDS;
        buildingBlockGiveRate = clamp(getIntSetting(CONFIG_BUILDING_BLOCK_GIVE_RATE,
                null,
                DEFAULT_BUILDING_BLOCK_GIVE_RATE),
                MIN_BUILDING_BLOCK_GIVE_RATE,
                MAX_BUILDING_BLOCK_GIVE_RATE);
        maxGivenBlocks = clamp(getIntSetting(CONFIG_MAX_GIVEN_BLOCKS,
                null,
                DEFAULT_MAX_GIVEN_BLOCKS),
                MIN_MAX_GIVEN_BLOCKS,
                MAX_MAX_GIVEN_BLOCKS);
        sandMayhemChancePercent = clamp(getIntSetting(CONFIG_SAND_MAYHEM_CHANCE,
                null,
                DEFAULT_SAND_MAYHEM_CHANCE_PERCENT),
                MIN_SAND_MAYHEM_CHANCE_PERCENT,
                MAX_SAND_MAYHEM_CHANCE_PERCENT);
        villageStartChancePercent = clamp(getIntSetting(CONFIG_VILLAGE_START_CHANCE,
                null,
                DEFAULT_VILLAGE_START_CHANCE_PERCENT),
                MIN_VILLAGE_START_CHANCE_PERCENT,
                MAX_VILLAGE_START_CHANCE_PERCENT);
        lobbyX = getDoubleSetting(CONFIG_LOBBY_X, null, DEFAULT_LOBBY_X);
        lobbyZ = getDoubleSetting(CONFIG_LOBBY_Z, null, DEFAULT_LOBBY_Z);
        lobbyRadius = clamp(getIntSetting(CONFIG_LOBBY_RADIUS, null, DEFAULT_LOBBY_RADIUS),
                MIN_LOBBY_RADIUS,
                MAX_LOBBY_RADIUS);
        minPlayersToStart = clamp(getIntSetting(CONFIG_MIN_PLAYERS_TO_START, null, DEFAULT_MIN_PLAYERS_TO_START),
                MIN_PLAYERS_TO_START,
                MAX_PLAYERS_TO_START);
        loadArenaSelectionSettings();
        loadUsedArenaCenters();
        saveAllSettings();
        plugin.getConfig().set("arena-diameter", null);
        plugin.getConfig().set("base-speed-seconds", null);
        plugin.saveConfig();
        logConsole("Settings loaded: arenaDiameter=" + arenaDiameter
                + ", minPlayersToStart=" + minPlayersToStart
                + ", lobby=(" + formatCoordinate(lobbyX) + "," + formatCoordinate(lobbyZ)
                + ") radius=" + lobbyRadius
                + ", sandMayhemChance=" + sandMayhemChancePercent + "%"
                + ", villageStartChance=" + villageStartChancePercent + "%"
                + ", maxGivenBlocks=" + maxGivenBlocks
                + ", celebrationTime=" + celebrationTimeSeconds
                + ", consoleLogging=" + consoleLogging + ".");
    }

    private void loadArenaSelectionSettings() {
        arenaMinDistanceFromLobby = clamp(getIntSetting(CONFIG_ARENA_MIN_DISTANCE_FROM_LOBBY,
                null,
                DEFAULT_ARENA_MIN_DISTANCE_FROM_LOBBY),
                MIN_ARENA_DISTANCE,
                MAX_ARENA_DISTANCE);
        arenaMinDistanceFromUsed = clamp(getIntSetting(CONFIG_ARENA_MIN_DISTANCE_FROM_USED,
                null,
                DEFAULT_ARENA_MIN_DISTANCE_FROM_USED),
                MIN_ARENA_DISTANCE,
                MAX_ARENA_DISTANCE);
        arenaSearchMinRadius = clamp(getIntSetting(CONFIG_ARENA_SEARCH_MIN_RADIUS,
                null,
                DEFAULT_ARENA_SEARCH_MIN_RADIUS),
                MIN_ARENA_DISTANCE,
                MAX_ARENA_DISTANCE);
        arenaSearchMaxRadius = clamp(getIntSetting(CONFIG_ARENA_SEARCH_MAX_RADIUS,
                null,
                DEFAULT_ARENA_SEARCH_MAX_RADIUS),
                MIN_ARENA_DISTANCE,
                MAX_ARENA_DISTANCE);
        if (arenaSearchMaxRadius < arenaSearchMinRadius) {
            arenaSearchMaxRadius = arenaSearchMinRadius;
        }
        arenaSearchAttempts = clamp(getIntSetting(CONFIG_ARENA_SEARCH_ATTEMPTS,
                null,
                DEFAULT_ARENA_SEARCH_ATTEMPTS),
                MIN_ARENA_SEARCH_ATTEMPTS,
                MAX_ARENA_SEARCH_ATTEMPTS);
        arenaTreeCheckRadius = clamp(getIntSetting(CONFIG_ARENA_TREE_CHECK_RADIUS,
                null,
                DEFAULT_ARENA_TREE_CHECK_RADIUS),
                MIN_ARENA_TREE_CHECK_RADIUS,
                MAX_ARENA_TREE_CHECK_RADIUS);
        villageSearchRadiusChunks = clamp(getIntSetting(CONFIG_VILLAGE_SEARCH_RADIUS_CHUNKS,
                null,
                DEFAULT_VILLAGE_SEARCH_RADIUS_CHUNKS),
                MIN_VILLAGE_SEARCH_RADIUS_CHUNKS,
                MAX_VILLAGE_SEARCH_RADIUS_CHUNKS);
        villageSearchAttempts = clamp(getIntSetting(CONFIG_VILLAGE_SEARCH_ATTEMPTS,
                null,
                DEFAULT_VILLAGE_SEARCH_ATTEMPTS),
                MIN_VILLAGE_SEARCH_ATTEMPTS,
                MAX_VILLAGE_SEARCH_ATTEMPTS);

        List<String> configuredBiomes = plugin.getConfig().getStringList(CONFIG_ARENA_BIOME_WHITELIST);
        if (configuredBiomes.isEmpty()) {
            configuredBiomes = DEFAULT_ARENA_BIOME_WHITELIST;
        }
        arenaBiomeWhitelist.clear();
        arenaBiomeWhitelist.addAll(parseBiomeWhitelist(configuredBiomes));
    }

    private void loadUsedArenaCenters() {
        usedArenaCenters.clear();
        for (String rawCenter : plugin.getConfig().getStringList(CONFIG_USED_ARENA_CENTERS)) {
            ArenaCenter center = parseArenaCenter(rawCenter);
            if (center != null) {
                usedArenaCenters.add(center);
            }
        }
    }

    private void loadLavaPhaseSpeeds() {
        double baseSpeed = getDoubleSetting(CONFIG_LAVA_RISING_SPEED,
                "base-speed-seconds",
                DEFAULT_LAVA_RISING_SPEED_SECONDS);
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_LAVA_RISING_SPEED);

        for (LavaPhase phase : LavaPhase.values()) {
            double defaultSpeed = getDefaultLavaPhaseSpeedSeconds(phase, baseSpeed);
            double seconds = getLavaPhaseSpeedSetting(section, phase, defaultSpeed);
            lavaPhaseSpeedTicks[phase.ordinal()] = secondsToTicks(seconds);
        }
    }

    private double getLavaPhaseSpeedSetting(ConfigurationSection section, LavaPhase phase, double defaultSpeed) {
        if (section == null) {
            return defaultSpeed;
        }
        if (section.contains(phase.getConfigKey())) {
            return section.getDouble(phase.getConfigKey(), defaultSpeed);
        }
        if (phase == LavaPhase.REACH_314 && section.contains("reach303")) {
            return section.getDouble("reach303", defaultSpeed);
        }
        return defaultSpeed;
    }

    private double getDefaultLavaPhaseSpeedSeconds(LavaPhase phase, double baseSpeed) {
        return switch (phase) {
            case START, REACH_0 -> baseSpeed;
            case REACH_60, REACH_100 -> baseSpeed / 2.0D;
            case REACH_314, HEIGHT_LIMIT -> baseSpeed * 5.0D;
        };
    }

    private int secondsToTicks(double seconds) {
        double clamped = Math.max(MIN_LAVA_RISING_SPEED_SECONDS,
                Math.min(MAX_LAVA_RISING_SPEED_SECONDS, seconds));
        return Math.max(1, (int) Math.round(clamped * 20.0D));
    }

    private int getIntSetting(String key, String legacyKey, int defaultValue) {
        if (plugin.getConfig().contains(key)) {
            return plugin.getConfig().getInt(key, defaultValue);
        }
        if (legacyKey != null && plugin.getConfig().contains(legacyKey)) {
            return plugin.getConfig().getInt(legacyKey, defaultValue);
        }
        return defaultValue;
    }

    private double getDoubleSetting(String key, String legacyKey, double defaultValue) {
        if (plugin.getConfig().contains(key)) {
            return plugin.getConfig().getDouble(key, defaultValue);
        }
        if (legacyKey != null && plugin.getConfig().contains(legacyKey)) {
            return plugin.getConfig().getDouble(legacyKey, defaultValue);
        }
        return defaultValue;
    }

    private boolean getBooleanSetting(String key, boolean defaultValue) {
        if (plugin.getConfig().contains(key)) {
            return plugin.getConfig().getBoolean(key, defaultValue);
        }
        return defaultValue;
    }

    private Set<Biome> parseBiomeWhitelist(List<String> rawBiomes) {
        Set<Biome> parsed = new HashSet<>();
        for (String rawBiome : rawBiomes) {
            for (String token : rawBiome.split(",")) {
                String normalized = token.trim()
                        .replace(" ", "_")
                        .replace("-", "_")
                        .toUpperCase(Locale.ROOT);
                if (normalized.isEmpty()) {
                    continue;
                }

                try {
                    parsed.add(Biome.valueOf(normalized));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ignoring unknown arena biome in config: " + token.trim());
                }
            }
        }

        if (parsed.isEmpty()) {
            parsed.add(Biome.FOREST);
            parsed.add(Biome.BIRCH_FOREST);
        }
        return parsed;
    }

    private ArenaCenter parseArenaCenter(String rawCenter) {
        String[] parts = rawCenter.split(",");
        if (parts.length != 2) {
            return null;
        }

        try {
            return new ArenaCenter(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void saveAllSettings() {
        plugin.getConfig().set(CONFIG_ARENA_DIAMETER, arenaDiameter);
        saveLavaPhaseSpeedSettings();
        plugin.getConfig().set(CONFIG_SUDDEN_DEATH_DELAY, suddenDeathDelaySeconds);
        plugin.getConfig().set(CONFIG_SUDDEN_DEATH_SPEED, suddenDeathSpeedSeconds);
        plugin.getConfig().set(CONFIG_SUDDEN_DEATH_RADIUS, suddenDeathRadius);
        plugin.getConfig().set(CONFIG_CELEBRATION_TIME, celebrationTimeSeconds);
        plugin.getConfig().set(CONFIG_SOUNDS_ENABLED, soundsEnabled);
        plugin.getConfig().set(CONFIG_MILESTONE_MESSAGES, milestoneMessages);
        plugin.getConfig().set(CONFIG_GIVE_DIRT, giveDirt);
        plugin.getConfig().set(CONFIG_PVP_AT_SURFACE, pvpAtSurface);
        plugin.getConfig().set(CONFIG_BORDER_DAMAGE, borderDamage);
        plugin.getConfig().set(CONFIG_CONSOLE_LOGGING, consoleLogging);
        plugin.getConfig().set(CONFIG_NO_MORE_BOTTOM_DWELLERS, noMoreBottomDwellersSeconds);
        plugin.getConfig().set(CONFIG_BUILDING_BLOCK_GIVE_RATE, buildingBlockGiveRate);
        plugin.getConfig().set(CONFIG_MAX_GIVEN_BLOCKS, maxGivenBlocks);
        plugin.getConfig().set(CONFIG_SAND_MAYHEM_CHANCE, sandMayhemChancePercent);
        plugin.getConfig().set(CONFIG_VILLAGE_START_CHANCE, villageStartChancePercent);
        plugin.getConfig().set(CONFIG_LOBBY_X, lobbyX);
        plugin.getConfig().set(CONFIG_LOBBY_Z, lobbyZ);
        plugin.getConfig().set(CONFIG_LOBBY_RADIUS, lobbyRadius);
        plugin.getConfig().set(CONFIG_MIN_PLAYERS_TO_START, minPlayersToStart);
        plugin.getConfig().set(CONFIG_ARENA_MIN_DISTANCE_FROM_LOBBY, arenaMinDistanceFromLobby);
        plugin.getConfig().set(CONFIG_ARENA_MIN_DISTANCE_FROM_USED, arenaMinDistanceFromUsed);
        plugin.getConfig().set(CONFIG_ARENA_SEARCH_MIN_RADIUS, arenaSearchMinRadius);
        plugin.getConfig().set(CONFIG_ARENA_SEARCH_MAX_RADIUS, arenaSearchMaxRadius);
        plugin.getConfig().set(CONFIG_ARENA_SEARCH_ATTEMPTS, arenaSearchAttempts);
        plugin.getConfig().set(CONFIG_ARENA_TREE_CHECK_RADIUS, arenaTreeCheckRadius);
        plugin.getConfig().set(CONFIG_VILLAGE_SEARCH_RADIUS_CHUNKS, villageSearchRadiusChunks);
        plugin.getConfig().set(CONFIG_VILLAGE_SEARCH_ATTEMPTS, villageSearchAttempts);
        plugin.getConfig().set(CONFIG_ARENA_BIOME_WHITELIST,
                arenaBiomeWhitelist.stream().map(Biome::name).sorted().toList());
        if (!plugin.getConfig().contains(CONFIG_USED_ARENA_CENTERS)) {
            plugin.getConfig().set(CONFIG_USED_ARENA_CENTERS, Collections.emptyList());
        }
    }

    private void saveLavaPhaseSpeedSettings() {
        plugin.getConfig().set(CONFIG_LAVA_RISING_SPEED, null);
        for (LavaPhase phase : LavaPhase.values()) {
            plugin.getConfig().set(CONFIG_LAVA_RISING_SPEED + "." + phase.getConfigKey(),
                    getLavaRisingSpeedSeconds(phase));
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clampSuddenDeathRadius(int radius) {
        int maxRadiusForArena = Math.max(MIN_SUDDEN_DEATH_RADIUS, (arenaDiameter - 1) / 2);
        int maxRadius = Math.min(MAX_SUDDEN_DEATH_RADIUS, maxRadiusForArena);
        return clamp(radius, MIN_SUDDEN_DEATH_RADIUS, maxRadius);
    }

    private String formatSeconds(double seconds) {
        if (Math.abs(seconds - Math.rint(seconds)) < 0.0001D) {
            return Integer.toString((int) Math.rint(seconds));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", seconds);
    }

    private String formatAllPhaseSpeedsForLog() {
        return java.util.Arrays.stream(LavaPhase.values())
                .map(phase -> phase.getConfigKey() + "=" + formatSeconds(getLavaRisingSpeedSeconds(phase)) + "s")
                .collect(Collectors.joining(", "));
    }

    private void saveBorderState(WorldBorder border) {
        if (savedBorderSize != null) {
            return;
        }

        savedBorderSize = border.getSize();
        savedBorderCenter = border.getCenter().clone();
        savedBorderDamageBuffer = border.getDamageBuffer();
        savedBorderDamageAmount = border.getDamageAmount();
        savedBorderWarningTimeTicks = border.getWarningTimeTicks();
        savedBorderWarningDistance = border.getWarningDistance();
    }

    private void restoreBorderState(World world) {
        if (savedBorderSize == null) {
            return;
        }

        WorldBorder border = world.getWorldBorder();
        if (savedBorderCenter != null) {
            border.setCenter(savedBorderCenter.getX(), savedBorderCenter.getZ());
        }
        border.setSize(savedBorderSize);
        border.setDamageBuffer(savedBorderDamageBuffer);
        border.setDamageAmount(savedBorderDamageAmount);
        border.setWarningTimeTicks(savedBorderWarningTimeTicks);
        border.setWarningDistance(savedBorderWarningDistance);

        savedBorderSize = null;
        savedBorderCenter = null;
        savedBorderDamageBuffer = null;
        savedBorderDamageAmount = null;
        savedBorderWarningTimeTicks = null;
        savedBorderWarningDistance = null;
    }

    private void saveDifficultyState(World world) {
        if (savedDifficulty != null) {
            return;
        }

        savedDifficulty = world.getDifficulty();
    }

    private void restoreDifficultyState(World world) {
        if (savedDifficulty == null) {
            return;
        }

        world.setDifficulty(savedDifficulty);
        savedDifficulty = null;
    }

    private void saveImmediateRespawnState(World world) {
        if (savedImmediateRespawn != null) {
            return;
        }

        savedImmediateRespawn = world.getGameRuleValue(GameRule.DO_IMMEDIATE_RESPAWN);
    }

    private void restoreImmediateRespawnState(World world) {
        if (savedImmediateRespawn == null) {
            return;
        }

        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, savedImmediateRespawn);
        savedImmediateRespawn = null;
    }

    private LavaPhase getLavaPhaseForNextLayer() {
        int nextY = Math.min(currentY + 1, MAX_LAVA_Y);
        if (nextY >= MAX_LAVA_Y) {
            return LavaPhase.REACH_314;
        }
        return getLavaPhaseForY(nextY);
    }

    private LavaPhase getLavaPhaseForY(int y) {
        if (y >= MAX_LAVA_Y) {
            return LavaPhase.HEIGHT_LIMIT;
        }
        if (y >= FINAL_SLOW_START_Y) {
            return LavaPhase.REACH_314;
        }
        if (y >= 100) {
            return LavaPhase.REACH_100;
        }
        if (y >= SURFACE_Y) {
            return LavaPhase.REACH_60;
        }
        if (y >= 0) {
            return LavaPhase.REACH_0;
        }
        return LavaPhase.START;
    }

    public enum LavaPhase {
        START(1, "start", "Start (-64)"),
        REACH_0(2, "reach0", "Reach 0"),
        REACH_60(3, "reach60", "Reach 60"),
        REACH_100(4, "reach100", "Reach 100"),
        REACH_314(5, "reach314", "Reach 314"),
        HEIGHT_LIMIT(6, "heightLimit", "Height Limit");

        private final int id;
        private final String configKey;
        private final String displayName;

        LavaPhase(int id, String configKey, String displayName) {
            this.id = id;
            this.configKey = configKey;
            this.displayName = displayName;
        }

        public int getId() {
            return id;
        }

        public String getConfigKey() {
            return configKey;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static LavaPhase fromInput(String input) {
            String normalized = input.replace("-", "")
                    .replace("_", "")
                    .replace(".", "")
                    .toLowerCase(java.util.Locale.ROOT);
            for (LavaPhase phase : values()) {
                if (Integer.toString(phase.id).equals(normalized)
                        || phase.configKey.toLowerCase(java.util.Locale.ROOT).equals(normalized)
                        || phase.displayName.replace(" ", "")
                                .replace("(", "")
                                .replace(")", "")
                                .toLowerCase(java.util.Locale.ROOT)
                                .equals(normalized)) {
                    return phase;
                }
            }
            if (normalized.equals("reach303") || normalized.equals("303")) {
                return REACH_314;
            }
            return null;
        }
    }

    public enum GameState {
        WAITING,
        COUNTDOWN,
        RUNNING,
        SUDDEN_DEATH,
        CELEBRATION
    }

    public enum StartResult {
        STARTED,
        ALREADY_RUNNING,
        COUNTDOWN_ACTIVE,
        NO_WORLD,
        NO_LOBBY_PLAYERS,
        NO_ARENA_FOUND
    }

    public record ReviveResult(ReviveStatus status, Player anchor) {
    }

    public enum ReviveStatus {
        REVIVED,
        NOT_RUNNING,
        PLAYER_NOT_ONLINE,
        PLAYER_NOT_RESPAWNED,
        ALREADY_ALIVE,
        ANCHOR_NOT_ALIVE,
        NO_ALIVE_ANCHOR
    }

    private record ArenaBounds(int minX, int minZ, int maxXExclusive, int maxZExclusive) {
    }

    private record ArenaCenter(int x, int z) {
    }

    private record ArenaSelection(ArenaCenter center, Biome biome, String sourceLabel) {
    }

    private record FlightState(boolean allowFlight, boolean flying) {
    }
}
