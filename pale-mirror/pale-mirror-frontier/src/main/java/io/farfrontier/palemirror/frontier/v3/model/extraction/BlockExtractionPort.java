package io.farfrontier.palemirror.frontier.v3.model.extraction;

/** Adapter boundary. An owner must durably retain preparation before admitting mutation. */
public interface BlockExtractionPort {
    /** POSTCONDITION_PRESENT is an observation, never proof of who removed the source. */
    enum Result { APPLIED, POSTCONDITION_PRESENT, UNAVAILABLE, SOURCE_CHANGED, TOOL_CHANGED }

    /** Reads native loot and source state, without changing blocks, actors or inventories. */
    BlockExtraction prepare(String operationId,
                            io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution,
                            io.farfrontier.palemirror.frontier.v3.model.BlockPosition target,
                            BlockExtraction.Definition definition);

    /** A durable owner supplies its exact preparation; no loot reroll or resource issue here. */
    Result apply(BlockExtraction prepared);
}
