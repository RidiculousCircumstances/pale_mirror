package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontierV3PilotDemandHandshakeCommandTest {
    private static final FrontierV3PilotDemandHandshakeCommand.TicketState READY_TICKET =
            new FrontierV3PilotDemandHandshakeCommand.TicketState(true, true, 1, true);

    @Test
    void admitsOnlyAnObservedDestinationWithItsExistingProviderCandidateAndDemandFacts() {
        assertEquals(FrontierV3PilotDemandHandshakeCommand.Reason.ADMITTED,
                FrontierV3PilotDemandHandshakeCommand.reason(true, READY_TICKET, Optional.of("projection-snapshot"),
                        Optional.of(1), true, true));
    }

    @Test
    void reportsTheFirstUnavailableServerFactWithoutSupplyingAProviderOrCandidateDefault() {
        assertEquals(FrontierV3PilotDemandHandshakeCommand.Reason.NO_PROVIDER,
                FrontierV3PilotDemandHandshakeCommand.reason(true, READY_TICKET, Optional.empty(), Optional.empty(), true, true));
        assertEquals(FrontierV3PilotDemandHandshakeCommand.Reason.NO_CANDIDATE,
                FrontierV3PilotDemandHandshakeCommand.reason(true, READY_TICKET, Optional.of("projection-snapshot"),
                        Optional.empty(), true, true));
        assertEquals(FrontierV3PilotDemandHandshakeCommand.Reason.NO_DEMAND,
                FrontierV3PilotDemandHandshakeCommand.reason(true, READY_TICKET, Optional.of("projection-snapshot"),
                        Optional.of(1), true, false));
    }

    @Test
    void rejectsClientLocalVisibilityWhenTheServerHasNotObservedTheDestination() {
        assertEquals(FrontierV3PilotDemandHandshakeCommand.Reason.NO_DEMAND,
                FrontierV3PilotDemandHandshakeCommand.reason(false, READY_TICKET, Optional.of("projection-snapshot"),
                        Optional.of(1), true, true));
    }
}
