package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveInfectionProcessTest {
    @Test
    void liveHeartReseedsTheMetabolismAfterExactDecontaminationRemovedEveryCell() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-infection"), 105L));
        for (InfectionCell cell : List.copyOf(state.infection().keySet())) state = state.withInfection(cell,
                new io.farfrontier.palemirror.frontier.v3.api.FixedRatio(io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO));

        List<ProposedEvent> planned = HiveInfectionProcess.plan(state, HiveInfectionProcess.pulse(1, 100L));

        InfectionChanged changed = assertInstanceOf(InfectionChanged.class, planned.getFirst().payload());
        assertEquals(InfectionCell.at(new BlockPosition(420, 64, 420)), changed.cell());
        assertTrue(changed.intensity().value().raw() > 0L);
        assertInstanceOf(ScheduleEffect.Created.class, planned.get(1).payload());
    }

    @Test
    void destroyedHeartsCannotContinueTheSharedHiveInfectionMetabolism() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-infection-destroyed"), 106L));
        for (HiveOrgan heart : state.bootstrap().hive().organs().stream().filter(organ -> organ.kind() == HiveOrganKind.HEART).toList()) {
            int threshold = (FrontierGrayboxPlan.intactOrganCellCount(heart) + 2) / 3;
            List<GrayboxCell> cells = FrontierGrayboxPlan.compile(state).cells().values().stream().filter(cell -> cell.ownerId().equals(heart.id()))
                    .sorted(java.util.Comparator.comparingInt((GrayboxCell cell) -> cell.position().x())
                            .thenComparingInt(cell -> cell.position().y()).thenComparingInt(cell -> cell.position().z())).toList();
            for (int index = 0; index < threshold; index++) {
                GrayboxCell cell = cells.get(index);
                state = state.recordPhysicalDelta(new PhysicalDelta(cell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                        java.util.Optional.of(heart.id()), java.util.Optional.of(cell.semanticPart()), "test:heart-loss"));
            }
        }

        List<ProposedEvent> planned = HiveInfectionProcess.plan(state, HiveInfectionProcess.pulse(1, 100L));

        assertEquals(1, planned.size());
        assertInstanceOf(ScheduleEffect.Created.class, planned.getFirst().payload());
    }
}
