package io.farfrontier.palemirror.frontier.v3.model;

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
                for (ExplosionItemImpact impact : explosion.itemImpacts()) { string(output, impact.itemId().value()); output.writeByte(impact.outcome().ordinal()); }
                output.writeInt(explosion.affectedInfectionOverlayCount()); output.writeInt(explosion.changedInfectionOverlayCount());
            } else if (observation instanceof ExactItemConsumedObservation consumed) {
                output.writeByte(6); string(output, consumed.id().value()); string(output, consumed.intentId().value()); string(output, consumed.itemId().value());
                output.writeByte(consumed.countBefore()); output.writeByte(consumed.countAfter());
            } else if (observation instanceof ResourceSitePreparationObservation preparation) {
                output.writeByte(7); string(output, preparation.id().value()); string(output, preparation.intentId().value()); string(output, preparation.siteId().value());
                output.writeByte(preparation.preparedSoilSlots()); output.writeByte(preparation.preparedCropSlots());
            } else if (observation instanceof ResourceSiteHarvestObservation harvest) {
                output.writeByte(8); string(output, harvest.id().value()); string(output, harvest.intentId().value()); string(output, harvest.siteId().value());
                string(output, harvest.workerId().value()); string(output, harvest.output().id().value()); string(output, harvest.output().economicOwnerId().value());
                string(output, harvest.output().itemKind()); output.writeByte(harvest.output().count()); FrontierWorldStateCodec.writeCustody(output, harvest.output().custody());
                output.writeByte(harvest.harvestedCropSlots());
            } else if (observation instanceof ProductionTransformationObservation production) {
                output.writeByte(9); string(output, production.id().value()); string(output, production.intentId().value()); string(output, production.inputItemId().value());
                string(output, production.outputItemId().value()); output.writeByte(production.inputCount()); output.writeByte(production.outputCount());
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
                case 7 -> new ResourceSitePreparationObservation(id, intentId, new SubjectId(text(input)), input.readUnsignedByte(), input.readUnsignedByte());
                case 8 -> harvest(input, id, intentId);
                case 9 -> new ProductionTransformationObservation(id, intentId, new SubjectId(text(input)), new SubjectId(text(input)), input.readUnsignedByte(), input.readUnsignedByte());
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
            items.add(new ExplosionItemImpact(item, ExplosionItemImpact.Outcome.values()[outcome]));
        }
        return new ExplosionObservation(id, intentId, origin, radius, affected, changed, entities, items, input.readInt(), input.readInt());
    }
    private static ResourceSiteHarvestObservation harvest(DataInputStream input, PhysicalObservationId id, PhysicalIntentId intentId) throws IOException {
        SubjectId site = new SubjectId(text(input)), worker = new SubjectId(text(input)), item = new SubjectId(text(input)), owner = new SubjectId(text(input));
        String kind = text(input); int count = input.readUnsignedByte(); InventoryCustody custody = FrontierWorldStateCodec.readCustody(input);
        return new ResourceSiteHarvestObservation(id, intentId, site, worker, new ExactItemStack(item, owner, kind, count, custody), input.readUnsignedByte());
    }

    private static void string(DataOutputStream output, String value) throws IOException { FrontierWorldStateCodec.writeString(output, value); }
    private static String text(DataInputStream input) throws IOException { return FrontierWorldStateCodec.readString(input); }
}
