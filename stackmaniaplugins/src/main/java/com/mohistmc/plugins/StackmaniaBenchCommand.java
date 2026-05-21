/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.mohistmc.plugins;

import com.stackmania.core.StackmaniaConfig;
import com.stackmania.optimization.StackmaniaTickOptimizer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-game and console runtime introspection for the Stackmania bench harness.
 *
 * Subcommands:
 *
 * - {@code /stackmania bench} or {@code /stackmania bench status} —
 *   prints, to the calling sender, which of the 14 togglable modules are
 *   on or off plus the live metrics for modules that expose them.
 *
 * - {@code /stackmania bench dump} —
 *   writes a {@code stackmania-bench-yyyyMMdd-HHmmss.json} file in the
 *   plugin data folder containing the same information as JSON, plus a
 *   timestamp + the running mod set. Useful to feed Spark or any external
 *   benchmark script.
 *
 * - {@code /stackmania bench help} — usage.
 *
 * Permission: {@code stackmania.admin}. By default only the server console
 * and OPs hold it; configure it in your permissions plugin to delegate.
 */
public class StackmaniaBenchCommand implements CommandExecutor {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Plugin plugin;

    public StackmaniaBenchCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        if (!"bench".equalsIgnoreCase(args[0])) {
            sendUsage(sender);
            return true;
        }
        String sub = args.length > 1 ? args[1].toLowerCase() : "status";
        switch (sub) {
            case "status":
                sendStatus(sender);
                return true;
            case "dump":
                sendDump(sender);
                return true;
            case "help":
            default:
                sendBenchHelp(sender);
                return true;
        }
    }

    // ---------------------------------------------------------------- output

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Stackmania — usage:");
        sender.sendMessage(ChatColor.YELLOW + "  /stackmania bench [status|dump|help]");
    }

    private void sendBenchHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Stackmania bench — subcommands:");
        sender.sendMessage(ChatColor.YELLOW + "  status " + ChatColor.GRAY + "— show module on/off + live metrics");
        sender.sendMessage(ChatColor.YELLOW + "  dump   " + ChatColor.GRAY + "— write a JSON snapshot to the plugin data folder");
        sender.sendMessage(ChatColor.YELLOW + "  help   " + ChatColor.GRAY + "— this help");
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Stackmania bench status:");
        for (Map.Entry<String, Boolean> e : collectModuleFlags().entrySet()) {
            ChatColor mark = e.getValue() ? ChatColor.GREEN : ChatColor.DARK_GRAY;
            String state = e.getValue() ? "enabled" : "DISABLED";
            sender.sendMessage("  " + mark + e.getKey() + ChatColor.GRAY + ": " + state);
        }

        // Live metrics for modules that expose them. Wrapped in try/catch
        // because each getInstance() throws IllegalStateException when the
        // owning module is disabled — that is the intentional fail-loud
        // behavior, not a bug to suppress.
        sender.sendMessage(ChatColor.GOLD + "Live metrics:");
        try {
            StackmaniaTickOptimizer.TPSStats stats = StackmaniaTickOptimizer.getInstance().getStats();
            sender.sendMessage("  " + ChatColor.AQUA + "tick_optimizer" + ChatColor.GRAY + ": " + stats.toString());
            sender.sendMessage(
                    "    total_ticks=" + stats.totalTicks
                            + " dropped=" + stats.droppedTicks
                            + " catchup=" + stats.catchupTicks
                            + " overloaded=" + stats.overloaded
                            + " critical=" + stats.critical);
        } catch (Throwable t) {
            sender.sendMessage("  " + ChatColor.DARK_GRAY + "tick_optimizer: " + t.getClass().getSimpleName()
                    + " (module disabled or not initialized)");
        }
    }

    private void sendDump(CommandSender sender) {
        File dir = plugin.getDataFolder();
        if (!dir.exists() && !dir.mkdirs()) {
            sender.sendMessage(ChatColor.RED + "Could not create plugin data folder: " + dir);
            return;
        }
        String filename = "stackmania-bench-" + LocalDateTime.now().format(STAMP) + ".json";
        File out = new File(dir, filename);

        String json = buildDumpJson();
        try {
            Files.write(out.toPath(), json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            sender.sendMessage(ChatColor.RED + "Failed to write " + out.getName() + ": " + ex.getMessage());
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Bench dump written to " + out.getAbsolutePath());
    }

    // -------------------------------------------------------------- helpers

    /**
     * Collects the 14 togglable Stackmania module flags in the order they
     * are declared in StackmaniaConfig. {@code LinkedHashMap} so the output
     * order is stable across calls.
     */
    private Map<String, Boolean> collectModuleFlags() {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        // Tick-hot modules (gated on the hot path too).
        flags.put("tick_optimizer",           StackmaniaConfig.moduleTickOptimizerEnabled);
        flags.put("aggressive_memory",        StackmaniaConfig.moduleAggressiveMemoryEnabled);
        // Init-only modules.
        flags.put("crash_recovery",           StackmaniaConfig.moduleCrashRecoveryEnabled);
        flags.put("security",                 StackmaniaConfig.moduleSecurityEnabled);
        flags.put("mod_loader_bridge",        StackmaniaConfig.moduleModLoaderBridgeEnabled);
        flags.put("material_cache",           StackmaniaConfig.moduleMaterialCacheEnabled);
        flags.put("persistent_player",        StackmaniaConfig.modulePersistentPlayerEnabled);
        flags.put("bukkit_bridge",            StackmaniaConfig.moduleBukkitBridgeEnabled);
        flags.put("perfect_registry",         StackmaniaConfig.modulePerfectRegistryEnabled);
        flags.put("performance_monitor",      StackmaniaConfig.modulePerformanceMonitorEnabled);
        flags.put("stackmania_memory",        StackmaniaConfig.moduleStackmaniaMemoryEnabled);
        flags.put("universal_platform_adapter", StackmaniaConfig.moduleUniversalPlatformAdapterEnabled);
        flags.put("fabric_compatibility",     StackmaniaConfig.moduleFabricCompatibilityEnabled);
        flags.put("sinytra_bridge",           StackmaniaConfig.moduleSinytraBridgeEnabled);
        return flags;
    }

    private String buildDumpJson() {
        // Hand-rolled JSON. Keeps the plugin module dependency-free; the
        // file is small (< 2 KB typical) and read by humans, not parsed at
        // scale, so a Gson dependency would be overkill.
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{\n");
        sb.append("  \"timestamp\": \"").append(LocalDateTime.now()).append("\",\n");
        sb.append("  \"server_version\": \"").append(escape(plugin.getServer().getVersion())).append("\",\n");
        sb.append("  \"bukkit_version\": \"").append(escape(plugin.getServer().getBukkitVersion())).append("\",\n");

        sb.append("  \"modules\": {\n");
        Map<String, Boolean> flags = collectModuleFlags();
        int i = 0;
        for (Map.Entry<String, Boolean> e : flags.entrySet()) {
            sb.append("    \"").append(e.getKey()).append("\": ").append(e.getValue());
            if (++i < flags.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append("  },\n");

        sb.append("  \"metrics\": {\n");
        try {
            StackmaniaTickOptimizer.TPSStats stats = StackmaniaTickOptimizer.getInstance().getStats();
            sb.append("    \"tick_optimizer\": {\n");
            sb.append("      \"tps\": ").append(stats.tps).append(",\n");
            sb.append("      \"avg_tick_ms\": ").append(stats.avgTickMs).append(",\n");
            sb.append("      \"total_ticks\": ").append(stats.totalTicks).append(",\n");
            sb.append("      \"dropped_ticks\": ").append(stats.droppedTicks).append(",\n");
            sb.append("      \"catchup_ticks\": ").append(stats.catchupTicks).append(",\n");
            sb.append("      \"overloaded\": ").append(stats.overloaded).append(",\n");
            sb.append("      \"critical\": ").append(stats.critical).append(",\n");
            sb.append("      \"skipping_non_essential\": ").append(stats.skippingNonEssential).append(",\n");
            sb.append("      \"aggressive_mode\": ").append(stats.aggressiveMode).append('\n');
            sb.append("    }\n");
        } catch (Throwable t) {
            sb.append("    \"tick_optimizer\": null\n");
        }
        sb.append("  }\n");

        sb.append("}\n");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
