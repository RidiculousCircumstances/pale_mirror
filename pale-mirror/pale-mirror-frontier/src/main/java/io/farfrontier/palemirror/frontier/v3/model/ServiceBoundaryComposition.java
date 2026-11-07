package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Read-only closed point registry. An ID addresses a complete producer declaration, never discovers its type. */
public final class ServiceBoundaryComposition {
    private static final List<ServiceBoundaryProvider> PROVIDERS = List.of(new DepotServiceBoundaries(), new ExtractionServiceBoundaries(),
            new StorageServiceBoundaries(), new ProductionServiceBoundaries());
    static {
        if (PROVIDERS.size() != ServicePointId.Kind.values().length
                || !PROVIDERS.stream().map(ServiceBoundaryProvider::kind).collect(java.util.stream.Collectors.toSet())
                    .equals(EnumSet.allOf(ServicePointId.Kind.class)))
            throw new IllegalArgumentException("missing or duplicate service boundary owner");
    }
    // Provider version tokens may be thread-confined. Publish/cache on the same
    // immutable planning lane rather than comparing tokens from different workers.
    private static final ImmutableInputView<Map<SubjectId, ServiceBoundaryProvider.Declaration>> DECLARATIONS = new ImmutableInputView<>();
    private static final ImmutableInputView<Map<SubjectId, ServiceBoundaryProvider.Declaration>> ACTIVE = new ImmutableInputView<>();
    /** Explicit all-points view; ordinary local turnover uses the declaration-only column index. */
    private static final ImmutableResultView<String, List<ServiceAccessPoint>> TURNOVER = new ImmutableResultView<>(4);
    private record ColumnIndex(Object declarations, List<Object> providerVersions, Object residents,
            Map<ServicePointId, ServiceBoundaryProvider.SearchFootprint> footprints,
            Map<TerrainColumn, List<ServiceBoundaryProvider.Declaration>> candidates) { }
    private static final ThreadLocal<ColumnIndex> COLUMN_INDEX = new ThreadLocal<>();
    private ServiceBoundaryComposition() { }
    static Map<SubjectId, ServiceBoundaryProvider.Declaration> declarations(FrontierWorldState state) {
        var inputs = new ArrayList<Object>();
        inputs.add(state.bootstrap());
        PROVIDERS.forEach(provider -> inputs.add(provider.version(state)));
        return DECLARATIONS.getPublished(inputs, () -> {
            var result = new LinkedHashMap<SubjectId, ServiceBoundaryProvider.Declaration>();
            for (var provider : PROVIDERS) for (var declaration : provider.declarations(state)) {
                if (declaration.kind() != provider.kind() || result.putIfAbsent(declaration.pointId(), declaration) != null)
                    throw new IllegalArgumentException("foreign or duplicate service boundary declaration");
            }
            return Map.copyOf(result);
        });
    }
    static ServiceAccessBoundary boundary(FrontierWorldState state, SubjectId point) {
        var declaration = declarations(state).get(point);
        if (declaration == null) throw new IllegalArgumentException("unknown declared service point: " + point);
        return declaration.boundary();
    }
    public static ServiceBoundaryProvider.Declaration declaration(FrontierWorldState state, SubjectId point) {
        var declared = declarations(state).get(point);
        if (declared == null) throw new IllegalArgumentException("unknown declared service point: " + point);
        declared.identity().validate(state.inventory());
        return declared;
    }
    public static ServiceBoundaryProvider.Declaration require(FrontierWorldState state, ServicePointId point) {
        var declared = declaration(state, point.containerId());
        if (!declared.identity().equals(point)) throw new IllegalArgumentException("foreign service point declaration");
        return declared;
    }
    private static ServiceBoundaryProvider provider(ServicePointId.Kind kind) {
        return PROVIDERS.stream().filter(value -> value.kind() == kind).findFirst().orElseThrow();
    }
    public static KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, ServicePointId point) {
        var declared = require(state, point);
        return provider(point.kind()).knowledge(state, declared);
    }
    public static ServiceAccessPoint geometry(FrontierWorldState state, ServicePointId point) {
        var declared = require(state, point);
        return provider(point.kind()).geometry(state, declared);
    }
    public static List<ServiceAccessPoint> geometries(FrontierWorldState state) {
        return turnoverPoints(state);
    }
    /** Retained identities remain resolvable for draining work; retired facilities impose no new spatial restrictions. */
    static Map<SubjectId, ServiceBoundaryProvider.Declaration> operationalDeclarations(FrontierWorldState state) {
        var declared = declarations(state);
        return ACTIVE.getPublished(List.of(declared, state.inventory().surfaces()), () -> {
            var result = new LinkedHashMap<SubjectId, ServiceBoundaryProvider.Declaration>();
            declared.forEach((id, value) -> {
                var surface = state.inventory().surfaces().get(id);
                if (surface != null && surface.status() != ContainerSurfaceStatus.RETIRED) result.put(id, value);
            });
            return Map.copyOf(result);
        });
    }
    public static boolean occupancyChanged(FrontierWorldState state, BodyPosition previous, BodyPosition observed) {
        return !previous.equals(observed) && operationalDeclarations(state).values().stream()
                .anyMatch(point -> point.boundary().occupied(previous) || point.boundary().occupied(observed));
    }
    /** A retired/destroyed access point cannot fence departure as though it still operated. */
    public static Optional<ServiceBoundaryProvider.Declaration> operational(FrontierWorldState state, SubjectId point) {
        return Optional.ofNullable(operationalDeclarations(state).get(point));
    }
    public static Optional<ServiceBoundaryProvider.Declaration> turnoverPoint(FrontierWorldState state, SurfaceAnchor actual) {
        var candidates = nearbyDeclarations(state, new TerrainColumn(actual.x(), actual.z()));
        // Actual service boundaries outrank adjacent aprons, in stable point-ID order.
        for (var declaration : candidates)
            if (declaration.boundary().occupiedSurfaces().contains(actual)) return Optional.of(declaration);
        // Facility occupancy is a turnover policy, never part of the exclusive access boundary.
        for (var declaration : candidates)
            if (declaration.idleClearanceAt(actual)) return Optional.of(declaration);
        // Resolve terrain only for points whose declared search footprint contains this column.
        // Height, obstructions and current regional versions are still checked by their owner.
        for (var declaration : candidates) {
            var point = geometry(state, declaration.identity());
            if (point.waitingSurfaces().contains(actual) || point.egressSurfaces().contains(actual))
                return Optional.of(declaration);
        }
        return Optional.empty();
    }
    static List<ServiceBoundaryProvider.Declaration> nearbyDeclarations(FrontierWorldState state, TerrainColumn column) {
        return nearbyDeclarationIndex(state).getOrDefault(column, List.of());
    }
    /** Exact immutable broad phase captured once by a multi-contact clearance calculation. */
    static Map<TerrainColumn, List<ServiceBoundaryProvider.Declaration>> nearbyDeclarationIndex(FrontierWorldState state) {
        return columnIndex(state).candidates();
    }
    private static ColumnIndex columnIndex(FrontierWorldState state) {
        var columnIndex = COLUMN_INDEX.get();
        var versions = PROVIDERS.stream().map(provider -> provider.version(state)).toList();
        var declared = operationalDeclarations(state);
        var residents = state.humanPopulation().residents();
        if (columnIndex != null && columnIndex.declarations() == declared
                && ImmutablePlanningScope.same(columnIndex.providerVersions(), versions) && columnIndex.residents() == residents) return columnIndex;
        var footprints = new HashMap<ServicePointId, ServiceBoundaryProvider.SearchFootprint>();
        declared.values().forEach(value -> footprints.put(value.identity(),
                provider(value.kind()).searchFootprint(state, value)));
        // Inventory/resident revisions may change without changing any search area.
        // Compare compact producer declarations before expanding thousands of columns.
        if (columnIndex != null && columnIndex.declarations() == declared && columnIndex.footprints().equals(footprints)) {
            columnIndex = new ColumnIndex(declared, versions, residents, columnIndex.footprints(), columnIndex.candidates());
            COLUMN_INDEX.set(columnIndex);
            return columnIndex;
        }
        var byColumn = new HashMap<TerrainColumn, List<ServiceBoundaryProvider.Declaration>>();
        declared.values().stream().sorted(Comparator.comparing(ServiceBoundaryProvider.Declaration::pointId)).forEach(value -> {
            var columns = new HashSet<>(footprints.get(value.identity()).columns());
            value.idleClearanceArea().ifPresent(area -> {
                for (int x = area.min().x(); x <= area.max().x(); x++)
                    for (int z = area.min().z(); z <= area.max().z(); z++) columns.add(new TerrainColumn(x, z));
            });
            for (var column : columns) byColumn.computeIfAbsent(column, ignored -> new ArrayList<>()).add(value);
        });
        byColumn.replaceAll((column, values) -> List.copyOf(values));
        // Terrain-column hashes cluster on regular grids; retain HashMap's bucket
        // separation instead of rehashing this large index into MapN's linear probes.
        columnIndex = new ColumnIndex(declared, versions, residents, Map.copyOf(footprints),
                Collections.unmodifiableMap(byColumn));
        COLUMN_INDEX.set(columnIndex);
        return columnIndex;
    }
    private static List<ServiceAccessPoint> turnoverPoints(FrontierWorldState state) {
        var declared = operationalDeclarations(state);
        // Capture exact immutable inputs, then resolve geometry outside the
        // declaration/index monitor. An all-points diagnostic or clearance view
        // cannot hold local admission behind terrain preparation for every site.
        var inputs = new ArrayList<Object>();
        inputs.add(state.bootstrap()); inputs.add(declared);
        inputs.add(KnownPedestrianRouteKnowledge.forFrontier(state));
        inputs.add(state.humanPopulation().residents());
        PROVIDERS.forEach(provider -> inputs.add(provider.version(state)));
        return TURNOVER.get(inputs, "points", () -> declared.values().stream()
                .sorted(Comparator.comparing(ServiceBoundaryProvider.Declaration::pointId))
                .map(value -> geometry(state, value.identity())).toList());
    }
}
