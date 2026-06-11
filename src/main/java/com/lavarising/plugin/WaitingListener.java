package com.lavarising.plugin;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class WaitingListener implements Listener {
    private final LavaRisingManager manager;

    public WaitingListener(LavaRisingManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (manager.isInGame()) {
            player.setGameMode(GameMode.SPECTATOR);
        } else {
            manager.sendPlayerToLobby(player);
        }
    }
}
