package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Registered machine owner, independent of bakery worker/task selection. */
final class ProductionStationProcessModule implements FrontierWorldProcessModule {
    static final String REVIEW = "frontier.production_station.review";
    static final Set<String> TYPES = Set.of(ProductionStationFuelChanged.TYPE, ProductionStationProgressed.TYPE,
            ProductionStationOperatorChanged.TYPE, ProductionStationRecipeChanged.TYPE, ProductionStationInstalled.TYPE, ProductionStationRetired.TYPE,
            ProductionStationAutomaticStarted.TYPE);
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor("production-stations",
            TYPES, Set.of(REVIEW), TYPES, Set.of(ProductionStationFuelChanged.TYPE, ProductionStationProgressed.TYPE,
                    ProductionStationOperatorChanged.TYPE, ProductionStationRecipeChanged.TYPE, ProductionStationInstalled.TYPE, ProductionStationRetired.TYPE,
                    ProductionStationAutomaticStarted.TYPE, "frontier.reference_mutation_closed",
                    "kernel.schedule_rescheduled", "kernel.schedule_created", "kernel.schedule_cancelled"), TYPES);
    static ScheduledAction review(SubjectId id, long at) {
        return new ScheduledAction(new ScheduleId("schedule:station-" + UUID.nameUUIDFromBytes(
                id.value().getBytes(java.nio.charset.StandardCharsets.UTF_8))), new SimInstant(at), 8, id, REVIEW, 1);
    }
    @Override public List<ScheduledAction> initialSchedules(FrontierBootstrap bootstrap) {
        if (bootstrap.ruleset().production().recipes().stream().noneMatch(recipe -> recipe.heatPerBatch() > 0
                || recipe.processingMode() == ProductionRecipe.ProcessingMode.AUTOMATIC)) return List.of();
        return bootstrap.settlements().stream().flatMap(home -> home.structures().stream()).filter(facility -> ProductionFacilityLayout.supports(facility.kind()))
                .map(ProductionFacilityLayout::station).map(station -> review(station.id(), 1)).toList();
    }
    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, SimInstant now) {
        if (!review(action.subject(), action.dueAt().ticks()).equals(action)) throw new IllegalArgumentException("foreign station review schedule");
        if (state.inventory().surfaces().get(ProductionStationEnergy.record(state, action.subject()).id()).status() == ContainerSurfaceStatus.RETIRED)
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())),
                    new ProposedEvent(action.subject(), new ProductionStationRetired(action.subject(), state.productionStations().require(action.subject()).revision())));
        var events = new ArrayList<ProposedEvent>();
        var automatic = AutomaticStationProduction.offer(state, action.subject(), now.ticks());
        if (automatic.isPresent()) {
            events.add(new ProposedEvent(action.subject(), new ProductionStationAutomaticStarted(action.subject(),
                    state.productionStations().require(action.subject()).revision(), automatic.orElseThrow().operation().recipeId(), now.ticks())));
            events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(), review(action.subject(), Math.addExact(now.ticks(), 20)))));
            return List.copyOf(events);
        }
        ProductionStationEnergy.next(state, action.subject()).filter(burn -> burn.authorityEpoch() == 0).ifPresent(burn ->
                events.add(new ProposedEvent(action.subject(), new ProductionStationFuelChanged(
                        ProductionStationFuelChanged.Stage.COLD_CONSUMED, burn, List.of()))));
        ProductionStationProgressed.automatic(state, action.subject(), now.ticks()).ifPresent(progress ->
                events.add(new ProposedEvent(action.subject(), progress)));
        ProductionStationTransformation.next(state, action.subject()).filter(boundary -> boundary.authorityEpoch() == 0).ifPresent(boundary ->
                events.add(new ProposedEvent(action.subject(), new ProductionStationRecipeChanged(
                        ProductionStationRecipeChanged.Stage.COLD_COMPLETED, boundary, List.of()))));
        events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(),
                review(action.subject(), Math.addExact(Math.max(now.ticks(), action.dueAt().ticks()), 20)))));
        return List.copyOf(events);
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case ProductionStationFuelChanged changed -> ProductionStationEnergy.apply(state, event.subject(), changed);
            case ProductionStationAutomaticStarted started -> AutomaticStationProduction.start(state, event.subject(), started);
            case ProductionStationProgressed changed -> ProductionStationProgressed.apply(state, event.subject(), changed);
            case ProductionStationOperatorChanged changed -> ProductionStationOperator.apply(state, event.subject(), changed);
            case ProductionStationRecipeChanged changed -> ProductionStationTransformation.apply(state, event.subject(), changed,
                    ProductionOperationCompletionComposition::completed);
            case ProductionStationInstalled installed -> installed.apply(state, event.subject(), ProductionInstallationComposition::installed);
            case ProductionStationRetired retired -> retired.apply(state, event.subject());
            default -> throw new IllegalArgumentException("machine owner rejects undeclared event");
        };
    }
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (!command.actor().equals(io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR))
            return FrontierWorldCommandPlanner.rejected("station observation requires the physical executor");
        try {
            SubjectId subject;
            switch (command.payload()) {
                case ProductionStationFuelChanged changed -> {
                    if (changed.stage() == ProductionStationFuelChanged.Stage.COLD_CONSUMED)
                        return FrontierWorldCommandPlanner.rejected("physical executor cannot consume COLD fuel");
                    subject = changed.burn().stationId();
                    var next = ProductionStationEnergy.apply(state, subject, changed);
                    if (changed.stage() == ProductionStationFuelChanged.Stage.HOT_CONSUMED)
                        return confirmed(state, next, command, subject);
                }
                case ProductionStationOperatorChanged changed -> {
                    if (changed.atTick() != command.submittedAt().ticks())
                        return FrontierWorldCommandPlanner.rejected("station attendance has no current simulation instant");
                    subject = changed.stationId(); ProductionStationOperator.apply(state, subject, changed);
                }
                case ProductionStationRecipeChanged changed -> {
                    if (changed.stage() == ProductionStationRecipeChanged.Stage.COLD_COMPLETED)
                        return FrontierWorldCommandPlanner.rejected("physical executor cannot perform a COLD recipe");
                    subject = changed.boundary().stationId();
                    var next = ProductionStationTransformation.apply(state, subject, changed,
                            ProductionOperationCompletionComposition::completed);
                    if (changed.stage() == ProductionStationRecipeChanged.Stage.HOT_COMPLETED)
                        return confirmed(state, next, command, subject);
                }
                case ProductionStationInstalled installed -> {
                    subject = installed.container().productionStation().orElseThrow().id();
                    installed.apply(state, subject, ProductionInstallationComposition::installed);
                    return new CommandPlan.Accepted(List.of(new ProposedEvent(subject, installed),
                            new ProposedEvent(subject, new ScheduleEffect.Created(review(subject, Math.addExact(command.submittedAt().ticks(), 1))))));
                }
                default -> { return FrontierWorldCommandPlanner.rejected("station command is not a physical observation"); }
            }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(subject, command.payload())));
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }
    private static CommandPlan confirmed(FrontierWorldState before, FrontierWorldState next,
            FrontierCommand command, SubjectId station) {
        var events = new ArrayList<ProposedEvent>(); events.add(new ProposedEvent(station, command.payload()));
        events.addAll(ConfirmedContainerMutationPlan.close(next,
                List.of(ProductionStationEnergy.record(before, station).id()), command.expectedRevision().next().value()));
        return new CommandPlan.Accepted(List.copyOf(events));
    }
}
