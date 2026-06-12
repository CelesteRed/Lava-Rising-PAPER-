package com.lavarising.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class BuildHeightListener implements Listener {
    private static final int TOO_EARLY_BUILD_Y = 200;
    private static final int NAUSEA_TICKS = 10 * 20;
    private static final int BLINDNESS_TICKS = 5 * 20;

    private final LavaRisingManager manager;
    private final Map<UUID, Integer> highBuildAttempts = new HashMap<>();

    public BuildHeightListener(LavaRisingManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!manager.isEarlyBuildHeightLocked()) {
            highBuildAttempts.clear();
            return;
        }

        Player player = event.getPlayer();
        punishTooEarlyHighBuild(player, event.getBlockPlaced().getY());

        if (event.getBlockPlaced().getY() <= manager.getEarlyBuildLockY()) {
            return;
        }

        event.setCancelled(true);
        player.sendActionBar(ChatColor.RED + "Build limit is Y=" + manager.getEarlyBuildLockY()
                + " until lava reaches Y=" + LavaRisingManager.SURFACE_Y + ".");
    }

    private void punishTooEarlyHighBuild(Player player, int attemptedY) {
        if (attemptedY <= TOO_EARLY_BUILD_Y || !isPhaseOneOrTwo()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        int attempts = highBuildAttempts.getOrDefault(uuid, 0) + 1;
        highBuildAttempts.put(uuid, attempts);

        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, NAUSEA_TICKS, 0, false, true, true), true);
        if (attempts >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, BLINDNESS_TICKS, 0, false, true, true),
                    true);
        }

        player.sendMessage(ChatColor.RED + "You're building too early. Stay below Y="
                + TOO_EARLY_BUILD_Y + " until phase 3.");
    }

    private boolean isPhaseOneOrTwo() {
        LavaRisingManager.LavaPhase phase = manager.getCurrentLavaPhase();
        return phase == LavaRisingManager.LavaPhase.START || phase == LavaRisingManager.LavaPhase.REACH_0;
    }
}
