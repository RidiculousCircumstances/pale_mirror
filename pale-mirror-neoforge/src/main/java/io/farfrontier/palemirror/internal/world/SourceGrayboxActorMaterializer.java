package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Lease-aware projection of exact source actors; it never decides who canonically exists. */
final class SourceGrayboxActorMaterializer {
    private static final int ENTITY_Y = ReferenceGrayboxLayout.GROUND_Y + 1;

    private SourceGrayboxActorMaterializer() { }

    static void materialize(ServerLevel level, SourceGrayboxPresentationLedger ledger, ReferenceGrayboxSnapshot snapshot,
                            ReferenceGrayboxActorExecutionState execution, Set<String> active, Map<String, Entity> admitted,
                            SourceGrayboxActorRecoveryCapture recovered) {
        Map<String, String> semanticRevisions = semanticRevisions(snapshot);
        for (ReferenceGrayboxSnapshot.Resident resident : snapshot.residents()) {
            ReferenceGrayboxActorExecutionState.ActorState actor = actor(execution, resident.id());
            if (actor != null && actor.mode() != ReferenceGrayboxActorExecutionState.Mode.COLD) active.add(SourceGrayboxMaterializer.entityKey(resident.id(), "RESIDENT"));
            if (actor == null || actor.mode() != ReferenceGrayboxActorExecutionState.Mode.COLD
                    && actor.mode() != ReferenceGrayboxActorExecutionState.Mode.RETIRED) ensureResident(level, ledger, snapshot.stateRevision(),
                    semanticRevision(actor, semanticRevisions, resident.id()), resident, actor, active, admitted, recovered);
        }
        for (ReferenceGrayboxSnapshot.Bioform bioform : snapshot.bioforms()) {
            ReferenceGrayboxActorExecutionState.ActorState actor = actor(execution, bioform.id());
            if (actor != null && actor.mode() != ReferenceGrayboxActorExecutionState.Mode.COLD) active.add(SourceGrayboxMaterializer.entityKey(bioform.id(), "BIOFORM"));
            if (actor == null || actor.mode() != ReferenceGrayboxActorExecutionState.Mode.COLD
                    && actor.mode() != ReferenceGrayboxActorExecutionState.Mode.RETIRED) ensureBioform(level, ledger, snapshot.stateRevision(),
                    semanticRevision(actor, semanticRevisions, bioform.id()), bioform, actor, active, admitted, recovered);
        }
    }

    static Entity actorEntity(ServerLevel level, Map<String, Entity> admitted, ReferenceGrayboxActorExecutionState.ActorState actor) {
        String kind = actor.kind().name();
        Entity entity = SourceGrayboxMaterializer.existingEntity(level, admitted, actor.id(), kind,
                SourceGrayboxMaterializer.uuid(kind.equals("RESIDENT") ? "resident" : "bioform", actor.id()));
        if (entity == null || !SourceGrayboxMaterializer.identityMatches(entity, actor.id(), kind)) return null;
        SourceGrayboxMaterializer.ManagedEntity managed = SourceGrayboxMaterializer.managed(entity);
        String semanticRevision = entity.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ACTOR_REVISION);
        if (managed == null || !semanticRevision.matches("[0-9a-f]{64}") || !semanticRevision.equals(actor.sourceRevision())) return null;
        return switch (actor.kind()) {
            case RESIDENT -> entity instanceof Villager ? entity : null;
            case BIOFORM -> entity instanceof Zombie ? entity : null;
        };
    }

    private static void ensureResident(ServerLevel level, SourceGrayboxPresentationLedger ledger, String observationRevision,
                                       String semanticRevision,
                                       ReferenceGrayboxSnapshot.Resident resident, ReferenceGrayboxActorExecutionState.ActorState actor,
                                       Set<String> active, Map<String, Entity> admitted, SourceGrayboxActorRecoveryCapture recovered) {
        String key = SourceGrayboxMaterializer.entityKey(resident.id(), "RESIDENT");
        active.add(key);
        Vec3 position = position(actor, resident.position());
        if (!level.hasChunkAt(BlockPos.containing(position))) return;
        Entity current = SourceGrayboxMaterializer.existingEntity(level, admitted, resident.id(), "RESIDENT", SourceGrayboxMaterializer.uuid("resident", resident.id()));
        if (current != null && !(current instanceof Villager && SourceGrayboxMaterializer.identityMatches(current, resident.id(), "RESIDENT"))) return;
        if (current instanceof Villager known) {
            known.setVillagerData(known.getVillagerData().setProfession(VillagerProfession.NONE));
            configure(known, resident.id(), "RESIDENT", observationRevision, semanticRevision, resident.occupation() + " | " + resident.location(),
                    SourceGrayboxPalette.residentHat(resident.occupation(), resident.condition()), false);
            if (requiresPhysicalAdmission(actor, known)) {
                Vec3 recovery = recoveryPosition(actor, known, position);
                if (!placePrepared(level, ledger, key, resident.id(), "RESIDENT", semanticRevision, known, recovery)) {
                    known.discard();
                    admitted.remove(key);
                    ledger.releaseEntity(key);
                    return;
                }
                if (actor != null && actor.mode() == ReferenceGrayboxActorExecutionState.Mode.HOT) recovered.record(resident.id(), "RESIDENT");
            }
            ledger.claimEntity(key);
            return;
        }
        if (actor != null && actor.mode() != ReferenceGrayboxActorExecutionState.Mode.PREPARING || ledger.entityClaimed(key)) return;
        Villager villager = new Villager(EntityType.VILLAGER, level);
        villager.setUUID(SourceGrayboxMaterializer.uuid("resident", resident.id()));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
        configure(villager, resident.id(), "RESIDENT", observationRevision, semanticRevision, resident.occupation() + " | " + resident.location(),
                SourceGrayboxPalette.residentHat(resident.occupation(), resident.condition()), false);
        if (!placePrepared(level, ledger, key, resident.id(), "RESIDENT", semanticRevision, villager, position)) return;
        if (level.addFreshEntity(villager)) {
            ledger.claimEntity(key);
            admitted.put(key, villager);
        }
    }

    private static void ensureBioform(ServerLevel level, SourceGrayboxPresentationLedger ledger, String observationRevision,
                                      String semanticRevision,
                                      ReferenceGrayboxSnapshot.Bioform bioform, ReferenceGrayboxActorExecutionState.ActorState actor,
                                      Set<String> active, Map<String, Entity> admitted, SourceGrayboxActorRecoveryCapture recovered) {
        String key = SourceGrayboxMaterializer.entityKey(bioform.id(), "BIOFORM");
        active.add(key);
        Vec3 position = position(actor, bioform.position());
        if (!level.hasChunkAt(BlockPos.containing(position))) return;
        Entity current = SourceGrayboxMaterializer.existingEntity(level, admitted, bioform.id(), "BIOFORM", SourceGrayboxMaterializer.uuid("bioform", bioform.id()));
        if (current != null && !(current instanceof Zombie && SourceGrayboxMaterializer.identityMatches(current, bioform.id(), "BIOFORM"))) return;
        if (current instanceof Zombie known) {
            configure(known, bioform.id(), "BIOFORM", observationRevision, semanticRevision, bioform.kind() + " | " + bioform.phase(),
                    SourceGrayboxPalette.bioformHat(bioform.kind()), true);
            if (requiresPhysicalAdmission(actor, known)) {
                Vec3 recovery = recoveryPosition(actor, known, position);
                if (!placePrepared(level, ledger, key, bioform.id(), "BIOFORM", semanticRevision, known, recovery)) {
                    known.discard();
                    admitted.remove(key);
                    ledger.releaseEntity(key);
                    return;
                }
                if (actor != null && actor.mode() == ReferenceGrayboxActorExecutionState.Mode.HOT) recovered.record(bioform.id(), "BIOFORM");
            }
            ledger.claimEntity(key);
            return;
        }
        if (actor != null && actor.mode() != ReferenceGrayboxActorExecutionState.Mode.PREPARING || ledger.entityClaimed(key)) return;
        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        zombie.setUUID(SourceGrayboxMaterializer.uuid("bioform", bioform.id()));
        configure(zombie, bioform.id(), "BIOFORM", observationRevision, semanticRevision, bioform.kind() + " | " + bioform.phase(),
                SourceGrayboxPalette.bioformHat(bioform.kind()), true);
        if (!placePrepared(level, ledger, key, bioform.id(), "BIOFORM", semanticRevision, zombie, position)) return;
        if (level.addFreshEntity(zombie)) {
            ledger.claimEntity(key);
            admitted.put(key, zombie);
        }
    }

    private static ReferenceGrayboxActorExecutionState.ActorState actor(ReferenceGrayboxActorExecutionState state, String id) {
        return state == null ? null : state.actor(id).orElseThrow(() -> new IllegalStateException("source actor execution is missing " + id));
    }

    private static Map<String, String> semanticRevisions(ReferenceGrayboxSnapshot snapshot) {
        Map<String, String> result = new LinkedHashMap<>();
        for (ReferenceGrayboxActorExecutionState.ActorDescriptor descriptor : ReferenceGrayboxActorExecutionState.descriptors(snapshot)) {
            if (result.putIfAbsent(descriptor.id(), descriptor.sourceRevision()) != null) {
                throw new IllegalStateException("duplicate source actor semantic revision: " + descriptor.id());
            }
        }
        return result;
    }

    private static String semanticRevision(ReferenceGrayboxActorExecutionState.ActorState actor, Map<String, String> expected, String actorId) {
        String revision = expected.get(actorId);
        if (revision == null) throw new IllegalStateException("source actor is absent from semantic revision map: " + actorId);
        if (actor != null && !actor.sourceRevision().equals(revision)) {
            throw new IllegalStateException("source actor execution disagrees with current semantic revision: " + actorId);
        }
        return revision;
    }

    private static Vec3 position(ReferenceGrayboxActorExecutionState.ActorState actor, ReferenceGrayboxLayout.Point source) {
        if (actor == null) return Vec3.atBottomCenterOf(new BlockPos(source.x(), ENTITY_Y, source.z()));
        return new Vec3(actor.actualXSixteenths() / (double) ReferenceGrayboxActorExecutionState.POSITION_SCALE, ENTITY_Y,
                actor.actualZSixteenths() / (double) ReferenceGrayboxActorExecutionState.POSITION_SCALE);
    }

    /** A source scene may appear around an already HOT actor between local movement captures. */
    private static boolean requiresPhysicalAdmission(ReferenceGrayboxActorExecutionState.ActorState actor, Mob body) {
        return actor == null || actor.mode() == ReferenceGrayboxActorExecutionState.Mode.PREPARING
                || actor.mode() == ReferenceGrayboxActorExecutionState.Mode.HOT && !body.level().noCollision(body, body.getBoundingBox());
    }

    /** Existing HOT actors recover from their observed exact body position, never an older source marker. */
    private static Vec3 recoveryPosition(ReferenceGrayboxActorExecutionState.ActorState actor, Mob body, Vec3 sourcePosition) {
        if (actor != null && actor.mode() == ReferenceGrayboxActorExecutionState.Mode.HOT) {
            return new Vec3(body.getX(), ENTITY_Y, body.getZ());
        }
        return sourcePosition;
    }

    private static boolean placePrepared(ServerLevel level, SourceGrayboxPresentationLedger ledger, String key, String actorId,
                                         String kind, String semanticRevision, Mob body, Vec3 desired) {
        Optional<Vec3> safe = SourceGrayboxActorSpawnResolver.resolve(level, body, desired, actorId);
        if (safe.isEmpty()) {
            recordObstruction(ledger, actorId, kind, semanticRevision, desired);
            return false;
        }
        body.setPos(safe.get());
        clearObstruction(ledger, key);
        return true;
    }

    private static void recordObstruction(SourceGrayboxPresentationLedger ledger, String actorId, String kind,
                                          String semanticRevision, Vec3 desired) {
        String id = obstructionId(kind, actorId);
        BlockPos position = BlockPos.containing(desired);
        ledger.put(new SourceGrayboxPresentationLedger.Claim(id, actorId, "SOURCE_ACTOR_OBSTRUCTION", semanticRevision,
                position.getX(), position.getY(), position.getZ(), 1, 1, 1, false, "", 0.0d, false, false));
        ledger.conflict(id);
    }

    private static void clearObstruction(SourceGrayboxPresentationLedger ledger, String key) {
        int separator = key.indexOf(':');
        if (separator < 1 || separator == key.length() - 1) throw new IllegalArgumentException("source actor key is invalid");
        String id = obstructionId(key.substring(0, separator), key.substring(separator + 1));
        SourceGrayboxPresentationLedger.Claim claim = ledger.claim(id);
        if (claim != null && claim.kind().equals("SOURCE_ACTOR_OBSTRUCTION")) ledger.remove(id);
    }

    static boolean isActorObstructed(SourceGrayboxPresentationLedger ledger, ReferenceGrayboxActorExecutionState.ActorState actor) {
        SourceGrayboxPresentationLedger.Claim claim = ledger.claim(obstructionId(actor.kind().name(), actor.id()));
        return claim != null && claim.kind().equals("SOURCE_ACTOR_OBSTRUCTION") && claim.conflicted() && !claim.installed();
    }

    static String obstructionId(String kind, String actorId) {
        return "actor-obstruction:" + kind + ":" + actorId;
    }

    private static void configure(Mob entity, String id, String kind, String observationRevision, String semanticRevision,
                                  String name, Item helmet, boolean nameVisible) {
        entity.setPersistenceRequired();
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setCustomName(Component.literal(name));
        entity.setCustomNameVisible(nameVisible);
        entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmet));
        entity.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_ID, id);
        entity.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_KIND, kind);
        entity.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_REVISION, observationRevision);
        entity.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_ACTOR_REVISION, semanticRevision);
    }
}
