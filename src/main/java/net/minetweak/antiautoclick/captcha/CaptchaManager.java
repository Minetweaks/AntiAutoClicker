/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.minetweak.antiautoclick.captcha;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.minetweak.antiautoclick.AntiAutoClickerPlugin;
import net.minetweak.antiautoclick.config.MessageManager;
import net.minetweak.antiautoclick.detection.AttackPatternAnalyzer;
import net.minetweak.antiautoclick.storage.StorageProvider;
import net.minetweak.antiautoclick.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages captcha challenges for players using GUI
 */
public class CaptchaManager {
    
    private final AntiAutoClickerPlugin plugin;
    private final Map<UUID, CaptchaSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> failureCounts = new ConcurrentHashMap<>();
    private final Map<UUID, CaptchaGui> activeGuis = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> captchaCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> guiGracePeriod = new ConcurrentHashMap<>(); // Grace period after GUI open/close
    
    // Grace period in milliseconds - don't trigger cheating detection during this time
    private static final long GRACE_PERIOD_MS = 1000; // 1 second grace period
    
    public CaptchaManager(AntiAutoClickerPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Issue captchas to players who meet the criteria
     */
    public void issueScheduledCaptchas() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("antiautoclick.bypass")) {
                continue;
            }
            
            if (activeSessions.containsKey(player.getUniqueId())) {
                continue; // Already has active captcha
            }
            
            if (plugin.getPatternAnalyzer().shouldReceiveCaptcha(player.getUniqueId())) {
                issueCaptcha(player);
            }
        }
    }
    
    /**
     * Issue a captcha challenge to a specific player
     */
    public void issueCaptcha(Player player) {
        if (activeSessions.containsKey(player.getUniqueId())) {
            return; // Already has active captcha
        }
        
        int timeoutSeconds = plugin.getConfig().getInt("captcha-timeout-seconds", 120);
        
        // Increment captcha count for this player
        int captchaCount = captchaCounts.merge(player.getUniqueId(), 1, Integer::sum);
        
        CaptchaSession session = new CaptchaSession(System.currentTimeMillis());
        activeSessions.put(player.getUniqueId(), session);
        
        // Create and open the GUI
        CaptchaGui gui = new CaptchaGui(plugin, player);
        activeGuis.put(player.getUniqueId(), gui);
        
        // Notify staff
        notifyStaffCaptchaIssued(player, captchaCount);
        
        // Save to storage
        savePlayerStats(player);
        
        // Open GUI on player's thread
        SchedulerUtil.runTask(plugin, player, () -> {
            gui.open();
            
            // Set grace period - don't detect cheating for a moment after opening
            guiGracePeriod.put(player.getUniqueId(), System.currentTimeMillis());
            
            MessageManager msg = plugin.getMessages();
            
            // Play alert sound
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
            
            // Show title
            player.showTitle(Title.title(
                msg.get("captcha.title"),
                msg.get("captcha.subtitle"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            ));
            
            // Send chat instructions
            player.sendMessage(Component.empty());
            player.sendMessage(msg.get("captcha.issued-header"));
            player.sendMessage(msg.get("captcha.issued-title"));
            player.sendMessage(msg.get("captcha.issued-header"));
            player.sendMessage(msg.get("captcha.issued-instructions"));
            player.sendMessage(msg.get("captcha.issued-time-warning", "time", String.valueOf(timeoutSeconds)));
            player.sendMessage(msg.get("captcha.issued-header"));
        });
        
        // Schedule timeout
        SchedulerUtil.ScheduledTask timeoutTask = SchedulerUtil.scheduleEntityTask(plugin, player, () -> {
            handleTimeout(player);
        }, timeoutSeconds * 20L);
        
        session.setTimeoutTask(timeoutTask);
        
        plugin.getLogger().info("Issued GUI captcha to " + player.getName());
    }
    
    /**
     * Handle cheating detected - player was attacking or moving with captcha GUI open
     * This is an immediate auto-fail
     */
    public void handleCheatingDetected(Player player, String action) {
        CaptchaSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return; // No active session
        }
        
        // Cancel timeout task
        if (session.getTimeoutTask() != null) {
            session.getTimeoutTask().cancel();
        }
        
        // Close any open GUI
        SchedulerUtil.runTask(plugin, player, () -> player.closeInventory());
        
        activeSessions.remove(player.getUniqueId());
        activeGuis.remove(player.getUniqueId());
        
        // Notify the player
        player.sendMessage(plugin.getMessages().get("captcha.cheating-detected"));
        
        // Log it
        plugin.getLogger().warning(plugin.getMessages().getRaw("log.captcha-cheating")
            .replace("%player%", player.getName()));
        
        // Handle as failure (but with cheating-specific messages)
        handleCheatingFailure(player, action);
    }
    
    /**
     * Handle successful GUI captcha completion
     */
    public void handleGuiSuccess(Player player) {
        CaptchaSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        
        // Cancel timeout task
        if (session.getTimeoutTask() != null) {
            session.getTimeoutTask().cancel();
        }
        
        activeSessions.remove(player.getUniqueId());
        activeGuis.remove(player.getUniqueId());
        failureCounts.remove(player.getUniqueId());
        
        // Reset attack data since they passed
        plugin.getPatternAnalyzer().resetPlayerData(player.getUniqueId());
        
        MessageManager msg = plugin.getMessages();
        
        // Show success
        player.sendMessage(Component.empty());
        player.sendMessage(msg.get("captcha.success-chat"));
        player.sendMessage(msg.get("captcha.success-subtitle"));
        
        player.showTitle(Title.title(
            msg.get("captcha.success-title"),
            msg.get("captcha.success-subtitle"),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
        ));
        
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        
        // Update storage with passed captcha
        incrementCaptchaPassed(player);
        
        plugin.getLogger().info(msg.getRaw("log.captcha-passed").replace("%player%", player.getName()));
    }
    
    /**
     * Reopen the captcha GUI for a player who closed it
     */
    public void reopenGui(Player player) {
        // Set grace period before reopening
        guiGracePeriod.put(player.getUniqueId(), System.currentTimeMillis());
        
        CaptchaGui gui = activeGuis.get(player.getUniqueId());
        if (gui != null) {
            gui.open();
        } else if (hasActiveCaptcha(player.getUniqueId())) {
            // Create new GUI if old one was lost
            CaptchaGui newGui = new CaptchaGui(plugin, player);
            activeGuis.put(player.getUniqueId(), newGui);
            newGui.open();
        }
    }
    
    /**
     * Called when a player closes the captcha GUI - sets grace period
     */
    public void onGuiClosed(Player player) {
        guiGracePeriod.put(player.getUniqueId(), System.currentTimeMillis());
    }
    
    /**
     * Check if a player is within the grace period (just opened/closed GUI)
     */
    public boolean isInGracePeriod(UUID playerId) {
        Long graceStart = guiGracePeriod.get(playerId);
        if (graceStart == null) {
            return false;
        }
        return (System.currentTimeMillis() - graceStart) < GRACE_PERIOD_MS;
    }
    
    /**
     * Handle a player's answer attempt (legacy - kept for compatibility)
     */
    public boolean handleAnswer(Player player, String answer) {
        // GUI-based captcha doesn't use chat answers
        return false;
    }
    
    /**
     * Check if a player has an active captcha session
     */
    public boolean hasActiveCaptcha(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }
    
    /**
     * Clear a player's session data
     */
    public void clearPlayerSession(UUID playerId) {
        CaptchaSession session = activeSessions.remove(playerId);
        if (session != null && session.getTimeoutTask() != null) {
            session.getTimeoutTask().cancel();
        }
        activeGuis.remove(playerId);
        failureCounts.remove(playerId);
        guiGracePeriod.remove(playerId);
        // Note: We don't clear captchaCounts here so staff can see total count
    }
    
    /**
     * Notify staff that a captcha was issued
     */
    private void notifyStaffCaptchaIssued(Player target, int captchaCount) {
        if (!plugin.getConfig().getBoolean("actions.notify-staff", true)) {
            return;
        }
        
        String permission = plugin.getConfig().getString("actions.staff-permission", "antiautoclick.notify");
        Component notification = plugin.getMessages().getWithPrefix("staff.captcha-issued",
            "player", target.getName(),
            "count", String.valueOf(captchaCount));
        
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(permission) && !staff.getUniqueId().equals(target.getUniqueId())) {
                staff.sendMessage(notification);
            }
        }
    }
    
    /**
     * Save player stats to persistent storage
     */
    private void savePlayerStats(Player player) {
        StorageProvider storage = plugin.getStorage();
        if (storage == null || !storage.isConnected()) return;
        
        int attacks = plugin.getPatternAnalyzer().getAttackCount(player.getUniqueId());
        int captchas = getCaptchaCount(player.getUniqueId());
        
        StorageProvider.PlayerData data = new StorageProvider.PlayerData(
            attacks, captchas, 0, 0, System.currentTimeMillis()
        );
        
        storage.savePlayerData(player.getUniqueId(), player.getName(), data);
    }
    
    /**
     * Increment passed captcha count in storage
     */
    private void incrementCaptchaPassed(Player player) {
        StorageProvider storage = plugin.getStorage();
        if (storage == null || !storage.isConnected()) return;
        
        storage.loadPlayerData(player.getUniqueId()).thenAccept(existing -> {
            StorageProvider.PlayerData updated = new StorageProvider.PlayerData(
                existing.totalAttacks(),
                existing.captchasReceived(),
                existing.captchasPassed() + 1,
                existing.captchasFailed(),
                System.currentTimeMillis()
            );
            storage.savePlayerData(player.getUniqueId(), player.getName(), updated);
        });
    }
    
    /**
     * Increment failed captcha count in storage
     */
    private void incrementCaptchaFailed(Player player) {
        StorageProvider storage = plugin.getStorage();
        if (storage == null || !storage.isConnected()) return;
        
        storage.loadPlayerData(player.getUniqueId()).thenAccept(existing -> {
            StorageProvider.PlayerData updated = new StorageProvider.PlayerData(
                existing.totalAttacks(),
                existing.captchasReceived(),
                existing.captchasPassed(),
                existing.captchasFailed() + 1,
                System.currentTimeMillis()
            );
            storage.savePlayerData(player.getUniqueId(), player.getName(), updated);
        });
    }
    
    /**
     * Load player data from storage on join
     */
    public void loadPlayerData(Player player) {
        StorageProvider storage = plugin.getStorage();
        if (storage == null || !storage.isConnected()) return;
        
        storage.loadPlayerData(player.getUniqueId()).thenAccept(data -> {
            if (data.captchasReceived() > 0) {
                captchaCounts.put(player.getUniqueId(), data.captchasReceived());
            }
        });
    }
    
    /**
     * Shutdown the manager
     */
    public void shutdown() {
        for (CaptchaSession session : activeSessions.values()) {
            if (session.getTimeoutTask() != null) {
                session.getTimeoutTask().cancel();
            }
        }
        activeSessions.clear();
        activeGuis.clear();
    }
    
    private void handleTimeout(Player player) {
        CaptchaSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return; // Already handled
        }
        
        // Close any open GUI
        SchedulerUtil.runTask(plugin, player, () -> player.closeInventory());
        
        activeSessions.remove(player.getUniqueId());
        activeGuis.remove(player.getUniqueId());
        
        player.sendMessage(plugin.getMessages().get("captcha.timeout"));
        
        handleMaxFailures(player);
    }
    
    private void handleMaxFailures(Player player) {
        // Get attack summary for Discord report
        AttackPatternAnalyzer.AttackSummary summary = 
            plugin.getPatternAnalyzer().getAttackSummary(player.getUniqueId());
        
        // Send Discord webhook
        plugin.getDiscordWebhook().sendFailureReport(player, summary);
        
        // Update storage with failed captcha and get new failure count
        int failureCount = incrementCaptchaFailedAndGet(player);
        
        // Notify staff
        if (plugin.getConfig().getBoolean("actions.notify-staff", true)) {
            String permission = plugin.getConfig().getString("actions.staff-permission", "antiautoclick.notify");
            Component notification = plugin.getMessages().getWithPrefix("staff.captcha-failed",
                "player", player.getName(),
                "failures", String.valueOf(failureCount));
            
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission(permission)) {
                    staff.sendMessage(notification);
                }
            }
        }
        
        // Execute tiered punishment
        executeTieredPunishment(player, summary, failureCount, false);
        
        // Clear data
        clearPlayerSession(player.getUniqueId());
        plugin.getPatternAnalyzer().resetPlayerData(player.getUniqueId());
        
        plugin.getLogger().warning(plugin.getMessages().getRaw("log.captcha-failed")
            .replace("%player%", player.getName())
            .replace("%failures%", String.valueOf(failureCount)));
    }
    
    /**
     * Execute console commands for a player who failed captcha
     */
    private void executeConsoleCommands(Player player, AttackPatternAnalyzer.AttackSummary summary, int failureCount, List<String> commands) {
        if (commands.isEmpty()) return;
        
        for (String command : commands) {
            // Replace placeholders
            String parsed = command
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%attacks%", String.valueOf(summary.totalAttacks()))
                .replace("%suspicion%", String.format("%.0f", summary.suspicionScore() * 100))
                .replace("%failures%", String.valueOf(failureCount));
            
            // Execute on global region thread
            SchedulerUtil.runGlobalTask(plugin, () -> {
                plugin.getServer().dispatchCommand(
                    plugin.getServer().getConsoleSender(),
                    parsed
                );
                plugin.getLogger().info("Executed command: " + parsed);
            });
        }
    }
    
    /**
     * Execute tiered punishment based on failure count
     */
    private void executeTieredPunishment(Player player, AttackPatternAnalyzer.AttackSummary summary, int failureCount, boolean isCheating) {
        String tierPath;
        
        if (isCheating) {
            // Use cheating tier
            tierPath = "actions.cheating";
        } else {
            // Find the appropriate tier based on failure count
            // Use the highest tier that's <= failure count
            int selectedTier = 1;
            for (int tier = 1; tier <= 10; tier++) {
                if (plugin.getConfig().contains("actions.tiers." + tier) && tier <= failureCount) {
                    selectedTier = tier;
                }
            }
            tierPath = "actions.tiers." + selectedTier;
        }
        
        // Get commands for this tier
        List<String> commands = plugin.getConfig().getStringList(tierPath + ".commands");
        if (!commands.isEmpty()) {
            executeConsoleCommands(player, summary, failureCount, commands);
        }
        
        // Kick if configured for this tier
        boolean shouldKick = plugin.getConfig().getBoolean(tierPath + ".kick", false);
        if (shouldKick) {
            Component kickMessage = isCheating 
                ? plugin.getMessages().get("captcha.kick-message-cheating")
                : plugin.getMessages().get("captcha.kick-message");
            player.kick(kickMessage);
        }
    }
    
    /**
     * Handle cheating failure - player was attacking/moving with captcha GUI open
     * This is more severe than a normal failure
     */
    private void handleCheatingFailure(Player player, String action) {
        // Get attack summary for Discord report
        AttackPatternAnalyzer.AttackSummary summary = 
            plugin.getPatternAnalyzer().getAttackSummary(player.getUniqueId());
        
        // Send Discord webhook
        plugin.getDiscordWebhook().sendFailureReport(player, summary);
        
        // Update storage with failed captcha
        int failureCount = incrementCaptchaFailedAndGet(player);
        
        // Notify staff with cheating-specific message
        if (plugin.getConfig().getBoolean("actions.notify-staff", true)) {
            String permission = plugin.getConfig().getString("actions.staff-permission", "antiautoclick.notify");
            Component notification = plugin.getMessages().getWithPrefix("staff.captcha-cheating",
                "player", player.getName());
            
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission(permission)) {
                    staff.sendMessage(notification);
                }
            }
        }
        
        // Execute cheating tier punishment (always most severe)
        executeTieredPunishment(player, summary, failureCount, true);
        
        // Clear data
        clearPlayerSession(player.getUniqueId());
        plugin.getPatternAnalyzer().resetPlayerData(player.getUniqueId());
    }
    
    /**
     * Increment failed captcha count and return the new count
     */
    private int incrementCaptchaFailedAndGet(Player player) {
        // Track locally in memory as well
        int localCount = failureCounts.merge(player.getUniqueId(), 1, Integer::sum);
        
        StorageProvider storage = plugin.getStorage();
        if (storage == null || !storage.isConnected()) {
            return localCount;
        }
        
        try {
            StorageProvider.PlayerData existing = storage.loadPlayerData(player.getUniqueId()).join();
            int newFailureCount = existing.captchasFailed() + 1;
            
            StorageProvider.PlayerData updated = new StorageProvider.PlayerData(
                existing.totalAttacks(),
                existing.captchasReceived(),
                existing.captchasPassed(),
                newFailureCount,
                System.currentTimeMillis()
            );
            storage.savePlayerData(player.getUniqueId(), player.getName(), updated);
            
            return newFailureCount;
        } catch (Exception e) {
            return localCount;
        }
    }
    
    /**
     * Get the number of captchas a player has received
     */
    public int getCaptchaCount(java.util.UUID playerId) {
        if (plugin.getStorage() == null) {
            return captchaCounts.getOrDefault(playerId, 0);
        }
        try {
            var data = plugin.getStorage().loadPlayerData(playerId).join();
            return data.captchasReceived();
        } catch (Exception e) {
            return captchaCounts.getOrDefault(playerId, 0);
        }
    }
    
    /**
     * Active captcha session
     */
    private static class CaptchaSession {
        private final long startTime;
        private SchedulerUtil.ScheduledTask timeoutTask;
        
        CaptchaSession(long startTime) {
            this.startTime = startTime;
        }
        
        long getStartTime() {
            return startTime;
        }
        
        SchedulerUtil.ScheduledTask getTimeoutTask() {
            return timeoutTask;
        }
        
        void setTimeoutTask(SchedulerUtil.ScheduledTask task) {
            this.timeoutTask = task;
        }
    }
}
