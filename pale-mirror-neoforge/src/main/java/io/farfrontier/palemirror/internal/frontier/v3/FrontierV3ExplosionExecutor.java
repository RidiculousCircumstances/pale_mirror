package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ExplosionObservation;
import io.farfrontier.palemirror.frontier.v3.model.ExplosionItemImpact;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemDestroyed;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemCustodyChanged;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Executes one already-durable v3 blast; normal Minecraft geometry remains completely unrestricted. */
final class FrontierV3ExplosionExecutor {
    private FrontierV3ExplosionExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = state(runtime); if (state == null) return;
        state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.EXPLOSION)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .sorted(Comparator.comparing(PhysicalIntent::id)).findFirst().ifPresent(intent -> execute(level, runtime, intent));
    }

    static boolean observeDetonation(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId intentId,
                                     java.util.List<BlockPos> affected, java.util.List<Entity> entities) {
        FrontierWorldState state = state(runtime); if (state == null) return false;
        PhysicalIntent intent = state.physicalIntents().get(intentId);
        if (intent == null || intent.kind() != PhysicalIntentKind.EXPLOSION || intent.status() != PhysicalIntentStatus.RUNNING) return false;
        return FrontierV3ManagedExplosionLedger.get(level).capture(level, level.getGameTime(), intentId, affected, entities, state,
                FrontierV3GrayboxLedger.get(level), FrontierV3InfectionOverlayLedger.get(level),
                position -> state.bootstrap().bounds().contains(new BlockPosition(position.getX(), position.getY(), position.getZ())));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent) {
        BlockPos origin = origin(intent); if (origin == null) { unknown(runtime, intent.id(), "non-block-origin"); return; }
        if (!level.hasChunkAt(origin)) return;
        FrontierV3ManagedExplosionLedger ledger = FrontierV3ManagedExplosionLedger.get(level);
        if (intent.status() == PhysicalIntentStatus.RUNNING) { reconcile(level, runtime, intent, ledger); return; }
        FrontierWorldState state = state(runtime); if (state == null) return;
        Entity source = FrontierV3SceneExecutor.explosionCause(level, state, intent).orElse(null);
        if (source == null) { unknown(runtime, intent.id(), "missing-hot-bomber"); return; }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        FrontierV3ExplosionExecutionScope.run(intent.id(), () -> level.explode(source, origin.getX() + 0.5D, origin.getY() + 0.5D, origin.getZ() + 0.5D,
                (float) intent.radiusBlocks(), false, Level.ExplosionInteraction.TNT));
        if (!ledger.has(intent.id())) unknown(runtime, intent.id(), "missing-detonation-observation");
    }

    private static void reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent,
                                  FrontierV3ManagedExplosionLedger ledger) {
        Optional<FrontierV3ManagedExplosionLedger.ItemReady> item = ledger.nextItem(intent.id(), level.getGameTime());
        if (item.isPresent()) { reconcileItem(level, runtime, ledger, item.orElseThrow()); return; }
        Optional<FrontierV3ManagedExplosionLedger.EntityReady> entity = ledger.nextEntity(intent.id(), level.getGameTime());
        if (entity.isPresent()) { reconcileEntity(level, ledger, entity.orElseThrow()); return; }
        Optional<FrontierV3ManagedExplosionLedger.BlockReady> block = ledger.nextBlock(intent.id(), level.getGameTime());
        if (block.isEmpty()) {
            FrontierV3ManagedExplosionLedger.Completion completion = ledger.completeIfResolved(intent.id(), level.getGameTime()).orElse(null);
            if (completion == null) return;
            ExplosionObservation receipt = new ExplosionObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                    intent.id(), intent.origin(), intent.radiusBlocks(), completion.affectedBlockCount(), completion.changedBlockCount(), completion.entityImpacts(),
                    completion.itemImpacts(), completion.affectedInfectionOverlayCount(), completion.changedInfectionOverlayCount());
            if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt), "confirmed")) throw new IllegalStateException("managed explosion confirmation was rejected");
            return;
        }
        FrontierV3ManagedExplosionLedger.BlockReady value = block.orElseThrow(); BlockPos position = value.candidate().blockPos(); if (!level.hasChunkAt(position)) return;
        boolean changed = !level.getBlockState(position).equals(value.candidate().baseline(level.registryAccess()));
        if (changed) {
            FrontierV3PhysicalObservationExecutor.recordManagedExplosionDelta(level, runtime, intent.id(), value.candidate());
            value.candidate().infectionCell().ifPresent(cell -> FrontierV3InfectionOverlayLedger.get(level).conflict(cell));
        }
        ledger.resolveBlock(value, changed);
    }

    private static void reconcileEntity(ServerLevel level, FrontierV3ManagedExplosionLedger ledger, FrontierV3ManagedExplosionLedger.EntityReady ready) {
        if (!level.hasChunkAt(ready.candidate().blockPos())) return;
        Entity actual = level.getEntity(ready.candidate().entityId()); ledger.resolveEntity(ready, actual == null || actual.isRemoved());
    }

    private static void reconcileItem(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierV3ManagedExplosionLedger ledger,
                                      FrontierV3ManagedExplosionLedger.ItemReady ready) {
        FrontierV3ManagedExplosionLedger.ItemCandidate candidate = ready.candidate(); BlockPos position = candidate.blockPos();
        if (!level.hasChunkAt(position)) return;
        FrontierWorldState state = state(runtime); if (state == null) return;
        var item = state.inventory().items().get(candidate.itemId()); InventoryCustody.ContainerSlot source = new InventoryCustody.ContainerSlot(candidate.containerId(), candidate.slot());
        if (item == null) { ledger.resolveItem(ready, ExplosionItemImpact.Outcome.DESTROYED); return; }
        if (!item.custody().equals(source)) { ledger.resolveItem(ready, ExplosionItemImpact.Outcome.TRANSFERRED); return; }
        if (level.getBlockEntity(position) instanceof ChestBlockEntity chest && candidate.slot() < chest.getContainerSize()
                && FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(candidate.slot()), item)) { ledger.resolveItem(ready, ExplosionItemImpact.Outcome.RETAINED); return; }
        List<ItemEntity> carriers = level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(16.0D), entity -> FrontierV3CargoHandoffExecutor.exactMatch(entity.getItem(), item));
        List<Player> holders = level.players().stream().filter(player -> FrontierV3InventoryObservationExecutor.hasExactItem(player, item)).map(player -> (Player) player).toList();
        if (carriers.size() + holders.size() != 1) { ledger.resolveItem(ready, carriers.isEmpty() && holders.isEmpty() ? destroy(runtime, item.id(), source, "explosion") : ExplosionItemImpact.Outcome.CONFLICT); return; }
        if (!carriers.isEmpty()) {
            ItemEntity carrier = carriers.getFirst(); ItemStack stack = carrier.getItem(); FrontierV3CargoHandoffExecutor.bindWorldCarrier(stack, carrier.getUUID()); carrier.setItem(stack);
            move(runtime, item.id(), source, new InventoryCustody.WorldCarrier(carrier.getUUID())); ledger.resolveItem(ready, ExplosionItemImpact.Outcome.TRANSFERRED); return;
        }
        move(runtime, item.id(), source, new InventoryCustody.Player(holders.getFirst().getUUID())); ledger.resolveItem(ready, ExplosionItemImpact.Outcome.TRANSFERRED);
    }

    private static ExplosionItemImpact.Outcome destroy(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, io.farfrontier.palemirror.frontier.v3.api.SubjectId itemId, InventoryCustody source, String cause) {
        submit(runtime, "item-destroyed", itemId.value(), new ExactItemDestroyed(itemId, source, cause)); return ExplosionItemImpact.Outcome.DESTROYED;
    }
    private static void move(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, io.farfrontier.palemirror.frontier.v3.api.SubjectId itemId, InventoryCustody from, InventoryCustody to) {
        submit(runtime, "item-moved", itemId.value(), new ExactItemCustodyChanged(itemId, from, to));
    }
    private static void submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String action, String subject, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = new CommandId("executor:explosion-" + action + "-" + subject.replace(':', '-') + "-r" + checkpoint.revision().value());
        FrontierCommand request = new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), payload);
        CommandResult result = runtime.submit(request).orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("managed explosion exact-item observation was rejected: " + result);
    }

    private static BlockPos origin(PhysicalIntent intent) {
        long scale = FixedScalar.SCALE;
        if (intent.origin().x().raw() % scale != 0L || intent.origin().y().raw() % scale != 0L || intent.origin().z().raw() % scale != 0L) return null;
        try { return new BlockPos(Math.toIntExact(intent.origin().x().raw() / scale), Math.toIntExact(intent.origin().y().raw() / scale), Math.toIntExact(intent.origin().z().raw() / scale)); }
        catch (ArithmeticException ignored) { return null; }
    }

    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                      Optional<io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation> observation, String phase) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = new CommandId("executor:explosion-" + phase + "-" + id.value().replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalIntentTransition(id, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result instanceof CommandResult.Accepted;
    }
    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) { transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase); }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) { return runtime.checkpointImage().map(image -> new FrontierWorldStateCodec().decode(image.canonicalState())).orElse(null); }
}
