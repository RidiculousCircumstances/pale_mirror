package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Existence only: accepted surfaces are explicit, never inferred owners or dispatch.
 * Publication queries exact membership and only possible removals. Ordinary changes
 * do not build or difference a union of every subject in the world. */
final class ScheduledSubjectExistence {
    private record Image(FrontierWorldState state, List<Map<SubjectId, ?>> sources,
                         Set<SubjectId> fixed, Set<SubjectId> nested) { }
    private static final ThreadLocal<Image> IMAGE = new ThreadLocal<>();
    private static final ImmutableInputView<Set<SubjectId>> FIXED = new ImmutableInputView<>();
    private static final ImmutableInputView<Set<SubjectId>> NESTED = new ImmutableInputView<>();
    private static final ImmutableInputView<Set<SubjectId>> STATION_OPERATIONS = new ImmutableInputView<>();
    private static final ImmutableRegistryView<SubjectId, ResourceSiteLifecycle, Set<SubjectId>> SITE_SUBJECTS =
            new ImmutableRegistryView<>(ResourceSiteState.MAX_SITES, Objects::equals);
    private ScheduledSubjectExistence() { }
    static boolean contains(FrontierWorldState state, SubjectId id) { return contains(image(state), id); }
    private static boolean contains(Image image, SubjectId id) {
        if (image.fixed().contains(id) || image.nested().contains(id)) return true;
        for (var source : image.sources()) if (source.containsKey(id)) return true;
        return false;
    }
    static Set<SubjectId> removed(FrontierWorldState before, FrontierWorldState after) {
        if (before == after) return Set.of();
        var old = image(before); var next = image(after); var candidates = new HashSet<SubjectId>();
        for (int index = 0; index < old.sources().size(); index++)
            ImmutableMapChanges.forEachRemovedKey(old.sources().get(index), next.sources().get(index), candidates::add);
        if (old.fixed() != next.fixed()) for (var id : old.fixed()) if (!next.fixed().contains(id)) candidates.add(id);
        if (old.nested() != next.nested()) for (var id : old.nested()) if (!next.nested().contains(id)) candidates.add(id);
        // The same spelling can remain on another accepted surface. Withdrawal
        // from one map alone does not mean the canonical subject was retired.
        candidates.removeIf(id -> contains(next, id));
        return Set.copyOf(candidates);
    }
    private static Image image(FrontierWorldState state) {
        var old = IMAGE.get();
        if (old != null && old.state() == state) return old;
        var fixed = FIXED.get(List.of(state.bootstrap()), () -> {
            var ids = new HashSet<SubjectId>(); var bootstrap = state.bootstrap();
            ids.add(bootstrap.hive().id());
            bootstrap.settlements().forEach(settlement -> {
                ids.add(settlement.id()); settlement.residents().forEach(resident -> ids.add(resident.id()));
            });
            bootstrap.hive().bioforms().forEach(actor -> ids.add(actor.id()));
            bootstrap.hive().organs().forEach(organ -> ids.add(organ.id()));
            return Set.copyOf(ids);
        });
        var operations = STATION_OPERATIONS.getPublished(List.of(state.productionStations().machines()), () -> {
            var ids = new HashSet<SubjectId>();
            state.productionStations().machines().values().forEach(machine ->
                    machine.operation().ifPresent(operation -> ids.add(operation.id().value())));
            return Set.copyOf(ids);
        });
        var siteSubjects = SITE_SUBJECTS.project(state.bootstrap(), state.resourceSites().sites(), (id, site) -> {
            var ids = new HashSet<>(site.harvestJobs().keySet());
            site.preparationWork().ifPresent(work -> ids.add(work.id()));
            return Set.copyOf(ids);
        });
        var nested = NESTED.get(List.of(operations, siteSubjects), () -> {
            var ids = new HashSet<>(operations); siteSubjects.values().forEach(ids::addAll);
            return Set.copyOf(ids);
        });
        var next = new Image(state, sources(state), fixed, nested); IMAGE.set(next); return next;
    }
    private static List<Map<SubjectId, ?>> sources(FrontierWorldState state) {
        return List.of(state.humanPopulation().clinical().tasks(),state.actorLocations(), state.structureConditions(),
                state.inventory().items(), state.inventory().cargo(), state.inventory().fungibleResources().lots(),
                state.inventory().fungibleResources().claims(), state.productionJobs(), state.serviceWorks(),
                state.routeConstructions(), state.routeMaintenances(), state.resourceSites().sites(),
                state.extractionSites().deposits(), state.extractionSites().work(), state.forest().work(), state.productionStations().machines(),
                state.humanPopulation().residents(), state.humanPopulation().birthJobs(), state.humanPopulation().migrations(),
                state.humanPopulation().provisions(), state.hiveColony().growthJobs(), state.hiveColony().nutrientTransfers(),
                state.hiveColony().mobilizations(), state.strategicPlans().objectives(), state.strategicPlans().tasks(),
                state.strategicPlans().routePatrols(), state.strategicPlans().settlementAssaults(), state.strategicPlans().guards(), state.companies().companies(),
                state.companies().market().demands(), state.companies().market().quotes(), state.companies().market().workOrders(),
                state.companies().goodsTrade().orders(), state.companies().goodsTrade().contracts(),
                state.shipments().shipments(), state.shipments().missions(), state.unitGroups().groups());
    }
}
