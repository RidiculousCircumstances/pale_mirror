package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

/**
 * Immutable, engine-owned view of one continuation action.
 *
 * <p>This adapter never retains the queue or copies timing into a lease/model. It merely selects
 * exactly one descriptor-named action from the current checkpoint for a single server-thread
 * submission; the engine revalidates the same complete action atomically before committing.</p>
 */
final class FrontierV3ContinuationBinding {
    private FrontierV3ContinuationBinding() { }

    static ScheduledAction require(CheckpointImage checkpoint, SubjectId subject, String kind) {
        var matches = checkpoint.schedules().stream()
                .filter(action -> action.subject().equals(subject))
                .filter(action -> action.kind().equals(kind)).toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException("continuation binding must resolve exactly one engine action");
        }
        return matches.getFirst();
    }

}
