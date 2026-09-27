package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
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
        for (Settlement settlement : state.bootstrap().settlements()) {
            assertEquals(1L, state.humanPopulation().residents().values().stream()
                    .filter(resident -> resident.settlementId().equals(settlement.id()) && resident.profession() == ResidentProfession.BAKER).count());
            assertTrue(state.humanPopulation().residents().values().stream()
                    .anyMatch(resident -> resident.settlementId().equals(settlement.id())
                            && resident.profession() == ResidentProfession.INDUSTRIAL_WORKER));
        }
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
    void exactBirthCommitsCanonicalFoodBeforeItsScheduledResidentAdmission() {
        WorldId world = new WorldId("frontier:human-events");
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(world, 91L));
        FrontierWorldState initial = state(engine);
        ResidentProfile existing = initial.humanPopulation().resident(new SubjectId("resident:1-1"));
        ResidentBorn forged = new ResidentBorn(new SubjectId("job:resident-birth-forged"),
                new ResidentProfile(new SubjectId("resident:forged"), existing.householdId(), existing.settlementId(), ResidentRole.FARMER,
                0L, existing.skills()), initial.bootstrap().settlements().getFirst().anchor(),
                new io.farfrontier.palemirror.frontier.v3.api.ActorBirthIdentity(new SubjectId("resident:forged"),
                        io.farfrontier.palemirror.frontier.v3.api.ActorBirthIdentity.Kind.RESIDENT));
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(world, engine, "command:forged-birth", forged)));

        FrontierWorldState active = stateWithBread(initial, initial.bootstrap().settlements().getFirst().id());
        ScheduledAction review = PopulationBirthProcess.review(active.bootstrap().settlements().getFirst().id(), 1, 100L);
        var proposed = PopulationBirthProcess.planReview(active, review);
        ResidentBirthStarted started = proposed.stream().map(event -> event.payload()).filter(ResidentBirthStarted.class::isInstance)
                .map(ResidentBirthStarted.class::cast).findFirst().orElseThrow();
        BlockPosition birthPosition = started.job().position();
        GrayboxCell birthFoot = FrontierGrayboxPlan.compile(active).cells().get(birthPosition);
        assertTrue(birthFoot == null || birthFoot.semanticPart() == GrayboxSemanticPart.ROUTE_SURFACE
                        || birthFoot.semanticPart() == GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE,
                "a new resident may use a public support surface but must not be born inside structure geometry");
        assertEquals(null, FrontierGrayboxPlan.compile(active).cells().get(birthPosition.offset(0, 1, 0)));
        assertEquals(null, FrontierGrayboxPlan.compile(active).cells().get(birthPosition.offset(0, 2, 0)));
        assertTrue(active.actorLocations().values().stream().noneMatch(actor -> FrontierTestPositions.supportOf(actor).equals(birthPosition)),
                "the next resident slot must not overlap an existing exact actor");
        assertEquals(started, FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
        active = PopulationBirthProcess.reduceStarted(active, started.job().settlementId(), started);
        assertEquals(started.job(), active.humanPopulation().birthJobs().get(started.job().id()));
        assertEquals(63, active.inventory().items().get(started.job().foodItemId()).count());
        assertTrue(active.physicalIntents().isEmpty(), "birth food is a COLD semantic commitment, not an unfenced physical intent");
        assertEquals(active, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(active)));

        ScheduledAction completionAction = proposed.stream().map(event -> event.payload())
                .filter(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class::cast)
                .map(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created::action)
                .filter(action -> action.kind().equals("frontier.population.birth.complete")).findFirst().orElseThrow();
        FrontierWorldState stillActive = active;
        assertThrows(IllegalArgumentException.class, () -> PopulationBirthProcess.planCompletion(stillActive,
                new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:resident-birth-complete-foreign"),
                        completionAction.dueAt(), 0, started.job().id(), completionAction.kind(), 1)));
        assertThrows(IllegalArgumentException.class, () -> PopulationBirthProcess.planCompletion(stillActive,
                new ScheduledAction(completionAction.id(), new SimInstant(completionAction.dueAt().ticks() + 1L),
                        0, started.job().id(), completionAction.kind(), 1)));
        var completion = PopulationBirthProcess.planCompletion(active, completionAction);
        ResidentBorn born = (ResidentBorn) completion.getFirst().payload();
        assertEquals(started.job().id(), born.jobId());
        ResidentBorn foreignJob = new ResidentBorn(new SubjectId("job:resident-birth-foreign"), born.resident(), born.position(), born.birth());
        ResidentBorn replayedForeignJob = (ResidentBorn) FrontierWorldRuntimeDefinition.payloadCodecs().decode(
                foreignJob.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(foreignJob));
        assertThrows(IllegalArgumentException.class, () -> PopulationBirthProcess.reduceBorn(stillActive,
                started.job().settlementId(), replayedForeignJob));
        FrontierWorldState completed = PopulationBirthProcess.reduceBorn(active, started.job().settlementId(), born);
        assertEquals(born.resident(), completed.humanPopulation().resident(born.resident().id()));
        assertEquals(born.position(), FrontierTestPositions.supportOf(completed.actorLocations().get(born.resident().id())));
        assertEquals(63, completed.inventory().items().get(started.job().foodItemId()).count());
        assertTrue(completed.humanPopulation().birthJobs().isEmpty());
        assertEquals(born, FrontierWorldRuntimeDefinition.payloadCodecs().decode(born.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(born)));
    }

    @Test
    void birthReviewWaitsWithoutCreatingAHiddenPopulationWhenNoOwnedFoodExists() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:birth-no-food"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        var proposed = PopulationBirthProcess.planReview(state, PopulationBirthProcess.review(settlement.id(), 1, 100L));
        assertEquals(1, proposed.size());
        assertTrue(proposed.getFirst().payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created);
        assertTrue(state.humanPopulation().birthJobs().isEmpty());
    }

    @Test
    void coldReferenceDepotStartsTheSameCanonicalBirthAsAnActiveReplicaAndKeepsTheRemainderProvisionable() {
        FrontierWorldState active = stateWithBread(FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:birth-reference"), 91L)),
                new SubjectId("settlement:1"));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        PhysicalReplicaRecord replica = PhysicalReplicaRecord.expected(depot, ReferenceContainerCustody.semanticKind(active, depot), 7L,
                ReferenceContainerCustody.canonicalFingerprint(active, depot), ReferenceContainerCustody.provenance(depot));
        FrontierWorldState released = active.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(PhysicalReplicaCustodyState.empty().declare(replica)));

        ScheduledAction review = PopulationBirthProcess.review(new SubjectId("settlement:1"), 1, 100L);
        var proposed = PopulationBirthProcess.planReview(released, review);
        FrontierWorldState activeReplica = active.withInventory(active.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE));
        var activeProposed = PopulationBirthProcess.planReview(activeReplica, review);
        ResidentBirthStarted coldStarted = proposed.stream().map(event -> event.payload()).filter(ResidentBirthStarted.class::isInstance)
                .map(ResidentBirthStarted.class::cast).findFirst().orElseThrow();
        ResidentBirthStarted activeStarted = activeProposed.stream().map(event -> event.payload()).filter(ResidentBirthStarted.class::isInstance)
                .map(ResidentBirthStarted.class::cast).findFirst().orElseThrow();

        assertEquals(3, proposed.size(), "COLD birth emits only its review, semantic commitment and delayed completion");
        assertEquals(activeStarted, coldStarted, "historical replica materialization cannot alter the canonical birth subject");
        FrontierWorldState committed = PopulationBirthProcess.reduceStarted(released, coldStarted.job().settlementId(), coldStarted);
        assertEquals(63, SettlementProvisionProcess.availableFood(committed, new SubjectId("settlement:1")),
                "the semantic birth consumes one ration and leaves the remaining exact stack available to provisioning");
        assertTrue(committed.physicalIntents().isEmpty(), "COLD birth creates no physical consumption permit");

        PhysicalReplicaCustodyState heldCustody = PhysicalReplicaCustodyState.empty().declare(replica)
                .observe(depot, 7L, 1L, replica.fingerprint(), replica.provenance(), 7L)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(depot), depot, ReferenceContainerCustody.PROVIDER_ID,
                        1L, 7L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null));
        FrontierWorldState held = released.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(heldCustody));

        assertEquals(1, PopulationBirthProcess.planReview(held, review).size(),
                "a live reference custodian excludes competing canonical spending until its current lease is released");
        assertThrows(IllegalArgumentException.class,
                () -> PopulationBirthProcess.reduceStarted(held, coldStarted.job().settlementId(), coldStarted),
                "the reducer also rejects a forged commitment while the current custodian holds the scope");
    }

    @Test
    void conflictedReferenceDepotRejectsOnlyItsBirthFoodWhileHiveScopeRemainsEligible() {
        FrontierWorldState stocked = stateWithBread(FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:birth-conflicted-reference"), 91L)),
                new SubjectId("settlement:1"));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        PhysicalReplicaRecord expected = PhysicalReplicaRecord.expected(depot, ReferenceContainerCustody.semanticKind(stocked, depot), 0L,
                ReferenceContainerCustody.canonicalFingerprint(stocked, depot), ReferenceContainerCustody.provenance(depot));
        FrontierWorldState conflicted = stocked.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(PhysicalReplicaCustodyState.empty().declare(expected)
                .observe(depot, 0L, 1L, "sha256:player-changed", "foreign:player", 0L)));

        var proposed = PopulationBirthProcess.planReview(conflicted, PopulationBirthProcess.review(new SubjectId("settlement:1"), 1, 100L));
        assertEquals(1, proposed.size(), "a changed depot cannot mint a birth permit from retained canonical bread");
        assertTrue(!ReferenceContainerCustody.blocksCanonicalUse(conflicted, new SubjectId("container:hive-east-store")),
                "the depot fence is not a hive-wide admission stop");
        assertTrue(conflicted.inventory().items().containsKey(new SubjectId("item:birth-test-bread")),
                "conflict leaves the exact food claim intact for explicit reconciliation");
    }

    @Test
    void schedulerAdmitsExactlyOneResidentAfterTheCanonicalBirthDelayWithoutPhysicalConsumption() {
        WorldId world = new WorldId("frontier:birth-scheduled");
        var base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        FrontierWorldState active = stateWithBread(base.initialState(), base.initialState().bootstrap().settlements().getFirst().id());
        SubjectId settlement = active.bootstrap().settlements().getFirst().id();
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(world, active, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                java.util.List.of(PopulationBirthProcess.review(settlement, 1, 100L)), base.transactionCommitter());
        var engine = FrontierEngines.create(configuration);
        engine.advanceTo(new SimInstant(100L), new WorkBudget(16, 64));
        FrontierWorldState permitted = state(engine);
        ResidentBirthJob job = permitted.humanPopulation().birthJobs().values().stream().findFirst().orElseThrow();
        assertTrue(permitted.humanPopulation().resident(job.resident().id()) == null);
        assertEquals(63, permitted.inventory().items().get(job.foodItemId()).count());
        assertTrue(permitted.physicalIntents().isEmpty());

        engine.advanceTo(new SimInstant(300L), new WorkBudget(16, 64));
        FrontierWorldState born = state(engine);
        assertEquals(job.resident(), born.humanPopulation().resident(job.resident().id()));
        assertEquals(63, born.inventory().items().get(job.foodItemId()).count());
    }

    @Test
    void semanticBirthCommitmentSurvivesRestartWithoutAPhysicalConsumptionRecoveryWindow() {
        FrontierWorldState state = stateWithBread(FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:birth-restart"), 91L)),
                new SubjectId("settlement:1"));
        ScheduledAction review = PopulationBirthProcess.review(new SubjectId("settlement:1"), 1, 100L);
        var planned = PopulationBirthProcess.planReview(state, review);
        ResidentBirthStarted started = planned.stream().map(event -> event.payload()).filter(ResidentBirthStarted.class::isInstance)
                .map(ResidentBirthStarted.class::cast).findFirst().orElseThrow();
        state = PopulationBirthProcess.reduceStarted(state, started.job().settlementId(), started);
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(63, state.inventory().items().get(started.job().foodItemId()).count());
        assertTrue(state.physicalIntents().isEmpty());
        ScheduledAction completionAction = planned.stream().map(event -> event.payload())
                .filter(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class::cast)
                .map(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created::action)
                .filter(action -> action.kind().equals("frontier.population.birth.complete")).findFirst().orElseThrow();
        ResidentBorn born = (ResidentBorn) PopulationBirthProcess.planCompletion(state, completionAction).getFirst().payload();
        state = PopulationBirthProcess.reduceBorn(state, started.job().settlementId(), born);
        assertEquals(born.resident(), state.humanPopulation().resident(born.resident().id()));
    }

    @Test
    void housingIsAPhysicalCapacityGateRatherThanASecondMutablePopulationCounter() {
        WorldId world = new WorldId("frontier:housing-capacity");
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SettlementStructure housing = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.HOUSING).findFirst().orElseThrow();

        assertEquals(state.bootstrap().ruleset().facilityCapacity().intactHousingBeds(), SettlementFacilityCapability.housingCapacity(state, settlement.id()));
        assertEquals(settlement.residents().size(), SettlementFacilityCapability.livingResidents(state, settlement.id()));
        assertEquals(state.bootstrap().ruleset().facilityCapacity().damagedHousingBeds(),
                SettlementFacilityCapability.forCondition(state.bootstrap().ruleset(), StructureKind.HOUSING, StructureCondition.DAMAGED).residentCapacity());

        FrontierWorldState destroyed = state.withStructureCondition(housing.id(), StructureCondition.DESTROYED);
        assertEquals(0, SettlementFacilityCapability.housingCapacity(destroyed, settlement.id()));
        ResidentProfile parent = destroyed.humanPopulation().resident(settlement.residents().getFirst().id());
        ResidentProfile newborn = new ResidentProfile(new SubjectId("resident:1-housing-blocked"), parent.householdId(), settlement.id(),
                ResidentRole.FARMER, 0L, parent.skills());
        ResidentBirthJob permit = new ResidentBirthJob(new SubjectId("job:resident-birth-housing-blocked"), settlement.id(), parent.householdId(),
                new SubjectId("item:bootstrap-1-wheat"), new SubjectId("commitment:resident-birth-housing-blocked"), newborn, settlement.anchor());
        assertThrows(IllegalArgumentException.class, () -> destroyed.startResidentBirth(permit));

        FrontierObjectBoard board = FrontierReadabilityPlan.compile(state).boards().get(housing.id());
        assertTrue(board.text().contains(settlement.residents().size() + " / " + state.bootstrap().ruleset().facilityCapacity().intactHousingBeds() + " RESIDENTS"));
    }

    private static FrontierWorldState state(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine) {
        return new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
    }

    private static FrontierWorldState stateWithActiveBread(FrontierWorldState state, SubjectId settlementId) {
        state = stateWithBread(state, settlementId);
        SubjectId depot = FrontierWorldState.depotId(settlementId);
        return state.withInventory(state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE));
    }

    private static FrontierWorldState stateWithBread(FrontierWorldState state, SubjectId settlementId) {
        SubjectId depot = FrontierWorldState.depotId(settlementId); SubjectId bread = new SubjectId("item:birth-test-bread");
        ExactInventory inventory = state.inventory().store(new ExactItemStack(bread, settlementId, PopulationBirthProcess.BREAD, 64,
                new InventoryCustody.ContainerSlot(depot, 1)));
        return state.withInventory(inventory);
    }
    private static FrontierCommand command(WorldId world, io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine,
                                           String id, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        CommandId command = new CommandId(id); var checkpoint = engine.checkpoint();
        return new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(command), payload);
    }
}
