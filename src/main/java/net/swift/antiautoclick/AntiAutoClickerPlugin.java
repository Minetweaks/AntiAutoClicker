/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.swift.antiautoclick;

import net.kyori.adventure.text.Component;
import net.swift.antiautoclick.captcha.CaptchaManager;
import net.swift.antiautoclick.commands.AntiAutoClickerCommands;
import net.swift.antiautoclick.config.MessageManager;
import net.swift.antiautoclick.detection.AttackPatternAnalyzer;
import net.swift.antiautoclick.discord.DiscordWebhook;
import net.swift.antiautoclick.listeners.CombatListener;
import net.swift.antiautoclick.listeners.ChatListener;
import net.swift.antiautoclick.listeners.CaptchaGuiListener;
import net.swift.antiautoclick.storage.StorageFactory;
import net.swift.antiautoclick.storage.StorageProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.bukkit.command.CommandSender;

public class AntiAutoClickerPlugin extends JavaPlugin {
    
    private static AntiAutoClickerPlugin instance;
    private CaptchaManager captchaManager;
    private AttackPatternAnalyzer patternAnalyzer;
    private DiscordWebhook discordWebhook;
    private MessageManager messageManager;
    private StorageProvider storage;
    private LegacyPaperCommandManager<CommandSender> commandManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize message manager
        this.messageManager = new MessageManager(this);
        
        // Initialize storage
        this.storage = StorageFactory.createStorage(this);
        storage.initialize().thenAccept(success -> {
            if (success) {
                getLogger().info("Connected to " + storage.getTypeName() + " storage");
            } else {
                getLogger().severe("Failed to initialize storage!");
            }
        });
        
        // Initialize components
        this.discordWebhook = new DiscordWebhook(this);
        this.captchaManager = new CaptchaManager(this);
        this.patternAnalyzer = new AttackPatternAnalyzer(this);
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new CaptchaGuiListener(this), this);
        
        // Setup Cloud Commands
        setupCommands();
        
        // Start periodic captcha task
        startCaptchaScheduler();
        
        getLogger().info("AntiAutoClicker enabled! Monitoring for automated clicking patterns.");
    }
    
    @Override
    public void onDisable() {
        if (captchaManager != null) {
            captchaManager.shutdown();
        }
        if (storage != null) {
            storage.shutdown();
        }
        getLogger().info("AntiAutoClicker disabled.");
    }
    
    public void reloadPlugin() {
        reloadConfig();
        messageManager.loadMessages();
    }
    
    private void setupCommands() {
        this.commandManager = LegacyPaperCommandManager.createNative(
            this,
            ExecutionCoordinator.simpleCoordinator()
        );
        
        AnnotationParser<CommandSender> annotationParser = new AnnotationParser<>(
            commandManager,
            CommandSender.class
        );
        
        annotationParser.parse(new AntiAutoClickerCommands(this));
    }
    
    private void startCaptchaScheduler() {
        boolean devMode = getConfig().getBoolean("dev-mode", false);
        int intervalMinutes = devMode ? 2 : getConfig().getInt("captcha-interval-minutes", 30);
        long intervalTicks = intervalMinutes * 60L * 20L; // Convert to ticks
        
        if (devMode) {
            getLogger().warning("DEV MODE ENABLED - Captcha interval set to 2 minutes!");
        }
        
        getServer().getScheduler().runTaskTimer(this, () -> {
            captchaManager.issueScheduledCaptchas();
        }, intervalTicks, intervalTicks);
    }
    
    public static AntiAutoClickerPlugin getInstance() {
        return instance;
    }
    
    public CaptchaManager getCaptchaManager() {
        return captchaManager;
    }
    
    public AttackPatternAnalyzer getPatternAnalyzer() {
        return patternAnalyzer;
    }
    
    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }
    
    public MessageManager getMessages() {
        return messageManager;
    }
    
    public StorageProvider getStorage() {
        return storage;
    }
}
