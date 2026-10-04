package io.farfrontier.palemirror.internal.frontier.v3;

/** Test-only binding producer. Production supplies its real owner address explicitly. */
final class FrontierV3ActorAdoptionFixture {
    private FrontierV3ActorAdoptionFixture() { }
    static FrontierV3ActorOwnerBinding binding(FrontierV3ActorCarrierComposition.Declaration value) {
        return FrontierV3ActorOwnerBinding.body(value);
    }
    /** Explicit modeled first-body save, never a production permission fallback. */
    static void establishPhysicalHistory(FrontierV3AmbientCarrierLedger ledger,
                                          FrontierV3ActorCarrierComposition.Declaration value) {
        var first = value.liveBody(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, 0L, 1L);
        var target = binding(first);
        if (!ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                new FrontierV3ActorFirstAdmission.Identity(first.actorId(), first.kind(), first.entityId())))
                || !ledger.beginFirstAdmission(target)
                || !ledger.acknowledgeFirstAdmission(ledger.firstAdmission(first.actorId()).orElseThrow(), target))
            throw new IllegalStateException("modeled first physical save could not establish its exact history");
    }
}
