package io.farfrontier.palemirror.internal.frontier.v3;

/** Test-only binding producer. Production supplies its real owner address explicitly. */
final class FrontierV3ActorAdoptionFixture {
    private FrontierV3ActorAdoptionFixture() { }
    static FrontierV3ActorOwnerBinding binding(FrontierV3ActorCarrierComposition.Declaration value) {
        return value.owner() == FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE
                ? FrontierV3ActorOwnerBinding.ambient(value)
                : FrontierV3ActorOwnerBinding.scene(value, new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:test-" + value.authorityRevision()));
    }
}
