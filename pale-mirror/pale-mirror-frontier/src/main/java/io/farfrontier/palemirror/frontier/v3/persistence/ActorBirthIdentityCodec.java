package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.ActorBirthIdentity;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Required prefix for birth events; old payloads without a declaration are unsupported. */
final class ActorBirthIdentityCodec {
    private static final int FORMAT = 1;
    private ActorBirthIdentityCodec() { }
    static void write(DataOutputStream output, ActorBirthIdentity birth) throws IOException {
        output.writeInt(FORMAT);
        FrontierWorldPayloadCodecs.writeSubject(output, birth.actorId());
        FrontierWorldPayloadCodecs.writeString(output, birth.kind().wire());
    }
    static ActorBirthIdentity read(DataInputStream input) throws IOException {
        if (input.readInt() != FORMAT) throw new IOException("unsupported actor birth declaration format");
        return new ActorBirthIdentity(FrontierWorldPayloadCodecs.readSubject(input).value(),
                ActorBirthIdentity.Kind.fromWire(FrontierWorldPayloadCodecs.readString(input)));
    }
}
