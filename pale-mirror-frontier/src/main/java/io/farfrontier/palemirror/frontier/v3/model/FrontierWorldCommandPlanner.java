package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection; import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition; import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition; import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessRegistry;
import io.farfrontier.palemirror.frontier.v3.kernel.EngineLimits;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionCommitter;
import java.util.List;
/**
 * Trusted physical-command policy for the closed Frontier world composition.
 *
 * <p>It is deliberately separate from the composition root: process admission stays
 * explicit and deterministic while later H0.3 work splits these domain branches into
 * registered process modules.</p>
 */
final class FrontierWorldCommandPlanner {
    private FrontierWorldCommandPlanner() { }
    static CommandPlan plan(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command,
                            DeterministicProcessRegistry processRegistry) {
        if (!FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR.equals(command.actor())) {
            return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                    io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, "command is not from the trusted physical executor"));
        }
        final String processId;
        try { processId = processRegistry.requireCommandOwner(command.payload().type()); }
        catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        CommandPlan plan = planCommandUnchecked(state, command);
        if (plan instanceof CommandPlan.Accepted accepted) {
            return new CommandPlan.Accepted(processRegistry.validateEmissions(processId, accepted.events()));
        }
        return plan;
    }
    private static CommandPlan planCommandUnchecked(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command) {
        if (command.payload() instanceof ResidentBorn birth) {
            return rejected("resident birth is emitted only by a confirmed population permit");
        }
        if (command.payload() instanceof ResidentMigrated migration) {
            try { state.recordResidentMigration(migration); } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(migration.destinationSettlementId(), migration)));
        }
        if (command.payload() instanceof ResidentTransitAdvanced advanced) {
            ResidentMigrationJourney journey = state.humanPopulation().migration(advanced.residentId());
            if (journey == null) return rejected("HOT transit observation has no active migration journey");
            try { PopulationMigrationProcess.reduceHotAdvance(state, advanced); } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(journey.originSettlementId(), advanced)));
        }
        if (command.payload() instanceof ScoutPatrolAdvanced advanced) {
            try { HiveScoutPatrolProcess.reduce(state, state.bootstrap().hive().id(), advanced); }
            catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), advanced)));
        }
        if (command.payload() instanceof HotScoutOperationObserved observed) {
            try { HivePerceptionProcess.reduceHot(state, state.bootstrap().hive().id(), observed); }
            catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            HiveOperationKnowledge.Sighting sighting = new HiveOperationKnowledge.Sighting(observed.operationId(), observed.scoutId(),
                    observed.seenCarrierPosition(), observed.observedAt());
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), observed),
                    new ProposedEvent(state.bootstrap().hive().id(), new ScheduleEffect.Created(
                            StrategicObjectiveProcess.interceptOpportunity(state.bootstrap().hive().id(), sighting,
                                    Math.addExact(command.submittedAt().ticks(), 1L))))));
        }
        if (command.payload() instanceof OperationAssemblyAdvanced advanced) {
            RouteOperation operation = state.operations().get(advanced.operationId());
            if (operation == null) return rejected("operation assembly observation has no active operation");
            try {
                validateHotAssemblyObservation(state, operation, advanced.assembly());
                state.advanceOperationAssembly(advanced.operationId(), advanced.assembly());
            } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            List<ProposedEvent> events = new java.util.ArrayList<>(List.of(new ProposedEvent(operation.settlementId(), advanced)));
            if (advanced.assembly().complete()) {
                events.add(new ProposedEvent(operation.settlementId(), new OperationTravelStarted(operation.id(),
                        SupplyOperationProcess.travelForCompletedAssembly(operation, advanced.assembly()))));
                events.add(new ProposedEvent(operation.id(), new ScheduleEffect.Created(
                        SupplyOperationProcess.operationProgress(operation, command.submittedAt().ticks() + 20L))));
            }
            return new CommandPlan.Accepted(List.copyOf(events));
        }
        if (command.payload() instanceof OperationAssemblyDeferred deferred) {
            RouteOperation operation = state.operations().get(deferred.operationId());
            if (operation == null) return rejected("operation assembly deferral has no active operation");
            try {
                validateHotAssemblyDeferral(state, operation, deferred.deferral());
                state.deferOperationAssembly(deferred.operationId(), deferred.deferral());
            } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), deferred)));
        }
        if (command.payload() instanceof OperationTravelSegmentCompleted completed) {
            RouteOperation operation = state.operations().get(completed.operationId());
            if (operation == null) return rejected("operation travel completion has no active operation");
            try { state.completeOperationTravelSegment(completed.operationId()); } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), completed)));
        }
        if (command.payload() instanceof OperationTravelAdvanced advanced) {
            RouteOperation operation = state.operations().get(advanced.operationId());
            if (operation == null) return rejected("operation travel observation has no active operation");
            try { state.advanceOperationTravel(advanced.operationId(), advanced.travel()); } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), advanced)));
        }
        if (command.payload() instanceof OperationTravelStarted started) {
            RouteOperation operation = state.operations().get(started.operationId());
            if (operation == null) return rejected("operation travel start has no active operation");
            try { state.startOperationTravel(started.operationId(), started.travel()); } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), started)));
        }
        if (command.payload() instanceof PhysicalIntentTransition || command.payload() instanceof PhysicalIntentPrepared) {
            return FrontierPhysicalIntentCommandProcess.plan(state, command);
        }
        if (command.payload() instanceof SceneLeasePrepared prepared) {
            RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(prepared.lease()).operationId()); if (operation == null) return rejected("scene lease has no owning operation");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), prepared)));
        }
        if (command.payload() instanceof SettlementAssaultSceneLeasePrepared prepared) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSettlementAssaultSceneSupport.owner(state, prepared.lease()), prepared))); }
            catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof SceneLeaseHandoff handoff) {
            RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(handoff.lease()).operationId()); if (operation == null) return rejected("scene hand-off has no owning operation");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), handoff)));
        }
        if (command.payload() instanceof SettlementAssaultSceneLeaseHandoff handoff) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSettlementAssaultSceneSupport.owner(state, handoff.lease()), handoff))); }
            catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof SceneLeaseTransition transition) {
            SceneLease lease = state.sceneLeases().get(transition.leaseId()); if (lease == null) return rejected("scene lease is unknown");
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSceneOwnerSupport.owner(state, lease), transition))); }
            catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof SceneLeaseReleased released) {
            SceneLease lease = state.sceneLeases().get(released.leaseId()); if (lease == null) return rejected("scene lease is unknown");
            if (FrontierSceneBehaviors.isSettlementAssault(lease)) {
                try {
                    SettlementAssault assault = FrontierSettlementAssaultSceneSupport.require(state, FrontierSceneBehaviors.settlementAssault(lease));
                    return new CommandPlan.Accepted(List.of(new ProposedEvent(assault.hiveId(), released), new ProposedEvent(assault.hiveId(),
                            new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(HiveSettlementAssaultProcess.combat(assault, command.submittedAt().ticks() + 20L)))));
                } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            }
            LogisticsSceneCause logistics = FrontierSceneBehaviors.logistics(lease);
            RouteOperation operation = state.operations().get(logistics.operationId());
            if (operation == null) return rejected("scene lease has no owning operation");
            if (logistics.engagementId().isPresent()) {
                RouteEngagement engagement = state.strategicPlans().routeEngagements().get(logistics.engagementId().orElseThrow());
                if (engagement == null) return rejected("scene lease has no canonical engagement");
                if (engagement.status() == RouteEngagementStatus.HOT) {
                    return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), released),
                            new ProposedEvent(operation.settlementId(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(
                                    HiveRouteEngagementProcess.combat(engagement, command.submittedAt().ticks() + 20L)))));
                }
                // A real blast/player interaction may legitimately destroy the cargo while the
                // scene is HOT. Cargo release atomically interrupts the operation and aborts its
                // engagement; remaining bodies must drain and close, never restart COLD combat.
                if (operation.stage() == OperationStage.INTERRUPTED && engagement.status() == RouteEngagementStatus.RESOLVED
                        && engagement.outcome().filter(outcome -> outcome == RouteEngagementOutcome.ABORTED).isPresent()) {
                    return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), released)));
                }
                return rejected("scene lease cannot resume its interrupted engagement");
            }
            if (operation.participantIds().stream().anyMatch(actor -> state.actorLocations().get(actor).condition().status() == ActorLifeStatus.DEAD)) {
                List<ProposedEvent> events = new java.util.ArrayList<>(); events.add(new ProposedEvent(operation.settlementId(), released));
                events.addAll(SupplyOperationProcess.failed(state, operation, "actor-death")); return new CommandPlan.Accepted(List.copyOf(events));
            }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), released),
                    new ProposedEvent(operation.settlementId(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(
                            SupplyOperationProcess.operationProgress(operation, command.submittedAt().ticks() + 100L)))));
        }
        if (command.payload() instanceof SceneLeaseRecoveryUnresolved unresolved) {
            SceneLease lease = state.sceneLeases().get(unresolved.leaseId());
            if (lease == null || lease.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART || lease.recoveryEvidence().isPresent()) {
                return rejected("scene recovery evidence does not bind one unresolved restart lease");
            }
            if (FrontierSceneBehaviors.isSettlementAssault(lease)) {
                try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSceneOwnerSupport.owner(state, lease), unresolved))); }
                catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            }
            LogisticsSceneCause logistics = FrontierSceneBehaviors.logistics(lease);
            if (logistics.engagementId().isPresent()) return rejected("engagement scene recovery needs its own outcome policy");
            RouteOperation operation = state.operations().get(logistics.operationId());
            if (operation == null || operation.stage() != OperationStage.EN_ROUTE) return rejected("scene recovery evidence has no active route operation");
            try {
                List<ProposedEvent> events = new java.util.ArrayList<>();
                events.add(new ProposedEvent(operation.settlementId(), unresolved));
                events.addAll(SupplyOperationProcess.failed(state, operation, "scene-recovery-unresolved"));
                return new CommandPlan.Accepted(List.copyOf(events));
            } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ActorDied death) {
            SceneLease lease = state.sceneLeases().get(death.leaseId());
            if (lease == null || (lease.status() != SceneLeaseStatus.HOT && lease.status() != SceneLeaseStatus.DRAINING)
                    || lease.members().stream().noneMatch(member -> member.actorId().equals(death.actorId()))) {
                return rejected("actor death is not evidence for an active scene member");
            }
            SubjectId owner;
            try { owner = FrontierSceneOwnerSupport.owner(state, lease); } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            List<ProposedEvent> events = new java.util.ArrayList<>();
            events.add(new ProposedEvent(owner, death));
            CompanyFoundationProcess.terminationForDeath(state, death.actorId()).ifPresent(events::add);
            events.addAll(ProductionProcess.failPreEffectWorkForDeath(state, death.actorId()));
            if (lease.status() == SceneLeaseStatus.HOT) {
                events.add(new ProposedEvent(owner, new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING)));
            }
            return new CommandPlan.Accepted(List.copyOf(events));
        }
        if (command.payload() instanceof AmbientActorDied death) return AmbientActorProcess.plan(state, death);
        if (command.payload() instanceof AmbientActorObserved observation) return AmbientActorProcess.plan(state, observation);
        if (command.payload() instanceof AmbientLeasePrepared || command.payload() instanceof AmbientLeaseTransition || command.payload() instanceof AmbientLeaseReleased) return AmbientActorProcess.planLease(state, command.payload());
        if (command.payload() instanceof ResourceSiteConflictObserved conflict) try { return new CommandPlan.Accepted(ResourceSiteProcess.planConflict(state, conflict)); }
        catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        if (command.payload() instanceof StructureDamaged damage) {
            try {
                state.recordStructureDamage(damage);
            } catch (IllegalArgumentException invalid) {
                return rejected(invalid.getMessage());
            }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierWorldStateSupport.structureSettlement(state.bootstrap(), damage.structureId()), damage)));
        }
        if (command.payload() instanceof PhysicalDeltaObserved observed) return FrontierWorldPhysicalObservationProcess.plan(state, observed, command.submittedAt().ticks());
        if (command.payload() instanceof ResourceDeposited deposited) return FrontierWorldPhysicalObservationProcess.planResourceDeposit(state, deposited);
        if (command.payload() instanceof ExactItemCustodyChanged changed) {
            ExactItemStack item = state.inventory().items().get(changed.itemId());
            if (item == null || !item.custody().equals(changed.from())) return rejected("observed item source differs from canonical custody");
            ProposedEvent observation = new ProposedEvent(FrontierWorldStateSupport.itemOwner(state, changed), changed);
            try { return new CommandPlan.Accepted(ProductionProcess.planMaterializedInputDeparture(state, changed.itemId(), observation)); }
            catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ExactItemDestroyed destroyed) {
            ExactItemStack item = state.inventory().items().get(destroyed.itemId());
            if (item == null || !item.custody().equals(destroyed.source())) return rejected("destroyed item source differs from canonical custody");
            ProposedEvent observation = new ProposedEvent(FrontierWorldStateSupport.itemOwner(state, destroyed), destroyed);
            try { return new CommandPlan.Accepted(ProductionProcess.planMaterializedInputDeparture(state, destroyed.itemId(), observation)); }
            catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof CargoCarrierReleased released) {
            SceneLease lease = state.sceneLeases().get(released.leaseId());
            if (lease == null || !FrontierSceneBehaviors.isLogistics(lease) || !FrontierSceneBehaviors.logistics(lease).cargoId().equals(released.cargoId()) || lease.status() != SceneLeaseStatus.HOT) {
                return rejected("cargo carrier release lacks one HOT matching scene lease");
            }
            if (!CargoCarrierIdentity.id(lease).equals(released.carrierId())) return rejected("cargo carrier identity is not canonical for its scene");
            RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
            if (operation == null) return rejected("cargo carrier release has no owning operation");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), released)));
        }
        if (command.payload() instanceof InventoryConflictObserved observed) {
            InventoryConflict conflict = observed.conflict();
            ContainerRecord container = state.inventory().containers().get(conflict.containerId());
            if (container == null || conflict.slot() >= container.slotCount()
                    || (!state.inventory().items().containsKey(conflict.subjectId()) && !state.inventory().containers().containsKey(conflict.subjectId()))) {
                return rejected("inventory conflict references an unknown exact surface");
            }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(container.ownerId(), observed)));
        }
        if (command.payload() instanceof ContainerSurfaceTransition transition) {
            return ContainerSurfaceProcess.plan(state, transition);
        }
        return rejected("command is not a trusted physical transition or scene lease");
    }
    private static CommandPlan.Rejected rejected(String message) { return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, message));
    }
    private static void validateHotAssemblyObservation(FrontierWorldState state, RouteOperation operation, OperationAssembly next) {
        OperationAssembly current = operation.activeAssembly().orElseThrow(() -> new IllegalArgumentException("operation has no active assembly"));
        if (!current.members().keySet().equals(next.members().keySet())) throw new IllegalArgumentException("HOT assembly observation changes formation");
        SubjectId observed = null;
        for (SubjectId actor : current.members().keySet()) {
            OperationAssembly.Member before = current.members().get(actor), after = next.members().get(actor);
            if (!before.corridor().equals(after.corridor()) || after.cursor() < before.cursor() || after.cursor() > before.cursor() + 1) {
                throw new IllegalArgumentException("HOT assembly observation may advance only one adjacent cursor");
            }
            if (after.cursor() > before.cursor()) {
                if (observed != null) throw new IllegalArgumentException("HOT assembly observation may acknowledge only one actor");
                observed = actor;
            }
        }
        if (observed == null) throw new IllegalArgumentException("HOT assembly observation did not advance an actor");
        if (current.deferral().isPresent()) {
            OperationAssemblyDeferral blocked = current.deferral().orElseThrow();
            if (!blocked.actorId().equals(observed) || !next.members().get(observed).currentPosition().equals(blocked.target())) {
                throw new IllegalArgumentException("HOT assembly observation may not bypass a loaded-world assembly deferral");
            }
        }
        AmbientActorLease lease = state.ambientLeases().get(observed);
        OperationAssembly.Member arrived = next.members().get(observed);
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.OPERATION_ASSEMBLY
                || !lease.goalPosition().equals(arrived.currentPosition())) {
            throw new IllegalArgumentException("HOT assembly observation lacks its exact active actor lease");
        }
    }
    private static void validateHotAssemblyDeferral(FrontierWorldState state, RouteOperation operation, OperationAssemblyDeferral deferral) {
        OperationAssembly assembly = operation.activeAssembly().orElseThrow(() -> new IllegalArgumentException("operation has no active assembly"));
        OperationAssembly.Member member = assembly.members().get(deferral.actorId());
        AmbientActorLease lease = state.ambientLeases().get(deferral.actorId());
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), operation.settlementId());
        SettlementStructure hall = settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("assembly settlement has no Hall access port"));
        SettlementAccessPort access = SettlementAccessPort.forHall(hall);
        if (member == null || member.arrived() || !member.corridor().get(member.cursor() + 1).equals(deferral.target())
                || lease == null || lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.OPERATION_ASSEMBLY
                || !lease.goalPosition().equals(deferral.target())
                || (!deferral.obstructionFloor().equals(deferral.target()) && !deferral.obstructionFloor().equals(access.throatFloor()))) {
            throw new IllegalArgumentException("HOT assembly deferral lacks its exact active actor lease");
        }
}
}
