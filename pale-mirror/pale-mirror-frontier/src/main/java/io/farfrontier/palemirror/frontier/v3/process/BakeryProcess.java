package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The bakery job owns each COLD movement, custody and station-work transition. */
public final class BakeryProcess {
    private BakeryProcess() { }

    /** Exposes the current typed transfer order to the physical executor, never recipe policy. */
    public static ActorContainerItemOrder currentItemOrder(FrontierWorldState state, ProductionJob job) {
        BakeryColdStep.Action action = switch (job.bakeryWork().orElseThrow().phase()) {
            case DEPOT_PICKUP -> BakeryColdStep.Action.PICKUP;
            case STATION_LOAD -> BakeryColdStep.Action.LOAD;
            case STATION_UNLOAD -> BakeryColdStep.Action.UNLOAD;
            case DEPOT_DELIVERY -> BakeryColdStep.Action.DELIVER;
            case PROCESSING -> throw new IllegalArgumentException("station recipe is not an actor item transfer");
            case DELIVERED -> throw new IllegalArgumentException("delivered bakery output has no actor transfer");
        };
        return itemOrder(state, job, action);
    }

    static FrontierWorldState start(FrontierWorldState state, ProductionJob job, SubjectId assertedInput) {
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        if (work.phase() != BakeryWorkState.Phase.DEPOT_PICKUP || !job.consumedItemId().equals(assertedInput)
                || state.productionJobs().containsKey(job.id()))
            throw new IllegalArgumentException("bakery start must retain one fresh depot pickup");
        station(state, job);
        if (job.inputHold() instanceof ProductionInputHold.Materialized) {
            ExactItemStack wheat = state.inventory().items().get(assertedInput);
            if (wheat == null || !"minecraft:wheat".equals(wheat.itemKind()) || wheat.count() != job.outputCount()
                    || !wheat.economicOwnerId().equals(job.settlementId())
                    || !(wheat.custody() instanceof InventoryCustody.ContainerSlot slot)
                    || !slot.containerId().equals(FrontierWorldState.depotId(job.settlementId())))
                throw new IllegalArgumentException("bakery exact start has no owned depot wheat");
            return CompanyWorkPaymentProcess.reserve(state.withProductionJob(job), job);
        }
        if (!(job.inputHold() instanceof ProductionInputHold.FungibleCold
                || job.inputHold() instanceof ProductionInputHold.FungibleBound))
            throw new IllegalArgumentException("bakery job has no supported input custody");
        return CompanyWorkPaymentProcess.reserve(state.startFungibleProductionJob(job), job);
    }

    /** A witnessed source loss does not retire the accepted order; choose only currently unclaimed depot wheat. */
    static Optional<BakeryInputReallocated> planInputReallocation(FrontierWorldState state, ProductionJob job) {
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        if (work.phase() != BakeryWorkState.Phase.DEPOT_PICKUP || work.pendingPhysicalStep().isPresent()
                || work.block().map(value -> value.reason() != BakeryWorkBlock.Reason.SOURCE_CHANGED).orElse(true))
            return Optional.empty();
        if (job.inputHold() instanceof ProductionInputHold.Materialized) {
            SubjectId depot = FrontierWorldState.depotId(job.settlementId());
            return state.inventory().items().values().stream().sorted(java.util.Comparator.comparing(ExactItemStack::id))
                    .filter(item -> item.economicOwnerId().equals(job.settlementId())
                            && item.itemKind().equals("minecraft:wheat") && item.count() == job.outputCount()
                            && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot)
                            && !ResourceSiteHarvestLineage.hasPendingOutputReceipt(state.resourceSites().sites().values(), item.id())
                            && state.productionJobs().values().stream().noneMatch(other -> !other.id().equals(job.id())
                                && other.inputHold() instanceof ProductionInputHold.Materialized
                                && other.consumedItemId().equals(item.id())))
                    .findFirst().map(item -> new BakeryInputReallocated(job.id(), new ProductionInputHold.Materialized(item.id())));
        }
        SubjectId claim = claimId(job);
        if (state.inventory().fungibleResources().claims().containsKey(claim)) return Optional.empty();
        SubjectId depot = FrontierWorldState.depotId(job.settlementId());
        return FungibleResourceCustodySupport.selectAtContainer(state, depot, job.settlementId(),
                "minecraft:wheat", job.outputCount()).filter(selection ->
                ProductionResourceCustody.canStart(state, selection, job.outputCount())).map(selection ->
                new BakeryInputReallocated(job.id(), ProductionResourceCustody.holdForStart(state, selection,
                        claim, job.outputCount())));
    }

    static FrontierWorldState reduceInputReallocated(FrontierWorldState state, SubjectId subject,
                                                     BakeryInputReallocated reallocated) {
        ProductionJob job = state.productionJobs().get(reallocated.jobId());
        if (job == null || !job.settlementId().equals(subject)
                || !reallocated.equals(planInputReallocation(state, job).orElse(null)))
            throw new IllegalArgumentException("bakery replacement is not the current unclaimed owned depot input");
        ProductionInputHold replacement = reallocated.replacement();
        ProductionJob updated = job.reallocateBakeryInput(replacement);
        Map<SubjectId, ProductionJob> jobs = new LinkedHashMap<>(state.productionJobs());
        jobs.put(job.id(), updated);
        if (replacement instanceof ProductionInputHold.Materialized)
            return state.withChanges(FrontierWorldStateUpdate.begin().productionJobs(jobs));
        SubjectId accountId = replacement instanceof ProductionInputHold.FungibleCold cold
                ? cold.accountId() : ((ProductionInputHold.FungibleBound) replacement).accountId();
        SubjectId claimId = replacement instanceof ProductionInputHold.FungibleCold cold
                ? cold.claimId() : ((ProductionInputHold.FungibleBound) replacement).claimId();
        Map<SubjectId, Integer> lots = replacement instanceof ProductionInputHold.FungibleCold cold
                ? cold.inputLots() : ((ProductionInputHold.FungibleBound) replacement).inputLots();
        ClaimAllocation allocation = new ClaimAllocation(claimId, job.id(), job.settlementId(),
                "minecraft:wheat", job.outputCount(), lots, ClaimPurpose.PRODUCTION_WORK);
        FungibleResourceLedger ledger = replacement instanceof ProductionInputHold.FungibleBound bound
                ? state.inventory().fungibleResources().reserveBound(allocation, accountId, bound.authorityEpoch())
                : state.inventory().fungibleResources().reserve(allocation, accountId);
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().withFungibleResources(ledger)).productionJobs(jobs));
    }

    /** Read-only reason for a retained bakery job not receiving its next COLD turn. */
    public static Optional<String> coldBlocker(FrontierWorldState state, ProductionJob job) {
        return coldBlocker(state, job, true);
    }

    private static Optional<String> coldBlocker(FrontierWorldState state, ProductionJob job, boolean checkOutputCapacity) {
        if (job.bakeryWork().orElseThrow().block().isPresent())
            return Optional.of("BAKERY_" + job.bakeryWork().orElseThrow().block().orElseThrow().reason());
        if (job.bakeryWork().orElseThrow().pendingPhysicalStep().isPresent()) return Optional.of("PENDING_PHYSICAL_EFFECT");
        if (job.inputHold() instanceof ProductionInputHold.FungibleBound
                && job.bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.DEPOT_PICKUP)
            return Optional.of("SOURCE_PHYSICAL_BINDING");
        if (!FrontierSceneAdmission.available(state, List.of(job.workerId()))) return Optional.of("AMBIENT_ACTOR_AUTHORITY");
        if (state.sceneLeases().values().stream().anyMatch(lease -> lease.retainsMemberCustody(job.workerId())))
            return Optional.of("SCENE_ACTOR_AUTHORITY");
        ProductionStationSpec station = station(state, job);
        SubjectId depot = FrontierWorldState.depotId(job.settlementId());
        if (ReferenceContainerCustody.hasLiveCustody(state, depot)) return Optional.of("DEPOT_PHYSICAL_AUTHORITY");
        if (ReferenceContainerCustody.blocksCanonicalUse(state, depot)) return Optional.of("DEPOT_CONFLICT");
        if (ReferenceContainerCustody.hasLiveCustody(state, station.containerId()))
            return Optional.of("STATION_PHYSICAL_AUTHORITY");
        if (ReferenceContainerCustody.blocksCanonicalUse(state, station.containerId()))
            return Optional.of("STATION_CONFLICT");
        if (checkOutputCapacity && ProductionOutputCapacity.depotDeliveryUnavailable(state, job))
            return Optional.of("DEPOT_STORAGE_FULL");
        return Optional.empty();
    }

    /** A read-only route diagnostic; no planner state or physical world is changed. */
    public static Optional<String> coldRouteBlocker(FrontierWorldState state, ProductionJob job) {
        if (coldBlocker(state, job).isPresent()) return Optional.empty();
        try {
            BakeryKnownNavigation.path(state, job);
            return Optional.empty();
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            return Optional.of(unavailable.getMessage());
        }
    }

    static Optional<BakeryColdStep> planColdStep(FrontierWorldState state, ProductionJob job) {
        return planColdStep(state, job, true);
    }

    private static Optional<BakeryColdStep> planColdStep(FrontierWorldState state, ProductionJob job,
                                                         boolean checkOutputCapacity) {
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        if (work.phase() == BakeryWorkState.Phase.DELIVERED) {
            boolean openScene = state.sceneLeases().values().stream().anyMatch(lease ->
                    lease.status() != SceneLeaseStatus.CLOSED && FrontierSceneBehaviors.isProductionWork(lease)
                    && FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id()));
            return openScene ? Optional.empty() : Optional.of(new BakeryColdStep(job.id(), work.phase(),
                    BakeryColdStep.Action.FINALIZE, state.actorLocations().get(job.workerId()).supportingSurface()));
        }
        if (coldBlocker(state, job, checkOutputCapacity).isPresent()) return Optional.empty();
        List<SurfaceAnchor> route;
        try { route = BakeryKnownNavigation.path(state, job); }
        catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return Optional.empty(); }
        if (route.size() > 1) return Optional.of(new BakeryColdStep(job.id(), work.phase(), BakeryColdStep.Action.MOVE, route.get(1)));
        SurfaceAnchor current = state.actorLocations().get(job.workerId()).supportingSurface();
        BakeryColdStep.Action action = switch (work.phase()) {
            case DEPOT_PICKUP -> BakeryColdStep.Action.PICKUP;
            case STATION_LOAD -> BakeryColdStep.Action.LOAD;
            case PROCESSING -> work.completedWorkTicks() < ProductionWorkProgress.REQUIRED_PROCESSING_TICKS
                    ? BakeryColdStep.Action.WORK_TICK : BakeryColdStep.Action.RECIPE;
            case STATION_UNLOAD -> BakeryColdStep.Action.UNLOAD;
            case DEPOT_DELIVERY -> BakeryColdStep.Action.DELIVER;
            case DELIVERED -> throw new IllegalStateException("delivered bakery work is handled before routing");
        };
        return Optional.of(new BakeryColdStep(job.id(), work.phase(), action, current));
    }

    static FrontierWorldState reduceColdStep(FrontierWorldState state, SubjectId subject, BakeryColdStep step) {
        ProductionJob job = state.productionJobs().get(step.jobId());
        if (job == null || !job.settlementId().equals(subject)
                || job.bakeryWork().isEmpty() || job.bakeryWork().orElseThrow().phase() != step.expectedPhase()
                // WAL replay must validate historically committed movement using the old
                // capacity-independent route. New planning still stops at a full depot.
                || !step.equals(planColdStep(state, job, false).orElse(null)))
            throw new IllegalArgumentException("bakery COLD step is not the current exclusive planned successor");
        if (step.action() == BakeryColdStep.Action.DELIVER
                && ProductionOutputCapacity.depotDeliveryUnavailable(state, job))
            throw new IllegalArgumentException("bakery output cannot enter a full depot");
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        if (step.action() == BakeryColdStep.Action.FINALIZE) return finish(state, job, state.inventory());
        if (step.action() == BakeryColdStep.Action.MOVE) {
            Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
            actors.put(job.workerId(), actors.get(job.workerId()).withBody(BodyPosition.above(step.nextSurface())));
            return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        }
        if (step.action() == BakeryColdStep.Action.WORK_TICK)
            return FrontierProductionWorkSceneSupport.replaceJob(state, job.withBakeryWork(work.workTick()));
        ExactInventory inventory;
        if (step.action() == BakeryColdStep.Action.RECIPE) {
            ProductionStationSpec station = station(state, job);
            if (job.inputHold() instanceof ProductionInputHold.Materialized) {
                ExactItemStack input = state.inventory().items().get(job.consumedItemId());
                ExactItemStack output = new ExactItemStack(job.outputItemId(), job.settlementId(), job.outputItemKind(),
                        job.outputCount(), new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot()));
                inventory = ProductionStationRecipe.transformExactCold(state, station, input, output);
            } else {
                FungibleResourceLedger ledger = state.inventory().fungibleResources();
                ResourceLot output = new ResourceLot(job.outputItemId(), job.settlementId(), job.outputItemKind(), job.outputCount(),
                        "recipe:bread", job.inputQuantities().keySet().stream().sorted().toList());
                inventory = ProductionStationRecipe.transformCold(state, station, work.stationAccountId(), job.inputQuantities(),
                        Map.of(claimId(job), job.outputCount()), output);
            }
            return replace(state, job.withBakeryWork(work.advance(BakeryWorkState.Phase.STATION_UNLOAD)), inventory);
        }
        ActorContainerItemOrder order = itemOrder(state, job, step.action());
        inventory = ActorItemCustody.transferCold(state, order);
        if (step.action() == BakeryColdStep.Action.DELIVER) return finish(state, job, inventory);
        BakeryWorkState.Phase next = switch (step.action()) {
            case PICKUP -> BakeryWorkState.Phase.STATION_LOAD;
            case LOAD -> BakeryWorkState.Phase.PROCESSING;
            case UNLOAD -> BakeryWorkState.Phase.DEPOT_DELIVERY;
            default -> throw new IllegalArgumentException("bakery transfer has no next custody phase");
        };
        return replace(state, job.withBakeryWork(work.advance(next)), inventory);
    }

    static FrontierWorldState hotGoalArrived(FrontierWorldState state, SubjectId subject, BakeryHotGoalArrived arrived) {
        ProductionJob job = state.productionJobs().get(arrived.jobId());
        if (job == null || !job.settlementId().equals(subject) || job.bakeryWork().isEmpty()
                || job.bakeryWork().orElseThrow().phase() != arrived.phase())
            throw new IllegalArgumentException("bakery HOT arrival has no current owner phase");
        SceneLease lease = FrontierProductionWorkSceneSupport.requireHotLease(state, job, arrived.leaseId());
        BodyPosition expected = BakeryWorkGoal.current(state, job).station().standingBody();
        if (!arrived.observedWorker().equals(expected))
            throw new IllegalArgumentException("bakery HOT arrival differs from the semantic station");
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases());
        leases.put(lease.id(), lease.withMemberPositions(Map.of(job.workerId(), expected)));
        return state.withChanges(FrontierWorldStateUpdate.begin().sceneLeases(leases));
    }

    static FrontierWorldState hotBlockChanged(FrontierWorldState state, SubjectId subject, BakeryHotBlockChanged changed) {
        ProductionJob job = state.productionJobs().get(changed.jobId());
        if (job == null || !job.settlementId().equals(subject) || job.bakeryWork().isEmpty()
                || job.bakeryWork().orElseThrow().phase() != changed.phase())
            throw new IllegalArgumentException("bakery block has no current owner phase");
        SceneLease lease = state.sceneLeases().get(changed.leaseId());
        if (lease == null || (lease.status() != SceneLeaseStatus.HOT && lease.status() != SceneLeaseStatus.DRAINING)
                || !FrontierSceneBehaviors.isProductionWork(lease)
                || !FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id())
                || lease.members().size() != 1 || !lease.members().getFirst().actorId().equals(job.workerId()))
            throw new IllegalArgumentException("bakery block lacks the exact live worker scene");
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        if (changed.block().isPresent()) {
            BakeryWorkBlock block = changed.block().orElseThrow();
            SubjectId scope = block.reason() == BakeryWorkBlock.Reason.ROUTE_BLOCKED
                    ? job.facilityId() : changed.phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                    || changed.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY
                    ? FrontierWorldState.depotId(job.settlementId()) : station(state, job).containerId();
            if (!block.scopeId().equals(scope) || work.pendingPhysicalStep().isPresent()
                    && block.reason() != BakeryWorkBlock.Reason.AMBIGUOUS_EFFECT)
                throw new IllegalArgumentException("bakery block has foreign physical scope or effect phase");
        } else if (work.block().isEmpty() || work.block().orElseThrow().reason() == BakeryWorkBlock.Reason.AMBIGUOUS_EFFECT
                || work.block().orElseThrow().reason() == BakeryWorkBlock.Reason.SOURCE_CHANGED
                && !hasCurrentInputClaim(state, job))
            throw new IllegalArgumentException("bakery block clearance lacks a reversible current obstruction");
        return FrontierProductionWorkSceneSupport.replaceJob(state, job.withBakeryWork(work.withBlock(changed.block())));
    }

    private static boolean hasCurrentInputClaim(FrontierWorldState state, ProductionJob job) {
        if (job.inputHold() instanceof ProductionInputHold.Materialized) {
            ExactItemStack input = state.inventory().items().get(job.consumedItemId());
            return input != null && input.custody() instanceof InventoryCustody.ContainerSlot slot
                    && slot.containerId().equals(FrontierWorldState.depotId(job.settlementId()));
        }
        SubjectId claimId = switch (job.inputHold()) {
            case ProductionInputHold.FungibleCold cold -> cold.claimId();
            case ProductionInputHold.FungibleBound bound -> bound.claimId();
            default -> null;
        };
        return claimId == null || state.inventory().fungibleResources().claims().containsKey(claimId);
    }

    static FrontierWorldState prepareHotEffect(FrontierWorldState state, SubjectId subject,
                                               BakeryHotEffectPrepared prepared) {
        ProductionJob job = state.productionJobs().get(prepared.jobId());
        if (job == null || !job.settlementId().equals(subject) || job.bakeryWork().isEmpty())
            throw new IllegalArgumentException("bakery HOT effect has no exact owner job");
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        SceneLease lease = FrontierProductionWorkSceneSupport.requireHotLease(state, job, prepared.leaseId());
        ProductionStationSpec station = station(state, job);
        if (work.phase() != prepared.phase() || work.pendingPhysicalStep().isPresent()
                || !lease.memberPosition(job.workerId()).equals(BakeryWorkGoal.current(state, job).station().standingBody())
                || prepared.phase() == BakeryWorkState.Phase.PROCESSING
                && work.completedWorkTicks() != ProductionWorkProgress.REQUIRED_PROCESSING_TICKS)
            throw new IllegalArgumentException("bakery HOT effect is not at a ready current station");
        int requiredSlot = switch (prepared.phase()) {
            case DEPOT_PICKUP, STATION_UNLOAD -> -1;
            case STATION_LOAD -> station.inputSlot();
            case PROCESSING -> station.outputSlot();
            case DEPOT_DELIVERY -> prepared.destinationSlot();
            case DELIVERED -> throw new IllegalArgumentException("delivered bakery output has no physical effect");
        };
        if (prepared.destinationSlot() != requiredSlot
                || prepared.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY
                && !state.containerSlotAvailable(new InventoryCustody.ContainerSlot(
                FrontierWorldState.depotId(job.settlementId()), prepared.destinationSlot())))
            throw new IllegalArgumentException("bakery HOT effect has no reserved current target port");
        BakeryPhysicalStep step = new BakeryPhysicalStep(prepared.phase(), prepared.leaseId(), prepared.destinationSlot());
        return FrontierProductionWorkSceneSupport.replaceJob(state, job.withBakeryWork(work.preparePhysical(step)));
    }

    static FrontierWorldState hotWorkTick(FrontierWorldState state, SubjectId subject, BakeryHotWorkTick tick) {
        ProductionJob job = state.productionJobs().get(tick.jobId());
        if (job == null || !job.settlementId().equals(subject) || job.bakeryWork().isEmpty())
            throw new IllegalArgumentException("bakery labor has no exact job");
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        SceneLease lease = FrontierProductionWorkSceneSupport.requireHotLease(state, job, tick.leaseId());
        if (work.phase() != BakeryWorkState.Phase.PROCESSING || work.pendingPhysicalStep().isPresent()
                || tick.nextCompletedTicks() != work.completedWorkTicks() + 1
                || !tick.observedWorker().equals(station(state, job).workerStation().standingBody())
                || !lease.memberPosition(job.workerId()).equals(tick.observedWorker()))
            throw new IllegalArgumentException("bakery labor was not observed at its retained station");
        return FrontierProductionWorkSceneSupport.replaceJob(state, job.withBakeryWork(work.workTick()));
    }

    static FrontierWorldState releaseHotHand(FrontierWorldState state, SubjectId subject, BakeryHotHandRelease released) {
        ProductionJob job = state.productionJobs().get(released.jobId());
        if (job == null || !job.settlementId().equals(subject) || job.bakeryWork().isEmpty()
                || job.inputHold() instanceof ProductionInputHold.Materialized)
            throw new IllegalArgumentException("bakery hand release has no fungible owner job");
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        SceneLease lease = state.sceneLeases().get(released.sceneRelease().leaseId());
        if ((work.phase() != BakeryWorkState.Phase.STATION_LOAD
                && work.phase() != BakeryWorkState.Phase.DEPOT_DELIVERY)
                || work.pendingPhysicalStep().isPresent() || !work.actorAccountId().equals(released.actorAccountId())
                || lease == null || lease.status() != SceneLeaseStatus.DRAINING
                || !FrontierSceneBehaviors.isProductionWork(lease)
                || !FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id())
                || lease.members().size() != 1 || !lease.members().getFirst().actorId().equals(job.workerId())
                || released.sceneRelease().members().size() != 1
                || !released.sceneRelease().members().getFirst().actorId().equals(job.workerId()))
            throw new IllegalArgumentException("bakery hand release has no draining exact baker scene");
        PhysicalStackAddress.ActorHand address = (PhysicalStackAddress.ActorHand) released.observedHand().address();
        FungibleResourceLedger ledger = state.inventory().fungibleResources();
        CustodyAccount account = ledger.accounts().get(work.actorAccountId());
        List<PhysicalStackBinding> bindings = ledger.bindings().values().stream()
                .filter(binding -> binding.accountId().equals(work.actorAccountId())).toList();
        String kind = work.phase() == BakeryWorkState.Phase.STATION_LOAD ? "minecraft:wheat" : "minecraft:bread";
        if (!address.actorId().equals(job.workerId()) || !address.entityId().equals(lease.members().getFirst().entityId())
                || account == null || !account.custody().equals(new ResourceCustody.Actor(job.workerId()))
                || bindings.size() != 1 || !bindings.getFirst().address().equals(address)
                || bindings.getFirst().authorityEpoch() != released.actorEpoch()
                || !bindings.getFirst().lotQuantities().equals(account.lotQuantities())
                || !bindings.getFirst().claimQuantities().equals(account.claimQuantities())
                || !kind.equals(released.observedHand().itemKind())
                || released.observedHand().quantity() != job.outputCount())
            throw new IllegalArgumentException("bakery hand release does not match its current physical cargo");
        FrontierWorldState unbound = state.withInventory(state.inventory().withFungibleResources(
                ledger.releaseBindings(account.id(), released.actorEpoch())));
        return unbound.releaseSceneLease(lease.id(), released.sceneRelease().members());
    }

    static FrontierWorldState materializeHotHand(FrontierWorldState state, SubjectId subject,
                                                  BakeryHotHandMaterialized observed) {
        ProductionJob job = state.productionJobs().get(observed.jobId());
        if (job == null || !job.settlementId().equals(subject) || job.bakeryWork().isEmpty()
                || job.inputHold() instanceof ProductionInputHold.Materialized)
            throw new IllegalArgumentException("materialized bakery hand has no fungible owner job");
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        SceneLease lease = FrontierProductionWorkSceneSupport.requireHotLease(state, job, observed.leaseId());
        PhysicalStackAddress.ActorHand address = (PhysicalStackAddress.ActorHand) observed.observedHand().address();
        String kind = work.phase() == BakeryWorkState.Phase.STATION_LOAD ? "minecraft:wheat" : "minecraft:bread";
        CustodyAccount account = state.inventory().fungibleResources().accounts().get(work.actorAccountId());
        if ((work.phase() != BakeryWorkState.Phase.STATION_LOAD
                && work.phase() != BakeryWorkState.Phase.DEPOT_DELIVERY)
                || work.pendingPhysicalStep().isPresent() || !work.actorAccountId().equals(observed.actorAccountId())
                || !address.actorId().equals(job.workerId())
                || !address.entityId().equals(lease.members().getFirst().entityId())
                || observed.actorEpoch() != Math.max(1L, lease.revision())
                || !kind.equals(observed.observedHand().itemKind())
                || observed.observedHand().quantity() != job.outputCount()
                || account == null || !account.custody().equals(new ResourceCustody.Actor(job.workerId()))
                || state.inventory().fungibleResources().bindings().values().stream()
                    .anyMatch(binding -> binding.accountId().equals(account.id())))
            throw new IllegalArgumentException("materialized bakery hand differs from its retained cold batch or body");
        FungibleResourceLedger ledger = state.inventory().fungibleResources();
        return state.withInventory(state.inventory().withFungibleResources(ledger.rebind(account.id(),
                observed.actorEpoch(), FungiblePhysicalObservation.bind(ledger, account.id(),
                        observed.actorEpoch(), List.of(observed.observedHand())))));
    }

    static FrontierWorldState observeHotEffect(FrontierWorldState state, SubjectId subject,
                                               BakeryHotEffectObserved observed) {
        ProductionJob job = state.productionJobs().get(observed.jobId());
        if (job == null || !job.settlementId().equals(subject) || job.bakeryWork().isEmpty())
            throw new IllegalArgumentException("observed bakery effect has no retained owner job");
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        BakeryPhysicalStep pending = work.pendingPhysicalStep().orElseThrow(
                () -> new IllegalArgumentException("observed bakery effect was not durably prepared"));
        SceneLease lease = state.sceneLeases().get(observed.leaseId());
        if (lease == null || (lease.status() != SceneLeaseStatus.HOT && lease.status() != SceneLeaseStatus.DRAINING)
                || !FrontierSceneBehaviors.isProductionWork(lease)
                || !FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id())
                || lease.members().size() != 1 || !lease.members().getFirst().actorId().equals(job.workerId()))
            throw new IllegalArgumentException("bakery physical receipt has no current retained worker scene");
        if (pending.phase() != observed.phase() || !pending.leaseId().equals(observed.leaseId())
                || !observed.observedWorker().equals(BakeryWorkGoal.current(state, job).station().standingBody())
                || !lease.memberPosition(job.workerId()).equals(observed.observedWorker()))
            throw new IllegalArgumentException("observed bakery effect has a foreign phase, body or lease");
        if (observed.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY
                && !(job.inputHold() instanceof ProductionInputHold.Materialized)
                && (observed.destination().size() != 1
                || !observed.destination().getFirst().address().equals(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(FrontierWorldState.depotId(job.settlementId()),
                        pending.destinationSlot())))))
            throw new IllegalArgumentException("observed bakery delivery differs from its prepared depot slot");
        BakeryWorkState ready = work.withBlock(Optional.empty()).withoutPhysical();
        ExactInventory inventory;
        if (observed.phase() == BakeryWorkState.Phase.PROCESSING) {
            ProductionStationSpec station = station(state, job);
            if (job.inputHold() instanceof ProductionInputHold.Materialized) {
                if (!observed.remainingSource().isEmpty() || !observed.destination().isEmpty())
                    throw new IllegalArgumentException("exact recipe cannot carry fungible witness layouts");
                ExactItemStack input = state.inventory().items().get(job.consumedItemId());
                ExactItemStack output = new ExactItemStack(job.outputItemId(), job.settlementId(), job.outputItemKind(),
                        job.outputCount(), new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot()));
                inventory = ProductionStationRecipe.transformExactObserved(state, station, input, output);
            } else {
                ResourceLot output = new ResourceLot(job.outputItemId(), job.settlementId(), job.outputItemKind(), job.outputCount(),
                        "recipe:bread", job.inputQuantities().keySet().stream().sorted().toList());
                inventory = ProductionStationRecipe.transformObserved(state, station, work.stationAccountId(),
                        observed.sourceEpoch(), job.inputQuantities(), Map.of(claimId(job), job.outputCount()),
                        output, observed.destination());
                if (!observed.remainingSource().isEmpty())
                    throw new IllegalArgumentException("station recipe cannot retain unprocessed source wheat");
            }
            return replace(state, job.withBakeryWork(ready.advance(BakeryWorkState.Phase.STATION_UNLOAD)), inventory);
        }
        BakeryColdStep.Action action = switch (observed.phase()) {
            case DEPOT_PICKUP -> BakeryColdStep.Action.PICKUP;
            case STATION_LOAD -> BakeryColdStep.Action.LOAD;
            case STATION_UNLOAD -> BakeryColdStep.Action.UNLOAD;
            case DEPOT_DELIVERY -> BakeryColdStep.Action.DELIVER;
            case PROCESSING -> throw new IllegalStateException("recipe handled above");
            case DELIVERED -> throw new IllegalArgumentException("delivered bakery job has no physical transfer");
        };
        ActorContainerItemOrder order = itemOrder(state, job, action);
        inventory = ActorItemCustody.transferObserved(state, order, observed.sourceEpoch(), observed.destinationEpoch(),
                observed.remainingSource(), observed.destination());
        if (action == BakeryColdStep.Action.DELIVER)
            return replace(state, job.withBakeryWork(ready.advance(BakeryWorkState.Phase.DELIVERED)), inventory);
        BakeryWorkState.Phase next = switch (action) {
            case PICKUP -> BakeryWorkState.Phase.STATION_LOAD;
            case LOAD -> BakeryWorkState.Phase.PROCESSING;
            case UNLOAD -> BakeryWorkState.Phase.DEPOT_DELIVERY;
            default -> throw new IllegalStateException("observed bakery transfer has no successor");
        };
        return replace(state, job.withBakeryWork(ready.advance(next)), inventory);
    }

    private static FrontierWorldState replace(FrontierWorldState state, ProductionJob job, ExactInventory inventory) {
        Map<SubjectId, ProductionJob> jobs = new LinkedHashMap<>(state.productionJobs()); jobs.put(job.id(), job);
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).productionJobs(jobs));
    }

    private static FrontierWorldState finish(FrontierWorldState state, ProductionJob job, ExactInventory delivered) {
        StrategicTask task = state.strategicPlans().tasks().get(job.taskId());
        if (task == null || task.status() != StrategicTaskStatus.ACTIVE || !task.ownerId().equals(job.settlementId()))
            throw new IllegalArgumentException("delivered bakery output has no active owner task");
        FrontierWorldState paid = CompanyWorkPaymentProcess.settle(state, job);
        Optional<MarketWorkOrder> order = paid.companies().market().acceptedForJob(job.id());
        CompanyRegistry companies = order.map(value -> paid.companies().withMarket(paid.companies().market().complete(value.id(), job)))
                .orElse(paid.companies());
        Map<SubjectId, ProductionJob> jobs = new LinkedHashMap<>(state.productionJobs()); jobs.remove(job.id());
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(delivered.withEconomics(paid.inventory().economics())).productionJobs(jobs)
                .companies(companies).strategicPlans(state.strategicPlans().transitionTask(task.id(), StrategicTaskStatus.COMPLETED)));
    }

    static ActorContainerItemOrder itemOrder(FrontierWorldState state, ProductionJob job, BakeryColdStep.Action action) {
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        ProductionStationSpec station = station(state, job);
        SubjectId depotId = FrontierWorldState.depotId(job.settlementId());
        SurfaceAnchor surface = BakeryWorkGoal.current(state, job).station();
        ActorContainerItemOrder.Direction direction = action == BakeryColdStep.Action.PICKUP || action == BakeryColdStep.Action.UNLOAD
                ? ActorContainerItemOrder.Direction.TAKE : ActorContainerItemOrder.Direction.PLACE;
        boolean output = action == BakeryColdStep.Action.UNLOAD || action == BakeryColdStep.Action.DELIVER;
        ActorContainerItemOrder.ContainerEndpoint endpoint;
        ActorContainerItemOrder.Portion portion;
        if (job.inputHold() instanceof ProductionInputHold.Materialized) {
            ExactItemStack item = state.inventory().items().get(output ? job.outputItemId() : job.consumedItemId());
            if (item == null) throw new IllegalArgumentException("bakery exact transfer lacks its current item");
            endpoint = switch (action) {
                case PICKUP -> new ActorContainerItemOrder.ContainerEndpoint.ExactSlot((InventoryCustody.ContainerSlot) item.custody());
                case LOAD -> new ActorContainerItemOrder.ContainerEndpoint.ExactStationSlot(station, ActorContainerItemOrder.StationPort.INPUT,
                        new InventoryCustody.ContainerSlot(station.containerId(), station.inputSlot()));
                case UNLOAD -> new ActorContainerItemOrder.ContainerEndpoint.ExactStationSlot(station, ActorContainerItemOrder.StationPort.OUTPUT,
                        new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot()));
                case DELIVER -> new ActorContainerItemOrder.ContainerEndpoint.ExactSlot(new InventoryCustody.ContainerSlot(depotId,
                        work.pendingPhysicalStep().map(BakeryPhysicalStep::destinationSlot)
                                .orElseGet(() -> state.firstFreeContainerSlot(depotId).orElseThrow())));
                default -> throw new IllegalArgumentException("not an exact transfer step");
            };
            portion = new ActorContainerItemOrder.Portion.Exact(item);
        } else {
            SubjectId source = switch (action) {
                case PICKUP -> work.sourceAccountId();
                case LOAD, DELIVER -> work.actorAccountId();
                case UNLOAD -> work.stationAccountId();
                default -> throw new IllegalArgumentException("not a resource transfer step");
            };
            SubjectId destination = switch (action) {
                case PICKUP, UNLOAD -> work.actorAccountId();
                case LOAD -> work.stationAccountId();
                case DELIVER -> work.destinationAccountId();
                default -> throw new IllegalArgumentException("not a resource transfer step");
            };
            ResourceCustody from = state.inventory().fungibleResources().accounts().get(source).custody();
            ResourceCustody to = direction == ActorContainerItemOrder.Direction.TAKE
                    ? new ResourceCustody.Actor(job.workerId())
                    : new ResourceCustody.Container(action == BakeryColdStep.Action.LOAD ? station.containerId() : depotId);
            portion = new ActorContainerItemOrder.Portion.Fungible(source, from, destination, to,
                    output ? Optional.empty() : Optional.of(claimId(job)), output ? job.outputItemKind() : "minecraft:wheat",
                    output ? Map.of(job.outputItemId(), job.outputCount()) : job.inputQuantities());
            endpoint = switch (action) {
                case PICKUP, DELIVER -> new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(depotId);
                case LOAD -> new ActorContainerItemOrder.ContainerEndpoint.FungibleStation(station, ActorContainerItemOrder.StationPort.INPUT);
                case UNLOAD -> new ActorContainerItemOrder.ContainerEndpoint.FungibleStation(station, ActorContainerItemOrder.StationPort.OUTPUT);
                default -> throw new IllegalArgumentException("not a station transfer step");
            };
        }
        return new ActorContainerItemOrder(job.id(), job.workerId(), direction, portion, endpoint, surface,
                ActorContainerItemOrder.Hand.MAIN, work.phase().wireTag(), 1);
    }

    private static SubjectId claimId(ProductionJob job) {
        return switch (job.inputHold()) {
            case ProductionInputHold.FungibleCold cold -> cold.claimId();
            case ProductionInputHold.FungibleBound bound -> bound.claimId();
            default -> throw new IllegalArgumentException("exact bakery input has no fungible claim");
        };
    }

    private static ProductionStationSpec station(FrontierWorldState state, ProductionJob job) {
        SubjectId id = job.bakeryWork().orElseThrow().stationId();
        return state.inventory().containers().values().stream().flatMap(container -> container.productionStation().stream())
                .filter(candidate -> candidate.id().equals(id) && candidate.facilityId().equals(job.facilityId()))
                .reduce((left, right) -> { throw new IllegalArgumentException("bakery station identity is ambiguous"); })
                .orElseThrow(() -> new IllegalArgumentException("bakery job has no current machine"));
    }
}
