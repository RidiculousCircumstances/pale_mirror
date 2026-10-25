package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.ForestObservation;
import java.util.*;

/** One registered forest owner for generation, source authority and work continuations. */
final class ForestryProcessModule implements FrontierWorldProcessModule {
    static final SubjectId OWNER = new SubjectId("nature:forest");
    static final Set<String> COMMANDS = Set.of(ForestObservation.Generated.TYPE, ForestObservation.Withdrawn.TYPE, ForestrySourceBoundary.TYPE, ForestryPhysicalEvent.Prepared.TYPE, ForestryPhysicalEvent.Cancelled.TYPE,
            ForestryPhysicalEvent.Observed.TYPE, ForestryPhysicalEvent.HandBoundary.TYPE, ForestryPhysicalEvent.Retired.TYPE, ForestryPhysicalEvent.DeathSettled.TYPE);
    static final Set<String> TYPES = Set.of(ForestObservation.Generated.TYPE, ForestObservation.Withdrawn.TYPE,
            ForestrySourceBoundary.TYPE, ForestryWorkStarted.TYPE, ForestryWorkAdvanced.TYPE, ForestryPhysicalEvent.Prepared.TYPE,
            ForestryPhysicalEvent.Cancelled.TYPE, ForestryPhysicalEvent.Observed.TYPE, ForestryPhysicalEvent.HandBoundary.TYPE, ForestryPhysicalEvent.Retired.TYPE, ForestryPhysicalEvent.DeathSettled.TYPE);
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor("forestry", COMMANDS,
            Set.of(ForestryContinuation.REVIEW, ForestryContinuation.PROGRESS), TYPES,
            java.util.stream.Stream.concat(TYPES.stream(), Set.of("frontier.actor_movement_started", "frontier.reference_mutation_closed", "kernel.schedule_created",
                    "kernel.schedule_rescheduled", "kernel.schedule_cancelled").stream()).collect(java.util.stream.Collectors.toUnmodifiableSet()), TYPES);
    @Override public List<ScheduledAction> initialSchedules(FrontierBootstrap bootstrap) {
        return bootstrap.ruleset().forestry().patches().stream().map(patch -> patch.knownBy()).distinct().sorted()
                .map(home -> ForestryContinuation.review(home, bootstrap.ruleset().forestry().reviewTicks())).toList();
    }
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        try {
            var events = new ArrayList<ProposedEvent>(); SubjectId subject;
            switch (command.payload()) {
                case ForestObservation observation -> {
                    subject = OWNER; var preview = ForestObservation.apply(state, observation, command.expectedRevision().next().value());
                    for (var job : preview.forest().work().values())
                        if (job.target().isPresent() && !ForestryWorkSelection.targetCurrent(preview, job))
                            events.add(ForestryContinuation.wake(job.id(), command.submittedAt().ticks()));
                }
                case ForestrySourceBoundary boundary -> {
                    subject = ForestrySourceCustody.object(boundary.tree());
                    ForestrySourceCustody.apply(state, subject, boundary, command.expectedRevision().next().value());
                    for (var job : state.forest().work().values()) if (job.target().filter(target -> target.tree().equals(boundary.tree())).isPresent())
                        events.add(ForestryContinuation.wake(job.id(), command.submittedAt().ticks()));
                }
                case ForestryPhysicalEvent.Prepared prepared -> {
                    subject = prepared.job(); ForestryPhysicalStateSupport.prepare(state, subject, prepared, command.submittedAt().ticks());
                }
                case ForestryPhysicalEvent.Cancelled cancelled -> {
                    subject = cancelled.job(); ForestryPhysicalStateSupport.cancelled(state, subject, cancelled);
                    events.add(ForestryContinuation.wake(subject, command.submittedAt().ticks()));
                    events.add(ResidentActivityProcess.wakeAfterActivity(cancelled.step().observation().actuation().execution().actorId(), command.submittedAt().ticks()));
                }
                case ForestryPhysicalEvent.Observed observed -> {
                    subject = observed.job(); var original = state.forest().work().get(subject);
                    var preview = ForestryPhysicalStateSupport.observed(state, subject, observed, command.expectedRevision().next().value());
                    if (!(observed.step() instanceof io.farfrontier.palemirror.frontier.v3.model.forestry.ForestryPhysicalStep.Fell))
                        events.addAll(ConfirmedContainerMutationPlan.close(preview, List.of(ForestryServiceAccess.identity(original).pointId()), command.expectedRevision().next().value()));
                    if (preview.forest().work().containsKey(subject)) events.add(ForestryContinuation.wake(subject, command.submittedAt().ticks()));
                    else {
                        events.add(new ProposedEvent(subject, new ScheduleEffect.Cancelled(ForestryContinuation.progress(subject, 0).id())));
                        events.add(ResidentActivityProcess.wakeAfterActivity(original.execution().actorId(), command.submittedAt().ticks()));
                    }
                }
                case ForestryPhysicalEvent.HandBoundary hand -> {
                    subject = hand.job(); ForestryPhysicalStateSupport.handBoundary(state, subject, hand);
                    events.add(ForestryContinuation.wake(subject, command.submittedAt().ticks()));
                }
                case ForestryPhysicalEvent.Retired retired -> {
                    subject = retired.effect().job(); var original = state.forest().work().get(subject);
                    var preview = ForestryPhysicalStateSupport.retired(state, subject, retired, command.expectedRevision().next().value());
                    if (!(retired.effect().step() instanceof io.farfrontier.palemirror.frontier.v3.model.forestry.ForestryPhysicalStep.Fell))
                        events.addAll(ConfirmedContainerMutationPlan.close(preview, List.of(ForestryServiceAccess.identity(original).pointId()), command.expectedRevision().next().value()));
                    events.add(new ProposedEvent(subject, new ScheduleEffect.Cancelled(ForestryContinuation.progress(subject, 0).id())));
                }
                case ForestryPhysicalEvent.DeathSettled settled -> {
                    subject = settled.identity().execution().activityOwnerId(); ForestryDeathResources.settle(state, subject, settled);
                    events.add(new ProposedEvent(subject, new ScheduleEffect.Cancelled(ForestryContinuation.progress(subject, 0).id())));
                }
                default -> throw new IllegalArgumentException("forest owner rejects an undeclared native command");
            }
            events.addFirst(new ProposedEvent(subject, command.payload())); return new CommandPlan.Accepted(List.copyOf(events));
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case ForestObservation observation -> {
                if (!OWNER.equals(event.subject())) throw new IllegalArgumentException("foreign forest observation owner");
                yield ForestObservation.apply(state, observation, event.revision().value());
            }
            case ForestrySourceBoundary boundary -> state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(
                    ForestrySourceCustody.apply(state, event.subject(), boundary, event.revision().value())));
            case ForestryWorkStarted start -> {
                if (!event.subject().equals(start.work().id())) throw new IllegalArgumentException("forest admission has a foreign subject");
                yield ForestryWorkAdmission.start(state, start, event.instant().ticks());
            }
            case ForestryWorkAdvanced step -> ForestryColdWork.apply(state, event.subject(), step, event.instant().ticks());
            case ForestryPhysicalEvent.Prepared prepared -> ForestryPhysicalStateSupport.prepare(state, event.subject(), prepared, event.instant().ticks());
            case ForestryPhysicalEvent.Cancelled cancelled -> ForestryPhysicalStateSupport.cancelled(state, event.subject(), cancelled);
            case ForestryPhysicalEvent.Observed observed -> ResidentActivityProcess.retargetHotResident(
                    ForestryPhysicalStateSupport.observed(state, event.subject(), observed, event.revision().value()),
                    observed.step().observation().actuation().execution().actorId(), event.instant().ticks());
            case ForestryPhysicalEvent.HandBoundary hand -> ForestryPhysicalStateSupport.handBoundary(state, event.subject(), hand);
            case ForestryPhysicalEvent.Retired retired -> ForestryPhysicalStateSupport.retired(state, event.subject(), retired, event.revision().value());
            case ForestryPhysicalEvent.DeathSettled settled -> ForestryDeathResources.settle(state, event.subject(), settled);
            default -> throw new IllegalArgumentException("forest owner rejects an undeclared event");
        };
    }
}
