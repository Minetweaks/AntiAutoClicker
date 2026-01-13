/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.minetweak.antiautoclick.storage;

import net.minetweak.antiautoclick.AntiAutoClickerPlugin;

/**
 * Factory for creating storage providers
 */
public class StorageFactory {
    
    /**
     * Create a storage provider based on config
     */
    public static StorageProvider createStorage(AntiAutoClickerPlugin plugin) {
        String type = plugin.getConfig().getString("storage.type", "sqlite").toLowerCase();
        
        return switch (type) {
            case "mysql", "mariadb" -> new MysqlStorage(plugin);
            default -> new SqliteStorage(plugin);
        };
    }
}
