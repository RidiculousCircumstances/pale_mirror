package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Transport admission and custody transitions. Neither movement nor unloading sells the goods. */
public final class ShipmentStateSupport {
    private ShipmentStateSupport() { }
    public static boolean serviceAvailable(FrontierWorldState state, Shipment shipment) {
        return ShipmentServiceAccess.available(state, shipment);
    }
    public static FrontierWorldState dispatch(FrontierWorldState state, SubjectId subject, Shipment shipment) {
        validateDispatch(state, subject, shipment);
        var next = state.shipments().admit(shipment);
        return ActorExecutionComposition.LIFECYCLE.prepareVacant(state, shipment.execution())
                .commit(state, FrontierWorldStateUpdate.begin().shipments(next));
    }
    /** Validation contributes no partial shipment, execution or mission publication. */
    public static void validateDispatch(FrontierWorldState state, SubjectId subject, Shipment shipment) {
        if (shipment.mobileContainerId().isPresent()) io.farfrontier.palemirror.frontier.v3.model.expedition.TransportAssetAdmission.requireCourier(state, shipment);
        else SettlementLabourAllocation.requireMissionCommitment(state, shipment.sender().settlementId(),
                ResidentWorkKind.LOGISTICS, java.util.List.of(shipment.execution().actorId()));
        ShipmentAuthorizationComposition.validate(state, shipment, true);
        ShipmentEndpointComposition.validate(state, shipment.sender()); ShipmentEndpointComposition.validate(state, shipment.receiver());
        ClaimAllocation claim = state.inventory().fungibleResources().claims().get(shipment.authorization().claimId());
        CustodyAccount source = state.inventory().fungibleResources().accounts().get(shipment.sourceAccountId());
        if (!subject.equals(claim.economicOwnerId()) || source == null
                || !source.custody().equals(new ResourceCustody.Container(shipment.sender().containerId()))
                || source.claimQuantities().getOrDefault(claim.id(), 0) != shipment.quantity()
                || state.inventory().fungibleResources().accounts().containsKey(shipment.carriedAccountId())
                    && (shipment.mobileContainerId().isEmpty() || !state.inventory().fungibleResources().accounts().get(shipment.carriedAccountId()).custody().equals(shipment.carriedCustody()))
                || state.inventory().fungibleResources().accounts().values().stream().anyMatch(account ->
                        account.custody().equals(new ResourceCustody.Container(shipment.receiver().containerId()))
                                && !account.id().equals(shipment.receivingAccountId()))
                || state.inventory().fungibleResources().accounts().containsKey(shipment.receivingAccountId())
                    && !state.inventory().fungibleResources().accounts().get(shipment.receivingAccountId()).custody()
                            .equals(new ResourceCustody.Container(shipment.receiver().containerId()))
                || !(shipment.mobileContainerId().isPresent() ? ActorExecutionCoordinator.declaredActorWorkAdmission(state, shipment.execution().actorId())
                    : ActorExecutionCoordinator.ordinaryWorkAdmission(state, shipment.execution().actorId())).permitted())
            throw new IllegalArgumentException("shipment has no authorized source allocation or available exact courier");
    }
    public static void validateOrder(FrontierWorldState state, ActorContainerItemOrder order) {
        Shipment shipment = state.shipments().shipments().get(order.ownerId());
        if (shipment == null || shipment.terminal() || !(order.portion() instanceof ActorContainerItemOrder.Portion.Fungible portion)
                || !shipment.itemOrder(portion.lotQuantities()).equals(order))
            throw new IllegalArgumentException("delegated item order does not equal its current shipment declaration");
        state.actorExecutions().requireCurrent(shipment.execution());
        ShipmentAuthorizationComposition.validate(state, shipment, false);
    }
    static ActorItemTransferPreparation prepareTransfer(FrontierWorldState state, ActorContainerItemOrder order) {
        validateOrder(state, order);
        Shipment shipment = state.shipments().shipments().get(order.ownerId());
        var portion = (ActorContainerItemOrder.Portion.Fungible) order.portion();
        if (portion.lotQuantities().equals(shipment.lotQuantities())) return ActorItemTransferPreparation.unchanged(state, order);
        var receipt = ShipmentReception.unloaded(shipment, portion.lotQuantities());
        var partition = new ResourceClaimPartition(shipment.carriedAccountId(), shipment.authorization().claimId(), receipt.claimId(), portion.lotQuantities());
        var ownerChanges = ShipmentAuthorizationComposition.allocationPartitioned(state, shipment, partition);
        var grant = new ResourceClaimDelegation(shipment.authorization().kind(), receipt.claimId(), shipment.authorization().claimantId(),
                shipment.id(), shipment.authorization().authorizationRevision());
        var executable = new ActorContainerItemOrder(order.ownerId(), order.actorId(), order.direction(),
                new ActorContainerItemOrder.Portion.Fungible(portion.sourceAccountId(), portion.sourceCustody(), portion.destinationAccountId(),
                        portion.destinationCustody(), java.util.Optional.of(receipt.claimId()), portion.itemKind(), portion.lotQuantities(), java.util.Optional.of(grant)),
                order.containerEndpoint(), order.station(), order.actorSlot(), order.goalOrdinal(), order.goalRevision());
        return new ActorItemTransferPreparation(state.inventory().withFungibleResources(state.inventory().fungibleResources().partitionClaim(partition)), executable, ownerChanges);
    }
    public static FrontierWorldState transferCold(FrontierWorldState state, SubjectId subject, SubjectId shipmentId,
                                                 long revision, Shipment.Status expectedStatus) {
        Shipment shipment = state.shipments().shipments().get(shipmentId);
        if (shipment == null || shipment.terminal() || !subject.equals(shipment.id())
                || shipment.pendingPhysicalStep().isPresent() || shipment.reception().isPresent()
                || shipment.revision() != revision || shipment.status() != expectedStatus
                || !ActorExecutionCoordinator.coldAvailable(state, shipment.execution().actorId()))
            throw new IllegalArgumentException("shipment handoff has stale identity, phase or competing physical custody");
        var lots = coldTransferLots(state, shipment);
        var order = shipment.itemOrder(lots);
        validateOrder(state, order);
        if (!ShipmentServiceAccess.available(state, shipment))
            throw new IllegalArgumentException("shipment has not acquired its shared endpoint access turn");
        if (shipment.status() == Shipment.Status.CARRYING && !ContainerStorageAdmission.receive(state,
                shipment.receiver().containerId(), shipment.itemKind(), order.portion().quantity(), ShipmentAuthorizationComposition.capacityCompletionOwner(shipment)))
            throw new IllegalArgumentException("shipment receiver cannot fit its retained cargo");
        var transfer = ActorItemCustody.transferColdUpdate(state, order);
        Shipment replacement = shipment.status() == Shipment.Status.AWAITING_LOAD ? shipment.withStatus(Shipment.Status.CARRYING)
                : shipment.unloaded(ShipmentReception.unloaded(shipment, lots));
        var changes = transfer.shipments(state.shipments().replace(shipment, replacement));
        if (replacement.terminal()) changes.actorExecutions(ActorExecutionComposition.LIFECYCLE.retire(state,
                shipment.execution().actorId(), shipment.execution().activityKind(), shipment.id()));
        return state.withChanges(changes);
    }
    public static boolean coldTransferAvailable(FrontierWorldState state, Shipment shipment) {
        if (shipment.terminal() || shipment.pendingPhysicalStep().isPresent() || shipment.reception().isPresent() || state.actorMovements().containsKey(shipment.execution().actorId())
                || !ActorExecutionCoordinator.coldAvailable(state, shipment.execution().actorId())
                || !shipment.movementOrder().arrivedAt(state.actorLocations().get(shipment.execution().actorId()).supportingSurface())
                || !ShipmentServiceAccess.available(state, shipment)) return false;
        var endpoint = shipment.status() == Shipment.Status.AWAITING_LOAD ? shipment.sender() : shipment.receiver();
        if (ReferenceContainerCustody.hasLiveCustody(state, endpoint.containerId())
                || ReferenceContainerCustody.blocksCanonicalUse(state, endpoint.containerId())) return false;
        if (shipment.mobileContainerId().filter(container -> ReferenceContainerCustody.hasLiveCustody(state, container)
                || ReferenceContainerCustody.blocksCanonicalUse(state, container)).isPresent()) return false;
        return !coldTransferLots(state, shipment).isEmpty();
    }
    public static java.util.Map<SubjectId, Integer> coldTransferLots(FrontierWorldState state, Shipment shipment) {
        if (shipment.status() == Shipment.Status.AWAITING_LOAD) return shipment.mobileContainerId().filter(container ->
                !ContainerStorageAdmission.receive(state, container, shipment.itemKind(), shipment.quantity(), java.util.Optional.empty())).isPresent()
                ? java.util.Map.of() : shipment.lotQuantities();
        int quantity = shipment.quantity();
        while (quantity > 0 && !ContainerStorageAdmission.receive(state, shipment.receiver().containerId(),
                shipment.itemKind(), quantity, ShipmentAuthorizationComposition.capacityCompletionOwner(shipment))) quantity--;
        return portion(shipment.lotQuantities(), quantity);
    }
    static java.util.Map<SubjectId, Integer> portion(java.util.Map<SubjectId, Integer> lots, int quantity) {
        var result = new java.util.LinkedHashMap<SubjectId, Integer>();
        for (var e : lots.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            int take = Math.min(quantity, e.getValue());
            if (take > 0) result.put(e.getKey(), take);
            quantity -= take;
        }
        if (quantity != 0) throw new IllegalArgumentException("shipment portion exceeds retained cargo");
        return java.util.Map.copyOf(result);
    }
    public static FrontierWorldState acknowledge(FrontierWorldState state, SubjectId subject, ShipmentReceiptAcknowledged value) {
        var shipment = state.shipments().shipments().get(value.shipmentId());
        if (shipment == null || !subject.equals(shipment.id()) || shipment.revision() != value.expectedRevision()
                || shipment.reception().isEmpty() || !shipment.reception().orElseThrow().id().equals(value.receiptId())
                || !ShipmentAuthorizationComposition.receptionAccepted(state, shipment, shipment.reception().orElseThrow()))
            throw new IllegalArgumentException("shipment reception has no exact recipient-owned acceptance proof");
        return state.withChanges(FrontierWorldStateUpdate.begin().shipments(state.shipments().replace(shipment, shipment.acknowledged(value.receiptId()))));
    }
    public static FrontierWorldState retire(FrontierWorldState state, SubjectId subject, SubjectId id, long revision) {
        Shipment shipment = state.shipments().shipments().get(id);
        if (!subject.equals(id) || shipment == null || state.physicalIntents().values().stream().anyMatch(i -> i.causeSubjectId().equals(id)))
            throw new IllegalArgumentException("shipment retirement retains an unresolved effect or foreign authority");
        return state.withChanges(FrontierWorldStateUpdate.begin().shipments(state.shipments().retire(id, revision)));
    }
    public static boolean closedForRetirement(FrontierWorldState state, Shipment shipment) {
        return shipment.transportMissionId().isEmpty() && cargoClosedForRetirement(state, shipment);
    }
    public static boolean cargoClosedForRetirement(FrontierWorldState state, Shipment shipment) {
        return shipment.terminal() && shipment.reception().isEmpty() && shipment.pendingPhysicalStep().isEmpty()
                && state.physicalIntents().values().stream().noneMatch(i -> i.causeSubjectId().equals(shipment.id()))
                && state.actorMovements().values().stream().noneMatch(m -> m.order().ownerId().equals(shipment.id()))
                && state.actorExecutions().actors().values().stream().noneMatch(a ->
                    a.current().filter(e -> e.activityOwnerId().equals(shipment.id())).isPresent()
                    || a.suspended().filter(e -> e.activityOwnerId().equals(shipment.id())).isPresent());
    }
    public static void validate(FrontierWorldState state) {
        ShipmentExecutionCapability.validateReferences(state.shipments(), state.actorExecutions());
        for (Shipment shipment : state.shipments().shipments().values()) {
            ShipmentEndpointComposition.validate(state, shipment.sender()); ShipmentEndpointComposition.validate(state, shipment.receiver());
            shipment.mobileContainerId().ifPresent(container -> {
                var asset = state.transportFleet().require(shipment.execution().actorId());
                if (!asset.containerId().equals(container) || shipment.transportMissionId().isEmpty()
                        || !state.shipments().missions().get(shipment.transportMissionId().orElseThrow()).transportAssetId().equals(java.util.Optional.of(asset.actorId())))
                    throw new IllegalArgumentException("mobile shipment lost its exact mission/asset/container declaration");
            });
            if (shipment.terminal()) continue; // Cargo declaration is historical after unload, not a live claim/account reference.
            ShipmentAuthorizationComposition.validate(state, shipment, false);
            SubjectId accountId = shipment.status() == Shipment.Status.AWAITING_LOAD ? shipment.sourceAccountId() : shipment.carriedAccountId();
            CustodyAccount account = state.inventory().fungibleResources().accounts().get(accountId);
            ResourceCustody expected = shipment.status() == Shipment.Status.AWAITING_LOAD
                    ? new ResourceCustody.Container(shipment.sender().containerId()) : shipment.carriedCustody();
            if (account == null || !account.custody().equals(expected)
                    || account.claimQuantities().getOrDefault(shipment.authorization().claimId(), 0) != shipment.quantity())
                throw new IllegalArgumentException("live shipment lost its exact cargo custody");
        }
    }
    /** A source promise may be withdrawn before pickup, or an exact dead attached carrier's load may be disposed.
     * Living in-flight cargo and possibly applied endpoint effects keep their own receipt authority. */
    static void requireSourceWithdrawal(FrontierWorldState state, SubjectId claim) {
        for (Shipment shipment : state.shipments().shipments().values()) {
            if (shipment.terminal() || !shipment.authorization().claimId().equals(claim)) continue;
            boolean deadAttachedCargo = shipment.status() == Shipment.Status.CARRYING && shipment.mobileContainerId().isPresent()
                    && state.actorLocations().get(shipment.execution().actorId()).condition().status() == ActorLifeStatus.DEAD;
            if ((shipment.status() != Shipment.Status.AWAITING_LOAD && !deadAttachedCargo)
                    || shipment.pendingPhysicalStep().isPresent()
                    || shipment.reception().isPresent()
                    || state.physicalIntents().values().stream().anyMatch(i -> i.causeSubjectId().equals(shipment.id())))
                throw new IllegalArgumentException("cargo allocation change retains an independent shipment obligation");
            if (deadAttachedCargo) {
                var actorId = shipment.execution().actorId();
                var retired = state.fencedRecovery().tombstones().get(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.recoveryBindingId(actorId));
                if (retired == null) throw new IllegalArgumentException("attached cargo loss lacks an observed retired incarnation");
                ActorBodyAuthority.requireRetiredDeath(state, new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(actorId, retired.retiredEpoch()));
            }
        }
    }
    public static java.util.Optional<ActorCarriedResources.Presentation> carriedResources(FrontierWorldState state, HumanAssignment assignment) {
        Shipment shipment = state.shipments().shipments().get(assignment.ownerId().orElseThrow());
        if (assignment.kind() != HumanAssignmentKind.COURIER || shipment == null || shipment.terminal()
                || !shipment.execution().actorId().equals(assignment.residentId()))
            throw new IllegalArgumentException("courier carried view lacks its exact assignment");
        return shipment.status() == Shipment.Status.CARRYING && shipment.mobileContainerId().isEmpty() ? java.util.Optional.of(new ActorCarriedResources.Presentation(
                shipment.execution().actorId(), shipment.carriedAccountId(), new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN))) : java.util.Optional.empty();
    }
    static FrontierWorldStateUpdate withdrawSourceClaim(FrontierWorldState state, SubjectId claim) {
        var settled = withdrawSourceClaim(state, claim, new FungibleForfeitureSettlement(state.inventory(), state.companies(),
                state.shipments(), state.actorExecutions(), state.actorMovements()));
        return settled.shipments().equals(state.shipments()) ? FrontierWorldStateUpdate.begin()
                : FrontierWorldStateUpdate.begin().shipments(settled.shipments()).actorExecutions(settled.executions()).actorMovements(settled.movements());
    }
    static FungibleForfeitureSettlement withdrawSourceClaim(FrontierWorldState state, SubjectId claim,
                                                            FungibleForfeitureSettlement transaction) {
        requireSourceWithdrawal(state, claim);
        ShipmentState shipments = transaction.shipments(); var executions = transaction.executions();
        var movements = new java.util.LinkedHashMap<>(transaction.movements());
        boolean changed = false;
        for (Shipment shipment : transaction.shipments().shipments().values()) {
            if (shipment.terminal() || !shipment.authorization().claimId().equals(claim)) continue;
            shipments = shipments.replace(shipment, shipment.withStatus(shipment.status() == Shipment.Status.CARRYING
                    ? Shipment.Status.CARGO_DISPOSED : Shipment.Status.ALLOCATION_WITHDRAWN));
            var movement = movements.get(shipment.execution().actorId());
            if (movement != null) {
                if (!movement.executionId().equals(shipment.execution()))
                    throw new IllegalArgumentException("withdrawn shipment cannot retire a foreign movement");
                movements.remove(shipment.execution().actorId());
            }
            executions = ActorExecutionComposition.LIFECYCLE.retire(executions, shipment.execution().actorId(),
                    shipment.execution().activityKind(), shipment.id());
            changed = true;
        }
        return changed ? transaction.withShipments(shipments, executions, movements) : transaction;
    }
    static void validateTransition(FrontierWorldState before, FrontierWorldState after) {
        for (Shipment shipment : before.shipments().shipments().values()) {
            if (!shipment.terminal() && !after.shipments().shipments().containsKey(shipment.id()))
                throw new IllegalArgumentException("live shipment cannot disappear before its cargo disposition");
        }
        validate(after);
    }
}
