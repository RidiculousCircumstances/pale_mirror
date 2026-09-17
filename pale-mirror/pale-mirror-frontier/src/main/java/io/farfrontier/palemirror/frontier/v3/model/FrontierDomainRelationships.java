package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Closed, pure-domain descriptor and read-only projection for retained
 * cross-owner identities.  This class never stores an edge: each edge is
 * reconstructed from the aggregate that already owns the fact.
 */
public final class FrontierDomainRelationships {
    private FrontierDomainRelationships() { }

    public enum EntityKind {
        OBJECTIVE, TASK, MARKET_DEMAND, MARKET_QUOTE, MARKET_ORDER, FINANCIAL_RESERVATION,
        PRODUCTION_JOB, RESOURCE_SITE, RESOURCE_HARVEST_JOB, RESIDENT, EXACT_ITEM, RESOURCE_LOT,
        RESOURCE_CLAIM, PROVISION_CYCLE, PROVISION_ALLOCATION, SCENE_LEASE, AMBIENT_LEASE, CARRIER_EVIDENCE
    }

    /** Stable semantic strings are deliberately independent from enum ordinal/wire ordering. */
    public enum Kind {
        OBJECTIVE_TASK("objective-task"), TASK_RESOURCE_SITE("task-resource-site"), TASK_PREDECESSOR("task-predecessor"),
        DEMAND_TASK("demand-task"), QUOTE_DEMAND("quote-demand"), ORDER_DEMAND("order-demand"),
        ORDER_QUOTE("order-quote"), ORDER_TASK("order-task"), ORDER_JOB("order-job"), ORDER_RESERVATION("order-reservation"),
        JOB_WORKER("job-worker"), JOB_INPUT("job-input"), JOB_OUTPUT("job-output"), JOB_SCENE_LEASE("job-scene-lease"),
        ACTOR_AMBIENT_LEASE("actor-ambient-lease"), HARVEST_SITE("harvest-site"), HARVEST_TASK("harvest-task"),
        HARVEST_WORKER("harvest-worker"), HARVEST_OUTPUT("harvest-output"), HARVEST_PREDECESSOR("harvest-predecessor"),
        HARVEST_SUCCESSOR_TASK("harvest-successor-task"), HARVEST_SUCCESSOR_JOB("harvest-successor-job"),
        PROVISION_ALLOCATION("provision-allocation"), ALLOCATION_RESOURCE("allocation-resource"), ALLOCATION_RECIPIENT("allocation-recipient"),
        ACTOR_CARRIER_EVIDENCE("actor-carrier-evidence");

        private final String tag;
        Kind(String tag) { this.tag = tag; }
        public String tag() { return tag; }
    }

    public enum Lifecycle { ACTIVE, TERMINAL_RETAINED, TERMINAL_COMPACTED, PREPARED, OBSERVED }
    public enum IncidentReason { MISSING_ENDPOINT, DUPLICATE_ENDPOINT, WRONG_TYPE, LIFECYCLE_INCOMPATIBLE, STALE_RELATION }
    public enum Cardinality { ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY }
    public enum CycleRule { FORBIDDEN, HIERARCHICAL, MEANINGFUL }
    public enum Disposition { FAIL_CLOSED_LOCAL, QUARANTINE_RECOVERY, EVIDENCE_ONLY }
    /** Closed executable declaration: emitted edges are rejected unless declared here. */
    public record Declaration(Kind kind, EntityKind source, EntityKind target, EntityKind owner, Cardinality cardinality,
                              Set<Lifecycle> lifecycle, Lifecycle retention, CycleRule cycleRule, Disposition disposition) {
        public Declaration { Objects.requireNonNull(kind); Objects.requireNonNull(source); Objects.requireNonNull(target); Objects.requireNonNull(owner);
            Objects.requireNonNull(cardinality); lifecycle = Set.copyOf(lifecycle); Objects.requireNonNull(retention); Objects.requireNonNull(cycleRule); Objects.requireNonNull(disposition); }
        void validate(Edge edge) { boolean resourceVariant = (kind == Kind.JOB_INPUT || kind == Kind.JOB_OUTPUT || kind == Kind.ALLOCATION_RESOURCE) && edge.target().kind() == EntityKind.RESOURCE_LOT;
            if (edge.source().kind() != source || (!resourceVariant && edge.target().kind() != target)
                    || edge.owner().kind() != owner || !lifecycle.contains(edge.lifecycle()))
                throw new IllegalArgumentException("relationship declaration rejects " + kind.tag()); }
    }
    private static final Map<Kind, Declaration> DECLARATIONS = declared();
    public static Declaration declaration(Kind kind) { Declaration value = DECLARATIONS.get(Objects.requireNonNull(kind));
        if (value == null) throw new IllegalArgumentException("unregistered relationship kind"); return value; }
    public static Map<Kind, Declaration> declarations() { return DECLARATIONS; }
    private static Map<Kind, Declaration> declared() { Map<Kind, Declaration> result = new java.util.EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) result.put(kind, new Declaration(kind, source(kind), target(kind), owner(kind), cardinality(kind), lifecycle(kind),
                Lifecycle.TERMINAL_RETAINED, CycleRule.FORBIDDEN, kind == Kind.ACTOR_CARRIER_EVIDENCE ? Disposition.EVIDENCE_ONLY : Disposition.FAIL_CLOSED_LOCAL));
        return Map.copyOf(result); }
    private static Set<Lifecycle> lifecycle(Kind kind) { return kind == Kind.ACTOR_AMBIENT_LEASE || kind == Kind.ACTOR_CARRIER_EVIDENCE ? EnumSet.of(Lifecycle.OBSERVED) : EnumSet.allOf(Lifecycle.class); }
    private static EntityKind source(Kind kind) { return switch (kind) {
        case OBJECTIVE_TASK -> EntityKind.OBJECTIVE; case TASK_RESOURCE_SITE, TASK_PREDECESSOR -> EntityKind.TASK; case DEMAND_TASK -> EntityKind.MARKET_DEMAND; case QUOTE_DEMAND -> EntityKind.MARKET_QUOTE;
        case ORDER_DEMAND, ORDER_QUOTE, ORDER_TASK, ORDER_JOB, ORDER_RESERVATION -> EntityKind.MARKET_ORDER; case JOB_WORKER, JOB_INPUT, JOB_OUTPUT, JOB_SCENE_LEASE -> EntityKind.PRODUCTION_JOB;
        case ACTOR_AMBIENT_LEASE, ACTOR_CARRIER_EVIDENCE -> EntityKind.RESIDENT; case HARVEST_SITE, HARVEST_PREDECESSOR -> EntityKind.RESOURCE_SITE;
        case HARVEST_TASK, HARVEST_WORKER, HARVEST_OUTPUT, HARVEST_SUCCESSOR_TASK, HARVEST_SUCCESSOR_JOB -> EntityKind.RESOURCE_HARVEST_JOB;
        case PROVISION_ALLOCATION -> EntityKind.PROVISION_CYCLE; case ALLOCATION_RESOURCE, ALLOCATION_RECIPIENT -> EntityKind.PROVISION_ALLOCATION; }; }
    private static EntityKind target(Kind kind) { return switch (kind) {
        case OBJECTIVE_TASK, ORDER_TASK, DEMAND_TASK, HARVEST_TASK, HARVEST_SUCCESSOR_TASK, TASK_PREDECESSOR -> EntityKind.TASK;
        case TASK_RESOURCE_SITE -> EntityKind.RESOURCE_SITE; case QUOTE_DEMAND, ORDER_DEMAND -> EntityKind.MARKET_DEMAND;
        case ORDER_QUOTE -> EntityKind.MARKET_QUOTE; case ORDER_JOB -> EntityKind.PRODUCTION_JOB; case ORDER_RESERVATION -> EntityKind.FINANCIAL_RESERVATION;
        case JOB_WORKER, HARVEST_WORKER, ALLOCATION_RECIPIENT -> EntityKind.RESIDENT; case JOB_INPUT, JOB_OUTPUT, HARVEST_OUTPUT, ALLOCATION_RESOURCE -> EntityKind.EXACT_ITEM;
        case JOB_SCENE_LEASE -> EntityKind.SCENE_LEASE; case ACTOR_AMBIENT_LEASE -> EntityKind.AMBIENT_LEASE; case ACTOR_CARRIER_EVIDENCE -> EntityKind.CARRIER_EVIDENCE;
        case HARVEST_SITE, HARVEST_PREDECESSOR, HARVEST_SUCCESSOR_JOB -> EntityKind.RESOURCE_HARVEST_JOB; case PROVISION_ALLOCATION -> EntityKind.PROVISION_ALLOCATION; }; }
    private static EntityKind owner(Kind kind) { return switch (kind) {
        case OBJECTIVE_TASK -> EntityKind.OBJECTIVE; case TASK_RESOURCE_SITE, TASK_PREDECESSOR -> EntityKind.TASK;
        case DEMAND_TASK -> EntityKind.MARKET_DEMAND; case QUOTE_DEMAND -> EntityKind.MARKET_QUOTE;
        case ORDER_DEMAND, ORDER_QUOTE, ORDER_TASK, ORDER_JOB, ORDER_RESERVATION -> EntityKind.MARKET_ORDER;
        case JOB_WORKER, JOB_INPUT, JOB_OUTPUT, JOB_SCENE_LEASE -> EntityKind.PRODUCTION_JOB;
        case ACTOR_AMBIENT_LEASE, ACTOR_CARRIER_EVIDENCE -> EntityKind.RESIDENT;
        case HARVEST_SITE, HARVEST_PREDECESSOR, HARVEST_SUCCESSOR_TASK, HARVEST_SUCCESSOR_JOB -> EntityKind.RESOURCE_SITE;
        case HARVEST_TASK, HARVEST_WORKER, HARVEST_OUTPUT -> EntityKind.RESOURCE_HARVEST_JOB;
        case PROVISION_ALLOCATION -> EntityKind.PROVISION_CYCLE; case ALLOCATION_RESOURCE, ALLOCATION_RECIPIENT -> EntityKind.PROVISION_ALLOCATION; }; }
    private static Cardinality cardinality(Kind kind) { return switch (kind) {
        case OBJECTIVE_TASK, PROVISION_ALLOCATION, ACTOR_CARRIER_EVIDENCE -> Cardinality.ONE_TO_MANY;
        case TASK_PREDECESSOR, ALLOCATION_RECIPIENT -> Cardinality.MANY_TO_MANY; default -> Cardinality.MANY_TO_ONE; }; }
    public enum FamilyDisposition { RELATION_LAYER_CURRENT, OWNER_EXPLICIT_UNCHANGED, MIGRATION_REQUIRED_BEFORE_TOUCH, NOT_A_DOMAIN_RELATION }
    /** Closed inventory: only the current vertical is migrated by REL-001. */
    public record Family(String tag, FamilyDisposition disposition, String owner, String migrationBoundary) {
        public Family {
            if (tag == null || !tag.matches("[a-z0-9-]{3,64}")) throw new IllegalArgumentException("relationship family tag is invalid");
            Objects.requireNonNull(disposition, "relationship family disposition"); Objects.requireNonNull(owner, "relationship family owner"); Objects.requireNonNull(migrationBoundary, "relationship family migration boundary");
        }
    }
    private static final List<Family> INVENTORY = List.of(
            new Family("strategic-objective-task", FamilyDisposition.RELATION_LAYER_CURRENT, "StrategicPlanState", "F0.6R3 vertical"),
            new Family("market-demand-order-production", FamilyDisposition.RELATION_LAYER_CURRENT, "MarketOrderBook/ProductionJob", "F0.6R3 vertical"),
            new Family("resource-harvest-successor", FamilyDisposition.RELATION_LAYER_CURRENT, "ResourceSiteLifecycle", "F0.6R3 vertical"),
            new Family("provision-allocation-recipient", FamilyDisposition.RELATION_LAYER_CURRENT, "SettlementProvision", "F0.6R3 vertical"),
            new Family("actor-physical-carrier", FamilyDisposition.RELATION_LAYER_CURRENT, "canonical actor plus NeoForge evidence", "XACT-001 completes producer/adopter inventory"),
            new Family("supply-cargo-route", FamilyDisposition.OWNER_EXPLICIT_UNCHANGED, "SupplyContract/RouteOperation", "migrate before logistics modification"),
            new Family("hive-operation-roster", FamilyDisposition.OWNER_EXPLICIT_UNCHANGED, "HiveColony/StrategicPlanState", "migrate before MAT-007 modification"),
            new Family("service-work-station", FamilyDisposition.MIGRATION_REQUIRED_BEFORE_TOUCH, "SettlementServiceWork", "migrate before service-work modification"),
            new Family("replica-fingerprint", FamilyDisposition.NOT_A_DOMAIN_RELATION, "PhysicalReplicaCustodyState", "adapter evidence, never domain relationship authority"));
    public static List<Family> inventory() { return INVENTORY; }

    /**
     * The REL-001 current families deliberately name their durable owner surfaces.  Reflection
     * makes a newly added direct identity component fail the relationship gate until its owner
     * and relationship disposition are explicitly reviewed here; it is not a claim to classify
     * untouched domain families or ordinary scalar implementation details.
     */
    public record OwnerSurface(Class<?> ownerType, Set<String> registeredIdentityComponents) {
        public OwnerSurface {
            Objects.requireNonNull(ownerType, "relationship owner surface type");
            registeredIdentityComponents = Set.copyOf(registeredIdentityComponents);
            if (!ownerType.isRecord()) throw new IllegalArgumentException("relationship owner surface must be a record");
        }

        void validate() { validateOwnerSurface(ownerType, registeredIdentityComponents); }
    }

    private static final List<OwnerSurface> CURRENT_OWNER_SURFACES = List.of(
            surface(StrategicObjective.class, "id", "ownerId", "resourceSiteTarget", "authorityId"),
            surface(StrategicTask.class, "id", "objectiveId", "ownerId", "operationTarget", "resourceSiteTarget", "dependencies", "authorityId"),
            surface(MarketDemand.class, "id", "buyerId", "reasonId"),
            surface(CompanyQuote.class, "id", "demandId", "sellerId"),
            surface(MarketWorkOrder.class, "id", "demandId", "quoteId", "sellerId", "taskId", "jobId", "reservationId"),
            surface(ProductionJob.class, "id", "settlementId", "facilityId", "workerId", "consumedItemId", "outputItemId"),
            surface(ResourceSiteLifecycle.class, "siteId"),
            surface(ResourceSiteHarvestJob.class, "id", "taskId", "siteId", "workerId", "outputItemId", "intentId"),
            surface(ResourceSiteHarvestLineage.class, "predecessorJobId", "predecessorTaskId", "workerId", "outputItemId", "successorTaskId", "successorJobId"),
            surface(SettlementProvision.class, "settlementId", "recipientIds", "activeIntentId"),
            surface(SettlementRationAllocation.class, "itemId", "recipientIds"),
            surface(SceneLease.class, "id", "worldId", "memberPositions", "ambientHandoffActorIds"),
            surface(AmbientActorLease.class, "actorId"));

    public static List<OwnerSurface> currentOwnerSurfaces() { return CURRENT_OWNER_SURFACES; }

    /** Static recurrence gate for the closed current-family inventory. */
    public static void verifyCurrentOwnerSurfaces() { CURRENT_OWNER_SURFACES.forEach(OwnerSurface::validate); }

    /** Visible for the negative architecture fixture; production callers use the closed manifest above. */
    static void validateOwnerSurface(Class<?> ownerType, Set<String> registeredIdentityComponents) {
        Objects.requireNonNull(ownerType, "relationship owner surface type");
        Objects.requireNonNull(registeredIdentityComponents, "relationship owner identity manifest");
        if (!ownerType.isRecord()) throw new IllegalArgumentException("relationship owner surface must be a record");
        Set<String> actual = new LinkedHashSet<>();
        for (RecordComponent component : ownerType.getRecordComponents()) {
            if (isDirectIdentityComponent(component)) actual.add(component.getName());
        }
        Set<String> registered = Set.copyOf(registeredIdentityComponents);
        if (!actual.equals(registered)) {
            throw new IllegalArgumentException("relationship owner surface is unregistered for " + ownerType.getName()
                    + ": expected=" + registered + " actual=" + actual);
        }
    }

    private static OwnerSurface surface(Class<?> ownerType, String... componentNames) {
        return new OwnerSurface(ownerType, Set.of(componentNames));
    }

    private static boolean isDirectIdentityComponent(RecordComponent component) {
        String type = component.getGenericType().getTypeName();
        return type.contains(".SubjectId") || type.contains(".SceneLeaseId") || type.contains(".PhysicalIntentId") || type.contains(".WorldId");
    }

    /** Nominal endpoint types prevent two equal wire strings from being treated as interchangeable. */
    public sealed interface Endpoint permits SubjectEndpoint, ProvisionAllocationEndpoint, SceneLeaseEndpoint, CarrierEvidenceEndpoint {
        EntityKind kind();
        String stableKey();
    }
    public record SubjectEndpoint(EntityKind kind, SubjectId id) implements Endpoint {
        public SubjectEndpoint { Objects.requireNonNull(kind, "relationship endpoint kind"); Objects.requireNonNull(id, "relationship endpoint id"); }
        @Override public String stableKey() { return kind + ":" + id.value(); }
    }
    public record ProvisionAllocationEndpoint(SubjectId settlementId, int cycle, int ordinal) implements Endpoint {
        @Override public EntityKind kind() { return EntityKind.PROVISION_ALLOCATION; }
        public ProvisionAllocationEndpoint { Objects.requireNonNull(settlementId, "provision allocation settlement"); if (cycle < 0 || ordinal < 0) throw new IllegalArgumentException("invalid provision allocation identity"); }
        @Override public String stableKey() { return kind() + ":" + settlementId.value() + ":" + cycle + ":" + ordinal; }
    }
    public record SceneLeaseEndpoint(SceneLeaseId id) implements Endpoint {
        @Override public EntityKind kind() { return EntityKind.SCENE_LEASE; }
        public SceneLeaseEndpoint { Objects.requireNonNull(id, "scene lease endpoint"); }
        @Override public String stableKey() { return kind() + ":" + id.value(); }
    }
    public record CarrierEvidenceEndpoint(SubjectId actorId, String carrierId) implements Endpoint {
        @Override public EntityKind kind() { return EntityKind.CARRIER_EVIDENCE; }
        public CarrierEvidenceEndpoint { Objects.requireNonNull(actorId, "carrier evidence actor"); if (carrierId == null || carrierId.isBlank()) throw new IllegalArgumentException("carrier evidence identity is invalid"); }
        @Override public String stableKey() { return kind() + ":" + actorId.value() + ":" + carrierId; }
    }
    /** Adapter observation only: adding it never affects canonical validation or selection. */
    public record CarrierEvidence(SubjectId actorId, String carrierId, String correlation) {
        public CarrierEvidence { Objects.requireNonNull(actorId, "carrier evidence actor");
            if (carrierId == null || carrierId.isBlank() || correlation == null || correlation.isBlank()) throw new IllegalArgumentException("carrier evidence is invalid"); }
    }

    public record Edge(Kind kind, Endpoint owner, Endpoint source, Endpoint target, Lifecycle lifecycle, String correlation) {
        public Edge {
            Objects.requireNonNull(kind, "relationship kind"); Objects.requireNonNull(owner, "relationship owner");
            Objects.requireNonNull(source, "relationship source"); Objects.requireNonNull(target, "relationship target");
            Objects.requireNonNull(lifecycle, "relationship lifecycle"); Objects.requireNonNull(correlation, "relationship correlation");
        }
    }
    public record Incident(Kind kind, Endpoint owner, IncidentReason reason, String expected, String observed, String disposition, long canonicalRevision, String traceCorrelation) {
        public Incident(Kind kind, Endpoint owner, IncidentReason reason, String expected, String observed, String disposition) { this(kind, owner, reason, expected, observed, disposition, -1L, "relationship:" + owner.stableKey()); }
        public Incident {
            Objects.requireNonNull(kind, "relationship incident kind"); Objects.requireNonNull(owner, "relationship incident owner");
            Objects.requireNonNull(reason, "relationship incident reason"); Objects.requireNonNull(expected, "relationship incident expected");
            Objects.requireNonNull(observed, "relationship incident observed"); Objects.requireNonNull(disposition, "relationship incident disposition"); Objects.requireNonNull(traceCorrelation, "relationship incident trace");
        }
    }

    public record View(long canonicalRevision, List<Edge> edges, List<Incident> incidents) {
        public View {
            if (canonicalRevision < -1L) throw new IllegalArgumentException("relationship view revision is invalid");
            edges = List.copyOf(edges); incidents = List.copyOf(incidents);
        }
        public List<Edge> outgoing(Endpoint endpoint) { return edges.stream().filter(edge -> edge.source().equals(endpoint)).toList(); }
        public List<Edge> incoming(Endpoint endpoint) { return edges.stream().filter(edge -> edge.target().equals(endpoint)).toList(); }
        /** Bounded deterministic undirected causal neighbourhood, suitable only for explanation. */
        public List<Edge> causalChain(Endpoint start) {
            Objects.requireNonNull(start, "relationship chain start");
            Set<Endpoint> seen = new LinkedHashSet<>(); ArrayDeque<Endpoint> todo = new ArrayDeque<>(); List<Edge> result = new ArrayList<>();
            seen.add(start); todo.add(start);
            while (!todo.isEmpty() && result.size() < 128) {
                Endpoint current = todo.removeFirst();
                for (Edge edge : edges) if (edge.source().equals(current) || edge.target().equals(current)) {
                    if (!result.contains(edge)) result.add(edge);
                    Endpoint next = edge.source().equals(current) ? edge.target() : edge.source();
                    if (seen.add(next)) todo.addLast(next);
                }
            }
            return List.copyOf(result);
        }
    }

    public static View view(FrontierWorldState state) { return view(state, -1L); }
    public static View view(FrontierWorldState state, long canonicalRevision) {
        Objects.requireNonNull(state, "relationship state");
        List<Edge> edges = new ArrayList<>(); List<Incident> incidents = new ArrayList<>();
        addStrategic(state, edges, incidents); addMarketAndProduction(state, edges, incidents); addHarvest(state, edges, incidents); addProvision(state, edges, incidents);
        addLeases(state, edges);
        edges.sort(Comparator.comparing((Edge edge) -> edge.kind().tag()).thenComparing(edge -> edge.source().stableKey()).thenComparing(edge -> edge.target().stableKey()));
        incidents.sort(Comparator.comparing((Incident incident) -> incident.kind().tag()).thenComparing(incident -> incident.owner().stableKey()));
        List<Incident> revisioned = incidents.stream().map(incident -> atRevision(incident, canonicalRevision)).toList();
        return new View(canonicalRevision, edges, revisioned);
    }
    public static View withCarrierEvidence(View canonical, List<CarrierEvidence> evidence) {
        Objects.requireNonNull(canonical, "canonical relationship view"); Objects.requireNonNull(evidence, "carrier evidence");
        List<Edge> edges = new ArrayList<>(canonical.edges());
        for (CarrierEvidence carrier : evidence) edge(edges, Kind.ACTOR_CARRIER_EVIDENCE, subject(EntityKind.RESIDENT, carrier.actorId()),
                subject(EntityKind.RESIDENT, carrier.actorId()), new CarrierEvidenceEndpoint(carrier.actorId(), carrier.carrierId()), Lifecycle.OBSERVED, carrier.correlation());
        edges.sort(Comparator.comparing((Edge edge) -> edge.kind().tag()).thenComparing(edge -> edge.source().stableKey()).thenComparing(edge -> edge.target().stableKey()));
        return new View(canonical.canonicalRevision(), edges, canonical.incidents());
    }

    /** Full-state/recovery audit.  A derived view never changes this result. */
    public static void validate(FrontierWorldState state) {
        View view = view(state);
        Optional<Incident> blocking = view.incidents().stream().filter(incident -> incident.traceCorrelation().startsWith("relationship:")).findFirst();
        if (blocking.isPresent()) {
            Incident first = blocking.orElseThrow();
            throw new IllegalArgumentException("domain relationship " + first.kind().tag() + " " + first.reason() + ": " + first.expected() + " / " + first.observed());
        }
    }

    private static void addStrategic(FrontierWorldState state, List<Edge> edges, List<Incident> incidents) {
        for (StrategicTask task : state.strategicPlans().tasks().values()) {
            Endpoint taskEndpoint = subject(EntityKind.TASK, task.id());
            StrategicObjective objective = state.strategicPlans().objectives().get(task.objectiveId());
            if (objective == null) incident(incidents, Kind.OBJECTIVE_TASK, taskEndpoint, IncidentReason.MISSING_ENDPOINT, "retained objective", task.objectiveId().value());
            else edge(edges, Kind.OBJECTIVE_TASK, subject(EntityKind.OBJECTIVE, objective.id()), subject(EntityKind.OBJECTIVE, objective.id()), taskEndpoint, lifecycle(task.status()), task.id().value());
            task.resourceSiteTarget().ifPresent(site -> edge(edges, Kind.TASK_RESOURCE_SITE, taskEndpoint, taskEndpoint, subject(EntityKind.RESOURCE_SITE, site), lifecycle(task.status()), task.id().value()));
            for (SubjectId predecessor : task.dependencies()) {
                if (!state.strategicPlans().tasks().containsKey(predecessor)) incident(incidents, Kind.TASK_PREDECESSOR, taskEndpoint, IncidentReason.MISSING_ENDPOINT, "retained predecessor task", predecessor.value());
                else edge(edges, Kind.TASK_PREDECESSOR, taskEndpoint, taskEndpoint, subject(EntityKind.TASK, predecessor), lifecycle(task.status()), task.id().value());
            }
        }
    }

    private static void addMarketAndProduction(FrontierWorldState state, List<Edge> edges, List<Incident> incidents) {
        Map<SubjectId, MarketWorkOrder> acceptedByJob = new LinkedHashMap<>();
        for (MarketWorkOrder order : state.companies().market().workOrders().values()) {
            Endpoint owner = subject(EntityKind.MARKET_ORDER, order.id()); Lifecycle lifecycle = order.status() == MarketWorkOrderStatus.ACCEPTED ? Lifecycle.ACTIVE : Lifecycle.TERMINAL_RETAINED;
            order.relationshipIncident().ifPresent(value -> incidents.add(new Incident(value.kind(), owner, value.reason(), value.expected(), value.observed(), value.disposition().name(), value.canonicalRevision(), value.traceCorrelation())));
            MarketDemand demand = state.companies().market().demands().get(order.demandId()); CompanyQuote quote = state.companies().market().quotes().get(order.quoteId());
            if (demand == null) incident(incidents, Kind.ORDER_DEMAND, owner, IncidentReason.MISSING_ENDPOINT, "retained demand", order.demandId().value());
            else edge(edges, Kind.ORDER_DEMAND, owner, owner, subject(EntityKind.MARKET_DEMAND, demand.id()), lifecycle, order.id().value());
            if (quote == null) incident(incidents, Kind.ORDER_QUOTE, owner, IncidentReason.MISSING_ENDPOINT, "retained quote", order.quoteId().value());
            else edge(edges, Kind.ORDER_QUOTE, owner, owner, subject(EntityKind.MARKET_QUOTE, quote.id()), lifecycle, order.id().value());
            if (!state.strategicPlans().tasks().containsKey(order.taskId())) {
                if (order.status() == MarketWorkOrderStatus.ACCEPTED) {
                    incident(incidents, Kind.ORDER_TASK, owner, IncidentReason.MISSING_ENDPOINT, "active retained task", order.taskId().value());
                } else {
                    // A terminal order retains its accepted task identity even after bounded
                    // strategic-plan compaction.  It is explicit historical absence, never
                    // permission to infer a replacement task from kind or settlement.
                    edge(edges, Kind.ORDER_TASK, owner, owner, subject(EntityKind.TASK, order.taskId()),
                            Lifecycle.TERMINAL_COMPACTED, order.id().value());
                }
            } else edge(edges, Kind.ORDER_TASK, owner, owner, subject(EntityKind.TASK, order.taskId()), lifecycle, order.id().value());
            if (!state.inventory().economics().reservations().containsKey(order.reservationId())
                    && order.status() == MarketWorkOrderStatus.ACCEPTED) {
                incident(incidents, Kind.ORDER_RESERVATION, owner, IncidentReason.MISSING_ENDPOINT,
                        "active reservation", order.reservationId().value());
            }
            else edge(edges, Kind.ORDER_RESERVATION, owner, owner, subject(EntityKind.FINANCIAL_RESERVATION, order.reservationId()), lifecycle, order.id().value());
            if (order.status() == MarketWorkOrderStatus.ACCEPTED && acceptedByJob.put(order.jobId(), order) != null) incident(incidents, Kind.ORDER_JOB, owner, IncidentReason.DUPLICATE_ENDPOINT, "one accepted order per job", order.jobId().value());
            ProductionJob job = state.productionJobs().get(order.jobId());
            if (job == null && order.status() == MarketWorkOrderStatus.ACCEPTED) incident(incidents, Kind.ORDER_JOB, owner, IncidentReason.MISSING_ENDPOINT, "active production job", order.jobId().value());
            else edge(edges, Kind.ORDER_JOB, owner, owner, subject(EntityKind.PRODUCTION_JOB, order.jobId()), lifecycle, order.id().value());
            order.terminalReceipt().ifPresent(receipt -> {
                Endpoint terminalJob = subject(EntityKind.PRODUCTION_JOB, receipt.jobId());
                edge(edges, Kind.JOB_WORKER, terminalJob, terminalJob, subject(EntityKind.RESIDENT, receipt.workerId()), Lifecycle.TERMINAL_RETAINED, order.id().value());
                EntityKind inputKind = receipt.inputRepresentation() == TerminalProductionReceipt.ResourceRepresentation.RESOURCE_LOT
                        ? EntityKind.RESOURCE_LOT : EntityKind.EXACT_ITEM;
                EntityKind outputKind = receipt.outputRepresentation() == TerminalProductionReceipt.ResourceRepresentation.RESOURCE_LOT
                        ? EntityKind.RESOURCE_LOT : EntityKind.EXACT_ITEM;
                edge(edges, Kind.JOB_INPUT, terminalJob, terminalJob, subject(inputKind, receipt.inputId()), Lifecycle.TERMINAL_RETAINED,
                        order.id().value());
                edge(edges, Kind.JOB_OUTPUT, terminalJob, terminalJob, subject(outputKind, receipt.outputId()), Lifecycle.TERMINAL_RETAINED,
                        order.id().value());
            });
        }
        for (ProductionJob job : state.productionJobs().values()) {
            Endpoint owner = subject(EntityKind.PRODUCTION_JOB, job.id());
            if (state.humanPopulation().resident(job.workerId()) == null) incident(incidents, Kind.JOB_WORKER, owner, IncidentReason.MISSING_ENDPOINT, "canonical resident worker", job.workerId().value());
            else edge(edges, Kind.JOB_WORKER, owner, owner, subject(EntityKind.RESIDENT, job.workerId()), Lifecycle.ACTIVE, job.id().value());
            edge(edges, Kind.JOB_INPUT, owner, owner, subject(job.inputHold() instanceof ProductionInputHold.FungibleCold ? EntityKind.RESOURCE_LOT : EntityKind.EXACT_ITEM, job.consumedItemId()), Lifecycle.ACTIVE, job.id().value());
            edge(edges, Kind.JOB_OUTPUT, owner, owner, subject(job.inputHold() instanceof ProductionInputHold.FungibleCold ? EntityKind.RESOURCE_LOT : EntityKind.EXACT_ITEM, job.outputItemId()), Lifecycle.ACTIVE, job.id().value());
        }
    }

    private static void addHarvest(FrontierWorldState state, List<Edge> edges, List<Incident> incidents) {
        for (ResourceSiteLifecycle lifecycle : state.resourceSites().sites().values()) {
            Endpoint site = subject(EntityKind.RESOURCE_SITE, lifecycle.siteId());
            lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast).ifPresent(job -> {
                Endpoint jobEndpoint = subject(EntityKind.RESOURCE_HARVEST_JOB, job.id());
                edge(edges, Kind.HARVEST_SITE, site, site, jobEndpoint, Lifecycle.ACTIVE, job.id().value());
                edge(edges, Kind.HARVEST_TASK, jobEndpoint, jobEndpoint, subject(EntityKind.TASK, job.taskId()), Lifecycle.ACTIVE, job.id().value());
                edge(edges, Kind.HARVEST_WORKER, jobEndpoint, jobEndpoint, subject(EntityKind.RESIDENT, job.workerId()), Lifecycle.ACTIVE, job.id().value());
                edge(edges, Kind.HARVEST_OUTPUT, jobEndpoint, jobEndpoint, subject(EntityKind.EXACT_ITEM, job.outputItemId()), Lifecycle.ACTIVE, job.id().value());
            });
            lifecycle.harvestLineage().ifPresent(lineage -> {
                Endpoint predecessor = subject(EntityKind.RESOURCE_HARVEST_JOB, lineage.predecessorJobId());
                edge(edges, Kind.HARVEST_PREDECESSOR, site, site, predecessor, Lifecycle.TERMINAL_RETAINED, lineage.predecessorJobId().value());
                lineage.successorTaskId().ifPresent(task -> {
                    if (!state.strategicPlans().tasks().containsKey(task)) incident(incidents, Kind.HARVEST_SUCCESSOR_TASK, site, IncidentReason.MISSING_ENDPOINT, "retained successor task", task.value());
                    else edge(edges, Kind.HARVEST_SUCCESSOR_TASK, site, predecessor, subject(EntityKind.TASK, task), Lifecycle.ACTIVE, lineage.predecessorJobId().value());
                });
                lineage.successorJobId().ifPresent(job -> {
                    ResourceSiteHarvestJob active = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast).orElse(null);
                    if (active == null || !active.id().equals(job)) incident(incidents, Kind.HARVEST_SUCCESSOR_JOB, site, IncidentReason.STALE_RELATION, "active retained successor harvest", job.value());
                    else edge(edges, Kind.HARVEST_SUCCESSOR_JOB, site, predecessor, subject(EntityKind.RESOURCE_HARVEST_JOB, job), Lifecycle.ACTIVE, lineage.predecessorJobId().value());
                });
                if (state.humanPopulation().resident(lineage.workerId()) == null) incident(incidents, Kind.HARVEST_PREDECESSOR, site, IncidentReason.MISSING_ENDPOINT, "retained predecessor farmer", lineage.workerId().value());
            });
        }
    }

    private static void addProvision(FrontierWorldState state, List<Edge> edges, List<Incident> incidents) {
        for (SettlementProvision provision : state.humanPopulation().provisions().values()) {
            Endpoint cycle = subject(EntityKind.PROVISION_CYCLE, provision.settlementId());
            for (int ordinal = 0; ordinal < provision.allocations().size(); ordinal++) {
                SettlementRationAllocation allocation = provision.allocations().get(ordinal); Endpoint allocationEndpoint = new ProvisionAllocationEndpoint(provision.settlementId(), provision.cycleOrdinal(), ordinal);
                edge(edges, Kind.PROVISION_ALLOCATION, cycle, cycle, allocationEndpoint, provision.status() == SettlementProvisionStatus.IN_PROGRESS ? Lifecycle.ACTIVE : Lifecycle.TERMINAL_RETAINED, provision.settlementId().value());
                edge(edges, Kind.ALLOCATION_RESOURCE, allocationEndpoint, allocationEndpoint, subject(allocation.fungible() ? EntityKind.RESOURCE_LOT : EntityKind.EXACT_ITEM, allocation.itemId()), Lifecycle.ACTIVE, provision.settlementId().value());
                for (SubjectId recipient : allocation.recipientIds()) {
                    if (state.humanPopulation().resident(recipient) == null) incident(incidents, Kind.ALLOCATION_RECIPIENT, allocationEndpoint, IncidentReason.MISSING_ENDPOINT, "canonical provision recipient", recipient.value());
                    else edge(edges, Kind.ALLOCATION_RECIPIENT, allocationEndpoint, allocationEndpoint, subject(EntityKind.RESIDENT, recipient), Lifecycle.ACTIVE, provision.settlementId().value());
                }
            }
        }
    }

    private static void addLeases(FrontierWorldState state, List<Edge> edges) {
        for (SceneLease lease : state.sceneLeases().values()) if (FrontierSceneBehaviors.isProductionWork(lease)) {
            SubjectId jobId = FrontierSceneBehaviors.productionWork(lease).jobId(); Endpoint job = subject(EntityKind.PRODUCTION_JOB, jobId);
            edge(edges, Kind.JOB_SCENE_LEASE, job, job, new SceneLeaseEndpoint(lease.id()), lease.status() == SceneLeaseStatus.CLOSED ? Lifecycle.TERMINAL_RETAINED : Lifecycle.ACTIVE, jobId.value());
        }
        for (AmbientActorLease lease : state.ambientLeases().values()) edge(edges, Kind.ACTOR_AMBIENT_LEASE,
                subject(EntityKind.RESIDENT, lease.actorId()), subject(EntityKind.RESIDENT, lease.actorId()), subject(EntityKind.AMBIENT_LEASE, lease.actorId()), Lifecycle.OBSERVED, lease.actorId().value());
    }

    private static SubjectEndpoint subject(EntityKind kind, SubjectId id) { return new SubjectEndpoint(kind, id); }
    private static Incident atRevision(Incident incident, long revision) { return new Incident(incident.kind(), incident.owner(), incident.reason(),
            incident.expected(), incident.observed(), incident.disposition(), revision, incident.traceCorrelation()); }
    private static Lifecycle lifecycle(StrategicTaskStatus status) { return status == StrategicTaskStatus.PENDING || status == StrategicTaskStatus.ACTIVE ? Lifecycle.ACTIVE : Lifecycle.TERMINAL_RETAINED; }
    private static void edge(List<Edge> edges, Kind kind, Endpoint owner, Endpoint source, Endpoint target, Lifecycle lifecycle, String correlation) {
        Edge edge = new Edge(kind, owner, source, target, lifecycle, correlation); declaration(kind).validate(edge); edges.add(edge); }
    private static void incident(List<Incident> incidents, Kind kind, Endpoint owner, IncidentReason reason, String expected, String observed) { incidents.add(new Incident(kind, owner, reason, expected, observed, "FAIL_CLOSED_LOCAL")); }
}
