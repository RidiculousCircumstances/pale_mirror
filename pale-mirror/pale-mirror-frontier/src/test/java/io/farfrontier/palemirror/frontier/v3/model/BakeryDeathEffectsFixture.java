package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Branches at the real vertical fixture's four prepared transfers. Modeled facts, not native acceptance. */
final class BakeryDeathEffectsFixture {
    private BakeryDeathEffectsFixture() { }
    static FrontierWorldState observe(FrontierWorldState prepared, SubjectId subject, BakeryHotEffectObserved effect) {
        var job = prepared.productionJobs().get(effect.jobId()); var actor = job.workerId();
        var identity = new ActorActuationId(ActorBodyAuthority.current(prepared, actor),
                BakeryDeathResources.execution(prepared, actor).orElseThrow());
        var normal = ProductionProcess.reduceBakeryHotEffectObserved(prepared, subject, effect);
        var dead = ActorBodyAuthority.died(prepared, ModeledActorBodyFacts.death(prepared, actor,
                prepared.actorLocations().get(actor).body(), "fixture:bakery-prepared-death"), FrontierActorDeathConsequences.INSTANCE, 1000);
        assertTrue(ActorInventoryInteractionFences.pending(dead, actor));
        for (boolean applied : List.of(false, true)) {
            var outcome = new BakeryRetiredEffectObserved(identity, applied ? effect : new BakeryHotEffectObserved(effect.jobId(),
                    effect.leaseId(), effect.phase(), effect.observedWorker(), effect.sourceEpoch(), effect.destinationEpoch(), List.of(), List.of()), applied);
            var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
            assertEquals(outcome, codecs.decode(outcome.type(), codecs.encode(outcome)));
            assertThrows(IllegalArgumentException.class, () -> apply(prepared, outcome));
            var next = apply(dead, outcome);
            assertEquals(applied ? normal.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase() : effect.phase(),
                    next.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase());
            assertTrue(next.productionJobs().get(job.id()).bakeryWork().orElseThrow().pendingPhysicalStep().isEmpty());
            var expectedResources = applied ? normal.inventory().fungibleResources() : dead.inventory().fungibleResources();
            // The full command may release a drained stationary endpoint's native bindings.
            // That is not stock disposal: quantities, ownership and allocations must be identical.
            assertEquals(expectedResources.lots(), next.inventory().fungibleResources().lots());
            assertEquals(expectedResources.accounts(), next.inventory().fungibleResources().accounts());
            assertEquals(expectedResources.claims(), next.inventory().fungibleResources().claims());
            assertEquals(applied ? normal.productionStations() : dead.productionStations(), next.productionStations());
            assertEquals(dead.inventory().economics(), next.inventory().economics());
            assertThrows(IllegalArgumentException.class, () -> apply(next, outcome));
            var codec = new FrontierWorldStateCodec();
            assertEquals(next, codec.decode(codec.encode(next)));
            if (next.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.DEPOT_DELIVERY)
                assertTrue(ActorInventoryInteractionFences.pending(next, actor), "free bread still belongs to the retained job until its explicit retirement");
            checkRetirement(next, identity);
        }
        return normal;
    }
    private static void checkRetirement(FrontierWorldState state, ActorActuationId identity) {
        var job = state.productionJobs().get(identity.execution().activityOwnerId()); var work = job.bakeryWork().orElseThrow();
        boolean carried = work.phase() == BakeryWorkState.Phase.STATION_LOAD || work.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY;
        var source = state.inventory().fungibleResources();
        long epoch = carried ? source.bindings().values().stream().filter(binding -> binding.accountId().equals(work.actorAccountId()))
                .findFirst().orElseThrow().authorityEpoch() : 0;
        var ground = carried ? java.util.Optional.of(new ResourceCustody.Ground(java.util.UUID.nameUUIDFromBytes(
                ("bakery-death-" + work.phase()).getBytes(java.nio.charset.StandardCharsets.UTF_8)), new BlockPosition(0, 64, 0)))
                : java.util.Optional.<ResourceCustody.Ground>empty();
        var receipt = new BakeryDeathSettled(identity, work.phase(), epoch, ground);
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(receipt, codecs.decode(receipt.type(), codecs.encode(receipt)));
        var next = apply(state, receipt);
        assertFalse(next.productionJobs().containsKey(job.id()));
        assertTrue(BakeryDeathResources.execution(next, job.workerId()).isEmpty());
        assertEquals(source.lots(), next.inventory().fungibleResources().lots());
        assertEquals(state.inventory().economics().accounts(), next.inventory().economics().accounts());
        assertTrue(next.inventory().fungibleResources().claims().values().stream().noneMatch(claim -> claim.claimantId().equals(job.id())));
        assertEquals(work.phase() == BakeryWorkState.Phase.DELIVERED ? StrategicTaskStatus.COMPLETED : StrategicTaskStatus.BLOCKED,
                next.strategicPlans().tasks().get(job.taskId()).status());
        if (carried) assertEquals(BakeryCargo.quantity(job), next.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> ground.orElseThrow().equals(account.custody())).flatMap(account -> account.lotQuantities().values().stream())
                .mapToInt(Integer::intValue).sum());
        var codec = new FrontierWorldStateCodec(); assertEquals(next, codec.decode(codec.encode(next)));
        assertThrows(IllegalArgumentException.class, () -> apply(next, receipt));
    }
    private static FrontierWorldState apply(FrontierWorldState state, FrontierPayload payload) {
        var id = new CommandId("command:bakery-death-cut");
        var command = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, id, state.bootstrap().worldId(), new Revision(100),
                new SimInstant(1000), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload);
        var plan = FrontierWorldRuntimeDefinition.planCommand(state, command);
        if (!(plan instanceof CommandPlan.Accepted accepted)) throw new IllegalArgumentException("bakery death cut rejected: " + plan);
        return GoodsTradeTest.applyEvents(state, accepted.events(), 1000, "economy");
    }
}
