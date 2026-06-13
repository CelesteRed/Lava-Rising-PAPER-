package com.lavarising.v2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class LavaCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "reset", "status", "bypass", "revive", "setlobby", "set", "get", "reload", "help");
    private static final List<String> SETTINGS = List.of(
            "logging.console",
            "lobby.world",
            "lobby.namespace",
            "round.world",
            "start.minPlayers",
            "start.countdownSeconds",
            "round.arenaDiameter",
            "round.pvpEnableY",
            "round.countdownDifficulty",
            "round.surfaceDifficulty",
            "round.phase.1",
            "round.phase.2",
            "round.phase.3",
            "round.phase.4",
            "round.phase.5",
            "round.phaseTitles",
            "round.blockGiveRate",
            "round.maxGivenBlocks",
            "round.sandMayhemChance",
            "round.villageStartChance",
            "deathmatch.borderRadius",
            "deathmatch.borderShrinkSeconds",
            "celebration.seconds",
            "performance.forceLoadArenaChunks",
            "performance.forceLoadedChunkRadius",
            "performance.lavaChunksPerTick",
            "performance.fastLavaSpeedThreshold",
            "performance.fastLavaChunksPerTick",
            "arenaSelection.biomeWhitelist");

    private final LavaRisingPlugin plugin;

    public LavaCommand(LavaRisingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /start [gamemode] is a shortcut for /lava start [gamemode].
        if (command.getName().equalsIgnoreCase("start")) {
            String[] startArgs = new String[args.length + 1];
            startArgs[0] = "start";
            System.arraycopy(args, 0, startArgs, 1, args.length);
            handleStart(sender, startArgs);
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> handleStart(sender, args);
            case "stop" -> handleStop(sender);
            case "reset" -> handleReset(sender);
            case "status" -> handleStatus(sender);
            case "bypass" -> handleBypass(sender, args);
            case "revive" -> handleRevive(sender, args);
            case "setlobby" -> handleSetLobby(sender, args);
            case "set" -> handleSet(sender, args);
            case "get" -> handleGet(sender, args);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /lava help.");
        }
        return true;
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (!canStart(sender)) {
            return;
        }
        if (plugin.game().isVoting()) {
            sender.sendMessage(ChatColor.RED + "A gamemode vote is already in progress.");
            return;
        }
        if (!plugin.game().isWaiting()) {
            sender.sendMessage(ChatColor.RED + "A round is already active.");
            return;
        }
        int lobbyCount = plugin.game().lobbyPlayers().size();
        int min = plugin.settings().start().minPlayers();
        if (lobbyCount < min) {
            sender.sendMessage(ChatColor.RED + "Need " + min + " players in lobby. Current: " + lobbyCount + ".");
            return;
        }

        if (args.length >= 2) {
            if (!requireAdmin(sender)) {
                return;
            }
            GameModeType mode = GameModeType.byId(args[1]);
            if (mode == null) {
                sender.sendMessage(ChatColor.RED + "Unknown gamemode '" + args[1] + "'. Options: " + modeIdList());
                return;
            }
            sender.sendMessage(ChatColor.YELLOW + "Forcing gamemode "
                    + plugin.gamemodes().settings(mode).coloredName() + ChatColor.YELLOW + "...");
            plugin.game().startRoundWithFeedback(mode);
            return;
        }

        plugin.game().beginGamemodeVote();
    }

    private String modeIdList() {
        return java.util.Arrays.stream(GameModeType.values()).map(GameModeType::id)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private void handleStop(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (plugin.game().isWaiting()) {
            sender.sendMessage(ChatColor.GRAY + "No round is active.");
            return;
        }
        plugin.game().stopRound(true);
        sender.sendMessage(ChatColor.YELLOW + "Round stopped and players returned to lobby.");
    }

    private void handleReset(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        plugin.game().stopRound(true);
        sender.sendMessage(ChatColor.YELLOW + "Round reset complete.");
    }

    private void handleStatus(CommandSender sender) {
        LavaConfig.Lobby lobby = plugin.settings().lobby();
        sender.sendMessage(ChatColor.YELLOW + "LavaRising 2.5.30");
        sender.sendMessage(ChatColor.GRAY + "State: " + ChatColor.WHITE + plugin.game().state());
        sender.sendMessage(ChatColor.GRAY + "Lobby dimension: " + ChatColor.WHITE + lobby.dimensionKey()
                + ChatColor.GRAY + " spawn " + ChatColor.WHITE
                + (lobby.configured()
                        ? formatCoordinate(lobby.x()) + ", " + formatCoordinate(lobby.y())
                                + ", " + formatCoordinate(lobby.z())
                        : "platform centre")
                + ChatColor.GRAY + " players " + ChatColor.WHITE
                + plugin.game().lobbyPlayers().size() + "/" + plugin.settings().start().minPlayers());
        sender.sendMessage(ChatColor.GRAY + "Game world: " + ChatColor.WHITE + plugin.settings().round().world());
        sender.sendMessage(ChatColor.GRAY + "Lava Y: " + ChatColor.WHITE + plugin.game().currentY()
                + ChatColor.GRAY + " | PVP: " + (plugin.game().isPvpEnabled()
                ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
        sender.sendMessage(ChatColor.GRAY + "Speed: " + ChatColor.WHITE + speedLabel());
    }

    private void handleBypass(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length == 1) {
            plugin.game().setLavaSpeedBypass(0.25D);
            sender.sendMessage(ChatColor.YELLOW + "Lava speed bypass set to "
                    + ChatColor.WHITE + "0.25 seconds per layer" + ChatColor.YELLOW + ".");
            return;
        }
        if (args[1].equalsIgnoreCase("clear") || args[1].equalsIgnoreCase("off")) {
            plugin.game().setLavaSpeedBypass(null);
            sender.sendMessage(ChatColor.YELLOW + "Lava speed bypass cleared.");
            return;
        }
        try {
            double seconds = Double.parseDouble(args[1]);
            if (!Double.isFinite(seconds) || seconds <= 0.0D) {
                throw new NumberFormatException("seconds must be positive");
            }
            plugin.game().setLavaSpeedBypass(seconds);
            sender.sendMessage(ChatColor.YELLOW + "Lava speed bypass set to "
                    + ChatColor.WHITE + formatSeconds(seconds) + " seconds per layer" + ChatColor.YELLOW + ".");
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Usage: /lava bypass [seconds|clear]");
        }
    }

    private void handleRevive(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /lava revive <player|@a|@r|@s|@p> [anchor]");
            return;
        }
        List<Player> targets = resolvePlayers(sender, args[1]);
        if (targets.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No online players matched: " + args[1]);
            return;
        }
        Player anchor = null;
        if (args.length >= 3) {
            String anchorToken = args[2];
            List<Player> anchorMatches = resolvePlayers(sender, anchorToken);
            if (anchorMatches.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No online players matched anchor: " + anchorToken);
                return;
            }
            // Prefer a living match so selectors like @r / @a land on a valid anchor instead
            // of failing on a dead pick. If a selector matched only dead players, fall back to
            // any alive player; an explicit name that isn't alive still reports the problem.
            List<Player> aliveNow = plugin.game().alivePlayers();
            for (Player candidate : anchorMatches) {
                if (aliveNow.contains(candidate)) {
                    anchor = candidate;
                    break;
                }
            }
            if (anchor == null && !anchorToken.startsWith("@")) {
                anchor = anchorMatches.get(0);
            }
        }

        if (targets.size() == 1) {
            reportRevive(sender, plugin.game().revive(targets.get(0), anchor), targets.get(0));
            return;
        }
        // Bulk selector (e.g. @a): revive each match and summarise.
        int revived = 0;
        for (Player target : targets) {
            if (plugin.game().revive(target, anchor).status() == ReviveStatus.REVIVED) {
                revived++;
            }
        }
        sender.sendMessage(ChatColor.YELLOW + "Revived " + revived + " of " + targets.size() + " matched players.");
    }

    // Resolves a target argument as a Minecraft selector (@a, @r, @s, @p, @e, names, etc.),
    // falling back to an exact online-player name if it isn't a valid selector.
    private List<Player> resolvePlayers(CommandSender sender, String token) {
        List<Player> result = new ArrayList<>();
        try {
            for (Entity entity : Bukkit.selectEntities(sender, token)) {
                if (entity instanceof Player player) {
                    result.add(player);
                }
            }
        } catch (IllegalArgumentException ex) {
            Player player = Bukkit.getPlayerExact(token);
            if (player != null) {
                result.add(player);
            }
        }
        return result;
    }

    private void reportRevive(CommandSender sender, ReviveResult result, Player target) {
        switch (result.status()) {
            case REVIVED -> sender.sendMessage(ChatColor.YELLOW + "Revived " + target.getName()
                    + " onto " + result.anchor().getName() + ".");
            case NOT_RUNNING -> sender.sendMessage(ChatColor.RED + "A live round must be running.");
            case PLAYER_NOT_ONLINE -> sender.sendMessage(ChatColor.RED + "Player is not online.");
            case PLAYER_NOT_RESPAWNED -> sender.sendMessage(ChatColor.RED
                    + "That player is still on the death screen. Wait for respawn.");
            case ALREADY_ALIVE -> sender.sendMessage(ChatColor.RED + "That player is already alive.");
            case ANCHOR_NOT_ALIVE -> sender.sendMessage(ChatColor.RED + "The target player is not alive in the round.");
            case NO_ALIVE_ANCHOR -> sender.sendMessage(ChatColor.RED + "There is no alive player to revive onto.");
        }
    }

    private void handleSetLobby(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Stand in the lobby dimension and run /lava setlobby.");
            return;
        }
        if (!player.getWorld().getName().equals(plugin.settings().lobby().world())) {
            sender.sendMessage(ChatColor.RED + "Stand inside the lobby dimension ("
                    + plugin.settings().lobby().dimensionKey() + ") first. "
                    + ChatColor.GRAY + "You spawn there on join when no round is running.");
            return;
        }

        Location location = player.getLocation();
        plugin.getConfig().set("lobby.x", location.getX());
        plugin.getConfig().set("lobby.y", location.getY());
        plugin.getConfig().set("lobby.z", location.getZ());
        plugin.getConfig().set("lobby.yaw", location.getYaw());
        plugin.getConfig().set("lobby.pitch", location.getPitch());
        plugin.getConfig().set("lobby.configured", true);
        plugin.saveConfig();
        plugin.reloadSettings();

        LavaConfig.Lobby lobby = plugin.settings().lobby();
        sender.sendMessage(ChatColor.YELLOW + "Lobby spawn set to " + ChatColor.WHITE
                + lobby.dimensionKey() + " " + formatCoordinate(lobby.x()) + ", "
                + formatCoordinate(lobby.y()) + ", " + formatCoordinate(lobby.z())
                + ChatColor.YELLOW + ". Players spawn here on join (no round) and after rounds.");
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /lava set <config.path> <value>");
            return;
        }
        String path = args[1];
        String rawValue = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        plugin.getConfig().set(path, parseConfigValue(rawValue));
        plugin.saveConfig();
        plugin.reloadSettings();
        sender.sendMessage(ChatColor.YELLOW + path + " set to " + ChatColor.WHITE + rawValue + ChatColor.YELLOW + ".");
    }

    private void handleGet(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /lava get <config.path>");
            return;
        }
        Object value = plugin.getConfig().get(args[1]);
        sender.sendMessage(ChatColor.YELLOW + args[1] + ": " + ChatColor.WHITE
                + (value == null ? "<not set>" : value.toString()));
    }

    private void handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        plugin.reloadSettings();
        sender.sendMessage(ChatColor.YELLOW + "LavaRising config reloaded.");
    }

    private boolean canStart(CommandSender sender) {
        if (plugin.game().isAdmin(sender)) {
            return true;
        }
        if (!sender.hasPermission("lavarising.start")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to start rounds.");
            return false;
        }
        if (plugin.settings().start().publicStartWhenNoAdminOnline() && !hasOnlineAdmin()) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + "An admin is online, so an admin must start the round.");
        return false;
    }

    private boolean hasOnlineAdmin() {
        return Bukkit.getOnlinePlayers().stream().anyMatch(plugin.game()::isAdmin);
    }

    private boolean requireAdmin(CommandSender sender) {
        if (plugin.game().isAdmin(sender)) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
        return false;
    }

    private Object parseConfigValue(String rawValue) {
        String normalized = rawValue.trim();
        if (normalized.equalsIgnoreCase("true") || normalized.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(normalized);
        }
        if (normalized.contains(",")) {
            return Stream.of(normalized.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            // Try double next.
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return rawValue;
        }
    }

    private String speedLabel() {
        Double bypass = plugin.game().lavaSpeedBypassSeconds();
        if (bypass != null) {
            return formatSeconds(bypass) + "s bypass";
        }
        LavaPhase phase = plugin.game().phaseForNextLayer();
        return phase.displayName() + " " + formatSeconds(plugin.settings().lavaSpeed(phase)) + "s";
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "LavaRising commands:");
        sender.sendMessage(ChatColor.GRAY + "/lava start [gamemode]" + ChatColor.DARK_GRAY
                + " - open the gamemode vote (or force a mode)");
        sender.sendMessage(ChatColor.GRAY + "/start [gamemode]" + ChatColor.DARK_GRAY
                + " - shortcut for /lava start");
        sender.sendMessage(ChatColor.GRAY + "/lava status" + ChatColor.DARK_GRAY + " - show state");
        sender.sendMessage(ChatColor.GRAY + "/lava bypass [seconds|clear]" + ChatColor.DARK_GRAY
                + " - override lava speed");
        sender.sendMessage(ChatColor.GRAY + "/lava setlobby" + ChatColor.DARK_GRAY
                + " - set the lobby spawn (stand in the lobby dimension)");
        sender.sendMessage(ChatColor.GRAY + "/lava revive <player|@a|@r|@s|@p> [anchor]" + ChatColor.DARK_GRAY
                + " - revive a player (supports target selectors)");
        sender.sendMessage(ChatColor.GRAY + "/lava set <path> <value>" + ChatColor.DARK_GRAY + " - update config");
    }

    private String formatSeconds(double seconds) {
        if (Math.abs(seconds - Math.rint(seconds)) < 0.0001D) {
            return Integer.toString((int) Math.rint(seconds));
        }
        return String.format(Locale.ROOT, "%.2f", seconds);
    }

    private String formatCoordinate(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return Integer.toString((int) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("start")) {
            if (args.length == 1) {
                return filter(java.util.Arrays.stream(GameModeType.values()).map(GameModeType::id).toList(), args[0]);
            }
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ((sub.equals("set") || sub.equals("get")) && args.length == 2) {
            return filter(SETTINGS, args[1]);
        }
        if (sub.equals("bypass") && args.length == 2) {
            return filter(List.of("0.25", "0.5", "1", "2", "clear"), args[1]);
        }
        if (sub.equals("revive") && (args.length == 2 || args.length == 3)) {
            List<String> options = new ArrayList<>(List.of("@a", "@p", "@r", "@s"));
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            return filter(options, args[args.length - 1]);
        }
        if (sub.equals("start") && args.length == 2) {
            return filter(java.util.Arrays.stream(GameModeType.values()).map(GameModeType::id).toList(), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }
}
