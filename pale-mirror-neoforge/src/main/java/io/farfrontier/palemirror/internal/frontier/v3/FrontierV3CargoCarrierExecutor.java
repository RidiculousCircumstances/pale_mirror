package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.CargoBatch;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.MinecartChest;
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
    private static final double SPEED = 0.055D, ARRIVAL_DISTANCE = 0.35D;

    /** Bounded read-only physical admission result for one exact HOT cargo carrier. */
    enum Readiness { CURRENT, READY, UNLOADED, BLOCKED, CONFLICT }

    private FrontierV3CargoCarrierExecutor() { }

    static FrontierV3SceneExecutor.BodyMaterialization materialize(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        CargoBatch cargo = state.inventory().cargo().get(lease.cargoId());
        if (cargo == null) return FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
        List<ExactItemStack> items = items(state, cargo);
        if (items.size() != cargo.itemIds().size()) return FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
        Entity existing = carrier(level, lease);
        if (existing != null) return owned(existing, lease, items) ? FrontierV3SceneExecutor.BodyMaterialization.COMPLETE
                : FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
        BlockPos candidate = spawnCandidate(lease);
        if (!level.hasChunkAt(candidate)) return FrontierV3SceneExecutor.BodyMaterialization.DEFERRED;
        BlockPos position = FrontierV3StandingPosition.aboveFloor(level, candidate);
        if (position == null) return FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
        MinecartChest cart = EntityType.CHEST_MINECART.create(level);
        if (cart == null) throw new IllegalStateException("Minecraft could not create a Frontier v3 cargo carrier");
        cart.setUUID(id(lease));
        cart.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        cart.setCustomName(FrontierV3ScenePresentation.cargoName(state, cargo));
        cart.setCustomNameVisible(true); cart.setNoGravity(true);
        for (int index = 0; index < items.size(); index++) cart.setItem(index, FrontierV3CargoHandoffExecutor.materializedStack(items.get(index)));
        cart.getPersistentData().putString(LEASE_KEY, lease.id().value());
        cart.getPersistentData().putString(CARGO_KEY, lease.cargoId().value());
        return level.addFreshEntity(cart) ? FrontierV3SceneExecutor.BodyMaterialization.COMPLETE : FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
    }

    static boolean move(ServerLevel level, FrontierWorldState state, SceneLease lease, BlockPosition destination) {
        CargoBatch cargo = state.inventory().cargo().get(lease.cargoId());
        if (cargo == null) return false;
        Entity entity = carrier(level, lease);
        if (!owned(entity, lease, items(state, cargo))) return false;
        MinecartChest cart = (MinecartChest) entity;
        Vec3 target = new Vec3(destination.x() + 0.5D, cart.getY(), destination.z() + 0.5D);
        Vec3 delta = target.subtract(cart.position()); double distance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (distance <= ARRIVAL_DISTANCE) return true;
        Vec3 step = new Vec3(delta.x / distance * SPEED, 0.0D, delta.z / distance * SPEED);
        if (!level.noCollision(cart, cart.getBoundingBox().move(step))) return true;
        cart.move(MoverType.SELF, step); cart.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    static boolean atDestination(ServerLevel level, FrontierWorldState state, SceneLease lease, BlockPosition destination) {
        CargoBatch cargo = state.inventory().cargo().get(lease.cargoId());
        Entity entity = carrier(level, lease);
        if (cargo == null || !owned(entity, lease, items(state, cargo))) return false;
        Vec3 delta = entity.position().subtract(destination.x() + 0.5D, entity.getY(), destination.z() + 0.5D);
        return delta.x * delta.x + delta.z * delta.z <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
    }

    static boolean intact(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        CargoBatch cargo = state.inventory().cargo().get(lease.cargoId());
        return cargo != null && cargo.itemIds().size() == items(state, cargo).size() && owned(carrier(level, lease), lease, items(state, cargo));
    }

    static Readiness readiness(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        CargoBatch cargo = state.inventory().cargo().get(lease.cargoId());
        if (cargo == null) return Readiness.CONFLICT;
        List<ExactItemStack> items = items(state, cargo);
        if (items.size() != cargo.itemIds().size()) return Readiness.CONFLICT;
        Entity existing = carrier(level, lease);
        if (existing != null) return owned(existing, lease, items) ? Readiness.CURRENT : Readiness.CONFLICT;
        BlockPos candidate = spawnCandidate(lease);
        if (!level.hasChunkAt(candidate)) return Readiness.UNLOADED;
        return FrontierV3StandingPosition.aboveFloor(level, candidate) == null ? Readiness.BLOCKED : Readiness.READY;
    }

    static void discardClosed(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        if (intact(level, state, lease)) carrier(level, lease).discard();
    }

    static UUID id(SceneLease lease) {
        return io.farfrontier.palemirror.frontier.v3.model.CargoCarrierIdentity.id(lease);
    }

    /** Finds the one still-atomic HOT shipment represented by this exact Minecraft cart. */
    static Optional<SceneLease> activeLease(FrontierWorldState state, Entity entity) {
        return state.sceneLeases().values().stream().filter(lease -> lease.status() == io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.HOT)
                .filter(lease -> intactEntity(state, entity, lease)).sorted(Comparator.comparing(SceneLease::id)).findFirst();
    }

    /** Applies stable world-carrier provenance before the durable release permits container use. */
    static boolean markReleasedCarrier(FrontierWorldState state, SceneLease lease, Entity entity) {
        CargoBatch cargo = state.inventory().cargo().get(lease.cargoId());
        if (cargo == null) return false;
        List<ExactItemStack> items = items(state, cargo);
        if (!owned(entity, lease, items)) return false;
        MinecartChest cart = (MinecartChest) entity;
        for (int index = 0; index < items.size(); index++) {
            var stack = cart.getItem(index);
            FrontierV3CargoHandoffExecutor.bindWorldCarrier(stack, cart.getUUID());
            cart.setItem(index, stack);
        }
        cart.setChanged();
        return true;
    }

    static boolean owned(Entity entity, SceneLease lease, List<ExactItemStack> items) {
        if (!(entity instanceof MinecartChest cart) || entity.isRemoved() || !id(lease).equals(entity.getUUID())
                || !lease.id().value().equals(entity.getPersistentData().getString(LEASE_KEY))
                || !lease.cargoId().value().equals(entity.getPersistentData().getString(CARGO_KEY)) || cart.getContainerSize() < items.size()) return false;
        for (int index = 0; index < items.size(); index++) if (!FrontierV3CargoHandoffExecutor.exactMatch(cart.getItem(index), items.get(index))) return false;
        for (int index = items.size(); index < cart.getContainerSize(); index++) if (!cart.getItem(index).isEmpty()) return false;
        return true;
    }

    static boolean active(FrontierWorldState state, Entity entity) {
        return activeLease(state, entity).isPresent();
    }

    private static boolean intactEntity(FrontierWorldState state, Entity entity, SceneLease lease) {
        CargoBatch cargo = state.inventory().cargo().get(lease.cargoId());
        return cargo != null && cargo.itemIds().size() == items(state, cargo).size() && owned(entity, lease, items(state, cargo));
    }

    private static List<ExactItemStack> items(FrontierWorldState state, CargoBatch cargo) {
        return cargo.itemIds().stream().map(state.inventory().items()::get).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ExactItemStack::id)).toList();
    }

    private static Entity carrier(ServerLevel level, SceneLease lease) {
        Entity direct = level.getEntity(id(lease));
        if (direct != null) return direct;
        return level.getEntitiesOfClass(MinecartChest.class, new AABB(spawnCandidate(lease)).inflate(32.0D), entity -> id(lease).equals(entity.getUUID()))
                .stream().min(Comparator.comparing(Entity::getUUID)).orElse(null);
    }

    private static BlockPos spawnCandidate(SceneLease lease) {
        return new BlockPos(lease.cargoPosition().x(), lease.cargoPosition().y(), lease.cargoPosition().z());
    }

}
