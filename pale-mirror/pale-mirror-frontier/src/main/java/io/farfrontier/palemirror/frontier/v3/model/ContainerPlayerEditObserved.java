package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** One causal player action over an explicitly composed inventory; members retain their identities. */
public record ContainerPlayerEditObserved(UUID playerId, UUID interactionId, List<Member> members,
        Optional<PlayerReturn> returning, Optional<ExactItemIngress> exactIngress) implements FrontierPayload {
    public ContainerPlayerEditObserved(UUID playerId, UUID interactionId, List<Member> members, Optional<PlayerReturn> returning) {
        this(playerId, interactionId, members, returning, Optional.empty());
    }
    public record Member(SubjectId containerId, SubjectId accountId, SubjectId ownerId, long epoch,
            List<FungiblePhysicalObservation.Stack> before, List<FungiblePhysicalObservation.Stack> after) {
        public Member {
            Objects.requireNonNull(containerId); Objects.requireNonNull(accountId); Objects.requireNonNull(ownerId);
            before = List.copyOf(before); after = List.copyOf(after);
            if (epoch < 1) throw new IllegalArgumentException("composed edit lacks member custody epoch");
            validate(containerId, before); validate(containerId, after);
        }
        private static void validate(SubjectId container, List<FungiblePhysicalObservation.Stack> stacks) {
            if (stacks.size() > 54 || stacks.stream().map(FungiblePhysicalObservation.Stack::address).distinct().count() != stacks.size()
                    || stacks.stream().anyMatch(s -> !(s.address() instanceof PhysicalStackAddress.ContainerSlot slot)
                            || !slot.slot().containerId().equals(container)))
                throw new IllegalArgumentException("composed edit has a foreign or duplicate member address");
        }
    }
    public record PlayerReturn(SubjectId bindingId, SubjectId accountId, long epoch, int slot,
            String itemKind, int quantity, int remaining) {
        public PlayerReturn {
            Objects.requireNonNull(bindingId); Objects.requireNonNull(accountId); Objects.requireNonNull(itemKind);
            if (epoch < 1 || slot < 0 || slot > 255 || quantity < 1 || quantity > 64 || remaining < 0 || remaining > quantity)
                throw new IllegalArgumentException("composed edit lacks exact player source/postimage");
        }
    }
    public ContainerPlayerEditObserved {
        Objects.requireNonNull(playerId); Objects.requireNonNull(interactionId); Objects.requireNonNull(returning); Objects.requireNonNull(exactIngress);
        members = List.copyOf(members);
        if (members.isEmpty() || members.size() > 54
                || members.stream().map(Member::containerId).distinct().count() != members.size()
                || members.stream().map(Member::accountId).distinct().count() != members.size())
            throw new IllegalArgumentException("composed edit requires distinct explicitly declared members");
        if (exactIngress.isPresent()) {
            var transfer = exactIngress.orElseThrow(); var declared = members;
            if (returning.isPresent() || (transfer instanceof ExactItemIngress.Returned returned
                    && !returned.before().custody().equals(new InventoryCustody.Player(playerId)))
                    || !(transfer.after().custody() instanceof InventoryCustody.ContainerSlot destination)
                    || declared.stream().noneMatch(m -> m.containerId().equals(destination.containerId())))
                throw new IllegalArgumentException("exact ingress requires this player and an explicitly declared destination");
        }
    }
    @Override public String type() { return "frontier.container_player_edit_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
