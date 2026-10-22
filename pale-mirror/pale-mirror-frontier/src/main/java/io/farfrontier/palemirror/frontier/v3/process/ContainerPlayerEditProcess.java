package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Composes existing physical accounting owners atomically, never another inventory authority. */
final class ContainerPlayerEditProcess {
    private ContainerPlayerEditProcess() { }
    static CommandPlan plan(FrontierWorldState state, ContainerPlayerEditObserved edit, FrontierCommand command) {
        var consequences = new ArrayList<ProposedEvent>();
        FrontierWorldState successor;
        try { successor = apply(state, FrontierExecutionSubjects.PHYSICAL_EXECUTOR, edit, command.submittedAt().ticks(), consequences, command); }
        catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        var events = new ArrayList<ProposedEvent>();
        events.add(new ProposedEvent(FrontierExecutionSubjects.PHYSICAL_EXECUTOR, edit));
        // The callback supplies the complete postimage. Keep its exact layout and HOT
        // owner instead of releasing the chest and making the next click await discovery.
        try {
            for (var member : edit.members()) {
                var lease = successor.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(member.containerId()));
                var observed = new ReferenceMutationObserved(member.containerId(), member.epoch(),
                        lease.expectedCanonicalRevision(), lease.expectedReplicaRevision(), command.expectedRevision().next().value(),
                        ReferenceContainerCustody.canonicalFingerprint(successor, member.containerId()));
                successor = ReferenceContainerCustody.observeMutation(successor, observed);
                events.add(new ProposedEvent(member.containerId(), observed));
            }
        }
        catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        events.addAll(consequences);
        return new CommandPlan.Accepted(List.copyOf(events));
    }
    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, ContainerPlayerEditObserved edit) {
        return apply(state, subject, edit, 0, null, null);
    }
    private static FrontierWorldState apply(FrontierWorldState state, SubjectId subject,
            ContainerPlayerEditObserved edit, long now, List<ProposedEvent> consequences, FrontierCommand command) {
        if (!subject.equals(FrontierExecutionSubjects.PHYSICAL_EXECUTOR))
            throw new IllegalArgumentException("composed edit lacks physical executor authority");
        // Validate every predecessor against the same incoming state before publishing any successor.
        for (var member : edit.members()) validate(state, member);
        var next = state;
        if (edit.exactIngress().isPresent()) {
            var transfer = edit.exactIngress().orElseThrow();
            var item = state.inventory().items().get(transfer.itemId());
            if (item == null || !item.custody().equals(transfer.from()))
                throw new IllegalArgumentException("exact ingress differs from its declared canonical source");
            var observation = new ProposedEvent(FrontierWorldStateSupport.itemOwner(state, transfer), transfer);
            if (consequences != null) consequences.addAll(ExactItemDepartureComposition.plan(state, command, transfer.itemId(), observation)
                    .stream().filter(event -> !event.equals(observation)).toList());
            next = FrontierPhysicalProcessModule.reduceCustodyChanged(next, observation.subject(), transfer);
        }
        int returned = 0;
        for (var member : edit.members()) {
            if (member.before().equals(member.after())) continue;
            FrontierPayload fact;
            if (edit.returning().isPresent()) {
                var source = edit.returning().orElseThrow();
                int arrived = quantity(member.after(), source.itemKind()) - quantity(member.before(), source.itemKind());
                if (arrived <= 0) throw new IllegalArgumentException("composed player return must only add its exact resource");
                var resources = next.inventory().fungibleResources();
                var binding = resources.bindings().get(source.bindingId());
                if (binding == null || !binding.accountId().equals(source.accountId()) || binding.authorityEpoch() != source.epoch()
                        || binding.quantity() != source.quantity() - returned || !binding.itemKind().equals(source.itemKind())
                        || !binding.address().equals(new PhysicalStackAddress.PlayerSlot(edit.playerId(), source.slot())))
                    throw new IllegalArgumentException("composed player return lost its exact source binding");
                fact = FungiblePhysicalHandoff.returnObserved(resources, binding, binding.quantity() - arrived,
                        member.accountId(), member.containerId(), member.epoch(), member.after());
                returned = Math.addExact(returned, arrived);
            } else {
                UUID memberInteraction = UUID.nameUUIDFromBytes((edit.interactionId() + "/" + member.containerId().value())
                        .getBytes(StandardCharsets.UTF_8));
                fact = FungibleContainerPlayerEdit.classify(next.inventory().fungibleResources(), member.accountId(),
                        member.containerId(), member.ownerId(), member.epoch(), edit.playerId(), memberInteraction,
                        member.after(), SourceStockClaimLoss.admissibleClaims(next, member.accountId()));
            }
            var planned = switch (fact) {
                case FungibleStackLayoutObserved value -> FrontierWorldPhysicalObservationProcess.planFungibleLayout(next, value);
                case FungibleStockDepartureObserved value -> FrontierWorldPhysicalObservationProcess.planFungibleStockDeparture(next, value, now);
                case FungibleStockContributionObserved value -> FrontierWorldPhysicalObservationProcess.planFungibleStockContribution(next, value, now);
                case FungibleResourceHandoffObserved value -> FrontierWorldPhysicalObservationProcess.planFungibleHandoff(next, value, now);
                default -> throw new IllegalArgumentException("unsupported composed accounting fact");
            };
            if (!(planned instanceof CommandPlan.Accepted accepted))
                throw new IllegalArgumentException("composed member edit rejected: " + planned);
            var first = accepted.events().getFirst();
            next = switch (fact) {
                case FungibleStackLayoutObserved value -> FrontierWorldPhysicalObservationProcess.reduceFungibleLayout(next, first.subject(), value);
                case FungibleStockDepartureObserved value -> FrontierWorldPhysicalObservationProcess.reduceFungibleStockDeparture(next, first.subject(), value);
                case FungibleStockContributionObserved value -> FrontierWorldPhysicalObservationProcess.reduceFungibleStockContribution(next, first.subject(), value);
                case FungibleResourceHandoffObserved value -> FrontierWorldPhysicalObservationProcess.reduceFungibleHandoff(next, first.subject(), value);
                default -> throw new IllegalArgumentException("unsupported composed accounting fact");
            };
            if (consequences != null) consequences.addAll(accepted.events().subList(1, accepted.events().size()));
        }
        if (edit.returning().isPresent() && returned != edit.returning().orElseThrow().quantity() - edit.returning().orElseThrow().remaining())
            throw new IllegalArgumentException("composed return does not balance the exact player postimage");
        return next;
    }
    private static int quantity(List<FungiblePhysicalObservation.Stack> stacks, String kind) {
        return stacks.stream().filter(s -> s.itemKind().equals(kind)).mapToInt(FungiblePhysicalObservation.Stack::quantity).sum();
    }
    private static void validate(FrontierWorldState state, ContainerPlayerEditObserved.Member member) {
        var container = state.inventory().containers().get(member.containerId());
        var lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(member.containerId()));
        if (container == null || !container.ownerId().equals(member.ownerId()) || lease == null
                || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED || lease.authorityEpoch() != member.epoch()
                || !ReferenceContainerCustody.hasOperationalCustody(state, member.containerId())
                || ContainerPhysicalAuthorityComposition.pending(state, member.containerId()))
            throw new IllegalArgumentException("composed edit lacks current declared member authority");
        if (java.util.stream.Stream.concat(member.before().stream(), member.after().stream()).anyMatch(s ->
                ((PhysicalStackAddress.ContainerSlot) s.address()).slot().slot() >= container.slotCount()))
            throw new IllegalArgumentException("composed member slot exceeds declared capacity");
        var resources = state.inventory().fungibleResources();
        var account = resources.accounts().get(member.accountId());
        if (account == null) {
            if (!member.accountId().equals(ReferenceContainerCustody.scopeId(member.containerId())) || !member.before().isEmpty()
                    || resources.accounts().values().stream().anyMatch(a -> a.custody().equals(new ResourceCustody.Container(member.containerId()))))
                throw new IllegalArgumentException("composed edit has a forged empty predecessor");
        } else {
            if (!account.custody().equals(new ResourceCustody.Container(member.containerId()))
                    || !resources.bindings().values().stream().filter(b -> b.accountId().equals(account.id()))
                            .sorted(Comparator.comparing(PhysicalStackBinding::id)).toList()
                            .equals(FungiblePhysicalObservation.bind(resources, account.id(), member.epoch(), member.before())))
                throw new IllegalArgumentException("composed edit predecessor differs from canonical layout");
        }
    }
}
