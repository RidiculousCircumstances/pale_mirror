package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.CargoBatch;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLabels;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

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

    static Component cargoName(FrontierWorldState state, CargoBatch cargo) {
        return Component.literal(FrontierSceneLabels.cargo(state, cargo)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }
}
