package com.lavarising.plugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;

public class CraftListener implements Listener {
    private final LavaRisingManager manager;

    public CraftListener(LavaRisingManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!manager.isInGame()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!manager.isInGame()) {
            event.getInventory().setResult(null);
        }
    }
}
