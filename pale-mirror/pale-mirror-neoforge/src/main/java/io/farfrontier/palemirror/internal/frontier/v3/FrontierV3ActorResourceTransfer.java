package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import java.util.*;

/** Physical endpoint adapters for the same retained item order. No job, recipe or economic policy lives here. */
final class FrontierV3ActorResourceTransfer {
    interface Session {
        boolean before();
        boolean after();
        boolean apply();
        List<FrontierV3MinecraftOperation.Participant> participants();
        List<FungiblePhysicalObservation.Stack> sourceLayout();
        List<FungiblePhysicalObservation.Stack> destinationLayout();
        void ownerConfirmed();
    }
    private record Request(ActorResourceItemOrder order, Mob actor, List<MaterialSourceSelection.Slice> source,
                           int destinationSlot, int destinationBefore) { }
    private interface Port {
        Optional<Session> open(ServerLevel level, FrontierWorldState state, Request request);
    }
    private static final Map<ActorResourceItemOrder.EndpointKind, Port> PORTS = Map.of(
            ActorResourceItemOrder.EndpointKind.CONTAINER, FrontierV3ActorResourceTransfer::container,
            ActorResourceItemOrder.EndpointKind.GROUND_ITEM, FrontierV3ActorResourceTransfer::ground);
    static {
        if (!PORTS.keySet().equals(EnumSet.allOf(ActorResourceItemOrder.EndpointKind.class)))
            throw new IllegalArgumentException("missing physical resource endpoint adapter");
    }
    private FrontierV3ActorResourceTransfer() { }
    static Optional<Session> open(ServerLevel level, FrontierWorldState state, ActorResourceItemOrder order,
            Mob actor, List<MaterialSourceSelection.Slice> source, int slot, int before) {
        var port = PORTS.get(order.endpointKind());
        if (port == null) throw new IllegalArgumentException("unknown physical resource endpoint capability");
        return port.open(level, state, new Request(order, actor, List.copyOf(source), slot, before));
    }
    private static Optional<Session> container(ServerLevel level, FrontierWorldState state, Request request) {
        if (!(request.order() instanceof ActorContainerItemOrder order)) throw new IllegalArgumentException("foreign container physical schema");
        var id = order.containerEndpoint().containerId();
        var physical = FrontierV3PhysicalContainer.loaded(level, state, id).orElse(null);
        if (physical == null || !ReferenceContainerCustody.hasLiveCustody(state, id)
                || ReferenceContainerCustody.blocksCanonicalUse(state, id)) return Optional.empty();
        var attached = order.actorSlot() instanceof ActorItemSlot.AttachedStorage storage
                ? FrontierV3PhysicalContainer.loaded(level, state, storage.containerId()).orElse(null) : null;
        if (order.actorSlot() instanceof ActorItemSlot.AttachedStorage && (attached == null
                || !ReferenceContainerCustody.hasLiveCustody(state, attached.containerId())
                || ReferenceContainerCustody.blocksCanonicalUse(state, attached.containerId()))) return Optional.empty();
        if (order.portion() instanceof ActorContainerItemOrder.Portion.Exact exact)
            return Optional.of(individual(state, request, order, exact, physical, attached));
        var step = new FrontierV3ActorItemTransfer.FungibleStep(order, physical, attached, request.actor(), request.actor().getUUID(),
                request.source(), request.destinationSlot(), request.destinationBefore());
        return Optional.of(new Session() {
            @Override public List<FrontierV3MinecraftOperation.Participant> participants() {
                return List.of(physical.persistence().get(), attached == null
                        ? FrontierV3OperationEntityParticipant.current(request.actor(), ActorBodyAuthority.current(state, order.actorId()).physicalEpoch())
                        : attached.persistence().get());
            }
            @Override public boolean before() { return step.before(); }
            @Override public boolean after() { return step.after(); }
            @Override public boolean apply() { return step.apply(); }
            private List<FungiblePhysicalObservation.Stack> endpoint() {
                return FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(physical.inventory(), state, id);
            }
            private List<FungiblePhysicalObservation.Stack> carried() {
                return attached == null ? actorLayout(request) : FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(
                        attached.inventory(), state, attached.containerId());
            }
            @Override public List<FungiblePhysicalObservation.Stack> sourceLayout() {
                return order.direction() == ActorContainerItemOrder.Direction.TAKE ? endpoint() : carried();
            }
            @Override public List<FungiblePhysicalObservation.Stack> destinationLayout() {
                return order.direction() == ActorContainerItemOrder.Direction.TAKE ? carried() : endpoint();
            }
            @Override public void ownerConfirmed() {
                // The owning transfer command closes every declared container atomically.
            }
        });
    }
    private static Session individual(FrontierWorldState state, Request request, ActorContainerItemOrder order,
            ActorContainerItemOrder.Portion.Exact exact, FrontierV3PhysicalContainer endpoint, FrontierV3PhysicalContainer attached) {
        int slot = order.exactSlot().slot();
        if (!request.source().isEmpty() || request.destinationBefore() != 0 || slot >= endpoint.inventory().getContainerSize()
                || attached != null && exact.attachedSlot().orElseThrow().slot() >= attached.inventory().getContainerSize())
            throw new IllegalArgumentException("individual transfer has foreign physical addresses");
        return new Session() {
            private ItemStack endpointItem() { return endpoint.inventory().getItem(slot); }
            private ItemStack carriedItem() { return attached == null ? FrontierV3ActorResourceSlots.get(request.actor(), order.actorSlot())
                    : attached.inventory().getItem(exact.attachedSlot().orElseThrow().slot()); }
            private void endpointItem(ItemStack item) { endpoint.inventory().setItem(slot, item); endpoint.inventory().setChanged(); }
            private void carriedItem(ItemStack item) {
                if (attached == null) FrontierV3ActorResourceSlots.set(request.actor(), order.actorSlot(), item);
                else { attached.inventory().setItem(exact.attachedSlot().orElseThrow().slot(), item); attached.inventory().setChanged(); }
            }
            private boolean taking() { return order.direction() == ActorContainerItemOrder.Direction.TAKE; }
            @Override public boolean before() { return taking()
                    ? carriedItem().isEmpty() && FrontierV3ExactItemPresentation.exactMatch(endpointItem(), exact.item())
                    : endpointItem().isEmpty() && FrontierV3ExactItemPresentation.exactMatch(carriedItem(), exact.item()); }
            @Override public boolean after() { return taking()
                    ? endpointItem().isEmpty() && FrontierV3ExactItemPresentation.exactMatch(carriedItem(), exact.item())
                    : carriedItem().isEmpty() && FrontierV3ExactItemPresentation.exactMatch(endpointItem(), exact.item()); }
            @Override public boolean apply() {
                if (!before()) return false;
                var item = taking() ? endpointItem().copy() : carriedItem().copy();
                if (taking()) { endpointItem(ItemStack.EMPTY); carriedItem(item); }
                else { carriedItem(ItemStack.EMPTY); endpointItem(item); }
                return after();
            }
            @Override public List<FrontierV3MinecraftOperation.Participant> participants() {
                return List.of(endpoint.persistence().get(), attached == null
                        ? FrontierV3OperationEntityParticipant.current(request.actor(), ActorBodyAuthority.current(state, order.actorId()).physicalEpoch())
                        : attached.persistence().get());
            }
            @Override public List<FungiblePhysicalObservation.Stack> sourceLayout() { return List.of(); }
            @Override public List<FungiblePhysicalObservation.Stack> destinationLayout() { return List.of(); }
            @Override public void ownerConfirmed() { }
        };
    }
    private static List<FungiblePhysicalObservation.Stack> actorLayout(Request request) {
        var actual = FrontierV3ActorResourceSlots.get(request.actor(), request.order().actorSlot());
        return actual.isEmpty() ? List.of() : List.of(new FungiblePhysicalObservation.Stack(
                FrontierV3ActorResourceSlots.address(request.order().actorId(), request.actor(), request.order().actorSlot()),
                BuiltInRegistries.ITEM.getKey(actual.getItem()).toString(), actual.getCount()));
    }
    private static Optional<Session> ground(ServerLevel level, FrontierWorldState state, Request request) {
        if (!(request.order() instanceof ActorGroundItemOrder order)) throw new IllegalArgumentException("foreign ground physical schema");
        var position = ((ResourceCustody.Ground) order.portion().sourceCustody()).position();
        if (!level.hasChunkAt(new net.minecraft.core.BlockPos(position.x(), position.y(), position.z()))) return Optional.empty();
        var nativeEntity = level.getEntity(order.entityId());
        if (nativeEntity != null && !(nativeEntity instanceof ItemEntity))
            throw new IllegalArgumentException("ground source UUID denotes a non-item native entity");
        var entity = (ItemEntity) nativeEntity;
        var column = FrontierV3OperationEntityColumns.current(level, new net.minecraft.world.level.ChunkPos(position.x() >> 4, position.z() >> 4)).orElse(null);
        if (column == null) return Optional.empty();
        if (entity != null && !entity.chunkPosition().equals(new net.minecraft.world.level.ChunkPos(position.x() >> 4, position.z() >> 4)))
            return Optional.empty(); // source observation must first rebind an item that moved to another native save unit
        if (request.destinationSlot() != -1 || request.destinationBefore() != 0 || request.source().size() != 1
                || !request.source().getFirst().address().equals(new PhysicalStackAddress.WorldEntity(order.entityId()))
                || request.source().getFirst().epoch() != order.sourceEpoch())
            throw new IllegalArgumentException("ground pickup has foreign physical source or destination evidence");
        var slice = request.source().getFirst();
        var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(order.portion().itemKind())).orElse(Items.AIR);
        if (item == Items.AIR || slice.moved() != order.portion().quantity()) throw new IllegalArgumentException("invalid native ground resource portion");
        String witness = order.ownerId().value() + "|" + order.actorId().value() + "|" + order.goalOrdinal() + "|" + order.goalRevision()
                + "|" + order.entityId() + "|" + order.sourceEpoch() + "|" + slice.before() + "|" + slice.moved()
                + "|" + ActorBodyAuthority.current(state, order.actorId()).physicalEpoch();
        var journal = FrontierV3ResourceHandoffLedger.get(level);
        var pickup = new FrontierV3ResourceHandoffLedger.Pickup(order.ownerId(), order.portion().sourceAccountId(), order.entityId(),
                state.actorExecutions().actors().get(order.actorId()).current().orElseThrow().activityKind(), order.goalRevision(), witness);
        FrontierV3GroundPickupRecovery.requireOwner(pickup.kind());
        return Optional.of(new Session() {
            @Override public List<FrontierV3MinecraftOperation.Participant> participants() {
                return List.of(column, FrontierV3OperationEntityParticipant.current(request.actor(),
                        ActorBodyAuthority.current(state, order.actorId()).physicalEpoch()));
            }
            private boolean plain(ItemStack stack, int count) {
                return count == 0 ? stack.isEmpty() : stack.getCount() == count
                        && ItemStack.isSameItemSameComponents(stack, new ItemStack(item, count));
            }
            private ItemStack source() { return entity == null || entity.isRemoved() ? ItemStack.EMPTY : entity.getItem(); }
            private ItemStack destination() { return FrontierV3ActorResourceSlots.get(request.actor(), order.actorSlot()); }
            private boolean retainedWitness() { return pickup.equals(journal.pickup(order.ownerId())); }
            private boolean sourceBefore() {
                return entity != null && io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN
                        .contains(order.station(), new BlockPosition(entity.blockPosition().getX(), entity.blockPosition().getY(), entity.blockPosition().getZ()))
                        && plain(source(), slice.before());
            }
            @Override public boolean before() { return (retainedWitness() || !journal.blocks(order.entityId())
                    && !journal.blocksAccount(order.portion().sourceAccountId())) && sourceBefore() && destination().isEmpty(); }
            @Override public boolean after() { return retainedWitness() && plain(source(), slice.before() - slice.moved()) && plain(destination(), slice.moved()); }
            @Override public boolean apply() {
                if (!before()) return false;
                journal.preparePickup(pickup); journal.persist(level);
                FrontierV3ActorResourceSlots.set(request.actor(), order.actorSlot(), new ItemStack(item, slice.moved()));
                int remaining = slice.before() - slice.moved();
                if (remaining == 0) {
                    entity.setItem(ItemStack.EMPTY);
                    FrontierV3GroundItemDestruction.discardConsumed(entity);
                } else entity.setItem(new ItemStack(item, remaining));
                return after();
            }
            @Override public List<FungiblePhysicalObservation.Stack> sourceLayout() {
                return source().isEmpty() ? List.of() : List.of(new FungiblePhysicalObservation.Stack(
                        new PhysicalStackAddress.WorldEntity(order.entityId()), order.portion().itemKind(), source().getCount()));
            }
            @Override public List<FungiblePhysicalObservation.Stack> destinationLayout() { return actorLayout(request); }
            @Override public void ownerConfirmed() {
                journal.retirePickup(pickup); journal.persist(level);
            }
        });
    }
}
