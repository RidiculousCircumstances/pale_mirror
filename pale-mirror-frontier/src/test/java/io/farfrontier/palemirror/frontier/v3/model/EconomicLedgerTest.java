package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomicLedgerTest {
    @Test
    void bootstrapRegistersEveryCurrentExactClaimHolderAsAnAccount() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:economy-owners"), 91L));

        assertEquals(14, state.inventory().economics().accounts().size());
        assertEquals(EconomicOwnerKind.SETTLEMENT_TREASURY, state.inventory().economics().require(new SubjectId("settlement:1")).ownerKind());
        assertEquals(EconomicOwnerKind.HIVE_COLLECTIVE, state.inventory().economics().require(state.bootstrap().hive().id()).ownerKind());
        assertEquals(EconomicOwnerKind.PUBLIC_INFRASTRUCTURE, state.inventory().economics().require(FrontierRouteNetwork.OWNER).ownerKind());
        assertTrue(state.inventory().containers().values().stream().allMatch(container -> state.inventory().economics().accounts().containsKey(container.ownerId())));
        assertTrue(state.inventory().items().values().stream().allMatch(item -> state.inventory().economics().accounts().containsKey(item.economicOwnerId())));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test
    void missingAccountCannotOwnAnExactContainerItemOrCargo() {
        SubjectId owner = new SubjectId("settlement:one"); SubjectId container = new SubjectId("container:one");
        Map<SubjectId, ContainerRecord> containers = Map.of(container, new ContainerRecord(container, owner, 1));
        Map<SubjectId, ContainerSurface> surfaces = Map.of(container, new ContainerSurface(container, new BlockPosition(0, 64, 0), ContainerSurfaceStatus.UNMATERIALIZED));

        assertThrows(IllegalArgumentException.class, () -> new ExactInventory(containers, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), surfaces,
                new EconomicLedger(Map.of())));
    }

    @Test
    void transferIsDurableZeroSumAndRejectsCreditOrReplayViolations() {
        WorldId world = new WorldId("frontier:economy-transfer");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        SubjectId payer = new SubjectId("settlement:1"); SubjectId payee = base.initialState().bootstrap().hive().id();
        Map<SubjectId, EconomicAccount> accounts = new LinkedHashMap<>(base.initialState().inventory().economics().accounts());
        accounts.put(payer, new EconomicAccount(payer, EconomicOwnerKind.SETTLEMENT_TREASURY, EconomicAccountStatus.ACTIVE, FixedScalar.whole(8), FixedScalar.ZERO));
        FrontierWorldState funded = base.initialState().withInventory(base.initialState().inventory().withEconomics(new EconomicLedger(accounts)));
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(world, funded,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                base.initialSchedules(), base.transactionCommitter());
        var engine = FrontierEngines.create(configuration); EconomicTransfer transfer = new EconomicTransfer(payer, payee, FixedScalar.whole(3), "wage.test");

        CommandResult result = engine.submit(command(engine, world, "command:economy-transfer", transfer));
        assertInstanceOf(CommandResult.Accepted.class, result);
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(FixedScalar.whole(5), after.inventory().economics().require(payer).balance());
        assertEquals(FixedScalar.whole(3), after.inventory().economics().require(payee).balance());
        assertEquals(transfer, FrontierWorldRuntimeDefinition.payloadCodecs().decode(transfer.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(transfer)));
        assertEquals(after, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after)));
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(engine, world, "command:economy-overdraft",
                new EconomicTransfer(payer, payee, FixedScalar.whole(6), "wage.test"))));
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(engine, world, "command:economy-transfer", transfer)));
    }

    private static FrontierCommand command(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine engine, WorldId world, String id, EconomicTransfer transfer) {
        CommandId command = new CommandId(id); var checkpoint = engine.checkpoint();
        return new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(command), transfer);
    }
}
