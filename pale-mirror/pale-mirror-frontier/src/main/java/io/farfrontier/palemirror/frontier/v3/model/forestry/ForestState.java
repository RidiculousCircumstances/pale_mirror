package io.farfrontier.palemirror.frontier.v3.model.forestry;

import java.util.*;

/** Sparse changed/admitted natural sources. Untouched authored forest stays in its provider.
 * Region partitions share unchanged data; an edit never rescans the whole known forest. */
public final class ForestState {
    public static final int MAX_REGIONS = 4096;
    public static final int MAX_TREES_PER_REGION = 256;
    private final Map<ForestRegion, Map<TreeId, ForestTree>> regions;
    private final Map<ForestRegion, Map<TreeId, ForestRegion>> links;
    private final Map<TreeId, ForestRegion> addresses;
    private final Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ForestryWork> work;
    private final long nextWorkOrdinal;
    // Successful validation of immutable declarations only, never a world observation.
    private volatile io.farfrontier.palemirror.frontier.v3.model.WorldBounds validatedBounds;

    public ForestState(Map<ForestRegion, Map<TreeId, ForestTree>> regions) { this(regions, Map.of(), 1); }
    public ForestState(Map<ForestRegion, Map<TreeId, ForestTree>> regions,
                       Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ForestryWork> work, long nextWorkOrdinal) {
        this.work = Map.copyOf(work); this.nextWorkOrdinal = nextWorkOrdinal;
        var copy = new HashMap<ForestRegion, Map<TreeId, ForestTree>>();
        var references = new HashMap<ForestRegion, Map<TreeId, ForestRegion>>();
        var ids = new HashSet<TreeId>();
        var directory = new HashMap<TreeId, ForestRegion>();
        for (var entry : regions.entrySet()) {
            if (entry.getValue().isEmpty() || entry.getValue().size() > MAX_TREES_PER_REGION)
                throw new IllegalArgumentException("invalid forest region population");
            for (var tree : entry.getValue().entrySet()) {
                if (!tree.getKey().equals(tree.getValue().declaration().id()) || !ids.add(tree.getKey())
                        || !entry.getKey().equals(tree.getValue().declaration().owningRegion()))
                    throw new IllegalArgumentException("tree lost exact identity or storage region");
                for (var footprint : tree.getValue().declaration().footprint()) if (!footprint.equals(entry.getKey()))
                    references.computeIfAbsent(footprint, ignored -> new HashMap<>()).put(tree.getKey(), entry.getKey());
                directory.put(tree.getKey(), entry.getKey());
            }
            copy.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        var immutableLinks = new HashMap<ForestRegion, Map<TreeId, ForestRegion>>();
        references.forEach((region, values) -> immutableLinks.put(region, Map.copyOf(values)));
        requireBounds(copy, immutableLinks); this.regions = Collections.unmodifiableMap(copy);
        this.links = Collections.unmodifiableMap(immutableLinks);
        this.addresses = Collections.unmodifiableMap(directory);
        validateWork();
    }
    private ForestState(Map<ForestRegion, Map<TreeId, ForestTree>> regions,
                        Map<ForestRegion, Map<TreeId, ForestRegion>> links, Map<TreeId, ForestRegion> addresses,
                        io.farfrontier.palemirror.frontier.v3.model.WorldBounds validatedBounds,
                        Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ForestryWork> work, long nextWorkOrdinal) {
        requireBounds(regions, links); this.regions = Collections.unmodifiableMap(regions); this.links = Collections.unmodifiableMap(links);
        this.addresses = Collections.unmodifiableMap(addresses);
        this.validatedBounds = validatedBounds; this.work = work; this.nextWorkOrdinal = nextWorkOrdinal; validateWork();
    }
    private static void requireBounds(Map<ForestRegion, Map<TreeId, ForestTree>> regions,
                                      Map<ForestRegion, Map<TreeId, ForestRegion>> links) {
        if (regions.size() > MAX_REGIONS || links.size() > MAX_REGIONS)
            throw new IllegalArgumentException("forest sparse storage capacity reached");
    }
    private void validateWork() {
        if (nextWorkOrdinal < 1 || work.size() > 64) throw new IllegalArgumentException("invalid bounded forestry work register");
        var actors = new HashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
        var tools = new HashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>(); var trees = new HashSet<TreeReference>();
        for (var entry : work.entrySet()) {
            var job = entry.getValue();
            if (!entry.getKey().equals(job.id()) || job.terminal() || !actors.add(job.execution().actorId()) || !tools.add(job.toolId()))
                throw new IllegalArgumentException("competing forestry actor/tool or terminal work reference");
            if (job.target().isPresent()) {
                var target = job.target().orElseThrow(); require(target.tree());
                if (!trees.add(target.tree())) throw new IllegalArgumentException("two forestry executions reserved the same tree");
            }
        }
    }
    public Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ForestryWork> work() { return work; }
    public long nextWorkOrdinal() { return nextWorkOrdinal; }
    public ForestState admitWork(ForestryWork job, long expectedOrdinal) {
        if (nextWorkOrdinal != expectedOrdinal || work.containsKey(job.id()) || job.phase() != ForestryWork.Phase.TAKE_TOOL
                || job.revision() != 1 || job.pending().isPresent() || job.felling().isPresent() || job.labour().isPresent())
            throw new IllegalArgumentException("stale or incomplete forestry admission");
        var jobs = new LinkedHashMap<>(work); jobs.put(job.id(), job);
        return new ForestState(regions, links, addresses, validatedBounds, Map.copyOf(jobs), Math.addExact(nextWorkOrdinal, 1));
    }
    public ForestState replaceWork(ForestryWork expected, ForestryWork next) {
        if (!Objects.equals(work.get(expected.id()), expected) || !expected.id().equals(next.id()) || !expected.home().equals(next.home())
                || !expected.execution().actorId().equals(next.execution().actorId()) || !expected.toolId().equals(next.toolId())
                || !expected.toolSource().equals(next.toolSource()))
            throw new IllegalArgumentException("forestry continuation changed its exact retained obligations");
        var jobs = new LinkedHashMap<>(work);
        if (next.terminal()) jobs.remove(next.id()); else jobs.put(next.id(), next);
        return new ForestState(regions, links, addresses, validatedBounds, Map.copyOf(jobs), nextWorkOrdinal);
    }
    /** Exact dead-owner disposal after the common death/resource protocol has settled. */
    public ForestState releaseDeadWork(ForestryWork expected) {
        if (!expected.equals(work.get(expected.id())) || expected.pending().isPresent())
            throw new IllegalArgumentException("dead forest owner retains a stale or unresolved physical effect");
        var next = new LinkedHashMap<>(work); next.remove(expected.id());
        return new ForestState(regions, links, addresses, validatedBounds, Map.copyOf(next), nextWorkOrdinal);
    }
    public static ForestState empty() { return new ForestState(Map.of()); }
    public void requireWithin(io.farfrontier.palemirror.frontier.v3.model.WorldBounds bounds) {
        if (bounds.equals(validatedBounds)) return;
        for (var part : regions.values()) for (var tree : part.values()) requireWithin(tree.declaration(), bounds);
        validatedBounds = bounds;
    }
    private static void requireWithin(TreeDescriptor tree, io.farfrontier.palemirror.frontier.v3.model.WorldBounds bounds) {
        for (int i = 0; i < tree.logs().size(); i++) if (!bounds.contains(tree.logs().position(tree.origin(), i)))
            throw new IllegalArgumentException("tree source is outside managed world bounds");
    }
    public Map<TreeId, ForestRegion> treeDirectory() { return addresses; }
    public Map<ForestRegion, Map<TreeId, ForestTree>> regions() { return regions; }
    /** Complete regional cache dependencies, including changed cross-boundary source parts. */
    public List<Object> regionalInputs(ForestRegion region) {
        var local = links.getOrDefault(region, Map.of()); var result = new ArrayList<Object>();
        result.add(regions.getOrDefault(region, Map.of())); result.add(local);
        for (var owner : new TreeSet<>(local.values())) result.add(regions.get(owner));
        return List.copyOf(result);
    }
    public Optional<ForestTree> find(TreeReference reference) {
        var actualAddress = addresses.get(reference.id());
        if (actualAddress != null && !actualAddress.equals(reference.region()))
            throw new IllegalArgumentException("tree reference substituted its storage address");
        var tree = regions.getOrDefault(reference.region(), Map.of()).get(reference.id());
        if (tree != null && !reference.matches(tree.declaration())) throw new IllegalArgumentException("stale tree generation/address");
        return Optional.ofNullable(tree);
    }
    public ForestTree require(TreeReference reference) {
        return find(reference).orElseThrow(() -> new IllegalArgumentException("tree continuation lost its exact source"));
    }
    public List<ForestTree> touching(ForestRegion region) {
        var result = new ArrayList<>(regions.getOrDefault(region, Map.of()).values());
        links.getOrDefault(region, Map.of()).forEach((id, address) -> result.add(regions.get(address).get(id)));
        result.sort(Comparator.comparing(value -> value.declaration().id())); return List.copyOf(result);
    }
    /** Generation producer admission, never an extraction replay or implicit same-position replacement. */
    public ForestState admit(TreeDescriptor declaration) {
        var bounds = validatedBounds;
        if (bounds != null) requireWithin(declaration, bounds);
        var reference = TreeReference.declaredBy(declaration);
        if (find(reference).isPresent()) throw new IllegalArgumentException("duplicate tree generation admission");
        var owned = new HashMap<>(regions.getOrDefault(reference.region(), Map.of()));
        if (owned.size() >= MAX_TREES_PER_REGION) throw new IllegalArgumentException("forest region capacity reached");
        owned.put(reference.id(), ForestTree.initial(declaration));
        var next = new HashMap<>(regions); next.put(reference.region(), Map.copyOf(owned));
        var references = new HashMap<>(links);
        for (var footprint : declaration.footprint()) if (!footprint.equals(reference.region())) {
            var part = new HashMap<>(references.getOrDefault(footprint, Map.of())); part.put(reference.id(), reference.region());
            references.put(footprint, Map.copyOf(part));
        }
        var directory = new HashMap<>(addresses); directory.put(reference.id(), reference.region());
        return new ForestState(next, references, directory, bounds, work, nextWorkOrdinal);
    }
    public ForestState replace(ForestTree expected, ForestTree replacement) {
        if (!require(expected.reference()).equals(expected) || !expected.declaration().equals(replacement.declaration())
                || replacement.revision() <= expected.revision())
            throw new IllegalArgumentException("stale or destructive tree-source replacement");
        var before = expected.absent(); var after = replacement.absent();
        for (int i = 0; i < before.length; i++) if ((before[i] & ~after[i]) != 0)
            throw new IllegalArgumentException("a withdrawn tree generation cannot regenerate its logs");
        var extractedBefore = expected.extracted(); var extractedAfter = replacement.extracted();
        for (int i = 0; i < before.length; i++) if (((extractedAfter[i] & ~extractedBefore[i]) & before[i]) != 0)
            throw new IllegalArgumentException("external tree loss cannot become work projection authority");
        var region = expected.reference().region(); var part = new HashMap<>(regions.get(region));
        part.put(expected.reference().id(), replacement);
        var next = new HashMap<>(regions); next.put(region, Map.copyOf(part));
        return new ForestState(next, links, addresses, validatedBounds, work, nextWorkOrdinal);
    }
    @Override public boolean equals(Object other) { return this == other || other instanceof ForestState state && regions.equals(state.regions) && work.equals(state.work) && nextWorkOrdinal == state.nextWorkOrdinal; }
    @Override public int hashCode() { return Objects.hash(regions, work, nextWorkOrdinal); }
}
