package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaKind;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3AmbientActorReservationCacheTest {
    @Test
    void reusesOneAdmissionViewForEveryActorInOneRevisionAndDropsItAfterAnExactProviderLoss(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.settlementAssaultConfiguration(new WorldId("frontier:admission-cache"), 89L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        try {
            FrontierWorldState before = runtime.decodedState().orElseThrow();
            var assault = before.strategicPlans().settlementAssaults().values().iterator().next();
            var candidate = before.coldSettlementAssaultSceneCandidates().stream()
                    .filter(value -> value.assaultId().equals(assault.id())).findFirst().orElseThrow();
            assertTrue(assault.status() == SettlementAssaultStatus.WAITING_FOR_BATTLE || assault.status() == SettlementAssaultStatus.COLD_COMBAT,
                    "the ordinary fixture must install an active assault before admission is observed");
            assertFalse(candidate.memberPositions().isEmpty(), "the active assault must have a real provider-backed candidate before loss");

            Set<SubjectId> firstAdmission = null;
            for (SubjectId actorId : before.actorLocations().keySet()) {
                Set<SubjectId> admission = FrontierV3AmbientActorCaches.reservedActors(runtime, before);
                if (firstAdmission == null) firstAdmission = admission;
                assertSame(firstAdmission, admission, "one immutable canonical revision must reuse its derived reservation view for every ambient actor scan");
                if (candidate.memberPositions().containsKey(actorId)) {
                    assertTrue(admission.contains(actorId), "the real candidate member must stay reserved during its ordinary hand-off");
                }
            }

            var lostSupport = candidate.memberPositions().values().iterator().next();
            GrayboxCell provider = FrontierGrayboxPlan.compile(before).cells().get(lostSupport);
            assertTrue(provider != null, "the selected active-assault floor must be a declared physical provider cell");
            Map<io.farfrontier.palemirror.frontier.v3.model.BlockPosition, PhysicalDelta> losses = new LinkedHashMap<>(before.physicalDeltas());
            losses.put(lostSupport, new PhysicalDelta(lostSupport, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                    Optional.of(provider.ownerId()), Optional.of(provider.semanticPart()), "test:admission-cache-provider-loss"));
            FrontierWorldState afterLoss = before.withChanges(FrontierWorldStateUpdate.begin().physicalDeltas(losses));

            assertTrue(afterLoss.strategicPlans().settlementAssaults().containsKey(assault.id()), "the exact canonical loss must not erase the assault to manufacture a passing absence");
            assertFalse(afterLoss.coldSettlementAssaultSceneCandidates().stream().anyMatch(value -> value.assaultId().equals(assault.id())),
                    "the same active assault must lose admission when its selected exact provider support is lost");
            assertFalse(FrontierGrayboxPlan.compile(afterLoss).cells().containsKey(lostSupport),
                    "the current physical-provider view must apply the loss mask immediately");
            assertNotSame(firstAdmission, FrontierV3AmbientActorCaches.reservedActors(runtime, afterLoss),
                    "a changed canonical state object must never reuse the prior revision's derived admission view");
        } finally {
            FrontierV3AmbientActorExecutor.forget(runtime);
            runtime.shutdown();
        }
    }
}
