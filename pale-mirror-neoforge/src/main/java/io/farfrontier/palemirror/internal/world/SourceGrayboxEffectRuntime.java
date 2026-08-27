package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxStructureObservation;
import io.farfrontier.palemirror.internal.effect.ControlledEffectExecutor;
import io.farfrontier.palemirror.internal.effect.EffectLease;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Delivers source-committed one-day effects into loaded physical space.
 *
 * <p>The source snapshot chooses the existence, kind, cause and location of
 * an effect.  This executor owns only the one-way physical hand-off.  At the
 * source-day boundary a target that is not HOT gets a durable cold receipt;
 * it is never replayed when a player happens to return later.  A HOT breach
 * uses normal TNT interaction with no ownership or parcel filter, then
 * reconciles every known claim it actually changed and retains every other
 * changed block as a bounded physical scar.</p>
 */
final class SourceGrayboxEffectRuntime {
    private static final String SOURCE_ID = "reference-graybox";
    private static final int EFFECT_Y = io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout.GROUND_Y + 3;

    boolean settleSourceDay(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer) {
        SourceGrayboxHotZone zone = SourceGrayboxHotZone.from(level);
        return settleSourceDay(level, data, materializer, data.snapshot(), effect -> {
            BlockPos target = target(effect);
            return zone.hot(target) && level.hasChunkAt(target);
        });
    }

    /** Package-visible predicate seam for the physical GameTest; production always uses {@link SourceGrayboxHotZone}. */
    boolean settleSourceDay(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                            ReferenceGrayboxSnapshot snapshot, Predicate<ReferenceGrayboxSnapshot.Effect> hot) {
        if (snapshot.effects().isEmpty()) return false;
        if (snapshot.effects().stream().anyMatch(effect -> effect.day() != snapshot.day())) {
            throw new IllegalStateException("source graybox effect is not scoped to its snapshot day");
        }
        boolean changed = false;
        for (ReferenceGrayboxSnapshot.Effect effect : snapshot.effects().stream().sorted(Comparator.comparing(ReferenceGrayboxSnapshot.Effect::id)).toList()) {
            changed |= settle(level, data, materializer, effect, hot.test(effect));
        }
        return changed;
    }

    private static boolean settle(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                  ReferenceGrayboxSnapshot.Effect effect, boolean hot) {
        long gameTick = data.actorExecutionGameTime(level.getGameTime());
        EffectLease lease = EffectLease.planned("source-graybox:effect:" + effect.id(), "source-graybox:effect:" + effect.id(),
                SOURCE_ID, effect.subjectId(), effect.id(), effect.kind(), gameTick, gameTick + 1L);
        if (!hot) {
            return ControlledEffectExecutor.executeOnceWithReceipt(data, lease, gameTick,
                    () -> "disposition=cold; day=" + effect.day() + "; target=" + effect.subjectId());
        }
        return switch (effect.kind()) {
            case "breach_bomb" -> ControlledEffectExecutor.executeOnceWithReceipt(data, lease, gameTick,
                    () -> breach(level, data, materializer, effect));
            case "swarm_assault" -> ControlledEffectExecutor.executeOnceWithReceipt(data, lease, gameTick,
                    () -> assault(level, effect));
            case "containment" -> ControlledEffectExecutor.executeOnceWithReceipt(data, lease, gameTick,
                    () -> containment(level, effect));
            default -> throw new IllegalStateException("unknown source graybox effect kind: " + effect.kind());
        };
    }

    /** Normal Minecraft explosion geometry; deliberately no claim, owner or operation-boundary filter exists here. */
    private static String breach(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                 ReferenceGrayboxSnapshot.Effect effect) {
        BlockPos impact = target(effect);
        List<BlockImpact> beforeBlocks = blocksAround(level, materializer, impact, effect.radius());
        List<EntityImpact> beforeEntities = entitiesAround(level, impact, effect.radius());
        SourceGrayboxExplosionObservation.runSourceEffect(() -> level.explode(null, impact.getX() + .5d, impact.getY() + .5d,
                impact.getZ() + .5d, (float) effect.radius(), false, Level.ExplosionInteraction.TNT));
        int facts = 0;
        int conflicts = 0;
        int scars = 0;
        for (BlockImpact candidate : beforeBlocks) {
            BlockState after = level.getBlockState(candidate.position());
            if (candidate.before().equals(after)) continue;
            if (candidate.claim() == null) {
                if (data.physicalScars().record(effect.id(), candidate.position(), candidate.before(), after)) {
                    data.markPhysicalScarsDirty();
                    scars++;
                }
                continue;
            }
            if (candidate.claim().interactionKind().isEmpty()) {
                materializer.recordBlockConflict(level, candidate.position());
                conflicts++;
                continue;
            }
            ReferenceGrayboxObservationOutcome outcome = data.observe(new ReferenceGrayboxStructureObservation(
                    ReferenceGrayboxStructureObservation.VERSION, "source-graybox:physical-effect:" + effect.id() + ":"
                    + candidate.position().getX() + ":" + candidate.position().getY() + ":" + candidate.position().getZ(),
                    data.snapshot().stateRevision(), ReferenceGrayboxStructureObservation.Kind.valueOf(
                    candidate.claim().interactionKind().toUpperCase(java.util.Locale.ROOT)), candidate.claim().subjectId(),
                    candidate.claim().interactionWeight()));
            if (outcome.applied()) {
                materializer.consumeBlockClaim(level, candidate.position());
                facts++;
            } else {
                materializer.recordBlockConflict(level, candidate.position());
                conflicts++;
            }
        }
        int affectedEntities = 0;
        int managedDeaths = 0;
        for (EntityImpact candidate : beforeEntities) {
            if (!(candidate.entity() instanceof LivingEntity entity)) continue;
            if (entity.isAlive() && entity.getHealth() == candidate.health()) continue;
            affectedEntities++;
            if (!entity.isAlive() && SourceGrayboxEntityObservation.observe(data, entity,
                    "effect:" + effect.id() + ":" + entity.getUUID())) managedDeaths++;
        }
        return "disposition=hot; kind=breach_bomb; radius=" + fixed(effect.radius()) + "; facts=" + facts + "; conflicts="
                + conflicts + "; scars=" + scars + "; affectedEntities=" + affectedEntities + "; managedDeaths=" + managedDeaths;
    }

    private static String assault(ServerLevel level, ReferenceGrayboxSnapshot.Effect effect) {
        BlockPos impact = target(effect);
        level.sendParticles(ParticleTypes.SMOKE, impact.getX() + .5d, impact.getY() + .5d, impact.getZ() + .5d,
                36, 2.0d, 1.0d, 2.0d, .02d);
        level.playSound(null, impact, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, .8f);
        return "disposition=hot; kind=swarm_assault; power=" + fixed(effect.magnitude()) + "; physical=smoke_sound";
    }

    private static String containment(ServerLevel level, ReferenceGrayboxSnapshot.Effect effect) {
        BlockPos impact = target(effect);
        double radius = Math.max(1.0d, effect.radius());
        level.sendParticles(ParticleTypes.END_ROD, impact.getX() + .5d, impact.getY() + .5d, impact.getZ() + .5d,
                48, radius, 1.0d, radius, .025d);
        level.playSound(null, impact, SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.BLOCKS, .8f, 1.4f);
        return "disposition=hot; kind=containment; tissueRemoved=" + fixed(effect.magnitude()) + "; physical=containment_flare";
    }

    private static List<BlockImpact> blocksAround(ServerLevel level, SourceGrayboxMaterializer materializer, BlockPos impact, double radius) {
        int range = (int) Math.ceil(radius);
        List<BlockImpact> result = new ArrayList<>();
        double radiusSquared = (radius + .75d) * (radius + .75d);
        for (int x = impact.getX() - range; x <= impact.getX() + range; x++) {
            for (int y = impact.getY() - range; y <= impact.getY() + range; y++) {
                for (int z = impact.getZ() - range; z <= impact.getZ() + range; z++) {
                    double dx = x + .5d - (impact.getX() + .5d);
                    double dy = y + .5d - (impact.getY() + .5d);
                    double dz = z + .5d - (impact.getZ() + .5d);
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
                    BlockPos position = new BlockPos(x, y, z);
                    BlockState before = level.getBlockState(position);
                    if (!before.isAir()) result.add(new BlockImpact(position, before, materializer.claimAt(level, position)));
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<EntityImpact> entitiesAround(ServerLevel level, BlockPos impact, double radius) {
        AABB area = new AABB(impact).inflate(radius + 1.0d);
        return level.getEntitiesOfClass(LivingEntity.class, area, Entity::isAlive).stream()
                .map(entity -> new EntityImpact(entity, entity.getHealth())).toList();
    }

    private static BlockPos target(ReferenceGrayboxSnapshot.Effect effect) {
        return new BlockPos(effect.position().x(), EFFECT_Y, effect.position().z());
    }

    private static String fixed(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private record BlockImpact(BlockPos position, BlockState before, SourceGrayboxPresentationLedger.Claim claim) { }
    private record EntityImpact(Entity entity, float health) { }
}
