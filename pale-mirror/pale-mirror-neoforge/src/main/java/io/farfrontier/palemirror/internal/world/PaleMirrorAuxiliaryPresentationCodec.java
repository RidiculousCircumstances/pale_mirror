package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.internal.effect.EffectLease;
import io.farfrontier.palemirror.internal.effect.EffectLeaseState;
import io.farfrontier.palemirror.internal.quarantine.QuarantineKind;
import io.farfrontier.palemirror.internal.quarantine.QuarantineRecord;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticOwner;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubject;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticTuple;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticWireTags;
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
        tag.putString("receipt", lease.receipt());
        return tag;
    }

    static EffectLease readEffectLease(CompoundTag tag) {
        return new EffectLease(tag.getString("id"), tag.getString("key"), tag.getString("source"),
                tag.getString("facility"), tag.getString("slot"), tag.getString("kind"), tag.getLong("created"),
                tag.getLong("expires"), EffectLeaseState.valueOf(tag.getString("state")), tag.getLong("finished"),
                tag.hasUUID("native") ? tag.getUUID("native") : null, tag.getString("diagnostic"), tag.getString("receipt"));
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
        tag.putShort("causeReason", (short) record.cause().reason().wireTag());
        tag.putByte("causeCategory", (byte) record.cause().category().wireTag());
        tag.putByte("causeOwnerKind", (byte) DiagnosticWireTags.ownerTag(record.cause().owner().kind()));
        tag.putString("causeOwner", record.cause().owner().id().value());
        tag.putByte("causeSubjectKind", (byte) DiagnosticWireTags.subjectTag(record.cause().subject().kind()));
        tag.putString("causeSubject", record.cause().subject().id().value());
        tag.putByte("causeDisposition", (byte) record.cause().disposition().wireTag());
        return tag;
    }

    static QuarantineRecord readQuarantine(CompoundTag tag) {
        return new QuarantineRecord(tag.getString("id"), tag.getString("source"),
                QuarantineKind.valueOf(tag.getString("kind")), tag.getString("fingerprint"),
                tag.hasUUID("owner") ? tag.getUUID("owner") : null, tag.getLong("firstSeen"), tag.getLong("lastSeen"),
                tag.getInt("observations"), tag.getString("diagnostic"), cause(tag));
    }

    private static DiagnosticTuple cause(CompoundTag tag) {
        if (!tag.contains("causeReason") || !tag.contains("causeCategory") || !tag.contains("causeOwnerKind")
                || !tag.contains("causeOwner") || !tag.contains("causeSubjectKind") || !tag.contains("causeSubject") || !tag.contains("causeDisposition")) {
            throw new IllegalArgumentException("legacy quarantine record lacks its producer-stamped diagnostic cause");
        }
        return new DiagnosticTuple(DiagnosticWireTags.reason(tag.getShort("causeReason")), DiagnosticWireTags.category(tag.getByte("causeCategory")),
                new DiagnosticOwner(DiagnosticWireTags.ownerKind(tag.getByte("causeOwnerKind")), new SubjectId(tag.getString("causeOwner"))),
                new DiagnosticSubject(DiagnosticWireTags.subjectKind(tag.getByte("causeSubjectKind")), new SubjectId(tag.getString("causeSubject"))),
                DiagnosticWireTags.disposition(tag.getByte("causeDisposition")));
    }
}
