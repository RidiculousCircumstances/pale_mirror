package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Exact pinned Handcrafted integration; no activity or resource authority. */
final class FrontierV3HandcraftedSeating implements FrontierV3SeatProvider {
    public Optional<Contact> contact(ServerLevel level, FrontierWorldState state, SeatUse use, Mob actor,
            ActorActuationId authority, SeatUse.Action action) {
        var seat = carrier(level, state, use, actor, authority, action);
        if (seat.isEmpty() && action != SeatUse.Action.EXIT) return Optional.empty();
        return Optional.of(new Contact(() -> ready(level, state, use, actor, action),
                () -> changePose(level, actor, use, authority, action, seat)));
    }
    private boolean ready(ServerLevel level, FrontierWorldState state, SeatUse use, Mob actor, SeatUse.Action action) {
        if (!actor.isAlive()) return false;
        if (action == SeatUse.Action.EXIT) return actor.isPassenger()
                ? seated(state, use, actor) && ((FrontierV3SeatEntity) actor.getVehicle()).loadedExit(actor).isPresent()
                : FrontierV3SupportedBodyCapture.observe(level, actor).filter(p -> !p.equals(use.place().contact().standingBody())).isPresent();
        if (!furniturePresent(level, use.place())) return false;
        if (!level.getEntitiesOfClass(earth.terrarium.handcrafted.common.entities.Seat.class, new AABB(pos(use.place().chair())),
                other -> !other.hasPassenger(actor) && !other.getPassengers().isEmpty()).isEmpty()) return false;
        return action == SeatUse.Action.ENTER ? FrontierV3SurfaceObservation.at(actor, use.place().approach())
                : actor.distanceToSqr(pos(use.place().chair()).getCenter()) <= 4;
    }
    private Optional<FrontierV3SeatEntity> carrier(ServerLevel level, FrontierWorldState state, SeatUse use, Mob actor, ActorActuationId authority, SeatUse.Action action) {
        if (actor.getVehicle() instanceof FrontierV3SeatEntity seat)
            return seat.boundTo(authority, actor.getUUID(), use.place().id(), use.carrierEpoch()) ? Optional.of(seat) : Optional.empty();
        if (actor.isPassenger() || action == SeatUse.Action.EXIT) return Optional.empty();
        var uuid = FrontierV3SeatEntity.identity(state.bootstrap().worldId(), authority, use.place().id(), use.carrierEpoch());
        var existing = level.getEntity(uuid);
        if (existing instanceof FrontierV3SeatEntity seat)
            return seat.boundTo(authority, actor.getUUID(), use.place().id(), use.carrierEpoch()) && seat.getPassengers().isEmpty() ? Optional.of(seat) : Optional.empty();
        if (existing != null || !furniturePresent(level, use.place())
                || !FrontierV3NativeBodyResidence.admissionReady(level, use.place().contact())) return Optional.empty();
        return Optional.of(FrontierV3SeatEntity.create(level, state.bootstrap().worldId(), authority, actor.getUUID(), use.place(), use.carrierEpoch()));
    }
    public boolean seated(FrontierWorldState state, SeatUse use, Mob actor) {
        if (!(actor.getVehicle() instanceof FrontierV3SeatEntity seat)) return false;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(actor);
        if (declaration.isEmpty()) return false;
        return seat.boundTo(new ActorActuationId(new ActorBodyId(use.execution().actorId(), declaration.orElseThrow().epoch()), use.execution()),
                actor.getUUID(), use.place().id(), use.carrierEpoch())
                && FrontierV3SupportedBodyCapture.observe((ServerLevel) actor.level(), actor).filter(use.place().contact().standingBody()::equals).isPresent();
    }
    public Optional<Boolean> available(ServerLevel level, SeatPlace place, Mob actor) {
        if (!FrontierV3LoadedWorld.ready(level, pos(place.chair())) || !FrontierV3LoadedWorld.ready(level, pos(place.table()))) return Optional.empty();
        return Optional.of(furniturePresent(level, place) && level.getEntitiesOfClass(earth.terrarium.handcrafted.common.entities.Seat.class,
                new AABB(pos(place.chair())), other -> !other.hasPassenger(actor) && !other.getPassengers().isEmpty()).isEmpty());
    }
    public boolean furniturePresent(ServerLevel level, SeatPlace place) {
        var chair = pos(place.chair()); var table = pos(place.table());
        return FrontierV3LoadedWorld.ready(level, chair) && FrontierV3LoadedWorld.ready(level, table)
                && BuiltInRegistries.BLOCK.getKey(level.getBlockState(chair).getBlock()).toString().equals(place.chairBlock())
                && BuiltInRegistries.BLOCK.getKey(level.getBlockState(table).getBlock()).toString().equals(place.tableBlock())
                && level.getBlockState(chair).hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                && level.getBlockState(chair).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                        .getName().equals(place.facing().name().toLowerCase(java.util.Locale.ROOT));
    }
    private FrontierV3NativeEffect.Outcome changePose(ServerLevel level, Mob actor, SeatUse use, ActorActuationId authority, SeatUse.Action action, Optional<FrontierV3SeatEntity> carrier) {
        if (!actor.isAlive()) return FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT;
        if (action == SeatUse.Action.EXIT) {
            if (actor.isPassenger()) {
                if (carrier.isEmpty() || actor.getVehicle() != carrier.orElseThrow() || carrier.orElseThrow().loadedExit(actor).isEmpty())
                    return FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT;
                actor.stopRiding();
            }
            if (actor.isPassenger() || FrontierV3SupportedBodyCapture.observe(level, actor)
                    .filter(p -> !p.equals(use.place().contact().standingBody())).isEmpty()) return FrontierV3NativeEffect.Outcome.UNKNOWN;
        } else {
            if (carrier.isEmpty() || !furniturePresent(level, use.place())) return FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT;
            var seat = carrier.orElseThrow();
            var occupied = level.getEntitiesOfClass(earth.terrarium.handcrafted.common.entities.Seat.class, new AABB(pos(use.place().chair())),
                    other -> other != seat && !other.getPassengers().isEmpty());
            if (!occupied.isEmpty()) return FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT;
            boolean newlyInserted = level.getEntity(seat.getUUID()) != seat;
            if (newlyInserted && !level.addFreshEntity(seat)) return !seat.isAddedToLevel() && level.getEntity(seat.getUUID()) != seat
                    ? FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT : FrontierV3NativeEffect.Outcome.UNKNOWN;
            var before = actor.position();
            if (actor.getVehicle() != seat && !actor.startRiding(seat)) {
                if (newlyInserted && seat.getPassengers().isEmpty()) seat.removeSeat();
                return !actor.isPassenger() && actor.position().equals(before)
                        ? FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT : FrontierV3NativeEffect.Outcome.UNKNOWN;
            }
            actor.setYRot(seat.getYRot()); actor.setYBodyRot(seat.getYRot()); actor.setYHeadRot(seat.getYRot());
            actor.yRotO = seat.getYRot(); actor.yBodyRotO = seat.getYRot(); actor.yHeadRotO = seat.getYRot();
            seat.positionRider(actor);
            if (!seat.contactEvidence(actor, level, pos(use.place().chair()))) return FrontierV3NativeEffect.Outcome.UNKNOWN;
        }
        return FrontierV3NativeEffect.Outcome.APPLIED;
    }
    private static BlockPos pos(BlockPosition value) { return new BlockPos(value.x(), value.y(), value.z()); }
}
