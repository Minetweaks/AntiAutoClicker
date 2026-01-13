/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.swift.antiautoclick.captcha;

import net.kyori.adventure.text.Component;
import net.swift.antiautoclick.AntiAutoClickerPlugin;
import net.swift.antiautoclick.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Creates captcha GUI inventories where players must drag a diamond to a chest
 */
public class CaptchaGui implements InventoryHolder {
    
    private static final int GUI_SIZE = 54; // 6 rows
    private static final int CHEST_SLOT = 22; // Middle of the GUI (row 3, slot 5)
    
    // Cheap filler materials - worthless items
    private static final Material[] FILLER_MATERIALS = {
        Material.COBBLESTONE,
        Material.DIRT,
        Material.GRAVEL,
        Material.SAND,
        Material.OAK_LOG,
        Material.BIRCH_LOG,
        Material.SPRUCE_LOG,
        Material.ACACIA_LOG,
        Material.OAK_PLANKS,
        Material.BIRCH_PLANKS,
        Material.STICK,
        Material.ROTTEN_FLESH,
        Material.SPIDER_EYE,
        Material.BONE,
        Material.STRING,
        Material.FEATHER,
        Material.WHEAT_SEEDS,
        Material.KELP,
        Material.CLAY_BALL,
        Material.FLINT
    };
    
    private final AntiAutoClickerPlugin plugin;
    private final MessageManager msg;
    private final Player player;
    private final Inventory inventory;
    private final int targetSlot;
    private final Random random = new Random();
    
    public CaptchaGui(AntiAutoClickerPlugin plugin, Player player) {
        this.plugin = plugin;
        this.msg = plugin.getMessages();
        this.player = player;
        
        // Create the inventory with title from messages
        this.inventory = Bukkit.createInventory(
            this,
            GUI_SIZE,
            msg.get("gui.title")
        );
        
        // Pick a random slot for the redstone (not the chest slot)
        int slot;
        do {
            slot = random.nextInt(GUI_SIZE);
        } while (slot == CHEST_SLOT);
        this.targetSlot = slot;
        
        // Fill the inventory
        setupInventory();
    }
    
    private void setupInventory() {
        // Fill all slots with random filler items
        for (int i = 0; i < GUI_SIZE; i++) {
            if (i == CHEST_SLOT) {
                // Place the target chest
                inventory.setItem(i, createChestItem());
            } else if (i == targetSlot) {
                // Place the redstone target
                inventory.setItem(i, createTargetItem());
            } else {
                // Place random filler
                inventory.setItem(i, createFillerItem());
            }
        }
    }
    
    private ItemStack createChestItem() {
        ItemStack chest = new ItemStack(Material.CHEST);
        ItemMeta meta = chest.getItemMeta();
        meta.displayName(msg.get("gui.chest-name"));
        meta.lore(msg.getList("gui.chest-lore"));
        chest.setItemMeta(meta);
        return chest;
    }
    
    private ItemStack createTargetItem() {
        ItemStack redstone = new ItemStack(Material.REDSTONE);
        ItemMeta meta = redstone.getItemMeta();
        meta.displayName(msg.get("gui.target-name"));
        meta.lore(msg.getList("gui.target-lore"));
        // Make it glow
        meta.setEnchantmentGlintOverride(true);
        redstone.setItemMeta(meta);
        return redstone;
    }
    
    private ItemStack createFillerItem() {
        Material material = FILLER_MATERIALS[random.nextInt(FILLER_MATERIALS.length)];
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        
        // Get filler names from messages
        List<Component> fillerNames = msg.getList("gui.filler-names");
        if (!fillerNames.isEmpty()) {
            meta.displayName(fillerNames.get(random.nextInt(fillerNames.size())));
        }
        filler.setItemMeta(meta);
        return filler;
    }
    
    public void open() {
        player.openInventory(inventory);
    }
    
    public Player getPlayer() {
        return player;
    }
    
    public int getTargetSlot() {
        return targetSlot;
    }
    
    public int getChestSlot() {
        return CHEST_SLOT;
    }
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    
    /**
     * Check if the given slot is the target (redstone) slot
     */
    public boolean isTargetSlot(int slot) {
        return slot == targetSlot;
    }
    
    /**
     * Check if the given slot is the chest slot
     */
    public boolean isChestSlot(int slot) {
        return slot == CHEST_SLOT;
    }
}
