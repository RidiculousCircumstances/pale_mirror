package io.farfrontier.palemirror.frontier.v3.model.forestry;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.util.*;

/** Tree-owned membership proof for a common bounded extraction batch. No resource credit here. */
public record TreeFelling(TreeReference tree, long sourceRevision, BlockExtractionBatch batch) {
    public TreeFelling { Objects.requireNonNull(tree); Objects.requireNonNull(batch);
        if (sourceRevision < 1) throw new IllegalArgumentException("felling needs the exact source revision"); }
    public static TreeFelling prepareKnown(ForestTree tree, ActorExecutionId execution, SurfaceAnchor station,
                                           String operation, ForestryRules rules) {
        if (tree.remaining() < 1 || tree.remaining() > Math.min(BlockExtractionBatch.MAX_BLOCKS, rules.cargoItems()))
            throw new IllegalArgumentException("tree does not fit the supported extraction/cargo envelope");
        var descriptor = tree.declaration(); var remaining = new ArrayList<Integer>();
        for (int i = 0; i < descriptor.logs().size(); i++) if (tree.present(i)) remaining.add(i);
        // The contact remains until last; no persistent upper-trunk remnant from our own cut.
        remaining.sort(Comparator.<Integer>comparingInt(i -> descriptor.logs().position(descriptor.origin(), i).y()).reversed()
                .thenComparingInt(Integer::intValue));
        var contact = descriptor.logs().position(descriptor.origin(), remaining.getLast());
        var effects = new ArrayList<BlockExtraction>();
        for (int log : remaining) {
            var source = descriptor.logs().block(log);
            var definition = rules.requireHarvest(descriptor.species(), source.kind()).definition(source, rules.tool());
            effects.add(BlockExtraction.prepareKnown(operation + "/log-" + log, execution,
                    descriptor.logs().position(descriptor.origin(), log), definition, source));
        }
        var batch = new BlockExtractionBatch(operation, execution, station, contact, effects); batch.totalOutput();
        return new TreeFelling(tree.reference(), tree.revision(), batch);
    }
    public void requireCurrent(ForestTree source, ForestryRules rules) {
        if (!tree.matches(source.declaration()) || source.revision() != sourceRevision
                || !equals(prepareKnown(source, batch.execution(), batch.workStation(), batch.operationId(), rules)))
            throw new IllegalArgumentException("felling replaced its exact current object membership or consequence");
    }
    /** A consequence suffix is valid only after this exact already-settled prefix.
     * External edits advance the source revision and invalidate that proof. */
    public void requireProgress(ForestTree source, int applied, ForestryRules rules) {
        if (!tree.matches(source.declaration()) || applied < 0 || applied >= batch.effects().size()
                || source.revision() != Math.addExact(sourceRevision, applied))
            throw new IllegalArgumentException("felling source generation/revision no longer matches its settled prefix");
        var indices = new HashSet<Integer>();
        for (int i = 0; i < batch.effects().size(); i++) {
            var effect = batch.effects().get(i);
            int log = source.declaration().logs().indexOf(source.declaration().origin(), effect.target());
            if (log < 0 || !indices.add(log) || !effect.definition().before().equals(source.declaration().logs().block(log))
                    || i < applied && (source.present(log) || !source.extracted(log) || !source.extractionOperation(log).equals(Optional.of(effect.operationId())))
                    || i >= applied && !source.present(log))
                throw new IllegalArgumentException("felling consequence replaced a source or settled log");
            var definition = rules.requireHarvest(source.declaration().species(), effect.definition().before().kind()).definition(effect.definition().before(), rules.tool());
            if (!effect.equals(io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.prepareKnown(
                    batch.operationId() + "/log-" + log, batch.execution(), effect.target(), definition, effect.definition().before())))
                throw new IllegalArgumentException("felling consequence differs from its declared harvest rule");
        }
        if (source.remaining() != batch.effects().size() - applied)
            throw new IllegalArgumentException("felling suffix omitted available source logs");
    }

}
