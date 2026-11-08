package io.farfrontier.palemirror.internal.frontier.v3;

import earth.terrarium.handcrafted.common.blocks.base.SittableBlock;
import earth.terrarium.handcrafted.common.entities.Seat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** A saved Handcrafted seat root, so its managed passenger participates in ordinary EntityStorage saves. */
final class FrontierV3SeatEntity extends Seat implements FrontierV3SeatCarrier {
    record Binding(ActorActuationId actor, UUID entityId, SeatPlace.Id seat, long carrierEpoch) { }
    private Optional<Binding> binding = Optional.empty();
    private AABB seatShape;
    private BlockPos exit;
    private int emptyTicks;
    private boolean occupancyRegistered;

    FrontierV3SeatEntity(EntityType<? extends Entity> type, Level level) { super(type, level); }
    static UUID identity(io.farfrontier.palemirror.frontier.v3.api.WorldId world, ActorActuationId actor, SeatPlace.Id seat, long carrierEpoch) {
        var execution = actor.execution();
        return UUID.nameUUIDFromBytes(("pm-seat-v1|" + world.value() + "|" + actor.body().actorId().value() + "|" + actor.body().physicalEpoch()
                + "|" + FrontierWireTags.tag(execution.activityKind()) + "|" + execution.activityOwnerId().value() + "|" + execution.generation()
                + "|" + seat.buildingId().value() + "|" + seat.localId() + "|" + carrierEpoch).getBytes(StandardCharsets.UTF_8));
    }
    static FrontierV3SeatEntity create(Level level, io.farfrontier.palemirror.frontier.v3.api.WorldId world, ActorActuationId actor, UUID entityId, SeatPlace place, long carrierEpoch) {
        var result = new FrontierV3SeatEntity(PaleMirrorSeatEntities.require(), level);
        var chair = pos(place.chair()); var block = level.getBlockState(chair);
        if (!BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString().equals(place.chairBlock()) || !(block.getBlock() instanceof SittableBlock sittable))
            throw new IllegalArgumentException("persistent seat requires its exact current furniture");
        if (carrierEpoch < 1) throw new IllegalArgumentException("invalid seat carrier incarnation");
        result.binding = Optional.of(new Binding(actor, entityId, place.id(), carrierEpoch));
        result.seatShape = sittable.getSeatSize(block);
        result.exit = pos(place.egress().support()).above();
        result.setUUID(identity(world, actor, place.id(), carrierEpoch));
        result.setYRot(Direction.valueOf(place.facing().name()).toYRot());
        result.setPos(chair.getX() + .5, chair.getY(), chair.getZ() + .5);
        return result;
    }
    boolean boundTo(ActorActuationId actor, UUID entityId, SeatPlace.Id place, long carrierEpoch) {
        return binding.equals(Optional.of(new Binding(actor, entityId, place, carrierEpoch)));
    }
    @Override protected boolean canAddPassenger(Entity passenger) {
        return binding.filter(proof -> proof.entityId().equals(passenger.getUUID())).isPresent() && getPassengers().isEmpty();
    }
    private AABB shape() {
        if (seatShape != null) return seatShape;
        if (level() != null && level().hasChunkAt(blockPosition())) {
            var block = level().getBlockState(blockPosition());
            if (block.getBlock() instanceof SittableBlock sittable) return sittable.getSeatSize(block);
        }
        return null;
    }
    @Override protected AABB makeBoundingBox() {
        var shape = shape();
        return shape == null ? super.makeBoundingBox() : shape.move(blockPosition());
    }
    @Override protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        var shape = shape();
        if (shape == null) return super.getPassengerAttachmentPoint(passenger, dimensions, partialTick);
        double height = shape.getYsize() * .75F + .2F;
        if (passenger instanceof net.minecraft.world.entity.npc.Villager actor) {
            // Handcrafted seats are calibrated for Player's VEHICLE attachment at the hips.
            // Villager's default attachment is AT_FEET; preserve the same actual seated hip contact.
            height += actor.getVehicleAttachmentPoint(this).y
                    - net.minecraft.world.entity.player.Player.DEFAULT_VEHICLE_ATTACHMENT.y * actor.getScale();
        }
        return new Vec3(0, height, 0);
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return loadedExit(passenger).orElse(passenger.position());
    }
    Optional<Vec3> loadedExit(LivingEntity passenger) {
        var candidates = new LinkedHashSet<BlockPos>();
        if (exit != null) candidates.add(exit);
        for (var direction : Direction.Plane.HORIZONTAL) candidates.add(blockPosition().relative(direction));
        for (var feet : candidates) {
            if (!level().hasChunkAt(feet) || !level().hasChunkAt(feet.below())) continue;
            var box = passenger.getBoundingBox().move(feet.getX() + .5 - passenger.getX(), feet.getY() - passenger.getY(), feet.getZ() + .5 - passenger.getZ());
            boolean loaded = true;
            for (int x = (int) Math.floor(box.minX) >> 4; x <= (int) Math.floor(box.maxX) >> 4; x++)
                for (int z = (int) Math.floor(box.minZ) >> 4; z <= (int) Math.floor(box.maxZ) >> 4; z++)
                    if (!level().hasChunk(x, z)) loaded = false;
            if (!loaded) continue;
            var support = feet.below(); var shape = level().getBlockState(support).getCollisionShape(level(), support);
            if (!shape.isEmpty() && Math.abs(shape.max(Direction.Axis.Y) - 1) < .001
                    && level().getFluidState(feet).isEmpty() && level().getFluidState(support).isEmpty() && level().noCollision(passenger, box))
                return Optional.of(new Vec3(feet.getX() + .5, feet.getY(), feet.getZ() + .5));
        }
        return Optional.empty();
    }
    @Override public boolean contactEvidence(Entity passenger, BlockGetter geometry, BlockPos support) {
        if (binding.isEmpty() || passenger.getVehicle() != this || !hasPassenger(passenger) || !support.equals(blockPosition())) return false;
        var proof = binding.orElseThrow(); var declaration = FrontierV3ActorCarrierComposition.declaredBy(passenger);
        if (!passenger.getUUID().equals(proof.entityId()) || declaration.isEmpty()
                || !declaration.orElseThrow().actorId().equals(proof.actor().body().actorId())
                || declaration.orElseThrow().epoch() != proof.actor().body().physicalEpoch()) return false;
        var block = geometry.getBlockState(support);
        if (!(block.getBlock() instanceof SittableBlock sittable) || !sittable.getSeatSize(block).equals(shape())) return false;
        var expected = getPassengerRidingPosition(passenger).subtract(passenger.getVehicleAttachmentPoint(this));
        return passenger.position().distanceToSqr(expected) < .000001;
    }
    @Override public void tick() {
        if (!getPassengers().isEmpty()) { emptyTicks = 0; super.tick(); return; }
        if (!level().isClientSide && !isRemoved() && ++emptyTicks % 20 == 0) {
            var ledger = FrontierV3OperationLedger.get((net.minecraft.server.level.ServerLevel) level());
            if (FrontierV3NativeOperationMarkers.read(getPersistentData()).stream().allMatch(ledger::prunable)) removeSeat();
        }
    }
    @Override protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide && !occupancyRegistered) {
            SITTING_POSITIONS.put(level().dimension(), blockPosition()); occupancyRegistered = true;
        }
    }
    @Override protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (getPassengers().isEmpty()) releaseOccupancy();
    }
    private void releaseOccupancy() {
        if (occupancyRegistered) { SITTING_POSITIONS.remove(level().dimension(), blockPosition()); occupancyRegistered = false; }
    }
    @Override public void removeSeat() { releaseOccupancy(); discard(); }
    @Override public void onRemovedFromLevel() { releaseOccupancy(); super.onRemovedFromLevel(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        var proof = binding.orElseThrow(() -> new IllegalStateException("unbound persistent seat cannot be saved"));
        tag.putInt("pmSeatSchema", 1);
        tag.putString("actor", proof.actor().body().actorId().value()); tag.putLong("physicalEpoch", proof.actor().body().physicalEpoch());
        tag.putUUID("actorEntity", proof.entityId()); tag.putInt("activityKind", FrontierWireTags.tag(proof.actor().execution().activityKind()));
        tag.putString("activityOwner", proof.actor().execution().activityOwnerId().value()); tag.putLong("generation", proof.actor().execution().generation());
        tag.putString("building", proof.seat().buildingId().value()); tag.putString("seat", proof.seat().localId());
        tag.putLong("carrierEpoch", proof.carrierEpoch());
        var shape = Objects.requireNonNull(shape(), "saved seat geometry");
        tag.put("seatShape", newDoubleList(shape.minX, shape.minY, shape.minZ, shape.maxX, shape.maxY, shape.maxZ));
        tag.putIntArray("egress", new int[]{exit.getX(), exit.getY(), exit.getZ()});
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.getInt("pmSeatSchema") != 1) throw new IllegalArgumentException("unsupported persistent seat schema");
        var actor = new SubjectId(tag.getString("actor"));
        var execution = new ActorExecutionId(actor, FrontierWireTags.require(ActorActivityKind.class, tag.getInt("activityKind")),
                new SubjectId(tag.getString("activityOwner")), tag.getLong("generation"));
        binding = Optional.of(new Binding(new ActorActuationId(new ActorBodyId(actor, tag.getLong("physicalEpoch")), execution),
                tag.getUUID("actorEntity"), new SeatPlace.Id(new SubjectId(tag.getString("building")), tag.getString("seat")), tag.getLong("carrierEpoch")));
        if (binding.orElseThrow().carrierEpoch() < 1) throw new IllegalArgumentException("invalid saved seat carrier incarnation");
        var values = tag.getList("seatShape", Tag.TAG_DOUBLE); var egress = tag.getIntArray("egress");
        if (values.size() != 6 || egress.length != 3) throw new IllegalArgumentException("incomplete saved seat geometry");
        for (var value : values) if (!Double.isFinite(((DoubleTag) value).getAsDouble()) || Math.abs(((DoubleTag) value).getAsDouble()) > 2)
            throw new IllegalArgumentException("unbounded saved seat shape");
        seatShape = new AABB(values.getDouble(0), values.getDouble(1), values.getDouble(2), values.getDouble(3), values.getDouble(4), values.getDouble(5));
        exit = new BlockPos(egress[0], egress[1], egress[2]); emptyTicks = 0;
    }
    private static BlockPos pos(BlockPosition p) { return new BlockPos(p.x(), p.y(), p.z()); }
}
