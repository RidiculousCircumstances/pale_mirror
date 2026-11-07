package com.vibecraft.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.level.Level;

/** Minecraft 1.21.1 API boundary; this port deliberately targets one version. */
public final class VersionCompat {
    private VersionCompat() {}
    public static int getTopYInclusive(Level level) { return level.getMaxBuildHeight() - 1; }
    public static String getProfileName(GameProfile profile) { return profile.getName(); }
}
