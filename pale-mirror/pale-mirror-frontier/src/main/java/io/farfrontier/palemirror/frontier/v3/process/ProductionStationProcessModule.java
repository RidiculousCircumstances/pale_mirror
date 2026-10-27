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
        if (bootstrap.ruleset().production().recipes().stream().noneMatch(recipe -> recipe.requiresCombustion()
                || recipe.processingMode() == ProductionRecipe.ProcessingMode.AUTOMATIC)) return List.of();
        return bootstrap.settlements().stream().flatMap(home -> home.structures().stream()).filter(facility -> ProductionFacilityLayout.supports(facility.kind()))
                .map(ProductionFacilityLayout::station).map(station -> review(station.id(), 1)).toList();
    }
    static FrontierScheduledPlanner planner() {
        return new FrontierScheduledPlanner() {
            @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
                throw new IllegalArgumentException("station review requires its execution instant");
            }
            @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, SimInstant instant) {
                return ProductionStationProcessModule.plan(state, action, instant);
            }

            @Override public boolean held(FrontierWorldState state, ScheduledAction action) {
                return waitingForOperation(state, action);
            }
            @Override public Set<SubjectId> wakeDependencies(FrontierWorldState state, ScheduledAction action) {
                return Set.of(action.subject(), ProductionStationEnergy.record(state, action.subject()).id());
            }
            @Override public Optional<ScheduledHold<FrontierWorldState>> holdProof(FrontierWorldState state, ScheduledAction action) {
                if (!waitingForOperation(state, action)) return Optional.empty();
                var record = ProductionStationEnergy.record(state, action.subject());
                var inputs = ImmutableDependencies.of(state.bootstrap(), state.productionStations().machines().get(action.subject()),
                        record, state.inventory().surfaces().get(record.id()), inputAccount(state, record.id()));
                return Optional.of(new ScheduledHold<>(action, wakeDependencies(state, action), current ->
                        inputs.matches(current.bootstrap(), current.productionStations().machines().get(action.subject()),
                                current.inventory().containers().get(record.id()), current.inventory().surfaces().get(record.id()),
                                inputAccount(current, record.id()))));
            }
        };
    }
    /** Neither an absent operator-owned operation nor an empty automatic input
     * can become available merely by advancing time. Their exact owners wake them. */
    private static boolean waitingForOperation(FrontierWorldState state, ScheduledAction action) {
        var machine = state.productionStations().machines().get(action.subject());
        if (machine == null || machine.operation().isPresent()) return false;
        var record = ProductionStationEnergy.record(state, action.subject());
        if (state.inventory().surfaces().get(record.id()).status() == ContainerSurfaceStatus.RETIRED) return false;
        var input = inputAccount(state, record.id());
        return AutomaticStationProduction.policy(state, record.productionStation().orElseThrow()).isEmpty()
                || input == null || input.lotQuantities().isEmpty();
    }
    private static CustodyAccount inputAccount(FrontierWorldState state, SubjectId container) {
        return FungibleResourceCustodySupport.accountAtContainer(state, container, ContainerSectionId.INPUT).orElse(null);
    }
    /** Machine mutations may be caused by a job or inventory operation with another
     * event subject. The machine owner supplies its exact changed wake identities. */
    static Set<SubjectId> wakeKeys(FrontierWorldState previous, FrontierWorldState next) {
        var before = previous.productionStations().machines(); var after = next.productionStations().machines();
        var keys = new HashSet<SubjectId>();
        if (before != after) {
            before.forEach((id, machine) -> { if (!Objects.equals(machine, after.get(id))) keys.add(id); });
            after.forEach((id, machine) -> { if (!before.containsKey(id)) keys.add(id); });
        }
        if (previous.inventory().fungibleResources().accounts() != next.inventory().fungibleResources().accounts())
            after.forEach((id, machine) -> {
                var container = ProductionStationEnergy.record(next, id).id();
                if (inputAccount(previous, container) != inputAccount(next, container)) keys.add(id);
            });
        return Set.copyOf(keys);
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
        var fuel = ProductionStationEnergy.next(state, action.subject(), now.ticks()).filter(burn -> burn.authorityEpoch() == 0);
        if (fuel.isPresent()) return List.of(new ProposedEvent(action.subject(), new ProductionStationFuelChanged(
                ProductionStationFuelChanged.Stage.COLD_CONSUMED, fuel.orElseThrow(), List.of(), now.ticks())),
                new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(), review(action.subject(), Math.addExact(now.ticks(), 1L)))));
        ProductionStationProgressed.automatic(state, action.subject(), now.ticks()).ifPresent(progress ->
                events.add(new ProposedEvent(action.subject(), progress)));
        ProductionStationTransformation.next(state, action.subject()).filter(boundary -> boundary.authorityEpoch() == 0).ifPresent(boundary ->
                events.add(new ProposedEvent(action.subject(), new ProductionStationRecipeChanged(
                        ProductionStationRecipeChanged.Stage.COLD_COMPLETED, boundary, List.of()))));
        events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(),
                review(action.subject(), nextReview(state, action.subject(), now.ticks(), events)))));
        return List.copyOf(events);
    }
    /** A retained work/burn/attendance boundary is exact. Only an unknown resource/physical wait uses the bounded retry. */
    private static long nextReview(FrontierWorldState state, SubjectId station, long now, List<ProposedEvent> events) {
        var progress = events.stream().map(ProposedEvent::payload).filter(ProductionStationProgressed.class::isInstance)
                .map(ProductionStationProgressed.class::cast).map(ProductionStationProgressed::next).findFirst()
                .or(() -> state.productionStations().require(station).operation().flatMap(ProductionOperation::progress));
        if (progress.filter(WorkProgress::complete).isPresent()) return Math.addExact(now, 1);
        return progress.filter(WorkProgress::running).filter(clock -> clock.activeUntilTick() > now)
                .map(WorkProgress::activeUntilTick).orElseGet(() -> Math.addExact(now, 20));
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
                    if (changed.atTick() > command.submittedAt().ticks()) return FrontierWorldCommandPlanner.rejected("fuel receipt is from a future simulation instant");
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
