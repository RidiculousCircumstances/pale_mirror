package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemConsumptionStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemConsumedObservation;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceConsumedObservation;
import io.farfrontier.palemirror.frontier.v3.model.HiveGrowthInputHold;
import io.farfrontier.palemirror.frontier.v3.model.HiveGrowthJob;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceLot;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierMedicalTreatmentSceneSupport;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Removes one exact identity-tagged count only after durable admission and loaded-world inspection. */
final class FrontierV3ExactItemConsumptionExecutor {
    private FrontierV3ExactItemConsumptionExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> intent.kind() == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING
                        || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
                .filter(intent -> !state.humanPopulation().medicalOperations().containsKey(intent.causeSubjectId())
                        || FrontierMedicalTreatmentSceneSupport.permitsCurrentConsumptionIntent(state, intent))
                .findFirst().ifPresent(intent -> execute(level, runtime, state, intent));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, PhysicalIntent intent) {
        FungibleTarget fungible = fungibleTarget(state, intent);
        if (fungible != null) {
            executeFungible(level, runtime, state, intent, fungible);
            return;
        }
        Target target = target(state, intent);
        if (target == null) { if (intent.status() == PhysicalIntentStatus.RUNNING) unknown(runtime, intent.id(), "target-conflict"); return; }
        if (!level.hasChunkAt(target.chestPosition())) return;
        if (ReferenceContainerCustody.isReferenceContainer(state, target.containerId())
                && !ReferenceContainerCustody.hasOperationalCustody(state, target.containerId())) return;
        ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.activeChest(level, new FrontierV3CargoHandoffExecutor.StoreTarget(target.chestPosition(), target.containerId()));
        if (chest == null) { unknown(runtime, intent.id(), "chest-conflict"); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            inspectRunning(runtime, intent, target, chest); return;
        }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!consume(chest, target)) { unknown(runtime, intent.id(), "precondition-conflict"); return; }
        confirm(runtime, intent, target, chest);
    }

    private static FungibleTarget fungibleTarget(FrontierWorldState state, PhysicalIntent intent) {
        HiveGrowthJob job = state.hiveColony().growthJobs().get(intent.causeSubjectId());
        if (job == null || !(job.inputHold() instanceof HiveGrowthInputHold.FungibleCold held)
                || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.hiveGrowthConsumption(job.id(), held.itemId()))) return null;
        CustodyAccount account = state.inventory().fungibleResources().accounts().get(held.accountId());
        ResourceLot lot = state.inventory().fungibleResources().lots().get(held.itemId());
        if (account == null || lot == null || !(account.custody() instanceof ResourceCustody.Container container)) return null;
        List<PhysicalStackBinding> current = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(account.id())).toList();
        if (current.isEmpty()) return null;
        long epoch = current.getFirst().authorityEpoch();
        if (current.stream().anyMatch(binding -> binding.authorityEpoch() != epoch)) return null;
        PhysicalStackBinding input = current.stream().filter(binding -> binding.lotQuantities().equals(java.util.Map.of(lot.id(), 64))
                && binding.claimQuantities().equals(java.util.Map.of(held.claimId(), 64))
                && binding.address() instanceof PhysicalStackAddress.ContainerSlot).findFirst().orElse(null);
        if (input == null) return null;
        InventoryCustody.ContainerSlot slot = ((PhysicalStackAddress.ContainerSlot) input.address()).slot();
        var surface = state.inventory().surfaces().get(container.containerId());
        if (surface == null || !slot.containerId().equals(container.containerId())) return null;
        return new FungibleTarget(account, lot, held.claimId(), epoch, container.containerId(), slot.slot(),
                new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()));
    }

    private static void executeFungible(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                        FrontierWorldState state, PhysicalIntent intent, FungibleTarget target) {
        if (!level.hasChunkAt(target.chestPosition())) return;
        if (ReferenceContainerCustody.isReferenceContainer(state, target.containerId())
                && !ReferenceContainerCustody.hasOperationalCustody(state, target.containerId())) return;
        ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.activeChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(target.chestPosition(), target.containerId()));
        if (chest == null) { unknown(runtime, intent.id(), "fungible-chest-conflict"); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            if (fungibleConsumed(chest, target)) confirmFungible(runtime, intent, target, chest);
            else unknown(runtime, intent.id(), "fungible-restart-postcondition-conflict");
            return;
        }
        if (!matchesFungibleInput(chest, target)) { unknown(runtime, intent.id(), "fungible-precondition-conflict"); return; }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "fungible-running")) return;
        chest.setItem(target.slot(), ItemStack.EMPTY); chest.setChanged();
        if (!fungibleConsumed(chest, target)) { unknown(runtime, intent.id(), "fungible-physical-effect-conflict"); return; }
        confirmFungible(runtime, intent, target, chest);
    }

    private static boolean matchesFungibleInput(ChestBlockEntity chest, FungibleTarget target) {
        if (target.slot() < 0 || target.slot() >= chest.getContainerSize()) return false;
        ItemStack stack = chest.getItem(target.slot());
        return stack.getCount() == 64 && target.lot().itemKind().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private static boolean fungibleConsumed(ChestBlockEntity chest, FungibleTarget target) {
        return target.slot() >= 0 && target.slot() < chest.getContainerSize() && chest.getItem(target.slot()).isEmpty();
    }

    private static void confirmFungible(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent,
                                        FungibleTarget target, ChestBlockEntity chest) {
        FrontierWorldState current = runtime.decodedState().orElseThrow();
        List<FungiblePhysicalObservation.Stack> remaining = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, current, target.containerId());
        PhysicalEffectObservation observation = new FungibleResourceConsumedObservation(
                new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')), intent.id(), target.account().id(), target.lot().id(),
                target.claimId(), 64, target.authorityEpoch(), remaining);
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "fungible-confirmed")) {
            throw new IllegalStateException("fungible consumption confirmation was rejected");
        }
    }

    static boolean consume(ChestBlockEntity chest, Target target) {
        ItemStack stack = chest.getItem(target.slot());
        if (!FrontierV3CargoHandoffExecutor.exactMatch(stack, target.item())) return false;
        stack.shrink(target.count()); chest.setItem(target.slot(), stack); chest.setChanged(); return true;
    }

    /** Restart predicate: the same identity tag with exactly the expected remainder is the only success. */
    static boolean consumed(ChestBlockEntity chest, Target target) {
        ItemStack stack = chest.getItem(target.slot()); int remainder = target.item().count() - target.count();
        return remainder == 0 ? stack.isEmpty() : FrontierV3CargoHandoffExecutor.exactMatch(stack, new ExactItemStack(
                target.item().id(), target.item().economicOwnerId(), target.item().itemKind(), remainder, target.item().custody()));
    }

    private static void inspectRunning(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target, ChestBlockEntity chest) {
        if (consumed(chest, target)) confirm(runtime, intent, target, chest);
        else unknown(runtime, intent.id(), "restart-postcondition-conflict");
    }

    private static void confirm(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target, ChestBlockEntity chest) {
        ExactItemConsumedObservation observation = new ExactItemConsumedObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                intent.id(), target.item().id(), target.item().count(), target.item().count() - target.count());
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed")) throw new IllegalStateException("exact consumption confirmation was rejected");
        if (ReferenceContainerCustody.isReferenceContainer(runtime.decodedState().orElseThrow(), target.containerId())
                && !FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedMutation(runtime, target.containerId(), chest)) {
            throw new IllegalStateException("confirmed reference consumption did not establish its next replica boundary");
        }
    }

    static Target target(FrontierWorldState state, PhysicalIntent intent) {
        try {
            ExactItemConsumptionStateSupport.Claim claim = ExactItemConsumptionStateSupport.claim(state, intent);
            return new Target(claim.item(), claim.containerId(), claim.slot(), new BlockPos(claim.position().x(), claim.position().y(), claim.position().z()), claim.count());
        } catch (IllegalArgumentException conflict) {
            return null;
        }
    }

    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) { transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase); }
    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                      Optional<PhysicalEffectObservation> observation, String phase) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = new CommandId("executor:exact-consumption-" + phase + "-" + id.value().replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalIntentTransition(id, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result instanceof CommandResult.Accepted;
    }

    record Target(ExactItemStack item, SubjectId containerId, int slot, BlockPos chestPosition, int count) { }
    private record FungibleTarget(CustodyAccount account, ResourceLot lot, SubjectId claimId, long authorityEpoch,
                                  SubjectId containerId, int slot, BlockPos chestPosition) { }
}
