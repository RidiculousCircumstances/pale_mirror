package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteKind;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3ResourceSiteExecutorTest {
    @Test
    void canonicalPreparationDoesNotWaitForAnyNaturallyLoadedField() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:loaded-field-selection"), 77L));
        engine.advanceTo(new SimInstant(100L), new WorkBudget(64, 512));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());

        assertEquals(io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase.GROWING,
                state.resourceSites().site(new io.farfrontier.palemirror.frontier.v3.api.SubjectId("site:2-wheat-field")).phase(),
                "an unloaded lexicographically earlier field cannot gate the canonical field clock");
    }

    @Test
    void residentChunksCannotSubstituteForTheOnePlayerLocalIngressBoundary() {
        ArrayList<BlockPosition> slots = new ArrayList<>();
        for (int x = 160; x < 168; x++) for (int z = 320; z < 328; z++) slots.add(new BlockPosition(x, 64, z));
        ResourceSite site = new ResourceSite(new SubjectId("site:99-wheat-field"), new SubjectId("settlement:99"),
                new SubjectId("structure:99-farm"), ResourceSiteKind.WHEAT_FIELD, slots);

        assertTrue(FrontierV3GrayboxExecutor.resourceSiteIngressMatches(new ChunkPos(9, 19), site),
                "a player at the adjacent farm shell is an ordinary field ingress");
        assertFalse(FrontierV3GrayboxExecutor.resourceSiteIngressMatches(new ChunkPos(2, 2), site),
                "a merely resident/generated remote chunk cannot project this COLD facility");
    }
}
