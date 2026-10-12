package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Read-only closed point registry. An ID addresses a complete producer declaration, never discovers its type. */
public final class ServiceBoundaryComposition {
    private static final List<ServiceBoundaryProvider> PROVIDERS = List.of(new DepotServiceBoundaries(), new ExtractionServiceBoundaries(),
            new StorageServiceBoundaries());
    static {
        if (PROVIDERS.size() != ServicePointId.Kind.values().length
                || !PROVIDERS.stream().map(ServiceBoundaryProvider::kind).collect(java.util.stream.Collectors.toSet())
                    .equals(EnumSet.allOf(ServicePointId.Kind.class)))
            throw new IllegalArgumentException("missing or duplicate service boundary owner");
    }
    private static FrontierBootstrap cachedBootstrap;
    private static List<Object> versions = List.of();
    private static Map<SubjectId, ServiceBoundaryProvider.Declaration> cached;
    /** One latest rebuildable spatial view; actor positions, permits and stock never enter this cache. */
    private record TurnoverIndex(Map<SubjectId, ServiceBoundaryProvider.Declaration> declarations,
            Object providerVersions, KnownPedestrianRouteKnowledge knowledge, Object residents, List<ServiceAccessPoint> points,
            Map<SurfaceAnchor, ServiceBoundaryProvider.Declaration> bySurface) { }
    private static TurnoverIndex turnoverIndex;
    private ServiceBoundaryComposition() { }
    static synchronized Map<SubjectId, ServiceBoundaryProvider.Declaration> declarations(FrontierWorldState state) {
        var nextVersions = PROVIDERS.stream().map(provider -> provider.version(state)).toList();
        boolean same = cached != null && cachedBootstrap == state.bootstrap() && nextVersions.size() == versions.size();
        for (int index = 0; same && index < versions.size(); index++) same = versions.get(index) == nextVersions.get(index);
        if (same) return cached;
        var result = new LinkedHashMap<SubjectId, ServiceBoundaryProvider.Declaration>();
        for (var provider : PROVIDERS) for (var declaration : provider.declarations(state)) {
            if (declaration.kind() != provider.kind() || result.putIfAbsent(declaration.pointId(), declaration) != null)
                throw new IllegalArgumentException("foreign or duplicate service boundary declaration");
        }
        // A stock/policy update may replace container records without changing any declared footprint.
        if (cachedBootstrap != state.bootstrap() || !result.equals(cached)) cached = Map.copyOf(result);
        cachedBootstrap = state.bootstrap(); versions = nextVersions;
        return cached;
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
        return turnoverIndex(state).points();
    }
    public static Optional<ServiceBoundaryProvider.Declaration> turnoverPoint(FrontierWorldState state, SurfaceAnchor actual) {
        return Optional.ofNullable(turnoverIndex(state).bySurface().get(actual));
    }
    private static synchronized TurnoverIndex turnoverIndex(FrontierWorldState state) {
        var declared = declarations(state);
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        var residents = state.humanPopulation().residents();
        if (turnoverIndex != null && turnoverIndex.declarations() == declared
                && turnoverIndex.providerVersions() == versions
                && turnoverIndex.knowledge() == knowledge && turnoverIndex.residents() == residents) return turnoverIndex;
        var points = new ArrayList<ServiceAccessPoint>();
        var surfaces = new HashMap<SurfaceAnchor, ServiceBoundaryProvider.Declaration>();
        declared.values().stream().sorted(Comparator.comparing(ServiceBoundaryProvider.Declaration::pointId)).forEach(value -> {
            var point = geometry(state, value.identity());
            points.add(point);
            // Preserve the former sorted-first result where declared temporary footprints overlap.
            point.boundary().occupiedSurfaces().forEach(surface -> surfaces.putIfAbsent(surface, value));
            point.waitingSurfaces().forEach(surface -> surfaces.putIfAbsent(surface, value));
            point.egressSurfaces().forEach(surface -> surfaces.putIfAbsent(surface, value));
        });
        turnoverIndex = new TurnoverIndex(declared, versions, knowledge, residents, List.copyOf(points), Map.copyOf(surfaces));
        return turnoverIndex;
    }
}
