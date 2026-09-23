package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

/** Shared payload/snapshot encoding, present only in the new bound-strike schemas. */
final class PhysicalSceneBindingCodec {
    private PhysicalSceneBindingCodec() { }
    static void write(DataOutputStream output, PhysicalIntentRoleBinding roles) throws IOException {
        if (roles.scene().isEmpty()) return;
        var scene = roles.scene().orElseThrow();
        FrontierWorldPayloadCodecs.writeString(output, scene.leaseId().value());
        output.writeLong(scene.revision());
    }
    static Optional<PhysicalSceneBinding> read(DataInputStream input, PhysicalIntentRoleSchema schema) throws IOException {
        return schema.kind() == PhysicalIntentKind.SCENE_STRIKE
                ? Optional.of(new PhysicalSceneBinding(new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)), input.readLong()))
                : Optional.empty();
    }
}
