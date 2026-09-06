package io.farfrontier.palemirror.internal.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import org.junit.jupiter.api.Test;

class SourceGrayboxInspectorTest {
    @Test
    void exposesExactSectorAndSettlementValuesAtTheirPhysicalCoordinates() {
        ReferenceGrayboxSimulation simulation = ReferenceGrayboxSimulation.create(9031746258841137206L);
        for (int day = 0; day < 40; day++) simulation.tick();
        ReferenceGrayboxSnapshot snapshot = simulation.snapshot();
        ReferenceGrayboxSnapshot.Sector sector = snapshot.sectors().stream()
                .filter(value -> value.infection() > 0.01d || value.humanAccess() > 0.01d).findFirst().orElseThrow();
        String sectorReport = SourceGrayboxInspector.at(snapshot, sector.rectangle().centreX(), sector.rectangle().centreZ());
        assertTrue(sectorReport.contains("[V2] sector=" + sector.key()),
                "the physical sector marker must expose its exact canonical identity");
        assertTrue(sectorReport.contains("infection=") && sectorReport.contains("humanAccess=") && sectorReport.contains("hiveInfluence="),
                "the inspector must retain all four sector metrics instead of collapsing them into the overview towers");

        ReferenceGrayboxSnapshot.Settlement settlement = snapshot.settlements().getFirst();
        String settlementReport = SourceGrayboxInspector.at(snapshot, settlement.rectangle().centreX(), settlement.rectangle().centreZ());
        assertTrue(settlementReport.contains("[S] #" + settlement.id()) && settlementReport.contains("population="),
                "the settlement footprint must identify its canonical people and civic state");
        assertTrue(settlementReport.contains("[CIVIC]") && settlementReport.contains("[MARKET]"),
                "dense source economic dashboards must remain inspectable without creating overlapping map labels");
    }

    @Test
    void reportsNoFactWithoutInventingASecondState() {
        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxSimulation.create(42L).snapshot();
        String report = SourceGrayboxInspector.at(snapshot, snapshot.bounds().minX(), snapshot.bounds().minZ() - 32);
        assertTrue(report.contains("no source object"),
                "an empty position must stay an explicit absence instead of receiving inferred source facts");
    }

    @Test
    void exposesADeclaredPhysicalInteractionWithoutGuessingItsEffect() {
        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxSnapshot.Interaction interaction = snapshot.interactions().getFirst();
        var slot = interaction.slots().getFirst();

        String report = SourceGrayboxInspector.at(snapshot, slot.x(), slot.z());
        assertTrue(report.contains("[X] " + interaction.id()) && report.contains("target=" + interaction.subjectId())
                        && report.contains("total="),
                "only the source-declared interaction slot may expose its exact canonical effect");
    }
}
