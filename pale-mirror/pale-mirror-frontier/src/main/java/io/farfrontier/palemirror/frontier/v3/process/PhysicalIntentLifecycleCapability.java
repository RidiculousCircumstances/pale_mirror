package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryAsset;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;

import java.util.Set;

/**
 * Closed executable behavior supplied by the canonical family of one durable lifecycle owner.
 * The explicit owner on the intent selects this behavior; compatible kinds are validation only.
 */
interface PhysicalIntentLifecycleCapability {
    PhysicalIntentLifecycleOwner owner();

    Set<PhysicalIntentKind> compatibleKinds();

    /** Explicit executable terminal/late-input policy supplied by this owner's family. */
    PhysicalIntentLifecycleRetirementPolicy retirementPolicy();

    PhysicalIntentRetirementAccount retirementAccount();

    /** Family-supplied fence asset; common lifecycle storage may validate it but never infer it from kind. */
    FencedRecoveryAsset recoveryAsset(PhysicalIntent intent);

    CommandPlan planPrepared(FrontierWorldState state, FrontierCommand command, PhysicalIntentPrepared prepared);

    CommandPlan planTransition(FrontierWorldState state, FrontierCommand command, PhysicalIntent intent,
                               PhysicalIntentTransition transition);

    FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent);

    FrontierWorldState reduceTransition(FrontierWorldState state, SubjectId subject, PhysicalIntent intent,
                                        PhysicalIntentTransition transition);
}
