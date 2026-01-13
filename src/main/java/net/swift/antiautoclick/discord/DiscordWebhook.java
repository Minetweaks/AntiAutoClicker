/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.swift.antiautoclick.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.swift.antiautoclick.AntiAutoClickerPlugin;
import net.swift.antiautoclick.detection.AttackPatternAnalyzer;
import org.bukkit.entity.Player;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Handles Discord webhook notifications for captcha failures
 */
public class DiscordWebhook {
    
    private final AntiAutoClickerPlugin plugin;
    private static final DateTimeFormatter TIME_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());
    
    public DiscordWebhook(AntiAutoClickerPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Send a failure report to Discord
     */
    public void sendFailureReport(Player player, AttackPatternAnalyzer.AttackSummary summary) {
        String webhookUrl = plugin.getConfig().getString("discord-webhook-url", "");
        
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            plugin.getLogger().info("Discord webhook URL not configured, skipping notification");
            return;
        }
        
        // Run async to not block main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sendWebhook(webhookUrl, buildPayload(player, summary));
                plugin.getLogger().info("Discord webhook sent for player: " + player.getName());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
            }
        });
    }
    
    private String buildPayload(Player player, AttackPatternAnalyzer.AttackSummary summary) {
        JsonObject payload = new JsonObject();
        
        // Username for the webhook
        payload.addProperty("username", "AntiAutoClicker");
        payload.addProperty("avatar_url", "https://mc-heads.net/avatar/MHF_Question/128");
        
        // Create embed
        JsonArray embeds = new JsonArray();
        JsonObject embed = new JsonObject();
        
        // Embed color (red for failed captcha)
        embed.addProperty("color", 0xFF0000);
        
        // Title
        embed.addProperty("title", "⚠️ Captcha Verification Failed");
        
        // Description
        embed.addProperty("description", 
            "A player has failed captcha verification and may be using automation tools.");
        
        // Fields
        JsonArray fields = new JsonArray();
        
        // Player info
        addField(fields, "👤 Player", player.getName(), true);
        addField(fields, "🔑 UUID", player.getUniqueId().toString(), true);
        addField(fields, "🌐 IP Address", getPlayerIP(player), true);
        
        // Location
        addField(fields, "📍 Location", String.format("%.1f, %.1f, %.1f (%s)",
            player.getLocation().getX(),
            player.getLocation().getY(),
            player.getLocation().getZ(),
            player.getWorld().getName()), false);
        
        // Attack statistics
        addField(fields, "⚔️ Total Attacks", String.valueOf(summary.totalAttacks()), true);
        addField(fields, "📊 Attacks/Second", String.format("%.2f", summary.attacksPerSecond()), true);
        addField(fields, "🎯 Timing Consistency", String.format("%.1f%%", summary.timingConsistency() * 100), true);
        
        // Suspicion score with emoji indicator
        String suspicionEmoji = getSuspicionEmoji(summary.suspicionScore());
        addField(fields, "🔍 Suspicion Score", 
            String.format("%s %.1f%%", suspicionEmoji, summary.suspicionScore() * 100), true);
        
        // Game info
        addField(fields, "❤️ Health", String.format("%.1f/%.1f", 
            player.getHealth(), player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()), true);
        addField(fields, "🎮 Game Mode", player.getGameMode().name(), true);
        
        embed.add("fields", fields);
        
        // Timestamp
        embed.addProperty("timestamp", Instant.now().toString());
        
        // Footer
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "AntiAutoClicker Plugin");
        embed.add("footer", footer);
        
        embeds.add(embed);
        payload.add("embeds", embeds);
        
        return payload.toString();
    }
    
    private void addField(JsonArray fields, String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", inline);
        fields.add(field);
    }
    
    private String getPlayerIP(Player player) {
        if (player.getAddress() != null) {
            String ip = player.getAddress().getAddress().getHostAddress();
            // Partially mask IP for privacy
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".***." + parts[3];
            }
            return ip;
        }
        return "Unknown";
    }
    
    private String getSuspicionEmoji(double score) {
        if (score >= 0.8) return "🔴";
        if (score >= 0.6) return "🟠";
        if (score >= 0.4) return "🟡";
        return "🟢";
    }
    
    private void sendWebhook(String webhookUrl, String payload) throws Exception {
        URI uri = new URI(webhookUrl);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", "AntiAutoClicker/1.0");
        connection.setDoOutput(true);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = payload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new RuntimeException("Discord webhook returned HTTP " + responseCode);
        }
        
        connection.disconnect();
    }
}
