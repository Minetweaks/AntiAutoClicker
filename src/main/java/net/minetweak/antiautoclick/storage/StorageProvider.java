/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.minetweak.antiautoclick.storage;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for persistent data storage
 */
public interface StorageProvider {
    
    /**
     * Initialize the storage connection
     * @return true if successful
     */
    CompletableFuture<Boolean> initialize();
    
    /**
     * Close the storage connection
     */
    void shutdown();
    
    /**
     * Save player attack data
     */
    CompletableFuture<Void> savePlayerData(UUID playerId, String playerName, PlayerData data);
    
    /**
     * Load player attack data
     */
    CompletableFuture<PlayerData> loadPlayerData(UUID playerId);
    
    /**
     * Delete player data
     */
    CompletableFuture<Void> deletePlayerData(UUID playerId);
    
    /**
     * Check if storage is connected
     */
    boolean isConnected();
    
    /**
     * Get the storage type name
     */
    String getTypeName();
    
    /**
     * Player data record
     */
    record PlayerData(
        int totalAttacks,
        int captchasReceived,
        int captchasPassed,
        int captchasFailed,
        long lastSeen
    ) {
        public static PlayerData empty() {
            return new PlayerData(0, 0, 0, 0, System.currentTimeMillis());
        }
    }
}
