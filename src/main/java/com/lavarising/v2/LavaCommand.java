package com.lavarising.v2;

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
import org.bukkit.entity.Player;

public final class LavaCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "reset", "status", "bypass", "revive", "setlobby", "set", "get", "reload", "help");
    private static final List<String> SETTINGS = List.of(
            "logging.console",
            "lobby.x",
            "lobby.z",
            "lobby.radius",
            "start.minPlayers",
            "start.countdownSeconds",
            "round.arenaDiameter",
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
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> handleStart(sender);
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

    private void handleStart(CommandSender sender) {
        if (!canStart(sender)) {
            return;
        }
        StartResult result = plugin.game().startRound();
        switch (result) {
            case STARTED -> sender.sendMessage(ChatColor.YELLOW + "Round countdown started.");
            case ALREADY_ACTIVE -> sender.sendMessage(ChatColor.RED + "A round is already active.");
            case NO_WORLD -> sender.sendMessage(ChatColor.RED + "Could not find the configured lobby world.");
            case NOT_ENOUGH_PLAYERS -> sender.sendMessage(ChatColor.RED + "Need "
                    + plugin.settings().start().minPlayers() + " players in lobby. Current: "
                    + plugin.game().lobbyPlayers().size() + ".");
            case NO_ARENA_FOUND -> sender.sendMessage(ChatColor.RED
                    + "No playable fresh arena found. Try increasing arenaSelection.maxAttempts/searchMaxRadius.");
        }
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
        sender.sendMessage(ChatColor.YELLOW + "LavaRising 2.0.0");
        sender.sendMessage(ChatColor.GRAY + "State: " + ChatColor.WHITE + plugin.game().state());
        sender.sendMessage(ChatColor.GRAY + "Lobby: " + ChatColor.WHITE
                + formatCoordinate(lobby.x()) + ", " + formatCoordinate(lobby.z())
                + ChatColor.GRAY + " radius " + ChatColor.WHITE + lobby.radius()
                + ChatColor.GRAY + " players " + ChatColor.WHITE
                + plugin.game().lobbyPlayers().size() + "/" + plugin.settings().start().minPlayers());
        sender.sendMessage(ChatColor.GRAY + "Lava Y: " + ChatColor.WHITE + plugin.game().currentY()
                + ChatColor.GRAY + " | PVP: " + (plugin.game().isCombatLive()
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
            sender.sendMessage(ChatColor.RED + "Usage: /lava revive <player> [targetPlayer]");
            return;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        Player anchor = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player is not online: " + args[1]);
            return;
        }
        if (args.length >= 3 && anchor == null) {
            sender.sendMessage(ChatColor.RED + "Target player is not online: " + args[2]);
            return;
        }

        ReviveResult result = plugin.game().revive(player, anchor);
        switch (result.status()) {
            case REVIVED -> sender.sendMessage(ChatColor.YELLOW + "Revived " + player.getName()
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
        if (!plugin.game().isWaiting()) {
            sender.sendMessage(ChatColor.RED + "Stop the current round before changing the lobby.");
            return;
        }

        try {
            if (args.length == 1 || args.length == 2) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Console usage: /lava setlobby <x> <z> [radius]");
                    return;
                }
                Integer radius = args.length == 2 ? Integer.parseInt(args[1]) : null;
                Location location = player.getLocation();
                setLobby(location.getX(), location.getZ(), radius);
                sendLobbySet(sender);
                return;
            }

            if (args.length == 3 || args.length == 4) {
                double x = Double.parseDouble(args[1]);
                double z = Double.parseDouble(args[2]);
                Integer radius = args.length == 4 ? Integer.parseInt(args[3]) : null;
                setLobby(x, z, radius);
                sendLobbySet(sender);
                return;
            }

            sender.sendMessage(ChatColor.RED + "Usage: /lava setlobby [radius] or /lava setlobby <x> <z> [radius]");
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Lobby X/Z must be numbers and radius must be a whole number.");
        }
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

    private void setLobby(double x, double z, Integer radius) {
        plugin.getConfig().set("lobby.x", x);
        plugin.getConfig().set("lobby.z", z);
        if (radius != null) {
            plugin.getConfig().set("lobby.radius", Math.max(4, Math.min(512, radius)));
        }
        plugin.saveConfig();
        plugin.reloadSettings();
    }

    private void sendLobbySet(CommandSender sender) {
        LavaConfig.Lobby lobby = plugin.settings().lobby();
        sender.sendMessage(ChatColor.YELLOW + "Lobby set to " + ChatColor.WHITE
                + formatCoordinate(lobby.x()) + ", " + formatCoordinate(lobby.z())
                + ChatColor.YELLOW + " radius " + ChatColor.WHITE + lobby.radius() + ChatColor.YELLOW + ".");
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
        sender.sendMessage(ChatColor.GRAY + "/lava start" + ChatColor.DARK_GRAY + " - start from lobby");
        sender.sendMessage(ChatColor.GRAY + "/lava status" + ChatColor.DARK_GRAY + " - show state");
        sender.sendMessage(ChatColor.GRAY + "/lava bypass [seconds|clear]" + ChatColor.DARK_GRAY
                + " - override lava speed");
        sender.sendMessage(ChatColor.GRAY + "/lava setlobby [radius]" + ChatColor.DARK_GRAY + " - set lobby here");
        sender.sendMessage(ChatColor.GRAY + "/lava revive <player> [target]" + ChatColor.DARK_GRAY
                + " - revive a player");
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
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[args.length - 1]);
        }
        if (sub.equals("setlobby")) {
            if (args.length == 2) {
                return filter(List.of("32", "64", "0.5", "100.5", "-100.5"), args[1]);
            }
            if (args.length == 3) {
                return filter(List.of("0.5", "100.5", "-100.5"), args[2]);
            }
            if (args.length == 4) {
                return filter(List.of("32", "48", "64"), args[3]);
            }
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
