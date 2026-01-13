/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.minetweak.antiautoclick.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minetweak.antiautoclick.AntiAutoClickerPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Checks Modrinth for plugin updates
 */
public class UpdateChecker implements Listener {
    
    private static final String MODRINTH_API = "https://api.modrinth.com/v2/project/anti-auto-clicker/version";
    private static final String MODRINTH_PAGE = "https://modrinth.com/plugin/anti-auto-clicker";
    
    private final AntiAutoClickerPlugin plugin;
    private String latestVersion = null;
    private boolean updateAvailable = false;
    
    public UpdateChecker(AntiAutoClickerPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Check for updates asynchronously
     */
    public void checkForUpdates() {
        if (!plugin.getConfig().getBoolean("update-checker", true)) {
            return;
        }
        
        SchedulerUtil.runAsync(plugin, () -> {
            try {
                String currentVersion = plugin.getPluginMeta().getVersion();
                String latest = fetchLatestVersion();
                
                if (latest != null && !latest.equals(currentVersion)) {
                    // Compare versions (simple string comparison, works for semver)
                    if (isNewerVersion(latest, currentVersion)) {
                        latestVersion = latest;
                        updateAvailable = true;
                        
                        plugin.getLogger().warning("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        plugin.getLogger().warning("A new version of AntiAutoClicker is available!");
                        plugin.getLogger().warning("Current: " + currentVersion + " → Latest: " + latest);
                        plugin.getLogger().warning("Download: " + MODRINTH_PAGE);
                        plugin.getLogger().warning("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().fine("Could not check for updates: " + e.getMessage());
            }
        });
    }
    
    /**
     * Fetch the latest version from Modrinth API
     */
    private String fetchLatestVersion() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(MODRINTH_API).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "AntiAutoClicker/" + plugin.getPluginMeta().getVersion());
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        if (connection.getResponseCode() != 200) {
            return null;
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String response = reader.lines().collect(Collectors.joining());
            JsonArray versions = JsonParser.parseString(response).getAsJsonArray();
            
            if (versions.isEmpty()) {
                return null;
            }
            
            // First version in array is the latest
            JsonObject latest = versions.get(0).getAsJsonObject();
            return latest.get("version_number").getAsString();
        }
    }
    
    /**
     * Simple version comparison (assumes semver format like 1.0.0)
     */
    private boolean isNewerVersion(String latest, String current) {
        try {
            String[] latestParts = latest.replaceAll("[^0-9.]", "").split("\\.");
            String[] currentParts = current.replaceAll("[^0-9.]", "").split("\\.");
            
            int maxLength = Math.max(latestParts.length, currentParts.length);
            for (int i = 0; i < maxLength; i++) {
                int latestNum = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
                int currentNum = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                
                if (latestNum > currentNum) return true;
                if (latestNum < currentNum) return false;
            }
            return false;
        } catch (Exception e) {
            // Fall back to string comparison
            return !latest.equals(current);
        }
    }
    
    /**
     * Notify admins on join if an update is available
     */
    @EventHandler
    public void onAdminJoin(PlayerJoinEvent event) {
        if (!updateAvailable || latestVersion == null) {
            return;
        }
        
        Player player = event.getPlayer();
        if (!player.hasPermission("antiautoclick.admin")) {
            return;
        }
        
        // Delay message slightly so it appears after join messages
        SchedulerUtil.runTaskLater(plugin, player, () -> {
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
            player.sendMessage(Component.text(" AntiAutoClicker Update Available!", NamedTextColor.YELLOW, TextDecoration.BOLD));
            player.sendMessage(Component.text(" Current: ", NamedTextColor.GRAY)
                    .append(Component.text(plugin.getPluginMeta().getVersion(), NamedTextColor.RED))
                    .append(Component.text(" → Latest: ", NamedTextColor.GRAY))
                    .append(Component.text(latestVersion, NamedTextColor.GREEN)));
            player.sendMessage(Component.text(" [Click to download]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(MODRINTH_PAGE)));
            player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
            player.sendMessage(Component.empty());
        }, 40L); // 2 second delay
    }
    
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }
    
    public String getLatestVersion() {
        return latestVersion;
    }
}
