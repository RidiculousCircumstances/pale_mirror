package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLocation;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ContainerRecord;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSettlementWorkDiagnostic;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.ResidentProfile;
import io.farfrontier.palemirror.frontier.v3.model.ResidentNutritionStatus;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstruction;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import io.farfrontier.palemirror.frontier.v3.model.SettlementProvision;

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
                         Optional<FrontierV3AmbientActorExecutor.AdmissionDiagnostic> admission) {
        return render(kind, id, checkpoint, state, trace, admission, Optional.empty());
    }

    static String render(String kind, String id, CheckpointImage checkpoint, FrontierWorldState state,
                         Optional<FrontierV3DiagnosticTrace.Entry> trace,
                         Optional<FrontierV3AmbientActorExecutor.AdmissionDiagnostic> admission,
                         Optional<FrontierV3ResourceSiteHarvestExecutor.Readiness> harvestReadiness) {
        return render(kind, id, checkpoint, state, trace, admission, harvestReadiness, Optional.empty());
    }

    static String render(String kind, String id, CheckpointImage checkpoint, FrontierWorldState state,
                         Optional<FrontierV3DiagnosticTrace.Entry> trace,
                         Optional<FrontierV3AmbientActorExecutor.AdmissionDiagnostic> admission,
                         Optional<FrontierV3ResourceSiteHarvestExecutor.Readiness> harvestReadiness,
                         Optional<FrontierV3SceneExecutor.Readiness> sceneReadiness) {
        return render(kind, id, checkpoint, state, trace, admission, harvestReadiness, sceneReadiness, Optional.empty());
    }

    static String render(String kind, String id, CheckpointImage checkpoint, FrontierWorldState state,
                         Optional<FrontierV3DiagnosticTrace.Entry> trace,
                         Optional<FrontierV3AmbientActorExecutor.AdmissionDiagnostic> admission,
                         Optional<FrontierV3ResourceSiteHarvestExecutor.Readiness> harvestReadiness,
                         Optional<FrontierV3SceneExecutor.Readiness> sceneReadiness,
                         Optional<FrontierV3AmbientActorExecutor.AssemblyReadiness> assemblyReadiness) {
        Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(id, "id");
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(trace, "trace");
        Objects.requireNonNull(admission, "admission");
        Objects.requireNonNull(harvestReadiness, "harvestReadiness");
        Objects.requireNonNull(sceneReadiness, "sceneReadiness");
        Objects.requireNonNull(assemblyReadiness, "assemblyReadiness");
        String value = switch (kind) {
            case "summary" -> summary(checkpoint, state);
            case "site" -> site(id, checkpoint, state);
            case "settlement" -> settlement(id, checkpoint, state);
            case "hive" -> hive(id, checkpoint, state);
            case "actor" -> actor(id, checkpoint, state, admission);
            case "item" -> item(id, checkpoint, state);
            case "container" -> container(id, checkpoint, state);
            case "operation" -> operation(id, checkpoint, state, assemblyReadiness);
            case "route_construction" -> routeConstruction(id, checkpoint, state);
            case "physical_delta" -> physicalDelta(id, checkpoint, state);
            case "scene" -> scene(id, checkpoint, state, sceneReadiness);
            case "intent" -> intent(id, checkpoint, state, harvestReadiness);
            case "trace" -> trace(id, checkpoint, trace);
            case "transit" -> transit(id, checkpoint, state);
            default -> unavailable(kind, id, checkpoint, "unknown_view");
        };
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            value = unavailable(kind, id, checkpoint, "response_limit");
        }
        return PREFIX + value;
    }

    static String unavailableRuntime(String kind, String id) {
        return PREFIX + "{\"schema\":1,\"kind\":\"" + quote(kind) + "\",\"id\":\"" + quote(id)
                + "\",\"status\":\"runtime_unavailable\"}";
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

    private static String site(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null);
        ResourceSiteLifecycle lifecycle = subject == null ? null : state.resourceSites().sites().get(subject);
        ResourceSite site = subject == null ? null : FrontierResourceSitePlan.compile(state.bootstrap()).get(subject);
        if (lifecycle == null || site == null) return unavailable("site", id, checkpoint, "not_found");
        String work = lifecycle.activeWork().map(value -> value.id().value()).orElse("");
        return base("site", id, checkpoint) + ",\"status\":\"ok\",\"owner\":\"" + quote(site.settlementId().value())
                + "\",\"facility\":\"" + quote(site.facilityId().value()) + "\",\"phase\":\"" + lifecycle.phase()
                + "\",\"growthEpoch\":" + lifecycle.growthEpoch() + ",\"growthStage\":" + lifecycle.growthStage()
                + ",\"activeWork\":\"" + quote(work) + "\",\"firstCrop\":" + position(site.cropSlots().getFirst()) + "}";
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

    private static String lane(FrontierSettlementWorkDiagnostic.Lane lane) {
        return "{\"objective\":\"" + quote(lane.objectiveId()) + "\",\"kind\":\"" + quote(lane.kind())
                + "\",\"status\":\"" + quote(lane.status()) + "\"}";
    }

    private static String actor(String id, CheckpointImage checkpoint, FrontierWorldState state,
                                Optional<FrontierV3AmbientActorExecutor.AdmissionDiagnostic> admission) {
        SubjectId subject = subject(id).orElse(null); ActorLocation location = subject == null ? null : state.actorLocations().get(subject);
        if (location == null) return unavailable("actor", id, checkpoint, "not_found");
        ResidentProfile resident = state.humanPopulation().resident(subject);
        Bioform bioform = bioform(state, subject).orElse(null);
        String role = resident != null ? resident.role().name() : bioform != null ? bioform.role().name() : "UNKNOWN";
        String owner = resident != null ? resident.settlementId().value() : bioform != null ? bioform.hiveId().value() : "";
        String nutrition = resident == null ? "" : state.humanPopulation().nutrition(subject).status().name();
        return base("actor", id, checkpoint) + ",\"status\":\"ok\",\"actorKind\":\"" + (resident != null ? "RESIDENT" : "BIOFORM")
                + "\",\"owner\":\"" + quote(owner) + "\",\"role\":\"" + role + "\",\"life\":\"" + location.condition().status()
                + "\",\"healthRaw\":" + location.condition().health().raw() + ",\"position\":" + position(location.position())
                + ",\"nutrition\":\"" + quote(nutrition) + "\",\"ambientLease\":\""
                + quote(state.ambientLeases().containsKey(subject) ? state.ambientLeases().get(subject).status().name() : "NONE") + "\""
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
        String goalPosition = lease == null ? "null" : position(lease.goalPosition());
        return base("transit", id, checkpoint) + ",\"status\":\"ok\",\"journeyStatus\":\"" + journey.status()
                + "\",\"origin\":\"" + quote(journey.originSettlementId().value()) + "\",\"destination\":\""
                + quote(journey.destinationSettlementId().value()) + "\",\"routeIndex\":" + journey.routeIndex()
                + ",\"routeLength\":" + journey.route().size() + ",\"current\":" + position(journey.currentPosition())
                + ",\"next\":" + next + ",\"ambientLease\":\"" + quote(lease == null ? "NONE" : lease.status().name())
                + "\",\"ambientGoal\":\"" + quote(goal) + "\",\"goalPosition\":" + goalPosition + "}";
    }

    private static String admission(FrontierV3AmbientActorExecutor.AdmissionDiagnostic value) {
        String placement = value.placement() == null ? "null" : position(value.placement());
        String observedPosition = value.observedPosition() == null ? "null" : position(value.observedPosition());
        String entityId = value.entityId() == null ? "" : value.entityId().toString();
        return ",\"physicalAdmission\":{\"status\":\"" + quote(value.status()) + "\",\"entityUuid\":\""
                + quote(entityId) + "\",\"pending\":" + value.pending() + ",\"placement\":" + placement
                + ",\"observedPosition\":" + observedPosition + "}";
    }

    private static String item(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null); ExactItemStack item = subject == null ? null : state.inventory().items().get(subject);
        if (item == null) return unavailable("item", id, checkpoint, "not_found");
        return base("item", id, checkpoint) + ",\"status\":\"ok\",\"owner\":\"" + quote(item.economicOwnerId().value())
                + "\",\"itemKind\":\"" + quote(item.itemKind()) + "\",\"count\":" + item.count() + ",\"custody\":" + custody(item.custody()) + "}";
    }

    /** One bounded exact-container projection for test-pilot and operator inspection. */
    private static String container(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null); ContainerRecord container = subject == null ? null : state.inventory().containers().get(subject);
        ContainerSurface surface = subject == null ? null : state.inventory().surfaces().get(subject);
        if (container == null || surface == null) return unavailable("container", id, checkpoint, "not_found");
        java.util.List<ExactItemStack> occupiedItems = state.inventory().items().values().stream().filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot
                        && slot.containerId().equals(subject)).sorted(java.util.Comparator.comparingInt(item -> ((InventoryCustody.ContainerSlot) item.custody()).slot()))
                .toList();
        String occupied = occupiedItems.stream().map(item -> "{\"slot\":" + ((InventoryCustody.ContainerSlot) item.custody()).slot() + ",\"item\":\"" + quote(item.id().value())
                        + "\",\"itemKind\":\"" + quote(item.itemKind()) + "\",\"count\":" + item.count() + "}")
                .reduce((left, right) -> left + "," + right).map(value -> "[" + value + "]").orElse("[]");
        return base("container", id, checkpoint) + ",\"status\":\"ok\",\"owner\":\"" + quote(container.ownerId().value())
                + "\",\"surface\":\"" + surface.status() + "\",\"slotCount\":" + container.slotCount()
                + ",\"occupiedCount\":" + occupiedItems.size() + ",\"occupied\":" + occupied + "}";
    }

    private static String operation(String id, CheckpointImage checkpoint, FrontierWorldState state,
                                    Optional<FrontierV3AmbientActorExecutor.AssemblyReadiness> readiness) {
        SubjectId subject = subject(id).orElse(null); RouteOperation operation = subject == null ? null : state.operations().get(subject);
        if (operation == null) return unavailable("operation", id, checkpoint, "not_found");
        String members = operation.participantIds().stream().sorted().map(value -> "\"" + quote(value.value()) + "\"").reduce((left, right) -> left + "," + right).orElse("");
        String assembly = operation.activeAssembly().map(value -> ",\"assemblyMembers\":" + value.members().size()
                + ",\"assemblyCursorTotal\":" + value.members().values().stream().mapToInt(io.farfrontier.palemirror.frontier.v3.model.OperationAssembly.Member::cursor).sum()
                + ",\"assemblyComplete\":" + value.complete() + ",\"cargoCarrier\":\"" + quote(value.cargoCarrierId().value()) + "\""
                + ",\"assemblyProgress\":[" + value.members().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> assemblyProgress(entry.getKey(), entry.getValue())).reduce((left, right) -> left + "," + right).orElse("") + "]"
                + value.deferral().map(deferral -> ",\"assemblyDeferred\":true,\"assemblyDeferredActor\":\"" + quote(deferral.actorId().value())
                        + "\",\"assemblyDeferredTarget\":" + position(deferral.target()) + ",\"assemblyObstructionFloor\":" + position(deferral.obstructionFloor())
                        + ",\"assemblyDeferredReason\":\"" + deferral.reason() + "\"")
                        .orElse(",\"assemblyDeferred\":false")).orElse("");
        String travel = operation.activeTravel().map(value -> ",\"travelCursor\":" + value.cursor() + ",\"travelLength\":" + value.corridor().size()
                + ",\"travelCurrent\":" + position(value.currentPosition()) + ",\"travelCargo\":" + position(value.cargoAnchor())
                + ",\"travelArrived\":" + value.arrived()).orElse("");
        return base("operation", id, checkpoint) + ",\"status\":\"ok\",\"owner\":\"" + quote(operation.settlementId().value())
                + "\",\"cargo\":\"" + quote(operation.cargoId().value()) + "\",\"destination\":\"" + quote(operation.destinationId().value())
                + "\",\"stage\":\"" + operation.stage() + "\",\"routeIndex\":" + operation.routeIndex()
                + ",\"routeLength\":" + operation.route().size() + ",\"participants\":[" + members + "]" + assembly + travel
                + readiness.map(FrontierV3DiagnosticJson::assemblyReadiness).orElse("") + "}";
    }

    /** Bounded exact cursors make a stalled ordinary HOT approach diagnosable without world mutation. */
    private static String assemblyProgress(SubjectId actorId, io.farfrontier.palemirror.frontier.v3.model.OperationAssembly.Member member) {
        String next = member.arrived() ? "null" : position(member.corridor().get(member.cursor() + 1));
        return "{\"actor\":\"" + quote(actorId.value()) + "\",\"cursor\":" + member.cursor()
                + ",\"length\":" + member.corridor().size() + ",\"current\":" + position(member.currentPosition()) + ",\"next\":" + next + "}";
    }

    private static String assemblyReadiness(FrontierV3AmbientActorExecutor.AssemblyReadiness value) {
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
        return base("route_construction", id, checkpoint) + ",\"status\":\"ok\",\"project\":\"" + quote(project.id().value())
                + "\",\"phase\":\"" + project.status() + "\",\"confirmedCells\":" + project.confirmedCells()
                + ",\"requiredCells\":" + cells.size() + ",\"cargo\":\"" + quote(project.cargoId().map(SubjectId::value).orElse(""))
                + "\",\"cargoPresent\":" + project.cargoId().isPresent() + ",\"nextCell\":" + next + "}";
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

    /** One stable engagement- or route-level view for player-piloted physical-scene evidence. */
    private static String scene(String id, CheckpointImage checkpoint, FrontierWorldState state,
                                Optional<FrontierV3SceneExecutor.Readiness> readiness) {
        SubjectId sceneSubject = subject(id).orElse(null);
        if (sceneSubject == null) return unavailable("scene", id, checkpoint, "not_found");
        var lease = currentLease(state, sceneSubject);
        if (lease == null) return unavailable("scene", id, checkpoint, "not_found");
        SubjectId engagement = lease.engagementId().orElse(null);
        var primaryMember = lease.members().getFirst();
        PhysicalIntent explosion = state.physicalIntents().values().stream().filter(value -> value.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXPLOSION)
                .filter(value -> engagement != null && value.subjectIds().size() == 2 && value.subjectIds().getLast().equals(engagement))
                .sorted(java.util.Comparator.comparing(PhysicalIntent::id)).findFirst().orElse(null);
        return base("scene", id, checkpoint) + ",\"status\":\"ok\",\"leaseId\":\"" + quote(lease.id().value())
                + "\",\"leaseStatus\":\"" + lease.status() + "\",\"operation\":\"" + quote(lease.operationId().value())
                + "\",\"members\":" + lease.members().size() + ",\"primaryActor\":\"" + quote(primaryMember.actorId().value())
                + "\",\"primaryEntityUuid\":\"" + primaryMember.entityId() + "\",\"explosionStatus\":\"" + (explosion == null ? "NONE" : explosion.status()) + "\""
                + readiness.map(FrontierV3DiagnosticJson::sceneReadiness).orElse("") + "}";
    }

    /** Diagnostic selection is pure and cannot make the standalone formatter load Minecraft classes. */
    private static io.farfrontier.palemirror.frontier.v3.model.SceneLease currentLease(FrontierWorldState state, SubjectId sceneSubject) {
        return state.sceneLeases().values().stream()
                .filter(lease -> lease.operationId().equals(sceneSubject)
                        || lease.engagementId().filter(sceneSubject::equals).isPresent())
                .max(java.util.Comparator.comparingInt((io.farfrontier.palemirror.frontier.v3.model.SceneLease lease) ->
                                lease.status() == io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED ? 0 : 1)
                        .thenComparing(io.farfrontier.palemirror.frontier.v3.model.SceneLease::handoffInstant)
                        .thenComparingLong(io.farfrontier.palemirror.frontier.v3.model.SceneLease::revision)
                        .thenComparing(io.farfrontier.palemirror.frontier.v3.model.SceneLease::id))
                .orElse(null);
    }

    private static String sceneReadiness(FrontierV3SceneExecutor.Readiness value) {
        return ",\"physicalReadiness\":{\"bodies\":\"" + quote(value.bodies()) + "\",\"carrier\":\"" + quote(value.carrier()) + "\"}";
    }

    private static String intent(String id, CheckpointImage checkpoint, FrontierWorldState state,
                                 Optional<FrontierV3ResourceSiteHarvestExecutor.Readiness> harvestReadiness) {
        PhysicalIntent intent;
        try { intent = state.physicalIntents().get(new PhysicalIntentId(id)); }
        catch (IllegalArgumentException invalid) { intent = null; }
        if (intent == null) return unavailable("intent", id, checkpoint, "not_found");
        String subjects = intent.subjectIds().stream().sorted().map(value -> "\"" + quote(value.value()) + "\"").reduce((left, right) -> left + "," + right).orElse("");
        return base("intent", id, checkpoint) + ",\"status\":\"ok\",\"intentKind\":\"" + intent.kind()
                + "\",\"intentStatus\":\"" + intent.status() + "\",\"causeSubject\":\"" + quote(intent.causeSubjectId().value())
                + "\",\"radius\":" + intent.radiusBlocks() + ",\"subjects\":[" + subjects + "]"
                + (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.RESOURCE_SITE_HARVEST
                    ? harvestReadiness.map(FrontierV3DiagnosticJson::harvestReadiness).orElse("") : "") + "}";
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
        };
    }
    private static String quote(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
