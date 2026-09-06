package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceResidentLedgerTest {
    @Test
    void matchesPythonMicroTraceForEmploymentOperationCasualtiesAndRecovery() {
        ReferenceResidentLedger ledger = new ReferenceResidentLedger(3, 8);

        assertEquals(List.of(
                "resident:3:1", "resident:3:2", "resident:3:3", "resident:3:4",
                "resident:3:5", "resident:3:6", "resident:3:7", "resident:3:8"), ledger.livingIds());
        assertEquals("farmer", ledger.resident("resident:3:1").occupation());
        assertEquals("worker", ledger.resident("resident:3:1").economicClass());
        assertEquals(List.of("resident:3:9", "resident:3:10"), ledger.create(2));
        assertEquals(List.of(
                "resident:3:1", "resident:3:10", "resident:3:2", "resident:3:3", "resident:3:4",
                "resident:3:5", "resident:3:6", "resident:3:7", "resident:3:8", "resident:3:9"), ledger.workerIds());

        ledger.assignEmployment(19, List.of("resident:3:1", "resident:3:2"));
        ledger.deploy(
                List.of("resident:3:1", "resident:3:3"),
                17,
                Map.of("resident:3:1", "guard", "resident:3:3", "scout"));

        assertEquals(
                List.of("resident:3:1", "resident:3:3"),
                ledger.woundExpectedFrom(
                        ledger.idsAt(ReferenceResidentLocation.OPERATION, null), 1.0d, new PythonRandom(7)));
        assertEquals(ReferenceResidentCondition.WOUNDED, ledger.resident("resident:3:1").condition());
        assertNull(ledger.resident("resident:3:1").deploymentRole());
        assertEquals(
                List.of("resident:3:1"), ledger.recoverExpected(1.0d, new PythonRandom(11)));
        assertEquals(ReferenceResidentCondition.ACTIVE, ledger.resident("resident:3:1").condition());
        assertEquals(ReferenceResidentLocation.OPERATION, ledger.resident("resident:3:1").location());
        assertEquals(17, ledger.resident("resident:3:1").locationRef());

        assertEquals(
                List.of("resident:3:2", "resident:3:3"),
                ledger.killExpectedFrom(
                        List.of("resident:3:2", "resident:3:3", "resident:3:7"), 1.7d, new PythonRandom(11)));
        assertEquals(List.of(
                "resident:3:1", "resident:3:10", "resident:3:4", "resident:3:5",
                "resident:3:6", "resident:3:7", "resident:3:8", "resident:3:9"), ledger.livingIds());
        assertEquals(7L, ledger.revision());
        ledger.assertValid();
    }

    @Test
    void recoveryRetainsPhysicalCustodyAndWoundedTransferRejectsHealthyPerson() {
        ReferenceResidentLedger ledger = new ReferenceResidentLedger(9, 3);
        ledger.deploy(List.of("resident:9:1"), 41, Map.of("resident:9:1", "guard"));
        assertEquals(
                List.of("resident:9:1"),
                ledger.woundExpectedFrom(List.of("resident:9:1"), 1.0d, new PythonRandom(0)));
        ledger.assignFieldPost(List.of("resident:9:1"), 77);

        assertEquals(
                List.of("resident:9:1"),
                ledger.recoverExpectedFrom(List.of("resident:9:1"), 1.0d, new PythonRandom(0)));
        ReferenceResident resident = ledger.resident("resident:9:1");
        assertEquals(ReferenceResidentLocation.FIELD_POST, resident.location());
        assertEquals(77, resident.locationRef());
        assertEquals(ReferenceResidentCondition.ACTIVE, resident.condition());

        assertThrows(
                IllegalArgumentException.class,
                () -> ledger.transferWoundedFromFieldPost(List.of("resident:9:1"), 77, 99));
        assertEquals(5L, ledger.revision());
        ledger.assertValid();
    }

    @Test
    void migrationMovesTheSamePersonAndRejectsDuplicateArrival() {
        ReferenceResidentLedger origin = new ReferenceResidentLedger(1, 5);
        ReferenceResidentLedger destination = new ReferenceResidentLedger(2, 1);

        List<ReferenceResident> travellers = origin.departExpected(2.5d, new PythonRandom(2));
        assertEquals(List.of("resident:1:3", "resident:1:4"), travellers.stream().map(ReferenceResident::id).toList());
        assertEquals(List.of("resident:1:1", "resident:1:2", "resident:1:5"), origin.livingIds());
        assertEquals(List.of("resident:1:3", "resident:1:4"), destination.acceptTransferred(travellers));

        ReferenceResident traveller = destination.resident("resident:1:3");
        assertEquals(2, traveller.homeSettlementId());
        assertEquals(ReferenceResidentLocation.SETTLEMENT, traveller.location());
        assertNull(traveller.employerCompanyId());
        assertThrows(IllegalArgumentException.class, () -> destination.acceptTransferred(travellers));
        origin.assertValid();
        destination.assertValid();
    }

    @Test
    void rejectsNonAvailableEmploymentAndNegativeCreation() {
        ReferenceResidentLedger ledger = new ReferenceResidentLedger(4, 1);
        ledger.deploy(List.of("resident:4:1"), 3, Map.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> ledger.assignEmployment(1, List.of("resident:4:1")));
        assertThrows(IllegalArgumentException.class, () -> ledger.create(-1));
        assertThrows(IllegalArgumentException.class, () -> new ReferenceResidentLedger(4, -1));
    }

    @Test
    void preservesClassCycleAndExhaustivelyCoversFieldPostCustodyTransitions() {
        ReferenceResidentLedger roster = new ReferenceResidentLedger(6, 20);
        assertEquals("owner", roster.resident("resident:6:12").economicClass());
        assertEquals("dependent", roster.resident("resident:6:13").economicClass());
        assertEquals("logistics", roster.resident("resident:6:12").occupation());
        assertEquals("farmer", roster.resident("resident:6:13").occupation());

        ReferenceResidentLedger ledger = new ReferenceResidentLedger(10, 3);
        ledger.assignFieldPost(List.of("resident:10:1"), 4);
        ledger.deployFromFieldPost(
                List.of("resident:10:1"), 4, 6, Map.of("resident:10:1", "scout"));
        assertEquals(ReferenceResidentLocation.OPERATION, ledger.resident("resident:10:1").location());
        assertEquals("scout", ledger.resident("resident:10:1").deploymentRole());
        ledger.returnHome(List.of("resident:10:1", "resident:10:missing"));
        assertEquals(ReferenceResidentLocation.SETTLEMENT, ledger.resident("resident:10:1").location());
        assertNull(ledger.resident("resident:10:1").locationRef());
        assertNull(ledger.resident("resident:10:1").deploymentRole());

        assertEquals(
                List.of("resident:10:2"),
                ledger.woundExpectedFrom(List.of("resident:10:2"), 1.0d, new PythonRandom(0)));
        ledger.assignFieldPost(List.of("resident:10:2"), 9);
        ledger.transferWoundedFromFieldPost(List.of("resident:10:2"), 9, 12);
        ReferenceResident patient = ledger.resident("resident:10:2");
        assertEquals(ReferenceResidentCondition.WOUNDED, patient.condition());
        assertEquals(ReferenceResidentLocation.OPERATION, patient.location());
        assertEquals(12, patient.locationRef());
        assertNull(patient.deploymentRole());
        ledger.assertValid();
    }

    @Test
    void reassigningACompanyClearsOnlyItsPriorWorkers() {
        ReferenceResidentLedger ledger = new ReferenceResidentLedger(5, 4);
        ledger.assignEmployment(1, List.of("resident:5:1", "resident:5:2"));
        ledger.assignEmployment(2, List.of("resident:5:3"));
        ledger.assignEmployment(1, List.of("resident:5:4"));

        assertNull(ledger.resident("resident:5:1").employerCompanyId());
        assertNull(ledger.resident("resident:5:2").employerCompanyId());
        assertEquals(2, ledger.resident("resident:5:3").employerCompanyId());
        assertEquals(1, ledger.resident("resident:5:4").employerCompanyId());
        assertEquals(4L, ledger.revision());
        ledger.assertValid();
    }

    @Test
    void marketBatchRetainsTheSourceRevisionForEveryCompanyAssignment() {
        ReferenceResidentLedger ledger = new ReferenceResidentLedger(14, 4);

        ledger.replaceEmployment(Map.of(
                1, List.of("resident:14:1"),
                2, List.of("resident:14:2"),
                3, List.of()));

        assertEquals(5L, ledger.revision(), "initial creation, employer-clear pass and three source assignments");
        assertEquals(1, ledger.resident("resident:14:1").employerCompanyId());
        assertEquals(2, ledger.resident("resident:14:2").employerCompanyId());
        assertNull(ledger.resident("resident:14:3").employerCompanyId());
        ledger.assertValid();
    }

    @Test
    void exactRosterStateRestoresCustodyRevisionAndOrdinalWithoutDemographicReplay() {
        ReferenceResidentLedger ledger = new ReferenceResidentLedger(15, 4);
        ledger.assignEmployment(8, List.of("resident:15:1"));
        ledger.deploy(List.of("resident:15:1", "resident:15:2"), 19, Map.of("resident:15:1", "guard"));
        assertEquals(true, ledger.woundExact("resident:15:1"));
        ledger.assignFieldPost(List.of("resident:15:2"), 5);
        assertEquals(true, ledger.killExact("resident:15:3"));

        ReferenceResidentLedger.State state = ledger.state();
        ReferenceResidentLedger restored = ReferenceResidentLedger.restore(state);

        assertEquals(state, restored.state());
        assertEquals(ReferenceResidentCondition.WOUNDED, restored.resident("resident:15:1").condition());
        assertEquals(ReferenceResidentLocation.FIELD_POST, restored.resident("resident:15:2").location());
        assertEquals(List.of("resident:15:5"), restored.create(1));
        restored.assertValid();
    }

    @Test
    void rejectsAResidentRestoreWithDuplicateOrForeignOwnership() {
        ReferenceResidentLedger.ResidentState resident = new ReferenceResidentLedger.ResidentState(
                "resident:16:1", 16, "farmer", "worker", null,
                ReferenceResidentLocation.SETTLEMENT, null, ReferenceResidentCondition.ACTIVE, null);
        assertThrows(IllegalArgumentException.class, () -> ReferenceResidentLedger.restore(
                new ReferenceResidentLedger.State(16, 2, 1L, List.of(resident, resident))));
        assertThrows(IllegalArgumentException.class, () -> ReferenceResidentLedger.restore(
                new ReferenceResidentLedger.State(16, 2, 1L, List.of(new ReferenceResidentLedger.ResidentState(
                        "resident:17:1", 17, "farmer", "worker", null,
                        ReferenceResidentLocation.SETTLEMENT, null, ReferenceResidentCondition.ACTIVE, null)))));
    }

    @Test
    void exactCasualtiesPreserveTheDemographicRandomStream() {
        ReferenceResidentLedger ledger = new ReferenceResidentLedger(13, 2);
        PythonRandom actual = new PythonRandom(91);
        PythonRandom expected = new PythonRandom(91);

        assertEquals(true, ledger.woundExact("resident:13:1"));
        assertEquals(expected.nextUInt32(), actual.nextUInt32());
        assertEquals(false, ledger.woundExact("resident:13:1"));
        assertEquals(true, ledger.killExact("resident:13:1"));
        assertEquals(expected.nextUInt32(), actual.nextUInt32());
        assertEquals(false, ledger.killExact("resident:13:1"));
        ledger.assertValid();
    }
}
