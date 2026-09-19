package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One owner-supplied durable lifecycle contract.
 *
 * <p>The declaration deliberately repeats neither a family implementation nor a relationship
 * graph.  It closes the three identities which select a physical lifecycle (owner, kind and
 * nominal role schema), and supplies the bounded unresolved/resolved retention pressure that
 * operators may inspect before a producer admits another obligation.</p>
 */
record PhysicalIntentLifecycleDeclaration(PhysicalIntentLifecycleOwner owner, int version,
                                          Set<PhysicalIntentRoleSchema> schemas,
                                          int maxUnresolved, int maxResolvedRetention) {
    static final int VERSION = 1;
    /** Matches the canonical world intent cap; composition must not silently narrow existing throughput. */
    static final int MAX_PER_OWNER = 4_096;

    PhysicalIntentLifecycleDeclaration {
        owner = Objects.requireNonNull(owner, "physical lifecycle declaration owner");
        schemas = Set.copyOf(Objects.requireNonNull(schemas, "physical lifecycle declaration schemas"));
        if (version != VERSION) throw new IllegalArgumentException("unsupported physical lifecycle declaration version: " + version);
        if (maxUnresolved < 0 || maxResolvedRetention < 0) {
            throw new IllegalArgumentException("physical lifecycle retention limits cannot be negative");
        }
    }

    static PhysicalIntentLifecycleDeclaration declared(PhysicalIntentLifecycleOwner owner,
                                                        Set<PhysicalIntentKind> kinds) {
        Objects.requireNonNull(owner, "physical lifecycle declaration owner");
        kinds = Set.copyOf(Objects.requireNonNull(kinds, "physical lifecycle declaration kinds"));
        Set<PhysicalIntentRoleSchema> schemas = EnumSet.noneOf(PhysicalIntentRoleSchema.class);
        for (PhysicalIntentRoleSchema schema : PhysicalIntentRoleSchema.values()) {
            if (schema.owner() == owner && kinds.contains(schema.kind())) schemas.add(schema);
        }
        return new PhysicalIntentLifecycleDeclaration(owner, VERSION, schemas,
                kinds.isEmpty() ? 0 : MAX_PER_OWNER, kinds.isEmpty() ? 0 : MAX_PER_OWNER);
    }

    static Set<PhysicalIntentRoleSchema> expectedSchemas(PhysicalIntentLifecycleOwner owner) {
        Set<PhysicalIntentRoleSchema> schemas = EnumSet.noneOf(PhysicalIntentRoleSchema.class);
        for (PhysicalIntentRoleSchema schema : PhysicalIntentRoleSchema.values()) {
            if (schema.owner() == owner) schemas.add(schema);
        }
        return Set.copyOf(schemas);
    }

    boolean admits(PhysicalIntent intent) {
        return intent.lifecycleOwner() == owner && schemas.contains(intent.roles().schema())
                && intent.roles().schema().kind() == intent.kind();
    }

    boolean unresolved(PhysicalIntent intent) {
        return intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING
                || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART;
    }

    String canonicalMaterial() {
        return owner.stableId() + ':' + version + ':' + maxUnresolved + ':' + maxResolvedRetention + ':'
                + schemas.stream().map(PhysicalIntentRoleSchema::wireTag).sorted()
                .map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }
}
