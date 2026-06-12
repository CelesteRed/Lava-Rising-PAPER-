package com.lavarising.plugin;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

public class RoundCombatListener implements Listener {
    private final LavaRisingPlugin plugin;

    public RoundCombatListener(LavaRisingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        LavaRisingManager manager = plugin.getLavaManager();
        Player attacker = getAttackingPlayer(event.getDamager());
        if (attacker == null || !manager.isActiveCombatPlayer(attacker)) {
            return;
        }

        Entity target = event.getEntity();
        if (target instanceof Player targetPlayer) {
            if (!attacker.getWorld().getPVP()) {
                event.setCancelled(true);
                return;
            }
            if (manager.isActiveCombatPlayer(targetPlayer)) {
                event.setCancelled(false);
            }
            return;
        }

        if (target instanceof LivingEntity) {
            event.setCancelled(false);
        }
    }

    private Player getAttackingPlayer(Entity damager) {
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
