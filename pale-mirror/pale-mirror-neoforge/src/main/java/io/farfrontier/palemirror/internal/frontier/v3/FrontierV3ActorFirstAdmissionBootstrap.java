package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import java.util.LinkedHashMap;

/** Issuance at the same explicit fresh-instance boundary used by the canonical runtime. */
final class FrontierV3ActorFirstAdmissionBootstrap {
    private FrontierV3ActorFirstAdmissionBootstrap() { }
    static boolean initialize(FrontierV3AmbientCarrierLedger ledger, FrontierWorldState initial,
                              RecoveryImage recovery, Runnable persist) {
        if (!initial.bootstrap().worldId().equals(recovery.worldId())) throw new IllegalArgumentException("foreign first-admission bootstrap");
        if (recovery.checkpoint().isPresent() || !recovery.walTail().isEmpty()) return false;
        var permits = new LinkedHashMap<io.farfrontier.palemirror.frontier.v3.api.SubjectId, FrontierV3ActorFirstAdmission>();
        initial.humanPopulation().residents().values().forEach(resident -> add(permits, initial, resident.id(), ActorKind.RESIDENT));
        initial.bootstrap().hive().bioforms().forEach(bioform -> add(permits, initial, bioform.id(), ActorKind.BIOFORM));
        initial.hiveColony().spawnedBioforms().values().forEach(bioform -> add(permits, initial, bioform.id(), ActorKind.BIOFORM));
        if (!permits.keySet().equals(initial.actorLocations().keySet()))
            throw new IllegalStateException("initial actor identities do not match the declared canonical rosters");
        if (!ledger.registerFirstAdmissions(java.util.List.copyOf(permits.values())))
            throw new IllegalStateException("fresh bootstrap contradicts retained physical history");
        // Must precede canonical startup and every possible physical admission.
        // An interrupted pre-runtime publication can only repeat identical unused permits.
        persist.run();
        return true;
    }
    private static void add(java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, FrontierV3ActorFirstAdmission> permits,
                            FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId actor, ActorKind kind) {
        var permit = FrontierV3ActorFirstAdmission.neverCreated(new FrontierV3ActorFirstAdmission.Identity(actor, kind,
                FrontierV3AmbientActorExecutor.entityId(state, actor)));
        if (permits.putIfAbsent(actor, permit) != null) throw new IllegalStateException("duplicate declared initial actor");
    }
}
