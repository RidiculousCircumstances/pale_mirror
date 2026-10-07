package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SceneCausePersistenceTest {
    @Test void harvestSceneWalRetainsDeclaredSiteAndRejectsItsOldJobOnlyMarker() {
        WorldId world = new WorldId("frontier:harvest-scene-owner-wire");
        SubjectId actor = new SubjectId("resident:harvest-scene-owner-wire");
        ResourceSiteHarvestSceneCause cause = new ResourceSiteHarvestSceneCause(
                new SubjectId("site:harvest-scene-owner-wire"),
                new SubjectId("job:site-harvest-owner-wire"));
        BlockPosition support = new BlockPosition(8, 64, 8);
        SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:harvest-scene-owner-wire"), world,
                cause, support, new SimInstant(10L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(actor, SceneLease.deterministicEntityId(world, actor))), Set.of(), Optional.empty());
        ResourceSiteHarvestSceneLeasePrepared payload = new ResourceSiteHarvestSceneLeasePrepared(lease);
        byte[] encoded = FrontierWorldRuntimeDefinition.payloadCodecs().encode(payload);
        assertEquals(payload, FrontierWorldRuntimeDefinition.payloadCodecs().decode(payload.type(), encoded));
        byte[] oldMarker = encoded.clone(); oldMarker[1] = (byte) 0xfd;
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldRuntimeDefinition.payloadCodecs()
                .decode(payload.type(), oldMarker), "the old job-only WAL body cannot be reinterpreted as a site declaration");
    }

    @Test void preTypedLogisticsLeaseWalIsRejectedForFreshWorlds() {
        WorldId world = new WorldId("frontier:legacy-scene-wal");
        SubjectId operation = new SubjectId("operation:legacy-scene-wal");
        SubjectId cargo = new SubjectId("cargo:legacy-scene-wal");
        SubjectId actor = new SubjectId("resident:legacy-scene-wal");
        BlockPosition support = new BlockPosition(8, 64, 8);
        byte[] legacy = FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeString(output, "lease:legacy-scene-wal");
            FrontierWorldPayloadCodecs.writeString(output, world.value());
            FrontierWorldPayloadCodecs.writeSubject(output, operation); FrontierWorldPayloadCodecs.writeSubject(output, cargo);
            output.writeBoolean(false);
            for (BlockPosition position : List.of(new BlockPosition(8, 64, 7), new BlockPosition(8, 64, 6))) {
                output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z());
            }
            output.writeLong(10L); output.writeLong(1L); output.writeByte(SceneLeaseStatus.PREPARED.wireTag()); output.writeByte(1);
            FrontierWorldPayloadCodecs.writeSubject(output, actor);
            FrontierWorldPayloadCodecs.writeString(output, SceneLease.deterministicEntityId(world, actor).toString());
            output.writeInt(support.x()); output.writeInt(support.y()); output.writeInt(support.z());
            output.writeByte(0);
        });

        assertThrows(IllegalArgumentException.class, () -> FrontierWorldRuntimeDefinition.payloadCodecs()
                .decode("frontier.scene_lease_prepared", legacy));
    }


    @Test void engineeringWorkSceneWalRetainsProjectAndExactWorkCursor() {
        WorldId world = new WorldId("frontier:engineering-scene-wal");
        SubjectId actor = new SubjectId("resident:engineering-scene-wal");
        SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:engineering-wal"), world,
                new EngineeringWorkSceneCause(new SubjectId("construction:engineering-wal"), 3),
                new BlockPosition(8, 64, 8), new SimInstant(10L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(actor, SceneLease.deterministicEntityId(world, actor))), Set.of(), Optional.empty());

        EngineeringWorkSceneLeasePrepared payload = new EngineeringWorkSceneLeasePrepared(lease);
        EngineeringWorkSceneLeasePrepared decoded = assertInstanceOf(EngineeringWorkSceneLeasePrepared.class,
                FrontierWorldRuntimeDefinition.payloadCodecs().decode(payload.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(payload)));
        EngineeringWorkSceneCause retained = assertInstanceOf(EngineeringWorkSceneCause.class, decoded.lease().cause());
        assertEquals(new SubjectId("construction:engineering-wal"), retained.projectId());
        assertEquals(3, retained.workCellIndex());
    }


}
