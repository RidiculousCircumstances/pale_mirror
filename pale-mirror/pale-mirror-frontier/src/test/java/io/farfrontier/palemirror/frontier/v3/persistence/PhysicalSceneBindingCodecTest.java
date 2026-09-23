package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalSceneBindingCodecTest {
    @Test void payloadAndSnapshotRetainExactSceneAndRevision() throws Exception {
        for (boolean assault : new boolean[]{false, true}) {
            PhysicalIntent intent = intent(assault);
            var payload = new ByteArrayOutputStream();
            PhysicalIntentPayloadCodec.write(new DataOutputStream(payload), intent);
            assertEquals(intent, PhysicalIntentPayloadCodec.read(new DataInputStream(new ByteArrayInputStream(payload.toByteArray()))));
            var snapshot = new ByteArrayOutputStream();
            PhysicalIntentStateCodec.write(new DataOutputStream(snapshot), Map.of(intent.id(), intent));
            assertEquals(Map.of(intent.id(), intent), PhysicalIntentStateCodec.read(
                    new DataInputStream(new ByteArrayInputStream(snapshot.toByteArray())), true));
            assertEquals(intent.roles().scene(), intent.withStatus(PhysicalIntentStatus.RUNNING, Optional.empty()).roles().scene());
        }
    }

    @Test void missingBindingAndRetiredUnboundSchemasAreRejected() throws Exception {
        var intent = intent(false);
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentRoleBinding.decode(intent.roles().schema(), intent.roles().namedRoles()));
        assertThrows(IllegalArgumentException.class, () -> new PhysicalSceneBinding(new SceneLeaseId("lease:test"), -1));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentRoleSchema.fromWire(7));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentRoleSchema.fromWire(8));
        var output = new ByteArrayOutputStream();
        PhysicalIntentPayloadCodec.write(new DataOutputStream(output), intent);
        byte[] bytes = output.toByteArray();
        var input = new DataInputStream(new ByteArrayInputStream(bytes));
        FrontierWorldPayloadCodecs.readString(input);
        input.readUnsignedByte(); input.readUnsignedByte();
        FrontierWorldPayloadCodecs.readSubject(input);
        int schemaOffset = bytes.length - input.available();
        bytes[schemaOffset] = 7;
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentPayloadCodec.read(new DataInputStream(new ByteArrayInputStream(bytes))));
    }

    private static PhysicalIntent intent(boolean assault) {
        var attacker = new SubjectId("actor:a"); var target = new SubjectId("actor:b");
        var lease = new SceneLeaseId("lease:exact");
        return new PhysicalIntent(new PhysicalIntentId("intent:opaque"), PhysicalIntentKind.SCENE_STRIKE, PhysicalIntentStatus.PREPARED,
                new SubjectId("operation:opaque"), assault
                ? PhysicalIntentRoleBinding.assaultSceneStrike(attacker, target, lease, 73)
                : PhysicalIntentRoleBinding.routeSceneStrike(attacker, target, lease, 73),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, PhysicalPostcondition.SCENE_STRIKE_OBSERVED,
                assault ? PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT : PhysicalIntentLifecycleOwner.ROUTE_ENGAGEMENT);
    }
}
