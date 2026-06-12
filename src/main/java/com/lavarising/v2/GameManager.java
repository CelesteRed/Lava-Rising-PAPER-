package com.lavarising.v2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class GameManager {
    private final LavaRisingPlugin plugin;
    private final ArenaService arenaService;
    private final ArenaStore arenaStore;
    private final Random random = new Random();
    private final Set<UUID> activePlayers = new HashSet<>();
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Set<ChunkKey> forceLoadedChunks = new HashSet<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<UUID, FlightState> savedFlightStates = new HashMap<>();
    private final Queue<ChunkKey> integrityQueue = new ArrayDeque<>();
    private final Map<ChunkKey, Integer> integrityRepairCursor = new HashMap<>();

    private GameState state = GameState.WAITING;
    private int currentY = -65;
    private ArenaCenter arenaCenter;
    private ArenaBounds arenaBounds;
    private Material buildingBlock = Material.DIRT;
    private boolean sandMayhemRound;
    private Double lavaSpeedBypassSeconds;
    private Double savedBorderSize;
    private Location savedBorderCenter;
    private Difficulty savedDifficulty;
    private Boolean savedPvp;
    private Boolean savedImmediateRespawn;

    public GameManager(LavaRisingPlugin plugin, ArenaService arenaService, ArenaStore arenaStore) {
        this.plugin = plugin;
        this.arenaService = arenaService;
        this.arenaStore = arenaStore;
    }

    public GameState state() {
        return state;
    }

    public int currentY() {
        return currentY;
    }

    public boolean isWaiting() {
        return state == GameState.WAITING;
    }

    public boolean isRoundActive() {
        return state == GameState.COUNTDOWN || state == GameState.RUNNING || state == GameState.DEATHMATCH;
    }

    public boolean isLavaRising() {
        return state == GameState.RUNNING || state == GameState.DEATHMATCH;
    }

    public boolean isCombatLive() {
        return state == GameState.RUNNING || state == GameState.DEATHMATCH;
    }

    public boolean isActivePlayer(Player player) {
        return activePlayers.contains(player.getUniqueId());
    }

    public boolean isEliminated(Player player) {
        return eliminatedPlayers.contains(player.getUniqueId());
    }

    public boolean shouldManageGameMode(Player player) {
        return !isAdmin(player);
    }

    public boolean shouldEnforceLobbyBoundary(Player player) {
        return !isAdmin(player);
    }

    public boolean isAdmin(org.bukkit.command.CommandSender sender) {
        return sender.isOp() || sender.hasPermission("lavarising.admin");
    }

    public List<Player> lobbyPlayers() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                .filter(player -> arenaService.isInLobby(player.getLocation()))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    public List<Player> alivePlayers() {
        return activePlayers.stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline() && !player.isDead())
                .filter(player -> !eliminatedPlayers.contains(player.getUniqueId()))
                .collect(Collectors.toList());
    }

    public StartResult startRound() {
        if (state != GameState.WAITING) {
            return StartResult.ALREADY_ACTIVE;
        }

        World world = arenaService.mainWorld();
        if (world == null) {
            return StartResult.NO_WORLD;
        }

        keepWaitingPlayersInLobby();
        List<Player> participants = lobbyPlayers();
        if (participants.size() < plugin.settings().start().minPlayers()) {
            plugin.logGame("Start failed: lobbyPlayers=" + participants.size()
                    + "/" + plugin.settings().start().minPlayers() + ".");
            return StartResult.NOT_ENOUGH_PLAYERS;
        }

        ArenaSelection selection = arenaService.selectArena();
        if (selection == null) {
            return StartResult.NO_ARENA_FOUND;
        }

        stopTasks();
        cleanupRoundMemory();
        state = GameState.COUNTDOWN;
        currentY = plugin.settings().round().startLavaY() - 1;
        arenaCenter = selection.center();
        arenaBounds = boundsFor(selection.center());
        forceLoadedChunks.addAll(arenaService.forceLoadArena(world, selection.center()));
        arenaStore.remember(selection.center());

        saveWorldState(world);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, false);
        world.setDifficulty(plugin.settings().round().countdownDifficulty());
        world.setPVP(false);
        prepareBorder(world, selection.center(), plugin.settings().round().arenaDiameter());
        chooseRoundBlock();

        activePlayers.clear();
        eliminatedPlayers.clear();
        participants.forEach(player -> activePlayers.add(player.getUniqueId()));
        teleportParticipants(world, participants, selection.center());
        broadcast(ChatColor.YELLOW + "Arena selected: " + selection.source() + " "
                + ChatColor.GRAY + "(" + selection.biome().name() + ")");
        announceSandMayhem();
        scheduleCountdown();
        plugin.logGame("Round countdown started: participants=" + participants.size()
                + " [" + participants.stream().map(Player::getName).collect(Collectors.joining(", ")) + "]"
                + ", center=" + selection.center().x() + "," + selection.center().z()
                + ", biome=" + selection.biome().name()
                + ", block=" + buildingBlock
                + ", pvp=false.");
        return StartResult.STARTED;
    }

    public void stopRound(boolean returnPlayersToLobby) {
        stopTasks();
        World world = arenaService.mainWorld();
        if (world != null) {
            restoreWorldState(world);
        }
        arenaService.releaseForceLoadedChunks(forceLoadedChunks);
        cleanupRoundMemory();
        state = GameState.WAITING;
        if (returnPlayersToLobby) {
            finalLobbyCleanup();
        }
        plugin.logGame("Round stopped. returnPlayersToLobby=" + returnPlayersToLobby + ".");
    }

    public void keepWaitingPlayersInLobby() {
        if (state != GameState.WAITING) {
            return;
        }

        Location lobby = arenaService.lobbyLocation();
        if (lobby == null) {
            return;
        }
        lobby.getChunk().load(true);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shouldEnforceLobbyBoundary(player)
                    && (!arenaService.isInLobby(player.getLocation()) || player.getGameMode() == GameMode.SPECTATOR)) {
                sendPlayerToLobby(player);
            } else if (shouldManageGameMode(player) && player.getGameMode() != GameMode.ADVENTURE) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    public void sendPlayerToLobby(Player player) {
        Location lobby = arenaService.lobbyLocation();
        if (lobby == null || !player.isOnline()) {
            return;
        }
        lobby.getChunk().load(true);
        if (player.getGameMode() == GameMode.SPECTATOR || shouldManageGameMode(player)) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        player.setAllowFlight(false);
        player.setFlying(false);
        player.teleport(lobby);
        plugin.logGame("Lobby teleport: player=" + player.getName()
                + ", op=" + player.isOp()
                + ", loc=" + formatLocation(lobby) + ".");
    }

    public void handleJoin(Player player) {
        if (state == GameState.WAITING) {
            if (shouldEnforceLobbyBoundary(player)) {
                sendPlayerToLobby(player);
            } else {
                plugin.logGame("Join while waiting: admin lobby teleport bypass for " + player.getName() + ".");
            }
            return;
        }

        if (state == GameState.CELEBRATION) {
            sendPlayerToLobby(player);
            return;
        }

        if (!activePlayers.contains(player.getUniqueId()) && shouldManageGameMode(player)) {
            player.setGameMode(GameMode.SPECTATOR);
            plugin.logGame("Join during active round: " + player.getName() + " set to spectator.");
        }
    }

    public void handleDeath(Player player) {
        if (!activePlayers.remove(player.getUniqueId())) {
            return;
        }
        eliminatedPlayers.add(player.getUniqueId());
        plugin.logGame("Player eliminated: " + player.getName()
                + ", aliveRemaining=" + alivePlayers().size() + ".");
        Bukkit.getScheduler().runTask(plugin, this::checkWinCondition);
    }

    public void handleRespawn(Player player) {
        if (!eliminatedPlayers.contains(player.getUniqueId())) {
            return;
        }
        if (state == GameState.RUNNING || state == GameState.DEATHMATCH || state == GameState.COUNTDOWN) {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(ChatColor.GRAY + "You are eliminated. You can spectate until the next round.");
            plugin.logGame("Respawn after elimination: " + player.getName() + " set to spectator.");
        }
    }

    public Location spectatorRespawnLocation() {
        World world = arenaService.mainWorld();
        if (world == null || arenaCenter == null) {
            return null;
        }
        Location location = arenaService.safeSurfaceLocation(world, arenaCenter.x() + 0.5D, arenaCenter.z() + 0.5D);
        if (location == null) {
            location = arenaService.emergencyAboveGround(world, arenaCenter.x() + 0.5D, arenaCenter.z() + 0.5D);
        }
        location.setY(Math.min(world.getMaxHeight() - 2, location.getY() + 12.0D));
        return location;
    }

    public ReviveResult revive(Player player, Player requestedAnchor) {
        if (state != GameState.RUNNING && state != GameState.DEATHMATCH) {
            return new ReviveResult(ReviveStatus.NOT_RUNNING, null);
        }
        if (!player.isOnline()) {
            return new ReviveResult(ReviveStatus.PLAYER_NOT_ONLINE, null);
        }
        if (player.isDead()) {
            return new ReviveResult(ReviveStatus.PLAYER_NOT_RESPAWNED, null);
        }
        if (activePlayers.contains(player.getUniqueId())) {
            return new ReviveResult(ReviveStatus.ALREADY_ALIVE, null);
        }

        Player anchor = requestedAnchor;
        if (anchor != null && !activePlayers.contains(anchor.getUniqueId())) {
            return new ReviveResult(ReviveStatus.ANCHOR_NOT_ALIVE, anchor);
        }
        if (anchor == null) {
            List<Player> anchors = alivePlayers();
            if (anchors.isEmpty()) {
                return new ReviveResult(ReviveStatus.NO_ALIVE_ANCHOR, null);
            }
            anchor = anchors.get(random.nextInt(anchors.size()));
        }

        eliminatedPlayers.remove(player.getUniqueId());
        activePlayers.add(player.getUniqueId());
        if (player.getGameMode() == GameMode.SPECTATOR || shouldManageGameMode(player)) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.teleport(anchor.getLocation());
        plugin.logGame("Player revived: player=" + player.getName() + ", anchor=" + anchor.getName() + ".");
        return new ReviveResult(ReviveStatus.REVIVED, anchor);
    }

    public void setLavaSpeedBypass(Double seconds) {
        lavaSpeedBypassSeconds = seconds == null ? null : Math.max(0.05D, Math.min(600.0D, seconds));
        plugin.logGame("Lava speed bypass=" + (lavaSpeedBypassSeconds == null
                ? "off"
                : formatSeconds(lavaSpeedBypassSeconds) + "s") + ".");
    }

    public Double lavaSpeedBypassSeconds() {
        return lavaSpeedBypassSeconds;
    }

    public LavaPhase phaseForNextLayer() {
        int nextY = Math.min(currentY + 1, plugin.settings().round().maxLavaY());
        if (nextY >= plugin.settings().round().deathmatchStartY()) {
            return LavaPhase.DEATHMATCH_TO_TOP;
        }
        if (nextY >= 100) {
            return LavaPhase.Y100_TO_DEATHMATCH;
        }
        if (nextY >= 60) {
            return LavaPhase.Y60_TO_Y100;
        }
        if (nextY >= 0) {
            return LavaPhase.Y0_TO_Y60;
        }
        return LavaPhase.START_TO_Y0;
    }

    public boolean isCoveredByActiveLava(Block block) {
        return arenaBounds != null
                && block.getWorld().equals(arenaService.mainWorld())
                && arenaBounds.contains(block.getX(), block.getZ())
                && block.getY() >= plugin.settings().round().startLavaY()
                && block.getY() <= currentY;
    }

    public boolean forceLavaAtCoveredBlock(Block block) {
        if (!isCoveredByActiveLava(block) || !isLavaFillTarget(block.getType())) {
            return false;
        }
        setCoveredBlockToLava(block);
        return true;
    }

    public int repairCoveredLavaInChunk(Chunk chunk) {
        if (!isLavaRising() || arenaBounds == null || currentY < plugin.settings().round().startLavaY()) {
            return 0;
        }
        return repairChunkVerticalRange(chunk,
                plugin.settings().round().startLavaY(),
                currentY);
    }

    public void handleSurfaceDifficulty() {
        World world = arenaService.mainWorld();
        if (world != null && currentY == 60) {
            world.setDifficulty(plugin.settings().round().surfaceDifficulty());
            plugin.logGame("Surface reached: difficulty set to " + world.getDifficulty() + ".");
        }
    }

    private void teleportParticipants(World world, List<Player> participants, ArenaCenter center) {
        List<Location> spawns = arenaService.participantSpawns(world, center, participants.size());
        for (int index = 0; index < participants.size(); index++) {
            Player player = participants.get(index);
            Location spawn = spawns.get(index);
            if (shouldManageGameMode(player)) {
                player.setGameMode(GameMode.ADVENTURE);
            }
            player.setAllowFlight(false);
            player.setFlying(false);
            player.teleport(spawn);
            plugin.logGame("Participant spawn: player=" + player.getName() + ", loc=" + formatLocation(spawn) + ".");
        }
    }

    private void chooseRoundBlock() {
        sandMayhemRound = random.nextInt(100) < plugin.settings().round().sandMayhemChance();
        buildingBlock = sandMayhemRound
                ? plugin.settings().round().sandMayhemBlock()
                : plugin.settings().round().defaultBlock();
    }

    private void announceSandMayhem() {
        if (!sandMayhemRound) {
            return;
        }
        broadcast(ChatColor.YELLOW + "" + ChatColor.BOLD + "SAND MAYHEM");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(ChatColor.YELLOW + "" + ChatColor.BOLD + "SAND MAYHEM",
                    ChatColor.GOLD + "Blocks this round are sand",
                    10,
                    60,
                    20);
        }
        plugin.logGame("Sand Mayhem announced.");
    }

    private void scheduleCountdown() {
        int seconds = plugin.settings().start().countdownSeconds();
        for (int left = seconds; left >= 1; left--) {
            if (left > 10 && left % 5 != 0) {
                continue;
            }
            if (left < 10 || left == 10 || left % 5 == 0) {
                int secondsLeft = left;
                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state == GameState.COUNTDOWN) {
                        broadcast(ChatColor.YELLOW + "Game starting in " + ChatColor.WHITE + secondsLeft
                                + ChatColor.YELLOW + " second" + (secondsLeft == 1 ? "" : "s") + ".");
                        playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, secondsLeft <= 3 ? 1.6F : 1.0F);
                    }
                }, (long) (seconds - left) * 20L);
                tasks.add(task);
            }
        }

        BukkitTask begin = Bukkit.getScheduler().runTaskLater(plugin, this::beginRound, seconds * 20L);
        tasks.add(begin);
    }

    private void beginRound() {
        if (state != GameState.COUNTDOWN) {
            return;
        }

        state = GameState.RUNNING;
        World world = arenaService.mainWorld();
        if (world != null) {
            world.setPVP(true);
        }
        for (Player player : alivePlayers()) {
            if (shouldManageGameMode(player)) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
        broadcast(ChatColor.RED + "" + ChatColor.BOLD + "LAVA RISING HAS STARTED!");
        showSandMayhemTitleForActivePlayers();
        startActionBarLoop();
        scheduleIntegrityLoop();
        scheduleNextLavaRise();
        plugin.logGame("Round live: pvp=true, lavaStartY=" + plugin.settings().round().startLavaY()
                + ", deathmatchStartY=" + plugin.settings().round().deathmatchStartY()
                + ", maxY=" + plugin.settings().round().maxLavaY() + ".");
    }

    private void scheduleNextLavaRise() {
        if (!isLavaRising() || currentY >= plugin.settings().round().maxLavaY()) {
            return;
        }
        LavaPhase phase = phaseForNextLayer();
        double seconds = lavaSpeedBypassSeconds == null ? plugin.settings().lavaSpeed(phase) : lavaSpeedBypassSeconds;
        long delay = Math.max(1L, Math.round(seconds * 20.0D));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isLavaRising() || currentY >= plugin.settings().round().maxLavaY()) {
                return;
            }
            currentY++;
            if (state == GameState.RUNNING && currentY >= plugin.settings().round().deathmatchStartY()) {
                startDeathmatch();
            }
            handleSurfaceDifficulty();
            fillLavaLayerBatched(currentY, () -> {
                broadcastMilestone(currentY);
                giveRoundBlocks();
                if (plugin.settings().round().clearItemsOnLavaRise()) {
                    clearDroppedItems();
                }
                checkWinCondition();
                scheduleNextLavaRise();
            });
        }, delay);
        tasks.add(task);
    }

    private void fillLavaLayerBatched(int y, Runnable whenDone) {
        if (arenaBounds == null || arenaCenter == null) {
            whenDone.run();
            return;
        }

        Queue<ChunkKey> chunks = arenaChunks();
        BukkitRunnable runnable = new BukkitRunnable() {
            private int changed;

            @Override
            public void run() {
                if (!isLavaRising()) {
                    cancel();
                    return;
                }

                int chunksThisTick = plugin.settings().performance().lavaChunksPerTick();
                for (int i = 0; i < chunksThisTick && !chunks.isEmpty(); i++) {
                    ChunkKey key = chunks.remove();
                    World world = Bukkit.getWorld(key.worldName());
                    if (world == null) {
                        continue;
                    }
                    changed += fillLavaLayerInChunk(world.getChunkAt(key.x(), key.z()), y);
                }

                if (chunks.isEmpty()) {
                    cancel();
                    plugin.logGame("Lava layer filled: y=" + y + ", changedBlocks=" + changed
                            + ", phase=" + phaseForNextLayer().configKey()
                            + ", alive=" + alivePlayers().size() + ".");
                    whenDone.run();
                }
            }
        };
        BukkitTask task = runnable.runTaskTimer(plugin, 0L, 1L);
        tasks.add(task);
    }

    private int fillLavaLayerInChunk(Chunk chunk, int y) {
        if (arenaBounds == null) {
            return 0;
        }
        int minX = Math.max(arenaBounds.minX(), chunk.getX() << 4);
        int maxX = Math.min(arenaBounds.maxXExclusive() - 1, (chunk.getX() << 4) + 15);
        int minZ = Math.max(arenaBounds.minZ(), chunk.getZ() << 4);
        int maxZ = Math.min(arenaBounds.maxZExclusive() - 1, (chunk.getZ() << 4) + 15);
        int changed = 0;
        World world = chunk.getWorld();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = world.getBlockAt(x, y, z);
                if (isLavaFillTarget(block.getType())) {
                    setCoveredBlockToLava(block);
                    changed++;
                }
            }
        }
        return changed;
    }

    private void scheduleIntegrityLoop() {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isLavaRising() || arenaBounds == null || currentY < plugin.settings().round().startLavaY()) {
                    return;
                }
                if (integrityQueue.isEmpty()) {
                    integrityQueue.addAll(arenaChunks());
                }
                int repairs = 0;
                int limit = plugin.settings().performance().integrityChunksPerTick();
                for (int i = 0; i < limit && !integrityQueue.isEmpty(); i++) {
                    ChunkKey key = integrityQueue.remove();
                    World world = Bukkit.getWorld(key.worldName());
                    if (world != null && world.isChunkLoaded(key.x(), key.z())) {
                        int startY = integrityRepairCursor.getOrDefault(key, plugin.settings().round().startLavaY());
                        int endY = Math.min(currentY, startY + plugin.settings().performance().integrityVerticalBatch() - 1);
                        repairs += repairChunkVerticalRange(world.getChunkAt(key.x(), key.z()), startY, endY);
                        if (endY >= currentY) {
                            integrityRepairCursor.put(key, plugin.settings().round().startLavaY());
                        } else {
                            integrityRepairCursor.put(key, endY + 1);
                            integrityQueue.add(key);
                        }
                    }
                }
                if (repairs > 0) {
                    plugin.logGame("Lava integrity background repair: repairedBlocks=" + repairs + ".");
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
        tasks.add(task);
    }

    private int repairChunkVerticalRange(Chunk chunk, int minY, int maxY) {
        if (arenaBounds == null) {
            return 0;
        }
        World world = chunk.getWorld();
        int minX = Math.max(arenaBounds.minX(), chunk.getX() << 4);
        int maxX = Math.min(arenaBounds.maxXExclusive() - 1, (chunk.getX() << 4) + 15);
        int minZ = Math.max(arenaBounds.minZ(), chunk.getZ() << 4);
        int maxZ = Math.min(arenaBounds.maxZExclusive() - 1, (chunk.getZ() << 4) + 15);
        int yStart = Math.max(minY, world.getMinHeight());
        int yEnd = Math.min(maxY, world.getMaxHeight() - 1);
        int changed = 0;
        for (int y = yStart; y <= yEnd; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (isLavaFillTarget(block.getType())) {
                        setCoveredBlockToLava(block);
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    private void startDeathmatch() {
        if (state != GameState.RUNNING) {
            return;
        }
        state = GameState.DEATHMATCH;
        World world = arenaService.mainWorld();
        if (world != null && arenaCenter != null) {
            WorldBorder border = world.getWorldBorder();
            border.setDamageAmount(plugin.settings().deathmatch().borderDamage() ? 0.2D : 0.0D);
            border.setCenter(arenaCenter.x() + 0.5D, arenaCenter.z() + 0.5D);
            border.setSize(plugin.settings().deathmatch().borderDiameter(),
                    TimeUnit.SECONDS,
                    plugin.settings().deathmatch().borderShrinkSeconds());
        }
        broadcast(ChatColor.DARK_RED + "" + ChatColor.BOLD + "DEATHMATCH");
        broadcast(ChatColor.RED + "Phase 5 reached at Y=" + currentY + ". Lava is still rising.");
        plugin.logGame("Deathmatch started at lavaY=" + currentY + "; lava continues rising.");
    }

    private void startActionBarLoop() {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isLavaRising()) {
                    cancel();
                    return;
                }
                String pvp = isCombatLive() ? ChatColor.GREEN + "PVP ON" : ChatColor.RED + "PVP OFF";
                String speed = lavaSpeedBypassSeconds == null
                        ? formatSeconds(plugin.settings().lavaSpeed(phaseForNextLayer())) + "s"
                        : ChatColor.GOLD + formatSeconds(lavaSpeedBypassSeconds) + "s bypass";
                String message = ChatColor.RED + "Lava Y " + ChatColor.WHITE + currentY
                        + ChatColor.DARK_GRAY + " | " + pvp
                        + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Speed " + ChatColor.WHITE + speed;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendActionBar(message);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        tasks.add(task);
    }

    private void giveRoundBlocks() {
        LavaConfig.Round round = plugin.settings().round();
        if (!round.giveBlocks() || round.blockGiveRate() <= 0 || round.maxGivenBlocks() <= 0) {
            return;
        }
        for (Player player : alivePlayers()) {
            int currentAmount = countMaterial(player, buildingBlock);
            int missing = round.maxGivenBlocks() - currentAmount;
            if (missing <= 0) {
                continue;
            }
            int amount = Math.min(round.blockGiveRate(), missing);
            player.getInventory().addItem(new ItemStack(buildingBlock, amount));
        }
    }

    private int countMaterial(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void broadcastMilestone(int y) {
        if (!plugin.settings().round().milestoneMessages()) {
            return;
        }
        if (y == plugin.settings().round().startLavaY() || y == 0 || y == 60 || y == 100
                || y == plugin.settings().round().deathmatchStartY() || y == plugin.settings().round().maxLavaY()) {
            broadcast(ChatColor.RED + "Lava reached Y=" + ChatColor.WHITE + y + ChatColor.RED + ".");
            playSound(Sound.BLOCK_BELL_USE, 1.0F, 0.8F);
        }
    }

    private void checkWinCondition() {
        if (state != GameState.RUNNING && state != GameState.DEATHMATCH) {
            return;
        }
        List<Player> alive = alivePlayers();
        if (alive.size() > 1) {
            return;
        }
        startCelebration(alive.isEmpty() ? null : alive.getFirst());
    }

    private void startCelebration(Player winner) {
        if (state == GameState.CELEBRATION || state == GameState.WAITING) {
            return;
        }
        stopTasks();
        state = GameState.CELEBRATION;
        World world = arenaService.mainWorld();
        if (world != null) {
            world.setPVP(false);
        }

        savedFlightStates.clear();
        showAllPlayers();
        for (Player player : Bukkit.getOnlinePlayers()) {
            savedFlightStates.put(player.getUniqueId(), new FlightState(player.getAllowFlight(), player.isFlying()));
            if (player.getGameMode() == GameMode.SPECTATOR || shouldManageGameMode(player)) {
                player.setGameMode(GameMode.ADVENTURE);
            }
            player.setAllowFlight(true);
            player.setFlying(true);
        }

        if (winner != null) {
            broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + winner.getName() + " wins!");
            startFireworkLoop(winner);
        } else {
            broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + "Round ended with no winner.");
        }

        BukkitTask finish = Bukkit.getScheduler().runTaskLater(plugin,
                this::finishCelebration,
                plugin.settings().celebration().seconds() * 20L);
        tasks.add(finish);
        plugin.logGame("Celebration started: winner=" + (winner == null ? "none" : winner.getName())
                + ", seconds=" + plugin.settings().celebration().seconds() + ".");
    }

    private void startFireworkLoop(Player winner) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.CELEBRATION || !winner.isOnline()) {
                    cancel();
                    return;
                }
                int min = plugin.settings().celebration().minFireworks();
                int max = plugin.settings().celebration().maxFireworks();
                int count = min + random.nextInt(Math.max(1, max - min + 1));
                for (int i = 0; i < count; i++) {
                    double angle = (Math.PI * 2.0D * i) / count;
                    Location location = winner.getLocation().clone().add(Math.cos(angle) * 4.0D, 1.0D,
                            Math.sin(angle) * 4.0D);
                    spawnFirework(location);
                }
            }
        }.runTaskTimer(plugin, 0L, plugin.settings().celebration().fireworkIntervalTicks());
        tasks.add(task);
    }

    private void spawnFirework(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Firework firework = world.spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.YELLOW, Color.ORANGE)
                .withFade(Color.RED)
                .trail(true)
                .flicker(true)
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }

    private void finishCelebration() {
        stopTasks();
        World world = arenaService.mainWorld();
        if (world != null) {
            restoreWorldState(world);
        }
        arenaService.releaseForceLoadedChunks(forceLoadedChunks);
        cleanupRoundMemory();
        state = GameState.WAITING;
        finalLobbyCleanup();
        plugin.logGame("Celebration finished; players returned to lobby.");
    }

    private void finalLobbyCleanup() {
        World world = arenaService.mainWorld();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getInventory().clear();
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            player.setFireTicks(0);
            player.setFallDistance(0.0F);
            player.setAllowFlight(false);
            player.setFlying(false);
            if (player.getGameMode() == GameMode.SPECTATOR || shouldManageGameMode(player)) {
                player.setGameMode(GameMode.ADVENTURE);
            }
            sendPlayerToLobby(player);
        }

        if (world != null) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Player)) {
                    entity.remove();
                }
            }
        }
        savedFlightStates.clear();
    }

    private void saveWorldState(World world) {
        WorldBorder border = world.getWorldBorder();
        if (savedBorderSize == null) {
            savedBorderSize = border.getSize();
            savedBorderCenter = border.getCenter();
        }
        if (savedDifficulty == null) {
            savedDifficulty = world.getDifficulty();
        }
        if (savedPvp == null) {
            savedPvp = world.getPVP();
        }
        if (savedImmediateRespawn == null) {
            savedImmediateRespawn = world.getGameRuleValue(GameRule.DO_IMMEDIATE_RESPAWN);
        }
    }

    private void restoreWorldState(World world) {
        if (savedBorderSize != null && savedBorderCenter != null) {
            WorldBorder border = world.getWorldBorder();
            border.setCenter(savedBorderCenter);
            border.setSize(savedBorderSize);
            border.setDamageAmount(0.2D);
        }
        if (savedDifficulty != null) {
            world.setDifficulty(savedDifficulty);
        }
        if (savedPvp != null) {
            world.setPVP(savedPvp);
        }
        if (savedImmediateRespawn != null) {
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, savedImmediateRespawn);
        }
        savedBorderSize = null;
        savedBorderCenter = null;
        savedDifficulty = null;
        savedPvp = null;
        savedImmediateRespawn = null;
    }

    private void prepareBorder(World world, ArenaCenter center, int diameter) {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(center.x() + 0.5D, center.z() + 0.5D);
        border.setSize(diameter);
        border.setDamageAmount(plugin.settings().deathmatch().borderDamage() ? 0.2D : 0.0D);
    }

    private void stopTasks() {
        for (BukkitTask task : tasks) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        tasks.clear();
    }

    private void cleanupRoundMemory() {
        activePlayers.clear();
        eliminatedPlayers.clear();
        integrityQueue.clear();
        integrityRepairCursor.clear();
        currentY = plugin.settings().round().startLavaY() - 1;
        arenaCenter = null;
        arenaBounds = null;
        sandMayhemRound = false;
        buildingBlock = plugin.settings().round().defaultBlock();
    }

    private ArenaBounds boundsFor(ArenaCenter center) {
        int diameter = plugin.settings().round().arenaDiameter();
        int minX = center.x() - diameter / 2;
        int minZ = center.z() - diameter / 2;
        return new ArenaBounds(minX, minZ, minX + diameter, minZ + diameter);
    }

    private Queue<ChunkKey> arenaChunks() {
        Queue<ChunkKey> chunks = new ArrayDeque<>();
        if (arenaBounds == null) {
            return chunks;
        }
        World world = arenaService.mainWorld();
        if (world == null) {
            return chunks;
        }
        int minChunkX = arenaBounds.minX() >> 4;
        int maxChunkX = (arenaBounds.maxXExclusive() - 1) >> 4;
        int minChunkZ = arenaBounds.minZ() >> 4;
        int maxChunkZ = (arenaBounds.maxZExclusive() - 1) >> 4;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                chunks.add(new ChunkKey(world.getName(), x, z));
            }
        }
        return chunks;
    }

    private boolean isLavaFillTarget(Material material) {
        return material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR
                || material == Material.WATER
                || material == Material.SEAGRASS
                || material == Material.TALL_SEAGRASS
                || material == Material.KELP
                || material == Material.KELP_PLANT
                || material == Material.SNOW
                || material == Material.VINE
                || material == Material.GLOW_LICHEN
                || isSoftReplaceable(material)
                || isTreeMaterial(material)
                || isDoorOrTrapdoor(material);
    }

    private boolean isSoftReplaceable(Material material) {
        return material.isAir()
                || (!material.isSolid()
                && material != Material.LAVA
                && material != Material.BEDROCK
                && material != Material.BARRIER);
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

    private boolean isDoorOrTrapdoor(Material material) {
        String name = material.name();
        return name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR");
    }

    private void setCoveredBlockToLava(Block block) {
        Material material = block.getType();
        if (isDoorOrTrapdoor(material)) {
            clearDoorHalf(block.getRelative(BlockFace.UP), material);
            clearDoorHalf(block.getRelative(BlockFace.DOWN), material);
        }
        block.setType(Material.LAVA, false);
    }

    private void clearDoorHalf(Block block, Material material) {
        if (block.getType() == material) {
            block.setType(Material.AIR, false);
        }
    }

    private void clearDroppedItems() {
        World world = arenaService.mainWorld();
        if (world == null) {
            return;
        }
        int removed = 0;
        for (Item item : world.getEntitiesByClass(Item.class)) {
            item.remove();
            removed++;
        }
        plugin.logGame("Dropped items cleared: " + removed + ".");
    }

    private void showAllPlayers() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player viewer : players) {
            for (Player target : players) {
                viewer.showPlayer(plugin, target);
            }
        }
    }

    private void showSandMayhemTitleForActivePlayers() {
        if (!sandMayhemRound) {
            return;
        }
        for (Player player : alivePlayers()) {
            player.sendTitle(ChatColor.YELLOW + "" + ChatColor.BOLD + "SAND MAYHEM",
                    ChatColor.GOLD + "Sand is your block this round",
                    10,
                    60,
                    20);
        }
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(message);
    }

    private void playSound(Sound sound, float volume, float pitch) {
        if (!plugin.settings().round().sounds()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private String formatSeconds(double seconds) {
        if (Math.abs(seconds - Math.rint(seconds)) < 0.0001D) {
            return Integer.toString((int) Math.rint(seconds));
        }
        return String.format(Locale.ROOT, "%.2f", seconds);
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + " "
                + String.format(Locale.ROOT, "%.1f,%.1f,%.1f",
                location.getX(), location.getY(), location.getZ());
    }
}
