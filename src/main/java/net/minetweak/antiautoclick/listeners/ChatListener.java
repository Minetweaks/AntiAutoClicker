/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.minetweak.antiautoclick.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minetweak.antiautoclick.AntiAutoClickerPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listens for chat messages to capture captcha answers
 */
public class ChatListener implements Listener {
    
    private final AntiAutoClickerPlugin plugin;
    
    public ChatListener(AntiAutoClickerPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        
        // Check if player has an active captcha
        if (!plugin.getCaptchaManager().hasActiveCaptcha(player.getUniqueId())) {
            return;
        }
        
        // Get the message content
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        
        // Cancel the event so the answer isn't broadcast
        event.setCancelled(true);
        
        // Handle the answer on the main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getCaptchaManager().handleAnswer(player, message);
        });
    }
}
