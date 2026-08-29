package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * The physical body of a bomber effect.  Its canonical authority is still the durable
 * {@link PhysicalIntent}; this tagged vanilla TNT is only its loaded-world continuation.
 */
final class FrontierV3BomberBomb {
    static final String INTENT_KEY = "pale_mirror.frontier_v3.bomber_bomb_intent";
    static final String BOMBER_KEY = "pale_mirror.frontier_v3.bomber_bomb_bomber";
    private static final int FUSE_TICKS = 20;

    enum Inspection { ACTIVE, DEFERRED, MISSING_OR_ALTERED }

    private FrontierV3BomberBomb() { }

    /** Durable intent must already be RUNNING before this ordinary Minecraft entity is admitted. */
    static boolean materialize(ServerLevel level, PhysicalIntent intent, Entity bomber) {
        // This materializer deliberately uses the unmodified vanilla TNT entity, whose blast
        // radius is four blocks.  Do not silently materialize a future differently-sized intent.
        if (intent.radiusBlocks() != 4) return false;
        if (!(bomber instanceof LivingEntity living) || !level.hasChunkAt(bomber.blockPosition()) || !level.hasChunkAt(origin(intent))) return false;
        UUID expected = entityId(intent.id()); Entity existing = level.getEntity(expected);
        if (existing != null) return isCurrent(existing, intent);
        PrimedTnt bomb = new OwnedBomb(level, bomber.getX(), bomber.getY() + 1.25D, bomber.getZ(), living, intent.id());
        bomb.setUUID(expected); bomb.setFuse(FUSE_TICKS);
        bomb.getPersistentData().putString(INTENT_KEY, intent.id().value());
        bomb.getPersistentData().putString(BOMBER_KEY, intent.causeSubjectId().value());
        bomb.setCustomName(Component.literal("Hive organic bomb"));
        Vec3 target = Vec3.atCenterOf(origin(intent)); Vec3 delta = target.subtract(bomb.position());
        double horizontal = Math.max(0.001D, Math.sqrt(delta.x * delta.x + delta.z * delta.z));
        bomb.setDeltaMovement(delta.x / horizontal * 0.24D, 0.18D, delta.z / horizontal * 0.24D);
        return level.addFreshEntity(bomb);
    }

    static Inspection inspect(ServerLevel level, PhysicalIntent intent) {
        Entity entity = level.getEntity(entityId(intent.id()));
        if (entity != null && !entity.isRemoved()) return isCurrent(entity, intent) ? Inspection.ACTIVE : Inspection.MISSING_OR_ALTERED;
        return level.hasChunkAt(origin(intent)) ? Inspection.MISSING_OR_ALTERED : Inspection.DEFERRED;
    }

    /** Resolves only an exact tagged physical bomb; ordinary TNT never enters the v3 intent path. */
    static Optional<PhysicalIntentId> intentFor(Explosion explosion) {
        return explosion == null ? Optional.empty() : intentFor(explosion.getDirectSourceEntity());
    }

    static Optional<PhysicalIntentId> intentFor(Entity entity) {
        if (!(entity instanceof PrimedTnt)) return Optional.empty();
        String value = entity.getPersistentData().getString(INTENT_KEY);
        try {
            PhysicalIntentId intent = new PhysicalIntentId(value);
            return entityId(intent).equals(entity.getUUID()) ? Optional.of(intent) : Optional.empty();
        } catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }

    static boolean isCurrent(Entity entity, PhysicalIntent intent) {
        return entity instanceof PrimedTnt && entityId(intent.id()).equals(entity.getUUID())
                && intent.id().value().equals(entity.getPersistentData().getString(INTENT_KEY))
                && intent.causeSubjectId().value().equals(entity.getPersistentData().getString(BOMBER_KEY));
    }

    static UUID entityId(PhysicalIntentId intent) {
        return UUID.nameUUIDFromBytes(("pale-mirror-frontier-v3-bomber-bomb:" + intent.value()).getBytes(StandardCharsets.UTF_8));
    }

    private static net.minecraft.core.BlockPos origin(PhysicalIntent intent) {
        long scale = io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE;
        if (intent.origin().x().raw() % scale != 0L || intent.origin().y().raw() % scale != 0L || intent.origin().z().raw() % scale != 0L) {
            throw new IllegalArgumentException("bomber bomb origin must be whole-block");
        }
        return new net.minecraft.core.BlockPos(Math.toIntExact(intent.origin().x().raw() / scale),
                Math.toIntExact(intent.origin().y().raw() / scale), Math.toIntExact(intent.origin().z().raw() / scale));
    }

    /** Keeps vanilla TNT ticking, trajectory, blast geometry and damage; adds only scoped observation identity. */
    private static final class OwnedBomb extends PrimedTnt {
        private final PhysicalIntentId intentId;

        private OwnedBomb(ServerLevel level, double x, double y, double z, LivingEntity owner, PhysicalIntentId intentId) {
            super(level, x, y, z, owner); this.intentId = intentId;
        }

        @Override protected void explode() {
            FrontierV3ExplosionExecutionScope.run(intentId, super::explode);
        }
    }
}
