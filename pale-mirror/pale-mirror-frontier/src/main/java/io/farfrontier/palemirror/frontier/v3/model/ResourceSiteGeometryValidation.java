package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Map;

/** Field-owned geometry closure. The common indexes select changed declarations
 * and detect exact shared positions, never land admission or work authority. */
final class ResourceSiteGeometryValidation {
    private record Inputs(FrontierBootstrap bootstrap, Map<SubjectId, ResourceFieldLayout> layouts,
                          Map<SubjectId, ResourceSite> authored) { }
    private static final ImmutableReferenceIndex.Source<Inputs, SubjectId, ResourceFieldLayout> LAYOUTS =
            new ImmutableReferenceIndex.Source<>(Inputs::layouts);
    private static final ImmutableReferenceIndex<Inputs, SubjectId, ResourceSiteDeclaration> SITES =
            new ImmutableReferenceIndex<>(ResourceSiteState.MAX_SITES, 1, List.of(LAYOUTS),
                    (inputs, site) -> List.of(LAYOUTS.at(site.id())), (inputs, id, site) -> validateSite(inputs, site));
    private static final ImmutableGroupView<SubjectId, ResourceFieldLayout, BlockPosition> POSITIONS = new ImmutableGroupView<>();
    private static final ImmutableInvariantCheck<Void> AUTHORED = new ImmutableInvariantCheck<>();
    private ResourceSiteGeometryValidation() { }
    static void validate(FrontierBootstrap bootstrap, Map<SubjectId, ResourceSiteDeclaration> declarations,
                         Map<SubjectId, ResourceFieldLayout> layouts) {
        var authored = FrontierResourceSitePlan.compile(bootstrap);
        AUTHORED.validate(() -> {
            if (!declarations.keySet().containsAll(authored.keySet()))
                throw new IllegalArgumentException("resource-site state lost an initial declared field");
        }, bootstrap, declarations);
        SITES.validate(bootstrap, declarations, new Inputs(bootstrap, layouts, authored));
        if (!POSITIONS.uniqueMemberships(bootstrap, layouts, ResourceFieldLayout::managedPositions))
            throw new IllegalArgumentException("field geometry overlaps another field or leaves world bounds");
    }
    private static void validateSite(Inputs inputs, ResourceSiteDeclaration declaration) {
        var initial = inputs.authored().get(declaration.id());
        if (declaration.land().kind() == FieldLandOrigin.Kind.AUTHORED
                && (initial == null || !declaration.equals(ResourceSiteDeclaration.of(initial)))
                || initial != null && !declaration.equals(ResourceSiteDeclaration.of(initial)))
            throw new IllegalArgumentException("field declaration forged its authored land authority");
        Settlement settlement = FrontierWorldStateSupport.settlement(inputs.bootstrap(), declaration.settlementId());
        SettlementStructure facility = FrontierWorldStateSupport.structure(settlement, declaration.facilityId());
        if (facility.kind() != StructureKind.FARM || inputs.bootstrap().ruleset().fields().crops().stream()
                .noneMatch(policy -> policy.kind() == declaration.kind()))
            throw new IllegalArgumentException("resource-site lifecycle must bind an owning farm");
        var layout = inputs.layouts().get(declaration.id());
        if (layout == null) throw new IllegalArgumentException("field geometry lost its declared layout");
        declaration.land().ground().keySet().forEach(layout::requireCell);
        for (BlockPosition position : layout.managedSlots())
            if (!inputs.bootstrap().bounds().contains(position))
                throw new IllegalArgumentException("field geometry overlaps another field or leaves world bounds");
    }
}
