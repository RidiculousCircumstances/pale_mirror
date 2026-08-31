package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanPopulationProcessTest {
    @Test
    void bootstrapRegistersEveryExactResidentInBoundedHouseholdsAndRoundTripsIt() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:humans"), 91L));

        assertEquals(state.bootstrap().residentCount(), state.humanPopulation().residents().size());
        assertEquals(state.actorLocations().keySet().stream().filter(id -> id.value().startsWith("resident:")).count(), state.humanPopulation().residents().size());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test
    void starvationIsAnIndividualNutritionOutcomeWithoutChangingActorIdentityOrDiseaseState() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:nutrition-work"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst(); HumanPopulation population = state.humanPopulation();
        var workers = population.residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id()))
                .filter(resident -> resident.role() == ResidentRole.HAULER || resident.role() == ResidentRole.GUARD
                        || resident.role() == ResidentRole.FARMER || resident.role() == ResidentRole.CRAFTER).toList();
        for (int cycle = 1; cycle <= ResidentNutrition.STARVING_AFTER_MISSED_CYCLES; cycle++) {
            for (ResidentProfile worker : workers) population = population.resolveNutrition(worker.id(), cycle, false);
        }
        FrontierWorldState hungry = state.withHumanPopulation(population);

        assertTrue(workers.stream().allMatch(worker -> hungry.humanPopulation().nutrition(worker.id()).status() == ResidentNutritionStatus.STARVING));
        assertTrue(workers.stream().allMatch(worker -> hungry.actorLocations().get(worker.id()).condition().status() == ActorLifeStatus.ALIVE));
        assertTrue(workers.stream().allMatch(worker -> hungry.humanPopulation().health(worker.id()).status() == ResidentHealthStatus.HEALTHY));
        assertTrue(FrontierWorldStateSupport.availableRouteResident(hungry, settlement.id(), ResidentRole.HAULER).isEmpty());
        assertTrue(FrontierWorldStateSupport.availableRouteResident(hungry, settlement.id(), ResidentRole.GUARD).isEmpty());
        assertTrue(FrontierWorldStateSupport.availableFieldResident(hungry, settlement.id(), ResidentRole.FARMER).isEmpty());
        assertTrue(FrontierWorldStateSupport.availableWorkResident(hungry, settlement.id(), ResidentRole.CRAFTER).isEmpty());
        FrontierWorldState afterRecovery = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(hungry));
        assertTrue(FrontierWorldStateSupport.availableRouteResident(afterRecovery, settlement.id(), ResidentRole.HAULER).isEmpty());
        assertTrue(FrontierWorldStateSupport.availableFieldResident(afterRecovery, settlement.id(), ResidentRole.FARMER).isEmpty());

        for (ResidentProfile worker : workers) population = population.resolveNutrition(worker.id(), 4, true);
        FrontierWorldState recovered = state.withHumanPopulation(population);
        assertTrue(FrontierWorldStateSupport.availableRouteResident(recovered, settlement.id(), ResidentRole.HAULER).isPresent());
        assertTrue(FrontierWorldStateSupport.availableRouteResident(recovered, settlement.id(), ResidentRole.GUARD).isPresent());
        assertTrue(FrontierWorldStateSupport.availableFieldResident(recovered, settlement.id(), ResidentRole.FARMER).isPresent());
        assertTrue(FrontierWorldStateSupport.availableWorkResident(recovered, settlement.id(), ResidentRole.CRAFTER).isPresent());
    }

    @Test
    void exactBirthNeedsAConfirmedOwnedFoodReceiptAndCannotBeSubmittedDirectly() {
        WorldId world = new WorldId("frontier:human-events");
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(world, 91L));
        FrontierWorldState initial = state(engine);
        ResidentProfile existing = initial.humanPopulation().resident(new SubjectId("resident:1-1"));
        ResidentBorn forged = new ResidentBorn(new ResidentProfile(new SubjectId("resident:forged"), existing.householdId(), existing.settlementId(), ResidentRole.FARMER,
                0L, existing.skills()), initial.bootstrap().settlements().getFirst().anchor());
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(world, engine, "command:forged-birth", forged)));

        FrontierWorldState active = stateWithActiveBread(initial, initial.bootstrap().settlements().getFirst().id());
        ScheduledAction review = PopulationBirthProcess.review(active.bootstrap().settlements().getFirst().id(), 1, 100L);
        var proposed = PopulationBirthProcess.planReview(active, review);
        ResidentBirthStarted started = proposed.stream().map(event -> event.payload()).filter(ResidentBirthStarted.class::isInstance)
                .map(ResidentBirthStarted.class::cast).findFirst().orElseThrow();
        PhysicalIntentPrepared prepared = proposed.stream().map(event -> event.payload()).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        BlockPosition birthPosition = started.job().position();
        GrayboxCell birthFoot = FrontierGrayboxPlan.compile(active).cells().get(birthPosition);
        assertTrue(birthFoot == null || birthFoot.semanticPart() == GrayboxSemanticPart.ROUTE_SURFACE,
                "a new resident may use a route surface but must not be born inside structure geometry");
        assertEquals(null, FrontierGrayboxPlan.compile(active).cells().get(birthPosition.offset(0, 1, 0)));
        assertEquals(null, FrontierGrayboxPlan.compile(active).cells().get(birthPosition.offset(0, 2, 0)));
        assertTrue(active.actorLocations().values().stream().noneMatch(actor -> actor.position().equals(birthPosition)),
                "the next resident slot must not overlap an existing exact actor");
        assertEquals(started, FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
        active = PopulationBirthProcess.reduceStarted(active, started.job().settlementId(), started);
        active = PopulationBirthProcess.reducePrepared(active, started.job().settlementId(), prepared.intent());
        assertEquals(started.job(), active.humanPopulation().birthJobs().get(started.job().id()));
        assertEquals(active, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(active)));

        ExactItemConsumedObservation receipt = new ExactItemConsumedObservation(new PhysicalObservationId("observation:birth-food"), prepared.intent().id(),
                started.job().foodItemId(), 64, 63);
        active = active.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, java.util.Optional.empty());
        active = active.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt));
        var completion = PopulationBirthProcess.planCompletion(active, new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:resident-birth-complete-test"),
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(300L), 0, started.job().id(), "frontier.population.birth.complete", 1));
        ResidentBorn born = (ResidentBorn) completion.getFirst().payload();
        FrontierWorldState completed = PopulationBirthProcess.reduceBorn(active, started.job().settlementId(), born);
        assertEquals(born.resident(), completed.humanPopulation().resident(born.resident().id()));
        assertEquals(born.position(), completed.actorLocations().get(born.resident().id()).position());
        assertEquals(63, completed.inventory().items().get(started.job().foodItemId()).count());
        assertTrue(completed.humanPopulation().birthJobs().isEmpty());
        assertEquals(born, FrontierWorldRuntimeDefinition.payloadCodecs().decode(born.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(born)));
    }

    @Test
    void birthReviewWaitsWithoutCreatingAHiddenPopulationWhenNoOwnedActiveFoodExists() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:birth-no-food"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        var proposed = PopulationBirthProcess.planReview(state, PopulationBirthProcess.review(settlement.id(), 1, 100L));
        assertEquals(1, proposed.size());
        assertTrue(proposed.getFirst().payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created);
        assertTrue(state.humanPopulation().birthJobs().isEmpty());
    }

    @Test
    void schedulerAndPhysicalTransitionAdmitExactlyOneResidentOnlyAfterTheConfirmedStackReceipt() {
        WorldId world = new WorldId("frontier:birth-scheduled");
        var base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        FrontierWorldState active = stateWithActiveBread(base.initialState(), base.initialState().bootstrap().settlements().getFirst().id());
        SubjectId settlement = active.bootstrap().settlements().getFirst().id();
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(world, active, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                java.util.List.of(PopulationBirthProcess.review(settlement, 1, 100L)), base.transactionCommitter());
        var engine = FrontierEngines.create(configuration);
        engine.advanceTo(new SimInstant(100L), new WorkBudget(16, 64));
        FrontierWorldState permitted = state(engine);
        ResidentBirthJob job = permitted.humanPopulation().birthJobs().values().stream().findFirst().orElseThrow();
        assertTrue(permitted.humanPopulation().resident(job.resident().id()) == null);

        PhysicalIntentTransition running = new PhysicalIntentTransition(job.consumptionIntentId(), PhysicalIntentStatus.RUNNING, java.util.Optional.empty());
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(world, engine, "command:birth-running", running)));
        ExactItemConsumedObservation receipt = new ExactItemConsumedObservation(new PhysicalObservationId("observation:birth-scheduled"), job.consumptionIntentId(), job.foodItemId(), 64, 63);
        PhysicalIntentTransition confirmed = new PhysicalIntentTransition(job.consumptionIntentId(), PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt));
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(world, engine, "command:birth-confirmed", confirmed)));
        assertTrue(state(engine).humanPopulation().resident(job.resident().id()) == null);

        engine.advanceTo(new SimInstant(300L), new WorkBudget(16, 64));
        FrontierWorldState born = state(engine);
        assertEquals(job.resident(), born.humanPopulation().resident(job.resident().id()));
        assertEquals(63, born.inventory().items().get(job.foodItemId()).count());
    }

    @Test
    void unknownFoodEffectReleasesThePermitWithoutInventingAResidentOrDiscardingFood() {
        FrontierWorldState state = stateWithActiveBread(FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:birth-unknown"), 91L)),
                new SubjectId("settlement:1"));
        ScheduledAction review = PopulationBirthProcess.review(new SubjectId("settlement:1"), 1, 100L);
        var planned = PopulationBirthProcess.planReview(state, review);
        ResidentBirthStarted started = planned.stream().map(event -> event.payload()).filter(ResidentBirthStarted.class::isInstance)
                .map(ResidentBirthStarted.class::cast).findFirst().orElseThrow();
        PhysicalIntentPrepared prepared = planned.stream().map(event -> event.payload()).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        state = PopulationBirthProcess.reduceStarted(state, started.job().settlementId(), started);
        state = PopulationBirthProcess.reducePrepared(state, started.job().settlementId(), prepared.intent());
        state = state.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, java.util.Optional.empty());
        PhysicalIntentTransition unknown = new PhysicalIntentTransition(prepared.intent().id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty());
        var outcome = PopulationBirthProcess.planTransition(state, prepared.intent(), unknown, 110L);
        state = state.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty());
        ResidentBirthCancelled cancelled = outcome.stream().map(event -> event.payload()).filter(ResidentBirthCancelled.class::isInstance)
                .map(ResidentBirthCancelled.class::cast).findFirst().orElseThrow();
        state = PopulationBirthProcess.reduceCancelled(state, started.job().settlementId(), cancelled);
        assertTrue(state.humanPopulation().birthJobs().isEmpty());
        assertTrue(state.humanPopulation().resident(started.job().resident().id()) == null);
        assertTrue(state.inventory().items().containsKey(started.job().foodItemId()));
    }

    @Test
    void housingIsAPhysicalCapacityGateRatherThanASecondMutablePopulationCounter() {
        WorldId world = new WorldId("frontier:housing-capacity");
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SettlementStructure housing = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.HOUSING).findFirst().orElseThrow();

        assertEquals(SettlementFacilityCapability.INTACT_HOUSING_BEDS, SettlementFacilityCapability.housingCapacity(state, settlement.id()));
        assertEquals(settlement.residents().size(), SettlementFacilityCapability.livingResidents(state, settlement.id()));
        assertEquals(SettlementFacilityCapability.DAMAGED_HOUSING_BEDS,
                SettlementFacilityCapability.forCondition(StructureKind.HOUSING, StructureCondition.DAMAGED).residentCapacity());

        FrontierWorldState destroyed = state.withStructureCondition(housing.id(), StructureCondition.DESTROYED);
        assertEquals(0, SettlementFacilityCapability.housingCapacity(destroyed, settlement.id()));
        ResidentProfile parent = destroyed.humanPopulation().resident(settlement.residents().getFirst().id());
        ResidentProfile newborn = new ResidentProfile(new SubjectId("resident:1-housing-blocked"), parent.householdId(), settlement.id(),
                ResidentRole.FARMER, 0L, parent.skills());
        ResidentBirthJob permit = new ResidentBirthJob(new SubjectId("job:resident-birth-housing-blocked"), settlement.id(), parent.householdId(),
                new SubjectId("item:bootstrap-1-wheat"), new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:resident-birth-housing-blocked"), newborn, settlement.anchor());
        assertThrows(IllegalArgumentException.class, () -> destroyed.startResidentBirth(permit));

        FrontierObjectBoard board = FrontierReadabilityPlan.compile(state).boards().get(housing.id());
        assertTrue(board.text().contains(settlement.residents().size() + " / " + SettlementFacilityCapability.INTACT_HOUSING_BEDS + " RESIDENTS"));
    }

    private static FrontierWorldState state(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine) {
        return new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
    }

    private static FrontierWorldState stateWithActiveBread(FrontierWorldState state, SubjectId settlementId) {
        SubjectId depot = FrontierWorldState.depotId(settlementId); SubjectId bread = new SubjectId("item:birth-test-bread");
        ExactInventory inventory = state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(bread, settlementId, PopulationBirthProcess.BREAD, 64, new InventoryCustody.ContainerSlot(depot, 1)));
        return state.withInventory(inventory);
    }
    private static FrontierCommand command(WorldId world, io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine,
                                           String id, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        CommandId command = new CommandId(id); var checkpoint = engine.checkpoint();
        return new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(command), payload);
    }
}
