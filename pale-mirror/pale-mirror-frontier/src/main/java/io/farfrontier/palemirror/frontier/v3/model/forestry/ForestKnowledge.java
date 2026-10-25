package io.farfrontier.palemirror.frontier.v3.model.forestry;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Regional composition of immutable authored sources and their sparse current changes.
 * This index owns no navigation, work admission, inventory or physical authority. */
public final class ForestKnowledge {
    private static final ImmutableKeyedInputView<ForestRegion, List<ForestTree>> REGIONS = new ImmutableKeyedInputView<>(256);
    private static final ImmutableKeyedInputView<SubjectId, List<GrayboxForestPlan.Header>> KNOWN = new ImmutableKeyedInputView<>(32);
    public record Log(TreeReference tree, long revision, int index) { }
    private ForestKnowledge() { }
    /** Cheap source availability only; routes and economic decisions have different owners. */
    public static boolean harvestable(FrontierWorldState state, SubjectId home) {
        int capacity = state.bootstrap().ruleset().forestry().cargoItems();
        return knownBy(state, home).stream().anyMatch(header -> {
            int remaining = state.forest().find(header.tree()).map(ForestTree::remaining).orElse(header.logs());
            return remaining > 0 && remaining <= capacity;
        });
    }
    public static List<ForestTree> touching(FrontierWorldState state, ForestRegion region) {
        return REGIONS.get(state.bootstrap(), region, state.forest().regionalInputs(region), () -> {
            var trees = new TreeMap<TreeId, ForestTree>();
            for (var authored : GrayboxForestPlan.compile(state.bootstrap()).touching(region))
                trees.put(authored.id(), ForestTree.initial(authored));
            for (var changed : state.forest().touching(region)) trees.put(changed.declaration().id(), changed);
            return List.copyOf(trees.values());
        });
    }
    /** Discovery under explicitly declared forest-knowledge patches, separate from economic title.
     * Current losses do not invalidate the origin directory or trigger a whole-forest rebuild. */
    public static List<GrayboxForestPlan.Header> knownBy(FrontierWorldState state, SubjectId home) {
        return KNOWN.get(state.bootstrap(), home, List.of(state.forest().treeDirectory()), () -> {
            var result = new TreeMap<TreeId, GrayboxForestPlan.Header>();
            GrayboxForestPlan.compile(state.bootstrap()).knownBy(home).forEach(header -> result.put(header.tree().id(), header));
            var settlement = state.bootstrap().settlements().stream().filter(value -> value.id().equals(home)).findFirst().orElseThrow();
            var patches = state.bootstrap().ruleset().forestry().patches().stream().filter(patch -> patch.knownBy().equals(home)).toList();
            for (var entry : state.forest().treeDirectory().entrySet()) {
                var declaration = state.forest().regions().get(entry.getValue()).get(entry.getKey()).declaration();
                long dx = (long)declaration.origin().x()-settlement.anchor().x(), dz = (long)declaration.origin().z()-settlement.anchor().z();
                long distance = dx*dx+dz*dz;
                if (patches.stream().anyMatch(patch -> distance >= (long)patch.innerRadius()*patch.innerRadius()
                        && distance <= (long)patch.outerRadius()*patch.outerRadius()))
                    result.putIfAbsent(declaration.id(), new GrayboxForestPlan.Header(TreeReference.declaredBy(declaration), declaration.origin(), declaration.logs().size()));
            }
            return List.copyOf(result.values());
        });
    }
    public static Optional<Log> at(FrontierWorldState state, BlockPosition position) {
        Log found = null;
        for (var tree : touching(state, ForestRegion.containing(position))) {
            int log = tree.declaration().logs().indexOf(tree.declaration().origin(), position);
            if (log < 0 || !tree.present(log) && !tree.extracted(log)) continue;
            if (found != null) throw new IllegalArgumentException("competing current natural tree declarations");
            found = new Log(tree.reference(), tree.revision(), log);
        }
        return Optional.ofNullable(found);
    }
    public static ForestTree require(FrontierWorldState state, TreeReference reference) {
        var changed = state.forest().find(reference);
        if (changed.isPresent()) return changed.orElseThrow();
        return touching(state, reference.region()).stream().filter(tree -> reference.matches(tree.declaration())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown exact tree generation"));
    }
}
