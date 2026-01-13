/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.minetweak.antiautoclick.listeners;

import net.minetweak.antiautoclick.AntiAutoClickerPlugin;
import net.minetweak.antiautoclick.captcha.CaptchaGui;
import net.minetweak.antiautoclick.util.SchedulerUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;

/**
 * Handles captcha GUI interactions with strict item protection
 */
public class CaptchaGuiListener implements Listener {
    
    private final AntiAutoClickerPlugin plugin;
    
    public CaptchaGuiListener(AntiAutoClickerPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CaptchaGui captchaGui)) {
            return;
        }
        
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        // ALWAYS cancel the event first - we'll handle everything manually
        event.setCancelled(true);
        
        // Block all hotbar/number key swaps
        if (event.getClick() == ClickType.NUMBER_KEY) {
            return;
        }
        
        // Block shift clicks entirely
        if (event.isShiftClick()) {
            return;
        }
        
        // Block double clicks
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            return;
        }
        
        // Block middle click (creative pick)
        if (event.getClick() == ClickType.MIDDLE) {
            return;
        }
        
        // Block dropping
        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            return;
        }
        
        // Get clicked slot
        int clickedSlot = event.getRawSlot();
        
        // If clicking outside the captcha GUI (player inventory), block entirely
        if (clickedSlot >= 54 || clickedSlot < 0) {
            // Clear any cursor item to prevent exploits
            event.getWhoClicked().setItemOnCursor(null);
            return;
        }
        
        // Get cursor item
        ItemStack cursor = event.getCursor();
        boolean hasCursorItem = cursor != null && cursor.getType() != Material.AIR;
        
        // Check if player is holding redstone and clicking on chest
        if (hasCursorItem && cursor.getType() == Material.REDSTONE && captchaGui.isChestSlot(clickedSlot)) {
            // SUCCESS! Clear cursor and complete captcha
            event.getWhoClicked().setItemOnCursor(null);
            player.closeInventory();
            plugin.getCaptchaManager().handleGuiSuccess(player);
            return;
        }
        
        // Allow picking up ONLY the redstone (target item)
        if (!hasCursorItem && captchaGui.isTargetSlot(clickedSlot)) {
            // Give them a copy of redstone on cursor (don't actually take from inventory)
            ItemStack redstone = new ItemStack(Material.REDSTONE);
            event.getWhoClicked().setItemOnCursor(redstone);
            return;
        }
        
        // If they have redstone on cursor and click anywhere else, just keep it
        // (do nothing, let them try again)
        
        // Clear cursor if they somehow have something else
        if (hasCursorItem && cursor.getType() != Material.REDSTONE) {
            event.getWhoClicked().setItemOnCursor(null);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof CaptchaGui captchaGui)) {
            return;
        }
        
        // Cancel ALL drag events - no dragging allowed
        event.setCancelled(true);
        
        // Check if they were dragging redstone onto the chest slot
        if (event.getOldCursor().getType() == Material.REDSTONE) {
            for (int slot : event.getRawSlots()) {
                if (slot >= 0 && slot < 54 && captchaGui.isChestSlot(slot)) {
                    // Success!
                    if (event.getWhoClicked() instanceof Player player) {
                        event.getWhoClicked().setItemOnCursor(null);
                        player.closeInventory();
                        plugin.getCaptchaManager().handleGuiSuccess(player);
                    }
                    return;
                }
            }
        }
        
        // Clear cursor to prevent any items from being duplicated
        event.getWhoClicked().setItemOnCursor(null);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Block hoppers and other inventory movers
        if (event.getSource().getHolder() instanceof CaptchaGui || 
            event.getDestination().getHolder() instanceof CaptchaGui) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CaptchaGui captchaGui)) {
            return;
        }
        
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        
        // Clear any items on cursor when closing
        player.setItemOnCursor(null);
        
        // Check if they have an active captcha session (they closed without completing)
        if (plugin.getCaptchaManager().hasActiveCaptcha(player.getUniqueId())) {
            // Set grace period - player closed GUI, don't trigger cheating detection
            plugin.getCaptchaManager().onGuiClosed(player);
            
            // Reopen the GUI after a short delay (prevent escape)
            SchedulerUtil.runTaskLater(plugin, player, () -> {
                if (player.isOnline() && plugin.getCaptchaManager().hasActiveCaptcha(player.getUniqueId())) {
                    player.sendMessage(plugin.getMessages().get("captcha.must-complete"));
                    plugin.getCaptchaManager().reopenGui(player);
                }
            }, 5L); // 0.25 second delay
        }
    }
}
