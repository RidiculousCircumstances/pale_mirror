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
import io.farfrontier.palemirror.frontier.v3.model.ResidentProfile;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;

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
        Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(id, "id");
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(trace, "trace");
        Objects.requireNonNull(admission, "admission");
        Objects.requireNonNull(harvestReadiness, "harvestReadiness");
        Objects.requireNonNull(sceneReadiness, "sceneReadiness");
        String value = switch (kind) {
            case "summary" -> summary(checkpoint, state);
            case "site" -> site(id, checkpoint, state);
            case "settlement" -> settlement(id, checkpoint, state);
            case "hive" -> hive(id, checkpoint, state);
            case "actor" -> actor(id, checkpoint, state, admission);
            case "item" -> item(id, checkpoint, state);
            case "container" -> container(id, checkpoint, state);
            case "operation" -> operation(id, checkpoint, state);
            case "scene" -> scene(id, checkpoint, state, sceneReadiness);
            case "intent" -> intent(id, checkpoint, state, harvestReadiness);
            case "trace" -> trace(id, checkpoint, trace);
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
        return base("settlement", id, checkpoint) + ",\"status\":\"ok\",\"strategic\":" + lane(value.strategic())
                + ",\"facility\":" + lane(value.facility()) + ",\"readySites\":" + strings(value.readySites())
                + ",\"pendingHarvestSchedules\":" + strings(value.pendingHarvestSchedules())
                + ",\"harvestAdmission\":\"" + quote(value.harvestAdmission()) + "\",\"livingFarmers\":" + value.livingFarmers()
                + ",\"availableFarmer\":\"" + quote(value.availableFarmerId()) + "\",\"farmStatus\":\"" + quote(value.farmStatus())
                + "\",\"depotSurface\":\"" + quote(value.depotSurface()) + "\",\"depotHasFreeSlot\":" + value.depotHasFreeSlot() + "}";
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
        return base("actor", id, checkpoint) + ",\"status\":\"ok\",\"actorKind\":\"" + (resident != null ? "RESIDENT" : "BIOFORM")
                + "\",\"owner\":\"" + quote(owner) + "\",\"role\":\"" + role + "\",\"life\":\"" + location.condition().status()
                + "\",\"healthRaw\":" + location.condition().health().raw() + ",\"position\":" + position(location.position())
                + ",\"ambientLease\":\"" + quote(state.ambientLeases().containsKey(subject) ? state.ambientLeases().get(subject).status().name() : "NONE") + "\""
                + admission.map(FrontierV3DiagnosticJson::admission).orElse("") + "}";
    }

    private static String admission(FrontierV3AmbientActorExecutor.AdmissionDiagnostic value) {
        String placement = value.placement() == null ? "null" : position(value.placement());
        String entityId = value.entityId() == null ? "" : value.entityId().toString();
        return ",\"physicalAdmission\":{\"status\":\"" + quote(value.status()) + "\",\"entityUuid\":\""
                + quote(entityId) + "\",\"pending\":" + value.pending() + ",\"placement\":" + placement + "}";
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

    private static String operation(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null); RouteOperation operation = subject == null ? null : state.operations().get(subject);
        if (operation == null) return unavailable("operation", id, checkpoint, "not_found");
        String members = operation.participantIds().stream().sorted().map(value -> "\"" + quote(value.value()) + "\"").reduce((left, right) -> left + "," + right).orElse("");
        return base("operation", id, checkpoint) + ",\"status\":\"ok\",\"owner\":\"" + quote(operation.settlementId().value())
                + "\",\"cargo\":\"" + quote(operation.cargoId().value()) + "\",\"destination\":\"" + quote(operation.destinationId().value())
                + "\",\"stage\":\"" + operation.stage() + "\",\"routeIndex\":" + operation.routeIndex()
                + ",\"routeLength\":" + operation.route().size() + ",\"participants\":[" + members + "]}";
    }

    /** One stable engagement-level view for player-piloted physical-scene evidence. */
    private static String scene(String id, CheckpointImage checkpoint, FrontierWorldState state,
                                Optional<FrontierV3SceneExecutor.Readiness> readiness) {
        SubjectId engagement = subject(id).orElse(null);
        if (engagement == null) return unavailable("scene", id, checkpoint, "not_found");
        var lease = state.sceneLeases().values().stream().filter(value -> value.engagementId().filter(engagement::equals).isPresent())
                .sorted(java.util.Comparator.comparing(io.farfrontier.palemirror.frontier.v3.model.SceneLease::id)).findFirst().orElse(null);
        if (lease == null) return unavailable("scene", id, checkpoint, "not_found");
        PhysicalIntent explosion = state.physicalIntents().values().stream().filter(value -> value.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXPLOSION)
                .filter(value -> value.subjectIds().size() == 2 && value.subjectIds().getLast().equals(engagement))
                .sorted(java.util.Comparator.comparing(PhysicalIntent::id)).findFirst().orElse(null);
        return base("scene", id, checkpoint) + ",\"status\":\"ok\",\"leaseStatus\":\"" + lease.status()
                + "\",\"members\":" + lease.members().size() + ",\"explosionStatus\":\"" + (explosion == null ? "NONE" : explosion.status()) + "\""
                + readiness.map(FrontierV3DiagnosticJson::sceneReadiness).orElse("") + "}";
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
                + "\",\"acceptedRevision\":" + entry.revision() + "}";
    }

    private static String unavailable(String kind, String id, CheckpointImage checkpoint, String status) {
        return base(kind, id, checkpoint) + ",\"status\":\"" + quote(status) + "\"}";
    }

    private static String base(String kind, String id, CheckpointImage checkpoint) {
        return "{\"schema\":1,\"kind\":\"" + quote(kind) + "\",\"id\":\"" + quote(id) + "\",\"world\":\""
                + quote(checkpoint.worldId().value()) + "\",\"revision\":" + checkpoint.revision().value() + ",\"instant\":" + checkpoint.instant().ticks();
    }

    private static Optional<SubjectId> subject(String value) { try { return Optional.of(new SubjectId(value)); } catch (IllegalArgumentException invalid) { return Optional.empty(); } }
    private static Optional<Bioform> bioform(FrontierWorldState state, SubjectId id) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream()).filter(value -> value.id().equals(id)).findFirst();
    }
    private static String position(BlockPosition position) { return "{\"x\":" + position.x() + ",\"y\":" + position.y() + ",\"z\":" + position.z() + "}"; }
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
