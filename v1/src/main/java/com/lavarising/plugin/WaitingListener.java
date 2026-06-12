package com.lavarising.plugin;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class WaitingListener implements Listener {
    private final LavaRisingPlugin plugin;

    public WaitingListener(LavaRisingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            LavaRisingManager manager = plugin.getLavaManager();
            if (manager.isInGame()) {
                if (manager.shouldManageGameMode(player)) {
                    player.setGameMode(GameMode.SPECTATOR);
                    manager.logConsole("Join during active game: " + player.getName()
                            + " set to spectator.");
                } else {
                    manager.logConsole("Join during active game: OP " + player.getName()
                            + " kept gamemode=" + player.getGameMode() + ".");
                }
                return;
            }

            manager.logConsole("Join while waiting: sending " + player.getName() + " to lobby.");
            if (manager.shouldEnforceLobbyBoundary(player)) {
                manager.sendPlayerToLobby(player);
            } else {
                manager.logConsole("Join while waiting: admin lobby teleport bypass for "
                        + player.getName() + ".");
            }
        }, 2L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        LavaRisingManager manager = plugin.getLavaManager();
        if (manager.isInGame()) {
            return;
        }
        if (!manager.shouldEnforceLobbyBoundary(event.getPlayer())) {
            return;
        }

        Location to = event.getTo();
        if (to == null || !hasHorizontalMove(event.getFrom(), to)) {
            return;
        }
        if (manager.isLocationInLobby(to)) {
            return;
        }

        Player player = event.getPlayer();
        event.setCancelled(true);
        manager.sendPlayerToLobby(player);
        player.sendActionBar(ChatColor.RED + "Stay inside the lobby radius: "
                + ChatColor.WHITE + manager.getLobbyRadius() + ChatColor.RED + " blocks.");
        manager.logConsole("Lobby boundary returned player: " + player.getName()
                + ", radius=" + manager.getLobbyRadius() + ".");
    }

    private boolean hasHorizontalMove(Location from, Location to) {
        return from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ();
    }
}
