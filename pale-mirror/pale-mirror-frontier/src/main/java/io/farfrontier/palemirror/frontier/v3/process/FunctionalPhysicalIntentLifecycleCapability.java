package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryAsset;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;


/** Wiring adapter only; every callback is supplied by the owning family module. */
final class FunctionalPhysicalIntentLifecycleCapability extends AbstractPhysicalIntentLifecycleCapability {
    @FunctionalInterface interface PreparedPlanner { CommandPlan apply(FrontierWorldState state, FrontierCommand command, PhysicalIntentPrepared prepared); }
    @FunctionalInterface interface TransitionPlanner { CommandPlan apply(FrontierWorldState state, FrontierCommand command, PhysicalIntent intent, PhysicalIntentTransition transition); }
    @FunctionalInterface interface PreparedReducer { FrontierWorldState apply(FrontierWorldState state, SubjectId subject, PhysicalIntent intent); }
    @FunctionalInterface interface TransitionReducer { FrontierWorldState apply(FrontierWorldState state, SubjectId subject, PhysicalIntent intent, PhysicalIntentTransition transition); }
    @FunctionalInterface interface RecoveryAssetResolver { FencedRecoveryAsset apply(PhysicalIntent intent); }

    private final PreparedPlanner preparedPlanner;
    private final TransitionPlanner transitionPlanner;
    private final PreparedReducer preparedReducer;
    private final TransitionReducer transitionReducer;
    private final RecoveryAssetResolver recoveryAssetResolver;

    FunctionalPhysicalIntentLifecycleCapability(PhysicalIntentLifecycleDeclaration declaration,
                                                PreparedPlanner preparedPlanner, TransitionPlanner transitionPlanner,
                                                PreparedReducer preparedReducer, TransitionReducer transitionReducer,
                                                PhysicalIntentLifecycleRetirementPolicy retirementPolicy,
                                                RecoveryAssetResolver recoveryAssetResolver,
                                                PhysicalIntentRetirementAccount retirementAccount,
                                                PhysicalIntentResolvedRetentionPolicy resolvedRetentionPolicy) {
        super(declaration, retirementPolicy, retirementAccount, resolvedRetentionPolicy);
        this.preparedPlanner = preparedPlanner;
        this.transitionPlanner = transitionPlanner;
        this.preparedReducer = preparedReducer;
        this.transitionReducer = transitionReducer;
        this.recoveryAssetResolver = recoveryAssetResolver;
    }

    @Override public CommandPlan planPrepared(FrontierWorldState state, FrontierCommand command, PhysicalIntentPrepared prepared) {
        return preparedPlanner.apply(state, command, prepared);
    }
    @Override public CommandPlan planTransition(FrontierWorldState state, FrontierCommand command, PhysicalIntent intent,
                                                PhysicalIntentTransition transition) {
        return transitionPlanner.apply(state, command, intent, transition);
    }
    @Override public FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        return preparedReducer.apply(state, subject, intent);
    }
    @Override public FrontierWorldState reduceTransition(FrontierWorldState state, SubjectId subject, PhysicalIntent intent,
                                                         PhysicalIntentTransition transition) {
        return transitionReducer.apply(state, subject, intent, transition);
    }
    @Override public FencedRecoveryAsset recoveryAsset(PhysicalIntent intent) { return recoveryAssetResolver.apply(intent); }
}
