package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettlementStaffingPolicyTest {
    @Test void reusedInitialAuthorityImageDoesNotShareCurrentDecisionsOrWeakenValidation() {
        var state = initial();
        var bootstrap = state.bootstrap();
        var declared = DecisionAuthorityState.initial(bootstrap);
        assertSame(declared, DecisionAuthorityState.initial(bootstrap));
        var other = FrontierBootstrapper.create(new WorldId("frontier:other-staffing-policy"), 72);
        assertNotSame(declared, DecisionAuthorityState.initial(other));
        assertEquals(declared, DecisionAuthorityState.initial(bootstrap));
        var home = bootstrap.settlements().getFirst().id();
        var permissions = declared.require(home).workPermissions();
        state.withStrategicPlans(state.strategicPlans().withWorkPermissions(home, ResidentWorkPermissions.none()));
        assertEquals(permissions, DecisionAuthorityState.initial(bootstrap).require(home).workPermissions());
        var authority = declared.require(home);
        var forged = declared.replace(new DecisionAuthority(home, authority.kind(),
                new DecisionPolicyDescriptor("frontier:unregistered", 1), authority.reconsiderationEpoch(),
                authority.commitmentIds(), authority.provenanceIds(), authority.workPermissions()));
        var plans = state.strategicPlans();
        var invalid = new StrategicPlanState(plans.objectives(), plans.tasks(), plans.routePatrols(),
                plans.infectionKnowledge(), plans.hiveTerritoryKnowledge(), plans.hiveSettlementKnowledge(),
                plans.hiveDoctrine(), plans.settlementAssaults(), forged, plans.frontEffects(), plans.scoutPatrols());
        assertThrows(IllegalArgumentException.class, () -> state.withStrategicPlans(invalid));
    }

    @Test void isolatedFieldConflictRetainsItsWorkerAndDoesNotQuarantineStaffingReview() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var state = fixture.state();
        var job = fixture.job();
        var home = state.resourceSite(fixture.site()).settlementId();
        var position = state.resourceSite(fixture.site()).cropSlots().getFirst();
        state = ResourceSiteProcess.reduceConflict(state, fixture.site(), new ResourceSiteConflictObserved(
                fixture.site(), position, ResourceSiteDiagnosticProducer.SCENE_CARRIER_FENCE));
        assertEquals(ResourceSitePhase.CONFLICT, state.resourceSites().site(fixture.site()).phase());
        assertEquals(job, state.resourceSites().site(fixture.site()).harvestJob(job.id()).orElseThrow());
        var assignment = HumanAssignmentProjection.compile(state).assignment(job.workerId());
        assertEquals(HumanAssignmentKind.FIELD_HARVEST, assignment.kind());
        assertEquals(Optional.of(job.id()), assignment.ownerId());
        var proposal = SettlementStaffingComposition.POLICY.propose(state, home);
        assertTrue(proposal.permits(ResidentWorkKind.AGRICULTURE, job.workerId()));
        var retained = state;
        assertThrows(IllegalArgumentException.class, () -> SettlementWorkforce.requireAvailable(retained, home,
                List.of(job.workerId())));
        var codec = new FrontierWorldStateCodec(state.bootstrap());
        var recovered = codec.decode(codec.encode(state));
        assertEquals(assignment, HumanAssignmentProjection.compile(recovered).assignment(job.workerId()));
        assertEquals(proposal, SettlementStaffingComposition.POLICY.propose(recovered, home));
    }

    @Test void demandBuildsDisjointEssentialRostersAndRetainsOptionalOffers() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst().id();
        var policy = fixedPolicy(3, 2).propose(state, home);
        assertEquals(3, policy.workers(ResidentWorkKind.AGRICULTURE).size());
        assertEquals(2, policy.workers(ResidentWorkKind.BAKING).size());
        assertTrue(Collections.disjoint(policy.workers(ResidentWorkKind.AGRICULTURE), policy.workers(ResidentWorkKind.BAKING)));
        assertTrue(policy.workers(ResidentWorkKind.LOGISTICS).containsAll(policy.workers(ResidentWorkKind.AGRICULTURE)));
        assertTrue(policy.workers(ResidentWorkKind.LOGISTICS).containsAll(policy.workers(ResidentWorkKind.BAKING)));
        var changed = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(home, policy));
        assertEquals(policy, fixedPolicy(3, 2).propose(changed, home), "reassessment does not reshuffle a sufficient roster");
    }
    @Test void replacingMissingPermissionsUsesCapabilitiesNotPermanentRoleAssignments() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst().id();
        state = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(home, ResidentWorkPermissions.none()));
        var policy = fixedPolicy(3, 2).propose(state, home);
        assertEquals(3, policy.workers(ResidentWorkKind.AGRICULTURE).size());
        assertEquals(2, policy.workers(ResidentWorkKind.BAKING).size());
        for (var actor : policy.workers(ResidentWorkKind.AGRICULTURE))
            assertTrue(state.humanPopulation().resident(actor).capability(HumanCapability.AGRICULTURE) > 0);
        for (var actor : policy.workers(ResidentWorkKind.BAKING))
            assertTrue(state.humanPopulation().resident(actor).capability(HumanCapability.INDUSTRY) > 0);
    }
    @Test void demandReductionReleasesPermissionsWithoutCreatingAnAssignment() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst().id();
        var old = fixedPolicy(3, 2).propose(state, home);
        var projected = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(home, old));
        var next = fixedPolicy(1, 1).propose(projected, home);
        assertEquals(1, next.workers(ResidentWorkKind.AGRICULTURE).size());
        assertEquals(1, next.workers(ResidentWorkKind.BAKING).size());
        assertEquals(1, next.localReserve(ResidentWorkKind.AGRICULTURE));
        assertTrue(HumanAssignmentProjection.compile(projected).assignments().values().stream().noneMatch(HumanAssignment::active));
    }
    @Test void actualRegisteredPolicyEventRoundTripsAndRejectsStaleOrForgedDecisions() {
        var initial = initial(); var home = initial.bootstrap().settlements().getFirst().id();
        var state = initial.withStrategicPlans(initial.strategicPlans().withWorkPermissions(home, ResidentWorkPermissions.none()));
        var event = SettlementStaffingComposition.change(state, home, 200).orElseThrow();
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(event, codecs.decode(event.type(), codecs.encode(event)));
        var changed = SettlementStaffingComposition.reduce(state, home, event);
        assertEquals(state.strategicPlans().requireDecisionAuthority(home).reconsiderationEpoch(),
                changed.strategicPlans().requireDecisionAuthority(home).reconsiderationEpoch());
        assertEquals(changed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(changed)));
        assertThrows(IllegalArgumentException.class, () -> SettlementStaffingComposition.reduce(changed, home, event));
        assertThrows(IllegalArgumentException.class, () -> SettlementStaffingComposition.reduce(state, initial.bootstrap().settlements().getLast().id(), event));
        var forged = new SettlementWorkPolicyChanged(home, event.authorityEpoch() + 1, 200, event.expected(), event.next());
        assertThrows(IllegalArgumentException.class, () -> SettlementStaffingComposition.reduce(state, home, forged));
        var forgedRoster = new SettlementWorkPolicyChanged(home, event.authorityEpoch(), 200,
                event.expected(), new ResidentWorkPermissions(Map.of(ResidentWorkKind.LOGISTICS,
                        Map.of(initial.bootstrap().settlements().getFirst().residents().getFirst().id(), 1)), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> SettlementStaffingComposition.reduce(state, home, forgedRoster));
    }
    @Test void missingDuplicateOrMismatchedFamilyDeclarationsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new SettlementStaffingPolicy(List.of()));
        var port = port(ResidentWorkKind.AGRICULTURE, HumanCapability.AGRICULTURE, 3, 1);
        assertThrows(IllegalArgumentException.class, () -> new SettlementStaffingPolicy(List.of(port, port, port)));
        var forged = new SettlementStaffingPort() {
            public ResidentWorkKind kind() { return ResidentWorkKind.AGRICULTURE; }
            public HumanCapability capability() { return HumanCapability.AGRICULTURE; }
            public Demand assess(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules) {
                return new Demand(ResidentWorkKind.AGRICULTURE, HumanCapability.LOGISTICS, 3, 1, 1, Set.of());
            }
        };
        var policy = new SettlementStaffingPolicy(List.of(forged,
                port(ResidentWorkKind.BAKING, HumanCapability.INDUSTRY, 2, 1),
                port(ResidentWorkKind.EXTRACTION, HumanCapability.EXTRACTION, 0, 0),
                port(ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS, 64, 0)));
        var state = initial();
        assertThrows(IllegalArgumentException.class, () -> policy.propose(state, state.bootstrap().settlements().getFirst().id()));
    }
    @Test void deadLocalWorkersAreReplacedByLivingCapableResidents() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst().id();
        var farmers = SettlementWorkPolicy.permissions(state, home).workers(ResidentWorkKind.AGRICULTURE);
        var locations = new LinkedHashMap<>(state.actorLocations());
        for (var id : farmers) locations.put(id, locations.get(id).deadAt(locations.get(id).body()));
        state = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(locations));
        var next = fixedPolicy(3, 2).propose(state, home);
        assertEquals(3, next.workers(ResidentWorkKind.AGRICULTURE).size());
        assertTrue(Collections.disjoint(farmers, next.workers(ResidentWorkKind.AGRICULTURE)));
        assertEquals(1, next.localReserve(ResidentWorkKind.AGRICULTURE));
    }
    private static SettlementStaffingPolicy fixedPolicy(int farmers, int bakers) {
        return new SettlementStaffingPolicy(List.of(
                port(ResidentWorkKind.AGRICULTURE, HumanCapability.AGRICULTURE, farmers, 1),
                port(ResidentWorkKind.BAKING, HumanCapability.INDUSTRY, bakers, 1),
                port(ResidentWorkKind.EXTRACTION, HumanCapability.EXTRACTION, 0, 0),
                port(ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS, 64, 0)));
    }
    private static SettlementStaffingPort port(ResidentWorkKind kind, HumanCapability capability, int target, int reserve) {
        return new SettlementStaffingPort() {
            public ResidentWorkKind kind() { return kind; }
            public HumanCapability capability() { return capability; }
            public Demand assess(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules) {
                return new Demand(kind, capability, target, reserve, rules.priority(), Set.of());
            }
        };
    }
    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:staffing-policy"), 71));
    }
}
