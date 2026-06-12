package com.lavarising.plugin;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class ReviveCommand implements CommandExecutor, TabCompleter {
    private final LavaRisingPlugin plugin;
    private final LavaRisingManager manager;

    public ReviveCommand(LavaRisingPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getLavaManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!canUse(sender)) {
            sender.sendMessage(ChatColor.RED + "Only OPs can use /revive.");
            return true;
        }

        if (args.length < 1 || args.length > 2) {
            sendUsage(sender);
            return true;
        }

        Player revived = findPlayer(args[0]);
        if (revived == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        Player anchor = null;
        if (args.length == 2) {
            anchor = findPlayer(args[1]);
            if (anchor == null) {
                sender.sendMessage(ChatColor.RED + "Revive target not found: " + args[1]);
                return true;
            }
        }

        LavaRisingManager.ReviveResult result = manager.revivePlayer(revived, anchor);
        switch (result.status()) {
            case REVIVED -> {
                Player finalAnchor = result.anchor();
                sender.sendMessage(ChatColor.YELLOW + "Revived " + ChatColor.WHITE + revived.getName()
                        + ChatColor.YELLOW + " on " + ChatColor.WHITE + finalAnchor.getName()
                        + ChatColor.YELLOW + ".");
                plugin.getServer().broadcastMessage(ChatColor.YELLOW + revived.getName()
                        + " has been revived on " + finalAnchor.getName() + "!");
            }
            case NOT_RUNNING -> sender.sendMessage(ChatColor.RED
                    + "A lava round must be running before you can revive someone.");
            case PLAYER_NOT_ONLINE -> sender.sendMessage(ChatColor.RED
                    + revived.getName() + " is not online.");
            case PLAYER_NOT_RESPAWNED -> sender.sendMessage(ChatColor.RED
                    + revived.getName() + " is still on the death screen. Have them respawn, then run /revive again.");
            case ALREADY_ALIVE -> sender.sendMessage(ChatColor.RED
                    + revived.getName() + " is already alive in this round.");
            case ANCHOR_NOT_ALIVE -> sender.sendMessage(ChatColor.RED
                    + anchor.getName() + " is not an alive round player.");
            case NO_ALIVE_ANCHOR -> sender.sendMessage(ChatColor.RED
                    + "No alive players are available to revive onto.");
        }

        return true;
    }

    private boolean canUse(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.isOp();
        }
        return sender.hasPermission("lavarising.use");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage: /revive <player> [targetPlayer]");
        sender.sendMessage(ChatColor.GRAY + "Leave targetPlayer blank to revive onto a random alive player.");
    }

    private Player findPlayer(String name) {
        Player exact = plugin.getServer().getPlayerExact(name);
        if (exact != null) {
            return exact;
        }
        return plugin.getServer().getPlayer(name);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!canUse(sender)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList(), args[0]);
        }
        if (args.length == 2) {
            return filter(manager.getAlivePlayers().stream()
                    .map(Player::getName)
                    .toList(), args[1]);
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
