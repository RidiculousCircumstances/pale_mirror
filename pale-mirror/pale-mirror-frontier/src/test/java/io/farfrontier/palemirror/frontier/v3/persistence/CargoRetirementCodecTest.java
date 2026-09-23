package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CargoRetirementCodecTest {
    @Test void pendingCleanupSurvivesPersistenceWithoutHistoricalTombstone() throws IOException {
        var world = new WorldId("frontier:cleanup");
        var scene = new SceneLeaseId("lease:cleanup");
        var cargo = new SubjectId("cargo:cleanup");
        var proof = new FencedRecoveryTombstone(FrontierSceneLeaseStateSupport.cargoRecoveryBindingId(cargo),
                FencedRecoveryAsset.CARGO, FrontierSceneLeaseStateSupport.recoveryOwner(scene), 7, 3,
                FencedRecoveryDisposition.REJECT_STALE, "confirmed");
        var obligation = new CargoProjectionRetirement(world, scene, cargo, CargoCarrierIdentity.id(world, scene, cargo),
                proof, CargoProjectionRetirement.Disposition.REMOVE_PROJECTION);
        assertThrows(IllegalArgumentException.class, () -> FencedRecoveryState.empty().retainCargoRetirement(obligation));
        var state = new FencedRecoveryState(Map.of(), Map.of(proof.bindingId(), proof))
                .retainCargoRetirement(obligation).compactTombstones(Set.of(proof.bindingId()));
        var bytes = new ByteArrayOutputStream();
        FencedRecoveryStateCodec.write(new DataOutputStream(bytes), state);
        var encoded = bytes.toByteArray();
        var input = new DataInputStream(new ByteArrayInputStream(encoded));
        assertEquals(state, FencedRecoveryStateCodec.read(input));
        assertEquals(0, input.available());
        encoded[encoded.length - 1] = 99;
        assertThrows(IllegalArgumentException.class, () -> FencedRecoveryStateCodec.read(
                new DataInputStream(new ByteArrayInputStream(encoded))));
    }
}
