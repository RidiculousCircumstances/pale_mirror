package io.farfrontier.palemirror.internal.integration.crimson;

import java.util.Comparator;

import io.farfrontier.palemirror.internal.world.EncounterActorRef;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.SourceGatePartRef;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.sounds.SoundSource;

/**
 * PM-owned audiovisual presentation for the exact Crimson 1.4.3.1 local forms.
 * It is deliberately ephemeral: it cannot mutate PM domain state, invoke a
 * Crimson function, scan unregistered entities, or produce terrain effects.
 */
final class CrimsonPresentationRuntime {
    static final String VISUAL_PHASE_KEY = "pale_mirror_crimson_visual_phase";
    private static final String DASH_POSE_UNTIL_KEY = "pale_mirror_crimson_dash_pose_until";
    private static final String DASH_POSE_TAG = "PM_Crimson_Dash_Pose";
    private static final String PUMMELER_VISUAL_TAG = "PM_Pummeler_Visual";
    private static final int WORK_BUDGET_PER_TICK = 32;
    private static final long AMBIENT_PERIOD_TICKS = 400L;
    private static final int[] RAPTOR_MAIN_HAND = {
            5450174, 5450173, 5450172, 5450171, 5450170, 5450176, 5450177,
            5450178, 5450179, 5450180, 5450180, 5450179, 5450178, 5450177,
            5450176, 5450170, 5450171, 5450171, 5450172, 5450173, 5450174
    };
    private static final int[] RAPTOR_OFF_HAND = {
            5450181, 5450182, 5450183, 5450184, 5450185, 5450186, 5450185,
            5450184, 5450183, 5450182, 5450182, 5450181, 5450187, 5450188,
            5450189, 5450190, 5450191, 5450191, 5450190, 5450189, 5450188
    };

    void tick(MinecraftServer server, PaleMirrorSavedData data) {
        int remaining = WORK_BUDGET_PER_TICK;
        long gameTick = server.overworld().getGameTime();
        for (TestMineRecord mine : data.testMines().values().stream().sorted(Comparator.comparing(TestMineRecord::id)).toList()) {
            if (remaining == 0) return;
            ServerLevel level = levelFor(server, mine);
            if (level == null) continue;
            for (EncounterActorRef reference : mine.encounter().actors()) {
                if (remaining == 0) return;
                if (reference.status() != EncounterActorRef.Status.ACTIVE || reference.entityId() == null) continue;
                Entity entity = level.getEntity(reference.entityId());
                CrimsonActorProfile profile = CrimsonActorProfile.byId(reference.actorProfileId()).orElse(null);
                if (!(entity instanceof Mob actor) || profile == null
                        || !CrimsonSandboxAdapter.isOwnedActor(actor, mine, reference.slotId()) || !profile.matches(actor)) continue;
                tickProfile(level, actor, profile.id(), gameTick);
                remaining--;
            }
            for (SourceGatePartRef part : mine.gate().parts()) {
                if (remaining == 0) return;
                if (part.status() != SourceGatePartRef.Status.ACTIVE || part.entityId() == null) continue;
                Entity entity = level.getEntity(part.entityId());
                CrimsonSiegeProfile profile = CrimsonSiegeProfile.byId(part.profileId()).orElse(null);
                if (!(entity instanceof Mob actor) || profile == null
                        || !CrimsonSandboxAdapter.isOwnedGatePart(actor, mine, part.slotId()) || !profile.matches(actor)) continue;
                tickProfile(level, actor, profile.id(), gameTick);
                remaining--;
            }
        }
    }

    void spawned(ServerLevel level, Mob actor, String profileId) {
        if (isCrimsonified(profileId)) {
            sound(level, actor, "entity.zombie.infect", 3.0F, 1.0F);
            particles(level, ParticleTypes.EXPLOSION, actor, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            particles(level, ParticleTypes.CRIMSON_SPORE, actor, 80, 0.0D, 0.4D, 0.0D, 0.0D);
        } else if (isDecayed(profileId)) {
            sound(level, actor, "entity.zombie.infect", 3.0F, 1.0F);
            sound(level, actor, "entity.zombie_villager.converted", 3.0F, 1.0F);
            particles(level, ParticleTypes.EXPLOSION, actor, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            particles(level, ParticleTypes.ASH, actor, 100, 0.6D, 0.6D, 0.6D, 0.0D);
        } else if (isBloodlink(profileId)) {
            sound(level, actor, "entity.wither.spawn", 7.0F, 0.0F);
            particles(level, ParticleTypes.CRIMSON_SPORE, actor, 120, 0.45D, 0.8D, 0.45D, 0.01D);
        }
    }

    void hurt(LivingEntity entity, String profileId) {
        if (!(entity.level() instanceof ServerLevel level) || entity.hurtTime < 9) return;
        if (isCrimsonified(profileId)) {
            sound(level, entity, baseSound(profileId, "hurt"), 1.0F, 0.7F);
            return;
        }
        if (isDecayed(profileId)) {
            sound(level, entity, baseSound(profileId, "hurt"), 1.0F, 1.0F);
            sound(level, entity, baseSound(profileId, "hurt"), 1.0F, 0.4F);
            return;
        }
        switch (profileId) {
            case "pale_mirror:rusher" -> {
                sound(level, entity, "entity.stray.hurt", 0.7F, 0.4F);
                sound(level, entity, "entity.stray.hurt", 0.7F, 0.7F);
                sound(level, entity, "entity.warden.listening_angry", 1.6F, 1.6F);
                sound(level, entity, "entity.warden.listening_angry", 1.6F, 2.0F);
            }
            case "pale_mirror:raptor" -> sound(level, entity, "entity.bogged.hurt", 1.5F, 0.7F);
            case "pale_mirror:juggernaut" -> {
                sound(level, entity, "entity.iron_golem.hurt", 1.4F, 1.2F);
                sound(level, entity, "entity.iron_golem.hurt", 1.4F, 1.6F);
                sound(level, entity, "entity.iron_golem.hurt", 1.4F, 2.0F);
            }
            case "pale_mirror:knight" -> sound(level, entity, "entity.iron_golem.hurt", 1.4F, 1.6F);
            case "pale_mirror:mangler" -> {
                sound(level, entity, "entity.warden.hurt", 1.5F, 0.5F);
                sound(level, entity, "entity.wither_skeleton.hurt", 1.5F, 1.0F);
                sound(level, entity, "entity.wither_skeleton.hurt", 1.5F, 1.5F);
            }
            case "pale_mirror:pummeler" -> sound(level, entity, "entity.ender_dragon.hurt", 10.0F, 0.8F);
            case "pale_mirror:kraken" -> {
                sound(level, entity, "entity.zoglin.hurt", 3.0F, 0.4F);
                sound(level, entity, "entity.zoglin.hurt", 3.0F, 0.7F);
                sound(level, entity, "entity.zoglin.hurt", 3.0F, 1.0F);
            }
            case "pale_mirror:osiris" -> sound(level, entity, "entity.wither.hurt", 5.0F, 0.0F);
            default -> { }
        }
    }

    void died(LivingEntity entity, String profileId) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (isCrimsonified(profileId)) {
            sound(level, entity, baseSound(profileId, "death"), 1.5F, 0.7F);
            particles(level, ParticleTypes.CRIMSON_SPORE, entity, 36, 0.3D, 0.5D, 0.3D, 0.01D);
            return;
        }
        if (isDecayed(profileId)) {
            sound(level, entity, baseSound(profileId, "death"), 1.5F, 1.0F);
            sound(level, entity, baseSound(profileId, "death"), 1.5F, 0.4F);
            particles(level, ParticleTypes.ASH, entity, 45, 0.4D, 0.5D, 0.4D, 0.01D);
            return;
        }
        switch (profileId) {
            case "pale_mirror:rusher" -> {
                sound(level, entity, "entity.stray.death", 1.0F, 0.4F);
                sound(level, entity, "entity.stray.death", 1.0F, 0.7F);
                sound(level, entity, "entity.warden.hurt", 1.6F, 0.6F);
            }
            case "pale_mirror:raptor" -> {
                sound(level, entity, "entity.bogged.death", 0.8F, 0.7F);
                sound(level, entity, "entity.wither_skeleton.death", 0.8F, 1.0F);
            }
            case "pale_mirror:juggernaut" -> {
                sound(level, entity, "entity.iron_golem.damage", 1.0F, 1.0F);
                sound(level, entity, "entity.iron_golem.damage", 1.0F, 0.5F);
                sound(level, entity, "entity.iron_golem.death", 1.0F, 1.2F);
                sound(level, entity, "entity.iron_golem.death", 1.0F, 1.6F);
                sound(level, entity, "entity.iron_golem.death", 1.0F, 2.0F);
            }
            case "pale_mirror:knight" -> {
                sound(level, entity, "entity.iron_golem.death", 0.8F, 1.6F);
                sound(level, entity, "entity.iron_golem.death", 0.8F, 2.0F);
            }
            case "pale_mirror:mangler" -> {
                sound(level, entity, "entity.iron_golem.damage", 1.0F, 0.0F);
                sound(level, entity, "entity.iron_golem.death", 1.0F, 0.5F);
                sound(level, entity, "entity.warden.angry", 1.5F, 0.0F);
                sound(level, entity, "entity.wither_skeleton.death", 1.0F, 1.0F);
                sound(level, entity, "entity.wither_skeleton.death", 1.0F, 1.5F);
            }
            case "pale_mirror:pummeler" -> {
                sound(level, entity, "entity.ender_dragon.hurt", 10.0F, 0.5F);
                sound(level, entity, "entity.ghast.death", 10.0F, 1.0F);
                sound(level, entity, "entity.ghast.death", 10.0F, 0.7F);
                sound(level, entity, "entity.ghast.death", 10.0F, 0.4F);
            }
            case "pale_mirror:kraken" -> {
                sound(level, entity, "entity.hoglin.converted_to_zombified", 3.0F, 0.4F);
                sound(level, entity, "entity.hoglin.converted_to_zombified", 3.0F, 0.7F);
                sound(level, entity, "entity.hoglin.converted_to_zombified", 3.0F, 1.0F);
                sound(level, entity, "entity.hoglin.death", 1.0F, 0.4F);
            }
            case "pale_mirror:osiris" -> {
                sound(level, entity, "item.trident.thunder", 5.0F, 0.0F);
                sound(level, entity, "entity.wither.death", 5.0F, 0.6F);
                sound(level, entity, "entity.wither.death", 5.0F, 0.3F);
                sound(level, entity, "entity.wither.death", 5.0F, 0.0F);
            }
            default -> { }
        }
    }

    void attacked(Mob actor, String profileId) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        switch (profileId) {
            case "pale_mirror:crimsonified_skeleton" -> {
                sound(level, actor, "entity.skeleton.shoot", 1.8F, 0.7F);
                particles(level, ParticleTypes.CRIMSON_SPORE, actor, 18, 0.15D, 0.25D, 0.15D, 0.0D);
            }
            case "pale_mirror:decayed_skeleton" -> {
                sound(level, actor, "entity.skeleton.shoot", 1.8F, 0.4F);
                sound(level, actor, "entity.skeleton.shoot", 1.8F, 0.7F);
                sound(level, actor, "entity.skeleton.shoot", 1.8F, 1.0F);
                particles(level, ParticleTypes.SMOKE, actor, 15, 0.15D, 0.25D, 0.15D, 0.02D);
            }
            default -> { }
        }
    }

    void rusherDash(Mob actor) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        dashPose(level, actor);
        sound(level, actor, "entity.ender_dragon.flap", 2.0F, 1.0F);
        particles(level, ParticleTypes.ENCHANTED_HIT, actor, 50, 1.2D, 1.2D, 1.2D, 0.0D);
    }

    void raptorAura(Mob actor) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        particles(level, ParticleTypes.CRIMSON_SPORE, actor, 14, 0.35D, 0.5D, 0.35D, 0.0D);
    }

    void manglerDash(Mob actor) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        dashPose(level, actor);
        sound(level, actor, "item.trident.riptide_3", 10.0F, 0.4F);
        sound(level, actor, "item.trident.riptide_3", 10.0F, 0.7F);
        sound(level, actor, "item.trident.riptide_3", 10.0F, 1.0F);
        particles(level, ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER, actor, 20, 2.0D, 2.0D, 2.0D, 0.02D);
    }

    void pummelerPulse(Mob actor, LivingEntity target) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        sound(level, actor, "entity.wither.break_block", 10.0F, 1.0F);
        particles(level, ParticleTypes.EXPLOSION, target, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        particles(level, ParticleTypes.SMOKE, target, 18, 0.35D, 0.5D, 0.35D, 0.04D);
    }

    void krakenGrasp(Mob actor, LivingEntity target) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        particles(level, ParticleTypes.CRIMSON_SPORE, target, 45, 0.35D, 0.7D, 0.35D, 0.01D);
        sound(level, actor, "entity.ravager.roar", 3.5F, 0.4F);
    }

    void bloodlinkAura(Mob actor) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        particles(level, ParticleTypes.RAID_OMEN, actor, 3, 0.25D, 0.8D, 0.25D, 0.0D);
    }

    private void tickProfile(ServerLevel level, Mob actor, String profileId, long gameTick) {
        if (shouldAmbient(level, actor, profileId, gameTick)) ambient(level, actor, profileId);
        tickDashPose(actor, gameTick);
        if ("pale_mirror:raptor".equals(profileId)) animateRaptor(actor, gameTick);
        if (isBloodlink(profileId)) animateBloodlink(actor, profileId, gameTick);
        if ("pale_mirror:osiris".equals(profileId)) tickOsirisPhase(level, actor);
        if ("pale_mirror:pummeler".equals(profileId)) orientPummelerDisplay(actor);
    }

    private static boolean shouldAmbient(ServerLevel level, Mob actor, String profileId, long gameTick) {
        if (isBloodlink(profileId) || Math.floorMod(gameTick + actor.getUUID().getLeastSignificantBits(), AMBIENT_PERIOD_TICKS) != 0L) {
            return false;
        }
        return level.players().stream().anyMatch(player -> !player.isSpectator()
                && player.distanceToSqr(actor) <= ambientRangeSquared(profileId));
    }

    private static double ambientRangeSquared(String profileId) {
        return switch (profileId) {
            case "pale_mirror:pummeler", "pale_mirror:osiris" -> 64.0D * 64.0D;
            case "pale_mirror:kraken" -> 32.0D * 32.0D;
            default -> 16.0D * 16.0D;
        };
    }

    private static void ambient(ServerLevel level, Mob actor, String profileId) {
        if (isCrimsonified(profileId)) {
            sound(level, actor, baseSound(profileId, "ambient"), 1.5F, 0.7F);
            return;
        }
        if (isDecayed(profileId)) {
            sound(level, actor, "block.sculk_shrieker.shriek", 1.5F, 0.3F);
            return;
        }
        switch (profileId) {
            case "pale_mirror:rusher" -> {
                sound(level, actor, "entity.stray.ambient", 1.5F, 1.2F);
                sound(level, actor, "entity.warden.listening_angry", 1.5F, 1.4F);
            }
            case "pale_mirror:raptor" -> sound(level, actor, "entity.husk.ambient", 1.5F, 0.5F);
            case "pale_mirror:juggernaut" -> {
                sound(level, actor, "entity.iron_golem.death", 1.5F, 0.0F);
                sound(level, actor, "entity.puffer_fish.death", 1.5F, 0.0F);
            }
            case "pale_mirror:knight" -> sound(level, actor, "entity.iron_golem.death", 1.5F, 0.0F);
            case "pale_mirror:mangler" -> {
                sound(level, actor, "entity.warden.roar", 1.6F, 0.6F);
                sound(level, actor, "entity.warden.roar", 1.6F, 2.0F);
            }
            case "pale_mirror:pummeler" -> {
                sound(level, actor, "entity.zoglin.death", 5.0F, 1.0F);
                sound(level, actor, "entity.zoglin.death", 5.0F, 0.8F);
                sound(level, actor, "entity.zoglin.death", 5.0F, 0.6F);
                sound(level, actor, "entity.ender_dragon.ambient", 5.0F, 0.0F);
            }
            case "pale_mirror:kraken" -> {
                sound(level, actor, "entity.ravager.roar", 3.5F, 0.4F);
                sound(level, actor, "entity.ravager.roar", 3.5F, 0.1F);
            }
            case "pale_mirror:osiris" -> sound(level, actor, "entity.wither.ambient", 20.0F, 0.0F);
            default -> { }
        }
    }

    private static void animateRaptor(Mob actor, long gameTick) {
        boolean moving = actor.getDeltaMovement().horizontalDistanceSqr() > 0.0025D;
        int frame = moving ? Math.floorMod((int) (gameTick + actor.getUUID().getLeastSignificantBits()), RAPTOR_MAIN_HAND.length) : -1;
        int mainModel = frame < 0 ? 5450170 : RAPTOR_MAIN_HAND[frame];
        int offModel = frame < 0 ? 5450181 : RAPTOR_OFF_HAND[frame];
        actor.setItemSlot(EquipmentSlot.MAINHAND, modelBook(mainModel));
        actor.setItemSlot(EquipmentSlot.OFFHAND, modelBook(offModel));
        if (frame == 9 || frame == 16) sound((ServerLevel) actor.level(), actor, "block.netherite_block.step", 0.4F, 1.0F);
    }

    private static void animateBloodlink(Mob actor, String profileId, long gameTick) {
        int offset = switch (profileId) {
            case "pale_mirror:bloodlink_i" -> 0;
            case "pale_mirror:bloodlink_ii" -> 3;
            case "pale_mirror:bloodlink_iii" -> 6;
            default -> throw new IllegalArgumentException("Not a Bloodlink profile: " + profileId);
        };
        int[] frames = {5450100 + offset, 5450101 + offset, 5450102 + offset, 5450019 + offset, 5450013 + offset};
        int frame = Math.floorMod((int) (gameTick + actor.getUUID().getLeastSignificantBits()), frames.length);
        actor.setItemSlot(EquipmentSlot.HEAD, modelBook(frames[frame]));
    }

    private static void tickOsirisPhase(ServerLevel level, Mob actor) {
        float ratio = actor.getHealth() / actor.getMaxHealth();
        int desiredPhase = ratio <= 0.25F ? 2 : ratio <= 0.50F ? 1 : 0;
        int observedPhase = actor.getPersistentData().getInt(VISUAL_PHASE_KEY);
        if (desiredPhase <= observedPhase) return;
        actor.getPersistentData().putInt(VISUAL_PHASE_KEY, desiredPhase);
        if (desiredPhase == 1) {
            sound(level, actor, "entity.allay.death", 7.0F, 2.0F);
            sound(level, actor, "entity.wither.spawn", 7.0F, 1.5F);
            particles(level, ParticleTypes.CRIMSON_SPORE, actor, 160, 0.0D, 3.5D, 0.0D, 0.0D);
            particles(level, ParticleTypes.SNEEZE, actor, 60, 0.0D, 3.5D, 0.0D, 0.5D);
        } else {
            sound(level, actor, "entity.wither.death", 7.0F, 2.0F);
            sound(level, actor, "entity.wither.death", 7.0F, 1.0F);
            sound(level, actor, "entity.wither.death", 7.0F, 0.0F);
            sound(level, actor, "entity.wither.spawn", 7.0F, 1.5F);
            particles(level, ParticleTypes.FLAME, actor, 160, 0.0D, 3.5D, 0.0D, 0.3D);
        }
    }

    void discardVisualChildren(Entity entity) {
        entity.getPassengers().stream().filter(passenger -> passenger.getTags().contains("PM_Crimson_Visual")
                || passenger.getTags().contains(DASH_POSE_TAG)).forEach(Entity::discard);
    }

    private static void dashPose(ServerLevel level, Mob actor) {
        actor.getPersistentData().putLong(DASH_POSE_UNTIL_KEY, level.getGameTime() + 12L);
        boolean present = actor.getPassengers().stream().anyMatch(passenger -> passenger.getTags().contains(DASH_POSE_TAG));
        if (present) return;
        Entity marker = EntityType.MARKER.create(level);
        if (marker == null) return;
        marker.moveTo(actor.getX(), actor.getY(), actor.getZ(), actor.getYRot(), actor.getXRot());
        marker.addTag(DASH_POSE_TAG);
        marker.addTag("PM_Crimson_Visual");
        if (level.addFreshEntity(marker)) marker.startRiding(actor, true);
    }

    private static void tickDashPose(Mob actor, long gameTick) {
        if (gameTick < actor.getPersistentData().getLong(DASH_POSE_UNTIL_KEY)) return;
        actor.getPassengers().stream().filter(passenger -> passenger.getTags().contains(DASH_POSE_TAG))
                .forEach(Entity::discard);
    }

    private static void orientPummelerDisplay(Mob actor) {
        actor.getPassengers().stream().filter(passenger -> passenger.getTags().contains(PUMMELER_VISUAL_TAG)).forEach(passenger -> {
            passenger.setYRot(actor.getYRot());
            passenger.setXRot(actor.getXRot());
        });
    }

    private static ItemStack modelBook(int customModelData) {
        ItemStack stack = new ItemStack(Items.BOOK);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(customModelData));
        return stack;
    }

    private static void sound(ServerLevel level, Entity entity, String id, float volume, float pitch) {
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.withDefaultNamespace(id));
        if (event != null) level.playSound(null, entity.blockPosition(), event, SoundSource.HOSTILE, volume, pitch);
    }

    private static void particles(ServerLevel level, ParticleOptions particle, Entity entity, int count,
                                  double xRadius, double yRadius, double zRadius, double speed) {
        level.sendParticles(particle, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(),
                count, xRadius, yRadius, zRadius, speed);
    }

    private static boolean isCrimsonified(String profileId) { return profileId.startsWith("pale_mirror:crimsonified_"); }
    private static boolean isDecayed(String profileId) { return profileId.startsWith("pale_mirror:decayed_"); }
    private static boolean isBloodlink(String profileId) { return profileId.startsWith("pale_mirror:bloodlink_"); }

    private static String baseSound(String profileId, String suffix) {
        String base = switch (profileId) {
            case "pale_mirror:crimsonified_villager", "pale_mirror:decayed_villager" -> "entity.zombie_villager";
            case "pale_mirror:crimsonified_husk", "pale_mirror:decayed_husk" -> "entity.husk";
            case "pale_mirror:crimsonified_skeleton", "pale_mirror:decayed_skeleton" -> "entity.skeleton";
            case "pale_mirror:crimsonified_drowned", "pale_mirror:decayed_drowned" -> "entity.drowned";
            case "pale_mirror:crimsonified_bogged", "pale_mirror:decayed_bogged" -> "entity.bogged";
            case "pale_mirror:crimsonified_wither_skeleton", "pale_mirror:decayed_wither_skeleton" -> "entity.wither_skeleton";
            default -> "entity.zombie";
        };
        return base + "." + suffix;
    }

    private static ServerLevel levelFor(MinecraftServer server, TestMineRecord mine) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(mine.dimensionId())) return level;
        }
        return null;
    }
}
