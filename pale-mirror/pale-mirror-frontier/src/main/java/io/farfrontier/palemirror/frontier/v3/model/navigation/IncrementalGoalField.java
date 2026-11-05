package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Shared reverse shortest distances on one immutable directed graph. No actor or movement authority. */
final class IncrementalGoalField<N> {
    interface Graph<N> {
        List<N> nodes();
        boolean contains(N node);
        List<N> successors(N node);
        List<N> predecessors(N node);
        default int cost(N from, N to) { return 1; }
        default boolean unitCosts() { return true; }
    }
    record Image<N>(Graph<N> graph, N goal, Map<N, Integer> distances, boolean complete) { }
    private static final int INFINITY = Integer.MAX_VALUE / 4;
    private static final int MAX_NODES = PedestrianRegionBuild.MAX_SURFACES;
    private static final class Label<N> {
        final N node; final int order;
        int distance = INFINITY, rhs = INFINITY;
        Open<N> open;
        Label(N node, int order) { this.node = node; this.order = order; }
    }
    private record Open<N>(int cost, int order, Label<N> label) { }
    private final Graph<N> graph;
    private final N goal;
    private final Image<N> seed;
    private final Map<N, Label<N>> labels = new HashMap<>();
    private final LinkedHashSet<N> dirty = new LinkedHashSet<>();
    private final TreeSet<Open<N>> queue = new TreeSet<>(Comparator.comparingInt((Open<N> value) -> value.cost())
            .thenComparingInt(Open::order));
    private Iterator<N> initialization;
    private boolean initialized;
    private volatile Image<N> image;
    private long repaired, reused;
    private Label<N> updating;
    private Iterator<N> successors, propagating;
    private int rhs;
    private Iterator<Label<N>> saving;
    private Map<N, Integer> saved;
    private final ArrayDeque<N> fresh = new ArrayDeque<>();
    private N freshNode;
    private Iterator<N> freshEdges;
    private int finite;

    IncrementalGoalField(Graph<N> graph, N goal, Image<N> seed) {
        this.graph = Objects.requireNonNull(graph); this.goal = Objects.requireNonNull(goal); this.seed = seed;
        if (graph.nodes().size() > MAX_NODES) throw new IllegalArgumentException("goal field exceeds its graph bound");
        if (seed != null && !seed.goal().equals(goal)) throw new IllegalArgumentException("goal field seed addresses another goal");
        if (seed == null) {
            initialized = true;
            if (graph.unitCosts()) {
                if (graph.contains(goal)) { var root = label(goal); root.distance = root.rhs = 0; finite++; fresh.add(goal); }
            } else dirty.add(goal);
        } else initialization = graph.nodes().iterator();
    }
    int advance(int budget, BooleanSupplier mayContinue) { return advance(Optional.empty(), budget, mayContinue); }
    int advanceUntil(N origin, int budget, BooleanSupplier mayContinue) { return advance(Optional.of(origin), budget, mayContinue); }
    private int advance(Optional<N> origin, int budget, BooleanSupplier mayContinue) {
        int work = 0;
        while (work < budget && image == null && mayContinue.getAsBoolean()) {
            if (origin.isPresent() && settled(origin.orElseThrow())) break;
            if (seed == null && graph.unitCosts()) {
                work++;
                if (freshEdges != null) {
                    if (freshEdges.hasNext()) {
                        var predecessor = freshEdges.next(); var next = label(predecessor);
                        if (next.distance == INFINITY) {
                            next.distance = next.rhs = label(freshNode).distance + 1; finite++; fresh.addLast(predecessor);
                        }
                    } else { freshEdges = null; freshNode = null; }
                } else if (!fresh.isEmpty()) { freshNode = fresh.removeFirst(); freshEdges = graph.predecessors(freshNode).iterator(); }
                else { image = snapshot(); }
                continue;
            }
            if (!initialized) {
                work++;
                if (!initialization.hasNext()) { initialized = true; dirty.add(goal); continue; }
                N node = initialization.next(); var label = label(node);
                label.distance = seed.distances().getOrDefault(node, INFINITY); label.rhs = label.distance;
                if (label.distance < INFINITY) finite++;
                if (!seed.graph().contains(node) || !graph.successors(node).equals(seed.graph().successors(node))
                        || graph.successors(node).stream().anyMatch(next -> graph.cost(node, next) != seed.graph().cost(node, next))
                        || !seed.complete() && label.distance == INFINITY) dirty.add(node);
                else if (label.distance < INFINITY) reused++;
                continue;
            }
            work++;
            if (updating != null) {
                if (successors.hasNext()) {
                    var next = successors.next();
                    rhs = Math.min(rhs, Math.min(INFINITY, label(next).distance + graph.cost(updating.node, next)));
                }
                else {
                    updating.rhs = rhs;
                    if (updating.distance != rhs) {
                        updating.open = new Open<>(Math.min(updating.distance, rhs), updating.order, updating); queue.add(updating.open);
                    }
                    updating = null; successors = null; repaired++;
                }
                continue;
            }
            if (propagating != null) {
                if (propagating.hasNext()) dirty.add(propagating.next()); else propagating = null;
                continue;
            }
            if (!dirty.isEmpty()) {
                updating = label(dirty.removeFirst());
                if (updating.open != null) queue.remove(updating.open); updating.open = null;
                rhs = updating.node.equals(goal) && graph.contains(goal) ? 0 : INFINITY;
                successors = graph.successors(updating.node).iterator(); continue;
            }
            if (queue.isEmpty()) {
                if (saving == null) { saving = labels.values().iterator(); saved = new HashMap<>(); }
                if (saving.hasNext()) {
                    var value = saving.next(); if (value.distance < INFINITY && graph.contains(value.node)) saved.put(value.node, value.distance);
                } else image = new Image<>(graph, goal, Map.copyOf(saved), true);
                continue;
            }
            var head = queue.removeFirst(); var value = head.label(); value.open = null;
            if (value.distance > value.rhs) { if (value.distance == INFINITY) finite++; value.distance = value.rhs; }
            else { if (value.distance < INFINITY) finite--; value.distance = INFINITY; dirty.add(value.node); }
            propagating = graph.predecessors(value.node).iterator();
        }
        return work;
    }
    private Label<N> label(N node) {
        var value = labels.get(node);
        if (value != null) return value;
        if (labels.size() == MAX_NODES && graph.contains(node)) throw new IllegalArgumentException("goal field retained too many labels");
        value = new Label<>(node, labels.size()); labels.put(node, value); return value;
    }
    boolean finished() { return image != null; }
    Image<N> image() { if (image == null) throw new IllegalStateException("unfinished field has no trusted image"); return image; }
    boolean settled(N node) {
        if (image != null) return true;
        var value = labels.get(node);
        if (seed == null && graph.unitCosts()) return value != null && value.distance < INFINITY || complete();
        return initialized && updating == null && propagating == null && dirty.isEmpty()
                && value != null && value.distance == value.rhs
                && (queue.isEmpty() || queue.getFirst().cost() >= value.distance);
    }
    boolean complete() {
        return seed == null && graph.unitCosts() ? fresh.isEmpty() && freshEdges == null
                : initialized && updating == null && propagating == null && dirty.isEmpty() && queue.isEmpty();
    }
    int finiteLabels() { return finite; }
    Image<N> snapshot() {
        if (image != null) return image;
        var values = new HashMap<N, Integer>();
        labels.forEach((node, value) -> { if (value.distance < INFINITY && graph.contains(node) && settled(node)) values.put(node, value.distance); });
        return new Image<>(graph, goal, Map.copyOf(values), complete());
    }
    int distance(N node) {
        if (!settled(node)) throw new IllegalStateException("unsettled goal-field vertex");
        var value = labels.get(node); return value == null ? INFINITY : value.distance;
    }
    N next(N node) {
        int distance = distance(node); if (distance == INFINITY || distance == 0) return null;
        for (N next : graph.successors(node)) {
            var value = labels.get(next);
            if (value != null && value.distance + graph.cost(node, next) == distance && settled(next)) return next;
        }
        throw new IllegalStateException("settled goal field lost its directed successor");
    }
    long reusedLabels() { return reused; }
    long repairedVertices() { return repaired; }
}
