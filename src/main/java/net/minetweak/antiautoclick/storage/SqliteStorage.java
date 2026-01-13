/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.minetweak.antiautoclick.storage;

import net.minetweak.antiautoclick.AntiAutoClickerPlugin;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQLite file-based storage implementation
 */
public class SqliteStorage implements StorageProvider {
    
    private final AntiAutoClickerPlugin plugin;
    private final File databaseFile;
    private Connection connection;
    
    public SqliteStorage(AntiAutoClickerPlugin plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "data.db");
    }
    
    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Ensure data folder exists
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                
                // Load SQLite driver
                Class.forName("org.sqlite.JDBC");
                
                // Connect to database
                String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
                connection = DriverManager.getConnection(url);
                
                // Create tables
                createTables();
                
                plugin.getLogger().info("Connected to SQLite storage: " + databaseFile.getName());
                return true;
                
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize SQLite storage: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    private void createTables() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS player_data (
                uuid VARCHAR(36) PRIMARY KEY,
                player_name VARCHAR(16),
                total_attacks INTEGER DEFAULT 0,
                captchas_received INTEGER DEFAULT 0,
                captchas_passed INTEGER DEFAULT 0,
                captchas_failed INTEGER DEFAULT 0,
                last_seen DATETIME DEFAULT CURRENT_TIMESTAMP
            )
            """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    @Override
    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("SQLite connection closed");
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing SQLite connection: " + e.getMessage());
            }
        }
    }
    
    @Override
    public CompletableFuture<Void> savePlayerData(UUID playerId, String playerName, PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT OR REPLACE INTO player_data 
                (uuid, player_name, total_attacks, captchas_received, captchas_passed, captchas_failed, last_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, playerName);
                stmt.setInt(3, data.totalAttacks());
                stmt.setInt(4, data.captchasReceived());
                stmt.setInt(5, data.captchasPassed());
                stmt.setInt(6, data.captchasFailed());
                stmt.setTimestamp(7, new Timestamp(data.lastSeen()));
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save player data: " + e.getMessage());
            }
        });
    }
    
    @Override
    public CompletableFuture<PlayerData> loadPlayerData(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM player_data WHERE uuid = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    Timestamp lastSeenTs = rs.getTimestamp("last_seen");
                    long lastSeen = lastSeenTs != null ? lastSeenTs.getTime() : System.currentTimeMillis();
                    return new PlayerData(
                        rs.getInt("total_attacks"),
                        rs.getInt("captchas_received"),
                        rs.getInt("captchas_passed"),
                        rs.getInt("captchas_failed"),
                        lastSeen
                    );
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load player data: " + e.getMessage());
            }
            
            return PlayerData.empty();
        });
    }
    
    @Override
    public CompletableFuture<Void> deletePlayerData(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM player_data WHERE uuid = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to delete player data: " + e.getMessage());
            }
        });
    }
    
    @Override
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
    
    @Override
    public String getTypeName() {
        return "SQLite";
    }
}
