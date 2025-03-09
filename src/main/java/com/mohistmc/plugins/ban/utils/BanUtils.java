package com.mohistmc.plugins.ban.utils;

import com.mohistmc.MohistConfig;
import com.mohistmc.plugins.ban.BanType;
import com.mohistmc.plugins.ban.ClickType;
import com.mohistmc.util.I18n;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * @author Mgazul by MohistMC
 * @date 2023/7/27 15:10:47
 */
public class BanUtils {

    public static void saveToYaml(Player player, ClickType clickType, List<String> list, BanType banType) {
        MohistConfig.yml.set(banType.key, list);
        MohistConfig.save();
        if (clickType == ClickType.ADD) {
            player.sendMessage(I18n.as("bans.add.item"));
        } else if (clickType == ClickType.REMOVE) {
            player.sendMessage(I18n.as("bans.remove.item"));
        }
    }
}
