package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Tests real family filtering/service cursors, not native scene effects or domain admission. */
class FrontierV3SceneTurnSchedulerTest {
    @Test void everyRegisteredFamilyServicesBDespiteWaitingAAndExcludesTerminalConflicts() {
        // Null is an isolated runtime key: this selector never calls the runtime. No domain
        // world or scene result is fabricated and presented as an integration acceptance.
        FrontierV3SceneTurnScheduler.forget(null);
        try {
            var inventory = new ArrayList<SceneLease>();
            var causes = causes();
            assertEquals(EnumSet.allOf(SceneCauseKind.class), causes.stream().map(SceneCause::kind)
                    .collect(java.util.stream.Collectors.toSet()), "new families must join the coverage");
            for (var cause : causes) {
                inventory.add(lease(cause, "a", SceneLeaseStatus.UNKNOWN_AFTER_RESTART));
                inventory.add(lease(cause, "b", SceneLeaseStatus.HOT));
                inventory.add(lease(cause, "0-conflict", SceneLeaseStatus.CONFLICT));
                inventory.add(lease(cause, "0-closed", SceneLeaseStatus.CLOSED));
            }
            Collections.reverse(inventory);
            for (var cause : causes) {
                var visited = new ArrayList<String>();
                for (int turn = 0; turn < 4; turn++) {
                    int before = visited.size();
                    assertTrue(FrontierV3SceneTurnScheduler.runInventory(null, inventory, cause.kind(), scene -> {
                        assertEquals(cause.kind(), scene.cause().kind());
                        visited.add(scene.id().value()); // A deliberately makes no progress.
                    }, () -> false));
                    assertEquals(before + 1, visited.size(), "exactly one active callback per turn");
                }
                assertEquals(List.of(id(cause, "a"), id(cause, "b"), id(cause, "a"), id(cause, "b")), visited);
            }
            FrontierV3SceneTurnScheduler.forget(null);
            var visited = new ArrayList<String>();
            FrontierV3SceneTurnScheduler.runInventory(null, inventory, SceneCauseKind.LOGISTICS,
                    scene -> visited.add(scene.id().value()), () -> false);
            assertEquals(List.of(id(causes.getFirst(), "a")), visited, "forget resets only ephemeral service order");
        } finally { FrontierV3SceneTurnScheduler.forget(null); }
    }

    private static List<SceneCause> causes() {
        return List.of(new LogisticsSceneCause(new SubjectId("operation:test"), new SubjectId("cargo:test"), Optional.empty(), new BlockPosition(0, 0, 0), CargoProjectionRetirement.Disposition.REMOVE_PROJECTION),
                new SettlementAssaultSceneCause(new SubjectId("assault:test"), new SubjectId("settlement:1")),
                new EngineeringWorkSceneCause(new SubjectId("project:test"), 0),
                new MedicalTreatmentSceneCause(new SubjectId("medical:test")),
                new ResourceSiteHarvestSceneCause(new SubjectId("site:harvest-test"), new SubjectId("job:site-harvest-test")),
                new ProductionWorkSceneCause(new SubjectId("job:production-test")),
                new SettlementServiceWorkSceneCause(new SubjectId("service:test")),
                new RoutePatrolSceneCause(new SubjectId("task:test")));
    }

    private static String id(SceneCause cause, String suffix) {
        return "lease:" + cause.kind().name().toLowerCase(Locale.ROOT).replace('_', '-') + "-" + suffix;
    }

    private static SceneLease lease(SceneCause cause, String suffix, SceneLeaseStatus status) {
        var world = new WorldId("frontier:service-order");
        var actor = new SubjectId("resident:test");
        return SceneLease.forCause(new SceneLeaseId(id(cause, suffix)), world, cause, new BlockPosition(0, 0, 0),
                new SimInstant(0), 1, status, List.of(new SceneMember(actor, SceneLease.deterministicEntityId(world, actor))), Set.of(), Optional.empty());
    }
}
