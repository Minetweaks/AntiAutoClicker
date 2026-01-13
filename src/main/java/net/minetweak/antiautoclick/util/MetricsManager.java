/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.minetweak.antiautoclick.util;

import net.minetweak.antiautoclick.AntiAutoClickerPlugin;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

/**
 * bStats metrics integration for anonymous usage statistics
 * View stats at: https://bstats.org/plugin/bukkit/AntiAutoClicker/28828
 */
public class MetricsManager {
    
    // bStats plugin ID - https://bstats.org/plugin/bukkit/AntiAutoClicker/28828
    private static final int BSTATS_PLUGIN_ID = 28828;
    
    private final AntiAutoClickerPlugin plugin;
    private Metrics metrics;
    
    public MetricsManager(AntiAutoClickerPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialize bStats metrics
     */
    public void initialize() {
        if (BSTATS_PLUGIN_ID == 00000) {
            plugin.getLogger().info("bStats not configured - skipping metrics");
            return;
        }
        
        metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
        
        // Custom chart: Storage type being used
        metrics.addCustomChart(new SimplePie("storage_type", () -> 
            plugin.getConfig().getString("storage.type", "sqlite")
        ));
        
        // Custom chart: Discord webhook enabled
        metrics.addCustomChart(new SimplePie("discord_enabled", () -> {
            String webhook = plugin.getConfig().getString("discord-webhook-url", "");
            return (webhook != null && !webhook.isEmpty()) ? "Enabled" : "Disabled";
        }));
        
        // Custom chart: Dev mode
        metrics.addCustomChart(new SimplePie("dev_mode", () -> 
            plugin.getConfig().getBoolean("dev-mode", false) ? "Enabled" : "Disabled"
        ));
        
        // Custom chart: Folia vs Paper
        metrics.addCustomChart(new SimplePie("server_type", () -> 
            SchedulerUtil.isFolia() ? "Folia" : "Paper"
        ));
        
        // Custom chart: Captcha interval
        metrics.addCustomChart(new SimplePie("captcha_interval", () -> {
            int interval = plugin.getConfig().getInt("captcha-interval-minutes", 30);
            if (interval <= 10) return "≤10 minutes";
            if (interval <= 30) return "11-30 minutes";
            if (interval <= 60) return "31-60 minutes";
            return ">60 minutes";
        }));
        
        // Custom chart: Total captchas issued (if tracked)
        metrics.addCustomChart(new SingleLineChart("captchas_issued", () -> {
            // This would need a counter in CaptchaManager - return 0 for now
            return 0;
        }));
        
        plugin.getLogger().info("bStats metrics enabled");
    }
}
