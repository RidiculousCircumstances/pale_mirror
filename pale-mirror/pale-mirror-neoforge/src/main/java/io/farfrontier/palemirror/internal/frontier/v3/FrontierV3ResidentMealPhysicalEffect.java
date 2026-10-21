package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.List;
import java.util.Map;

/** Loaded executor for the resident's retained meal; only a WAL-prepared step may mutate stacks. */
final class FrontierV3ResidentMealPhysicalEffect {
    private FrontierV3ResidentMealPhysicalEffect() { }

    static boolean release(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                           FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease lease) {
        ResidentMeal meal = state.humanPopulation().meals().get(actorId);
        if (meal == null) return true;
        if (meal.pendingPhysicalStep().isPresent()) return false;
        if (!meal.carriesFood()) return true;
        var bindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(meal.actorAccountId())).toList();
        if (bindings.isEmpty()) return true;
        var address = FrontierV3ActorResourceSlots.address(actorId, body, meal.inventorySlot());
        if (bindings.size() != 1 || !bindings.getFirst().address().equals(address)
                || !FrontierV3ResidentMealItems.matches(FrontierV3ActorResourceSlots.get(body,
                        meal.inventorySlot()), FrontierV3ResidentMealItems.heldPortion(state, meal))) return false;
        return accepted(level, runtime, actorId, "ambient-meal-hand-release", new ResidentMealHotHandReleased(
                actorId, lease.revision(), new FungiblePhysicalObservation.Stack(address,
                        meal.portion().itemKind(), FrontierV3ResidentMealItems.heldPortion(state, meal).quantity()), meal.executionId()));
    }

    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                        FrontierWorldState state, SubjectId residentId, Mob body, AmbientActorLease lease) {
        ResidentMeal meal = state.humanPopulation().meals().get(residentId);
        if (meal == null || lease.goal() != AmbientGoalKind.MEAL || lease.status() != AmbientLeaseStatus.HOT
                || !(body instanceof Villager worker) || !body.getUUID().equals(
                        SceneLease.deterministicEntityId(state.bootstrap().worldId(), residentId))) return false;
        // A COLD-held portion acquires exact HOT hand custody before either walking or eating.
        if (meal.carriesFood() && meal.pendingPhysicalStep().isEmpty()
                && state.inventory().fungibleResources().bindings().values().stream()
                    .noneMatch(binding -> binding.accountId().equals(meal.actorAccountId()))
                && FrontierV3ResidentMealItems.matches(FrontierV3ActorResourceSlots.get(worker,
                        meal.inventorySlot()), FrontierV3ResidentMealItems.heldPortion(state, meal)))
            return accepted(level, runtime, meal.residentId(), "resident-meal-hand-materialized",
                    new ResidentMealHotHandMaterialized(meal.residentId(), lease.revision(),
                            new FungiblePhysicalObservation.Stack(FrontierV3ActorResourceSlots.address(
                                    meal.residentId(), worker, meal.inventorySlot()),
                                    meal.portion().itemKind(), FrontierV3ResidentMealItems.heldPortion(state, meal).quantity()), meal.executionId()));
        var observedBody = FrontierV3SupportedBodyCapture.observe(level, body);
        if (observedBody.isEmpty()) return false;
        // Resource confirmation never installs pose. Inspect the exact live body separately;
        // retired/possibly applied operations are settled by their retained resource owner.
        var actuation = FrontierV3AmbientActuation.capture(state, runtime, body, lease, meal.executionId()).orElse(null);
        if (actuation == null || !FrontierV3ActorBodyController.inspectCurrent(level, runtime, body)
                || !actuation.current(body)) return false;
        state = runtime.decodedState().orElseThrow();
        var currentMeal = state.humanPopulation().meals().get(residentId);
        if (currentMeal == null || !currentMeal.equals(meal)) return false;
        if (meal.phase() == ResidentMeal.Phase.CONSUME) {
            if (meal.pendingPhysicalStep().isEmpty() && ResidentMealProcess.requiresClearance(state, meal, observedBody.orElseThrow()))
                return accepted(level, runtime, residentId, "resident-meal-clearance-required",
                        new ResidentMealClearanceRequired(meal.executionId(), observedBody.orElseThrow()));
            if (!ResidentMealProcess.mayConsumeAt(state, meal, observedBody.orElseThrow())) return false;
            return consume(level, runtime, state, meal, worker, lease);
        }
        if (!FrontierV3SurfaceObservation.at(body, ResidentMealProcess.goalSurface(state, meal))) return false;
        if (meal.phase() == ResidentMeal.Phase.TAKE) return take(level, runtime, state, meal, worker, lease);
        return false;
    }

    private static boolean take(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, ResidentMeal meal, Villager worker, AmbientActorLease lease) {
        ContainerSurface surface = state.inventory().surfaces().get(meal.depotId());
        if (surface == null) return false;
        BlockPos position = new BlockPos(surface.position().x(), surface.position().y(), surface.position().z());
        if (!level.hasChunkAt(position)) return false;
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, position, meal.depotId());
        if (chest == null) return false;
        if (meal.pendingPhysicalStep().isEmpty()
                && !ReferenceContainerCustody.hasOperationalCustody(state, meal.depotId())) return false;
        if (meal.pendingPhysicalStep().isPresent()
                && (!ReferenceContainerCustody.hasLiveCustody(state, meal.depotId())
                    || ReferenceContainerCustody.blocksCanonicalUse(state, meal.depotId()))) return false;
        List<MaterialSourceSelection.Slice> slices;
        ActorContainerItemOrder order;
        try {
            order = ResidentMealProcess.takeOrder(state, meal);
            slices = MaterialSourceSelection.select(state.inventory().fungibleResources(), order);
        } catch (IllegalArgumentException changed) { return false; }
        if (slices.isEmpty() || slices.stream().anyMatch(slice -> !(slice.address() instanceof PhysicalStackAddress.ContainerSlot))) return false;
        var sourceCounts = ResidentMealPhysicalStep.sourceCounts(slices);
        var transfer = new FrontierV3ActorItemTransfer.FungibleStep(order, chest, worker,
                worker.getUUID(), slices, -1);
        ResidentMealPhysicalStep pending = meal.pendingPhysicalStep().orElse(null);
        if (pending == null) {
            if (!transfer.before()) return false;
            ResidentMealPhysicalStep step = new ResidentMealPhysicalStep(ResidentMeal.Phase.TAKE,
                    sourceCounts, 0, slices.getFirst().epoch(),
                    lease.revision(), lease.revision(), meal.executionId());
            if (!accepted(level, runtime, meal.residentId(), "resident-meal-take-prepare",
                    new ResidentMealHotEffectPrepared(meal.residentId(), step))) return false;
            // The WAL fence is durable before submit returns. Finish this atomic
            // interaction in the same server callback: a later tick may already have
            // unloaded either participant. Re-enter only with the retained step.
            var prepared = runtime.decodedState().orElseThrow();
            var next = prepared.humanPopulation().meals().get(meal.residentId());
            if (next == null || !next.pendingPhysicalStep().equals(java.util.Optional.of(step)))
                throw new IllegalStateException("accepted meal preparation lost its exact pending effect");
            return take(level, runtime, prepared, next, worker, lease);
        }
        if (pending.phase() != ResidentMeal.Phase.TAKE || pending.ambientRevision() != lease.revision()
                || !pending.sourceCounts().equals(sourceCounts)
                || pending.sourceEpoch() != slices.getFirst().epoch()) return false;
        var actorParticipant = FrontierV3OperationEntityParticipant.current(worker,
                ActorBodyAuthority.current(state, meal.residentId()).physicalEpoch());
        var containerParticipant = FrontierV3OperationBlockEntityParticipant.current(chest);
        var declaration = FrontierV3ResidentMealNativeOperation.take(meal, pending, actorParticipant, containerParticipant);
        var operation = FrontierV3MinecraftOperation.apply(level, declaration, List.of(actorParticipant, containerParticipant),
                transfer::before, transfer::apply, transfer::after);
        if (!operation.complete()) return false;
        List<FungiblePhysicalObservation.Stack> remaining =
                FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, state, meal.depotId());
        var held = new FungiblePhysicalObservation.Stack(FrontierV3ActorResourceSlots.address(
                meal.residentId(), worker, meal.inventorySlot()), meal.portion().itemKind(), meal.portion().quantity());
        return FrontierV3OperationConfirmation.submit(level, runtime, operation.ticket(),
                new ResidentMealHotEffectObserved(meal.residentId(), ResidentMeal.Phase.TAKE,
                        lease.revision(), lease.goalBody(), remaining, List.of(held), meal.executionId()));
    }

    private static boolean consume(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                   FrontierWorldState state, ResidentMeal meal, Villager worker, AmbientActorLease lease) {
        var ledger = state.inventory().fungibleResources();
        List<PhysicalStackBinding> bindings = ledger.bindings().values().stream()
                .filter(binding -> binding.accountId().equals(meal.actorAccountId())).toList();
        var held = FrontierV3ResidentMealItems.heldPortion(state, meal);
        int remainingCount = held.quantity() - meal.portion().quantity();
        ItemStack actual = FrontierV3ActorResourceSlots.get(worker, meal.inventorySlot());
        boolean before = FrontierV3ResidentMealItems.matches(actual, held);
        if (bindings.size() != 1 || bindings.getFirst().quantity() != held.quantity()
                || !bindings.getFirst().lotQuantities().equals(held.lotQuantities())
                || !bindings.getFirst().address().equals(FrontierV3ActorResourceSlots.address(
                        meal.residentId(), worker, meal.inventorySlot()))) return false;
        ResidentMealPhysicalStep pending = meal.pendingPhysicalStep().orElse(null);
        if (pending == null) {
            if (!before) return false;
            var step = new ResidentMealPhysicalStep(
                            ResidentMeal.Phase.CONSUME, -1, meal.portion().quantity(), bindings.getFirst().authorityEpoch(),
                            0L, lease.revision(), meal.executionId());
            if (!accepted(level, runtime, meal.residentId(), "resident-meal-consume-prepare",
                    new ResidentMealHotEffectPrepared(meal.residentId(), step))) return false;
            var prepared = runtime.decodedState().orElseThrow();
            var next = prepared.humanPopulation().meals().get(meal.residentId());
            if (next == null || !next.pendingPhysicalStep().equals(java.util.Optional.of(step)))
                throw new IllegalStateException("accepted meal preparation lost its exact pending effect");
            return consume(level, runtime, prepared, next, worker, lease);
        }
        if (pending.phase() != ResidentMeal.Phase.CONSUME || pending.ambientRevision() != lease.revision()
                || pending.consumptionQuantity() != meal.portion().quantity()
                || pending.sourceEpoch() != bindings.getFirst().authorityEpoch()) return false;
        var participant = FrontierV3OperationEntityParticipant.current(worker,
                ActorBodyAuthority.current(state, meal.residentId()).physicalEpoch());
        var declaration = FrontierV3ResidentMealNativeOperation.consume(meal, pending, held, participant);
        var operation = FrontierV3MinecraftOperation.apply(level, declaration, List.of(participant),
                () -> FrontierV3ResidentMealItems.matches(FrontierV3ActorResourceSlots.get(worker, meal.inventorySlot()), held),
                () -> {
                    var remainder = FrontierV3ActorResourceSlots.get(worker, meal.inventorySlot()).copy(); remainder.shrink(meal.portion().quantity());
                    FrontierV3ActorResourceSlots.set(worker, meal.inventorySlot(), remainder);
                    level.playSound(null, worker.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8F, 1.0F);
                    return true;
                }, () -> {
                    var remaining = FrontierV3ActorResourceSlots.get(worker, meal.inventorySlot());
                    return remainingCount == 0 ? remaining.isEmpty() : remaining.getCount() == remainingCount
                            && ItemStack.isSameItemSameComponents(remaining, FrontierV3ResidentMealItems.stack(held));
                });
        if (!operation.complete()) return false;
        var remaining = remainingCount == 0 ? List.<FungiblePhysicalObservation.Stack>of()
                : List.of(new FungiblePhysicalObservation.Stack(FrontierV3ActorResourceSlots.address(
                        meal.residentId(), worker, meal.inventorySlot()), meal.portion().itemKind(), remainingCount));
        boolean consumed = FrontierV3OperationConfirmation.submit(level, runtime, operation.ticket(),
                new ResidentMealHotEffectObserved(meal.residentId(), ResidentMeal.Phase.CONSUME,
                        lease.revision(), FrontierV3SupportedBodyCapture.observe(level, worker).orElseThrow(), remaining, List.of(), meal.executionId()));
        if (consumed) FrontierV3ActorCarryProjection.rememberConfirmed(runtime.decodedState().orElseThrow(), meal.residentId(), worker);
        return consumed;
    }

    private static boolean accepted(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    SubjectId residentId, String operation, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        CommandResult result = FrontierV3CommandSubmission.submit(runtime, operation, residentId.value(), payload);
        FrontierV3DiagnosticTrace.record(level.getServer(), "resident-meal:" + residentId.value(), operation, residentId, result);
        return result instanceof CommandResult.Accepted;
    }
}
