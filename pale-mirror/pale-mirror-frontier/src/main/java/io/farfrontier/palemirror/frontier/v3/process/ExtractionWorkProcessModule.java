package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Registered owner, not a manual server tick branch. */
final class ExtractionWorkProcessModule implements FrontierWorldProcessModule {
    static final Set<String> COMMANDS = Set.of("frontier.extraction_geology_invalidated.v2", "frontier.extraction_geometry_changed", "frontier.extraction_source_changed", "frontier.extraction_source_boundary", "frontier.extraction_hot_prepared",
            "frontier.extraction_hot_observed", "frontier.extraction_hot_cancelled", "frontier.extraction_hand_custody_observed", "frontier.extraction_retired_effect_observed", "frontier.extraction_death_settled");
    static final Set<String> TYPES = Set.of("frontier.extraction_geology_invalidated.v2", "frontier.extraction_area_extended.v3", "frontier.extraction_frontier_opened", "frontier.extraction_work_started", "frontier.extraction_work_progressed",
            "frontier.extraction_geometry_changed", "frontier.extraction_source_changed", "frontier.extraction_source_boundary", "frontier.extraction_hot_prepared",
            "frontier.extraction_hot_observed", "frontier.extraction_hot_cancelled", "frontier.extraction_hand_custody_observed", "frontier.extraction_retired_effect_observed", "frontier.extraction_death_settled");
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor("extraction",
            COMMANDS, Set.of(ExtractionContinuation.PROGRESS, ExtractionContinuation.REVIEW), TYPES,
            java.util.stream.Stream.concat(TYPES.stream(), Set.of("frontier.actor_movement_interrupted", "frontier.actor_movement_started", "kernel.schedule_created",
                    "frontier.ambient_lease_transition", "frontier.reference_mutation_closed", "kernel.schedule_rescheduled", "kernel.schedule_cancelled").stream()).collect(java.util.stream.Collectors.toUnmodifiableSet()), TYPES);
    @Override public List<ScheduledAction> initialSchedules(FrontierBootstrap bootstrap) {
        return GrayboxQuarryPlan.initial(bootstrap).deposits().values().stream()
                .map(deposit -> deposit.site().settlementId()).distinct().sorted()
                .map(home -> ExtractionContinuation.review(home, bootstrap.ruleset().extraction().reviewTicks())).toList();
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case ExtractionGeologyInvalidated invalidated -> invalidated.apply(state, event.subject());
            case ExtractionAreaExtended extended -> ExtractionAreaPlanning.apply(state, event.subject(), extended, event.revision().value());
            case ExtractionFrontierOpened opened -> ExtractionDevelopment.apply(state, event.subject(), opened, event.instant().ticks());
            case ExtractionGeometryChanged changed -> changed.apply(state, event.subject());
            case ExtractionWorkStarted start -> {
                if (!event.subject().equals(start.work().id())) throw new IllegalArgumentException("mining admission has a foreign subject");
                yield ExtractionWorkAdmission.start(state, start, event.instant().ticks());
            }
            case ExtractionWorkProgressed step -> ExtractionColdWork.apply(state, event.subject(), step, event.instant().ticks());
            case ExtractionSourceBoundary boundary -> state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(
                    ExtractionSourceCustody.apply(state, event.subject(), boundary, event.revision().value())));
            case ExtractionSourceChanged changed -> ExtractionExternalChanges.apply(state, event.subject(), changed, event.revision().value(), event.instant().ticks());
            case ExtractionHotPrepared prepared -> ExtractionPhysicalStateSupport.prepare(state, event.subject(), prepared, event.instant().ticks());
            case ExtractionHotCancelled cancelled -> ExtractionPhysicalStateSupport.cancelled(state, event.subject(), cancelled);
            case ExtractionHotObserved observed -> ResidentActivityProcess.retargetHotResident(
                    ExtractionPhysicalStateSupport.observed(state, event.subject(), observed, event.revision().value(), event.instant().ticks()),
                    observed.step().observation().actuation().execution().actorId(), event.instant().ticks());
            case ExtractionHandCustodyObserved hand -> ExtractionPhysicalStateSupport.handCustody(state, event.subject(), hand);
            case ExtractionRetiredEffectObserved retired -> ExtractionPhysicalStateSupport.retired(state, event.subject(), retired,
                    event.revision().value(), event.instant().ticks());
            case ExtractionDeathSettled settled -> ExtractionDeathResources.settle(state, event.subject(), settled);
            default -> throw new IllegalArgumentException("extraction owner rejects undeclared payload");
        };
    }
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        try {
            var events = new ArrayList<ProposedEvent>();
            SubjectId id;
            switch (command.payload()) {
                case ExtractionGeologyInvalidated invalidated -> {
                    id = ExtractionGeologyInvalidated.OWNER; invalidated.apply(state, id);
                }
                case ExtractionGeometryChanged changed -> {
                    id = changed.predecessor().key().owner();
                    changedContinuations(state, changed.apply(state, id), events, command.submittedAt().ticks());
                }
                case ExtractionSourceChanged changed -> {
                    id = changed.target().key().owner();
                    var preview = ExtractionExternalChanges.apply(state, id, changed, command.expectedRevision().next().value(), command.submittedAt().ticks());
                    preview = ExtractionDevelopmentContinuation.appendAfterObservation(preview, id, events, command.submittedAt().ticks());
                    changedContinuations(state, preview, events, command.submittedAt().ticks());
                    for (var job : state.extractionSites().work().values()) {
                        if (!preview.extractionSites().work().containsKey(job.id())) {
                            events.add(new ProposedEvent(job.id(), new ScheduleEffect.Cancelled(ExtractionContinuation.at(job.id(), 0).id())));
                            events.add(ResidentActivityProcess.wakeAfterActivity(job.execution().actorId(), command.submittedAt().ticks()));
                        }
                    }
                }
                case ExtractionSourceBoundary boundary -> {
                    id = boundary.region().objectId();
                    ExtractionSourceCustody.apply(state, id, boundary, command.expectedRevision().next().value());
                }
                case ExtractionHotPrepared prepared -> {
                    id = prepared.jobId(); ExtractionPhysicalStateSupport.prepare(state, id, prepared, command.submittedAt().ticks());
                }
                case ExtractionHotCancelled cancelled -> {
                    id = cancelled.jobId(); ExtractionPhysicalStateSupport.cancelled(state, id, cancelled);
                    events.add(ExtractionContinuation.wake(id, command.submittedAt().ticks()));
                    events.add(ResidentActivityProcess.wakeAfterActivity(cancelled.step().observation().actuation().execution().actorId(), command.submittedAt().ticks()));
                }
                case ExtractionHotObserved observed -> {
                    id = observed.jobId();
                    var preview = ExtractionPhysicalStateSupport.observed(state, id, observed, command.expectedRevision().next().value(), command.submittedAt().ticks());
                    var original = state.extractionSites().work().get(id);
                    if (!(observed.step() instanceof io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPhysicalStep.BlockWork))
                        events.addAll(ConfirmedContainerMutationPlan.close(preview,
                                List.of(ExtractionServiceAccess.identity(state, original).pointId()), command.expectedRevision().next().value()));
                    preview = ExtractionDevelopmentContinuation.appendAfterObservation(preview, original.siteId(), events, command.submittedAt().ticks());
                    if (preview.extractionSites().work().containsKey(id)) {
                        if (events.stream().noneMatch(proposed -> proposed.subject().equals(observed.jobId()) && proposed.payload() instanceof ScheduleEffect))
                            events.add(ExtractionContinuation.wake(id, command.submittedAt().ticks()));
                    }
                    else {
                        events.add(new ProposedEvent(id, new ScheduleEffect.Cancelled(ExtractionContinuation.at(id, 0).id())));
                        events.add(ResidentActivityProcess.wakeAfterActivity(observed.step().observation().actuation().execution().actorId(), command.submittedAt().ticks()));
                    }
                }
                case ExtractionHandCustodyObserved hand -> {
                    id = hand.jobId(); ExtractionPhysicalStateSupport.handCustody(state, id, hand);
                    events.add(ExtractionContinuation.wake(id, command.submittedAt().ticks()));
                }
                case ExtractionRetiredEffectObserved retired -> {
                    id = retired.effect().jobId();
                    var original = state.extractionSites().work().get(id);
                    var preview = ExtractionPhysicalStateSupport.retired(state, id, retired,
                            command.expectedRevision().next().value(), command.submittedAt().ticks());
                    if (!(retired.effect().step() instanceof io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPhysicalStep.BlockWork))
                        events.addAll(ConfirmedContainerMutationPlan.close(preview,
                                List.of(ExtractionServiceAccess.identity(state, original).pointId()), command.expectedRevision().next().value()));
                    events.add(new ProposedEvent(id, new ScheduleEffect.Cancelled(ExtractionContinuation.at(id, 0).id())));
                }
                case ExtractionDeathSettled settled -> {
                    id = settled.identity().execution().activityOwnerId();
                    ExtractionDeathResources.settle(state, id, settled);
                    events.add(new ProposedEvent(id, new ScheduleEffect.Cancelled(ExtractionContinuation.at(id, 0).id())));
                }
                default -> throw new IllegalArgumentException("extraction rejects an undeclared native command");
            }
            events.addFirst(new ProposedEvent(id, command.payload()));
            return new CommandPlan.Accepted(List.copyOf(events));
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }
    private static void changedContinuations(FrontierWorldState before, FrontierWorldState after,
            List<ProposedEvent> events, long tick) {
        for (var job : before.extractionSites().work().values().stream()
                .sorted(Comparator.comparing(io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionWork::id)).toList()) {
            var movement = before.actorMovements().get(job.execution().actorId());
            if (job.equals(after.extractionSites().work().get(job.id()))
                    && Objects.equals(movement, after.actorMovements().get(job.execution().actorId()))) continue;
            if (movement != null && !after.actorMovements().containsKey(job.execution().actorId()))
                events.add(new ProposedEvent(job.execution().actorId(), new ScheduleEffect.Cancelled(
                        ActorMovementProcess.progress(movement, Math.addExact(movement.issuedAtTick(), 1)).id())));
            if (after.extractionSites().work().containsKey(job.id())) events.add(ExtractionContinuation.wake(job.id(), tick));
        }
    }
}
