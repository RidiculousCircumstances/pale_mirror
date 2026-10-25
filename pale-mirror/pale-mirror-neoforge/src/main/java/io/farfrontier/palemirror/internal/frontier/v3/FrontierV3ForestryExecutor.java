package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Forest effects only. The common controllers alone own bodies, routes, inventories and source authority. */
final class FrontierV3ForestryExecutor {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private FrontierV3ForestryExecutor() { }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        for (var job : state.forest().work().values().stream().filter(value -> !value.terminal())
                .sorted(Comparator.comparing((ForestryWork value) -> value.pending().isEmpty())
                        .thenComparing(ForestryWork::id)).toList()) {
            if (progress(level, runtime, state, job)) return;
        }
    }
    private static boolean progress(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, ForestryWork job) {
        if (runtime.awaitingCanonicalPredecessor()) return false;
        var actor = job.execution().actorId();
        var entity = level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), actor));
        if (entity == null) return savedDeparture(level, runtime, state, job);
        if (!(entity instanceof Villager worker) || !worker.isAlive()
                || !FrontierV3ActorBodyController.recognizesRecordedBody(level, state, worker)) return false;
        var lease = state.ambientLeases().get(actor);
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT || ActorExecutionCoordinator.sceneOwns(state, actor)) return false;
        var custody = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(job.carriedAccount())).toList();
        int carried = ForestryWorkAuthority.carried(state, job);
        if (carried > 0 && custody.isEmpty() && job.pending().isEmpty()
                && job.execution().equals(state.actorExecutions().current(ActorActivityKind.FORESTRY).get(actor))
                && FrontierV3ActorCarryProjection.witnessed(state, actor, worker)) {
            // Binding requires this exact current actuator, not the suspended work owner.
            var body = ActorBodyAuthority.current(state, actor);
            return submit(level, runtime, job.id(), "hand-materialized", new ForestryPhysicalEvent.HandBoundary(job.id(),
                    new ActorActuationId(body, job.execution()), ForestryPhysicalEvent.HandBoundary.Boundary.MATERIALIZED,
                    ForestryPhysicalStateSupport.hand(state, job, carried)));
        }
        FrontierV3ActorActuation actuation;
        try { actuation = FrontierV3ActorActuation.capture(state, worker, job.execution(), runtime::decodedState); }
        catch (IllegalArgumentException inactive) { return false; }
        if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, worker) || !actuation.current(worker)) return false;
        state = runtime.decodedState().orElseThrow();
        if (job.phase() == ForestryWork.Phase.SELECT_TREE) return false;
        if (!job.equals(state.forest().work().get(job.id())) || state.actorMovements().containsKey(actor)
                || !FrontierV3SurfaceObservation.at(worker, job.movementOrder().legalStations().getFirst()))
            return false;
        var step = job.pending().orElse(null);
        if (step == null) {
            long tick = runtime.executionView().orElseThrow().instant().ticks();
            if (job.felling().isEmpty() && !ResidentActivityCoordinator.ordinaryWorkPermitted(state, actor, tick)) return false;
            if (job.phase() == ForestryWork.Phase.FELL && job.felling().isEmpty()
                    && job.labour().filter(labour -> labour.running() && tick < labour.activeUntilTick()).isPresent()) {
                FrontierV3BlockWorkGesture.present(worker, ForestryWorkSelection.contact(state.forest().require(job.target().orElseThrow().tree())));
                return false;
            }
            try {
                step = prepare(level, state, job, worker, actuation, tick);
                if (step == null) return false;
                return submit(level, runtime, job.id(), "effect-prepared", new ForestryPhysicalEvent.Prepared(job.id(), step));
            } catch (IllegalArgumentException unavailable) {
                LOGGER.debug("PMV3 forestry preparation waiting job={} phase={} reason={}", job.id(), job.phase(), unavailable.getMessage());
                return false;
            }
        }
        try {
            var effect = apply(level, runtime, runtime.decodedState().orElseThrow(), job, step, worker, actuation, () -> {
                var current = runtime.decodedState().orElseThrow();
                if (job.equals(current.forest().work().get(job.id()))) progress(level, runtime, current, job);
            });
            if (effect == null) return false;
            var retainedStep = step;
            return FrontierV3OperationConfirmation.submit(level, runtime, effect.ticket(), effect.receipt(), () -> {
              var current = runtime.decodedState().orElseThrow();
              if (retainedStep instanceof ForestryPhysicalStep.Fell block) {
                var tree = current.forest().require(job.felling().orElseThrow().tree());
                int log = tree.declaration().logs().indexOf(tree.declaration().origin(), block.effect().target());
                var declared = ForestryWorksiteBlocks.cell(tree, log); var ledger = FrontierV3GrayboxLedger.get(level);
                if (tree.extracted(log)) ledger.worksite(new FrontierV3WorksiteBlockWitness(declared, block.effect().definition().before(),
                        FrontierV3WorksiteBlockWitness.Phase.SETTLED));
                else ledger.worksite(new FrontierV3WorksiteBlockWitness(declared, block.effect().definition().before(), FrontierV3WorksiteBlockWitness.Phase.EXTERNAL));
                ledger.persist(level);
              }
              FrontierV3ActorCarryProjection.rememberConfirmed(runtime.decodedState().orElseThrow(), actor, worker);
            });
        } catch (IllegalArgumentException changed) {
            // A retained ambiguous effect is never converted into a timeout unlock or replay.
            LOGGER.warn("PMV3 forestry effect unresolved job={} revision={} phase={} step={} reason={}",
                    job.id(), job.revision(), job.phase(), step, changed.getMessage());
            return false;
        }
    }
    private static ForestryPhysicalStep prepare(ServerLevel level, FrontierWorldState state, ForestryWork job,
            Villager worker, FrontierV3ActorActuation actuation, long tick) {
        var observation = new ActorHotObservation(actuation.id(), job.revision());
        if (job.phase() == ForestryWork.Phase.FELL) {
            if (job.felling().isEmpty() || !ForestryWorkSelection.targetCurrent(state, job)) return null;
            var batch = job.felling().orElseThrow(); var tree = state.forest().require(batch.tree());
            var custody = state.replicaCustody().custodyByScope().get(ForestrySourceCustody.scope(batch.tree()));
            if (custody == null || custody.status() != PhysicalCustodyLeaseStatus.ACQUIRED) return null;
            for (int i = job.appliedEffects(); i < batch.batch().effects().size(); i++) {
                var effect = batch.batch().effects().get(i); var source = new BlockPos(effect.target().x(), effect.target().y(), effect.target().z());
                if (!FrontierV3LoadedWorld.ready(level, source)) return null;
                var witness = FrontierV3WorksiteEvidence.at(level, FrontierV3GrayboxLedger.get(level), state, source);
                int log = tree.declaration().logs().indexOf(tree.declaration().origin(), effect.target());
                if (witness == null || !witness.settledCurrent(ForestryWorksiteBlocks.cell(tree, log),
                        FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(source)))) return null;
            }
            var tool = state.inventory().items().get(job.toolId());
            if (tool == null || !FrontierV3ExactItemPresentation.exactMatch(worker.getMainHandItem(), tool)) return null;
            int carried = ForestryWorkAuthority.carried(state, job); if (!plain(worker.getOffhandItem(), job.outputKind(), carried)) return null;
            var adapter = new FrontierV3MinecraftBatchExtraction(level, worker, actuation, state.bootstrap().ruleset().tools());
            var effect = adapter.prepareChild(batch.batch(), job.appliedEffects());
            return new ForestryPhysicalStep.Fell(observation, effect, job.appliedEffects(), carried, custody.authorityEpoch(),
                    io.farfrontier.palemirror.frontier.v3.model.tooling.ToolUse.prepare(tool, state.bootstrap().ruleset().tools(), effect.definition().toolRequirement()));
        }
        var container = ForestryServiceAccess.identity(job).pointId();
        var physical = FrontierV3PhysicalContainer.loaded(level, state, container).orElse(null);
        if (physical == null || !ReferenceContainerCustody.hasOperationalCustody(state, container)
                || !ServiceAccessCoordinator.available(state, ForestryServiceAccess.identity(job))) return null;
        long epoch = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(container)).authorityEpoch();
        if (job.phase() == ForestryWork.Phase.STORE) {
            var order = ForestryWorkEffects.cargo(state, job);
            var source = MaterialSourcePreparation.review(state, order).requireReady();
            var target = ContainerMaterialDestination.selectWhole(state, container, job.outputKind(),
                    ((ActorContainerItemOrder.Portion.Fungible) order.portion()).lotQuantities(), Optional.empty()).orElse(null);
            if (target == null) return null;
            var transfer = new ActorItemTransferStep(observation, source, epoch, target.slot(), target.before());
            var physicalStep = new FrontierV3ActorItemTransfer.FungibleStep(order, physical, worker, worker.getUUID(), source, target.slot(), target.before());
            return physicalStep.before() ? new ForestryPhysicalStep.Cargo(transfer) : null;
        }
        var order = ForestryWorkEffects.equipment(state, job); var slot = order.exactSlot();
        if (job.phase() == ForestryWork.Phase.TAKE_TOOL
                ? !worker.getMainHandItem().isEmpty() || !FrontierV3ExactItemPresentation.exactMatch(physical.inventory().getItem(slot.slot()),
                    ((ActorContainerItemOrder.Portion.Exact) order.portion()).item())
                : !physical.inventory().getItem(slot.slot()).isEmpty() || !FrontierV3ExactItemPresentation.exactMatch(worker.getMainHandItem(),
                    ((ActorContainerItemOrder.Portion.Exact) order.portion()).item())) return null;
        return new ForestryPhysicalStep.Equipment(new WorkEquipmentStep(observation, ((ActorContainerItemOrder.Portion.Exact)order.portion()).item(), slot, epoch));
    }
    private record Applied(UUID ticket, ForestryPhysicalEvent.Observed receipt) { }
    private static Applied apply(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, ForestryWork job,
            ForestryPhysicalStep step, Villager worker, FrontierV3ActorActuation actuation, Runnable resume) {
        if (!step.observation().actuation().equals(actuation.id()) || step.observation().scopeRevision() != job.revision())
            throw new IllegalArgumentException("forestry retained effect has a stale body/owner fence");
        switch (step) {
            case ForestryPhysicalStep.Fell block -> {
                var source = new BlockPos(block.effect().target().x(), block.effect().target().y(), block.effect().target().z());
                if (!FrontierV3LoadedWorld.ready(level, source)) return null;
                int resultCount = block.carriedBefore() + block.effect().output().getFirst().quantity();
                var adapter = new FrontierV3MinecraftBatchExtraction(level, worker, actuation, state.bootstrap().ruleset().tools());
                var ledger = FrontierV3GrayboxLedger.get(level); var witness = FrontierV3WorksiteEvidence.at(level, ledger, state, source);
                var tree = state.forest().require(job.felling().orElseThrow().tree());
                int log = tree.declaration().logs().indexOf(tree.declaration().origin(), block.effect().target());
                var key = ForestryWorksiteBlocks.cell(tree, log).key();
                if (witness == null || !witness.declaration().key().equals(key)) return null;
                boolean changedSuffix = witness.phase() != FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED
                        && job.felling().orElseThrow().batch().effects().subList(job.appliedEffects(), job.felling().orElseThrow().batch().effects().size())
                            .stream().anyMatch(effect -> {
                                var point = new BlockPos(effect.target().x(), effect.target().y(), effect.target().z());
                                if (!FrontierV3LoadedWorld.ready(level, point)) return true;
                                var evidence = FrontierV3WorksiteEvidence.at(level, ledger, state, point);
                                return evidence == null || evidence.phase() != FrontierV3WorksiteBlockWitness.Phase.SETTLED
                                        || !FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(point)).equals(effect.definition().before());
                            });
                if (changedSuffix) {
                    var ports = List.of(FrontierV3OperationChunks.current(level, source), FrontierV3OperationEntityParticipant.current(worker, actuation.id().body().physicalEpoch()));
                    var unused = FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.FORESTRY_BLOCK, job.id(), new ForestryPhysicalEvent.Prepared(job.id(), step), ports);
                    var cancelled = FrontierV3MinecraftOperation.cancelUnattempted(level, unused).orElse(null);
                    if (cancelled != null) FrontierV3OperationConfirmation.submitRecovery(level, runtime, cancelled.ticket(),
                            new ForestryPhysicalEvent.Cancelled(job.id(), step), Optional.empty());
                    return null;
                }
                boolean sourceAfter = FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(source)).equals(block.effect().definition().after());
                if (sourceAfter && witness.phase() != FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED)
                    throw new IllegalArgumentException("source postcondition alone is not proof of this worker's extraction");
                if (witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED
                        && !witness.effectOperation().equals(Optional.of(block.effect().operationId())))
                    throw new IllegalArgumentException("source block-half receipt belongs to a different operation");
                var external = witness.externalSuccessor();
                if (external.isPresent() && !external.orElseThrow().equals(FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(source))))
                    throw new IllegalArgumentException("split forestry successor lacks the current captured external fact");
                if (!sourceAfter && external.isEmpty()) {
                    if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                            new net.minecraft.world.phys.AABB(source).inflate(1.0D), entity -> entity.isAlive()
                                && FrontierV3BodyObservation.capture(entity).support().filter(support ->
                                    support.support().equals(block.effect().target())).isPresent()).isEmpty()) return null;
                }
                var participants = List.of(FrontierV3OperationChunks.current(level, source),
                        FrontierV3OperationEntityParticipant.current(worker, actuation.id().body().physicalEpoch()));
                var declaration = FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.FORESTRY_BLOCK, job.id(), new ForestryPhysicalEvent.Prepared(job.id(), step), participants);
                var operation = FrontierV3MinecraftOperation.applyDeferred(level, declaration, participants,
                    () -> FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(source)).equals(block.effect().definition().before())
                            && FrontierV3ToolUse.before(worker.getMainHandItem(), block.toolUse())
                            && plain(worker.getOffhandItem(), job.outputKind(), block.carriedBefore()), FrontierV3ManagedBlockWrites.effect(level, List.of(new FrontierV3ManagedBlockWrites.PlannedWrite(source,
                            FrontierV3MinecraftBlockExtraction.block(block.effect().definition().after()))), () -> {
                        var result = new BlockExtractionPort.Result[1];
                        FrontierV3WorksiteWrites.apply(key, () -> {
                            result[0] = adapter.apply(job.felling().orElseThrow().batch(), block.child());
                            return result[0] == BlockExtractionPort.Result.APPLIED;
                        });
                        if (result[0] != BlockExtractionPort.Result.APPLIED)
                            throw new IllegalArgumentException("retained forestry block effect refused: " + result[0]);
                        ledger.worksite(new FrontierV3WorksiteBlockWitness(witness.declaration(), witness.before(),
                                FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED, Optional.of(block.effect().operationId())));
                        ledger.persist(level);
                        worker.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(job.outputKind())), resultCount));
                        return FrontierV3ToolUse.apply(worker.getMainHandItem(), block.toolUse());
                    }, resume), () -> FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(source)).equals(external.orElse(block.effect().definition().after()))
                            && FrontierV3ToolUse.after(worker.getMainHandItem(), block.toolUse())
                            && plain(worker.getOffhandItem(), job.outputKind(), resultCount));
                if (!operation.complete()) return null;
                worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                return new Applied(operation.ticket(), new ForestryPhysicalEvent.Observed(job.id(), step, List.of(), List.of(ForestryPhysicalStateSupport.hand(state, job, resultCount)), external));
            }
            case ForestryPhysicalStep.Equipment equipment -> {
                var physical = FrontierV3PhysicalContainer.loaded(level, state, equipment.transfer().slot().containerId()).orElse(null);
                if (physical == null) return null;
                var chest = physical.inventory();
                var expected = state.inventory().items().get(job.toolId()); boolean take = job.phase() == ForestryWork.Phase.TAKE_TOOL;
                var participants = List.of(physical.persistence().get(), FrontierV3OperationEntityParticipant.current(worker, actuation.id().body().physicalEpoch()));
                var declaration = FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.FORESTRY_EQUIPMENT, job.id(), new ForestryPhysicalEvent.Prepared(job.id(), step), participants);
                var adapter = new FrontierV3EquipmentItemTransfer(chest, worker, expected, equipment.transfer().slot(), take
                        ? ActorContainerItemOrder.Direction.TAKE : ActorContainerItemOrder.Direction.PLACE);
                var operation = FrontierV3MinecraftOperation.applyDeferred(level, declaration, participants, adapter::before, FrontierV3NativeEffect.observed(adapter::apply), adapter::after);
                return operation.complete() ? new Applied(operation.ticket(), new ForestryPhysicalEvent.Observed(job.id(), step, List.of(), List.of(), Optional.empty())) : null;
            }
            case ForestryPhysicalStep.Cargo cargo -> {
                var container = job.storageTarget().containerId();
                var physical = FrontierV3PhysicalContainer.loaded(level, state, container).orElse(null);
                if (physical == null) return null;
                var order = ForestryWorkEffects.cargo(state, job); var transfer = cargo.transfer();
                var adapter = new FrontierV3ActorItemTransfer.FungibleStep(order, physical, worker, worker.getUUID(),
                        transfer.source(), transfer.destinationSlot(), transfer.destinationBefore());
                var participants = List.of(physical.persistence().get(), FrontierV3OperationEntityParticipant.current(worker, actuation.id().body().physicalEpoch()));
                var declaration = FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.FORESTRY_CARGO, job.id(), new ForestryPhysicalEvent.Prepared(job.id(), step), participants);
                var operation = FrontierV3MinecraftOperation.applyDeferred(level, declaration, participants, adapter::before, FrontierV3NativeEffect.observed(adapter::apply), adapter::after);
                return operation.complete() ? new Applied(operation.ticket(), new ForestryPhysicalEvent.Observed(job.id(), step, List.of(),
                        FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(physical.inventory(), state, container), Optional.empty())) : null;
            }
        }
    }
    private static boolean plain(ItemStack stack, String kind, int count) {
        return count == 0 ? stack.isEmpty() : stack.getCount() == count
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(kind)
                && ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem(), count));
    }
    private static boolean savedDeparture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, ForestryWork job) {
        if (job.pending().isPresent()) {
            var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
            var departure = ledger.bodyDeparture(job.execution().actorId()).orElse(null);
            if (departure == null || !departure.current(state) || !ledger.savedBodyDeparture(departure)
                    || !departure.executionAtCapture().equals(Optional.of(job.execution()))) return false;
            var step = job.pending().orElseThrow();
            var protocol = switch (step) {
                case ForestryPhysicalStep.Fell ignored -> FrontierV3OperationReceipt.Protocol.FORESTRY_BLOCK;
                case ForestryPhysicalStep.Equipment ignored -> FrontierV3OperationReceipt.Protocol.FORESTRY_EQUIPMENT;
                case ForestryPhysicalStep.Cargo ignored -> FrontierV3OperationReceipt.Protocol.FORESTRY_CARGO;
            };
            return FrontierV3PreparedOperationWithdrawal.submit(level, runtime, protocol, job.id(),
                    new ForestryPhysicalEvent.Prepared(job.id(), step), new ForestryPhysicalEvent.Cancelled(job.id(), step));
        }
        if (!ForestryWorkAuthority.physicalCargo(state, job)) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var departure = ledger.bodyDeparture(job.execution().actorId()).orElse(null);
        int carried = ForestryWorkAuthority.carried(state, job);
        if (departure == null || !departure.current(state) || !ledger.savedBodyDeparture(departure)
                || !departure.offhand().equals(Optional.of(new FrontierV3ActorBodyDeparture.HandStack(job.outputKind(), carried)))) return false;
        return submit(level, runtime, job.id(), "hand-saved-departure", new ForestryPhysicalEvent.HandBoundary(job.id(),
                new ActorActuationId(new ActorBodyId(job.execution().actorId(), departure.identity().epoch()), job.execution()),
                ForestryPhysicalEvent.HandBoundary.Boundary.DEPARTED, ForestryPhysicalStateSupport.hand(state, job, carried)));
    }
    private static boolean submit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            SubjectId id, String operation, FrontierPayload payload) {
        var result = FrontierV3CommandSubmission.submit(runtime, "forestry-" + operation, id.value(), payload);
        FrontierV3DiagnosticTrace.record(level.getServer(), "forestry:" + id.value(), operation, id, result);
        return result instanceof CommandResult.Accepted;
    }
}
