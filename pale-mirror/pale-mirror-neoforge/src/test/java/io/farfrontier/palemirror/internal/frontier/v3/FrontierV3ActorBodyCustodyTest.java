package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorKind;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ActorBodyCustodyTest {
    @Test void unstartedCancellationNeedsExactRetainedEvidenceAndNoPendingInsertion() {
        var actor = new SubjectId("resident:body-absence");
        var body = new ActorBodyId(actor, 2L);
        var id = UUID.fromString("a520b5ba-7c35-36b7-845c-689ed5f4c697");
        var identity = new FrontierV3ActorFirstAdmission.Identity(actor, ActorKind.RESIDENT, id);
        var never = Optional.of(FrontierV3ActorFirstAdmission.neverCreated(identity));
        assertTrue(FrontierV3ActorBodyCustody.unstartedEvidence(body, ActorKind.RESIDENT, id, never, Optional.empty(), false));
        assertFalse(FrontierV3ActorBodyCustody.unstartedEvidence(body, ActorKind.RESIDENT, id, Optional.empty(), Optional.empty(), false));
        assertFalse(FrontierV3ActorBodyCustody.unstartedEvidence(body, ActorKind.RESIDENT, id, never, Optional.empty(), true));
        assertFalse(FrontierV3ActorBodyCustody.unstartedEvidence(body, ActorKind.BIOFORM, id, never, Optional.empty(), false));
        assertFalse(FrontierV3ActorBodyCustody.unstartedEvidence(body, ActorKind.RESIDENT, UUID.randomUUID(), never, Optional.empty(), false));
        var declaration = new FrontierV3ActorCarrierComposition.Declaration(actor, ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, id,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 0L, 1L);
        var prior = Optional.of(new FrontierV3AmbientCarrierLedger.Carrier(declaration, 7L, 7L));
        assertTrue(FrontierV3ActorBodyCustody.unstartedEvidence(body, ActorKind.RESIDENT, id, Optional.empty(), prior, false));
        assertFalse(FrontierV3ActorBodyCustody.unstartedEvidence(new ActorBodyId(actor, 1L), ActorKind.RESIDENT, id,
                Optional.empty(), prior, false));
        assertFalse(FrontierV3ActorBodyCustody.unstartedEvidence(body, ActorKind.RESIDENT, id, Optional.empty(), prior, true));
    }
}
