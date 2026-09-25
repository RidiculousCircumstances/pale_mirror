package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorBirthIdentityCodecTest {
    @Test void bothBirthFamiliesRoundTripTheirRequiredDeclaration() throws Exception {
        var resident = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:birth-codec"), 91L)
                .initialState().humanPopulation().residents().values().iterator().next();
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        var human = new ResidentBorn(new SubjectId("job:resident-birth-codec"), resident, new BlockPosition(0, 64, 0),
                new ActorBirthIdentity(resident.id(), ActorBirthIdentity.Kind.RESIDENT));
        var hive = new HiveGrowthCompleted(new SubjectId("job:birth"),
                new ActorBirthIdentity(new SubjectId("bioform:new"), ActorBirthIdentity.Kind.BIOFORM));
        for (var birth : java.util.List.of(human, hive)) {
            assertEquals(birth, codecs.decode(birth.type(), codecs.encode(birth)));
            assertTrue(birth.requiresDurableBeforeEffect());
            assertTrue(birth.actorBirth().isPresent());
            byte[] encoded = codecs.encode(birth);
            encoded[3] = 99;
            assertThrows(RuntimeException.class, () -> codecs.decode(birth.type(), encoded));
            assertThrows(RuntimeException.class, () -> codecs.decode(birth.type(), new byte[0]));
        }
        assertThrows(IllegalArgumentException.class, () -> new ResidentBorn(human.jobId(), resident, human.position(), hive.birth()));
        assertThrows(IllegalArgumentException.class, () -> new ResidentBorn(human.jobId(), resident, human.position(),
                new ActorBirthIdentity(new SubjectId("resident:other"), ActorBirthIdentity.Kind.RESIDENT)));
        assertThrows(IllegalArgumentException.class, () -> new ResidentBorn(new SubjectId("job:foreign"), resident,
                human.position(), human.birth()));
        byte[] currentHumanPayload = codecs.encode(human);
        var input = new java.io.DataInputStream(new java.io.ByteArrayInputStream(currentHumanPayload));
        ActorBirthIdentityCodec.read(input);
        int markerOffset = currentHumanPayload.length - input.available();
        int jobEnvelopeBytes = 4 + 2 + human.jobId().value().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        byte[] oldHumanPayload = new byte[currentHumanPayload.length - jobEnvelopeBytes];
        System.arraycopy(currentHumanPayload, 0, oldHumanPayload, 0, markerOffset);
        System.arraycopy(currentHumanPayload, markerOffset + jobEnvelopeBytes, oldHumanPayload, markerOffset,
                oldHumanPayload.length - markerOffset);
        assertThrows(RuntimeException.class, () -> codecs.decode(human.type(), oldHumanPayload));
        assertThrows(IllegalArgumentException.class, () -> new HiveGrowthCompleted(hive.jobId(), human.birth()));
    }
    @Test void oldHivePayloadWithoutBirthDeclarationIsRejected() {
        byte[] legacy = FrontierWorldPayloadCodecs.encodeProduction(output ->
                FrontierWorldPayloadCodecs.writeSubject(output, new SubjectId("job:old")));
        assertThrows(RuntimeException.class, () -> FrontierWorldRuntimeDefinition.payloadCodecs()
                .decode("frontier.hive_growth_completed", legacy));
        assertThrows(IllegalArgumentException.class, () -> ActorBirthIdentity.Kind.fromWire("unknown"));
    }
}
