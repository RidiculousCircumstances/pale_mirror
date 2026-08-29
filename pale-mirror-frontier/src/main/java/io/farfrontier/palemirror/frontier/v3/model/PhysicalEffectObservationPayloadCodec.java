package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** WAL-payload codec for the physical receipt union. */
final class PhysicalEffectObservationPayloadCodec {
    private PhysicalEffectObservationPayloadCodec() { }

    static void write(DataOutputStream output, PhysicalEffectObservation observation) throws IOException {
        if (observation instanceof CargoHandoffObservation cargo) { output.writeByte(0); cargo(output, cargo); }
        else if (observation instanceof StructuralRepairObservation repair) writeRepair(output, repair);
        else if (observation instanceof RouteConstructionObservation construction) writeConstruction(output, construction);
        else if (observation instanceof DecontaminationObservation decontamination) writeDecontamination(output, decontamination);
        else if (observation instanceof SceneStrikeObservation strike) writeStrike(output, strike);
        else if (observation instanceof ExplosionObservation explosion) writeExplosion(output, explosion);
        else if (observation instanceof ExactItemConsumedObservation consumed) {
            output.writeByte(6); ids(output, consumed); FrontierWorldPayloadCodecs.writeSubject(output, consumed.itemId());
            output.writeByte(consumed.countBefore()); output.writeByte(consumed.countAfter());
        }
        else if (observation instanceof ResourceSitePreparationObservation preparation) {
            output.writeByte(7); ids(output, preparation); FrontierWorldPayloadCodecs.writeSubject(output, preparation.siteId());
            output.writeByte(preparation.preparedSoilSlots()); output.writeByte(preparation.preparedCropSlots());
        }
        else if (observation instanceof ProductionTransformationObservation production) {
            output.writeByte(8); ids(output, production); FrontierWorldPayloadCodecs.writeSubject(output, production.inputItemId());
            FrontierWorldPayloadCodecs.writeSubject(output, production.outputItemId()); output.writeByte(production.inputCount()); output.writeByte(production.outputCount());
        }
        else throw new IllegalArgumentException("unknown physical effect observation");
    }

    static PhysicalEffectObservation read(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> cargo(input);
            case 1 -> new StructuralRepairObservation(id(input), intent(input), FrontierWorldPayloadCodecs.readSubject(input).value(), FrontierWorldPayloadCodecs.readPosition(input));
            case 2 -> new RouteConstructionObservation(id(input), intent(input), FrontierWorldPayloadCodecs.readSubject(input).value(), FrontierWorldPayloadCodecs.readSubject(input).value(), FrontierWorldPayloadCodecs.readPosition(input));
            case 3 -> new DecontaminationObservation(id(input), intent(input), FrontierWorldPayloadCodecs.readSubject(input).value(), new InfectionCell(input.readInt(), input.readInt()), input.readLong(), input.readLong());
            case 4 -> strike(input);
            case 5 -> explosion(input);
            case 6 -> new ExactItemConsumedObservation(id(input), intent(input), FrontierWorldPayloadCodecs.readSubject(input).value(), input.readUnsignedByte(), input.readUnsignedByte());
            case 7 -> new ResourceSitePreparationObservation(id(input), intent(input), FrontierWorldPayloadCodecs.readSubject(input).value(), input.readUnsignedByte(), input.readUnsignedByte());
            case 8 -> new ProductionTransformationObservation(id(input), intent(input), FrontierWorldPayloadCodecs.readSubject(input).value(),
                    FrontierWorldPayloadCodecs.readSubject(input).value(), input.readUnsignedByte(), input.readUnsignedByte());
            default -> throw new IllegalArgumentException("unknown physical effect observation kind");
        };
    }

    private static void cargo(DataOutputStream output, CargoHandoffObservation cargo) throws IOException {
        ids(output, cargo); FrontierWorldPayloadCodecs.writeSubject(output, cargo.cargoId()); output.writeByte(cargo.placements().size());
        for (CargoHandoffPlacement placement : cargo.placements()) {
            FrontierWorldPayloadCodecs.writeSubject(output, placement.itemId());
            FrontierWorldPayloadCodecs.writeSubject(output, placement.receiverSlot().containerId()); output.writeByte(placement.receiverSlot().slot());
        }
    }
    private static CargoHandoffObservation cargo(DataInputStream input) throws IOException {
        PhysicalObservationId id = id(input); PhysicalIntentId intent = intent(input); var cargo = FrontierWorldPayloadCodecs.readSubject(input).value();
        java.util.ArrayList<CargoHandoffPlacement> placements = new java.util.ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) placements.add(new CargoHandoffPlacement(
                FrontierWorldPayloadCodecs.readSubject(input).value(), new InventoryCustody.ContainerSlot(FrontierWorldPayloadCodecs.readSubject(input).value(), input.readUnsignedByte())));
        return new CargoHandoffObservation(id, intent, cargo, placements);
    }
    private static void writeRepair(DataOutputStream output, StructuralRepairObservation value) throws IOException {
        output.writeByte(1); ids(output, value); FrontierWorldPayloadCodecs.writeSubject(output, value.itemId()); FrontierWorldPayloadCodecs.writePosition(output, value.position());
    }
    private static void writeConstruction(DataOutputStream output, RouteConstructionObservation value) throws IOException {
        output.writeByte(2); ids(output, value); FrontierWorldPayloadCodecs.writeSubject(output, value.projectId());
        FrontierWorldPayloadCodecs.writeSubject(output, value.itemId()); FrontierWorldPayloadCodecs.writePosition(output, value.position());
    }
    private static void writeDecontamination(DataOutputStream output, DecontaminationObservation value) throws IOException {
        output.writeByte(3); ids(output, value); FrontierWorldPayloadCodecs.writeSubject(output, value.itemId()); output.writeInt(value.cell().x());
        output.writeInt(value.cell().z()); output.writeLong(value.priorRaw()); output.writeLong(value.remainingRaw());
    }
    private static void writeStrike(DataOutputStream output, SceneStrikeObservation value) throws IOException {
        output.writeByte(4); ids(output, value); FrontierWorldPayloadCodecs.writeSubject(output, value.attackerId());
        FrontierWorldPayloadCodecs.writeSubject(output, value.targetId()); output.writeLong(value.targetHealthBefore().raw()); output.writeLong(value.targetHealthAfter().raw());
    }
    private static void writeExplosion(DataOutputStream output, ExplosionObservation value) throws IOException {
        output.writeByte(5); ids(output, value); output.writeLong(value.origin().x().raw()); output.writeLong(value.origin().y().raw());
        output.writeLong(value.origin().z().raw()); output.writeByte(value.radiusBlocks()); output.writeInt(value.affectedBlockCount()); output.writeInt(value.changedBlockCount());
        output.writeByte(1); output.writeByte(value.entityImpacts().size());
        for (ExplosionEntityImpact impact : value.entityImpacts()) {
            output.writeLong(impact.entityId().getMostSignificantBits()); output.writeLong(impact.entityId().getLeastSignificantBits());
            FrontierWorldPayloadCodecs.writeString(output, impact.entityType()); output.writeBoolean(impact.frontierActorId().isPresent());
            if (impact.frontierActorId().isPresent()) FrontierWorldPayloadCodecs.writeSubject(output, impact.frontierActorId().orElseThrow());
            output.writeBoolean(impact.removed());
        }
        output.writeByte(value.itemImpacts().size());
        for (ExplosionItemImpact impact : value.itemImpacts()) {
            FrontierWorldPayloadCodecs.writeSubject(output, impact.itemId()); output.writeByte(impact.outcome().ordinal());
        }
        output.writeInt(value.affectedInfectionOverlayCount()); output.writeInt(value.changedInfectionOverlayCount());
    }
    private static SceneStrikeObservation strike(DataInputStream input) throws IOException {
        return new SceneStrikeObservation(id(input), intent(input), FrontierWorldPayloadCodecs.readSubject(input).value(),
                FrontierWorldPayloadCodecs.readSubject(input).value(), new FixedScalar(input.readLong()), new FixedScalar(input.readLong()));
    }
    private static ExplosionObservation explosion(DataInputStream input) throws IOException {
        PhysicalObservationId id = id(input); PhysicalIntentId intent = intent(input);
        FixedPosition origin = new FixedPosition(new FixedScalar(input.readLong()), new FixedScalar(input.readLong()), new FixedScalar(input.readLong()));
        int radius = input.readUnsignedByte(), affected = input.readInt(), changed = input.readInt();
        if (input.available() == 0) return new ExplosionObservation(id, intent, origin, radius, affected, changed, java.util.List.of(), java.util.List.of(), 0, 0);
        if (input.readUnsignedByte() != 1) throw new IllegalArgumentException("unknown explosion receipt schema");
        int entities = input.readUnsignedByte(); java.util.ArrayList<ExplosionEntityImpact> entityImpacts = new java.util.ArrayList<>();
        for (int index = 0; index < entities; index++) {
            java.util.UUID entity = new java.util.UUID(input.readLong(), input.readLong()); String type = FrontierWorldPayloadCodecs.readString(input);
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> actor = input.readBoolean()
                    ? java.util.Optional.of(FrontierWorldPayloadCodecs.readSubject(input).value()) : java.util.Optional.empty();
            entityImpacts.add(new ExplosionEntityImpact(entity, type, actor, input.readBoolean()));
        }
        int items = input.readUnsignedByte(); java.util.ArrayList<ExplosionItemImpact> itemImpacts = new java.util.ArrayList<>();
        for (int index = 0; index < items; index++) {
            io.farfrontier.palemirror.frontier.v3.api.SubjectId item = FrontierWorldPayloadCodecs.readSubject(input).value(); int outcome = input.readUnsignedByte();
            if (outcome >= ExplosionItemImpact.Outcome.values().length) throw new IllegalArgumentException("unknown explosion item outcome");
            itemImpacts.add(new ExplosionItemImpact(item, ExplosionItemImpact.Outcome.values()[outcome]));
        }
        return new ExplosionObservation(id, intent, origin, radius, affected, changed, entityImpacts, itemImpacts, input.readInt(), input.readInt());
    }
    private static void ids(DataOutputStream output, PhysicalEffectObservation observation) throws IOException {
        FrontierWorldPayloadCodecs.writeString(output, observation.id().value()); FrontierWorldPayloadCodecs.writeString(output, observation.intentId().value());
    }
    private static PhysicalObservationId id(DataInputStream input) throws IOException { return new PhysicalObservationId(FrontierWorldPayloadCodecs.readString(input)); }
    private static PhysicalIntentId intent(DataInputStream input) throws IOException { return new PhysicalIntentId(FrontierWorldPayloadCodecs.readString(input)); }
}
