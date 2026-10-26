package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** One causal player action over an explicitly composed inventory; members retain their identities. */
public record ContainerPlayerEditObserved(UUID playerId, UUID interactionId, List<Member> members,
        Optional<PlayerReturn> returning, Optional<ExactItemContainerChange> exactChange) implements FrontierPayload {
    public ContainerPlayerEditObserved(UUID playerId, UUID interactionId, List<Member> members, Optional<PlayerReturn> returning) {
        this(playerId, interactionId, members, returning, Optional.empty());
    }
    public record Member(SubjectId containerId, Map<ContainerSectionId, SubjectId> accounts, SubjectId ownerId, long epoch,
            List<FungiblePhysicalObservation.Stack> before, List<FungiblePhysicalObservation.Stack> after) {
        public Member {
            Objects.requireNonNull(containerId); accounts = Map.copyOf(accounts); Objects.requireNonNull(ownerId);
            if (accounts.isEmpty() || accounts.size() > 4 || new HashSet<>(accounts.values()).size() != accounts.size())
                throw new IllegalArgumentException("composed member lacks distinct compartment account declarations");
            before = List.copyOf(before); after = List.copyOf(after);
            if (epoch < 1) throw new IllegalArgumentException("composed edit lacks member custody epoch");
            validate(containerId, before); validate(containerId, after);
        }
        public Member(SubjectId container, SubjectId account, SubjectId owner, long epoch,
                List<FungiblePhysicalObservation.Stack> before, List<FungiblePhysicalObservation.Stack> after) {
            this(container, Map.of(ContainerSectionId.STOCK, account), owner, epoch, before, after);
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
        Objects.requireNonNull(playerId); Objects.requireNonNull(interactionId); Objects.requireNonNull(returning); Objects.requireNonNull(exactChange);
        members = List.copyOf(members);
        if (members.isEmpty() || members.size() > 54
                || members.stream().map(Member::containerId).distinct().count() != members.size()
                || members.stream().flatMap(member -> member.accounts().values().stream()).distinct().count()
                    != members.stream().mapToLong(member -> member.accounts().size()).sum())
            throw new IllegalArgumentException("composed edit requires distinct explicitly declared members");
        if (exactChange.isPresent()) {
            if (returning.isPresent()) throw new IllegalArgumentException("mixed individual and fungible player return");
            var change = exactChange.orElseThrow();
            validateEndpoint(change.after().custody(), playerId, members);
            if (change instanceof ExactItemContainerChange.Transfer transfer) {
                validateEndpoint(transfer.before().custody(), playerId, members);
                if (!(transfer.before().custody() instanceof InventoryCustody.ContainerSlot)
                        && !(transfer.after().custody() instanceof InventoryCustody.ContainerSlot))
                    throw new IllegalArgumentException("individual edit must involve a declared container");
            }
        }
    }
    private static void validateEndpoint(InventoryCustody custody, UUID player, List<Member> members) {
        if (custody instanceof InventoryCustody.Player held && held.playerId().equals(player)) return;
        if (custody instanceof InventoryCustody.ContainerSlot slot
                && members.stream().anyMatch(m -> m.containerId().equals(slot.containerId()))) return;
        throw new IllegalArgumentException("individual edit has an undeclared endpoint");
    }
    @Override public String type() { return "frontier.container_player_edit_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
