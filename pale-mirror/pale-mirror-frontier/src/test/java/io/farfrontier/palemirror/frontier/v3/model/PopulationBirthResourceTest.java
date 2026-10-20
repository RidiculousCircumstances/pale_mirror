package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.PopulationBirthProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual demographic producer/consumer, including historical consumed portions after recovery. */
class PopulationBirthResourceTest {
    @Test void delayedReviewUsesExecutionTimeForItsDecisionAndSuccessorClocks() {
        var f = fixture();
        var action = PopulationBirthProcess.review(f.home(), 1, 100);
        var events = FrontierWorldRuntimeDefinition.planScheduled(f.state(), action, new SimInstant(2000));
        var started = start(events);
        assertEquals(2000 + f.state().bootstrap().ruleset().cadence().populationBirthCompletionDelay(),
                started.job().resident().birthTick());
        assertTrue(events.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                .map(ScheduleEffect.Created.class::cast).allMatch(event -> event.action().dueAt().ticks() > 2000),
                "late admission must not schedule new work in the past");
        PopulationBirthProcess.reduceStarted(f.state(), f.home(), started, 2000);
    }
    @Test void alternateFungibleWarehouseCommitsOneRationAndCompletesAfterSnapshotAndWalReplay() throws Exception {
        var f = fixture(); var events = review(f.state(), f.home());
        var started = start(events);
        var selected = assertInstanceOf(ColdResourceConsumption.Fungible.class, started.job().food());
        assertEquals(f.container(), selected.containerId());
        assertEquals(Map.of(f.first(), 1), selected.lots().lotQuantities());
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        var decoded = assertInstanceOf(ResidentBirthStarted.class, codecs.decode(started.type(), codecs.encode(started)));
        var codec = new FrontierWorldStateCodec();
        try (var ignored = PedestrianRoutePlanning.bind((geometry, origin, target) -> {
            throw new AssertionError("demographic consumption never ranks or calculates a pedestrian route");
        })) {
            var recovered = codec.decode(codec.encode(f.state()));
            var admitted = PopulationBirthProcess.reduceStarted(recovered, f.home(), decoded, 100L);
            assertFalse(admitted.inventory().fungibleResources().lots().containsKey(f.first()));
            assertEquals(127, SettlementFoodPolicy.breadStock(admitted, f.home()));
            assertEquals(f.state().humanPopulation().residents(), admitted.humanPopulation().residents());
            assertEquals(f.state().physicalIntents(), admitted.physicalIntents());
            assertEquals(admitted, codec.decode(codec.encode(admitted)), "the spent source is history, not a dangling live lot claim");
            assertThrows(IllegalArgumentException.class, () -> PopulationBirthProcess.reduceStarted(admitted, f.home(), decoded, 100L));
            var restored = codec.decode(codec.encode(admitted));
            var completion = events.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                    .map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action)
                    .filter(action -> action.kind().equals("frontier.population.birth.complete")).findFirst().orElseThrow();
            var born = PopulationBirthProcess.planCompletion(restored, completion).stream().map(ProposedEvent::payload)
                    .filter(ResidentBorn.class::isInstance).map(ResidentBorn.class::cast).findFirst().orElseThrow();
            var completed = PopulationBirthProcess.reduceBorn(restored, f.home(), born);
            assertEquals(f.state().humanPopulation().residents().size() + 1, completed.humanPopulation().residents().size());
            assertEquals(127, SettlementFoodPolicy.breadStock(completed, f.home()), "completion cannot spend the ration again");
            assertEquals(completed, codec.decode(codec.encode(completed)));
        }
        byte[] bytes = codecs.encode(started);
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(started.type(), Arrays.copyOf(bytes, bytes.length - 1)));
        int tagOffset = 6 + started.job().id().value().length() + f.home().value().length() + started.job().householdId().value().length();
        bytes[tagOffset] = 99;
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(started.type(), bytes), "unknown resource representation is never inferred from an ID");
    }

    @Test void changedAllocationCannotBeReplacedByAnotherAvailableLotOrForgedAccount() {
        var f = fixture(); var event = start(review(f.state(), f.home()));
        var held = new ClaimAllocation(new SubjectId("claim:birth-source-intervention"), f.home(), f.home(), "minecraft:bread", 1,
                Map.of(f.first(), 1), ClaimPurpose.EXTERNAL_RESERVATION);
        var claimed = f.state().withInventory(f.state().inventory().withFungibleResources(
                f.state().inventory().fungibleResources().reserve(held, f.account())));
        var next = start(review(claimed, f.home()));
        assertEquals(f.reserve(), next.job().foodItemId());
        assertThrows(IllegalArgumentException.class, () -> PopulationBirthProcess.reduceStarted(claimed, f.home(), event, 100L),
                "unclaimed total stock does not authorize consuming another owner's retained portion");
        var old = (ColdResourceConsumption.Fungible) event.job().food();
        var forged = new ColdResourceConsumption.Fungible(f.home(), f.container(), "minecraft:bread",
                new FungibleResourceCustodySupport.LotSelection(new SubjectId("account:foreign"), old.lots().lotQuantities()));
        var job = event.job();
        var wrong = new ResidentBirthStarted(new ResidentBirthJob(job.id(), job.settlementId(), job.householdId(), forged,
                job.foodCommitmentId(), job.resident(), job.position()));
        assertThrows(IllegalArgumentException.class, () -> PopulationBirthProcess.reduceStarted(f.state(), f.home(), wrong, 100L));
        assertEquals(128, SettlementFoodPolicy.breadStock(claimed, f.home()));
    }

    @Test void currentPhysicalCustodyAndStorageFilterFenceTheSelectedSourceWithoutSpendingItsStock() {
        var f = fixture(); var event = start(review(f.state(), f.home()));
        var record = PhysicalReplicaRecord.expected(f.container(), ReferenceContainerCustody.semanticKind(f.state(), f.container()), 7,
                ReferenceContainerCustody.canonicalFingerprint(f.state(), f.container()), ReferenceContainerCustody.provenance(f.container()));
        var custody = PhysicalReplicaCustodyState.empty().declare(record)
                .observe(f.container(), 7, 1, record.fingerprint(), record.provenance(), 7)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(f.container()), f.container(), ReferenceContainerCustody.PROVIDER_ID,
                        1, 7, 2, PhysicalCustodyLeaseStatus.ACQUIRED, null));
        var hot = f.state().withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
        assertTrue(review(hot, f.home()).stream().noneMatch(proposed -> proposed.payload() instanceof ResidentBirthStarted));
        assertThrows(IllegalArgumentException.class, () -> PopulationBirthProcess.reduceStarted(hot, f.home(), event, 100L));
        var policy = f.state().inventory().containers().get(f.container()).storage().orElseThrow();
        var replacement = new ResourceStoragePolicy(policy.endpoint(), new ResourceStorageFilter(false, Set.of(), Map.of()), policy.priority(), policy.role());
        var filtered = new ResourceStoragePolicyChanged(f.container(), policy, replacement).apply(f.state(), f.home());
        assertThrows(IllegalArgumentException.class, () -> PopulationBirthProcess.reduceStarted(filtered, f.home(), event, 100L));
        assertEquals(f.state().inventory().fungibleResources(), filtered.inventory().fungibleResources());
        assertEquals(f.state().inventory().fungibleResources(), hot.inventory().fungibleResources());
    }

    private static List<ProposedEvent> review(FrontierWorldState state, SubjectId home) {
        return PopulationBirthProcess.planReview(state, PopulationBirthProcess.review(home, 1, 100));
    }
    private static ResidentBirthStarted start(List<ProposedEvent> events) {
        return events.stream().map(ProposedEvent::payload).filter(ResidentBirthStarted.class::isInstance)
                .map(ResidentBirthStarted.class::cast).findFirst().orElseThrow();
    }
    private static Fixture fixture() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:birth-shared-stock"), 91L));
        var settlement = state.bootstrap().settlements().getFirst(); var home = settlement.id();
        var container = new SubjectId("container:birth-alternate-warehouse");
        var position = settlement.anchor().offset(7, 0, 7); var station = new SurfaceAnchor(position.offset(-1, -1, 0));
        var endpoint = new ShipmentEndpoint.Storage(home, container, station, Optional.empty(), station);
        var policy = new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 5, ResourceStorageRole.WAREHOUSE);
        state = new ResourceStorageDeclared(new ContainerRecord(container, home, 27, ContainerPurpose.RESOURCE_STORAGE,
                Optional.empty(), Optional.of(policy)), position).apply(state, home);
        var resources = state.inventory().fungibleResources(); var lots = new LinkedHashMap<>(resources.lots());
        var first = new SubjectId("lot:birth-a-portion"); var reserve = new SubjectId("lot:birth-z-reserve");
        lots.put(first, new ResourceLot(first, home, "minecraft:bread", 1, "test:birth-food", List.of()));
        lots.put(reserve, new ResourceLot(reserve, home, "minecraft:bread", 127, "test:birth-food", List.of()));
        var account = ReferenceContainerCustody.scopeId(container); var accounts = new LinkedHashMap<>(resources.accounts());
        accounts.put(account, new CustodyAccount(account, new ResourceCustody.Container(container), Map.of(first, 1, reserve, 127), Map.of()));
        state = state.withInventory(state.inventory().withFungibleResources(new FungibleResourceLedger(lots, resources.claims(), accounts, resources.bindings()))
                .withSurfaceStatus(FrontierWorldState.depotId(home), ContainerSurfaceStatus.CONFLICT));
        return new Fixture(state, home, container, account, first, reserve);
    }
    private record Fixture(FrontierWorldState state, SubjectId home, SubjectId container, SubjectId account, SubjectId first, SubjectId reserve) { }
}
