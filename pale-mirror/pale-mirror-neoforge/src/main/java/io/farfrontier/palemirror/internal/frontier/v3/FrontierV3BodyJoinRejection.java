package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryAsset;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryDisposition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;

/** Read-only explanation of an already canceled join; never admission or cleanup authority. */
final class FrontierV3BodyJoinRejection {
    private FrontierV3BodyJoinRejection() { }

    enum Reason {
        INVALID_DECLARATION, FOREIGN_IDENTITY, DEPARTURE_CONFLICT, UNKNOWN_RESIDENCE,
        RETIRED_INCARNATION, NO_CURRENT_AUTHORITY, EPOCH_MISMATCH, DUPLICATE_UUID,
        STALE_RESIDENCE, INACTIVE_CUSTODY, RETURN_NOT_CONFIRMED, OWNER_HISTORY_MISMATCH,
        CURRENT_BODY_CANCELED
    }

    record Evidence(Reason reason, long observedEpoch, long currentEpoch, long retiredEpoch,
                    String phase, long residence, boolean knownResidence, boolean currentResidence,
                    boolean inactiveCustody, boolean pendingDeparture, boolean indexedDuplicate) {
        boolean expectedRetirement() { return reason == Reason.RETIRED_INCARNATION; }
    }

    static Evidence inspect(FrontierWorldState state, FrontierV3AmbientCarrierLedger ledger,
                           FrontierV3ActorCarrierComposition.Declaration declaration,
                           long residence, boolean indexedDuplicate) {
        if (declaration == null) return new Evidence(Reason.INVALID_DECLARATION, -1, -1, -1,
                "UNKNOWN", residence, false, false, false, false, indexedDuplicate);
        var actor = state.actorLocations().get(declaration.actorId());
        var bindingId = ActorBodyId.recoveryBindingId(declaration.actorId());
        var current = state.fencedRecovery().current().get(bindingId);
        var retired = state.fencedRecovery().tombstones().get(bindingId);
        boolean known = ledger.knownBodyResidence(declaration.actorId(), residence);
        boolean currentResidence = ledger.currentBodyResidence(declaration.actorId(), residence);
        boolean inactive = ledger.hasCarrier(declaration.actorId());
        boolean departure = ledger.hasBodyDeparture(declaration.actorId());
        Reason reason;
        if (actor == null || actor.kind() != declaration.kind()
                || !ActorBodyId.entityId(state.bootstrap().worldId(), declaration.actorId()).equals(declaration.entityId())
                || declaration.representation() != FrontierV3ActorCarrierComposition.Representation.LIVE_BODY)
            reason = Reason.FOREIGN_IDENTITY;
        else if (ledger.hasDepartureConflict(declaration.actorId())) reason = Reason.DEPARTURE_CONFLICT;
        else if (!known) reason = Reason.UNKNOWN_RESIDENCE;
        else if (retired != null && retired.asset() == FencedRecoveryAsset.BODY
                && retired.ownerId().equals(declaration.actorId()) && retired.ownerRevision() == 0L
                && (retired.disposition() == FencedRecoveryDisposition.REJECT_STALE
                    || retired.disposition() == FencedRecoveryDisposition.RESUME_COLD)
                && declaration.epoch() <= retired.retiredEpoch()) reason = Reason.RETIRED_INCARNATION;
        else if (current == null) reason = Reason.NO_CURRENT_AUTHORITY;
        else if (declaration.epoch() != current.authorityEpoch()) reason = Reason.EPOCH_MISMATCH;
        else if (indexedDuplicate) reason = Reason.DUPLICATE_UUID;
        else if (!currentResidence) reason = Reason.STALE_RESIDENCE;
        else if (inactive) reason = Reason.INACTIVE_CUSTODY;
        else if (departure) reason = Reason.RETURN_NOT_CONFIRMED;
        else if (!ledger.permitsRecordedOwner(FrontierV3ActorOwnerBinding.body(declaration)))
            reason = Reason.OWNER_HISTORY_MISMATCH;
        else reason = Reason.CURRENT_BODY_CANCELED;
        return new Evidence(reason, declaration.epoch(), current == null ? -1 : current.authorityEpoch(),
                retired == null ? -1 : retired.retiredEpoch(), current == null ? "ABSENT" : current.phase().name(),
                residence, known, currentResidence, inactive, departure, indexedDuplicate);
    }
}
