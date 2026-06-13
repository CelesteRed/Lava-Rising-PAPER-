package com.lavarising.v2;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.event.player.PlayerJoinEvent;
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
        // Delay so the player is fully loaded in before we assign them (dead/spectator),
        // otherwise the gamemode set may not stick and /lava revive can't find them.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.game().handleJoin(player);
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.game().isActivePlayer(player)) {
            plugin.game().handleDeath(player);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && killer != event.getEntity()) {
            plugin.game().handleKill(killer);
        }
        plugin.game().handleDeath(event.getEntity());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GamemodeVote vote)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == top && event.getWhoClicked() instanceof Player player) {
            vote.handleClick(player, event.getSlot());
        }
    }

    @EventHandler
    public void onJuggernautHelmet(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !plugin.game().isJuggernaut(player)) {
            return;
        }
        // The Juggernaut's wither-skull helmet can't be taken off.
        if (event.getSlotType() == InventoryType.SlotType.ARMOR
                && event.getCurrentItem() != null
                && event.getCurrentItem().getType() == Material.WITHER_SKELETON_SKULL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GamemodeVote vote)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        // Keep players locked in the menu until they have actually voted for a gamemode.
        if (vote.isClosing() || vote != plugin.game().currentVote() || vote.hasVoted(player)) {
            return;
        }
        player.sendMessage(ChatColor.RED + "You must vote for a gamemode before closing!");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!vote.isClosing() && vote == plugin.game().currentVote() && !vote.hasVoted(player)
                    && player.isOnline() && player.getGameMode() != GameMode.SPECTATOR) {
                vote.open(player);
            }
        });
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

        if (target instanceof Player targetPlayer) {
            if (!game.isPvpEnabled() || !game.canFight(attacker) || !game.canFight(targetPlayer)) {
                event.setCancelled(true);
                return;
            }
            // Team modes: teammates (hunters, or red/blue) can't hurt each other.
            if (game.isFriendlyFire(attacker, targetPlayer)) {
                event.setCancelled(true);
                return;
            }

            game.ensureRoundPvpState();
            event.setCancelled(false);
            return;
        }

        // PvE: mobs/animals can be hit by anyone genuinely playing once the round is live.
        if (target instanceof LivingEntity && attacker.getGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(false);
        }
    }

    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        ItemStack bow = event.getBow();
        if (bow == null || !bow.hasItemMeta()) {
            return;
        }
        ItemMeta meta = bow.getItemMeta();
        if (meta != null && meta.hasDisplayName()
                && ChatColor.stripColor(meta.getDisplayName()).equalsIgnoreCase("TNT Bow")) {
            event.getProjectile().setMetadata("lr_tntbow", new FixedMetadataValue(plugin, true));
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!event.getEntity().hasMetadata("lr_tntbow")) {
            return;
        }
        Location location = event.getEntity().getLocation();
        World world = location.getWorld();
        event.getEntity().remove();
        if (world == null) {
            return;
        }
        // Wherever the TNT-bow arrow lands, summon 3 primed TNT for the chaos.
        for (int i = 0; i < 3; i++) {
            world.spawn(location.clone().add((i - 1) * 0.6D, 0.5D, 0.0D), TNTPrimed.class);
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

    @EventHandler(ignoreCancelled = true)
    public void onSilkTouchHands(BlockBreakEvent event) {
        GameManager game = plugin.game();
        // When a gamemode enables silkTouchHands, breaking a block by hand drops the block
        // itself (silk-touch style) so players always collect exactly what they mine.
        if (!game.isLavaRising() || !plugin.gamemodes().settings(game.activeMode()).silkTouchHands()) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!game.isActivePlayer(player) || game.isCoveredByActiveLava(block)) {
            return;
        }
        Material type = block.getType();
        if (!type.isBlock() || !type.isItem()) {
            return;
        }
        event.setDropItems(false);
        for (ItemStack extra : player.getInventory().addItem(new ItemStack(type)).values()) {
            player.getWorld().dropItemNaturally(block.getLocation(), extra);
        }
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
}
