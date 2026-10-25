package io.farfrontier.palemirror.frontier.v3.model.extraction;

/** Physical mechanics only. The family retains prepared effects and commits their exact receipts. */
public interface BlockExtractionBatchPort {
    BlockExtraction prepareChild(BlockExtractionBatch declaration, int effectIndex);
    BlockExtractionBatch prepare(BlockExtractionBatch declaration);
    BlockExtractionPort.Result apply(BlockExtractionBatch prepared, int effectIndex);
}
