/*
 * Stackmania - Valonia Games
 * Memory command for in-game memory management
 */

package com.stackmania.memory;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * /stackmemory command - View and manage server memory
 * 
 * Usage:
 * /stackmemory - Show memory stats
 * /stackmemory gc - Force garbage collection
 * /stackmemory clean - Force cache cleanup
 */
public class MemoryCommand extends Command {
    
    public MemoryCommand() {
        super("stackmemory");
        this.description = "Stackmania Memory Management";
        this.usageMessage = "/stackmemory [gc|clean|stats]";
        this.setPermission("stackmania.memory");
    }
    
    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!testPermission(sender)) {
            return false;
        }
        
        try {
            StackmaniaMemoryManager manager = StackmaniaMemoryManager.getInstance();
            
            if (args.length == 0 || args[0].equalsIgnoreCase("stats")) {
                showStats(sender, manager);
            } else if (args[0].equalsIgnoreCase("gc")) {
                forceGC(sender, manager);
            } else if (args[0].equalsIgnoreCase("clean")) {
                forceClean(sender, manager);
            } else {
                sender.sendMessage(ChatColor.RED + "Usage: /stackmemory [gc|clean|stats]");
            }
        } catch (IllegalStateException e) {
            sender.sendMessage(ChatColor.RED + "Memory Manager not initialized!");
        }
        
        return true;
    }
    
    private void showStats(CommandSender sender, StackmaniaMemoryManager manager) {
        StackmaniaMemoryManager.MemoryStats stats = manager.getStats();
        
        sender.sendMessage(ChatColor.GOLD + "═══════ " + ChatColor.WHITE + "Stackmania Memory" + ChatColor.GOLD + " ═══════");
        
        // Memory bar
        int barLength = 20;
        int filled = (int) (stats.usagePercent / 100 * barLength);
        StringBuilder bar = new StringBuilder();
        
        ChatColor barColor = stats.usagePercent < 70 ? ChatColor.GREEN :
                            stats.usagePercent < 85 ? ChatColor.YELLOW : ChatColor.RED;
        
        for (int i = 0; i < barLength; i++) {
            bar.append(i < filled ? barColor + "█" : ChatColor.GRAY + "░");
        }
        
        sender.sendMessage(ChatColor.WHITE + "RAM: " + bar + ChatColor.WHITE + 
            String.format(" %dMB/%dMB (%.1f%%)", stats.usedMB, stats.maxMB, stats.usagePercent));
        
        sender.sendMessage(ChatColor.WHITE + "Free: " + ChatColor.AQUA + stats.freeMB + " MB");
        sender.sendMessage(ChatColor.WHITE + "Total Freed: " + ChatColor.GREEN + stats.totalFreedMB + " MB");
        sender.sendMessage(ChatColor.WHITE + "Cleanups: " + ChatColor.YELLOW + stats.cleanupCount);
        sender.sendMessage(ChatColor.WHITE + "Managed Caches: " + ChatColor.AQUA + stats.managedCaches);
        sender.sendMessage(ChatColor.WHITE + "Pooled Objects: " + ChatColor.AQUA + stats.pooledObjects);
        
        if (stats.aggressiveMode) {
            sender.sendMessage(ChatColor.RED + "⚠ AGGRESSIVE MODE ACTIVE - High memory pressure!");
        } else {
            sender.sendMessage(ChatColor.GREEN + "✓ Memory OK");
        }
    }
    
    private void forceGC(CommandSender sender, StackmaniaMemoryManager manager) {
        long before = manager.getUsedMemory() / 1024 / 1024;
        
        sender.sendMessage(ChatColor.YELLOW + "Requesting garbage collection...");
        System.gc();
        
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {}
        
        long after = manager.getUsedMemory() / 1024 / 1024;
        long freed = before - after;
        
        if (freed > 0) {
            sender.sendMessage(ChatColor.GREEN + "GC freed " + freed + " MB");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "GC completed, no significant memory freed");
        }
    }
    
    private void forceClean(CommandSender sender, StackmaniaMemoryManager manager) {
        long before = manager.getUsedMemory() / 1024 / 1024;
        
        sender.sendMessage(ChatColor.YELLOW + "Forcing memory cleanup...");
        manager.forceCleanup();
        
        long after = manager.getUsedMemory() / 1024 / 1024;
        long freed = before - after;
        
        sender.sendMessage(ChatColor.GREEN + "Cleanup complete! Freed " + freed + " MB");
    }
}
