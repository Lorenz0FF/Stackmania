/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.stackmania.core;

import com.mohistmc.i18n.i18n;
import net.minecraftforge.versions.forge.ForgeVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Stackmania Core — shared logger and version-info holder for the
 * com.stackmania.* modules.
 *
 * NOT a Forge @Mod entry point. The active @Mod("stackmania") class is
 * {@link com.mohistmc.MohistMC}, which drives the 12-layer init sequence
 * (including the four sub-systems this class used to initialize). To
 * avoid two classes claiming the same mod id, the @Mod and @OnlyIn
 * annotations were removed from this class on 2026-05-21.
 *
 * Static fields exposed to the rest of com.stackmania.* (LOGGER in
 * particular) remain available because they are initialized at class
 * load time, not in the constructor.
 *
 * @author Valonia Games
 * @version 1.0.0
 */
public class StackmaniaCore {
    
    public static final String NAME = "Stackmania";
    public static final String MOD_ID = "stackmania";
    public static final String VERSION = "1.20.1";
    public static final String BUILD_TARGET = "SpyGut";
    
    public static final Logger LOGGER = LogManager.getLogger(NAME);
    
    public static i18n i18n;
    public static ClassLoader classLoader;
    public static StackmaniaVersion versionInfo;

    private StackmaniaCore() {
        // Utility class; instantiation is suppressed.
        // The original @Mod-driven init flow lived here but moved to
        // com.mohistmc.MohistMC.initializeStackmaniaLayers() so that there is
        // exactly one Forge @Mod("stackmania") entry point.
    }

    /**
     * Called when the server is fully started to initialize version info.
     * Currently unused — kept as a public hook in case a future entry-point
     * cleanup wants to wire i18n + versionInfo here instead of in MohistMC.
     */
    public static void initVersion() {
        String lang = StackmaniaConfig.getLanguage();
        i18n = new i18n(StackmaniaCore.class.getClassLoader(), lang);
        
        Map<String, String> arguments = new HashMap<>();
        String[] cbs = CraftServer.class.getPackage().getImplementationVersion().split("-");
        
        arguments.put("stackmania", getStackmaniaVersion());
        arguments.put("bukkit", cbs[0]);
        arguments.put("craftbukkit", cbs[1]);
        arguments.put("spigot", cbs[2]);
        arguments.put("neoforge", cbs[3]);
        arguments.put("forge", ForgeVersion.getVersion());
        arguments.put("target", BUILD_TARGET);
        
        versionInfo = new StackmaniaVersion(arguments);
        
        LOGGER.info("Stackmania {} initialized - Target: {} - Forge: {}", 
            getStackmaniaVersion(), BUILD_TARGET, ForgeVersion.getVersion());
    }
    
    /**
     * Get the Stackmania version string
     */
    public static String getStackmaniaVersion() {
        String implVersion = StackmaniaCore.class.getPackage().getImplementationVersion();
        return implVersion != null ? implVersion : VERSION;
    }
    
}
