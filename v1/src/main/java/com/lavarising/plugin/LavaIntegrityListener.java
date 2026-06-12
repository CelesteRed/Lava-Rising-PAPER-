package com.lavarising.plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;

public class LavaIntegrityListener implements Listener {
    private final LavaRisingPlugin plugin;

    public LavaIntegrityListener(LavaRisingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        repairAfterEvent(event.getBlock(), "block-break by " + event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        repairAfterEvent(event.getBlockPlaced(), "block-place by " + event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        repairAfterExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        repairAfterExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        LavaRisingManager manager = plugin.getLavaManager();
        Chunk chunk = event.getChunk();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int repaired = manager.repairCoveredLavaInChunk(chunk);
            if (repaired > 0) {
                manager.logConsole("Lava integrity repaired loaded chunk: chunk="
                        + chunk.getX() + "," + chunk.getZ()
                        + ", repairedBlocks=" + repaired + ".");
            }
        });
    }

    private void repairAfterEvent(Block block, String reason) {
        LavaRisingManager manager = plugin.getLavaManager();
        if (!manager.isCoveredByActiveLava(block)) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (manager.forceLavaAtCoveredBlock(block)) {
                manager.logConsole("Lava integrity repaired block after " + reason
                        + ": world=" + block.getWorld().getName()
                        + ", x=" + block.getX()
                        + ", y=" + block.getY()
                        + ", z=" + block.getZ() + ".");
            }
        });
    }

    private void repairAfterExplosion(List<Block> blocks) {
        LavaRisingManager manager = plugin.getLavaManager();
        Set<Chunk> coveredChunks = new HashSet<>();
        for (Block block : blocks) {
            if (manager.isCoveredByActiveLava(block)) {
                coveredChunks.add(block.getChunk());
            }
        }
        if (coveredChunks.isEmpty()) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int repaired = 0;
            for (Chunk chunk : coveredChunks) {
                repaired += manager.repairCoveredLavaInChunk(chunk);
            }
            if (repaired > 0) {
                manager.logConsole("Lava integrity repaired explosion damage: chunks="
                        + coveredChunks.size() + ", repairedBlocks=" + repaired + ".");
            }
        });
    }
}
