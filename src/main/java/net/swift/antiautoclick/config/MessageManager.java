/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.swift.antiautoclick.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.swift.antiautoclick.AntiAutoClickerPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages all plugin messages from messages.yml
 */
public class MessageManager {
    
    private final AntiAutoClickerPlugin plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = 
        LegacyComponentSerializer.legacyAmpersand();
    
    public MessageManager(AntiAutoClickerPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }
    
    /**
     * Load or reload messages from file
     */
    public void loadMessages() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }
        
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        
        // Load defaults from jar
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream));
            messagesConfig.setDefaults(defaultConfig);
        }
    }
    
    /**
     * Get a raw message string from the config
     */
    public String getRaw(String path) {
        return messagesConfig.getString(path, path);
    }
    
    /**
     * Get a message as a Component with color codes parsed
     */
    public Component get(String path) {
        String message = getRaw(path);
        return colorize(message);
    }
    
    /**
     * Get a message with placeholder replacements
     */
    public Component get(String path, String... replacements) {
        String message = getRaw(path);
        message = replacePlaceholders(message, replacements);
        return colorize(message);
    }
    
    /**
     * Get a message with prefix
     */
    public Component getWithPrefix(String path) {
        String prefix = getRaw("prefix");
        String message = getRaw(path);
        return colorize(prefix + message);
    }
    
    /**
     * Get a message with prefix and placeholder replacements
     */
    public Component getWithPrefix(String path, String... replacements) {
        String prefix = getRaw("prefix");
        String message = getRaw(path);
        message = replacePlaceholders(message, replacements);
        return colorize(prefix + message);
    }
    
    /**
     * Get a list of messages as Components
     */
    public List<Component> getList(String path) {
        List<String> messages = messagesConfig.getStringList(path);
        return messages.stream()
            .map(this::colorize)
            .toList();
    }
    
    /**
     * Get a list of raw strings
     */
    public List<String> getRawList(String path) {
        return messagesConfig.getStringList(path);
    }
    
    /**
     * Convert legacy color codes to Component
     */
    public Component colorize(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return LEGACY_SERIALIZER.deserialize(message);
    }
    
    /**
     * Replace placeholders in format %key% with values
     * Replacements should be in pairs: key1, value1, key2, value2, ...
     */
    private String replacePlaceholders(String message, String... replacements) {
        if (replacements.length % 2 != 0) {
            plugin.getLogger().warning("Invalid placeholder replacements - must be in pairs");
            return message;
        }
        
        for (int i = 0; i < replacements.length; i += 2) {
            String key = replacements[i];
            String value = replacements[i + 1];
            message = message.replace("%" + key + "%", value);
        }
        
        return message;
    }
    
    /**
     * Get the underlying config for direct access
     */
    public FileConfiguration getConfig() {
        return messagesConfig;
    }
}
