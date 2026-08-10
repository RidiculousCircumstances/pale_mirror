package io.farfrontier.palemirror.internal.integration.spore;

import java.util.Set;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.internal.adapter.ThreatActorAdapter;
import io.farfrontier.palemirror.internal.adapter.VanillaAnchorAdapter;
import io.farfrontier.palemirror.internal.content.EncounterProfile;
import io.farfrontier.palemirror.internal.integration.ActorDamageResult;
import io.farfrontier.palemirror.internal.integration.ActorOperationResult;
import io.farfrontier.palemirror.internal.world.EncounterActorRef;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Isolated Spore 2.2.0j bridge.  The adapter deliberately uses only the
 * registered entity surface; it imports no Spore implementation class.
 *
 * Native infected AI is not an authority and is unsafe for a PM-controlled
 * site because Spore entities can evolve and write terrain themselves.  Every
 * PM-owned Spore entity is therefore kept in a persistent constrained state
 * (NoAI, no target, no navigation) while PM retains source, phase, spread and
 * cleanup.  A small PM combat executor may use the entity as a stationary
 * model/sound provider, but never invokes native goals, damage, evolution,
 * terrain work or infection. The PM anchor remains the only clearable
 * controller; cleanup discards these forms without invoking Spore's native
 * death/remains path.
 */
public final class SporeSandboxAdapter implements ThreatActorAdapter {
    public static final String ROLE_KEY = VanillaAnchorAdapter.ROLE_KEY;
    public static final String SLOT_KEY = "pale_mirror_encounter_slot";
    public static final String PROFILE_KEY = "pale_mirror_spore_profile";
    public static final String ACTOR_ROLE = "spore_actor";
    public static final String MOD_ID = "spore";
    public static final String VERSION = "2.2.0j";
    private final SporeCombatRuntime combatRuntime = new SporeCombatRuntime();

    @Override
    public String id() { return "pale_mirror:spore_sandbox"; }

    @Override
    public InfectionSourceId source() { return InfectionSourceId.SPORE; }

    @Override
    public AdapterHealth health() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return new AdapterHealth(AdapterHealth.Status.ABSENT, "Fungal Infection:Spore is not installed", Set.of());
        }
        String version = ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
        if (!VERSION.equals(version)) {
            return new AdapterHealth(AdapterHealth.Status.BLOCKED,
                    "Spore sandbox only supports " + VERSION + ", found " + version, Set.of());
        }
        for (SporeActorProfile profile : SporeActorProfile.values()) {
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(profile.entityTypeId()))) {
                return new AdapterHealth(AdapterHealth.Status.BLOCKED,
                        "Spore registry is missing required entity " + profile.entityTypeId(), Set.of());
            }
            if (!BuiltInRegistries.SOUND_EVENT.containsKey(ResourceLocation.parse(profile.attackSoundId()))) {
                return new AdapterHealth(AdapterHealth.Status.BLOCKED,
                        "Spore registry is missing required combat sound " + profile.attackSoundId(), Set.of());
            }
        }
        return new AdapterHealth(AdapterHealth.Status.AVAILABLE,
                "Spore " + version + " sandboxed: PM owns spread, tiers, controller and constrained combat",
                Set.of(Capability.SPORE_ENCOUNTER_ACTORS, Capability.SPORE_CONTROLLED_COMBAT));
    }

    @Override
    public ActorOperationResult ensureActor(ServerLevel level, TestMineRecord site, String jobId,
                                            EncounterProfile.ActorSlot slot) {
        if (health().status() != AdapterHealth.Status.AVAILABLE) return ActorOperationResult.unavailable(health().detail());
        SporeActorProfile profile = SporeActorProfile.byId(slot.actorProfileId()).orElse(null);
        if (profile == null) return ActorOperationResult.unavailable(
                "Spore sandbox " + VERSION + " does not support PM actor profile " + slot.actorProfileId());
        EncounterActorRef reference = site.encounter().actor(slot.id()).orElse(null);
        if (reference != null && reference.status() == EncounterActorRef.Status.DEFEATED) {
            return ActorOperationResult.materialized();
        }
        if (reference != null && reference.entityId() != null) {
            Entity existing = level.getEntity(reference.entityId());
            if (matchesOwnedActor(existing, site, slot.id()) && profile.entityTypeId().equals(entityTypeId(existing))) {
                holdConstrained(existing);
                site.encounter().activate(slot.id(), reference.entityId(), profile.entityTypeId());
                site.encounter().initializeCombatHitPoints(slot.id(), profile.combatHitPoints());
                return ActorOperationResult.materialized();
            }
            if (existing != null) return ActorOperationResult.unavailable("Spore actor identity conflict for slot " + slot.id());
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(profile.entityTypeId()));
        Entity candidate = type.create(level);
        if (!(candidate instanceof Mob actor)) return ActorOperationResult.unavailable(
                "Spore profile " + profile.id() + " did not create a mob");
        int slotIndex = Math.max(site.encounter().slotIndex(slot.id()), 0);
        actor.moveTo(site.anchor().getX() + 0.5D + (slotIndex % 4) - 1, site.anchor().getY() + 1.0D,
                site.anchor().getZ() + 0.5D + (slotIndex / 4) - 1, 0.0F, 0.0F);
        actor.setPersistenceRequired();
        actor.setCustomName(Component.literal("Pale Mirror Spore " + profile.id().substring(profile.id().lastIndexOf(':') + 1)));
        actor.getPersistentData().putString(VanillaAnchorAdapter.OBJECT_ID_KEY, site.id().value());
        actor.getPersistentData().putString(VanillaAnchorAdapter.JOB_ID_KEY, jobId);
        actor.getPersistentData().putString(ROLE_KEY, ACTOR_ROLE);
        actor.getPersistentData().putString(SLOT_KEY, slot.id());
        actor.getPersistentData().putString(PROFILE_KEY, profile.id());
        holdConstrained(actor);
        if (!level.addFreshEntity(actor)) return ActorOperationResult.unavailable("Could not add Spore actor to the level");
        if (!matchesOwnedActor(actor, site, slot.id()) || !profile.entityTypeId().equals(entityTypeId(actor))) {
            actor.discard();
            return ActorOperationResult.unavailable("Spore actor postcondition failed");
        }
        site.encounter().activate(slot.id(), actor.getUUID(), profile.entityTypeId());
        site.encounter().initializeCombatHitPoints(slot.id(), profile.combatHitPoints());
        return ActorOperationResult.materialized();
    }

    @Override
    public ActorOperationResult removeActor(ServerLevel level, TestMineRecord site, String slotId) {
        EncounterActorRef reference = site.encounter().actor(slotId).orElse(null);
        if (reference == null || reference.entityId() == null) {
            site.encounter().removed(slotId);
            return ActorOperationResult.materialized();
        }
        Entity entity = level.getEntity(reference.entityId());
        if (entity == null) {
            site.encounter().removed(slotId);
            return ActorOperationResult.materialized();
        }
        if (!matchesOwnedActor(entity, site, slotId)) {
            return ActorOperationResult.unavailable("Spore actor identity conflict during cleanup for slot " + slotId);
        }
        entity.discard();
        site.encounter().removed(slotId);
        return ActorOperationResult.materialized();
    }

    @Override
    public boolean matchesActor(Entity entity) {
        return entity != null && ACTOR_ROLE.equals(entity.getPersistentData().getString(ROLE_KEY))
                && !entity.getPersistentData().getString(VanillaAnchorAdapter.OBJECT_ID_KEY).isBlank()
                && !entity.getPersistentData().getString(SLOT_KEY).isBlank();
    }

    @Override
    public boolean matchesOwnedActor(Entity entity, TestMineRecord site, String slotId) {
        return matchesActor(entity) && site.id().value().equals(entity.getPersistentData().getString(VanillaAnchorAdapter.OBJECT_ID_KEY))
                && slotId.equals(entity.getPersistentData().getString(SLOT_KEY));
    }

    /**
     * PM consumes player damage into its persisted encounter health.  All
     * other sources are rejected so a native Spore {@code hurt}/{@code die}
     * path can never gain authority over this site.
     */
    @Override
    public ActorDamageResult receiveDamage(ServerLevel level, TestMineRecord site, Entity entity,
                                           EncounterActorRef reference, DamageSource source, float amount) {
        if (!matchesOwnedActor(entity, site, reference.slotId()) || reference.status() != EncounterActorRef.Status.ACTIVE) {
            return ActorDamageResult.blocked("Spore actor identity is stale or not active");
        }
        if (!(source.getEntity() instanceof ServerPlayer player) || player.isSpectator()
                || player.serverLevel() != level || !site.contains(player.blockPosition()) || !site.contains(entity.blockPosition())) {
            return ActorDamageResult.blocked("Only a player inside the PM threat site may damage its constrained Spore actor");
        }
        SporeActorProfile profile = SporeActorProfile.byId(reference.actorProfileId()).orElse(null);
        if (profile == null) return ActorDamageResult.blocked("Unsupported Spore combat profile " + reference.actorProfileId());
        site.encounter().initializeCombatHitPoints(reference.slotId(), profile.combatHitPoints());
        int remaining = site.encounter().consumeCombatHitPoints(reference.slotId(), Math.max(1, Mth.ceil(amount)));
        level.broadcastEntityEvent(entity, (byte) 2);
        level.playSound(null, entity.blockPosition(), profile.attackSound(), SoundSource.HOSTILE, 0.65F, 0.8F);
        if (remaining > 0) return ActorDamageResult.consumed();
        entity.discard();
        return ActorDamageResult.defeated();
    }

    @Override
    public void tickRuntime(MinecraftServer server, PaleMirrorSavedData data) {
        if (health().status() != AdapterHealth.Status.AVAILABLE) return;
        for (TestMineRecord site : data.testMines().values()) {
            ServerLevel level = levelFor(server, site);
            if (level == null) continue;
            for (EncounterActorRef reference : site.encounter().actors()) {
                if (reference.entityId() == null) continue;
                Entity entity = level.getEntity(reference.entityId());
                if (matchesOwnedActor(entity, site, reference.slotId())) holdConstrained(entity);
            }
        }
        combatRuntime.tick(server, data, this);
    }

    private static String entityTypeId(Entity entity) {
        return entity == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    private static ServerLevel levelFor(MinecraftServer server, TestMineRecord site) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(site.dimensionId())) return level;
        }
        return null;
    }

    void holdConstrained(Entity entity) {
        if (!(entity instanceof Mob mob)) return;
        mob.setNoAi(true);
        mob.setInvulnerable(false);
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        mob.getNavigation().stop();
        mob.setDeltaMovement(Vec3.ZERO);
    }
}
