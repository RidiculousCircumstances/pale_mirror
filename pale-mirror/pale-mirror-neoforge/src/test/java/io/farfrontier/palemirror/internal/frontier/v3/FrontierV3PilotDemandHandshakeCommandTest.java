package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCandidate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3PilotDemandHandshakeCommandTest {
    private static final FrontierV3PilotDemandHandshakeCommand.TicketState READY_TICKET =
            new FrontierV3PilotDemandHandshakeCommand.TicketState(true, true, 1, true);
    private static final SubjectId ASSAULT = new SubjectId("assault:requested");
    /** Northwatch settlement anchor; it is deliberately not the player travel coordinate. */
    private static final BlockPosition HANDOFF = new BlockPosition(-360, 64, -340);
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000035");
    private static final FrontierV3PilotDemandHandshakeCommand.Correlation CORRELATION = new FrontierV3PilotDemandHandshakeCommand.Correlation(
            "00000000-0000-0000-0000-000000000031", 1, "00000000-0000-0000-0000-000000000032");

    @Test
    void admitsOnlyAnObservedDestinationWithItsExistingProviderCandidateAndDemandFacts() {
        assertEquals(FrontierV3PilotDemandHandshakeCommand.Reason.ADMITTED,
                FrontierV3PilotDemandHandshakeCommand.reason(true, READY_TICKET, Optional.of("projection-snapshot"),
                        Optional.of(1), Optional.of(HANDOFF), true, true));
    }

    @Test
    void reportsTheFirstUnavailableServerFactWithoutSupplyingAProviderOrCandidateDefault() {
        assertEquals(FrontierV3PilotDemandHandshakeCommand.Reason.NO_PROVIDER,
                FrontierV3PilotDemandHandshakeCommand.reason(true, READY_TICKET, Optional.empty(), Optional.empty(), Optional.empty(), true, true));
        assertEquals(FrontierV3PilotDemandHandshakeCommand.Reason.NO_CANDIDATE,
                FrontierV3PilotDemandHandshakeCommand.reason(true, READY_TICKET, Optional.of("projection-snapshot"),
                        Optional.empty(), Optional.empty(), true, true));
        assertEquals(FrontierV3PilotDemandHandshakeCommand.Reason.NO_DEMAND,
                FrontierV3PilotDemandHandshakeCommand.reason(true, READY_TICKET, Optional.of("projection-snapshot"),
                        Optional.of(1), Optional.of(HANDOFF), true, false));
    }

    @Test
    void rejectsClientLocalVisibilityWhenTheServerHasNotObservedTheDestination() {
        assertEquals(FrontierV3PilotDemandHandshakeCommand.Reason.NO_DEMAND,
                FrontierV3PilotDemandHandshakeCommand.reason(false, READY_TICKET, Optional.of("projection-snapshot"),
                        Optional.of(1), Optional.of(HANDOFF), true, true));
    }

    @Test
    void producerSelectsOnlyTheRequestedProviderBackedCandidateThenObservesItsHandoff() {
        AtomicReference<BlockPosition> observedAt = new AtomicReference<>();
        FrontierV3PilotSceneDemandSnapshot snapshot = FrontierV3PilotSceneDemandSnapshot.fromProviderCandidates(
                Optional.of("projection-snapshot"), List.of(candidate(new SubjectId("assault:other"), new BlockPosition(4, 64, 8)), candidate(ASSAULT, HANDOFF)), ASSAULT,
                handoff -> { observedAt.set(handoff); return new FrontierV3SceneDemand.Snapshot(true, Set.of(PLAYER)); });
        assertEquals(HANDOFF, observedAt.get());
        assertEquals(Optional.of(HANDOFF), snapshot.handoffPosition());
        assertEquals(1, snapshot.exactCandidateCount().orElseThrow());
        FrontierV3PilotDemandHandshakeCommand.Receipt receipt = FrontierV3PilotDemandHandshakeCommand.Receipt.from(armed(), Optional.of(PLAYER), Optional.of(new BlockPos(-360, 65, -352)),
                true, READY_TICKET, snapshot);
        assertEquals(FrontierV3PilotDemandHandshakeCommand.Reason.ADMITTED, receipt.reason());
        assertTrue(receipt.json().contains("\"candidateHandoff\":{\"x\":-360,\"y\":64,\"z\":-340}"));
        assertTrue(receipt.json().contains("\"serverPlayerPosition\":{\"x\":-360,\"y\":65,\"z\":-352}"));
    }

    @Test
    void producerPreservesMissingStaleAndZeroCandidateFactsWithoutDemandFallback() {
        AtomicReference<BlockPosition> observedAt = new AtomicReference<>();
        FrontierV3PilotSceneDemandSnapshot missing = FrontierV3PilotSceneDemandSnapshot.fromProviderCandidates(
                Optional.empty(), List.of(candidate(ASSAULT, HANDOFF)), ASSAULT,
                handoff -> { observedAt.set(handoff); return new FrontierV3SceneDemand.Snapshot(true, Set.of(PLAYER)); });
        assertTrue(missing.providerIdentity().isEmpty()); assertTrue(missing.exactCandidateCount().isEmpty()); assertTrue(missing.handoffPosition().isEmpty());
        FrontierV3PilotSceneDemandSnapshot zero = FrontierV3PilotSceneDemandSnapshot.fromProviderCandidates(
                Optional.of("stale-provider"), List.of(candidate(new SubjectId("assault:other"), HANDOFF)), ASSAULT,
                handoff -> { observedAt.set(handoff); return new FrontierV3SceneDemand.Snapshot(true, Set.of(PLAYER)); });
        assertEquals(0, zero.exactCandidateCount().orElseThrow()); assertTrue(zero.handoffPosition().isEmpty()); assertNull(observedAt.get());
        assertEquals(FrontierV3PilotDemandHandshakeCommand.Reason.NO_CANDIDATE,
                FrontierV3PilotDemandHandshakeCommand.reason(true, READY_TICKET, zero.providerIdentity(), Optional.of(0), zero.handoffPosition(), false, false));
        assertFalse(zero.demandChunkLoaded());
    }

    @Test
    void armTransferPostDistanceAndCleanupUseOneReceiptCustodyComposition() {
        FrontierV3PilotDemandHandshakeCommand.ArmedReceipts receipts = new FrontierV3PilotDemandHandshakeCommand.ArmedReceipts();
        ResourceKey<Level> destination = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("pale_mirror:frontier_graybox"));
        ResourceKey<Level> wrongDestination = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        FrontierV3PilotDemandHandshakeCommand.ArmedReceipt armed = armed();
        FrontierV3PilotDemandHandshakeCommand.Candidate candidate = new FrontierV3PilotDemandHandshakeCommand.Candidate("projection-snapshot", HANDOFF);
        assertTrue(receipts.arm(PLAYER, armed));
        assertFalse(receipts.arm(PLAYER, armed));
        assertFalse(receipts.observeTransfer(PLAYER, wrongDestination));
        assertTrue(receipts.observeAfterDistance(PLAYER, ignored -> Optional.empty()).isEmpty());
        assertTrue(receipts.observeTransfer(PLAYER, destination));
        assertFalse(receipts.observeTransfer(PLAYER, destination));
        assertTrue(receipts.pending(PLAYER));
        FrontierV3PilotDemandHandshakeCommand.Receipt notReady = receipt(armed, FrontierV3PilotDemandHandshakeCommand.TicketState.absent());
        assertTrue(receipts.observeAfterDistance(PLAYER, pending -> Optional.of(new FrontierV3PilotDemandHandshakeCommand.PostDistanceObservation(notReady, Optional.of(candidate)))).isEmpty());
        assertTrue(receipts.pending(PLAYER));
        FrontierV3PilotDemandHandshakeCommand.Receipt admitted = receipt(armed, READY_TICKET);
        assertEquals(Optional.of(admitted), receipts.observeAfterDistance(PLAYER, pending -> {
            assertEquals(Optional.of(candidate), pending.candidate());
            return Optional.of(new FrontierV3PilotDemandHandshakeCommand.PostDistanceObservation(admitted, Optional.of(candidate)));
        }));
        assertFalse(receipts.pending(PLAYER));
    }

    @Test
    void armedReceiptIsRemovedOnTheSamePilotCleanupBoundariesAsTheTransferOwner() {
        FrontierV3PilotDemandHandshakeCommand.ArmedReceipts receipts = new FrontierV3PilotDemandHandshakeCommand.ArmedReceipts();
        ResourceKey<Level> destination = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("pale_mirror:frontier_graybox"));
        FrontierV3PilotDemandHandshakeCommand.ArmedReceipt armed = armed();
        assertTrue(receipts.arm(PLAYER, armed)); assertTrue(receipts.observeTransfer(PLAYER, destination)); receipts.forget(PLAYER);
        assertFalse(receipts.pending(PLAYER));
        assertTrue(receipts.observeAfterDistance(PLAYER, ignored -> Optional.empty()).isEmpty());
        assertTrue(receipts.arm(PLAYER, armed)); receipts.clear();
        assertFalse(receipts.pending(PLAYER));
    }

    private static FrontierV3PilotDemandHandshakeCommand.ArmedReceipt armed() {
        ResourceKey<Level> destination = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("pale_mirror:frontier_graybox"));
        return new FrontierV3PilotDemandHandshakeCommand.ArmedReceipt(CORRELATION, "settlement-assault-visit", ASSAULT.value(), destination.location().toString(), destination, new BlockPos(-360, 65, -352));
    }

    private static FrontierV3PilotDemandHandshakeCommand.Receipt receipt(FrontierV3PilotDemandHandshakeCommand.ArmedReceipt armed,
                                                                            FrontierV3PilotDemandHandshakeCommand.TicketState ticket) {
        FrontierV3PilotSceneDemandSnapshot snapshot = new FrontierV3PilotSceneDemandSnapshot(Optional.of("projection-snapshot"), java.util.OptionalInt.of(1), Optional.of(HANDOFF), true, Set.of(PLAYER));
        return FrontierV3PilotDemandHandshakeCommand.Receipt.from(armed, Optional.of(PLAYER), Optional.of(armed.travelAnchor()), true, ticket, snapshot);
    }

    private static SettlementAssaultSceneCandidate candidate(SubjectId assault, BlockPosition handoff) {
        return new SettlementAssaultSceneCandidate(assault, new SubjectId("settlement:1"), handoff,
                Map.of(new SubjectId("bioform:1"), handoff.offset(1, 0, 0), new SubjectId("resident:1"), handoff.offset(2, 0, 0)));
    }
}
