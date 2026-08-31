package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FixedRatio; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId; import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId; import java.util.HashSet; import java.util.LinkedHashMap; import java.util.List; import java.util.Map; import java.util.Objects; import java.util.Set; import java.util.function.Supplier;
    public record FrontierWorldState(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actorLocations,
        Map<SubjectId, StructureCondition> structureConditions, Map<InfectionCell, FixedRatio> infection, ExactInventory inventory, Map<SubjectId, ProductionJob> productionJobs,
        Map<SubjectId, SupplyContract> contracts, Map<SubjectId, RouteOperation> operations, LogisticsHistory logisticsHistory,
        Map<PhysicalIntentId, PhysicalIntent> physicalIntents, Map<PhysicalObservationId, PhysicalEffectObservation> physicalObservations,
        Map<SceneLeaseId, SceneLease> sceneLeases, HiveColony hiveColony, Map<SubjectId, StructureDamage> structureDamage, Map<BlockPosition, PhysicalDelta> physicalDeltas,
        Map<SubjectId, AmbientActorLease> ambientLeases, Map<SubjectId, RouteConstruction> routeConstructions, RouteTopology routeTopology, StrategicPlanState strategicPlans,
        HumanPopulation humanPopulation, CompanyRegistry companies, ResourceSiteState resourceSites) {
    private static final FixedRatio ZERO_INFECTION = new FixedRatio(io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO); private static final int MAX_OPERATIONS = 1_024, MAX_PHYSICAL_INTENTS = 4_096, MAX_PHYSICAL_OBSERVATIONS = 4_096;
    private static final int MAX_SCENE_LEASES = 1_024, MAX_AMBIENT_LEASES = 4_096, MAX_STRUCTURE_DAMAGE_CELLS = 65_536;
    private static final ThreadLocal<Integer> DEFERRED_FULL_VALIDATION_DEPTH = ThreadLocal.withInitial(() -> 0);
    /** Compatibility constructor for pre-company pure fixtures; production transitions always carry the registry explicitly. */
    public FrontierWorldState(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actorLocations, Map<SubjectId, StructureCondition> structureConditions,
                       Map<InfectionCell, FixedRatio> infection, ExactInventory inventory, Map<SubjectId, ProductionJob> productionJobs,
                       Map<SubjectId, SupplyContract> contracts, Map<SubjectId, RouteOperation> operations, LogisticsHistory logisticsHistory,
                       Map<PhysicalIntentId, PhysicalIntent> physicalIntents, Map<PhysicalObservationId, PhysicalEffectObservation> physicalObservations,
                       Map<SceneLeaseId, SceneLease> sceneLeases, HiveColony hiveColony, Map<SubjectId, StructureDamage> structureDamage,
                       Map<BlockPosition, PhysicalDelta> physicalDeltas, Map<SubjectId, AmbientActorLease> ambientLeases,
                       Map<SubjectId, RouteConstruction> routeConstructions, RouteTopology routeTopology, StrategicPlanState strategicPlans,
                       HumanPopulation humanPopulation, ResourceSiteState resourceSites) {
        this(bootstrap, actorLocations, structureConditions, infection, inventory, productionJobs, contracts, operations, logisticsHistory,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases, routeConstructions,
                routeTopology, strategicPlans, humanPopulation, CompanyRegistry.empty(), resourceSites);
    }
    public FrontierWorldState { Objects.requireNonNull(bootstrap, "bootstrap");
        actorLocations = FrontierWorldStateSupport.immutableMap(actorLocations, "actor locations"); structureConditions = FrontierWorldStateSupport.immutableMap(structureConditions, "structure conditions");
        infection = FrontierWorldStateSupport.immutableMap(infection, "infection"); Objects.requireNonNull(inventory, "inventory");
        productionJobs = FrontierWorldStateSupport.immutableMap(productionJobs, "production jobs"); contracts = FrontierWorldStateSupport.immutableMap(contracts, "supply contracts");
        operations = FrontierWorldStateSupport.immutableMap(operations, "route operations"); Objects.requireNonNull(logisticsHistory, "logistics history"); physicalIntents = FrontierWorldStateSupport.immutableMap(physicalIntents, "physical intents");
        physicalObservations = FrontierWorldStateSupport.immutableMap(physicalObservations, "physical observations"); sceneLeases = FrontierWorldStateSupport.immutableMap(sceneLeases, "scene leases");
        structureDamage = FrontierWorldStateSupport.immutableMap(structureDamage, "structure damage"); physicalDeltas = FrontierWorldStateSupport.immutableMap(physicalDeltas, "physical deltas");
        ambientLeases = FrontierWorldStateSupport.immutableMap(ambientLeases, "ambient leases"); routeConstructions = FrontierWorldStateSupport.immutableMap(routeConstructions, "route constructions");
        Objects.requireNonNull(routeTopology, "route topology"); Objects.requireNonNull(strategicPlans, "strategic plans"); Objects.requireNonNull(humanPopulation, "human population");
        Objects.requireNonNull(companies, "company registry"); Objects.requireNonNull(resourceSites, "resource sites");
        if (!fullValidationDeferred()) {
            resourceSites.validate(bootstrap); strategicPlans.validate(bootstrap, humanPopulation); strategicPlans.hiveOperationKnowledge().validate(bootstrap, hiveColony, actorLocations);
            strategicPlans.hiveSettlementKnowledge().validate(bootstrap, hiveColony, actorLocations);
            strategicPlans.hiveTerritoryKnowledge().validate(bootstrap, hiveColony, actorLocations, structureConditions);
        routeTopology.replacementSupplyRoutes().forEach((settlement, route) -> FrontierRouteNetwork.validateSupplyWaypoints(bootstrap, settlement, route));
        RouteConstructionStateSupport.validate(bootstrap, routeTopology, routeConstructions, humanPopulation, productionJobs, resourceSites, operations, contracts, strategicPlans); Objects.requireNonNull(hiveColony, "hive colony");
        hiveColony.validateAgainst(bootstrap); HiveNutrientTransferStateSupport.validate(bootstrap, inventory, hiveColony, strategicPlans);
        FrontierWorldStateSupport.validateEconomicClaims(bootstrap, inventory);
        Set<SubjectId> expectedActors = FrontierWorldStateSupport.bioformIds(bootstrap); expectedActors.addAll(hiveColony.spawnedBioforms().keySet()); expectedActors.addAll(humanPopulation.residentIds());
        if (!expectedActors.equals(actorLocations.keySet())) throw new IllegalArgumentException("actor location index must own every and only canonical actor"); FrontierWorldStateSupport.validateActorItemCustody(expectedActors, inventory);
        Set<SubjectId> expectedSettlementPolicies = bootstrap.settlements().stream().map(Settlement::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!humanPopulation.quarantines().keySet().equals(expectedSettlementPolicies)) throw new IllegalArgumentException("settlement quarantine index must own every and only canonical settlement");
        for (Settlement settlement : bootstrap.settlements()) for (Resident bootstrapResident : settlement.residents()) {
            ResidentProfile profile = humanPopulation.resident(bootstrapResident.id());
            // Bootstrap defines an exact person's immutable identity, not their permanent home.
            // A completed v3 migration deliberately changes the profile's household/settlement.
            if (profile == null) throw new IllegalArgumentException("bootstrap resident must remain in the canonical population register"); }
        for (Company company : companies.companies().values()) {
            FrontierWorldStateSupport.settlement(bootstrap, company.settlementId());
            ResidentProfile founder = humanPopulation.resident(company.founderId());
            if (founder == null) {
                throw new IllegalArgumentException("company founder must remain a canonical resident");
            }
            EconomicAccount account = inventory.economics().require(company.id());
            if (account.ownerKind() != EconomicOwnerKind.COMPANY || account.status() != EconomicAccountStatus.ACTIVE && company.status() == CompanyStatus.ACTIVE) {
                throw new IllegalArgumentException("company legal state and account must agree");
            }
        }
        for (EmploymentContract contract : companies.employmentContracts().values()) {
            Company company = companies.companies().get(contract.companyId());
            ResidentProfile resident = humanPopulation.resident(contract.residentId());
            if (company == null || resident == null) {
                throw new IllegalArgumentException("employment must bind known legal identities");
            }
            EconomicAccount account = inventory.economics().require(contract.residentId());
            if (account.ownerKind() != EconomicOwnerKind.RESIDENT) {
                throw new IllegalArgumentException("employment resident must retain a resident account");
            }
            if (contract.status() == EmploymentContractStatus.ACTIVE
                    && (company.status() != CompanyStatus.ACTIVE || !resident.settlementId().equals(company.settlementId())
                    || humanPopulation.migrations().containsKey(resident.id())
                    || actorLocations.get(resident.id()).condition().status() != ActorLifeStatus.ALIVE)) {
                throw new IllegalArgumentException("active employment requires a settled resident in an active local company");
            }
        }
        for (EconomicAccount account : inventory.economics().accounts().values()) {
            if (account.ownerKind() == EconomicOwnerKind.COMPANY && !companies.companies().containsKey(account.ownerId())) {
                throw new IllegalArgumentException("company account must have one registered legal company");
            }
            if (account.ownerKind() == EconomicOwnerKind.RESIDENT && companies.employmentContracts().values().stream()
                    .noneMatch(contract -> contract.residentId().equals(account.ownerId()))) {
                throw new IllegalArgumentException("resident account must have one employment contract");
            }
        }
        for (ActorLocation location : actorLocations.values()) FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), location.position());
        Map<SubjectId, SupplyContract> validatedContracts = contracts;
        StrategicPlanState validatedPlans = strategicPlans;
        for (ResidentMigrationJourney journey : humanPopulation.migrations().values()) {
            ActorLocation actor = actorLocations.get(journey.residentId());
            if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE || !actor.position().equals(journey.currentPosition())) {
                throw new IllegalArgumentException("migration journey must retain one living resident at its exact route cursor");
            }
            journey.route().forEach(position -> FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), position));
            if (sceneLeases.values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                    && lease.members().stream().anyMatch(member -> member.actorId().equals(journey.residentId())))) {
                throw new IllegalArgumentException("migration journey resident may not retain a competing scene executor");
            }
            AmbientActorLease ambient = ambientLeases.get(journey.residentId());
            if (ambient != null && ambient.status() != AmbientLeaseStatus.CLOSED && ambient.goal() != AmbientGoalKind.TRANSIT) {
                throw new IllegalArgumentException("migration journey resident may retain only its exact HOT transit executor");
            }
            boolean operationClaim = operations.values().stream().anyMatch(operation -> FrontierWorldStateSupport.retainsParticipantClaim(validatedContracts, operation)
                    && operation.participantIds().contains(journey.residentId()));
            boolean patrolClaim = validatedPlans.routePatrols().values().stream().anyMatch(patrol -> patrol.status() == RoutePatrolStatus.EN_ROUTE
                    && patrol.memberIds().contains(journey.residentId()));
            if (operationClaim || patrolClaim) {
                throw new IllegalArgumentException("migration journey resident cannot retain a competing operation or patrol claim");
            }
        }
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
            ResidentProfile worker = humanPopulation.resident(job.workerId());
            if (worker == null) throw new IllegalArgumentException("production job worker must be a canonical resident");
            if (!worker.settlementId().equals(settlement.id()) || worker.profession() != ResidentProfession.INDUSTRIAL_WORKER) throw new IllegalArgumentException("production job worker must be a settlement industrial worker");
            if (inventory.items().containsKey(job.outputItemId())) {
                throw new IllegalArgumentException("active production job must not retain its output stack");
            }
            switch (job.inputHold()) {
                case ProductionInputHold.Cold held -> {
                    ExactItemStack input = held.item();
                    if (inventory.items().containsKey(input.id()) || !input.id().equals(job.consumedItemId())
                            || !input.economicOwnerId().equals(settlement.id()) || !"minecraft:wheat".equals(input.itemKind())
                            || input.count() != job.outputCount() || !(input.custody() instanceof InventoryCustody.ContainerSlot slot)
                            || !slot.containerId().equals(depotId(settlement.id())) || inventory.itemAt(slot.containerId(), slot.slot()).isPresent()) {
                        throw new IllegalArgumentException("cold production job must be the sole exact holder of its depot wheat input");
                    }
                }
                case ProductionInputHold.Materialized ignored -> {
                    ExactItemStack input = inventory.items().get(job.consumedItemId());
                    boolean inOwnedDepot = input != null && input.custody() instanceof InventoryCustody.ContainerSlot slot
                            && slot.containerId().equals(depotId(settlement.id()));
                    boolean awaitingPhysicalReconciliation = physicalIntents.values().stream().anyMatch(intent -> intent.causeSubjectId().equals(job.id())
                            && intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.PRODUCTION_TRANSFORMATION
                            && intent.status() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED);
                    boolean exactInputMissingOrAltered = input == null || input.count() != job.outputCount();
                    if ((exactInputMissingOrAltered || !inOwnedDepot) && !awaitingPhysicalReconciliation) {
                        throw new IllegalArgumentException("materialized production job input must remain in its exact settlement depot slot");
                    }
                }
            }
        }
        Map<SubjectId, SupplyContract> contractsByCargo = new LinkedHashMap<>();
        for (Map.Entry<SubjectId, SupplyContract> entry : contracts.entrySet()) {
            SupplyContract contract = entry.getValue();
            if (!entry.getKey().equals(contract.id())) throw new IllegalArgumentException("contract map key must match contract identity");
            FrontierWorldStateSupport.settlement(bootstrap, contract.settlementId());
            if (!bootstrap.hive().id().equals(contract.recipientId())) throw new IllegalArgumentException("contract recipient must be the frontier hive");
            if (contract.status() == ContractStatus.LOADED && !inventory.cargo().containsKey(contract.cargoId())) throw new IllegalArgumentException("loaded contract must own its cargo");
            if (contract.status() != ContractStatus.LOADED && inventory.cargo().containsKey(contract.cargoId())) throw new IllegalArgumentException("only a loaded contract can own cargo");
            contractsByCargo.putIfAbsent(contract.cargoId(), contract);
        }
        for (TerminalLogisticsReceipt receipt : logisticsHistory.receipts().values()) {
            if (operations.containsKey(receipt.operationId()) || contracts.containsKey(receipt.contractId())
                    || inventory.cargo().containsKey(receipt.cargoId())) {
                throw new IllegalArgumentException("terminal logistics receipt must not retain an active logistics claim");
            }
        }
        if (operations.size() > MAX_OPERATIONS) throw new IllegalArgumentException("route operation retention limit exceeded");
        Set<SubjectId> leaseHistoryOperations = sceneLeases.values().stream().filter(FrontierSceneBehaviors::isLogistics)
                .map(lease -> FrontierSceneBehaviors.logistics(lease).operationId()).collect(java.util.stream.Collectors.toSet());
        Map<SubjectId, Set<SubjectId>> residentsBySettlement = new LinkedHashMap<>();
        for (ResidentProfile resident : humanPopulation.residents().values()) {
            residentsBySettlement.computeIfAbsent(resident.settlementId(), ignored -> new HashSet<>()).add(resident.id());
        }
        Set<SubjectId> assignedCargo = new HashSet<>();
        Set<SubjectId> assignedParticipants = new HashSet<>();
        for (Map.Entry<SubjectId, RouteOperation> entry : operations.entrySet()) {
            RouteOperation operation = entry.getValue();
            if (!entry.getKey().equals(operation.id())) throw new IllegalArgumentException("route operation map key must match operation identity");
            Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, operation.settlementId());
            if (!bootstrap.hive().id().equals(operation.destinationId())) throw new IllegalArgumentException("route operation destination must be the frontier hive");
            CargoBatch cargo = inventory.cargo().get(operation.cargoId());
            SupplyContract contract = contractsByCargo.get(operation.cargoId());
            if (contract == null) throw new IllegalArgumentException("route operation cargo has no contract");
            if ((operation.stage() == OperationStage.ARRIVED || operation.stage() == OperationStage.RETURNING || operation.stage() == OperationStage.COMPLETED)
                    && contract.status() == ContractStatus.DELIVERED) {
                if (cargo != null) throw new IllegalArgumentException("delivered operation cannot retain cargo");
            } else if (operation.stage() == OperationStage.INTERRUPTED && contract.status() == ContractStatus.INTERRUPTED) {
                if (cargo != null) throw new IllegalArgumentException("interrupted operation cannot retain cargo");
            } else if (cargo == null || !cargo.ownerId().equals(operation.settlementId())) {
                throw new IllegalArgumentException("route operation must own settlement cargo");
            }
            if (!assignedCargo.add(operation.cargoId())) throw new IllegalArgumentException("cargo cannot be assigned to multiple route operations");
            Set<SubjectId> settlementResidents = residentsBySettlement.getOrDefault(settlement.id(), Set.of());
            RouteUnitManifest unit = operation.unit();
            ResidentProfile hauler = humanPopulation.resident(unit.cargoCrewId());
            List<ResidentProfile> escorts = unit.members().stream().filter(member -> member.duty() == RouteUnitDuty.ESCORT).map(RouteUnitMember::residentId).map(humanPopulation::resident).toList();
            if (hauler == null || hauler.profession() != ResidentProfession.LOGISTICIAN || escorts.stream().anyMatch(java.util.Objects::isNull)
                    || escorts.stream().anyMatch(escort -> escort.profession() != ResidentProfession.SECURITY_WORKER)) {
                throw new IllegalArgumentException("supply route operation must retain logistician crew and exact security escorts");
            }
            for (SubjectId participant : operation.participantIds()) {
                if (!settlementResidents.contains(participant)) throw new IllegalArgumentException("route operation participant must belong to its settlement");
                if (FrontierWorldStateSupport.retainsParticipantClaim(contracts, operation) && !assignedParticipants.add(participant)) {
                    throw new IllegalArgumentException("resident cannot be assigned to multiple active route operations");
                }
                boolean patrolClaim = strategicPlans.routePatrols().values().stream().anyMatch(patrol -> patrol.status() == RoutePatrolStatus.EN_ROUTE
                        && patrol.memberIds().contains(participant));
                if (FrontierWorldStateSupport.retainsParticipantClaim(contracts, operation)
                        && (humanPopulation.migrations().containsKey(participant) || patrolClaim)) {
                    throw new IllegalArgumentException("active route operation participant cannot retain a competing migration or patrol claim");
                }
                if (operation.stage() != OperationStage.COMPLETED && operation.stage() != OperationStage.FAILED && operation.stage() != OperationStage.INTERRUPTED && actorLocations.get(participant).condition().status() == ActorLifeStatus.ALIVE
                        && !leaseHistoryOperations.contains(operation.id())
                        && !actorLocations.get(participant).position().equals(operation.activeTravel().map(travel -> travel.formation().get(participant))
                        .orElseGet(() -> operation.activeAssembly().map(assembly -> assembly.positions().get(participant)).orElseGet(operation::currentPosition)))) {
                    throw new IllegalArgumentException("active route operation participant must be at its canonical travel position");
                }
            }
            operation.route().forEach(position -> FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), position));
        }
        FrontierRouteEngagementSupport.validate(bootstrap, hiveColony, actorLocations, operations, strategicPlans);
        FrontierSettlementAssaultSupport.validate(bootstrap, hiveColony, humanPopulation, actorLocations, strategicPlans);
        if (physicalIntents.size() > MAX_PHYSICAL_INTENTS) throw new IllegalArgumentException("physical intent retention limit exceeded");
        for (Map.Entry<PhysicalIntentId, PhysicalIntent> entry : physicalIntents.entrySet()) {
            PhysicalIntent intent = entry.getValue();
            if (!entry.getKey().equals(intent.id())) throw new IllegalArgumentException("physical intent map key must match intent identity");
            if ((intent.status() != PhysicalIntentStatus.CONFIRMED && intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
                    && !expectedActors.contains(intent.causeSubjectId()) && !inventory.cargo().containsKey(intent.causeSubjectId()) && !operations.containsKey(intent.causeSubjectId()) && !hiveColony.growthJobs().containsKey(intent.causeSubjectId())
                    && !humanPopulation.birthJobs().containsKey(intent.causeSubjectId())
                    && !humanPopulation.provisions().containsKey(intent.causeSubjectId())
                    && !expectedStructures.contains(intent.causeSubjectId()) && !productionJobs.containsKey(intent.causeSubjectId())
                    && !contracts.containsKey(intent.causeSubjectId()) && !FrontierWorldStateSupport.isHiveOrgan(bootstrap, hiveColony, intent.causeSubjectId())
                    && !strategicPlans.settlementAssaults().containsKey(intent.causeSubjectId())
                    && !bootstrap.hive().id().equals(intent.causeSubjectId())
                    && !FrontierRouteNetwork.OWNER.equals(intent.causeSubjectId()) && !routeConstructions.containsKey(intent.causeSubjectId())
                    && !ResourceSitePhysicalIntentStateSupport.ownsNonterminalSubject(resourceSites, intent.causeSubjectId())) {
                throw new IllegalArgumentException("physical intent cause must be a canonical subject");
            }
            for (SubjectId subject : intent.subjectIds()) {
                if (intent.status() == PhysicalIntentStatus.CONFIRMED || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) continue;
                RouteConstruction routeConstruction = routeConstructions.get(intent.causeSubjectId());
                boolean hiveNutrientSubject = HiveNutrientTransferStateSupport.ownsIntentSubject(hiveColony, intent, subject);
                boolean reservedRouteConstructionCargo = intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING
                        && routeConstruction != null && routeConstruction.cargoId().isEmpty()
                        && (subject.equals(routeConstruction.plannedCargoId())
                        || subject.equals(routeConstruction.plannedCargoItemId()));
                if (!hiveNutrientSubject && !expectedActors.contains(subject) && !inventory.cargo().containsKey(subject) && !operations.containsKey(subject)
                        && !expectedStructures.contains(subject) && !inventory.items().containsKey(subject)
                        && !hiveColony.growthJobs().containsKey(subject) && !humanPopulation.birthJobs().containsKey(subject) && !productionJobs.containsKey(subject) && !contracts.containsKey(subject)
                        && !humanPopulation.provisions().containsKey(subject)
                        && productionJobs.values().stream().noneMatch(job -> job.outputItemId().equals(subject))
                        && !FrontierWorldStateSupport.isHiveOrgan(bootstrap, hiveColony, subject)
                        && !FrontierRouteNetwork.OWNER.equals(subject) && !routeConstructions.containsKey(subject)
                        && !strategicPlans.routeEngagements().containsKey(subject) && !strategicPlans.settlementAssaults().containsKey(subject)
                        && contracts.values().stream().noneMatch(contract -> contract.cargoId().equals(subject))
                        && !reservedRouteConstructionCargo && !ResourceSitePhysicalIntentStateSupport.ownsNonterminalSubject(resourceSites, subject)) {
                    throw new IllegalArgumentException("physical intent references an unknown canonical subject");
                }
            }
        }
        if (physicalObservations.size() > MAX_PHYSICAL_OBSERVATIONS) throw new IllegalArgumentException("physical observation retention limit exceeded");
        FrontierWorldPhysicalObservationValidation.validate(bootstrap, inventory, infection, physicalIntents, physicalObservations, operations,
                contracts, sceneLeases, routeConstructions, routeTopology);
        ResourceSitePhysicalIntentStateSupport.validateState(resourceSites, physicalIntents, physicalObservations);
        for (PhysicalIntent intent : physicalIntents.values()) {
            if (intent.status() == PhysicalIntentStatus.CONFIRMED
                    && !physicalObservations.containsKey(intent.postconditionObservationId().orElseThrow())) {
                throw new IllegalArgumentException("confirmed physical intent must retain its exact observation");
            }
        }
        if (sceneLeases.size() > MAX_SCENE_LEASES) throw new IllegalArgumentException("scene lease retention limit exceeded");
        FrontierSceneLeaseValidationSupport.validate(bootstrap, actorLocations, structureConditions, operations, strategicPlans, sceneLeases, activelyAmbientLeased);
        }
        }
    public static FrontierWorldState initial(FrontierBootstrap bootstrap) { return FrontierWorldInitialState.create(bootstrap); }
    /**
     * Reducer-local state construction is allowed to defer the complete aggregate audit until
     * the kernel has assembled the whole transaction. The enclosing kernel must call
     * {@link #validateComplete()} before WAL durability; this is deliberately package-private
     * so adapters and codecs keep the strict public constructor boundary.
     */
    public static <T> T duringReducerTransition(Supplier<T> transition) {
        Objects.requireNonNull(transition, "transition");
        int depth = DEFERRED_FULL_VALIDATION_DEPTH.get();
        DEFERRED_FULL_VALIDATION_DEPTH.set(depth + 1);
        try { return transition.get(); }
        finally {
            if (depth == 0) DEFERRED_FULL_VALIDATION_DEPTH.remove();
            else DEFERRED_FULL_VALIDATION_DEPTH.set(depth);
        }
    }
    /** Runs the same complete invariant audit as strict construction, without altering this snapshot. */
    void validateComplete() {
        int depth = DEFERRED_FULL_VALIDATION_DEPTH.get();
        DEFERRED_FULL_VALIDATION_DEPTH.remove();
        try {
            new FrontierWorldState(bootstrap, actorLocations, structureConditions, infection, inventory, productionJobs, contracts,
                    operations, logisticsHistory, physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas,
                    ambientLeases, routeConstructions, routeTopology, strategicPlans, humanPopulation, companies, resourceSites);
        } finally {
            if (depth != 0) DEFERRED_FULL_VALIDATION_DEPTH.set(depth);
        }
    }
    /**
     * Pre-WAL dependency-aware audit for the one high-frequency sparse-field transition.
     * Everything outside the infection field remains the exact same immutable object, therefore
     * it was already proven by the preceding accepted state. Unknown construction paths fall
     * back to the complete audit instead of trusting an inferred delta.
     */
    void validateTransitionFrom(FrontierWorldState previous) {
        Objects.requireNonNull(previous, "previous state");
        if (onlyInfectionPlannerAndHealthChangedFrom(previous)) {
            validatePlannerAndHealthTransition();
            validateInfectionTransition(previous);
            return;
        }
        if (onlyPlannerAndHealthChangedFrom(previous)) {
            validatePlannerAndHealthTransition();
            return;
        }
        if (!onlyInfectionChangedFrom(previous)) { validateComplete(); return; }
        validateInfectionTransition(previous);
    }
    /**
     * A hive infection task commonly changes its one sparse cell and its task/plan state in the
     * same atomic transaction. Both mutable domains have complete local validators; enumerating
     * every untouched infection cell merely because the task transitioned is unnecessary.
     */
    private boolean onlyInfectionPlannerAndHealthChangedFrom(FrontierWorldState previous) {
        return infection != previous.infection && onlyPlannerAndHealthChangedFrom(previous, false);
    }
    private boolean onlyPlannerAndHealthChangedFrom(FrontierWorldState previous) {
        return onlyPlannerAndHealthChangedFrom(previous, true);
    }
    private boolean onlyPlannerAndHealthChangedFrom(FrontierWorldState previous, boolean requirePlannerOrHealthChange) {
        if (bootstrap != previous.bootstrap || actorLocations != previous.actorLocations || structureConditions != previous.structureConditions
                || (requirePlannerOrHealthChange && infection != previous.infection) || inventory != previous.inventory || productionJobs != previous.productionJobs
                || contracts != previous.contracts || operations != previous.operations || logisticsHistory != previous.logisticsHistory || physicalIntents != previous.physicalIntents
                || physicalObservations != previous.physicalObservations || sceneLeases != previous.sceneLeases || hiveColony != previous.hiveColony
                || structureDamage != previous.structureDamage || physicalDeltas != previous.physicalDeltas || ambientLeases != previous.ambientLeases
                || routeConstructions != previous.routeConstructions || routeTopology != previous.routeTopology || companies != previous.companies || resourceSites != previous.resourceSites
                || (strategicPlans == previous.strategicPlans && humanPopulation == previous.humanPopulation)) return false;
        if (!sceneLeases.isEmpty() || !physicalIntents.isEmpty() || !physicalObservations.isEmpty()) return false;
        return humanPopulation.households() == previous.humanPopulation.households()
                && humanPopulation.residents() == previous.humanPopulation.residents()
                && humanPopulation.birthJobs() == previous.humanPopulation.birthJobs()
                && humanPopulation.migrations() == previous.humanPopulation.migrations();
    }
    private void validateInfectionTransition(FrontierWorldState previous) {
        FrontierInfectionFrontier.InfectionChange change = FrontierWorldStateSupport.infectionChange(infection, bootstrap.bounds()).orElse(null);
        PersistentInfectionMap before = FrontierWorldStateSupport.persistentInfection(previous.infection);
        PersistentInfectionMap after = FrontierWorldStateSupport.persistentInfection(infection);
        if (change == null || !after.directlyFollows(before, change.cell(), change.previousRaw(), change.nextRaw())) {
            validateComplete(); return;
        }
        FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), change.cell().originAtY(0));
        FixedRatio current = infection.get(change.cell());
        if ((change.nextRaw() == 0L) != (current == null)
                || current != null && (current.value().raw() != change.nextRaw() || current.value().raw() == 0L)
                || raw(previous.infection.get(change.cell())) != change.previousRaw()) {
            throw new IllegalArgumentException("infection transition does not match its retained sparse-field delta");
        }
    }
    private boolean onlyInfectionChangedFrom(FrontierWorldState previous) {
        return bootstrap == previous.bootstrap && actorLocations == previous.actorLocations && structureConditions == previous.structureConditions
                && inventory == previous.inventory && productionJobs == previous.productionJobs && contracts == previous.contracts
                && operations == previous.operations && logisticsHistory == previous.logisticsHistory && physicalIntents == previous.physicalIntents && physicalObservations == previous.physicalObservations
                && sceneLeases == previous.sceneLeases && hiveColony == previous.hiveColony && structureDamage == previous.structureDamage
                && physicalDeltas == previous.physicalDeltas && ambientLeases == previous.ambientLeases && routeConstructions == previous.routeConstructions
                && routeTopology == previous.routeTopology && strategicPlans == previous.strategicPlans && humanPopulation == previous.humanPopulation && companies == previous.companies
                && resourceSites == previous.resourceSites && infection != previous.infection;
    }
    private void validatePlannerAndHealthTransition() {
        Set<SubjectId> expectedSettlementPolicies = bootstrap.settlements().stream().map(Settlement::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!humanPopulation.quarantines().keySet().equals(expectedSettlementPolicies)) {
            throw new IllegalArgumentException("settlement quarantine index must own every and only canonical settlement");
        }
        strategicPlans.validate(bootstrap, humanPopulation);
        validatePlannerActorClaims();
        FrontierRouteEngagementSupport.validate(bootstrap, hiveColony, actorLocations, operations, strategicPlans);
        FrontierSettlementAssaultSupport.validate(bootstrap, hiveColony, humanPopulation, actorLocations, strategicPlans);
    }
    /** Exact COLD authority remains exclusive even when only a route patrol plan has changed. */
    private void validatePlannerActorClaims() {
        Set<SubjectId> operationParticipants = new HashSet<>();
        for (RouteOperation operation : operations.values()) {
            if (!FrontierWorldStateSupport.retainsParticipantClaim(contracts, operation)) continue;
            for (SubjectId participant : operation.participantIds()) {
                if (!operationParticipants.add(participant)) {
                    throw new IllegalArgumentException("resident cannot be assigned to multiple active route operations");
                }
                boolean patrolClaim = strategicPlans.routePatrols().values().stream()
                        .anyMatch(patrol -> patrol.status() == RoutePatrolStatus.EN_ROUTE && patrol.memberIds().contains(participant));
                if (humanPopulation.migrations().containsKey(participant) || patrolClaim) {
                    throw new IllegalArgumentException("active route operation participant cannot retain a competing migration or patrol claim");
                }
            }
        }
        for (ResidentMigrationJourney journey : humanPopulation.migrations().values()) {
            boolean patrolClaim = strategicPlans.routePatrols().values().stream()
                    .anyMatch(patrol -> patrol.status() == RoutePatrolStatus.EN_ROUTE && patrol.memberIds().contains(journey.residentId()));
            if (patrolClaim) throw new IllegalArgumentException("migration journey resident cannot retain a competing operation or patrol claim");
        }
    }
    private static long raw(FixedRatio ratio) { return ratio == null ? 0L : ratio.value().raw(); }
    private static boolean fullValidationDeferred() { return DEFERRED_FULL_VALIDATION_DEPTH.get() > 0; }
    FrontierWorldState next(Map<SubjectId, ActorLocation> actors, Map<SubjectId, StructureCondition> structures,
                            Map<InfectionCell, FixedRatio> nextInfection, ExactInventory nextInventory, Map<SubjectId, ProductionJob> jobs,
                            Map<SubjectId, SupplyContract> nextContracts, Map<SubjectId, RouteOperation> nextOperations,
                            Map<PhysicalIntentId, PhysicalIntent> intents, Map<PhysicalObservationId, PhysicalEffectObservation> observations,
                            Map<SceneLeaseId, SceneLease> leases, HiveColony colony, Map<SubjectId, StructureDamage> damage,
                            Map<BlockPosition, PhysicalDelta> deltas, Map<SubjectId, AmbientActorLease> ambient) {
        return new FrontierWorldState(bootstrap, actors, structures, nextInfection, nextInventory, jobs, nextContracts, nextOperations,
                logisticsHistory, intents, observations, leases, colony, damage, deltas, ambient, routeConstructions, routeTopology, strategicPlans, humanPopulation, companies, resourceSites);
    }
    public FrontierWorldState withChanges(FrontierWorldStateUpdate change) {
        change = Objects.requireNonNull(change, "state change");
        return new FrontierWorldState(bootstrap, change.actorLocations(this), change.structureConditions(this), change.infection(this),
                change.inventory(this), change.productionJobs(this), change.contracts(this), change.operations(this), change.logisticsHistory(this),
                change.physicalIntents(this), change.physicalObservations(this), change.sceneLeases(this), change.hiveColony(this),
                change.structureDamage(this), change.physicalDeltas(this), change.ambientLeases(this), change.routeConstructions(this),
                change.routeTopology(this), change.strategicPlans(this), change.humanPopulation(this), change.companies(this), change.resourceSites(this));
    }
    public FrontierWorldState withRouteTopology(RouteTopology nextTopology) { return withChanges(FrontierWorldStateUpdate.begin().routeTopology(nextTopology)); }
    public FrontierWorldState withStrategicPlans(StrategicPlanState nextPlans) { return withChanges(FrontierWorldStateUpdate.begin().strategicPlans(nextPlans)); }
    public FrontierWorldState withResourceSites(ResourceSiteState nextSites) { return withChanges(FrontierWorldStateUpdate.begin().resourceSites(nextSites)); }
    public FrontierWorldState withHumanPopulation(HumanPopulation nextPopulation) { return withChanges(FrontierWorldStateUpdate.begin().humanPopulation(nextPopulation)); }
    public FrontierWorldState withCompanies(CompanyRegistry nextCompanies) { return withChanges(FrontierWorldStateUpdate.begin().companies(nextCompanies)); }
    public FrontierWorldState registerCompany(Company company) {
        Objects.requireNonNull(company, "company");
        EconomicLedger economics = inventory.economics().register(new EconomicAccount(company.id(), EconomicOwnerKind.COMPANY,
                EconomicAccountStatus.ACTIVE, io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO,
                io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO));
        return withChanges(FrontierWorldStateUpdate.begin().inventory(inventory.withEconomics(economics)).companies(companies.register(company)));
    }
    public FrontierWorldState openEmployment(EmploymentContract contract) {
        Objects.requireNonNull(contract, "employment contract");
        Company company = companies.companies().get(contract.companyId());
        ResidentProfile resident = humanPopulation.resident(contract.residentId());
        if (company == null || resident == null || !resident.settlementId().equals(company.settlementId())
                || humanPopulation.migrations().containsKey(resident.id())) {
            throw new IllegalArgumentException("employment must bind a resident in its company's settlement");
        }
        EconomicLedger economics = inventory.economics().register(new EconomicAccount(contract.residentId(), EconomicOwnerKind.RESIDENT,
                EconomicAccountStatus.ACTIVE, io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO,
                io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO));
        return withChanges(FrontierWorldStateUpdate.begin().inventory(inventory.withEconomics(economics)).companies(companies.openEmployment(contract)));
    }
    public FrontierWorldState recordResidentMigration(ResidentMigrated migration) { return HumanPopulationStateSupport.recordMigration(this, migration); }
    public FrontierWorldState startResidentBirth(ResidentBirthJob job) { return HumanPopulationStateSupport.startBirth(this, job); }
    public FrontierWorldState completeResidentBirth(ResidentBirthJob job) { return HumanPopulationStateSupport.completeBirth(this, job); }
    public FrontierWorldState cancelResidentBirth(SubjectId jobId) { return HumanPopulationStateSupport.cancelBirth(this, jobId); }
    public List<SceneEngagementCandidate> coldEngagementSceneCandidates() { return FrontierSceneEngagementSupport.candidates(this); }
    public List<SettlementAssaultSceneCandidate> coldSettlementAssaultSceneCandidates() { return FrontierSettlementAssaultSceneSupport.candidates(this); }
    public FrontierWorldState withActorLocation(SubjectId actor, BlockPosition position) { return withActorLocation(actor, position, strategicPlans); }
    public FrontierWorldState withActorLocation(SubjectId actor, BlockPosition position, StrategicPlanState nextPlans) {
        Objects.requireNonNull(actor, "actor"); FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), position);
        if (!actorLocations.containsKey(actor)) throw new IllegalArgumentException("unknown actor: " + actor.value());
        Map<SubjectId, ActorLocation> next = new LinkedHashMap<>(actorLocations); next.put(actor, actorLocations.get(actor).withPosition(position));
        return withChanges(FrontierWorldStateUpdate.begin().actorLocations(next).strategicPlans(nextPlans));
    }
    public FrontierWorldState withStructureCondition(SubjectId structure, StructureCondition condition) {
        Objects.requireNonNull(structure, "structure"); Objects.requireNonNull(condition, "structure condition");
        if (!structureConditions.containsKey(structure)) throw new IllegalArgumentException("unknown structure: " + structure.value());
        Map<SubjectId, StructureCondition> next = new LinkedHashMap<>(structureConditions); next.put(structure, condition);
        return withChanges(FrontierWorldStateUpdate.begin().structureConditions(next)
                .resourceSites(resourceSitesForCondition(structure, condition)));
    }
    ResourceSiteState resourceSitesForCondition(SubjectId facilityId, StructureCondition condition) {
        if (condition != StructureCondition.DESTROYED) return resourceSites;
        return FrontierResourceSitePlan.compile(bootstrap).values().stream().filter(site -> site.facilityId().equals(facilityId)).findFirst()
                .map(site -> resourceSites.replace(resourceSites.site(site.id()).destroyed())).orElse(resourceSites);
    }
    public FrontierWorldState recordStructureDamage(StructureDamaged damage) { return FrontierWorldPhysicalDeltaSupport.recordStructureDamage(this, damage); }
    public FrontierWorldState recordPhysicalDelta(PhysicalDelta delta) { return FrontierWorldPhysicalDeltaSupport.record(this, delta); }
    public boolean isHiveOrganOperational(SubjectId organId) { return FrontierWorldPhysicalDeltaSupport.organOperational(bootstrap, hiveColony, physicalDeltas, organId); }
    public FrontierWorldState withInfection(InfectionCell cell, FixedRatio intensity) {
        Objects.requireNonNull(cell, "infection cell"); Objects.requireNonNull(intensity, "infection intensity");
        FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), cell.originAtY(0));
        PersistentInfectionMap next = FrontierWorldStateSupport.persistentInfection(infection)
                .changed(cell, intensity.equals(ZERO_INFECTION) ? null : intensity);
        FrontierInfectionFrontier frontier = FrontierWorldStateSupport.infectionFrontier(infection, bootstrap.bounds()).changed(infection, next, cell);
        return next(actorLocations, structureConditions, FrontierWorldStateSupport.infectionMap(next, frontier), inventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState withInventory(ExactInventory nextInventory) {
        return next(actorLocations, structureConditions, infection, nextInventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    /** A COLD job reserves its former source slot even though its exact stack is held by the job. */
    public boolean productionHoldReserves(InventoryCustody.ContainerSlot slot) {
        Objects.requireNonNull(slot, "container slot");
        return productionJobs.values().stream().map(ProductionJob::inputHold).filter(ProductionInputHold.Cold.class::isInstance)
                .map(ProductionInputHold.Cold.class::cast).map(ProductionInputHold.Cold::item)
                .anyMatch(item -> item.custody().equals(slot));
    }
    public boolean containerSlotAvailable(InventoryCustody.ContainerSlot slot) {
        return inventory.itemAt(slot.containerId(), slot.slot()).isEmpty() && !productionHoldReserves(slot);
    }
    public java.util.OptionalInt firstFreeContainerSlot(SubjectId containerId) {
        ContainerRecord container = inventory.containers().get(Objects.requireNonNull(containerId, "container id"));
        if (container == null) throw new IllegalArgumentException("unknown container: " + containerId.value());
        for (int slot = 0; slot < container.slotCount(); slot++) {
            if (containerSlotAvailable(new InventoryCustody.ContainerSlot(containerId, slot))) return java.util.OptionalInt.of(slot);
        }
        return java.util.OptionalInt.empty();
    }
    public FrontierWorldState withProductionJob(ProductionJob job) {
        Objects.requireNonNull(job, "production job");
        if (!(job.inputHold() instanceof ProductionInputHold.Materialized)) {
            throw new IllegalArgumentException("only a materialized production input may remain in exact inventory");
        }
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
        ExactItemStack input = inventory.items().get(inputItemId);
        if (input == null || !(input.custody() instanceof InventoryCustody.ContainerSlot)) throw new IllegalArgumentException("production input is unavailable");
        if (!(job.inputHold() instanceof ProductionInputHold.Cold held) || !held.item().equals(input)) {
            throw new IllegalArgumentException("cold production job must retain its exact removed input");
        }
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
        if (!(job.inputHold() instanceof ProductionInputHold.Cold) || inventory.items().containsKey(job.consumedItemId())) {
            throw new IllegalArgumentException("materialized production must confirm its physical transformation rather than emit a direct completion");
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(productionJobs); next.remove(jobId);
        return next(actorLocations, structureConditions, infection, inventory.store(output), next, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState cancelProductionJob(SubjectId jobId) {
        ProductionJob job = productionJobs.get(Objects.requireNonNull(jobId, "production job id"));
        if (job == null) throw new IllegalArgumentException("unknown production job: " + jobId.value());
        ExactInventory nextInventory = switch (job.inputHold()) {
            case ProductionInputHold.Cold cold -> {
                ExactItemStack held = cold.item();
                if (inventory.items().containsKey(held.id()) || !(held.custody() instanceof InventoryCustody.ContainerSlot source)
                        || inventory.itemAt(source.containerId(), source.slot()).isPresent()) {
                    throw new IllegalArgumentException("cold production hold cannot return to its original exact slot");
                }
                yield inventory.store(held);
            }
            // The real stack remains under observed custody; cancellation neither restores nor deletes it.
            case ProductionInputHold.Materialized ignored -> inventory;
        };
        Map<PhysicalIntentId, PhysicalIntent> nextIntents = new LinkedHashMap<>(physicalIntents);
        for (PhysicalIntent intent : physicalIntents.values()) if (intent.causeSubjectId().equals(job.id())) {
            if (intent.kind() != PhysicalIntentKind.PRODUCTION_TRANSFORMATION || intent.status() != PhysicalIntentStatus.PREPARED)
                throw new IllegalArgumentException("production job with a running or terminal physical transformation cannot be cancelled");
            nextIntents.remove(intent.id());
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(productionJobs); next.remove(job.id());
        return next(actorLocations, structureConditions, infection, nextInventory, next, contracts, operations,
                nextIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState createSupplyContract(SupplyContract contract) {
        if (contracts.containsKey(contract.id())) throw new IllegalArgumentException("supply contract identity already exists");
        Map<SubjectId, SupplyContract> next = new LinkedHashMap<>(contracts); next.put(contract.id(), contract);
        return next(actorLocations, structureConditions, infection, inventory, productionJobs, next, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState abandonOrderedSupplyContract(SubjectId contractId) {
        SupplyContract contract = contracts.get(Objects.requireNonNull(contractId, "contract id"));
        if (contract == null || contract.status() != ContractStatus.ORDERED) throw new IllegalArgumentException("only an ordered supply contract may be abandoned");
        if (inventory.cargo().containsKey(contract.cargoId())
                || operations.values().stream().anyMatch(operation -> operation.cargoId().equals(contract.cargoId()))
                || physicalIntents.values().stream().anyMatch(intent -> intent.causeSubjectId().equals(contract.id())
                || intent.subjectIds().contains(contract.id()) || intent.subjectIds().contains(contract.cargoId()))) {
            throw new IllegalArgumentException("a supply contract with acquired cargo or physical work may not be abandoned");
        }
        Map<SubjectId, SupplyContract> next = new LinkedHashMap<>(contracts); next.remove(contract.id());
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
    public FrontierWorldState completeColdCargoHandoff(SubjectId operationId, SubjectId cargoId, List<CargoHandoffPlacement> placements) {
        RouteOperation operation = operations.get(Objects.requireNonNull(operationId, "cold cargo operation id"));
        if (operation == null || operation.stage() != OperationStage.ARRIVED || !operation.cargoId().equals(cargoId)) {
            throw new IllegalArgumentException("cold cargo handoff does not match an arrived operation");
        }
        SubjectId receiver = FrontierCargoValidation.receiverStore(bootstrap, operation);
        if (placements.stream().anyMatch(placement -> !receiver.equals(placement.receiverSlot().containerId()))) {
            throw new IllegalArgumentException("cold cargo handoff targets a foreign receiver");
        }
        SupplyContract contract = contracts.values().stream().filter(value -> value.cargoId().equals(cargoId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("cold cargo handoff has no supply contract"));
        if (contract.status() != ContractStatus.LOADED) throw new IllegalArgumentException("only loaded cold cargo can arrive");
        Map<SubjectId, SupplyContract> nextContracts = new LinkedHashMap<>(contracts); nextContracts.put(contract.id(), contract.withStatus(ContractStatus.DELIVERED));
        return next(actorLocations, structureConditions, infection, inventory.completeCargoHandoff(cargoId, placements), productionJobs, nextContracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState createOperation(RouteOperation operation) {
        Objects.requireNonNull(operation, "route operation");
        if (operations.containsKey(operation.id())) throw new IllegalArgumentException("route operation identity already exists: " + operation.id().value());
        Map<SubjectId, RouteOperation> next = new LinkedHashMap<>(operations); next.put(operation.id(), operation);
        // Creation is a claim, never a movement.  Each assembly/travel cursor owns later positions.
        return next(actorLocations, structureConditions, infection, inventory, productionJobs, contracts, next,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState advanceOperation(SubjectId operationId, int nextRouteIndex, OperationStage nextStage) {
        RouteOperation operation = operations.get(Objects.requireNonNull(operationId, "route operation id"));
        if (operation == null) throw new IllegalArgumentException("unknown route operation: " + operationId.value());
        throw new IllegalArgumentException("route operation advancement must use one exact operation travel segment");
    }
    public FrontierWorldState startOperationTravel(SubjectId operationId, OperationTravel travel) {
        RouteOperation operation = operations.get(Objects.requireNonNull(operationId, "operation travel operation id"));
        if (operation == null || (operation.activeTravel().isPresent() && !operation.activeTravel().orElseThrow().arrived())) {
            throw new IllegalArgumentException("operation already owns an in-progress exact travel or does not exist");
        }
        RouteOperation started = operation.startTravel(Objects.requireNonNull(travel, "operation travel"));
        Map<SubjectId, RouteOperation> nextOperations = new LinkedHashMap<>(operations); nextOperations.put(operation.id(), started);
        Map<SubjectId, ActorLocation> nextActors = new LinkedHashMap<>(actorLocations);
        travel.formation().forEach((actor, position) -> nextActors.put(actor, actorLocations.get(actor).withPosition(position)));
        return next(nextActors, structureConditions, infection, inventory, productionJobs, contracts, nextOperations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState advanceOperationTravel(SubjectId operationId, OperationTravel travel) {
        RouteOperation operation = operations.get(Objects.requireNonNull(operationId, "operation travel operation id"));
        if (operation == null || operation.activeTravel().isEmpty()) throw new IllegalArgumentException("operation has no active exact travel");
        OperationTravel current = operation.activeTravel().orElseThrow();
        if (!current.corridor().equals(travel.corridor()) || travel.cursor() <= current.cursor() || travel.cursor() > current.nextColdCursor()) {
            throw new IllegalArgumentException("operation travel must advance its current bounded corridor");
        }
        RouteOperation advanced = operation.withTravel(travel); Map<SubjectId, RouteOperation> nextOperations = new LinkedHashMap<>(operations); nextOperations.put(operation.id(), advanced);
        Map<SubjectId, ActorLocation> nextActors = new LinkedHashMap<>(actorLocations);
        travel.formation().forEach((actor, position) -> nextActors.put(actor, actorLocations.get(actor).withPosition(position)));
        Map<SceneLeaseId, SceneLease> nextLeases = sceneLeases;
        SceneLease activeScene = sceneLeases.values().stream().filter(FrontierSceneBehaviors::isLogistics).filter(lease -> FrontierSceneBehaviors.logistics(lease).operationId().equals(operation.id()))
                .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED).findFirst().orElse(null);
        if (activeScene != null) {
            if (!travel.isExactHotAdvanceFrom(current)) {
                throw new IllegalArgumentException("HOT operation travel may advance only one exact cursor");
            }
            SceneLease rebased = activeScene.rebaseHotOperationTravel(current, travel);
            nextLeases = new LinkedHashMap<>(sceneLeases); nextLeases.put(rebased.id(), rebased);
        }
        return next(nextActors, structureConditions, infection, inventory, productionJobs, contracts, nextOperations,
                physicalIntents, physicalObservations, nextLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState advanceOperationAssembly(SubjectId operationId, OperationAssembly assembly) {
        RouteOperation operation = operations.get(Objects.requireNonNull(operationId, "operation assembly operation id"));
        if (operation == null || operation.activeAssembly().isEmpty()) throw new IllegalArgumentException("operation has no active assembly");
        OperationAssembly advanced = operation.activeAssembly().orElseThrow().advance(Objects.requireNonNull(assembly, "operation assembly").members());
        Map<SubjectId, RouteOperation> nextOperations = new LinkedHashMap<>(operations); nextOperations.put(operation.id(), operation.withAssembly(advanced));
        Map<SubjectId, ActorLocation> nextActors = new LinkedHashMap<>(actorLocations);
        advanced.positions().forEach((actor, position) -> nextActors.put(actor, actorLocations.get(actor).withPosition(position)));
        Map<SubjectId, AmbientActorLease> nextAmbient = new LinkedHashMap<>(ambientLeases);
        advanced.members().forEach((actor, member) -> {
            AmbientActorLease lease = nextAmbient.get(actor);
            if (lease != null && lease.status() == AmbientLeaseStatus.HOT && lease.goal() == AmbientGoalKind.OPERATION_ASSEMBLY) {
                BlockPosition target = member.arrived() ? member.currentPosition() : member.corridor().get(member.cursor() + 1);
                nextAmbient.put(actor, lease.withGoal(AmbientGoalKind.OPERATION_ASSEMBLY, target));
            }
        });
        return next(nextActors, structureConditions, infection, inventory, productionJobs, contracts, nextOperations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, nextAmbient);
    }
    public FrontierWorldState deferOperationAssembly(SubjectId operationId, OperationAssemblyDeferral deferral) {
        RouteOperation operation = operations.get(Objects.requireNonNull(operationId, "operation assembly operation id"));
        if (operation == null || operation.activeAssembly().isEmpty()) throw new IllegalArgumentException("operation has no active assembly");
        RouteOperation deferred = operation.withAssembly(operation.activeAssembly().orElseThrow().defer(Objects.requireNonNull(deferral, "assembly deferral")));
        Map<SubjectId, RouteOperation> nextOperations = new LinkedHashMap<>(operations); nextOperations.put(operation.id(), deferred);
        return next(actorLocations, structureConditions, infection, inventory, productionJobs, contracts, nextOperations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState completeOperationTravelSegment(SubjectId operationId) {
        RouteOperation operation = operations.get(Objects.requireNonNull(operationId, "operation travel operation id"));
        if (operation == null) throw new IllegalArgumentException("unknown operation travel");
        RouteOperation completed = operation.completeTravelSegment();
        Map<SubjectId, RouteOperation> nextOperations = new LinkedHashMap<>(operations); nextOperations.put(operation.id(), completed);
        return next(actorLocations, structureConditions, infection, inventory, productionJobs, contracts, nextOperations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState preparePhysicalIntent(PhysicalIntent intent) {
        Objects.requireNonNull(intent, "physical intent");
        if (physicalIntents.containsKey(intent.id())) throw new IllegalArgumentException("physical intent identity already exists: " + intent.id().value());
        SceneStrikeStateSupport.validateIntent(this, intent); ResourceSitePhysicalIntentStateSupport.validateIntent(this, intent);
        ProductionTransformationStateSupport.validateIntent(this, intent); CargoLoadingStateSupport.validateIntent(this, intent);
        if (HumanEquipmentStateSupport.owns(intent)) HumanEquipmentStateSupport.validateIntent(this, intent);
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
            if (current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.PRODUCTION_TRANSFORMATION
                    && nextStatus == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
                return ProductionTransformationStateSupport.unknown(this, current, next);
            }
            if ((current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.RESOURCE_SITE_PREPARATION
                    || current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.RESOURCE_SITE_HARVEST)
                    && nextStatus == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) return ResourceSitePhysicalIntentStateSupport.conflict(this, current, next);
            if ((current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_CONSTRUCTION
                    || current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING)
                    && nextStatus == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) return RouteConstructionStateSupport.conflict(this, current, next);
            if (HiveNutrientTransferStateSupport.isEndpointIntent(current) && nextStatus == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) return HiveNutrientTransferStateSupport.unknownEndpoint(this, current, next);
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
        if (current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.RESOURCE_SITE_PREPARATION) {
            if (!(evidence instanceof ResourceSitePreparationObservation preparation)) throw new IllegalArgumentException("resource-site preparation requires exact field evidence");
            return ResourceSitePhysicalIntentStateSupport.complete(this, current, preparation, next);
        }
        if (current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.RESOURCE_SITE_HARVEST) {
            if (!(evidence instanceof ResourceSiteHarvestObservation harvest)) throw new IllegalArgumentException("resource-site harvest requires exact field and output evidence");
            return ResourceSitePhysicalIntentStateSupport.completeHarvest(this, current, harvest, next);
        }
        if (current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.STRUCTURAL_REPAIR) {
            if (!(evidence instanceof StructuralRepairObservation repair)) throw new IllegalArgumentException("structural repair requires repair observation evidence");
            return StructuralRepairStateSupport.complete(this, current, repair, next);
        }
        if (current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_CONSTRUCTION) {
            if (!(evidence instanceof RouteConstructionObservation construction)) throw new IllegalArgumentException("route construction requires construction observation evidence");
            return RouteConstructionStateSupport.complete(this, current, construction, next);
        }
        if (current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING) {
            if (!(evidence instanceof RouteConstructionMaterialLoadObservation loading)) {
                throw new IllegalArgumentException("route construction material loading requires exact pickup evidence");
            }
            RouteConstructionStateSupport.validateMaterialLoadingReceipt(this, current, loading);
            next.put(intentId, current.withStatus(nextStatus, java.util.Optional.of(loading.id())));
            Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(physicalObservations); observations.put(loading.id(), loading);
            return next(actorLocations, structureConditions, infection, inventory, productionJobs, contracts, operations,
                    next, observations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
        }
        if (current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE) {
            if (!(evidence instanceof SceneStrikeObservation strike)) throw new IllegalArgumentException("scene strike requires exact hit evidence");
            SceneStrikeStateSupport.validateObservation(this, current, strike);
            next.put(intentId, current.withStatus(nextStatus, java.util.Optional.of(strike.id())));
            Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(physicalObservations); observations.put(strike.id(), strike);
            return next(actorLocations, structureConditions, infection, inventory, productionJobs, contracts, operations,
                    next, observations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
        }
        if (current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXPLOSION) {
            if (!(evidence instanceof ExplosionObservation explosion)) throw new IllegalArgumentException("explosion requires post-impact observation evidence");
            return ExplosionStateSupport.complete(this, current, explosion, next);
        }
        if (current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) {
            if (!(evidence instanceof ExactItemConsumedObservation consumed)) throw new IllegalArgumentException("exact consumption requires item observation evidence");
            SubjectId itemId = current.subjectIds().stream().filter(id -> !id.equals(current.causeSubjectId())).findFirst().orElseThrow();
            ExactItemStack item = inventory.items().get(itemId);
            if (!itemId.equals(consumed.itemId()) || item == null || item.count() != consumed.countBefore()
                    || !(item.custody() instanceof InventoryCustody.ContainerSlot slot)) throw new IllegalArgumentException("exact consumption receipt does not match current stack");
            ExactItemConsumptionStateSupport.Claim claim = ExactItemConsumptionStateSupport.claim(this, current);
            if (!claim.item().equals(item) || !claim.containerId().equals(slot.containerId()) || claim.slot() != slot.slot() || claim.count() != consumed.consumedCount()) {
                throw new IllegalArgumentException("exact consumption stack is not in an active owner container");
            }
            next.put(intentId, current.withStatus(nextStatus, java.util.Optional.of(consumed.id())));
            Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(physicalObservations); observations.put(consumed.id(), consumed);
            HiveColony consumedColony = hiveColony.growthJobs().containsKey(current.causeSubjectId())
                    ? hiveColony.consumeTransferredNutrient(current.causeSubjectId(), itemId) : hiveColony;
            FrontierWorldState consumedState = next(actorLocations, structureConditions, infection, inventory.consume(itemId, consumed.consumedCount()), productionJobs, contracts, operations,
                    next, observations, sceneLeases, consumedColony, structureDamage, physicalDeltas, ambientLeases);
            if (humanPopulation.provisions().values().stream().anyMatch(provision -> provision.activeIntentId().filter(current.id()::equals).isPresent())) {
                return SettlementProvisionStateSupport.reducePhysicalConsumptionAfterInventory(consumedState, current, consumed);
            }
            return consumedState;
        }
        if (current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.PRODUCTION_TRANSFORMATION) {
            if (!(evidence instanceof ProductionTransformationObservation production)) {
                throw new IllegalArgumentException("production transformation requires exact physical receipt");
            }
            return ProductionTransformationStateSupport.complete(this, current, production, next);
        }
        if (current.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.CARGO_LOADING) return CargoLoadingStateSupport.complete(this, current, evidence, next);
        if (HiveNutrientTransferStateSupport.isEndpointIntent(current)) return HiveNutrientTransferStateSupport.completeEndpoint(this, current, evidence, next);
        if (HumanEquipmentStateSupport.owns(current)) return HumanEquipmentStateSupport.complete(this, current, evidence, next);
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
    public FrontierWorldState prepareSceneLease(SceneLease lease) { return FrontierSceneLeaseStateSupport.prepare(this, lease); }
    /** Atomically records loaded-body evidence, closes ambient authority and prepares one scene. */ public FrontierWorldState handoffAmbientScene(SceneLeaseHandoff handoff) { return FrontierSceneLeaseStateSupport.handoff(this, handoff); }
    public FrontierWorldState transitionSceneLease(SceneLeaseId leaseId, SceneLeaseStatus nextStatus) { return FrontierSceneLeaseStateSupport.transition(this, Objects.requireNonNull(leaseId, "scene lease id"), nextStatus); }
    public FrontierWorldState releaseSceneLease(SceneLeaseId leaseId, java.util.List<SceneMemberPosition> positions) { return FrontierSceneLeaseStateSupport.release(this, Objects.requireNonNull(leaseId, "scene lease id"), positions); }
    public FrontierWorldState recordActorDeath(ActorDied death) {
        Objects.requireNonNull(death, "actor death");
        SceneLease lease = sceneLeases.get(death.leaseId());
        if (lease == null || (lease.status() != SceneLeaseStatus.HOT && lease.status() != SceneLeaseStatus.DRAINING)
                || lease.members().stream().noneMatch(member -> member.actorId().equals(death.actorId()))) {
            throw new IllegalArgumentException("actor death is not evidence for an active scene member");
        }
        ActorLocation current = actorLocations.get(death.actorId());
        if (current.condition().status() != ActorLifeStatus.ALIVE) throw new IllegalArgumentException("actor death is already recorded");
        FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), death.position());
        Map<SubjectId, ActorLocation> nextActors = new LinkedHashMap<>(actorLocations);
        nextActors.put(death.actorId(), current.deadAt(death.position()));
        return next(nextActors, structureConditions, infection, inventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState failOperation(SubjectId operationId) { return FrontierOperationStateSupport.fail(this, operationId); }
    public FrontierWorldState compactTerminalLogistics(SubjectId operationId, long terminalAtTick) {
        TerminalLogisticsReceipt receipt = terminalLogisticsReceipt(operationId, terminalAtTick);
        RouteOperation operation = operations.get(operationId);
        SupplyContract contract = contracts.get(receipt.contractId());
        Map<SubjectId, RouteOperation> nextOperations = new LinkedHashMap<>(operations); nextOperations.remove(operation.id());
        Map<SubjectId, SupplyContract> nextContracts = new LinkedHashMap<>(contracts); nextContracts.remove(contract.id());
        LogisticsHistory nextHistory = logisticsHistory.record(receipt);
        return withChanges(FrontierWorldStateUpdate.begin().contracts(nextContracts).operations(nextOperations).logisticsHistory(nextHistory));
    }
    public boolean canCompactTerminalLogistics(SubjectId operationId) {
        try { terminalLogisticsReceipt(operationId, 0L); return true; }
        catch (IllegalArgumentException notReady) { return false; }
    }
    private TerminalLogisticsReceipt terminalLogisticsReceipt(SubjectId operationId, long terminalAtTick) {
        RouteOperation operation = operations.get(Objects.requireNonNull(operationId, "terminal operation id"));
        if (operation == null) throw new IllegalArgumentException("terminal logistics operation is absent");
        SupplyContract contract = contracts.values().stream().filter(value -> value.cargoId().equals(operation.cargoId())).reduce((left, right) -> {
            throw new IllegalArgumentException("terminal logistics cargo has ambiguous contracts");
        }).orElseThrow(() -> new IllegalArgumentException("terminal logistics operation has no contract"));
        TerminalLogisticsReceipt.TerminalLogisticsOutcome outcome = terminalLogisticsOutcome(operation, contract);
        if (inventory.cargo().containsKey(operation.cargoId())) throw new IllegalArgumentException("terminal logistics cargo remains claimed");
        if (sceneLeases.values().stream().filter(FrontierSceneBehaviors::isLogistics).anyMatch(lease -> FrontierSceneBehaviors.logistics(lease).operationId().equals(operation.id()))
                || strategicPlans.routeEngagements().values().stream().anyMatch(engagement -> engagement.operationId().equals(operation.id()))
                || strategicPlans.tasks().values().stream().anyMatch(task -> task.operationTarget().equals(java.util.Optional.of(operation.id())))) {
            throw new IllegalArgumentException("terminal logistics operation retains a dependent scene or strategic claim");
        }
        if (physicalIntents.values().stream().anyMatch(intent -> intent.status() != PhysicalIntentStatus.CONFIRMED
                && (intent.causeSubjectId().equals(operation.id()) || intent.causeSubjectId().equals(contract.id())
                || intent.subjectIds().contains(operation.id()) || intent.subjectIds().contains(contract.id()) || intent.subjectIds().contains(operation.cargoId())))) {
            throw new IllegalArgumentException("terminal logistics operation retains an unresolved physical intent");
        }
        return new TerminalLogisticsReceipt(operation.id(), contract.id(), operation.cargoId(), operation.settlementId(), operation.destinationId(),
                operation.participantIds(), outcome, terminalAtTick);
    }
    private static TerminalLogisticsReceipt.TerminalLogisticsOutcome terminalLogisticsOutcome(RouteOperation operation, SupplyContract contract) {
        if (operation.stage() == OperationStage.COMPLETED && contract.status() == ContractStatus.DELIVERED) {
            return TerminalLogisticsReceipt.TerminalLogisticsOutcome.DELIVERED;
        }
        if (operation.stage() == OperationStage.INTERRUPTED && contract.status() == ContractStatus.INTERRUPTED) {
            return TerminalLogisticsReceipt.TerminalLogisticsOutcome.INTERRUPTED;
        }
        if (operation.stage() == OperationStage.FAILED && contract.status() == ContractStatus.INTERRUPTED) {
            return TerminalLogisticsReceipt.TerminalLogisticsOutcome.FAILED;
        }
        throw new IllegalArgumentException("operation and contract are not a compactable terminal pair");
    }
    /** Atomically exposes an exact shipment as its loaded-world carrier and interrupts its route. */
    public FrontierWorldState releaseCargoCarrier(CargoCarrierReleased released) { return CargoCarrierReleaseStateSupport.release(this, released); }
    public FrontierWorldState addHiveOrgan(HiveOrgan organ) {
        Objects.requireNonNull(organ, "hive organ");
        if (bootstrap.hive().organs().stream().anyMatch(existing -> existing.id().equals(organ.id()))) throw new IllegalArgumentException("added organ collides with bootstrap identity");
        if (organ.containerId().isPresent() && !inventory.containers().containsKey(organ.containerId().orElseThrow())) throw new IllegalArgumentException("added store organ needs an exact canonical container");
        return next(actorLocations, structureConditions, infection, inventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony.addOrgan(organ), structureDamage, physicalDeltas, ambientLeases);
    } public FrontierWorldState spawnBioform(Bioform bioform) {
        Objects.requireNonNull(bioform, "bioform"); if (actorLocations.containsKey(bioform.id())) throw new IllegalArgumentException("spawned bioform collides with actor identity");
        Map<SubjectId, ActorLocation> nextActors = new LinkedHashMap<>(actorLocations); nextActors.put(bioform.id(), new ActorLocation(bioform.position()));
        return next(nextActors, structureConditions, infection, inventory, productionJobs, contracts, operations,
                physicalIntents, physicalObservations, sceneLeases, hiveColony.spawn(bioform), structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState startHiveGrowth(HiveGrowthJob job) { return HiveGrowthStateSupport.start(this, job); }
    public FrontierWorldState completeHiveGrowth(SubjectId jobId) { return HiveGrowthStateSupport.complete(this, jobId); }
    public FrontierWorldState consumeHiveGrowthBiomass(SubjectId jobId, SubjectId itemId) { return HiveGrowthStateSupport.consume(this, jobId, itemId); }
    public FrontierWorldState cancelHiveGrowth(SubjectId jobId) { return HiveGrowthStateSupport.cancel(this, jobId); }
    public boolean isHiveStore(SubjectId containerId) { return HiveStorageSupport.isOperationalStore(this, containerId); }
    public static SubjectId depotId(SubjectId settlementId) {
        Objects.requireNonNull(settlementId, "settlement id"); if (!settlementId.value().startsWith("settlement:")) throw new IllegalArgumentException("settlement id must use settlement: namespace");
        return new SubjectId("container:" + settlementId.value().substring("settlement:".length()) + "-depot");
    }
}
