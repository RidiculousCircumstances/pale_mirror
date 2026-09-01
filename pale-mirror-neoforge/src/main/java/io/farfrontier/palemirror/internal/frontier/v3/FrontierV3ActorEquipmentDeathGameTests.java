package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** Exact weapon fate at an ordinary managed body death: drop when seen, loss when not. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ActorEquipmentDeathGameTests {
    private FrontierV3ActorEquipmentDeathGameTests() { }

    @GameTest(batch = "pm-frontier-v3-equipment-death", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void deathDropsTheSameSeenStackAndDestroysOnlyAChangedHandClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                fixtureRuntime("frontier:actor-equipment-death-game-test");
        ExactItemStack first = exactWeapon(runtime); SubjectId firstActor = ((InventoryCustody.Actor) first.custody()).actorId();
        Villager firstBody = body(level, state(runtime), firstActor, helper.absolutePos(new BlockPos(36, 8, 0)));
        firstBody.setItemSlot(EquipmentSlot.MAINHAND, FrontierV3CargoHandoffExecutor.materializedStack(first)); level.addFreshEntity(firstBody);
        helper.assertTrue(FrontierV3ActorEquipmentDeathExecutor.resolve(level, runtime, firstBody), "the exact actor-held stack must be accounted before the managed body dies");
        ExactItemStack released = state(runtime).inventory().items().get(first.id());
        helper.assertTrue(released.custody() instanceof InventoryCustody.WorldCarrier, "the same canonical stack must become one world carrier");
        helper.assertTrue(firstBody.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "the durable release clears the physical hand before vanilla death");
        InventoryCustody.WorldCarrier firstCarrier = (InventoryCustody.WorldCarrier) released.custody();
        helper.assertValueEqual(state(runtime), new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state(runtime))),
                "the released world-carrier custody survives snapshot hydration without a second stack");
        // GameTest batches may delay the global UUID index even after the nearby entity is live.
        // The physical postcondition is the one local, UUID-bound exact stack, not index timing.
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, firstBody.getBoundingBox().inflate(2.0D),
                            drop -> firstCarrier.carrierId().equals(drop.getUUID()) && FrontierV3CargoHandoffExecutor.exactMatch(drop.getItem(), first)).size() == 1,
                    "one tagged physical drop proves the matching hand was released instead of recreated");
            runtime.shutdown();
            FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> changedRuntime =
                    fixtureRuntime("frontier:actor-equipment-death-changed-hand-game-test");
            ExactItemStack second = exactWeapon(changedRuntime); SubjectId secondActor = ((InventoryCustody.Actor) second.custody()).actorId();
            Villager secondBody = body(level, state(changedRuntime), secondActor, helper.absolutePos(new BlockPos(40, 8, 0)));
            secondBody.setItemSlot(EquipmentSlot.MAINHAND, Items.STICK.getDefaultInstance()); level.addFreshEntity(secondBody);
            helper.assertTrue(FrontierV3ActorEquipmentDeathExecutor.resolve(level, changedRuntime, secondBody), "a changed physical hand is still an accounted equipment outcome");
            helper.assertTrue(!state(changedRuntime).inventory().items().containsKey(second.id()), "a missing exact hand is destroyed rather than replaced or dropped");
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, secondBody.getBoundingBox().inflate(2.0D), drop -> FrontierV3CargoHandoffExecutor.exactMatch(drop.getItem(), second)).isEmpty(),
                    "a changed hand cannot manufacture an exact physical drop");
            changedRuntime.shutdown(); helper.succeed();
        });
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> fixtureRuntime(String world) {
        FrontierEngineConfiguration<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> base =
                FrontierWorldRuntimeDefinition.configuration(new WorldId(world), 91L);
        FrontierWorldState initial = base.initialState(); SubjectId actor = new SubjectId("resident:1-1");
        SubjectId depot = FrontierWorldState.depotId(initial.humanPopulation().residents().get(actor).settlementId());
        ExactItemStack sword = new ExactItemStack(new SubjectId("item:actor-death-sword"), initial.humanPopulation().residents().get(actor).settlementId(),
                "minecraft:iron_sword", 1, new InventoryCustody.ContainerSlot(depot, initial.inventory().firstFreeSlot(depot).orElseThrow()));
        FrontierWorldState state = initial.withInventory(initial.inventory().store(sword)
                .moveObservedItem(sword.id(), sword.custody(), new InventoryCustody.Actor(actor)));
        FrontierEngineConfiguration<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> configured = new FrontierEngineConfiguration<>(
                base.worldId(), state, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                new FrontierWorldStateCodec(state.bootstrap()), base.projectionMapper(), base.limits(), base.initialSchedules(),
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(configured, new EphemeralStore(), 10_000);
    }

    private static ExactItemStack exactWeapon(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return state(runtime).inventory().items().get(new SubjectId("item:actor-death-sword"));
    }

    private static Villager body(ServerLevel level, FrontierWorldState state, SubjectId actorId, BlockPos position) {
        Villager body = EntityType.VILLAGER.create(level); if (body == null) throw new IllegalStateException("GameTest could not create Villager");
        body.setUUID(FrontierV3AmbientActorExecutor.entityId(state, actorId)); body.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        body.getPersistentData().putString(FrontierV3AmbientActorExecutor.ACTOR_KEY, actorId.value()); body.getPersistentData().putString(FrontierV3AmbientActorExecutor.KIND_KEY, "RESIDENT");
        return body;
    }

    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
    }

    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) { return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value()); }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision coveredRevision) { throw new UnsupportedOperationException("GameTest does not compact"); }
    }
}
