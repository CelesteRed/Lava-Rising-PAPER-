package com.lavarising.plugin;

import java.util.List;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class RespawnListener implements Listener {
    private final LavaRisingPlugin plugin;

    public RespawnListener(LavaRisingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        LavaRisingManager manager = plugin.getLavaManager();
        if (!manager.isInGame()) {
            return;
        }

        Player player = event.getEntity();
        if (!manager.isActivePlayer(player.getUniqueId())) {
            return;
        }

        manager.addWaitingPlayer(player.getUniqueId());

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                manager.checkWinCondition();
            }
        }, 1L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        LavaRisingManager manager = plugin.getLavaManager();
        if (!manager.isInGame()) {
            return;
        }

        Player player = event.getPlayer();
        if (!manager.isActivePlayer(player.getUniqueId())
                || !manager.getWaitingPlayers().contains(player.getUniqueId())) {
            return;
        }

        List<Player> alive = manager.getAlivePlayers();
        World world = manager.getMainWorld();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            player.setGameMode(GameMode.SPECTATOR);
            if (!alive.isEmpty()) {
                Player target = alive.get(new Random().nextInt(alive.size()));
                player.teleport(target.getLocation());
                player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "You are eliminated!");
                player.sendMessage(ChatColor.GRAY + "Now spectating " + ChatColor.WHITE
                        + target.getName() + ChatColor.GRAY + ".");
                return;
            }

            if (world != null) {
                Location spawnLocation = world.getSpawnLocation().clone();
                spawnLocation.setY(world.getMaxHeight() - 5);
                player.teleport(spawnLocation);
            }
            player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "You are eliminated!");
            player.sendMessage(ChatColor.GRAY + "No living players are available to spectate.");
        }, 2L);
    }
}
