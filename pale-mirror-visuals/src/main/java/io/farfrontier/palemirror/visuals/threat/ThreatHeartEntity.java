package io.farfrontier.palemirror.visuals.threat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class ThreatHeartEntity extends Mob implements GeoEntity {
    public static final String FACILITY_KEY = "pale_mirror_visuals_facility";
    public static final String JOB_KEY = "pale_mirror_visuals_job";
    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(
            ThreatHeartEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation PULSE = RawAnimation.begin().thenLoop("animation.threat_heart.pulse");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ThreatHeartEntity(EntityType<? extends ThreatHeartEntity> type, Level level) {
        super(type, level); noPhysics = true; setPersistenceRequired();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STAGE, 1);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        facilityId(tag.getString("facility")); jobId(tag.getString("job")); setStage(tag.getInt("stage"));
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("facility", facilityId()); tag.putString("job", jobId()); tag.putInt("stage", stage());
    }
    public String facilityId() { return getPersistentData().getString(FACILITY_KEY); }
    public void facilityId(String value) { getPersistentData().putString(FACILITY_KEY, value); }
    public String jobId() { return getPersistentData().getString(JOB_KEY); }
    public void jobId(String value) { getPersistentData().putString(JOB_KEY, value); }
    public int stage() { return entityData.get(STAGE); }
    public void setStage(int value) { entityData.set(STAGE, Math.max(1, Math.min(4, value))); }

    @Override public void tick() {
        super.tick(); setDeltaMovement(0, 0, 0);
        if (!(level() instanceof ServerLevel server) || tickCount % Math.max(8, 24 - stage() * 4) != 0) return;
        server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.7, getZ(), stage() * 2,
                0.55, 0.65, 0.55, 0.015);
        if (tickCount % 80 == 0) server.playSound(null, blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.HOSTILE, 0.4F + stage() * 0.1F, 0.7F + stage() * 0.05F);
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "pulse", 0, state -> state.setAndContinue(PULSE))
                .setAnimationSpeed(0.75 + stage() * 0.2));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 2048.0D)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }
}
