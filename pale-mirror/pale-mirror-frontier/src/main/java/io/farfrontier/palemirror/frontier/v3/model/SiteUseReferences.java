package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.land.SiteUseState;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.ForestState;
import io.farfrontier.palemirror.frontier.v3.model.forestry.ForestryRules;
import java.util.*;
/** Exact retained operation/right closure. Geometry never reconstructs a missing operator. */
final class SiteUseReferences {
    private static final ImmutableInvariantCheck<Void> RIGHTS=new ImmutableInvariantCheck<>(), TARGETS=new ImmutableInvariantCheck<>();
    private static final ImmutableRegistryView<SubjectId,ExtractionDeposit,SubjectId> MINES=
            new ImmutableRegistryView<>(SimulationCapacity.MAX_EXTRACTION_SITES,Objects::equals);
    static void validate(FrontierBootstrap bootstrap,SiteUseState rights,ResourceSiteState fields,ExtractionSiteState mines,ForestState forest) {
        var operators=MINES.project(bootstrap,mines.deposits(),(id,deposit)->deposit.site().settlementId());
        RIGHTS.validate(()->{
            for(var area:rights.areas().values()) for(var known:area.knownBy()) FrontierWorldStateSupport.settlement(bootstrap,known);
            for(var field:fields.declarations().values()) if(!rights.authorizes(new SiteUseState.Operation(SiteUseState.Kind.CULTIVATION,field.id()),field.settlementId()))
                throw new IllegalArgumentException("declared cultivation operation lacks its explicit use right");
            for(var mine:operators.entrySet()) if(!rights.authorizes(new SiteUseState.Operation(SiteUseState.Kind.EXTRACTION,mine.getKey()),mine.getValue()))
                throw new IllegalArgumentException("declared extraction operation lacks its explicit use right");
            for(var entry:rights.operations().entrySet()) for(var grant:entry.getValue()) {
                SubjectId home=switch(entry.getKey().kind()) {
                    case CULTIVATION -> Optional.ofNullable(fields.declarations().get(entry.getKey().id()))
                            .map(ResourceSiteDeclaration::settlementId).orElseThrow(()->new IllegalArgumentException("land use lost its cultivation operation"));
                    case EXTRACTION -> Optional.ofNullable(operators.get(entry.getKey().id())).orElseThrow(()->new IllegalArgumentException("land use lost its mining operation"));
                    case FORESTRY -> bootstrap.ruleset().forestry().patches().stream().filter(patch->new SubjectId(patch.id()).equals(entry.getKey().id()))
                            .map(ForestryRules.Patch::knownBy).findFirst().orElseThrow(()->new IllegalArgumentException("land use lost its forest operation"));
                };
                if(!home.equals(grant.holder()))throw new IllegalArgumentException("operation has a foreign site-use holder");
            }
        },bootstrap,rights,fields.declarations(),operators);
        TARGETS.validate(()->{
            for(var job:forest.work().values()) for(var target:job.target().stream().toList())
                if(!job.home().equals(target.use().holder()) || !rights.operations().getOrDefault(target.operation(),Set.of()).contains(target.use()))
                    throw new IllegalArgumentException("forestry continuation lost its exact site-use generation");
        },rights,forest.work());
    }
    private SiteUseReferences(){}
}
