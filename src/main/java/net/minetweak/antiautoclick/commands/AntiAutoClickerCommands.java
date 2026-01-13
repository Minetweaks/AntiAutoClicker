/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.minetweak.antiautoclick.commands;

import net.kyori.adventure.text.format.NamedTextColor;
import net.minetweak.antiautoclick.AntiAutoClickerPlugin;
import net.minetweak.antiautoclick.config.MessageManager;
import net.minetweak.antiautoclick.detection.AttackPatternAnalyzer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * Admin commands for the AntiAutoClicker plugin
 */
public class AntiAutoClickerCommands {
    
    private final AntiAutoClickerPlugin plugin;
    private final MessageManager msg;
    
    public AntiAutoClickerCommands(AntiAutoClickerPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessages();
    }
    
    @Command("aac reload")
    @CommandDescription("Reload the AntiAutoClicker configuration")
    @Permission("antiautoclick.admin")
    public void reloadConfig(CommandSender sender) {
        plugin.reloadPlugin();
        sender.sendMessage(msg.getWithPrefix("commands.reload-success"));
    }
    
    @Command("aac captcha <player>")
    @CommandDescription("Manually issue a captcha to a player")
    @Permission("antiautoclick.admin")
    public void issueCaptcha(CommandSender sender, @Argument("player") Player target) {
        if (target == null) {
            sender.sendMessage(msg.getWithPrefix("commands.player-not-found"));
            return;
        }
        
        if (plugin.getCaptchaManager().hasActiveCaptcha(target.getUniqueId())) {
            sender.sendMessage(msg.getWithPrefix("commands.already-has-captcha", 
                "player", target.getName()));
            return;
        }
        
        plugin.getCaptchaManager().issueCaptcha(target);
        sender.sendMessage(msg.getWithPrefix("commands.captcha-issued-to", 
            "player", target.getName()));
    }
    
    @Command("aac stats <player>")
    @CommandDescription("View attack statistics for a player")
    @Permission("antiautoclick.admin")
    public void viewStats(CommandSender sender, @Argument("player") Player target) {
        if (target == null) {
            sender.sendMessage(msg.getWithPrefix("commands.player-not-found"));
            return;
        }
        
        AttackPatternAnalyzer.AttackSummary summary = 
            plugin.getPatternAnalyzer().getAttackSummary(target.getUniqueId());
        
        sender.sendMessage(msg.get("commands.stats-header", "player", target.getName()));
        
        sender.sendMessage(msg.get("commands.stats-total-attacks", 
            "value", String.valueOf(summary.totalAttacks())));
        
        sender.sendMessage(msg.get("commands.stats-attacks-per-second", 
            "value", String.format("%.2f", summary.attacksPerSecond())));
        
        String consistencyColor = getColorCode(summary.timingConsistency());
        sender.sendMessage(msg.get("commands.stats-timing-consistency", 
            "value", consistencyColor + String.format("%.1f%%", summary.timingConsistency() * 100)));
        
        String suspicionColor = getColorCode(summary.suspicionScore());
        sender.sendMessage(msg.get("commands.stats-suspicion-score", 
            "value", suspicionColor + String.format("%.1f%%", summary.suspicionScore() * 100)));
        
        boolean eligible = plugin.getPatternAnalyzer().shouldReceiveCaptcha(target.getUniqueId());
        String eligibleColor = eligible ? "&c" : "&a";
        sender.sendMessage(msg.get("commands.stats-captcha-eligible", 
            "value", eligibleColor + (eligible ? "Yes" : "No")));
        
        // Show captcha count from storage
        int captchaCount = plugin.getCaptchaManager().getCaptchaCount(target.getUniqueId());
        sender.sendMessage(msg.get("commands.stats-captcha-count", 
            "value", String.valueOf(captchaCount)));
    }
    
    @Command("aac reset <player>")
    @CommandDescription("Reset a player's attack data")
    @Permission("antiautoclick.admin")
    public void resetPlayer(CommandSender sender, @Argument("player") Player target) {
        if (target == null) {
            sender.sendMessage(msg.getWithPrefix("commands.player-not-found"));
            return;
        }
        
        plugin.getPatternAnalyzer().resetPlayerData(target.getUniqueId());
        plugin.getCaptchaManager().clearPlayerSession(target.getUniqueId());
        
        sender.sendMessage(msg.getWithPrefix("commands.data-reset", "player", target.getName()));
    }
    
    @Command("aac list")
    @CommandDescription("List all players with attack data")
    @Permission("antiautoclick.admin")
    public void listPlayers(CommandSender sender) {
        sender.sendMessage(msg.get("commands.list-header"));
        
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            int attacks = plugin.getPatternAnalyzer().getAttackCount(player.getUniqueId());
            if (attacks > 0) {
                double suspicion = plugin.getPatternAnalyzer()
                    .calculateSuspicionScore(player.getUniqueId());
                
                String suspicionColor = getColorCode(suspicion);
                sender.sendMessage(msg.get("commands.list-entry", 
                    "player", player.getName(),
                    "attacks", String.valueOf(attacks),
                    "suspicion", suspicionColor + String.format("%.0f", suspicion * 100)));
                count++;
            }
        }
        
        if (count == 0) {
            sender.sendMessage(msg.get("commands.no-players-monitored"));
        }
    }
    
    @Command("aac test webhook")
    @CommandDescription("Send a test message to the Discord webhook")
    @Permission("antiautoclick.admin")
    public void testWebhook(CommandSender sender) {
        String webhookUrl = plugin.getConfig().getString("discord-webhook-url", "");
        
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            sender.sendMessage(msg.getWithPrefix("commands.webhook-not-configured"));
            return;
        }
        
        sender.sendMessage(msg.getWithPrefix("commands.webhook-sending"));
        
        // Create a fake summary for testing
        if (sender instanceof Player player) {
            AttackPatternAnalyzer.AttackSummary testSummary = 
                new AttackPatternAnalyzer.AttackSummary(999, 15.5, 0.95, 0.87);
            plugin.getDiscordWebhook().sendFailureReport(player, testSummary);
        } else {
            sender.sendMessage(msg.getWithPrefix("commands.must-be-player"));
        }
    }
    
    private String getColorCode(double score) {
        if (score >= 0.8) return "&c";
        if (score >= 0.6) return "&6";
        if (score >= 0.4) return "&e";
        return "&a";
    }
}
