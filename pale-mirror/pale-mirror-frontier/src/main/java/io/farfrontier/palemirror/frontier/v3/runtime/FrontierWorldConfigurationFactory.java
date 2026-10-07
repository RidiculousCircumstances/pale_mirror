package io.farfrontier.palemirror.frontier.v3.runtime;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.EngineLimits;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessRegistry;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledActionPlanner;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionCommitter;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrap;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjectionCompiler;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateTransitionValidator;
import io.farfrontier.palemirror.frontier.v3.model.KernelQuarantineObserved;
import io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldProcessCodecs;
import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Composes the kernel's initial state, scheduler and failure reporter. */
final class FrontierWorldConfigurationFactory {
    private FrontierWorldConfigurationFactory() { }

    static DeterministicProcessRegistry processRegistry(PayloadCodecs codecs) {
        FrontierWorldProcessCatalog.requirePhysicalLifecycleComposition();
        List<DeterministicProcessDescriptor> descriptors = FrontierWorldProcessCatalog.descriptors();
        DeterministicProcessRegistry registry = new DeterministicProcessRegistry(descriptors, codecs,
                FrontierWorldProcessCodecs.typesByProcess());
        FrontierDurationProcessDriverRegistry.requireCurrentComposition(descriptors, FrontierWorldProcessCatalog.scheduledKinds(),
                Set.of(SceneCauseKind.values()), codecs.types());
        return registry;
    }

    static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> create(FrontierBootstrap bootstrap) {
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        ScheduledActionPlanner<FrontierWorldState> scheduler = new ScheduledActionPlanner<>() {
            @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
                return FrontierWorldRuntimeDefinition.planScheduled(state, action);
            }
            @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action,
                                                      SimInstant currentInstant) {
                return FrontierWorldRuntimeDefinition.planScheduled(state, action, currentInstant);
            }
            @Override public boolean held(FrontierWorldState state, ScheduledAction action) {
                return FrontierWorldRuntimeDefinition.scheduledHeld(state, action);
            }
            @Override public java.util.Set<SubjectId> holdWakeKeys(FrontierWorldState state, ScheduledAction action) {
                return FrontierWorldRuntimeDefinition.holdWakeKeys(state, action);
            }
            @Override public java.util.Set<SubjectId> wakeKeys(FrontierWorldState previous,
                                                                 FrontierWorldState next, FrontierEvent event) {
                return FrontierWorldRuntimeDefinition.wakeKeys(previous, next, event);
            }
            @Override public List<ScheduledAction> retiredBy(FrontierWorldState previous, FrontierWorldState next,
                                                               FrontierEvent event, Supplier<List<ScheduledAction>> pending) {
                return FrontierWorldRuntimeDefinition.retiredSchedules(previous, next, event, pending);
            }
        };
        return new FrontierEngineConfiguration<>(bootstrap.worldId(), initial, SimInstant.ZERO, FrontierWorldRuntimeDefinition::planCommand,
                scheduler, FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(bootstrap), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096, 4_096), FrontierWorldProcessCatalog.initialSchedule(bootstrap),
                TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE)
                .withKernelQuarantineReporter((state, world, causes, instant, boundary, failure) -> Optional.of(new ProposedEvent(
                        new SubjectId(world.value()), new KernelQuarantineObserved(new SubjectId(world.value()),
                        switch (boundary) {
                            case COMMAND_TRANSACTION -> KernelQuarantineObserved.Producer.COMMAND_TRANSACTION;
                            case DUE_CAPACITY -> KernelQuarantineObserved.Producer.DUE_CAPACITY;
                            case DUE_TRANSACTION -> KernelQuarantineObserved.Producer.DUE_TRANSACTION;
                        }, failure.getClass().getSimpleName()))));
    }
}
