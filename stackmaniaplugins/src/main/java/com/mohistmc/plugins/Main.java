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
    }
}