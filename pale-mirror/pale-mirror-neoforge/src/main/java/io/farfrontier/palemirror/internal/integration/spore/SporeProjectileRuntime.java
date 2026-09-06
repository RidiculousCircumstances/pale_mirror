package io.farfrontier.palemirror.internal.integration.spore;

import java.util.Comparator;

import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.combat.PmProjectileRef;
import io.farfrontier.palemirror.internal.combat.PmProjectileRuntime;
import io.farfrontier.palemirror.internal.combat.ThreatActorControlState;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** PM physics for the pinned Spore AcidBall visual carrier. */
final class SporeProjectileRuntime {
    private static final int WORK_BUDGET_PER_TICK = 16;

    void tick(MinecraftServer server, PaleMirrorSavedData data) {
        int remaining = WORK_BUDGET_PER_TICK;
        for (PmProjectileRef ref : data.threatCombat().projectiles().stream()
                .filter(value -> "spore".equals(value.sourceId()) && value.state() == PmProjectileRef.State.ACTIVE)
                .sorted(Comparator.comparing(PmProjectileRef::id)).toList()) {
            if (remaining-- == 0) return;
            TestMineRecord site;
            try {
                site = data.testMines().get(new WorldObjectId(ref.facilityId()));
            } catch (IllegalArgumentException ignored) {
                data.threatCombat().discardProjectile(ref.id(), server.overworld().getGameTime(), "invalid facility id");
                data.setDirty();
                continue;
            }
            if (site == null || ref.entityId() == null) continue;
            ServerLevel level = levelFor(server, site);
            if (level == null) continue;
            Entity entity = level.getEntity(ref.entityId());
            if (!(entity instanceof Projectile projectile) || !ref.id().equals(entity.getPersistentData()
                    .getString(PmProjectileRuntime.PROJECTILE_ID_KEY))) {
                data.threatCombat().discardProjectile(ref.id(), server.overworld().getGameTime(), "missing visual carrier");
                data.setDirty();
                continue;
            }
            moveAndResolve(server, data, ref, projectile);
        }
    }

    private static void moveAndResolve(MinecraftServer server, PaleMirrorSavedData data, PmProjectileRef ref, Projectile projectile) {
        ServerLevel level = (ServerLevel) projectile.level();
        Vec3 start = projectile.position();
        Vec3 motion = projectile.getDeltaMovement();
        Vec3 end = start.add(motion);
        BlockHitResult block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
        if (block.getType() != HitResult.Type.MISS) {
            PmProjectileRuntime.handleImpact(server, projectile, block);
            return;
        }
        ThreatActorControlState shooter = data.threatCombat().actor(ref.shooterKey()).orElse(null);
        Entity hit = level.getEntities(projectile, projectile.getBoundingBox().expandTowards(motion).inflate(0.4D), candidate ->
                        candidate instanceof LivingEntity && ref.targetId() != null && ref.targetId().equals(candidate.getUUID())
                                && (shooter == null || !candidate.getUUID().equals(shooter.entityId())))
                .stream().min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(start))).orElse(null);
        if (hit != null) {
            PmProjectileRuntime.handleImpact(server, projectile, new EntityHitResult(hit));
            return;
        }
        projectile.move(MoverType.SELF, motion);
        projectile.setDeltaMovement(motion.scale(0.99D));
    }

    private static ServerLevel levelFor(MinecraftServer server, TestMineRecord site) {
        for (ServerLevel level : server.getAllLevels()) if (level.dimension().location().toString().equals(site.dimensionId())) return level;
        return null;
    }
}
