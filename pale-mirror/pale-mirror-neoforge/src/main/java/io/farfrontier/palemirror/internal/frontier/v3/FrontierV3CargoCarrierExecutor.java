package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.CargoBatch;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.ResourceLot;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Loaded-world exact cargo representation for one HOT route scene. */
final class FrontierV3CargoCarrierExecutor {
    static final String LEASE_KEY = "pale_mirror_frontier_v3_cargo_carrier_lease";
    static final String CARGO_KEY = "pale_mirror_frontier_v3_cargo_carrier";
    static final String REVISION_KEY = "pale_mirror_frontier_v3_cargo_carrier_revision";
    static final String EPOCH_KEY = "pale_mirror_frontier_v3_cargo_carrier_epoch";
    private static final double SPEED = 0.055D, ARRIVAL_DISTANCE = 0.35D;

    /** Bounded read-only physical admission result for one exact HOT cargo carrier. */
    enum Readiness { CURRENT, READY, UNLOADED, BLOCKED, CONFLICT }

    private FrontierV3CargoCarrierExecutor() { }

    static FrontierV3SceneExecutor.BodyMaterialization materialize(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        return materialize(level, state, lease, false);
    }

    /** Isolated GameTest fixture entry point; production code must use {@link #materialize}. */
    static FrontierV3SceneExecutor.BodyMaterialization materializeForFixture(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        return materialize(level, state, lease, true);
    }

    private static FrontierV3SceneExecutor.BodyMaterialization materialize(ServerLevel level, FrontierWorldState state, SceneLease lease,
                                                                             boolean knownFixtureColumns) {
        CargoBatch cargo = state.inventory().cargo().get(FrontierSceneBehaviors.logistics(lease).cargoId());
        if (cargo == null) return FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
        List<ExactItemStack> items = items(state, cargo);
        if (!validContents(state, cargo, items)) return FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
        var authority = FrontierV3CargoCarrierAuthority.currentEpoch(state.fencedRecovery(), lease);
        if (authority.isEmpty()) return FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
        Entity existing = carrier(level, lease);
        if (FrontierV3CargoDepartureLedger.get(level, state.bootstrap().worldId()).observation(id(lease)).isPresent()) {
            return FrontierV3SceneExecutor.BodyMaterialization.DEFERRED;
        }
        if (existing != null) {
            if (!owned(state, existing, lease, cargo, items)) return FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
            FrontierV3CargoCarrierPresentation.ensure(level, state, lease, (MinecartChest) existing);
            return FrontierV3SceneExecutor.BodyMaterialization.COMPLETE;
        }
        BlockPos candidate = spawnCandidate(lease);
        if (!knownFixtureColumns && !FrontierV3SceneExecutor.entityStorageReady(level, candidate)) {
            return FrontierV3SceneExecutor.BodyMaterialization.DEFERRED;
        }
        if (knownFixtureColumns && !level.hasChunkAt(candidate)) return FrontierV3SceneExecutor.BodyMaterialization.DEFERRED;
        BlockPos position = FrontierV3StandingPosition.aboveExactFloor(level, candidate);
        if (position == null) return FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
        MinecartChest cart = EntityType.CHEST_MINECART.create(level);
        if (cart == null) throw new IllegalStateException("Minecraft could not create a Frontier v3 cargo carrier");
        cart.setUUID(id(lease));
        cart.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        cart.setCustomName(FrontierV3ScenePresentation.cargoName(state, cargo));
        // The attached TextDisplay is the readable local caption; retain the exact ordinary
        // entity name for accessible metadata without stacking a tiny duplicate above the cart.
        cart.setCustomNameVisible(false); cart.setNoGravity(true);
        if (cargo.fungibleContents()) cart.setItem(0, fungibleStack(state, cargo));
        else for (int index = 0; index < items.size(); index++) cart.setItem(index, FrontierV3CargoHandoffExecutor.materializedStack(items.get(index)));
        cart.getPersistentData().putString(LEASE_KEY, lease.id().value());
        cart.getPersistentData().putLong(REVISION_KEY, lease.revision());
        cart.getPersistentData().putLong(EPOCH_KEY, authority.getAsLong());
        cart.getPersistentData().putString(CARGO_KEY, FrontierSceneBehaviors.logistics(lease).cargoId().value());
        if (!knownFixtureColumns && !FrontierV3CargoFootprintObserver.prepareBirth(level, lease, authority.getAsLong(), cart)) {
            return FrontierV3SceneExecutor.BodyMaterialization.DEFERRED;
        }
        if (!level.addFreshEntity(cart)) return FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
        FrontierV3CargoCarrierPresentation.ensure(level, state, lease, cart);
        return FrontierV3SceneExecutor.BodyMaterialization.COMPLETE;
    }

    static boolean move(ServerLevel level, FrontierWorldState state, SceneLease lease, BlockPosition destination) {
        CargoBatch cargo = state.inventory().cargo().get(FrontierSceneBehaviors.logistics(lease).cargoId());
        if (cargo == null) return false;
        Entity entity = carrier(level, lease);
        if (!owned(state, entity, lease, cargo, items(state, cargo))) return false;
        MinecartChest cart = (MinecartChest) entity;
        BlockPos standing = FrontierV3StandingPosition.aboveExactFloor(level, destination);
        if (standing == null) return false;
        Vec3 target = new Vec3(standing.getX() + 0.5D, standing.getY(), standing.getZ() + 0.5D);
        Vec3 delta = target.subtract(cart.position()); double distance = delta.length();
        if (distance <= ARRIVAL_DISTANCE) return true;
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        // The current graybox carrier is deliberately a no-gravity visual vehicle, not a
        // vanilla rail-cart physics replacement.  A retained one-block graded edge therefore
        // uses its declared destination datum: rise before crossing an uphill support face,
        // cross before descending, and never flatten the stored Y.  Collision remains the
        // world authority for each bounded physical move.
        Vec3 step;
        // Do not use the general arrival tolerance before the uphill horizontal crossing:
        // a minecart's collision box still intersects the raised support until its base reaches
        // the exact destination datum.
        if (delta.y > 1.0E-6D) {
            step = new Vec3(0.0D, Math.min(SPEED, delta.y), 0.0D);
        } else if (delta.y < -ARRIVAL_DISTANCE && horizontalDistance <= ARRIVAL_DISTANCE) {
            step = new Vec3(0.0D, -Math.min(SPEED, -delta.y), 0.0D);
        } else if (horizontalDistance > 1.0E-8D) {
            step = new Vec3(delta.x / horizontalDistance * SPEED, 0.0D, delta.z / horizontalDistance * SPEED);
        } else {
            step = new Vec3(0.0D, Math.copySign(Math.min(SPEED, Math.abs(delta.y)), delta.y), 0.0D);
        }
        if (!level.noCollision(cart, cart.getBoundingBox().move(step))) return true;
        cart.move(MoverType.SELF, step); cart.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    static boolean atDestination(ServerLevel level, FrontierWorldState state, SceneLease lease, BlockPosition destination) {
        CargoBatch cargo = state.inventory().cargo().get(FrontierSceneBehaviors.logistics(lease).cargoId());
        Entity entity = carrier(level, lease);
        if (cargo == null || !owned(state, entity, lease, cargo, items(state, cargo))) return false;
        BlockPos standing = FrontierV3StandingPosition.aboveExactFloor(level, destination);
        if (standing == null) return false;
        Vec3 delta = entity.position().subtract(standing.getX() + 0.5D, standing.getY(), standing.getZ() + 0.5D);
        return delta.lengthSqr() <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
    }

    static boolean intact(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        CargoBatch cargo = state.inventory().cargo().get(FrontierSceneBehaviors.logistics(lease).cargoId());
        return cargo != null && validContents(state, cargo, items(state, cargo))
                && owned(state, carrier(level, lease), lease, cargo, items(state, cargo));
    }

    static Readiness readiness(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        CargoBatch cargo = state.inventory().cargo().get(FrontierSceneBehaviors.logistics(lease).cargoId());
        if (cargo == null || FrontierV3CargoCarrierAuthority.currentEpoch(state.fencedRecovery(), lease).isEmpty()) return Readiness.CONFLICT;
        List<ExactItemStack> items = items(state, cargo);
        if (!validContents(state, cargo, items)) return Readiness.CONFLICT;
        Entity existing = carrier(level, lease);
        if (existing != null) return owned(state, existing, lease, cargo, items) ? Readiness.CURRENT : Readiness.CONFLICT;
        BlockPos candidate = spawnCandidate(lease);
        if (!level.hasChunkAt(candidate)) return Readiness.UNLOADED;
        return FrontierV3StandingPosition.aboveExactFloor(level, candidate) == null ? Readiness.BLOCKED : Readiness.READY;
    }

    /** Pending retirement, not evictable scene history, owns terminal provider cleanup. */
    static void cleanRetired(ServerLevel level, FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.model.CargoProjectionRetirement retirement) {
        if (!retirement.worldId().equals(state.bootstrap().worldId())
                || !retirement.equals(state.fencedRecovery().cargoRetirements().pending().get(retirement.entityId()))) return;
        if (!(level.getEntity(retirement.entityId()) instanceof MinecartChest cart) || cart.isRemoved()) return;
        if (retirement.disposition() == io.farfrontier.palemirror.frontier.v3.model.CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY) {
            if (hasDeclaration(cart)) FrontierV3CargoDepartureObserver.observeJoin(level, state, cart);
            else FrontierV3CargoFootprintObserver.retireIdentity(level, retirement, cart);
            return;
        }
        var lease = state.sceneLeases().get(retirement.leaseId());
        if (lease != null) discardClosed(level, state, lease);
        else FrontierV3CargoDepartureObserver.observeJoin(level, state, cart);
    }

    static void discardClosed(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        var terminal = state.fencedRecovery().cargoRetirements().pending().get(id(lease));
        if (terminal != null && terminal.disposition()
                == io.farfrontier.palemirror.frontier.v3.model.CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY
                && carrier(level, lease) instanceof MinecartChest retained) {
            FrontierV3CargoFootprintObserver.retireIdentity(level, terminal, retained);
            return;
        }
        // A canonical cargo batch alone is not authority to delete a naturally returned cart.
        // It must be the exact retired scene-cargo binding.  Released player/world custody is
        // intentionally not intact and therefore remains outside this cleanup path.
        if (intact(level, state, lease) && FrontierV3ClosedProjectionFence.cargoIsStale(state, lease)) {
            var retirement = state.fencedRecovery().cargoRetirements().pending().get(id(lease));
            if (retirement == null || retirement.disposition()
                    != io.farfrontier.palemirror.frontier.v3.model.CargoProjectionRetirement.Disposition.REMOVE_PROJECTION
                    || hasWorldCustody(state, id(lease))) return;
            var cart = (MinecartChest) carrier(level, lease);
            var receipt = new FrontierV3CargoDeparture(lease.id(), retirement.cargoId(), cart.getUUID(), lease.revision(),
                    retirement.authorization().retiredEpoch(), new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(
                    cart.getBlockX(), cart.getBlockY(), cart.getBlockZ()), observedInventory(cart));
            if (!FrontierV3CargoDepartureObserver.samePhysicalSnapshot(cart, receipt)
                    || !FrontierV3CargoDepartureObserver.retainCleanupWitness(level, state, receipt)) return;
            FrontierV3CargoCarrierPresentation.discard(cart, lease);
            cart.clearContent(); cart.discard();
        }
    }

    static UUID id(SceneLease lease) {
        return io.farfrontier.palemirror.frontier.v3.model.CargoCarrierIdentity.id(lease);
    }

    /** Finds the one still-atomic HOT shipment represented by this exact Minecraft cart. */
    static Optional<SceneLease> activeLease(FrontierWorldState state, Entity entity) {
        return activeLease(state.sceneLeases().values(), lease -> intactEntity(state, entity, lease));
    }

    static Optional<SceneLease> activeLease(java.util.Collection<SceneLease> leases, java.util.function.Predicate<SceneLease> intactCarrier) {
        return leases.stream().filter(lease -> lease.status() == io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.HOT)
                .filter(FrontierSceneBehaviors::isLogistics)
                .filter(intactCarrier).min(Comparator.comparing(SceneLease::id));
    }

    /** Applies world-carrier provenance after durable release, before ordinary container use. */
    static boolean markReleasedCarrier(FrontierWorldState state, SceneLease lease, Entity entity) {
        CargoBatch cargo = state.inventory().cargo().get(FrontierSceneBehaviors.logistics(lease).cargoId());
        if (cargo == null) return false;
        List<ExactItemStack> items = items(state, cargo);
        if (!owned(state, entity, lease, cargo, items)) return false;
        MinecartChest cart = (MinecartChest) entity;
        FrontierV3CargoCarrierPresentation.discard(cart, lease);
        for (int index = 0; index < items.size(); index++) {
            var stack = cart.getItem(index);
            FrontierV3CargoHandoffExecutor.bindWorldCarrier(stack, cart.getUUID());
            cart.setItem(index, stack);
        }
        cart.setChanged();
        return true;
    }

    static boolean owned(FrontierWorldState state, Entity entity, SceneLease lease, CargoBatch cargo, List<ExactItemStack> items) {
        return owned(state, entity, lease, cargo, items, false);
    }

    private static boolean owned(FrontierWorldState state, Entity entity, SceneLease lease, CargoBatch cargo,
                                 List<ExactItemStack> items, boolean unloading) {
        if (!(entity instanceof MinecartChest cart) || !matchesDeclaration(state, entity, lease, unloading)
                || cart.getContainerSize() < Math.max(1, items.size())) return false;
        if (!unloading && entity.level() instanceof ServerLevel level
                && FrontierV3CargoDepartureLedger.get(level, state.bootstrap().worldId()).observation(entity.getUUID()).isPresent()) return false;
        if (cargo.fungibleContents()) {
            ItemStack expected = fungibleStack(state, cargo);
            if (expected.isEmpty() || cart.getItem(0).getCount() != expected.getCount()
                    || !ItemStack.isSameItemSameComponents(cart.getItem(0), expected)) return false;
            for (int index = 1; index < cart.getContainerSize(); index++) if (!cart.getItem(index).isEmpty()) return false;
            return true;
        }
        for (int index = 0; index < items.size(); index++) if (!FrontierV3CargoHandoffExecutor.exactMatch(cart.getItem(index), items.get(index))) return false;
        for (int index = items.size(); index < cart.getContainerSize(); index++) if (!cart.getItem(index).isEmpty()) return false;
        return true;
    }

    private static boolean matchesDeclaration(FrontierWorldState state, Entity entity, SceneLease lease) {
        return matchesDeclaration(state, entity, lease, false);
    }

    private static boolean matchesDeclaration(FrontierWorldState state, Entity entity, SceneLease lease, boolean unloading) {
        if (!(entity instanceof MinecartChest) || !FrontierSceneBehaviors.isLogistics(lease)) return false;
        if (unloading ? entity.getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK : entity.isRemoved()) return false;
        var tag = entity.getPersistentData();
        return id(lease).equals(entity.getUUID()) && lease.id().value().equals(tag.getString(LEASE_KEY))
                && tag.contains(REVISION_KEY, net.minecraft.nbt.Tag.TAG_LONG) && tag.getLong(REVISION_KEY) == lease.revision()
                && tag.contains(EPOCH_KEY, net.minecraft.nbt.Tag.TAG_LONG)
                && FrontierV3CargoCarrierAuthority.matches(state.fencedRecovery(), lease, tag.getLong(EPOCH_KEY))
                && FrontierSceneBehaviors.logistics(lease).cargoId().value().equals(tag.getString(CARGO_KEY));
    }

    /** Called only with the exact declared scene at a real chunk-unload boundary. */
    static Optional<FrontierV3CargoDeparture> captureDeparture(FrontierWorldState state, Entity entity, SceneLease lease) {
        return captureObservation(state, entity, lease, true);
    }

    /** Current loaded observation for durable pre-close preparation, not an unload event. */
    static Optional<FrontierV3CargoDeparture> captureLoadedRelease(FrontierWorldState state, Entity entity, SceneLease lease) {
        return captureObservation(state, entity, lease, false);
    }

    private static Optional<FrontierV3CargoDeparture> captureObservation(FrontierWorldState state, Entity entity, SceneLease lease,
                                                                      boolean unloading) {
        if (!FrontierSceneBehaviors.isLogistics(lease)
                || lease.status() == io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED) return Optional.empty();
        CargoBatch cargo = state.inventory().cargo().get(FrontierSceneBehaviors.logistics(lease).cargoId());
        if (cargo == null) return Optional.empty();
        var exact = items(state, cargo);
        if (!validContents(state, cargo, exact) || !owned(state, entity, lease, cargo, exact, unloading)) return Optional.empty();
        var cart = (MinecartChest) entity;
        if (cart.getContainerSize() != FrontierV3CargoDeparture.SLOTS) return Optional.empty();
        var inventory = observedInventory(cart);
        return Optional.of(new FrontierV3CargoDeparture(lease.id(), cargo.id(), cart.getUUID(), lease.revision(),
                cart.getPersistentData().getLong(EPOCH_KEY),
                new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(cart.getBlockX(), cart.getBlockY(), cart.getBlockZ()), inventory));
    }

    static List<net.minecraft.nbt.CompoundTag> observedInventory(MinecartChest cart) {
        return java.util.stream.IntStream.range(0, cart.getContainerSize())
                .mapToObj(slot -> (net.minecraft.nbt.CompoundTag) cart.getItem(slot).saveOptional(cart.registryAccess())).toList();
    }

    /** Revalidate the full expected contents and current attempt, never just the cart UUID. */
    static boolean currentDeparture(FrontierWorldState state, SceneLease lease, FrontierV3CargoDeparture receipt,
                                     net.minecraft.core.HolderLookup.Provider registries) {
        if (!FrontierSceneBehaviors.isLogistics(lease) || !receipt.leaseId().equals(lease.id())
                || receipt.sceneRevision() != lease.revision() || !receipt.entityId().equals(id(lease))
                || !receipt.cargoId().equals(FrontierSceneBehaviors.logistics(lease).cargoId())
                || FrontierV3CargoCarrierAuthority.currentEpoch(state.fencedRecovery(), lease).orElse(-1L) != receipt.authorityEpoch()) return false;
        CargoBatch cargo = state.inventory().cargo().get(receipt.cargoId());
        if (cargo == null) return false;
        var exact = items(state, cargo);
        if (!validContents(state, cargo, exact) || exact.size() > FrontierV3CargoDeparture.SLOTS) return false;
        var expected = java.util.stream.IntStream.range(0, FrontierV3CargoDeparture.SLOTS).mapToObj(slot -> {
            ItemStack stack = cargo.fungibleContents() ? (slot == 0 ? fungibleStack(state, cargo) : ItemStack.EMPTY)
                    : (slot < exact.size() ? FrontierV3CargoHandoffExecutor.materializedStack(exact.get(slot)) : ItemStack.EMPTY);
            return (net.minecraft.nbt.CompoundTag) stack.saveOptional(registries);
        }).toList();
        return receipt.inventory().equals(expected);
    }

    /** World custody alone cannot validate a stale physical declaration from another attempt. */
    static boolean hasCurrentDeclaration(FrontierWorldState state, Entity entity) {
        var retained = state.fencedRecovery().cargoRetirements().pending().get(entity.getUUID());
        if (entity instanceof MinecartChest && !entity.isRemoved() && retained != null
                && retained.worldId().equals(state.bootstrap().worldId())
                && retained.disposition() == io.farfrontier.palemirror.frontier.v3.model.CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY
                && hasWorldCustody(state, entity.getUUID())
                && matchesRetiredDeclaration(retained, entity.getUUID(), entity.getPersistentData())) return true;
        try {
            var lease = state.sceneLeases().get(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(
                    entity.getPersistentData().getString(LEASE_KEY)));
            return lease != null && matchesDeclaration(state, entity, lease);
        } catch (IllegalArgumentException invalidDeclaration) { return false; }
    }

    static boolean matchesRetiredDeclaration(
            io.farfrontier.palemirror.frontier.v3.model.CargoProjectionRetirement retired,
            UUID entity, net.minecraft.nbt.CompoundTag tag) {
        return retired.entityId().equals(entity)
                && tag.contains(LEASE_KEY, net.minecraft.nbt.Tag.TAG_STRING)
                && tag.getString(LEASE_KEY).equals(retired.leaseId().value())
                && tag.contains(CARGO_KEY, net.minecraft.nbt.Tag.TAG_STRING)
                && tag.getString(CARGO_KEY).equals(retired.cargoId().value())
                && tag.contains(REVISION_KEY, net.minecraft.nbt.Tag.TAG_LONG)
                && tag.getLong(REVISION_KEY) == retired.authorization().ownerRevision()
                && tag.contains(EPOCH_KEY, net.minecraft.nbt.Tag.TAG_LONG)
                && tag.getLong(EPOCH_KEY) == retired.authorization().retiredEpoch();
    }

    static boolean active(FrontierWorldState state, Entity entity) {
        return activeLease(state, entity).isPresent();
    }

    /** Partial declarations are managed-but-invalid, never an ordinary container bypass. */
    static boolean hasDeclaration(Entity entity) {
        var tag = entity.getPersistentData();
        return tag.contains(LEASE_KEY) || tag.contains(CARGO_KEY) || tag.contains(REVISION_KEY) || tag.contains(EPOCH_KEY);
    }

    /** Canonical custody, not scene tags or current contents, owns post-release observation. */
    static boolean hasWorldCustody(FrontierWorldState state, UUID carrierId) {
        return state.inventory().hasWorldCarrierCustody(carrierId);
    }

    /** Only a durable accepted handoff permits removing the scene's physical declaration. */
    static void relinquishDeclaration(Entity entity) {
        var tag = entity.getPersistentData();
        tag.remove(LEASE_KEY); tag.remove(CARGO_KEY); tag.remove(REVISION_KEY); tag.remove(EPOCH_KEY);
        entity.setNoGravity(false);
    }

    private static boolean intactEntity(FrontierWorldState state, Entity entity, SceneLease lease) {
        CargoBatch cargo = state.inventory().cargo().get(FrontierSceneBehaviors.logistics(lease).cargoId());
        return cargo != null && validContents(state, cargo, items(state, cargo))
                && owned(state, entity, lease, cargo, items(state, cargo));
    }

    private static List<ExactItemStack> items(FrontierWorldState state, CargoBatch cargo) {
        return cargo.itemIds().stream().map(state.inventory().items()::get).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ExactItemStack::id)).toList();
    }

    private static boolean validContents(FrontierWorldState state, CargoBatch cargo, List<ExactItemStack> items) {
        return cargo.fungibleContents() ? !fungibleStack(state, cargo).isEmpty() : items.size() == cargo.itemIds().size();
    }

    /** A fungible route batch has one bounded physical carrier stack, never a permanent stack ID. */
    private static ItemStack fungibleStack(FrontierWorldState state, CargoBatch cargo) {
        var account = state.inventory().fungibleResources().accounts().values().stream()
                .filter(value -> value.custody() instanceof io.farfrontier.palemirror.frontier.v3.model.ResourceCustody.Cargo carried
                        && carried.cargoId().equals(cargo.id()))
                .findFirst().orElse(null);
        if (account == null || account.lotQuantities().isEmpty() || account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum() > 64) {
            return ItemStack.EMPTY;
        }
        String kind = account.lotQuantities().keySet().stream().map(state.inventory().fungibleResources().lots()::get)
                .map(ResourceLot::itemKind).distinct().reduce((left, right) -> "").orElse("");
        if (kind.isEmpty()) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(kind)).orElse(null);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum());
    }

    private static Entity carrier(ServerLevel level, SceneLease lease) {
        Entity direct = level.getEntity(id(lease));
        if (direct != null) return direct;
        return level.getEntitiesOfClass(MinecartChest.class, new AABB(spawnCandidate(lease)).inflate(32.0D), entity -> id(lease).equals(entity.getUUID()))
                .stream().min(Comparator.comparing(Entity::getUUID)).orElse(null);
    }

    private static BlockPos spawnCandidate(SceneLease lease) {
        BlockPosition cargoPosition = FrontierSceneBehaviors.logistics(lease).cargoPosition();
        return new BlockPos(cargoPosition.x(), cargoPosition.y(), cargoPosition.z());
    }

}
