package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rebuildable existence-only image of the scheduler's explicit accepted
 * subject surfaces. Membership never supplies a type, owner or dispatch key.
 * Values/health/positions do not retire IDs; nested operation/job IDs do. */
final class ScheduledSubjectExistence {
    private record Image(FrontierWorldState state, ImmutableDependencies dependencies,
                         List<Map<SubjectId, ?>> sources, Set<SubjectId> nested, Set<SubjectId> subjects) { }
    private static final ThreadLocal<Image> IMAGE = new ThreadLocal<>();
    private ScheduledSubjectExistence() { }
    static Set<SubjectId> subjects(FrontierWorldState state) {
        var old = IMAGE.get();
        if (old != null && old.state() == state) return old.subjects();
        List<Map<SubjectId, ?>> sources = List.of(state.actorLocations(), state.structureConditions(),
                state.inventory().items(), state.inventory().cargo(), state.inventory().fungibleResources().lots(),
                state.inventory().fungibleResources().claims(), state.productionJobs(), state.serviceWorks(),
                state.routeConstructions(), state.routeMaintenances(), state.resourceSites().sites(),
                state.extractionSites().deposits(), state.extractionSites().work(), state.productionStations().machines(),
                state.humanPopulation().residents(), state.humanPopulation().birthJobs(), state.humanPopulation().migrations(),
                state.humanPopulation().provisions(), state.hiveColony().growthJobs(), state.hiveColony().nutrientTransfers(),
                state.hiveColony().mobilizations(), state.strategicPlans().objectives(), state.strategicPlans().tasks(),
                state.strategicPlans().routePatrols(), state.strategicPlans().settlementAssaults(), state.companies().companies(),
                state.companies().market().demands(), state.companies().market().quotes(), state.companies().market().workOrders(),
                state.companies().goodsTrade().orders(), state.companies().goodsTrade().contracts(),
                state.shipments().shipments(), state.shipments().missions(), state.unitGroups().groups());
        Object[] inputs = new Object[sources.size() + 1]; inputs[0] = state.bootstrap();
        for (int index = 0; index < sources.size(); index++) inputs[index + 1] = sources.get(index);
        if (old != null && old.dependencies().matches(inputs)) {
            IMAGE.set(new Image(state, old.dependencies(), old.sources(), old.nested(), old.subjects()));
            return old.subjects();
        }
        var nested = old != null
                && old.state().productionStations().machines() == state.productionStations().machines()
                && old.state().resourceSites().sites() == state.resourceSites().sites()
                ? old.nested() : nestedSubjects(state);
        // Movement, hunger, stock quantities and work progress change values, not
        // existence. Inspect only changed source keysets before allocating another
        // whole-world union. Nested job/operation identities remain explicit inputs.
        if (old != null && old.state().bootstrap() == state.bootstrap() && old.nested().equals(nested)
                && sameKeys(old.sources(), sources)) {
            IMAGE.set(new Image(state, ImmutableDependencies.of(inputs), sources, nested, old.subjects()));
            return old.subjects();
        }
        var subjects = new HashSet<SubjectId>(); sources.forEach(source -> subjects.addAll(source.keySet()));
        var bootstrap = state.bootstrap(); subjects.add(bootstrap.hive().id());
        bootstrap.settlements().forEach(settlement -> {
            subjects.add(settlement.id()); settlement.residents().forEach(resident -> subjects.add(resident.id()));
        });
        bootstrap.hive().bioforms().forEach(actor -> subjects.add(actor.id()));
        bootstrap.hive().organs().forEach(organ -> subjects.add(organ.id()));
        subjects.addAll(nested);
        Set<SubjectId> result = old != null && old.subjects().equals(subjects)
                ? old.subjects() : Collections.unmodifiableSet(subjects);
        IMAGE.set(new Image(state, ImmutableDependencies.of(inputs), sources, nested, result));
        return result;
    }
    private static boolean sameKeys(List<Map<SubjectId, ?>> before, List<Map<SubjectId, ?>> after) {
        for (int i = 0; i < before.size(); i++) if (before.get(i) != after.get(i)
                && !before.get(i).keySet().equals(after.get(i).keySet())) return false;
        return true;
    }
    private static Set<SubjectId> nestedSubjects(FrontierWorldState state) {
        var subjects = new HashSet<SubjectId>();
        state.productionStations().machines().values().forEach(machine ->
                machine.operation().ifPresent(operation -> subjects.add(operation.id().value())));
        state.resourceSites().sites().values().forEach(site -> {
            subjects.addAll(site.harvestJobs().keySet()); site.preparationWork().ifPresent(work -> subjects.add(work.id()));
        });
        return Set.copyOf(subjects);
    }

}
