package com.mohistmc.plugins;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * @author Mgazul by MohistMC
 * @date 2023/6/26 20:02:25
 */
public class Main extends JavaPlugin {

    @Override
    public void onEnable() {
        // /stackmania root command. Subcommand routing lives in
        // StackmaniaBenchCommand so the executor stays small here.
        PluginCommand stackmania = getCommand("stackmania");
        if (stackmania != null) {
            stackmania.setExecutor(new StackmaniaBenchCommand(this));
        } else {
            getLogger().warning(
                    "Could not register the /stackmania command — make sure plugin.yml "
                            + "declares it under commands:");
        }

        // Per-player hostile mob cap. The listener itself is gated on
        // StackmaniaConfig.moduleMobCapDistributorEnabled (default false), so
        // registering it unconditionally is fine — until the operator opts in
        // via stackmania.yml, every event short-circuits on the first check.
        getServer().getPluginManager().registerEvents(new StackmaniaMobCapDistributor(), this);

        // Dynamic view distance scheduler. Same opt-in pattern as the mob cap:
        // the task is scheduled unconditionally and bails on the first line of
        // run() when the feature is disabled.
        StackmaniaDynamicViewDistance.start(this);
    }
}