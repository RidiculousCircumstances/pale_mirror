package io.farfrontier.palemirror.internal.integration.crimson;

import java.util.Set;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.api.IntegrationAdapter;
import io.farfrontier.palemirror.internal.content.EncounterProfile;
import io.farfrontier.palemirror.internal.world.EncounterActorRef;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
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
public final class CrimsonSandboxAdapter implements IntegrationAdapter {
    public static final String OBJECT_ID_KEY = "pale_mirror_object_id";
    public static final String JOB_ID_KEY = "pale_mirror_job_id";
    public static final String ROLE_KEY = "pale_mirror_role";
    public static final String SLOT_KEY = "pale_mirror_encounter_slot";
    public static final String PROFILE_KEY = "pale_mirror_crimson_profile";
    public static final String ACTOR_ROLE = "crimson_actor";
    private static final ResourceLocation CRIMSON_LOAD = ResourceLocation.fromNamespaceAndPath("crimson_curse", "function/load.mcfunction");
    private static final ResourceLocation CRIMSON_TICK = ResourceLocation.fromNamespaceAndPath("crimson_curse", "function/tick.mcfunction");
    private static final String SANDBOX_PACK_ID = "mod/pale_mirror:crimson_sandbox";
    private volatile AdapterHealth verifiedHealth;
    private final CrimsonActorRuntime actorRuntime = new CrimsonActorRuntime();

    @Override
    public String id() { return "pale_mirror:crimson_sandbox"; }

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
                Set.of(Capability.CRIMSON_ENCOUNTER_ACTORS));
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
        actor.moveTo(mine.anchor().getX() + 1.5D + Math.max(slotIndex, 0), mine.anchor().getY() + 1.0D,
                mine.anchor().getZ() + 0.5D, 0.0F, 0.0F);
        actor.setPersistenceRequired();
        actor.getPersistentData().putString(OBJECT_ID_KEY, mine.id().value());
        actor.getPersistentData().putString(JOB_ID_KEY, jobId);
        actor.getPersistentData().putString(ROLE_KEY, ACTOR_ROLE);
        actor.getPersistentData().putString(SLOT_KEY, slot.id());
        actor.getPersistentData().putString(PROFILE_KEY, profile.id());
        if (!level.addFreshEntity(actor)) return ActorOperationResult.unavailable("Could not add Crimson actor to the level");
        if (!CrimsonProtocol1431.initializeActor(level, actor, profile)) {
            actor.discard();
            return ActorOperationResult.unavailable("Crimson sandbox initializer failed");
        }
        if (!isOwnedActor(actor, mine, slot.id()) || !profile.matches(actor)) {
            actor.discard();
            return ActorOperationResult.unavailable("Crimson sandbox initializer postcondition failed");
        }
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
        entity.discard();
        mine.encounter().removed(slotId);
        return ActorOperationResult.materialized();
    }

    /** Runs only PM-registered local actors, with a fixed work budget inside the runtime. */
    public void tickRuntime(MinecraftServer server, PaleMirrorSavedData data) {
        if (server.overworld().getGameTime() % 100L == 0L) verifySandbox(server);
        if (health().status() == AdapterHealth.Status.AVAILABLE) actorRuntime.tick(server, data);
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

}
