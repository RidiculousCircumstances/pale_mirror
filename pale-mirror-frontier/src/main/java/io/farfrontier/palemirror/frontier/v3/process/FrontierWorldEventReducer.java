package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
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
 * Deterministic event policy for the closed Frontier world composition.
 *
 * <p>The composition root delegates here after registry admission. H0.3 keeps the
 * finite reducer explicit and separately bounded until each domain branch becomes
 * a registered process module.</p>
 */
public final class FrontierWorldEventReducer {
    private FrontierWorldEventReducer() { }
    public static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierEvent event,
                                            DeterministicProcessRegistry processRegistry) {
        processRegistry.requireReducedEventOwner(event.payload().type());
        return FrontierWorldState.duringReducerTransition(() -> reduceUnchecked(state, event));
    }
    private static FrontierWorldState reduceUnchecked(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierEvent event) {
        if (event.payload() instanceof AmbientLeasePrepared || event.payload() instanceof AmbientLeaseTransition || event.payload() instanceof AmbientLeaseReleased) {
            return AmbientActorProcess.reduceLease(state, event.subject(), event.instant(), event.payload());
        }
        return switch (event.payload()) {
            case InfectionChanged changed -> state.withInfection(changed.cell(), changed.intensity());
            case CompanyRegistered registered -> CompanyFoundationProcess.reduce(state, event.subject(), registered);
            case EmploymentContractOpened opened -> CompanyFoundationProcess.reduceEmployment(state, event.subject(), opened);
            case EmploymentContractTerminated terminated -> CompanyFoundationProcess.reduceEmploymentTermination(state, event.subject(), terminated);
            case MarketDemandOpened opened -> MarketClearingProcess.reduceOpened(state, event.subject(), opened);
            case MarketQuotePublished published -> MarketClearingProcess.reduceQuote(state, event.subject(), event.instant().ticks(), published);
            case MarketWorkOrderAccepted accepted -> MarketClearingProcess.reduceAccepted(state, event.subject(), event.instant().ticks(), accepted);
            case MarketWorkOrderCancelled cancelled -> MarketClearingProcess.reduceWorkOrderCancelled(state, event.subject(), cancelled);
            case MarketDemandExpired expired -> MarketClearingProcess.reduceExpired(state, event.subject(), event.instant().ticks(), expired);
            case MarketDemandCancelled cancelled -> MarketClearingProcess.reduceCancelled(state, event.subject(), cancelled);
            case ProductionStarted started -> ProductionProcess.reduceStarted(state, event.subject(), started);
            case ProductionCompleted completed -> ProductionProcess.reduceCompleted(state, event.subject(), completed);
            case ProductionBlocked blocked -> ProductionProcess.reduceBlocked(state, event.subject(), blocked);
            case SupplyContractCreated created -> reduceContractCreated(state, event.subject(), created);
            case SupplyContractAbandoned abandoned -> reduceContractAbandoned(state, event.subject(), abandoned);
            case CargoLoaded loaded -> reduceCargoLoaded(state, event.subject(), loaded);
            case CargoDelivered delivered -> SupplyOperationProcess.reduceDelivered(state, event.subject(), delivered);
            case OperationCreated created -> reduceOperationCreated(state, event.subject(), created);
            case OperationAdvanced advanced -> reduceOperationAdvanced(state, event.subject(), advanced);
            case OperationAssemblyAdvanced advanced -> reduceOperationAssemblyAdvanced(state, event.subject(), advanced);
            case OperationAssemblyDeferred deferred -> reduceOperationAssemblyDeferred(state, event.subject(), deferred);
            case OperationTravelStarted started -> reduceOperationTravelStarted(state, event.subject(), started);
            case OperationTravelAdvanced advanced -> reduceOperationTravelAdvanced(state, event.subject(), advanced);
            case OperationTravelSegmentCompleted completed -> reduceOperationTravelSegmentCompleted(state, event.subject(), completed);
            case OperationColdSuspended suspended -> reduceOperationColdSuspended(state, event.subject(), suspended);
            case PhysicalIntentPrepared prepared -> reducePhysicalIntentPrepared(state, event.subject(), prepared);
            case PhysicalIntentTransition transition -> reducePhysicalIntentTransition(state, event.subject(), transition);
            case SceneLeasePrepared prepared -> reduceSceneLeasePrepared(state, event.subject(), event.instant(), prepared);
            case SceneLeaseHandoff handoff -> reduceSceneLeaseHandoff(state, event.subject(), event.instant(), handoff);
            case SettlementAssaultSceneLeasePrepared prepared -> reduceAssaultSceneLeasePrepared(state, event.subject(), event.instant(), prepared);
            case SettlementAssaultSceneLeaseHandoff handoff -> reduceAssaultSceneLeaseHandoff(state, event.subject(), event.instant(), handoff);
            case SceneLeaseTransition transition -> reduceSceneLeaseTransition(state, event.subject(), transition);
            case SceneLeaseReleased released -> reduceSceneLeaseReleased(state, event.subject(), released);
            case SceneLeaseRecoveryUnresolved unresolved -> reduceSceneLeaseRecoveryUnresolved(state, event.subject(), unresolved);
            case ActorDied death -> reduceActorDied(state, event.subject(), death);
            case AmbientActorDied death -> AmbientActorProcess.reduce(state, event.subject(), death);
            case AmbientActorObserved observation -> AmbientActorProcess.reduce(state, event.subject(), observation);
            case ResidentBorn birth -> reduceResidentBorn(state, event.subject(), birth);
            case ResidentMigrated migration -> reduceResidentMigrated(state, event.subject(), migration);
            case ResidentMigrationStarted started -> reduceMigrationStarted(state, event.subject(), started);
            case ResidentMigrationAdvanced advanced -> reduceMigrationAdvanced(state, event.subject(), advanced);
            case ResidentTransitAdvanced advanced -> reduceTransitAdvanced(state, event.subject(), advanced);
            case ResidentMigrationBlocked blocked -> reduceMigrationBlocked(state, event.subject(), blocked);
            case ResidentMigrationResumed resumed -> reduceMigrationResumed(state, event.subject(), resumed);
            case ResidentBirthStarted started -> PopulationBirthProcess.reduceStarted(state, event.subject(), started);
            case ResidentBirthCancelled cancelled -> PopulationBirthProcess.reduceCancelled(state, event.subject(), cancelled);
            case LegacySettlementProvisionStarted started -> SettlementProvisionProcess.reduceLegacyStarted(state, event.subject(), started);
            case SettlementProvisionStarted started -> SettlementProvisionProcess.reduceStarted(state, event.subject(), started);
            case SettlementProvisionConsumed consumed -> SettlementProvisionProcess.reduceConsumed(state, event.subject(), consumed);
            case SettlementProvisionResolved resolved -> SettlementProvisionProcess.reduceResolved(state, event.subject(), resolved);
            case ResidentHealthTransition transition -> HumanHealthProcess.reduceResidentTransition(state, event.subject(), event.instant().ticks(), transition);
            case SettlementQuarantineTransition transition -> HumanHealthProcess.reduceQuarantineTransition(state, event.subject(), event.instant().ticks(), transition);
            case ResourceSiteGrowthAdvanced advanced -> ResourceSiteProcess.reduceGrowth(state, event.subject(), advanced);
            case ResourceSitePreparationStarted started -> ResourceSiteProcess.reducePreparationStarted(state, event.subject(), started);
            case ResourceSitePrepared prepared -> ResourceSiteProcess.reducePrepared(state, event.subject(), prepared);
            case ResourceSiteHarvestStarted started -> ResourceSiteHarvestProcess.reduceStarted(state, event.subject(), started);
            case ResourceSiteHarvested harvested -> ResourceSiteHarvestProcess.reduceHarvested(state, event.subject(), harvested);
            case ResourceSiteConflictObserved conflict -> ResourceSiteProcess.reduceConflict(state, event.subject(), conflict);
            case StructureDamaged damaged -> reduceStructureDamaged(state, event.subject(), damaged);
            case PhysicalDeltaObserved observed -> FrontierWorldPhysicalObservationProcess.reduce(state, event.subject(), observed);
            case ResourceDeposited deposited -> FrontierWorldPhysicalObservationProcess.reduceResourceDeposit(state, event.subject(), deposited);
            case OperationFailed failed -> reduceOperationFailed(state, event.subject(), failed);
            case TerminalLogisticsCompacted compacted -> reduceTerminalLogisticsCompacted(state, event.subject(), event.instant().ticks(), compacted);
            case ExactItemCustodyChanged changed -> reduceExactItemCustodyChanged(state, event.subject(), changed);
            case ExactItemDestroyed destroyed -> reduceExactItemDestroyed(state, event.subject(), destroyed);
            case CargoCarrierReleased released -> reduceCargoCarrierReleased(state, event.subject(), released);
            case InventoryConflictObserved observed -> reduceInventoryConflict(state, event.subject(), observed);
            case ContainerSurfaceTransition transition -> ContainerSurfaceProcess.reduce(state, event.subject(), transition);
            case HiveGrowthStarted started -> HiveGrowthProcess.reduceStarted(state, event.subject(), started);
            case HiveGrowthBiomassConsumed consumed -> HiveGrowthProcess.reduceConsumed(state, event.subject(), consumed);
            case HiveGrowthCompleted completed -> HiveGrowthProcess.reduceCompleted(state, event.subject(), completed);
            case HiveGrowthBlocked blocked -> HiveGrowthProcess.reduceBlocked(state, event.subject(), blocked);
            case HiveNutrientTransferStarted started -> HiveNutrientTransferProcess.reduceStarted(state, event.subject(), started.transfer());
            case HiveNutrientTransferAdvanced advanced -> HiveNutrientTransferProcess.reduceAdvanced(state, event.subject(), advanced);
            case HiveNutrientTransferCompleted completed -> HiveNutrientTransferProcess.reduceCompleted(state, event.subject(), completed);
            case HiveNutrientTransferBlocked blocked -> HiveNutrientTransferProcess.reduceBlocked(state, event.subject(), blocked);
            case HiveNutrientTransferEndpointPrepared prepared -> HiveNutrientTransferProcess.reduceEndpointPrepared(state, event.subject(), prepared);
            case RouteConstructionStarted started -> RouteConstructionStateSupport.reduceStarted(state, event.subject(), started);
            case RouteConstructionMaterialLoaded loaded -> RouteConstructionStateSupport.reduceMaterialLoaded(state, event.subject(), loaded);
            case RouteTopologyCutover cutover -> RouteConstructionStateSupport.reduceCutover(state, event.subject(), cutover);
            case RoutePatrolStarted started -> RoutePatrolProcess.reduceStarted(state, event.subject(), started);
            case RoutePatrolAdvanced advanced -> RoutePatrolProcess.reduceAdvanced(state, event.subject(), advanced);
            case RoutePatrolObstructionConfirmed confirmed -> RoutePatrolProcess.reduceObstruction(state, event.subject(), confirmed);
            case RoutePatrolFailed failed -> RoutePatrolProcess.reduceFailed(state, event.subject(), failed);
            case SettlementInfectionObserved observed -> SettlementPerceptionProcess.reduce(state, event.subject(), observed);
            case HiveOperationObserved observed -> HivePerceptionProcess.reduce(state, event.subject(), observed);
            case HiveTerritoryObserved observed -> HiveTerritoryPerceptionProcess.reduce(state, event.subject(), observed);
            case HiveSettlementObserved observed -> HiveSettlementPerceptionProcess.reduce(state, event.subject(), observed);
            case HiveDoctrineSelected selected -> HiveDoctrineProcess.reduce(state, event.subject(), selected);
            case HotScoutOperationObserved observed -> HivePerceptionProcess.reduceHot(state, event.subject(), observed);
            case ScoutPatrolAdvanced advanced -> HiveScoutPatrolProcess.reduce(state, event.subject(), advanced);
            case RouteEngagementStarted started -> HiveRouteEngagementProcess.reduceStarted(state, event.subject(), started);
            case RouteEngagementAttackerAdvanced advanced -> HiveRouteEngagementProcess.reduceAdvanced(state, event.subject(), advanced);
            case RouteEngagementTransition transition -> HiveRouteEngagementProcess.reduceTransition(state, event.subject(), transition);
            case RouteEngagementStrike strike -> HiveRouteEngagementProcess.reduceStrike(state, event.subject(), strike);
            case RouteEngagementResolved resolved -> HiveRouteEngagementProcess.reduceResolved(state, event.subject(), resolved);
            case SettlementAssaultStarted started -> HiveSettlementAssaultProcess.reduceStarted(state, event.subject(), started);
            case SettlementAssaultAttackerAdvanced advanced -> HiveSettlementAssaultProcess.reduceAdvanced(state, event.subject(), advanced);
            case SettlementAssaultTransition transition -> HiveSettlementAssaultProcess.reduceTransition(state, event.subject(), transition);
            case SettlementAssaultStrike strike -> HiveSettlementAssaultProcess.reduceStrike(state, event.subject(), strike);
            case SettlementAssaultResolved resolved -> HiveSettlementAssaultProcess.reduceResolved(state, event.subject(), resolved);
            case StrategicObjectiveSelected selected -> StrategicObjectiveProcess.reduceObjective(state, event.subject(), selected);
            case StrategicTaskPlanned planned -> StrategicObjectiveProcess.reduceTask(state, event.subject(), planned);
            case StrategicTaskTransition transition -> StrategicObjectiveProcess.reduceTaskTransition(state, event.subject(), transition);
            default -> fail(event.payload().type());
        };
    }
    private static FrontierWorldState reduceResidentBorn(FrontierWorldState state, SubjectId subject, ResidentBorn birth) {
        return PopulationBirthProcess.reduceBorn(state, subject, birth);
    }
    private static FrontierWorldState reduceResidentMigrated(FrontierWorldState state, SubjectId subject, ResidentMigrated migration) {
        if (!subject.equals(migration.destinationSettlementId())) throw new IllegalArgumentException("resident migration lacks destination settlement owner");
        return state.recordResidentMigration(migration);
    }
    private static FrontierWorldState reduceMigrationStarted(FrontierWorldState state, SubjectId subject, ResidentMigrationStarted started) {
        if (!subject.equals(started.journey().originSettlementId())) throw new IllegalArgumentException("migration start lacks its origin settlement owner");
        return HumanPopulationStateSupport.startMigration(state, started.journey());
    }
    private static FrontierWorldState reduceMigrationAdvanced(FrontierWorldState state, SubjectId subject, ResidentMigrationAdvanced advanced) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(advanced.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("migration advance lacks its origin settlement owner");
        return HumanPopulationStateSupport.advanceMigration(state, advanced);
    }
    private static FrontierWorldState reduceTransitAdvanced(FrontierWorldState state, SubjectId subject, ResidentTransitAdvanced advanced) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(advanced.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("HOT transit observation lacks its origin settlement owner");
        return PopulationMigrationProcess.reduceHotAdvance(state, advanced);
    }
    private static FrontierWorldState reduceMigrationBlocked(FrontierWorldState state, SubjectId subject, ResidentMigrationBlocked blocked) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(blocked.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("migration block lacks its origin settlement owner");
        return HumanPopulationStateSupport.blockMigration(state, blocked);
    }
    private static FrontierWorldState reduceMigrationResumed(FrontierWorldState state, SubjectId subject, ResidentMigrationResumed resumed) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(resumed.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("migration resume lacks its origin settlement owner");
        return HumanPopulationStateSupport.resumeMigration(state, resumed);
    }
    private static FrontierWorldState reduceContractCreated(FrontierWorldState state, SubjectId subject, SupplyContractCreated created) {
        SupplyContract contract = created.contract();
        if (!subject.equals(contract.settlementId())) throw new IllegalArgumentException("contract subject does not own settlement");
        SubjectId depot = FrontierWorldState.depotId(contract.settlementId());
        boolean backed = state.inventory().items().values().stream().anyMatch(item -> item.itemKind().equals(contract.itemKind())
                && item.count() == contract.itemCount() && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot));
        if (!backed) throw new IllegalArgumentException("supply contract has no exact depot-backed item");
        return state.createSupplyContract(created.contract());
    }
    private static FrontierWorldState reduceContractAbandoned(FrontierWorldState state, SubjectId subject, SupplyContractAbandoned abandoned) {
        SupplyContract contract = state.contracts().get(abandoned.contractId());
        if (contract == null || !subject.equals(contract.settlementId())) throw new IllegalArgumentException("abandoned contract has a foreign settlement owner");
        return state.abandonOrderedSupplyContract(abandoned.contractId());
    }
    private static FrontierWorldState reduceCargoLoaded(FrontierWorldState state, SubjectId subject, CargoLoaded loaded) {
        SupplyContract contract = state.contracts().get(loaded.contractId());
        if (contract == null || !subject.equals(contract.settlementId()) || !loaded.cargo().id().equals(contract.cargoId()) || loaded.cargo().itemIds().size() != 1) throw new IllegalArgumentException("cargo load does not match its contract");
        ExactItemStack item = state.inventory().items().get(loaded.cargo().itemIds().getFirst());
        if (item == null || !item.itemKind().equals(contract.itemKind()) || item.count() != contract.itemCount()) throw new IllegalArgumentException("cargo item does not match contract demand");
        return state.loadContractCargo(loaded.contractId(), loaded.cargo());
    }
    private static FrontierWorldState reduceOperationCreated(FrontierWorldState state, SubjectId subject, OperationCreated created) {
        RouteOperation operation = created.operation();
        if (!subject.equals(operation.settlementId()) || operation.stage() != OperationStage.ASSEMBLING || operation.routeIndex() != 0) {
            throw new IllegalArgumentException("route operation must begin assembling at its owning settlement");
        }
        SupplyContract contract = state.contracts().values().stream().filter(value -> value.cargoId().equals(operation.cargoId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("route operation cargo has no supply contract"));
        if (contract.status() != ContractStatus.LOADED || !contract.settlementId().equals(operation.settlementId()) || !contract.recipientId().equals(operation.destinationId())) {
            throw new IllegalArgumentException("route operation does not match its loaded supply contract");
        }
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), operation.settlementId());
        if (!operation.route().equals(state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id()))) {
            throw new IllegalArgumentException("route operation must use the deterministic settlement-to-nest route");
        }
        return state.createOperation(operation);
    }
    private static FrontierWorldState reduceOperationAdvanced(FrontierWorldState state, SubjectId subject, OperationAdvanced advanced) {
        RouteOperation operation = state.operations().get(advanced.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("route advancement subject does not own operation");
        return state.advanceOperation(advanced.operationId(), advanced.routeIndex(), advanced.stage());
    }
    private static FrontierWorldState reduceOperationAssemblyAdvanced(FrontierWorldState state, SubjectId subject, OperationAssemblyAdvanced advanced) {
        RouteOperation operation = state.operations().get(advanced.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("operation assembly subject does not own operation");
        return state.advanceOperationAssembly(advanced.operationId(), advanced.assembly());
    }
    private static FrontierWorldState reduceOperationAssemblyDeferred(FrontierWorldState state, SubjectId subject, OperationAssemblyDeferred deferred) {
        RouteOperation operation = state.operations().get(deferred.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("operation assembly deferral subject does not own operation");
        return state.deferOperationAssembly(deferred.operationId(), deferred.deferral());
    }
    private static FrontierWorldState reduceOperationTravelStarted(FrontierWorldState state, SubjectId subject, OperationTravelStarted started) {
        RouteOperation operation = state.operations().get(started.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("operation travel subject does not own operation");
        return state.startOperationTravel(started.operationId(), started.travel());
    }
    private static FrontierWorldState reduceOperationTravelAdvanced(FrontierWorldState state, SubjectId subject, OperationTravelAdvanced advanced) {
        RouteOperation operation = state.operations().get(advanced.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("operation travel subject does not own operation");
        return state.advanceOperationTravel(advanced.operationId(), advanced.travel());
    }
    private static FrontierWorldState reduceOperationTravelSegmentCompleted(FrontierWorldState state, SubjectId subject, OperationTravelSegmentCompleted completed) {
        RouteOperation operation = state.operations().get(completed.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("operation travel completion subject does not own operation");
        return state.completeOperationTravelSegment(completed.operationId());
    }
    private static FrontierWorldState reduceOperationColdSuspended(FrontierWorldState state, SubjectId subject, OperationColdSuspended suspended) {
        RouteOperation operation = state.operations().get(suspended.operationId());
        SceneLease lease = state.sceneLeases().get(suspended.leaseId());
        if (operation == null || !subject.equals(operation.settlementId()) || lease == null || !FrontierSceneBehaviors.isLogistics(lease) || !FrontierSceneBehaviors.logistics(lease).operationId().equals(operation.id())
                || lease.status() == SceneLeaseStatus.CLOSED || lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART) {
            throw new IllegalArgumentException("cold operation suspension lacks an active matching scene lease");
        }
        return state;
    }
    private static FrontierWorldState reducePhysicalIntentPrepared(FrontierWorldState state, SubjectId subject, PhysicalIntentPrepared prepared) {
        PhysicalIntent intent = prepared.intent();
        if (intent.kind() == PhysicalIntentKind.STRUCTURAL_REPAIR) return StructuralRepairProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION) return RouteConstructionProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING) {
            if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction material pickup must be prepared by the route network");
            RouteConstructionStateSupport.validateMaterialLoadingIntent(state, intent); return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.DECONTAMINATION) return DecontaminationProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION) return ResourceSiteProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST) return ResourceSiteHarvestProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.PRODUCTION_TRANSFORMATION) {
            ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
            if (job == null || !subject.equals(job.settlementId())) throw new IllegalArgumentException("production transformation must be prepared by its settlement");
            ProductionTransformationStateSupport.validateIntent(state, intent);
            return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.CARGO_LOADING) return CargoLoadingStateSupport.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE || intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL) {
            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive nutrient endpoint intent must be prepared by the hive");
            return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) {
            if (state.hiveColony().growthJobs().containsKey(intent.causeSubjectId())) return HiveGrowthProcess.reducePrepared(state, subject, intent);
            if (state.humanPopulation().birthJobs().containsKey(intent.causeSubjectId())) return PopulationBirthProcess.reducePrepared(state, subject, intent);
            if (state.humanPopulation().provisions().containsKey(intent.causeSubjectId())) return SettlementProvisionProcess.reducePrepared(state, subject, intent);
            throw new IllegalArgumentException("exact consumption has no supported owning process");
        }
        if (intent.kind() == PhysicalIntentKind.SCENE_STRIKE) {
            SceneStrikeStateSupport.validateIntent(state, intent);
            if (!subject.equals(SceneStrikeStateSupport.owner(state, intent))) throw new IllegalArgumentException("scene strike must be prepared by its exact scene owner");
            return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.EXPLOSION) {
            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("explosion intent must be prepared by the hive");
            ExplosionStateSupport.validateIntent(state, intent);
            return state.preparePhysicalIntent(intent);
        }
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null || operation.stage() != OperationStage.ARRIVED || !subject.equals(operation.settlementId())) {
            throw new IllegalArgumentException("physical intent must be prepared by an arrived route operation owner");
        }
        if (intent.kind() != PhysicalIntentKind.CARGO_HANDOFF || !intent.subjectIds().contains(operation.cargoId())
                || !intent.subjectIds().contains(operation.id())) throw new IllegalArgumentException("physical intent does not own arrived cargo hand-off");
        return state.preparePhysicalIntent(intent);
    }
    private static FrontierWorldState reducePhysicalIntentTransition(FrontierWorldState state, SubjectId subject, PhysicalIntentTransition transition) {
        PhysicalIntent intent = state.physicalIntents().get(transition.intentId());
        if (intent == null) throw new IllegalArgumentException("physical intent transition has no prepared intent");
        if (intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING) {
            if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction material pickup transition lacks route-network ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.STRUCTURAL_REPAIR || intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION || intent.kind() == PhysicalIntentKind.DECONTAMINATION) {
            SubjectId owner = intent.kind() == PhysicalIntentKind.DECONTAMINATION ? DecontaminationProcess.owner(state, intent.causeSubjectId()).id()
                    : FrontierWorldStateSupport.semanticOwner(state.bootstrap(), state.hiveColony(), intent.causeSubjectId());
            if (!subject.equals(owner)) {
                throw new IllegalArgumentException("structural repair transition lacks its owning settlement");
            }
            if (intent.kind() == PhysicalIntentKind.DECONTAMINATION) DecontaminationProcess.taskForIntent(state, intent, StrategicTaskStatus.ACTIVE);
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.EXPLOSION) {
            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("explosion transition lacks hive ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.SCENE_STRIKE) {
            if (!subject.equals(SceneStrikeStateSupport.owner(state, intent))) throw new IllegalArgumentException("scene strike transition lacks exact scene ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION) {
            if (!subject.equals(intent.causeSubjectId())) {
                throw new IllegalArgumentException("resource-site preparation transition lacks site ownership");
            }
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST) {
            if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("resource-site harvest transition lacks site ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.PRODUCTION_TRANSFORMATION) {
            ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
            if (job == null || !subject.equals(job.settlementId())) throw new IllegalArgumentException("production transformation transition lacks settlement ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.CARGO_LOADING) return CargoLoadingStateSupport.reduceTransition(state, subject, intent, transition);
        if (intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE || intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL) {
            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive nutrient endpoint transition lacks hive ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) {
            HiveGrowthJob job = state.hiveColony().growthJobs().get(intent.causeSubjectId());
            if (job != null) {
                if (!subject.equals(job.hiveId())) throw new IllegalArgumentException("hive growth consumption transition lacks hive ownership");
                return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
            }
            ResidentBirthJob birth = state.humanPopulation().birthJobs().get(intent.causeSubjectId());
            if (birth != null) {
                if (!subject.equals(birth.settlementId())) throw new IllegalArgumentException("resident birth consumption transition lacks settlement ownership");
                return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
            }
            if (!state.humanPopulation().provisions().containsKey(intent.causeSubjectId()) || !subject.equals(intent.causeSubjectId())) {
                throw new IllegalArgumentException("settlement provision consumption transition lacks settlement ownership");
            }
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("physical intent transition subject does not own operation");
        return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
    }
    private static FrontierWorldState reduceSceneLeasePrepared(FrontierWorldState state, SubjectId subject, SimInstant instant, SceneLeasePrepared prepared) {
        SceneLease lease = prepared.lease();
        RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
        if (operation == null || !subject.equals(operation.settlementId()) || !lease.handoffInstant().equals(instant)) {
            throw new IllegalArgumentException("scene lease does not match its current operation hand-off");
        }
        return state.prepareSceneLease(lease);
    }
    private static FrontierWorldState reduceSceneLeaseHandoff(FrontierWorldState state, SubjectId subject, SimInstant instant, SceneLeaseHandoff handoff) {
        SceneLease lease = handoff.lease();
        RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
        if (operation == null || !subject.equals(operation.settlementId()) || !lease.handoffInstant().equals(instant)) {
            throw new IllegalArgumentException("scene hand-off does not match its current operation hand-off");
        }
        return state.handoffAmbientScene(handoff);
    }
    private static FrontierWorldState reduceAssaultSceneLeasePrepared(FrontierWorldState state, SubjectId subject, SimInstant instant,
                                                                       SettlementAssaultSceneLeasePrepared prepared) {
        SceneLease lease = prepared.lease();
        if (!subject.equals(FrontierSettlementAssaultSceneSupport.owner(state, lease)) || !lease.handoffInstant().equals(instant)) {
            throw new IllegalArgumentException("assault scene lease does not match its retained battle hand-off");
        }
        return state.prepareSceneLease(lease);
    }
    private static FrontierWorldState reduceAssaultSceneLeaseHandoff(FrontierWorldState state, SubjectId subject, SimInstant instant,
                                                                       SettlementAssaultSceneLeaseHandoff handoff) {
        SceneLease lease = handoff.lease();
        if (!subject.equals(FrontierSettlementAssaultSceneSupport.owner(state, lease)) || !lease.handoffInstant().equals(instant)) {
            throw new IllegalArgumentException("assault scene hand-off does not match its retained battle");
        }
        return state.handoffAmbientScene(new SceneLeaseHandoff(lease, handoff.ambientMembers()));
    }
    private static FrontierWorldState reduceSceneLeaseTransition(FrontierWorldState state, SubjectId subject, SceneLeaseTransition transition) {
        SceneLease lease = state.sceneLeases().get(transition.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("scene lease transition lacks its owning scene");
        return state.transitionSceneLease(transition.leaseId(), transition.status());
    }
    private static FrontierWorldState reduceSceneLeaseReleased(FrontierWorldState state, SubjectId subject, SceneLeaseReleased released) {
        SceneLease lease = state.sceneLeases().get(released.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("scene release lacks its owning scene");
        return state.releaseSceneLease(released.leaseId(), released.members());
    }
    private static FrontierWorldState reduceActorDied(FrontierWorldState state, SubjectId subject, ActorDied death) {
        SceneLease lease = state.sceneLeases().get(death.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("actor death lacks its owning scene");
        return state.recordActorDeath(death);
    }
    private static FrontierWorldState reduceStructureDamaged(FrontierWorldState state, SubjectId subject, StructureDamaged damage) {
        if (!subject.equals(FrontierWorldStateSupport.structureSettlement(state.bootstrap(), damage.structureId()))) {
            throw new IllegalArgumentException("structure damage lacks its owning settlement");
        }
        return state.recordStructureDamage(damage);
    }
    private static FrontierWorldState reduceOperationFailed(FrontierWorldState state, SubjectId subject, OperationFailed failed) {
        RouteOperation operation = state.operations().get(failed.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("operation failure lacks its owning settlement");
        boolean death = operation.participantIds().stream().anyMatch(actor -> state.actorLocations().get(actor).condition().status() == ActorLifeStatus.DEAD);
        boolean obstruction = "route-obstructed".equals(failed.reason())
                && !FrontierRouteNetwork.isPassable(state.bootstrap(), operation.route(), state.physicalDeltas());
        boolean recoveryUnresolved = "scene-recovery-unresolved".equals(failed.reason()) && state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isLogistics)
                .anyMatch(lease -> FrontierSceneBehaviors.logistics(lease).operationId().equals(operation.id()) && lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART
                        && lease.recoveryEvidence().isPresent());
        if (!death && !obstruction && !recoveryUnresolved) {
            throw new IllegalArgumentException("operation failure lacks a dead participant or observed route obstruction");
        }
        return state.failOperation(failed.operationId());
    }
    private static FrontierWorldState reduceTerminalLogisticsCompacted(FrontierWorldState state, SubjectId subject, long atTick,
                                                                        TerminalLogisticsCompacted compacted) {
        RouteOperation operation = state.operations().get(compacted.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) {
            throw new IllegalArgumentException("terminal logistics receipt has a foreign operation owner");
        }
        return state.compactTerminalLogistics(compacted.operationId(), atTick);
    }
    private static FrontierWorldState reduceSceneLeaseRecoveryUnresolved(FrontierWorldState state, SubjectId subject, SceneLeaseRecoveryUnresolved unresolved) {
        SceneLease lease = state.sceneLeases().get(unresolved.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("scene recovery evidence lacks its owning scene");
        return FrontierSceneLeaseStateSupport.recoveryUnresolved(state, unresolved);
    }
    private static FrontierWorldState reduceExactItemCustodyChanged(FrontierWorldState state, SubjectId subject, ExactItemCustodyChanged changed) {
        if (!subject.equals(FrontierWorldStateSupport.itemOwner(state, changed))) throw new IllegalArgumentException("item custody observation lacks its canonical owner");
        return state.withInventory(state.inventory().moveObservedItem(changed.itemId(), changed.from(), changed.to()));
    }
    private static FrontierWorldState reduceExactItemDestroyed(FrontierWorldState state, SubjectId subject, ExactItemDestroyed destroyed) {
        ExactItemStack item = state.inventory().items().get(destroyed.itemId());
        if (item == null || !item.custody().equals(destroyed.source())
                || !subject.equals(FrontierWorldStateSupport.itemOwner(state, destroyed))) {
            throw new IllegalArgumentException("item destruction lacks its canonical owner");
        }
        return state.withInventory(state.inventory().destroyObservedItem(destroyed.itemId(), destroyed.source()));
    }
    private static FrontierWorldState reduceCargoCarrierReleased(FrontierWorldState state, SubjectId subject, CargoCarrierReleased released) {
        SceneLease lease = state.sceneLeases().get(released.leaseId());
        RouteOperation operation = lease == null || !FrontierSceneBehaviors.isLogistics(lease) ? null : state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("cargo carrier release lacks its owning settlement");
        return state.releaseCargoCarrier(released);
    }
    private static FrontierWorldState reduceInventoryConflict(FrontierWorldState state, SubjectId subject, InventoryConflictObserved observed) {
        InventoryConflict conflict = observed.conflict();
        ContainerRecord container = state.inventory().containers().get(conflict.containerId());
        if (container == null || !subject.equals(container.ownerId())) throw new IllegalArgumentException("inventory conflict lacks its container owner");
        return state.withInventory(state.inventory().recordConflict(conflict));
    }
    private static FrontierWorldState fail(String type) { throw new IllegalStateException("unregistered v3 world event: " + type); }
}
