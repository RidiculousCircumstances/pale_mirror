package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stable codecs for the one owner of retained-route repair. */
final class RouteMaintenancePayloadCodecs {
    private RouteMaintenancePayloadCodecs() { }
    static PayloadCodec started() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_maintenance_started"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            RouteMaintenance maintenance = ((RouteMaintenanceStarted) payload).maintenance(); RouteMaintenanceStateCodec.write(output, Map.of(maintenance.id(), maintenance));
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            Map<SubjectId, RouteMaintenance> values = RouteMaintenanceStateCodec.read(input);
            if (values.size() != 1) throw new IllegalArgumentException("route maintenance start payload requires one operation");
            return new RouteMaintenanceStarted(values.values().iterator().next());
        }); }
    }; }
    static PayloadCodec materialLoaded() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_maintenance_material_loaded"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            RouteMaintenanceMaterialLoaded loaded = (RouteMaintenanceMaterialLoaded) payload;
            FrontierWorldPayloadCodecs.writeSubject(output, loaded.maintenanceId()); FrontierWorldPayloadCodecs.writeSubject(output, loaded.cargo().id());
            FrontierWorldPayloadCodecs.writeSubject(output, loaded.cargo().ownerId()); FrontierWorldPayloadCodecs.writeSubject(output, loaded.cargo().itemIds().getFirst());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new RouteMaintenanceMaterialLoaded(
                FrontierWorldPayloadCodecs.readSubject(input).value(), new CargoBatch(FrontierWorldPayloadCodecs.readSubject(input).value(),
                FrontierWorldPayloadCodecs.readSubject(input).value(), java.util.List.of(FrontierWorldPayloadCodecs.readSubject(input).value())))); }
    }; }
    static PayloadCodec assemblyStarted() { return assembly("frontier.route_maintenance_assembly_started", RouteMaintenanceAssemblyStarted::new); }
    static PayloadCodec assemblyAdvanced() { return assembly("frontier.route_maintenance_assembly_advanced", RouteMaintenanceAssemblyAdvanced::new); }
    static PayloadCodec closed() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_maintenance_closed"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output ->
                FrontierWorldPayloadCodecs.writeSubject(output, ((RouteMaintenanceClosed) payload).maintenanceId())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                new RouteMaintenanceClosed(FrontierWorldPayloadCodecs.readSubject(input).value())); }
    }; }
    private static PayloadCodec assembly(String type, java.util.function.BiFunction<SubjectId, EngineeringWorkAssembly, FrontierPayload> factory) { return new PayloadCodec() {
        @Override public String type() { return type; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            SubjectId id; EngineeringWorkAssembly assembly;
            if (payload instanceof RouteMaintenanceAssemblyStarted started) { id = started.maintenanceId(); assembly = started.assembly(); }
            else if (payload instanceof RouteMaintenanceAssemblyAdvanced advanced) { id = advanced.maintenanceId(); assembly = advanced.assembly(); }
            else throw new IllegalArgumentException("route maintenance assembly codec received foreign payload");
            FrontierWorldPayloadCodecs.writeSubject(output, id); RouteConstructionPayloadCodecs.writeAssembly(output, assembly);
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> factory.apply(
                FrontierWorldPayloadCodecs.readSubject(input).value(), RouteConstructionPayloadCodecs.readAssembly(input))); }
    }; }
}
