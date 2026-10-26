package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import java.util.*;

/** Agriculture declares the operation's tool requirement; shared equipment owns eligibility and transfers. */
public final class FieldEquipment {
    public static EquipmentRequirements requirements() {
        var requirements = new HashSet<ToolRequirement>(); requirements.add(FieldHarvestExtraction.DEFINITION.toolRequirement());
        ResourceFieldCycle.Soil.catalog().soils().stream().filter(ResourceFieldCycle.Soil::untilled)
                .map(soil -> ResourceFieldCycle.Soil.catalog().step(soil).tool()).forEach(requirements::add);
        return EquipmentRequirements.allOf(requirements);
    }
    public static Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> pendingOwnerForActor(
            FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        var authority = state.actorExecutions().actors().get(actor);
        if (authority == null) return Optional.empty();
        var execution = authority.current().filter(id -> id.activityKind() == io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST)
                .or(() -> authority.suspended().filter(id -> id.activityKind() == io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST));
        return execution.flatMap(id -> state.resourceSites().sites().values().stream()
                .flatMap(site -> site.harvestJob(id.activityOwnerId()).stream())
                .filter(job -> job.workerId().equals(actor) && job.equipment().pending().isPresent())
                .map(ResourceSiteHarvestJob::id).findFirst());
    }
    public static boolean pendingForContainer(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId container) {
        return state.resourceSites().sites().values().stream().flatMap(site -> site.harvestJobs().values().stream())
                .anyMatch(job -> job.equipment().pending().filter(step -> step.slot().containerId().equals(container)).isPresent());
    }
    public static boolean transferAvailable(FrontierWorldState state, ResourceSiteHarvestJob job) {
        var endpoint = endpoint(state, job);
        var item = state.inventory().items().get(job.equipment().itemId());
        var container = state.inventory().containers().get(endpoint.containerId());
        if (item == null || container == null || ReferenceContainerCustody.blocksCanonicalUse(state, container.id())
                || container.storage().filter(policy -> !policy.filter().accepts(item.itemKind())).isPresent()
                || ContainerPhysicalAuthorityComposition.pending(state, container.id())) return false;
        return job.equipment().stage() == WorkToolAssignment.Stage.ACQUIRING
                ? item.custody() instanceof InventoryCustody.ContainerSlot stored && stored.containerId().equals(container.id())
                    && requirements().serviceable(state.bootstrap().ruleset().tools(), item)
                : ContainerStorageAdmission.first(state, container.id(), Optional.empty(), slot -> false).isPresent();
    }
    public static Optional<ToolRequirement> requirement(ResourceFieldCycle cycle, ResourceFieldLayout.CellId cell) {
        return switch (cycle.expectedWorkOutcome(cell)) {
            case HARVESTED -> Optional.of(FieldHarvestExtraction.DEFINITION.toolRequirement());
            case TILLED -> Optional.of(ResourceFieldCycle.Soil.catalog().step(cycle.cell(cell).soil()).tool());
            default -> Optional.empty();
        };
    }
    public static ExactItemStack held(FrontierWorldState state, ResourceSiteHarvestJob job) {
        var item = state.inventory().items().get(job.equipment().itemId());
        if (item == null || job.equipment().stage() != WorkToolAssignment.Stage.EQUIPPED
                || !item.custody().equals(new InventoryCustody.Actor(job.workerId())))
            throw new IllegalArgumentException("field work lacks its exact equipped tool");
        return item;
    }
    public static Optional<ToolUse> use(FrontierWorldState state, ResourceSiteHarvestJob job, ResourceFieldCycle.WorkOutcome outcome) {
        var cycle = state.resourceSites().cycle(job.siteId()); var cell = job.target().cellId();
        if (cycle.expectedWorkOutcome(cell) != outcome) throw new IllegalArgumentException("tool use has a foreign field operation");
        return requirement(cycle, cell).map(required -> ToolUse.prepare(held(state, job), state.bootstrap().ruleset().tools(), required));
    }
    public static boolean transferring(ResourceSiteHarvestJob job) {
        return job.equipment().stage() == WorkToolAssignment.Stage.ACQUIRING
                || job.progress().complete() && job.undeliveredYieldQuantity() == 0 && job.equipment().stage() == WorkToolAssignment.Stage.EQUIPPED
                    && job.equipment().returnTarget().isPresent();
    }
    public static boolean needsReturnStorage(FrontierWorldState state, ResourceSiteHarvestJob job) {
        return job.progress().complete() && job.undeliveredYieldQuantity() == 0 && job.equipment().stage() == WorkToolAssignment.Stage.EQUIPPED
                && job.equipment().pending().isEmpty()
                && job.equipment().returnTarget().filter(target -> WorkEquipmentStorage.available(state, held(state, job), target)).isEmpty();
    }
    /** Eligibility reads stock/capacity only; choosing a reachable endpoint belongs to the work planner. */
    public static boolean waitingForStorage(FrontierWorldState state, ResourceSiteHarvestJob job) {
        if (transferring(job)) return !transferAvailable(state, job);
        return needsReturnStorage(state, job) && ResourceStorageSelection.candidates(state,
                held(state, job).economicOwnerId(), held(state, job).itemKind(),
                state.actorLocations().get(job.workerId()).supportingSurface(), Set.of(ResourceStorageRole.WAREHOUSE))
                .stream().noneMatch(endpoint -> WorkEquipmentStorage.available(state, held(state, job), endpoint));
    }
    public static Optional<FieldEquipmentChanged> selectReturn(FrontierWorldState state, ResourceSiteHarvestJob job) {
        if (!needsReturnStorage(state, job) || job.progress().hasPendingPhysicalWork()) return Optional.empty();
        return WorkEquipmentStorage.select(state, held(state, job), state.actorLocations().get(job.workerId()).supportingSurface())
                .map(target -> new FieldEquipmentChanged(job.siteId(), job.id(), job.equipment().stage(),
                        FieldEquipmentChanged.Action.SELECT_RETURN, Optional.empty(), Optional.of(target)));
    }
    public static ShipmentEndpoint endpoint(FrontierWorldState state, ResourceSiteHarvestJob job) {
        return job.equipment().stage() == WorkToolAssignment.Stage.ACQUIRING ? job.equipment().source()
                : job.equipment().returnTarget().orElseThrow(() -> new IllegalArgumentException("equipment return has no selected destination"));
    }
    public static ActorContainerItemOrder order(FrontierWorldState state, ResourceSiteHarvestJob job,
                                                Optional<InventoryCustody.ContainerSlot> retainedSlot) {
        if (!transferring(job)) throw new IllegalArgumentException("field equipment has no current service operation");
        return WorkEquipmentOrders.create(state, job.id(), job.workerId(), state.inventory().items().get(job.equipment().itemId()),
                endpoint(state, job), job.equipment().stage() == WorkToolAssignment.Stage.ACQUIRING
                        ? ActorContainerItemOrder.Direction.TAKE : ActorContainerItemOrder.Direction.PLACE,
                retainedSlot, job.equipment().stage().wireTag(), job.storageRevision());
    }
    private FieldEquipment() { }
}
