package io.farfrontier.palemirror.visuals.foundry;

import io.farfrontier.palemirror.api.FoundryAuditPhase;
import io.farfrontier.palemirror.api.FoundryAuditReport;
import io.farfrontier.palemirror.api.FoundryBlockInspection;
import io.farfrontier.palemirror.api.FoundryFinding;
import io.farfrontier.palemirror.api.FoundryMapSample;
import io.farfrontier.palemirror.api.FoundryMetric;
import io.farfrontier.palemirror.api.FoundrySeverity;
import io.farfrontier.palemirror.api.SiteSurfaceColumn;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPortKind;
import io.farfrontier.palemirror.visuals.genesis.CompiledGenesisCatalog;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Reusable rule engine for immutable plans and already-loaded materializations. */
public final class FoundryAuditEngine {
    public static final int FORMAT_VERSION = 1;
    private static final int MAX_FINDINGS_PER_RULE = 64;
    private static final int MAX_RUNTIME_FINDINGS = 256;
    private static final int MIN_FLOATING_COMPONENT = 3;
    private String cachedCatalogHash = "";
    private final Map<String, FoundryRegionIndex> indexCache = new HashMap<>();
    private final Map<String, FoundryAuditReport> compiledCache = new HashMap<>();

    /** Minecraft-free entry point used by compile gates and deterministic asset tests. */
    public FoundryAuditReport auditCompiled(CompiledGenesisCatalog catalog, String regionId) {
        return audit(catalog, regionId, null, FoundryAuditPhase.COMPILED);
    }

    public synchronized FoundryAuditReport audit(CompiledGenesisCatalog catalog, String regionId,
                                                 ServerLevel level, FoundryAuditPhase phase) {
        long started = System.nanoTime();
        var region = catalog.manifests().stream().filter(value -> value.planId().equals(regionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown authored region " + regionId));
        prepare(catalog);
        FoundryRegionIndex index = indexCache.computeIfAbsent(regionId,
                ignored -> FoundryRegionIndex.build(region, catalog));
        FoundryAuditReport baseline = compiledCache.computeIfAbsent(regionId,
                ignored -> compiledBaseline(catalog, index));
        if (level == null || phase == FoundryAuditPhase.PLAN || phase == FoundryAuditPhase.COMPILED) {
            return phase == FoundryAuditPhase.COMPILED ? baseline : rephase(baseline, phase);
        }
        List<FoundryFinding> findings = baseline.findings().stream().map(value -> new FoundryFinding(
                value.ruleId(), value.severity(), phase, value.targetKind(), value.targetId(), value.dimensionId(),
                value.position(), value.message(), value.remediation())).collect(java.util.stream.Collectors.toCollection(
                ArrayList::new));
        List<FoundryMetric> metrics = new ArrayList<>(baseline.metrics());

        RuntimeResult runtime = inspectRuntime(index, level, phase, findings);
        metrics.addAll(runtime.metrics());
        FoundrySurfaceInspector.Result surface = FoundrySurfaceInspector.inspect(index, level, phase);
        List<FoundryMapSample> samples = surface.samples();
        findings.addAll(surface.findings());
        metrics.addAll(surface.metrics());
        metrics.add(new FoundryMetric("map.columns", samples.size(), "columns"));
        metrics.add(new FoundryMetric("audit.elapsed", (System.nanoTime() - started) / 1_000_000D, "ms"));
        findings.sort(Comparator.comparing(FoundryFinding::severity).reversed()
                .thenComparing(FoundryFinding::ruleId).thenComparing(value -> value.position().x())
                .thenComparing(value -> value.position().z()).thenComparing(value -> value.position().y()));
        return new FoundryAuditReport(FORMAT_VERSION, regionId, catalog.hash(), phase,
                findings, metrics, samples);
    }

    private static FoundryAuditReport compiledBaseline(CompiledGenesisCatalog catalog, FoundryRegionIndex index) {
        long started = System.nanoTime();
        FoundryAuditPhase phase = FoundryAuditPhase.COMPILED;
        List<FoundryFinding> findings = new ArrayList<>();
        List<FoundryMetric> metrics = new ArrayList<>();
        compiledCoverage(index, phase, findings, metrics);
        assetLint(index, phase, findings, metrics);
        entranceTopology(index, phase, findings, metrics);
        FoundryMinePortalAuditor.compiled(index, phase, findings, metrics);
        railTopology(index, phase, findings, metrics);
        plannedSupport(index, phase, findings, metrics);
        List<FoundryMapSample> samples = FoundrySurfaceInspector.inspect(index, null, phase).samples();
        metrics.add(new FoundryMetric("map.columns", samples.size(), "columns"));
        metrics.add(new FoundryMetric("compiled.elapsed", (System.nanoTime() - started) / 1_000_000D, "ms"));
        findings.sort(Comparator.comparing(FoundryFinding::severity).reversed()
                .thenComparing(FoundryFinding::ruleId).thenComparing(value -> value.position().x())
                .thenComparing(value -> value.position().z()).thenComparing(value -> value.position().y()));
        return new FoundryAuditReport(FORMAT_VERSION, index.region().planId(), catalog.hash(), phase,
                findings, metrics, samples);
    }

    private static FoundryAuditReport rephase(FoundryAuditReport report, FoundryAuditPhase phase) {
        return new FoundryAuditReport(report.formatVersion(), report.regionId(), report.catalogHash(), phase,
                report.findings().stream().map(value -> new FoundryFinding(value.ruleId(), value.severity(), phase,
                        value.targetKind(), value.targetId(), value.dimensionId(), value.position(), value.message(),
                        value.remediation())).toList(), report.metrics(), report.mapSamples());
    }

    private void prepare(CompiledGenesisCatalog catalog) {
        if (cachedCatalogHash.equals(catalog.hash())) return;
        cachedCatalogHash = catalog.hash();
        indexCache.clear();
        compiledCache.clear();
    }

    public synchronized FoundryBlockInspection inspect(CompiledGenesisCatalog catalog, String regionId,
                                                        ServerLevel level, VisualPoint point) {
        var region = catalog.manifests().stream().filter(value -> value.planId().equals(regionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown authored region " + regionId));
        prepare(catalog);
        FoundryRegionIndex index = indexCache.computeIfAbsent(regionId,
                ignored -> FoundryRegionIndex.build(region, catalog));
        BlockPos position = new BlockPos(point.x(), point.y(), point.z());
        FoundryRegionIndex.ExpectedCell expected = index.expected(position);
        boolean loaded = loaded(level, position);
        BlockState actual = loaded ? level.getBlockState(position) : null;
        SiteSurfaceColumn surface = index.surface(point.x(), point.z());
        String diagnostic = !loaded ? "Containing chunk is not loaded; Foundry did not request it."
                : expected == null ? "No exact authored cell owns this position."
                : sameBlock(expected.state(), actual) ? "Physical block matches the authored block type."
                : "Physical block differs from the immutable authored plan.";
        return new FoundryBlockInspection(regionId, point, loaded, index.owner(position),
                expected == null ? "unplanned" : state(expected.state()),
                actual == null ? "unavailable" : state(actual),
                surface == null ? null : surface.groundY(), diagnostic);
    }

    private static void compiledCoverage(FoundryRegionIndex index, FoundryAuditPhase phase,
                                         List<FoundryFinding> findings, List<FoundryMetric> metrics) {
        int missingModules = 0;
        for (VisualModulePlacement module : index.modules()) {
            boolean any = index.expected().entrySet().stream().anyMatch(entry -> !entry.getValue().state().isAir()
                    && module.footprint().contains(point(entry.getKey())));
            if (!any) {
                missingModules++;
                add(findings, "compiled.module.empty", FoundrySeverity.BLOCKER, phase, "module", module.instanceId(),
                        index.region().dimensionId(), module.origin(), "Module has no compiled non-air cells.",
                        "Inspect its NBT asset, transform and true footprint.");
            }
        }
        Set<Long> expectedColumns = new HashSet<>();
        index.expected().keySet().forEach(position -> expectedColumns.add(
                FoundryRegionIndex.column(position.getX(), position.getZ())));
        long coveredSurface = index.surface().keySet().stream().filter(expectedColumns::contains).count();
        metrics.add(new FoundryMetric("compiled.cells", index.expected().size(), "cells"));
        metrics.add(new FoundryMetric("compiled.modules", index.modules().size(), "modules"));
        metrics.add(new FoundryMetric("compiled.modules_missing", missingModules, "modules"));
        metrics.add(new FoundryMetric("compiled.surface_columns_with_cells", coveredSurface, "columns"));
    }

    private static void assetLint(FoundryRegionIndex index, FoundryAuditPhase phase,
                                  List<FoundryFinding> findings, List<FoundryMetric> metrics) {
        FoundryAssetInspector inspector = new FoundryAssetInspector();
        int cells = 0;
        int escaped = 0;
        int blockEntities = 0;
        int falling = 0;
        for (VisualModulePlacement module : index.modules()) {
            FoundryAssetInspector.Inspection inspection = inspector.inspect(module,
                    index.region().dimensionId(), phase);
            cells += inspection.cells();
            escaped += inspection.escaped();
            blockEntities += inspection.blockEntities();
            falling += inspection.unsupportedFalling();
            findings.addAll(inspection.findings());
        }
        metrics.add(new FoundryMetric("assets.templates", index.modules().size(), "templates"));
        metrics.add(new FoundryMetric("assets.cells", cells, "cells"));
        metrics.add(new FoundryMetric("assets.escaped", escaped, "cells"));
        metrics.add(new FoundryMetric("assets.block_entities", blockEntities, "cells"));
        metrics.add(new FoundryMetric("assets.unsupported_falling", falling, "cells"));
    }

    private static void entranceTopology(FoundryRegionIndex index, FoundryAuditPhase phase,
                                         List<FoundryFinding> findings, List<FoundryMetric> metrics) {
        List<VisualPoint> routeNodes = index.region().settlementSite().circulation().stream()
                .flatMap(value -> value.nodes().stream()).toList();
        int entrances = 0;
        int disconnected = 0;
        for (VisualModulePlacement module : index.region().modules()) {
            for (var port : module.ports()) {
                // SERVICE is a machinery/loading connection, not necessarily a
                // public doorway. RAIL has its own topology audit below. Only
                // ports that promise player-facing circulation belong here.
                if (port.kind() != VisualPortKind.PUBLIC_ENTRANCE && port.kind() != VisualPortKind.FREIGHT) continue;
                entrances++;
                long nearest = routeNodes.stream().mapToLong(node -> horizontalDistanceSquared(node, port.position()))
                        .min().orElse(Long.MAX_VALUE);
                if (nearest > 12L * 12L) {
                    disconnected++;
                    add(findings, "navigation.entrance.route", FoundrySeverity.ERROR, phase, "module",
                            module.instanceId(), index.region().dimensionId(), port.position(),
                            "Entrance is more than 12 blocks from authored circulation.",
                            "Compile a flared access apron or move the semantic port to the real facade.");
                }
                if (port.kind() == VisualPortKind.PUBLIC_ENTRANCE
                        && !semanticEntrance(index, block(port.position()))) {
                    disconnected++;
                    add(findings, "navigation.entrance.semantic", FoundrySeverity.BLOCKER, phase, "module",
                            module.instanceId(), index.region().dimensionId(), port.position(),
                            "Semantic public entrance is not a real door, gate or grounded two-block opening.",
                            "Correct the curated local NBT entrance coordinate and module rotation contract.");
                }
            }
        }
        metrics.add(new FoundryMetric("navigation.entrances", entrances, "ports"));
        metrics.add(new FoundryMetric("navigation.disconnected", disconnected, "ports"));
    }

    /**
     * Public thresholds describe traversal, not a requirement to synthesize a
     * door. Freight sheds, stables and watch shelters deliberately use open
     * bays. They are valid only when the authored NBT itself supplies either a
     * door/gate, a grounded two-block air opening, or a walkable sill followed
     * by two clear cells.
     */
    private static boolean semanticEntrance(FoundryRegionIndex index, BlockPos position) {
        FoundryRegionIndex.ExpectedCell threshold = index.expected(position);
        if (threshold == null) return false;
        BlockState state = threshold.state();
        if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) return true;
        if (passable(state) && passable(index.expected(position.above()))) {
            int ground = index.plannedGroundY(position.getX(), position.getZ());
            return ground >= position.getY() - 1 && ground <= position.getY();
        }
        boolean walkableSill = state.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.StairBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock;
        return walkableSill && passable(index.expected(position.above()))
                && passable(index.expected(position.above(2)));
    }

    private static boolean passable(FoundryRegionIndex.ExpectedCell cell) {
        return cell != null && passable(cell.state());
    }

    private static boolean passable(BlockState state) {
        return state.isAir() || state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock;
    }

    private static void railTopology(FoundryRegionIndex index, FoundryAuditPhase phase,
                                     List<FoundryFinding> findings, List<FoundryMetric> metrics) {
        int missing = 0;
        int invalid = 0;
        List<VisualPoint> nodes = index.region().baselineRailNodes();
        for (int i = 0; i < nodes.size(); i++) {
            VisualPoint node = nodes.get(i);
            var rail = index.rails().get(FoundryRegionIndex.column(node.x(), node.z()));
            if (rail == null || !rail.rail().equals(new BlockPos(node.x(), node.y(), node.z()))) {
                missing++;
                addBounded(findings, "rail.node.missing", FoundrySeverity.BLOCKER, phase, "railway",
                        index.region().planId() + ":baseline", index.region().dimensionId(), node,
                        "Baseline route node has no compiled rail cell.", "Recompile the canonical path.");
                continue;
            }
            if (!(rail.railState().getBlock() instanceof BaseRailBlock)
                    || rail.support().getBlock() instanceof FallingBlock) {
                invalid++;
                addBounded(findings, "rail.cell.invalid", FoundrySeverity.BLOCKER, phase, "railway",
                        index.region().planId() + ":baseline", index.region().dimensionId(), node,
                        "Rail is not a rail block or rests on a falling support.",
                        "Use ordinary/powered rail over non-falling masonry.");
            }
            if (i == 0) continue;
            VisualPoint previous = nodes.get(i - 1);
            int horizontal = Math.max(Math.abs(previous.x() - node.x()), Math.abs(previous.z() - node.z()));
            if (horizontal != 1 || Math.abs(previous.y() - node.y()) > 1) {
                invalid++;
                addBounded(findings, "rail.graph.gap", FoundrySeverity.BLOCKER, phase, "railway",
                        index.region().planId() + ":baseline", index.region().dimensionId(), node,
                        "Consecutive canonical rail nodes are not adjacent.",
                        "Repair the persisted route graph before chunk compilation.");
            }
        }
        metrics.add(new FoundryMetric("rail.nodes", nodes.size(), "nodes"));
        metrics.add(new FoundryMetric("rail.missing", missing, "nodes"));
        metrics.add(new FoundryMetric("rail.invalid", invalid, "nodes"));
    }

    private static void plannedSupport(FoundryRegionIndex index, FoundryAuditPhase phase,
                                       List<FoundryFinding> findings, List<FoundryMetric> metrics) {
        Map<BlockPos, FoundryRegionIndex.ExpectedCell> structural = new LinkedHashMap<>();
        index.expected().forEach((position, cell) -> {
            if (!cell.state().isAir() && !cell.kind().equals("rail")) structural.put(position, cell);
        });
        Set<BlockPos> unseen = new HashSet<>(structural.keySet());
        int floating = 0;
        int components = 0;
        while (!unseen.isEmpty()) {
            BlockPos start = unseen.iterator().next();
            List<BlockPos> component = component(start, unseen, structural.keySet());
            components++;
            if (component.size() < MIN_FLOATING_COMPONENT) continue;
            boolean anchored = component.stream().anyMatch(position -> position.getY()
                    <= index.plannedGroundY(position.getX(), position.getZ()));
            if (anchored) continue;
            int minimumY = component.stream().mapToInt(BlockPos::getY).min().orElse(start.getY());
            if (minimumY < index.region().primaryMineSite().portal().y() - 2
                    || minimumY < index.region().alternateMineSite().portal().y() - 2) continue;
            floating++;
            String owner = structural.get(start).ownerId();
            addBounded(findings, "structure.component.floating", FoundrySeverity.WARNING, phase, "component", owner,
                    index.region().dimensionId(), point(start),
                    "Compiled component of " + component.size() + " cells does not reach its planned ground datum.",
                    "Add a real foundation/support chain or correct the module origin.");
        }
        metrics.add(new FoundryMetric("structure.components", components, "components"));
        metrics.add(new FoundryMetric("structure.floating_components", floating, "components"));
    }

    private static RuntimeResult inspectRuntime(FoundryRegionIndex index, ServerLevel level, FoundryAuditPhase phase,
                                                List<FoundryFinding> findings) {
        Set<Long> loadedChunks = new HashSet<>();
        Set<Long> unloadedChunks = new HashSet<>();
        int checked = 0;
        int mismatches = 0;
        int mismatchFindings = 0;
        int unsupported = 0;
        int fluidRails = 0;
        for (var entry : index.expected().entrySet()) {
            BlockPos position = entry.getKey();
            long chunk = ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
            if (!loaded(level, position)) {
                unloadedChunks.add(chunk);
                continue;
            }
            loadedChunks.add(chunk);
            checked++;
            BlockState expected = entry.getValue().state();
            BlockState actual = level.getBlockState(position);
            if (!sameBlock(expected, actual)) {
                mismatches++;
                if (mismatchFindings++ < MAX_RUNTIME_FINDINGS) add(findings, "world.cell.mismatch",
                        FoundrySeverity.ERROR, phase, entry.getValue().kind(), entry.getValue().ownerId(),
                        index.region().dimensionId(), point(position),
                        "Expected " + state(expected) + " but observed " + state(actual) + ".",
                        "Inspect writer ordering, post-feature damage or an intentional player conflict.");
                continue;
            }
            if (!actual.isAir() && !actual.canSurvive(level, position)) {
                unsupported++;
                addBounded(findings, "world.cell.cannot_survive", FoundrySeverity.ERROR, phase,
                        entry.getValue().kind(), entry.getValue().ownerId(), index.region().dimensionId(),
                        point(position), "Authored block fails its Minecraft survival predicate.",
                        "Provide the required face, floor or ceiling support.");
            }
            if (entry.getValue().kind().equals("rail")
                    && (!level.getFluidState(position).isEmpty() || !level.getFluidState(position.below()).isEmpty())) {
                fluidRails++;
                addBounded(findings, "rail.world.fluid", FoundrySeverity.ERROR, phase, "railway",
                        entry.getValue().ownerId(), index.region().dimensionId(), point(position),
                        "Materialized rail or support is occupied by fluid.",
                        "Route around water or compile a supported bridge span.");
            }
        }
        if (!unloadedChunks.isEmpty()) {
            add(findings, "world.chunks.unverified", FoundrySeverity.WARNING, phase, "region",
                    index.region().planId(), index.region().dimensionId(), index.region().anchor(),
                    unloadedChunks.size() + " authored chunks were not loaded and were intentionally not inspected.",
                    "Visit the area, let neighbour updates settle, then rerun SETTLED or RELOADED audit.");
        }
        runtimeEntrances(index, level, phase, findings);
        FoundryMinePortalAuditor.runtime(index, level, phase, findings);
        runtimeRails(index, level, phase, findings);
        return new RuntimeResult(List.of(
                new FoundryMetric("world.cells_checked", checked, "cells"),
                new FoundryMetric("world.cell_mismatches", mismatches, "cells"),
                new FoundryMetric("world.unsupported", unsupported, "cells"),
                new FoundryMetric("world.fluid_rails", fluidRails, "cells"),
                new FoundryMetric("world.loaded_chunks", loadedChunks.size(), "chunks"),
                new FoundryMetric("world.unloaded_chunks", unloadedChunks.size(), "chunks")));
    }

    private static void runtimeEntrances(FoundryRegionIndex index, ServerLevel level, FoundryAuditPhase phase,
                                         List<FoundryFinding> findings) {
        for (VisualModulePlacement module : index.modules()) for (var port : module.ports()) {
            if (port.kind() != VisualPortKind.PUBLIC_ENTRANCE && port.kind() != VisualPortKind.FREIGHT) continue;
            Direction outward = direction(port.outwardQuarterTurns());
            BlockPos entrance = block(port.position());
            if (!loaded(level, entrance) || !loaded(level, entrance.relative(outward, 2))) continue;
            for (int distance = 1; distance <= 2; distance++) for (int up = 0; up <= 1; up++) {
                BlockPos position = entrance.relative(outward, distance).above(up);
                if (!level.getBlockState(position).getCollisionShape(level, position).isEmpty()) {
                    addBounded(findings, "navigation.entrance.blocked", FoundrySeverity.ERROR, phase, "module",
                            module.instanceId(), index.region().dimensionId(), point(position),
                            "The two-block entrance throat is physically obstructed.",
                            "Make the route/apron the final writer and clear a two-high throat.");
                    distance = 3;
                    break;
                }
            }
        }
    }

    private static void runtimeRails(FoundryRegionIndex index, ServerLevel level, FoundryAuditPhase phase,
                                     List<FoundryFinding> findings) {
        for (VisualPoint node : index.region().baselineRailNodes()) {
            BlockPos position = block(node);
            if (!loaded(level, position)) continue;
            BlockState state = level.getBlockState(position);
            if (!(state.getBlock() instanceof BaseRailBlock)) {
                addBounded(findings, "rail.world.graph", FoundrySeverity.ERROR, phase, "railway",
                        index.region().planId() + ":baseline", index.region().dimensionId(), node,
                        "Canonical rail graph node is not a physical rail block.",
                        "Treat the material chunk as damaged and reconcile the route capability.");
            }
        }
    }

    private static List<BlockPos> component(BlockPos start, Set<BlockPos> unseen, Set<BlockPos> occupied) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> result = new ArrayList<>();
        queue.add(start);
        unseen.remove(start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            result.add(current);
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (occupied.contains(next) && unseen.remove(next)) queue.addLast(next);
            }
        }
        return result;
    }

    private static void addBounded(List<FoundryFinding> target, String rule, FoundrySeverity severity,
                                   FoundryAuditPhase phase, String kind, String id, String dimension,
                                   VisualPoint position, String message, String remediation) {
        if (target.stream().filter(value -> value.ruleId().equals(rule)).count() >= MAX_FINDINGS_PER_RULE) return;
        add(target, rule, severity, phase, kind, id, dimension, position, message, remediation);
    }

    private static void add(List<FoundryFinding> target, String rule, FoundrySeverity severity,
                            FoundryAuditPhase phase, String kind, String id, String dimension,
                            VisualPoint position, String message, String remediation) {
        target.add(new FoundryFinding(rule, severity, phase, kind, id, dimension, position, message, remediation));
    }

    private static boolean loaded(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null;
    }

    private static boolean sameBlock(BlockState expected, BlockState actual) {
        return expected.isAir() ? actual.isAir() : expected.getBlock() == actual.getBlock();
    }

    private static String state(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()) + (state.getValues().isEmpty() ? "" : state.toString()
                .substring(state.toString().indexOf('[')));
    }

    private static long horizontalDistanceSquared(VisualPoint first, VisualPoint second) {
        long dx = (long) first.x() - second.x();
        long dz = (long) first.z() - second.z();
        return dx * dx + dz * dz;
    }

    private static Direction direction(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> Direction.EAST;
            case 1 -> Direction.SOUTH;
            case 2 -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    private static VisualPoint point(BlockPos position) {
        return new VisualPoint(position.getX(), position.getY(), position.getZ());
    }

    private static BlockPos block(VisualPoint point) { return new BlockPos(point.x(), point.y(), point.z()); }

    private record RuntimeResult(List<FoundryMetric> metrics) {
        static RuntimeResult empty() { return new RuntimeResult(List.of()); }
    }
}
