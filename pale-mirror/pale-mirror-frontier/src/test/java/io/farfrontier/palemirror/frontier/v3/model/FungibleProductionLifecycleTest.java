package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.PhysicalIntentLifecycleFixture;
import io.farfrontier.palemirror.frontier.v3.process.CompanyFoundationProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class FungibleProductionLifecycleTest {
    @Test
    void genericCustodyReleaseCannotBypassTheResourceLayoutOwner() {
        Fixture f = fixture();
        var scope = ReferenceContainerCustody.scopeId(f.depot());
        var lease = f.state().replicaCustody().custodyByScope().get(scope);
        var checkpointed = f.state().withChanges(FrontierWorldStateUpdate.begin().replicaCustody(f.state().replicaCustody()
                .checkpoint(scope, 1L, lease.expectedCanonicalRevision(), lease.expectedReplicaRevision())));
        var rejected = assertThrows(IllegalArgumentException.class, () -> ReferenceContainerCustody.release(checkpointed,
                new PhysicalReplicaCustodyPayloads.CustodyReleased(scope, 1L, lease.expectedCanonicalRevision(), lease.expectedReplicaRevision())));
        assertTrue(rejected.getMessage().contains("bound resource layout"));
        assertEquals(f.state().inventory(), checkpointed.inventory());
        assertEquals(f.job(), checkpointed.productionJobs().get(f.job().id()));
    }

    @Test
    void unstartedEffectReleaseRestoresOneColdContinuationAndRejectsItsLateActuatorAfterRecovery() {
        assertReleasedContinuation(false);
    }

    @Test
    void referenceMutationClosureAlsoRestoresTheUnstartedProductionContinuation() {
        assertReleasedContinuation(true);
    }

    @Test
    void checkpointedContainerReleaseCompletesBeforeAnotherLoadedTickOrRecovery() {
        assertReleasedContinuation(false, true);
    }

    private static void assertReleasedContinuation(boolean closeMutation) {
        assertReleasedContinuation(closeMutation, false);
    }

    private static void assertReleasedContinuation(boolean closeMutation, boolean checkpointFirst) {
        Fixture f = fixture();
        var prepared = PhysicalIntentLifecycleFixture.prepare(f.state(), f.job().settlementId(), f.intent());
        var world = prepared.bootstrap().worldId();
        var base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var configuration = new FrontierEngineConfiguration<>(world, prepared, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(),
                base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter());
        var engine = FrontierEngines.create(configuration);
        if (checkpointFirst) {
            var lease = prepared.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(f.depot()));
            assertInstanceOf(CommandResult.Accepted.class, submit(engine, "checkpoint-before-layout-release",
                    new PhysicalReplicaCustodyPayloads.CustodyCheckpointed(lease.scopeId(), lease.authorityEpoch(),
                            lease.expectedCanonicalRevision(), lease.expectedReplicaRevision())));
        }
        FrontierPayload release = closeMutation
                ? ReferenceContainerCustody.confirmedMutationTransition(prepared, f.depot(), 2L)
                : new FungibleStackBindingsReleased(f.account(), 1L);
        var released = submit(engine, "release-input", release);
        assertInstanceOf(CommandResult.Accepted.class, released, released.toString());
        var state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertFalse(state.physicalIntents().containsKey(f.intent().id()));
        assertInstanceOf(ProductionInputHold.FungibleCold.class, state.productionJobs().get(f.job().id()).inputHold());
        assertEquals(f.job().workProgress(), state.productionJobs().get(f.job().id()).workProgress());
        assertFalse(state.fencedRecovery().current().containsKey(FencedRecoveryPhysicalIntentSupport.bindingId(f.intent())));
        assertEquals(List.of(io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.complete(f.job(), 1L)), engine.checkpoint().schedules());
        if (!closeMutation) {
            var scope = ReferenceContainerCustody.scopeId(f.depot());
            var lease = state.replicaCustody().custodyByScope().get(scope);
            assertEquals(PhysicalCustodyLeaseStatus.RELEASED, lease.status(),
                    "resource release must not publish a COLD input behind live reference custody");
            assertTrue(state.inventory().fungibleResources().bindings().isEmpty());
        }
        var image = new io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage(world,
                Optional.of(new io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord(engine.checkpoint(), 1L)), List.of());
        var recovered = FrontierEngines.recover(configuration, image);
        assertInstanceOf(CommandResult.Rejected.class, submit(recovered, "late-actuator",
                new PhysicalIntentTransition(f.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty())));
        recovered.advanceTo(new SimInstant(1L), new WorkBudget(100, 100));
        var completed = new FrontierWorldStateCodec().decode(recovered.checkpoint().canonicalState());
        assertFalse(completed.productionJobs().containsKey(f.job().id()));
        assertEquals(64, completed.inventory().fungibleResources().lots().get(f.job().outputItemId()).quantity());
        assertEquals(f.state().inventory().economics().require(CompanyFoundationProcess.companyId(f.job().settlementId())).balance()
                        .plus(f.state().companies().market().acceptedForJob(f.job().id()).orElseThrow().acceptedTotalPrice()),
                completed.inventory().economics().require(CompanyFoundationProcess.companyId(f.job().settlementId())).balance());
        assertTrue(recovered.checkpoint().schedules().stream().noneMatch(action -> action.subject().equals(f.job().id())));
    }

    @Test
    void physicalRecipeLayoutPreservesUnconsumedStockAcrossSplitStacksAndRecovery() {
        Fixture f = fixture(96);
        var layout = FungibleProductionLayout.plan(f.state(), f.job());
        assertEquals(Map.of(0, new ReferenceContainerCustody.ProjectedFungibleSlot("minecraft:wheat", 32),
                1, new ReferenceContainerCustody.ProjectedFungibleSlot("minecraft:wheat", 64)), layout.before());
        assertEquals(Map.of(0, new ReferenceContainerCustody.ProjectedFungibleSlot("minecraft:bread", 64),
                1, new ReferenceContainerCustody.ProjectedFungibleSlot("minecraft:wheat", 32)), layout.after());
        var codec = new FrontierWorldStateCodec();
        var recovered = codec.decode(codec.encode(f.state()));
        assertEquals(layout, FungibleProductionLayout.plan(recovered, recovered.productionJobs().get(f.job().id())));
        assertEquals(96, f.state().inventory().fungibleResources().lots().get(f.job().consumedItemId()).quantity(),
                "planning cannot spend stock before the physical observation");
    }

    @Test
    void physicalRecipeCannotPlanFromReleasedInput() {
        Fixture f = fixture();
        var released = ProductionResourceCustody.release(f.state(), f.account(), 1L);
        assertThrows(IllegalArgumentException.class, () -> FungibleProductionLayout.plan(released,
                released.productionJobs().get(f.job().id())));
        assertThrows(IllegalArgumentException.class, () -> FungibleProductionLayout.plan(released, f.job()),
                "a caller retaining an old bound job cannot bypass the current account layout");
    }

    @Test
    void ambiguousRegisteredTransitionRecoversTheSameCommitmentAndAcceptsOneLaterReceipt() {
        Fixture f = fixture();
        var prepared = PhysicalIntentLifecycleFixture.prepare(f.state(), f.job().settlementId(), f.intent());
        var running = PhysicalIntentLifecycleFixture.transition(prepared, f.job().settlementId(), f.intent(), PhysicalIntentStatus.RUNNING, Optional.empty());
        var world = running.bootstrap().worldId();
        var base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var configuration = new FrontierEngineConfiguration<>(world, running, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(),
                base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter());
        var engine = FrontierEngines.create(configuration);
        var unknown = new PhysicalIntentTransition(f.intent().id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty())
                .withRecoveryDiagnostic(PhysicalIntentRecoveryDiagnosticProducer.PRODUCTION_WORK.stamp(f.intent()));
        var result = submit(engine, "unknown", unknown);
        assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
        assertInstanceOf(CommandResult.Rejected.class, submit(engine, "ambiguous-release",
                new FungibleStackBindingsReleased(f.account(), 1L)), "an ambiguous write must retain physical custody");
        var retained = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(running.inventory().fungibleResources(), retained.inventory().fungibleResources());
        assertEquals(f.job(), retained.productionJobs().get(f.job().id()));
        var image = new io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage(world,
                Optional.of(new io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord(engine.checkpoint(), 1L)), List.of());
        var recovered = FrontierEngines.recover(configuration, image);
        var confirmation = new PhysicalIntentTransition(f.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(f.receipt(1L, "minecraft:bread")));
        result = submit(recovered, "confirm", confirmation);
        assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
        assertInstanceOf(CommandResult.Rejected.class, submit(recovered, "repeat", confirmation));
        var completed = new FrontierWorldStateCodec().decode(recovered.checkpoint().canonicalState());
        assertFalse(completed.productionJobs().containsKey(f.job().id()));
        assertEquals(64, completed.inventory().fungibleResources().lots().get(f.job().outputItemId()).quantity());
    }

    private static CommandResult submit(FrontierEngine<FrontierWorldProjection> engine, String phase, FrontierPayload payload) {
        var checkpoint = engine.checkpoint(); var id = new CommandId("command:lot-production-" + phase);
        return engine.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload));
    }

    @Test
    void registeredOwnerConfirmsLotOutputAndFinanceWithoutAnExactItemAlias() {
        Fixture f = fixture();
        FrontierWorldState prepared = PhysicalIntentLifecycleFixture.prepare(f.state(), f.job().settlementId(), f.intent());
        FrontierWorldState running = PhysicalIntentLifecycleFixture.transition(prepared, f.job().settlementId(), f.intent(), PhysicalIntentStatus.RUNNING, Optional.empty());
        var receipt = f.receipt(1L, "minecraft:bread");
        assertThrows(IllegalArgumentException.class, () -> ProductionResourceCustody.release(running, f.account(), 1L),
                "a started physical recipe cannot release its input to COLD spending");
        var payload = new PhysicalIntentTransition(f.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(payload, codecs.decode(payload.type(), codecs.encode(payload)));
        FrontierWorldState complete = PhysicalIntentLifecycleFixture.transition(running, f.job().settlementId(), f.intent(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertFalse(complete.productionJobs().containsKey(f.job().id()));
        assertFalse(complete.inventory().items().containsKey(f.job().outputItemId()));
        assertEquals(64, complete.inventory().fungibleResources().lots().get(f.job().outputItemId()).quantity());
        assertFalse(complete.inventory().fungibleResources().claims().containsKey(f.claim()));
        assertEquals(f.state().inventory().economics().require(CompanyFoundationProcess.companyId(f.job().settlementId())).balance()
                        .plus(f.state().companies().market().acceptedForJob(f.job().id()).orElseThrow().acceptedTotalPrice()),
                complete.inventory().economics().require(CompanyFoundationProcess.companyId(f.job().settlementId())).balance());
        var order = complete.companies().market().workOrders().get(f.order());
        assertEquals(MarketWorkOrderStatus.FULFILLED, order.status());
        assertEquals(TerminalProductionReceipt.ResourceRepresentation.RESOURCE_LOT, order.terminalReceipt().orElseThrow().outputRepresentation());
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(complete));
        assertEquals(receipt, recovered.physicalObservations().get(receipt.id()));
        assertEquals(order, recovered.companies().market().workOrders().get(f.order()));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.transition(complete,
                f.job().settlementId(), f.intent(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)));
    }

    @Test
    void twoLotBoundRecipeSurvivesSnapshotAndRetiresBothInputsIntoOneOutput() {
        Fixture original = fixture();
        var old = original.state().inventory().fungibleResources().releaseBindings(original.account(), 1L);
        SubjectId first = original.job().consumedItemId();
        SubjectId second = new SubjectId("lot:production-second-field");
        Map<SubjectId, Integer> portions = Map.of(first, 32, second, 32);
        var lots = new HashMap<>(old.lots());
        ResourceLot previous = lots.get(first);
        lots.put(first, previous.withQuantity(32));
        lots.put(second, new ResourceLot(second, previous.economicOwnerId(), previous.itemKind(), 32, "harvest:second-field", List.of()));
        var claims = new HashMap<>(old.claims());
        claims.put(original.claim(), new ClaimAllocation(original.claim(), original.job().id(), original.job().settlementId(),
                "minecraft:wheat", 64, portions, ClaimPurpose.PRODUCTION_WORK));
        var accounts = new HashMap<>(old.accounts());
        accounts.put(original.account(), new CustodyAccount(original.account(), new ResourceCustody.Container(original.depot()),
                portions, Map.of(original.claim(), 64)));
        var cold = new FungibleResourceLedger(lots, claims, accounts, old.bindings());
        var stacks = List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(original.depot(), 0)), "minecraft:wheat", 32),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(original.depot(), 1)), "minecraft:wheat", 32));
        var bound = cold.rebind(original.account(), 1L, FungiblePhysicalObservation.bind(cold, original.account(), 1L, stacks));
        var job = original.job().withInputHold(new ProductionInputHold.FungibleBound(first, original.account(), original.claim(), 1L, portions));
        var state = original.state().withChanges(FrontierWorldStateUpdate.begin()
                .inventory(original.state().inventory().withFungibleResources(bound)).productionJobs(Map.of(job.id(), job)));
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(portions, recovered.productionJobs().get(job.id()).inputQuantities());
        assertEquals(Map.of(0, new ReferenceContainerCustody.ProjectedFungibleSlot("minecraft:bread", 64)),
                FungibleProductionLayout.plan(recovered, job).after());
        var prepared = PhysicalIntentLifecycleFixture.prepare(recovered, job.settlementId(), original.intent());
        var running = PhysicalIntentLifecycleFixture.transition(prepared, job.settlementId(), original.intent(), PhysicalIntentStatus.RUNNING, Optional.empty());
        var receipt = new FungibleProductionObservation(new PhysicalObservationId("observation:two-lot-production"), original.intent().id(),
                original.account(), original.depot(), first, portions, original.claim(), job.outputItemId(), 64, 1L,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(original.depot(), 0)), "minecraft:bread", 64)));
        var completed = PhysicalIntentLifecycleFixture.transition(running, job.settlementId(), original.intent(), PhysicalIntentStatus.CONFIRMED,
                Optional.of(receipt));
        assertFalse(completed.productionJobs().containsKey(job.id()));
        assertFalse(completed.inventory().fungibleResources().lots().containsKey(first));
        assertFalse(completed.inventory().fungibleResources().lots().containsKey(second));
        assertEquals(List.of(first, second), completed.inventory().fungibleResources().lots().get(job.outputItemId()).lineage());
        assertEquals(portions, completed.companies().market().workOrders().get(original.order()).terminalReceipt().orElseThrow().inputLots());
    }

    @Test
    void foreignEpochAndWrongActualProductCannotConsumeTheReservation() {
        Fixture f = fixture();
        FrontierWorldState prepared = PhysicalIntentLifecycleFixture.prepare(f.state(), f.job().settlementId(), f.intent());
        FrontierWorldState running = PhysicalIntentLifecycleFixture.transition(prepared, f.job().settlementId(), f.intent(), PhysicalIntentStatus.RUNNING, Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.transition(running,
                f.job().settlementId(), f.intent(), PhysicalIntentStatus.CONFIRMED, Optional.of(f.receipt(2L, "minecraft:bread"))));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.transition(running,
                f.job().settlementId(), f.intent(), PhysicalIntentStatus.CONFIRMED, Optional.of(f.receipt(1L, "minecraft:wheat"))));
        assertEquals(f.job(), running.productionJobs().get(f.job().id()));
        assertEquals(64, running.inventory().fungibleResources().claims().get(f.claim()).quantity());
        assertEquals(f.state().inventory().economics(), running.inventory().economics(),
                "a rejected physical transformation must not pay the invoice beneficiary");
    }

    private static Fixture fixture() {
        return fixture(64);
    }

    private static Fixture fixture(int inputQuantity) {
        var base = ProductionProcessTest.activeMaterializedProduction();
        SubjectId account = new SubjectId("custody:production-lot"), claim = new SubjectId("claim:production-lot");
        SubjectId depot = FrontierWorldState.depotId(base.job().settlementId());
        var input = base.state().inventory().items().get(base.job().consumedItemId());
        var resources = base.state().inventory().fungibleResources().issue(new ResourceLot(input.id(), base.job().settlementId(),
                        "minecraft:wheat", inputQuantity, "test:nominal-lot", List.of()),
                new CustodyAccount(account, new ResourceCustody.Container(depot), Map.of(input.id(), inputQuantity), Map.of()))
                .reserve(new ClaimAllocation(claim, base.job().id(), base.job().settlementId(), "minecraft:wheat", 64,
                        Map.of(input.id(), 64), ClaimPurpose.PRODUCTION_WORK), account);
        var stacks = inputQuantity == 64
                ? List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:wheat", 64))
                : List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:wheat", inputQuantity - 64),
                        new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 1)), "minecraft:wheat", 64));
        resources = resources.rebind(account, 1L, FungiblePhysicalObservation.bind(resources, account, 1L, stacks));
        var job = base.job().withWorkProgress(ProductionWorkProgress.outputReady())
                .withInputHold(new ProductionInputHold.FungibleBound(input.id(), account, claim, 1L));
        var state = base.state().withChanges(FrontierWorldStateUpdate.begin()
                .inventory(base.state().inventory().withoutItem(input.id()).withFungibleResources(resources)).productionJobs(Map.of(job.id(), job)));
        state = ReferenceContainerCustodyFixtures.observedAndHeld(state, depot);
        var intent = new PhysicalIntent(new PhysicalIntentId("intent:production-lot"), PhysicalIntentKind.PRODUCTION_TRANSFORMATION,
                PhysicalIntentStatus.PREPARED, job.id(), PhysicalIntentRoleBinding.productionResources(job.id(), input.id(), job.outputItemId(), claim, account, depot),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0,
                PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED, PhysicalIntentLifecycleOwner.PRODUCTION_WORK);
        return new Fixture(state, job, intent, account, claim, depot, base.order().id());
    }

    private record Fixture(FrontierWorldState state, ProductionJob job, PhysicalIntent intent, SubjectId account,
                           SubjectId claim, SubjectId depot, SubjectId order) {
        FungibleProductionObservation receipt(long epoch, String kind) {
            return new FungibleProductionObservation(new PhysicalObservationId("observation:production-lot"), intent.id(), account, depot,
                    job.consumedItemId(), claim, job.outputItemId(), 64, epoch, List.of(new FungiblePhysicalObservation.Stack(
                    new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 0)), kind, 64)));
        }
    }
}
