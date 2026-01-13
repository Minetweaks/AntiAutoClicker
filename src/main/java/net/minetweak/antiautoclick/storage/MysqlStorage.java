/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.minetweak.antiautoclick.storage;

import net.minetweak.antiautoclick.AntiAutoClickerPlugin;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MySQL/MariaDB storage implementation
 */
public class MysqlStorage implements StorageProvider {
    
    private final AntiAutoClickerPlugin plugin;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String tablePrefix;
    
    private Connection connection;
    
    public MysqlStorage(AntiAutoClickerPlugin plugin) {
        this.plugin = plugin;
        this.host = plugin.getConfig().getString("storage.mysql.host", "localhost");
        this.port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        this.database = plugin.getConfig().getString("storage.mysql.database", "antiautoclick");
        this.username = plugin.getConfig().getString("storage.mysql.username", "root");
        this.password = plugin.getConfig().getString("storage.mysql.password", "");
        this.tablePrefix = plugin.getConfig().getString("storage.mysql.table-prefix", "aac_");
    }
    
    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Load MySQL driver
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                // Try MariaDB driver
                try {
                    Class.forName("org.mariadb.jdbc.Driver");
                } catch (ClassNotFoundException e2) {
                    plugin.getLogger().severe("No MySQL/MariaDB driver found!");
                    return false;
                }
            }
            
            try {
                // First connect without database to create it if needed
                String urlWithoutDb = String.format("jdbc:mysql://%s:%d/?useSSL=false&allowPublicKeyRetrieval=true", 
                    host, port);
                
                try (Connection tempConn = DriverManager.getConnection(urlWithoutDb, username, password)) {
                    // Create database if it doesn't exist
                    try (Statement stmt = tempConn.createStatement()) {
                        stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + database + "`");
                        plugin.getLogger().info("Database '" + database + "' ensured to exist");
                    }
                }
                
                // Now connect to the actual database
                String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true", 
                    host, port, database);
                
                connection = DriverManager.getConnection(url, username, password);
                
                // Create tables
                createTables();
                
                plugin.getLogger().info("Connected to MySQL storage: " + host + ":" + port + "/" + database);
                return true;
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to initialize MySQL storage: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    private void createTables() throws SQLException {
        String sql = String.format("""
            CREATE TABLE IF NOT EXISTS %splayer_data (
                uuid VARCHAR(36) PRIMARY KEY,
                player_name VARCHAR(16),
                total_attacks INT DEFAULT 0,
                captchas_received INT DEFAULT 0,
                captchas_passed INT DEFAULT 0,
                captchas_failed INT DEFAULT 0,
                last_seen DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_player_name (player_name),
                INDEX idx_last_seen (last_seen)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """, tablePrefix);
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    private void ensureConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(3)) {
            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true", 
                host, port, database);
            connection = DriverManager.getConnection(url, username, password);
        }
    }
    
    @Override
    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("MySQL connection closed");
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing MySQL connection: " + e.getMessage());
            }
        }
    }
    
    @Override
    public CompletableFuture<Void> savePlayerData(UUID playerId, String playerName, PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            String sql = String.format("""
                INSERT INTO %splayer_data 
                (uuid, player_name, total_attacks, captchas_received, captchas_passed, captchas_failed, last_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                player_name = VALUES(player_name),
                total_attacks = VALUES(total_attacks),
                captchas_received = VALUES(captchas_received),
                captchas_passed = VALUES(captchas_passed),
                captchas_failed = VALUES(captchas_failed),
                last_seen = VALUES(last_seen)
                """, tablePrefix);
            
            try {
                ensureConnection();
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, playerName);
                    stmt.setInt(3, data.totalAttacks());
                    stmt.setInt(4, data.captchasReceived());
                    stmt.setInt(5, data.captchasPassed());
                    stmt.setInt(6, data.captchasFailed());
                    stmt.setTimestamp(7, new Timestamp(data.lastSeen()));
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save player data: " + e.getMessage());
            }
        });
    }
    
    @Override
    public CompletableFuture<PlayerData> loadPlayerData(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = String.format("SELECT * FROM %splayer_data WHERE uuid = ?", tablePrefix);
            
            try {
                ensureConnection();
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
            String sql = String.format("DELETE FROM %splayer_data WHERE uuid = ?", tablePrefix);
            
            try {
                ensureConnection();
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerId.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to delete player data: " + e.getMessage());
            }
        });
    }
    
    @Override
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }
    
    @Override
    public String getTypeName() {
        return "MySQL";
    }
}
