/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.swift.antiautoclick.detection;

import net.swift.antiautoclick.AntiAutoClickerPlugin;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Analyzes attack patterns to detect automated clicking behavior.
 * 
 * KillAura characteristics we're looking for:
 * 1. Consistent attack timing (respects attack cooldown precisely)
 * 2. High attack rate sustained over time
 * 3. Perfect timing consistency (humans vary more)
 * 4. Attacks at nearly exactly the cooldown reset point
 */
public class AttackPatternAnalyzer {
    
    private final AntiAutoClickerPlugin plugin;
    private final Map<UUID, PlayerAttackData> playerData = new ConcurrentHashMap<>();
    
    public AttackPatternAnalyzer(AntiAutoClickerPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Record an attack from a player
     */
    public void recordAttack(Player player) {
        PlayerAttackData data = playerData.computeIfAbsent(
            player.getUniqueId(), 
            k -> new PlayerAttackData()
        );
        data.recordAttack(System.currentTimeMillis());
    }
    
    /**
     * Get the total attack count for a player
     */
    public int getAttackCount(UUID playerId) {
        PlayerAttackData data = playerData.get(playerId);
        return data != null ? data.getTotalAttacks() : 0;
    }
    
    /**
     * Calculate suspicion score for a player (0.0 - 1.0)
     * Higher score = more likely using automation
     */
    public double calculateSuspicionScore(UUID playerId) {
        PlayerAttackData data = playerData.get(playerId);
        if (data == null) return 0.0;
        
        int windowSeconds = plugin.getConfig().getInt("detection.analysis-window-seconds", 30);
        List<Long> recentAttacks = data.getAttacksInWindow(windowSeconds * 1000L);
        
        if (recentAttacks.size() < 10) {
            return 0.0; // Not enough data
        }
        
        // Factor 1: Attack rate
        double attackRate = calculateAttackRate(recentAttacks, windowSeconds);
        double suspiciousRate = plugin.getConfig().getDouble("detection.suspicious-attack-rate", 8.0);
        double rateScore = Math.min(attackRate / suspiciousRate, 1.0);
        
        // Factor 2: Timing consistency (variance in attack intervals)
        double consistencyScore = calculateTimingConsistency(recentAttacks);
        
        // Factor 3: Cooldown precision (how close to perfect cooldown timing)
        double cooldownScore = calculateCooldownPrecision(recentAttacks);
        
        // Combined weighted score
        double score = (rateScore * 0.3) + (consistencyScore * 0.4) + (cooldownScore * 0.3);
        
        return Math.min(score, 1.0);
    }
    
    /**
     * Check if player should receive a captcha based on their activity
     */
    public boolean shouldReceiveCaptcha(UUID playerId) {
        boolean devMode = plugin.getConfig().getBoolean("dev-mode", false);
        int minAttacks = devMode ? 10 : plugin.getConfig().getInt("minimum-attacks-for-captcha", 100);
        return getAttackCount(playerId) >= minAttacks;
    }
    
    /**
     * Reset attack data for a player (after captcha success)
     */
    public void resetPlayerData(UUID playerId) {
        playerData.remove(playerId);
    }
    
    /**
     * Get a summary of player's attack patterns for Discord report
     */
    public AttackSummary getAttackSummary(UUID playerId) {
        PlayerAttackData data = playerData.get(playerId);
        if (data == null) {
            return new AttackSummary(0, 0.0, 0.0, 0.0);
        }
        
        int windowSeconds = plugin.getConfig().getInt("detection.analysis-window-seconds", 30);
        List<Long> recentAttacks = data.getAttacksInWindow(windowSeconds * 1000L);
        
        return new AttackSummary(
            data.getTotalAttacks(),
            calculateAttackRate(recentAttacks, windowSeconds),
            calculateTimingConsistency(recentAttacks),
            calculateSuspicionScore(playerId)
        );
    }
    
    private double calculateAttackRate(List<Long> attacks, int windowSeconds) {
        if (attacks.size() < 2) return 0.0;
        return (double) attacks.size() / windowSeconds;
    }
    
    private double calculateTimingConsistency(List<Long> attacks) {
        if (attacks.size() < 3) return 0.0;
        
        // Calculate intervals between attacks
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < attacks.size(); i++) {
            intervals.add(attacks.get(i) - attacks.get(i - 1));
        }
        
        // Calculate mean and standard deviation
        double mean = intervals.stream().mapToLong(Long::longValue).average().orElse(0);
        if (mean == 0) return 0.0;
        
        double variance = intervals.stream()
            .mapToDouble(i -> Math.pow(i - mean, 2))
            .average()
            .orElse(0);
        double stdDev = Math.sqrt(variance);
        
        // Coefficient of variation (lower = more consistent = more suspicious)
        double cv = stdDev / mean;
        
        // Convert to score (low CV = high consistency = high score)
        // Human clicking typically has CV > 0.3, bots < 0.15
        return Math.max(0, 1.0 - (cv * 2));
    }
    
    private double calculateCooldownPrecision(List<Long> attacks) {
        if (attacks.size() < 3) return 0.0;
        
        // Minecraft attack cooldown is ~625ms for swords
        // KillAura attacks at 90-98% cooldown, so ~562-612ms
        final long EXPECTED_COOLDOWN = 600; // ms
        final long TOLERANCE = 100; // ms
        
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < attacks.size(); i++) {
            intervals.add(attacks.get(i) - attacks.get(i - 1));
        }
        
        // Count how many intervals are within expected cooldown range
        long preciseCount = intervals.stream()
            .filter(i -> Math.abs(i - EXPECTED_COOLDOWN) <= TOLERANCE)
            .count();
        
        return (double) preciseCount / intervals.size();
    }
    
    /**
     * Inner class to track individual player attack data
     */
    private static class PlayerAttackData {
        private final List<Long> attackTimestamps = Collections.synchronizedList(new ArrayList<>());
        private int totalAttacks = 0;
        
        void recordAttack(long timestamp) {
            attackTimestamps.add(timestamp);
            totalAttacks++;
            
            // Keep only last 1000 timestamps to prevent memory issues
            if (attackTimestamps.size() > 1000) {
                attackTimestamps.remove(0);
            }
        }
        
        List<Long> getAttacksInWindow(long windowMs) {
            long cutoff = System.currentTimeMillis() - windowMs;
            synchronized (attackTimestamps) {
                return attackTimestamps.stream()
                    .filter(t -> t >= cutoff)
                    .toList();
            }
        }
        
        int getTotalAttacks() {
            return totalAttacks;
        }
    }
    
    /**
     * Summary record for Discord reports
     */
    public record AttackSummary(
        int totalAttacks,
        double attacksPerSecond,
        double timingConsistency,
        double suspicionScore
    ) {}
}
