package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.land.SiteUseState;
import java.util.*;
/** Composition of family-owned obligations. The coverage algorithm does not inspect work phases. */
public final class ProtectionObligations {
    public interface Port { List<ProtectedTarget> read(FrontierWorldState state, SubjectId home); }
    private static final ImmutableKeyedInputView<SubjectId,List<ProtectedTarget>> VIEW=new ImmutableKeyedInputView<>(SimulationCapacity.MAX_SETTLEMENTS);
    private static final List<Port> PORTS = List.of(new Town(), new Fields(), new Mines(), new Forests());
    public static List<ProtectedTarget> read(FrontierWorldState state, SubjectId home) {
        var inputs=new ArrayList<Object>(List.of(state.siteUses(),state.structureConditions(),state.resourceSiteDescriptors()));
        state.extractionSites().depositsIn(home).forEach(deposit->inputs.add(deposit.site()));
        return VIEW.getPublished(state.bootstrap(),home,inputs,()->PORTS.stream().flatMap(port -> port.read(state, home).stream())
                .sorted(Comparator.comparingInt(ProtectedTarget::importance).reversed()
                        .thenComparing(target -> target.kind().wireTag()).thenComparing(ProtectedTarget::id)).toList());
    }
    private static final class Town implements Port {
        public List<ProtectedTarget> read(FrontierWorldState state, SubjectId home) {
            var town = FrontierWorldStateSupport.settlement(state.bootstrap(), home);
            // A damaged asset still requires protection. Retirement of its declared plot,
            // rather than cosmetic integrity, removes this settlement obligation.
            return town.structures().stream()
                    .map(facility -> new ProtectedTarget(ProtectedTarget.Kind.SETTLEMENT, facility.id(), home,
                            new SurfaceAnchor(facility.anchor()), 3)).toList();
        }
    }
    private static final class Fields implements Port {
        public List<ProtectedTarget> read(FrontierWorldState state, SubjectId home) {
            return state.resourceSiteDescriptors().values().stream().filter(field -> field.settlementId().equals(home))
                    .filter(field -> !field.layout().cells().isEmpty())
                    .filter(field -> state.siteUses().permits(new SiteUseState.Operation(SiteUseState.Kind.CULTIVATION,field.id()),home, field.layout().cells().getFirst().crop()))
                    .map(field -> new ProtectedTarget(ProtectedTarget.Kind.CULTIVATION_SITE, field.id(), home,
                            fieldCentre(field), 2)).toList();
        }
    }
    private static SurfaceAnchor fieldCentre(ResourceSite field) {
        long x=field.layout().cells().stream().mapToLong(cell->cell.crop().x()).sum()/field.layout().cells().size();
        long z=field.layout().cells().stream().mapToLong(cell->cell.crop().z()).sum()/field.layout().cells().size();
        return field.layout().cells().stream().min(Comparator.comparingLong(cell->Math.abs(cell.crop().x()-x)+Math.abs(cell.crop().z()-z)))
                .orElseThrow().workstation();
    }
    private static final class Mines implements Port {
        public List<ProtectedTarget> read(FrontierWorldState state, SubjectId home) {
            return state.extractionSites().depositsIn(home).stream()
                    .filter(deposit -> state.siteUses().permits(new SiteUseState.Operation(SiteUseState.Kind.EXTRACTION,deposit.site().id()),home, deposit.site().layout().entrance().support()))
                    .map(deposit -> new ProtectedTarget(ProtectedTarget.Kind.EXTRACTION_SITE, deposit.site().id(), home,
                            deposit.site().layout().entrance(), 2)).toList();
        }
    }
    private static final class Forests implements Port {
        public List<ProtectedTarget> read(FrontierWorldState state, SubjectId home) {
            return state.siteUses().held(home).stream().filter(area -> area.kind() == SiteUseState.Kind.FORESTRY)
                    .map(area -> { var min=area.extent().min(); var max=area.extent().max(); int x=min.x()+(max.x()-min.x())/2;
                        int z=min.z()+(max.z()-min.z())/2;
                        // A forest is an annulus around its origin; the watch point belongs inside the patch, not in town.
                        x += (max.x()-min.x())/3;
                        return new ProtectedTarget(ProtectedTarget.Kind.FOREST_AREA, area.id(), home,
                                new SurfaceAnchor(new BlockPosition(x, state.bootstrap().terrain().supportYAt(x,z), z)), 1); }).toList();
        }
    }
    private ProtectionObligations() { }
}
