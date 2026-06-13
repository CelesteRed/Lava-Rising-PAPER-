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
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

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
    private UUID juggernaut;
    private JuggernautKits juggernautKits;
    private final Map<UUID, String> playerTeams = new HashMap<>();
    private final Map<UUID, String> teamChaosRoles = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private boolean wallActive;
    private int wallX;
    private int wallMinZ;
    private int wallMaxZExclusive;
    private int wallBaseY;
    private int wallTopY;
    private final Set<Attribute> managedAttributes = new HashSet<>();
    private boolean roundPvpEnabled;
    private GameModeType activeMode = GameModeType.CLASSIC;
    private GamemodeVote currentVote;
    private LavaPhase lastLavaPhase = LavaPhase.START_TO_Y0;
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

    public boolean isPvpEnabled() {
        return roundPvpEnabled && isCombatLive();
    }

    public void ensureRoundPvpState() {
        World world = arenaService.mainWorld();
        if (world == null || !isRoundActive()) {
            return;
        }

        boolean expected = isPvpEnabled();
        if (world.getPVP() != expected) {
            world.setPVP(expected);
            plugin.logGame("Corrected game world PVP: enabled=" + expected + ", state=" + state + ".");
        }
        Difficulty expectedDifficulty = pvpDifficulty(expected);
        if (world.getDifficulty() != expectedDifficulty) {
            world.setDifficulty(expectedDifficulty);
        }
    }

    public boolean isActivePlayer(Player player) {
        return activePlayers.contains(player.getUniqueId());
    }

    public boolean isEliminated(Player player) {
        return eliminatedPlayers.contains(player.getUniqueId());
    }

    public boolean canFight(Player player) {
        return !eliminatedPlayers.contains(player.getUniqueId())
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    public boolean shouldManageGameMode(Player player) {
        // Everyone, including ops/admins, is treated as a normal player in-game.
        return true;
    }

    public boolean isAdmin(org.bukkit.command.CommandSender sender) {
        return sender.isOp() || sender.hasPermission("lavarising.admin");
    }

    public List<Player> lobbyPlayers() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
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

    public void showStartingFeedback() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(ChatColor.GRAY + "Starting...", "", 5, 120, 20);
            player.sendActionBar(ChatColor.GRAY + "[Loading World...]");
        }
    }

    public boolean isVoting() {
        return currentVote != null;
    }

    public GamemodeVote currentVote() {
        return currentVote;
    }

    public GameModeType activeMode() {
        return activeMode;
    }

    private List<Player> votingPlayers() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                .collect(Collectors.toList());
    }

    public void beginGamemodeVote() {
        List<GameModeType> options = plugin.gamemodes().enabledModes();
        if (!plugin.settings().start().gamemodeVote() || options.size() <= 1) {
            startRoundWithFeedback(options.isEmpty() ? GameModeType.CLASSIC : options.get(0));
            return;
        }
        currentVote = new GamemodeVote(plugin.gamemodes(), options);
        for (Player player : votingPlayers()) {
            currentVote.open(player);
        }
        broadcast(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "VOTE "
                + ChatColor.RESET + ChatColor.GRAY + "for the next gamemode in the menu!");
        playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.4F);
        int seconds = plugin.settings().start().voteSeconds();
        scheduleVoteCountdown(seconds);
        BukkitTask end = Bukkit.getScheduler().runTaskLater(plugin, this::finishVote, seconds * 20L);
        tasks.add(end);
        plugin.logGame("Gamemode vote opened: options=" + options.stream().map(GameModeType::id).toList()
                + ", seconds=" + seconds + ".");
    }

    private void scheduleVoteCountdown(int seconds) {
        // Tick out the final 10 seconds so players hear that voting is about to close.
        int countdownFrom = Math.min(10, seconds);
        for (int left = countdownFrom; left >= 1; left--) {
            int secondsLeft = left;
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (currentVote == null) {
                    return;
                }
                if (secondsLeft == countdownFrom) {
                    broadcast(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + secondsLeft
                            + ChatColor.RESET + ChatColor.GRAY + " seconds left to vote!");
                    playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.4F);
                } else {
                    playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F, secondsLeft <= 3 ? 1.6F : 1.0F);
                }
                for (Player player : votingPlayers()) {
                    player.sendActionBar(ChatColor.YELLOW + "Voting ends in " + ChatColor.WHITE + secondsLeft
                            + ChatColor.YELLOW + " second" + (secondsLeft == 1 ? "" : "s") + "!");
                }
            }, (long) (seconds - secondsLeft) * 20L);
            tasks.add(task);
        }
    }

    public void openVoteFor(Player player) {
        if (currentVote != null && player.getGameMode() != GameMode.SPECTATOR) {
            currentVote.open(player);
        }
    }

    public void cancelVote() {
        if (currentVote != null) {
            currentVote.closeAll();
            currentVote = null;
        }
    }

    private void finishVote() {
        if (currentVote == null) {
            return;
        }
        GameModeType fallback = plugin.gamemodes().isEnabled(GameModeType.CLASSIC) ? GameModeType.CLASSIC : null;
        GameModeType winner = currentVote.winner(random, fallback);
        currentVote.closeAll();
        currentVote = null;
        broadcast(ChatColor.GRAY + "Gamemode chosen: " + plugin.gamemodes().settings(winner).coloredName());
        startRoundWithFeedback(winner);
    }

    public void startRoundWithFeedback(GameModeType mode) {
        showStartingFeedback();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            StartResult result = startRound(mode);
            // SEARCHING means the async arena search is underway; success/failure is
            // broadcast later from finishStart, so only report the immediate failures here.
            if (result != StartResult.STARTED && result != StartResult.SEARCHING) {
                broadcast(ChatColor.RED + "Could not start the round: " + describeStartFailure(result));
            }
        }, 2L);
        tasks.add(task);
    }

    private String describeStartFailure(StartResult result) {
        return switch (result) {
            case ALREADY_ACTIVE -> "a round is already active.";
            case NO_WORLD -> "the game world is missing.";
            case NOT_ENOUGH_PLAYERS -> "not enough players are in the lobby.";
            case NO_ARENA_FOUND -> "no fresh arena could be found.";
            case SEARCHING -> "searching for an arena.";
            case STARTED -> "started.";
        };
    }

    public StartResult startRound(GameModeType mode) {
        if (state != GameState.WAITING) {
            return StartResult.ALREADY_ACTIVE;
        }

        World world = arenaService.mainWorld();
        if (world == null) {
            return StartResult.NO_WORLD;
        }

        List<Player> participants = lobbyPlayers();
        if (participants.size() < plugin.settings().start().minPlayers()) {
            plugin.logGame("Start failed: lobbyPlayers=" + participants.size()
                    + "/" + plugin.settings().start().minPlayers() + ".");
            return StartResult.NOT_ENOUGH_PLAYERS;
        }

        activeMode = mode;
        GamemodeSettings modeSettings = plugin.gamemodes().settings(mode);
        boolean forceVillage = modeSettings.config().getBoolean("forceVillage", false);
        java.util.Set<Biome> extraBiomes = modeSettings.config().getBoolean("preferDesert", false)
                ? desertBiomes() : java.util.Set.of();

        // Search for the arena asynchronously so the main thread never freezes (and players
        // never time out) while chunks are generated. finishStart runs once it resolves.
        state = GameState.SEARCHING;
        broadcast(ChatColor.GRAY + "Finding a fresh arena...");
        plugin.logGame("Arena search started (async): mode=" + mode.id() + ", forceVillage=" + forceVillage + ".");
        arenaService.selectArenaAsync(forceVillage, extraBiomes, selection -> finishStart(mode, selection));
        return StartResult.SEARCHING;
    }

    // Runs on the main thread once the async arena search resolves. Completes the round
    // setup, or reverts to WAITING and reports why if no arena/players are available.
    private void finishStart(GameModeType mode, ArenaSelection selection) {
        if (state != GameState.SEARCHING) {
            // A stop/reset happened while searching; discard this stale result.
            plugin.logGame("Arena search result ignored: state=" + state + ".");
            return;
        }
        World world = arenaService.mainWorld();
        if (world == null) {
            broadcast(ChatColor.RED + "Could not start the round: " + describeStartFailure(StartResult.NO_WORLD));
            state = GameState.WAITING;
            return;
        }
        if (selection == null) {
            broadcast(ChatColor.RED + "Could not start the round: "
                    + describeStartFailure(StartResult.NO_ARENA_FOUND));
            state = GameState.WAITING;
            return;
        }
        // Players may have left during the search; re-check the lobby before committing.
        List<Player> participants = lobbyPlayers();
        if (participants.size() < plugin.settings().start().minPlayers()) {
            broadcast(ChatColor.RED + "Could not start the round: "
                    + describeStartFailure(StartResult.NOT_ENOUGH_PLAYERS));
            state = GameState.WAITING;
            return;
        }

        stopTasks();
        cleanupRoundMemory();
        activeMode = mode;
        state = GameState.COUNTDOWN;
        currentY = plugin.settings().round().startLavaY() - 1;
        arenaCenter = selection.center();
        arenaBounds = boundsFor(selection.center());
        forceLoadedChunks.addAll(arenaService.forceLoadArena(world, selection.center()));
        arenaStore.remember(selection.center());

        saveWorldState(world);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, false);
        setRoundPvp(world, false, "countdown");
        prepareBorder(world, selection.center(), plugin.settings().round().arenaDiameter());
        applyGamemodeBlock();

        activePlayers.clear();
        eliminatedPlayers.clear();
        participants.forEach(player -> activePlayers.add(player.getUniqueId()));
        selectJuggernaut(participants);
        setupTeamChaos();
        teleportParticipants(world, participants, selection.center());
        buildTeamChaosWall(world);
        broadcast(ChatColor.AQUA + "" + ChatColor.BOLD + "NEW ARENA "
                + ChatColor.RESET + ChatColor.GRAY + "— " + ChatColor.WHITE
                + capitalizeWords(selection.biome().name()) + ChatColor.DARK_GRAY + " (" + selection.source() + ")");
        announceGamemode(mode);
        scheduleCountdown();
        plugin.logGame("Round countdown started: mode=" + mode.id()
                + ", participants=" + participants.size()
                + " [" + participants.stream().map(Player::getName).collect(Collectors.joining(", ")) + "]"
                + ", center=" + selection.center().x() + "," + selection.center().z()
                + ", biome=" + selection.biome().name()
                + ", block=" + buildingBlock
                + ", pvp=false.");
    }

    private java.util.Set<Biome> desertBiomes() {
        return java.util.Set.of(Biome.DESERT);
    }

    public void stopRound(boolean returnPlayersToLobby) {
        cancelVote();
        arenaService.cancelSearch();
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
        clearGamemodeEffects(player);
        player.teleport(lobby);
        plugin.logGame("Lobby teleport: player=" + player.getName()
                + ", op=" + player.isOp()
                + ", loc=" + formatLocation(lobby) + ".");
    }

    public void handleJoin(Player player) {
        if (state == GameState.WAITING) {
            sendPlayerToLobby(player);
            openVoteFor(player);
            return;
        }

        if (state == GameState.CELEBRATION || state == GameState.SEARCHING) {
            // Searching: the round hasn't committed yet. Keep them in the lobby so they can
            // still be picked up as a participant when finishStart re-reads the lobby.
            sendPlayerToLobby(player);
            return;
        }

        if (!activePlayers.contains(player.getUniqueId()) && shouldManageGameMode(player)) {
            // Joined after the round started: deem them dead so they spectate and can be revived.
            eliminatedPlayers.add(player.getUniqueId());
            player.setGameMode(GameMode.SPECTATOR);
            teleportToLivingPlayer(player);
            plugin.logGame("Join during active round: " + player.getName() + " deemed eliminated, set to spectator.");
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
        if (state == GameState.CELEBRATION) {
            // Respawned right as the round ended — join the celebration instead of spectating.
            prepareCelebrationParticipant(player);
            return;
        }
        // An eliminated player must respawn as a spectator (never alive in the lava arena,
        // or they die on a loop). eliminatedPlayers is empty once a round is cleaned up,
        // so this never affects lobby respawns.
        player.setGameMode(GameMode.SPECTATOR);
        teleportToLivingPlayer(player);
        player.sendMessage(ChatColor.GRAY + "You are eliminated. You can spectate until the next round.");
        plugin.logGame("Respawn after elimination: " + player.getName() + " set to spectator (state=" + state + ").");
    }

    // Drop a spectator (or late-joiner) on a living player so they're watching the action.
    private void teleportToLivingPlayer(Player player) {
        List<Player> alive = alivePlayers();
        if (!alive.isEmpty()) {
            player.teleport(alive.get(random.nextInt(alive.size())).getLocation());
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
        // Only a player who is actually playing (tracked AND not spectating) counts as
        // "already alive". A late-join or eliminated spectator must stay revivable.
        if (activePlayers.contains(player.getUniqueId()) && player.getGameMode() != GameMode.SPECTATOR) {
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
        return phaseForLayer(currentY + 1);
    }

    public LavaPhase phaseForLayer(int y) {
        int layer = Math.min(y, plugin.settings().round().maxLavaY());
        if (layer >= plugin.settings().round().deathmatchStartY()) {
            return LavaPhase.DEATHMATCH_TO_TOP;
        }
        if (layer >= 100) {
            return LavaPhase.Y100_TO_DEATHMATCH;
        }
        if (layer >= 60) {
            return LavaPhase.Y60_TO_Y100;
        }
        if (layer >= 0) {
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
        // Enabling PVP also switches the arena difficulty (peaceful -> combat) via setRoundPvp.
        // The chat/title announcement is handled by announcePhaseChange() so it stays one message.
        if (world != null && !roundPvpEnabled && currentY >= plugin.settings().round().pvpEnableY()) {
            setRoundPvp(world, true, "lava reached Y=" + currentY);
        }
    }

    private void teleportParticipants(World world, List<Player> participants, ArenaCenter center) {
        List<Location> spawns = arenaService.participantSpawns(world, center, participants.size());
        Map<UUID, Location> teamSpawns = activeMode == GameModeType.TEAM_CHAOS
                ? computeTeamChaosSpawns(world, center, participants) : null;
        for (int index = 0; index < participants.size(); index++) {
            Player player = participants.get(index);
            Location spawn = teamSpawns != null
                    ? teamSpawns.getOrDefault(player.getUniqueId(), spawns.get(index))
                    : spawns.get(index);
            if (shouldManageGameMode(player)) {
                player.setGameMode(GameMode.ADVENTURE);
            }
            player.setInvulnerable(false);
            player.setAllowFlight(false);
            player.setFlying(false);
            // Start every round on a clean slate (equivalent to /clear + /effect clear)
            // so no items or effects (e.g. celebration fire resistance) carry over from
            // a previous round. Runs before applyStartKit so gamemode kits survive.
            player.getInventory().clear();
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            player.setFireTicks(0);
            player.setFallDistance(0.0F);
            player.teleport(spawn);
            applyStartKit(player);
            applyGamemodeEffects(player);
            plugin.logGame("Participant spawn: player=" + player.getName() + ", loc=" + formatLocation(spawn) + ".");
        }
    }

    private void applyGamemodeBlock() {
        GamemodeSettings modeSettings = plugin.gamemodes().settings(activeMode);
        buildingBlock = modeSettings.buildingBlock(plugin.settings().round().defaultBlock());
        sandMayhemRound = activeMode == GameModeType.SAND_MAYHEM;
    }

    // Juggernaut mode: one random participant becomes the buffed 1-vs-all target.
    private void selectJuggernaut(List<Player> participants) {
        juggernaut = null;
        juggernautKits = null;
        if (activeMode != GameModeType.JUGGERNAUT || participants.isEmpty()) {
            return;
        }
        Player chosen = participants.get(random.nextInt(participants.size()));
        juggernaut = chosen.getUniqueId();
        juggernautKits = new JuggernautKits(plugin.gamemodes().settings(activeMode).config());
        setupTeams();
        broadcast(ChatColor.DARK_RED + "" + ChatColor.BOLD + chosen.getName()
                + ChatColor.RED + " is the JUGGERNAUT! Everyone else, take them down!");
        chosen.sendTitle(ChatColor.DARK_RED + "" + ChatColor.BOLD + "JUGGERNAUT",
                ChatColor.RED + "Double health & gear — survive the horde!", 10, 70, 20);
        plugin.logGame("Juggernaut selected: " + chosen.getName() + ".");
    }

    public boolean isJuggernaut(Player player) {
        return activeMode == GameModeType.JUGGERNAUT && player.getUniqueId().equals(juggernaut);
    }

    // Players on the same managed team (hunters, or red/blue) can't damage each other.
    public boolean isFriendlyFire(Player attacker, Player target) {
        String team = playerTeams.get(attacker.getUniqueId());
        return team != null && team.equals(playerTeams.get(target.getUniqueId()));
    }

    // Red Juggernaut team vs the blue hunters team (no friendly fire among hunters).
    private void setupTeams() {
        var manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard scoreboard = manager.getMainScoreboard();
        Team naut = getOrCreateTeam(scoreboard, "lr_naut", ChatColor.RED, true);
        Team hunters = getOrCreateTeam(scoreboard, "lr_hunters", ChatColor.BLUE, false);
        clearTeam(naut);
        clearTeam(hunters);
        for (UUID id : activePlayers) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            (id.equals(juggernaut) ? naut : hunters).addEntry(player.getName());
            playerTeams.put(id, id.equals(juggernaut) ? "lr_naut" : "lr_hunters");
        }
    }

    private Team getOrCreateTeam(Scoreboard scoreboard, String name, ChatColor color, boolean friendlyFire) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }
        team.setColor(color);
        team.setAllowFriendlyFire(friendlyFire);
        return team;
    }

    private void clearTeam(Team team) {
        for (String entry : new ArrayList<>(team.getEntries())) {
            team.removeEntry(entry);
        }
    }

    private void removeFromTeams(Player player) {
        playerTeams.remove(player.getUniqueId());
        var manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard scoreboard = manager.getMainScoreboard();
        for (String teamName : new String[] {"lr_naut", "lr_hunters", "lr_red", "lr_blue"}) {
            Team team = scoreboard.getTeam(teamName);
            if (team != null && team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }
    }

    // When PVP turns on, give the Juggernaut a glow (red via their team) so everyone can spot them.
    private void applyJuggernautGlow() {
        if (activeMode != GameModeType.JUGGERNAUT || juggernaut == null) {
            return;
        }
        Player jugg = Bukkit.getPlayer(juggernaut);
        if (jugg != null) {
            jugg.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 60 * 60, 0, false, false, false));
        }
    }

    private void announceJuggernautKill() {
        playSound(Sound.ENTITY_WITHER_SPAWN, 1.0F, 1.0F);
        long hunters = activePlayers.stream().filter(id -> !id.equals(juggernaut)).count();
        Player jugg = juggernaut == null ? null : Bukkit.getPlayer(juggernaut);
        String hp = jugg == null ? "?" : Integer.toString((int) Math.ceil(jugg.getHealth()));
        String message = ChatColor.DARK_RED + "☠ " + ChatColor.RED + "Juggernaut HP: " + ChatColor.WHITE + hp
                + ChatColor.DARK_GRAY + " | " + ChatColor.BLUE + "Blue team left: " + ChatColor.WHITE + hunters;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(message);
        }
    }

    // ----- Team Chaos (TDM) -----

    private void setupTeamChaos() {
        if (activeMode != GameModeType.TEAM_CHAOS) {
            return;
        }
        var manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard scoreboard = manager.getMainScoreboard();
        Team red = getOrCreateTeam(scoreboard, "lr_red", ChatColor.RED, false);
        Team blue = getOrCreateTeam(scoreboard, "lr_blue", ChatColor.BLUE, false);
        clearTeam(red);
        clearTeam(blue);
        List<Player> players = new ArrayList<>();
        for (UUID id : activePlayers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                players.add(player);
            }
        }
        java.util.Collections.shuffle(players, random);
        List<Player> reds = new ArrayList<>();
        List<Player> blues = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            boolean isRed = i % 2 == 0;
            (isRed ? red : blue).addEntry(player.getName());
            playerTeams.put(player.getUniqueId(), isRed ? "lr_red" : "lr_blue");
            (isRed ? reds : blues).add(player);
        }
        assignTeamChaosRoles(reds);
        assignTeamChaosRoles(blues);
        broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + "TEAM CHAOS! "
                + ChatColor.RED + "Red " + ChatColor.GRAY + "vs " + ChatColor.BLUE + "Blue"
                + ChatColor.GRAY + " — the wall drops at lava Y100!");
    }

    private void assignTeamChaosRoles(List<Player> team) {
        for (int i = 0; i < team.size(); i++) {
            String role = i == 0 ? "TNT_BOW" : (i % 2 == 1 ? "BOW" : "SWORD");
            teamChaosRoles.put(team.get(i).getUniqueId(), role);
        }
    }

    private Map<UUID, Location> computeTeamChaosSpawns(World world, ArenaCenter center, List<Player> participants) {
        Map<UUID, Location> result = new HashMap<>();
        int offset = Math.max(6, plugin.settings().round().arenaDiameter() / 4);
        int redIndex = 0;
        int blueIndex = 0;
        for (Player player : participants) {
            boolean red = "lr_red".equals(playerTeams.get(player.getUniqueId()));
            int idx = red ? redIndex++ : blueIndex++;
            int x = red ? center.x() - offset : center.x() + offset;
            int z = center.z() + ((idx % 2 == 0 ? 1 : -1) * ((idx + 1) / 2) * 2);
            Location loc = arenaService.safeSurfaceLocation(world, x + 0.5D, z + 0.5D);
            if (loc == null) {
                loc = arenaService.emergencyAboveGround(world, x + 0.5D, z + 0.5D);
            }
            result.put(player.getUniqueId(), loc);
        }
        return result;
    }

    private void giveTeamChaosKit(Player player) {
        boolean red = "lr_red".equals(playerTeams.get(player.getUniqueId()));
        int protection = plugin.gamemodes().settings(activeMode).config().getInt("teamChaos.protectionLevel", 1);
        Color color = red ? Color.RED : Color.BLUE;
        var inv = player.getInventory();
        inv.setHelmet(teamArmorPiece(Material.LEATHER_HELMET, color, protection));
        inv.setChestplate(teamArmorPiece(Material.LEATHER_CHESTPLATE, color, protection));
        inv.setLeggings(teamArmorPiece(Material.LEATHER_LEGGINGS, color, protection));
        inv.setBoots(teamArmorPiece(Material.LEATHER_BOOTS, color, protection));
        switch (teamChaosRoles.getOrDefault(player.getUniqueId(), "SWORD")) {
            case "TNT_BOW" -> {
                inv.addItem(makeTntBow());
                inv.addItem(new ItemStack(Material.ARROW, 1));
            }
            case "BOW" -> {
                inv.addItem(new ItemStack(Material.BOW));
                inv.addItem(new ItemStack(Material.ARROW, 1));
            }
            default -> inv.addItem(new ItemStack(Material.IRON_SWORD));
        }
    }

    private ItemStack teamArmorPiece(Material material, Color color, int protection) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta instanceof LeatherArmorMeta leather) {
                leather.setColor(color);
            }
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
            if (protection > 0) {
                meta.addEnchant(Enchantment.PROTECTION, protection, true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeTntBow() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "TNT Bow");
            if (meta instanceof Damageable damageable) {
                damageable.setDamage(Material.BOW.getMaxDurability() - 1);
            }
            bow.setItemMeta(meta);
        }
        return bow;
    }

    private void buildTeamChaosWall(World world) {
        if (activeMode != GameModeType.TEAM_CHAOS || world == null || arenaCenter == null || arenaBounds == null) {
            return;
        }
        var config = plugin.gamemodes().settings(activeMode).config();
        wallX = arenaCenter.x();
        wallMinZ = arenaBounds.minZ();
        wallMaxZExclusive = arenaBounds.maxZExclusive();
        // Full world height (e.g. -64 .. 180) so nobody can get over or under it.
        wallBaseY = Math.max(world.getMinHeight(), config.getInt("teamChaos.wallBaseY", -64));
        wallTopY = Math.min(world.getMaxHeight() - 1, config.getInt("teamChaos.wallTopY", 180));
        wallActive = true;
        fillWall(Material.OBSIDIAN, false, null);
        plugin.logGame("Team Chaos wall building: x=" + wallX + ", z=" + wallMinZ + ".." + wallMaxZExclusive
                + ", y=" + wallBaseY + ".." + wallTopY + ".");
    }

    private void maybeRemoveTeamChaosWall() {
        if (!wallActive) {
            return;
        }
        int removeAt = plugin.gamemodes().settings(activeMode).config().getInt("teamChaos.wallRemoveLavaY", 100);
        if (currentY < removeAt) {
            return;
        }
        wallActive = false;
        fillWall(Material.AIR, true, () -> broadcast(ChatColor.GOLD + "" + ChatColor.BOLD
                + "THE WALL HAS FALLEN! " + ChatColor.GRAY + "Open combat!"));
        plugin.logGame("Team Chaos wall removal started at lavaY=" + currentY + ".");
    }

    // Build/remove the wall a few Z-columns per tick so a full-height wall doesn't lag-spike.
    private void fillWall(Material target, boolean onlyObsidian, Runnable onDone) {
        World world = arenaService.mainWorld();
        if (world == null) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        int x = wallX;
        int maxZExclusive = wallMaxZExclusive;
        int baseY = wallBaseY;
        int topY = wallTopY;
        BukkitTask task = new BukkitRunnable() {
            int z = wallMinZ;

            @Override
            public void run() {
                for (int processed = 0; processed < 8 && z < maxZExclusive; processed++, z++) {
                    for (int y = baseY; y <= topY; y++) {
                        Block block = world.getBlockAt(x, y, z);
                        if (!onlyObsidian || block.getType() == Material.OBSIDIAN) {
                            block.setType(target, false);
                        }
                    }
                }
                if (z >= maxZExclusive) {
                    cancel();
                    if (onDone != null) {
                        onDone.run();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        tasks.add(task);
    }

    // Per-round body effects: player size (Tiny), floaty movement (Low Gravity), Juggernaut health.
    private void applyGamemodeEffects(Player player) {
        var config = plugin.gamemodes().settings(activeMode).config();
        setScale(player, config.getDouble("scale", 1.0D));
        // Long duration; the round-end cleanup clears it well before it expires.
        int ticks = 20 * 60 * 60;
        if (activeMode == GameModeType.LOW_GRAVITY || config.getBoolean("slowFalling", false)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, ticks, 0, false, false, false));
        }
        int jumpBoost = config.getInt("jumpBoostLevel", 0);
        if (jumpBoost > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, jumpBoost - 1,
                    false, false, false));
        }
        // Juggernaut: the chosen player gets double max health + damage resistance (1 vs all).
        double maxHealth = 20.0D;
        if (activeMode == GameModeType.JUGGERNAUT && player.getUniqueId().equals(juggernaut)) {
            maxHealth = 20.0D * config.getDouble("juggernautHealthMultiplier", 2.0D);
            int resistance = config.getInt("juggernautResistanceLevel", 2);
            if (resistance > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, resistance - 1,
                        false, false, false));
            }
        }
        setMaxHealth(player, maxHealth);
        if (maxHealth > 20.0D) {
            player.setHealth(maxHealth);
        }
        // Generic per-gamemode attribute overrides from the config's "attributes:" section.
        resetConfiguredAttributes(player);
        applyConfiguredAttributes(player);
    }

    private void clearGamemodeEffects(Player player) {
        setScale(player, 1.0D);
        setMaxHealth(player, 20.0D);
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.ABSORPTION);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.GLOWING);
        removeFromTeams(player);
        resetConfiguredAttributes(player);
    }

    // Enchanted-golden-apple effects during the celebration so flyers don't burn up and die.
    private void applyCelebrationEffects(Player player) {
        int ticks = (plugin.settings().celebration().seconds() + 10) * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, ticks, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ticks, 3, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 1, false, false, false));
    }

    private void setScale(Player player, double scale) {
        var attribute = player.getAttribute(Attribute.SCALE);
        if (attribute != null) {
            attribute.setBaseValue(Math.max(0.0625D, Math.min(16.0D, scale)));
        }
    }

    private void setMaxHealth(Player player, double maxHealth) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(maxHealth);
            if (player.getHealth() > maxHealth) {
                player.setHealth(maxHealth);
            }
        }
    }

    // Applies any attributes listed under the gamemode config's "attributes:" section,
    // e.g. gravity, jump_strength, safe_fall_distance, movement_speed, attack_damage.
    private void applyConfiguredAttributes(Player player) {
        var section = plugin.gamemodes().settings(activeMode).config().getConfigurationSection("attributes");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Attribute attribute = resolveAttribute(key);
            if (attribute == null) {
                plugin.getLogger().warning("[LavaRising] Unknown attribute '" + key + "' in "
                        + activeMode.id() + ".yml");
                continue;
            }
            var instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.setBaseValue(section.getDouble(key));
                managedAttributes.add(attribute);
            }
        }
    }

    private void resetConfiguredAttributes(Player player) {
        for (Attribute attribute : managedAttributes) {
            var instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.setBaseValue(instance.getDefaultValue());
            }
        }
    }

    private Attribute resolveAttribute(String key) {
        try {
            String name = key.trim().toLowerCase(Locale.ROOT);
            if (name.startsWith("minecraft:")) {
                name = name.substring("minecraft:".length());
            }
            return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(name));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void announceGamemode(GameModeType mode) {
        GamemodeSettings modeSettings = plugin.gamemodes().settings(mode);
        broadcast(ChatColor.GRAY + "Gamemode: " + modeSettings.coloredName());
        List<String> description = modeSettings.description();
        String subtitle = description.isEmpty() ? "" : ChatColor.GRAY + description.get(0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(modeSettings.coloredName(), subtitle, 10, 60, 20);
        }
        plugin.logGame("Gamemode announced: " + mode.id() + ".");
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
        setRoundPvp(world, false, "round live until lava Y=" + plugin.settings().round().pvpEnableY());
        for (Player player : alivePlayers()) {
            if (shouldManageGameMode(player)) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
        broadcast(ChatColor.RED + "" + ChatColor.BOLD + "LAVA RISING HAS STARTED!");
        startActionBarLoop();
        scheduleIntegrityLoop();
        startLuckyLoop();
        scheduleNextLavaRise();
        plugin.logGame("Round live: pvp=false until lavaY=" + plugin.settings().round().pvpEnableY()
                + ", lavaStartY=" + plugin.settings().round().startLavaY()
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
            maybeRemoveTeamChaosWall();
            handleSurfaceDifficulty();
            announcePhaseChange();
            fillLavaLayerBatched(currentY, seconds, () -> {
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

    private void fillLavaLayerBatched(int y, double secondsPerLayer, Runnable whenDone) {
        if (arenaBounds == null || arenaCenter == null) {
            whenDone.run();
            return;
        }

        Queue<ChunkKey> chunks = arenaChunks();
        int chunksThisTick = lavaChunkBudgetFor(secondsPerLayer);
        if (chunksThisTick >= chunks.size()) {
            int changed = 0;
            while (!chunks.isEmpty()) {
                ChunkKey key = chunks.remove();
                World world = Bukkit.getWorld(key.worldName());
                if (world != null) {
                    changed += fillLavaLayerInChunk(world.getChunkAt(key.x(), key.z()), y);
                }
            }
            plugin.logGame("Lava layer filled: y=" + y + ", changedBlocks=" + changed
                    + ", phase=" + phaseForNextLayer().configKey()
                    + ", chunksPerTick=instant"
                    + ", alive=" + alivePlayers().size() + ".");
            whenDone.run();
            return;
        }

        BukkitRunnable runnable = new BukkitRunnable() {
            private int changed;

            @Override
            public void run() {
                if (!isLavaRising()) {
                    cancel();
                    return;
                }

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
                            + ", chunksPerTick=" + chunksThisTick
                            + ", alive=" + alivePlayers().size() + ".");
                    whenDone.run();
                }
            }
        };
        BukkitTask task = runnable.runTaskTimer(plugin, 0L, 1L);
        tasks.add(task);
    }

    private int lavaChunkBudgetFor(double secondsPerLayer) {
        LavaConfig.Performance performance = plugin.settings().performance();
        // Any manual /lava bypass fills instantly so the chosen speed (e.g. 0.5s) is accurate,
        // just like the very fast speeds; otherwise only sub-threshold speeds get the fast budget.
        if (lavaSpeedBypassSeconds != null || secondsPerLayer <= performance.fastLavaSpeedThreshold()) {
            return Math.max(performance.lavaChunksPerTick(), performance.fastLavaChunksPerTick());
        }
        return performance.lavaChunksPerTick();
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
            setRoundPvp(world, true, "deathmatch");
            WorldBorder border = world.getWorldBorder();
            border.setDamageAmount(plugin.settings().deathmatch().borderDamage() ? 0.2D : 0.0D);
            border.setCenter(arenaCenter.x() + 0.5D, arenaCenter.z() + 0.5D);
            border.setSize(plugin.settings().deathmatch().borderDiameter(),
                    TimeUnit.SECONDS,
                    plugin.settings().deathmatch().borderShrinkSeconds());
        }
        // The deathmatch banner is sent as one styled message by announcePhaseChange().
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
                ensureRoundPvpState();
                String pvp = isPvpEnabled() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
                String message = ChatColor.RED + "Lava Y " + ChatColor.WHITE + currentY
                        + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "PVP " + pvp;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendActionBar(message);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        tasks.add(task);
    }

    private void giveRoundBlocks() {
        if (!plugin.gamemodes().settings(activeMode).givesBuildingBlocks()) {
            return;
        }
        LavaConfig.Round round = plugin.settings().round();
        if (!round.giveBlocks() || round.blockGiveRate() <= 0 || round.maxGivenBlocks() <= 0) {
            return;
        }
        int juggernautMultiplier = activeMode == GameModeType.JUGGERNAUT
                ? Math.max(1, plugin.gamemodes().settings(activeMode).config().getInt("juggernautBlockMultiplier", 2))
                : 1;
        for (Player player : alivePlayers()) {
            int multiplier = player.getUniqueId().equals(juggernaut) ? juggernautMultiplier : 1;
            int currentAmount = countMaterial(player, buildingBlock);
            int missing = round.maxGivenBlocks() * multiplier - currentAmount;
            if (missing <= 0) {
                continue;
            }
            int amount = Math.min(round.blockGiveRate() * multiplier, missing);
            player.getInventory().addItem(new ItemStack(buildingBlock, amount));
        }
    }

    private void applyStartKit(Player player) {
        var config = plugin.gamemodes().settings(activeMode).config();
        if (activeMode == GameModeType.RANGE_RAMPAGE) {
            if (config.getBoolean("startBow", true)) {
                ItemStack bow = new ItemStack(Material.BOW);
                int punch = config.getInt("bowPunchLevel", 1);
                if (punch > 0) {
                    bow.addUnsafeEnchantment(Enchantment.PUNCH, punch);
                }
                player.getInventory().addItem(bow);
            }
            int arrows = config.getInt("startArrows", 8);
            if (arrows > 0) {
                player.getInventory().addItem(new ItemStack(Material.ARROW, arrows));
            }
        } else if (activeMode == GameModeType.JUGGERNAUT && player.getUniqueId().equals(juggernaut)) {
            // Only the Juggernaut gets gear (a random kit) — everyone else starts with nothing.
            if (juggernautKits != null && !juggernautKits.isEmpty()) {
                juggernautKits.giveRandom(player, random);
            } else {
                // Fallback if no kits are configured: the classic wither-skull set.
                int iron = config.getInt("juggernautIron", 8);
                if (iron > 0) {
                    player.getInventory().addItem(new ItemStack(Material.IRON_INGOT, iron));
                }
                player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                player.getInventory().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
                player.getInventory().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
                player.getInventory().setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
            }
        } else if (activeMode == GameModeType.TEAM_CHAOS) {
            giveTeamChaosKit(player);
        }
    }

    public void handleKill(Player killer) {
        if (killer == null || !isActivePlayer(killer)) {
            return;
        }
        kills.merge(killer.getUniqueId(), 1, Integer::sum);
        if (activeMode == GameModeType.RANGE_RAMPAGE) {
            int arrows = plugin.gamemodes().settings(activeMode).config().getInt("arrowsPerKill", 2);
            if (arrows > 0) {
                killer.getInventory().addItem(new ItemStack(Material.ARROW, arrows));
                killer.sendActionBar(ChatColor.GREEN + "+" + arrows + " arrows");
            }
        } else if (activeMode == GameModeType.JUGGERNAUT && isJuggernaut(killer)) {
            // Wither sound + reminder action bar when the Juggernaut downs a hunter.
            Bukkit.getScheduler().runTask(plugin, this::announceJuggernautKill);
        }
    }

    private void startLuckyLoop() {
        if (activeMode != GameModeType.LUCKY_LAVA_RUSH) {
            return;
        }
        var config = plugin.gamemodes().settings(activeMode).config();
        LuckyLootTable loot = new LuckyLootTable(config);
        if (loot.isEmpty()) {
            return;
        }
        int intervalTicks = Math.max(20, config.getInt("luckyIntervalSeconds", 1) * 20);
        int perDrop = Math.max(1, config.getInt("luckyItemsPerDrop", 1));
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isLavaRising()) {
                    cancel();
                    return;
                }
                for (Player player : alivePlayers()) {
                    for (int i = 0; i < perDrop; i++) {
                        player.getInventory().addItem(loot.roll(random));
                    }
                }
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
        tasks.add(task);
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
        if (y == plugin.settings().round().maxLavaY()) {
            if (plugin.settings().round().milestoneMessages()) {
                broadcast(ChatColor.DARK_RED + "Lava has reached the top at Y=" + ChatColor.WHITE + y
                        + ChatColor.DARK_RED + ".");
            }
            playSound(Sound.BLOCK_BELL_USE, 1.0F, 0.5F);
        }
    }

    private void announcePhaseChange() {
        LavaPhase phase = phaseForLayer(currentY);
        if (phase == lastLavaPhase) {
            return;
        }
        lastLavaPhase = phase;
        double speed = lavaSpeedBypassSeconds == null ? plugin.settings().lavaSpeed(phase) : lavaSpeedBypassSeconds;
        if (plugin.settings().round().milestoneMessages()) {
            String message = phaseChatMessage(phase, speed);
            if (message != null) {
                broadcast(message);
            }
        }
        playSound(phaseSound(phase), 1.0F, phasePitch(phase));
        if (plugin.settings().round().phaseTitles()) {
            String title = phaseTitle(phase);
            if (title != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendTitle(title, "", 10, 50, 20);
                }
            }
        }
        plugin.logGame("Lava phase reached: " + phase.configKey() + " at Y=" + currentY
                + ", speed=" + formatSeconds(speed) + "s.");
    }

    private String phaseTitle(LavaPhase phase) {
        return switch (phase) {
            case Y0_TO_Y60 -> ChatColor.WHITE + "" + ChatColor.BOLD + "Y=0";
            case Y60_TO_Y100 -> ChatColor.GREEN + "" + ChatColor.BOLD + "Y=60";
            case Y100_TO_DEATHMATCH -> ChatColor.RED + "" + ChatColor.BOLD + "Y=100";
            case DEATHMATCH_TO_TOP -> ChatColor.RED + "" + ChatColor.BOLD + "BORDER CLOSING";
            case START_TO_Y0 -> null;
        };
    }

    private String phaseChatMessage(LavaPhase phase, double speed) {
        String rate = ChatColor.DARK_GRAY + " (1 block / " + formatSeconds(speed) + "s)";
        return switch (phase) {
            case Y0_TO_Y60 -> ChatColor.WHITE + "" + ChatColor.BOLD + "Y=0 "
                    + ChatColor.RESET + ChatColor.GRAY + "Lava is rising out of the caves." + rate;
            case Y60_TO_Y100 -> ChatColor.GREEN + "" + ChatColor.BOLD + "Y=60 "
                    + ChatColor.RESET + ChatColor.GREEN + "Lava reached the surface — PVP is now ON!" + rate;
            case Y100_TO_DEATHMATCH -> ChatColor.RED + "" + ChatColor.BOLD + "Y=100 "
                    + ChatColor.RESET + ChatColor.GRAY + "Lava is climbing into the sky." + rate;
            case DEATHMATCH_TO_TOP -> ChatColor.DARK_RED + "" + ChatColor.BOLD + "DEATHMATCH "
                    + ChatColor.RESET + ChatColor.RED + "The border is closing in!";
            case START_TO_Y0 -> null;
        };
    }

    private Sound phaseSound(LavaPhase phase) {
        return switch (phase) {
            case Y0_TO_Y60 -> Sound.ENTITY_WARDEN_ROAR;
            case Y60_TO_Y100 -> Sound.ENTITY_WITHER_SPAWN;
            case Y100_TO_DEATHMATCH -> Sound.ENTITY_ENDER_DRAGON_GROWL;
            case DEATHMATCH_TO_TOP -> Sound.BLOCK_SCULK_SHRIEKER_SHRIEK;
            case START_TO_Y0 -> Sound.BLOCK_BELL_USE;
        };
    }

    private float phasePitch(LavaPhase phase) {
        return switch (phase) {
            case Y0_TO_Y60 -> 0.8F;
            case DEATHMATCH_TO_TOP -> 0.7F;
            default -> 1.0F;
        };
    }

    private void checkWinCondition() {
        if (state != GameState.RUNNING && state != GameState.DEATHMATCH) {
            return;
        }
        List<Player> alive = alivePlayers();
        if (isTeamMode()) {
            Set<String> teams = new HashSet<>();
            for (Player player : alive) {
                String team = playerTeams.get(player.getUniqueId());
                if (team != null) {
                    teams.add(team);
                }
            }
            if (teams.size() <= 1) {
                startCelebration(alive.isEmpty() ? null : alive.getFirst(),
                        teams.isEmpty() ? null : teams.iterator().next());
            }
            return;
        }
        if (alive.size() > 1) {
            return;
        }
        startCelebration(alive.isEmpty() ? null : alive.getFirst(), null);
    }

    private boolean isTeamMode() {
        return activeMode == GameModeType.TEAM_CHAOS || activeMode == GameModeType.JUGGERNAUT;
    }

    private String teamDisplayName(String teamName) {
        return switch (teamName) {
            case "lr_red" -> ChatColor.RED + "Red Team";
            case "lr_blue" -> ChatColor.BLUE + "Blue Team";
            case "lr_naut" -> ChatColor.DARK_RED + "The Juggernaut";
            case "lr_hunters" -> ChatColor.BLUE + "The Hunters";
            default -> ChatColor.GRAY + "A team";
        };
    }

    private void broadcastTopKills() {
        List<Map.Entry<UUID, Integer>> top = kills.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(3)
                .toList();
        if (top.isEmpty()) {
            return;
        }
        broadcast(ChatColor.GRAY + "" + ChatColor.BOLD + "Top Kills Leaderboard");
        for (int i = 0; i < top.size(); i++) {
            Player player = Bukkit.getPlayer(top.get(i).getKey());
            String name = player != null ? player.getName() : Bukkit.getOfflinePlayer(top.get(i).getKey()).getName();
            broadcast(ChatColor.GRAY + "#" + (i + 1) + " " + (name == null ? "?" : name)
                    + " - " + top.get(i).getValue() + " kills");
        }
    }

    private void startCelebration(Player winner, String winningTeam) {
        if (state == GameState.CELEBRATION || state == GameState.WAITING) {
            return;
        }
        stopTasks();
        state = GameState.CELEBRATION;
        World world = arenaService.mainWorld();
        setRoundPvp(world, false, "celebration");

        savedFlightStates.clear();
        showAllPlayers();

        // Announce the result immediately.
        if (winningTeam != null) {
            broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + teamDisplayName(winningTeam)
                    + ChatColor.GOLD + ChatColor.BOLD + " wins!");
        } else if (winner != null) {
            broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + winner.getName() + " wins!");
        } else {
            broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + "Round ended with no winner.");
        }
        broadcastTopKills();

        // Wait a few seconds so the player who just died can respawn and join the gamemode
        // change + celebration effects, instead of missing them while on the death screen.
        int delayTicks = 3 * 20;
        BukkitTask setup = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != GameState.CELEBRATION) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                prepareCelebrationParticipant(player);
            }
            if (winner != null) {
                startFireworkLoop(winner);
            }
        }, delayTicks);
        tasks.add(setup);

        BukkitTask finish = Bukkit.getScheduler().runTaskLater(plugin,
                this::finishCelebration,
                delayTicks + plugin.settings().celebration().seconds() * 20L);
        tasks.add(finish);
        plugin.logGame("Celebration started: winner=" + (winner == null ? "none" : winner.getName())
                + ", seconds=" + plugin.settings().celebration().seconds() + ".");
    }

    private void prepareCelebrationParticipant(Player player) {
        // Celebrate up in the lobby, not down in the lava arena (configurable).
        if (plugin.settings().celebration().lobbyCelebration()) {
            Location lobby = arenaService.lobbyLocation();
            if (lobby != null) {
                player.teleport(lobby);
            }
        }
        savedFlightStates.putIfAbsent(player.getUniqueId(),
                new FlightState(player.getAllowFlight(), player.isFlying()));
        if (player.getGameMode() == GameMode.SPECTATOR || shouldManageGameMode(player)) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        applyCelebrationEffects(player);
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
        Color[] palette = {Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.AQUA, Color.FUCHSIA, Color.WHITE};
        FireworkEffect.Type[] shapes = {FireworkEffect.Type.BALL, FireworkEffect.Type.BALL_LARGE,
                FireworkEffect.Type.STAR, FireworkEffect.Type.BURST};
        FireworkEffect effect = FireworkEffect.builder()
                .with(shapes[random.nextInt(shapes.length)])
                .withColor(palette[random.nextInt(palette.length)], palette[random.nextInt(palette.length)])
                .withFade(palette[random.nextInt(palette.length)])
                .trail(true)
                .flicker(true)
                .build();
        // Apply the effect via the spawn consumer so it's set BEFORE the rocket ticks/detonates.
        world.spawn(location, Firework.class, (java.util.function.Consumer<Firework>) firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(effect);
            meta.setPower(1);
            firework.setFireworkMeta(meta);
        });
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
            player.setInvulnerable(false);
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

    private void setRoundPvp(World world, boolean enabled, String reason) {
        roundPvpEnabled = enabled;
        if (world != null) {
            if (world.getPVP() != enabled) {
                world.setPVP(enabled);
            }
            Difficulty difficulty = pvpDifficulty(enabled);
            if (world.getDifficulty() != difficulty) {
                world.setDifficulty(difficulty);
            }
        }
        if (enabled) {
            applyJuggernautGlow();
        }
        plugin.logGame("Round PVP " + (enabled ? "enabled" : "disabled") + ": reason=" + reason
                + ", difficulty=" + pvpDifficulty(enabled) + ".");
    }

    private Difficulty pvpDifficulty(boolean pvpOn) {
        // PVP off -> peaceful (calm build phase), PVP on -> the combat difficulty.
        return pvpOn
                ? plugin.settings().round().surfaceDifficulty()
                : plugin.settings().round().countdownDifficulty();
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
        roundPvpEnabled = false;
        lastLavaPhase = LavaPhase.START_TO_Y0;
        currentY = plugin.settings().round().startLavaY() - 1;
        arenaCenter = null;
        arenaBounds = null;
        sandMayhemRound = false;
        juggernaut = null;
        juggernautKits = null;
        playerTeams.clear();
        teamChaosRoles.clear();
        kills.clear();
        wallActive = false;
        activeMode = GameModeType.CLASSIC;
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

    private String capitalizeWords(String enumName) {
        StringBuilder sb = new StringBuilder();
        for (String part : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + " "
                + String.format(Locale.ROOT, "%.1f,%.1f,%.1f",
                location.getX(), location.getY(), location.getZ());
    }
}
