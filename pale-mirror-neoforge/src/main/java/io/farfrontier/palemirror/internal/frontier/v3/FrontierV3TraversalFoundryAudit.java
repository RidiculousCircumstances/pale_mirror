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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
                                    Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> facilityScope) {
        Objects.requireNonNull(state, "frontier Foundry state"); Objects.requireNonNull(phase, "frontier Foundry phase");
        facilityScope = Objects.requireNonNull(facilityScope, "frontier Foundry facility scope");
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
            inspectRuntime(plan, graybox, level, phase, facilityScope, findings, metrics);
        }
        findings.sort(Comparator.comparing(FoundryFinding::severity).reversed().thenComparing(FoundryFinding::ruleId)
                .thenComparing(FoundryFinding::targetId).thenComparing(value -> value.position().x())
                .thenComparing(value -> value.position().y()).thenComparing(value -> value.position().z()));
        return new FoundryAuditReport(FORMAT_VERSION, REGION_ID, state.bootstrap().canonicalSha256(), phase,
                findings, metrics, List.of());
    }

    private static void inspectRuntime(FrontierTraversalPlan plan, FrontierGrayboxPlan graybox, ServerLevel level,
                                       FoundryAuditPhase phase,
                                       Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> facilityScope,
                                       List<FoundryFinding> findings, List<FoundryMetric> metrics) {
        RuntimeScope scope = runtimeScope(plan, facilityScope);
        Set<SurfaceAnchor> surfaces = scope.surfaces();
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        int checked = 0, unloaded = 0, pending = 0, mismatch = 0;
        for (SurfaceAnchor surface : surfaces) {
            if (checked + unloaded >= MAX_RUNTIME_SURFACES) break;
            BlockPos position = minecraft(surface.support());
            if (!level.hasChunkAt(position)) { unloaded++; continue; }
            checked++;
            GrayboxCell expected = graybox.cells().get(surface.support());
            var actual = level.getBlockState(position);
            FrontierV3GrayboxLedger.Claim claim = ledger.claim(position);
            RuntimeSupportStatus status = classifyRuntimeSupport(expected, claim, observedSupport(expected, actual));
            if (status == RuntimeSupportStatus.PENDING) {
                pending++;
                add(findings, "frontier.traversal.support.pending", FoundrySeverity.WARNING, phase, "traversal_surface",
                        expected.ownerId().value(), level, surface.support(),
                        "Loaded traversal support is still pending initial materialization (expected "
                                + expectedMaterial(expected) + ").",
                        "Wait for the ordinary loaded-chunk projector; Foundry will not create or repair the cell.");
                continue;
            }
            if (status == RuntimeSupportStatus.MISMATCH) {
                mismatch++;
                String expectedBlock = expected == null ? "<no-owned-cell>" : expectedMaterial(expected);
                String actualBlock = BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString();
                add(findings, "frontier.traversal.support.runtime", FoundrySeverity.ERROR, phase, "traversal_surface",
                        expected == null ? "unowned" : expected.ownerId().value(), level, surface.support(),
                        "Loaded traversal support differs from the immutable materialization plan (expected "
                                + expectedBlock + ", observed " + actualBlock + ").",
                        "Treat it as observed damage/conflict; do not repair or choose a hidden bypass.");
            }
        }
        int blockedThroats = 0;
        for (FrontierTraversalPlan.FacilityBinding binding : scope.facilities()) {
            for (BlockPosition air : binding.port().thresholdHeadroomCells()) {
                BlockPos position = minecraft(air);
                if (!level.hasChunkAt(position)) { unloaded++; continue; }
                if (level.getBlockState(position).isAir()) continue;
                blockedThroats++;
                add(findings, "frontier.port.throat.runtime", FoundrySeverity.ERROR, phase, "facility_port",
                        binding.port().facilityId().value(), level, air,
                        "Loaded facility throat lacks required two-body headroom.",
                        "Record the blocked port and reconcile its availability; Foundry will not clear the block.");
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
    }

    private static RuntimeScope runtimeScope(FrontierTraversalPlan plan,
                                             Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> facilityScope) {
        if (facilityScope.isEmpty()) {
            Set<SurfaceAnchor> surfaces = new LinkedHashSet<>();
            plan.topologies().values().forEach(topology -> surfaces.addAll(topology.nodes().values()));
            return new RuntimeScope(surfaces, List.copyOf(plan.facilities().values()));
        }
        FrontierTraversalPlan.FacilityBinding binding = plan.facilities().get(facilityScope.orElseThrow());
        if (binding == null) throw new IllegalArgumentException("frontier Foundry has no facility scope: " + facilityScope.orElseThrow());
        Set<SurfaceAnchor> surfaces = new LinkedHashSet<>(plan.publicTopologyFor(binding.port().facilityId()).nodes().values());
        surfaces.addAll(binding.port().ingressSurfaces());
        return new RuntimeScope(surfaces, List.of(binding));
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

    private static ObservedSupport observedSupport(GrayboxCell expected, net.minecraft.world.level.block.state.BlockState actual) {
        if (actual.isAir()) return ObservedSupport.AIR;
        return expected != null && actual.equals(FrontierV3GrayboxExecutor.material(expected.material()))
                ? ObservedSupport.EXPECTED : ObservedSupport.OTHER;
    }

    private static String expectedMaterial(GrayboxCell expected) {
        return BuiltInRegistries.BLOCK.getKey(FrontierV3GrayboxExecutor.material(expected.material()).getBlock()).toString();
    }

    private record RuntimeScope(Set<SurfaceAnchor> surfaces, List<FrontierTraversalPlan.FacilityBinding> facilities) {
        RuntimeScope {
            // The bounded global pass has a cap; preserve the compiler's deterministic scan
            // order rather than letting Set.copyOf randomize which locations make that cap.
            surfaces = Collections.unmodifiableSet(new LinkedHashSet<>(surfaces));
            facilities = List.copyOf(facilities);
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
