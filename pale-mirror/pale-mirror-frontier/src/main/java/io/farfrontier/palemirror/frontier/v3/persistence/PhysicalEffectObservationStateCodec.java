package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Snapshot binary codec for the bounded physical receipt union. */
final class PhysicalEffectObservationStateCodec {
    private PhysicalEffectObservationStateCodec() { }

    static void write(DataOutputStream output, Map<PhysicalObservationId, PhysicalEffectObservation> observations) throws IOException {
        FrontierWorldStateCodec.writeCount(output, observations.size());
        for (PhysicalEffectObservation observation : observations.values().stream().sorted(Comparator.comparing(PhysicalEffectObservation::id)).toList()) {
            if (observation instanceof CargoHandoffObservation cargo) {
                output.writeByte(0); string(output, cargo.id().value()); string(output, cargo.intentId().value()); string(output, cargo.cargoId().value());
                FrontierWorldStateCodec.writeCount(output, cargo.placements().size());
                for (CargoHandoffPlacement placement : cargo.placements()) { string(output, placement.itemId().value()); FrontierWorldStateCodec.writeCustody(output, placement.receiverSlot()); }
            } else if (observation instanceof StructuralRepairObservation repair) {
                output.writeByte(1); string(output, repair.id().value()); string(output, repair.intentId().value()); string(output, repair.itemId().value()); FrontierWorldStateCodec.writePosition(output, repair.position());
            } else if (observation instanceof RouteConstructionObservation construction) {
                output.writeByte(2); string(output, construction.id().value()); string(output, construction.intentId().value()); string(output, construction.projectId().value());
                string(output, construction.itemId().value()); FrontierWorldStateCodec.writePosition(output, construction.position());
            } else if (observation instanceof DecontaminationObservation decontamination) {
                output.writeByte(3); string(output, decontamination.id().value()); string(output, decontamination.intentId().value()); string(output, decontamination.itemId().value());
                output.writeInt(decontamination.cell().x()); output.writeInt(decontamination.cell().z()); output.writeLong(decontamination.priorRaw()); output.writeLong(decontamination.remainingRaw());
            } else if (observation instanceof SceneStrikeObservation strike) {
                output.writeByte(4); string(output, strike.id().value()); string(output, strike.intentId().value()); string(output, strike.attackerId().value()); string(output, strike.targetId().value());
                output.writeLong(strike.targetHealthBefore().raw()); output.writeLong(strike.targetHealthAfter().raw());
            } else if (observation instanceof ExplosionObservation explosion) {
                output.writeByte(5); string(output, explosion.id().value()); string(output, explosion.intentId().value()); output.writeLong(explosion.origin().x().raw());
                output.writeLong(explosion.origin().y().raw()); output.writeLong(explosion.origin().z().raw()); output.writeByte(explosion.radiusBlocks());
                output.writeInt(explosion.affectedBlockCount()); output.writeInt(explosion.changedBlockCount());
                FrontierWorldStateCodec.writeCount(output, explosion.entityImpacts().size());
                for (ExplosionEntityImpact impact : explosion.entityImpacts()) {
                    output.writeLong(impact.entityId().getMostSignificantBits()); output.writeLong(impact.entityId().getLeastSignificantBits()); string(output, impact.entityType());
                    output.writeBoolean(impact.frontierActorId().isPresent()); if (impact.frontierActorId().isPresent()) string(output, impact.frontierActorId().orElseThrow().value()); output.writeBoolean(impact.removed());
                }
                FrontierWorldStateCodec.writeCount(output, explosion.itemImpacts().size());
                for (ExplosionItemImpact impact : explosion.itemImpacts()) { string(output, impact.itemId().value()); output.writeByte(impact.outcome().wireTag()); }
                output.writeInt(explosion.affectedInfectionOverlayCount()); output.writeInt(explosion.changedInfectionOverlayCount());
            } else if (observation instanceof ExactItemConsumedObservation consumed) {
                output.writeByte(6); string(output, consumed.id().value()); string(output, consumed.intentId().value()); string(output, consumed.itemId().value());
                output.writeByte(consumed.countBefore()); output.writeByte(consumed.countAfter());
            } else if (observation instanceof ResourceSitePreparationObservation preparation) {
                output.writeByte(7); string(output, preparation.id().value()); string(output, preparation.intentId().value()); string(output, preparation.siteId().value());
                output.writeInt(preparation.preparedSoilSlots()); output.writeInt(preparation.preparedCropSlots());
            } else if (observation instanceof ResourceSiteHarvestObservation harvest) {
                output.writeByte(8); string(output, harvest.id().value()); string(output, harvest.intentId().value()); string(output, harvest.siteId().value());
                string(output, harvest.workerId().value()); string(output, harvest.output().id().value()); string(output, harvest.output().economicOwnerId().value());
                string(output, harvest.output().itemKind()); output.writeByte(harvest.output().count()); FrontierWorldStateCodec.writeCustody(output, harvest.output().custody());
                output.writeByte(harvest.harvestedCropSlots());
            } else if (observation instanceof ProductionTransformationObservation production) {
                output.writeByte(9); string(output, production.id().value()); string(output, production.intentId().value()); string(output, production.inputItemId().value());
                string(output, production.outputItemId().value()); output.writeByte(production.inputCount()); output.writeByte(production.outputCount());
            } else if (observation instanceof CargoLoadObservation loading) {
                output.writeByte(10); string(output, loading.id().value()); string(output, loading.intentId().value()); string(output, loading.contractId().value());
                string(output, loading.cargoId().value()); string(output, loading.itemId().value()); output.writeByte(loading.itemCount());
            } else if (observation instanceof RouteConstructionMaterialLoadObservation loading) {
                output.writeByte(12); string(output, loading.id().value()); string(output, loading.intentId().value()); string(output, loading.projectId().value());
                string(output, loading.cargoId().value()); string(output, loading.sourceItemId().value()); string(output, loading.cargoItemId().value()); output.writeByte(loading.sourceRemainingCount());
            } else if (observation instanceof HiveNutrientDepartureObservation departure) {
                output.writeByte(13); string(output, departure.id().value()); string(output, departure.intentId().value()); string(output, departure.transferId().value());
                string(output, departure.cargoId().value()); string(output, departure.itemId().value()); output.writeByte(departure.itemCount());
            } else if (observation instanceof HiveNutrientArrivalObservation arrival) {
                output.writeByte(14); string(output, arrival.id().value()); string(output, arrival.intentId().value()); string(output, arrival.transferId().value());
                string(output, arrival.cargoId().value()); string(output, arrival.itemId().value()); output.writeByte(arrival.itemCount());
            } else if (observation instanceof EquipmentIssueObservation issue) {
                output.writeByte(15); string(output, issue.id().value()); string(output, issue.intentId().value()); string(output, issue.assaultId().value());
                string(output, issue.residentId().value()); string(output, issue.itemId().value()); FrontierWorldStateCodec.writeCustody(output, issue.sourceSlot());
            } else if (observation instanceof EquipmentReturnObservation returned) {
                output.writeByte(16); string(output, returned.id().value()); string(output, returned.intentId().value()); string(output, returned.assaultId().value());
                string(output, returned.residentId().value()); string(output, returned.itemId().value()); FrontierWorldStateCodec.writeCustody(output, returned.targetSlot());
            } else if (observation instanceof RouteMaintenanceObservation repair) {
                output.writeByte(17); string(output, repair.id().value()); string(output, repair.intentId().value()); string(output, repair.maintenanceId().value());
                string(output, repair.itemId().value()); FrontierWorldStateCodec.writePosition(output, repair.position());
            } else if (observation instanceof RouteMaintenanceMaterialLoadObservation loading) {
                output.writeByte(18); string(output, loading.id().value()); string(output, loading.intentId().value()); string(output, loading.maintenanceId().value());
                string(output, loading.cargoId().value()); string(output, loading.sourceItemId().value()); string(output, loading.cargoItemId().value()); output.writeByte(loading.sourceRemainingCount());
            } else if (observation instanceof SettlementServiceInputIssueObservation issue) {
                output.writeByte(19); string(output, issue.id().value()); string(output, issue.intentId().value()); string(output, issue.workId().value());
                string(output, issue.workerId().value()); string(output, issue.itemId().value()); FrontierWorldStateCodec.writeCustody(output, issue.sourceSlot());
            } else if (observation instanceof FungibleCargoHandoffObservation cargo) {
                output.writeByte(20); string(output, cargo.id().value()); string(output, cargo.intentId().value()); string(output, cargo.cargoId().value()); output.writeLong(cargo.authorityEpoch());
                FrontierWorldStateCodec.writeCount(output, cargo.stacks().size());
                for (FungiblePhysicalObservation.Stack stack : cargo.stacks()) {
                    if (!(stack.address() instanceof PhysicalStackAddress.ContainerSlot slot)) throw new IllegalArgumentException("fungible cargo receipt has no container slot");
                    FrontierWorldStateCodec.writeCustody(output, slot.slot()); string(output, stack.itemKind()); output.writeByte(stack.quantity());
                }
            } else if (observation instanceof FungibleResourceConsumedObservation consumed) {
                output.writeByte(21); string(output, consumed.id().value()); string(output, consumed.intentId().value()); string(output, consumed.accountId().value());
                string(output, consumed.lotId().value()); string(output, consumed.claimId().value()); output.writeShort(consumed.consumedCount()); output.writeLong(consumed.authorityEpoch());
                FrontierWorldStateCodec.writeCount(output, consumed.remainingStacks().size());
                for (FungiblePhysicalObservation.Stack stack : consumed.remainingStacks()) {
                    if (!(stack.address() instanceof PhysicalStackAddress.ContainerSlot slot)) throw new IllegalArgumentException("fungible consumption receipt has no container slot");
                    FrontierWorldStateCodec.writeCustody(output, slot.slot()); string(output, stack.itemKind()); output.writeByte(stack.quantity());
                }
            } else if (observation instanceof FungibleNutrientDepartureObservation departure) {
                output.writeByte(22); string(output, departure.id().value()); string(output, departure.intentId().value()); string(output, departure.transferId().value());
                string(output, departure.cargoId().value()); string(output, departure.sourceAccountId().value()); string(output, departure.lotId().value());
                output.writeShort(departure.quantity()); output.writeLong(departure.authorityEpoch()); FrontierWorldStateCodec.writeCount(output, departure.remainingStacks().size());
                for (FungiblePhysicalObservation.Stack stack : departure.remainingStacks()) {
                    if (!(stack.address() instanceof PhysicalStackAddress.ContainerSlot slot)) throw new IllegalArgumentException("fungible nutrient departure receipt has no container slot");
                    FrontierWorldStateCodec.writeCustody(output, slot.slot()); string(output, stack.itemKind()); output.writeByte(stack.quantity());
                }
            } else if (observation instanceof FungibleProductionObservation production) {
                output.writeByte(production.inputLots().size() == 1 ? 23 : 24); string(output, production.id().value()); string(output, production.intentId().value());
                FungibleProductionObservationCodec.write(output, production);
            } else if (observation instanceof ResourceSiteHarvestDeliveryObservation delivery) {
                output.writeByte(25); string(output, delivery.id().value()); string(output, delivery.intentId().value());
                ResourceSiteHarvestDeliveryObservationCodec.write(output, delivery);
            } else if (observation instanceof ResourceSiteHarvestDeferredObservation deferred) {
                output.writeByte(26); string(output, deferred.id().value()); string(output, deferred.intentId().value());
                ResourceSiteHarvestDeferredObservationCodec.write(output, deferred);
            } else throw new IllegalArgumentException("unknown physical effect observation");
        }
    }

    static Map<PhysicalObservationId, PhysicalEffectObservation> read(DataInputStream input) throws IOException {
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            int kind = input.readUnsignedByte(); PhysicalObservationId id = new PhysicalObservationId(text(input)); PhysicalIntentId intentId = new PhysicalIntentId(text(input));
            PhysicalEffectObservation observation = switch (kind) {
                case 0 -> cargo(input, id, intentId);
                case 1 -> new StructuralRepairObservation(id, intentId, new SubjectId(text(input)), FrontierWorldStateCodec.readPosition(input));
                case 2 -> new RouteConstructionObservation(id, intentId, new SubjectId(text(input)), new SubjectId(text(input)), FrontierWorldStateCodec.readPosition(input));
                case 3 -> new DecontaminationObservation(id, intentId, new SubjectId(text(input)), new InfectionCell(input.readInt(), input.readInt()), input.readLong(), input.readLong());
                case 4 -> new SceneStrikeObservation(id, intentId, new SubjectId(text(input)), new SubjectId(text(input)), new FixedScalar(input.readLong()), new FixedScalar(input.readLong()));
                case 5 -> explosion(input, id, intentId);
                case 6 -> new ExactItemConsumedObservation(id, intentId, new SubjectId(text(input)), input.readUnsignedByte(), input.readUnsignedByte());
                case 7 -> new ResourceSitePreparationObservation(id, intentId, new SubjectId(text(input)), input.readInt(), input.readInt());
                case 8 -> harvest(input, id, intentId);
                case 9 -> new ProductionTransformationObservation(id, intentId, new SubjectId(text(input)), new SubjectId(text(input)), input.readUnsignedByte(), input.readUnsignedByte());
                case 10 -> new CargoLoadObservation(id, intentId, new SubjectId(text(input)), new SubjectId(text(input)), new SubjectId(text(input)), input.readUnsignedByte());
                case 11 -> throw new IllegalArgumentException("legacy unsplit route-material receipt is not recoverable");
                case 12 -> new RouteConstructionMaterialLoadObservation(id, intentId, new SubjectId(text(input)), new SubjectId(text(input)),
                        new SubjectId(text(input)), new SubjectId(text(input)), input.readUnsignedByte());
                case 13 -> new HiveNutrientDepartureObservation(id, intentId, new SubjectId(text(input)), new SubjectId(text(input)), new SubjectId(text(input)), input.readUnsignedByte());
                case 14 -> new HiveNutrientArrivalObservation(id, intentId, new SubjectId(text(input)), new SubjectId(text(input)), new SubjectId(text(input)), input.readUnsignedByte());
                case 15 -> equipmentIssue(input, id, intentId);
                case 16 -> equipmentReturn(input, id, intentId);
                case 17 -> new RouteMaintenanceObservation(id, intentId, new SubjectId(text(input)), new SubjectId(text(input)), FrontierWorldStateCodec.readPosition(input));
                case 18 -> new RouteMaintenanceMaterialLoadObservation(id, intentId, new SubjectId(text(input)), new SubjectId(text(input)),
                        new SubjectId(text(input)), new SubjectId(text(input)), input.readUnsignedByte());
                case 19 -> serviceInputIssue(input, id, intentId);
                case 20 -> fungibleCargo(input, id, intentId);
                case 21 -> fungibleConsumed(input, id, intentId);
                case 22 -> fungibleNutrientDeparture(input, id, intentId);
                case 23 -> FungibleProductionObservationCodec.read(input, id, intentId);
                case 24 -> FungibleProductionObservationCodec.read(input, id, intentId, true);
                case 25 -> ResourceSiteHarvestDeliveryObservationCodec.read(input, id, intentId);
                case 26 -> ResourceSiteHarvestDeferredObservationCodec.read(input, id, intentId);
                default -> throw new IllegalArgumentException("unknown physical observation kind");
            };
            if (observations.put(id, observation) != null) throw new IllegalArgumentException("duplicate physical observation id");
        }
        return observations;
    }

    private static CargoHandoffObservation cargo(DataInputStream input, PhysicalObservationId id, PhysicalIntentId intentId) throws IOException {
        SubjectId cargoId = new SubjectId(text(input)); java.util.ArrayList<CargoHandoffPlacement> placements = new java.util.ArrayList<>();
        for (int placement = 0, count = FrontierWorldStateCodec.readCount(input); placement < count; placement++) {
            SubjectId itemId = new SubjectId(text(input)); InventoryCustody custody = FrontierWorldStateCodec.readCustody(input);
            if (!(custody instanceof InventoryCustody.ContainerSlot receiver)) throw new IllegalArgumentException("cargo hand-off placement must target a container slot");
            placements.add(new CargoHandoffPlacement(itemId, receiver));
        }
        return new CargoHandoffObservation(id, intentId, cargoId, placements);
    }
    private static FungibleCargoHandoffObservation fungibleCargo(DataInputStream input, PhysicalObservationId id, PhysicalIntentId intentId) throws IOException {
        SubjectId cargoId = new SubjectId(text(input)); long epoch = input.readLong(); java.util.ArrayList<FungiblePhysicalObservation.Stack> stacks = new java.util.ArrayList<>();
        for (int stack = 0, count = FrontierWorldStateCodec.readCount(input); stack < count; stack++) {
            InventoryCustody custody = FrontierWorldStateCodec.readCustody(input);
            if (!(custody instanceof InventoryCustody.ContainerSlot slot)) throw new IllegalArgumentException("fungible cargo stack must target a container slot");
            stacks.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(slot), text(input), input.readUnsignedByte()));
        }
        return new FungibleCargoHandoffObservation(id, intentId, cargoId, epoch, stacks);
    }
    private static FungibleResourceConsumedObservation fungibleConsumed(DataInputStream input, PhysicalObservationId id, PhysicalIntentId intentId) throws IOException {
        SubjectId account = new SubjectId(text(input)); SubjectId lot = new SubjectId(text(input)); SubjectId claim = new SubjectId(text(input));
        int count = input.readUnsignedShort(); long epoch = input.readLong(); java.util.ArrayList<FungiblePhysicalObservation.Stack> remaining = new java.util.ArrayList<>();
        for (int index = 0, size = FrontierWorldStateCodec.readCount(input); index < size; index++) {
            InventoryCustody custody = FrontierWorldStateCodec.readCustody(input);
            if (!(custody instanceof InventoryCustody.ContainerSlot slot)) throw new IllegalArgumentException("fungible consumption stack must target a container slot");
            remaining.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(slot), text(input), input.readUnsignedByte()));
        }
        return new FungibleResourceConsumedObservation(id, intentId, account, lot, claim, count, epoch, remaining);
    }
    private static FungibleNutrientDepartureObservation fungibleNutrientDeparture(DataInputStream input, PhysicalObservationId id, PhysicalIntentId intentId) throws IOException {
        SubjectId transfer = new SubjectId(text(input)); SubjectId cargo = new SubjectId(text(input)); SubjectId source = new SubjectId(text(input)); SubjectId lot = new SubjectId(text(input));
        int quantity = input.readUnsignedShort(); long epoch = input.readLong(); java.util.ArrayList<FungiblePhysicalObservation.Stack> remaining = new java.util.ArrayList<>();
        for (int index = 0, size = FrontierWorldStateCodec.readCount(input); index < size; index++) {
            InventoryCustody custody = FrontierWorldStateCodec.readCustody(input);
            if (!(custody instanceof InventoryCustody.ContainerSlot slot)) throw new IllegalArgumentException("fungible nutrient departure stack must target a container slot");
            remaining.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(slot), text(input), input.readUnsignedByte()));
        }
        return new FungibleNutrientDepartureObservation(id, intentId, transfer, cargo, source, lot, quantity, epoch, remaining);
    }
    private static ExplosionObservation explosion(DataInputStream input, PhysicalObservationId id, PhysicalIntentId intentId) throws IOException {
        FixedPosition origin = new FixedPosition(new FixedScalar(input.readLong()), new FixedScalar(input.readLong()), new FixedScalar(input.readLong()));
        int radius = input.readUnsignedByte(), affected = input.readInt(), changed = input.readInt();
        java.util.ArrayList<ExplosionEntityImpact> entities = new java.util.ArrayList<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            java.util.UUID entity = new java.util.UUID(input.readLong(), input.readLong()); String type = text(input);
            java.util.Optional<SubjectId> actor = input.readBoolean() ? java.util.Optional.of(new SubjectId(text(input))) : java.util.Optional.empty();
            entities.add(new ExplosionEntityImpact(entity, type, actor, input.readBoolean()));
        }
        java.util.ArrayList<ExplosionItemImpact> items = new java.util.ArrayList<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId item = new SubjectId(text(input)); int outcome = input.readUnsignedByte();
            if (outcome >= ExplosionItemImpact.Outcome.values().length) throw new IllegalArgumentException("invalid explosion item outcome");
            items.add(new ExplosionItemImpact(item, FrontierWireTags.require(ExplosionItemImpact.Outcome.class, outcome)));
        }
        return new ExplosionObservation(id, intentId, origin, radius, affected, changed, entities, items, input.readInt(), input.readInt());
    }
    private static ResourceSiteHarvestObservation harvest(DataInputStream input, PhysicalObservationId id, PhysicalIntentId intentId) throws IOException {
        SubjectId site = new SubjectId(text(input)), worker = new SubjectId(text(input)), item = new SubjectId(text(input)), owner = new SubjectId(text(input));
        String kind = text(input); int count = input.readUnsignedByte(); InventoryCustody custody = FrontierWorldStateCodec.readCustody(input);
        return new ResourceSiteHarvestObservation(id, intentId, site, worker, new ExactItemStack(item, owner, kind, count, custody), input.readUnsignedByte());
    }
    private static EquipmentIssueObservation equipmentIssue(DataInputStream input, PhysicalObservationId id, PhysicalIntentId intentId) throws IOException {
        SubjectId assault = new SubjectId(text(input)), resident = new SubjectId(text(input)), item = new SubjectId(text(input));
        InventoryCustody custody = FrontierWorldStateCodec.readCustody(input);
        if (!(custody instanceof InventoryCustody.ContainerSlot source)) throw new IllegalArgumentException("equipment issue source must be a container slot");
        return new EquipmentIssueObservation(id, intentId, assault, resident, item, source);
    }
    private static EquipmentReturnObservation equipmentReturn(DataInputStream input, PhysicalObservationId id, PhysicalIntentId intentId) throws IOException {
        SubjectId assault = new SubjectId(text(input)), resident = new SubjectId(text(input)), item = new SubjectId(text(input));
        InventoryCustody custody = FrontierWorldStateCodec.readCustody(input);
        if (!(custody instanceof InventoryCustody.ContainerSlot target)) throw new IllegalArgumentException("equipment return target must be a container slot");
        return new EquipmentReturnObservation(id, intentId, assault, resident, item, target);
    }
    private static SettlementServiceInputIssueObservation serviceInputIssue(DataInputStream input, PhysicalObservationId id, PhysicalIntentId intentId) throws IOException {
        SubjectId work = new SubjectId(text(input)), worker = new SubjectId(text(input)), item = new SubjectId(text(input));
        InventoryCustody custody = FrontierWorldStateCodec.readCustody(input);
        if (!(custody instanceof InventoryCustody.ContainerSlot source)) throw new IllegalArgumentException("service input issue source must be a container slot");
        return new SettlementServiceInputIssueObservation(id, intentId, work, worker, item, source);
    }

    private static void string(DataOutputStream output, String value) throws IOException { FrontierWorldStateCodec.writeString(output, value); }
    private static String text(DataInputStream input) throws IOException { return FrontierWorldStateCodec.readString(input); }
}
