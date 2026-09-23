package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.MinecartChest;

/** Actual unload/return observations; canonical state is never mutated by these callbacks. */
final class FrontierV3CargoDepartureObserver {
    private FrontierV3CargoDepartureObserver() { }

    /** Freeze a verified current physical observation durably before canonical closure. */
    static boolean prepareRelease(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        if (!FrontierSceneBehaviors.isLogistics(lease)
                || FrontierSceneBehaviors.logistics(lease).carrierDisposition() == CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY) return true;
        var entity = level.getEntity(FrontierV3CargoCarrierExecutor.id(lease));
        var ledger = FrontierV3CargoDepartureLedger.get(level, state.bootstrap().worldId());
        var receipt = entity == null ? ledger.observation(FrontierV3CargoCarrierExecutor.id(lease))
                : FrontierV3CargoCarrierExecutor.captureLoadedRelease(state, entity, lease);
        if (ledger.conflicted(FrontierV3CargoCarrierExecutor.id(lease)) || receipt.isEmpty()
                || !FrontierV3CargoCarrierExecutor.currentDeparture(state, lease, receipt.orElseThrow(), level.registryAccess())) return false;
        try {
            FrontierV3CargoCleanupArchive.at(level, state.bootstrap().worldId()).prepareRelease(state, lease, receipt.orElseThrow());
            FrontierV3CargoCleanupPersistence.witnessPublished(level);
            return true;
        } catch (java.io.IOException failure) {
            PaleMirrorMod.LOGGER.error("Cargo release witness not durable entity={}; closure deferred", receipt.orElseThrow().entityId(), failure);
            return false;
        }
    }

    static boolean observeLeave(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        if (!(entity instanceof MinecartChest) || entity.getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK) return false;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        var lease = declaredLease(state, entity);
        return lease != null && FrontierV3CargoCarrierExecutor.captureDeparture(state, entity, lease)
                .map(receipt -> FrontierV3CargoDepartureLedger.get(level, state.bootstrap().worldId()).record(receipt)).orElse(false);
    }

    static void observeJoin(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        if (!(entity instanceof MinecartChest cart) || entity.isRemoved()) return;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        observeJoin(level, state, cart);
    }

    static void observeJoin(ServerLevel level, FrontierWorldState state, MinecartChest cart) {
        if (cart.isRemoved()) return;
        var worldRetirement = state.fencedRecovery().cargoRetirements().pending().get(cart.getUUID());
        if (worldRetirement != null && worldRetirement.worldId().equals(state.bootstrap().worldId())
                && worldRetirement.disposition() == CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY
                && FrontierV3CargoCarrierExecutor.matchesRetiredDeclaration(worldRetirement, cart.getUUID(), cart.getPersistentData())) {
            // This is terminal declaration retirement, not inventory reconciliation.
            // Player changes since handoff are irrelevant and must remain untouched.
            if (!FrontierV3CargoCarrierProvenance.restore(state.inventory().items(), cart.getUUID(), cart)) {
                PaleMirrorMod.LOGGER.error("Cargo handoff provenance conflicts entity={}; declaration retained", cart.getUUID());
                return;
            }
            if (!FrontierV3CargoFootprintObserver.retireIdentity(level, worldRetirement, cart)) return;
            FrontierV3CargoCarrierPresentation.discard(cart, worldRetirement.leaseId(), worldRetirement.cargoId());
            FrontierV3CargoCarrierExecutor.relinquishDeclaration(cart);
            return;
        }
        var ledger = FrontierV3CargoDepartureLedger.get(level, state.bootstrap().worldId());
        var receipt = ledger.observation(cart.getUUID()).orElse(null);
        if (receipt == null && state.fencedRecovery().cargoRetirements().pending().containsKey(cart.getUUID())) {
            try {
                receipt = FrontierV3CargoCleanupArchive.at(level, state.bootstrap().worldId()).observation(cart.getUUID()).orElse(null);
            } catch (java.io.IOException failure) {
                PaleMirrorMod.LOGGER.error("Cargo cleanup witness unreadable entity={}; projection retained", cart.getUUID(), failure);
                return;
            }
        }
        if (receipt == null || ledger.conflicted(cart.getUUID()) || !samePhysicalSnapshot(cart, receipt)) return;
        var lease = declaredLease(state, cart);
        if (lease != null && FrontierV3CargoCarrierExecutor.currentDeparture(state, lease, receipt, level.registryAccess())) {
            ledger.resolveExact(receipt);
            return;
        }
        // COLD may already have consumed the cargo. Only exact retained departure contents
        // plus this exact canonical retirement authorize removing the obsolete projection.
        var retirement = retirement(state, receipt);
        if (retirement == null) return;
        if (retirement.disposition() == CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY
                || FrontierV3CargoCarrierExecutor.hasWorldCustody(state, cart.getUUID())) {
            // Preserve the original witness until physical retirement is durably acknowledged.
            return;
        }
        if (!retainCleanupWitness(level, state, receipt)) return;
        FrontierV3CargoCarrierPresentation.discard(cart, retirement.leaseId(), retirement.cargoId());
        cart.clearContent();
        cart.discard();
        // Entity removal is not a disk-save acknowledgement. Keep both the canonical
        // obligation and exact departure witness so a crash/return can repeat safely.
    }

    static boolean retainCleanupWitness(ServerLevel level, FrontierWorldState state, FrontierV3CargoDeparture receipt) {
        try {
            FrontierV3CargoCleanupArchive.at(level, state.bootstrap().worldId()).retain(receipt);
            FrontierV3CargoCleanupPersistence.witnessPublished(level);
            return true;
        } catch (java.io.IOException failure) {
            PaleMirrorMod.LOGGER.error("Cargo cleanup witness not durable entity={}; projection retained", receipt.entityId(), failure);
            return false;
        }
    }

    static CargoProjectionRetirement retirement(FrontierWorldState state, FrontierV3CargoDeparture receipt) {
        var retained = state.fencedRecovery().cargoRetirements().pending().get(receipt.entityId());
        if (retained == null || !retained.worldId().equals(state.bootstrap().worldId())
                || !retained.leaseId().equals(receipt.leaseId()) || !retained.cargoId().equals(receipt.cargoId())
                || retained.authorization().ownerRevision() != receipt.sceneRevision()
                || retained.authorization().retiredEpoch() != receipt.authorityEpoch()) return null;
        return retained;
    }

    static boolean retired(FrontierWorldState state, SceneLease lease, FrontierV3CargoDeparture receipt) {
        if (!FrontierSceneBehaviors.isLogistics(lease) || !lease.id().equals(receipt.leaseId())
                || lease.revision() != receipt.sceneRevision() || !FrontierV3CargoCarrierExecutor.id(lease).equals(receipt.entityId())
                || !FrontierSceneBehaviors.logistics(lease).cargoId().equals(receipt.cargoId())) return false;
        return retirement(state, receipt) != null;
    }

    static boolean samePhysicalSnapshot(MinecartChest cart, FrontierV3CargoDeparture receipt) {
        var tag = cart.getPersistentData();
        return receipt.entityId().equals(cart.getUUID())
                && tag.contains(FrontierV3CargoCarrierExecutor.REVISION_KEY, Tag.TAG_LONG)
                && tag.getLong(FrontierV3CargoCarrierExecutor.REVISION_KEY) == receipt.sceneRevision()
                && tag.contains(FrontierV3CargoCarrierExecutor.EPOCH_KEY, Tag.TAG_LONG)
                && tag.getLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY) == receipt.authorityEpoch()
                && tag.getString(FrontierV3CargoCarrierExecutor.LEASE_KEY).equals(receipt.leaseId().value())
                && tag.getString(FrontierV3CargoCarrierExecutor.CARGO_KEY).equals(receipt.cargoId().value())
                && receipt.body().equals(new BodyPosition(cart.getBlockX(), cart.getBlockY(), cart.getBlockZ()))
                && receipt.inventory().equals(FrontierV3CargoCarrierExecutor.observedInventory(cart));
    }

    private static SceneLease declaredLease(FrontierWorldState state, Entity entity) {
        try { return state.sceneLeases().get(new SceneLeaseId(entity.getPersistentData().getString(FrontierV3CargoCarrierExecutor.LEASE_KEY))); }
        catch (IllegalArgumentException missingDeclaration) { return null; }
    }
}
