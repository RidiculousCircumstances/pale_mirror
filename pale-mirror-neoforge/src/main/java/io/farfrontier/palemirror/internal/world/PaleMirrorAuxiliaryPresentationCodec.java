package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.internal.effect.EffectLease;
import io.farfrontier.palemirror.internal.effect.EffectLeaseState;
import io.farfrontier.palemirror.internal.quarantine.QuarantineKind;
import io.farfrontier.palemirror.internal.quarantine.QuarantineRecord;
import net.minecraft.nbt.CompoundTag;

/** NBT codec for auxiliary physical-effect and diagnostic ledgers. */
final class PaleMirrorAuxiliaryPresentationCodec {
    private PaleMirrorAuxiliaryPresentationCodec() { }

    static CompoundTag writeEffectLease(EffectLease lease) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", lease.id());
        tag.putString("key", lease.idempotencyKey());
        tag.putString("source", lease.sourceId());
        tag.putString("facility", lease.facilityId());
        tag.putString("slot", lease.actorSlotId());
        tag.putString("kind", lease.kind());
        tag.putLong("created", lease.createdAtGameTick());
        tag.putLong("expires", lease.expiresAtGameTick());
        tag.putString("state", lease.state().name());
        tag.putLong("finished", lease.finishedAtGameTick());
        if (lease.nativeReference() != null) tag.putUUID("native", lease.nativeReference());
        tag.putString("diagnostic", lease.diagnostic());
        return tag;
    }

    static EffectLease readEffectLease(CompoundTag tag) {
        return new EffectLease(tag.getString("id"), tag.getString("key"), tag.getString("source"),
                tag.getString("facility"), tag.getString("slot"), tag.getString("kind"), tag.getLong("created"),
                tag.getLong("expires"), EffectLeaseState.valueOf(tag.getString("state")), tag.getLong("finished"),
                tag.hasUUID("native") ? tag.getUUID("native") : null, tag.getString("diagnostic"));
    }

    static CompoundTag writeQuarantine(QuarantineRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", record.id());
        tag.putString("source", record.sourceId());
        tag.putString("kind", record.kind().name());
        tag.putString("fingerprint", record.fingerprint());
        if (record.ownerId() != null) tag.putUUID("owner", record.ownerId());
        tag.putLong("firstSeen", record.firstSeenGameTick());
        tag.putLong("lastSeen", record.lastSeenGameTick());
        tag.putInt("observations", record.observations());
        tag.putString("diagnostic", record.diagnostic());
        return tag;
    }

    static QuarantineRecord readQuarantine(CompoundTag tag) {
        return new QuarantineRecord(tag.getString("id"), tag.getString("source"),
                QuarantineKind.valueOf(tag.getString("kind")), tag.getString("fingerprint"),
                tag.hasUUID("owner") ? tag.getUUID("owner") : null, tag.getLong("firstSeen"), tag.getLong("lastSeen"),
                tag.getInt("observations"), tag.getString("diagnostic"));
    }
}
