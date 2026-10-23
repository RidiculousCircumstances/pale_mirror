package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Mining supplies its own effect outcome and resource checkpoint to the common death protocol. */
final class FrontierV3ExtractionDeathResources {
    private FrontierV3ExtractionDeathResources() { }
    static FrontierV3ActorDeathResourceComposition.AfterFatality prepare(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id) {
        var state = runtime.decodedState().orElseThrow(); var job = ExtractionDeathResources.work(state, id.actorId()).orElse(null);
        if (job == null) return () -> { };
        var effect = job.pending().flatMap(step -> capture(level, state, body, job, step));
        var hand = body.getMainHandItem().copy();
        return FrontierV3ActorDeathResourceComposition.retained(effect.stream().toList(), () -> settle(level, runtime, body, id, hand));
    }
    static void recover(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, FrontierV3BodyResourceSnapshot snapshot) {
        var hand = snapshot.slots(level).get(new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN));
        if (hand == null) throw new IllegalStateException("mining death lost its declared pre-loot tool slot");
        settle(level, runtime, body, id, hand);
    }
    static boolean settled(FrontierWorldState state, ActorBodyId id) {
        return ExtractionDeathResources.work(state, id.actorId()).isEmpty();
    }
    private static void settle(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, ItemStack before) {
        var state = runtime.decodedState().orElseThrow(); ActorBodyAuthority.requireRetiredDeath(state, id);
        var job = ExtractionDeathResources.work(state, id.actorId()).orElse(null);
        if (job == null || job.pending().isPresent()) return;
        var tool = state.inventory().items().get(job.toolId());
        if (tool != null && tool.custody().equals(new InventoryCustody.Actor(id.actorId()))) {
            // The resource owner accepts only its retained exact tool, not another owner's
            // service item or an unexplained empty hand after native loot/restart.
            if (!ItemStack.matches(before, body.getMainHandItem()) || !FrontierV3ExactItemPresentation.exactMatch(before, tool)) return;
            FrontierV3ExactHeldItemDeath.resolve(level, runtime, body, tool, "extraction:" + job.id().value(), "extraction_tool");
            state = runtime.decodedState().orElseThrow();
        }
        if (!ExtractionDeathResources.resourcesSettled(state, job)) return;
        FrontierV3CommandSubmission.submit(runtime, "extraction-death-settled", job.id().value(),
                new ExtractionDeathSettled(new ActorActuationId(id, job.execution()), job.revision()));
    }
    private static Optional<FrontierV3OperationAftermath> capture(ServerLevel level, FrontierWorldState state, Mob body,
            ExtractionWork job, ExtractionPhysicalStep step) {
        var actor = FrontierV3OperationEntityParticipant.current(body, step.observation().actuation().body().physicalEpoch());
        List<FrontierV3MinecraftOperation.Participant> ports;
        FrontierV3OperationReceipt.Protocol protocol;
        BooleanSupplier before; BooleanSupplier after;
        List<FungiblePhysicalObservation.Stack> destination = List.of();
        Optional<BlockExtraction.Block> external = Optional.empty();
        switch (step) {
            case ExtractionPhysicalStep.BlockWork block -> {
                var source = block.extraction().target(); var pos = new BlockPos(source.x(), source.y(), source.z());
                if (!FrontierV3LoadedWorld.ready(level, pos)) return Optional.empty();
                var witness = FrontierV3WorksiteEvidence.at(level, FrontierV3GrayboxLedger.get(level), state, pos);
                var key = new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, job.siteId(),
                        WorksiteBlock.Role.RESOURCE, job.target().orElseThrow().key().cell());
                if (witness == null || !witness.declaration().key().equals(key)) return Optional.empty();
                int count = block.carriedBefore() + block.extraction().output().getFirst().quantity();
                external = witness.externalSuccessor(); var expectedAfter = external.orElse(block.extraction().definition().after());
                before = () -> witness.phase() == FrontierV3WorksiteBlockWitness.Phase.SETTLED
                        && FrontierV3ToolUse.before(body.getMainHandItem(), block.toolUse())
                        && FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(pos)).equals(block.extraction().definition().before())
                        && plain(body.getOffhandItem(), job.outputKind(), block.carriedBefore());
                after = () -> witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED
                        && FrontierV3ToolUse.after(body.getMainHandItem(), block.toolUse())
                        && witness.effectOperation().equals(Optional.of(block.extraction().operationId()))
                        && FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(pos)).equals(expectedAfter)
                        && plain(body.getOffhandItem(), job.outputKind(), count);
                destination = List.of(ExtractionPhysicalStateSupport.hand(state, job, count));
                ports = List.of(FrontierV3OperationChunks.current(level, pos), actor);
                protocol = FrontierV3OperationReceipt.Protocol.EXTRACTION_BLOCK;
            }
            case ExtractionPhysicalStep.Equipment equipment -> {
                var container = FrontierV3PhysicalContainer.loaded(level, state, equipment.slot().containerId()).orElse(null);
                if (container == null) return Optional.empty();
                var tool = state.inventory().items().get(job.toolId()); var chest = container.inventory();
                boolean take = job.phase() == ExtractionWork.Phase.TAKE_TOOL;
                before = () -> take ? body.getMainHandItem().isEmpty() && FrontierV3ExactItemPresentation.exactMatch(chest.getItem(equipment.slot().slot()), tool)
                        : chest.getItem(equipment.slot().slot()).isEmpty() && FrontierV3ExactItemPresentation.exactMatch(body.getMainHandItem(), tool);
                after = () -> take ? chest.getItem(equipment.slot().slot()).isEmpty() && FrontierV3ExactItemPresentation.exactMatch(body.getMainHandItem(), tool)
                        : body.getMainHandItem().isEmpty() && FrontierV3ExactItemPresentation.exactMatch(chest.getItem(equipment.slot().slot()), tool);
                ports = List.of(container.persistence().get(), actor); protocol = FrontierV3OperationReceipt.Protocol.EXTRACTION_EQUIPMENT;
            }
            case ExtractionPhysicalStep.Cargo cargo -> {
                var container = FrontierV3PhysicalContainer.loaded(level, state, job.storageTarget().containerId()).orElse(null);
                if (container == null) return Optional.empty();
                var transfer = cargo.transfer(); var order = ExtractionWorkEffects.cargo(state, job);
                var adapter = new FrontierV3ActorItemTransfer.FungibleStep(order, container, body, body.getUUID(),
                        transfer.source(), transfer.destinationSlot(), transfer.destinationBefore());
                before = adapter::before; after = adapter::after;
                destination = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(container.inventory(), state, container.containerId());
                ports = List.of(container.persistence().get(), actor); protocol = FrontierV3OperationReceipt.Protocol.EXTRACTION_CARGO;
            }
        }
        boolean applied = after.getAsBoolean();
        if (!applied && !before.getAsBoolean()) return Optional.empty();
        var consequence = new ExtractionRetiredEffectObserved(new ExtractionHotObserved(job.id(), step, List.of(),
                applied ? destination : List.of(), applied ? external : Optional.empty()), applied);
        var declaration = FrontierV3MinecraftOperation.declaration(protocol, job.id(), new ExtractionHotPrepared(job.id(), step), ports);
        return FrontierV3OperationAftermath.capture(level, declaration, ports, consequence, applied, applied ? after : before);
    }
    private static boolean plain(ItemStack stack, String kind, int count) {
        return count == 0 ? stack.isEmpty() : stack.getCount() == count
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(kind)
                && ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem(), count));
    }
}
