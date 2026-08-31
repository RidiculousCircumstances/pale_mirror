package io.farfrontier.palemirror.frontier.v3.model;
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
                Map.of(actor, new BlockPosition(8, 64, 8)), Set.of(), Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> FrontierWorldRuntimeDefinition.payloadCodecs().encode(new SceneLeasePrepared(assault)));
        SettlementAssaultSceneLeasePrepared payload = new SettlementAssaultSceneLeasePrepared(assault);
        assertEquals(payload, FrontierWorldRuntimeDefinition.payloadCodecs().decode(payload.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(payload)));
    }

    @Test void currentStateRejectsATypeWhoseOwnerValidatorDoesNotExistYet() {
        FrontierWorldState state = FrontierDevelopmentScenarios.hotSceneStrikeState(new WorldId("frontier:scene-cause-owner"), 91L);
        SubjectId actor = state.bootstrap().hive().bioforms().getFirst().id();
        Map<SubjectId, BlockPosition> positions = new LinkedHashMap<>();
        positions.put(actor, state.actorLocations().get(actor).position());
        SceneLease assault = SceneLease.forCause(new SceneLeaseId("lease:assault-owner"), state.bootstrap().worldId(),
                new SettlementAssaultSceneCause(new SubjectId("assault:owner"), new SubjectId("settlement:northwatch")),
                state.actorLocations().get(actor).position(), new SimInstant(10L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(actor, SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor))), positions, Set.of(), Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> state.prepareSceneLease(assault));
    }
}
