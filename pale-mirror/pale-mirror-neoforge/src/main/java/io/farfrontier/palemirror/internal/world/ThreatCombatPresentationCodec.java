package io.farfrontier.palemirror.internal.world;

import java.util.LinkedHashMap;
import java.util.Map;

import io.farfrontier.palemirror.internal.combat.PmProjectileRef;
import io.farfrontier.palemirror.internal.combat.ThreatActorControlState;
import io.farfrontier.palemirror.internal.combat.ThreatCombatLedger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** NBT boundary for optional PM threat-combat presentation state. */
final class ThreatCombatPresentationCodec {
    private ThreatCombatPresentationCodec() { }

    static ThreatCombatLedger read(CompoundTag root) {
        Map<String, ThreatActorControlState> actors = new LinkedHashMap<>();
        for (Tag element : root.getList("threatCombatActors", Tag.TAG_COMPOUND)) {
            ThreatActorControlState state = readActor((CompoundTag) element);
            actors.put(state.key(), state);
        }
        Map<String, PmProjectileRef> projectiles = new LinkedHashMap<>();
        for (Tag element : root.getList("pmProjectiles", Tag.TAG_COMPOUND)) {
            PmProjectileRef ref = readProjectile((CompoundTag) element);
            projectiles.put(ref.id(), ref);
        }
        return new ThreatCombatLedger(actors, projectiles);
    }

    static void write(CompoundTag root, ThreatCombatLedger ledger) {
        ListTag actors = new ListTag();
        ledger.actors().forEach(state -> actors.add(writeActor(state)));
        root.put("threatCombatActors", actors);
        ListTag projectiles = new ListTag();
        ledger.projectiles().forEach(ref -> projectiles.add(writeProjectile(ref)));
        root.put("pmProjectiles", projectiles);
    }

    private static CompoundTag writeActor(ThreatActorControlState state) {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", state.key());
        tag.putString("source", state.sourceId());
        tag.putString("facility", state.facilityId());
        tag.putString("role", state.role());
        tag.putString("slot", state.slotId());
        tag.putString("profile", state.profileId());
        if (state.entityId() != null) tag.putUUID("entity", state.entityId());
        tag.putInt("health", state.hitPoints());
        tag.putLong("nextAction", state.nextActionTick());
        tag.putLong("nextMovement", state.nextMovementTick());
        tag.putInt("routeCursor", state.routeCursor());
        tag.putString("status", state.status().name());
        return tag;
    }

    private static ThreatActorControlState readActor(CompoundTag tag) {
        return new ThreatActorControlState(tag.getString("key"), tag.getString("source"), tag.getString("facility"),
                tag.getString("role"), tag.getString("slot"), tag.getString("profile"),
                tag.hasUUID("entity") ? tag.getUUID("entity") : null, tag.getInt("health"), tag.getLong("nextAction"),
                tag.getLong("nextMovement"), tag.getInt("routeCursor"),
                ThreatActorControlState.Status.valueOf(tag.getString("status")));
    }

    private static CompoundTag writeProjectile(PmProjectileRef ref) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", ref.id());
        tag.putString("source", ref.sourceId());
        tag.putString("facility", ref.facilityId());
        tag.putString("shooter", ref.shooterKey());
        if (ref.targetId() != null) tag.putUUID("target", ref.targetId());
        tag.putString("visual", ref.visualProfileId());
        tag.putString("launchLease", ref.launchLeaseId());
        tag.putFloat("damage", ref.damage());
        tag.putLong("created", ref.createdAtGameTick());
        tag.putLong("expires", ref.expiresAtGameTick());
        if (ref.entityId() != null) tag.putUUID("entity", ref.entityId());
        tag.putString("impactLease", ref.impactLeaseId());
        tag.putString("state", ref.state().name());
        tag.putLong("finished", ref.finishedAtGameTick());
        tag.putString("diagnostic", ref.diagnostic());
        return tag;
    }

    private static PmProjectileRef readProjectile(CompoundTag tag) {
        return new PmProjectileRef(tag.getString("id"), tag.getString("source"), tag.getString("facility"),
                tag.getString("shooter"), tag.hasUUID("target") ? tag.getUUID("target") : null, tag.getString("visual"),
                tag.getString("launchLease"), tag.getFloat("damage"), tag.getLong("created"), tag.getLong("expires"),
                tag.hasUUID("entity") ? tag.getUUID("entity") : null, tag.getString("impactLease"),
                PmProjectileRef.State.valueOf(tag.getString("state")), tag.getLong("finished"), tag.getString("diagnostic"));
    }
}
