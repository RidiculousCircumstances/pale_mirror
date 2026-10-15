package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState;
import java.util.Map;

/** Read-only declarations from the registered geometry owners, independent of loaded chunks. */
public final class KnownNavigationBlocks {
    private KnownNavigationBlocks() { }
    public static Map<BlockPosition, NavigationBlockState> forState(FrontierWorldState state) {
        return KnownSiteGeometry.forState(state).retainedBlocks();
    }
}
