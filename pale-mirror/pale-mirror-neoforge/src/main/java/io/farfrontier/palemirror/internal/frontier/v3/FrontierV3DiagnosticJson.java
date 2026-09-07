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
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSiteHarvestSceneSupport;
import io.farfrontier.palemirror.frontier.v3.model.HumanAssignmentProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSettlementWorkDiagnostic;
import io.farfrontier.palemirror.frontier.v3.model.FrontierMarketOrderDiagnostic;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.process.HivePerceptionProcess;
import io.farfrontier.palemirror.frontier.v3.model.HiveNutrientReceipt;
import io.farfrontier.palemirror.frontier.v3.model.HiveNutrientTransfer;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.ProductionJob;
import io.farfrontier.palemirror.frontier.v3.model.ResidentProfile;
import io.farfrontier.palemirror.frontier.v3.model.ResidentNutritionStatus;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgress;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstruction;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenance;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import io.farfrontier.palemirror.frontier.v3.model.SettlementProvision;
import io.farfrontier.palemirror.frontier.v3.model.LogisticsSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.MedicalEvacuationOperation;
import io.farfrontier.palemirror.frontier.v3.model.MedicalTreatmentSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkSceneCause;
import io.farfrontier.palemirror.frontier.v3.process.EngineeringEquipmentProcess;

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
                         Optional<FrontierV3OperationAssemblyDiagnostic.Readiness> assemblyReadiness) {
        return render(kind, id, checkpoint, state, trace, admission, harvestReadiness, sceneReadiness, assemblyReadiness, Optional.empty());
    }

    static String render(String kind, String id, CheckpointImage checkpoint, FrontierWorldState state,
                         Optional<FrontierV3DiagnosticTrace.Entry> trace,
                         Optional<FrontierV3AmbientAdmissionDiagnostic> admission,
                         Optional<FrontierV3ResourceSiteHarvestExecutor.Readiness> harvestReadiness,
                         Optional<FrontierV3SceneReadiness.Value> sceneReadiness,
                         Optional<FrontierV3OperationAssemblyDiagnostic.Readiness> assemblyReadiness,
                         Optional<FrontierV3ContainerSurfaceExecutor.Readiness> containerReadiness) {
        return render(kind, id, checkpoint, state, trace, admission, harvestReadiness, sceneReadiness, assemblyReadiness,
                containerReadiness, Optional.empty(), Optional.empty());
    }

    static String render(String kind, String id, CheckpointImage checkpoint, FrontierWorldState state,
                         Optional<FrontierV3DiagnosticTrace.Entry> trace,
                         Optional<FrontierV3AmbientAdmissionDiagnostic> admission,
                         Optional<FrontierV3ResourceSiteHarvestExecutor.Readiness> harvestReadiness,
                         Optional<FrontierV3SceneReadiness.Value> sceneReadiness,
                         Optional<FrontierV3OperationAssemblyDiagnostic.Readiness> assemblyReadiness,
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
        Objects.requireNonNull(assemblyReadiness, "assemblyReadiness");
        Objects.requireNonNull(containerReadiness, "containerReadiness");
        Objects.requireNonNull(equipmentIssueReadiness, "equipmentIssueReadiness");
        Objects.requireNonNull(equipmentReturnReadiness, "equipmentReturnReadiness");
        String value = switch (kind) {
            case "summary" -> summary(checkpoint, state);
            case "process" -> process(id, checkpoint, state);
            case "site" -> site(id, checkpoint, state);
            case "settlement" -> settlement(id, checkpoint, state);
            case "hive" -> hive(id, checkpoint, state);
            case "hive_transfer" -> hiveTransfer(id, checkpoint, state);
            case "actor" -> actor(id, checkpoint, state, admission);
            case "item" -> item(id, checkpoint, state);
            case "container" -> container(id, checkpoint, state, containerReadiness);
            case "market_order" -> marketOrder(id, checkpoint, state);
            case "operation" -> operation(id, checkpoint, state, assemblyReadiness);
            case "route_construction" -> routeConstruction(id, checkpoint, state);
            case "route_maintenance" -> routeMaintenance(id, checkpoint, state);
            case "physical_delta" -> physicalDelta(id, checkpoint, state);
            case "medical" -> medical(id, checkpoint, state);
            case "scene" -> scene(id, checkpoint, state, sceneReadiness);
            case "intent" -> intent(id, checkpoint, state, harvestReadiness, equipmentIssueReadiness, equipmentReturnReadiness);
            case "trace" -> trace(id, checkpoint, trace);
            case "transit" -> transit(id, checkpoint, state);
            case "route_topology" -> routeTopology(id, checkpoint, state);
            default -> unavailable(kind, id, checkpoint, "unknown_view");
        };
        return bounded(kind, id, checkpoint, value);
    }

    static String unavailableRuntime(String kind, String id) {
        return PREFIX + "{\"schema\":1,\"kind\":\"" + quote(kind) + "\",\"id\":\"" + quote(id)
                + "\",\"status\":\"runtime_unavailable\"}";
    }

    /** Applies the one operator-response limit to every read-only v3 diagnostic view. */
    static String bounded(String kind, String id, CheckpointImage checkpoint, String value) {
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            value = unavailable(kind, id, checkpoint, "response_limit");
        }
        return PREFIX + value;
    }

    private static String summary(CheckpointImage checkpoint, FrontierWorldState state) {
        return base("summary", "", checkpoint)
                + ",\"status\":\"ok\",\"settlements\":" + state.bootstrap().settlements().size()
                + ",\"residents\":" + state.humanPopulation().residents().size()
                + ",\"bioforms\":" + state.actorLocations().keySet().stream().filter(value -> value.value().startsWith("bioform:")).count()
                + ",\"sites\":" + state.resourceSites().sites().size()
                + ",\"operations\":" + state.operations().size()
                + ",\"intents\":" + state.physicalIntents().size()
                + ",\"ambientLeases\":" + state.ambientLeases().size()
                + ",\"sceneLeases\":" + state.sceneLeases().size()
                + ",\"items\":" + state.inventory().items().size()
                + ",\"inventoryConflicts\":" + state.inventory().conflicts().size() + "}";
    }

    /**
     * One bounded semantic projection of an active duration process.
     *
     * <p>This is deliberately a read-only diagnostic boundary, not a second process registry:
     * the aggregate, lease and schedule remain owned by their production registries.  F0.V uses
     * it to compare identity, claims, conservation, schedule and result after an ordinary pilot
     * action without inferring any of those facts from a Minecraft entity or block.</p>
     */
    private static String process(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null);
        ResourceSiteHarvestJob job = subject == null ? null : state.resourceSites().sites().values().stream()
                .map(ResourceSiteLifecycle::activeWork).flatMap(Optional::stream)
                .filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .filter(value -> value.id().equals(subject)).findFirst().orElse(null);
        if (job == null) return unavailable("process", id, checkpoint, "not_found");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        PhysicalIntent intent = state.physicalIntents().get(job.intentId());
        ActorLocation actor = state.actorLocations().get(job.workerId());
        // A released harvest remains in the durable registry as a receipt.  It must not hide a
        // later PREPARED/HOT retry for the same job merely because its historical lease id sorts
        // first: F0.V's process view reports the current ownership fact, not an archive index.
        SceneLease lease = currentLease(state, job.id());
        var schedules = checkpoint.schedules().stream().filter(value -> value.subject().equals(job.id()))
                .sorted().limit(4).toList();
        String scheduleEntries = schedules.stream().map(value -> "{\"id\":\"" + quote(value.id().value())
                + "\",\"dueAt\":" + value.dueAt().ticks() + ",\"kind\":\"" + quote(value.kind())
                + "\",\"weight\":" + value.weight() + "}").reduce((left, right) -> left + "," + right)
                .map(value -> "[" + value + "]").orElse("[]");
        // A CLOSED receipt remains available through the scene diagnostic, but it is no longer
        // a current process claim.  Exposing it here would turn an already released observer
        // hand-off into a false HOT/COLD semantic difference.
        String leaseValue = lease == null || lease.status() == io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED ? "null" : "{\"id\":\"" + quote(lease.id().value())
                + "\",\"status\":\"" + lease.status() + "\",\"revision\":" + lease.revision()
                + ",\"members\":" + lease.members().size() + ",\"body\":"
                + (lease.memberPosition(job.workerId()) == null ? "null" : position(lease.memberPosition(job.workerId()))) + "}";
        String intentStatus = intent == null ? "MISSING" : intent.status().name();
        String intentKind = intent == null ? "MISSING" : intent.kind().name();
        String intentObservationId = intent == null || intent.postconditionObservationId().isEmpty() ? "null"
                : "\"" + quote(intent.postconditionObservationId().orElseThrow().value()) + "\"";
        String actorBody = actor == null ? "null" : position(actor.body());
        int cursorLength = job.traversal().linearCorridorSurfaces().size();
        return base("process", id, checkpoint) + ",\"status\":\"ok\",\"family\":\"frontier.resource-site-harvest\""
                + ",\"identity\":{\"job\":\"" + quote(job.id().value()) + "\",\"worker\":\"" + quote(job.workerId().value())
                + "\",\"outputItem\":\"" + quote(job.outputItemId().value()) + "\"}"
                + ",\"claims\":{\"task\":\"" + quote(job.taskId().value()) + "\",\"site\":\"" + quote(job.siteId().value())
                + "\",\"worker\":\"" + quote(job.workerId().value()) + "\",\"intent\":\"" + quote(job.intentId().value())
                + "\",\"outputSlot\":" + job.outputSlot().slot() + ",\"lease\":" + leaseValue + "}"
                + ",\"conservation\":{\"outputItem\":\"" + quote(job.outputItemId().value()) + "\",\"completedCropSlots\":"
                + job.progress().completedCropSlots() + ",\"pendingCropSlot\":" + job.progress().pendingCropSlotIndex()
                + ",\"totalCropSlots\":" + ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS + "}"
                + ",\"schedule\":{\"count\":" + checkpoint.schedules().stream().filter(value -> value.subject().equals(job.id())).count()
                + ",\"entries\":" + scheduleEntries + "}"
                + ",\"cursor\":{\"index\":" + job.traversalCursor() + ",\"length\":" + cursorLength
                + ",\"retainedBody\":" + position(job.traversal().linearCorridorSurfaces().get(job.traversalCursor()).standingBody())
                + ",\"actorBody\":" + actorBody + "}"
                + ",\"result\":{\"sitePhase\":\"" + lifecycle.phase() + "\",\"intentKind\":\"" + intentKind
                + "\",\"intentStatus\":\"" + intentStatus + "\",\"intentObservationId\":" + intentObservationId
                + ",\"complete\":" + job.progress().complete() + "}}";
    }

    private static String site(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null);
        ResourceSiteLifecycle lifecycle = subject == null ? null : state.resourceSites().sites().get(subject);
        ResourceSite site = subject == null ? null : FrontierResourceSitePlan.compile(state.bootstrap()).get(subject);
        if (lifecycle == null || site == null) return unavailable("site", id, checkpoint, "not_found");
        String work = lifecycle.activeWork().map(value -> value.id().value()).orElse("");
        return base("site", id, checkpoint) + ",\"status\":\"ok\",\"owner\":\"" + quote(site.settlementId().value())
                + "\",\"facility\":\"" + quote(site.facilityId().value()) + "\",\"phase\":\"" + lifecycle.phase()
                + "\",\"growthEpoch\":" + lifecycle.growthEpoch() + ",\"growthStage\":" + lifecycle.growthStage()
                + ",\"activeWork\":\"" + quote(work) + "\",\"firstCrop\":" + position(site.cropSlots().getFirst())
                + ",\"lastCrop\":" + position(site.cropSlots().getLast()) + "}";
    }

    private static String settlement(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null);
        FrontierSettlementWorkDiagnostic value = subject == null ? null
                : FrontierSettlementWorkDiagnostic.inspect(checkpoint, state, subject).orElse(null);
        if (value == null) return unavailable("settlement", id, checkpoint, "not_found");
        SettlementProvision provision = state.humanPopulation().provision(subject);
        int availableFood = state.inventory().items().values().stream().filter(item -> item.itemKind().equals("minecraft:bread"))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot
                        && slot.containerId().equals(FrontierWorldState.depotId(subject))).mapToInt(ExactItemStack::count).sum();
        int living = (int) state.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(subject))
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE).count();
        int reserve = Math.addExact(Math.multiplyExact(living, 2), provision.status().name().equals("IN_PROGRESS")
                ? provision.requiredRations() - provision.fulfilledRations() : 0);
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
                + state.humanPopulation().activeCases(subject) + ",\"food\":{\"status\":\"" + provision.status()
                + "\",\"available\":" + availableFood + ",\"reserve\":" + reserve + ",\"required\":" + provision.requiredRations()
                + ",\"fulfilled\":" + provision.fulfilledRations() + ",\"nourished\":" + nourished + ",\"hungry\":" + hungry
                + ",\"starving\":" + starving + ",\"intent\":\""
                + quote(provision.activeIntentId().map(PhysicalIntentId::value).orElse("")) + "\"}}";
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
        String role = resident != null ? resident.role().name() : bioform != null ? bioform.chassis().name() + "/" + bioform.assignment().name() : "UNKNOWN";
        String owner = resident != null ? resident.settlementId().value() : bioform != null ? bioform.hiveId().value() : "";
        String nutrition = resident == null ? "" : state.humanPopulation().nutrition(subject).status().name();
        var assignment = resident == null ? null : HumanAssignmentProjection.compile(state).assignment(subject);
        boolean harvestSceneCandidate = resident != null && FrontierResourceSiteHarvestSceneSupport.candidates(state).stream()
                .anyMatch(candidate -> candidate.workerId().equals(subject));
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
                + ",\"assignment\":\"" + (assignment == null ? "NONE" : assignment.kind().name())
                + "\",\"assignmentOwner\":\"" + quote(assignment == null ? "" : assignment.ownerId().map(SubjectId::value).orElse(""))
                + "\",\"harvestSceneCandidate\":" + harvestSceneCandidate
                + ",\"sceneReserved\":" + FrontierSceneAdmission.reserved(state, subject)
                + (lifecycle == null ? "" : ",\"lifecycle\":\"" + lifecycle.phase().name() + "\",\"cocoonHome\":" + cocoonHome)
                + admission.map(FrontierV3DiagnosticJson::admission).orElse("") + "}";
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
                + ",\"next\":" + next + ",\"ambientLease\":\"" + quote(lease == null ? "NONE" : lease.status().name())
                + "\",\"ambientGoal\":\"" + quote(goal) + "\",\"goalPosition\":" + goalPosition + "}";
    }

    private static String admission(FrontierV3AmbientAdmissionDiagnostic value) {
        String placement = value.placement() == null ? "null" : position(value.placement());
        String observedPosition = value.observedPosition() == null ? "null" : position(value.observedPosition());
        String observedExact = nullablePosition(value.observedExact());
        String entityId = value.entityId() == null ? "" : value.entityId().toString();
        return ",\"physicalAdmission\":{\"status\":\"" + quote(value.status()) + "\",\"entityUuid\":\""
                + quote(entityId) + "\",\"pending\":" + value.pending() + ",\"placement\":" + placement
                + ",\"observedPosition\":" + observedPosition + ",\"observedExact\":" + observedExact + "}";
    }

    private static String item(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null); ExactItemStack item = subject == null ? null : state.inventory().items().get(subject);
        if (item == null) return unavailable("item", id, checkpoint, "not_found");
        return base("item", id, checkpoint) + ",\"status\":\"ok\",\"owner\":\"" + quote(item.economicOwnerId().value())
                + "\",\"itemKind\":\"" + quote(item.itemKind()) + "\",\"count\":" + item.count() + ",\"custody\":" + custody(item.custody()) + "}";
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
        String physical = readiness.map(value -> ",\"physicalSocket\":{\"chunk\":\"" + quote(value.chunk())
                + "\",\"freshSocket\":\"" + quote(value.freshSocket()) + "\",\"support\":\"" + quote(value.support())
                + "\",\"targetBlock\":\"" + quote(value.targetBlock()) + "\",\"chest\":\"" + quote(value.chest())
                + "\",\"slots\":\"" + quote(value.slots()) + "\",\"mismatch\":\"" + quote(value.mismatch()) + "\"}").orElse("");
        return base("container", id, checkpoint) + ",\"status\":\"ok\",\"owner\":\"" + quote(container.ownerId().value())
                + "\",\"surface\":\"" + surface.status() + "\",\"position\":" + position(surface.position()) + ",\"slotCount\":" + container.slotCount()
                + ",\"occupiedCount\":" + occupiedItems.size() + ",\"occupied\":" + occupied + physical + "}";
    }

    private static String marketOrder(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null);
        FrontierMarketOrderDiagnostic order = subject == null ? null : FrontierMarketOrderDiagnostic.inspect(state, subject).orElse(null);
        if (order == null) return unavailable("market_order", id, checkpoint, "not_found");
        return base("market_order", id, checkpoint) + ",\"status\":\"ok\",\"orderStatus\":\"" + quote(order.status())
                + "\",\"job\":\"" + quote(order.jobId().value()) + "\",\"jobActive\":" + order.jobActive()
                + ",\"reservation\":\"" + quote(order.reservationId().value()) + "\",\"reservationActive\":" + order.reservationActive()
                + ",\"taskStatus\":\"" + quote(order.taskStatus()) + "\"}";
    }

    private static String operation(String id, CheckpointImage checkpoint, FrontierWorldState state,
                                    Optional<FrontierV3OperationAssemblyDiagnostic.Readiness> readiness) {
        SubjectId subject = subject(id).orElse(null); RouteOperation operation = subject == null ? null : state.operations().get(subject);
        if (operation == null) return unavailable("operation", id, checkpoint, "not_found");
        String members = operation.participantIds().stream().sorted().map(value -> "\"" + quote(value.value()) + "\"").reduce((left, right) -> left + "," + right).orElse("");
        String assembly = operation.activeAssembly().map(value -> ",\"assemblyMembers\":" + value.members().size()
                + ",\"assemblyCursorTotal\":" + value.members().values().stream().mapToInt(io.farfrontier.palemirror.frontier.v3.model.OperationAssembly.Member::cursor).sum()
                + ",\"assemblyComplete\":" + value.complete() + ",\"cargoCarrier\":\"" + quote(value.cargoCarrierId().value()) + "\""
                + ",\"assemblyProgress\":[" + value.members().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> assemblyProgress(entry.getKey(), entry.getValue())).reduce((left, right) -> left + "," + right).orElse("") + "]"
                + value.deferral().map(deferral -> ",\"assemblyDeferred\":true,\"assemblyDeferredActor\":\"" + quote(deferral.actorId().value())
                        + "\",\"assemblyDeferredTarget\":" + position(deferral.target().support()) + ",\"assemblyObstructionSurface\":" + position(deferral.obstructionSurface().support())
                        + ",\"assemblyDeferredReason\":\"" + deferral.reason() + "\"")
                        .orElse(",\"assemblyDeferred\":false")).orElse("");
        String travel = operation.activeTravel().map(value -> {
            long blockedEdges = value.topology().edges().stream()
                    .filter(edge -> edge.availability() != io.farfrontier.palemirror.frontier.v3.model.TraversalAvailability.OPEN).count();
            String nextEdge = value.arrived() ? "" : value.nextEdge().id().value();
            String nextAvailability = value.arrived() ? "" : value.nextEdge().availability().name();
            return ",\"travelTopology\":\"" + quote(value.topology().id().value()) + "\",\"travelTopologyRevision\":" + value.topology().revision()
                    + ",\"travelCursor\":" + value.cursor() + ",\"travelLength\":" + value.corridor().size()
                    + ",\"travelCurrent\":" + position(value.currentPosition()) + ",\"travelCargo\":" + position(value.cargoAnchor().surface().support())
                    + ",\"travelArrived\":" + value.arrived() + ",\"travelNextEdge\":\"" + quote(nextEdge)
                    + "\",\"travelNextEdgeAvailability\":\"" + quote(nextAvailability) + "\",\"travelUnavailableEdges\":" + blockedEdges;
        }).orElse("");
        var settlementTopology = state.routeTopology().supplyTraversalTopology(state.bootstrap(), operation.settlementId());
        long settlementUnavailableEdges = settlementTopology.edges().stream()
                .filter(edge -> edge.availability() != io.farfrontier.palemirror.frontier.v3.model.TraversalAvailability.OPEN).count();
        String hiveSighting = HivePerceptionProcess.observedCarrierPosition(state, operation.id())
                .map(position -> ",\"hiveObservedCarrier\":" + position(position)).orElse("");
        String hiveIntercept = HivePerceptionProcess.interceptTask(state, operation.id())
                .map(task -> ",\"hiveIntercept\":{\"position\":" + position(task.position()) + ",\"status\":\"" + quote(task.status()) + "\"}").orElse("");
        String hiveEngagement = HivePerceptionProcess.interceptEngagement(state, operation.id())
                .map(FrontierV3DiagnosticJson::hiveEngagement).orElse("");
        return base("operation", id, checkpoint) + ",\"status\":\"ok\",\"owner\":\"" + quote(operation.settlementId().value())
                + "\",\"cargo\":\"" + quote(operation.cargoId().value()) + "\",\"destination\":\"" + quote(operation.destinationId().value())
                + "\",\"stage\":\"" + operation.stage() + "\",\"routeIndex\":" + operation.routeIndex()
                + ",\"routeLength\":" + operation.route().size() + ",\"settlementTraversalAvailable\":"
                + (settlementUnavailableEdges == 0L) + ",\"settlementUnavailableEdges\":" + settlementUnavailableEdges
                + ",\"participants\":[" + members + "]" + assembly + travel + hiveSighting + hiveIntercept + hiveEngagement
                + readiness.map(FrontierV3DiagnosticJson::assemblyReadiness).orElse("") + "}";
    }

    /** Bounded operator evidence for the one retained command source of an active hive engagement. */
    private static String hiveEngagement(HivePerceptionProcess.InterceptEngagement engagement) {
        var authority = engagement.command();
        String coverage = authority.relayCoverage().map(value -> ",\"relay\":\"" + quote(authority.currentAuthorityId().value())
                + "\",\"ganglion\":\"" + quote(value.ganglionId().value()) + "\",\"radius\":" + value.radius()).orElse("");
        return ",\"hiveEngagement\":{\"status\":\"" + quote(engagement.status()) + "\",\"command\":{\"kind\":\""
                + quote(authority.kind()) + "\",\"original\":\"" + quote(authority.originalAuthorityId().value())
                + "\",\"controller\":\"" + quote(authority.currentAuthorityId().value()) + "\",\"signal\":\""
                + quote(authority.signalPhase()) + "\",\"members\":" + authority.rosterSize() + ",\"subordinateWeight\":"
                + authority.subordinateWeight() + coverage + "}}";
    }

    /** One declared supply topology, with grade facts only; diagnostics never survey or alter Minecraft terrain. */
    private static String routeTopology(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId settlement = subject(id).orElse(null);
        if (settlement == null || state.bootstrap().settlements().stream().noneMatch(value -> value.id().equals(settlement))) {
            return unavailable("route_topology", id, checkpoint, "not_found");
        }
        var topology = state.routeTopology().supplyTraversalTopology(state.bootstrap(), settlement);
        var grades = topology.edges().stream().filter(edge -> edge.grade() > 0).toList();
        int maximumGrade = grades.stream().mapToInt(io.farfrontier.palemirror.frontier.v3.model.TraversalTopology.Edge::grade).max().orElse(0);
        boolean supplyPassable = state.routeTopology().supplyPassable(state.bootstrap(), settlement);
        String firstGrade = grades.isEmpty() ? "null" : "{\"from\":" + position(topology.nodes().get(grades.getFirst().from()).support())
                + ",\"to\":" + position(topology.nodes().get(grades.getFirst().to()).support()) + "}";
        return base("route_topology", id, checkpoint) + ",\"status\":\"ok\",\"topology\":\"" + quote(topology.id().value())
                + "\",\"revision\":" + topology.revision() + ",\"nodes\":" + topology.nodes().size() + ",\"edges\":" + topology.edges().size()
                + ",\"supplyPassable\":" + supplyPassable
                + ",\"gradedEdges\":" + grades.size() + ",\"maximumGrade\":" + maximumGrade + ",\"firstGrade\":" + firstGrade + "}";
    }

    /** Bounded exact cursors make a stalled ordinary HOT approach diagnosable without world mutation. */
    private static String assemblyProgress(SubjectId actorId, io.farfrontier.palemirror.frontier.v3.model.OperationAssembly.Member member) {
        String next = member.arrived() ? "null" : position(member.nextSurface().support());
        return "{\"actor\":\"" + quote(actorId.value()) + "\",\"cursor\":" + member.cursor()
                + ",\"length\":" + member.corridor().size() + ",\"current\":" + position(member.currentSurface().support()) + ",\"next\":" + next + "}";
    }

    private static String assemblyReadiness(FrontierV3OperationAssemblyDiagnostic.Readiness value) {
        String members = value.members().stream().map(member -> "{\"actor\":\"" + quote(member.actorId().value())
                + "\",\"current\":" + position(member.current()) + ",\"next\":" + nullablePosition(member.next())
                + ",\"observed\":" + nullablePosition(member.observed()) + ",\"observedExact\":" + nullablePosition(member.observedExact())
                + ",\"targetStatus\":\"" + quote(member.targetStatus()) + "\",\"floorBlock\":\"" + quote(member.floorBlock())
                + "\",\"supportBlock\":\"" + quote(member.supportBlock()) + "\",\"bodyBlock\":\"" + quote(member.bodyBlock())
                + "\",\"headBlock\":\"" + quote(member.headBlock()) + "\",\"occupants\":" + strings(member.occupants()) + "}")
                .reduce((left, right) -> left + "," + right).orElse("");
        return ",\"physicalAssembly\":[" + members + "]";
    }

    /** One settlement's current replacement-route project; it never discovers or advances one. */
    private static String routeConstruction(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId settlement = subject(id).orElse(null);
        RouteConstruction project = settlement == null ? null : state.routeConstructions().values().stream()
                .filter(value -> value.settlementId().equals(settlement)).sorted(java.util.Comparator.comparing(RouteConstruction::id))
                .findFirst().orElse(null);
        if (project == null) return unavailable("route_construction", id, checkpoint, "not_found");
        java.util.List<BlockPosition> cells = io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan.routeConstructionCells(state, project);
        String next = project.confirmedCells() == cells.size() ? "null" : position(cells.get(project.confirmedCells()));
        String team = project.team().map(value -> {
            String members = value.memberIds().stream().sorted().map(actorId -> engineeringTeamMember(state, actorId))
                    .reduce((left, right) -> left + "," + right).orElse("");
            boolean fullyEquipped = io.farfrontier.palemirror.frontier.v3.model.EngineeringToolCustody.ready(state, value);
            return ",\"teamPresent\":true,\"teamFullyEquipped\":" + fullyEquipped + ",\"teamMembers\":[" + members + "]";
        }).orElse(",\"teamPresent\":false,\"teamFullyEquipped\":false,\"teamMembers\":[]");
        String assembly = project.assembly().map(value -> {
            String members = value.members().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                    .map(entry -> engineeringAssemblyMember(state, entry.getKey(), entry.getValue()))
                    .reduce((left, right) -> left + "," + right).orElse("");
            int cursorTotal = value.members().values().stream().mapToInt(io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkAssembly.Member::cursor).sum();
            return ",\"assemblyPresent\":true,\"assemblyPurpose\":\"" + value.purpose() + "\",\"assemblyComplete\":" + value.complete() + ",\"assemblyCursorTotal\":" + cursorTotal
                    + ",\"assemblyMembers\":[" + members + "]";
        }).orElse(",\"assemblyPresent\":false");
        long pendingProjectIntents = state.physicalIntents().values().stream()
                .filter(intent -> intent.subjectIds().contains(project.id()))
                .filter(intent -> intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED
                        || intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING).count();
        long pendingOtherIntents = state.physicalIntents().values().stream()
                .filter(intent -> !intent.subjectIds().contains(project.id()))
                .filter(intent -> intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED
                        || intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING).count();
        return base("route_construction", id, checkpoint) + ",\"status\":\"ok\",\"project\":\"" + quote(project.id().value())
                + "\",\"phase\":\"" + project.status() + "\",\"confirmedCells\":" + project.confirmedCells()
                + ",\"requiredCells\":" + cells.size() + ",\"cargo\":\"" + quote(project.cargoId().map(SubjectId::value).orElse(""))
                + "\",\"cargoPresent\":" + project.cargoId().isPresent() + ",\"nextCell\":" + next
                + ",\"pendingProjectIntents\":" + pendingProjectIntents + ",\"pendingOtherIntents\":" + pendingOtherIntents + team + assembly + "}";
    }

    /** One settlement's current in-place retained-route repair; it never selects another cell or route. */
    private static String routeMaintenance(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId settlement = subject(id).orElse(null);
        RouteMaintenance maintenance = settlement == null ? null : state.routeMaintenances().values().stream()
                .filter(value -> value.settlementId().equals(settlement)).sorted(java.util.Comparator.comparing(RouteMaintenance::id))
                .findFirst().orElse(null);
        if (maintenance == null) return unavailable("route_maintenance", id, checkpoint, "not_found");
        String members = maintenance.team().memberIds().stream().sorted().map(actorId -> engineeringTeamMember(state, actorId))
                .reduce((left, right) -> left + "," + right).orElse("");
        boolean fullyEquipped = io.farfrontier.palemirror.frontier.v3.model.EngineeringToolCustody.ready(state, maintenance.team());
        String assembly = maintenance.assembly().map(value -> {
            String assemblyMembers = value.members().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                    .map(entry -> engineeringAssemblyMember(state, entry.getKey(), entry.getValue()))
                    .reduce((left, right) -> left + "," + right).orElse("");
            int cursorTotal = value.members().values().stream().mapToInt(io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkAssembly.Member::cursor).sum();
            return ",\"assemblyPresent\":true,\"assemblyPurpose\":\"" + value.purpose() + "\",\"assemblyComplete\":" + value.complete() + ",\"assemblyCursorTotal\":" + cursorTotal
                    + ",\"assemblyMembers\":[" + assemblyMembers + "]";
        }).orElse(",\"assemblyPresent\":false");
        long pendingIntents = state.physicalIntents().values().stream().filter(intent -> intent.subjectIds().contains(maintenance.id()))
                .filter(intent -> intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED
                        || intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING).count();
        var assemblyAdmission = maintenanceAssemblyAdmission(state, maintenance, fullyEquipped);
        var toolReturn = EngineeringEquipmentProcess.returnReadiness(state, maintenance);
        boolean toolReturnRequired = !EngineeringEquipmentProcess.returnedOrLost(state, maintenance);
        String toolReturnActor = toolReturn.actorId().map(SubjectId::value).orElse("");
        String toolReturnItem = toolReturn.itemId().map(SubjectId::value).orElse("");
        String toolReturnSlot = toolReturn.targetSlot().map(slot -> Integer.toString(slot.slot())).orElse("");
        return base("route_maintenance", id, checkpoint) + ",\"status\":\"ok\",\"maintenance\":\"" + quote(maintenance.id().value())
                + "\",\"phase\":\"" + maintenance.status() + "\",\"repairCell\":" + position(maintenance.repairCell())
                + ",\"semanticPart\":\"" + maintenance.semanticPart() + "\",\"cargo\":\"" + quote(maintenance.cargoId().map(SubjectId::value).orElse(""))
                + "\",\"cargoPresent\":" + maintenance.cargoId().isPresent() + ",\"teamPresent\":true,\"teamFullyEquipped\":" + fullyEquipped
                + ",\"assemblyAdmission\":\"" + quote(assemblyAdmission.reason()) + "\",\"assemblyAdmissionDetail\":\"" + quote(assemblyAdmission.detail()) + "\",\"teamMembers\":[" + members
                + "],\"pendingMaintenanceIntents\":" + pendingIntents
                + ",\"toolReturnRequired\":" + toolReturnRequired + ",\"toolReturnReadiness\":\"" + quote(toolReturn.reason())
                + "\",\"toolReturnDepot\":\"" + quote(toolReturn.depotId().value()) + "\",\"toolReturnActor\":\"" + quote(toolReturnActor)
                + "\",\"toolReturnItem\":\"" + quote(toolReturnItem) + "\",\"toolReturnSlot\":\"" + quote(toolReturnSlot) + "\"" + assembly + "}";
    }

    /**
     * Read-only explanation for an intentionally asynchronous engineering admission.  This is
     * never an alternate planner: it evaluates the same bounded pure compiler that the next
     * canonical route-maintenance scan will use, so a tester can distinguish ordinary lease
     * draining from an actual rejected approach.
     */
    private static EngineeringAdmissionDiagnostic maintenanceAssemblyAdmission(FrontierWorldState state, RouteMaintenance maintenance, boolean fullyEquipped) {
        if (!maintenance.building()) return new EngineeringAdmissionDiagnostic("NOT_BUILDING", "");
        if (maintenance.assembly().isPresent()) return new EngineeringAdmissionDiagnostic("ASSEMBLY_RETAINED", "");
        if (!fullyEquipped) return new EngineeringAdmissionDiagnostic("WAITING_FOR_TOOL", "");
        boolean predecessorLease = maintenance.team().memberIds().stream().map(state.ambientLeases()::get)
                .anyMatch(lease -> lease != null && lease.status() != io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.CLOSED);
        if (predecessorLease) return new EngineeringAdmissionDiagnostic("WAITING_FOR_AMBIENT_LEASE", "");
        boolean retainedWorksite = state.sceneLeases().values().stream()
                .filter(io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors::isEngineeringWorksite)
                .anyMatch(lease -> io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.engineeringWorksite(lease).projectId().equals(maintenance.id())
                        && lease.status() != io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED);
        if (retainedWorksite) return new EngineeringAdmissionDiagnostic("WAITING_FOR_WORKSITE", "");
        var readiness = io.farfrontier.palemirror.frontier.v3.model.EngineeringWorksite.admission(state, maintenance);
        return readiness.admissible() ? new EngineeringAdmissionDiagnostic("READY_TO_ASSEMBLE", "")
                : new EngineeringAdmissionDiagnostic(readiness.reason(), readiness.detail());
    }

    private record EngineeringAdmissionDiagnostic(String reason, String detail) { }

    /** Bounded exact crew readiness shows why a project may not yet accept player-supplied material. */
    private static String engineeringTeamMember(FrontierWorldState state, SubjectId actorId) {
        var lease = state.ambientLeases().get(actorId);
        ActorLocation location = state.actorLocations().get(actorId);
        return "{\"actor\":\"" + quote(actorId.value()) + "\",\"toolReady\":"
                + io.farfrontier.palemirror.frontier.v3.model.EngineeringToolCustody.holdsTool(state, actorId)
                + ",\"position\":" + (location == null ? "null" : position(location.body()))
                + ",\"ambientLease\":\"" + quote(lease == null ? "NONE" : lease.status().name())
                + "\",\"ambientGoal\":\"" + quote(lease == null ? "NONE" : lease.goal().name()) + "\"}";
    }

    /** Read-only per-worker cursor and lease state for one bounded engineering approach. */
    private static String engineeringAssemblyMember(FrontierWorldState state, SubjectId actorId,
                                                          io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkAssembly.Member member) {
        var lease = state.ambientLeases().get(actorId);
        String next = member.arrived() ? "null" : position(member.corridor().get(member.cursor() + 1));
        return "{\"actor\":\"" + quote(actorId.value()) + "\",\"cursor\":" + member.cursor()
                + ",\"length\":" + member.corridor().size() + ",\"arrived\":" + member.arrived()
                + ",\"next\":" + next + ",\"ambientLease\":\"" + quote(lease == null ? "NONE" : lease.status().name())
                + "\",\"ambientGoal\":\"" + quote(lease == null ? "NONE" : lease.goal().name())
                + "\",\"leaseTarget\":" + (lease == null ? "null" : position(lease.goalBody().supportingSurface().support())) + "}";
    }

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

    /** One stable typed-scene view for player-piloted physical-scene evidence. */
    private static String scene(String id, CheckpointImage checkpoint, FrontierWorldState state,
                                Optional<FrontierV3SceneReadiness.Value> readiness) {
        SubjectId sceneSubject = subject(id).orElse(null);
        if (sceneSubject == null) return unavailable("scene", id, checkpoint, "not_found");
        var lease = currentLease(state, sceneSubject);
        if (lease == null) return unavailable("scene", id, checkpoint, "not_found");
        LogisticsSceneCause logistics = io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.isLogistics(lease)
                ? io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.logistics(lease) : null;
        SettlementAssaultSceneCause assault = io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.isSettlementAssault(lease)
                ? io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.settlementAssault(lease) : null;
        EngineeringWorkSceneCause engineering = io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.isEngineeringWorksite(lease)
                ? io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.engineeringWorksite(lease) : null;
        MedicalTreatmentSceneCause medical = io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.isMedicalTreatment(lease)
                ? io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.medicalTreatment(lease) : null;
        var harvest = io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.isResourceSiteHarvest(lease)
                ? io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.resourceSiteHarvest(lease) : null;
        var production = io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.isProductionWork(lease)
                ? io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.productionWork(lease) : null;
        var service = io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.isServiceWork(lease)
                ? io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.serviceWork(lease) : null;
        var routePatrol = io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.isRoutePatrol(lease)
                ? io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.routePatrol(lease) : null;
        ProductionJob productionJob = production == null ? null : state.productionJobs().get(production.jobId());
        var serviceWork = service == null ? null : state.serviceWorks().get(service.workId());
        var patrol = routePatrol == null ? null : state.strategicPlans().routePatrols().get(routePatrol.taskId());
        SubjectId engagement = logistics == null ? null : logistics.engagementId().orElse(null);
        var primaryMember = lease.members().getFirst();
        PhysicalIntent explosion = state.physicalIntents().values().stream().filter(value -> value.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXPLOSION)
                .filter(value -> engagement != null && value.subjectIds().size() == 2 && value.subjectIds().getLast().equals(engagement))
                .sorted(java.util.Comparator.comparing(PhysicalIntent::id)).findFirst().orElse(null);
        SubjectId strikeCause = logistics != null ? logistics.operationId() : assault != null ? assault.assaultId() : null;
        PhysicalIntent strike = strikeCause == null ? null : state.physicalIntents().values().stream()
                .filter(value -> value.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE)
                .filter(value -> value.causeSubjectId().equals(strikeCause)).sorted(java.util.Comparator.comparing(PhysicalIntent::id)).findFirst().orElse(null);
        String recovery = lease.recoveryEvidence().map(value -> ",\"recoveryMissingActors\":" + strings(value.missingActorIds().stream().map(SubjectId::value).sorted().toList())
                + ",\"recoveryMissingCarrier\":" + value.missingCargoCarrier()).orElse("");
        return base("scene", id, checkpoint) + ",\"status\":\"ok\",\"leaseId\":\"" + quote(lease.id().value())
                + "\",\"leaseStatus\":\"" + lease.status() + "\",\"sceneKind\":\"" + (logistics != null ? "LOGISTICS" : assault != null ? "SETTLEMENT_ASSAULT"
                : engineering != null ? "ENGINEERING_WORKSITE" : medical != null ? "MEDICAL_TREATMENT" : harvest != null ? "RESOURCE_SITE_HARVEST"
                : production != null ? "PRODUCTION_WORK" : service != null ? "SETTLEMENT_SERVICE_WORK" : routePatrol != null ? "ROUTE_PATROL" : "UNKNOWN")
                + "\",\"operation\":\"" + quote(logistics == null ? "" : logistics.operationId().value())
                + "\",\"assault\":\"" + quote(assault == null ? "" : assault.assaultId().value())
                + "\",\"project\":\"" + quote(engineering == null ? "" : engineering.projectId().value())
                + "\",\"medical\":\"" + quote(medical == null ? "" : medical.operationId().value())
                + "\",\"harvestJob\":\"" + quote(harvest == null ? "" : harvest.jobId().value())
                + "\",\"productionJob\":\"" + quote(production == null ? "" : production.jobId().value())
                + "\",\"patrolTask\":\"" + quote(routePatrol == null ? "" : routePatrol.taskId().value())
                + "\"" + productionTraversal(productionJob) + serviceTraversal(serviceWork) + patrolTraversal(patrol)
                + ",\"members\":" + lease.members().size() + ",\"primaryActor\":\"" + quote(primaryMember.actorId().value())
                + "\",\"primaryEntityUuid\":\"" + primaryMember.entityId() + "\",\"explosionStatus\":\"" + (explosion == null ? "NONE" : explosion.status()) + "\""
                + ",\"strikeStatus\":\"" + (strike == null ? "NONE" : strike.status()) + "\""
                + recovery + readiness.map(FrontierV3DiagnosticJson::sceneReadiness).orElse("") + "}";
    }

    /** The retained production edge and one future body make an obstruction reproducible without selecting an actor. */
    private static String productionTraversal(ProductionJob job) {
        if (job == null) return ",\"productionStage\":\"\",\"productionCursor\":-1,\"productionCurrent\":null,\"productionNext\":null,\"productionNextBody\":null,\"productionFutureBody\":null";
        var corridor = job.workTraversal().linearCorridorSurfaces();
        String next = job.traversalCursor() + 1 >= corridor.size() ? "null" : position(corridor.get(job.traversalCursor() + 1).support());
        String nextBody = job.traversalCursor() + 1 >= corridor.size() ? "null" : position(corridor.get(job.traversalCursor() + 1).standingBody());
        String futureBody = job.traversalCursor() + 2 >= corridor.size() ? "null" : position(corridor.get(job.traversalCursor() + 2).standingBody());
        return ",\"productionStage\":\"" + job.workProgress().stage() + "\",\"productionCursor\":" + job.traversalCursor()
                + ",\"productionCurrent\":" + position(corridor.get(job.traversalCursor()).support()) + ",\"productionNext\":" + next
                + ",\"productionNextBody\":" + nextBody + ",\"productionFutureBody\":" + futureBody;
    }

    /** The service cursor is diagnostic-only: it exposes the persisted station, never replans it. */
    private static String serviceTraversal(io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWork work) {
        if (work == null) return ",\"serviceWork\":\"\",\"servicePhase\":\"\",\"serviceCurrent\":null,\"serviceNext\":null,\"serviceInputCursor\":-1,\"serviceInputNodes\":0,\"serviceWorkCursor\":-1,\"serviceWorkNodes\":0,\"serviceTarget\":\"\"";
        var current = io.farfrontier.palemirror.frontier.v3.model.FrontierSettlementServiceWorkSceneSupport.currentSurface(work);
        boolean input = work.phase() == io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWorkPhase.PREPARED
                || work.phase() == io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWorkPhase.APPROACH_INPUT
                || work.phase() == io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWorkPhase.INPUT_ISSUE_PENDING;
        var corridor = input ? work.inputTraversal().linearCorridorSurfaces() : work.workTraversal().linearCorridorSurfaces();
        int cursor = input ? work.inputTraversalCursor() : work.workTraversalCursor();
        String next = cursor + 1 >= corridor.size() ? "null" : position(corridor.get(cursor + 1).support());
        return ",\"serviceWork\":\"" + quote(work.id().value()) + "\",\"servicePhase\":\"" + work.phase()
                + "\",\"serviceCurrent\":" + position(current.support()) + ",\"serviceNext\":" + next
                + ",\"serviceInputCursor\":" + work.inputTraversalCursor() + ",\"serviceInputNodes\":" + work.inputTraversal().linearCorridorSurfaces().size()
                + ",\"serviceWorkCursor\":" + work.workTraversalCursor() + ",\"serviceWorkNodes\":" + work.workTraversal().linearCorridorSurfaces().size()
                + ",\"serviceTarget\":\"" + quote(work.target().toString()) + "\"";
    }

    /** Read-only next-edge evidence for a class-D patrol; it never selects or moves a resident. */
    private static String patrolTraversal(io.farfrontier.palemirror.frontier.v3.model.RoutePatrol patrol) {
        if (patrol == null) return ",\"patrolStatus\":\"\",\"patrolRouteIndex\":-1,\"patrolCurrent\":null,\"patrolNextSurface\":null,\"patrolNextBody\":null";
        var bodies = io.farfrontier.palemirror.frontier.v3.model.FrontierRoutePatrolSceneSupport.bodies(patrol);
        var leader = bodies.get(patrol.guardId());
        java.util.List<io.farfrontier.palemirror.frontier.v3.api.SubjectId> safe = patrol.safeAdvances();
        io.farfrontier.palemirror.frontier.v3.model.BodyPosition nextBody = safe.isEmpty() ? null
                : io.farfrontier.palemirror.frontier.v3.model.FrontierRoutePatrolSceneSupport.bodies(patrol.advance(safe.getFirst())).get(safe.getFirst());
        return ",\"patrolStatus\":\"" + patrol.status() + "\",\"patrolRouteIndex\":" + patrol.routeIndex()
                + ",\"patrolCurrent\":" + position(leader.supportingSurface().support())
                + ",\"patrolNextSurface\":" + (nextBody == null ? "null" : position(nextBody.supportingSurface().support()))
                + ",\"patrolNextBody\":" + (nextBody == null ? "null" : position(nextBody));
    }

    /** Diagnostic selection is pure and cannot make the standalone formatter load Minecraft classes. */
    private static io.farfrontier.palemirror.frontier.v3.model.SceneLease currentLease(FrontierWorldState state, SubjectId sceneSubject) {
        return state.sceneLeases().values().stream()
                .filter(lease -> io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.owns(lease, sceneSubject))
                .max(java.util.Comparator.comparingInt((io.farfrontier.palemirror.frontier.v3.model.SceneLease lease) ->
                                lease.status() == io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED ? 0 : 1)
                        .thenComparing(io.farfrontier.palemirror.frontier.v3.model.SceneLease::handoffInstant)
                        .thenComparingLong(io.farfrontier.palemirror.frontier.v3.model.SceneLease::revision)
                        .thenComparing(io.farfrontier.palemirror.frontier.v3.model.SceneLease::id))
                .orElse(null);
    }

    private static String sceneReadiness(FrontierV3SceneReadiness.Value value) {
        return ",\"physicalReadiness\":{\"bodies\":\"" + quote(value.bodies()) + "\",\"carrier\":\"" + quote(value.carrier())
                + "\",\"serviceDemand\":\"" + quote(value.serviceDemand()) + "\",\"serviceMotion\":\"" + quote(value.serviceMotion())
                + "\",\"serviceInput\":\"" + quote(value.serviceInput()) + "\",\"members\":" + strings(value.members()) + "}";
    }

    private static String intent(String id, CheckpointImage checkpoint, FrontierWorldState state,
                                 Optional<FrontierV3ResourceSiteHarvestExecutor.Readiness> harvestReadiness,
                                 Optional<FrontierV3EquipmentIssueExecutor.Readiness> equipmentIssueReadiness,
                                 Optional<FrontierV3EquipmentReturnExecutor.Readiness> equipmentReturnReadiness) {
        PhysicalIntent intent;
        try { intent = state.physicalIntents().get(new PhysicalIntentId(id)); }
        catch (IllegalArgumentException invalid) { intent = null; }
        if (intent == null) return unavailable("intent", id, checkpoint, "not_found");
        String subjects = intent.subjectIds().stream().sorted().map(value -> "\"" + quote(value.value()) + "\"").reduce((left, right) -> left + "," + right).orElse("");
        return base("intent", id, checkpoint) + ",\"status\":\"ok\",\"intentKind\":\"" + intent.kind()
                + "\",\"intentStatus\":\"" + intent.status() + "\",\"causeSubject\":\"" + quote(intent.causeSubjectId().value())
                + "\",\"radius\":" + intent.radiusBlocks() + ",\"subjects\":[" + subjects + "]"
                + ",\"receiptId\":\"" + quote(intent.postconditionObservationId().map(value -> value.value()).orElse("")) + "\""
                + (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.RESOURCE_SITE_HARVEST
                    ? harvestReadiness.map(FrontierV3DiagnosticJson::harvestReadiness).orElse("") : "")
                + (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_ISSUE
                    ? equipmentIssueReadiness.map(FrontierV3DiagnosticJson::equipmentIssueReadiness).orElse("") : "")
                + (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_RETURN
                    ? equipmentReturnReadiness.map(FrontierV3DiagnosticJson::equipmentReturnReadiness).orElse("") : "") + "}";
    }

    private static String equipmentIssueReadiness(FrontierV3EquipmentIssueExecutor.Readiness value) {
        return ",\"physicalReadiness\":{\"selection\":\"" + quote(value.selection().name())
                + "\",\"detail\":\"" + quote(value.detail()) + "\"}";
    }

    private static String equipmentReturnReadiness(FrontierV3EquipmentReturnExecutor.Readiness value) {
        return ",\"physicalReadiness\":{\"selection\":\"" + quote(value.selection().name())
                + "\",\"detail\":\"" + quote(value.detail()) + "\"}";
    }

    private static String harvestReadiness(FrontierV3ResourceSiteHarvestExecutor.Readiness value) {
        return ",\"physicalReadiness\":{\"fieldLoaded\":" + value.fieldLoaded()
                + ",\"depotLoaded\":" + value.depotLoaded()
                + ",\"depotSurface\":\"" + quote(value.depotSurface())
                + "\",\"ownedChestPresent\":" + value.ownedChestPresent()
                + ",\"fieldMatchesMatureStage\":" + value.fieldMatchesMatureStage()
                + ",\"outputSlotEmpty\":" + value.outputSlotEmpty()
                + ",\"claimedFieldStage\":" + value.claimedFieldStage()
                + ",\"fieldMatchesClaimedStage\":" + value.fieldMatchesClaimedStage()
                + ",\"precondition\":\"" + value.precondition() + "\"}";
    }

    private static String trace(String id, CheckpointImage checkpoint, Optional<FrontierV3DiagnosticTrace.Entry> trace) {
        if (trace.isEmpty()) return unavailable("trace", id, checkpoint, "not_found");
        FrontierV3DiagnosticTrace.Entry entry = trace.orElseThrow();
        return base("trace", id, checkpoint) + ",\"status\":\"ok\",\"correlation\":\"" + quote(entry.correlation())
                + "\",\"eventKind\":\"" + quote(entry.kind()) + "\",\"subject\":\"" + quote(entry.subject())
                + "\",\"command\":\"" + quote(entry.commandId()) + "\",\"transaction\":\"" + quote(entry.transactionId())
                + "\",\"acceptedRevision\":" + entry.revision() + traceContext(entry.context()) + "}";
    }

    private static String traceContext(FrontierV3DiagnosticTrace.Context context) {
        if (context.operationId().isEmpty()) return "";
        return ",\"causal\":{\"operation\":\"" + quote(context.operationId()) + "\",\"lease\":\""
                + quote(context.leaseId()) + "\",\"cargo\":\"" + quote(context.cargoId()) + "\",\"actors\":" + strings(context.actorIds()) + "}";
    }

    private static String unavailable(String kind, String id, CheckpointImage checkpoint, String status) {
        return base(kind, id, checkpoint) + ",\"status\":\"" + quote(status) + "\"}";
    }

    private static String base(String kind, String id, CheckpointImage checkpoint) {
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

    private static Optional<SubjectId> subject(String value) { try { return Optional.of(new SubjectId(value)); } catch (IllegalArgumentException invalid) { return Optional.empty(); } }
    private static Optional<Bioform> bioform(FrontierWorldState state, SubjectId id) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream()).filter(value -> value.id().equals(id)).findFirst();
    }
    private static String position(BlockPosition position) { return "{\"x\":" + position.x() + ",\"y\":" + position.y() + ",\"z\":" + position.z() + "}"; }
    private static String position(BodyPosition position) { return "{\"x\":" + position.x() + ",\"y\":" + position.y() + ",\"z\":" + position.z() + "}"; }
    private static String nullablePosition(BlockPosition position) { return position == null ? "null" : position(position); }
    private static String nullablePosition(FrontierV3AmbientActorExecutor.ObservedPosition position) {
        return position == null ? "null" : "{\"x\":" + position.x() + ",\"y\":" + position.y() + ",\"z\":" + position.z() + "}";
    }
    private static String strings(java.util.List<String> values) { return values.stream().map(value -> "\"" + quote(value) + "\"").reduce((left, right) -> left + "," + right).map(value -> "[" + value + "]").orElse("[]"); }
    private static String custody(InventoryCustody custody) {
        return switch (custody) {
            case InventoryCustody.ContainerSlot slot -> "{\"kind\":\"CONTAINER_SLOT\",\"container\":\"" + quote(slot.containerId().value()) + "\",\"slot\":" + slot.slot() + "}";
            case InventoryCustody.Cargo cargo -> "{\"kind\":\"CARGO\",\"cargo\":\"" + quote(cargo.cargoId().value()) + "\"}";
            case InventoryCustody.Player player -> "{\"kind\":\"PLAYER\",\"player\":\"" + player.playerId() + "\"}";
            case InventoryCustody.WorldCarrier carrier -> "{\"kind\":\"WORLD_CARRIER\",\"carrier\":\"" + carrier.carrierId() + "\"}";
            case InventoryCustody.Actor actor -> "{\"kind\":\"ACTOR\",\"actor\":\"" + quote(actor.actorId().value()) + "\"}";
        };
    }
    private static String quote(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
