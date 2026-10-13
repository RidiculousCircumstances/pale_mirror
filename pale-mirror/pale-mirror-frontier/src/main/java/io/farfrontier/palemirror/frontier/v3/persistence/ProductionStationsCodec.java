package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.*;

/** Current-format installed instance and station-owned operation; no historical inference. */
final class ProductionStationsCodec {
    private ProductionStationsCodec() { }
    static void write(DataOutputStream out, ProductionStations state) throws IOException {
        writeCount(out, state.machines().size());
        for (var machine : state.machines().values().stream().sorted(Comparator.comparing(ProductionStations.Machine::stationId)).toList()) {
            writeString(out, machine.stationId().value()); out.writeLong(machine.storedHeat());
            out.writeLong(machine.revision()); out.writeBoolean(machine.pendingFuel().isPresent());
            if (machine.pendingFuel().isPresent()) ProductionStationFuelCodec.writeBurn(out, machine.pendingFuel().orElseThrow());
            out.writeBoolean(machine.pendingRecipe().isPresent());
            if (machine.pendingRecipe().isPresent()) ProductionStationRecipeCodec.writeBoundary(out, machine.pendingRecipe().orElseThrow());
            out.writeBoolean(machine.operation().isPresent());
            if (machine.operation().isEmpty()) continue;
            var operation = machine.operation().orElseThrow();
            writeString(out, operation.id().value().value()); writeString(out, operation.stationId().value());
            writeString(out, operation.orderId().value()); writeString(out, operation.recipeId().value());
            switch (operation.inputCustody()) {
                case ProductionOperation.ExactInput exact -> { out.writeByte(1); writeString(out, exact.itemId().value()); }
                case ProductionOperation.FungibleInput fungible -> {
                    out.writeByte(2); writeString(out, fungible.accountId().value());
                    ShipmentStateCodec.lots(out, fungible.lots()); ShipmentStateCodec.lots(out, fungible.claims());
                }
            }
            writeString(out, operation.outputId().value()); out.writeInt(operation.inputQuantity()); out.writeInt(operation.outputQuantity());
            out.writeLong(operation.committedHeat()); WorkStateCodec.writeProgress(out, operation.progress()); out.writeBoolean(operation.transformed());
            switch (operation.initiator()) {
                case ProductionOperation.OrderInitiator order -> { out.writeByte(1); out.writeByte(order.kind().wireTag()); writeString(out, order.orderId().value()); }
                case ProductionOperation.PlayerInitiator player -> {
                    out.writeByte(2); writeString(out, player.playerId().toString()); out.writeByte(player.attendanceIntent().wireTag());
                }
            }
        }
    }
    static ProductionStations read(DataInputStream in) throws IOException {
        int count = readCount(in);
        if (count > 4_096) throw new IllegalArgumentException("machine runtime bound exceeded");
        var machines = new LinkedHashMap<SubjectId, ProductionStations.Machine>();
        for (int index = 0; index < count; index++) {
            var id = new SubjectId(readString(in)); long heat = in.readLong();
            long revision = in.readLong(); var fuel = in.readBoolean() ? Optional.of(ProductionStationFuelCodec.readBurn(in))
                    : Optional.<ProductionStationFuelBurn>empty();
            var recipe = in.readBoolean() ? Optional.of(ProductionStationRecipeCodec.readBoundary(in))
                    : Optional.<ProductionStationRecipeBoundary>empty();
            Optional<ProductionOperation> operation = in.readBoolean() ? Optional.of(new ProductionOperation(
                    new ProductionOperation.Id(new SubjectId(readString(in))), new SubjectId(readString(in)),
                    new SubjectId(readString(in)), new ProductionRecipe.Id(readString(in)), readInput(in), new SubjectId(readString(in)),
                    in.readInt(), in.readInt(), in.readLong(), WorkStateCodec.readProgress(in), in.readBoolean(), readInitiator(in))) : Optional.empty();
            if (machines.putIfAbsent(id, new ProductionStations.Machine(id, heat, operation, revision, fuel, recipe)) != null)
                throw new IllegalArgumentException("duplicate installed machine runtime");
        }
        return new ProductionStations(machines);
    }
    private static ProductionOperation.InputCustody readInput(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 1 -> new ProductionOperation.ExactInput(new SubjectId(readString(in)));
            case 2 -> new ProductionOperation.FungibleInput(new SubjectId(readString(in)), ShipmentStateCodec.lots(in), ShipmentStateCodec.lots(in));
            default -> throw new IllegalArgumentException("unknown station input custody representation");
        };
    }
    private static ProductionOperation.Initiator readInitiator(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 1 -> new ProductionOperation.OrderInitiator(ProductionOrderKind.fromWireTag(in.readUnsignedByte()), new SubjectId(readString(in)));
            case 2 -> new ProductionOperation.PlayerInitiator(UUID.fromString(readString(in)),
                    ProductionOperation.PlayerAttendanceIntent.fromWireTag(in.readUnsignedByte()));
            default -> throw new IllegalArgumentException("unknown station operation initiator");
        };
    }
}
