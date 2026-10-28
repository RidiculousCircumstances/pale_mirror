package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Map;

/** Declared layout only: plant biology, terrain collisions and access permissions
 * remain separate current facts. This directory grants no physical clearance. */
final class ResourceSiteLayoutDirectory {
    private static final ImmutableRegistryView<SubjectId, ResourceFieldCycle, ResourceFieldLayout> ROWS =
            new ImmutableRegistryView<>(ResourceSiteState.MAX_SITES, 4);
    private static final ImmutableInputView<Map<SubjectId, ResourceFieldLayout>> VIEW = new ImmutableInputView<>();
    static Map<SubjectId, ResourceFieldLayout> layouts(ResourceSiteState sites) {
        return VIEW.get(List.of(sites.declarations(), sites.cycles()), () ->
                ROWS.project(sites.declarations(), sites.cycles(), (id, cycle) -> cycle.layout()));
    }
    private ResourceSiteLayoutDirectory() { }
}
