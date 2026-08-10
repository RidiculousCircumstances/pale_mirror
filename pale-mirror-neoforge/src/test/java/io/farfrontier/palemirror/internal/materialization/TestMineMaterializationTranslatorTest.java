package io.farfrontier.palemirror.internal.materialization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.ThreatTier;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.internal.content.EncounterProfile;
import io.farfrontier.palemirror.internal.world.EncounterRecord;
import io.farfrontier.palemirror.internal.world.EncounterActorRef;
import io.farfrontier.palemirror.internal.world.EncounterState;
import org.junit.jupiter.api.Test;

class TestMineMaterializationTranslatorTest {
    private static final InfectionSourceId TEST_SOURCE = new InfectionSourceId("pale_mirror:test_source");

    @Test
    void infectionPlanIsStableAndSeparatesOverlayFromController() {
        FacilityState facility = new FacilityState(new WorldObjectId("pale_mirror:test_mine"), TEST_SOURCE, 80, 10, 10);
        facility.infect();

        MaterializationPlan plan = new TestMineMaterializationTranslator().translate(facility);

        assertEquals(1, plan.desiredRevision());
        assertEquals(MaterializationOperationType.ENSURE_OVERLAY, plan.operations().getFirst().type());
        assertEquals(MaterializationOperationType.ENSURE_PM_ANCHOR, plan.operations().get(1).type());
        assertEquals("FOOTHOLD", plan.operations().getFirst().target());
        assertEquals("pale_mirror:test_mine:1:ensure_overlay:FOOTHOLD", plan.operations().getFirst().idempotencyKey());
    }

    @Test
    void encounterSlotsBecomeIndependentPersistedOperations() {
        FacilityState facility = new FacilityState(new WorldObjectId("pale_mirror:test_mine"), TEST_SOURCE, 80, 10, 10);
        facility.infect();
        EncounterProfile profile = new EncounterProfile("pale_mirror:guards", 1,
                java.util.List.of(new EncounterProfile.ActorSlot("guard_1", "pale_mirror:crimsonified_human", ThreatTier.FOOTHOLD)));

        MaterializationPlan plan = new TestMineMaterializationTranslator().translate(facility, profile, EncounterRecord.none());

        assertEquals(3, plan.operations().size());
        assertEquals(MaterializationOperationType.ENSURE_SOURCE_ENCOUNTER_ACTOR, plan.operations().get(2).type());
        assertEquals("guard_1", plan.operations().get(2).target());
        assertEquals("pale_mirror:test_mine:1:ensure_source_encounter_actor:guard_1", plan.operations().get(2).idempotencyKey());
    }

    @Test
    void encounterProfilesFilterActorsByPmTierInsteadOfCrimsonPhase() {
        FacilityState facility = new FacilityState(new WorldObjectId("pale_mirror:test_mine"), TEST_SOURCE, 80, 10, 10);
        facility.infect(1);
        EncounterProfile profile = new EncounterProfile("pale_mirror:tiers", 1, java.util.List.of(
                new EncounterProfile.ActorSlot("human", "pale_mirror:crimsonified_human", ThreatTier.FOOTHOLD),
                new EncounterProfile.ActorSlot("skeleton", "pale_mirror:crimsonified_skeleton", ThreatTier.INFESTED)));

        assertEquals(java.util.List.of("human"), profile.actorsFor(facility.threatTier()).stream()
                .map(EncounterProfile.ActorSlot::id).toList());
        facility.advanceThreatTier(13);
        assertEquals(java.util.List.of("human", "skeleton"), profile.actorsFor(facility.threatTier()).stream()
                .map(EncounterProfile.ActorSlot::id).toList());
    }

    @Test
    void encounterCompositionIsDeterministicAndNeverFallsBackToAnEarlierTier() {
        EncounterProfile.ActorSlot foothold = new EncounterProfile.ActorSlot("human", "pale_mirror:spore_infected_human",
                ThreatTier.FOOTHOLD);
        EncounterProfile.ActorSlot siege = new EncounterProfile.ActorSlot("spitter", "pale_mirror:spore_spitter",
                ThreatTier.FOOTHOLD);
        EncounterProfile profile = new EncounterProfile("pale_mirror:spore", 2, java.util.List.of(), java.util.List.of(
                new EncounterProfile.Composition("foothold_a", ThreatTier.FOOTHOLD, 1, java.util.List.of(foothold)),
                new EncounterProfile.Composition("foothold_b", ThreatTier.FOOTHOLD, 3, java.util.List.of(foothold)),
                new EncounterProfile.Composition("siege", ThreatTier.SIEGE, 1, java.util.List.of(siege))));

        EncounterProfile.Composition first = profile.selectComposition(ThreatTier.FOOTHOLD, 42L,
                "pale_mirror:test_mine", 7L);
        EncounterProfile.Composition repeat = profile.selectComposition(ThreatTier.FOOTHOLD, 42L,
                "pale_mirror:test_mine", 7L);
        assertEquals(first.id(), repeat.id());
        assertEquals("siege", profile.selectComposition(ThreatTier.SIEGE, 42L, "pale_mirror:test_mine", 7L).id());
        assertEquals(null, profile.selectComposition(ThreatTier.APEX, 42L, "pale_mirror:test_mine", 7L));
    }

    @Test
    void unavailableNewProfileNeverForgetsExistingActorsNeededForCleanup() {
        FacilityState facility = new FacilityState(new WorldObjectId("pale_mirror:test_mine"), TEST_SOURCE, 80, 10, 10);
        facility.infect(1);
        EncounterRecord existing = new EncounterRecord("pale_mirror:legacy", "1", "pm:job:legacy", 1,
                java.util.List.of(new EncounterActorRef("human", "pale_mirror:crimsonified_human", "minecraft:zombie",
                        java.util.UUID.randomUUID(), EncounterActorRef.Status.ACTIVE)), EncounterState.ACTIVE, "");

        MaterializationPlan degraded = new TestMineMaterializationTranslator().translate(facility, null, existing);
        assertEquals(2, degraded.operations().size());
        facility.beginRecovery(1);
        MaterializationPlan cleanup = new TestMineMaterializationTranslator().translate(facility, null, existing);
        assertEquals(MaterializationOperationType.REMOVE_SOURCE_ENCOUNTER_ACTOR, cleanup.operations().getFirst().type());
        assertEquals("human", cleanup.operations().getFirst().target());
    }

    @Test
    void sourceSelectsItsOwnSafeOverlayWithoutChangingGenericOperationVocabulary() {
        FacilityState facility = new FacilityState(new WorldObjectId("pale_mirror:spore_mine"),
                new InfectionSourceId("pale_mirror:spore"), 80, 10, 10);
        facility.infect();

        MaterializationPlan plan = new TestMineMaterializationTranslator().translate(facility);

        assertEquals(MaterializationOperationType.ENSURE_OVERLAY, plan.operations().getFirst().type());
        assertEquals(MaterializationOperationType.ENSURE_PM_ANCHOR, plan.operations().get(1).type());
    }
}
