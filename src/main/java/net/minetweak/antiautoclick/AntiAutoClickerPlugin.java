/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.minetweak.antiautoclick;

import net.minetweak.antiautoclick.captcha.CaptchaManager;
import net.minetweak.antiautoclick.commands.AntiAutoClickerCommands;
import net.minetweak.antiautoclick.config.MessageManager;
import net.minetweak.antiautoclick.detection.AttackPatternAnalyzer;
import net.minetweak.antiautoclick.discord.DiscordWebhook;
import net.minetweak.antiautoclick.listeners.CombatListener;
import net.minetweak.antiautoclick.listeners.ChatListener;
import net.minetweak.antiautoclick.listeners.CaptchaGuiListener;
import net.minetweak.antiautoclick.storage.StorageFactory;
import net.minetweak.antiautoclick.storage.StorageProvider;
import net.minetweak.antiautoclick.util.MetricsManager;
import net.minetweak.antiautoclick.util.SchedulerUtil;
import net.minetweak.antiautoclick.util.UpdateChecker;
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
    private UpdateChecker updateChecker;
    private MetricsManager metricsManager;
    
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
        
        // Initialize update checker
        this.updateChecker = new UpdateChecker(this);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        updateChecker.checkForUpdates();
        
        // Initialize bStats metrics
        this.metricsManager = new MetricsManager(this);
        metricsManager.initialize();
        
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
        
        SchedulerUtil.runGlobalTaskTimer(this, () -> {
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
