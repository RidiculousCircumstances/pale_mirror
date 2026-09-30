package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMealReferenceClosure;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;

import java.io.DataInputStream;
import java.io.IOException;

/** Fail-closed post-hydration closure, separate from the versioned field decoder. */
final class FrontierWorldStateCodecValidation {
    private FrontierWorldStateCodecValidation() { }

    static void validate(DataInputStream input, FrontierWorldState state) throws IOException {
        if (input.available() != 0) throw new IllegalArgumentException("trailing Frontier v3 state bytes");
        FrontierWorldProcessCatalog.requirePhysicalLifecycleState(state);
        FrontierDomainRelationships.validate(state);
        ResidentMealReferenceClosure.validate(state);
        FrontierDurationProcessDriverRegistry.requireRetainedSceneLeases(state.sceneLeases().values());
    }
}
