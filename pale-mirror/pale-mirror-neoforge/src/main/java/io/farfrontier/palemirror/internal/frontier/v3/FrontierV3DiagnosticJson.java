package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLocation;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.ContainerRecord;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryBinding;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryTombstone;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierReadabilityPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLabels;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSiteHarvestSceneSupport;
import io.farfrontier.palemirror.frontier.v3.model.HumanAssignmentProjection;
import io.farfrontier.palemirror.frontier.v3.model.HumanAssignment;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSettlementWorkDiagnostic;
import io.farfrontier.palemirror.frontier.v3.model.FrontierMarketOrderDiagnostic;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SettlementFoodPolicy;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticIncident;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubject;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubjectKind;
import io.farfrontier.palemirror.frontier.v3.model.HiveNutrientReceipt;
import io.farfrontier.palemirror.frontier.v3.model.HiveNutrientTransfer;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.HiveGrowthJob;
import io.farfrontier.palemirror.frontier.v3.model.MarketWorkOrder;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.ProductionJob;
import io.farfrontier.palemirror.frontier.v3.model.ResidentProfile;
import io.farfrontier.palemirror.frontier.v3.model.ResidentBirthJob;
import io.farfrontier.palemirror.frontier.v3.model.ResidentNutritionStatus;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.ClaimAllocation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceLot;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestGoal;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestLineage;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgress;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstruction;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenance;
import io.farfrontier.palemirror.frontier.v3.model.SettlementProvision;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTask;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskKind;
import io.farfrontier.palemirror.frontier.v3.model.MedicalEvacuationOperation;
import io.farfrontier.palemirror.frontier.v3.process.EngineeringEquipmentProcess;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.process.PhysicalIntentLifecycleCompositionDiagnostic;

import java.util.Objects;
import java.util.Optional;

/** Stable bounded JSON emitted by the v3 operator diagnostic command; it only reads one immutable checkpoint. */
final class FrontierV3DiagnosticJson {
    static final String PREFIX = "PMV3_DIAG ";
    private static final int MAX_BYTES = 8_192;
    private FrontierV3DiagnosticJson() { }

    static String render(String kind, String id, CheckpointImage checkpoint, FrontierWorldState state,
                         Optional<FrontierV3DiagnosticTrace.Entry> trace) {
        return render(kind, id, checkpoint, state, trace, Optional.empty());
    }

    static String render(String kind, String id, CheckpointImage checkpoint, FrontierWorldState state,
                         Optional<FrontierV3DiagnosticTrace.Entry> trace,
                         Optional<FrontierV3AmbientAdmissionDiagnostic> admission) {
        return render(kind, id, checkpoint, state, trace, admission, Optional.empty());
    }

    static String render(String kind, String id, CheckpointImage checkpoint, FrontierWorldState state,
                         Optional<FrontierV3DiagnosticTrace.Entry> trace,
                         Optional<FrontierV3AmbientAdmissionDiagnostic> admission,
                         Optional<FrontierV3ResourceSiteHarvestExecutor.Readiness> harvestReadiness) {
        return render(kind, id, checkpoint, state, trace, admission, harvestReadiness, Optional.empty());
    }


    static String render(String kind, String id, CheckpointImage checkpoint, FrontierWorldState state,
                         Optional<FrontierV3DiagnosticTrace.Entry> trace,
                         Optional<FrontierV3AmbientAdmissionDiagnostic> admission,
                         Optional<FrontierV3ResourceSiteHarvestExecutor.Readiness> harvestReadiness,
                         Optional<FrontierV3SceneReadiness.Value> sceneReadiness) {
        return render(kind, id, checkpoint, state, trace, admission, harvestReadiness, sceneReadiness, Optional.empty());
    }

    static String render(String kind, String id, CheckpointImage checkpoint, FrontierWorldState state,
                         Optional<FrontierV3DiagnosticTrace.Entry> trace,
                         Optional<FrontierV3AmbientAdmissionDiagnostic> admission,
                         Optional<FrontierV3ResourceSiteHarvestExecutor.Readiness> harvestReadiness,
                         Optional<FrontierV3SceneReadiness.Value> sceneReadiness,
                         Optional<FrontierV3ContainerSurfaceExecutor.Readiness> containerReadiness) {
        return render(kind, id, checkpoint, state, trace, admission, harvestReadiness, sceneReadiness,
                containerReadiness, Optional.empty(), Optional.empty());
    }

    static String render(String kind, String id, CheckpointImage checkpoint, FrontierWorldState state,
                         Optional<FrontierV3DiagnosticTrace.Entry> trace,
                         Optional<FrontierV3AmbientAdmissionDiagnostic> admission,
                         Optional<FrontierV3ResourceSiteHarvestExecutor.Readiness> harvestReadiness,
                         Optional<FrontierV3SceneReadiness.Value> sceneReadiness,
                         Optional<FrontierV3ContainerSurfaceExecutor.Readiness> containerReadiness,
                         Optional<FrontierV3EquipmentIssueExecutor.Readiness> equipmentIssueReadiness,
                         Optional<FrontierV3EquipmentReturnExecutor.Readiness> equipmentReturnReadiness) {
        Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(id, "id");
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(trace, "trace");
        Objects.requireNonNull(admission, "admission");
        Objects.requireNonNull(harvestReadiness, "harvestReadiness");
        Objects.requireNonNull(sceneReadiness, "sceneReadiness");
        Objects.requireNonNull(containerReadiness, "containerReadiness");
        Objects.requireNonNull(equipmentIssueReadiness, "equipmentIssueReadiness");
        Objects.requireNonNull(equipmentReturnReadiness, "equipmentReturnReadiness");
        String value = switch (kind) {
            case "summary" -> summary(checkpoint, state);
            case "physical_lifecycle" -> physicalLifecycle(checkpoint, state);
            case "process" -> process(id, checkpoint, state);
            case "process_inventory" -> FrontierV3ProcessInventoryDiagnostic.render(id, checkpoint, state);
            case "site" -> site(id, checkpoint, state);
            case "settlement" -> settlement(id, checkpoint, state);
            case "settlement_management" -> FrontierV3SettlementManagementDiagnostic.render(id, checkpoint, state);
            case "settlement_population" -> settlementPopulation(checkpoint, state, id, java.util.Map.of());
            case "hive" -> hive(id, checkpoint, state);
            case "hive_transfer" -> hiveTransfer(id, checkpoint, state);
            case "actor" -> actor(id, checkpoint, state, admission);
            case "resident_life" -> FrontierV3ResidentLifeDiagnostic.render(id, checkpoint, state);
            case "item" -> item(id, checkpoint, state);
            case "resource" -> resource(id, checkpoint, state);
            case "container" -> container(id, checkpoint, state, containerReadiness);
            case "reference_container" -> referenceContainer(id, checkpoint, state);
            case "market_order" -> marketOrder(id, checkpoint, state);
            case "route_construction" -> FrontierV3DiagnosticEngineeringJson.routeConstruction(id, checkpoint, state);
            case "route_maintenance" -> FrontierV3DiagnosticEngineeringJson.routeMaintenance(id, checkpoint, state);
            case "physical_delta" -> physicalDelta(id, checkpoint, state);
            case "aftermath" -> aftermath(id, checkpoint, state);
            case "medical" -> medical(id, checkpoint, state);
            case "scene" -> FrontierV3SceneDiagnosticJson.render(id, checkpoint, state, sceneReadiness);
            case "intent" -> intent(id, checkpoint, state, harvestReadiness, equipmentIssueReadiness, equipmentReturnReadiness);
            case "why" -> why(id, checkpoint, state);
            case "trace" -> FrontierV3DiagnosticExecutorJson.trace(id, checkpoint, state, trace);
            case "incident" -> incident(id, checkpoint, state);
            case "transit" -> transit(id, checkpoint, state);
            case "recovery" -> recovery(id, checkpoint, state);
            case "route_topology" -> routeTopology(id, checkpoint, state);
            default -> unavailable(kind, id, checkpoint, "unknown_view");
        };
        return bounded(kind, id, checkpoint, value);
    }

    static String firstVisibility(String id, CheckpointImage checkpoint, FrontierV3GrayboxExecutor.FirstVisibilitySnapshot value) {
        if (value == null || value.chunk() == null || value.status().equals("INVALID")) return unavailable("first_visibility", id, checkpoint, "invalid_chunk");
        return PREFIX + base("first_visibility", id, checkpoint) + ",\"status\":\"ok\",\"chunkX\":" + value.chunk().x
                + ",\"chunkZ\":" + value.chunk().z + ",\"visibility\":\"" + quote(value.status())
                + "\",\"replicaRevision\":" + value.revision() + ",\"staticCells\":" + value.cells() + "}";
    }

    static String unavailableRuntime(String kind, String id) {
        return PREFIX + "{\"schema\":1,\"kind\":\"" + quote(kind) + "\",\"id\":\"" + quote(id)
                + "\",\"status\":\"runtime_unavailable\"}";
    }

    /**
     * The small shared operator landing view.  It reads one immutable checkpoint and the bounded
     * server-local action receipts; object detail stays with actor/site/settlement views so this
     * never becomes a second authority or an unbounded world scan.
     */
    static String operatorStatus(CheckpointImage checkpoint, FrontierWorldState state,
                                 java.util.List<FrontierV3ServerLifecycle.FastForwardRequestOutcome> requests) {
        return operatorStatus(checkpoint, state, requests, "");
    }

    static String operatorStatus(CheckpointImage checkpoint, FrontierWorldState state,
                                 java.util.List<FrontierV3ServerLifecycle.FastForwardRequestOutcome> requests, String selectedId) {
        String receipt = requests.isEmpty() ? "null" : fastForwardReceipt(requests.getLast());
        String subject = selectedStatus(checkpoint, state, selectedId);
        var calendar = new io.farfrontier.palemirror.frontier.v3.time.SimulationCalendar(
                state.bootstrap().ruleset().residentLife().dayTicks()).at(checkpoint.instant().ticks());
        return bounded("status", "", checkpoint, base("status", "", checkpoint) + ",\"status\":\"ok\",\"instant\":"
                + checkpoint.instant().ticks() + ",\"residents\":" + state.humanPopulation().residents().size()
                + ",\"sites\":" + state.resourceSites().sites().size() + ",\"activeScenes\":"
                + state.sceneLeases().values().stream().filter(lease -> lease.status() != io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED).count()
                + ",\"calendar\":{\"dayIndex\":" + calendar.dayIndex() + ",\"tickOfDay\":" + calendar.tickOfDay()
                + ",\"ticksPerDay\":" + calendar.ticksPerDay() + ",\"minecraftCycleTime\":"
                + io.farfrontier.palemirror.internal.calendar.MinecraftCalendarPresentation.project(calendar) + "}"
                + ",\"selectedSubject\":" + subject + ",\"lastFastForwardRequest\":" + receipt + "}");
    }

    private static String selectedStatus(CheckpointImage checkpoint, FrontierWorldState state, String id) {
        if (id == null || id.isBlank()) return "null";
        SubjectId subject = subject(id).orElse(null);
        if (subject == null) return unavailable("status", id, checkpoint, "not_found");
        if (state.actorLocations().containsKey(subject)) return actor(id, checkpoint, state, Optional.empty());
        if (state.resourceSites().sites().containsKey(subject)) return site(id, checkpoint, state);
        if (state.bootstrap().settlements().stream().anyMatch(settlement -> settlement.id().equals(subject))) return settlement(id, checkpoint, state);
        return unavailable("status", id, checkpoint, "not_found");
    }

    private static String fastForwardReceipt(FrontierV3ServerLifecycle.FastForwardRequestOutcome value) {
        return "{\"requestId\":" + value.requestId() + ",\"kind\":\"" + quote(value.kind()) + "\",\"requestedTicks\":"
                + value.requestedTicks() + ",\"targetInstant\":" + (value.targetInstant() == null ? "null" : value.targetInstant())
                + ",\"admittedCheckpointInstant\":" + (value.admittedCheckpointInstant() == null ? "null" : value.admittedCheckpointInstant())
                + ",\"reachedCheckpointInstant\":" + (value.reachedCheckpointInstant() == null ? "null" : value.reachedCheckpointInstant())
                + ",\"status\":\"" + quote(value.status()) + "\",\"reason\":"
                + (value.reason() == null ? "null" : "\"" + quote(value.reason()) + "\"") + "}";
    }

    /** Applies the one operator-response limit to every read-only v3 diagnostic view. */
    static String bounded(String kind, String id, CheckpointImage checkpoint, String value) {
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            value = unavailable(kind, id, checkpoint, "response_limit");
        }
        return PREFIX + value;
    }

    private static String summary(CheckpointImage checkpoint, FrontierWorldState state) {
        long retainedConflicts = state.diagnosticIncidents().incidents().values().stream().filter(DiagnosticIncident::awaitingReview).count();
        long custodyDiagnostics = state.diagnosticIncidents().incidents().values().stream().filter(value -> value.diagnostic().owner().kind()
                == io.farfrontier.palemirror.frontier.v3.model.DiagnosticOwnerKind.REPLICA_CUSTODY).count();
        String verdict = retainedConflicts == 0 ? "green" : "blocked";
        return base("summary", "", checkpoint)
                + ",\"status\":\"ok\",\"diagnosticVerdict\":\"" + verdict + "\",\"requiredConflicts\":" + retainedConflicts
                + ",\"settlements\":" + state.bootstrap().settlements().size()
                + ",\"residents\":" + state.humanPopulation().residents().size()
                + ",\"bioforms\":" + state.actorLocations().keySet().stream().filter(value -> value.value().startsWith("bioform:")).count()
                + ",\"sites\":" + state.resourceSites().sites().size()
                + ",\"intents\":" + state.physicalIntents().size()
                + ",\"ambientLeases\":" + state.ambientLeases().size()
                + ",\"sceneLeases\":" + state.sceneLeases().size()
                + ",\"items\":" + state.inventory().items().size()
                + ",\"inventoryConflicts\":" + state.inventory().conflicts().size()
                + ",\"replicaCustodyDiagnostics\":" + custodyDiagnostics
                + ",\"incidentIndexSize\":" + state.diagnosticIncidents().incidents().size()
                + ",\"optionalDiagnosticDrops\":" + state.diagnosticIncidents().droppedOptional() + "}";
    }

    /** One bounded composition account; all counts are derived from canonical state. */
    private static String physicalLifecycle(CheckpointImage checkpoint, FrontierWorldState state) {
        PhysicalIntentLifecycleCompositionDiagnostic diagnostic = FrontierWorldProcessCatalog.physicalLifecycleDiagnostic(state);
        String owners = diagnostic.owners().stream().map(owner -> "{\"owner\":\"" + quote(owner.owner().stableId())
                + "\",\"version\":" + owner.declarationVersion() + ",\"schemaTags\":" + owner.schemaTags()
                + ",\"unresolved\":" + owner.unresolved() + ",\"resolvedRetained\":" + owner.resolvedRetained()
                + ",\"recoveryBindings\":" + owner.currentRecoveryBindings() + ",\"maxUnresolved\":" + owner.maxUnresolved()
                + ",\"maxResolvedRetention\":" + owner.maxResolvedRetention() + ",\"pressure\":\"" + owner.pressure() + "\"}")
                .reduce((left, right) -> left + "," + right).orElse("");
        return base("physical_lifecycle", "", checkpoint) + ",\"status\":\"ok\",\"fingerprint\":\""
                + diagnostic.fingerprint() + "\",\"owners\":[" + owners + "]}";
    }

    /** One exact durable recovery fence, rendered read-only for a crash/reconciliation receipt. */
    private static String recovery(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId bindingId = subject(id).orElse(null);
        if (bindingId == null) return unavailable("recovery", id, checkpoint, "not_found");
        FencedRecoveryBinding current = state.fencedRecovery().current().get(bindingId);
        if (current != null) return base("recovery", id, checkpoint) + ",\"status\":\"ok\",\"asset\":\"" + current.asset()
                + "\",\"owner\":\"" + quote(current.ownerId().value()) + "\",\"ownerRevision\":" + current.ownerRevision()
                + ",\"epoch\":" + current.authorityEpoch() + ",\"phase\":\"" + current.phase()
                + "\",\"reversible\":" + current.reversibleCheckpoint() + ",\"attempts\":" + current.recoveryAttempts()
                + ",\"nextAction\":\"" + current.nextAction() + "\",\"reason\":\"" + quote(current.reason()) + "\"}";
        FencedRecoveryTombstone tombstone = state.fencedRecovery().tombstones().get(bindingId);
        if (tombstone == null) return unavailable("recovery", id, checkpoint, "not_found");
        return base("recovery", id, checkpoint) + ",\"status\":\"retired\",\"asset\":\"" + tombstone.asset()
                + "\",\"owner\":\"" + quote(tombstone.ownerId().value()) + "\",\"ownerRevision\":" + tombstone.ownerRevision()
                + ",\"epoch\":" + tombstone.retiredEpoch() + ",\"disposition\":\"" + tombstone.disposition()
                + "\",\"reason\":\"" + quote(tombstone.reason()) + "\"}";
    }

    /** Bounded read-only duration-process projection; aggregate, lease and schedule remain authoritative. */
    private static String process(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        return FrontierV3ProcessDiagnosticJson.render(id, checkpoint, state);
    }

    private static String site(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        return FrontierV3HarvestDiagnosticJson.site(id, checkpoint, state);
    }

    /** A read-only typed-subject causal account; the caller supplies KIND/id, never an inferred kind. */
    private static String why(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        Optional<DiagnosticSubject> subject = typedSubject(id);
        if (subject.isEmpty()) return unavailable("why", id, checkpoint, "typed_subject_required");
        return state.diagnosticIncidents().why(subject.orElseThrow()).map(value -> base("why", id, checkpoint)
                + ",\"status\":\"ok\",\"incident\":" + incident(value) + "}")
                .orElseGet(() -> unavailable("why", id, checkpoint, "not_found"));
    }

    /** Exact persisted incident lookup; this bounded lookup reads no chunks or mutable state. */
    private static String incident(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        return state.diagnosticIncidents().incident(id).map(value -> base("incident", id, checkpoint)
                + ",\"status\":\"ok\",\"incident\":" + incident(value) + "}")
                .orElseGet(() -> unavailable("incident", id, checkpoint, "not_found"));
    }

    private static Optional<DiagnosticSubject> typedSubject(String value) {
        int delimiter = value.indexOf('/'); if (delimiter <= 0 || delimiter == value.length() - 1) return Optional.empty();
        try { return Optional.of(new DiagnosticSubject(DiagnosticSubjectKind.valueOf(value.substring(0, delimiter)), new SubjectId(value.substring(delimiter + 1)))); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }
    private static String incident(DiagnosticIncident value) {
        return "{\"id\":\"" + quote(value.id()) + "\",\"firstEvent\":\"" + quote(value.firstEventId())
                + "\",\"firstCause\":\"" + quote(value.firstCauseId()) + "\",\"firstRevision\":" + value.firstRevision()
                + ",\"lastRevision\":" + value.lastRevision() + ",\"occurrences\":" + value.occurrences()
                + ",\"awaitingReview\":" + value.awaitingReview() + ",\"diagnostic\":" + diagnostic(value.diagnostic())
                + ",\"bundle\":" + bundle(value.bundle()) + "}";
    }
    /** Read-only identity-complete carrier for bounded incident export; it is never an owner lookup. */
    private static String bundle(io.farfrontier.palemirror.frontier.v3.model.DiagnosticIncidentBundle value) {
        return "{\"incident\":\"" + quote(value.incidentId()) + "\",\"event\":\"" + quote(value.eventId())
                + "\",\"cause\":\"" + quote(value.causeId()) + "\",\"revision\":" + value.revision()
                + ",\"instant\":" + value.instant() + ",\"occurrences\":" + value.occurrences()
                + ",\"awaitingReview\":" + value.awaitingReview() + ",\"diagnostic\":" + diagnostic(value.diagnostic())
                + ",\"context\":" + context(value.context()) + "}";
    }
    private static String context(io.farfrontier.palemirror.frontier.v3.model.DiagnosticIncidentContext value) {
        return "{\"world\":\"" + quote(value.world()) + "\",\"runtime\":\"" + quote(value.runtime())
                + "\",\"sourceTree\":\"" + quote(value.sourceTree()) + "\",\"jar\":\"" + quote(value.jar())
                + "\",\"ruleset\":\"" + quote(value.ruleset()) + "\",\"restartIdentity\":\"" + quote(value.restartIdentity())
                + "\",\"causalWindow\":\"" + quote(value.causalWindow()) + "\",\"physical\":\"" + quote(value.physical())
                + "\",\"claim\":\"" + quote(value.claim()) + "\",\"intent\":\"" + quote(value.intent())
                + "\",\"observation\":\"" + quote(value.observation()) + "\",\"reconciliation\":\"" + quote(value.reconciliation())
                + "\",\"projection\":\"" + quote(value.projection()) + "\",\"complete\":" + value.complete()
                + ",\"degradation\":\"" + quote(value.degradation()) + "\"}";
    }

    private static String diagnostic(io.farfrontier.palemirror.frontier.v3.model.DiagnosticTuple value) {
        return "{\"category\":\"" + value.category() + "\",\"categoryTag\":" + value.category().wireTag()
                + ",\"reason\":\"" + value.reason() + "\",\"reasonTag\":" + value.reason().wireTag()
                + ",\"ownerKind\":\"" + value.owner().kind() + "\",\"owner\":\"" + quote(value.owner().id().value())
                + "\",\"subjectKind\":\"" + value.subject().kind() + "\",\"subject\":\"" + quote(value.subject().id().value())
                + "\",\"disposition\":\"" + value.disposition() + "\",\"dispositionTag\":" + value.disposition().wireTag() + "}";
    }
    private static String settlement(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null);
        FrontierSettlementWorkDiagnostic value = subject == null ? null
                : FrontierSettlementWorkDiagnostic.inspect(checkpoint, state, subject).orElse(null);
        FrontierV3SettlementIngressGeometry.Value geometry = FrontierV3SettlementIngressGeometry.find(state, subject).orElse(null);
        if (value == null || geometry == null) return unavailable("settlement", id, checkpoint, "not_found");
        SubjectId depot = FrontierWorldState.depotId(subject);
        int availableFood = SettlementFoodPolicy.coldUsableBread(state, subject);
        var nextNeedReview = checkpoint.schedules().stream()
                .filter(action -> action.kind().equals("frontier.resident.need.review"))
                .filter(action -> { ResidentProfile person = state.humanPopulation().resident(action.subject());
                    return person != null && person.settlementId().equals(subject); })
                .mapToLong(action -> action.dueAt().ticks()).min();
        int heldFood = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.itemKind().equals(SettlementFoodPolicy.BREAD))
                .filter(binding -> {
                    CustodyAccount account = state.inventory().fungibleResources().accounts().get(binding.accountId());
                    return account != null && account.custody() instanceof ResourceCustody.Container container && container.containerId().equals(depot);
                }).mapToInt(PhysicalStackBinding::quantity).sum();
        int reservedFood = state.inventory().fungibleResources().claims().values().stream()
                .filter(claim -> claim.itemKind().equals(SettlementFoodPolicy.BREAD))
                .filter(claim -> claim.economicOwnerId().equals(subject)).mapToInt(ClaimAllocation::quantity).sum();
        int activeMeals = (int) state.humanPopulation().meals().values().stream()
                .filter(meal -> meal.settlementId().equals(subject)).count();
        int reserve = SettlementFoodPolicy.reserveRequirement(state, subject);
        int nourished = (int) state.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(subject))
                .filter(resident -> state.humanPopulation().nutrition(resident.id()).status() == ResidentNutritionStatus.NOURISHED).count();
        int hungry = (int) state.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(subject))
                .filter(resident -> state.humanPopulation().nutrition(resident.id()).status() == ResidentNutritionStatus.HUNGRY).count();
        int starving = (int) state.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(subject))
                .filter(resident -> state.humanPopulation().nutrition(resident.id()).status() == ResidentNutritionStatus.STARVING).count();
        return base("settlement", id, checkpoint) + ",\"status\":\"ok\",\"strategic\":" + lane(value.strategic())
                + ",\"facility\":" + lane(value.facility()) + ",\"readySites\":" + strings(value.readySites())
                + ",\"pendingHarvestSchedules\":" + strings(value.pendingHarvestSchedules())
                + ",\"harvestAdmission\":\"" + quote(value.harvestAdmission()) + "\",\"livingFarmers\":" + value.livingFarmers()
                + ",\"availableFarmer\":\"" + quote(value.availableFarmerId()) + "\",\"farmStatus\":\"" + quote(value.farmStatus())
                + "\",\"depotSurface\":\"" + quote(value.depotSurface()) + "\",\"depotHasFreeSlot\":" + value.depotHasFreeSlot()
                + ",\"quarantine\":\"" + (state.humanPopulation().quarantined(subject) ? "QUARANTINED" : "NORMAL") + "\",\"activeCases\":"
                + state.humanPopulation().activeCases(subject) + ",\"food\":{\"status\":\""
                + (ReferenceContainerCustody.blocksCanonicalUse(state, depot) ? "CONFLICT"
                        : SettlementFoodPolicy.breadStock(state, subject) == 0 ? "SHORTAGE"
                        : hungry + starving > 0 ? "RESIDENTS_HUNGRY"
                        : SettlementFoodPolicy.breadStock(state, subject) < reserve ? "RESERVE_LOW" : "SECURE")
                + "\",\"stock\":" + SettlementFoodPolicy.breadStock(state, subject)
                + ",\"available\":" + availableFood + ",\"reserve\":" + reserve + ",\"held\":" + heldFood + ",\"reserved\":" + reservedFood
                + ",\"activeMeals\":" + activeMeals + ",\"nourished\":" + nourished + ",\"hungry\":" + hungry
                + ",\"starving\":" + starving
                + ",\"nextNeedReviewAt\":" + (nextNeedReview.isPresent() ? Long.toString(nextNeedReview.getAsLong()) : "null") + "}"
                + ",\"farmAnchor\":" + position(geometry.farmAnchor())
                + ",\"routeSurface\":" + position(geometry.routeSurface()) + "}";
    }

    /**
     * Read-only bounded census for one named settlement.  The canonical positions are captured
     * before a native first visit; the caller supplies the current physical admission evidence
     * only so the client can bind its own locally rendered UUIDs without choosing a body.
     */
    static String settlementPopulation(CheckpointImage checkpoint, FrontierWorldState state, String id,
                                       java.util.Map<SubjectId, FrontierV3AmbientAdmissionDiagnostic> admissions) {
        SubjectId settlementId = subject(id).orElse(null);
        if (settlementId == null || state.bootstrap().settlements().stream().noneMatch(value -> value.id().equals(settlementId))) {
            return unavailable("settlement_population", id, checkpoint, "not_found");
        }
        java.util.List<ResidentProfile> residents = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId))
                .sorted(java.util.Comparator.comparing(resident -> resident.id().value())).toList();
        // Bootstrap bounds every settlement to 20..40 residents. Refuse an unbounded future
        // projection rather than silently truncating a claimed complete first-visibility set.
        if (residents.isEmpty() || residents.size() > 40) {
            return unavailable("settlement_population", id, checkpoint, "resident_bound");
        }
        HumanAssignmentProjection assignments = HumanAssignmentProjection.compile(state);
        String values = residents.stream().map(resident -> {
            ActorLocation location = state.actorLocations().get(resident.id());
            if (location == null) return "";
            FrontierV3AmbientAdmissionDiagnostic admission = admissions.get(resident.id());
            // Keep this full-set receipt below the chat diagnostic budget: the individual actor
            // view owns observed position/tracker detail.  First visibility needs only the
            // deterministic expected UUID and its pre-visit canonical station/phase.
            String entityUuid = admission == null || admission.entityId() == null ? "" : admission.entityId().toString();
            String admissionStatus = admission == null ? "UNOBSERVED" : admission.status();
            return "{\"actor\":\"" + quote(resident.id().value()) + "\",\"life\":\"" + location.condition().status()
                    + "\",\"position\":" + position(location.body()) + ",\"dutyPhase\":\""
                    + quote(harvestDutyPhase(state, resident.id(), assignments.assignment(resident.id()))) + "\",\"entityUuid\":\""
                    + quote(entityUuid) + "\",\"admission\":\"" + quote(admissionStatus) + "\"}";
        }).filter(value -> !value.isEmpty()).collect(java.util.stream.Collectors.joining(","));
        if (values.isEmpty()) return unavailable("settlement_population", id, checkpoint, "missing_actor_location");
        return bounded("settlement_population", id, checkpoint, base("settlement_population", id, checkpoint)
                + ",\"status\":\"ok\",\"settlement\":\"" + quote(settlementId.value()) + "\",\"residentCount\":"
                + residents.size() + ",\"residents\":[" + values + "]}");
    }
    /** One named-polity diagnostic, bounded to aggregate counts plus the single next growth claim. */
    private static String hive(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null);
        if (subject == null || !subject.equals(state.bootstrap().hive().id())) return unavailable("hive", id, checkpoint, "not_found");
        var next = state.hiveColony().growthJobs().values().stream().sorted(java.util.Comparator.comparing(value -> value.id().value())).findFirst();
        String growth = next.map(value -> ",\"growthJob\":\"" + quote(value.id().value()) + "\",\"growthIntent\":\""
                + quote(value.consumptionIntentId().value()) + "\",\"nextOrgan\":\"" + quote(value.organ().id().value())
                + "\",\"nextBioform\":\"" + quote(value.bioform().id().value()) + "\"").orElse("");
        return base("hive", id, checkpoint) + ",\"status\":\"ok\",\"addedOrgans\":" + state.hiveColony().addedOrgans().size()
                + ",\"spawnedBioforms\":" + state.hiveColony().spawnedBioforms().size() + ",\"growthJobs\":" + state.hiveColony().growthJobs().size()
                + ",\"infectionCells\":" + state.infection().size() + growth + "}";
    }

    /** One exact nutrient corridor or terminal receipt; no aggregate hive stock is exposed. */
    private static String hiveTransfer(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null); if (subject == null) return unavailable("hive_transfer", id, checkpoint, "not_found");
        HiveNutrientTransfer transfer = state.hiveColony().nutrientTransfers().get(subject);
        if (transfer != null) {
            String endpoint = transfer.endpointIntentId().map(value -> ",\"endpointIntent\":\"" + quote(value.value()) + "\"").orElse("");
            String blocked = transfer.blockReason().map(value -> ",\"blockReason\":\"" + quote(value.name()) + "\"").orElse("");
            return base("hive_transfer", id, checkpoint) + ",\"status\":\"ok\",\"phase\":\"" + transfer.phase().name()
                    + "\",\"cursor\":" + transfer.cursor() + ",\"corridorNodes\":" + transfer.corridor().size()
                    + ",\"sourceStore\":\"" + quote(transfer.sourceStoreId().value()) + "\",\"targetStore\":\"" + quote(transfer.targetStoreId().value())
                    + "\",\"cargo\":\"" + quote(transfer.cargoId().value()) + "\",\"item\":\"" + quote(transfer.itemId().value()) + "\"" + endpoint + blocked + "}";
        }
        HiveNutrientReceipt receipt = state.hiveColony().nutrientReceipts().get(subject);
        if (receipt == null) return unavailable("hive_transfer", id, checkpoint, "not_found");
        String consumed = receipt.consumedByJobId().map(value -> ",\"consumedByJob\":\"" + quote(value.value()) + "\"").orElse("");
        return base("hive_transfer", id, checkpoint) + ",\"status\":\"ok\",\"phase\":\"COMPLETED\",\"receiptStatus\":\""
                + receipt.status().name() + "\",\"sourceStore\":\"" + quote(receipt.sourceSlot().containerId().value()) + "\",\"targetStore\":\""
                + quote(receipt.targetSlot().containerId().value()) + "\",\"cargo\":\"" + quote(receipt.cargoId().value()) + "\",\"item\":\"" + quote(receipt.itemId().value()) + "\"" + consumed + "}";
    }

    private static String lane(FrontierSettlementWorkDiagnostic.Lane lane) {
        return "{\"objective\":\"" + quote(lane.objectiveId()) + "\",\"kind\":\"" + quote(lane.kind())
                + "\",\"status\":\"" + quote(lane.status()) + "\"}";
    }

    private static String actor(String id, CheckpointImage checkpoint, FrontierWorldState state,
                                Optional<FrontierV3AmbientAdmissionDiagnostic> admission) {
        SubjectId subject = subject(id).orElse(null); ActorLocation location = subject == null ? null : state.actorLocations().get(subject);
        if (location == null) return unavailable("actor", id, checkpoint, "not_found");
        ResidentProfile resident = state.humanPopulation().resident(subject);
        Bioform bioform = bioform(state, subject).orElse(null);
        String role = resident != null ? resident.profession().name() : bioform != null ? bioform.chassis().name() + "/" + bioform.assignment().name() : "UNKNOWN";
        String owner = resident != null ? resident.settlementId().value() : bioform != null ? bioform.hiveId().value() : "";
        String nutrition = resident == null ? "" : state.humanPopulation().nutrition(subject).status().name();
        var assignment = resident == null ? null : HumanAssignmentProjection.compile(state).assignment(subject);
        String dutyPhase = harvestDutyPhase(state, subject, assignment);
        var lifecycle = bioform == null ? null : state.hiveColony().bioformLifecycles().get(subject);
        String cocoonHome = lifecycle == null || lifecycle.homeSlot().isEmpty() ? "null" : "{\"hibernaculum\":\""
                + quote(lifecycle.homeSlot().orElseThrow().hibernaculumId().value()) + "\",\"slot\":"
                + lifecycle.homeSlot().orElseThrow().index() + "}";
        var lease = state.ambientLeases().get(subject);
        String ambientGoal = lease == null ? "NONE" : lease.goal().name();
        String goalPosition = lease == null ? "null" : position(lease.goalBody().supportingSurface().support());
        return base("actor", id, checkpoint) + ",\"status\":\"ok\",\"actorKind\":\"" + (resident != null ? "RESIDENT" : "BIOFORM")
                + "\",\"owner\":\"" + quote(owner) + "\",\"role\":\"" + role + "\",\"life\":\"" + location.condition().status()
                + "\",\"healthRaw\":" + location.condition().health().raw() + ",\"position\":" + position(location.body())
                + ",\"nutrition\":\"" + quote(nutrition) + "\",\"ambientLease\":\""
                + quote(lease == null ? "NONE" : lease.status().name()) + "\",\"ambientGoal\":\"" + quote(ambientGoal)
                + "\",\"goalPosition\":" + goalPosition
                + Optional.ofNullable(state.strategicPlans().scoutPatrols().get(subject))
                    .map(journey -> ",\"retainedScoutGoalRevision\":" + journey.goalRevision()
                            + ",\"retainedScoutGoalPosition\":" + position(journey.target().support())
                            + ",\"retainedScoutExecutionGeneration\":" + journey.executionId().generation()).orElse("")
                + ",\"assignment\":\"" + (assignment == null ? "NONE" : assignment.kind().name())
                + "\",\"assignmentOwner\":\"" + quote(assignment == null ? "" : assignment.ownerId().map(SubjectId::value).orElse("")) + "\""
                + ",\"dutyPhase\":\"" + quote(dutyPhase) + "\""
                + (lifecycle == null ? "" : ",\"lifecycle\":\"" + lifecycle.phase().name() + "\",\"cocoonHome\":" + cocoonHome)
                + admission.map(FrontierV3DiagnosticJson::admission).orElse("") + "}";
    }


    /** One immutable assignment lookup gives the client a phase at its already-bounded actor cadence. */
    private static String harvestDutyPhase(FrontierWorldState state, SubjectId actor, HumanAssignment assignment) {
        if (assignment == null || assignment.ownerId().isEmpty()) {
            var ambient = state.ambientLeases().get(actor);
            if (ambient != null && ambient.status() != io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.CLOSED) {
                // A completed harvest deliberately hands the same body to shared ambient
                // custody before a later growth epoch can assign its successor scene.  This
                // is an owned, inspectable phase, not a diagnostic absence merely because no
                // harvest job currently owns the resident.
                return "AMBIENT:" + ambient.goal().name() + ":" + ambient.status().name();
            }
            return "IDLE:" + (ambient == null ? "UNLEASED" : "LEASE_CLOSED");
        }
        SubjectId owner = assignment.ownerId().orElseThrow();
        ResourceSiteHarvestJob job = state.resourceSites().sites().values().stream()
                .flatMap(site -> site.harvestJobs().values().stream())
                .filter(candidate -> candidate.id().equals(owner) && candidate.workerId().equals(actor))
                .findFirst().orElse(null);
        if (job == null) return "UNOBSERVED";
        PhysicalIntent intent = state.physicalIntents().get(job.intentId());
        String status = intent == null ? "MISSING" : intent.status().name();
        return (ResourceSiteHarvestGoal.actorAtWorkCell(state, job) ? "HARVESTING:" : "TRAVELLING:") + status;
    }

    /** One exact resident's durable movement corridor; diagnostics never choose, advance or unblock it. */
    private static String transit(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null);
        var journey = subject == null ? null : state.humanPopulation().migration(subject);
        if (journey == null) return unavailable("transit", id, checkpoint, "not_found");
        String next = journey.arriving() ? "null" : position(journey.nextColdPosition());
        var lease = state.ambientLeases().get(subject);
        String goal = lease == null ? "NONE" : lease.goal().name();
        String goalPosition = lease == null ? "null" : position(lease.goalBody().supportingSurface().support());
        return base("transit", id, checkpoint) + ",\"status\":\"ok\",\"journeyStatus\":\"" + journey.status()
                + "\",\"origin\":\"" + quote(journey.originSettlementId().value()) + "\",\"destination\":\""
                + quote(journey.destinationSettlementId().value()) + "\",\"routeIndex\":" + journey.routeIndex()
                + ",\"routeLength\":" + journey.route().size() + ",\"current\":" + position(journey.currentPosition())
                + ",\"spatialRevision\":" + journey.routeRevision() + ",\"approachCursor\":" + journey.rejoin().map(value -> value.cursor()).orElse(-1)
                + ",\"waitingOrigin\":" + journey.spatial().waitingOrigin().map(value -> position(value.support())).orElse("null")
                + ",\"next\":" + next + ",\"ambientLease\":\"" + quote(lease == null ? "NONE" : lease.status().name())
                + "\",\"ambientGoal\":\"" + quote(goal) + "\",\"goalPosition\":" + goalPosition + "}";
    }

    private static String admission(FrontierV3AmbientAdmissionDiagnostic value) {
        String placement = value.placement() == null ? "null" : position(value.placement());
        String observedPosition = value.observedPosition() == null ? "null" : position(value.observedPosition());
        String observedExact = nullablePosition(value.observedExact());
        FrontierV3ControlledMobMotion.MotionObservation motion = value.motion();
        String motionTarget = motion.target() == null ? "null" : "{\"x\":" + motion.target().x + ",\"y\":" + motion.target().y + ",\"z\":" + motion.target().z + "}";
        return ",\"physicalAdmission\":{\"status\":\"" + quote(value.status()) + "\",\"entityUuid\":\""
                + quote(value.entityId() == null ? "" : value.entityId().toString()) + "\",\"pending\":" + value.pending() + ",\"placement\":" + placement
                + ",\"observedPosition\":" + observedPosition + ",\"observedExact\":" + observedExact
                + ",\"trackerCalls\":" + value.trackerCalls() + ",\"trackerImpulseCalls\":" + value.trackerImpulseCalls()
                + ",\"motionStatus\":\"" + quote(motion.status()) + "\",\"motionTarget\":" + motionTarget
                + ",\"motionAcceptedMoves\":" + motion.acceptedMoves()
                + FrontierV3NavigationDiagnosticJson.fragment(value.navigation()) + "}";
    }

    private static String item(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null); ExactItemStack item = subject == null ? null : state.inventory().items().get(subject);
        if (item == null) return unavailable("item", id, checkpoint, "not_found");
        return base("item", id, checkpoint) + ",\"status\":\"ok\",\"owner\":\"" + quote(item.economicOwnerId().value())
                + "\",\"itemKind\":\"" + quote(item.itemKind()) + "\",\"count\":" + item.count() + ",\"custody\":" + custody(item.custody()) + "}";
    }

    /**
     * One account's exact fungible custody projection.  Physical addresses are retained
     * evidence only: lots and claims remain the canonical quantity and reservation owners.
     */
    private static String resource(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId accountId = subject(id).orElse(null);
        if (accountId == null) return unavailable("resource", id, checkpoint, "not_found");
        var resources = state.inventory().fungibleResources();
        CustodyAccount account = resources.accounts().get(accountId);
        if (account == null) return unavailable("resource", id, checkpoint, "not_found");
        String lots = account.lotQuantities().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).map(entry -> {
            ResourceLot lot = resources.lots().get(entry.getKey());
            return lot == null ? "" : "{\"id\":\"" + quote(lot.id().value()) + "\",\"owner\":\""
                    + quote(lot.economicOwnerId().value()) + "\",\"itemKind\":\"" + quote(lot.itemKind())
                    + "\",\"quantity\":" + entry.getValue() + "}";
        }).filter(value -> !value.isEmpty()).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String claims = account.claimQuantities().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).map(entry -> {
            ClaimAllocation claim = resources.claims().get(entry.getKey());
            return claim == null ? "" : "{\"id\":\"" + quote(claim.id().value()) + "\",\"claimant\":\""
                    + quote(claim.claimantId().value()) + "\",\"owner\":\"" + quote(claim.economicOwnerId().value())
                    + "\",\"itemKind\":\"" + quote(claim.itemKind()) + "\",\"quantity\":" + entry.getValue() + "}";
        }).filter(value -> !value.isEmpty()).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        java.util.List<PhysicalStackBinding> accountBindings = resources.bindings().values().stream().filter(binding -> binding.accountId().equals(account.id()))
                .sorted(java.util.Comparator.comparing(PhysicalStackBinding::id)).toList();
        String bindings = accountBindings.stream().map(binding -> "{\"id\":\""
                        + quote(binding.id().value()) + "\",\"epoch\":" + binding.authorityEpoch() + ",\"itemKind\":\""
                        + quote(binding.itemKind()) + "\",\"quantity\":" + binding.quantity() + ",\"address\":"
                        + resourceAddress(binding.address()) + "}").collect(java.util.stream.Collectors.joining(",", "[", "]"));
        int quantity = account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum();
        return base("resource", id, checkpoint) + ",\"status\":\"ok\",\"account\":\"" + quote(account.id().value())
                + "\",\"custody\":" + resourceCustody(account.custody()) + ",\"quantity\":" + quantity
                + ",\"lots\":" + lots + ",\"claims\":" + claims + ",\"bindingCount\":" + accountBindings.size()
                + ",\"bindings\":" + bindings + "}";
    }

    private static String resourceCustody(ResourceCustody custody) {
        if (custody instanceof ResourceCustody.Container value) return "{\"kind\":\"CONTAINER\",\"container\":\"" + quote(value.containerId().value()) + "\"}";
        if (custody instanceof ResourceCustody.Player value) return "{\"kind\":\"PLAYER\",\"player\":\"" + value.playerId() + "\"}";
        if (custody instanceof ResourceCustody.Cargo value) return "{\"kind\":\"CARGO\",\"cargo\":\"" + quote(value.cargoId().value()) + "\"}";
        if (custody instanceof ResourceCustody.WorldCarrier value) return "{\"kind\":\"WORLD_CARRIER\",\"carrier\":\"" + value.carrierId() + "\"}";
        ResourceCustody.Actor value = (ResourceCustody.Actor) custody;
        return "{\"kind\":\"ACTOR\",\"actor\":\"" + quote(value.actorId().value()) + "\"}";
    }

    private static String resourceAddress(PhysicalStackAddress address) {
        if (address instanceof PhysicalStackAddress.ContainerSlot value) return "{\"kind\":\"CONTAINER_SLOT\",\"container\":\""
                + quote(value.slot().containerId().value()) + "\",\"slot\":" + value.slot().slot() + "}";
        if (address instanceof PhysicalStackAddress.PlayerSlot value) return "{\"kind\":\"PLAYER_SLOT\",\"player\":\""
                + value.playerId() + "\",\"slot\":" + value.slot() + "}";
        if (address instanceof PhysicalStackAddress.HopperSlot value) return "{\"kind\":\"HOPPER_SLOT\",\"position\":"
                + position(value.position()) + ",\"slot\":" + value.slot() + "}";
        if (address instanceof PhysicalStackAddress.ActorHand value) return "{\"kind\":\"ACTOR_HAND\",\"actor\":\""
                + quote(value.actorId().value()) + "\",\"entity\":\"" + value.entityId() + "\",\"hand\":\"" + value.hand() + "\"}";
        if (address instanceof PhysicalStackAddress.ActorPocket value) return "{\"kind\":\"ACTOR_POCKET\",\"actor\":\""
                + quote(value.actorId().value()) + "\",\"entity\":\"" + value.entityId() + "\",\"slot\":" + value.slot() + "}";
        PhysicalStackAddress.WorldEntity value = (PhysicalStackAddress.WorldEntity) address;
        return "{\"kind\":\"WORLD_ENTITY\",\"entity\":\"" + value.entityId() + "\"}";
    }

    /** One bounded exact-container projection for test-pilot and operator inspection. */
    private static String container(String id, CheckpointImage checkpoint, FrontierWorldState state,
                                    Optional<FrontierV3ContainerSurfaceExecutor.Readiness> readiness) {
        SubjectId subject = subject(id).orElse(null); ContainerRecord container = subject == null ? null : state.inventory().containers().get(subject);
        ContainerSurface surface = subject == null ? null : state.inventory().surfaces().get(subject);
        if (container == null || surface == null) return unavailable("container", id, checkpoint, "not_found");
        java.util.List<ExactItemStack> occupiedItems = state.inventory().items().values().stream().filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot
                        && slot.containerId().equals(subject)).sorted(java.util.Comparator.comparingInt(item -> ((InventoryCustody.ContainerSlot) item.custody()).slot()))
                .toList();
        String occupied = occupiedItems.stream().map(item -> "{\"slot\":" + ((InventoryCustody.ContainerSlot) item.custody()).slot() + ",\"item\":\"" + quote(item.id().value())
                        + "\",\"itemKind\":\"" + quote(item.itemKind()) + "\",\"count\":" + item.count() + "}")
                .reduce((left, right) -> left + "," + right).map(value -> "[" + value + "]").orElse("[]");
        // Exact-item occupancy alone hides the wheat/bread lots that now cross the same
        // physical chest. Keep the legacy exact projection explicit and expose current
        // fungible bindings separately; neither a missing exact item nor a fungible lot is
        // inferred from a bare Vanilla stack.
        var fungibleBindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.address() instanceof io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress.ContainerSlot slot
                        && slot.slot().containerId().equals(subject))
                .sorted(java.util.Comparator.comparingInt(binding -> ((io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress.ContainerSlot)
                        binding.address()).slot().slot()))
                .toList();
        String fungibleOccupied = fungibleBindings.stream().map(binding -> {
                    var slot = (io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress.ContainerSlot) binding.address();
                    return "{\"slot\":" + slot.slot().slot() + ",\"account\":\"" + quote(binding.accountId().value())
                            + "\",\"itemKind\":\"" + quote(binding.itemKind()) + "\",\"count\":" + binding.quantity() + "}";
                }).reduce((left, right) -> left + "," + right).map(value -> "[" + value + "]").orElse("[]");
        String physical = readiness.map(value -> ",\"physicalSocket\":{\"chunk\":\"" + quote(value.chunk())
                + "\",\"freshSocket\":\"" + quote(value.freshSocket()) + "\",\"support\":\"" + quote(value.support())
                + "\",\"targetBlock\":\"" + quote(value.targetBlock()) + "\",\"chest\":\"" + quote(value.chest())
                + "\",\"slots\":\"" + quote(value.slots()) + "\",\"mismatch\":\"" + quote(value.mismatch())
                + "\",\"ordinaryPlayerNearby\":" + value.ordinaryPlayerNearby()
                + ",\"presentationDemand\":" + value.presentationDemand()
                + ",\"eligibleObserverCount\":" + value.eligibleObserverCount()
                + ",\"presentationObserverCount\":" + value.presentationObserverCount() + "}").orElse("");
        String replica = referenceCustody(state, subject);
        String canonicalCapacity = FrontierV3ContainerCapacityJson.render(state, subject);
        return base("container", id, checkpoint) + ",\"status\":\"ok\",\"owner\":\"" + quote(container.ownerId().value())
                + "\",\"surface\":\"" + surface.status() + "\",\"position\":" + position(surface.position()) + ",\"slotCount\":" + container.slotCount()
                + ",\"occupiedCount\":" + occupiedItems.size() + ",\"occupied\":" + occupied
                + ",\"fungibleOccupiedCount\":" + fungibleBindings.size() + ",\"fungibleOccupied\":" + fungibleOccupied
                + canonicalCapacity + replica + physical + "}";
    }

    /** Reference scopes report canonical stock separately from replica evidence and temporary lease authority. */
    private static String referenceCustody(FrontierWorldState state, SubjectId containerId) {
        if (!io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody.isReferenceContainer(state, containerId)) return "";
        var record = state.replicaCustody().replicas().get(containerId);
        var lease = state.replicaCustody().custodyByScope().get(io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody.scopeId(containerId));
        String replica = record == null ? "null" : "{\"state\":\"" + record.state() + "\",\"revision\":" + record.replicaRevision()
                + ",\"canonicalRevision\":" + record.observedCanonicalRevision() + ",\"conflict\":\""
                + quote(record.conflictReason().map(Enum::name).orElse("")) + "\",\"fingerprint\":\"" + quote(record.fingerprint())
                + "\",\"provenance\":\"" + quote(record.provenance()) + "\",\"observedFingerprint\":\""
                + quote(record.observedFingerprint().orElse("")) + "\",\"observedProvenance\":\""
                + quote(record.observedProvenance().orElse("")) + "\"}";
        String custody = lease == null ? "null" : "{\"status\":\"" + lease.status() + "\",\"epoch\":" + lease.authorityEpoch()
                + ",\"replicaRevision\":" + lease.expectedReplicaRevision() + ",\"unresolvedReason\":\""
                + quote(lease.unresolvedReason() == null ? "" : lease.unresolvedReason().name()) + "\"}";
        return ",\"replica\":" + replica + ",\"custody\":" + custody;
    }

    /**
     * Bounded F0.2B causal projection.  It reports the one representative
     * depot and hive task/schedule/reservation owners without creating a
     * second ledger or inferring history from a chest endpoint.
     */
    private static String referenceContainer(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        // Historical F0.2B composition report, not an arbitrary container lookup.
        // Never label its fixed subjects with a caller-supplied container identity.
        if (!"f02b".equals(id)) return unavailable("reference_container", id, checkpoint, "not_found");
        SubjectId settlement = new SubjectId("settlement:1");
        SubjectId hive = state.bootstrap().hive().id();
        java.util.List<StrategicTask> tasks = state.strategicPlans().tasks().values().stream()
                .filter(task -> (task.kind() == StrategicTaskKind.PRODUCE_BREAD && task.ownerId().equals(settlement))
                        || (task.kind() == StrategicTaskKind.GROW_HIVE_ORGANISM && task.ownerId().equals(hive)))
                .sorted(java.util.Comparator.comparing(StrategicTask::id)).toList();
        java.util.Set<SubjectId> taskIds = tasks.stream().map(StrategicTask::id).collect(java.util.stream.Collectors.toSet());
        String taskEntries = tasks.stream().map(task -> "{\"id\":\"" + quote(task.id().value()) + "\",\"kind\":\"" + task.kind()
                + "\",\"status\":\"" + task.status() + "\"}").collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String orders = state.companies().market().workOrders().values().stream().filter(order -> taskIds.contains(order.taskId()))
                .sorted(java.util.Comparator.comparing(MarketWorkOrder::id)).map(order -> "{\"task\":\"" + quote(order.taskId().value())
                        + "\",\"job\":\"" + quote(order.jobId().value()) + "\",\"reservation\":\"" + quote(order.reservationId().value())
                        + "\",\"reservationActive\":" + state.inventory().economics().reservations().containsKey(order.reservationId())
                        + ",\"status\":\"" + order.status() + "\"}").collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String production = state.productionJobs().values().stream().filter(job -> job.settlementId().equals(settlement))
                .sorted(java.util.Comparator.comparing(ProductionJob::id)).map(job -> "{\"id\":\"" + quote(job.id().value()) + "\",\"input\":\""
                        + quote(job.consumedItemId().value()) + "\",\"output\":\"" + quote(job.outputItemId().value()) + "\",\"count\":" + job.outputCount()
                        + ",\"hold\":\"" + job.inputHold().getClass().getSimpleName() + "\"}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String growth = state.hiveColony().growthJobs().values().stream().filter(job -> job.hiveId().equals(hive))
                .sorted(java.util.Comparator.comparing(HiveGrowthJob::id)).map(job -> "{\"id\":\"" + quote(job.id().value()) + "\",\"input\":\""
                        + quote(job.consumedItemId().value()) + "\",\"intent\":\"" + quote(job.consumptionIntentId().value())
                        + "\",\"organ\":\"" + quote(job.organ().id().value()) + "\",\"bioform\":\"" + quote(job.bioform().id().value()) + "\"}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        // This remains a bounded, read-only F0.2B projection: one settlement
        // can have at most one active birth commitment. It lets the normal-world
        // receipt distinguish COLD canonical food custody from a later unrelated
        // 63-bread endpoint without making demography a container authority.
        java.util.List<ResidentBirthJob> births = state.humanPopulation().birthJobs().values().stream()
                .filter(job -> job.settlementId().equals(settlement))
                .sorted(java.util.Comparator.comparing(ResidentBirthJob::id)).toList();
        java.util.Set<SubjectId> birthIds = births.stream().map(ResidentBirthJob::id).collect(java.util.stream.Collectors.toSet());
        String birthEntries = births.stream().map(job -> "{\"id\":\"" + quote(job.id().value()) + "\",\"food\":\""
                        + quote(job.foodItemId().value()) + "\",\"commitment\":\"" + quote(job.foodCommitmentId().value())
                        + "\",\"resident\":\"" + quote(job.resident().id().value()) + "\"}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        java.util.Set<SubjectId> subjects = new java.util.HashSet<>(taskIds);
        state.productionJobs().values().stream().filter(job -> job.settlementId().equals(settlement)).map(ProductionJob::id).forEach(subjects::add);
        state.hiveColony().growthJobs().values().stream().filter(job -> job.hiveId().equals(hive)).map(HiveGrowthJob::id).forEach(subjects::add);
        subjects.addAll(birthIds);
        String schedules = checkpoint.schedules().stream().filter(action -> subjects.contains(action.subject()))
                .sorted().map(action -> "{\"id\":\"" + quote(action.id().value()) + "\",\"subject\":\"" + quote(action.subject().value())
                        + "\",\"kind\":\"" + quote(action.kind()) + "\",\"dueAt\":" + action.dueAt().ticks() + ",\"weight\":" + action.weight() + "}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String intents = state.physicalIntents().values().stream().filter(intent -> subjects.contains(intent.causeSubjectId()))
                .sorted(java.util.Comparator.comparing(PhysicalIntent::id)).map(intent -> "{\"id\":\"" + quote(intent.id().value())
                        + "\",\"cause\":\"" + quote(intent.causeSubjectId().value()) + "\",\"kind\":\"" + intent.kind()
                        + "\",\"status\":\"" + intent.status() + "\"}").collect(java.util.stream.Collectors.joining(",", "[", "]"));
        return base("reference_container", id, checkpoint) + ",\"status\":\"ok\",\"tasks\":" + taskEntries
                + ",\"schedules\":" + schedules + ",\"orders\":" + orders + ",\"productionJobs\":" + production + ",\"growthJobs\":" + growth
                + ",\"birthJobs\":" + birthEntries + ",\"physicalIntents\":" + intents + "}";
    }

    private static String marketOrder(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null);
        FrontierMarketOrderDiagnostic order = subject == null ? null : FrontierMarketOrderDiagnostic.inspect(state, subject).orElse(null);
        if (order == null) return unavailable("market_order", id, checkpoint, "not_found");
        String terminal = state.companies().market().workOrders().get(subject).terminalReceipt().map(receipt -> "{\"job\":\""
                + quote(receipt.jobId().value()) + "\",\"worker\":\"" + quote(receipt.workerId().value()) + "\",\"inputItem\":\""
                + quote(receipt.inputId().value()) + "\",\"outputItem\":\"" + quote(receipt.outputId().value()) + "\",\"inputRepresentation\":\""
                + receipt.inputRepresentation() + "\",\"outputRepresentation\":\"" + receipt.outputRepresentation() + "\",\"outputKind\":\""
                + quote(receipt.outputKind()) + "\",\"outputCount\":" + receipt.outputCount() + ",\"topology\":{\"id\":\""
                + quote(receipt.topologyId().value()) + "\",\"revision\":\"" + receipt.topologyRevision() + "\",\"cursor\":" + receipt.traversalCursor()
                + ",\"terminalBody\":" + position(receipt.terminalBody()) + "}}").orElse("null");
        return base("market_order", id, checkpoint) + ",\"status\":\"ok\",\"orderStatus\":\"" + quote(order.status())
                + "\",\"job\":\"" + quote(order.jobId().value()) + "\",\"jobActive\":" + order.jobActive()
                + ",\"reservation\":\"" + quote(order.reservationId().value()) + "\",\"reservationActive\":" + order.reservationActive()
                + ",\"taskStatus\":\"" + quote(order.taskStatus()) + "\",\"terminalReceipt\":" + terminal + "}";
    }


    /** Bounded operator evidence for the one retained command source of an active hive engagement. */

    /** One declared supply topology, with grade facts only; diagnostics never survey or alter Minecraft terrain. */
    private static String routeTopology(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId settlement = subject(id).orElse(null);
        if (settlement == null || state.bootstrap().settlements().stream().noneMatch(value -> value.id().equals(settlement))) {
            return unavailable("route_topology", id, checkpoint, "not_found");
        }
        var topology = state.routeTopology().settlementTraversalTopology(state.bootstrap(), settlement);
        var grades = topology.edges().stream().filter(edge -> edge.grade() > 0).toList();
        int maximumGrade = grades.stream().mapToInt(io.farfrontier.palemirror.frontier.v3.model.TraversalTopology.Edge::grade).max().orElse(0);
        boolean routePassable = state.routeTopology().routePassable(state.bootstrap(), settlement);
        String firstGrade = grades.isEmpty() ? "null" : "{\"from\":" + position(topology.nodes().get(grades.getFirst().from()).support())
                + ",\"to\":" + position(topology.nodes().get(grades.getFirst().to()).support()) + "}";
        return base("route_topology", id, checkpoint) + ",\"status\":\"ok\",\"topology\":\"" + quote(topology.id().value())
                + "\",\"revision\":" + topology.revision() + ",\"nodes\":" + topology.nodes().size() + ",\"edges\":" + topology.edges().size()
                + ",\"routePassable\":" + routePassable
                + ",\"gradedEdges\":" + grades.size() + ",\"maximumGrade\":" + maximumGrade + ",\"firstGrade\":" + firstGrade + "}";
    }

    /** Bounded exact cursors make a stalled ordinary HOT approach diagnosable without world mutation. */


    /** One exact durable world-change fact, keyed by a canonical x,y,z cell rather than a player identity. */
    private static String physicalDelta(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        BlockPosition position = parsePosition(id).orElse(null);
        PhysicalDelta delta = position == null ? null : state.physicalDeltas().get(position);
        if (delta == null) return unavailable("physical_delta", id, checkpoint, "not_found");
        String owner = delta.ownerId().map(SubjectId::value).orElse("");
        String part = delta.semanticPart().map(Enum::name).orElse("");
        String causeKind = delta.cause().startsWith("player:") ? "PLAYER" : "SYSTEM";
        return base("physical_delta", id, checkpoint) + ",\"status\":\"ok\",\"deltaKind\":\"" + delta.kind()
                + "\",\"owner\":\"" + quote(owner) + "\",\"semanticPart\":\"" + quote(part)
                + "\",\"causeKind\":\"" + causeKind + "\",\"trace\":\""
                + quote(FrontierV3DiagnosticTrace.physicalDeltaCorrelation(position)) + "\"}";
    }

    /** One exact medical owner, including its patient, retained team and physical remedy receipt. */
    private static String medical(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null);
        MedicalEvacuationOperation operation = subject == null ? null : state.humanPopulation().medicalOperations().get(subject);
        if (operation == null) return unavailable("medical", id, checkpoint, "not_found");
        PhysicalIntent intent = state.physicalIntents().get(operation.consumptionIntentId());
        String team = operation.team().memberIds().stream().sorted().map(member -> "\"" + quote(member.value()) + "\"")
                .reduce((left, right) -> left + "," + right).orElse("");
        return base("medical", id, checkpoint) + ",\"status\":\"ok\",\"settlement\":\"" + quote(operation.settlementId().value())
                + "\",\"patient\":\"" + quote(operation.patientId().value()) + "\",\"patientHealth\":\""
                + state.humanPopulation().health(operation.patientId()).status() + "\",\"team\":[" + team + "]"
                + ",\"medicalStatus\":\"" + operation.status() + "\",\"intent\":\"" + quote(operation.consumptionIntentId().value())
                + "\",\"intentStatus\":\"" + (intent == null ? "MISSING" : intent.status()) + "\"}";
    }

    /** One bounded, read-only COLD aftermath receipt selected by exact id or exact causal selector. */
    private static String aftermath(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        var value = id.startsWith("cause:") ? state.deferredAftermath().entries().values().stream()
                .filter(entry -> entry.causeId().value().equals(id))
                .sorted(java.util.Comparator.comparing(entry -> entry.id())).findFirst().orElse(null)
                : state.deferredAftermath().entries().get(subject(id).orElse(null));
        if (value == null) return unavailable("aftermath", id, checkpoint, "not_found");
        // The completed cell remains visible after the cursor has reached its terminal state.
        // A terminal cursor alone is not evidence that the declared physical consequence happened.
        var cell = value.terminal() ? value.cells().getLast() : value.nextPending();
        return base("aftermath", id, checkpoint) + ",\"status\":\"ok\",\"aftermathId\":\"" + quote(value.id().value())
                + "\",\"cause\":\"" + quote(value.causeId().value()) + "\",\"provenance\":\"" + quote(value.provenance())
                + "\",\"knowledge\":\"" + value.knowledge() + "\",\"epoch\":" + value.expectedEpoch() + ",\"eventAt\":" + value.eventAt() + ",\"observedAt\":"
                + (value.observedAt().isPresent() ? value.observedAt().getAsLong() : "null") + ",\"cursor\":" + value.resolutionCursor()
                + ",\"cells\":" + value.cells().size() + ",\"terminal\":" + value.terminal() + ",\"expectedMaterial\":\"" + cell.expectedMaterial() + "\",\"nextStatus\":\""
                + (value.terminal() ? "NONE" : cell.status()) + "\",\"cellStatus\":\"" + cell.status()
                + "\",\"position\":" + position(cell.position()) + ",\"expectedOwner\":\"" + quote(cell.expectedOwner().value())
                + "\",\"expectedPart\":\"" + cell.expectedPart() + "\",\"authorityRevision\":" + cell.authorityRevision() + "}";
    }

    private static String intent(String id, CheckpointImage checkpoint, FrontierWorldState state,
                                 Optional<FrontierV3ResourceSiteHarvestExecutor.Readiness> harvestReadiness,
                                 Optional<FrontierV3EquipmentIssueExecutor.Readiness> equipmentIssueReadiness,
                                 Optional<FrontierV3EquipmentReturnExecutor.Readiness> equipmentReturnReadiness) {
        PhysicalIntent intent;
        try { intent = state.physicalIntents().get(new PhysicalIntentId(id)); }
        catch (IllegalArgumentException invalid) { intent = null; }
        if (intent == null) return unavailable("intent", id, checkpoint, "not_found");
        // Presentation-only deterministic compatibility projection; it has no execution authority.
        String subjects = intent.roles().subjectIds().stream().sorted().map(value -> "\"" + quote(value.value()) + "\"").reduce((left, right) -> left + "," + right).orElse("");
        return base("intent", id, checkpoint) + ",\"status\":\"ok\",\"intentKind\":\"" + intent.kind()
                + "\",\"intentStatus\":\"" + intent.status() + "\",\"causeSubject\":\"" + quote(intent.causeSubjectId().value())
                + "\",\"radius\":" + intent.radiusBlocks() + ",\"subjects\":[" + subjects + "]"
                + ",\"receiptId\":\"" + quote(intent.postconditionObservationId().map(value -> value.value()).orElse("")) + "\""
                + (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.RESOURCE_SITE_HARVEST
                    ? harvestReadiness.map(FrontierV3DiagnosticExecutorJson::harvestReadiness).orElse("") : "")
                + (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_ISSUE
                    ? equipmentIssueReadiness.map(FrontierV3DiagnosticExecutorJson::equipmentIssueReadiness).orElse("") : "")
                + (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_RETURN
                    ? equipmentReturnReadiness.map(FrontierV3DiagnosticExecutorJson::equipmentReturnReadiness).orElse("") : "") + "}";
    }

    static String unavailable(String kind, String id, CheckpointImage checkpoint, String status) {
        return base(kind, id, checkpoint) + ",\"status\":\"" + quote(status) + "\"}";
    }

    static String base(String kind, String id, CheckpointImage checkpoint) {
        return "{\"schema\":1,\"kind\":\"" + quote(kind) + "\",\"id\":\"" + quote(id) + "\",\"world\":\""
                + quote(checkpoint.worldId().value()) + "\",\"revision\":" + checkpoint.revision().value() + ",\"instant\":" + checkpoint.instant().ticks();
    }

    private static Optional<BlockPosition> parsePosition(String id) {
        String[] parts = id.split(",", -1);
        if (parts.length != 3) return Optional.empty();
        try {
            return Optional.of(new BlockPosition(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        } catch (NumberFormatException invalid) {
            return Optional.empty();
        }
    }

    static Optional<SubjectId> subject(String value) { try { return Optional.of(new SubjectId(value)); } catch (IllegalArgumentException invalid) { return Optional.empty(); } }
    private static Optional<Bioform> bioform(FrontierWorldState state, SubjectId id) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream()).filter(value -> value.id().equals(id)).findFirst();
    }
    static String position(BlockPosition position) { return "{\"x\":" + position.x() + ",\"y\":" + position.y() + ",\"z\":" + position.z() + "}"; }
    static String position(BodyPosition position) { return "{\"x\":" + position.x() + ",\"y\":" + position.y() + ",\"z\":" + position.z() + "}"; }
    private static String nullablePosition(BlockPosition position) { return position == null ? "null" : position(position); }
    private static String nullablePosition(FrontierV3AmbientActorExecutor.ObservedPosition position) {
        return position == null ? "null" : "{\"x\":" + position.x() + ",\"y\":" + position.y() + ",\"z\":" + position.z() + "}";
    }
    static String strings(java.util.List<String> values) { return values.stream().map(value -> "\"" + quote(value) + "\"").reduce((left, right) -> left + "," + right).map(value -> "[" + value + "]").orElse("[]"); }
    private static String custody(InventoryCustody custody) {
        return switch (custody) {
            case InventoryCustody.ContainerSlot slot -> "{\"kind\":\"CONTAINER_SLOT\",\"container\":\"" + quote(slot.containerId().value()) + "\",\"slot\":" + slot.slot() + "}";
            case InventoryCustody.Cargo cargo -> "{\"kind\":\"CARGO\",\"cargo\":\"" + quote(cargo.cargoId().value()) + "\"}";
            case InventoryCustody.Player player -> "{\"kind\":\"PLAYER\",\"player\":\"" + player.playerId() + "\"}";
            case InventoryCustody.WorldCarrier carrier -> "{\"kind\":\"WORLD_CARRIER\",\"carrier\":\"" + carrier.carrierId() + "\"}";
            case InventoryCustody.Actor actor -> "{\"kind\":\"ACTOR\",\"actor\":\"" + quote(actor.actorId().value()) + "\"}";
        };
    }
    static String quote(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
