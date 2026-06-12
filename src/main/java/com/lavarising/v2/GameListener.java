package com.lavarising.v2;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

public final class GameListener implements Listener {
    private static final int NAUSEA_TICKS = 10 * 20;
    private static final int BLINDNESS_TICKS = 5 * 20;

    private final LavaRisingPlugin plugin;
    private final java.util.Map<UUID, Integer> highBuildAttempts = new java.util.HashMap<>();

    public GameListener(LavaRisingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.game().handleJoin(player);
            }
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.game().isActivePlayer(player)) {
            plugin.game().handleDeath(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        GameManager game = plugin.game();
        if (!game.isWaiting() || !game.shouldEnforceLobbyBoundary(event.getPlayer())) {
            return;
        }

        Location to = event.getTo();
        if (to == null || !hasHorizontalMove(event.getFrom(), to) || plugin.arena().isInLobby(to)) {
            return;
        }

        event.setCancelled(true);
        game.sendPlayerToLobby(event.getPlayer());
        event.getPlayer().sendActionBar(ChatColor.RED + "Stay inside the lobby radius: "
                + ChatColor.WHITE + plugin.settings().lobby().radius() + ChatColor.RED + " blocks.");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.game().handleDeath(event.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location spectator = plugin.game().spectatorRespawnLocation();
        if (spectator != null && plugin.game().isEliminated(event.getPlayer())) {
            event.setRespawnLocation(spectator);
        }
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.game().handleRespawn(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null) {
            return;
        }

        GameManager game = plugin.game();
        Entity target = event.getEntity();
        if (!game.isCombatLive()) {
            if (target instanceof Player || attacker.getGameMode() != GameMode.CREATIVE) {
                event.setCancelled(true);
            }
            return;
        }

        if (!game.isActivePlayer(attacker)) {
            event.setCancelled(true);
            return;
        }

        if (target instanceof Player targetPlayer) {
            event.setCancelled(!game.isActivePlayer(targetPlayer) || !attacker.getWorld().getPVP());
            return;
        }

        if (target instanceof LivingEntity) {
            event.setCancelled(false);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (plugin.game().isWaiting() || plugin.game().state() == GameState.CELEBRATION) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (plugin.game().isWaiting() || plugin.game().state() == GameState.CELEBRATION) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        enforceBuildLimit(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        repairAfterBlockEvent(event.getBlock(), "block break by " + event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlaceRepair(BlockPlaceEvent event) {
        repairAfterBlockEvent(event.getBlockPlaced(), "block place by " + event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        repairExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        repairExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int repaired = plugin.game().repairCoveredLavaInChunk(chunk);
            if (repaired > 0) {
                plugin.logGame("Lava integrity repaired loaded chunk: "
                        + chunk.getX() + "," + chunk.getZ()
                        + ", blocks=" + repaired + ".");
            }
        });
    }

    private void enforceBuildLimit(BlockPlaceEvent event) {
        LavaConfig.BuildLimits limits = plugin.settings().buildLimits();
        GameManager game = plugin.game();
        if (!limits.enabled() || !game.isLavaRising() || game.currentY() >= limits.unlockWhenLavaY()) {
            highBuildAttempts.clear();
            return;
        }

        Player player = event.getPlayer();
        int blockY = event.getBlockPlaced().getY();
        if (blockY > limits.warningY()) {
            UUID uuid = player.getUniqueId();
            int attempts = highBuildAttempts.getOrDefault(uuid, 0) + 1;
            highBuildAttempts.put(uuid, attempts);
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, NAUSEA_TICKS, 0, false, true, true), true);
            if (attempts >= 2) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, BLINDNESS_TICKS, 0, false, true,
                        true), true);
            }
        }

        if (blockY > limits.lockY()) {
            event.setCancelled(true);
            player.sendActionBar(ChatColor.RED + "Build limit is Y=" + limits.lockY()
                    + " until lava reaches Y=" + limits.unlockWhenLavaY() + ".");
        }
    }

    private void repairAfterBlockEvent(Block block, String reason) {
        if (!plugin.game().isCoveredByActiveLava(block)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.game().forceLavaAtCoveredBlock(block)) {
                plugin.logGame("Lava integrity repaired after " + reason + ": "
                        + block.getWorld().getName() + " "
                        + block.getX() + "," + block.getY() + "," + block.getZ() + ".");
            }
        });
    }

    private void repairExplosion(List<Block> blocks) {
        Set<Chunk> chunks = new HashSet<>();
        for (Block block : blocks) {
            if (plugin.game().isCoveredByActiveLava(block)) {
                chunks.add(block.getChunk());
            }
        }
        if (chunks.isEmpty()) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int repaired = 0;
            for (Chunk chunk : chunks) {
                repaired += plugin.game().repairCoveredLavaInChunk(chunk);
            }
            if (repaired > 0) {
                plugin.logGame("Lava integrity repaired explosion: chunks=" + chunks.size()
                        + ", blocks=" + repaired + ".");
            }
        });
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private boolean hasHorizontalMove(Location from, Location to) {
        return from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ();
    }
}
