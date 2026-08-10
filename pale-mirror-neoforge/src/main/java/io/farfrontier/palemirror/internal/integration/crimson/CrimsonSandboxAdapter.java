package io.farfrontier.palemirror.internal.integration.crimson;

import java.util.Set;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.api.IntegrationAdapter;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.internal.adapter.ThreatActorAdapter;
import io.farfrontier.palemirror.internal.content.EncounterProfile;
import io.farfrontier.palemirror.internal.integration.ActorOperationResult;
import io.farfrontier.palemirror.internal.world.EncounterActorRef;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.world.SiegePartRef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.ModList;

/**
 * Isolated implementation of the pinned Crimson datapack protocol. PM owns
 * encounter identity and lifecycle; Crimson only supplies the local actor form.
 */
public final class CrimsonSandboxAdapter implements ThreatActorAdapter {
    public static final String OBJECT_ID_KEY = "pale_mirror_object_id";
    public static final String JOB_ID_KEY = "pale_mirror_job_id";
    public static final String ROLE_KEY = "pale_mirror_role";
    public static final String SLOT_KEY = "pale_mirror_encounter_slot";
    public static final String PROFILE_KEY = "pale_mirror_crimson_profile";
    public static final String ACTOR_ROLE = "crimson_actor";
    public static final String SIEGE_ROLE = "crimson_siege";
    private static final ResourceLocation CRIMSON_LOAD = ResourceLocation.fromNamespaceAndPath("crimson_curse", "function/load.mcfunction");
    private static final ResourceLocation CRIMSON_TICK = ResourceLocation.fromNamespaceAndPath("crimson_curse", "function/tick.mcfunction");
    private static final String SANDBOX_PACK_ID = "mod/pale_mirror:crimson_sandbox";
    private volatile AdapterHealth verifiedHealth;
    private final CrimsonPresentationRuntime presentation = new CrimsonPresentationRuntime();
    private final CrimsonActorRuntime actorRuntime = new CrimsonActorRuntime(presentation);
    private final CrimsonSiegeRuntime siegeRuntime = new CrimsonSiegeRuntime(presentation);

    @Override
    public String id() { return "pale_mirror:crimson_sandbox"; }

    @Override
    public InfectionSourceId source() { return InfectionSourceId.CRIMSON; }

    @Override
    public boolean matchesActor(Entity entity) { return isActor(entity); }

    @Override
    public boolean matchesOwnedActor(Entity entity, TestMineRecord site, String slotId) {
        return isOwnedActor(entity, site, slotId);
    }

    @Override
    public AdapterHealth health() {
        AdapterHealth installation = installationHealth();
        if (installation.status() != AdapterHealth.Status.AVAILABLE) return installation;
        return verifiedHealth == null ? new AdapterHealth(AdapterHealth.Status.DEGRADED,
                "Crimson sandbox has not yet verified its top-priority datapack", Set.of()) : verifiedHealth;
    }

    /** Must run after datapacks load; refuses actor materialization if a pack shadows the sandbox. */
    public void verifySandbox(MinecraftServer server) {
        AdapterHealth installation = installationHealth();
        if (installation.status() != AdapterHealth.Status.AVAILABLE) {
            verifiedHealth = installation;
            return;
        }
        String loadSource = sourcePack(server, CRIMSON_LOAD);
        String tickSource = sourcePack(server, CRIMSON_TICK);
        if (!SANDBOX_PACK_ID.equals(loadSource) || !SANDBOX_PACK_ID.equals(tickSource)) {
            verifiedHealth = new AdapterHealth(AdapterHealth.Status.BLOCKED,
                    "Crimson sandbox override is not active; load=" + loadSource + ", tick=" + tickSource, Set.of());
            return;
        }
        verifiedHealth = installation;
    }

    private static AdapterHealth installationHealth() {
        if (!ModList.get().isLoaded(CrimsonProtocol1431.MOD_ID)) {
            return new AdapterHealth(AdapterHealth.Status.ABSENT, "mr_crimson_curse is not installed", Set.of());
        }
        String version = ModList.get().getModContainerById(CrimsonProtocol1431.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
        if (!CrimsonProtocol1431.VERSION.equals(version)) {
            return new AdapterHealth(AdapterHealth.Status.BLOCKED,
                    "Crimson sandbox only supports " + CrimsonProtocol1431.VERSION + ", found " + version, Set.of());
        }
        return new AdapterHealth(AdapterHealth.Status.AVAILABLE,
                "Crimson " + version + " sandboxed: PM owns spread, phases, and actor lifecycle",
                Set.of(Capability.CRIMSON_ENCOUNTER_ACTORS, Capability.CRIMSON_SIEGE_OBJECTS));
    }

    private static String sourcePack(MinecraftServer server, ResourceLocation resource) {
        return server.getResourceManager().getResource(resource).map(value -> value.sourcePackId()).orElse("");
    }

    public ActorOperationResult ensureActor(ServerLevel level, TestMineRecord mine, String jobId,
                                            EncounterProfile.ActorSlot slot) {
        verifySandbox(level.getServer());
        if (health().status() != AdapterHealth.Status.AVAILABLE) return ActorOperationResult.unavailable(health().detail());
        CrimsonActorProfile profile = CrimsonActorProfile.byId(slot.actorProfileId())
                .orElse(null);
        if (profile == null) return ActorOperationResult.unavailable(
                "Crimson sandbox 1.4.3.1 does not support PM actor profile " + slot.actorProfileId());
        EncounterActorRef reference = mine.encounter().actor(slot.id()).orElse(null);
        if (reference != null && reference.status() == EncounterActorRef.Status.DEFEATED) {
            return ActorOperationResult.materialized();
        }
        if (reference != null && reference.entityId() != null) {
            Entity existing = level.getEntity(reference.entityId());
            if (isOwnedActor(existing, mine, slot.id()) && profile.matches(existing)) {
                mine.encounter().activate(slot.id(), reference.entityId(), profile.entityTypeId());
                return ActorOperationResult.materialized();
            }
            if (existing != null) return ActorOperationResult.unavailable("Crimson actor identity conflict for slot " + slot.id());
        }

        Mob actor = profile.create(level);
        if (actor == null) return ActorOperationResult.unavailable("Could not create Crimson actor base entity");
        int slotIndex = mine.encounter().slotIndex(slot.id());
        int boundedIndex = Math.max(slotIndex, 0);
        int xOffset = (boundedIndex % 4) - 1;
        int zOffset = (boundedIndex / 4) - 1;
        actor.moveTo(mine.anchor().getX() + 0.5D + xOffset, mine.anchor().getY() + 1.0D,
                mine.anchor().getZ() + 0.5D + zOffset, 0.0F, 0.0F);
        actor.setPersistenceRequired();
        actor.getPersistentData().putString(OBJECT_ID_KEY, mine.id().value());
        actor.getPersistentData().putString(JOB_ID_KEY, jobId);
        actor.getPersistentData().putString(ROLE_KEY, ACTOR_ROLE);
        actor.getPersistentData().putString(SLOT_KEY, slot.id());
        actor.getPersistentData().putString(PROFILE_KEY, profile.id());
        if (!level.addFreshEntity(actor)) return ActorOperationResult.unavailable("Could not add Crimson actor to the level");
        if (!CrimsonProtocol1431.initializeActor(level, actor, profile)) {
            presentation.discardVisualChildren(actor);
            actor.discard();
            return ActorOperationResult.unavailable("Crimson sandbox initializer failed");
        }
        if (!isOwnedActor(actor, mine, slot.id()) || !profile.matches(actor)) {
            presentation.discardVisualChildren(actor);
            actor.discard();
            return ActorOperationResult.unavailable("Crimson sandbox initializer postcondition failed");
        }
        presentation.spawned(level, actor, profile.id());
        mine.encounter().activate(slot.id(), actor.getUUID(), profile.entityTypeId());
        return ActorOperationResult.materialized();
    }

    public ActorOperationResult removeActor(ServerLevel level, TestMineRecord mine, String slotId) {
        EncounterActorRef reference = mine.encounter().actor(slotId).orElse(null);
        if (reference == null || reference.entityId() == null) {
            mine.encounter().removed(slotId);
            return ActorOperationResult.materialized();
        }
        Entity entity = level.getEntity(reference.entityId());
        if (entity == null) {
            mine.encounter().removed(slotId);
            return ActorOperationResult.materialized();
        }
        if (!isOwnedActor(entity, mine, slotId)) {
            return ActorOperationResult.unavailable("Crimson actor identity conflict during cleanup for slot " + slotId);
        }
        presentation.discardVisualChildren(entity);
        entity.discard();
        mine.encounter().removed(slotId);
        return ActorOperationResult.materialized();
    }

    public ActorOperationResult ensureSiegeEntity(ServerLevel level, TestMineRecord mine, String jobId, SiegePartRef part) {
        verifySandbox(level.getServer());
        if (health().status() != AdapterHealth.Status.AVAILABLE) return ActorOperationResult.unavailable(health().detail());
        CrimsonSiegeProfile profile = CrimsonSiegeProfile.byId(part.profileId()).orElse(null);
        if (profile == null) return ActorOperationResult.unavailable("Unsupported Crimson siege profile " + part.profileId());
        if (part.entityId() != null) {
            Entity existing = level.getEntity(part.entityId());
            if (isOwnedSiegeEntity(existing, mine, part.slotId()) && profile.matches(existing)) {
                mine.siege().activate(part.slotId(), part.entityId());
                return ActorOperationResult.materialized();
            }
            if (existing != null) return ActorOperationResult.unavailable("Crimson siege identity conflict for slot " + part.slotId());
        }
        Mob entity = profile.create(level);
        if (entity == null) return ActorOperationResult.unavailable("Could not create Crimson siege entity");
        entity.moveTo(part.position().getX() + 0.5D, part.position().getY(), part.position().getZ() + 0.5D, 0.0F, 0.0F);
        entity.setPersistenceRequired();
        entity.getPersistentData().putString(OBJECT_ID_KEY, mine.id().value());
        entity.getPersistentData().putString(JOB_ID_KEY, jobId);
        entity.getPersistentData().putString(ROLE_KEY, SIEGE_ROLE);
        entity.getPersistentData().putString(SLOT_KEY, part.slotId());
        entity.getPersistentData().putString(PROFILE_KEY, profile.id());
        if (!level.addFreshEntity(entity)) return ActorOperationResult.unavailable("Could not add Crimson siege entity to level");
        if (!CrimsonProtocol1431.initializeSiegeEntity(level, entity, profile)
                || !isOwnedSiegeEntity(entity, mine, part.slotId()) || !profile.matches(entity)) {
            presentation.discardVisualChildren(entity);
            entity.discard();
            return ActorOperationResult.unavailable("Crimson siege initializer postcondition failed");
        }
        presentation.spawned(level, entity, profile.id());
        mine.siege().activate(part.slotId(), entity.getUUID());
        return ActorOperationResult.materialized();
    }

    public ActorOperationResult removeSiegeEntity(ServerLevel level, TestMineRecord mine, String slotId) {
        SiegePartRef part = mine.siege().part(slotId).orElse(null);
        if (part == null || part.entityId() == null) return ActorOperationResult.materialized();
        Entity entity = level.getEntity(part.entityId());
        if (entity != null && !isOwnedSiegeEntity(entity, mine, slotId)) {
            return ActorOperationResult.unavailable("Crimson siege identity conflict during cleanup for slot " + slotId);
        }
        if (entity != null) {
            presentation.discardVisualChildren(entity);
            entity.discard();
        }
        mine.siege().remove(slotId);
        return ActorOperationResult.materialized();
    }

    /** Runs only PM-registered local actors, with a fixed work budget inside the runtime. */
    public void tickRuntime(MinecraftServer server, PaleMirrorSavedData data) {
        if (server.overworld().getGameTime() % 100L == 0L) verifySandbox(server);
        if (health().status() == AdapterHealth.Status.AVAILABLE) {
            actorRuntime.tick(server, data);
            siegeRuntime.tick(server, data);
            presentation.tick(server, data);
        }
    }

    /** Emits only local PM presentation for a successfully applied damage event. */
    public void presentDamage(net.minecraft.world.entity.LivingEntity entity) {
        presentationProfile(entity).ifPresent(profile -> presentation.hurt(entity, profile));
    }

    /** Emits only local PM presentation for an observed PM actor or siege death. */
    public void presentDeath(net.minecraft.world.entity.LivingEntity entity) {
        presentationProfile(entity).ifPresent(profile -> {
            presentation.died(entity, profile);
            presentation.discardVisualChildren(entity);
        });
    }

    /** Replays the approved attack cue when a PM actor is the source of actual damage. */
    public void presentAttack(Entity entity) {
        if (!(entity instanceof Mob actor)) return;
        presentationProfile(actor).ifPresent(profile -> presentation.attacked(actor, profile));
    }

    private static java.util.Optional<String> presentationProfile(Entity entity) {
        if (entity == null || entity.isRemoved()) return java.util.Optional.empty();
        String profileId = entity.getPersistentData().getString(PROFILE_KEY);
        if (ACTOR_ROLE.equals(entity.getPersistentData().getString(ROLE_KEY))) {
            return CrimsonActorProfile.byId(profileId).filter(profile -> profile.matches(entity)).map(CrimsonActorProfile::id);
        }
        if (SIEGE_ROLE.equals(entity.getPersistentData().getString(ROLE_KEY))) {
            return CrimsonSiegeProfile.byId(profileId).filter(profile -> profile.matches(entity)).map(CrimsonSiegeProfile::id);
        }
        return java.util.Optional.empty();
    }

    public static boolean isOwnedActor(Entity entity, TestMineRecord mine, String slotId) {
        return entity != null && !entity.isRemoved() && ACTOR_ROLE.equals(entity.getPersistentData().getString(ROLE_KEY))
                && mine.id().value().equals(entity.getPersistentData().getString(OBJECT_ID_KEY))
                && slotId.equals(entity.getPersistentData().getString(SLOT_KEY));
    }

    public static boolean isActor(Entity entity) {
        return entity != null && ACTOR_ROLE.equals(entity.getPersistentData().getString(ROLE_KEY))
                && !entity.getPersistentData().getString(OBJECT_ID_KEY).isBlank()
                && !entity.getPersistentData().getString(SLOT_KEY).isBlank();
    }

    public static boolean isSiegeEntity(Entity entity) {
        return entity != null && SIEGE_ROLE.equals(entity.getPersistentData().getString(ROLE_KEY))
                && !entity.getPersistentData().getString(OBJECT_ID_KEY).isBlank()
                && !entity.getPersistentData().getString(SLOT_KEY).isBlank();
    }

    public static boolean isOwnedSiegeEntity(Entity entity, TestMineRecord mine, String slotId) {
        return entity != null && !entity.isRemoved() && SIEGE_ROLE.equals(entity.getPersistentData().getString(ROLE_KEY))
                && mine.id().value().equals(entity.getPersistentData().getString(OBJECT_ID_KEY))
                && slotId.equals(entity.getPersistentData().getString(SLOT_KEY));
    }

}
