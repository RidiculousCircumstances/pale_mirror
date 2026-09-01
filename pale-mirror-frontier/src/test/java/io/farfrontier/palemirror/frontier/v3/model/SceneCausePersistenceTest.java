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

    @Test void snapshotRoundTripRetainsTheExplicitLogisticsCause() {
        FrontierWorldState before = FrontierDevelopmentScenarios.hotSceneStrikeState(new WorldId("frontier:scene-cause-snapshot"), 91L);
        RouteOperation operation = before.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();
        SceneLease lease = FrontierTestSceneLeases.exact(before, new SceneLeaseId("lease:scene-cause-snapshot"), operation.id(), operation.cargoId(),
                operation.currentPosition(), new SimInstant(2_600L), 1L, Optional.empty(), operation.participantIds());
        FrontierWorldState roundTripped = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(before.prepareSceneLease(lease)));

        SceneLease retained = roundTripped.sceneLeases().get(lease.id());
        LogisticsSceneCause cause = assertInstanceOf(LogisticsSceneCause.class, retained.cause());
        assertEquals(operation.id(), cause.operationId());
        assertEquals(operation.cargoId(), cause.cargoId());
        assertEquals(FrontierSceneBehaviors.logistics(lease).cargoPosition(), cause.cargoPosition());
    }

    @Test void legacyLeasePayloadRefusesAnAssaultCause() {
        WorldId world = new WorldId("frontier:scene-cause-wal");
        SubjectId actor = new SubjectId("bioform:scene-cause-wal");
        SceneLease assault = SceneLease.forCause(new SceneLeaseId("lease:assault-wal"), world,
                new SettlementAssaultSceneCause(new SubjectId("assault:scene-cause-wal"), new SubjectId("settlement:northwatch")),
                new BlockPosition(8, 64, 8), new SimInstant(10L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(actor, SceneLease.deterministicEntityId(world, actor))),
                Map.of(actor, BodyPosition.aboveLegacySupport(new BlockPosition(8, 64, 8))), Set.of(), Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> FrontierWorldRuntimeDefinition.payloadCodecs().encode(new SceneLeasePrepared(assault)));
        SettlementAssaultSceneLeasePrepared payload = new SettlementAssaultSceneLeasePrepared(assault);
        assertEquals(payload, FrontierWorldRuntimeDefinition.payloadCodecs().decode(payload.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(payload)));
    }

    @Test void engineeringWorkSceneWalRetainsProjectAndExactWorkCursor() {
        WorldId world = new WorldId("frontier:engineering-scene-wal");
        SubjectId actor = new SubjectId("resident:engineering-scene-wal");
        SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:engineering-wal"), world,
                new EngineeringWorkSceneCause(new SubjectId("construction:engineering-wal"), 3),
                new BlockPosition(8, 64, 8), new SimInstant(10L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(actor, SceneLease.deterministicEntityId(world, actor))),
                Map.of(actor, BodyPosition.aboveLegacySupport(new BlockPosition(7, 64, 7))), Set.of(), Optional.empty());

        EngineeringWorkSceneLeasePrepared payload = new EngineeringWorkSceneLeasePrepared(lease);
        EngineeringWorkSceneLeasePrepared decoded = assertInstanceOf(EngineeringWorkSceneLeasePrepared.class,
                FrontierWorldRuntimeDefinition.payloadCodecs().decode(payload.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(payload)));
        EngineeringWorkSceneCause retained = assertInstanceOf(EngineeringWorkSceneCause.class, decoded.lease().cause());
        assertEquals(new SubjectId("construction:engineering-wal"), retained.projectId());
        assertEquals(3, retained.workCellIndex());
    }

    @Test void currentStateRejectsATypeWhoseOwnerValidatorDoesNotExistYet() {
        FrontierWorldState state = FrontierDevelopmentScenarios.hotSceneStrikeState(new WorldId("frontier:scene-cause-owner"), 91L);
        SubjectId actor = state.bootstrap().hive().bioforms().getFirst().id();
        Map<SubjectId, BodyPosition> positions = new LinkedHashMap<>();
        positions.put(actor, state.actorLocations().get(actor).body());
        SceneLease assault = SceneLease.forCause(new SceneLeaseId("lease:assault-owner"), state.bootstrap().worldId(),
                new SettlementAssaultSceneCause(new SubjectId("assault:owner"), new SubjectId("settlement:northwatch")),
                FrontierTestPositions.supportOf(state.actorLocations().get(actor)), new SimInstant(10L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(actor, SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor))),
                positions, Set.of(), Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> state.prepareSceneLease(assault));
    }
}
