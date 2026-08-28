package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.CargoBatch;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResidentRole;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Loaded-world exact cargo representation for one HOT route scene. */
final class FrontierV3CargoCarrierExecutor {
    static final String LEASE_KEY = "pale_mirror_frontier_v3_cargo_carrier_lease";
    static final String CARGO_KEY = "pale_mirror_frontier_v3_cargo_carrier";
    private static final double SPEED = 0.055D, ARRIVAL_DISTANCE = 0.35D;

    private FrontierV3CargoCarrierExecutor() { }

    static FrontierV3SceneExecutor.BodyMaterialization materialize(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        CargoBatch cargo = state.inventory().cargo().get(lease.cargoId());
        if (cargo == null) return FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
        List<ExactItemStack> items = items(state, cargo);
        if (items.size() != cargo.itemIds().size()) return FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
        Entity existing = carrier(level, lease);
        if (existing != null) return owned(existing, lease, items) ? FrontierV3SceneExecutor.BodyMaterialization.COMPLETE
                : FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
        BlockPos candidate = spawnCandidate(lease).east();
        if (!level.hasChunkAt(candidate)) return FrontierV3SceneExecutor.BodyMaterialization.DEFERRED;
        if (!supported(level, candidate)) return FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
        MinecartChest cart = EntityType.CHEST_MINECART.create(level);
        if (cart == null) throw new IllegalStateException("Minecraft could not create a Frontier v3 cargo carrier");
        cart.setUUID(id(lease));
        cart.setPos(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
        cart.setCustomName(Component.literal("Frontier cargo " + lease.cargoId().value()));
        cart.setCustomNameVisible(true); cart.setNoGravity(true);
        for (int index = 0; index < items.size(); index++) cart.setItem(index, FrontierV3CargoHandoffExecutor.materializedStack(items.get(index)));
        cart.getPersistentData().putString(LEASE_KEY, lease.id().value());
        cart.getPersistentData().putString(CARGO_KEY, lease.cargoId().value());
        return level.addFreshEntity(cart) ? FrontierV3SceneExecutor.BodyMaterialization.COMPLETE : FrontierV3SceneExecutor.BodyMaterialization.CONFLICT;
    }

    static boolean move(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        CargoBatch cargo = state.inventory().cargo().get(lease.cargoId());
        if (cargo == null) return false;
        Entity entity = carrier(level, lease);
        if (!owned(entity, lease, items(state, cargo))) return false;
        MinecartChest cart = (MinecartChest) entity;
        Mob hauler = lease.members().stream().filter(member -> residentHauler(state, member.actorId())).map(member -> level.getEntity(member.entityId()))
                .filter(Mob.class::isInstance).map(Mob.class::cast).filter(Mob::isAlive).min(Comparator.comparing(Entity::getUUID)).orElse(null);
        if (hauler == null) return false;
        Vec3 target = hauler.position().subtract(hauler.getLookAngle().normalize().scale(1.25D));
        Vec3 delta = target.subtract(cart.position()); double distance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (distance <= ARRIVAL_DISTANCE) return true;
        Vec3 step = new Vec3(delta.x / distance * SPEED, 0.0D, delta.z / distance * SPEED);
        if (!level.noCollision(cart, cart.getBoundingBox().move(step))) return true;
        cart.move(MoverType.SELF, step); cart.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    static boolean intact(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        CargoBatch cargo = state.inventory().cargo().get(lease.cargoId());
        return cargo != null && cargo.itemIds().size() == items(state, cargo).size() && owned(carrier(level, lease), lease, items(state, cargo));
    }

    static void discardClosed(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        if (intact(level, state, lease)) carrier(level, lease).discard();
    }

    static UUID id(SceneLease lease) {
        return UUID.nameUUIDFromBytes(("frontier-v3:cargo-carrier:" + lease.worldId().value() + ":" + lease.id().value() + ":" + lease.cargoId().value())
                .getBytes(StandardCharsets.UTF_8));
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
        return state.sceneLeases().values().stream().filter(lease -> lease.status() != io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED)
                .anyMatch(lease -> intactEntity(state, entity, lease));
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
        return level.getEntitiesOfClass(MinecartChest.class, new AABB(spawnCandidate(lease).east()).inflate(32.0D), entity -> id(lease).equals(entity.getUUID()))
                .stream().min(Comparator.comparing(Entity::getUUID)).orElse(null);
    }

    private static BlockPos spawnCandidate(SceneLease lease) {
        int ordinal = lease.members().size();
        return new BlockPos(lease.handoffPosition().x() + (ordinal % 2) * 2, lease.handoffPosition().y(), lease.handoffPosition().z() + (ordinal / 2) * 2);
    }

    private static boolean supported(ServerLevel level, BlockPos position) {
        return level.getBlockState(position).isAir() && level.getBlockState(position.above()).isAir()
                && level.getBlockState(position.below()).isFaceSturdy(level, position.below(), net.minecraft.core.Direction.UP);
    }

    private static boolean residentHauler(FrontierWorldState state, SubjectId actorId) {
        return state.bootstrap().settlements().stream().flatMap(settlement -> settlement.residents().stream())
                .anyMatch(resident -> resident.id().equals(actorId) && resident.role() == ResidentRole.HAULER);
    }
}
