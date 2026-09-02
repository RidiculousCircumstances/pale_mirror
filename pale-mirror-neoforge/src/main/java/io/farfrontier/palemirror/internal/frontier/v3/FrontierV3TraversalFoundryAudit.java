package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.api.FoundryAuditPhase;
import io.farfrontier.palemirror.api.FoundryAuditReport;
import io.farfrontier.palemirror.api.FoundryFinding;
import io.farfrontier.palemirror.api.FoundryMetric;
import io.farfrontier.palemirror.api.FoundrySeverity;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FacilityTraversalPort;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierTraversalPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalTopology;
import io.farfrontier.palemirror.frontier.v3.model.TraversalTopologyId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only Foundry adapter for v3's immutable facility/route traversal plan.
 *
 * <p>The compiled pass needs no Minecraft world. SETTLED and RELOADED examine only already
 * loaded cells, explicitly retaining unverified columns; neither phase opens a throat, repairs
 * a sidewalk nor changes canonical edge availability.</p>
 */
final class FrontierV3TraversalFoundryAudit {
    static final String REGION_ID = "frontier-v3";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_RUNTIME_SURFACES = 4_096;
    private static final int MAX_FINDINGS = 256;

    /** Read-only support classification; PENDING is intentionally narrower than a conflict. */
    enum RuntimeSupportStatus { CURRENT, PENDING, MISMATCH }
    /** Minecraft adapter input kept deliberately small so the causal classification is JVM-testable. */
    enum ObservedSupport { AIR, EXPECTED, OTHER }
    /** One semantic port is open only when both declared throat cells are physically clear. */
    enum RuntimePortAvailability { OPEN, BLOCKED, UNVERIFIED }
    /** One declared topology edge is read-only availability evidence, never a replan request. */
    enum RuntimeEdgeAvailability { OPEN, PENDING, BLOCKED, UNVERIFIED }
    enum RuntimeSurfaceStatus { CURRENT, PENDING, MISMATCH, UNVERIFIED }

    private FrontierV3TraversalFoundryAudit() { }

    /** Minecraft-free COMPILED gate, intentionally available to the fast JVM test source set. */
    static FoundryAuditReport auditCompiled(FrontierWorldState state) {
        return audit(state, null, FoundryAuditPhase.COMPILED);
    }

    static FoundryAuditReport audit(FrontierWorldState state, ServerLevel level, FoundryAuditPhase phase) {
        return audit(state, level, phase, Optional.empty());
    }

    /**
     * A runtime facility scope is an inspection boundary, never an alternate route or an
     * authority to load/project the named object. It keeps a naturally visited object from
     * being judged by unrelated, newly resident chunks elsewhere in the autonomous world.
     */
    static FoundryAuditReport audit(FrontierWorldState state, ServerLevel level, FoundryAuditPhase phase,
                                    Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> scopeId) {
        Objects.requireNonNull(state, "frontier Foundry state"); Objects.requireNonNull(phase, "frontier Foundry phase");
        scopeId = Objects.requireNonNull(scopeId, "frontier Foundry scope");
        if ((phase == FoundryAuditPhase.SETTLED || phase == FoundryAuditPhase.RELOADED) && level == null) {
            throw new IllegalArgumentException("runtime Foundry phase needs its already-loaded level");
        }
        FrontierTraversalPlan plan = FrontierTraversalPlan.compile(state);
        FrontierGrayboxPlan graybox = FrontierGrayboxPlan.compile(state);
        List<FoundryFinding> findings = new ArrayList<>();
        List<FoundryMetric> metrics = new ArrayList<>();
        int topologyEdges = 0, invalidEdges = 0, missingSupport = 0, disconnectedPorts = 0;
        for (TraversalTopology topology : plan.topologies().values()) {
            topologyEdges += topology.edges().size();
            for (TraversalTopology.Edge edge : topology.edges()) {
                if (edge.grade() > 1 || edge.clearance() < 2 || edge.capabilities().isEmpty()) {
                    invalidEdges++;
                    add(findings, "frontier.traversal.edge.compiled", FoundrySeverity.BLOCKER, phase, "traversal_edge",
                            topology.id().value() + "/" + edge.id().value(), level, topology.nodes().get(edge.from()).support(),
                            "Compiled traversal edge has invalid grade, clearance or capability.",
                            "Correct the immutable terrain/provider topology before publication.");
                }
            }
            for (SurfaceAnchor surface : topology.nodes().values()) {
                if (graybox.cells().containsKey(surface.support())) continue;
                missingSupport++;
                add(findings, "frontier.traversal.support.compiled", FoundrySeverity.BLOCKER, phase, "traversal_node",
                        topology.id().value(), level, surface.support(), "Traversal support has no authored graybox cell.",
                        "Compile a semantic surface/foundation or remove the impossible topology node.");
            }
        }
        for (FrontierTraversalPlan.FacilityBinding binding : plan.facilities().values()) {
            FacilityTraversalPort port = binding.port();
            if (!plan.publicTopologyFor(port.facilityId()).nodes().containsValue(port.exteriorApproach().getFirst())) {
                disconnectedPorts++;
                add(findings, "frontier.port.public_topology.compiled", FoundrySeverity.BLOCKER, phase, "facility_port",
                        port.facilityId().value(), level, port.exteriorApproach().getFirst().support(),
                        "Facility exterior approach is not in its declared public topology.",
                        "Compile an explicit port-to-public-circulation connector; never find a nearest route at runtime.");
            }
            for (BlockPosition air : port.thresholdHeadroomCells()) {
                if (graybox.cells().containsKey(air)) {
                    add(findings, "frontier.port.throat.compiled", FoundrySeverity.BLOCKER, phase, "facility_port",
                            port.facilityId().value(), level, air, "Compiled facility throat has authored solid headroom.",
                            "Leave two declared body cells clear above the semantic threshold.");
                }
            }
        }
        metrics.add(new FoundryMetric("frontier.traversal.topologies", plan.topologies().size(), "topologies"));
        metrics.add(new FoundryMetric("frontier.traversal.edges", topologyEdges, "edges"));
        metrics.add(new FoundryMetric("frontier.traversal.invalid_edges", invalidEdges, "edges"));
        metrics.add(new FoundryMetric("frontier.traversal.missing_support", missingSupport, "surfaces"));
        metrics.add(new FoundryMetric("frontier.port.count", plan.facilities().size(), "ports"));
        metrics.add(new FoundryMetric("frontier.port.disconnected", disconnectedPorts, "ports"));
        if (level != null && phase != FoundryAuditPhase.COMPILED && phase != FoundryAuditPhase.PLAN) {
            inspectRuntime(plan, graybox, level, phase, scopeId, findings, metrics);
        }
        findings.sort(Comparator.comparing(FoundryFinding::severity).reversed().thenComparing(FoundryFinding::ruleId)
                .thenComparing(FoundryFinding::targetId).thenComparing(value -> value.position().x())
                .thenComparing(value -> value.position().y()).thenComparing(value -> value.position().z()));
        return new FoundryAuditReport(FORMAT_VERSION, REGION_ID, state.bootstrap().canonicalSha256(), phase,
                findings, metrics, List.of());
    }

    private static void inspectRuntime(FrontierTraversalPlan plan, FrontierGrayboxPlan graybox, ServerLevel level,
                                       FoundryAuditPhase phase,
                                       Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> scopeId,
                                       List<FoundryFinding> findings, List<FoundryMetric> metrics) {
        RuntimeScope scope = runtimeScope(plan, scopeId);
        Set<SurfaceAnchor> surfaces = scope.surfaces();
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        int checked = 0, unloaded = 0, pending = 0, mismatch = 0;
        Map<SurfaceAnchor, RuntimeSurfaceStatus> support = new LinkedHashMap<>();
        for (SurfaceAnchor surface : surfaces) {
            if (checked + unloaded >= MAX_RUNTIME_SURFACES) break;
            BlockPos position = minecraft(surface.support());
            if (!level.hasChunkAt(position)) {
                unloaded++;
                support.put(surface, RuntimeSurfaceStatus.UNVERIFIED);
                continue;
            }
            checked++;
            GrayboxCell expected = graybox.cells().get(surface.support());
            var actual = level.getBlockState(position);
            FrontierV3GrayboxLedger.Claim claim = ledger.claim(position);
            RuntimeSupportStatus status = classifyRuntimeSupport(expected, claim, observedSupport(expected, actual));
            if (status == RuntimeSupportStatus.PENDING) {
                pending++;
                support.put(surface, RuntimeSurfaceStatus.PENDING);
                add(findings, "frontier.traversal.support.pending", FoundrySeverity.WARNING, phase, "traversal_surface",
                        expected.ownerId().value(), level, surface.support(),
                        "Loaded traversal support is still pending initial materialization (expected "
                                + expectedMaterial(expected) + ").",
                        "Wait for the ordinary loaded-chunk projector; Foundry will not create or repair the cell.");
                continue;
            }
            if (status == RuntimeSupportStatus.MISMATCH) {
                mismatch++;
                support.put(surface, RuntimeSurfaceStatus.MISMATCH);
                String expectedBlock = expected == null ? "<no-owned-cell>" : expectedMaterial(expected);
                String actualBlock = BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString();
                add(findings, "frontier.traversal.support.runtime", FoundrySeverity.ERROR, phase, "traversal_surface",
                        expected == null ? "unowned" : expected.ownerId().value(), level, surface.support(),
                        "Loaded traversal support differs from the immutable materialization plan (expected "
                                + expectedBlock + ", observed " + actualBlock + ").",
                        "Treat it as observed damage/conflict; do not repair or choose a hidden bypass.");
            } else if (status == RuntimeSupportStatus.CURRENT) {
                support.put(surface, RuntimeSurfaceStatus.CURRENT);
            }
        }
        int blockedThroats = 0, openPorts = 0, blockedPorts = 0, unverifiedPorts = 0;
        for (FrontierTraversalPlan.FacilityBinding binding : scope.facilities()) {
            List<ObservedPortHeadroom> headroom = new ArrayList<>();
            for (BlockPosition air : binding.port().thresholdHeadroomCells()) {
                BlockPos position = minecraft(air);
                if (!level.hasChunkAt(position)) {
                    unloaded++;
                    headroom.add(ObservedPortHeadroom.UNVERIFIED);
                    continue;
                }
                headroom.add(level.getBlockState(position).isAir() ? ObservedPortHeadroom.CLEAR : ObservedPortHeadroom.BLOCKED);
                if (level.getBlockState(position).isAir()) continue;
                blockedThroats++;
            }
            RuntimePortAvailability availability = classifyPortAvailability(headroom);
            switch (availability) {
                case OPEN -> openPorts++;
                case BLOCKED -> {
                    blockedPorts++;
                    BlockPosition throat = binding.port().thresholdSurface().support();
                    add(findings, "frontier.port.availability.blocked", FoundrySeverity.ERROR, phase, "facility_port",
                            binding.port().facilityId().value(), level, throat,
                            "Loaded facility port is BLOCKED: its declared two-body throat no longer has complete headroom.",
                            "Reconcile this exact semantic port as unavailable; Foundry will not clear the block or choose another entrance.");
                }
                case UNVERIFIED -> unverifiedPorts++;
            }
        }
        int openEdges = 0, pendingEdges = 0, blockedEdges = 0, unverifiedEdges = 0;
        for (TraversalTopology topology : scope.topologies()) {
            for (TraversalTopology.Edge edge : topology.edges()) {
                RuntimeEdgeAvailability availability = classifyEdgeAvailability(
                        support.getOrDefault(topology.nodes().get(edge.from()), RuntimeSurfaceStatus.UNVERIFIED),
                        support.getOrDefault(topology.nodes().get(edge.to()), RuntimeSurfaceStatus.UNVERIFIED));
                switch (availability) {
                    case OPEN -> openEdges++;
                    case PENDING -> pendingEdges++;
                    case UNVERIFIED -> unverifiedEdges++;
                    case BLOCKED -> {
                        blockedEdges++;
                        add(findings, "frontier.traversal.edge.runtime.blocked", FoundrySeverity.ERROR, phase, "traversal_edge",
                                topology.id().value() + "/" + edge.id().value(), level, topology.nodes().get(edge.from()).support(),
                                "Loaded declared traversal edge is BLOCKED by observed support drift.",
                                "Reconcile the retained edge availability; Foundry will not select or materialize a bypass.");
                    }
                }
            }
        }
        if (unloaded > 0) add(findings, "frontier.traversal.unverified", FoundrySeverity.WARNING, phase, "region", REGION_ID,
                level, new BlockPosition(0, 64, 0), unloaded + " traversal cells were not loaded and were not inspected.",
                "Visit the area and rerun SETTLED or RELOADED; Foundry never loads chunks.");
        metrics.add(new FoundryMetric("frontier.traversal.runtime_checked", checked, "surfaces"));
        metrics.add(new FoundryMetric("frontier.traversal.runtime_unverified", unloaded, "surfaces"));
        metrics.add(new FoundryMetric("frontier.traversal.runtime_pending", pending, "surfaces"));
        metrics.add(new FoundryMetric("frontier.traversal.runtime_mismatch", mismatch, "surfaces"));
        metrics.add(new FoundryMetric("frontier.port.runtime_blocked", blockedThroats, "ports"));
        metrics.add(new FoundryMetric("frontier.port.runtime_open", openPorts, "ports"));
        metrics.add(new FoundryMetric("frontier.port.runtime_blocked_ports", blockedPorts, "ports"));
        metrics.add(new FoundryMetric("frontier.port.runtime_unverified", unverifiedPorts, "ports"));
        metrics.add(new FoundryMetric("frontier.traversal.edge.runtime_open", openEdges, "edges"));
        metrics.add(new FoundryMetric("frontier.traversal.edge.runtime_pending", pendingEdges, "edges"));
        metrics.add(new FoundryMetric("frontier.traversal.edge.runtime_blocked", blockedEdges, "edges"));
        metrics.add(new FoundryMetric("frontier.traversal.edge.runtime_unverified", unverifiedEdges, "edges"));
    }

    private static RuntimeScope runtimeScope(FrontierTraversalPlan plan,
                                             Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> scopeId) {
        if (scopeId.isEmpty()) {
            Set<SurfaceAnchor> surfaces = new LinkedHashSet<>();
            plan.topologies().values().forEach(topology -> surfaces.addAll(topology.nodes().values()));
            return new RuntimeScope(surfaces, List.copyOf(plan.facilities().values()), List.copyOf(plan.topologies().values()));
        }
        io.farfrontier.palemirror.frontier.v3.api.SubjectId requested = scopeId.orElseThrow();
        FrontierTraversalPlan.FacilityBinding binding = plan.facilities().get(requested);
        if (binding == null) {
            TraversalTopology topology = plan.topologies().get(new TraversalTopologyId(requested.value()));
            if (topology == null) throw new IllegalArgumentException("frontier Foundry has no facility or topology scope: " + requested);
            return new RuntimeScope(new LinkedHashSet<>(topology.nodes().values()), List.of(), List.of(topology));
        }
        Set<SurfaceAnchor> surfaces = new LinkedHashSet<>(plan.publicTopologyFor(binding.port().facilityId()).nodes().values());
        surfaces.addAll(binding.port().ingressSurfaces());
        List<TraversalTopology> topologies = plan.topologies().values().stream()
                .filter(topology -> topology.id().equals(binding.publicTopologyId()) || topology.provenance().equals(binding.port().facilityId()))
                .toList();
        return new RuntimeScope(surfaces, List.of(binding), topologies);
    }

    private static boolean matchesOwnedExpected(FrontierV3GrayboxLedger.Claim claim, GrayboxCell expected,
                                                ObservedSupport observed) {
        return claim != null && expected != null && !claim.conflicted()
                && claim.owner().equals(expected.ownerId().value())
                && claim.material().equals(expected.material().name())
                && claim.semanticPart().equals(expected.semanticPart().name())
                && observed == ObservedSupport.EXPECTED;
    }

    static RuntimeSupportStatus classifyRuntimeSupport(GrayboxCell expected, FrontierV3GrayboxLedger.Claim claim,
                                                       ObservedSupport observed) {
        Objects.requireNonNull(observed, "observed traversal support state");
        if (expected != null && claim == null && observed == ObservedSupport.AIR) return RuntimeSupportStatus.PENDING;
        return matchesOwnedExpected(claim, expected, observed) ? RuntimeSupportStatus.CURRENT : RuntimeSupportStatus.MISMATCH;
    }

    static RuntimePortAvailability classifyPortAvailability(List<ObservedPortHeadroom> cells) {
        cells = List.copyOf(Objects.requireNonNull(cells, "port headroom observations"));
        if (cells.size() != 2) throw new IllegalArgumentException("facility port requires exactly two headroom observations");
        if (cells.contains(ObservedPortHeadroom.BLOCKED)) return RuntimePortAvailability.BLOCKED;
        return cells.contains(ObservedPortHeadroom.UNVERIFIED) ? RuntimePortAvailability.UNVERIFIED : RuntimePortAvailability.OPEN;
    }

    static RuntimeEdgeAvailability classifyEdgeAvailability(RuntimeSurfaceStatus from, RuntimeSurfaceStatus to) {
        from = Objects.requireNonNull(from, "edge from support status"); to = Objects.requireNonNull(to, "edge to support status");
        if (from == RuntimeSurfaceStatus.MISMATCH || to == RuntimeSurfaceStatus.MISMATCH) return RuntimeEdgeAvailability.BLOCKED;
        if (from == RuntimeSurfaceStatus.UNVERIFIED || to == RuntimeSurfaceStatus.UNVERIFIED) return RuntimeEdgeAvailability.UNVERIFIED;
        if (from == RuntimeSurfaceStatus.PENDING || to == RuntimeSurfaceStatus.PENDING) return RuntimeEdgeAvailability.PENDING;
        return RuntimeEdgeAvailability.OPEN;
    }

    private static ObservedSupport observedSupport(GrayboxCell expected, net.minecraft.world.level.block.state.BlockState actual) {
        if (actual.isAir()) return ObservedSupport.AIR;
        return expected != null && actual.equals(FrontierV3GrayboxExecutor.material(expected.material()))
                ? ObservedSupport.EXPECTED : ObservedSupport.OTHER;
    }

    private static String expectedMaterial(GrayboxCell expected) {
        return BuiltInRegistries.BLOCK.getKey(FrontierV3GrayboxExecutor.material(expected.material()).getBlock()).toString();
    }

    enum ObservedPortHeadroom { CLEAR, BLOCKED, UNVERIFIED }

    private record RuntimeScope(Set<SurfaceAnchor> surfaces, List<FrontierTraversalPlan.FacilityBinding> facilities,
                                List<TraversalTopology> topologies) {
        RuntimeScope {
            // The bounded global pass has a cap; preserve the compiler's deterministic scan
            // order rather than letting Set.copyOf randomize which locations make that cap.
            surfaces = Collections.unmodifiableSet(new LinkedHashSet<>(surfaces));
            facilities = List.copyOf(facilities);
            topologies = List.copyOf(topologies);
        }
    }

    private static void add(List<FoundryFinding> findings, String rule, FoundrySeverity severity, FoundryAuditPhase phase,
                            String kind, String id, ServerLevel level, BlockPosition position, String message, String remediation) {
        if (findings.size() >= MAX_FINDINGS) return;
        findings.add(new FoundryFinding(rule, severity, phase, kind, id,
                level == null ? "pale_mirror:frontier_graybox" : level.dimension().location().toString(), point(position), message, remediation));
    }

    private static BlockPos minecraft(BlockPosition position) { return new BlockPos(position.x(), position.y(), position.z()); }
    private static VisualPoint point(BlockPosition position) { return new VisualPoint(position.x(), position.y(), position.z()); }
}
