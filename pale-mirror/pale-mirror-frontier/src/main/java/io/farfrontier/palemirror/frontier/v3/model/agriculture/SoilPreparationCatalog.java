package io.farfrontier.palemirror.frontier.v3.model.agriculture;

import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle.Soil;
import io.farfrontier.palemirror.frontier.v3.model.WorkOperation;
import io.farfrontier.palemirror.frontier.v3.model.tooling.ToolRequirement;
import java.io.*;
import java.util.*;

/** Bounded immutable mod definitions, shared by planning and native observation.
 * Its canonical text is pinned by every ruleset; it never owns work or world state. */
public final class SoilPreparationCatalog {
    public enum Role { UNKNOWN, FOREIGN, PREPARABLE, SEEDBED }
    public record Step(String id, Soil before, Soil after, WorkOperation operation,
                       ToolRequirement tool, String nativeAbility, int effortPermille) {
        public Step {
            Objects.requireNonNull(id); Objects.requireNonNull(before); Objects.requireNonNull(after);
            Objects.requireNonNull(operation); Objects.requireNonNull(tool); Objects.requireNonNull(nativeAbility);
            if (id.isBlank() || nativeAbility.isBlank() || effortPermille < 1 || effortPermille > 100_000 || !before.untilled() || !after.cultivable() || before == after)
                throw new IllegalArgumentException("invalid soil preparation step");
        }
        public long workUnits(long base) { return Math.ceilDiv(Math.multiplyExact(base, effortPermille), 1000); }
    }
    private final Map<String, Soil> soils;
    private final Map<Integer, Soil> tags;
    private final Map<String, Soil> blocks;
    private final Map<Soil, Step> steps;
    private final String canonicalText;
    private SoilPreparationCatalog(Properties data) {
        var names = new TreeMap<String, Soil>(); var wire = new TreeMap<Integer, Soil>(); var nativeBlocks = new TreeMap<String, Soil>();
        for (var name : list(data, "soils")) {
            var soil = new Soil(name, Integer.parseInt(required(data, "soil." + name + ".tag")),
                    required(data, "soil." + name + ".block"), Role.valueOf(required(data, "soil." + name + ".role")));
            if (names.put(name, soil) != null || wire.put(soil.wireTag(), soil) != null)
                throw new IllegalArgumentException("duplicate soil name or wire tag");
            if (soil.cultivable() && nativeBlocks.put(soil.blockKind(), soil) != null)
                throw new IllegalArgumentException("duplicate native soil definition");
        }
        if (names.isEmpty() || names.size() > 255) throw new IllegalArgumentException("unbounded soil definitions");
        var transitions = new LinkedHashMap<Soil, Step>(); var ids = new HashSet<String>();
        for (var id : list(data, "steps")) {
            String prefix = "step." + id + ".";
            var before = Objects.requireNonNull(names.get(required(data, prefix + "before")), "undeclared preparation predecessor");
            var after = Objects.requireNonNull(names.get(required(data, prefix + "after")), "undeclared preparation result");
            var step = new Step(id, before, after, WorkOperation.valueOf(required(data, prefix + "operation")),
                    new ToolRequirement(required(data, prefix + "tool"), Integer.parseInt(required(data, prefix + "tier")),
                            Integer.parseInt(required(data, prefix + "wear"))), required(data, prefix + "nativeAbility"), Integer.parseInt(required(data, prefix + "effortPermille")));
            if (!ids.add(id) || transitions.put(before, step) != null)
                throw new IllegalArgumentException("duplicate or ambiguous soil preparation");
        }
        for (var soil : names.values()) if (soil.untilled()) {
            var visited = new HashSet<Soil>(); var cursor = soil;
            while (cursor.untilled()) {
                if (!visited.add(cursor)) throw new IllegalArgumentException("cyclic soil preparation");
                var next = transitions.get(cursor);
                if (next == null) throw new IllegalArgumentException("preparable soil has no complete preparation path");
                cursor = next.after();
            }
        }
        soils = Map.copyOf(names); tags = Map.copyOf(wire); blocks = Map.copyOf(nativeBlocks); steps = Map.copyOf(transitions);
        canonicalText = data.stringPropertyNames().stream().sorted().map(key -> key + "=" + data.getProperty(key).trim())
                .collect(java.util.stream.Collectors.joining("|"));
    }
    public static SoilPreparationCatalog load() {
        try (var input = SoilPreparationCatalog.class.getResourceAsStream("/io/farfrontier/palemirror/frontier/v3/soil-preparation.properties")) {
            if (input == null) throw new IllegalStateException("missing soil preparation definitions");
            return read(input);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    public static SoilPreparationCatalog read(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(65537);
        if (bytes.length > 65536) throw new IllegalArgumentException("soil definitions exceed their bound");
        var data = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("duplicate soil definition property " + key);
                return super.put(key, value);
            }
        };
        data.load(new ByteArrayInputStream(bytes)); return new SoilPreparationCatalog(data);
    }
    public Soil require(String name) { var soil = soils.get(name); if (soil == null) throw new IllegalArgumentException("unknown soil definition " + name); return soil; }
    public Soil require(int tag) { var soil = tags.get(tag); if (soil == null) throw new IllegalArgumentException("unknown soil wire tag " + tag); return soil; }
    public Optional<Soil> byBlock(String block) { return Optional.ofNullable(blocks.get(block)); }
    public Collection<Soil> soils() { return soils.values().stream().sorted(Comparator.comparingInt(Soil::wireTag)).toList(); }
    public Step step(Soil before) { return Objects.requireNonNull(steps.get(before), "soil has no declared preparation step"); }
    public List<Step> path(Soil from) {
        var result = new ArrayList<Step>(); var cursor = from;
        while (cursor.untilled()) { var step = step(cursor); result.add(step); cursor = step.after(); }
        return List.copyOf(result);
    }
    public String canonicalText() { return canonicalText; }
    private static String required(Properties data, String key) {
        var value = data.getProperty(key);
        if (value == null) throw new IllegalArgumentException("missing soil definition property " + key);
        return value.trim();
    }
    private static List<String> list(Properties data, String key) { return List.of(required(data, key).split(",")); }
}
