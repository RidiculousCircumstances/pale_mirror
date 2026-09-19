package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.CommandRejection;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.RejectionCode;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryAsset;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticTuple;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentRecoveryDiagnosticProducer;

import java.util.Set;

/** Small family-local base for an explicit lifecycle policy; it contains no family routing. */
abstract class AbstractPhysicalIntentLifecycleCapability implements PhysicalIntentLifecycleCapability {
    private final PhysicalIntentLifecycleOwner owner;
    private final Set<PhysicalIntentKind> kinds;
    private final PhysicalIntentLifecycleRetirementPolicy retirementPolicy;
    private final PhysicalIntentRetirementAccount retirementAccount;
    private final PhysicalIntentLifecycleDeclaration declaration;
    private final PhysicalIntentResolvedRetentionPolicy resolvedRetentionPolicy;
    private final PhysicalIntentRecoveryDiagnosticProducer recoveryDiagnosticProducer;

    AbstractPhysicalIntentLifecycleCapability(PhysicalIntentLifecycleDeclaration declaration,
                                              PhysicalIntentLifecycleRetirementPolicy retirementPolicy,
                                              PhysicalIntentRetirementAccount retirementAccount,
                                              PhysicalIntentResolvedRetentionPolicy resolvedRetentionPolicy,
                                              PhysicalIntentRecoveryDiagnosticProducer recoveryDiagnosticProducer) {
        this.declaration = java.util.Objects.requireNonNull(declaration, "physical lifecycle declaration");
        this.owner = declaration.owner();
        this.kinds = declaration.kinds();
        this.retirementPolicy = retirementPolicy;
        this.retirementAccount = retirementAccount;
        this.resolvedRetentionPolicy = java.util.Objects.requireNonNull(resolvedRetentionPolicy, "physical lifecycle resolved retention policy");
        this.recoveryDiagnosticProducer = java.util.Objects.requireNonNull(recoveryDiagnosticProducer, "physical lifecycle recovery diagnostic producer");
    }

    @Override public final PhysicalIntentLifecycleOwner owner() { return owner; }
    @Override public final Set<PhysicalIntentKind> compatibleKinds() { return kinds; }
    @Override public final PhysicalIntentLifecycleRetirementPolicy retirementPolicy() { return retirementPolicy; }
    @Override public final PhysicalIntentRetirementAccount retirementAccount() { return retirementAccount; }
    @Override public final PhysicalIntentResolvedRetentionPolicy resolvedRetentionPolicy() { return resolvedRetentionPolicy; }
    @Override public PhysicalIntentLifecycleDeclaration declaration() { return declaration; }
    @Override public FencedRecoveryAsset recoveryAsset(PhysicalIntent intent) {
        throw new IllegalArgumentException("physical lifecycle owner has no declared recovery asset: " + owner.stableId());
    }
    @Override public final DiagnosticTuple recoveryUnknownDiagnostic(PhysicalIntent intent) {
        return recoveryDiagnosticProducer.stamp(intent);
    }
    @Override public final PhysicalIntentRecoveryDiagnosticProducer recoveryDiagnosticProducer() { return recoveryDiagnosticProducer; }

    @Override public CommandPlan planPrepared(FrontierWorldState state, FrontierCommand command, PhysicalIntentPrepared prepared) {
        return rejected("physical executor cannot prepare " + owner.stableId());
    }

    @Override public CommandPlan planTransition(FrontierWorldState state, FrontierCommand command, PhysicalIntent intent,
                                                PhysicalIntentTransition transition) {
        return rejected("physical executor cannot transition " + owner.stableId());
    }

    @Override public FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        throw new IllegalArgumentException("physical lifecycle owner does not admit preparation: " + owner.stableId());
    }

    @Override public FrontierWorldState reduceTransition(FrontierWorldState state, SubjectId subject, PhysicalIntent intent,
                                                         PhysicalIntentTransition transition) {
        throw new IllegalArgumentException("physical lifecycle owner does not admit transition: " + owner.stableId());
    }

    final CommandPlan.Rejected rejected(String message) {
        return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, message));
    }
}
