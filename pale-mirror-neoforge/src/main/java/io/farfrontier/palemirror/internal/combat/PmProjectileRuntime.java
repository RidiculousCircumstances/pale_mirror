package io.farfrontier.palemirror.internal.combat;

import java.util.Comparator;

import io.farfrontier.palemirror.internal.effect.ControlledEffectExecutor;
import io.farfrontier.palemirror.internal.effect.EffectLease;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Generic PM projectile executor. Source adapters select only their visual
 * carrier/name; launch, impact, persistence and recovery belong to PM.
 */
public final class PmProjectileRuntime {
    public static final String PROJECTILE_ID_KEY = "pale_mirror_projectile_id";
    private static final long DEFAULT_LIFETIME_TICKS = 100L;

    private PmProjectileRuntime() { }

    public static boolean launchArrow(PaleMirrorSavedData data, ServerLevel level, String sourceId, String facilityId,
                                      String shooterKey, String shooterSlot, LivingEntity shooter, LivingEntity target,
                                      String visualName, float damage, long gameTick, long actionSequence) {
        String projectileId = "pm:" + sourceId + ":projectile:" + facilityId + ":" + shooterKey + ":" + gameTick + ":" + actionSequence;
        String launchKey = projectileId + ":launch";
        EffectLease lease = EffectLease.planned(launchKey, launchKey, sourceId, facilityId, shooterSlot,
                "projectile_launch", gameTick, gameTick + DEFAULT_LIFETIME_TICKS);
        PmProjectileRef ref = data.threatCombat().planProjectile(new PmProjectileRef(projectileId, sourceId, facilityId,
                shooterKey, target.getUUID(), visualName, lease.id(), damage, gameTick, gameTick + DEFAULT_LIFETIME_TICKS, null, "",
                PmProjectileRef.State.PLANNED, 0L, ""));
        data.setDirty();
        if (ref.state() != PmProjectileRef.State.PLANNED) return false;
        return ControlledEffectExecutor.executeOnce(data, lease, gameTick, () -> {
            // The vanilla constructor validates its weapon argument even
            // though PM, rather than vanilla bow AI, owns this projectile.
            Arrow arrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            arrow.setOwner(shooter);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.setCustomName(Component.literal(visualName));
            arrow.getPersistentData().putString(PROJECTILE_ID_KEY, projectileId);
            arrow.setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
            double dx = target.getX() - arrow.getX();
            double dy = target.getEyeY() - arrow.getY();
            double dz = target.getZ() - arrow.getZ();
            arrow.shoot(dx, dy, dz, 1.6F, 0.0F);
            if (!level.addFreshEntity(arrow)) throw new IllegalStateException("Could not add PM projectile visual carrier");
            data.threatCombat().activateProjectile(projectileId, arrow.getUUID());
            data.setDirty();
        });
    }

    /**
     * Starts an adapter-selected visual carrier while retaining PM launch,
     * lifetime and impact authority. The carrier must be a vanilla Projectile
     * implementation, but its class never leaks out of the source adapter.
     */
    public static boolean launchVisualCarrier(PaleMirrorSavedData data, ServerLevel level, String sourceId, String facilityId,
                                              String shooterKey, String shooterSlot, LivingEntity shooter, LivingEntity target,
                                              String visualProfile, EntityType<?> carrierType, String nativeRole,
                                              float damage, long gameTick, long actionSequence) {
        String projectileId = "pm:" + sourceId + ":projectile:" + facilityId + ":" + shooterKey + ":" + gameTick + ":" + actionSequence;
        String launchKey = projectileId + ":launch";
        EffectLease lease = EffectLease.planned(launchKey, launchKey, sourceId, facilityId, shooterSlot,
                "projectile_launch", gameTick, gameTick + DEFAULT_LIFETIME_TICKS);
        PmProjectileRef ref = data.threatCombat().planProjectile(new PmProjectileRef(projectileId, sourceId, facilityId,
                shooterKey, target.getUUID(), visualProfile, lease.id(), damage, gameTick, gameTick + DEFAULT_LIFETIME_TICKS, null, "",
                PmProjectileRef.State.PLANNED, 0L, ""));
        data.setDirty();
        if (ref.state() != PmProjectileRef.State.PLANNED) return false;
        return ControlledEffectExecutor.executeOnce(data, lease, gameTick, () -> {
            Entity candidate = carrierType.create(level);
            if (!(candidate instanceof Projectile projectile)) throw new IllegalStateException("PM projectile visual carrier is not a Projectile");
            projectile.setOwner(shooter);
            projectile.setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
            projectile.getPersistentData().putString(PROJECTILE_ID_KEY, projectileId);
            projectile.getPersistentData().putString("pale_mirror_object_id", facilityId);
            projectile.getPersistentData().putString("pale_mirror_role", nativeRole);
            projectile.getPersistentData().putString("pale_mirror_encounter_slot", shooterSlot);
            double dx = target.getX() - projectile.getX();
            double dy = target.getEyeY() - projectile.getY();
            double dz = target.getZ() - projectile.getZ();
            projectile.shoot(dx, dy, dz, 1.4F, 0.0F);
            if (!level.addFreshEntity(projectile)) throw new IllegalStateException("Could not add PM projectile visual carrier");
            data.threatCombat().activateProjectile(projectileId, projectile.getUUID());
            data.setDirty();
        });
    }

    /** Returns true only when the caller must cancel native projectile impact. */
    public static boolean handleImpact(MinecraftServer server, Projectile projectile, HitResult hit) {
        String projectileId = projectile.getPersistentData().getString(PROJECTILE_ID_KEY);
        if (projectileId.isBlank() || !(projectile.level() instanceof ServerLevel level)) return false;
        PaleMirrorSavedData data = PaleMirrorSavedData.get(server.overworld());
        PmProjectileRef ref = data.threatCombat().projectile(projectileId).orElse(null);
        if (ref == null || ref.entityId() == null || !ref.entityId().equals(projectile.getUUID())) {
            projectile.discard();
            return true;
        }
        long gameTick = server.overworld().getGameTime();
        if (ref.state() != PmProjectileRef.State.ACTIVE) {
            projectile.discard();
            return true;
        }
        String impactKey = projectileId + ":impact";
        EffectLease impact = EffectLease.planned(impactKey, impactKey, ref.sourceId(), ref.facilityId(), ref.shooterKey(),
                "projectile_impact", gameTick, gameTick + 1L);
        ControlledEffectExecutor.executeOnce(data, impact, gameTick, () -> {
            if (!data.threatCombat().impactProjectile(projectileId, impact.id(), gameTick)) {
                projectile.discard();
                return;
            }
            if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target
                    && ref.targetId() != null && ref.targetId().equals(target.getUUID()) && target != projectile.getOwner()) {
                target.hurt(level.damageSources().arrow(projectile instanceof AbstractArrow arrow ? arrow : null,
                        projectile.getOwner()), ref.damage());
            }
            projectile.discard();
            data.setDirty();
        });
        return true;
    }

    /** Crash recovery never resumes uncertain projectile physics or delayed impact. */
    public static void discardUnknownAfterRestart(MinecraftServer server, PaleMirrorSavedData data) {
        data.threatCombat().projectiles().stream()
                .filter(ref -> ref.state() == PmProjectileRef.State.UNKNOWN_AFTER_RESTART)
                .sorted(Comparator.comparing(PmProjectileRef::id))
                .forEach(ref -> server.getAllLevels().forEach(level -> {
                    Entity entity = ref.entityId() == null ? null : level.getEntity(ref.entityId());
                    if (entity != null && ref.id().equals(entity.getPersistentData().getString(PROJECTILE_ID_KEY))) entity.discard();
                }));
    }
}
