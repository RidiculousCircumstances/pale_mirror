package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState;

import java.util.*;

/** Immutable authored space. Zones describe purpose; real collision facts still decide passage. */
public record BuildingDefinition(
        BuildingTemplateId id,
        int version,
        String sha256,
        Set<StructureKind> functions,
        Representation representation,
        BlockPosition origin,
        BuildingSiteDefinition site,
        FacilityFacing front,
        BuildingVolume parcel,
        BuildingVolume fabric,
        Map<String, Room> rooms,
        Map<String, Zone> zones,
        Map<String, Passage> passages,
        Map<String, Workplace> workplaces,
        Optional<String> primaryEntrance,
        Optional<String> primaryWorkplace,
        Map<String, BlockPosition> anchors,
        Map<String, Bed> beds,
        Map<String, BuildingSeat> seats,
        Map<BlockPosition, Block> blocks) {
    public enum Representation {
        GRAYBOX,
        AUTHORED
    }

    public enum ZoneUse {
        TRANSIT,
        WORK,
        STORAGE,
        EQUIPMENT,
        MAINTENANCE,
        REST,
        QUEUE,
        DINING
    }

    public enum EndpointKind {
        EXTERIOR,
        ROOM
    }

    public record Room(String id, BuildingVolume volume) {
        public Room {
            name(id);
            Objects.requireNonNull(volume);
        }
    }

    public record Zone(
            String id,
            String room,
            ZoneUse use,
            BuildingVolume volume,
            Set<String> resourceCategories,
            int storagePriority) {
        public Zone {
            name(id);
            name(room);
            Objects.requireNonNull(use);
            Objects.requireNonNull(volume);
            resourceCategories = Set.copyOf(resourceCategories);
            if (resourceCategories.size() > 32 || storagePriority < 0 || storagePriority > 1000)
                throw new IllegalArgumentException("unbounded building storage policy");
            if (use != ZoneUse.STORAGE && (!resourceCategories.isEmpty() || storagePriority != 0))
                throw new IllegalArgumentException("non-storage zone declares storage policy");
        }
    }

    public record Endpoint(EndpointKind kind, Optional<String> room) {
        public Endpoint {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(room);
            if ((kind == EndpointKind.ROOM) != room.isPresent())
                throw new IllegalArgumentException("incomplete passage endpoint declaration");
            room.ifPresent(BuildingDefinition::name);
        }
    }

    /** Supports are a passage hint, not a permanently granted path or an actor arrival receipt. */
    public record Passage(
            String id,
            Endpoint from,
            Endpoint to,
            List<BlockPosition> supports,
            int thresholdIndex,
            boolean publicAccess) {
        public Passage {
            name(id);
            Objects.requireNonNull(from);
            Objects.requireNonNull(to);
            supports = List.copyOf(supports);
            if (supports.size() < 2
                    || supports.size() > 64
                    || thresholdIndex < 0
                    || thresholdIndex >= supports.size()
                    || new HashSet<>(supports).size() != supports.size())
                throw new IllegalArgumentException("invalid bounded building passage");
            for (int i = 1; i < supports.size(); i++) {
                var a = supports.get(i - 1);
                var b = supports.get(i);
                if (Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) != 1
                        || Math.abs(a.y() - b.y()) > 1)
                    throw new IllegalArgumentException("disconnected authored passage " + id);
            }
            if (from.equals(to)) throw new IllegalArgumentException("passage joins the same room");
        }
    }

    /** The building provides a generic equipment socket and interaction supports, not a recipe. */
    public record Workplace(
            String id,
            String room,
            BlockPosition input,
            BlockPosition worker,
            BlockPosition socket,
            ProductionEquipment.Id equipmentType,
            FacilityFacing facing,
            BuildingVolume equipment,
            BuildingVolume maintenance) {
        public Workplace {
            name(id);
            name(room);
            Objects.requireNonNull(input);
            Objects.requireNonNull(worker);
            Objects.requireNonNull(socket);
            Objects.requireNonNull(equipmentType);
            Objects.requireNonNull(facing);
            Objects.requireNonNull(equipment);
            Objects.requireNonNull(maintenance);
            if (input.equals(worker)
                    || !worker.equals(socket.offset(facing.x(), 0, facing.z()))
                    || Math.abs(worker.x() - socket.x()) + Math.abs(worker.z() - socket.z()) != 1
                    || worker.y() != socket.y()
                    || !equipment.contains(socket.offset(0, 1, 0))
                    || !maintenance.contains(worker.offset(0, 1, 0)))
                throw new IllegalArgumentException("invalid authored workplace " + id);
        }
    }

    /** A complete physical bed, independent of occupancy, sleep AI or access reservations. */
    public record Bed(String id, BlockPosition head, BlockPosition foot,
            io.farfrontier.palemirror.frontier.v3.model.rest.RestPlaceProfile profile) {
        public Bed(String id,BlockPosition head,BlockPosition foot) { this(id,head,foot,io.farfrontier.palemirror.frontier.v3.model.rest.RestPlaceProfile.ordinary()); }
        public Bed {
            name(id); Objects.requireNonNull(head); Objects.requireNonNull(foot); Objects.requireNonNull(profile);
            if (head.y() != foot.y() || Math.abs(head.x()-foot.x())+Math.abs(head.z()-foot.z()) != 1)
                throw new IllegalArgumentException("bed must declare both adjacent halves");
        }
    }

    public BlockPosition anchor(String name) {
        var value=anchors.get(name);
        if(value==null) throw new IllegalArgumentException("building lacks declared anchor "+name);
        return value;
    }

    public record Block(NavigationBlockState state, String repairItem, GrayboxSemanticPart part, Optional<AuthoredBlockData> data) {
        public Block(NavigationBlockState state, String repairItem, GrayboxSemanticPart part) { this(state, repairItem, part, Optional.empty()); }
        public Block {
            data = Objects.requireNonNull(data);
            Objects.requireNonNull(state);
            Objects.requireNonNull(part);
            if (repairItem == null || !repairItem.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("block lacks declared repair item");
        }
    }

    public BuildingDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(representation);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(site);
        Objects.requireNonNull(front);
        Objects.requireNonNull(parcel);
        Objects.requireNonNull(fabric);
        Objects.requireNonNull(primaryEntrance);
        Objects.requireNonNull(primaryWorkplace);
        functions = Set.copyOf(functions);
        rooms = ordered(rooms, 64);
        zones = ordered(zones, 128);
        passages = ordered(passages, 64);
        workplaces = ordered(workplaces, 32);
        anchors = ordered(anchors, 128);
        beds = ordered(beds, 96);
        seats = ordered(seats, 64);
        blocks = Map.copyOf(blocks);
        var seatCells = new HashSet<BlockPosition>();
        for (var entry : seats.entrySet()) {
            var seat = entry.getValue();
            var chair = blocks.get(seat.chair());
            var table = blocks.get(seat.table());
            var zone = zones.get(seat.zoneId());
            if (!entry.getKey().equals(seat.id()) || !seatCells.add(seat.chair())
                    || chair == null || !chair.state().kind().equals(seat.chairBlock())
                    || !seat.facing().name().toLowerCase(Locale.ROOT).equals(chair.state().properties().get("facing"))
                    || table == null || !table.state().kind().equals(seat.tableBlock())
                    || zone == null || (zone.use() != ZoneUse.DINING && zone.use() != ZoneUse.REST) || !zone.volume().contains(seat.chair())
                    || !parcel.contains(seat.approach()) || !parcel.contains(seat.egress()))
                throw new IllegalArgumentException("invalid authored seating declaration " + entry.getKey());
        }
        if (version < 1
                || sha256 == null
                || !sha256.matches("[a-f0-9]{64}")
                || functions.isEmpty()
                || !parcel.contains(fabric)
                || blocks.size() > 65_536)
            throw new IllegalArgumentException("invalid authored building declaration");
        for(var entry:anchors.entrySet()) {
            name(entry.getKey());
            if(!parcel.contains(entry.getValue())) throw new IllegalArgumentException("anchor escaped building parcel");
        }
        var bedCells=new HashSet<BlockPosition>();
        for(var entry:beds.entrySet()) {
            var bed=entry.getValue();var head=blocks.get(bed.head());var foot=blocks.get(bed.foot());
            if(!entry.getKey().equals(bed.id()) || head==null || foot==null || !head.state().kind().endsWith("_bed")
                    || !head.state().kind().equals(foot.state().kind()) || !"head".equals(head.state().properties().get("part"))
                    || !"foot".equals(foot.state().properties().get("part")) || !bedCells.add(bed.head()) || !bedCells.add(bed.foot()))
                throw new IllegalArgumentException("invalid or overlapping physical bed halves");
            var direction=FacilityFacing.valueOf(head.state().properties().get("facing").toUpperCase(Locale.ROOT));
            if(!head.state().properties().get("facing").equals(foot.state().properties().get("facing"))
                    || !bed.head().equals(bed.foot().offset(direction.x(),0,direction.z())))
                throw new IllegalArgumentException("bed parts disagree with declared orientation");
        }
        for (var entry : rooms.entrySet())
            if (!entry.getKey().equals(entry.getValue().id())
                    || !parcel.contains(entry.getValue().volume()))
                throw new IllegalArgumentException("invalid room declaration");
        for (var entry : zones.entrySet()) {
            var z = entry.getValue();
            var room = rooms.get(z.room());
            if (!entry.getKey().equals(z.id())
                    || room == null
                    || !room.volume().contains(z.volume()))
                throw new IllegalArgumentException("zone escaped its declared room");
        }
        for (var entry : passages.entrySet()) {
            var p = entry.getValue();
            if (!entry.getKey().equals(p.id()))
                throw new IllegalArgumentException("passage identity mismatch");
            for (var end : List.of(p.from(), p.to()))
                if (end.kind() == EndpointKind.ROOM && !rooms.containsKey(end.room().orElseThrow()))
                    throw new IllegalArgumentException("passage names unknown room");
            if (p.supports().stream().anyMatch(point -> !parcel.contains(point)))
                throw new IllegalArgumentException("passage escaped parcel");
        }
        if (representation == Representation.AUTHORED) {
            var reached = new HashSet<String>();
            for (var p : passages.values()) {
                if (p.from().kind() == EndpointKind.EXTERIOR && p.to().kind() == EndpointKind.ROOM)
                    reached.add(p.to().room().orElseThrow());
                if (p.to().kind() == EndpointKind.EXTERIOR && p.from().kind() == EndpointKind.ROOM)
                    reached.add(p.from().room().orElseThrow());
                if (p.from().kind() == EndpointKind.ROOM
                                && !rooms.get(p.from().room().orElseThrow())
                                        .volume()
                                        .contains(p.supports().getFirst().offset(0, 1, 0))
                        || p.to().kind() == EndpointKind.ROOM
                                && !rooms.get(p.to().room().orElseThrow())
                                        .volume()
                                        .contains(p.supports().getLast().offset(0, 1, 0)))
                    throw new IllegalArgumentException(
                            "passage endpoint lies outside its declared room");
            }
            boolean changed;
            do {
                changed = false;
                for (var p : passages.values())
                    if (p.from().kind() == EndpointKind.ROOM
                            && p.to().kind() == EndpointKind.ROOM) {
                        var from = p.from().room().orElseThrow();
                        var to = p.to().room().orElseThrow();
                        if (reached.contains(from)) changed |= reached.add(to);
                        if (reached.contains(to)) changed |= reached.add(from);
                    }
            } while (changed);
            if (!reached.containsAll(rooms.keySet()))
                throw new IllegalArgumentException("room disconnected from all exterior passages");
        }
        for (var entry : workplaces.entrySet()) {
            var w = entry.getValue();
            var room = rooms.get(w.room());
            if (!entry.getKey().equals(w.id())
                    || room == null
                    || !room.volume().contains(w.worker().offset(0, 1, 0))
                    || !parcel.contains(w.equipment())
                    || !parcel.contains(w.maintenance()))
                throw new IllegalArgumentException("workplace escaped declared room/parcel");
        }
        if (primaryEntrance.isPresent()
                && (!passages.containsKey(primaryEntrance.orElseThrow())
                        || passages.get(primaryEntrance.orElseThrow()).from().kind()
                                != EndpointKind.EXTERIOR))
            throw new IllegalArgumentException("primary entry is not a declared exterior passage");
        if (primaryWorkplace.isPresent() && !workplaces.containsKey(primaryWorkplace.orElseThrow()))
            throw new IllegalArgumentException("primary workplace is absent");
        if (representation == Representation.AUTHORED
                && (blocks.isEmpty() || primaryEntrance.isEmpty()))
            throw new IllegalArgumentException("authored building lacks fabric or entrance");
        if (blocks.keySet().stream().anyMatch(point -> !fabric.contains(point)))
            throw new IllegalArgumentException("authored block escaped fabric");
        if (representation == Representation.AUTHORED) for (var entry : blocks.entrySet())
            if (entry.getKey().y() <= site.groundY() && entry.getValue().state().kind().equals("minecraft:air")
                    && site.excavations().stream().noneMatch(volume -> volume.contains(entry.getKey())))
                throw new IllegalArgumentException("underground authored void lacks a site excavation declaration");
        for (var zone : zones.values())
            if (zone.use() == ZoneUse.TRANSIT)
                for (var workplace : workplaces.values())
                    if (zone.volume().overlaps(workplace.equipment()))
                        throw new IllegalArgumentException(
                                "equipment occupies declared circulation");
    }

    public BuildingPlacement placement(BlockPosition anchor, FacilityFacing facing) {
        return new BuildingPlacement(anchor, origin, front, facing);
    }

    public Workplace primaryWorkplaceDefinition() {
        return workplaces.get(
                primaryWorkplace.orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "building has no declared primary workplace")));
    }

    public Passage primaryEntranceDefinition() {
        return passages.get(
                primaryEntrance.orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "building has no declared primary entrance")));
    }

    private static void name(String value) {
        if (value == null || !value.matches("[a-z0-9_./-]{1,96}"))
            throw new IllegalArgumentException("invalid local building annotation id");
    }

    private static <K, V> Map<K, V> ordered(Map<K, V> values, int limit) {
        if (values.size() > limit)
            throw new IllegalArgumentException("unbounded building annotations");
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
