package io.farfrontier.palemirror.internal.integration.crimson;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** All private Crimson 1.4.3.1 identifiers and command protocol stay here. */
final class CrimsonProtocol1431 {
    static final String MOD_ID = "mr_crimson_curse";
    static final String VERSION = "1.4.3.1";
    private CrimsonProtocol1431() { }

    static boolean initializeActor(ServerLevel level, Mob actor, CrimsonActorProfile profile) {
        String temporaryTag = "pm_crimson_init_" + actor.getUUID().toString().replace("-", "");
        actor.addTag(temporaryTag);
        try {
            MinecraftServer server = level.getServer();
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput().withPermission(4),
                    "execute as @e[tag=" + temporaryTag + ",limit=1] at @s run function pale_mirror:crimson/v1431/"
                            + profile.initializer());
            return actor.getTags().contains(profile.markerTag());
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            actor.removeTag(temporaryTag);
        }
    }
}
