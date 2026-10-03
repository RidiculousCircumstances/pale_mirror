package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.Set;

/** Exact reducer owner for route construction and patrol facts. */
final class FrontierInfrastructureProcessModule implements FrontierWorldProcessModule {
    @Override public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() {
        return List.of(engineeringCapability(), new NoPhysicalIntentLifecyclePolicy(
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.ROUTE_PATROL));
    }

    private static PhysicalIntentLifecycleCapability engineeringCapability() {
        return new FunctionalPhysicalIntentLifecycleCapability(
                PhysicalIntentLifecycleDeclaration.physical(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE,
                Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.STRUCTURAL_REPAIR,
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_CONSTRUCTION,
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING,
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE,
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING,
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_ISSUE,
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_RETURN),
                        Set.of(PhysicalIntentRoleSchema.STRUCTURAL_REPAIR, PhysicalIntentRoleSchema.ROUTE_CONSTRUCTION,
                                PhysicalIntentRoleSchema.ROUTE_CONSTRUCTION_LOADING, PhysicalIntentRoleSchema.ROUTE_MAINTENANCE,
                                PhysicalIntentRoleSchema.ROUTE_MAINTENANCE_LOADING, PhysicalIntentRoleSchema.ENGINEERING_EQUIPMENT_ISSUE,
                                PhysicalIntentRoleSchema.ENGINEERING_EQUIPMENT_RETURN)),
                FrontierInfrastructureProcessModule::planEngineeringPreparation,
                FrontierInfrastructureProcessModule::planEngineeringTransition,
                FrontierInfrastructureProcessModule::reduceEngineeringPreparation,
                FrontierInfrastructureProcessModule::reduceEngineeringTransition,
                PhysicalIntentLifecycleRetirementPolicy.of(
                        FrontierInfrastructureProcessModule::planEngineeringTransition,
                        FrontierInfrastructureProcessModule::reduceEngineeringTransition),
                FrontierInfrastructureProcessModule::engineeringRecoveryAsset,
                retirementAccount(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE),
                PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery(), PhysicalIntentRecoveryDiagnosticProducer.ENGINEERING_WORKSITE);
    }

    private static PhysicalIntentRetirementAccount retirementAccount(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner owner) {
        return PhysicalIntentRetirementAccount.declared(owner,
                java.util.EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                (before, command, intent, transition) -> retirementFacts(before, command, intent, transition, owner),
                (before, intent, transition, binding) -> PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding,
                        retirementFacts(before, binding.continuation(), intent, transition, owner)),
                (before, after, intent, transition, binding) -> {
                    if (intent.lifecycleOwner() != owner) throw new IllegalArgumentException("engineering retirement account owner mismatch");
                    if (after == before) return;
                    if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) return;
                    if (binding.commitment() instanceof PhysicalIntentRetirementAccount.Exact<SubjectId> exact
                            && (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_ISSUE
                            || intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_RETURN)
                            && !after.inventory().items().containsKey(exact.value())) {
                        throw new IllegalArgumentException("engineering retirement lost its exact equipment commitment");
                    }
                });
    }

    private static PhysicalIntentRetirementAccount.Binding retirementFacts(FrontierWorldState state, FrontierCommand command,
            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent, PhysicalIntentTransition transition,
            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner owner) {
        return retirementFacts(state, command == null
                ? new PhysicalIntentRetirementAccount.CheckedNone<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION)
                : command.scheduleBinding().<PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>>map(value -> new PhysicalIntentRetirementAccount.Exact<>(value.action().id()))
                        .orElseGet(() -> new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION)), intent, transition, owner);
    }

    private static PhysicalIntentRetirementAccount.Binding retirementFacts(FrontierWorldState state,
            PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.ScheduleId> continuation,
            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent, PhysicalIntentTransition transition,
            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner owner) {
        PhysicalIntentRetirementAccount.Obligation<SubjectId> carrier;
        PhysicalIntentRetirementAccount.Obligation<SubjectId> commitment = new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_RESOURCE_COMMITMENT);
        switch (intent.kind()) {
            case STRUCTURAL_REPAIR -> {
                StructuralRepairProcess.repairOwner(state, intent.semanticTarget().orElseThrow());
                carrier = new PhysicalIntentRetirementAccount.Exact<>(intent.causeSubjectId());
            }
            case ROUTE_CONSTRUCTION -> {
                RouteConstructionStateSupport.validateIntent(state, intent);
                carrier = new PhysicalIntentRetirementAccount.Exact<>(intent.causeSubjectId());
            }
            case ROUTE_MAINTENANCE -> {
                RouteMaintenanceStateSupport.validateWorkIntent(state, intent);
                carrier = new PhysicalIntentRetirementAccount.Exact<>(intent.causeSubjectId());
            }
            case ROUTE_CONSTRUCTION_MATERIAL_LOADING, ROUTE_MAINTENANCE_MATERIAL_LOADING -> {
                if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING) RouteConstructionStateSupport.validateMaterialLoadingIntent(state, intent);
                else RouteMaintenanceStateSupport.validateMaterialLoadingIntent(state, intent);
                carrier = new PhysicalIntentRetirementAccount.Exact<>(intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING
                        ? intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ROUTE_CONSTRUCTION_PROJECT)
                        : intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ROUTE_MAINTENANCE));
                commitment = new PhysicalIntentRetirementAccount.Exact<>(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.SOURCE_ITEM));
            }
            case EQUIPMENT_ISSUE, EQUIPMENT_RETURN -> {
                if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_ISSUE) EquipmentIssueStateSupport.validateIntent(state, intent);
                else EquipmentReturnStateSupport.validateIntent(state, intent);
                carrier = new PhysicalIntentRetirementAccount.Exact<>(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGINEERING_WORKER));
                commitment = new PhysicalIntentRetirementAccount.Exact<>(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.EQUIPMENT));
            }
            default -> throw new IllegalArgumentException("engineering retirement has undeclared intent kind");
        }
        // Engineering has no independent REL projection for these retained work aggregates;
        // each switch arm above executes its owner-specific aggregate validation.
        return new PhysicalIntentRetirementAccount.Binding(owner, intent.id(),
                new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_APPLICABLE_RELATION), continuation,
                carrier, commitment, transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                        ? PhysicalIntentRetirementAccount.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY : PhysicalIntentRetirementAccount.LateDisposition.REJECT_STALE_ONCE);
    }

    private static CommandPlan planEngineeringPreparation(FrontierWorldState state, FrontierCommand command, PhysicalIntentPrepared prepared) {
        var intent = prepared.intent();
        try {
            return switch (intent.kind()) {
                case STRUCTURAL_REPAIR -> new CommandPlan.Accepted(List.of(new ProposedEvent(
                        StructuralRepairProcess.repairOwner(state, intent.semanticTarget().orElseThrow()), prepared)));
                case ROUTE_CONSTRUCTION -> {
                    RouteConstructionStateSupport.validateIntent(state, intent);
                    if (!FrontierEngineeringWorkSceneSupport.permitsCurrentWorkIntent(state, intent)) {
                        yield FrontierWorldCommandPlanner.rejected("route construction physical work requires its current HOT engineering scene");
                    }
                    yield new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, prepared)));
                }
                case ROUTE_MAINTENANCE -> {
                    RouteMaintenanceStateSupport.validateWorkIntent(state, intent);
                    if (!FrontierEngineeringWorkSceneSupport.permitsCurrentWorkIntent(state, intent)) {
                        yield FrontierWorldCommandPlanner.rejected("route maintenance physical work requires its current HOT engineering scene");
                    }
                    yield new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, prepared)));
                }
                case ROUTE_MAINTENANCE_MATERIAL_LOADING -> {
                    RouteMaintenanceStateSupport.validateMaterialLoadingIntent(state, intent);
                    yield new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, prepared)));
                }
                case ROUTE_CONSTRUCTION_MATERIAL_LOADING -> {
                    RouteConstructionStateSupport.validateMaterialLoadingIntent(state, intent);
                    yield new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, prepared)));
                }
                case EQUIPMENT_ISSUE -> {
                    EquipmentIssueStateSupport.validateIntent(state, intent);
                    yield new CommandPlan.Accepted(List.of(new ProposedEvent(intent.causeSubjectId(), prepared)));
                }
                case EQUIPMENT_RETURN -> {
                    EquipmentReturnStateSupport.validateIntent(state, intent);
                    yield new CommandPlan.Accepted(List.of(new ProposedEvent(intent.causeSubjectId(), prepared)));
                }
                default -> throw new IllegalArgumentException("engineering capability received undeclared kind");
            };
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }

    private static FencedRecoveryAsset engineeringRecoveryAsset(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        return switch (intent.kind()) {
            case ROUTE_CONSTRUCTION_MATERIAL_LOADING, ROUTE_MAINTENANCE_MATERIAL_LOADING -> FencedRecoveryAsset.CARGO;
            case EQUIPMENT_RETURN -> FencedRecoveryAsset.CONTAINER;
            case STRUCTURAL_REPAIR, ROUTE_CONSTRUCTION, ROUTE_MAINTENANCE, EQUIPMENT_ISSUE -> FencedRecoveryAsset.EFFECT;
            default -> throw new IllegalArgumentException("engineering capability received undeclared recovery kind");
        };
    }

    private static CommandPlan planEngineeringTransition(FrontierWorldState state, FrontierCommand command,
                                                         io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                         PhysicalIntentTransition transition) {
        long now = command.submittedAt().ticks();
        try {
            return switch (intent.kind()) {
                case STRUCTURAL_REPAIR -> new CommandPlan.Accepted(List.of(new ProposedEvent(
                        StructuralRepairProcess.repairOwner(state, intent.semanticTarget().orElseThrow()), transition)));
                case ROUTE_CONSTRUCTION -> new CommandPlan.Accepted(RouteConstructionProcess.planTransition(state, intent, transition, now));
                case ROUTE_CONSTRUCTION_MATERIAL_LOADING -> new CommandPlan.Accepted(RouteConstructionProcess.planMaterialLoadingTransition(state, intent, transition, now));
                case ROUTE_MAINTENANCE -> new CommandPlan.Accepted(RouteMaintenanceProcess.planTransition(state, intent, transition, now));
                case ROUTE_MAINTENANCE_MATERIAL_LOADING -> new CommandPlan.Accepted(RouteMaintenanceProcess.planMaterialLoadingTransition(state, intent, transition, now));
                case EQUIPMENT_ISSUE -> {
                    if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING) EquipmentIssueStateSupport.validateIntent(state, intent);
                    yield new CommandPlan.Accepted(withEngineeringContinuation(state, intent, transition, now, false));
                }
                case EQUIPMENT_RETURN -> {
                    if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING) EquipmentReturnStateSupport.validateIntent(state, intent);
                    yield new CommandPlan.Accepted(withEngineeringContinuation(state, intent, transition, now, true));
                }
                default -> throw new IllegalArgumentException("engineering capability received undeclared kind");
            };
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }

    private static FrontierWorldState reduceEngineeringPreparation(FrontierWorldState state,
                                                                    io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                                    io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        return switch (intent.kind()) {
            case STRUCTURAL_REPAIR -> StructuralRepairProcess.reducePrepared(state, subject, intent);
            case ROUTE_CONSTRUCTION -> RouteConstructionProcess.reducePrepared(state, subject, intent);
            case ROUTE_MAINTENANCE, ROUTE_MAINTENANCE_MATERIAL_LOADING -> RouteMaintenanceProcess.reducePrepared(state, subject, intent);
            case ROUTE_CONSTRUCTION_MATERIAL_LOADING -> {
                if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction material pickup must be prepared by the route network");
                RouteConstructionStateSupport.validateMaterialLoadingIntent(state, intent);
                yield state.preparePhysicalIntent(intent);
            }
            case EQUIPMENT_ISSUE -> {
                EquipmentIssueStateSupport.validateIntent(state, intent);
                if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("equipment issue must be prepared by its settlement");
                yield state.preparePhysicalIntent(intent);
            }
            case EQUIPMENT_RETURN -> {
                EquipmentReturnStateSupport.validateIntent(state, intent);
                if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("equipment return must be prepared by its settlement");
                yield state.preparePhysicalIntent(intent);
            }
            default -> throw new IllegalArgumentException("engineering capability received undeclared kind");
        };
    }

    private static FrontierWorldState reduceEngineeringTransition(FrontierWorldState state,
                                                                   io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                                   io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                   PhysicalIntentTransition transition) {
        switch (intent.kind()) {
            case STRUCTURAL_REPAIR -> {
                if (!subject.equals(StructuralRepairProcess.repairOwner(state, intent.semanticTarget().orElseThrow()))) {
                    throw new IllegalArgumentException("engineering transition lacks its semantic owner");
                }
            }
            case ROUTE_CONSTRUCTION, ROUTE_CONSTRUCTION_MATERIAL_LOADING, ROUTE_MAINTENANCE, ROUTE_MAINTENANCE_MATERIAL_LOADING -> {
                if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("engineering transition lacks route-network ownership");
            }
            case EQUIPMENT_ISSUE -> {
                if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING) EquipmentIssueStateSupport.validateIntent(state, intent);
                if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("equipment issue transition lacks settlement ownership");
            }
            case EQUIPMENT_RETURN -> {
                if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING) EquipmentReturnStateSupport.validateIntent(state, intent);
                if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("equipment return transition lacks settlement ownership");
            }
            default -> throw new IllegalArgumentException("engineering capability received undeclared kind");
        }
        return PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                FrontierInfrastructureProcessModule::confirmEngineeringTransition,
                FrontierInfrastructureProcessModule::unknownEngineeringTransition);
    }

    private static FrontierWorldState confirmEngineeringTransition(FrontierWorldState state,
                                                                    io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                    PhysicalEffectObservation evidence,
                                                                    java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId,
                                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents) {
        return switch (intent.kind()) {
            case STRUCTURAL_REPAIR -> {
                if (!(evidence instanceof StructuralRepairObservation repair)) throw new IllegalArgumentException("structural repair requires repair observation evidence");
                yield StructuralRepairStateSupport.complete(state, intent, repair, new java.util.LinkedHashMap<>(intents));
            }
            case ROUTE_CONSTRUCTION -> {
                if (!(evidence instanceof RouteConstructionObservation construction)) throw new IllegalArgumentException("route construction requires construction observation evidence");
                yield RouteConstructionStateSupport.complete(state, intent, construction, new java.util.LinkedHashMap<>(intents));
            }
            case ROUTE_CONSTRUCTION_MATERIAL_LOADING -> {
                if (!(evidence instanceof RouteConstructionMaterialLoadObservation loading)) {
                    throw new IllegalArgumentException("route construction material loading requires exact pickup evidence");
                }
                RouteConstructionStateSupport.validateMaterialLoadingReceipt(state, intent, loading);
                yield PhysicalIntentTransitionStorage.recordConfirmed(state, intent, loading, intents);
            }
            case ROUTE_MAINTENANCE, ROUTE_MAINTENANCE_MATERIAL_LOADING ->
                    RouteMaintenanceStateSupport.confirm(state, intent, evidence, new java.util.LinkedHashMap<>(intents));
            case EQUIPMENT_ISSUE, EQUIPMENT_RETURN -> HumanEquipmentStateSupport.complete(state, intent, evidence,
                    new java.util.LinkedHashMap<>(intents));
            default -> throw new IllegalArgumentException("engineering capability received undeclared kind");
        };
    }

    private static FrontierWorldState unknownEngineeringTransition(FrontierWorldState state,
                                                                    io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                    java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId,
                                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents) {
        return switch (intent.kind()) {
            case ROUTE_CONSTRUCTION, ROUTE_CONSTRUCTION_MATERIAL_LOADING ->
                    RouteConstructionStateSupport.conflict(state, intent, new java.util.LinkedHashMap<>(intents));
            case ROUTE_MAINTENANCE, ROUTE_MAINTENANCE_MATERIAL_LOADING ->
                    RouteMaintenanceStateSupport.conflict(state, intent, new java.util.LinkedHashMap<>(intents));
            case STRUCTURAL_REPAIR, EQUIPMENT_ISSUE, EQUIPMENT_RETURN ->
                    PhysicalIntentTransitionStorage.recordUnknown(state, intent, intents);
            default -> throw new IllegalArgumentException("engineering capability received undeclared kind");
        };
    }

    private static List<ProposedEvent> withEngineeringContinuation(FrontierWorldState state,
                                                                     io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                     PhysicalIntentTransition transition, long now, boolean returning) {
        ProposedEvent physical = new ProposedEvent(intent.causeSubjectId(), transition);
        if (transition.status() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED) return List.of(physical);
        io.farfrontier.palemirror.frontier.v3.api.SubjectId projectId = switch (intent.kind()) {
            case ROUTE_CONSTRUCTION, ROUTE_CONSTRUCTION_MATERIAL_LOADING -> intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ROUTE_CONSTRUCTION_PROJECT);
            case ROUTE_MAINTENANCE, ROUTE_MAINTENANCE_MATERIAL_LOADING -> intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ROUTE_MAINTENANCE);
            case EQUIPMENT_ISSUE, EQUIPMENT_RETURN -> intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGINEERING_WORK_ORDER);
            default -> null;
        };
        if (projectId == null) return List.of(physical);
        RouteConstruction construction = state.routeConstructions().get(projectId);
        if (construction != null) return List.of(physical, new ProposedEvent(projectId, new ScheduleEffect.Created(
                returning ? RouteConstructionProcess.returnProgress(construction, Math.addExact(now, 1L)) : RouteConstructionProcess.progress(construction, Math.addExact(now, 1L)))));
        RouteMaintenance maintenance = state.routeMaintenances().get(projectId);
        if (maintenance != null) return List.of(physical, new ProposedEvent(projectId, new ScheduleEffect.Created(
                returning ? RouteMaintenanceProcess.returnProgress(maintenance, Math.addExact(now, 1L)) : RouteMaintenanceProcess.progress(maintenance, Math.addExact(now, 1L)))));
        return List.of(physical);
    }
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof RoutePatrolSceneLeasePrepared prepared) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierRoutePatrolSceneSupport.owner(state,
                    FrontierSceneBehaviors.routePatrol(prepared.lease())), prepared))); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof RoutePatrolSceneLeaseHandoff handoff) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierRoutePatrolSceneSupport.owner(state,
                    FrontierSceneBehaviors.routePatrol(handoff.lease())), handoff))); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof RoutePatrolFormationObserved observed) return planPatrolFormation(state, observed);
        if (command.payload() instanceof RoutePatrolBlocked blocked) return planPatrolBlocked(state, blocked);
        if (command.payload() instanceof RoutePatrolObstructionConfirmed confirmed) return planPatrolObstruction(state, confirmed, command.submittedAt().ticks());
        if (command.payload() instanceof RouteConstructionAssemblyAdvanced advanced) {
            RouteConstruction project = state.routeConstructions().get(advanced.projectId());
            return planEngineeringAssemblyAdvance(state, command, project, advanced.assembly(), advanced,
                    "route construction assembly observation has no active project");
        }
        if (command.payload() instanceof RouteMaintenanceAssemblyAdvanced advanced) {
            RouteMaintenance maintenance = state.routeMaintenances().get(advanced.maintenanceId());
            return planEngineeringAssemblyAdvance(state, command, maintenance, advanced.assembly(), advanced,
                    "route maintenance assembly observation has no active operation");
        }
        return FrontierWorldCommandPlanner.rejected("infrastructure process does not admit command: " + command.payload().type());
    }

    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case RouteConstructionStarted started -> RouteConstructionStateSupport.reduceStarted(state, event.subject(), started);
            case RouteConstructionMaterialLoaded loaded -> RouteConstructionStateSupport.reduceMaterialLoaded(state, event.subject(), loaded);
            case RouteConstructionAssemblyStarted started -> RouteConstructionStateSupport.reduceAssemblyStarted(state, event.subject(), started);
            case RouteConstructionAssemblyAdvanced advanced -> RouteConstructionStateSupport.reduceAssemblyAdvanced(state, event.subject(), advanced);
            case RouteTopologyCutover cutover -> RouteConstructionStateSupport.reduceCutover(state, event.subject(), cutover);
            case RouteMaintenanceStarted started -> RouteMaintenanceStateSupport.reduceStarted(state, event.subject(), started);
            case RouteMaintenanceMaterialLoaded loaded -> RouteMaintenanceStateSupport.reduceMaterialLoaded(state, event.subject(), loaded);
            case RouteMaintenanceAssemblyStarted started -> RouteMaintenanceStateSupport.reduceAssemblyStarted(state, event.subject(), started);
            case RouteMaintenanceAssemblyAdvanced advanced -> RouteMaintenanceStateSupport.reduceAssemblyAdvanced(state, event.subject(), advanced);
            case RouteMaintenanceClosed closed -> RouteMaintenanceStateSupport.reduceClosed(state, event.subject(), closed);
            case RoutePatrolStarted started -> RoutePatrolProcess.reduceStarted(state, event.subject(), started);
            case RoutePatrolFormationAdvanced advanced -> reducePatrolFormationAdvanced(state, event.subject(), advanced);
            case RoutePatrolObstructionConfirmed confirmed -> RoutePatrolProcess.reduceObstruction(state, event.subject(), confirmed);
            case RoutePatrolFailed failed -> RoutePatrolProcess.reduceFailed(state, event.subject(), failed);
            case RoutePatrolBlocked blocked -> RoutePatrolProcess.reduceBlocked(state, event.subject(), blocked);
            case RoutePatrolSceneLeasePrepared prepared -> reducePatrolPrepared(state, event.subject(), event, prepared);
            case RoutePatrolSceneLeaseHandoff handoff -> reducePatrolHandoff(state, event.subject(), event, handoff);
            case RoutePatrolFormationObserved observed -> reducePatrolFormation(state, event.subject(), observed);
            default -> throw new IllegalArgumentException("infrastructure process does not own event: " + event.payload().type());
        };
    }

    private static CommandPlan planPatrolFormation(FrontierWorldState state, RoutePatrolFormationObserved observed) {
        try {
            RoutePatrol patrol = FrontierRoutePatrolSceneSupport.require(state, new RoutePatrolSceneCause(observed.taskId()));
            FrontierRoutePatrolSceneSupport.advanceFormationObserved(state, patrol, observed.leaseId(), observed.bodies(), observed.executions());
            return new CommandPlan.Accepted(List.of(new ProposedEvent(patrol.settlementId(), observed)));
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }

    private static CommandPlan planPatrolBlocked(FrontierWorldState state, RoutePatrolBlocked blocked) {
        try {
            RoutePatrol patrol = state.strategicPlans().routePatrols().get(blocked.taskId());
            if (patrol == null || !patrol.active()) throw new IllegalArgumentException("route-patrol block has no active patrol");
            RoutePatrolProcess.reduceBlocked(state, patrol.settlementId(), blocked);
            return new CommandPlan.Accepted(List.of(new ProposedEvent(patrol.settlementId(), blocked),
                    new ProposedEvent(patrol.settlementId(), new StrategicTaskTransition(patrol.taskId(), StrategicTaskStatus.BLOCKED))));
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }

    private static CommandPlan planPatrolObstruction(FrontierWorldState state, RoutePatrolObstructionConfirmed confirmed, long submittedAt) {
        try {
            RoutePatrol patrol = state.strategicPlans().routePatrols().get(confirmed.taskId());
            if (patrol == null || !patrol.active() || !state.physicalDeltas().containsKey(confirmed.position())) {
                throw new IllegalArgumentException("route-patrol obstruction has no retained physical evidence");
            }
            RoutePatrolProcess.reduceObstruction(state, patrol.settlementId(), confirmed);
            return new CommandPlan.Accepted(List.of(new ProposedEvent(patrol.settlementId(), confirmed),
                    new ProposedEvent(patrol.settlementId(), new StrategicTaskTransition(patrol.taskId(), StrategicTaskStatus.COMPLETED)),
                    new ProposedEvent(patrol.settlementId(), new ScheduleEffect.Created(StrategicObjectiveProcess.routeReconsideration(patrol.settlementId(),
                            confirmed.position(), "confirmed", Math.addExact(submittedAt, 1L))))));
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }

    private static FrontierWorldState reducePatrolPrepared(FrontierWorldState state, SubjectId subject, FrontierEvent event,
                                                           RoutePatrolSceneLeasePrepared prepared) {
        if (!subject.equals(FrontierRoutePatrolSceneSupport.owner(state, FrontierSceneBehaviors.routePatrol(prepared.lease())))
                || !prepared.lease().handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("route-patrol scene preparation does not match its retained formation");
        }
        return state.prepareSceneLease(prepared.lease());
    }

    private static FrontierWorldState reducePatrolHandoff(FrontierWorldState state, SubjectId subject, FrontierEvent event,
                                                          RoutePatrolSceneLeaseHandoff handoff) {
        if (!subject.equals(FrontierRoutePatrolSceneSupport.owner(state, FrontierSceneBehaviors.routePatrol(handoff.lease())))
                || !handoff.lease().handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("route-patrol scene hand-off does not match its retained formation");
        }
        return state.handoffAmbientScene(new SceneLeaseHandoff(handoff.lease(), handoff.ambientMembers()));
    }

    private static FrontierWorldState reducePatrolFormation(FrontierWorldState state, SubjectId subject, RoutePatrolFormationObserved observed) {
        RoutePatrol patrol = FrontierRoutePatrolSceneSupport.require(state, new RoutePatrolSceneCause(observed.taskId()));
        if (!subject.equals(patrol.settlementId())) throw new IllegalArgumentException("route-patrol formation has a foreign owner");
        return FrontierRoutePatrolSceneSupport.advanceFormationObserved(state, patrol, observed.leaseId(), observed.bodies(), observed.executions());
    }
    private static FrontierWorldState reducePatrolFormationAdvanced(FrontierWorldState state, SubjectId subject, RoutePatrolFormationAdvanced advanced) {
        return RoutePatrolProcess.reduceFormationAdvanced(state, subject, advanced);
    }

    private static CommandPlan planEngineeringAssemblyAdvance(FrontierWorldState state, FrontierCommand command,
                                                               EngineeringWorkOrder project, EngineeringWorkAssembly next,
                                                               io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload,
                                                               String missingOwner) {
        if (project == null) return FrontierWorldCommandPlanner.rejected(missingOwner);
        try {
            SubjectId observed = validateHotEngineeringAssemblyObservation(state, project, next);
            List<ProposedEvent> events = next.complete()
                    ? List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, payload), new ProposedEvent(project.id(),
                    new ScheduleEffect.Created(progress(project, command.submittedAt().ticks() + 1L))))
                    : List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, payload));
            return new CommandPlan.Accepted(events);
        } catch (IllegalArgumentException invalid) {
            return FrontierWorldCommandPlanner.rejected(invalid.getMessage());
        }
    }

    /** The physical boundary may acknowledge exactly one live body at its existing lease goal. */
    private static SubjectId validateHotEngineeringAssemblyObservation(FrontierWorldState state, EngineeringWorkOrder project,
                                                                        EngineeringWorkAssembly next) {
        EngineeringWorkAssembly current = project.assembly().orElseThrow(() -> new IllegalArgumentException("engineering work has no active assembly"));
        if (!current.members().keySet().equals(next.members().keySet())) {
            throw new IllegalArgumentException("HOT engineering assembly observation changes the retained crew");
        }
        SubjectId observed = null;
        for (SubjectId actor : current.members().keySet()) {
            EngineeringWorkAssembly.Member before = current.members().get(actor);
            EngineeringWorkAssembly.Member after = next.members().get(actor);
            if (!before.corridor().equals(after.corridor()) || after.cursor() < before.cursor() || after.cursor() > before.cursor() + 1) {
                throw new IllegalArgumentException("HOT engineering assembly observation may advance only one adjacent cursor");
            }
            if (after.cursor() > before.cursor()) {
                if (observed != null) throw new IllegalArgumentException("HOT engineering assembly observation may acknowledge only one actor");
                observed = actor;
            }
        }
        if (observed == null || !current.advance(observed).equals(next)) {
            throw new IllegalArgumentException("HOT engineering assembly observation is not the retained safe next step");
        }
        EngineeringWorkAssembly.Member arrived = next.members().get(observed);
        AmbientActorLease lease = state.ambientLeases().get(observed);
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.ENGINEERING_ASSEMBLY
                || !lease.goalBody().equals(BodyPosition.above(new SurfaceAnchor(arrived.currentPosition())))) {
            throw new IllegalArgumentException("HOT engineering assembly observation lacks its exact active actor lease");
        }
        return observed;
    }

    private static io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction progress(EngineeringWorkOrder project, long dueAt) {
        return switch (project) {
            case RouteConstruction construction -> RouteConstructionProcess.progress(construction, dueAt);
            case RouteMaintenance maintenance -> RouteMaintenanceProcess.progress(maintenance, dueAt);
        };
    }
}
