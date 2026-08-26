package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Loaded-chunk-only hand-off coordinator for exact source-graybox actor bodies. */
final class SourceGrayboxActorExecutionRuntime {
    private static final String HOLDER = "source-graybox:actor-runtime";
    private static final long DRAIN_HYSTERESIS_TICKS = 200L;
    private static final long CAPTURE_INTERVAL_TICKS = 10L;

    boolean beforePublication(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                              Map<String, Entity> admittedEntities) {
        long gameTick = data.actorExecutionGameTime(level.getGameTime());
        SourceGrayboxHotZone zone = SourceGrayboxHotZone.from(level);
        boolean changed = recoverLoadedActors(level, data, materializer, admittedEntities, gameTick);
        for (ReferenceGrayboxActorExecutionState.ActorState actor : data.actorExecution().actors()) {
            BlockPos position = position(actor);
            switch (actor.mode()) {
                case COLD -> {
                    if (zone.preparing(position) && level.hasChunkAt(position)) {
                        changed |= data.prepareActor(actor.id(), HOLDER, gameTick);
                    }
                }
                case PREPARING -> {
                    if (zone.hot(position)) changed |= data.touchActorDemand(actor.id(), actor.leaseId(), HOLDER, gameTick);
                    if (!zone.preparing(position) && expired(actor, gameTick)
                            && materializer.actorEntity(level, admittedEntities, actor) == null) {
                        changed |= data.cancelActorPreparation(actor.id(), actor.leaseId(), HOLDER, gameTick);
                    }
                }
                case HOT -> {
                    if (zone.hot(position)) changed |= data.touchActorDemand(actor.id(), actor.leaseId(), HOLDER, gameTick);
                    if (!zone.hot(position) && expired(actor, gameTick) && zone.safeToDrain(position)) {
                        changed |= data.beginActorDrain(actor.id(), actor.leaseId(), HOLDER, gameTick);
                    }
                }
                case DRAINING, RETIRED, RECOVERING -> { /* completed after materialization/loaded observation */ }
            }
        }
        return changed;
    }

    boolean afterPublication(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                             Map<String, Entity> admittedEntities) {
        long gameTick = data.actorExecutionGameTime(level.getGameTime());
        SourceGrayboxHotZone zone = SourceGrayboxHotZone.from(level);
        boolean changed = false;
        for (ReferenceGrayboxActorExecutionState.ActorState actor : data.actorExecution().actors()) {
            BlockPos position = position(actor);
            Entity entity = materializer.actorEntity(level, admittedEntities, actor);
            switch (actor.mode()) {
                case PREPARING -> {
                    if (entity != null) changed |= data.activateActor(actor.id(), actor.leaseId(), HOLDER, gameTick);
                }
                case HOT -> {
                    if (entity != null && gameTick % CAPTURE_INTERVAL_TICKS == 0L) {
                        changed |= data.captureActor(actor.id(), actor.leaseId(), HOLDER, sixteenths(entity.getX()),
                                sixteenths(entity.getZ()), gameTick);
                    }
                    if (!zone.hot(position) && expired(actor, gameTick) && zone.safeToDrain(position)) {
                        changed |= data.beginActorDrain(actor.id(), actor.leaseId(), HOLDER, gameTick);
                    }
                }
                case DRAINING -> {
                    if (!level.hasChunkAt(position) || !zone.safeToDrain(position)) continue;
                    if (entity != null) {
                        data.captureActor(actor.id(), actor.leaseId(), HOLDER, sixteenths(entity.getX()), sixteenths(entity.getZ()), gameTick);
                        entity.discard();
                        admittedEntities.remove(SourceGrayboxMaterializer.entityKey(actor.id(), actor.kind()));
                    }
                    changed |= data.settleActorCold(actor.id(), actor.leaseId(), HOLDER, gameTick);
                }
                case RETIRED -> {
                    if (!level.hasChunkAt(position) || !zone.safeToDrain(position)) continue;
                    if (entity != null) {
                        entity.discard();
                        admittedEntities.remove(SourceGrayboxMaterializer.entityKey(actor.id(), actor.kind()));
                    }
                    changed |= data.acknowledgeRetiredActor(actor.id(), actor.leaseId(), HOLDER, gameTick);
                }
                case COLD, RECOVERING -> { /* handled before publication */ }
            }
        }
        return changed;
    }

    private static boolean recoverLoadedActors(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                               Map<String, Entity> admittedEntities, long gameTick) {
        boolean changed = false;
        for (ReferenceGrayboxActorExecutionState.ActorState actor : data.actorExecution().actors()) {
            if (actor.mode() != ReferenceGrayboxActorExecutionState.Mode.RECOVERING) continue;
            BlockPos position = position(actor);
            if (!level.hasChunkAt(position)) continue;
            Entity entity = materializer.actorEntity(level, admittedEntities, actor);
            changed |= entity == null
                    ? data.recoverActorCold(actor.id(), actor.leaseId(), HOLDER, gameTick)
                    : data.recoverActorHot(actor.id(), actor.leaseId(), HOLDER, gameTick);
        }
        return changed;
    }

    private static boolean expired(ReferenceGrayboxActorExecutionState.ActorState actor, long gameTick) {
        return gameTick - actor.demandedAtGameTick() >= DRAIN_HYSTERESIS_TICKS;
    }

    private static BlockPos position(ReferenceGrayboxActorExecutionState.ActorState actor) {
        return new BlockPos(Math.floorDiv(actor.actualXSixteenths(), ReferenceGrayboxActorExecutionState.POSITION_SCALE),
                io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout.GROUND_Y + 1,
                Math.floorDiv(actor.actualZSixteenths(), ReferenceGrayboxActorExecutionState.POSITION_SCALE));
    }

    private static int sixteenths(double coordinate) {
        if (!Double.isFinite(coordinate)) throw new IllegalStateException("Minecraft actor position is not finite");
        return Math.toIntExact(Math.round(coordinate * ReferenceGrayboxActorExecutionState.POSITION_SCALE));
    }
}
