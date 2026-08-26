package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.Map;
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
                            ReferenceGrayboxActorExecutionState execution, Set<String> active, Map<String, Entity> admitted) {
        for (ReferenceGrayboxSnapshot.Resident resident : snapshot.residents()) {
            ReferenceGrayboxActorExecutionState.ActorState actor = actor(execution, resident.id());
            if (actor != null && actor.mode() != ReferenceGrayboxActorExecutionState.Mode.COLD) active.add(SourceGrayboxMaterializer.entityKey(resident.id(), "RESIDENT"));
            if (actor == null || actor.mode() != ReferenceGrayboxActorExecutionState.Mode.COLD
                    && actor.mode() != ReferenceGrayboxActorExecutionState.Mode.RETIRED) ensureResident(level, ledger, snapshot.stateRevision(), resident, actor, active, admitted);
        }
        for (ReferenceGrayboxSnapshot.Bioform bioform : snapshot.bioforms()) {
            ReferenceGrayboxActorExecutionState.ActorState actor = actor(execution, bioform.id());
            if (actor != null && actor.mode() != ReferenceGrayboxActorExecutionState.Mode.COLD) active.add(SourceGrayboxMaterializer.entityKey(bioform.id(), "BIOFORM"));
            if (actor == null || actor.mode() != ReferenceGrayboxActorExecutionState.Mode.COLD
                    && actor.mode() != ReferenceGrayboxActorExecutionState.Mode.RETIRED) ensureBioform(level, ledger, snapshot.stateRevision(), bioform, actor, active, admitted);
        }
    }

    static Entity actorEntity(ServerLevel level, Map<String, Entity> admitted, ReferenceGrayboxActorExecutionState.ActorState actor) {
        String kind = actor.kind().name();
        Entity entity = SourceGrayboxMaterializer.existingEntity(level, admitted, actor.id(), kind,
                SourceGrayboxMaterializer.uuid(kind.equals("RESIDENT") ? "resident" : "bioform", actor.id()));
        if (entity == null || !SourceGrayboxMaterializer.identityMatches(entity, actor.id(), kind)) return null;
        SourceGrayboxMaterializer.ManagedEntity managed = SourceGrayboxMaterializer.managed(entity);
        if (managed == null || !managed.revision().equals(actor.sourceRevision())) return null;
        return switch (actor.kind()) {
            case RESIDENT -> entity instanceof Villager ? entity : null;
            case BIOFORM -> entity instanceof Zombie ? entity : null;
        };
    }

    private static void ensureResident(ServerLevel level, SourceGrayboxPresentationLedger ledger, String revision,
                                       ReferenceGrayboxSnapshot.Resident resident, ReferenceGrayboxActorExecutionState.ActorState actor,
                                       Set<String> active, Map<String, Entity> admitted) {
        String key = SourceGrayboxMaterializer.entityKey(resident.id(), "RESIDENT");
        active.add(key);
        Vec3 position = position(actor, resident.position());
        if (!SourceGrayboxMaterializer.ready(level, BlockPos.containing(position))) return;
        Entity current = SourceGrayboxMaterializer.existingEntity(level, admitted, resident.id(), "RESIDENT", SourceGrayboxMaterializer.uuid("resident", resident.id()));
        if (current != null && !(current instanceof Villager && SourceGrayboxMaterializer.identityMatches(current, resident.id(), "RESIDENT"))) return;
        if (current instanceof Villager known) {
            ledger.claimEntity(key);
            known.setVillagerData(known.getVillagerData().setProfession(VillagerProfession.NONE));
            configure(known, resident.id(), "RESIDENT", revision, resident.occupation() + " | " + resident.location(),
                    SourceGrayboxPalette.residentHat(resident.occupation(), resident.condition()), false);
            if (actor == null || actor.mode() == ReferenceGrayboxActorExecutionState.Mode.PREPARING) known.setPos(position);
            return;
        }
        if (actor != null && actor.mode() != ReferenceGrayboxActorExecutionState.Mode.PREPARING || ledger.entityClaimed(key)) return;
        Villager villager = new Villager(EntityType.VILLAGER, level);
        villager.setUUID(SourceGrayboxMaterializer.uuid("resident", resident.id()));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
        configure(villager, resident.id(), "RESIDENT", revision, resident.occupation() + " | " + resident.location(),
                SourceGrayboxPalette.residentHat(resident.occupation(), resident.condition()), false);
        villager.setPos(position);
        ledger.claimEntity(key);
        if (level.addFreshEntity(villager)) admitted.put(key, villager);
    }

    private static void ensureBioform(ServerLevel level, SourceGrayboxPresentationLedger ledger, String revision,
                                      ReferenceGrayboxSnapshot.Bioform bioform, ReferenceGrayboxActorExecutionState.ActorState actor,
                                      Set<String> active, Map<String, Entity> admitted) {
        String key = SourceGrayboxMaterializer.entityKey(bioform.id(), "BIOFORM");
        active.add(key);
        Vec3 position = position(actor, bioform.position());
        if (!SourceGrayboxMaterializer.ready(level, BlockPos.containing(position))) return;
        Entity current = SourceGrayboxMaterializer.existingEntity(level, admitted, bioform.id(), "BIOFORM", SourceGrayboxMaterializer.uuid("bioform", bioform.id()));
        if (current != null && !(current instanceof Zombie && SourceGrayboxMaterializer.identityMatches(current, bioform.id(), "BIOFORM"))) return;
        if (current instanceof Zombie known) {
            ledger.claimEntity(key);
            configure(known, bioform.id(), "BIOFORM", revision, bioform.kind() + " | " + bioform.phase(), SourceGrayboxPalette.bioformHat(bioform.kind()), true);
            if (actor == null || actor.mode() == ReferenceGrayboxActorExecutionState.Mode.PREPARING) known.setPos(position);
            return;
        }
        if (actor != null && actor.mode() != ReferenceGrayboxActorExecutionState.Mode.PREPARING || ledger.entityClaimed(key)) return;
        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        zombie.setUUID(SourceGrayboxMaterializer.uuid("bioform", bioform.id()));
        configure(zombie, bioform.id(), "BIOFORM", revision, bioform.kind() + " | " + bioform.phase(), SourceGrayboxPalette.bioformHat(bioform.kind()), true);
        zombie.setPos(position);
        ledger.claimEntity(key);
        if (level.addFreshEntity(zombie)) admitted.put(key, zombie);
    }

    private static ReferenceGrayboxActorExecutionState.ActorState actor(ReferenceGrayboxActorExecutionState state, String id) {
        return state == null ? null : state.actor(id).orElseThrow(() -> new IllegalStateException("source actor execution is missing " + id));
    }

    private static Vec3 position(ReferenceGrayboxActorExecutionState.ActorState actor, ReferenceGrayboxLayout.Point source) {
        if (actor == null) return Vec3.atBottomCenterOf(new BlockPos(source.x(), ENTITY_Y, source.z()));
        return new Vec3(actor.actualXSixteenths() / (double) ReferenceGrayboxActorExecutionState.POSITION_SCALE, ENTITY_Y,
                actor.actualZSixteenths() / (double) ReferenceGrayboxActorExecutionState.POSITION_SCALE);
    }

    private static void configure(Mob entity, String id, String kind, String revision, String name, Item helmet, boolean nameVisible) {
        entity.setPersistenceRequired();
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setCustomName(Component.literal(name));
        entity.setCustomNameVisible(nameVisible);
        entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmet));
        entity.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_ID, id);
        entity.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_KIND, kind);
        entity.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_REVISION, revision);
    }
}
