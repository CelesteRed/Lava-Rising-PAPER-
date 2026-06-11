package com.lavarising.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public class LavaRisingManager {
    public static final int SURFACE_Y = 60;

    private static final int START_LAVA_Y = -64;
    private static final int MAX_LAVA_Y = 319;
    private static final int FINAL_SLOW_START_Y = 314;
    private static final int SLOWDOWN_Y = FINAL_SLOW_START_Y - 1;
    private static final int EARLY_BUILD_LOCK_Y = 100;
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
    private static final int DEFAULT_NO_MORE_BOTTOM_DWELLERS_SECONDS = 60;
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
    private static final int MIN_NO_MORE_BOTTOM_DWELLERS_SECONDS = 0;
    private static final int MAX_NO_MORE_BOTTOM_DWELLERS_SECONDS = 3600;
    private static final int MIN_BUILDING_BLOCK_GIVE_RATE = 0;
    private static final int MAX_BUILDING_BLOCK_GIVE_RATE = 64;
    private static final int MAX_BUILDING_BLOCKS_HELD = 64;
    private static final double SAND_ROUND_CHANCE = 0.10D;
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
    private static final String CONFIG_NO_MORE_BOTTOM_DWELLERS = "noMoreBottomDwellers";
    private static final String CONFIG_BUILDING_BLOCK_GIVE_RATE = "buildingBlockGiveRate";

    private GameState state = GameState.WAITING;
    private int currentY = START_LAVA_Y;
    private final List<BukkitTask> activeTasks = new ArrayList<>();
    private final Set<UUID> waitingPlayers = new HashSet<>();
    private final LavaRisingPlugin plugin;
    private Double savedBorderSize;
    private Location savedBorderCenter;
    private Double savedBorderDamageBuffer;
    private Double savedBorderDamageAmount;
    private Integer savedBorderWarningTimeTicks;
    private Integer savedBorderWarningDistance;
    private Difficulty savedDifficulty;
    private int arenaDiameter;
    private final int[] lavaPhaseSpeedTicks = new int[LavaPhase.values().length];
    private final int[] lavaPhaseSpeedOverrideTicks = new int[LavaPhase.values().length];
    private BukkitTask lavaRiseTask;
    private int suddenDeathDelaySeconds;
    private int suddenDeathSpeedSeconds;
    private int suddenDeathRadius;
    private int noMoreBottomDwellersSeconds;
    private int noMoreBottomDwellersClearSeconds;
    private int buildingBlockGiveRate;
    private boolean noMoreBottomDwellersActive;
    private boolean sandMayhemRound;
    private Material buildingBlockMaterial = Material.DIRT;
    private boolean soundsEnabled;
    private boolean milestoneMessages;
    private boolean giveDirt;
    private boolean pvpAtSurface;
    private boolean borderDamage;

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

    public boolean isCountdown() {
        return state == GameState.COUNTDOWN;
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
        if (state == GameState.RUNNING) {
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

    public int getNoMoreBottomDwellersSeconds() {
        return noMoreBottomDwellersSeconds;
    }

    public int getBuildingBlockGiveRate() {
        return buildingBlockGiveRate;
    }

    public Material getBuildingBlockMaterial() {
        return buildingBlockMaterial;
    }

    public boolean isSandMayhemRound() {
        return sandMayhemRound;
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
    }

    public void setLavaRisingSpeedSeconds(double seconds) {
        double clamped = Math.max(MIN_LAVA_RISING_SPEED_SECONDS, Math.min(MAX_LAVA_RISING_SPEED_SECONDS, seconds));
        for (LavaPhase phase : LavaPhase.values()) {
            lavaPhaseSpeedTicks[phase.ordinal()] = secondsToTicks(clamped);
        }
        saveLavaPhaseSpeedSettings();
        plugin.saveConfig();
    }

    public void setLavaRisingSpeedSeconds(LavaPhase phase, double seconds) {
        double clamped = Math.max(MIN_LAVA_RISING_SPEED_SECONDS, Math.min(MAX_LAVA_RISING_SPEED_SECONDS, seconds));
        lavaPhaseSpeedTicks[phase.ordinal()] = secondsToTicks(clamped);
        plugin.getConfig().set(CONFIG_LAVA_RISING_SPEED + "." + phase.getConfigKey(),
                getLavaRisingSpeedSeconds(phase));
        plugin.saveConfig();
    }

    public void setBaseSpeedSeconds(double seconds) {
        setLavaRisingSpeedSeconds(seconds);
    }

    public void setSuddenDeathDelaySeconds(int seconds) {
        suddenDeathDelaySeconds = clamp(seconds, MIN_SUDDEN_DEATH_DELAY_SECONDS, MAX_SUDDEN_DEATH_DELAY_SECONDS);
        plugin.getConfig().set(CONFIG_SUDDEN_DEATH_DELAY, suddenDeathDelaySeconds);
        plugin.saveConfig();
    }

    public void setSuddenDeathSpeedSeconds(int seconds) {
        suddenDeathSpeedSeconds = clamp(seconds, MIN_SUDDEN_DEATH_SPEED_SECONDS, MAX_SUDDEN_DEATH_SPEED_SECONDS);
        plugin.getConfig().set(CONFIG_SUDDEN_DEATH_SPEED, suddenDeathSpeedSeconds);
        plugin.saveConfig();
    }

    public void setSuddenDeathRadius(int radius) {
        suddenDeathRadius = clampSuddenDeathRadius(radius);
        plugin.getConfig().set(CONFIG_SUDDEN_DEATH_RADIUS, suddenDeathRadius);
        plugin.saveConfig();
    }

    public void setSoundsEnabled(boolean enabled) {
        soundsEnabled = enabled;
        plugin.getConfig().set(CONFIG_SOUNDS_ENABLED, soundsEnabled);
        plugin.saveConfig();
    }

    public void setMilestoneMessagesEnabled(boolean enabled) {
        milestoneMessages = enabled;
        plugin.getConfig().set(CONFIG_MILESTONE_MESSAGES, milestoneMessages);
        plugin.saveConfig();
    }

    public void setGiveDirtEnabled(boolean enabled) {
        giveDirt = enabled;
        plugin.getConfig().set(CONFIG_GIVE_DIRT, giveDirt);
        plugin.saveConfig();
    }

    public void setPvpAtSurfaceEnabled(boolean enabled) {
        pvpAtSurface = enabled;
        plugin.getConfig().set(CONFIG_PVP_AT_SURFACE, pvpAtSurface);
        plugin.saveConfig();
    }

    public void setBorderDamageEnabled(boolean enabled) {
        borderDamage = enabled;
        plugin.getConfig().set(CONFIG_BORDER_DAMAGE, borderDamage);
        plugin.saveConfig();
    }

    public void setNoMoreBottomDwellersSeconds(int seconds) {
        noMoreBottomDwellersSeconds = clamp(seconds,
                MIN_NO_MORE_BOTTOM_DWELLERS_SECONDS,
                MAX_NO_MORE_BOTTOM_DWELLERS_SECONDS);
        plugin.getConfig().set(CONFIG_NO_MORE_BOTTOM_DWELLERS, noMoreBottomDwellersSeconds);
        plugin.saveConfig();
    }

    public void setBuildingBlockGiveRate(int blocksPerLavaRise) {
        buildingBlockGiveRate = clamp(blocksPerLavaRise,
                MIN_BUILDING_BLOCK_GIVE_RATE,
                MAX_BUILDING_BLOCK_GIVE_RATE);
        plugin.getConfig().set(CONFIG_BUILDING_BLOCK_GIVE_RATE, buildingBlockGiveRate);
        plugin.saveConfig();
    }

    public Set<UUID> getWaitingPlayers() {
        return Collections.unmodifiableSet(waitingPlayers);
    }

    public void addWaitingPlayer(UUID uuid) {
        waitingPlayers.add(uuid);
    }

    public void startLava() {
        if (state != GameState.WAITING) {
            return;
        }

        stopAllTasks();
        state = GameState.COUNTDOWN;
        waitingPlayers.clear();
        currentY = START_LAVA_Y;
        noMoreBottomDwellersActive = false;
        noMoreBottomDwellersClearSeconds = 0;
        clearLavaSpeedBypasses();
        sandMayhemRound = false;
        buildingBlockMaterial = Material.DIRT;
        scheduleCountdownAnnouncements();
    }

    private void scheduleCountdownAnnouncements() {
        int[] announcements = {10, 5, 4, 3, 2, 1};
        int countdownSeconds = 10;

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
        World world = getMainWorld();
        if (world != null) {
            if (savedBorderSize != null) {
                restoreBorderState(world);
            }
            WorldBorder border = world.getWorldBorder();
            saveBorderState(border);
            saveDifficultyState(world);
            Location center = border.getCenter();
            border.setCenter(center.getX(), center.getZ());
            border.setSize(arenaDiameter);
            world.setPVP(false);
        }

        chooseBuildingBlockMaterial();
        releaseWaitingPlayers();
        broadcastAll(ChatColor.DARK_RED + "" + ChatColor.BOLD + "LAVA RISING HAS STARTED!");
        broadcastAll(ChatColor.RED + "Lava starts at Y=" + START_LAVA_Y
                + " and rises to Y=" + MAX_LAVA_Y + " inside the " + arenaDiameter + "x" + arenaDiameter
                + " arena.");
        announceSandMayhemRound();

        startActionBarLoop();
        scheduleTick();
    }

    private void scheduleTick() {
        if (state != GameState.RUNNING) {
            return;
        }

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            lavaRiseTask = null;
            if (state != GameState.RUNNING) {
                return;
            }

            currentY++;
            fillLavaLayer(currentY);
            clearDroppedItems();
            broadcastMilestone(currentY);
            giveBuildingBlocksForLavaRise();
            checkWinCondition();

            if (state != GameState.RUNNING) {
                return;
            }

            if (currentY >= FINAL_SLOW_START_Y) {
                startFinalPhase(false);
                return;
            }

            if (currentY >= MAX_LAVA_Y) {
                broadcastAll(ChatColor.YELLOW + "Lava reached Y=" + MAX_LAVA_Y + ".");
                enterSuddenDeath();
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
        if (state != GameState.RUNNING) {
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

            if (state == GameState.RUNNING) {
                updateNoMoreBottomDwellers();
            }

            String message;
            if (state == GameState.SUDDEN_DEATH) {
                message = ChatColor.DARK_RED + "" + ChatColor.BOLD + "SUDDEN DEATH"
                        + ChatColor.GRAY + " | "
                        + ChatColor.RED + "Lava Y: " + ChatColor.WHITE + currentY
                        + ChatColor.GRAY + " | Border closing";
            } else {
                message = ChatColor.RED + "Lava Y: " + ChatColor.WHITE + currentY
                        + ChatColor.GRAY + " | "
                        + getActionBarPhaseColor() + getActionBarPhaseName()
                        + ChatColor.GRAY + " ("
                        + ChatColor.GOLD + "1 Lava/" + formatSeconds(getTicksPerLayer() / 20.0D) + "sec"
                        + ChatColor.GRAY + ")";
            }

            World world = getMainWorld();
            if (world == null) {
                return;
            }

            for (Player player : world.getPlayers()) {
                player.sendActionBar(message);
            }
        }, 0L, 20L);
        activeTasks.add(task);
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

    private void updateNoMoreBottomDwellers() {
        if (noMoreBottomDwellersSeconds <= 0 || noMoreBottomDwellersActive || currentY >= FINAL_SLOW_START_Y) {
            return;
        }

        List<Player> alive = getAlivePlayers();
        if (alive.isEmpty()) {
            noMoreBottomDwellersClearSeconds = 0;
            return;
        }

        boolean anyBottomDweller = alive.stream()
                .anyMatch(player -> player.getLocation().getY() < EARLY_BUILD_LOCK_Y);
        if (anyBottomDweller) {
            noMoreBottomDwellersClearSeconds = 0;
            return;
        }

        noMoreBottomDwellersClearSeconds++;
        if (noMoreBottomDwellersClearSeconds >= noMoreBottomDwellersSeconds) {
            noMoreBottomDwellersActive = true;
            startFinalPhase(true);
        }
    }

    private void startFinalPhase(boolean fromNoMoreBottomDwellers) {
        if (state != GameState.RUNNING) {
            return;
        }

        setHardDifficulty();
        if (fromNoMoreBottomDwellers) {
            broadcastAll(ChatColor.DARK_RED + "No more bottom dwellers. Phase 5 has started early.");
        } else {
            broadcastAll(ChatColor.DARK_RED + "Phase 5 has started at Y=" + currentY + ".");
        }
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

    private void chooseBuildingBlockMaterial() {
        sandMayhemRound = new Random().nextDouble() < SAND_ROUND_CHANCE;
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

        for (Player player : world.getPlayers()) {
            player.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "Sand Mayhem",
                    ChatColor.YELLOW + "This round gives sand instead of dirt.",
                    10,
                    70,
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

        for (Player player : getAlivePlayers()) {
            int held = capInventoryMaterial(player, buildingBlockMaterial);
            int missing = MAX_BUILDING_BLOCKS_HELD - held;
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

            int allowed = MAX_BUILDING_BLOCKS_HELD - kept;
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

    private void fillLavaLayer(int y) {
        World world = getMainWorld();
        if (world == null || y < world.getMinHeight() || y >= world.getMaxHeight() || y > MAX_LAVA_Y) {
            return;
        }

        ArenaBounds bounds = getArenaBounds(world);
        int minChunkX = Math.floorDiv(bounds.minX(), 16);
        int maxChunkX = Math.floorDiv(bounds.maxXExclusive() - 1, 16);
        int minChunkZ = Math.floorDiv(bounds.minZ(), 16);
        int maxChunkZ = Math.floorDiv(bounds.maxZExclusive() - 1, 16);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }

                int startX = Math.max(bounds.minX(), chunkX * 16);
                int endX = Math.min(bounds.maxXExclusive(), chunkX * 16 + 16);
                int startZ = Math.max(bounds.minZ(), chunkZ * 16);
                int endZ = Math.min(bounds.maxZExclusive(), chunkZ * 16 + 16);
                fillLavaBlocks(world, y, startX, endX, startZ, endZ);
            }
        }
    }

    private void fillLavaBlocks(World world, int y, int startX, int endX, int startZ, int endZ) {
        for (int x = startX; x < endX; x++) {
            for (int z = startZ; z < endZ; z++) {
                Block block = world.getBlockAt(x, y, z);
                if (isReplaceable(block.getType())) {
                    block.setType(Material.LAVA, false);
                }
            }
        }
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

        World world = getMainWorld();
        if (world != null) {
            world.setPVP(true);
        }
    }

    public void checkWinCondition() {
        if (state == GameState.WAITING || state == GameState.COUNTDOWN) {
            return;
        }

        List<Player> alive = getAlivePlayers();
        if (alive.size() <= 1) {
            endGame(alive.isEmpty() ? null : alive.get(0));
        }
    }

    private void endGame(Player winner) {
        stopAllTasks();
        state = GameState.WAITING;
        resetRoundState();

        World world = getMainWorld();
        if (world != null) {
            restoreBorderState(world);
            restoreDifficultyState(world);
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
                dead.setGameMode(GameMode.SPECTATOR);
                if (spectateTarget != null) {
                    dead.teleport(spectateTarget.getLocation());
                    dead.sendMessage(ChatColor.GRAY + "Spectating " + ChatColor.WHITE
                            + spectateTarget.getName() + ChatColor.GRAY + ".");
                }
            }
        }

        if (winner != null) {
            winner.setGameMode(GameMode.ADVENTURE);
            broadcastAll(ChatColor.GRAY + "Winner: " + ChatColor.WHITE + winner.getName() + ChatColor.GRAY + "!");
        } else {
            broadcastAll(ChatColor.GRAY + "Game ended with no winner.");
        }

        waitingPlayers.clear();
    }

    public void stopLava() {
        stopAllTasks();
        state = GameState.WAITING;
        waitingPlayers.clear();
        resetRoundState();
        World world = getMainWorld();
        if (world != null) {
            restoreBorderState(world);
            restoreDifficultyState(world);
            world.setPVP(false);
        }
        broadcastAll(ChatColor.YELLOW + "Lava rising has been stopped.");
    }

    public void manualReset() {
        stopAllTasks();
        state = GameState.WAITING;
        waitingPlayers.clear();
        resetRoundState();

        World world = getMainWorld();
        if (world == null) {
            return;
        }

        restoreBorderState(world);
        restoreDifficultyState(world);
        world.setPVP(false);

        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        Random random = new Random();

        for (Player player : world.getPlayers()) {
            player.setGameMode(GameMode.ADVENTURE);

            int radius = Math.max(1, arenaDiameter / 2);
            double x = center.getX() + random.nextInt(radius * 2) - radius;
            double z = center.getZ() + random.nextInt(radius * 2) - radius;
            int safeY = getSafeSurfaceY(world, (int) x, (int) z);
            player.teleport(new Location(world, x, safeY, z));
            player.sendMessage(ChatColor.YELLOW + "Reset complete. New position: " + (int) x + ", " + (int) z);
        }

        broadcastAll(ChatColor.YELLOW + "The lava rising game has been reset.");
    }

    private void releaseWaitingPlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.ADVENTURE) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
        waitingPlayers.clear();
    }

    public List<Player> getAlivePlayers() {
        return plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> !waitingPlayers.contains(player.getUniqueId()))
                .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                .collect(Collectors.toList());
    }

    public World getMainWorld() {
        return plugin.getServer().getWorlds().stream()
                .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                .findFirst()
                .orElse(null);
    }

    private int getSafeSurfaceY(World world, int x, int z) {
        int highest = world.getHighestBlockYAt(x, z);
        return Math.max(highest + 1, world.getMinHeight() + 5);
    }

    private boolean isReplaceable(Material material) {
        return material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR
                || material == Material.WATER
                || material.name().endsWith("_LEAVES")
                || material.name().endsWith("_WOOD");
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
        soundsEnabled = getBooleanSetting(CONFIG_SOUNDS_ENABLED, DEFAULT_SOUNDS_ENABLED);
        milestoneMessages = getBooleanSetting(CONFIG_MILESTONE_MESSAGES, DEFAULT_MILESTONE_MESSAGES);
        giveDirt = getBooleanSetting(CONFIG_GIVE_DIRT, DEFAULT_GIVE_DIRT);
        pvpAtSurface = getBooleanSetting(CONFIG_PVP_AT_SURFACE, DEFAULT_PVP_AT_SURFACE);
        borderDamage = getBooleanSetting(CONFIG_BORDER_DAMAGE, DEFAULT_BORDER_DAMAGE);
        noMoreBottomDwellersSeconds = clamp(getIntSetting(CONFIG_NO_MORE_BOTTOM_DWELLERS,
                null,
                DEFAULT_NO_MORE_BOTTOM_DWELLERS_SECONDS),
                MIN_NO_MORE_BOTTOM_DWELLERS_SECONDS,
                MAX_NO_MORE_BOTTOM_DWELLERS_SECONDS);
        buildingBlockGiveRate = clamp(getIntSetting(CONFIG_BUILDING_BLOCK_GIVE_RATE,
                null,
                DEFAULT_BUILDING_BLOCK_GIVE_RATE),
                MIN_BUILDING_BLOCK_GIVE_RATE,
                MAX_BUILDING_BLOCK_GIVE_RATE);
        saveAllSettings();
        plugin.getConfig().set("arena-diameter", null);
        plugin.getConfig().set("base-speed-seconds", null);
        plugin.saveConfig();
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

    private void saveAllSettings() {
        plugin.getConfig().set(CONFIG_ARENA_DIAMETER, arenaDiameter);
        saveLavaPhaseSpeedSettings();
        plugin.getConfig().set(CONFIG_SUDDEN_DEATH_DELAY, suddenDeathDelaySeconds);
        plugin.getConfig().set(CONFIG_SUDDEN_DEATH_SPEED, suddenDeathSpeedSeconds);
        plugin.getConfig().set(CONFIG_SUDDEN_DEATH_RADIUS, suddenDeathRadius);
        plugin.getConfig().set(CONFIG_SOUNDS_ENABLED, soundsEnabled);
        plugin.getConfig().set(CONFIG_MILESTONE_MESSAGES, milestoneMessages);
        plugin.getConfig().set(CONFIG_GIVE_DIRT, giveDirt);
        plugin.getConfig().set(CONFIG_PVP_AT_SURFACE, pvpAtSurface);
        plugin.getConfig().set(CONFIG_BORDER_DAMAGE, borderDamage);
        plugin.getConfig().set(CONFIG_NO_MORE_BOTTOM_DWELLERS, noMoreBottomDwellersSeconds);
        plugin.getConfig().set(CONFIG_BUILDING_BLOCK_GIVE_RATE, buildingBlockGiveRate);
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

    private LavaPhase getLavaPhaseForNextLayer() {
        int nextY = Math.min(currentY + 1, MAX_LAVA_Y);
        if (nextY >= MAX_LAVA_Y) {
            return LavaPhase.REACH_314;
        }
        if (noMoreBottomDwellersActive && nextY < FINAL_SLOW_START_Y) {
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
        SUDDEN_DEATH
    }

    private record ArenaBounds(int minX, int minZ, int maxXExclusive, int maxZExclusive) {
    }
}
