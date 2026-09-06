package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.model.*; import io.farfrontier.palemirror.frontier.v3.api.FixedRatio; import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec; import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import java.nio.ByteBuffer; import java.io.ByteArrayInputStream; import java.io.ByteArrayOutputStream; import java.io.DataInputStream;
import java.io.DataOutputStream; import java.io.IOException; import java.util.List;
/** Complete payload registry for the currently installed v3 world processes. */
public final class FrontierWorldPayloadCodecs { private FrontierWorldPayloadCodecs() { }
    public static PayloadCodecs create() {
        return FrontierWorldProcessCodecs.create();
    }

    static PayloadCodecs physicalCodecs() { return new PayloadCodecs(List.of(
            new PhysicalIntentPreparedCodec(), new PhysicalIntentTransitionCodec(), new StructureDamagedCodec(),
            PhysicalDeltaPayloadCodecs.single(), PhysicalDeltaPayloadCodecs.batch(), new ExactItemCustodyChangedCodec(), new ExactItemDestroyedCodec(),
            new InventoryConflictObservedCodec(), new ContainerSurfaceTransitionCodec(), new ResourceDepositedCodec(), new CargoCarrierReleasedPayloadCodec())); }
    static PayloadCodecs ambientCodecs() { return new PayloadCodecs(List.of(new AmbientActorDiedCodec(),
            new AmbientActorObservedCodec(), AmbientLeasePayloadCodecs.prepared(), AmbientLeasePayloadCodecs.transition(), AmbientLeasePayloadCodecs.released(),
            AmbientLeasePayloadCodecs.restartAbsenceObserved())); }
    static PayloadCodecs logisticsCodecs() { return PayloadCodecs.merge(new PayloadCodecs(List.of(
            new ContractCreatedCodec(), new ContractAbandonedCodec(), new CargoLoadedCodec(), new CargoDeliveredCodec(), new OperationCreatedCodec(),
            new OperationAdvancedCodec(), new OperationAssemblyAdvancedCodec(), new OperationAssemblyDeferredCodec(), new OperationTravelStartedCodec(),
            new OperationTravelAdvancedCodec(), new OperationTravelSegmentCompletedCodec(), new OperationColdSuspendedCodec(), new SceneLeasePreparedCodec(),
            new SceneLeaseHandoffCodec(), new SettlementAssaultSceneLeasePreparedCodec(), new SettlementAssaultSceneLeaseHandoffCodec(),
            new SceneLeaseTransitionCodec(), new SceneLeaseReleasedCodec(), new ActorDiedCodec(), new SceneRecoveryPayloadCodec(), new OperationFailedCodec(), new TerminalLogisticsCompactedCodec())),
            EngineeringWorkScenePayloadCodecs.codecs()); }
    static PayloadCodecs populationCodecs() { return PayloadCodecs.merge(new PayloadCodecs(List.of(
            HumanPopulationPayloadCodecs.born(), HumanPopulationPayloadCodecs.migrated(), HumanPopulationPayloadCodecs.birthStarted(), HumanPopulationPayloadCodecs.birthCancelled(),
            HumanPopulationPayloadCodecs.migrationStarted(), HumanPopulationPayloadCodecs.migrationAdvanced(), HumanPopulationPayloadCodecs.transitAdvanced(),
            HumanPopulationPayloadCodecs.migrationBlocked(), HumanPopulationPayloadCodecs.migrationResumed(), SettlementProvisionPayloadCodecs.legacyStarted(),
            SettlementProvisionPayloadCodecs.started(), SettlementProvisionPayloadCodecs.consumed(), SettlementProvisionPayloadCodecs.resolved(),
            HumanHealthPayloadCodecs.residentTransition(), HumanHealthPayloadCodecs.quarantineTransition(),
            MedicalTreatmentPayloadCodecs.started(), MedicalTreatmentPayloadCodecs.transition())),
            MedicalTreatmentScenePayloadCodecs.codecs()); }
    static PayloadCodecs economyCodecs() { return PayloadCodecs.merge(new PayloadCodecs(List.of(
            new ProductionStartedCodec(), new ProductionCompletedCodec(), new ProductionBlockedCodec(), ProductionInterruptionPayloadCodec.interrupted(),
            new CompanyRegisteredCodec(), new EmploymentContractOpenedCodec(), new EmploymentContractTerminatedCodec(), MarketPayloadCodecs.opened(),
            MarketPayloadCodecs.quote(), MarketPayloadCodecs.accepted(), MarketPayloadCodecs.workOrderCancelled(), MarketPayloadCodecs.expired(),
            MarketPayloadCodecs.cancelled())), ProductionWorkScenePayloadCodecs.codecs(), ProductionWorkScenePayloadCodecs.productionEvents()); }
    static PayloadCodecs resourceSiteCodecs() { return PayloadCodecs.merge(new PayloadCodecs(List.of(ResourceSitePayloadCodecs.growthAdvanced(),
            ResourceSitePayloadCodecs.preparationStarted(), ResourceSitePayloadCodecs.prepared(), ResourceSitePayloadCodecs.harvestStarted(),
            ResourceSitePayloadCodecs.harvestCropPrepared(), ResourceSitePayloadCodecs.harvestProgressed(),
            ResourceSitePayloadCodecs.harvestColdTraversalAdvanced(), ResourceSitePayloadCodecs.harvestHotTraversalAdvanced(),
            ResourceSitePayloadCodecs.conflictObserved())), ResourceSiteHarvestScenePayloadCodecs.codecs()); }
    static PayloadCodecs hiveCodecs() { return PayloadCodecs.merge(RouteEngagementPayloadCodecs.codecs(), SettlementAssaultPayloadCodecs.codecs(), HiveMobilizationPayloadCodecs.codecs(), new PayloadCodecs(List.of(new InfectionCodec(),
            new HiveGrowthStartedCodec(), new HiveGrowthBiomassConsumedCodec(), new HiveGrowthCompletedCodec(), new HiveGrowthBlockedCodec(),
            new HiveNutrientTransferStartedCodec(), new HiveNutrientTransferAdvancedCodec(), new HiveNutrientTransferCompletedCodec(),
            new HiveNutrientTransferBlockedCodec(), new HiveNutrientTransferEndpointPreparedCodec(), StrategicPlanPayloadCodecs.hiveOperationObserved(),
            StrategicPlanPayloadCodecs.hiveTerritoryObserved(), StrategicPlanPayloadCodecs.hiveSettlementObserved(),
            StrategicPlanPayloadCodecs.hiveDoctrineSelected(), StrategicPlanPayloadCodecs.hotScoutOperationObserved(), StrategicPlanPayloadCodecs.scoutPatrolAdvanced(),
            StrategicPlanPayloadCodecs.scoutPatrolLeaseRecovered()))); }
    static PayloadCodecs infrastructureCodecs() { return new PayloadCodecs(List.of(RouteConstructionPayloadCodecs.started(),
            RouteConstructionPayloadCodecs.cutover(), RouteConstructionPayloadCodecs.materialLoaded(), RouteConstructionPayloadCodecs.assemblyStarted(),
            RouteConstructionPayloadCodecs.assemblyAdvanced(), RouteMaintenancePayloadCodecs.started(), RouteMaintenancePayloadCodecs.materialLoaded(),
            RouteMaintenancePayloadCodecs.assemblyStarted(), RouteMaintenancePayloadCodecs.assemblyAdvanced(), RouteMaintenancePayloadCodecs.closed(), RoutePatrolPayloadCodecs.started(),
            RoutePatrolPayloadCodecs.advanced(), RoutePatrolPayloadCodecs.obstruction(), RoutePatrolPayloadCodecs.failed(), RoutePatrolPayloadCodecs.blocked(),
            RoutePatrolPayloadCodecs.prepared(), RoutePatrolPayloadCodecs.handoff(), RoutePatrolPayloadCodecs.traversalObserved())); }
    static PayloadCodecs settlementServiceWorkCodecs() { return SettlementServiceWorkPayloadCodecs.codecs(); }
    static PayloadCodecs strategyCodecs() { return new PayloadCodecs(List.of(StrategicPlanPayloadCodecs.selected(),
            StrategicPlanPayloadCodecs.taskPlanned(), StrategicPlanPayloadCodecs.transition(), StrategicPlanPayloadCodecs.infectionObserved())); }
    private static final class InfectionCodec implements PayloadCodec {
        @Override public String type() { return "frontier.infection_changed"; } @Override public byte[] encode(FrontierPayload payload) {
            InfectionChanged changed = (InfectionChanged) payload;
            return ByteBuffer.allocate(16).putInt(changed.cell().x()).putInt(changed.cell().z()).putLong(changed.intensity().value().raw()).array();
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            if (bytes.length != 16) throw new IllegalArgumentException("malformed infection change payload");
            ByteBuffer input = ByteBuffer.wrap(bytes);
            return new InfectionChanged(new InfectionCell(input.getInt(), input.getInt()), new FixedRatio(new FixedScalar(input.getLong())));
        }
    } private static final class CompanyRegisteredCodec implements PayloadCodec {
        @Override public String type() { return "frontier.company_registered"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            Company company = ((CompanyRegistered) payload).company(); writeSubject(output, company.id()); writeSubject(output, company.settlementId());
            writeSubject(output, company.founderId()); output.writeByte(company.purpose().wireTag()); output.writeByte(company.status().wireTag()); output.writeLong(company.registeredAtTick());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectId id = readSubject(input).value(); SubjectId settlement = readSubject(input).value(); SubjectId founder = readSubject(input).value();
            int purpose = input.readUnsignedByte(); int status = input.readUnsignedByte(); long registeredAt = input.readLong();
            if (purpose >= CompanyPurpose.values().length || status >= CompanyStatus.values().length) throw new IllegalArgumentException("invalid company registration payload");
            return new CompanyRegistered(new Company(id, settlement, founder, FrontierWireTags.require(CompanyPurpose.class, purpose), FrontierWireTags.require(CompanyStatus.class, status), registeredAt));
        }); }
    } private static final class EmploymentContractOpenedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.employment_contract_opened"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            EmploymentContract contract = ((EmploymentContractOpened) payload).contract(); writeSubject(output, contract.id()); writeSubject(output, contract.companyId());
            writeSubject(output, contract.residentId()); output.writeLong(contract.invoicePerCompletedJob().raw()); output.writeLong(contract.wagePerCompletedJob().raw());
            output.writeByte(contract.status().wireTag()); output.writeLong(contract.openedAtTick()); output.writeLong(contract.completedJobs()); output.writeLong(contract.totalWagesPaid().raw());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectId id = readSubject(input).value(); SubjectId company = readSubject(input).value(); SubjectId resident = readSubject(input).value();
            long invoice = input.readLong(); long wage = input.readLong(); int status = input.readUnsignedByte(); long openedAt = input.readLong();
            long completed = input.readLong(); long totalWages = input.readLong();
            if (status >= EmploymentContractStatus.values().length) throw new IllegalArgumentException("invalid employment contract status");
            return new EmploymentContractOpened(new EmploymentContract(id, company, resident, new FixedScalar(invoice), new FixedScalar(wage),
                    FrontierWireTags.require(EmploymentContractStatus.class, status), openedAt, completed, new FixedScalar(totalWages)));
        }); }
    } private static final class EmploymentContractTerminatedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.employment_contract_terminated"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            EmploymentContractTerminated terminated = (EmploymentContractTerminated) payload;
            writeSubject(output, terminated.contractId()); writeSubject(output, terminated.residentId()); output.writeByte(terminated.reason().wireTag());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectId contract = readSubject(input).value(); SubjectId resident = readSubject(input).value(); int reason = input.readUnsignedByte();
            if (reason >= EmploymentTerminationReason.values().length) throw new IllegalArgumentException("invalid employment termination reason");
            return new EmploymentContractTerminated(contract, resident, FrontierWireTags.require(EmploymentTerminationReason.class, reason));
        }); }
    } private static final class ResourceDepositedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.resource_deposited"; } @Override public byte[] encode(FrontierPayload payload) {
            ResourceDeposited deposited = (ResourceDeposited) payload;
            return encodeProduction(output -> {
                ExactItemStack item = deposited.item();
                if (!(item.custody() instanceof InventoryCustody.ContainerSlot slot)) {
                    throw new IllegalArgumentException("resource deposit must have container custody");
                }
                writeSubject(output, item.id()); writeSubject(output, item.economicOwnerId()); writeString(output, item.itemKind()); output.writeByte(item.count());
                writeSubject(output, slot.containerId()); output.writeByte(slot.slot());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return decodeProduction(bytes, input -> {
                SubjectIdHolder item = readSubject(input); SubjectIdHolder owner = readSubject(input); String kind = readString(input); int count = input.readUnsignedByte();
                SubjectIdHolder container = readSubject(input); int slot = input.readUnsignedByte();
                return new ResourceDeposited(new ExactItemStack(item.value(), owner.value(), kind, count,
                        new InventoryCustody.ContainerSlot(container.value(), slot)));
            });
        }
    } private static final class ProductionStartedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.production_started"; } @Override public byte[] encode(FrontierPayload payload) {
            ProductionStarted started = (ProductionStarted) payload;
            return encodeProduction(output -> { writeJob(output, started.job()); writeSubject(output, started.inputItemId()); writeProductionInputHold(output, started.job().inputHold()); });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return decodeProduction(bytes, input -> {
                ProductionJob job = readJob(input); SubjectIdHolder item = readSubject(input);
                if (input.available() != 0) job = job.withInputHold(readProductionInputHold(input, job.consumedItemId()));
                return new ProductionStarted(job, item.value());
            });
        }
    } private static final class ProductionCompletedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.production_completed"; } @Override public byte[] encode(FrontierPayload payload) {
            ProductionCompleted completed = (ProductionCompleted) payload;
            return encodeProduction(output -> {
                writeSubject(output, completed.jobId()); writeSubject(output, completed.output().id()); writeSubject(output, completed.output().economicOwnerId()); writeString(output, completed.output().itemKind());
                output.writeByte(completed.output().count());
                if (!(completed.output().custody() instanceof InventoryCustody.ContainerSlot slot)) throw new IllegalArgumentException("production output must have container custody");
                writeSubject(output, slot.containerId()); output.writeByte(slot.slot());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return decodeProduction(bytes, input -> {
                SubjectIdHolder job = readSubject(input); SubjectIdHolder output = readSubject(input); SubjectIdHolder owner = readSubject(input); String kind = readString(input); int count = input.readUnsignedByte();
                SubjectIdHolder container = readSubject(input); int slot = input.readUnsignedByte();
                return new ProductionCompleted(job.value(), new ExactItemStack(output.value(), owner.value(), kind, count, new InventoryCustody.ContainerSlot(container.value(), slot)));
            });
        }
    } private static final class ProductionBlockedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.production_blocked"; } @Override public byte[] encode(FrontierPayload payload) {
            ProductionBlocked blocked = (ProductionBlocked) payload;
            return encodeProduction(output -> { writeSubject(output, blocked.settlementId()); writeSubject(output, blocked.facilityId()); writeSubject(output, blocked.workId()); output.writeByte(blocked.reason().wireTag()); });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return decodeProduction(bytes, input -> {
                SubjectIdHolder settlement = readSubject(input); SubjectIdHolder facility = readSubject(input); SubjectIdHolder work = readSubject(input);
                int ordinal = input.readUnsignedByte();
                if (ordinal >= ProductionBlockReason.values().length) throw new IllegalArgumentException("unknown production block reason");
                return new ProductionBlocked(settlement.value(), facility.value(), work.value(), FrontierWireTags.require(ProductionBlockReason.class, ordinal));
            });
        }
    }
    private static final class ContractCreatedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.supply_contract_created"; } @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writeContract(output, ((SupplyContractCreated) payload).contract())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new SupplyContractCreated(readContract(input))); }
    }
    private static final class ContractAbandonedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.supply_contract_abandoned"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writeSubject(output, ((SupplyContractAbandoned) payload).contractId())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new SupplyContractAbandoned(readSubject(input).value())); }
    }
    private static final class CargoLoadedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.cargo_loaded"; } @Override public byte[] encode(FrontierPayload payload) {
            CargoLoaded loaded = (CargoLoaded) payload;
            return encodeProduction(output -> {
                writeSubject(output, loaded.contractId()); writeSubject(output, loaded.cargo().id()); writeSubject(output, loaded.cargo().ownerId());
                output.writeByte(loaded.cargo().itemIds().size());
                for (var item : loaded.cargo().itemIds()) writeSubject(output, item);
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectIdHolder contract = readSubject(input); SubjectIdHolder cargo = readSubject(input); SubjectIdHolder owner = readSubject(input); int count = input.readUnsignedByte();
            java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.api.SubjectId> items = new java.util.ArrayList<>(); for (int index = 0; index < count; index++) items.add(readSubject(input).value());
            return new CargoLoaded(contract.value(), new CargoBatch(cargo.value(), owner.value(), items));
        }); }
    }
    private static final class CargoDeliveredCodec implements PayloadCodec {
        @Override public String type() { return "frontier.cargo_delivered"; }
        @Override public byte[] encode(FrontierPayload payload) {
            CargoDelivered delivered = (CargoDelivered) payload;
            return encodeProduction(output -> {
                writeSubject(output, delivered.operationId()); writeSubject(output, delivered.cargoId()); output.writeByte(delivered.placements().size());
                for (CargoHandoffPlacement placement : delivered.placements()) {
                    writeSubject(output, placement.itemId()); writeSubject(output, placement.receiverSlot().containerId()); output.writeByte(placement.receiverSlot().slot());
                }
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectIdHolder operation = readSubject(input); SubjectIdHolder cargo = readSubject(input); int count = input.readUnsignedByte();
            java.util.ArrayList<CargoHandoffPlacement> placements = new java.util.ArrayList<>();
            for (int index = 0; index < count; index++) {
                SubjectIdHolder item = readSubject(input); SubjectIdHolder receiver = readSubject(input);
                placements.add(new CargoHandoffPlacement(item.value(), new InventoryCustody.ContainerSlot(receiver.value(), input.readUnsignedByte())));
            }
            return new CargoDelivered(operation.value(), cargo.value(), placements);
        }); }
    }
    private static final class OperationCreatedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.operation_created"; } @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writeOperation(output, ((OperationCreated) payload).operation())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new OperationCreated(readOperation(input))); }
    }
    private static final class OperationAdvancedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.operation_advanced"; } @Override public byte[] encode(FrontierPayload payload) {
            OperationAdvanced advanced = (OperationAdvanced) payload;
            return encodeProduction(output -> { writeSubject(output, advanced.operationId()); output.writeByte(advanced.routeIndex()); output.writeByte(advanced.stage().wireCode()); });
        }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectIdHolder id = readSubject(input); int routeIndex = input.readUnsignedByte(); int stage = input.readUnsignedByte();
            return new OperationAdvanced(id.value(), routeIndex, OperationStage.fromWireCode(stage));
        }); }
    }
    private static final class OperationAssemblyAdvancedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.operation_assembly_advanced"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> { OperationAssemblyAdvanced advanced = (OperationAssemblyAdvanced) payload;
            writeSubject(output, advanced.operationId()); writeOperationAssembly(output, advanced.assembly()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes,
                input -> new OperationAssemblyAdvanced(readSubject(input).value(), readOperationAssembly(input))); }
    }
    private static final class OperationAssemblyDeferredCodec implements PayloadCodec {
        @Override public String type() { return "frontier.operation_assembly_deferred"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> { OperationAssemblyDeferred deferred = (OperationAssemblyDeferred) payload;
            writeSubject(output, deferred.operationId()); writeSubject(output, deferred.deferral().actorId());
            output.writeInt(deferred.deferral().target().x()); output.writeInt(deferred.deferral().target().y()); output.writeInt(deferred.deferral().target().z());
            output.writeInt(deferred.deferral().obstructionSurface().x()); output.writeInt(deferred.deferral().obstructionSurface().y()); output.writeInt(deferred.deferral().obstructionSurface().z());
            output.writeByte(deferred.deferral().reason().wireTag()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectIdHolder operation = readSubject(input); SubjectIdHolder actor = readSubject(input);
            BlockPosition target = new BlockPosition(input.readInt(), input.readInt(), input.readInt());
            BlockPosition obstruction = new BlockPosition(input.readInt(), input.readInt(), input.readInt()); int reason = input.readUnsignedByte();
            if (reason >= OperationAssemblyDeferral.Reason.values().length) throw new IllegalArgumentException("unknown operation assembly deferral reason");
            return new OperationAssemblyDeferred(operation.value(), new OperationAssemblyDeferral(actor.value(), new SurfaceAnchor(target), new SurfaceAnchor(obstruction),
                    FrontierWireTags.require(OperationAssemblyDeferral.Reason.class, reason)));
        }); }
    }
    private static final class OperationTravelStartedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.operation_travel_started"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> { OperationTravelStarted started = (OperationTravelStarted) payload;
            writeSubject(output, started.operationId()); writeOperationTravel(output, started.travel()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input ->
                new OperationTravelStarted(readSubject(input).value(), readOperationTravel(input))); }
    }
    private static final class OperationTravelAdvancedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.operation_travel_advanced"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> { OperationTravelAdvanced advanced = (OperationTravelAdvanced) payload;
            writeSubject(output, advanced.operationId()); writeOperationTravel(output, advanced.travel()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input ->
                new OperationTravelAdvanced(readSubject(input).value(), readOperationTravel(input))); }
    }
    private static final class OperationTravelSegmentCompletedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.operation_travel_segment_completed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writeSubject(output, ((OperationTravelSegmentCompleted) payload).operationId())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new OperationTravelSegmentCompleted(readSubject(input).value())); }
    }
    private static final class OperationColdSuspendedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.operation_cold_suspended"; } @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> { OperationColdSuspended suspended = (OperationColdSuspended) payload;
            writeSubject(output, suspended.operationId()); writeString(output, suspended.leaseId().value()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input ->
                new OperationColdSuspended(readSubject(input).value(), new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(readString(input)))); }
    }
    private static final class PhysicalIntentPreparedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.physical_intent_prepared"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> PhysicalIntentPayloadCodec.write(output, ((PhysicalIntentPrepared) payload).intent())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new PhysicalIntentPrepared(PhysicalIntentPayloadCodec.read(input))); }
    }
    private static final class PhysicalIntentTransitionCodec implements PayloadCodec {
        @Override public String type() { return "frontier.physical_intent_transition"; } @Override public byte[] encode(FrontierPayload payload) {
            PhysicalIntentTransition transition = (PhysicalIntentTransition) payload;
            return encodeProduction(output -> { writeString(output, transition.intentId().value()); output.writeByte(transition.status().wireTag());
                output.writeBoolean(transition.observation().isPresent());
                if (transition.observation().isPresent()) PhysicalEffectObservationPayloadCodec.write(output, transition.observation().orElseThrow()); });
        }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            var id = new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(readString(input)); int status = input.readUnsignedByte(); boolean observed = input.readBoolean();
            var observation = observed ? java.util.Optional.of(PhysicalEffectObservationPayloadCodec.read(input)) : java.util.Optional.<PhysicalEffectObservation>empty();
            if (status >= io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.values().length) throw new IllegalArgumentException("unknown physical intent status");
            return new PhysicalIntentTransition(id, FrontierWireTags.require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.class, status), observation);
        }); }
    }
    private static final class SceneLeasePreparedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.scene_lease_prepared"; } @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writeSceneLease(output, ((SceneLeasePrepared) payload).lease())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new SceneLeasePrepared(readSceneLease(input))); }
    }
    private static final class SceneLeaseHandoffCodec implements PayloadCodec {
        @Override public String type() { return "frontier.scene_lease_handoff"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            SceneLeaseHandoff handoff = (SceneLeaseHandoff) payload; writeSceneLease(output, handoff.lease()); output.writeByte(handoff.ambientMembers().size());
            for (SceneMemberPosition member : handoff.ambientMembers()) {
                writeSubject(output, member.actorId()); output.writeInt(member.body().x()); output.writeInt(member.body().y()); output.writeInt(member.body().z()); output.writeLong(member.health().raw());
            }
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SceneLease lease = readSceneLease(input); java.util.ArrayList<SceneMemberPosition> members = new java.util.ArrayList<>();
            for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
                members.add(new SceneMemberPosition(readSubject(input).value(), new BodyPosition(input.readInt(), input.readInt(), input.readInt()),
                        new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong())));
            }
            return new SceneLeaseHandoff(lease, members);
        }); }
    }
    private static final class SettlementAssaultSceneLeasePreparedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.settlement_assault_scene_lease_prepared"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writeAssaultSceneLease(output, ((SettlementAssaultSceneLeasePrepared) payload).lease())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes,
                input -> new SettlementAssaultSceneLeasePrepared(readAssaultSceneLease(input))); }
    }
    private static final class SettlementAssaultSceneLeaseHandoffCodec implements PayloadCodec {
        @Override public String type() { return "frontier.settlement_assault_scene_lease_handoff"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            SettlementAssaultSceneLeaseHandoff handoff = (SettlementAssaultSceneLeaseHandoff) payload;
            writeAssaultSceneLease(output, handoff.lease()); writeMemberPositions(output, handoff.ambientMembers());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes,
                input -> new SettlementAssaultSceneLeaseHandoff(readAssaultSceneLease(input), readMemberPositions(input))); }
    }
    private static final class SceneLeaseTransitionCodec implements PayloadCodec {
        @Override public String type() { return "frontier.scene_lease_transition"; } @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> { SceneLeaseTransition transition = (SceneLeaseTransition) payload;
            writeString(output, transition.leaseId().value()); output.writeByte(transition.status().wireTag()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            var id = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(readString(input)); int status = input.readUnsignedByte();
            if (status >= SceneLeaseStatus.values().length) throw new IllegalArgumentException("unknown scene lease status");
            return new SceneLeaseTransition(id, FrontierWireTags.require(SceneLeaseStatus.class, status));
        }); }
    }
    private static final class SceneLeaseReleasedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.scene_lease_released_v2"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            SceneLeaseReleased released = (SceneLeaseReleased) payload; writeString(output, released.leaseId().value()); output.writeByte(released.members().size());
            for (SceneMemberPosition member : released.members()) {
                writeSubject(output, member.actorId());
                output.writeInt(member.body().x()); output.writeInt(member.body().y()); output.writeInt(member.body().z());
                output.writeLong(member.health().raw());
            }
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            var id = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(readString(input)); java.util.ArrayList<SceneMemberPosition> members = new java.util.ArrayList<>();
            for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
                var actor = readSubject(input).value();
                BodyPosition body = new BodyPosition(input.readInt(), input.readInt(), input.readInt());
                members.add(new SceneMemberPosition(actor, body, new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong())));
            }
            return new SceneLeaseReleased(id, members);
        }); }
    }
    private static final class ActorDiedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.actor_died"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            ActorDied death = (ActorDied) payload; writeString(output, death.leaseId().value()); writeSubject(output, death.actorId());
            output.writeInt(death.body().x()); output.writeInt(death.body().y()); output.writeInt(death.body().z()); writeString(output, death.cause());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new ActorDied(
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(readString(input)), readSubject(input).value(),
                new BodyPosition(input.readInt(), input.readInt(), input.readInt()), readString(input))); }
    }
    private static final class AmbientActorDiedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.ambient_actor_died"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            AmbientActorDied death = (AmbientActorDied) payload; writeSubject(output, death.actorId());
            output.writeInt(death.body().x()); output.writeInt(death.body().y()); output.writeInt(death.body().z()); writeString(output, death.cause());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new AmbientActorDied(
                readSubject(input).value(), new BodyPosition(input.readInt(), input.readInt(), input.readInt()), readString(input))); }
    }
    private static final class AmbientActorObservedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.ambient_actor_observed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            AmbientActorObserved observation = (AmbientActorObserved) payload; writeSubject(output, observation.actorId());
            output.writeInt(observation.body().x()); output.writeInt(observation.body().y()); output.writeInt(observation.body().z()); output.writeLong(observation.health().raw());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new AmbientActorObserved(
                readSubject(input).value(), new BodyPosition(input.readInt(), input.readInt(), input.readInt()), new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong()))); }
    }
    private static final class StructureDamagedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.structure_damaged"; }
        @Override public byte[] encode(FrontierPayload payload) {
            StructureDamaged damage = (StructureDamaged) payload;
            return encodeProduction(output -> {
                writeSubject(output, damage.structureId()); writePosition(output, damage.position());
                output.writeByte(damage.semanticPart().wireTag()); writeString(output, damage.cause());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return decodeProduction(bytes, input -> {
                SubjectIdHolder structure = readSubject(input); BlockPosition position = readPosition(input); int part = input.readUnsignedByte();
                if (part >= GrayboxSemanticPart.values().length) throw new IllegalArgumentException("unknown structure damage semantic part");
                return new StructureDamaged(structure.value(), position, FrontierWireTags.require(GrayboxSemanticPart.class, part), readString(input));
            });
        }
    }
    private static final class OperationFailedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.operation_failed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> { OperationFailed failed = (OperationFailed) payload; writeSubject(output, failed.operationId()); writeString(output, failed.reason()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new OperationFailed(readSubject(input).value(), readString(input))); }
    }
    private static final class TerminalLogisticsCompactedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.terminal_logistics_compacted"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writeSubject(output, ((TerminalLogisticsCompacted) payload).operationId())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new TerminalLogisticsCompacted(readSubject(input).value())); }
    }
    private static final class ExactItemCustodyChangedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.exact_item_custody_changed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            ExactItemCustodyChanged changed = (ExactItemCustodyChanged) payload;
            writeSubject(output, changed.itemId()); writeCustody(output, changed.from()); writeCustody(output, changed.to());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new ExactItemCustodyChanged(
                readSubject(input).value(), readCustody(input), readCustody(input))); }
    }
    private static final class ExactItemDestroyedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.exact_item_destroyed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            ExactItemDestroyed destroyed = (ExactItemDestroyed) payload;
            writeSubject(output, destroyed.itemId()); writeCustody(output, destroyed.source()); writeString(output, destroyed.cause());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new ExactItemDestroyed(
                readSubject(input).value(), readCustody(input), readString(input))); }
    }
    private static final class InventoryConflictObservedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.inventory_conflict_observed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            InventoryConflict conflict = ((InventoryConflictObserved) payload).conflict();
            writeSubject(output, conflict.id()); writeSubject(output, conflict.subjectId()); writeSubject(output, conflict.containerId());
            output.writeByte(conflict.slot()); output.writeByte(conflict.kind().wireTag());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            var id = readSubject(input).value(); var item = readSubject(input).value(); var container = readSubject(input).value();
            int slot = input.readUnsignedByte(); int kind = input.readUnsignedByte();
            if (kind >= InventoryConflictKind.values().length) throw new IllegalArgumentException("unknown inventory conflict kind");
            return new InventoryConflictObserved(new InventoryConflict(id, item, container, slot, FrontierWireTags.require(InventoryConflictKind.class, kind)));
        }); }
    }
    private static final class ContainerSurfaceTransitionCodec implements PayloadCodec {
        @Override public String type() { return "frontier.container_surface_transition"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            ContainerSurfaceTransition transition = (ContainerSurfaceTransition) payload;
            writeSubject(output, transition.containerId()); output.writeByte(transition.status().wireTag());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            var container = readSubject(input).value(); int status = input.readUnsignedByte();
            if (status >= ContainerSurfaceStatus.values().length) throw new IllegalArgumentException("unknown container surface status");
            return new ContainerSurfaceTransition(container, FrontierWireTags.require(ContainerSurfaceStatus.class, status));
        }); }
    }
    private static final class HiveGrowthStartedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_growth_started"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writeHiveGrowthJob(output, ((HiveGrowthStarted) payload).job())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new HiveGrowthStarted(readHiveGrowthJob(input))); }
    }
    private static final class HiveGrowthBiomassConsumedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_growth_biomass_consumed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            HiveGrowthBiomassConsumed consumed = (HiveGrowthBiomassConsumed) payload;
            writeSubject(output, consumed.jobId()); writeSubject(output, consumed.itemId());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectIdHolder job = readSubject(input); SubjectIdHolder item = readSubject(input);
            return new HiveGrowthBiomassConsumed(job.value(), item.value());
        }); }
    }
    private static final class HiveGrowthCompletedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_growth_completed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writeSubject(output, ((HiveGrowthCompleted) payload).jobId())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new HiveGrowthCompleted(readSubject(input).value())); }
    } private static final class HiveGrowthBlockedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_growth_blocked"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> { HiveGrowthBlocked blocked = (HiveGrowthBlocked) payload;
            writeSubject(output, blocked.hiveId()); writeSubject(output, blocked.nestId()); writeSubject(output, blocked.workId()); output.writeByte(blocked.reason().wireTag()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectIdHolder hive = readSubject(input); SubjectIdHolder nest = readSubject(input); SubjectIdHolder work = readSubject(input); int reason = input.readUnsignedByte();
            if (reason >= HiveGrowthBlockReason.values().length) throw new IllegalArgumentException("unknown hive growth block reason");
            return new HiveGrowthBlocked(hive.value(), nest.value(), work.value(), FrontierWireTags.require(HiveGrowthBlockReason.class, reason));
        }); }
    } private static final class HiveNutrientTransferStartedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_nutrient_transfer_started"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writeHiveNutrientTransfer(output, ((HiveNutrientTransferStarted) payload).transfer())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new HiveNutrientTransferStarted(readHiveNutrientTransfer(input))); }
    } private static final class HiveNutrientTransferAdvancedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_nutrient_transfer_advanced"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> { HiveNutrientTransferAdvanced advanced = (HiveNutrientTransferAdvanced) payload;
            writeSubject(output, advanced.transferId()); output.writeShort(advanced.cursor()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new HiveNutrientTransferAdvanced(readSubject(input).value(), input.readUnsignedShort())); }
    } private static final class HiveNutrientTransferCompletedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_nutrient_transfer_completed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writeHiveNutrientReceipt(output, ((HiveNutrientTransferCompleted) payload).receipt())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new HiveNutrientTransferCompleted(readHiveNutrientReceipt(input))); }
    } private static final class HiveNutrientTransferBlockedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_nutrient_transfer_blocked"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> { HiveNutrientTransferBlocked blocked = (HiveNutrientTransferBlocked) payload;
            writeSubject(output, blocked.transferId()); output.writeByte(blocked.reason().wireTag()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> { SubjectId transfer = readSubject(input).value(); int reason = input.readUnsignedByte();
            if (reason >= HiveNutrientTransferBlockReason.values().length) throw new IllegalArgumentException("unknown hive nutrient transfer block reason");
            return new HiveNutrientTransferBlocked(transfer, FrontierWireTags.require(HiveNutrientTransferBlockReason.class, reason)); }); }
    } private static final class HiveNutrientTransferEndpointPreparedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_nutrient_transfer_endpoint_prepared"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writeHiveNutrientTransfer(output, ((HiveNutrientTransferEndpointPrepared) payload).transfer())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new HiveNutrientTransferEndpointPrepared(readHiveNutrientTransfer(input))); }
    } @FunctionalInterface public interface ProductionEncoder { void write(DataOutputStream output) throws IOException; } @FunctionalInterface interface ProductionDecoder { FrontierPayload read(DataInputStream input) throws IOException; }
    record SubjectIdHolder(io.farfrontier.palemirror.frontier.v3.api.SubjectId value) { } public static byte[] encodeProduction(ProductionEncoder encoder) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) { encoder.write(output); }
            return bytes.toByteArray();
        } catch (IOException error) { throw new IllegalStateException("in-memory production payload encoding failed", error); }
    }
    static FrontierPayload decodeProduction(byte[] bytes, ProductionDecoder decoder) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            FrontierPayload payload = decoder.read(input);
            if (input.available() != 0) throw new IllegalArgumentException("trailing production payload bytes");
            return payload;
        } catch (IOException error) { throw new IllegalArgumentException("truncated production payload", error); }
    }
    private static void writeJob(DataOutputStream output, ProductionJob job) throws IOException {
        writeSubject(output, job.id()); writeSubject(output, job.settlementId()); writeSubject(output, job.facilityId()); writeSubject(output, job.workerId());
        writeSubject(output, job.consumedItemId()); writeSubject(output, job.outputItemId()); writeString(output, job.outputItemKind()); output.writeByte(job.outputCount());
        ProductionWorkProgressStateCodec.write(output, job.workProgress()); TraversalTopologyStateCodec.write(output, job.workTraversal()); output.writeShort(job.traversalCursor());
    }
    private static ProductionJob readJob(DataInputStream input) throws IOException {
        SubjectId id = readSubject(input).value(); SubjectId settlement = readSubject(input).value(); SubjectId facility = readSubject(input).value();
        SubjectId worker = readSubject(input).value(); SubjectId consumed = readSubject(input).value(); SubjectId output = readSubject(input).value();
        String outputKind = readString(input); int outputCount = input.readUnsignedByte();
        ProductionWorkProgress progress = ProductionWorkProgressStateCodec.read(input); TraversalTopology traversal = TraversalTopologyStateCodec.read(input);
        return new ProductionJob(id, settlement, facility, worker, consumed, new ProductionInputHold.Materialized(consumed), output, outputKind, outputCount,
                progress, traversal, input.readUnsignedShort());
    }
    private static void writeProductionInputHold(DataOutputStream output, ProductionInputHold hold) throws IOException {
        if (hold instanceof ProductionInputHold.Materialized) { output.writeByte(0); return; }
        ExactItemStack item = ((ProductionInputHold.Cold) hold).item();
        output.writeByte(1); writeSubject(output, item.economicOwnerId()); writeString(output, item.itemKind()); output.writeByte(item.count());
        if (!(item.custody() instanceof InventoryCustody.ContainerSlot slot)) throw new IllegalArgumentException("cold production input must retain its depot slot");
        writeSubject(output, slot.containerId()); output.writeByte(slot.slot());
    }
    private static ProductionInputHold readProductionInputHold(DataInputStream input, SubjectId itemId) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> new ProductionInputHold.Materialized(itemId);
            case 1 -> new ProductionInputHold.Cold(new ExactItemStack(itemId, readSubject(input).value(), readString(input), input.readUnsignedByte(),
                    new InventoryCustody.ContainerSlot(readSubject(input).value(), input.readUnsignedByte())));
            default -> throw new IllegalArgumentException("unknown production input hold");
        };
    }
    private static void writeHiveGrowthJob(DataOutputStream output, HiveGrowthJob job) throws IOException {
        writeSubject(output, job.id()); writeSubject(output, job.hiveId()); writeSubject(output, job.nestId()); writeSubject(output, job.consumedItemId()); writeString(output, job.consumptionIntentId().value());
        writeSubject(output, job.organ().id()); output.writeByte(job.organ().kind().wireTag()); writePosition(output, job.organ().anchor());
        writeSubject(output, job.bioform().id()); BioformProfileStateCodec.write(output, job.bioform());
    }
    private static HiveGrowthJob readHiveGrowthJob(DataInputStream input) throws IOException {
        SubjectIdHolder id = readSubject(input); SubjectIdHolder hive = readSubject(input); SubjectIdHolder nest = readSubject(input); SubjectIdHolder item = readSubject(input);
        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId consumption = new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(readString(input));
        SubjectIdHolder organId = readSubject(input); int kind = input.readUnsignedByte(); BlockPosition anchor = readPosition(input);
        SubjectIdHolder bioformId = readSubject(input); Bioform bioform = BioformProfileStateCodec.read(input, bioformId.value(), hive.value(), nest.value());
        return new HiveGrowthJob(id.value(), hive.value(), nest.value(), item.value(), consumption, new HiveOrgan(organId.value(), hive.value(), nest.value(), FrontierWireTags.require(HiveOrganKind.class, kind), anchor, java.util.Optional.empty()),
                bioform);
    }
    private static void writeHiveNutrientTransfer(DataOutputStream output, HiveNutrientTransfer transfer) throws IOException {
        writeSubject(output, transfer.id()); writeSubject(output, transfer.hiveId()); writeSubject(output, transfer.requesterTaskId());
        writeSubject(output, transfer.sourceStoreId()); output.writeByte(transfer.sourceSlot().slot()); writeSubject(output, transfer.targetStoreId()); output.writeByte(transfer.targetSlot().slot());
        writeSubject(output, transfer.cargoId()); writeSubject(output, transfer.itemId()); output.writeShort(transfer.corridor().size());
        for (BlockPosition node : transfer.corridor()) writePosition(output, node);
        output.writeShort(transfer.cursor()); output.writeByte(transfer.phase().wireTag()); output.writeBoolean(transfer.blockReason().isPresent());
        if (transfer.blockReason().isPresent()) output.writeByte(transfer.blockReason().orElseThrow().wireTag());
        output.writeBoolean(transfer.endpointIntentId().isPresent()); if (transfer.endpointIntentId().isPresent()) writeString(output, transfer.endpointIntentId().orElseThrow().value());
    }
    private static HiveNutrientTransfer readHiveNutrientTransfer(DataInputStream input) throws IOException {
        SubjectId id = readSubject(input).value(), hive = readSubject(input).value(), task = readSubject(input).value();
        SubjectId source = readSubject(input).value(); int sourceSlot = input.readUnsignedByte(); SubjectId target = readSubject(input).value(); int targetSlot = input.readUnsignedByte();
        SubjectId cargo = readSubject(input).value(), item = readSubject(input).value(); java.util.ArrayList<BlockPosition> corridor = new java.util.ArrayList<>();
        for (int index = 0, count = input.readUnsignedShort(); index < count; index++) corridor.add(readPosition(input));
        int cursor = input.readUnsignedShort(), phase = input.readUnsignedByte(); boolean blocked = input.readBoolean(); int reason = blocked ? input.readUnsignedByte() : -1;
        var endpoint = input.available() == 0 || !input.readBoolean() ? java.util.Optional.<PhysicalIntentId>empty() : java.util.Optional.of(new PhysicalIntentId(readString(input)));
        if (phase >= HiveNutrientTransferPhase.values().length || blocked != (phase == HiveNutrientTransferPhase.BLOCKED.wireTag())
                || blocked && reason >= HiveNutrientTransferBlockReason.values().length) throw new IllegalArgumentException("invalid hive nutrient transfer payload");
        return new HiveNutrientTransfer(id, hive, task, source, new InventoryCustody.ContainerSlot(source, sourceSlot), target, new InventoryCustody.ContainerSlot(target, targetSlot), cargo, item, corridor, cursor,
                FrontierWireTags.require(HiveNutrientTransferPhase.class, phase), endpoint,
                blocked ? java.util.Optional.of(FrontierWireTags.require(HiveNutrientTransferBlockReason.class, reason)) : java.util.Optional.empty());
    }
    private static void writeHiveNutrientReceipt(DataOutputStream output, HiveNutrientReceipt receipt) throws IOException {
        writeSubject(output, receipt.transferId()); writeSubject(output, receipt.hiveId()); writeSubject(output, receipt.cargoId()); writeSubject(output, receipt.itemId());
        writeSubject(output, receipt.sourceSlot().containerId()); output.writeByte(receipt.sourceSlot().slot()); writeSubject(output, receipt.targetSlot().containerId()); output.writeByte(receipt.targetSlot().slot());
    }
    private static HiveNutrientReceipt readHiveNutrientReceipt(DataInputStream input) throws IOException {
        SubjectId transfer = readSubject(input).value(), hive = readSubject(input).value(), cargo = readSubject(input).value(), item = readSubject(input).value();
        SubjectId source = readSubject(input).value(); int sourceSlot = input.readUnsignedByte(); SubjectId target = readSubject(input).value(); int targetSlot = input.readUnsignedByte();
        return new HiveNutrientReceipt(transfer, hive, cargo, item, new InventoryCustody.ContainerSlot(source, sourceSlot), new InventoryCustody.ContainerSlot(target, targetSlot));
    }
    static void writePosition(DataOutputStream output, BlockPosition position) throws IOException {
        output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z());
    }
    static BlockPosition readPosition(DataInputStream input) throws IOException { return new BlockPosition(input.readInt(), input.readInt(), input.readInt()); }
    static void writeBody(DataOutputStream output, BodyPosition body) throws IOException { output.writeInt(body.x()); output.writeInt(body.y()); output.writeInt(body.z()); }
    static BodyPosition readBody(DataInputStream input) throws IOException { return new BodyPosition(input.readInt(), input.readInt(), input.readInt()); }
    private static void writeContract(DataOutputStream output, SupplyContract contract) throws IOException {
        writeSubject(output, contract.id()); writeSubject(output, contract.settlementId()); writeSubject(output, contract.recipientId());
        writeSubject(output, contract.cargoId()); writeString(output, contract.itemKind()); output.writeByte(contract.itemCount()); output.writeByte(contract.status().wireTag());
    }
    private static SupplyContract readContract(DataInputStream input) throws IOException {
        SubjectIdHolder id = readSubject(input); SubjectIdHolder settlement = readSubject(input); SubjectIdHolder recipient = readSubject(input);
        SubjectIdHolder cargo = readSubject(input); String kind = readString(input); int count = input.readUnsignedByte(); int status = input.readUnsignedByte();
        if (status >= ContractStatus.values().length) throw new IllegalArgumentException("unknown contract status");
        return new SupplyContract(id.value(), settlement.value(), recipient.value(), cargo.value(), kind, count, FrontierWireTags.require(ContractStatus.class, status));
    }
    private static void writeOperation(DataOutputStream output, RouteOperation operation) throws IOException {
        writeSubject(output, operation.id()); writeSubject(output, operation.settlementId()); writeSubject(output, operation.cargoId()); writeSubject(output, operation.destinationId());
        // 0xFF cannot be a historical participant count (the old route owner capped at eight).
        output.writeByte(0xFF); RouteUnitManifestCodec.write(output, operation.unit());
        output.writeByte(operation.route().size());
        for (BlockPosition point : operation.route()) { output.writeInt(point.x()); output.writeInt(point.y()); output.writeInt(point.z()); }
        output.writeByte(operation.routeIndex()); output.writeByte(operation.stage().wireCode());
        // 0xA5 separates the v3 assembly-aware envelope from the legacy one-byte travel flag.
        output.writeByte(0xA5); output.writeBoolean(operation.activeAssembly().isPresent());
        if (operation.activeAssembly().isPresent()) writeOperationAssembly(output, operation.activeAssembly().orElseThrow());
        output.writeBoolean(operation.activeTravel().isPresent());
        if (operation.activeTravel().isPresent()) writeOperationTravel(output, operation.activeTravel().orElseThrow());
    }
    private static RouteOperation readOperation(DataInputStream input) throws IOException {
        SubjectIdHolder id = readSubject(input); SubjectIdHolder settlement = readSubject(input); SubjectIdHolder cargo = readSubject(input); SubjectIdHolder destination = readSubject(input);
        int participantEnvelope = input.readUnsignedByte();
        RouteUnitManifest unit;
        if (participantEnvelope != 0xFF) throw new IllegalArgumentException("route operation payload requires the current unit-manifest envelope");
        unit = RouteUnitManifestCodec.read(input);
        java.util.ArrayList<BlockPosition> route = new java.util.ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) route.add(new BlockPosition(input.readInt(), input.readInt(), input.readInt()));
        int routeIndex = input.readUnsignedByte(); int stage = input.readUnsignedByte();
        int marker = input.readUnsignedByte();
        java.util.Optional<OperationAssembly> assembly = java.util.Optional.empty();
        java.util.Optional<OperationTravel> travel;
        if (marker != 0xA5) throw new IllegalArgumentException("route operation payload requires the current assembly envelope");
        assembly = input.readBoolean() ? java.util.Optional.of(readOperationAssembly(input)) : java.util.Optional.empty();
        travel = input.readBoolean() ? java.util.Optional.of(readOperationTravel(input)) : java.util.Optional.empty();
        return new RouteOperation(id.value(), settlement.value(), cargo.value(), destination.value(), unit, route, routeIndex, OperationStage.fromWireCode(stage), assembly, travel);
    }
    private static void writeOperationTravel(DataOutputStream output, OperationTravel travel) throws IOException {
        // This envelope is mandatory for every current-schema operation cursor.
        output.writeShort(0xfffe); TraversalTopologyStateCodec.write(output, travel.topology());
        output.writeShort(travel.cursor()); output.writeByte(travel.formation().size());
        for (var entry : travel.formation().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            writeSubject(output, entry.getKey()); output.writeInt(entry.getValue().x());
            output.writeInt(entry.getValue().y()); output.writeInt(entry.getValue().z());
        }
        output.writeInt(travel.cargoAnchor().x()); output.writeInt(travel.cargoAnchor().y()); output.writeInt(travel.cargoAnchor().z());
    }
    private static OperationTravel readOperationTravel(DataInputStream input) throws IOException {
        int envelope = input.readUnsignedShort();
        if (envelope != 0xfffe) throw new IllegalArgumentException("operation travel payload requires the current typed-anchor envelope");
        return readOperationTravelWithTopology(input, TraversalTopologyStateCodec.read(input));
    }

    private static OperationTravel readOperationTravelWithTopology(DataInputStream input, TraversalTopology topology) throws IOException {
        int cursor = input.readUnsignedShort(); java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, BodyPosition> formation = new java.util.LinkedHashMap<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor = readSubject(input).value();
            BlockPosition position = new BlockPosition(input.readInt(), input.readInt(), input.readInt());
            formation.put(actor, new BodyPosition(position.x(), position.y(), position.z()));
        }
        return new OperationTravel(topology, cursor, formation, TransportAnchor.atSupportCell(new BlockPosition(input.readInt(), input.readInt(), input.readInt())));
    }
    private static void writeOperationAssembly(DataOutputStream output, OperationAssembly assembly) throws IOException {
        writeSubject(output, assembly.cargoCarrierId()); output.writeByte(assembly.members().size());
        for (var entry : assembly.members().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            writeSubject(output, entry.getKey()); TraversalTopologyStateCodec.write(output, entry.getValue().topology());
            output.writeShort(entry.getValue().cursor());
        }
    }
    private static OperationAssembly readOperationAssembly(DataInputStream input) throws IOException {
        io.farfrontier.palemirror.frontier.v3.api.SubjectId carrier = readSubject(input).value();
        java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, OperationAssembly.Member> members = new java.util.LinkedHashMap<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor = readSubject(input).value();
            if (members.put(actor, new OperationAssembly.Member(TraversalTopologyStateCodec.read(input), input.readUnsignedShort())) != null) throw new IllegalArgumentException("duplicate operation assembly member");
        }
        return new OperationAssembly(members, carrier);
    }
    private static void writeCargoHandoffObservation(DataOutputStream output, CargoHandoffObservation observation) throws IOException {
        writeString(output, observation.id().value()); writeString(output, observation.intentId().value()); writeSubject(output, observation.cargoId());
        output.writeByte(observation.placements().size());
        for (CargoHandoffPlacement placement : observation.placements()) {
            writeSubject(output, placement.itemId()); writeSubject(output, placement.receiverSlot().containerId()); output.writeByte(placement.receiverSlot().slot());
        }
    }
    private static CargoHandoffObservation readCargoHandoffObservation(DataInputStream input) throws IOException {
        var id = new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId(readString(input)); var intentId = new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(readString(input));
        SubjectIdHolder cargoId = readSubject(input);
        java.util.ArrayList<CargoHandoffPlacement> placements = new java.util.ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            SubjectIdHolder itemId = readSubject(input); SubjectIdHolder receiver = readSubject(input);
            placements.add(new CargoHandoffPlacement(itemId.value(), new InventoryCustody.ContainerSlot(receiver.value(), input.readUnsignedByte())));
        }
        return new CargoHandoffObservation(id, intentId, cargoId.value(), placements);
    }
    private static final int TYPED_BODY_LEASE_MARKER = 0xfffe;

    private static void writeSceneLease(DataOutputStream output, SceneLease lease) throws IOException {
        if (!FrontierSceneBehaviors.isLogistics(lease)) {
            throw new IllegalArgumentException("logistics scene WAL payload requires its typed cause");
        }
        LogisticsSceneCause logistics = FrontierSceneBehaviors.logistics(lease);
        output.writeShort(TYPED_BODY_LEASE_MARKER);
        writeString(output, lease.id().value()); writeString(output, lease.worldId().value()); writeSubject(output, logistics.operationId()); writeSubject(output, logistics.cargoId());
        output.writeBoolean(logistics.engagementId().isPresent()); if (logistics.engagementId().isPresent()) writeSubject(output, logistics.engagementId().orElseThrow());
        output.writeInt(lease.handoffPosition().x()); output.writeInt(lease.handoffPosition().y()); output.writeInt(lease.handoffPosition().z());
        output.writeInt(logistics.cargoPosition().x()); output.writeInt(logistics.cargoPosition().y()); output.writeInt(logistics.cargoPosition().z());
        output.writeLong(lease.handoffInstant().ticks()); output.writeLong(lease.revision()); output.writeByte(lease.status().wireTag()); output.writeByte(lease.members().size());
        for (SceneMember member : lease.members()) {
            writeSubject(output, member.actorId());
            writeString(output, member.entityId().toString());
            BodyPosition position = lease.memberPosition(member.actorId());
            output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z());
        }
        output.writeByte(lease.ambientHandoffActorIds().size()); for (io.farfrontier.palemirror.frontier.v3.api.SubjectId actor : lease.ambientHandoffActorIds().stream().sorted().toList()) writeSubject(output, actor);
    }
    private static SceneLease readSceneLease(DataInputStream input) throws IOException {
        if (input.readUnsignedShort() != TYPED_BODY_LEASE_MARKER) throw new IllegalArgumentException("scene lease payload requires the current typed-body envelope");
        var id = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(readString(input));
        var world = new io.farfrontier.palemirror.frontier.v3.api.WorldId(readString(input)); SubjectIdHolder operation = readSubject(input); SubjectIdHolder cargo = readSubject(input);
        java.util.Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> engagement = input.readBoolean() ? java.util.Optional.of(readSubject(input).value()) : java.util.Optional.empty();
        BlockPosition position = new BlockPosition(input.readInt(), input.readInt(), input.readInt());
        BlockPosition cargoPosition = new BlockPosition(input.readInt(), input.readInt(), input.readInt());
        long handoff = input.readLong(); long revision = input.readLong(); int status = input.readUnsignedByte();
        if (status >= SceneLeaseStatus.values().length) throw new IllegalArgumentException("unknown scene lease status");
        java.util.ArrayList<SceneMember> members = new java.util.ArrayList<>();
        java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, BodyPosition> memberPositions = new java.util.LinkedHashMap<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor = readSubject(input).value();
            members.add(new SceneMember(actor, java.util.UUID.fromString(readString(input))));
            BlockPosition memberPosition = new BlockPosition(input.readInt(), input.readInt(), input.readInt());
            memberPositions.put(actor, new BodyPosition(memberPosition.x(), memberPosition.y(), memberPosition.z()));
        }
        java.util.Set<io.farfrontier.palemirror.frontier.v3.api.SubjectId> handoffActors = new java.util.LinkedHashSet<>();
        for (int actor = 0, actorCount = input.readUnsignedByte(); actor < actorCount; actor++) handoffActors.add(readSubject(input).value());
        return SceneLease.forCause(id, world, new LogisticsSceneCause(operation.value(), cargo.value(), engagement, cargoPosition), position,
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(handoff), revision,
                FrontierWireTags.require(SceneLeaseStatus.class, status), members, memberPositions, handoffActors,
                java.util.Optional.empty());
    }
    private static void writeAssaultSceneLease(DataOutputStream output, SceneLease lease) throws IOException {
        if (!(lease.cause() instanceof SettlementAssaultSceneCause cause)) {
            throw new IllegalArgumentException("assault scene WAL payload requires its typed cause");
        }
        output.writeShort(TYPED_BODY_LEASE_MARKER);
        writeString(output, lease.id().value()); writeString(output, lease.worldId().value()); writeSubject(output, cause.assaultId()); writeSubject(output, cause.settlementId());
        output.writeInt(lease.handoffPosition().x()); output.writeInt(lease.handoffPosition().y()); output.writeInt(lease.handoffPosition().z());
        output.writeLong(lease.handoffInstant().ticks()); output.writeLong(lease.revision()); output.writeByte(lease.status().wireTag()); output.writeByte(lease.members().size());
        for (SceneMember member : lease.members()) {
            writeSubject(output, member.actorId()); writeString(output, member.entityId().toString());
            BodyPosition position = lease.memberPosition(member.actorId()); output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z());
        }
        output.writeByte(lease.ambientHandoffActorIds().size()); for (io.farfrontier.palemirror.frontier.v3.api.SubjectId actor : lease.ambientHandoffActorIds().stream().sorted().toList()) writeSubject(output, actor);
    }
    private static SceneLease readAssaultSceneLease(DataInputStream input) throws IOException {
        if (input.readUnsignedShort() != TYPED_BODY_LEASE_MARKER) throw new IllegalArgumentException("assault scene payload requires the current typed-body envelope");
        var id = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(readString(input)); var world = new io.farfrontier.palemirror.frontier.v3.api.WorldId(readString(input));
        var cause = new SettlementAssaultSceneCause(readSubject(input).value(), readSubject(input).value());
        BlockPosition handoff = new BlockPosition(input.readInt(), input.readInt(), input.readInt()); long instant = input.readLong(); long revision = input.readLong(); int status = input.readUnsignedByte();
        if (status >= SceneLeaseStatus.values().length) throw new IllegalArgumentException("unknown scene lease status");
        java.util.ArrayList<SceneMember> members = new java.util.ArrayList<>(); java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, BodyPosition> positions = new java.util.LinkedHashMap<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            var actor = readSubject(input).value(); members.add(new SceneMember(actor, java.util.UUID.fromString(readString(input))));
            BlockPosition memberPosition = new BlockPosition(input.readInt(), input.readInt(), input.readInt());
            positions.put(actor, new BodyPosition(memberPosition.x(), memberPosition.y(), memberPosition.z()));
        }
        java.util.Set<io.farfrontier.palemirror.frontier.v3.api.SubjectId> ambient = new java.util.LinkedHashSet<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) ambient.add(readSubject(input).value());
        return SceneLease.forCause(id, world, cause, handoff, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(instant), revision,
                FrontierWireTags.require(SceneLeaseStatus.class, status), members, positions, ambient, java.util.Optional.empty());
    }
    private static void writeMemberPositions(DataOutputStream output, java.util.List<SceneMemberPosition> values) throws IOException {
        output.writeByte(values.size()); for (SceneMemberPosition member : values) {
            writeSubject(output, member.actorId()); output.writeInt(member.body().x()); output.writeInt(member.body().y()); output.writeInt(member.body().z()); output.writeLong(member.health().raw());
        }
    }
    private static java.util.List<SceneMemberPosition> readMemberPositions(DataInputStream input) throws IOException {
        java.util.ArrayList<SceneMemberPosition> values = new java.util.ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) values.add(new SceneMemberPosition(readSubject(input).value(),
                new BodyPosition(input.readInt(), input.readInt(), input.readInt()), new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong())));
        return values;
    } static void writeCustody(DataOutputStream output, InventoryCustody custody) throws IOException {
        if (custody instanceof InventoryCustody.ContainerSlot slot) { output.writeByte(0); writeSubject(output, slot.containerId()); output.writeByte(slot.slot()); }
        else if (custody instanceof InventoryCustody.Player player) { output.writeByte(1); writeString(output, player.playerId().toString()); }
        else if (custody instanceof InventoryCustody.WorldCarrier carrier) { output.writeByte(2); writeString(output, carrier.carrierId().toString()); }
        else if (custody instanceof InventoryCustody.Actor actor) { output.writeByte(3); writeSubject(output, actor.actorId()); }
        else throw new IllegalArgumentException("observed item custody payload cannot encode cargo custody");
    } static InventoryCustody readCustody(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> new InventoryCustody.ContainerSlot(readSubject(input).value(), input.readUnsignedByte());
            case 1 -> new InventoryCustody.Player(java.util.UUID.fromString(readString(input)));
            case 2 -> new InventoryCustody.WorldCarrier(java.util.UUID.fromString(readString(input))); case 3 -> new InventoryCustody.Actor(readSubject(input).value());
            default -> throw new IllegalArgumentException("unknown observed item custody kind");
        };
    } public static void writeSubject(DataOutputStream output, io.farfrontier.palemirror.frontier.v3.api.SubjectId value) throws IOException { writeString(output, value.value()); }
    static SubjectIdHolder readSubject(DataInputStream input) throws IOException { return new SubjectIdHolder(new io.farfrontier.palemirror.frontier.v3.api.SubjectId(readString(input))); }
    public static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(java.nio.charset.StandardCharsets.UTF_8); if (encoded.length > 256) throw new IllegalArgumentException("production payload field is too long"); output.writeShort(encoded.length); output.write(encoded);
    } static String readString(DataInputStream input) throws IOException {
        return readString(input, input.readUnsignedShort());
    } static String readString(DataInputStream input, int length) throws IOException {
        if (length > 256) throw new IllegalArgumentException("production payload field is too long");
        byte[] encoded = input.readNBytes(length); if (encoded.length != length) throw new IOException("truncated production payload field"); return new String(encoded, java.nio.charset.StandardCharsets.UTF_8); }
}
