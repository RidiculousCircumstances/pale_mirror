package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonParser;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3CargoRetirementDiagnosticTest {
    @Test void successorSceneCannotHideAnOlderPendingCarrierRetirement() {
        var configuration = FrontierV3FixtureCatalog.routeSceneReturnConfiguration(new WorldId("frontier:retirement-diagnostic"), 41L);
        var state = configuration.initialState();
        var operations = state.operations().values().stream().filter(value -> value.settlementId().equals(new SubjectId("settlement:1"))).toList();
        assertEquals(1, operations.size());
        var operation = operations.getFirst();
        var checkpoint = FrontierEngines.create(configuration).checkpoint();
        var leaseId = new SceneLeaseId("lease:diagnostic-successor");
        var members = operation.participantIds().stream().map(actor -> new SceneMember(actor,
                SceneLease.deterministicEntityId(configuration.worldId(), actor))).toList();
        var travel = operation.activeTravel().orElseThrow();
        var lease = SceneLease.atExactPositions(leaseId, configuration.worldId(), operation.id(), operation.cargoId(),
                operation.currentPosition(), travel.cargoAnchor().surface().support(), configuration.initialInstant(),
                checkpoint.revision().value(), SceneLeaseStatus.PREPARED, Optional.empty(), members, travel.formation());
        var oldLease = new SceneLeaseId("lease:diagnostic-retired");
        var proof = new FencedRecoveryTombstone(FrontierSceneLeaseStateSupport.cargoRecoveryBindingId(operation.cargoId()),
                FencedRecoveryAsset.CARGO, FrontierSceneLeaseStateSupport.recoveryOwner(oldLease), 1, 1,
                FencedRecoveryDisposition.REJECT_STALE, "confirmed");
        var pending = new CargoProjectionRetirement(configuration.worldId(), oldLease, operation.cargoId(),
                CargoCarrierIdentity.id(configuration.worldId(), oldLease, operation.cargoId()), proof,
                CargoProjectionRetirement.Disposition.REMOVE_PROJECTION);
        var recovery = state.fencedRecovery();
        state = state.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(new FencedRecoveryState(
                recovery.current(), recovery.tombstones(), new CargoProjectionRetirements(Map.of(pending.entityId(), pending)))));
        state = state.prepareSceneLease(lease);
        var before = state;
        var json = JsonParser.parseString(FrontierV3DiagnosticJson.render("scene", operation.id().value(), checkpoint, state, Optional.empty())
                .substring(FrontierV3DiagnosticJson.PREFIX.length())).getAsJsonObject();
        assertFalse(json.get("cargoCleanupPending").getAsBoolean(), "the new scene itself has not retired");
        assertEquals(1, json.get("cargoPendingRetirements").getAsInt(), "older exact entity obligation remains visible");
        assertEquals(before, state);
    }
}
