package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Food owner reads the identified pre-loot body; never walks, feeds or reconstructs it. */
final class FrontierV3ResidentMealDeathResources {
    private FrontierV3ResidentMealDeathResources() { }

    static FrontierV3ActorDeathResourceComposition.AfterFatality prepare(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id) {
        var state = runtime.decodedState().orElseThrow();
        var meal = state.humanPopulation().meals().get(id.actorId());
        if (meal == null || !(body instanceof Villager)
                || !ActorBodyAuthority.current(state, id.actorId()).equals(id)) return () -> { };
        ItemStack beforeLoot = FrontierV3ActorResourceSlots.get(body, meal.inventorySlot()).copy();
        var preparedEffect = prepareEffect(level, runtime, body, id, state, meal);
        // The indexed dying body is the positive source witness. An absent corpse after
        // loot/unload is deliberately not accepted by this port.
        return () -> {
            if (!preparedEffect.getAsBoolean()) {
                io.farfrontier.palemirror.PaleMirrorMod.LOGGER.warn(
                        "PMV3_MEAL_DEATH_UNRESOLVED body={} execution={} step={}; exact native outcome unavailable, no replay or stock disposition",
                        id, meal.executionId(), meal.pendingPhysicalStep());
                return;
            }
            settlePortion(level, runtime, body, id, meal.executionId(), beforeLoot);
        };
    }

    static boolean settled(FrontierWorldState state, ActorBodyId id) {
        return !state.humanPopulation().mealResourceObligations().containsKey(id.actorId());
    }
    static void recover(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, FrontierV3BodyResourceSnapshot snapshot) {
        var state = runtime.decodedState().orElseThrow(); ActorBodyAuthority.requireRetiredDeath(state, id);
        var retained = state.humanPopulation().mealResourceObligations().get(id.actorId());
        if (retained == null || !retained.body().equals(id) || retained.pendingPhysicalStep().isPresent()) return;
        var before = snapshot.slots(level).get(retained.inventorySlot());
        if (before == null) throw new IllegalStateException("retired meal has no declared pre-loot native slot");
        settlePortion(level, runtime, body, id, retained.executionId(), before);
    }

    private static java.util.function.BooleanSupplier prepareEffect(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id,
            FrontierWorldState state, ResidentMeal meal) {
        if (meal.pendingPhysicalStep().isEmpty()) return () -> true;
        var worker = (Villager) body; var step = meal.pendingPhysicalStep().orElseThrow();
        var actor = FrontierV3OperationEntityParticipant.current(body, id.physicalEpoch());
        if (step.phase() == ResidentMeal.Phase.CONSUME) {
            var held = FrontierV3ResidentMealItems.heldPortion(state, meal);
            var declaration = FrontierV3ResidentMealNativeOperation.consume(meal, step, held, actor);
            var actual = FrontierV3ActorResourceSlots.get(body, meal.inventorySlot());
            boolean before = FrontierV3ResidentMealItems.matches(actual, held);
            int remainingCount = before ? held.quantity() : held.quantity() - meal.portion().quantity();
            if (!before && (remainingCount == 0 ? !actual.isEmpty() : actual.getCount() != remainingCount
                    || !ItemStack.isSameItemSameComponents(actual, FrontierV3ResidentMealItems.stack(held)))) return () -> false;
            var remainder = remainingCount == 0 ? List.<FungiblePhysicalObservation.Stack>of()
                    : List.of(new FungiblePhysicalObservation.Stack(FrontierV3ActorResourceSlots.address(
                            meal.residentId(), worker, meal.inventorySlot()), held.itemKind(), remainingCount));
            var receipt = new ResidentMealResourceEffectObserved(id, meal.executionId(), step,
                    before ? ResidentMealResourceEffectObserved.Outcome.CONSUMPTION_UNAPPLIED
                            : ResidentMealResourceEffectObserved.Outcome.CONSUMPTION_APPLIED, remainder, List.of());
            var snapshot = actual.copy();
            return () -> settleEffect(level, runtime, declaration, List.of(actor), receipt,
                    () -> ItemStack.matches(snapshot, FrontierV3ActorResourceSlots.get(body, meal.inventorySlot())));
        }
        var surface = state.inventory().surfaces().get(meal.depotId());
        if (surface == null || !ReferenceContainerCustody.hasLiveCustody(state, meal.depotId())
                || ReferenceContainerCustody.blocksCanonicalUse(state, meal.depotId())) return () -> false;
        var position = new BlockPos(surface.position().x(), surface.position().y(), surface.position().z());
        if (!level.hasChunkAt(position)) return () -> false;
        var chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, position, meal.depotId());
        if (chest == null) return () -> false;
        var order = ResidentMealProcess.takeOrder(state, meal);
        var slices = MaterialSourceSelection.select(state.inventory().fungibleResources(), order);
        if (slices.isEmpty() || slices.stream().anyMatch(slice -> slice.epoch() != step.sourceEpoch())
                || !ResidentMealPhysicalStep.sourceCounts(slices).equals(step.sourceCounts())) return () -> false;
        var transfer = new FrontierV3ActorItemTransfer.FungibleStep(order, chest, worker, worker.getUUID(), slices, -1);
        var source = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, state, meal.depotId());
        var container = FrontierV3OperationBlockEntityParticipant.current(chest);
        var declaration = FrontierV3ResidentMealNativeOperation.take(meal, step, actor, container);
        var actual = FrontierV3ActorResourceSlots.get(body, meal.inventorySlot());
        ResidentMealResourceEffectObserved receipt;
        boolean before = actual.isEmpty() && transfer.before();
        if (before) {
            receipt = new ResidentMealResourceEffectObserved(id, meal.executionId(), step,
                    ResidentMealResourceEffectObserved.Outcome.TAKE_UNAPPLIED, source, List.of());
        } else if (transfer.after() && FrontierV3ResidentMealItems.matches(actual, meal.portion())) {
            var held = new FungiblePhysicalObservation.Stack(FrontierV3ActorResourceSlots.address(
                    meal.residentId(), worker, meal.inventorySlot()), meal.portion().itemKind(), meal.portion().quantity());
            receipt = new ResidentMealResourceEffectObserved(id, meal.executionId(), step,
                    ResidentMealResourceEffectObserved.Outcome.TAKE_APPLIED, source, List.of(held));
        } else return () -> false;
        return () -> settleEffect(level, runtime, declaration, List.of(actor, container), receipt,
                before ? transfer::before : transfer::after);
    }

    private static boolean settleEffect(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierV3OperationReceipt.Declaration declaration, List<FrontierV3MinecraftOperation.Participant> ports,
            ResidentMealResourceEffectObserved receipt, java.util.function.BooleanSupplier unchanged) {
        if (!unchanged.getAsBoolean()) return false;
        var operations = FrontierV3OperationLedger.get(level);
        boolean cancelled = receipt.outcome() == ResidentMealResourceEffectObserved.Outcome.TAKE_UNAPPLIED
                || receipt.outcome() == ResidentMealResourceEffectObserved.Outcome.CONSUMPTION_UNAPPLIED;
        UUID ticket;
        if (cancelled) {
            // The same owner/key as ordinary execution admits a never-started intent,
            // or returns its prior attempt. A recovered PREPARED never gains that permission.
            if (!operations.canPrepare(declaration)) return false;
            var operation = operations.prepare(declaration);
            if (!operations.sealUnattemptedCancellation(operation.ticket())) {
                FrontierV3OperationDiagnostics.result(level, operation, FrontierV3MinecraftOperation.Status.AMBIGUOUS); return false;
            }
            ticket = operation.ticket();
            // Cancellation and its full typed consequence are forced together by
            // submitRecovery; neither is prematurely retired on this path.
        } else {
            var observed = FrontierV3MinecraftOperation.observe(level, declaration, ports, unchanged).orElse(null);
            if (observed == null || !observed.complete()) return false;
            ticket = observed.ticket();
        }
        return FrontierV3OperationConfirmation.submitRecovery(level, runtime, ticket, receipt, Optional.empty());
    }

    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        var operations = FrontierV3OperationLedger.get(level);
        for (var retained : state.humanPopulation().mealResourceObligations().values()) {
            var step = retained.pendingPhysicalStep().orElse(null);
            if (step == null) continue;
            var protocol = step.phase() == ResidentMeal.Phase.TAKE ? FrontierV3OperationReceipt.Protocol.MEAL_TAKE
                    : FrontierV3OperationReceipt.Protocol.MEAL_CONSUME;
            var operation = operations.find(protocol, retained.residentId(), FrontierV3ResidentMealNativeOperation.key(retained.residentId(), step)).orElse(null);
            if (operation == null) continue;
            var command = operations.ownerCommand(operation.ticket()).orElse(null);
            if (command == null) continue;
            ResidentMealResourceEffectObserved receipt;
            if (command.consequence() instanceof ResidentMealResourceEffectObserved retired) {
                receipt = retired;
            } else if (command.consequence() instanceof ResidentMealHotEffectObserved original
                    && original.residentId().equals(retained.residentId()) && original.executionId().equals(retained.executionId())
                    && original.phase() == step.phase() && original.ambientRevision() == step.ambientRevision()
                    && !operation.cancelled() && operation.applied().equals(operation.declaration().participants())) {
                receipt = new ResidentMealResourceEffectObserved(retained.body(), retained.executionId(), step,
                        original.phase() == ResidentMeal.Phase.TAKE ? ResidentMealResourceEffectObserved.Outcome.TAKE_APPLIED
                                : ResidentMealResourceEffectObserved.Outcome.CONSUMPTION_APPLIED,
                        original.remainingSource(), original.destination());
            } else continue; // No payload-shape inference of owner or unrecorded physical facts.
            if (!receipt.body().equals(retained.body()) || !receipt.executionId().equals(retained.executionId())
                    || !receipt.step().equals(step)) throw new IllegalStateException("retired meal has a foreign native consequence");
            if (FrontierV3OperationConfirmation.submitRecovery(level, runtime, operation.ticket(), receipt, Optional.empty())) return true;
        }
        return false;
    }

    private static void settlePortion(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution, ItemStack beforeLoot) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        var retained = state.humanPopulation().mealResourceObligations().get(id.actorId());
        if (retained == null || !retained.body().equals(id) || !retained.executionId().equals(execution)
                || retained.custodyState() == ResidentMealResourceObligation.CustodyState.SOURCE_TAKE_PENDING) return;
        var bindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(retained.actorAccountId())).toList();
        var expectedPocket = FrontierV3ActorResourceSlots.address(id.actorId(), body, retained.inventorySlot());
        if (bindings.size() != 1 || !bindings.getFirst().address().equals(expectedPocket)
                || !bindings.getFirst().lotQuantities().equals(state.inventory().fungibleResources().accounts()
                        .get(retained.actorAccountId()).lotQuantities())) return;
        var stock = new FoodPortion(retained.portion().itemKind(), retained.portion().nutritionPerItem(), bindings.getFirst().lotQuantities());
        var actual = FrontierV3ActorResourceSlots.get(body, retained.inventorySlot());
        if (beforeLoot.isEmpty() && actual.isEmpty()) {
            submitDisposition(level, runtime, new ResidentMealPortionDispositionObserved(id, retained.executionId(),
                    bindings.getFirst().authorityEpoch(), ResidentMealPortionDispositionObserved.Outcome.MISSING_BEFORE_LOOT,
                    Optional.empty()));
            return;
        }
        if (!FrontierV3ResidentMealItems.matches(beforeLoot, stock)
                || !ItemStack.matches(beforeLoot, actual)) return;
        UUID carrier = dropId(state, retained);
        // The already durable retired obligation is the before-effect fence. Never repeat
        // insertion after an absent lookup; this source can only use its positively held stack.
        if (level.getEntity(carrier) != null || FrontierV3ResourceHandoffLedger.get(level).blocks(carrier)) return;
        ItemEntity drop = new ItemEntity(level, body.getX(), body.getY() + 0.25D, body.getZ(), beforeLoot.copy());
        drop.setUUID(carrier);
        var receipt = new ResidentMealPortionDispositionObserved(id, retained.executionId(),
                bindings.getFirst().authorityEpoch(), ResidentMealPortionDispositionObserved.Outcome.WORLD_DROP,
                Optional.of(FrontierV3EntityResourceDropOperation.ground(drop)));
        FrontierV3EntityResourceDropOperation.perform(level, runtime, body, retained.inventorySlot(), drop, receipt);
    }

    private static UUID dropId(FrontierWorldState state, ResidentMealResourceObligation retained) {
        return UUID.nameUUIDFromBytes((state.bootstrap().worldId().value() + ":retired-meal:"
                + retained.body().actorId().value() + ":" + retained.body().physicalEpoch() + ":"
                + retained.executionId().generation()).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean submitDisposition(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                             ResidentMealPortionDispositionObserved receipt) {
        var result = FrontierV3CommandSubmission.submit(runtime, "resident-meal-retired-portion",
                receipt.body().actorId().value(), receipt);
        FrontierV3DiagnosticTrace.record(level.getServer(), "resident-meal:" + receipt.body().actorId().value(),
                "resident-meal-retired-portion", receipt.body().actorId(), result);
        return result instanceof CommandResult.Accepted;
    }

}
