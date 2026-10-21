package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reusable owner-lifecycle assertions, fed by the ordinary meal test path. */
final class ResidentMealRetiredResourceAssertions {
    private ResidentMealRetiredResourceAssertions() { }
    /** Forks the real ordinary meal path; no outcome is inserted by a fixture. */
    static void assertFatalityRetainsMealResources(FrontierWorldState state, SubjectId resident,
            long seed, long atTick, ResidentMealResourceObligation.CustodyState custody) {
        var meal = state.humanPopulation().meals().get(resident);
        var body = ActorBodyAuthority.current(state, resident);
        var location = state.actorLocations().get(resident);
        var death = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied(body,
                location.body(), location.condition().health(), java.util.Optional.empty(),
                java.util.Optional.of(meal.executionId()), "environment");
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), seed);
        var codec = new FrontierWorldStateCodec();
        var due = ResidentMealProcess.progress(meal, Math.addExact(atTick, 100L));
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(
                new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(), state,
                        new SimInstant(atTick), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), codec,
                        base.projectionMapper(), base.limits(), List.of(due), base.transactionCommitter(),
                        base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter()));
        var checkpoint = engine.checkpoint();
        var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:meal-fatality-" + custody.name().toLowerCase(java.util.Locale.ROOT));
        var command = new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(
                io.farfrontier.palemirror.frontier.v3.api.FrontierCommand.LEGACY_SCHEMA_VERSION, id,
                checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id), death);
        var result = engine.submit(command);
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, result, result::toString);
        var after = codec.decode(engine.checkpoint().canonicalState());
        var obligation = after.humanPopulation().mealResourceObligations().get(resident);
        assertEquals(custody, obligation.custodyState());
        assertEquals(body, obligation.body());
        assertEquals(meal.executionId(), obligation.executionId());
        assertEquals(meal.portion(), obligation.portion());
        assertEquals(meal.pendingPhysicalStep(), obligation.pendingPhysicalStep());
        assertEquals(atTick, obligation.retiredAtTick());
        assertEquals(custody == ResidentMealResourceObligation.CustodyState.SOURCE_TAKE_PENDING,
                ResidentMealPhysicalAuthority.pendingForContainer(after, meal.depotId()),
                "retiring the activity must not release a possibly changed source container");
        assertEquals(custody == ResidentMealResourceObligation.CustodyState.SOURCE_TAKE_PENDING,
                ContainerPhysicalAuthorityComposition.pending(after, meal.depotId()));
        assertFalse(ContainerPhysicalAuthorityComposition.pending(after, new SubjectId("container:other")));
        assertEquals(ActorLifeStatus.DEAD, after.actorLocations().get(resident).condition().status());
        assertFalse(after.humanPopulation().meals().containsKey(resident));
        assertTrue(after.actorExecutions().actors().get(resident).current().isEmpty());
        assertFalse(ActorBodyAuthority.retainsPhysicalCustody(after, resident));
        assertEquals(state.inventory(), after.inventory(), "fatality is not a take, consumption, drop or destruction receipt");
        assertEquals(state.humanPopulation().nutrition(resident), after.humanPopulation().nutrition(resident));
        assertFalse(engine.checkpoint().schedules().stream().anyMatch(action -> action.id().equals(due.id())));
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind());
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceHotArrived(after, resident,
                new ResidentMealHotArrived(resident, 1L, location.body(),
                        ModeledActorBodyFacts.hotObservation(state, meal.executionId(), 1L))));
        if (meal.pendingPhysicalStep().isPresent()) {
            var stale = new ResidentMealHotEffectObserved(resident, meal.phase(),
                    meal.pendingPhysicalStep().orElseThrow().ambientRevision(), location.body(), List.of(), List.of(), meal.executionId());
            assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.planHotObserved(after, stale, atTick, 1L));
        }
        var population = after.humanPopulation();
        population = population.withSchedule(meal.settlementId(), population.schedule(meal.settlementId()));
        population = population.withStarvation(resident, population.health(resident).starvation());
        var recovered = codec.decode(codec.encode(after.withHumanPopulation(population)));
        assertEquals(obligation, recovered.humanPopulation().mealResourceObligations().get(resident));
        var oldSchema = codec.encode(after);
        oldSchema[4] = (byte) 231;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(oldSchema),
                "an old snapshot cannot silently hydrate without retired resource obligations");
        var foreignEpoch = new ResidentMealResourceObligation(obligation.executionId(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(resident, body.physicalEpoch() + 1L),
                obligation.settlementId(), obligation.depotId(), obligation.sourceAccountId(), obligation.actorAccountId(),
                obligation.portion(), obligation.custodyState(), obligation.pendingPhysicalStep(), obligation.retiredAtTick());
        var forgedObligations = new LinkedHashMap<>(after.humanPopulation().mealResourceObligations());
        forgedObligations.put(resident, foreignEpoch);
        var original = after.humanPopulation();
        var forged = after.withHumanPopulation(new HumanPopulation(original.households(), original.residents(), original.birthJobs(),
                original.health(), original.quarantines(), original.migrations(), original.provisions(), original.nutrition(),
                original.medicalOperations(), original.schedules(), original.meals(), forgedObligations));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(codec.encode(forged)),
                "historical food evidence cannot claim a different physical incarnation");
        assertThrows(IllegalArgumentException.class, () -> new ResidentMealResourceObligation(meal.executionId(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(new SubjectId("resident:foreign"), body.physicalEpoch()),
                meal.settlementId(), meal.depotId(), meal.sourceAccountId(), meal.actorAccountId(), meal.portion(),
                custody, meal.pendingPhysicalStep(), atTick));
        if (custody == ResidentMealResourceObligation.CustodyState.SOURCE_TAKE_PENDING) {
            var sourceLayout = after.inventory().fungibleResources().bindings().values().stream()
                    .filter(binding -> binding.accountId().equals(meal.sourceAccountId()))
                    .map(binding -> new FungiblePhysicalObservation.Stack(binding.address(), binding.itemKind(), binding.quantity())).toList();
            var unapplied = new ResidentMealResourceEffectObserved(body, meal.executionId(), meal.pendingPhysicalStep().orElseThrow(),
                    ResidentMealResourceEffectObserved.Outcome.TAKE_UNAPPLIED, sourceLayout, List.of());
            var cancelled = assertRetiredMealReceipt(after, unapplied, seed, atTick);
            assertFalse(cancelled.humanPopulation().mealResourceObligations().containsKey(resident));
            assertFalse(ContainerPhysicalAuthorityComposition.pending(cancelled, meal.depotId()));
            assertEquals(after.inventory().fungibleResources().lots(), cancelled.inventory().fungibleResources().lots());
            assertEquals(after.inventory().fungibleResources().accounts().get(meal.sourceAccountId()).lotQuantities(),
                    cancelled.inventory().fungibleResources().accounts().get(meal.sourceAccountId()).lotQuantities());
            assertFalse(cancelled.inventory().fungibleResources().claims().containsKey(meal.claimId()));
            // The ordinary fixture has one bread stack; model the alternative already-applied take.
            assertEquals(1, sourceLayout.size());
            var sourceStack = sourceLayout.getFirst();
            var remainder = new FungiblePhysicalObservation.Stack(sourceStack.address(), sourceStack.itemKind(),
                    sourceStack.quantity() - meal.portion().quantity());
            var held = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorPocket(resident,
                    SceneLease.deterministicEntityId(after.bootstrap().worldId(), resident), 0),
                    meal.portion().itemKind(), meal.portion().quantity());
            var applied = new ResidentMealResourceEffectObserved(body, meal.executionId(), meal.pendingPhysicalStep().orElseThrow(),
                    ResidentMealResourceEffectObserved.Outcome.TAKE_APPLIED, List.of(remainder), List.of(held));
            var transferred = assertRetiredMealReceipt(after, applied, seed, atTick);
            assertEquals(ResidentMealResourceObligation.CustodyState.ACTOR_PORTION,
                    transferred.humanPopulation().mealResourceObligations().get(resident).custodyState());
            assertFalse(ContainerPhysicalAuthorityComposition.pending(transferred, meal.depotId()));
            assertEquals(after.inventory().fungibleResources().totalQuantity(meal.settlementId(), meal.portion().itemKind()),
                    transferred.inventory().fungibleResources().totalQuantity(meal.settlementId(), meal.portion().itemKind()));
            assertEquals(meal.portion().quantity(), transferred.inventory().fungibleResources().accounts()
                    .get(meal.actorAccountId()).lotQuantities().values().stream().mapToInt(Integer::intValue).sum());
            assertRetiredPortionDispositions(transferred, resident, seed, atTick);
            assertThrows(IllegalArgumentException.class, () -> ResidentMealResourceProcess.reduce(after, resident,
                    new ResidentMealResourceEffectObserved(body, meal.executionId(), meal.pendingPhysicalStep().orElseThrow(),
                            ResidentMealResourceEffectObserved.Outcome.TAKE_UNAPPLIED, List.of(remainder), List.of())),
                    "a changed source cannot release the pending fence as an unapplied take");
        } else if (custody == ResidentMealResourceObligation.CustodyState.ACTOR_CONSUMPTION_PENDING) {
            assertRetiredPortionDispositions(after, resident, seed, atTick);
            var held = after.inventory().fungibleResources().bindings().values().stream()
                    .filter(binding -> binding.accountId().equals(meal.actorAccountId())).findFirst().orElseThrow();
            var unapplied = new ResidentMealResourceEffectObserved(body, meal.executionId(), meal.pendingPhysicalStep().orElseThrow(),
                    ResidentMealResourceEffectObserved.Outcome.CONSUMPTION_UNAPPLIED,
                    List.of(new FungiblePhysicalObservation.Stack(held.address(), held.itemKind(), held.quantity())), List.of());
            var cancelled = assertRetiredMealReceipt(after, unapplied, seed, atTick);
            assertEquals(after.inventory(), cancelled.inventory(), "cancelling an unattempted consumption cannot eat or invent resources");
            assertEquals(ResidentMealResourceObligation.CustodyState.ACTOR_PORTION,
                    cancelled.humanPopulation().mealResourceObligations().get(resident).custodyState());
            assertRetiredPortionDispositions(cancelled, resident, seed, atTick);
            var receipt = new ResidentMealResourceEffectObserved(body, meal.executionId(), meal.pendingPhysicalStep().orElseThrow(),
                    ResidentMealResourceEffectObserved.Outcome.CONSUMPTION_APPLIED, List.of(), List.of());
            var consumed = assertRetiredMealReceipt(after, receipt, seed, atTick);
            assertFalse(consumed.humanPopulation().mealResourceObligations().containsKey(resident));
            assertFalse(consumed.inventory().fungibleResources().accounts().containsKey(meal.actorAccountId()));
            assertFalse(consumed.inventory().fungibleResources().claims().containsKey(meal.claimId()));
            assertEquals(after.inventory().fungibleResources().totalQuantity(meal.settlementId(), meal.portion().itemKind())
                    - meal.portion().quantity(), consumed.inventory().fungibleResources().totalQuantity(meal.settlementId(), meal.portion().itemKind()));
        } else if (!after.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(meal.actorAccountId())).toList().isEmpty()) {
            assertRetiredPortionDispositions(after, resident, seed, atTick);
        }
    }

    /** Actual TAKE/CONSUME path forks above retain the original claim, pocket and death fence. */
    private static void assertRetiredPortionDispositions(FrontierWorldState state, SubjectId resident, long seed, long atTick) {
        var retained = state.humanPopulation().mealResourceObligations().get(resident);
        var binding = state.inventory().fungibleResources().bindings().values().stream()
                .filter(value -> value.accountId().equals(retained.actorAccountId())).findFirst().orElseThrow();
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        var codec = new FrontierWorldStateCodec();
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), seed);
        for (var outcome : ResidentMealPortionDispositionObserved.Outcome.values()) {
            var carrier = java.util.UUID.nameUUIDFromBytes((resident.value() + ":food-drop").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var receipt = new ResidentMealPortionDispositionObserved(retained.body(), retained.executionId(),
                    binding.authorityEpoch(), outcome, outcome == ResidentMealPortionDispositionObserved.Outcome.WORLD_DROP
                            ? java.util.Optional.of(new ResourceCustody.Ground(carrier, new BlockPosition(1, 64, 1))) : java.util.Optional.empty());
            byte[] encoded = codecs.encode(receipt);
            assertEquals(receipt, codecs.decode(receipt.type(), encoded));
            assertThrows(IllegalArgumentException.class, () -> codecs.decode(receipt.type(),
                    java.util.Arrays.copyOf(encoded, encoded.length - 1)));
            var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(
                    new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(), state,
                            new SimInstant(atTick), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), codec,
                            base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(),
                            base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter()));
            var wrongBody = new ResidentMealPortionDispositionObserved(
                    new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(resident, retained.body().physicalEpoch() + 1L),
                    retained.executionId(), receipt.sourceEpoch(), outcome, receipt.ground());
            var wrongEpoch = new ResidentMealPortionDispositionObserved(retained.body(), retained.executionId(),
                    binding.authorityEpoch() + 1L, outcome, receipt.ground());
            int attempt = 0;
            for (var payload : List.of(wrongBody, wrongEpoch, receipt, receipt)) {
                var checkpoint = engine.checkpoint();
                var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:portion-disposition-" + attempt);
                var command = new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(
                        io.farfrontier.palemirror.frontier.v3.api.FrontierCommand.LEGACY_SCHEMA_VERSION, id,
                        checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                        io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id), payload);
                var result = engine.submit(command);
                if (attempt++ == 2) assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, result, result::toString);
                else assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class, result, result::toString);
                assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind());
            }
            var after = codec.decode(engine.checkpoint().canonicalState());
            assertFalse(after.humanPopulation().mealResourceObligations().containsKey(resident));
            assertFalse(after.inventory().fungibleResources().accounts().containsKey(retained.actorAccountId()));
            assertFalse(after.inventory().fungibleResources().claims().containsKey(retained.claimId()));
            assertEquals(state.actorLocations(), after.actorLocations());
            assertEquals(state.actorExecutions(), after.actorExecutions());
            assertEquals(state.fencedRecovery(), after.fencedRecovery());
            assertEquals(state.humanPopulation().nutrition(), after.humanPopulation().nutrition());
            var totalBefore = state.inventory().fungibleResources().totalQuantity(retained.settlementId(), retained.portion().itemKind());
            if (outcome == ResidentMealPortionDispositionObserved.Outcome.WORLD_DROP) {
                var drop = after.inventory().fungibleResources().accounts().get(new SubjectId("custody:world-" + carrier));
                assertEquals(receipt.ground().orElseThrow(), drop.custody());
                assertEquals(retained.portion().lotQuantities(), drop.lotQuantities());
                assertTrue(drop.claimQuantities().isEmpty());
                var droppedBinding = after.inventory().fungibleResources().bindings().values().stream()
                        .filter(value -> value.accountId().equals(drop.id())).findFirst().orElseThrow();
                assertEquals(new PhysicalStackAddress.WorldEntity(carrier), droppedBinding.address());
                assertEquals(totalBefore, after.inventory().fungibleResources().totalQuantity(retained.settlementId(), retained.portion().itemKind()));
                assertEquals(drop, codec.decode(codec.encode(after)).inventory().fungibleResources().accounts().get(drop.id()));
            } else assertEquals(totalBefore - retained.portion().quantity(),
                    after.inventory().fungibleResources().totalQuantity(retained.settlementId(), retained.portion().itemKind()));
        }
    }

    private static FrontierWorldState assertRetiredMealReceipt(FrontierWorldState state,
            ResidentMealResourceEffectObserved receipt, long seed, long atTick) {
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        byte[] encoded = codecs.encode(receipt);
        assertEquals(receipt, codecs.decode(receipt.type(), encoded));
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(receipt.type(),
                java.util.Arrays.copyOf(encoded, encoded.length - 1)));
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), seed);
        var codec = new FrontierWorldStateCodec();
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(), state,
                        new SimInstant(atTick), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), codec,
                        base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(),
                        base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
        // This branch contains real retained replica revisions from the ordinary
        // fixture. Recover its fork at that revision, not a fictitious revision zero.
        long revision = state.replicaCustody().replicas().values().stream().mapToLong(PhysicalReplicaRecord::emittedCanonicalRevision).max().orElse(0);
        var snapshot = new CheckpointImage(base.worldId(), new Revision(revision), new SimInstant(atTick), codec.encode(state), List.of(), List.of());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.recover(configuration,
                new io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage(base.worldId(),
                        Optional.of(new io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord(snapshot, 0)), List.of()));
        var foreign = new ResidentMealResourceEffectObserved(new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(
                receipt.body().actorId(), receipt.body().physicalEpoch() + 1), receipt.executionId(), receipt.step(),
                receipt.outcome(), receipt.remainingSource(), receipt.destination());
        int attempt = 0;
        for (var payload : List.of(foreign, receipt, receipt)) {
            var checkpoint = engine.checkpoint();
            var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:retired-food-" + checkpoint.revision().value()
                    + "-" + (payload == foreign ? "stale" : "receipt"));
            var command = new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(
                    io.farfrontier.palemirror.frontier.v3.api.FrontierCommand.LEGACY_SCHEMA_VERSION, id,
                    checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                    io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id), payload);
            boolean expected = attempt++ == 1;
            var result = engine.submit(command);
            if (expected) assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, result, result::toString);
            else assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class, result, result::toString);
            assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind());
        }
        var after = codec.decode(engine.checkpoint().canonicalState());
        assertEquals(state.humanPopulation().nutrition(), after.humanPopulation().nutrition());
        assertEquals(state.actorLocations(), after.actorLocations());
        assertEquals(state.actorExecutions(), after.actorExecutions());
        assertEquals(state.fencedRecovery(), after.fencedRecovery());
        assertTrue(after.humanPopulation().meals().isEmpty());
        return after;
    }

}
