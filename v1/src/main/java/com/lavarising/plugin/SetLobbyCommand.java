package com.lavarising.plugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class SetLobbyCommand implements CommandExecutor, TabCompleter {
    private final LavaRisingManager manager;

    public SetLobbyCommand(LavaRisingManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!isAdmin(sender)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to set the lobby.");
            return true;
        }
        if (manager.isInGame()) {
            sender.sendMessage(ChatColor.RED + "Stop the current game before changing the lobby.");
            return true;
        }

        try {
            if (args.length == 0 || args.length == 1) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Console usage: /setlobby <x> <z> [radius]");
                    return true;
                }

                Integer radius = args.length == 1 ? Integer.parseInt(args[0]) : null;
                manager.setLobbyLocation(player.getLocation(), radius);
                sendLobbySet(sender);
                return true;
            }

            if (args.length == 2 || args.length == 3) {
                double x = parseFiniteDouble(args[0]);
                double z = parseFiniteDouble(args[1]);
                Integer radius = args.length == 3 ? Integer.parseInt(args[2]) : null;
                manager.setLobbyLocation(x, z, radius);
                sendLobbySet(sender);
                return true;
            }

            sendUsage(sender);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Lobby coordinates must be numbers and radius must be a whole number.");
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
        }
        return true;
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("lavarising.use");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage: /setlobby [radius] or /setlobby <x> <z> [radius]");
    }

    private void sendLobbySet(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Lobby set to "
                + ChatColor.WHITE + formatCoordinate(manager.getLobbyX()) + ", "
                + formatCoordinate(manager.getLobbyZ())
                + ChatColor.YELLOW + " with radius "
                + ChatColor.WHITE + manager.getLobbyRadius() + ChatColor.YELLOW + ".");
    }

    private double parseFiniteDouble(String rawValue) {
        double value = Double.parseDouble(rawValue);
        if (!Double.isFinite(value)) {
            throw new NumberFormatException("Value must be finite.");
        }
        return value;
    }

    private String formatCoordinate(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return Integer.toString((int) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!isAdmin(sender)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(Arrays.asList("32", "64", "0.5", "100.5", "-100.5"), args[0]);
        }
        if (args.length == 2) {
            return filter(Arrays.asList("0.5", "100.5", "-100.5"), args[1]);
        }
        if (args.length == 3) {
            return filter(Arrays.asList("32", "48", "64", "16"), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String token) {
        String lowerToken = token.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowerToken))
                .toList();
    }
}
