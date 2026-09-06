package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.CargoBatch;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLabels;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;

/**
 * Player-facing names for HOT bodies and cargo. Canonical identities stay in tags and diagnostics;
 * an in-world player should see a role, settlement and actual cargo rather than an internal ID.
 */
final class FrontierV3ScenePresentation {
    private FrontierV3ScenePresentation() { }

    static Component actorName(FrontierWorldState state, SubjectId actorId, boolean bioform) {
        return Component.literal(FrontierSceneLabels.actor(state, actorId, bioform))
                .withStyle(bioform ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD);
    }

    /**
     * Applies the current pure actor presentation without making it another source of state.
     * Ambient civilians intentionally keep their nameplate quiet; a mobilized resident must be
     * identifiable at a glance, and every hive bioform is a deliberate non-civilian presence.
     */
    static void applyAmbientActorPresentation(Mob body, FrontierWorldState state, SubjectId actorId, boolean bioform) {
        body.setCustomName(actorName(state, actorId, bioform));
        body.setCustomNameVisible(FrontierSceneLabels.ambientActorNameVisible(state, actorId, bioform));
    }

    static Component cargoName(FrontierWorldState state, CargoBatch cargo) {
        return Component.literal(FrontierSceneLabels.cargo(state, cargo)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }
}
