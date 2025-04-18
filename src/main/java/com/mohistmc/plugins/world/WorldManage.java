package com.mohistmc.plugins.world;

import com.mohistmc.api.ServerAPI;
import com.mohistmc.plugins.world.utils.ConfigByWorlds;
import com.mohistmc.util.I18n;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Objects;

public class WorldManage {

    public static void onEnable() {
        ConfigByWorlds.init();
        ConfigByWorlds.loadWorlds();
        ConfigByWorlds.addWorld(ServerAPI.getNMSServer().server.getServer().getProperties().levelName, false);
    }

    public static void deleteDir(File path) {
        if (path.exists()) {
            File[] allContents = path.listFiles();
            if (allContents != null) {
                File[] array;
                for (int length = (array = allContents).length, i = 0; i < length; ++i) {
                    File file = array[i];
                    deleteDir(file);
                }
            }
            path.delete();
        }
    }

    public static void changeGameMode(World world, GameMode gameMode) {
        for (Player player : world.getPlayers()) {
            player.setGameMode(gameMode);
        }
        ConfigByWorlds.setGameMode(world, gameMode.name());
    }

    public static void changeGameMode(ServerPlayer serverPlayer, World world) {
        Player player = serverPlayer.getBukkitEntity();
        GameMode gameMode = ConfigByWorlds.getGameMode(world);
        player.setGameMode(Objects.requireNonNullElseGet(gameMode, Bukkit::getDefaultGameMode));
    }
}
