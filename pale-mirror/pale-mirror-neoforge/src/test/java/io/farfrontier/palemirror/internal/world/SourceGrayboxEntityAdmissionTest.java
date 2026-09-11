package io.farfrontier.palemirror.internal.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle.EntityJoinAdmission;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle.JoinFirewallProof;
import org.junit.jupiter.api.Test;

class SourceGrayboxEntityAdmissionTest {
    private static final JoinFirewallProof NOT_MANAGED = new JoinFirewallProof(EntityJoinAdmission.NOT_MANAGED, false);
    private static final JoinFirewallProof DUPLICATE = new JoinFirewallProof(EntityJoinAdmission.DUPLICATE_UNINDEXED, false);
    private static final JoinFirewallProof VERIFIED = new JoinFirewallProof(EntityJoinAdmission.RETAINED, true);

    @Test
    void productionProofAwareDecisionKeepsNonMobIngressOutsideActorCustody() {
        assertFalse(SourceGrayboxEntityAdmission.rejects(true, false, false, NOT_MANAGED),
                "an ordinary non-Mob must not be canceled as an unverified actor");
        assertFalse(SourceGrayboxEntityAdmission.rejects(true, false, false, DUPLICATE),
                "an actor duplicate proof must not acquire custody over a non-Mob ingress");
    }

    @Test
    void productionProofAwareDecisionRetainsForeignDuplicateAndVerifiedMobFirewall() {
        assertTrue(SourceGrayboxEntityAdmission.rejects(true, true, false, NOT_MANAGED),
                "an unmanaged source Mob must still fail closed");
        assertTrue(SourceGrayboxEntityAdmission.rejects(true, true, false, DUPLICATE),
                "a duplicate unindexed Mob must still fail closed even with a lifecycle proof");
        assertFalse(SourceGrayboxEntityAdmission.rejects(true, true, false, VERIFIED),
                "the existing strict verified-Mob handoff must remain admissible");
    }
}
