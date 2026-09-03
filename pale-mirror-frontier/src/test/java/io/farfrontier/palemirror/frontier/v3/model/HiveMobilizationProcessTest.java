package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.process.HiveMobilizationProcess;
import io.farfrontier.palemirror.frontier.v3.process.HiveSettlementAssaultProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveMobilizationProcessTest {
    @Test void registeredPhysicalReleaseCommandAdmitsTheExactWakingGroup() {
        Fixture fixture = fixture();
        List<ProposedEvent> planned = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload()));
        HiveMobilizationStarted started = assertInstanceOf(HiveMobilizationStarted.class, planned.get(1).payload());
        state = HiveMobilizationProcess.reduceStarted(state, fixture.hive(), started);
        CommandId commandId = new CommandId("command:hive-mobilization-release");

        CommandPlan plan = FrontierWorldRuntimeDefinition.planCommand(state, new FrontierCommand(1, commandId,
                state.bootstrap().worldId(), Revision.ZERO, new SimInstant(200L), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(commandId), new HiveMobilizationReleaseStarted(started.mobilization().id())));

        assertInstanceOf(CommandPlan.Accepted.class, plan);
    }

    @Test void registeredPhysicalAssemblyArrivalAdmitsOnlyTheExactHotCursor() {
        Mobilized assembled = assemble(fixture());
        HiveMobilization mobilization = assembled.mobilization();
        SubjectId actorId = mobilization.assembly().orElseThrow().safeAdvances().getFirst();
        HiveTaskAssembly.Member member = mobilization.assembly().orElseThrow().members().get(actorId);
        AmbientActorLease lease = new AmbientActorLease(actorId, assembled.state().actorLocations().get(actorId).body(),
                new SimInstant(300L), 1L, AmbientLeaseStatus.HOT, AmbientGoalKind.HIVE_TASK_ASSEMBLY,
                member.nextSurface().standingBody());
        FrontierWorldState hot = assembled.state().withChanges(FrontierWorldStateUpdate.begin().ambientLeases(java.util.Map.of(actorId, lease)));
        CommandId commandId = new CommandId("command:hive-mobilization-assembly-arrival");
        HiveMobilizationAssemblyAdvanced arrival = new HiveMobilizationAssemblyAdvanced(mobilization.id(), actorId, member.cursor());

        CommandPlan accepted = FrontierWorldRuntimeDefinition.planCommand(hot, new FrontierCommand(1, commandId,
                hot.bootstrap().worldId(), Revision.ZERO, new SimInstant(300L), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(commandId), arrival));
        assertInstanceOf(CommandPlan.Accepted.class, accepted,
                "the registered physical executor may commit only its retained HOT assembly cursor");

        CommandId staleId = new CommandId("command:hive-mobilization-assembly-arrival-stale");
        CommandPlan rejected = FrontierWorldRuntimeDefinition.planCommand(hot, new FrontierCommand(1, staleId,
                hot.bootstrap().worldId(), Revision.ZERO, new SimInstant(300L), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(staleId), new HiveMobilizationAssemblyAdvanced(mobilization.id(), actorId, member.cursor() + 1)));
        assertInstanceOf(CommandPlan.Rejected.class, rejected,
                "a physical executor cannot invent a later assembly cursor without observed arrival");
    }

    @Test void remoteAssaultRequiresOneExactDormantOverseerAndRejectsAnImpostorController() {
        Fixture fixture = fixture();
        List<ProposedEvent> planned = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        HiveMobilization valid = assertInstanceOf(HiveMobilizationStarted.class, planned.get(1).payload()).mobilization();

        assertEquals(new SubjectId("bioform:east-23"), valid.overseerId());
        Bioform controller = fixture.state().bootstrap().hive().bioforms().stream()
                .filter(value -> value.id().equals(valid.overseerId())).findFirst().orElseThrow();
        assertTrue(controller.isOverseer());
        assertTrue(valid.memberIds().contains(valid.overseerId()));

        FrontierWorldState active = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload()));
        HiveMobilization impostor = new HiveMobilization(valid.id(), valid.hiveId(), valid.nestId(), valid.taskId(), valid.settlementId(),
                valid.memberIds().getFirst(), valid.memberIds(), valid.releasedMemberIds(), valid.releasingMemberId(), valid.status(), valid.conflictReason(), valid.startedAt());
        assertThrows(IllegalArgumentException.class, () -> HiveMobilizationProcess.reduceStarted(active, fixture.hive(), new HiveMobilizationStarted(impostor)),
                "canonical validation must reject a breach member masquerading as a remote controller");
    }

    @Test void remoteAssaultAdmitsOnlyTheExactSubordinateWeightItsOverseerCanCommand() {
        Fixture fixture = fixture();
        List<ProposedEvent> planned = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        HiveMobilization valid = assertInstanceOf(HiveMobilizationStarted.class, planned.get(1).payload()).mobilization();
        java.util.Map<SubjectId, Bioform> bioforms = fixture.state().bootstrap().hive().bioforms().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Bioform::id, value -> value));

        assertEquals(6, HiveCommandCapacity.usedCapacity(fixture.state().bootstrap().ruleset(), valid.overseerId(), valid.memberIds(), bioforms));
        assertTrue(HiveCommandCapacity.admits(fixture.state().bootstrap().ruleset(), valid.overseerId(), valid.memberIds(), bioforms));

        SubjectId extra = fixture.state().bootstrap().hive().bioforms().stream().filter(value -> value.nestId().equals(valid.nestId()))
                .filter(value -> fixture.state().hiveColony().bioformLifecycles().get(value.id()).phase() == BioformLifecyclePhase.DORMANT)
                .map(Bioform::id).filter(id -> !valid.memberIds().contains(id)).findFirst().orElseThrow();
        java.util.List<SubjectId> overloadedMembers = new java.util.ArrayList<>(valid.memberIds());
        overloadedMembers.add(extra);
        HiveMobilization overloaded = new HiveMobilization(valid.id(), valid.hiveId(), valid.nestId(), valid.taskId(), valid.settlementId(),
                valid.overseerId(), overloadedMembers, valid.status(), valid.startedAt());
        FrontierWorldState active = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload()));

        assertFalse(HiveCommandCapacity.admits(active.bootstrap().ruleset(), overloaded.overseerId(), overloaded.memberIds(), bioforms));
        assertThrows(IllegalArgumentException.class, () -> HiveMobilizationProcess.reduceStarted(active, fixture.hive(), new HiveMobilizationStarted(overloaded)),
                "a forged fifth subordinate must not bypass the exact Overseer capacity");
    }

    @Test void assaultTaskWakesOnlyExactDormantMembersThenAdmitsTheWholeObservedGroupWithoutASyntheticBody() {
        Fixture fixture = fixture();
        List<ProposedEvent> planned = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        assertEquals(2, planned.size());
        StrategicTaskTransition active = assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload());
        HiveMobilizationStarted started = assertInstanceOf(HiveMobilizationStarted.class, planned.get(1).payload());

        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(), active);
        state = HiveMobilizationProcess.reduceStarted(state, fixture.hive(), started);
        HiveMobilization mobilization = state.hiveColony().mobilizations().get(started.mobilization().id());
        assertEquals(HiveMobilizationStatus.WAKING, mobilization.status());
        FrontierWorldState waking = state;
        assertTrue(mobilization.memberIds().stream().allMatch(member -> waking.hiveColony().bioformLifecycles().get(member).phase() == BioformLifecyclePhase.WAKING));
        SubjectId first = mobilization.memberIds().getFirst();
        HiveCocoonSlot firstSlot = waking.hiveColony().bioformLifecycles().get(first).homeSlot().orElseThrow();
        HiveOrgan firstHibernaculum = waking.bootstrap().hive().organs().stream()
                .filter(organ -> organ.id().equals(firstSlot.hibernaculumId())).findFirst().orElseThrow();
        GrayboxCell retainedCocoon = FrontierGrayboxPlan.compileStructuralBaseline(waking).cells()
                .get(HiveCocoonPlan.cocoonCell(firstHibernaculum, firstSlot));
        assertEquals(first, retainedCocoon.ownerId(), "task selection keeps the same physical cocoon until its release receipt");
        assertEquals(GrayboxSemanticPart.COCOON, retainedCocoon.semanticPart());
        assertFalse(FrontierGrayboxPlan.structuralInput(fixture.state()).equals(FrontierGrayboxPlan.structuralInput(waking)),
                "a waking cocoon changes the retained structural projection input so a naturally loaded projector cannot keep a stale plan");
        assertThrows(IllegalArgumentException.class, () -> AmbientActorProcess.nextLease(waking, first,
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(201L)), "unconfirmed waking must not create an ambient body");

        state = HiveMobilizationProcess.reduceReleaseStarted(state, fixture.hive(), new HiveMobilizationReleaseStarted(mobilization.id()));
        HiveMobilization releasing = state.hiveColony().mobilizations().get(mobilization.id());
        assertEquals(Optional.of(first), releasing.releasingMemberId());
        state = HiveMobilizationProcess.reduceCocoonReleased(state, fixture.hive(), new HiveMobilizationCocoonReleased(mobilization.id(), first));

        HiveMobilization after = state.hiveColony().mobilizations().get(mobilization.id());
        assertEquals(HiveMobilizationStatus.WAKING, after.status(), "the next exact cocoon remains a separate durable effect");
        assertEquals(List.of(first), after.releasedMemberIds());
        assertEquals(BioformLifecyclePhase.ASSEMBLING, state.hiveColony().bioformLifecycles().get(first).phase());
        FrontierWorldState partial = state;
        assertThrows(IllegalArgumentException.class, () -> AmbientActorProcess.nextLease(partial, first,
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(202L)), "one confirmed member must wait for the exact group");
        assertFalse(HivePhysiologySupport.permitsAmbientLease(state, after.memberIds().get(1)),
                "the next waking cocoon remains physically closed until its own confirmation");

        for (int index = 1; index < after.memberIds().size(); index++) {
            state = HiveMobilizationProcess.reduceReleaseStarted(state, fixture.hive(), new HiveMobilizationReleaseStarted(after.id()));
            SubjectId member = after.memberIds().get(index);
            state = HiveMobilizationProcess.reduceCocoonReleased(state, fixture.hive(), new HiveMobilizationCocoonReleased(after.id(), member));
        }
        HiveMobilization assembled = state.hiveColony().mobilizations().get(after.id());
        assertEquals(HiveMobilizationStatus.ASSEMBLING, assembled.status());
        HiveTaskAssembly assembly = assembled.assembly().orElseThrow();
        assertEquals(assembled.memberIds(), assembly.members().keySet().stream()
                .sorted(java.util.Comparator.comparingInt(assembled.memberIds()::indexOf)).toList(),
                "assembly retains the same exact group rather than reselecting ambient bioforms");
        assertTrue(assembly.members().values().stream().allMatch(member -> member.topology().edges().stream()
                .allMatch(edge -> edge.kind() == TraversalKind.GROUND_BIOFORM
                        && edge.capabilities().equals(java.util.Set.of(TraversalCapability.GROUND_BIOFORM)))),
                "every retained assembly edge is a distinct bioform topology, not a pedestrian or fixed-coordinate shortcut");
        FrontierWorldState assembledState = state;
        assertTrue(assembled.memberIds().stream().allMatch(member -> HivePhysiologySupport.permitsAmbientLease(assembledState, member)));
        assertTrue(assembled.memberIds().stream().noneMatch(member -> HivePhysiologySupport.availableForIndependentOperation(assembledState, member)),
                "the assembled group remains owned by its original task until its later departure boundary");
        assertTrue(AmbientActorProcess.nextLease(state, first, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(203L)).actorId().equals(first));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test void finalAssemblyRetainsASurveyedNonFlatGanglionApproach() {
        Mobilized flat = start(fixture());
        SurfaceAnchor raisedStage = HiveAssemblyPortPlan.compile(flat.state().bootstrap(), flat.state().hiveColony(), flat.mobilization())
                .memberStagingSurfaces().get(flat.mobilization().memberIds().getFirst());
        TerrainSurfacePlan steppedTerrain = TerrainSurfacePlan.uniform(63)
                .withSurveyedSupport(raisedStage.x(), raisedStage.z(), 64);

        Mobilized assembled = assemble(fixture(steppedTerrain));
        HiveTaskAssembly plan = assembled.mobilization().assembly().orElseThrow();
        HiveTaskAssembly.Member first = plan.members().get(assembled.mobilization().memberIds().getFirst());

        assertEquals(64, first.destinationSurface().y(), "the semantic Ganglion port consumes its surveyed terrain datum");
        assertTrue(first.topology().edges().stream().anyMatch(edge -> edge.grade() == 1),
                "a retained bioform topology must include the surveyed one-block approach rather than flattening or teleporting it");
        assertTrue(first.topology().edges().stream().allMatch(edge -> edge.grade() <= 1
                        && edge.kind() == TraversalKind.GROUND_BIOFORM
                        && edge.traversableBy(TraversalCapability.GROUND_BIOFORM)),
                "the non-flat approach remains a bounded open bioform topology");
    }

    @Test void coldAssemblyProgressAdvancesOnlyOneRetainedCursorAndNeverReplaysIt() {
        Mobilized assembled = assemble(fixture());
        HiveMobilization mobilization = assembled.mobilization();
        HiveTaskAssembly initial = mobilization.assembly().orElseThrow();
        SubjectId advancing = initial.safeAdvances().getFirst();
        ScheduledAction action = HiveMobilizationProcess.assemblyProgress(mobilization.id(), 300L);

        List<ProposedEvent> planned = HiveMobilizationProcess.planAssemblyProgress(assembled.state(), action);
        HiveMobilizationAssemblyAdvanced advanced = assertInstanceOf(HiveMobilizationAssemblyAdvanced.class, planned.getFirst().payload());
        assertEquals(advancing, advanced.bioformId());
        assertEquals(advanced, FrontierWorldRuntimeDefinition.payloadCodecs().decode(advanced.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(advanced)), "the exact expected cursor must survive WAL replay");
        assertTrue(planned.stream().anyMatch(event -> event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created),
                "an incomplete task receives only its next retained COLD clock");

        FrontierWorldState progressed = HiveMobilizationProcess.reduceAssemblyAdvanced(assembled.state(), assembled.state().bootstrap().hive().id(), advanced);
        HiveTaskAssembly next = progressed.hiveColony().mobilizations().get(mobilization.id()).assembly().orElseThrow();
        assertEquals(initial.members().get(advancing).cursor() + 1, next.members().get(advancing).cursor());
        assertEquals(next.members().get(advancing).currentSurface(), progressed.actorLocations().get(advancing).supportingSurface());
        assertEquals(initial.members().size(), next.members().size(), "the COLD clock cannot replace or reselect a group member");
        assertThrows(IllegalArgumentException.class, () -> HiveMobilizationProcess.reduceAssemblyAdvanced(progressed,
                progressed.bootstrap().hive().id(), advanced), "a stale event may not replay the same retained edge");
        assertEquals(progressed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(progressed)));
    }

    @Test void coldAssemblyProgressWaitsForTheWholeExactGroupToLeaveAmbientHotCustody() {
        Mobilized assembled = assemble(fixture());
        HiveMobilization mobilization = assembled.mobilization();
        SubjectId hotMember = mobilization.memberIds().getFirst();
        java.util.Map<SubjectId, AmbientActorLease> leases = new java.util.LinkedHashMap<>();
        leases.put(hotMember, new AmbientActorLease(hotMember, assembled.state().actorLocations().get(hotMember).body(),
                new SimInstant(300L), 1L, AmbientLeaseStatus.HOT, AmbientGoalKind.WORK,
                assembled.state().actorLocations().get(hotMember).body()));
        FrontierWorldState hot = assembled.state().withChanges(FrontierWorldStateUpdate.begin().ambientLeases(leases));

        List<ProposedEvent> deferred = HiveMobilizationProcess.planAssemblyProgress(hot,
                HiveMobilizationProcess.assemblyProgress(mobilization.id(), 300L));

        assertEquals(1, deferred.size(), "one HOT member defers the whole retained group rather than mixing body authorities");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class, deferred.getFirst().payload());
        assertThrows(IllegalArgumentException.class, () -> HiveMobilizationProcess.reduceAssemblyAdvanced(hot,
                hot.bootstrap().hive().id(), new HiveMobilizationAssemblyAdvanced(mobilization.id(), hotMember,
                        mobilization.assembly().orElseThrow().members().get(hotMember).cursor())));
    }

    @Test void hotAssemblyArrivalAdvancesTheSameCursorThenRetargetsOnlyThatExactBody() {
        Mobilized assembled = assemble(fixture());
        HiveMobilization mobilization = assembled.mobilization();
        SubjectId actorId = mobilization.assembly().orElseThrow().safeAdvances().getFirst();
        HiveTaskAssembly.Member before = mobilization.assembly().orElseThrow().members().get(actorId);
        AmbientActorLease lease = new AmbientActorLease(actorId, assembled.state().actorLocations().get(actorId).body(), new SimInstant(300L), 1L,
                AmbientLeaseStatus.HOT, AmbientGoalKind.HIVE_TASK_ASSEMBLY, before.nextSurface().standingBody());
        FrontierWorldState hot = assembled.state().withChanges(FrontierWorldStateUpdate.begin().ambientLeases(java.util.Map.of(actorId, lease)));

        assertEquals(AmbientGoalKind.HIVE_TASK_ASSEMBLY, AmbientActorProcess.nextLease(assembled.state(), actorId, new SimInstant(300L)).goal());
        FrontierWorldState advanced = HiveMobilizationProcess.reduceAssemblyAdvanced(hot, hot.bootstrap().hive().id(),
                new HiveMobilizationAssemblyAdvanced(mobilization.id(), actorId, before.cursor()));

        HiveTaskAssembly.Member after = advanced.hiveColony().mobilizations().get(mobilization.id()).assembly().orElseThrow().members().get(actorId);
        assertEquals(before.cursor() + 1, after.cursor());
        assertEquals(after.currentSurface().standingBody(), advanced.actorLocations().get(actorId).body());
        assertEquals(after.arrived() ? after.currentSurface().standingBody() : after.nextSurface().standingBody(),
                advanced.ambientLeases().get(actorId).goalBody(), "the HOT body receives only its next retained edge");
        assertThrows(IllegalArgumentException.class, () -> HiveMobilizationProcess.reduceAssemblyAdvanced(advanced,
                advanced.bootstrap().hive().id(), new HiveMobilizationAssemblyAdvanced(mobilization.id(), actorId, before.cursor())),
                "a stale physical arrival must not replay an already observed edge");
    }

    @Test void onlyALoadedAssemblyPathBlockMayConflictAnAssemblingMobilization() {
        Mobilized assembled = assemble(fixture());
        HiveTaskAssembly assembly = assembled.mobilization().assembly().orElseThrow();
        SubjectId actor = assembly.safeAdvances().getFirst();
        HiveTaskAssembly.Member member = assembly.members().get(actor);
        HiveAssemblyBlockage blockage = new HiveAssemblyBlockage(actor, member.cursor(), member.nextSurface());
        FrontierWorldState conflicted = HiveMobilizationProcess.reduceConflicted(assembled.state(), assembled.state().bootstrap().hive().id(),
                new HiveMobilizationConflicted(assembled.mobilization().id(), HiveMobilizationConflictReason.ASSEMBLY_PATH_BLOCKED, java.util.Optional.of(blockage)));

        assertEquals(HiveMobilizationStatus.CONFLICT, conflicted.hiveColony().mobilizations().get(assembled.mobilization().id()).status());
        assertEquals(java.util.Optional.of(blockage), conflicted.hiveColony().mobilizations().get(assembled.mobilization().id()).assemblyBlockage());
        assertThrows(IllegalArgumentException.class, () -> HiveMobilizationProcess.reduceConflicted(assembled.state(), assembled.state().bootstrap().hive().id(),
                new HiveMobilizationConflicted(assembled.mobilization().id(), HiveMobilizationConflictReason.ASSEMBLY_PATH_BLOCKED,
                        java.util.Optional.of(new HiveAssemblyBlockage(actor, member.cursor() + 1, member.nextSurface())))));
        assertThrows(IllegalArgumentException.class, () -> HiveMobilizationProcess.reduceConflicted(assembled.state(),
                assembled.state().bootstrap().hive().id(), new HiveMobilizationConflicted(assembled.mobilization().id(),
                        HiveMobilizationConflictReason.COCOON_CHANGED)));
    }

    @Test void playerBrokenCocoonInterruptsOnlyItsInFlightMobilizationAndReleasesTheExactOccupant() {
        Fixture fixture = fixture();
        List<ProposedEvent> planned = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload()));
        HiveMobilizationStarted started = assertInstanceOf(HiveMobilizationStarted.class, planned.get(1).payload());
        state = HiveMobilizationProcess.reduceStarted(state, fixture.hive(), started);
        HiveMobilization mobilization = state.hiveColony().mobilizations().get(started.mobilization().id());
        SubjectId broken = mobilization.memberIds().get(1);
        HiveCocoonSlot slot = state.hiveColony().bioformLifecycles().get(broken).homeSlot().orElseThrow();
        HiveOrgan hibernaculum = state.bootstrap().hive().organs().stream()
                .filter(organ -> organ.id().equals(slot.hibernaculumId())).findFirst().orElseThrow();
        BlockPosition position = HiveCocoonPlan.cocoonCell(hibernaculum, slot);

        state = state.recordPhysicalDelta(new PhysicalDelta(position, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(broken), Optional.of(GrayboxSemanticPart.COCOON), "player:test"));

        HiveMobilization conflicted = state.hiveColony().mobilizations().get(mobilization.id());
        assertEquals(HiveMobilizationStatus.CONFLICT, conflicted.status());
        assertEquals(Optional.of(HiveMobilizationConflictReason.COCOON_CHANGED), conflicted.conflictReason());
        assertEquals(BioformLifecyclePhase.WAKING, state.hiveColony().bioformLifecycles().get(broken).phase());
        assertTrue(HivePhysiologySupport.permitsAmbientLease(state, broken));
        FrontierWorldState conflictedState = state;
        assertThrows(IllegalArgumentException.class, () -> AmbientActorProcess.nextLease(conflictedState, mobilization.memberIds().getFirst(),
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(202L)));
    }

    @Test void changedOrRestartUnconfirmedCocoonConflictsWithoutMovingOrLeasingItsExactOccupant() {
        Fixture fixture = fixture();
        List<ProposedEvent> planned = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload()));
        HiveMobilizationStarted started = assertInstanceOf(HiveMobilizationStarted.class, planned.get(1).payload());
        state = HiveMobilizationProcess.reduceStarted(state, fixture.hive(), started);
        HiveMobilization mobilization = state.hiveColony().mobilizations().get(started.mobilization().id());
        SubjectId first = mobilization.memberIds().getFirst();
        BodyPosition original = state.actorLocations().get(first).body();
        state = HiveMobilizationProcess.reduceReleaseStarted(state, fixture.hive(), new HiveMobilizationReleaseStarted(mobilization.id()));
        state = HiveMobilizationProcess.reduceConflicted(state, fixture.hive(),
                new HiveMobilizationConflicted(mobilization.id(), HiveMobilizationConflictReason.UNKNOWN_AFTER_RESTART));

        HiveMobilization conflicted = state.hiveColony().mobilizations().get(mobilization.id());
        assertEquals(HiveMobilizationStatus.CONFLICT, conflicted.status());
        assertEquals(Optional.of(HiveMobilizationConflictReason.UNKNOWN_AFTER_RESTART), conflicted.conflictReason());
        assertEquals(original, state.actorLocations().get(first).body());
        FrontierWorldState conflictedState = state;
        assertThrows(IllegalArgumentException.class, () -> AmbientActorProcess.nextLease(conflictedState, first,
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(202L)));
    }

    private static Mobilized start(Fixture fixture) {
        List<ProposedEvent> planned = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload()));
        HiveMobilizationStarted started = assertInstanceOf(HiveMobilizationStarted.class, planned.get(1).payload());
        state = HiveMobilizationProcess.reduceStarted(state, fixture.hive(), started);
        return new Mobilized(state, state.hiveColony().mobilizations().get(started.mobilization().id()));
    }

    private static Mobilized assemble(Fixture fixture) {
        Mobilized started = start(fixture);
        FrontierWorldState state = started.state();
        for (SubjectId member : started.mobilization().memberIds()) {
            HiveMobilization current = state.hiveColony().mobilizations().get(started.mobilization().id());
            state = HiveMobilizationProcess.reduceReleaseStarted(state, fixture.hive(), new HiveMobilizationReleaseStarted(current.id()));
            state = HiveMobilizationProcess.reduceCocoonReleased(state, fixture.hive(), new HiveMobilizationCocoonReleased(current.id(), member));
        }
        HiveMobilization mobilization = state.hiveColony().mobilizations().get(started.mobilization().id());
        return new Mobilized(state, mobilization);
    }

    private static Fixture fixture() { return fixture(TerrainSurfacePlan.uniform(63)); }

    private static Fixture fixture(TerrainSurfacePlan terrain) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-mobilization"), 8128L,
                FrontierRulesets.production(), terrain));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        FrontierWorldState initial = state;
        Bioform scout = initial.bootstrap().hive().bioforms().stream().filter(Bioform::isScout)
                .filter(bioform -> initial.hiveColony().bioformLifecycles().get(bioform.id()).phase() == BioformLifecyclePhase.ACTIVE).findFirst().orElseThrow();
        HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(settlement.id(), scout.id(), settlement.anchor(), 100L);
        InfectionCell cell = InfectionCell.at(settlement.anchor());
        state = state.withInfection(cell, new FixedRatio(FixedScalar.ONE));
        StrategicPlanState plans = StrategicPlanState.empty()
                .withHiveSettlementKnowledge(new HiveSettlementKnowledge(java.util.Map.of(settlement.id(), sighting)))
                .withHiveTerritoryKnowledge(new HiveTerritoryKnowledge(java.util.Map.of(cell,
                        new HiveTerritoryKnowledge.Belief(cell, new FixedRatio(FixedScalar.ONE), scout.id(), settlement.anchor(), 100L))))
                .withHiveDoctrine(new HiveDoctrineState(HiveDoctrine.INTERDICT, 100L));
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-mobilization"), hive,
                StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-mobilization"), objective.id(), hive,
                StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD,
                StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.PENDING);
        return new Fixture(state.withStrategicPlans(plans.addObjective(objective).addTask(task)), hive, task, sighting);
    }

    private record Fixture(FrontierWorldState state, SubjectId hive, StrategicTask task, HiveSettlementKnowledge.Sighting sighting) { }
    private record Mobilized(FrontierWorldState state, HiveMobilization mobilization) { }
}
