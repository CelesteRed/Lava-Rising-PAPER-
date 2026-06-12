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
        Location deathLocation = player.getLocation();
        manager.logConsole("Player eliminated: " + player.getName()
                + ", op=" + player.isOp()
                + ", deathLocation=" + deathLocation.getWorld().getName()
                + " " + deathLocation.getBlockX()
                + "," + deathLocation.getBlockY()
                + "," + deathLocation.getBlockZ() + ".");

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

        if (manager.isCelebrating()) {
            plugin.getServer().getScheduler()
                    .runTaskLater(plugin, () -> manager.prepareRespawnedPlayerForCelebration(player), 2L);
            return;
        }

        List<Player> alive = manager.getAlivePlayers();
        World world = manager.getMainWorld();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!manager.isInGame()
                    || !manager.isActivePlayer(player.getUniqueId())
                    || !manager.getWaitingPlayers().contains(player.getUniqueId())) {
                return;
            }

            player.setGameMode(GameMode.SPECTATOR);
            manager.logConsole("Respawned eliminated player set to spectator: " + player.getName()
                    + ", op=" + player.isOp() + ".");
            if (!alive.isEmpty()) {
                Player target = alive.get(new Random().nextInt(alive.size()));
                player.teleport(target.getLocation());
                manager.logConsole("Respawned eliminated player spectating target: "
                        + player.getName() + " -> " + target.getName() + ".");
                player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "You are eliminated!");
                player.sendMessage(ChatColor.GRAY + "Now spectating " + ChatColor.WHITE
                        + target.getName() + ChatColor.GRAY + ".");
                return;
            }

            if (world != null) {
                Location spawnLocation = world.getSpawnLocation().clone();
                spawnLocation.setY(world.getMaxHeight() - 5);
                player.teleport(spawnLocation);
                manager.logConsole("Respawned eliminated player had no target: " + player.getName()
                        + ", sent to high spawn fallback.");
            }
            player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "You are eliminated!");
            player.sendMessage(ChatColor.GRAY + "No living players are available to spectate.");
        }, 2L);
    }
}
