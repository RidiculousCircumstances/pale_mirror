package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Arrays;

/**
 * Closed production vocabulary for read-only Frontier v3 diagnostics.
 *
 * <p>Command registration and the ordinary test pilot consume this same inventory. A newly
 * rendered diagnostic cannot therefore be admitted by one side and rejected by the other because
 * a second string whitelist was missed. This is an admission vocabulary only: it grants neither
 * diagnostic mutation nor process authority.</p>
 */
public enum FrontierV3DiagnosticView {
    SUMMARY("summary", false),
    /** One permission-gated operator status cut with the recoverable command receipt history. */
    STATUS("status", false),
    PERFORMANCE("performance", false),
    /** Closed owner/schema/retention composition and derived pressure; read-only. */
    PHYSICAL_LIFECYCLE("physical_lifecycle", false),
    /** One read-only whole-path counter snapshot for retained structural and infection projection work. */
    PROJECTION_WORK("projection_work", false),
    AFTERMATH("aftermath", true),
    PROCESS("process", true),
    /** Bounded read-only inventory of every current settlement harvest process. */
    PROCESS_INVENTORY("process_inventory", true),
    SITE("site", true),
    SETTLEMENT("settlement", true),
    /** One bounded settlement resident set for a first-visibility audit; read-only and permission-gated. */
    SETTLEMENT_POPULATION("settlement_population", true),
    HIVE("hive", true),
    HIVE_TRANSFER("hive_transfer", true),
    HIVE_MOBILIZATION("hive_mobilization", true),
    ACTOR("actor", true),
    ITEM("item", true),
    RESOURCE("resource", true),
    /** One authenticated player's current physical stack, fenced by its canonical resource account. */
    PLAYER_RESOURCE("player_resource", true),
    CONTAINER("container", true),
    REFERENCE_CONTAINER("reference_container", true),
    MARKET_ORDER("market_order", true),
    OPERATION("operation", true),
    ROUTE_CONSTRUCTION("route_construction", true),
    ROUTE_MAINTENANCE("route_maintenance", true),
    ROUTE_TOPOLOGY("route_topology", true),
    PHYSICAL_DELTA("physical_delta", true),
    SCENE("scene", true),
    INTENT("intent", true),
    TRACE("trace", true),
    TRANSIT("transit", true),
    RECOVERY("recovery", true),
    MEDICAL("medical", true),
    TRAVERSAL_FOUNDRY("traversal_foundry", true),
    HIVE_FOUNDRY("hive_foundry", true),
    /** One naturally exposed chunk's static-before-dynamic first-visibility receipt. */
    FIRST_VISIBILITY("first_visibility", true);

    private final String token;
    private final boolean requiresId;

    FrontierV3DiagnosticView(String token, boolean requiresId) {
        this.token = token;
        this.requiresId = requiresId;
    }

    public String token() { return token; }
    public boolean requiresId() { return requiresId; }

    public static FrontierV3DiagnosticView require(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Frontier v3 diagnostic view is required");
        return Arrays.stream(values()).filter(value -> value.token.equals(token)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown Frontier v3 diagnostic view: " + token));
    }

    public static boolean accepts(String token, String id) {
        try {
            FrontierV3DiagnosticView view = require(token);
            return !view.requiresId || (id != null && !id.isBlank());
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }
}
