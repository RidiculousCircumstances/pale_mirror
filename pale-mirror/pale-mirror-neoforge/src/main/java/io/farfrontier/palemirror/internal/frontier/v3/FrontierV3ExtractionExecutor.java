package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Mining effects only. The common controllers alone own bodies, routes, inventories and source authority. */
final class FrontierV3ExtractionExecutor {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private FrontierV3ExtractionExecutor() { }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        for (var job : state.extractionSites().work().values().stream().filter(value -> !value.terminal())
                .sorted(Comparator.comparing((ExtractionWork value) -> value.pending().isEmpty())
                        .thenComparing(ExtractionWork::id)).toList()) {
            if (progress(level, runtime, state, job)) return;
        }
    }
    private static boolean progress(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, ExtractionWork job) {
        if (runtime.awaitingCanonicalPredecessor()) return false;
        var actor = job.execution().actorId();
        var entity = level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), actor));
        if (entity == null) return savedDeparture(level, runtime, state, job);
        if (!(entity instanceof Villager worker) || !worker.isAlive()
                || !FrontierV3ActorBodyController.recognizesRecordedBody(level, state, worker)) return false;
        var lease = state.ambientLeases().get(actor);
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT || ActorExecutionCoordinator.sceneOwns(state, actor)) return false;
        var custody = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(job.carriedAccountId())).toList();
        int carried = ExtractionWorkAuthority.carried(state, job);
        if (carried > 0 && custody.isEmpty() && job.pending().isEmpty()
                && job.execution().equals(state.actorExecutions().current(ActorActivityKind.EXTRACTION).get(actor))
                && FrontierV3ActorCarryProjection.witnessed(state, actor, worker)) {
            // Binding requires this exact current actuator, not the suspended work owner.
            var body = ActorBodyAuthority.current(state, actor);
            return submit(level, runtime, job.id(), "hand-materialized", new ExtractionHandCustodyObserved(job.id(),
                    new ActorActuationId(body, job.execution()), ExtractionHandCustodyObserved.Boundary.MATERIALIZED,
                    ExtractionPhysicalStateSupport.hand(state, job, carried)));
        }
        FrontierV3ActorActuation actuation;
        try { actuation = FrontierV3ActorActuation.capture(state, worker, job.execution(), runtime::decodedState); }
        catch (IllegalArgumentException inactive) { return false; }
        if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, worker) || !actuation.current(worker)) return false;
        state = runtime.decodedState().orElseThrow();
        if (!job.equals(state.extractionSites().work().get(job.id())) || state.actorMovements().containsKey(actor)
                || !FrontierV3SurfaceObservation.at(worker, job.movementOrder(ExtractionWorkAuthority.site(state, job)).legalStations().getFirst()))
            return false;
        var step = job.pending().orElse(null);
        if (step == null) {
            if (job.phase() == ExtractionWork.Phase.SELECT_SOURCE
                    || !ExtractionWorkPolicy.mayFinishTarget(state, job, runtime.executionView().orElseThrow().instant().ticks())
                        && (job.phase() == ExtractionWork.Phase.TAKE_TOOL || job.phase() == ExtractionWork.Phase.EXTRACT))
                return false; // Semantic continuation/withdrawal belongs to the extraction process, not a native effect.
            long tick = runtime.executionView().orElseThrow().instant().ticks();
            if (!ResidentActivityCoordinator.ordinaryWorkPermitted(state, actor, tick)) return false;
            try {
                step = prepare(level, state, job, worker, actuation, tick);
                if (step == null) return false;
                return submit(level, runtime, job.id(), "effect-prepared", new ExtractionHotPrepared(job.id(), step));
            } catch (IllegalArgumentException unavailable) {
                LOGGER.debug("PMV3 mining preparation waiting job={} phase={} reason={}", job.id(), job.phase(), unavailable.getMessage());
                return false;
            }
        }
        try {
            var effect = apply(level, runtime.decodedState().orElseThrow(), job, step, worker, actuation, () -> {
                var current = runtime.decodedState().orElseThrow();
                if (job.equals(current.extractionSites().work().get(job.id()))) progress(level, runtime, current, job);
            });
            if (effect == null) return false;
            var retainedStep = step;
            return FrontierV3OperationConfirmation.submit(level, runtime, effect.ticket(), effect.receipt(), () -> {
              var current = runtime.decodedState().orElseThrow();
              if (retainedStep instanceof ExtractionPhysicalStep.BlockWork block) {
                var target = job.target().orElseThrow();
                var cell = current.extractionSites().deposits().get(job.siteId()).cells().get(target.key().cell());
                var declared = new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, job.siteId(),
                        WorksiteBlock.Role.RESOURCE, target.key().cell()), block.extraction().target(), cell.revision(), cell.knownBlock());
                var ledger = FrontierV3GrayboxLedger.get(level);
                ledger.worksite(new FrontierV3WorksiteBlockWitness(declared, block.extraction().definition().before(),
                        FrontierV3WorksiteBlockWitness.Phase.SETTLED));
                // Canonical confirmation already owns the outcome; SETTLED is
                // cleanup of its retained worksite witness, not pre-effect permission.
                ledger.persistAsync(level);
              }
              FrontierV3ActorCarryProjection.rememberConfirmed(runtime.decodedState().orElseThrow(), actor, worker);
            });
        } catch (IllegalArgumentException changed) {
            // A retained ambiguous effect is never converted into a timeout unlock or replay.
            LOGGER.warn("PMV3 mining effect unresolved job={} revision={} phase={} step={} reason={}",
                    job.id(), job.revision(), job.phase(), step, changed.getMessage());
            return false;
        }
    }
    private static ExtractionPhysicalStep prepare(ServerLevel level, FrontierWorldState state, ExtractionWork job,
            Villager worker, FrontierV3ActorActuation actuation, long tick) {
        var observation = new ActorHotObservation(actuation.id(), job.revision());
        var site = ExtractionWorkAuthority.site(state, job);
        if (job.phase() == ExtractionWork.Phase.EXTRACT) {
            if (job.labour().isEmpty() || job.labour().orElseThrow().completedAt(tick) < job.labour().orElseThrow().requiredMilliWork()) return null;
            if (!ExtractionSourceCustody.mutationAvailable(state.extractionSites(), job)) return null;
            var target = job.target().orElseThrow(); var cell = site.layout().require(target.key().cell());
            if (!ExtractionWorkSafety.available(state, state.extractionSites().deposits().get(site.id()), cell)) return null;
            var region = new ExtractionRegion(site.id(), Math.floorDiv(cell.source().x(), 16), Math.floorDiv(cell.source().z(), 16));
            var custody = state.replicaCustody().custodyByScope().get(region.scopeId());
            var witness = FrontierV3WorksiteEvidence.at(level, FrontierV3GrayboxLedger.get(level), state,
                    new BlockPos(cell.source().x(), cell.source().y(), cell.source().z()));
            if (custody == null || custody.status() != PhysicalCustodyLeaseStatus.ACQUIRED || witness == null
                    || witness.phase() != FrontierV3WorksiteBlockWitness.Phase.SETTLED
                    || witness.declaration().revision() != target.revision()) return null;
            var tool = state.inventory().items().get(job.toolId());
            if (tool == null || !FrontierV3ExactItemPresentation.exactMatch(worker.getMainHandItem(), tool)) return null;
            int carried = ExtractionWorkAuthority.carried(state, job);
            if (!plain(worker.getOffhandItem(), job.outputKind(), carried)) return null;
            var extraction = new FrontierV3MinecraftBlockExtraction(level, worker, actuation, state.bootstrap().ruleset().tools()).prepare(
                    ExtractionPhysicalStateSupport.operation(job), job.execution(), cell.source(), cell.definition());
            return new ExtractionPhysicalStep.BlockWork(observation, extraction, carried, custody.authorityEpoch(),
                    io.farfrontier.palemirror.frontier.v3.model.tooling.ToolUse.prepare(tool,
                            state.bootstrap().ruleset().tools(), cell.definition().toolRequirement()));
        }
        var container = ExtractionServiceAccess.identity(state, job).pointId();
        var physical = FrontierV3PhysicalContainer.loaded(level, state, container).orElse(null);
        if (physical == null || !ReferenceContainerCustody.hasOperationalCustody(state, container)
                || !ServiceAccessCoordinator.available(state, ExtractionServiceAccess.identity(state, job))) return null;
        long epoch = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(container)).authorityEpoch();
        if (job.phase() == ExtractionWork.Phase.STORE) {
            var order = ExtractionWorkEffects.cargo(state, job);
            var source = MaterialSourcePreparation.review(state, order).requireReady();
            var target = ContainerMaterialDestination.selectWhole(state, container, job.outputKind(),
                    ((ActorContainerItemOrder.Portion.Fungible) order.portion()).lotQuantities(), Optional.empty()).orElse(null);
            if (target == null) return null;
            var transfer = new ActorItemTransferStep(observation, source, epoch, target.slot(), target.before());
            var physicalStep = new FrontierV3ActorItemTransfer.FungibleStep(order, physical, worker, worker.getUUID(), source, target.slot(), target.before());
            return physicalStep.before() ? new ExtractionPhysicalStep.Cargo(transfer) : null;
        }
        var order = ExtractionWorkEffects.equipment(state, job); var slot = order.exactSlot();
        if (job.phase() == ExtractionWork.Phase.TAKE_TOOL
                ? !worker.getMainHandItem().isEmpty() || !FrontierV3ExactItemPresentation.exactMatch(physical.inventory().getItem(slot.slot()),
                    ((ActorContainerItemOrder.Portion.Exact) order.portion()).item())
                : !physical.inventory().getItem(slot.slot()).isEmpty() || !FrontierV3ExactItemPresentation.exactMatch(worker.getMainHandItem(),
                    ((ActorContainerItemOrder.Portion.Exact) order.portion()).item())) return null;
        return new ExtractionPhysicalStep.Equipment(observation, slot, epoch);
    }
    private record Applied(UUID ticket, ExtractionHotObserved receipt) { }
    private static Applied apply(ServerLevel level, FrontierWorldState state, ExtractionWork job,
            ExtractionPhysicalStep step, Villager worker, FrontierV3ActorActuation actuation, Runnable resume) {
        if (!step.observation().actuation().equals(actuation.id()) || step.observation().scopeRevision() != job.revision())
            throw new IllegalArgumentException("mining retained effect has a stale body/owner fence");
        switch (step) {
            case ExtractionPhysicalStep.BlockWork block -> {
                var source = new BlockPos(block.extraction().target().x(), block.extraction().target().y(), block.extraction().target().z());
                if (!FrontierV3LoadedWorld.ready(level, source)) return null;
                int resultCount = block.carriedBefore() + block.extraction().output().getFirst().quantity();
                var adapter = new FrontierV3MinecraftBlockExtraction(level, worker, actuation, state.bootstrap().ruleset().tools());
                var ledger = FrontierV3GrayboxLedger.get(level); var witness = FrontierV3WorksiteEvidence.at(level, ledger, state, source);
                var key = new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, job.siteId(),
                        WorksiteBlock.Role.RESOURCE, job.target().orElseThrow().key().cell());
                if (witness == null || !witness.declaration().key().equals(key)
                        || witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL
                        || witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING)
                    throw new IllegalArgumentException("mining source has external or missing physical provenance");
                boolean sourceAfter = FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(source)).equals(block.extraction().definition().after());
                if (sourceAfter && witness.phase() != FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED)
                    throw new IllegalArgumentException("source postcondition alone is not proof of this worker's extraction");
                if (witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED
                        && !witness.effectOperation().equals(Optional.of(block.extraction().operationId())))
                    throw new IllegalArgumentException("source block-half receipt belongs to a different operation");
                var external = witness.externalSuccessor();
                if (external.isPresent() && !external.orElseThrow().equals(FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(source))))
                    throw new IllegalArgumentException("split mining successor lacks the current captured external fact");
                if (!sourceAfter && external.isEmpty()) {
                    var deposit = state.extractionSites().deposits().get(job.siteId());
                    if (!ExtractionWorkSafety.available(state, deposit, deposit.site().layout().require(job.target().orElseThrow().key().cell()))
                            || !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                                new net.minecraft.world.phys.AABB(source).inflate(1.0D), entity -> entity.isAlive()
                                    && FrontierV3BodyObservation.capture(entity).support().filter(support ->
                                        support.support().equals(block.extraction().target())).isPresent()).isEmpty()) return null;
                }
                var participants = List.of(FrontierV3OperationChunks.current(level, source),
                        FrontierV3OperationEntityParticipant.current(worker, actuation.id().body().physicalEpoch()));
                var declaration = FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.EXTRACTION_BLOCK, job.id(), new ExtractionHotPrepared(job.id(), step), participants);
                var operation = FrontierV3MinecraftOperation.applyDeferred(level, declaration, participants,
                    () -> FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(source)).equals(block.extraction().definition().before())
                            && FrontierV3ToolUse.before(worker.getMainHandItem(), block.toolUse())
                            && plain(worker.getOffhandItem(), job.outputKind(), block.carriedBefore()), FrontierV3ManagedBlockWrites.effect(level, List.of(new FrontierV3ManagedBlockWrites.PlannedWrite(source,
                            FrontierV3MinecraftBlockExtraction.block(block.extraction().definition().after()))), () -> {
                        var result = new BlockExtractionPort.Result[1];
                        FrontierV3WorksiteWrites.apply(key, () -> {
                            result[0] = adapter.apply(block.extraction());
                            return result[0] == BlockExtractionPort.Result.APPLIED;
                        });
                        if (result[0] != BlockExtractionPort.Result.APPLIED)
                            throw new IllegalArgumentException("retained mining block effect refused: " + result[0]);
                        ledger.worksite(new FrontierV3WorksiteBlockWitness(witness.declaration(), witness.before(),
                                FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED, Optional.of(block.extraction().operationId())));
                        ledger.persist(level);
                        worker.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(job.outputKind())), resultCount));
                        return FrontierV3ToolUse.apply(worker.getMainHandItem(), block.toolUse());
                    }, resume), () -> FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(source)).equals(external.orElse(block.extraction().definition().after()))
                            && FrontierV3ToolUse.after(worker.getMainHandItem(), block.toolUse())
                            && plain(worker.getOffhandItem(), job.outputKind(), resultCount));
                if (!operation.complete()) return null;
                worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                return new Applied(operation.ticket(), new ExtractionHotObserved(job.id(), step, List.of(), List.of(ExtractionPhysicalStateSupport.hand(state, job, resultCount)), external));
            }
            case ExtractionPhysicalStep.Equipment equipment -> {
                var physical = FrontierV3PhysicalContainer.loaded(level, state, equipment.slot().containerId()).orElse(null);
                if (physical == null) return null;
                var chest = physical.inventory();
                var expected = state.inventory().items().get(job.toolId()); boolean take = job.phase() == ExtractionWork.Phase.TAKE_TOOL;
                var participants = List.of(physical.persistence().get(), FrontierV3OperationEntityParticipant.current(worker, actuation.id().body().physicalEpoch()));
                var declaration = FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.EXTRACTION_EQUIPMENT, job.id(), new ExtractionHotPrepared(job.id(), step), participants);
                var adapter = new FrontierV3EquipmentItemTransfer(chest, worker, expected, equipment.slot(), take
                        ? ActorContainerItemOrder.Direction.TAKE : ActorContainerItemOrder.Direction.PLACE);
                var operation = FrontierV3MinecraftOperation.applyDeferred(level, declaration, participants, adapter::before, FrontierV3NativeEffect.observed(adapter::apply), adapter::after);
                return operation.complete() ? new Applied(operation.ticket(), new ExtractionHotObserved(job.id(), step, List.of(), List.of())) : null;
            }
            case ExtractionPhysicalStep.Cargo cargo -> {
                var container = job.storageTarget().containerId();
                var physical = FrontierV3PhysicalContainer.loaded(level, state, container).orElse(null);
                if (physical == null) return null;
                var order = ExtractionWorkEffects.cargo(state, job); var transfer = cargo.transfer();
                var adapter = new FrontierV3ActorItemTransfer.FungibleStep(order, physical, worker, worker.getUUID(),
                        transfer.source(), transfer.destinationSlot(), transfer.destinationBefore());
                var participants = List.of(physical.persistence().get(), FrontierV3OperationEntityParticipant.current(worker, actuation.id().body().physicalEpoch()));
                var declaration = FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.EXTRACTION_CARGO, job.id(), new ExtractionHotPrepared(job.id(), step), participants);
                var operation = FrontierV3MinecraftOperation.applyDeferred(level, declaration, participants, adapter::before, FrontierV3NativeEffect.observed(adapter::apply), adapter::after);
                return operation.complete() ? new Applied(operation.ticket(), new ExtractionHotObserved(job.id(), step, List.of(),
                        FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(physical.inventory(), state, container))) : null;
            }
        }
    }
    private static boolean plain(ItemStack stack, String kind, int count) {
        return count == 0 ? stack.isEmpty() : stack.getCount() == count
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(kind)
                && ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem(), count));
    }
    private static boolean savedDeparture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, ExtractionWork job) {
        if (job.pending().isPresent()) {
            var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
            var departure = ledger.bodyDeparture(job.execution().actorId()).orElse(null);
            if (departure == null || !departure.current(state) || !ledger.savedBodyDeparture(departure)
                    || !departure.executionAtCapture().equals(Optional.of(job.execution()))) return false;
            var step = job.pending().orElseThrow();
            var protocol = switch (step) {
                case ExtractionPhysicalStep.BlockWork ignored -> FrontierV3OperationReceipt.Protocol.EXTRACTION_BLOCK;
                case ExtractionPhysicalStep.Equipment ignored -> FrontierV3OperationReceipt.Protocol.EXTRACTION_EQUIPMENT;
                case ExtractionPhysicalStep.Cargo ignored -> FrontierV3OperationReceipt.Protocol.EXTRACTION_CARGO;
            };
            return FrontierV3PreparedOperationWithdrawal.submit(level, runtime, protocol, job.id(),
                    new ExtractionHotPrepared(job.id(), step), new ExtractionHotCancelled(job.id(), step));
        }
        if (!ExtractionWorkAuthority.physicalCargo(state, job)) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var departure = ledger.bodyDeparture(job.execution().actorId()).orElse(null);
        int carried = ExtractionWorkAuthority.carried(state, job);
        if (departure == null || !departure.current(state) || !ledger.savedBodyDeparture(departure)
                || !departure.offhand().equals(Optional.of(new FrontierV3ActorBodyDeparture.HandStack(job.outputKind(), carried)))) return false;
        return submit(level, runtime, job.id(), "hand-saved-departure", new ExtractionHandCustodyObserved(job.id(),
                new ActorActuationId(new ActorBodyId(job.execution().actorId(), departure.identity().epoch()), job.execution()),
                ExtractionHandCustodyObserved.Boundary.SAVED_DEPARTURE, ExtractionPhysicalStateSupport.hand(state, job, carried)));
    }
    private static boolean submit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            SubjectId id, String operation, FrontierPayload payload) {
        var result = FrontierV3CommandSubmission.submit(runtime, "mining-" + operation, id.value(), payload);
        FrontierV3DiagnosticTrace.record(level.getServer(), "extraction:" + id.value(), operation, id, result);
        return result instanceof CommandResult.Accepted;
    }
}
