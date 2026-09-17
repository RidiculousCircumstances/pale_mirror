package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.api.FoundryAuditPhase;
import io.farfrontier.palemirror.api.FoundryAuditReport;
import io.farfrontier.palemirror.api.FoundryFinding;
import io.farfrontier.palemirror.api.FoundryMetric;
import io.farfrontier.palemirror.api.FoundrySeverity;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierInfectionOverlayPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.HiveOrgan;
import io.farfrontier.palemirror.frontier.v3.model.HiveOrganSupportPlan;
import io.farfrontier.palemirror.frontier.v3.model.InfectionCell;
import io.farfrontier.palemirror.frontier.v3.model.InfectionOverlayCell;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only Foundry audit for one exact hive organ and its terrain-provider-owned hiveroot.
 *
 * <p>The organ's support is not an inferred terrain detail: {@link HiveOrganSupportPlan} is the
 * immutable compiler used by bootstrap, projection and physical-loss accounting.  Runtime
 * inspection therefore reads only the named organ's already loaded claimed cells.  It never
 * projects roots, loads columns, repairs a loss or treats a matching unclaimed block as truth.</p>
 */
final class FrontierV3HiveFoundryAudit {
    static final String REGION_ID = "frontier-v3";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_ORGAN_CELLS = 512;
    private static final int MAX_FINDINGS = 128;

    enum RuntimeCellStatus { CURRENT, PENDING, MISMATCH }
    /** One read-only whole-organ gate for first-visible labels and cocoon release. */
    enum RuntimeCoherence { CURRENT, PENDING, CONFLICT }
    enum ObservedCell { AIR, EXPECTED, OTHER }

    private FrontierV3HiveFoundryAudit() { }

    /** Minecraft-free immutable-plan gate for exactly one stable organ owner. */
    static FoundryAuditReport auditCompiled(FrontierWorldState state, SubjectId organId) {
        return audit(state, organId, null, FoundryAuditPhase.COMPILED);
    }

    /** SETTLED/RELOADED inspect only naturally resident chunks in the named organ's bounded scope. */
    static FoundryAuditReport audit(FrontierWorldState state, SubjectId organId, ServerLevel level, FoundryAuditPhase phase) {
        Objects.requireNonNull(state, "hive Foundry state");
        Objects.requireNonNull(organId, "hive Foundry organ id");
        Objects.requireNonNull(phase, "hive Foundry phase");
        if ((phase == FoundryAuditPhase.SETTLED || phase == FoundryAuditPhase.RELOADED) && level == null) {
            throw new IllegalArgumentException("runtime hive Foundry phase needs its already-loaded level");
        }
        HiveOrgan organ = organ(state, organId);
        FrontierGrayboxPlan baseline = FrontierGrayboxPlan.compileStructuralBaseline(state);
        Map<BlockPosition, GrayboxCell> expected = ownedCells(baseline, organId);
        Set<BlockPosition> roots = HiveOrganSupportPlan.foundationCells(state.bootstrap().terrain(), organ);
        List<FoundryFinding> findings = new ArrayList<>();
        int invalidRoots = 0;
        for (BlockPosition root : roots) {
            GrayboxCell cell = expected.get(root);
            if (cell != null && cell.semanticPart() == GrayboxSemanticPart.FOUNDATION) continue;
            invalidRoots++;
            add(findings, "frontier.hive.hiveroot.compiled", FoundrySeverity.BLOCKER, phase, organId, level, root,
                    "Immutable organ support is absent or does not retain FOUNDATION provenance.",
                    "Correct the shared HiveOrganSupportPlan/compiler; never add player scaffolding or a runtime terrain fallback.");
        }
        int missingTissue = 0;
        for (GrayboxCell cell : expected.values()) {
            if (cell.semanticPart() == GrayboxSemanticPart.FOUNDATION || cell.semanticPart() == GrayboxSemanticPart.HIVE_TISSUE) continue;
            if (cell.semanticPart() == GrayboxSemanticPart.COCOON && cell.material() == GrayboxMaterial.HIVE_COCOON) continue;
            missingTissue++;
            add(findings, "frontier.hive.organ.compiled", FoundrySeverity.BLOCKER, phase, organId, level, cell.position(),
                    "Organ projection contains a non-hive semantic part.",
                    "Keep each organ and its provider-owned hiveroot in the one immutable organ grammar.");
        }
        List<FoundryMetric> metrics = new ArrayList<>();
        metrics.add(new FoundryMetric("frontier.hive.organ.cells", expected.size(), "cells"));
        metrics.add(new FoundryMetric("frontier.hive.hiveroot.cells", roots.size(), "cells"));
        metrics.add(new FoundryMetric("frontier.hive.hiveroot.invalid", invalidRoots, "cells"));
        metrics.add(new FoundryMetric("frontier.hive.organ.invalid_parts", missingTissue, "cells"));
        if (phase == FoundryAuditPhase.SETTLED || phase == FoundryAuditPhase.RELOADED) {
            inspectRuntime(expected, organId, level, phase, findings, metrics);
        }
        findings.sort(Comparator.comparing(FoundryFinding::severity).reversed().thenComparing(FoundryFinding::ruleId)
                .thenComparing(finding -> finding.position().x()).thenComparing(finding -> finding.position().y())
                .thenComparing(finding -> finding.position().z()));
        return new FoundryAuditReport(FORMAT_VERSION, REGION_ID, state.bootstrap().canonicalSha256(), phase, findings, metrics, List.of());
    }

    static RuntimeCellStatus classify(GrayboxCell expected, FrontierV3GrayboxLedger.Claim claim, ObservedCell observed) {
        Objects.requireNonNull(expected, "expected organ cell");
        Objects.requireNonNull(observed, "observed organ cell");
        if (claim == null && observed == ObservedCell.AIR) return RuntimeCellStatus.PENDING;
        return claim != null && !claim.conflicted() && claim.owner().equals(expected.ownerId().value())
                && claim.material().equals(expected.material().name()) && claim.semanticPart().equals(expected.semanticPart().name())
                && observed == ObservedCell.EXPECTED ? RuntimeCellStatus.CURRENT : RuntimeCellStatus.MISMATCH;
    }

    /**
     * Reports whether every exact current organ cell is both naturally resident and proven by
     * the structural provenance ledger.  This is an observation gate only: it never loads a
     * chunk, projects a cell, or repairs a foreign block.  A player-facing organ label must
     * wait for CURRENT rather than advertising an operational object over an incomplete shell.
     */
    static RuntimeCoherence runtimeCoherence(FrontierWorldState state, SubjectId organId, ServerLevel level,
                                             FrontierGrayboxPlan structuralBaseline) {
        Objects.requireNonNull(state, "hive coherence state"); Objects.requireNonNull(organId, "hive coherence organ");
        Objects.requireNonNull(level, "hive coherence level"); Objects.requireNonNull(structuralBaseline, "published structural baseline");
        Map<BlockPosition, GrayboxCell> expected = ownedCells(structuralBaseline, organId);
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        boolean pending = false;
        for (GrayboxCell cell : expected.values()) {
            BlockPos position = minecraft(cell.position());
            if (!level.hasChunkAt(position)) { pending = true; continue; }
            RuntimeCellStatus status = classify(cell, ledger.claim(position), observed(cell, level.getBlockState(position)));
            if (status == RuntimeCellStatus.MISMATCH) return RuntimeCoherence.CONFLICT;
            if (status == RuntimeCellStatus.PENDING) pending = true;
        }
        if (pending) return RuntimeCoherence.PENDING;
        // This direct organ-audit entry point is an explicit inspection operation, not the
        // board/cocoon runtime path.  Runtime presentation uses the published overlay below.
        return infectionCoherence(expected.keySet(), FrontierInfectionOverlayPlan.compile(state).cells(), level);
    }

    /**
     * A seed nest is the first-visible hive unit.  Do not let the label or a cocoon in one organ
     * advertise an operational nest while a sibling organ/foundation or its infection surface
     * is still pending or foreign.  This remains a bounded read of declared same-nest owners;
     * it does not cause the other organ columns to load.
     */
    static HiveExpectations expectations(FrontierWorldState state, FrontierGrayboxPlan structuralBaseline) {
        Map<SubjectId, List<GrayboxCell>> cells = structuralBaseline.cells().values().stream()
                .collect(java.util.stream.Collectors.groupingBy(GrayboxCell::ownerId, java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toUnmodifiableList()));
        Map<SubjectId, List<SubjectId>> nestMembers = new java.util.LinkedHashMap<>();
        java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream())
                .collect(java.util.stream.Collectors.groupingBy(HiveOrgan::nestId, java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(HiveOrgan::id, java.util.stream.Collectors.toList())))
                .values().forEach(members -> {
                    List<SubjectId> immutable = members.stream().sorted().toList();
                    immutable.forEach(member -> nestMembers.put(member, immutable));
                });
        return new HiveExpectations(Map.copyOf(cells), Map.copyOf(nestMembers));
    }

    /**
     * Extends an already-published immutable hive expectation index with the current small
     * growth overlay.  A newly added organ must be visible to the same nest-coherence gate,
     * but that does not justify grouping the complete settlement/route baseline again on the
     * server thread.
     */
    static HiveExpectations withDynamicOverlay(FrontierWorldState state, HiveExpectations baseline,
                                               FrontierGrayboxPlan dynamicOverlay) {
        Objects.requireNonNull(state, "hive expectation state");
        Objects.requireNonNull(baseline, "hive expectation baseline");
        Objects.requireNonNull(dynamicOverlay, "dynamic hive overlay");
        if (dynamicOverlay.cells().isEmpty()) return baseline;
        Map<SubjectId, List<GrayboxCell>> cells = new java.util.LinkedHashMap<>(baseline.cells());
        dynamicOverlay.cells().values().stream().collect(java.util.stream.Collectors.groupingBy(GrayboxCell::ownerId,
                java.util.LinkedHashMap::new, java.util.stream.Collectors.toUnmodifiableList())).forEach((owner, overlayCells) -> {
            List<GrayboxCell> retained = cells.get(owner);
            if (retained == null) {
                cells.put(owner, overlayCells);
                return;
            }
            List<GrayboxCell> combined = new ArrayList<>(retained.size() + overlayCells.size());
            combined.addAll(retained); combined.addAll(overlayCells);
            cells.put(owner, List.copyOf(combined));
        });
        Map<SubjectId, List<SubjectId>> nestMembers = new java.util.LinkedHashMap<>(baseline.nestMembers());
        state.hiveColony().addedOrgans().values().stream().map(HiveOrgan::nestId).collect(java.util.stream.Collectors.toSet())
                .forEach(nestId -> {
                    List<SubjectId> members = java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(),
                                    state.hiveColony().addedOrgans().values().stream())
                            .filter(organ -> organ.nestId().equals(nestId)).map(HiveOrgan::id).sorted().toList();
                    members.forEach(member -> nestMembers.put(member, members));
                });
        return new HiveExpectations(Map.copyOf(cells), Map.copyOf(nestMembers));
    }

    /**
     * Bounded first-visible nest check from immutable snapshots published by the two owning
     * projectors.  It deliberately does no world-plan compilation and no owner enumeration.
     */
    static RuntimeCoherence runtimeNestCoherence(SubjectId organId, ServerLevel level, HiveExpectations expectations,
                                                 Map<InfectionCell, InfectionOverlayCell> overlay) {
        List<SubjectId> members = expectations.nestMembers().get(organId);
        if (members == null) return RuntimeCoherence.CONFLICT;
        boolean pending = false;
        for (SubjectId candidate : members) {
            RuntimeCoherence coherence = runtimeCoherence(candidate, level, expectations.cells().get(candidate), overlay);
            if (coherence == RuntimeCoherence.CONFLICT) return RuntimeCoherence.CONFLICT;
            if (coherence == RuntimeCoherence.PENDING) pending = true;
        }
        return pending ? RuntimeCoherence.PENDING : RuntimeCoherence.CURRENT;
    }

    /**
     * An infected hive never advertises an active organ while its canonical whole-patch surface
     * is merely queued, partially written, foreign, or stale.  The overlay claim is deliberately
     * checked as a sixteen-column unit: a matching carpet under a single column is not evidence
     * for a coherent infestation.
     */
    private static RuntimeCoherence runtimeCoherence(SubjectId organId, ServerLevel level, List<GrayboxCell> expected,
                                                     Map<InfectionCell, InfectionOverlayCell> desired) {
        if (expected == null || expected.isEmpty()) return RuntimeCoherence.CONFLICT;
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        boolean pending = false;
        for (GrayboxCell cell : expected) {
            BlockPos position = minecraft(cell.position());
            if (!level.hasChunkAt(position)) { pending = true; continue; }
            RuntimeCellStatus status = classify(cell, ledger.claim(position), observed(cell, level.getBlockState(position)));
            if (status == RuntimeCellStatus.MISMATCH) return RuntimeCoherence.CONFLICT;
            if (status == RuntimeCellStatus.PENDING) pending = true;
        }
        return pending ? RuntimeCoherence.PENDING : infectionCoherence(expected.stream().map(GrayboxCell::position).collect(java.util.stream.Collectors.toUnmodifiableSet()), desired, level);
    }

    private static RuntimeCoherence infectionCoherence(Set<BlockPosition> organCells, Map<InfectionCell, InfectionOverlayCell> desired, ServerLevel level) {
        Set<InfectionCell> organInfection = organCells.stream().map(InfectionCell::at)
                .filter(desired::containsKey).collect(java.util.stream.Collectors.toSet());
        FrontierV3InfectionOverlayLedger ledger = FrontierV3InfectionOverlayLedger.get(level);
        boolean pending = false;
        for (InfectionCell cell : organInfection) {
            InfectionOverlayCell overlay = desired.get(cell);
            FrontierV3InfectionOverlayLedger.Claim claim = ledger.claim(cell);
            if (claim == null || claim.prepared()) { pending = true; continue; }
            if (!claim.active() || claim.stage() != overlay.stage()) return RuntimeCoherence.CONFLICT;
            for (BlockPos position : claim.blockPositions()) {
                if (!level.hasChunkAt(position)) { pending = true; continue; }
                if (!level.getBlockState(position).equals(FrontierV3InfectionOverlayExecutor.material(overlay.stage()))) {
                    return RuntimeCoherence.CONFLICT;
                }
            }
        }
        return pending ? RuntimeCoherence.PENDING : RuntimeCoherence.CURRENT;
    }

    record HiveExpectations(Map<SubjectId, List<GrayboxCell>> cells, Map<SubjectId, List<SubjectId>> nestMembers) { }

    private static void inspectRuntime(Map<BlockPosition, GrayboxCell> expected, SubjectId organId, ServerLevel level,
                                       FoundryAuditPhase phase, List<FoundryFinding> findings, List<FoundryMetric> metrics) {
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        int current = 0, pending = 0, mismatch = 0, unverified = 0;
        for (GrayboxCell cell : expected.values()) {
            BlockPos position = minecraft(cell.position());
            if (!level.hasChunkAt(position)) {
                unverified++;
                continue;
            }
            RuntimeCellStatus status = classify(cell, ledger.claim(position), observed(cell, level.getBlockState(position)));
            switch (status) {
                case CURRENT -> current++;
                case PENDING -> {
                    pending++;
                    add(findings, "frontier.hive.organ.pending", FoundrySeverity.WARNING, phase, organId, level, cell.position(),
                            "Loaded organ cell is fresh air with no provenance claim; ordinary projection has not reached it yet.",
                            "Wait for normal loaded-chunk materialization; Foundry will not create the cell.");
                }
                case MISMATCH -> {
                    mismatch++;
                    add(findings, "frontier.hive.organ.runtime", FoundrySeverity.ERROR, phase, organId, level, cell.position(),
                            "Loaded organ cell differs from its immutable plan or exact provenance claim.",
                            "Treat it as physical drift/conflict and reconcile through normal causal observation; Foundry will not overwrite it.");
                }
            }
        }
        if (unverified > 0) add(findings, "frontier.hive.organ.unverified", FoundrySeverity.WARNING, phase, organId, level, organAnchor(expected),
                unverified + " organ cells were not loaded and were not inspected.",
                "Visit this organ and rerun SETTLED or RELOADED; Foundry never loads chunks.");
        metrics.add(new FoundryMetric("frontier.hive.organ.runtime_current", current, "cells"));
        metrics.add(new FoundryMetric("frontier.hive.organ.runtime_pending", pending, "cells"));
        metrics.add(new FoundryMetric("frontier.hive.organ.runtime_mismatch", mismatch, "cells"));
        metrics.add(new FoundryMetric("frontier.hive.organ.runtime_unverified", unverified, "cells"));
    }

    private static HiveOrgan organ(FrontierWorldState state, SubjectId organId) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream())
                .filter(candidate -> candidate.id().equals(organId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown hive organ Foundry scope: " + organId.value()));
    }

    private static Map<BlockPosition, GrayboxCell> ownedCells(FrontierGrayboxPlan plan, SubjectId organId) {
        Map<BlockPosition, GrayboxCell> cells = new java.util.LinkedHashMap<>();
        plan.cells().values().stream().filter(cell -> cell.ownerId().equals(organId))
                .sorted(Comparator.comparing((GrayboxCell cell) -> cell.position().x()).thenComparing(cell -> cell.position().y()).thenComparing(cell -> cell.position().z()))
                .forEach(cell -> cells.put(cell.position(), cell));
        if (cells.isEmpty() || cells.size() > MAX_ORGAN_CELLS) throw new IllegalArgumentException("hive organ Foundry scope is invalid");
        return Map.copyOf(cells);
    }

    private static ObservedCell observed(GrayboxCell expected, net.minecraft.world.level.block.state.BlockState actual) {
        if (actual.isAir()) return ObservedCell.AIR;
        return actual.equals(FrontierV3GrayboxExecutor.material(expected.material())) ? ObservedCell.EXPECTED : ObservedCell.OTHER;
    }

    private static BlockPosition organAnchor(Map<BlockPosition, GrayboxCell> expected) {
        return expected.keySet().stream()
                .min(Comparator.comparing(BlockPosition::x).thenComparing(BlockPosition::y).thenComparing(BlockPosition::z))
                .orElseThrow();
    }

    private static void add(List<FoundryFinding> findings, String rule, FoundrySeverity severity, FoundryAuditPhase phase,
                            SubjectId organId, ServerLevel level, BlockPosition position, String message, String remediation) {
        if (findings.size() >= MAX_FINDINGS) return;
        findings.add(new FoundryFinding(rule, severity, phase, "hive_organ", organId.value(),
                level == null ? "pale_mirror:frontier_graybox" : level.dimension().location().toString(),
                new VisualPoint(position.x(), position.y(), position.z()), message, remediation));
    }

    private static BlockPos minecraft(BlockPosition position) { return new BlockPos(position.x(), position.y(), position.z()); }
}
