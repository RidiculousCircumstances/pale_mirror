package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.Zombie;

import java.util.Objects;
import java.util.UUID;
import java.util.List;

/**
 * The one nominal declaration carried by every v3 exact actor body.
 *
 * <p>This is a composition boundary, not a second actor registry.  The canonical resident
 * roster and hive roster remain the only producers of actor kind; this class only requires the
 * producer to state the complete tuple before a Minecraft entity or inactive carrier is touched.
 * In particular, an entity class, NBT id spelling, UUID lookup, or a sole matching roster entry
 * can validate a declared tuple but never supplies a missing dimension.</p>
 */
final class FrontierV3ActorCarrierComposition {
    static final String ACTOR_KEY = "pale_mirror_frontier_v3_actor";
    static final String KIND_KEY = "pale_mirror_frontier_v3_actor_kind";
    static final String OWNER_KEY = "pale_mirror_frontier_v3_actor_owner";
    static final String REPRESENTATION_KEY = "pale_mirror_frontier_v3_actor_representation";
    static final String REVISION_KEY = "pale_mirror_frontier_v3_actor_revision";
    static final String EPOCH_KEY = "pale_mirror_frontier_v3_actor_epoch";

    private FrontierV3ActorCarrierComposition() { }

    enum ActorKind { RESIDENT, BIOFORM }
    enum Owner { AMBIENT_LEASE, SCENE_LEASE }
    enum Representation { LIVE_BODY, INACTIVE_CARRIER }
    enum Role { PRODUCER, ADOPTER }

    /** Closed XACT-001 inventory; every exact body executor must be represented here. */
    enum InventoryEntry {
        AMBIENT_BODY(FrontierV3AmbientActorExecutor.class, Role.PRODUCER),
        SCENE_BODY(FrontierV3SceneExecutor.class, Role.PRODUCER),
        RESOURCE_HARVEST(FrontierV3ResourceSiteHarvestSceneExecutor.class, Role.ADOPTER),
        PRODUCTION_WORK(FrontierV3ProductionWorkSceneExecutor.class, Role.ADOPTER),
        SETTLEMENT_SERVICE(FrontierV3SettlementServiceWorkSceneExecutor.class, Role.ADOPTER),
        SETTLEMENT_ASSAULT(FrontierV3SettlementAssaultSceneExecutor.class, Role.ADOPTER),
        ENGINEERING_WORK(FrontierV3EngineeringWorkSceneExecutor.class, Role.ADOPTER),
        MEDICAL_TREATMENT(FrontierV3MedicalTreatmentSceneExecutor.class, Role.ADOPTER),
        ROUTE_PATROL(FrontierV3RoutePatrolSceneExecutor.class, Role.ADOPTER);
        private final Class<?> boundaryType;
        private final Role role;
        InventoryEntry(Class<?> boundaryType, Role role) { this.boundaryType = boundaryType; this.role = role; }
        Class<?> boundaryType() { return boundaryType; }
        Role role() { return role; }
    }

    static List<InventoryEntry> inventory() { return List.of(InventoryEntry.values()); }
    static void requireRegistered(InventoryEntry entry) {
        if (entry == null || !inventory().contains(entry)) throw new IllegalArgumentException("unregistered actor-carrier boundary");
    }
    static void requireRole(InventoryEntry entry, Role expected) {
        requireRegistered(entry);
        if (entry.role() != expected) throw new IllegalArgumentException("actor-carrier boundary role mismatch");
    }
    record Declaration(SubjectId actorId, ActorKind kind, Owner owner, UUID entityId,
                       Representation representation, long authorityRevision, long epoch) {
        Declaration {
            Objects.requireNonNull(actorId, "actor id"); Objects.requireNonNull(kind, "actor kind");
            Objects.requireNonNull(owner, "lifecycle owner"); Objects.requireNonNull(entityId, "entity id");
            Objects.requireNonNull(representation, "representation");
            // The canonical bootstrap's first durable lease is revision -1 until its prepare
            // transaction is committed.  It is still an explicit authority value, not an
            // omitted tuple dimension; values below that sentinel are invalid.
            if (authorityRevision < -1L || epoch < 1L) throw new IllegalArgumentException("invalid actor carrier authority revision="
                    + authorityRevision + " epoch=" + epoch);
        }
        Declaration inactiveCarrier() { return new Declaration(actorId, kind, owner, entityId, Representation.INACTIVE_CARRIER, authorityRevision, epoch); }
        Declaration liveBody(Owner nextOwner, long nextRevision, long nextEpoch) {
            return new Declaration(actorId, kind, nextOwner, entityId, Representation.LIVE_BODY, nextRevision, nextEpoch);
        }
    }

    /** Validates only an already complete producer declaration; it deliberately derives nothing. */
    static boolean owns(Entity entity, Declaration declaration) {
        return entity != null && !entity.isRemoved() && matchesDeclaration(entity, declaration);
    }

    /** Only an actual chunk-unload callback may inspect the already-removed final body. */
    static boolean ownsUnloading(Entity entity, Declaration declaration) {
        return entity != null && entity.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK
                && matchesDeclaration(entity, declaration);
    }

    private static boolean matchesDeclaration(Entity entity, Declaration declaration) {
        if (!declaration.entityId().equals(entity.getUUID())) return false;
        if (declaration.kind() == ActorKind.BIOFORM ? !(entity instanceof Zombie) : !(entity instanceof Villager)) return false;
        return declaration.actorId().value().equals(entity.getPersistentData().getString(ACTOR_KEY))
                && declaration.kind().name().equals(entity.getPersistentData().getString(KIND_KEY))
                && declaration.owner().name().equals(entity.getPersistentData().getString(OWNER_KEY))
                && declaration.representation().name().equals(entity.getPersistentData().getString(REPRESENTATION_KEY))
                && declaration.authorityRevision() == entity.getPersistentData().getLong(REVISION_KEY)
                && declaration.epoch() == entity.getPersistentData().getLong(EPOCH_KEY);
    }

    static void stamp(Entity entity, Declaration declaration) {
        entity.getPersistentData().putString(ACTOR_KEY, declaration.actorId().value());
        entity.getPersistentData().putString(KIND_KEY, declaration.kind().name());
        entity.getPersistentData().putString(OWNER_KEY, declaration.owner().name());
        entity.getPersistentData().putString(REPRESENTATION_KEY, declaration.representation().name());
        entity.getPersistentData().putLong(REVISION_KEY, declaration.authorityRevision());
        entity.getPersistentData().putLong(EPOCH_KEY, declaration.epoch());
    }

    /**
     * The canonical producer supplies the actor kind as one of its two closed rosters.  A missing
     * or duplicate roster declaration is not repaired by an ID convention and is rejected here.
     */
    static Declaration fromCanonical(FrontierWorldState state, SubjectId actorId, ActorKind kind, Owner owner,
                                     UUID entityId, Representation representation, long revision, long epoch) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(actorId, "actor id");
        boolean resident = state.humanPopulation().residents().containsKey(actorId);
        boolean bioform = state.bootstrap().hive().bioforms().stream().anyMatch(value -> value.id().equals(actorId))
                || state.hiveColony().spawnedBioforms().containsKey(actorId);
        if (resident == bioform || (kind == ActorKind.RESIDENT) != resident || !state.actorLocations().containsKey(actorId)) {
            throw new IllegalArgumentException("canonical actor declaration does not match its closed producer roster");
        }
        return new Declaration(actorId, kind, owner, entityId, representation, revision, epoch);
    }
}
