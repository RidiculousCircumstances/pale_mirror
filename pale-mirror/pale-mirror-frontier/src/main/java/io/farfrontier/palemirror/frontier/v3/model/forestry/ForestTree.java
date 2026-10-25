package io.farfrontier.palemirror.frontier.v3.model.forestry;

import java.util.*;

/** Current source losses. Loot/cargo belong to the extraction and resource owners.
 * A lost generation never reappears merely because the native block looks equal again. */
public final class ForestTree {
    private final TreeDescriptor declaration;
    private final long revision;
    private final long[] absent;
    private final long[] extracted;
    private final int missing;
    private final Map<Integer, String> extractionOperations;
    public ForestTree(TreeDescriptor declaration, long revision, long[] absent) {
        this(declaration, revision, absent, new long[absent.length]);
    }
    public ForestTree(TreeDescriptor declaration, long revision, long[] absent, long[] extracted) {
        this(declaration, revision, absent, extracted, Map.of());
    }
    public ForestTree(TreeDescriptor declaration, long revision, long[] absent, long[] extracted, Map<Integer, String> extractionOperations) {
        this.extractionOperations = Map.copyOf(extractionOperations);
        this.declaration = Objects.requireNonNull(declaration); this.revision = revision;
        this.absent = absent.clone();
        this.extracted = extracted.clone();
        if (revision < 1 || absent.length != Math.ceilDiv(declaration.logs().size(), 64) || extracted.length != absent.length)
            throw new IllegalArgumentException("invalid tree source image");
        int tail = declaration.logs().size() & 63;
        if (tail != 0 && absent.length > 0 && absent[absent.length - 1] >>> tail != 0)
            throw new IllegalArgumentException("tree loss mask names undeclared logs");
        missing = Arrays.stream(absent).mapToInt(Long::bitCount).sum();
        for (int i = 0; i < absent.length; i++) if ((extracted[i] & ~absent[i]) != 0)
            throw new IllegalArgumentException("extraction aftermath cannot retain an available source");
        if (this.extractionOperations.size() != Arrays.stream(extracted).mapToInt(Long::bitCount).sum())
            throw new IllegalArgumentException("worked source mask lacks its exact consequence identities");
        this.extractionOperations.forEach((log, operation) -> {
            if (!extracted(log) || operation == null || operation.isBlank() || operation.length() > 512)
                throw new IllegalArgumentException("invalid worked source consequence identity");
        });

    }
    public static ForestTree initial(TreeDescriptor declaration) {
        return new ForestTree(declaration, 1, new long[Math.ceilDiv(declaration.logs().size(), 64)]);
    }
    public TreeDescriptor declaration() { return declaration; }
    public TreeReference reference() { return TreeReference.declaredBy(declaration); }
    public long revision() { return revision; }
    public Map<Integer, String> extractionOperations() { return extractionOperations; }
    public Optional<String> extractionOperation(int log) { return Optional.ofNullable(extractionOperations.get(log)); }
    public long[] absent() { return absent.clone(); }
    public long[] extracted() { return extracted.clone(); }
    public boolean extracted(int log) {
        if (log < 0 || log >= declaration.logs().size()) throw new IllegalArgumentException("foreign tree log");
        return (extracted[log >>> 6] & (1L << (log & 63))) != 0;
    }
    public int remaining() { return declaration.logs().size() - missing; }
    public boolean present(int log) {
        if (log < 0 || log >= declaration.logs().size()) throw new IllegalArgumentException("foreign tree log");
        return (absent[log >>> 6] & (1L << (log & 63))) == 0;
    }
    public ForestTree without(int log) {
        if (!present(log)) throw new IllegalArgumentException("tree log was already withdrawn");
        var next = absent.clone(); next[log >>> 6] |= 1L << (log & 63);
        return new ForestTree(declaration, Math.addExact(revision, 1), next, extracted, extractionOperations);
    }
    public ForestTree extract(int log, String operation) {
        if (!present(log)) throw new IllegalArgumentException("tree log was already withdrawn");
        var lost = absent.clone(); var worked = extracted.clone();
        lost[log >>> 6] |= 1L << (log & 63); worked[log >>> 6] |= 1L << (log & 63);
        var operations = new HashMap<>(extractionOperations); operations.put(log, operation);
        return new ForestTree(declaration, Math.addExact(revision, 1), lost, worked, operations);
    }
    /** A later external edit revokes only our projection authority, never restores the old source. */
    public ForestTree external(int log) {
        if (present(log)) return without(log);
        if (!extracted(log)) throw new IllegalArgumentException("external tree loss was already observed");
        var worked = extracted.clone(); worked[log >>> 6] &= ~(1L << (log & 63));
        var operations = new HashMap<>(extractionOperations); operations.remove(log);
        return new ForestTree(declaration, Math.addExact(revision, 1), absent, worked, operations);
    }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof ForestTree tree && revision == tree.revision
                && extractionOperations.equals(tree.extractionOperations) && declaration.equals(tree.declaration) && Arrays.equals(absent, tree.absent) && Arrays.equals(extracted, tree.extracted);
    }
    @Override public int hashCode() { return Objects.hash(declaration, revision, Arrays.hashCode(absent), Arrays.hashCode(extracted), extractionOperations); }
}
