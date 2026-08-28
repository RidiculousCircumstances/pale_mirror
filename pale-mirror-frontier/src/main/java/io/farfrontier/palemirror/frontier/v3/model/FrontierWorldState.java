package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FixedRatio; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId; import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId; import java.util.Comparator;
import java.util.HashSet; import java.util.LinkedHashMap;
import java.util.List; import java.util.Map;
import java.util.Objects;
import java.util.Set;
    public record FrontierWorldState(
        FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actorLocations,
        Map<SubjectId, StructureCondition> structureConditions, Map<InfectionCell, FixedRatio> infection,
        ExactInventory inventory, Map<SubjectId, ProductionJob> productionJobs,
        Map<SubjectId, SupplyContract> contracts, Map<SubjectId, RouteOperation> operations,
        Map<PhysicalIntentId, PhysicalIntent> physicalIntents, Map<PhysicalObservationId, PhysicalEffectObservation> physicalObservations,
        Map<SceneLeaseId, SceneLease> sceneLeases, HiveColony hiveColony,
        Map<SubjectId, StructureDamage> structureDamage, Map<BlockPosition, PhysicalDelta> physicalDeltas,
        Map<SubjectId, AmbientActorLease> ambientLeases, Map<SubjectId, RouteConstruction> routeConstructions, RouteTopology routeTopology
) {
    private static final FixedRatio ZERO_INFECTION = new FixedRatio(io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO); private static final int MAX_OPERATIONS = 1_024, MAX_PHYSICAL_INTENTS = 4_096, MAX_PHYSICAL_OBSERVATIONS = 4_096;
    private static final int MAX_SCENE_LEASES = 1_024, MAX_AMBIENT_LEASES = 4_096, MAX_STRUCTURE_DAMAGE_CELLS = 65_536;
    public FrontierWorldState {
        Objects.requireNonNull(bootstrap, "bootstrap");
        actorLocations = FrontierWorldStateSupport.immutableMap(actorLocations, "actor locations"); structureConditions = FrontierWorldStateSupport.immutableMap(structureConditions, "structure conditions");
        infection = FrontierWorldStateSupport.immutableMap(infection, "infection");
        Objects.requireNonNull(inventory, "inventory");
        productionJobs = FrontierWorldStateSupport.immutableMap(productionJobs, "production jobs"); contracts = FrontierWorldStateSupport.immutableMap(contracts, "supply contracts");
        operations = FrontierWorldStateSupport.immutableMap(operations, "route operations"); physicalIntents = FrontierWorldStateSupport.immutableMap(physicalIntents, "physical intents");
        physicalObservations = FrontierWorldStateSupport.immutableMap(physicalObservations, "physical observations"); sceneLeases = FrontierWorldStateSupport.immutableMap(sceneLeases, "scene leases");
        structureDamage = FrontierWorldStateSupport.immutableMap(structureDamage, "structure damage"); physicalDeltas = FrontierWorldStateSupport.immutableMap(physicalDeltas, "physical deltas");
        ambientLeases = FrontierWorldStateSupport.immutableMap(ambientLeases, "ambient leases"); routeConstructions = FrontierWorldStateSupport.immutableMap(routeConstructions, "route constructions");
        Objects.requireNonNull(routeTopology, "route topology"); routeTopology.replacementSupplyRoutes().forEach((settlement, route) -> FrontierRouteNetwork.validateSupplyWaypoints(bootstrap, settlement, route));
        RouteConstructionStateSupport.validate(bootstrap, routeTopology, routeConstructions); Objects.requireNonNull(hiveColony, "hive colony"); hiveColony.validateAgainst(bootstrap);
        Set<SubjectId> expectedActors = FrontierWorldStateSupport.actorIds(bootstrap); expectedActors.addAll(hiveColony.spawnedBioforms().keySet());
        if (!expectedActors.equals(actorLocations.keySet())) throw new IllegalArgumentException("actor location index must own every and only bootstrap actor");
        for (ActorLocation location : actorLocations.values()) FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), location.position());
        if (ambientLeases.size() > MAX_AMBIENT_LEASES) throw new IllegalArgumentException("ambient lease retention limit exceeded"); Set<SubjectId> activelyAmbientLeased = new HashSet<>();
        for (Map.Entry<SubjectId, AmbientActorLease> entry : ambientLeases.entrySet()) {
            AmbientActorLease lease = entry.getValue();
            if (!entry.getKey().equals(lease.actorId()) || !expectedActors.contains(lease.actorId())) {
                throw new IllegalArgumentException("ambient lease must belong to one canonical actor");
            }
            if (actorLocations.get(lease.actorId()).condition().status() != ActorLifeStatus.ALIVE && lease.status() != AmbientLeaseStatus.CLOSED) {
                throw new IllegalArgumentException("dead actor cannot retain an active ambient lease");
            }
            FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), lease.handoffPosition()); FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), lease.goalPosition());
            if (lease.status() != AmbientLeaseStatus.CLOSED && !activelyAmbientLeased.add(lease.actorId())) {
                throw new IllegalArgumentException("actor cannot retain multiple active ambient leases");
            }
        }
        Set<SubjectId> expectedStructures = FrontierWorldStateSupport.structureIds(bootstrap);
        if (!expectedStructures.equals(structureConditions.keySet())) throw new IllegalArgumentException("structure condition index must own every and only bootstrap structure");
        int damageCellCount = 0;
        for (Map.Entry<SubjectId, StructureDamage> entry : structureDamage.entrySet()) {
            StructureDamage damage = entry.getValue();
            if (!expectedStructures.contains(entry.getKey()) || !entry.getKey().equals(damage.structureId())) {
                throw new IllegalArgumentException("structure damage must belong to one bootstrap structure");
            }
            SettlementStructure structure = FrontierWorldStateSupport.structureById(bootstrap, entry.getKey());
            for (Map.Entry<BlockPosition, StructureDamage.DamageCell> cell : damage.cells().entrySet()) {
                GrayboxCell expected = FrontierGrayboxPlan.intactStructureCell(structure, cell.getKey());
                if (expected == null || expected.semanticPart() != cell.getValue().semanticPart()) {
                    throw new IllegalArgumentException("structure damage must name one exact intact semantic cell");
                }
            }
            damageCellCount = Math.addExact(damageCellCount, damage.cells().size());
        }
        FrontierWorldPhysicalDeltaSupport.validate(bootstrap, hiveColony, physicalDeltas); if (damageCellCount > MAX_STRUCTURE_DAMAGE_CELLS) throw new IllegalArgumentException("structure damage retention limit exceeded");
        inventory.surfaces().values().forEach(surface -> FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), surface.position()));
        for (Map.Entry<InfectionCell, FixedRatio> entry : infection.entrySet()) {
            if (entry.getValue().equals(ZERO_INFECTION)) throw new IllegalArgumentException("sparse infection index must not retain zero cells");
            FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), entry.getKey().originAtY(0));
        }
        for (Map.Entry<SubjectId, ProductionJob> entry : productionJobs.entrySet()) {
            ProductionJob job = entry.getValue();
            if (!entry.getKey().equals(job.id())) throw new IllegalArgumentException("production job map key must match job identity");
            Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, job.settlementId());
            SettlementStructure facility = FrontierWorldStateSupport.structure(settlement, job.facilityId());
            if (facility.kind() != StructureKind.WORKSHOP) throw new IllegalArgumentException("production job facility must be a workshop");
            Resident worker = FrontierWorldStateSupport.resident(settlement, job.workerId());
            if (worker.role() != ResidentRole.CRAFTER) throw new IllegalArgumentException("production job worker must be a crafter");
            if (inventory.items().containsKey(job.consumedItemId()) || inventory.items().containsKey(job.outputItemId())) {
                throw new IllegalArgumentException("active production job must own neither consumed nor output item stack");
            }
        }
        for (Map.Entry<SubjectId, SupplyContract> entry : contracts.entrySet()) {
            SupplyContract contract = entry.getValue();
            if (!entry.getKey().equals(contract.id())) throw new IllegalArgumentException("contract map key must match contract identity");
            FrontierWorldStateSupport.settlement(bootstrap, contract.settlementId());
            if (!bootstrap.hive().id().equals(contract.recipientId())) throw new IllegalArgumentException("contract recipient must be the frontier hive");
            if (contract.status() == ContractStatus.LOADED && !inventory.cargo().containsKey(contract.cargoId())) throw new IllegalArgumentException("loaded contract must own its cargo");
            if (contract.status() != ContractStatus.LOADED && inventory.cargo().containsKey(contract.cargoId())) throw new IllegalArgumentException("only a loaded contract can own cargo");
        }
        if (operations.size() > MAX_OPERATIONS) throw new IllegalArgumentException("route operation retention limit exceeded");
        Set<SubjectId> leaseHistoryOperations = sceneLeases.values().stream().map(SceneLease::operationId).collect(java.util.stream.Collectors.toSet());
        Set<SubjectId> assignedCargo = new HashSet<>();
        Set<SubjectId> assignedParticipants = new HashSet<>();
        for (Map.Entry<SubjectId, RouteOperation> entry : operations.entrySet()) {
            RouteOperation operation = entry.getValue();
            if (!entry.getKey().equals(operation.id())) throw new IllegalArgumentException("route operation map key must match operation identity");
            Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, operation.settlementId());
            if (!bootstrap.hive().id().equals(operation.destinationId())) throw new IllegalArgumentException("route operation destination must be the frontier hive");
            CargoBatch cargo = inventory.cargo().get(operation.cargoId());
            SupplyContract contract = contracts.values().stream().filter(value -> value.cargoId().equals(operation.cargoId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("route operation cargo has no contract"));
            if (operation.stage() == OperationStage.ARRIVED && contract.status() == ContractStatus.DELIVERED) {
                if (cargo != null) throw new IllegalArgumentException("delivered operation cannot retain cargo");
            } else if (cargo == null || !cargo.ownerId().equals(operation.settlementId())) {
                throw new IllegalArgumentException("route operation must own settlement cargo");
            }
            if (!assignedCargo.add(operation.cargoId())) throw new IllegalArgumentException("cargo cannot be assigned to multiple route operations");
            Set<SubjectId> settlementResidents = new HashSet<>();
            settlement.residents().forEach(resident -> settlementResidents.add(resident.id()));
            for (SubjectId participant : operation.participantIds()) {
                if (!settlementResidents.contains(participant)) throw new IllegalArgumentException("route operation participant must belong to its settlement");
                if (!assignedParticipants.add(participant)) throw new IllegalArgumentException("resident cannot be assigned to multiple route operations");
                if (operation.stage() != OperationStage.FAILED && actorLocations.get(participant).condition().status() == ActorLifeStatus.ALIVE
                        && !leaseHistoryOperations.contains(operation.id())
                        && !actorLocations.get(participant).position().equals(operation.route().get(operation.routeIndex()))) {
                    throw new IllegalArgumentException("active route operation participant must be at its canonical route point");
                }
            }
            operation.route().forEach(position -> FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), position));
        }
        if (physicalIntents.size() > MAX_PHYSICAL_INTENTS) throw new IllegalArgumentException("physical intent retention limit exceeded");
        for (Map.Entry<PhysicalIntentId, PhysicalIntent> entry : physicalIntents.entrySet()) {
            PhysicalIntent intent = entry.getValue();
            if (!entry.getKey().equals(intent.id())) throw new IllegalArgumentException("physical intent map key must match intent identity");
            if (!expectedActors.contains(intent.causeSubjectId()) && !inventory.cargo().containsKey(intent.causeSubjectId()) && !operations.containsKey(intent.causeSubjectId())
                    && !expectedStructures.contains(intent.causeSubjectId()) && !FrontierWorldStateSupport.isHiveOrgan(bootstrap, hiveColony, intent.causeSubjectId()) && !FrontierRouteNetwork.OWNER.equals(intent.causeSubjectId())) {
                throw new IllegalArgumentException("physical intent cause must be a canonical subject");
            }
            for (SubjectId subject : intent.subjectIds()) {
                if (intent.status() == PhysicalIntentStatus.CONFIRMED || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) continue;
                if (!expectedActors.contains(subject) && !inventory.cargo().containsKey(subject) && !operations.containsKey(subject)
                        && !expectedStructures.contains(subject) && !inventory.items().containsKey(subject)
                        && !FrontierWorldStateSupport.isHiveOrgan(bootstrap, hiveColony, subject)
                        && !FrontierRouteNetwork.OWNER.equals(subject) && !routeConstructions.containsKey(subject)
                        && contracts.values().stream().noneMatch(contract -> contract.cargoId().equals(subject))) {
                    throw new IllegalArgumentException("physical intent references an unknown canonical subject");
                }
            }
        }
        if (physicalObservations.size() > MAX_PHYSICAL_OBSERVATIONS) throw new IllegalArgumentException("physical observation retention limit exceeded");
        for (Map.Entry<PhysicalObservationId, PhysicalEffectObservation> entry : physicalObservations.entrySet()) {
            PhysicalEffectObservation observation = entry.getValue();
            if (!entry.getKey().equals(observation.id())) throw new IllegalArgumentException("physical observation map key must match observation identity");
            PhysicalIntent intent = physicalIntents.get(observation.intentId());
            if (intent == null || intent.status() != PhysicalIntentStatus.CONFIRMED
                    || !intent.postconditionObservationId().equals(java.util.Optional.of(observation.id()))) {
                throw new IllegalArgumentException("physical observation must be the confirmed intent receipt");
            }
            if (observation instanceof CargoHandoffObservation cargo) {
                FrontierCargoValidation.validateObservation(bootstrap, operations, contracts, inventory, intent, cargo);
            } else if (observation instanceof DecontaminationObservation decontamination) {
                DecontaminationStateSupport.validateReceipt(bootstrap, infection, intent, decontamination);
            } else if (observation instanceof StructuralRepairObservation repair) {
                StructuralRepairStateSupport.validateReceipt(intent, repair);
            } else if (observation instanceof RouteConstructionObservation construction) {
                RouteConstructionStateSupport.validateReceipt(bootstrap, routeTopology, routeConstructions, intent, construction);
            } else throw new IllegalArgumentException("physical observation has an unknown effect kind");
        }
        for (PhysicalIntent intent : physicalIntents.values()) {
            if (intent.status() == PhysicalIntentStatus.CONFIRMED
                    && !physicalObservations.containsKey(intent.postconditionObservationId().orElseThrow())) {
                throw new IllegalArgumentException("confirmed physical intent must retain its exact observation");
            }
        }
        if (sceneLeases.size() > MAX_SCENE_LEASES) throw new IllegalArgumentException("scene lease retention limit exceeded");
        Set<SubjectId> leasedActors = new HashSet<>();
        Set<SubjectId> leasedOperations = new HashSet<>();
        for (Map.Entry<SceneLeaseId, SceneLease> entry : sceneLeases.entrySet()) {
            SceneLease lease = entry.getValue();
            if (!entry.getKey().equals(lease.id())) throw new IllegalArgumentException("scene lease map key must match lease identity");
            RouteOperation operation = operations.get(lease.operationId());
            if (operation == null || !operation.cargoId().equals(lease.cargoId())) {
                throw new IllegalArgumentException("scene lease must bind its current en-route operation state");
            }
            if (lease.status() != SceneLeaseStatus.CLOSED && (operation.stage() != OperationStage.EN_ROUTE
                    || !operation.route().get(operation.routeIndex()).equals(lease.handoffPosition()))) {
                throw new IllegalArgumentException("active scene lease must bind its current en-route operation state");
            }
            if (lease.status() != SceneLeaseStatus.CLOSED && !leasedOperations.add(lease.operationId())) {
                throw new IllegalArgumentException("operation cannot have multiple active scene leases");
            }
            Set<SubjectId> members = lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet());
            if (!members.equals(Set.copyOf(operation.participantIds()))) throw new IllegalArgumentException("scene lease members must exactly match route participants");
            for (SubjectId actor : members) {
                if (lease.status() != SceneLeaseStatus.CLOSED && !leasedActors.add(actor)) throw new IllegalArgumentException("actor cannot belong to multiple active scene leases");
                if (lease.status() != SceneLeaseStatus.CLOSED && activelyAmbientLeased.contains(actor)) throw new IllegalArgumentException("actor cannot have both scene and ambient execution leases");
                if (lease.status() == SceneLeaseStatus.PREPARED && !actorLocations.get(actor).position().equals(lease.handoffPosition())) {
                    throw new IllegalArgumentException("prepared scene lease actor must be at its handoff position");
                }
            }
        }
    }
    public static FrontierWorldState initial(FrontierBootstrap bootstrap) {
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>();
        bootstrap.settlements().forEach(settlement -> settlement.residents().forEach(resident -> actors.put(resident.id(), new ActorLocation(resident.home()))));
        bootstrap.hive().bioforms().forEach(bioform -> actors.put(bioform.id(), new ActorLocation(bioform.position())));
        Map<SubjectId, StructureCondition> structures = new LinkedHashMap<>();
        bootstrap.settlements().forEach(settlement -> settlement.structures().forEach(structure -> structures.put(structure.id(), StructureCondition.INTACT)));
        Map<SubjectId, ContainerRecord> containers = new LinkedHashMap<>();
        bootstrap.settlements().forEach(settlement -> containers.put(new SubjectId("container:" + settlement.id().value().substring("settlement:".length()) + "-depot"),
                new ContainerRecord(new SubjectId("container:" + settlement.id().value().substring("settlement:".length()) + "-depot"), settlement.id(), 27)));
        bootstrap.hive().organs().forEach(organ -> organ.containerId().ifPresent(container ->
                containers.put(container, new ContainerRecord(container, bootstrap.hive().id(), 27))));
        containers.put(FrontierRouteNetwork.MAINTENANCE_CONTAINER, new ContainerRecord(FrontierRouteNetwork.MAINTENANCE_CONTAINER, FrontierRouteNetwork.OWNER, 27));
        Map<SubjectId, ExactItemStack> items = new LinkedHashMap<>(); SubjectId firstDepot = depotId(bootstrap.settlements().getFirst().id());
        SubjectId firstInput = new SubjectId("item:bootstrap-1-wheat"); items.put(firstInput, new ExactItemStack(firstInput, "minecraft:wheat", 64, new InventoryCustody.ContainerSlot(firstDepot, 0)));
        SubjectId hiveBiomass = new SubjectId("item:bootstrap-hive-biomass"); items.put(hiveBiomass, new ExactItemStack(hiveBiomass, "minecraft:rotten_flesh", 64,
                new InventoryCustody.ContainerSlot(new SubjectId("container:hive-east-store"), 0)));
        Map<InfectionCell, FixedRatio> infection = new LinkedHashMap<>();
        bootstrap.hive().seedNests().forEach(nest -> infection.put(InfectionCell.at(nest.anchor()), new FixedRatio(new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(500_000L))));
        ExactInventory inventory = new ExactInventory(containers, items, Map.of(), Map.of(), Map.of(), Map.of(), ContainerSurfaceManifest.initial(bootstrap));
        return new FrontierWorldState(bootstrap, actors, structures, infection, inventory,
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), HiveColony.empty(), Map.of(), Map.of(), Map.of(), Map.of(), RouteTopology.initial());
    }
    private FrontierWorldState next(Map<SubjectId, ActorLocation> actors, Map<SubjectId, StructureCondition> structures, Map<InfectionCell, FixedRatio> nextInfection, ExactInventory nextInventory,
                                    Map<SubjectId, ProductionJob> jobs, Map<SubjectId, SupplyContract> nextContracts, Map<SubjectId, RouteOperation> nextOperations, Map<PhysicalIntentId, PhysicalIntent> intents,
                                    Map<PhysicalObservationId, PhysicalEffectObservation> observations, Map<SceneLeaseId, SceneLease> leases, HiveColony colony, Map<SubjectId, StructureDamage> damage,
                                    Map<BlockPosition, PhysicalDelta> deltas, Map<SubjectId, AmbientActorLease> ambient) {
        return new FrontierWorldState(bootstrap, actors, structures, nextInfection, nextInventory, jobs, nextContracts, nextOperations, intents, observations, leases, colony, damage, deltas, ambient, routeConstructions, routeTopology);
    } public FrontierWorldState withRouteTopology(RouteTopology nextTopology) {
        return new FrontierWorldState(bootstrap, actorLocations, structureConditions, infection, inventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases, routeConstructions, nextTopology);
    }
    public FrontierWorldState withActorLocation(SubjectId actor, BlockPosition position) {
        Objects.requireNonNull(actor, "actor");
        FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), position);
        if (!actorLocations.containsKey(actor)) throw new IllegalArgumentException("unknown actor: " + actor.value());
        Map<SubjectId, ActorLocation> next = new LinkedHashMap<>(actorLocations);
        next.put(actor, actorLocations.get(actor).withPosition(position));
        return next(next, structureConditions, infection, inventory, productionJobs, contracts, operations, physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState withStructureCondition(SubjectId structure, StructureCondition condition) {
        Objects.requireNonNull(structure, "structure"); Objects.requireNonNull(condition, "structure condition");
        if (!structureConditions.containsKey(structure)) throw new IllegalArgumentException("unknown structure: " + structure.value());
        Map<SubjectId, StructureCondition> next = new LinkedHashMap<>(structureConditions); next.put(structure, condition);
        return next(actorLocations, next, infection, inventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState recordStructureDamage(StructureDamaged damage) { return FrontierWorldPhysicalDeltaSupport.recordStructureDamage(this, damage); }
    /** Records real post-effect evidence; the support owns bounds, exact-semantic validation and consequences. */
    public FrontierWorldState recordPhysicalDelta(PhysicalDelta delta) { return FrontierWorldPhysicalDeltaSupport.record(this, delta); }
    /** A disabled organ cannot authorize its store or future growth work until a repair process exists. */
    public boolean isHiveOrganOperational(SubjectId organId) { return FrontierWorldPhysicalDeltaSupport.organOperational(bootstrap, hiveColony, physicalDeltas, organId); }
    public FrontierWorldState withInfection(InfectionCell cell, FixedRatio intensity) {
        Objects.requireNonNull(cell, "infection cell"); Objects.requireNonNull(intensity, "infection intensity");
        FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), cell.originAtY(0));
        Map<InfectionCell, FixedRatio> next = new LinkedHashMap<>(infection);
        if (intensity.equals(ZERO_INFECTION)) next.remove(cell); else next.put(cell, intensity);
        return next(actorLocations, structureConditions, next, inventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState withInventory(ExactInventory nextInventory) {
        return next(actorLocations, structureConditions, infection, nextInventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState withProductionJob(ProductionJob job) {
        Objects.requireNonNull(job, "production job");
        if (productionJobs.containsKey(job.id())) throw new IllegalArgumentException("production job identity already exists: " + job.id().value());
        if (productionJobs.values().stream().anyMatch(existing -> existing.facilityId().equals(job.facilityId()))) {
            throw new IllegalArgumentException("facility already has an active production job: " + job.facilityId().value());
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(productionJobs); next.put(job.id(), job);
        return next(actorLocations, structureConditions, infection, inventory, next, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState startProductionJob(ProductionJob job, SubjectId inputItemId) {
        Objects.requireNonNull(job, "production job");
        Objects.requireNonNull(inputItemId, "production input item id");
        if (!job.consumedItemId().equals(inputItemId)) throw new IllegalArgumentException("production job input identity differs");
        if (productionJobs.containsKey(job.id())) throw new IllegalArgumentException("production job identity already exists: " + job.id().value());
        if (productionJobs.values().stream().anyMatch(existing -> existing.facilityId().equals(job.facilityId()))) {
            throw new IllegalArgumentException("facility already has an active production job: " + job.facilityId().value());
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(productionJobs); next.put(job.id(), job);
        return next(actorLocations, structureConditions, infection, inventory.withoutItem(inputItemId), next,
                contracts, operations, physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState completeProductionJob(SubjectId jobId, ExactItemStack output) {
        ProductionJob job = productionJobs.get(Objects.requireNonNull(jobId, "production job id"));
        if (job == null) throw new IllegalArgumentException("unknown production job: " + jobId.value());
        if (!job.outputItemId().equals(output.id()) || !job.outputItemKind().equals(output.itemKind()) || job.outputCount() != output.count()) {
            throw new IllegalArgumentException("production output does not match durable job result");
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(productionJobs); next.remove(jobId);
        return next(actorLocations, structureConditions, infection, inventory.store(output), next, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState createSupplyContract(SupplyContract contract) {
        if (contracts.containsKey(contract.id())) throw new IllegalArgumentException("supply contract identity already exists");
        Map<SubjectId, SupplyContract> next = new LinkedHashMap<>(contracts); next.put(contract.id(), contract);
        return next(actorLocations, structureConditions, infection, inventory, productionJobs, next, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState loadContractCargo(SubjectId contractId, CargoBatch cargo) {
        SupplyContract contract = contracts.get(contractId);
        if (contract == null || contract.status() != ContractStatus.ORDERED || !contract.cargoId().equals(cargo.id())) throw new IllegalArgumentException("cargo load does not match an ordered contract");
        Map<SubjectId, SupplyContract> next = new LinkedHashMap<>(contracts);
        next.put(contractId, new SupplyContract(contract.id(), contract.settlementId(), contract.recipientId(), contract.cargoId(), contract.itemKind(), contract.itemCount(), ContractStatus.LOADED));
        return next(actorLocations, structureConditions, infection, inventory.loadCargo(cargo), productionJobs,
                next, operations, physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState createOperation(RouteOperation operation) {
        Objects.requireNonNull(operation, "route operation");
        if (operations.containsKey(operation.id())) throw new IllegalArgumentException("route operation identity already exists: " + operation.id().value());
        Map<SubjectId, RouteOperation> next = new LinkedHashMap<>(operations); next.put(operation.id(), operation);
        Map<SubjectId, ActorLocation> nextActors = new LinkedHashMap<>(actorLocations);
        BlockPosition position = operation.route().get(operation.routeIndex());
        operation.participantIds().forEach(participant -> nextActors.put(participant, actorLocations.get(participant).withPosition(position)));
        return next(nextActors, structureConditions, infection, inventory, productionJobs, contracts, next,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState advanceOperation(SubjectId operationId, int nextRouteIndex, OperationStage nextStage) {
        RouteOperation operation = operations.get(Objects.requireNonNull(operationId, "route operation id"));
        if (operation == null) throw new IllegalArgumentException("unknown route operation: " + operationId.value());
        if (operation.stage() != OperationStage.EN_ROUTE || nextRouteIndex != operation.routeIndex() + 1) throw new IllegalArgumentException("route operation advancement is not sequential");
        OperationStage expectedStage = nextRouteIndex == operation.route().size() - 1 ? OperationStage.ARRIVED : OperationStage.EN_ROUTE;
        if (nextStage != expectedStage) throw new IllegalArgumentException("route operation stage does not match route cursor");
        RouteOperation advanced = new RouteOperation(operation.id(), operation.settlementId(), operation.cargoId(), operation.destinationId(),
                operation.participantIds(), operation.route(), nextRouteIndex, nextStage);
        Map<SubjectId, RouteOperation> nextOperations = new LinkedHashMap<>(operations); nextOperations.put(operation.id(), advanced);
        Map<SubjectId, ActorLocation> nextActors = new LinkedHashMap<>(actorLocations);
        BlockPosition position = advanced.route().get(nextRouteIndex);
        advanced.participantIds().forEach(participant -> nextActors.put(participant, actorLocations.get(participant).withPosition(position)));
        return next(nextActors, structureConditions, infection, inventory, productionJobs, contracts, nextOperations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState preparePhysicalIntent(PhysicalIntent intent) {
        Objects.requireNonNull(intent, "physical intent");
        if (physicalIntents.containsKey(intent.id())) throw new IllegalArgumentException("physical intent identity already exists: " + intent.id().value());
        Map<PhysicalIntentId, PhysicalIntent> next = new LinkedHashMap<>(physicalIntents);
        next.put(intent.id(), intent);
        return next(actorLocations, structureConditions, infection, inventory, productionJobs, contracts, operations,
                next, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState transitionPhysicalIntent(PhysicalIntentId intentId, PhysicalIntentStatus nextStatus, java.util.Optional<PhysicalEffectObservation> observation) {
        PhysicalIntent current = physicalIntents.get(Objects.requireNonNull(intentId, "physical intent id"));
        if (current == null) throw new IllegalArgumentException("unknown physical intent: " + intentId.value());
        boolean allowed = current.status() == PhysicalIntentStatus.PREPARED && (nextStatus == PhysicalIntentStatus.RUNNING || nextStatus == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
                || current.status() == PhysicalIntentStatus.RUNNING && (nextStatus == PhysicalIntentStatus.CONFIRMED || nextStatus == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART);
        if (!allowed) throw new IllegalArgumentException("physical intent transition is not allowed");
        Map<PhysicalIntentId, PhysicalIntent> next = new LinkedHashMap<>(physicalIntents);
        if (nextStatus != PhysicalIntentStatus.CONFIRMED) {
            next.put(intentId, current.withStatus(nextStatus, java.util.Optional.empty()));
            if (current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_CONSTRUCTION
                    && nextStatus == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) return RouteConstructionStateSupport.conflict(this, current, next);
            return next(actorLocations, structureConditions, infection, inventory, productionJobs, contracts, operations,
                    next, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
        }
        PhysicalEffectObservation evidence = observation.orElseThrow(() -> new IllegalArgumentException("confirmed physical intent requires observation evidence"));
        if (!current.id().equals(evidence.intentId()) || physicalObservations.containsKey(evidence.id())) {
            throw new IllegalArgumentException("cargo hand-off observation does not match a unique confirmed intent");
        }
        if (current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.DECONTAMINATION) {
            if (!(evidence instanceof DecontaminationObservation decontamination)) throw new IllegalArgumentException("decontamination requires observation evidence");
            return DecontaminationStateSupport.complete(this, current, decontamination, next);
        }
        if (current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.STRUCTURAL_REPAIR) {
            if (!(evidence instanceof StructuralRepairObservation repair)) throw new IllegalArgumentException("structural repair requires repair observation evidence");
            return StructuralRepairStateSupport.complete(this, current, repair, next);
        }
        if (current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_CONSTRUCTION) {
            if (!(evidence instanceof RouteConstructionObservation construction)) throw new IllegalArgumentException("route construction requires construction observation evidence");
            return RouteConstructionStateSupport.complete(this, current, construction, next);
        }
        if (current.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.CARGO_HANDOFF || !(evidence instanceof CargoHandoffObservation cargo)) {
            throw new IllegalArgumentException("physical intent kind has no matching confirmation evidence");
        }
        CargoHandoffObservation cargoEvidence = cargo;
        RouteOperation operation = operations.get(current.causeSubjectId());
        if (operation == null || !operation.cargoId().equals(cargoEvidence.cargoId())) throw new IllegalArgumentException("cargo hand-off observation does not match its route operation");
        SubjectId receiver = FrontierCargoValidation.receiverStore(bootstrap, operation);
        if (cargoEvidence.placements().stream().anyMatch(placement -> !receiver.equals(placement.receiverSlot().containerId()))) {
            throw new IllegalArgumentException("cargo hand-off observation targets a foreign hive receiver");
        }
        SupplyContract contract = contracts.values().stream().filter(value -> value.cargoId().equals(cargoEvidence.cargoId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("cargo hand-off has no supply contract"));
        if (contract.status() != ContractStatus.LOADED) throw new IllegalArgumentException("only loaded cargo can complete hand-off");
        Map<SubjectId, SupplyContract> nextContracts = new LinkedHashMap<>(contracts); nextContracts.put(contract.id(), contract.withStatus(ContractStatus.DELIVERED));
        next.put(intentId, current.withStatus(nextStatus, java.util.Optional.of(cargoEvidence.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> nextObservations = new LinkedHashMap<>(physicalObservations); nextObservations.put(cargoEvidence.id(), cargoEvidence);
        return next(actorLocations, structureConditions, infection, inventory.completeCargoHandoff(cargoEvidence.cargoId(), cargoEvidence.placements()),
                productionJobs, nextContracts, operations, next, nextObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState prepareSceneLease(SceneLease lease) {
        Objects.requireNonNull(lease, "scene lease");
        if (sceneLeases.containsKey(lease.id())) throw new IllegalArgumentException("scene lease identity already exists: " + lease.id().value());
        if (lease.status() != SceneLeaseStatus.PREPARED) throw new IllegalArgumentException("new scene lease must be prepared");
        if (lease.members().stream().anyMatch(member -> actorLocations.get(member.actorId()).condition().status() != ActorLifeStatus.ALIVE)) {
            throw new IllegalArgumentException("scene lease cannot materialize a dead actor");
        }
        Map<SceneLeaseId, SceneLease> next = new LinkedHashMap<>(sceneLeases);
        // Closed leases have no further authority: history remains causal while checkpoints retain a bounded terminal index.
        int requiredCompaction = next.size() - MAX_SCENE_LEASES + 1;
        if (requiredCompaction > 0) {
            List<SceneLease> terminal = next.values().stream()
                    .filter(existing -> existing.status() == SceneLeaseStatus.CLOSED)
                    .sorted(Comparator.comparing(SceneLease::handoffInstant).thenComparing(existing -> existing.id().value()))
                    .toList();
            if (terminal.size() < requiredCompaction) {
                throw new IllegalArgumentException("scene lease retention limit has no terminal leases to compact");
            }
            terminal.stream().limit(requiredCompaction).forEach(existing -> next.remove(existing.id()));
        }
        next.put(lease.id(), lease);
        return next(actorLocations, structureConditions, infection, inventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, next, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState transitionSceneLease(SceneLeaseId leaseId, SceneLeaseStatus nextStatus) {
        SceneLease current = sceneLeases.get(Objects.requireNonNull(leaseId, "scene lease id"));
        if (current == null) throw new IllegalArgumentException("unknown scene lease: " + leaseId.value());
        boolean allowed = current.status().canTransitionTo(nextStatus);
        if (!allowed) throw new IllegalArgumentException("scene lease transition is not allowed");
        Map<SceneLeaseId, SceneLease> next = new LinkedHashMap<>(sceneLeases); next.put(leaseId, current.withStatus(nextStatus));
        return next(actorLocations, structureConditions, infection, inventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, next, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState releaseSceneLease(SceneLeaseId leaseId, java.util.List<SceneMemberPosition> positions) {
        SceneLease current = sceneLeases.get(Objects.requireNonNull(leaseId, "scene lease id"));
        if (current == null || current.status() != SceneLeaseStatus.DRAINING) throw new IllegalArgumentException("only a draining scene lease can be released");
        Set<SubjectId> expected = current.members().stream().map(SceneMember::actorId)
                .filter(actor -> actorLocations.get(actor).condition().status() == ActorLifeStatus.ALIVE).collect(java.util.stream.Collectors.toSet());
        Set<SubjectId> observed = positions.stream().map(SceneMemberPosition::actorId).collect(java.util.stream.Collectors.toSet());
        if (!expected.equals(observed) || observed.size() != positions.size()) throw new IllegalArgumentException("scene release must capture exactly its leased actors");
        Map<SubjectId, ActorLocation> nextActors = new LinkedHashMap<>(actorLocations);
        for (SceneMemberPosition position : positions) {
            FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), position.position());
            ActorLocation currentActor = actorLocations.get(position.actorId());
            nextActors.put(position.actorId(), new ActorLocation(position.position(), currentActor.condition().withHealth(position.health())));
        }
        Map<SceneLeaseId, SceneLease> next = new LinkedHashMap<>(sceneLeases); next.put(leaseId, current.withStatus(SceneLeaseStatus.CLOSED));
        return next(nextActors, structureConditions, infection, inventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, next, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState recordActorDeath(ActorDied death) {
        Objects.requireNonNull(death, "actor death");
        SceneLease lease = sceneLeases.get(death.leaseId());
        if (lease == null || lease.status() != SceneLeaseStatus.HOT
                || lease.members().stream().noneMatch(member -> member.actorId().equals(death.actorId()))) {
            throw new IllegalArgumentException("actor death is not evidence for an active HOT scene member");
        }
        ActorLocation current = actorLocations.get(death.actorId());
        if (current.condition().status() != ActorLifeStatus.ALIVE) throw new IllegalArgumentException("actor death is already recorded");
        FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), death.position());
        Map<SubjectId, ActorLocation> nextActors = new LinkedHashMap<>(actorLocations);
        nextActors.put(death.actorId(), current.deadAt(death.position()));
        return next(nextActors, structureConditions, infection, inventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState failOperation(SubjectId operationId) {
        RouteOperation current = operations.get(Objects.requireNonNull(operationId, "operation id"));
        if (current == null || current.stage() != OperationStage.EN_ROUTE) throw new IllegalArgumentException("only an en-route operation can fail");
        if (sceneLeases.values().stream().anyMatch(lease -> lease.operationId().equals(operationId) && lease.status() != SceneLeaseStatus.CLOSED)) {
            throw new IllegalArgumentException("operation cannot fail before its active scene lease closes");
        }
        Map<SubjectId, RouteOperation> next = new LinkedHashMap<>(operations);
        next.put(operationId, new RouteOperation(current.id(), current.settlementId(), current.cargoId(), current.destinationId(),
                current.participantIds(), current.route(), current.routeIndex(), OperationStage.FAILED));
        return next(actorLocations, structureConditions, infection, inventory, productionJobs, contracts, next,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState addHiveOrgan(HiveOrgan organ) {
        Objects.requireNonNull(organ, "hive organ");
        if (bootstrap.hive().organs().stream().anyMatch(existing -> existing.id().equals(organ.id()))) throw new IllegalArgumentException("added organ collides with bootstrap identity");
        if (organ.containerId().isPresent() && !inventory.containers().containsKey(organ.containerId().orElseThrow())) throw new IllegalArgumentException("added store organ needs an exact canonical container");
        return next(actorLocations, structureConditions, infection, inventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony.addOrgan(organ), structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState spawnBioform(Bioform bioform) {
        Objects.requireNonNull(bioform, "bioform"); if (actorLocations.containsKey(bioform.id())) throw new IllegalArgumentException("spawned bioform collides with actor identity");
        Map<SubjectId, ActorLocation> nextActors = new LinkedHashMap<>(actorLocations); nextActors.put(bioform.id(), new ActorLocation(bioform.position()));
        return next(nextActors, structureConditions, infection, inventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony.spawn(bioform), structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState startHiveGrowth(HiveGrowthJob job) { Objects.requireNonNull(job, "hive growth job"); ExactItemStack input = inventory.items().get(job.consumedItemId());
        if (input == null || !(input.custody() instanceof InventoryCustody.ContainerSlot slot) || !isHiveStore(slot.containerId())) throw new IllegalArgumentException("hive growth needs one exact hive-store input");
        return next(actorLocations, structureConditions, infection, inventory.withoutItem(input.id()), productionJobs,
                contracts, operations, physicalIntents, physicalObservations, sceneLeases, hiveColony.startGrowth(job), structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState completeHiveGrowth(SubjectId jobId) {
        HiveGrowthJob job = hiveColony.growthJobs().get(Objects.requireNonNull(jobId, "hive growth job id"));
        if (job == null || actorLocations.containsKey(job.bioform().id())) throw new IllegalArgumentException("hive growth completion is invalid");
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(actorLocations); actors.put(job.bioform().id(), new ActorLocation(job.bioform().position()));
        return next(actors, structureConditions, infection, inventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony.completeGrowth(jobId), structureDamage, physicalDeltas, ambientLeases);
    }
    boolean isHiveStore(SubjectId containerId) {
        return java.util.stream.Stream.concat(bootstrap.hive().organs().stream(), hiveColony.addedOrgans().values().stream())
                .anyMatch(organ -> organ.kind() == HiveOrganKind.STORE && organ.containerId().equals(java.util.Optional.of(containerId))
                        && isHiveOrganOperational(organ.id()));
    }
    public static SubjectId depotId(SubjectId settlementId) {
        Objects.requireNonNull(settlementId, "settlement id");
        if (!settlementId.value().startsWith("settlement:")) throw new IllegalArgumentException("settlement id must use settlement: namespace");
        return new SubjectId("container:" + settlementId.value().substring("settlement:".length()) + "-depot");
    }
}
