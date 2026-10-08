package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonParser;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3GroupSteeringDiagnosticTest {
    @Test void latchedHoldIsReportedInsteadOfHypotheticalFreshAllowanceAndMissingHotPoseIsExplicit() {
        var config = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:group-steering-diagnostic"), 41);
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        for (int boundary = 0; boundary < 1000; boundary++) {
            var state = engine.canonicalState().state();
            var candidate = state.unitGroups().groups().values().stream().filter(g -> g.phase() == UnitGroup.Phase.TRAVELLING
                    && g.members().stream().allMatch(m -> state.actorMovements().containsKey(m.actorId()))).findFirst();
            if (candidate.isPresent()) {
                var group = candidate.orElseThrow(); var actor = group.members().getFirst().actorId();
                var peer = group.members().getLast().actorId();
                var hold = MovementPermission.hold(MovementPermission.Reason.GROUP_PROGRESS_STRETCH, peer)
                        .withSpacing(new MovementPermission.Spacing(14, 16, 12, 2));
                var json = JsonParser.parseString(FrontierV3GroupDiagnosticJson.render(engine.checkpoint(), state, group,
                        ActorPositionView.canonical(state, engine.checkpoint().instant().ticks()), "CURRENT",
                        movement -> Optional.empty(), movement -> Optional.of(hold))).getAsJsonObject();
                var member = json.getAsJsonArray("members").get(0).getAsJsonObject();
                assertEquals("EXECUTED_HOT", member.get("decisionSource").getAsString());
                assertEquals("GROUP_PROGRESS_STRETCH", member.get("disposition").getAsString());
                assertEquals(peer.value(), member.get("waitingFor").getAsString());
                assertTrue(member.get("requestedPaceBlocksPerTick").isJsonNull());
                assertEquals(14, member.getAsJsonObject("spacing").get("progressGap").getAsDouble());
                var physicalState = ActorBodyAuthority.demand(state, actor);
                physicalState = ActorBodyAuthority.running(physicalState, ActorBodyAuthority.current(physicalState, actor));
                var missing = JsonParser.parseString(FrontierV3GroupDiagnosticJson.render(engine.checkpoint(), physicalState, group))
                        .getAsJsonObject().getAsJsonArray("members").get(0).getAsJsonObject();
                assertFalse(missing.get("positionCurrent").getAsBoolean());
                assertEquals("HOT_DECISION_UNAVAILABLE", missing.get("decisionSource").getAsString());
                assertEquals("POSITION_UNAVAILABLE", missing.get("disposition").getAsString());
                return;
            }
            var next = engine.checkpoint().schedules().stream().filter(a -> !FrontierWorldRuntimeDefinition.scheduledHeld(state, a))
                    .sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), next.dueAt().ticks())), new WorkBudget(1, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind());
        }
        fail("ordinary trade did not create travelling members");
    }
}
