package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable canonical mutable state for the fresh v3 profile. Bootstrap defines identity and
 * ownership; this state owns every mutable actor location, structure condition and infection cell.
 */
public record FrontierWorldState(
        FrontierBootstrap bootstrap,
        Map<SubjectId, ActorLocation> actorLocations,
        Map<SubjectId, StructureCondition> structureConditions,
        Map<InfectionCell, FixedRatio> infection,
        ExactInventory inventory,
        Map<SubjectId, ProductionJob> productionJobs,
        Map<SubjectId, SupplyContract> contracts
) {
    private static final FixedRatio ZERO_INFECTION = new FixedRatio(io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO);

    public FrontierWorldState {
        Objects.requireNonNull(bootstrap, "bootstrap");
        actorLocations = immutableMap(actorLocations, "actor locations");
        structureConditions = immutableMap(structureConditions, "structure conditions");
        infection = immutableMap(infection, "infection");
        Objects.requireNonNull(inventory, "inventory");
        productionJobs = immutableMap(productionJobs, "production jobs");
        contracts = immutableMap(contracts, "supply contracts");
        Set<SubjectId> expectedActors = actorIds(bootstrap);
        if (!expectedActors.equals(actorLocations.keySet())) throw new IllegalArgumentException("actor location index must own every and only bootstrap actor");
        for (ActorLocation location : actorLocations.values()) requirePosition(bootstrap.bounds(), location.position());
        Set<SubjectId> expectedStructures = structureIds(bootstrap);
        if (!expectedStructures.equals(structureConditions.keySet())) throw new IllegalArgumentException("structure condition index must own every and only bootstrap structure");
        for (Map.Entry<InfectionCell, FixedRatio> entry : infection.entrySet()) {
            if (entry.getValue().equals(ZERO_INFECTION)) throw new IllegalArgumentException("sparse infection index must not retain zero cells");
            requirePosition(bootstrap.bounds(), entry.getKey().originAtY(0));
        }
        for (Map.Entry<SubjectId, ProductionJob> entry : productionJobs.entrySet()) {
            ProductionJob job = entry.getValue();
            if (!entry.getKey().equals(job.id())) throw new IllegalArgumentException("production job map key must match job identity");
            Settlement settlement = settlement(bootstrap, job.settlementId());
            SettlementStructure facility = structure(settlement, job.facilityId());
            if (facility.kind() != StructureKind.WORKSHOP) throw new IllegalArgumentException("production job facility must be a workshop");
            Resident worker = resident(settlement, job.workerId());
            if (worker.role() != ResidentRole.CRAFTER) throw new IllegalArgumentException("production job worker must be a crafter");
            if (inventory.items().containsKey(job.consumedItemId()) || inventory.items().containsKey(job.outputItemId())) {
                throw new IllegalArgumentException("active production job must own neither consumed nor output item stack");
            }
        }
        for (Map.Entry<SubjectId, SupplyContract> entry : contracts.entrySet()) {
            SupplyContract contract = entry.getValue();
            if (!entry.getKey().equals(contract.id())) throw new IllegalArgumentException("contract map key must match contract identity");
            settlement(bootstrap, contract.settlementId());
            if (!bootstrap.hive().id().equals(contract.recipientId())) throw new IllegalArgumentException("contract recipient must be the frontier hive");
            if (contract.status() == ContractStatus.LOADED && !inventory.cargo().containsKey(contract.cargoId())) throw new IllegalArgumentException("loaded contract must own its cargo");
            if (contract.status() == ContractStatus.ORDERED && inventory.cargo().containsKey(contract.cargoId())) throw new IllegalArgumentException("ordered contract cannot already own cargo");
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
        Map<SubjectId, ExactItemStack> items = new LinkedHashMap<>();
        SubjectId firstDepot = depotId(bootstrap.settlements().getFirst().id());
        SubjectId firstInput = new SubjectId("item:bootstrap-1-wheat");
        items.put(firstInput, new ExactItemStack(firstInput, "minecraft:wheat", 64, new InventoryCustody.ContainerSlot(firstDepot, 0)));
        Map<InfectionCell, FixedRatio> infection = new LinkedHashMap<>();
        bootstrap.hive().seedNests().forEach(nest -> infection.put(InfectionCell.at(nest.anchor()), new FixedRatio(new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(500_000L))));
        return new FrontierWorldState(bootstrap, actors, structures, infection, new ExactInventory(containers, items, Map.of(), Map.of()), Map.of(), Map.of());
    }

    public FrontierWorldState withActorLocation(SubjectId actor, BlockPosition position) {
        Objects.requireNonNull(actor, "actor");
        requirePosition(bootstrap.bounds(), position);
        if (!actorLocations.containsKey(actor)) throw new IllegalArgumentException("unknown actor: " + actor.value());
        Map<SubjectId, ActorLocation> next = new LinkedHashMap<>(actorLocations);
        next.put(actor, new ActorLocation(position));
        return new FrontierWorldState(bootstrap, next, structureConditions, infection, inventory, productionJobs, contracts);
    }

    public FrontierWorldState withStructureCondition(SubjectId structure, StructureCondition condition) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(condition, "structure condition");
        if (!structureConditions.containsKey(structure)) throw new IllegalArgumentException("unknown structure: " + structure.value());
        Map<SubjectId, StructureCondition> next = new LinkedHashMap<>(structureConditions);
        next.put(structure, condition);
        return new FrontierWorldState(bootstrap, actorLocations, next, infection, inventory, productionJobs, contracts);
    }

    public FrontierWorldState withInfection(InfectionCell cell, FixedRatio intensity) {
        Objects.requireNonNull(cell, "infection cell");
        Objects.requireNonNull(intensity, "infection intensity");
        requirePosition(bootstrap.bounds(), cell.originAtY(0));
        Map<InfectionCell, FixedRatio> next = new LinkedHashMap<>(infection);
        if (intensity.equals(ZERO_INFECTION)) next.remove(cell); else next.put(cell, intensity);
        return new FrontierWorldState(bootstrap, actorLocations, structureConditions, next, inventory, productionJobs, contracts);
    }

    public FrontierWorldState withInventory(ExactInventory nextInventory) {
        return new FrontierWorldState(bootstrap, actorLocations, structureConditions, infection, nextInventory, productionJobs, contracts);
    }

    public FrontierWorldState withProductionJob(ProductionJob job) {
        Objects.requireNonNull(job, "production job");
        if (productionJobs.containsKey(job.id())) throw new IllegalArgumentException("production job identity already exists: " + job.id().value());
        if (productionJobs.values().stream().anyMatch(existing -> existing.facilityId().equals(job.facilityId()))) {
            throw new IllegalArgumentException("facility already has an active production job: " + job.facilityId().value());
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(productionJobs);
        next.put(job.id(), job);
        return new FrontierWorldState(bootstrap, actorLocations, structureConditions, infection, inventory, next, contracts);
    }

    public FrontierWorldState startProductionJob(ProductionJob job, SubjectId inputItemId) {
        Objects.requireNonNull(job, "production job");
        Objects.requireNonNull(inputItemId, "production input item id");
        if (!job.consumedItemId().equals(inputItemId)) throw new IllegalArgumentException("production job input identity differs");
        if (productionJobs.containsKey(job.id())) throw new IllegalArgumentException("production job identity already exists: " + job.id().value());
        if (productionJobs.values().stream().anyMatch(existing -> existing.facilityId().equals(job.facilityId()))) {
            throw new IllegalArgumentException("facility already has an active production job: " + job.facilityId().value());
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(productionJobs);
        next.put(job.id(), job);
        return new FrontierWorldState(bootstrap, actorLocations, structureConditions, infection, inventory.withoutItem(inputItemId), next, contracts);
    }

    public FrontierWorldState completeProductionJob(SubjectId jobId, ExactItemStack output) {
        ProductionJob job = productionJobs.get(Objects.requireNonNull(jobId, "production job id"));
        if (job == null) throw new IllegalArgumentException("unknown production job: " + jobId.value());
        if (!job.outputItemId().equals(output.id()) || !job.outputItemKind().equals(output.itemKind()) || job.outputCount() != output.count()) {
            throw new IllegalArgumentException("production output does not match durable job result");
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(productionJobs);
        next.remove(jobId);
        return new FrontierWorldState(bootstrap, actorLocations, structureConditions, infection, inventory.store(output), next, contracts);
    }

    public FrontierWorldState createSupplyContract(SupplyContract contract) {
        if (contracts.containsKey(contract.id())) throw new IllegalArgumentException("supply contract identity already exists");
        Map<SubjectId, SupplyContract> next = new LinkedHashMap<>(contracts); next.put(contract.id(), contract);
        return new FrontierWorldState(bootstrap, actorLocations, structureConditions, infection, inventory, productionJobs, next);
    }
    public FrontierWorldState loadContractCargo(SubjectId contractId, CargoBatch cargo) {
        SupplyContract contract = contracts.get(contractId);
        if (contract == null || contract.status() != ContractStatus.ORDERED || !contract.cargoId().equals(cargo.id())) throw new IllegalArgumentException("cargo load does not match an ordered contract");
        Map<SubjectId, SupplyContract> next = new LinkedHashMap<>(contracts);
        next.put(contractId, new SupplyContract(contract.id(), contract.settlementId(), contract.recipientId(), contract.cargoId(), contract.itemKind(), contract.itemCount(), ContractStatus.LOADED));
        return new FrontierWorldState(bootstrap, actorLocations, structureConditions, infection, inventory.loadCargo(cargo), productionJobs, next);
    }

    public static SubjectId depotId(SubjectId settlementId) {
        Objects.requireNonNull(settlementId, "settlement id");
        if (!settlementId.value().startsWith("settlement:")) throw new IllegalArgumentException("settlement id must use settlement: namespace");
        return new SubjectId("container:" + settlementId.value().substring("settlement:".length()) + "-depot");
    }

    private static Set<SubjectId> actorIds(FrontierBootstrap bootstrap) {
        Set<SubjectId> ids = new HashSet<>();
        bootstrap.settlements().forEach(settlement -> settlement.residents().forEach(resident -> ids.add(resident.id())));
        bootstrap.hive().bioforms().forEach(bioform -> ids.add(bioform.id()));
        return ids;
    }
    private static Set<SubjectId> structureIds(FrontierBootstrap bootstrap) {
        Set<SubjectId> ids = new HashSet<>();
        bootstrap.settlements().forEach(settlement -> settlement.structures().forEach(structure -> ids.add(structure.id())));
        return ids;
    }
    private static Settlement settlement(FrontierBootstrap bootstrap, SubjectId settlementId) {
        return bootstrap.settlements().stream().filter(value -> value.id().equals(settlementId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown production settlement: " + settlementId.value()));
    }
    private static SettlementStructure structure(Settlement settlement, SubjectId structureId) {
        return settlement.structures().stream().filter(value -> value.id().equals(structureId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown production facility: " + structureId.value()));
    }
    private static Resident resident(Settlement settlement, SubjectId residentId) {
        return settlement.residents().stream().filter(value -> value.id().equals(residentId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown production worker: " + residentId.value()));
    }
    private static void requirePosition(WorldBounds bounds, BlockPosition position) {
        if (!bounds.contains(position)) throw new IllegalArgumentException("canonical state position is outside frontier bounds");
    }
    private static <K, V> Map<K, V> immutableMap(Map<K, V> input, String label) {
        Objects.requireNonNull(input, label);
        LinkedHashMap<K, V> copy = new LinkedHashMap<>();
        input.forEach((key, value) -> copy.put(Objects.requireNonNull(key, label + " key"), Objects.requireNonNull(value, label + " value")));
        return Map.copyOf(copy);
    }
}
