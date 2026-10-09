package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.List;

/** Closed wire identities of physical stores, supplied by their composition, not inferred. */
enum FrontierV3PhysicalStoreKind {
    FIELDS(1, "pale_mirror_frontier_v3_resource_sites", List.of(new Table(1,"fieldClaims"), new Table(2,"fieldDeliveries"), new Table(3,"fieldHandProjections"), new Table(4,"fieldPlayerBreaks"),
            new Table(5,"fieldWorldChanges"), new Table(6,"fieldForeignChanges"))),
    BLOCKS(2, "pale_mirror_frontier_v3_graybox", List.of(new Table(1,"claims"), new Table(2,"worksiteCells"))),
    PLAYER_CLICKS(3, "pale_mirror_frontier_v3_depot_clicks", List.of(new Table(1,"pending"))),
    BOARDS(4, "pale_mirror_frontier_v3_object_boards", List.of(new Table(1,"claims"))),
    HOPPERS(5, "pale_mirror_frontier_v3_hopper_carriers", List.of(new Table(1,"carriers"))),
    OBSERVATIONS(6, "pale_mirror_frontier_v3_physical_observations", List.of(new Table(1,"pending"))),
    MANAGED_EFFECTS(7, "pale_mirror_frontier_v3_managed_explosions", List.of(new Table(1,"pending"))),
    ACTORS(9, "pale_mirror_frontier_v3_ambient_carriers", List.of()),
    INFECTION(8, "pale_mirror_frontier_v3_infection_overlay", List.of(new Table(1,"claims")));

    record Table(int wireTag, String name) {
        Table {
            if (wireTag <= 0 || name == null || name.isBlank()) throw new IllegalArgumentException("invalid physical table declaration");
        }
    }
    static {
        var tags = new java.util.HashSet<Integer>();
        var names = new java.util.HashSet<String>();
        for (var kind : values()) {
            if (!tags.add(kind.wireTag) || !names.add(kind.fileName)) throw new IllegalStateException("duplicate physical store declaration");
        }
    }
    Table table(String name) { return tables.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow(() -> new IllegalArgumentException("undeclared physical table: " + name)); }
    Table table(int tag) { return tables.stream().filter(t -> t.wireTag() == tag).findFirst().orElseThrow(() -> new IllegalArgumentException("unknown physical table tag: " + tag)); }
    final int wireTag;
    final String fileName;
    final List<Table> tables;
    FrontierV3PhysicalStoreKind(int wireTag, String fileName, List<Table> tables) {
        this.wireTag = wireTag; this.fileName = fileName; this.tables = List.copyOf(tables);
        var tags = new java.util.HashSet<Integer>();
        var names = new java.util.HashSet<String>();
        for (var table : tables) {
            if (!tags.add(table.wireTag) || !names.add(table.name)) throw new IllegalArgumentException("duplicate physical table declaration");
        }
    }
}
