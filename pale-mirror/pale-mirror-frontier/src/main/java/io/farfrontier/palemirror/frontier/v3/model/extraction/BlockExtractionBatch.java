package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.*;

/** Bounded consequences of one completed interaction with a family-declared composite object.
 * The owning capability proves source membership; this common value never recognizes a tree,
 * selects work or increases ordinary single-block interaction reach. */
public record BlockExtractionBatch(String operationId, ActorExecutionId execution,
                                   SurfaceAnchor workStation, BlockPosition contact,
                                   List<BlockExtraction> effects) {
    public static final int MAX_BLOCKS = 64;
    public BlockExtractionBatch {
        Objects.requireNonNull(execution); Objects.requireNonNull(workStation); Objects.requireNonNull(contact);
        effects = List.copyOf(effects);
        if (operationId == null || operationId.isBlank() || operationId.length() > 256
                || effects.isEmpty() || effects.size() > MAX_BLOCKS
                || effects.stream().anyMatch(effect -> !effect.execution().equals(execution))
                || effects.stream().map(BlockExtraction::target).distinct().count() != effects.size()
                || effects.stream().map(BlockExtraction::operationId).distinct().count() != effects.size()
                || effects.stream().noneMatch(effect -> effect.target().equals(contact)))
            throw new IllegalArgumentException("invalid bounded composite extraction declaration");
    }
    public List<BlockExtraction.Output> totalOutput() {
        var quantities = new TreeMap<String, Integer>();
        for (var effect : effects) for (var output : effect.output()) quantities.merge(output.itemKind(), output.quantity(), Math::addExact);
        if (quantities.size() > BlockExtraction.MAX_OUTPUTS || quantities.values().stream().anyMatch(quantity -> quantity > 64))
            throw new IllegalArgumentException("composite extraction exceeds a bounded retained resource part");
        return quantities.entrySet().stream().map(entry -> new BlockExtraction.Output(entry.getKey(), entry.getValue())).toList();
    }
}
