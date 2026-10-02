package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Field-specific custody contract; resource quantity remains owned only by the common ledger. */
public final class ResourceSiteHarvestCargo {
    private ResourceSiteHarvestCargo() { }

    public static int quantity(FrontierWorldState state, ResourceSiteHarvestJob job) {
        return part(state, job).map(ResourceLot::quantity).orElse(0);
    }

    public static Optional<ResourceLot> part(FrontierWorldState state, ResourceSiteHarvestJob job) {
        var owner = state.resourceSite(job.siteId()).settlementId();
        var resources = state.inventory().fungibleResources();
        var account = resources.accounts().get(job.actorAccountId());
        if (account != null && (!account.claimQuantities().isEmpty() || account.lotQuantities().size() != 1))
            throw new IllegalArgumentException("unreserved harvest cargo has foreign resource claims");
        int quantity = ActorCarriedResources.stackQuantity(resources, job.workerId(), job.actorAccountId(),
                owner, "minecraft:wheat");
        if (quantity != job.undeliveredYieldQuantity())
            throw new IllegalArgumentException("harvest custody differs from its own confirmed output history");
        if (account == null) return Optional.empty();
        ResourceLot lot = resources.lots().get(account.lotQuantities().keySet().iterator().next());
        if (lot.quantity() != quantity)
            throw new IllegalArgumentException("harvest actor does not hold its whole unreserved resource part");
        if (!lot.equals(newPart(state, job, quantity)))
            throw new IllegalArgumentException("harvest actor account contains another execution's resource part");
        return Optional.of(lot);
    }

    /** Birth identity belongs to one exact execution and its delivered offset, not collective field yield. */
    public static ResourceLot newPart(FrontierWorldState state, ResourceSiteHarvestJob job, int quantity) {
        if (quantity < 1 || quantity > 64) throw new IllegalArgumentException("harvest part exceeds hand capacity");
        String identity = job.id().value() + ":part-after-" + job.deliveredYieldQuantity();
        try {
            String digest = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
            return new ResourceLot(new io.farfrontier.palemirror.frontier.v3.api.SubjectId("lot:harvest-" + digest),
                    state.resourceSite(job.siteId()).settlementId(), "minecraft:wheat", quantity,
                    "harvest:sha256:" + digest, List.of());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    /** Existing shared actor/container transfer owns custody; the family supplies exact source and endpoint. */
    public static ActorContainerItemOrder deliveryOrder(FrontierWorldState state, ResourceSiteHarvestJob job) {
        ResourceLot part = part(state, job).orElseThrow(() -> new IllegalArgumentException("harvest delivery has no carried part"));
        if (!job.progress().complete() && (!job.returningForBatch() || part.quantity() != 64)
                || !ResourceSiteHarvestGoal.actorAtDepot(state, job))
            throw new IllegalArgumentException("harvest delivery lacks its ready part or actual depot arrival");
        var container = new ResourceCustody.Container(job.outputSlot().containerId());
        return new ActorContainerItemOrder(job.id(), job.workerId(), ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Fungible(job.actorAccountId(), new ResourceCustody.Actor(job.workerId()),
                        job.depotAccountId(), container, Optional.empty(), part.itemKind(), Map.of(part.id(), part.quantity())),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(container.containerId()),
                state.actorLocations().get(job.workerId()).supportingSurface(), ActorContainerItemOrder.Hand.OFF,
                job.deliveredYieldQuantity() + 1L, state.resourceSites().cycle(job.siteId()).layout().revision());
    }

    public static FungibleResourceLedger deliverObserved(FrontierWorldState state, ResourceSiteHarvestJob job,
                                                         java.util.UUID entityId, long actorEpoch, long depotEpoch,
                                                         List<FungiblePhysicalObservation.Stack> depotStacks) {
        var resources = state.inventory().fungibleResources();
        var bindings = resources.bindings().values().stream().filter(value -> value.accountId().equals(job.actorAccountId())).toList();
        if (bindings.size() != 1 || bindings.getFirst().authorityEpoch() != actorEpoch
                || !bindings.getFirst().address().equals(new PhysicalStackAddress.ActorHand(job.workerId(), entityId)))
            throw new IllegalArgumentException("harvest transfer has a foreign physical body or hand epoch");
        return resources.transferActorOrderObservedStacks(deliveryOrder(state, job), actorEpoch, depotEpoch, List.of(), depotStacks);
    }
}
