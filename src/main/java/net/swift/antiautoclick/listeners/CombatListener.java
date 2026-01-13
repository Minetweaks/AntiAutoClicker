/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.swift.antiautoclick.listeners;

import net.swift.antiautoclick.AntiAutoClickerPlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens for combat events to track attack patterns
 */
public class CombatListener implements Listener {
    
    private final AntiAutoClickerPlugin plugin;
    
    public CombatListener(AntiAutoClickerPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Only track player attacks on living entities
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        
        // Skip if player has bypass permission
        if (player.hasPermission("antiautoclick.bypass")) {
            return;
        }
        
        // Check if player has an active captcha - this is cheating!
        // But allow grace period after GUI open/close
        if (plugin.getCaptchaManager().hasActiveCaptcha(player.getUniqueId())) {
            if (!plugin.getCaptchaManager().isInGracePeriod(player.getUniqueId())) {
                plugin.getCaptchaManager().handleCheatingDetected(player, "attacking");
            }
            return;
        }
        
        // Record the attack
        plugin.getPatternAnalyzer().recordAttack(player);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // Only check if they actually moved position (not just head rotation)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        // Skip if player has bypass permission
        if (player.hasPermission("antiautoclick.bypass")) {
            return;
        }
        
        // Check if player has an active captcha - moving while GUI open is cheating!
        // But allow grace period after GUI open/close (player might move when pressing ESC)
        if (plugin.getCaptchaManager().hasActiveCaptcha(player.getUniqueId())) {
            if (!plugin.getCaptchaManager().isInGracePeriod(player.getUniqueId())) {
                plugin.getCaptchaManager().handleCheatingDetected(player, "moving");
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up player data when they leave
        // Keep data for a while in case they rejoin quickly
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) {
                plugin.getPatternAnalyzer().resetPlayerData(event.getPlayer().getUniqueId());
                plugin.getCaptchaManager().clearPlayerSession(event.getPlayer().getUniqueId());
            }
        }, 20 * 60 * 5); // 5 minutes
    }
}
