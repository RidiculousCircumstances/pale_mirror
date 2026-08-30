package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanHealthProcessTest {
    @Test void localExposureMatchesTheCurrentGrayboxStructureProjectionAcrossConditions() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:health-geometry"), 101L));
        for (Settlement settlement : state.bootstrap().settlements()) {
            InfectionCell contact = contactCell(state, settlement);
            FrontierWorldState infected = state.withInfection(contact, new FixedRatio(new FixedScalar(FixedScalar.SCALE)));
            assertEquals(referenceExposure(infected, settlement), HumanHealthProcess.localExposure(infected, settlement));

            FrontierWorldState destroyed = infected;
            for (SettlementStructure structure : settlement.structures()) destroyed = destroyed.withStructureCondition(structure.id(), StructureCondition.DESTROYED);
            assertEquals(referenceExposure(destroyed, settlement), HumanHealthProcess.localExposure(destroyed, settlement));
        }
    }

    @Test void semanticInfectionContactProgressesOneExactResidentAndActivatesQuarantine() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:health-contact"), 41L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        InfectionCell contact = contactCell(state, settlement);
        state = state.withInfection(contact, new FixedRatio(new FixedScalar(FixedScalar.SCALE)));

        FrontierWorldState exposed = reduce(state, HumanHealthProcess.assess(state, settlement, 100L));
        SubjectId resident = settlement.residents().stream().map(Resident::id).sorted().findFirst().orElseThrow();
        assertEquals(ResidentHealthStatus.EXPOSED, exposed.humanPopulation().health(resident).status());
        assertTrue(exposed.humanPopulation().quarantined(settlement.id()));
        assertEquals(SettlementQuarantineStatus.QUARANTINED, exposed.humanPopulation().quarantine(settlement.id()).status());

        FrontierWorldState infected = reduce(exposed, HumanHealthProcess.assess(exposed, settlement, 1_300L));
        assertEquals(ResidentHealthStatus.INFECTED, infected.humanPopulation().health(resident).status());
        assertEquals(1, infected.humanPopulation().activeCases(settlement.id()));
    }

    @Test void recoveryLiftsQuarantineOnlyAfterTheExactActiveCaseIsNoLongerActive() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:health-recovery"), 42L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        InfectionCell contact = contactCell(state, settlement);
        state = state.withInfection(contact, new FixedRatio(new FixedScalar(FixedScalar.SCALE)));
        state = reduce(state, HumanHealthProcess.assess(state, settlement, 100L));
        state = reduce(state, HumanHealthProcess.assess(state, settlement, 1_300L));
        state = state.withInfection(contact, new FixedRatio(FixedScalar.ZERO));

        FrontierWorldState recovering = reduce(state, HumanHealthProcess.assess(state, settlement, 2_500L));
        SubjectId resident = settlement.residents().stream().map(Resident::id).sorted().findFirst().orElseThrow();
        assertEquals(ResidentHealthStatus.RECOVERING, recovering.humanPopulation().health(resident).status());
        assertFalse(recovering.humanPopulation().quarantined(settlement.id()));

        FrontierWorldState healthy = reduce(recovering, HumanHealthProcess.assess(recovering, settlement, 3_700L));
        assertEquals(ResidentHealthStatus.HEALTHY, healthy.humanPopulation().health(resident).status());
    }

    @Test void policyReducerRejectsAQuarantineLiftWhileTheExactCaseRemainsActive() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:health-negative"), 43L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        InfectionCell contact = contactCell(state, settlement);
        state = state.withInfection(contact, new FixedRatio(new FixedScalar(FixedScalar.SCALE)));
        state = reduce(state, HumanHealthProcess.assess(state, settlement, 100L));
        FrontierWorldState active = state;
        assertThrows(IllegalArgumentException.class, () -> HumanHealthProcess.reduceQuarantineTransition(active, settlement.id(), 101L,
                new SettlementQuarantineTransition(settlement.id(), SettlementQuarantineStatus.NORMAL, 101L)));
    }

    @Test void snapshotRoundTripRetainsExactDiseaseAndQuarantineState() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:health-codec"), 44L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        state = state.withInfection(contactCell(state, settlement), new FixedRatio(new FixedScalar(FixedScalar.SCALE)));
        state = reduce(state, HumanHealthProcess.assess(state, settlement, 100L));
        FrontierWorldState decoded = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(state.humanPopulation().health(), decoded.humanPopulation().health());
        assertEquals(state.humanPopulation().quarantines(), decoded.humanPopulation().quarantines());
    }

    private static FrontierWorldState reduce(FrontierWorldState state, List<ProposedEvent> events) {
        FrontierWorldState next = state;
        for (ProposedEvent event : events) {
            if (event.payload() instanceof ResidentHealthTransition transition) next = HumanHealthProcess.reduceResidentTransition(next, event.subject(), transition.atTick(), transition);
            if (event.payload() instanceof SettlementQuarantineTransition transition) next = HumanHealthProcess.reduceQuarantineTransition(next, event.subject(), transition.atTick(), transition);
        }
        return next;
    }

    private static InfectionCell contactCell(FrontierWorldState state, Settlement settlement) {
        return FrontierGrayboxPlan.compile(state).cells().values().stream()
                .filter(cell -> cell.ownerId().value().startsWith("structure:"))
                .filter(cell -> FrontierWorldStateSupport.structureSettlement(state.bootstrap(), cell.ownerId()).equals(settlement.id()))
                .map(cell -> InfectionCell.at(cell.position())).findFirst().orElseThrow();
    }

    private static boolean referenceExposure(FrontierWorldState state, Settlement settlement) {
        return FrontierGrayboxPlan.compile(state).cells().values().stream()
                .filter(cell -> cell.ownerId().value().startsWith("structure:"))
                .filter(cell -> FrontierWorldStateSupport.structureSettlement(state.bootstrap(), cell.ownerId()).equals(settlement.id()))
                .map(cell -> InfectionCell.at(cell.position())).distinct().anyMatch(state.infection()::containsKey);
    }
}
