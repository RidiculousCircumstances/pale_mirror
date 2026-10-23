package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContainerPlayerEditProcessTest {
    private static final UUID PLAYER = new UUID(0, 751), CLICK = new UUID(0, 752);
    private static final SubjectId LEFT = new SubjectId("container:composed-left"), RIGHT = new SubjectId("container:composed-right");
    private static FrontierWorldState fixture() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:composed-test"), 41,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r6")));
        var home = state.bootstrap().settlements().getFirst();
        var station = KnownPedestrianGround.forFrontier(state).at(home.anchor().x() + 22, home.anchor().z() + 22);
        int offset = 0;
        for (var id : List.of(LEFT, RIGHT)) {
            var endpoint = new ShipmentEndpoint.Storage(home.id(), id, station, Optional.empty(), station);
            var container = new ContainerRecord(id, home.id(), 27, ContainerPurpose.RESOURCE_STORAGE, Optional.empty(),
                    Optional.of(new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 0, ResourceStorageRole.WAREHOUSE)));
            state = new ResourceStorageDeclared(container, station.support().offset(++offset, 1, 0)).apply(state, home.id());
            state = state.withInventory(state.inventory().withSurfaceStatus(id, ContainerSurfaceStatus.PREPARED)
                    .withSurfaceStatus(id, ContainerSurfaceStatus.ACTIVE));
            state = ReferenceContainerCustodyFixtures.observedAndHeld(state, id);
        }
        return state;
    }
    private static FungiblePhysicalObservation.Stack stack(SubjectId id, int count) {
        return new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(id, 0)),
                "minecraft:cobblestone", count);
    }
    private static ContainerPlayerEditObserved.Member member(FrontierWorldState state, SubjectId id,
            List<FungiblePhysicalObservation.Stack> before, List<FungiblePhysicalObservation.Stack> after) {
        return new ContainerPlayerEditObserved.Member(id, ReferenceContainerCustody.scopeId(id),
                state.inventory().containers().get(id).ownerId(), 1, before, after);
    }
    private static ContainerPlayerEditObserved gift(FrontierWorldState state) {
        return new ContainerPlayerEditObserved(PLAYER, CLICK, List.of(member(state, LEFT, List.of(), List.of(stack(LEFT, 1))),
                member(state, RIGHT, List.of(), List.of(stack(RIGHT, 16)))), Optional.empty());
    }
    private static FrontierCommand command(ContainerPlayerEditObserved edit) {
        var id = new CommandId("test:container-click");
        return new FrontierCommand(1, id, new WorldId("frontier:composed-test"), new Revision(99), new SimInstant(12),
                FrontierExecutionSubjects.PHYSICAL_EXECUTOR, CauseChain.root(id), edit);
    }
    @Test void exactToolIngressAndAllMemberFencesShareOneCanonicalTransaction() {
        var state = fixture(); var owner = state.inventory().containers().get(LEFT).ownerId();
        var tool = new ExactItemStack(new SubjectId("item:player-tool"), owner, "minecraft:iron_pickaxe", 1,
                new InventoryCustody.ContainerSlot(LEFT, 0));
        state = state.withInventory(state.inventory().store(tool).moveObservedItem(tool.id(), tool.custody(), new InventoryCustody.Player(PLAYER)));
        var edit = new ContainerPlayerEditObserved(PLAYER, CLICK,
                List.of(member(state, LEFT, List.of(), List.of())), Optional.empty(),
                Optional.of(new ExactItemIngress.Returned(tool.withCustody(new InventoryCustody.Player(PLAYER)), tool)));
        var codec = FrontierWorldPayloadCodecs.create(); assertEquals(edit, codec.decode(edit.type(), codec.encode(edit)));
        var registry = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.processRegistry();
        var plan = assertInstanceOf(CommandPlan.Accepted.class, FrontierWorldCommandPlanner.plan(state, command(edit), registry,
                FrontierExecutionSubjects.PHYSICAL_EXECUTOR));
        assertEquals(1, plan.events().stream().filter(e -> e.payload() instanceof ReferenceMutationObserved).count());
        var successor = ContainerPlayerEditProcess.reduce(state, FrontierExecutionSubjects.PHYSICAL_EXECUTOR, edit);
        assertEquals(tool.custody(), successor.inventory().items().get(tool.id()).custody());
        var invalid = new ContainerPlayerEditObserved(PLAYER, CLICK, edit.members(), Optional.empty(),
                Optional.of(new ExactItemIngress.Returned(new ExactItemStack(new SubjectId("item:missing"), owner,
                        tool.itemKind(), 1, new InventoryCustody.Player(PLAYER)), new ExactItemStack(new SubjectId("item:missing"),
                        owner, tool.itemKind(), 1, tool.custody()))));
        assertInstanceOf(CommandPlan.Rejected.class, ContainerPlayerEditProcess.plan(state, invalid, command(invalid)));
    }
    @Test void singleAndCompositeClicksRetainAuthorityAndAdmitNextEditWithoutRediscovery() {
        for (var count : List.of(1, 2)) {
            var state = fixture();
            var edit = new ContainerPlayerEditObserved(PLAYER, CLICK, gift(state).members().subList(0, count), Optional.empty());
            var codecs = FrontierWorldPayloadCodecs.create();
            assertEquals(edit, codecs.decode(edit.type(), codecs.encode(edit)), "single and composed transactions must both recover");
            var id = new CommandId("test:container-click");
            var command = new FrontierCommand(1, id, new WorldId("frontier:composed-test"), new Revision(99), new SimInstant(12),
                    FrontierExecutionSubjects.PHYSICAL_EXECUTOR, CauseChain.root(id), edit);
            var registry = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.processRegistry();
            var plan = assertInstanceOf(CommandPlan.Accepted.class, FrontierWorldCommandPlanner.plan(state, command, registry,
                    FrontierExecutionSubjects.PHYSICAL_EXECUTOR));
            assertTrue(edit.requiresDurableBeforeEffect());
            assertEquals(count.longValue(), plan.events().stream().filter(e -> e.payload() instanceof ReferenceMutationObserved).count());
            int index = 0;
            // This check applies the declared aggregate events; queue wake effects belong
            // to the kernel and are not replayed through the domain reducer.
            for (var event : plan.events()) {
                assertEquals(event.payload(), codecs.decode(event.payload().type(), codecs.encode(event.payload())));
                if (registry.requireReducedEventOwner(event.payload().type()).equals("kernel-schedule")) continue;
                state = FrontierWorldEventReducer.reduce(state, new FrontierEvent(1,
                    new EventId("test:click-event-" + index++), new TransactionId("test:click-transaction"),
                    command.worldId(), new Revision(100), new SimInstant(12), event.subject(), CauseChain.root(id), event.payload()), registry);
            }
            for (var member : edit.members()) {
                var lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(member.containerId()));
                assertEquals(PhysicalCustodyLeaseStatus.ACQUIRED, lease.status());
                assertEquals(member.epoch(), lease.authorityEpoch());
                assertEquals(ReferenceContainerCustody.canonicalFingerprint(state, member.containerId()),
                        state.replicaCustody().replicas().get(member.containerId()).fingerprint());
            }
            var restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
            var nextEdit = new ContainerPlayerEditObserved(PLAYER, new UUID(0, 759), edit.members().stream()
                    .map(m -> new ContainerPlayerEditObserved.Member(m.containerId(), m.accountId(), m.ownerId(), m.epoch(),
                            m.after(), List.of(stack(m.containerId(), m.after().getFirst().quantity() + 1)))).toList(), Optional.empty());
            var nextCommand = new FrontierCommand(1, new CommandId("test:next-click"), command.worldId(), new Revision(100),
                    command.submittedAt(), command.actor(), command.causes(), nextEdit);
            assertInstanceOf(CommandPlan.Accepted.class, FrontierWorldCommandPlanner.plan(restored, nextCommand, registry,
                    FrontierExecutionSubjects.PHYSICAL_EXECUTOR));
            var stale = (ReferenceMutationObserved) plan.events().stream().map(ProposedEvent::payload)
                    .filter(ReferenceMutationObserved.class::isInstance).findFirst().orElseThrow();
            assertThrows(IllegalArgumentException.class, () -> ReferenceContainerCustody.observeMutation(restored, stale),
                    "the same owner does not authorize replay of an old observation");
        }
    }
    @Test void oneClickCreditsBothMembersWithDistinctLotsAndRoundTripsItsWholeSuccessor() {
        var state = fixture(); var edit = gift(state);
        var plan = assertInstanceOf(CommandPlan.Accepted.class, ContainerPlayerEditProcess.plan(state, edit, command(edit)));
        assertEquals(edit, plan.events().getFirst().payload());
        assertEquals(1, plan.events().stream().filter(e -> e.payload() instanceof ContainerPlayerEditObserved).count());
        var next = ContainerPlayerEditProcess.reduce(state, FrontierExecutionSubjects.PHYSICAL_EXECUTOR, edit);
        for (var id : List.of(LEFT, RIGHT)) assertEquals(id.equals(LEFT) ? 1 : 16,
                next.inventory().fungibleResources().accounts().get(ReferenceContainerCustody.scopeId(id)).lotQuantities()
                        .values().stream().mapToInt(Integer::intValue).sum());
        var leftLots = next.inventory().fungibleResources().accounts().get(ReferenceContainerCustody.scopeId(LEFT)).lotQuantities().keySet();
        var rightLots = next.inventory().fungibleResources().accounts().get(ReferenceContainerCustody.scopeId(RIGHT)).lotQuantities().keySet();
        assertTrue(Collections.disjoint(leftLots, rightLots));
        var codec = FrontierWorldPayloadCodecs.create(); assertEquals(edit, codec.decode(edit.type(), codec.encode(edit)));
        var stateCodec = new FrontierWorldStateCodec(); assertEquals(next, stateCodec.decode(stateCodec.encode(next)));
        var withdrawal = new ContainerPlayerEditObserved(PLAYER, new UUID(0, 753), List.of(
                member(next, LEFT, List.of(stack(LEFT, 1)), List.of()), member(next, RIGHT, List.of(stack(RIGHT, 16)), List.of())), Optional.empty());
        var empty = ContainerPlayerEditProcess.reduce(next, FrontierExecutionSubjects.PHYSICAL_EXECUTOR, withdrawal);
        assertFalse(empty.inventory().fungibleResources().accounts().containsKey(ReferenceContainerCustody.scopeId(LEFT)));
        assertFalse(empty.inventory().fungibleResources().accounts().containsKey(ReferenceContainerCustody.scopeId(RIGHT)));
    }
    @Test void invalidSecondMemberCannotPublishFirstMemberAndEachNominalDimensionIsRequired() {
        var state = fixture(); var edit = gift(state); var right = edit.members().get(1);
        var invalid = List.of(new ContainerPlayerEditObserved.Member(right.containerId(), right.accountId(), new SubjectId("settlement:foreign"), 1, right.before(), right.after()),
                new ContainerPlayerEditObserved.Member(right.containerId(), new SubjectId("custody:foreign"), right.ownerId(), 1, right.before(), right.after()),
                new ContainerPlayerEditObserved.Member(right.containerId(), right.accountId(), right.ownerId(), 2, right.before(), right.after()),
                new ContainerPlayerEditObserved.Member(right.containerId(), right.accountId(), right.ownerId(), 1, List.of(stack(RIGHT, 1)), right.after()));
        for (var forged : invalid) {
            var bad = new ContainerPlayerEditObserved(PLAYER, CLICK, List.of(edit.members().getFirst(), forged), Optional.empty());
            assertInstanceOf(CommandPlan.Rejected.class, ContainerPlayerEditProcess.plan(state, bad, command(bad)));
            assertThrows(IllegalArgumentException.class, () -> ContainerPlayerEditProcess.reduce(state, FrontierExecutionSubjects.PHYSICAL_EXECUTOR, bad));
        }
        assertFalse(state.inventory().fungibleResources().accounts().containsKey(ReferenceContainerCustody.scopeId(LEFT)));
        assertThrows(IllegalArgumentException.class, () -> ContainerPlayerEditProcess.reduce(state, new SubjectId("resident:foreign"), edit));
        assertThrows(IllegalArgumentException.class, () -> new ContainerPlayerEditObserved(PLAYER, CLICK, List.of(right, right), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new ContainerPlayerEditObserved.Member(RIGHT, right.accountId(), right.ownerId(), 1, List.of(), List.of(stack(LEFT, 1))));
        assertThrows(NullPointerException.class, () -> new ContainerPlayerEditObserved.Member(RIGHT, right.accountId(), null, 1, List.of(), List.of()));
    }
    @Test void trackedPlayerReturnCanSplitAcrossMembersWithoutIssuingNewLotsOrChangingTitle() {
        var state = fixture(); var owner = state.inventory().containers().get(LEFT).ownerId();
        var lot = new ResourceLot(new SubjectId("lot:composed-player"), owner, "minecraft:cobblestone", 17, "original", List.of());
        var account = new SubjectId("custody:composed-player"); var address = new PhysicalStackAddress.PlayerSlot(PLAYER, 9);
        var resources = state.inventory().fungibleResources().issue(lot,
                new CustodyAccount(account, new ResourceCustody.Player(PLAYER), Map.of(lot.id(), 17), Map.of()));
        resources = resources.rebind(account, 1, FungiblePhysicalObservation.bind(resources, account, 1,
                List.of(new FungiblePhysicalObservation.Stack(address, lot.itemKind(), 17))));
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        var binding = resources.bindings().values().stream().filter(b -> b.accountId().equals(account)).findFirst().orElseThrow();
        var source = new ContainerPlayerEditObserved.PlayerReturn(binding.id(), account, 1, 9, lot.itemKind(), 17, 0);
        var edit = new ContainerPlayerEditObserved(PLAYER, CLICK, gift(state).members(), Optional.of(source));
        var next = ContainerPlayerEditProcess.reduce(state, FrontierExecutionSubjects.PHYSICAL_EXECUTOR, edit);
        assertEquals(resources.lots(), next.inventory().fungibleResources().lots());
        assertFalse(next.inventory().fungibleResources().accounts().containsKey(account));
        assertEquals(Map.of(lot.id(), 1), next.inventory().fungibleResources().accounts().get(ReferenceContainerCustody.scopeId(LEFT)).lotQuantities());
        assertEquals(Map.of(lot.id(), 16), next.inventory().fungibleResources().accounts().get(ReferenceContainerCustody.scopeId(RIGHT)).lotQuantities());
    }
}
