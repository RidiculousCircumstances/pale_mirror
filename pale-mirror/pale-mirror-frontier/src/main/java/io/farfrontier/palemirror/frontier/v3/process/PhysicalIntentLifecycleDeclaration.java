package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;

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
                                          Set<PhysicalIntentKind> kinds,
                                          Set<PhysicalIntentRoleSchema> schemas,
                                          int maxUnresolved, int maxResolvedRetention) {
    static final int VERSION = 1;
    /** Sixteen physical owners each receive one 256-intent retained-work share of the 4,096 bound. */
    static final int MAX_PER_OWNER = 256;

    PhysicalIntentLifecycleDeclaration {
        owner = Objects.requireNonNull(owner, "physical lifecycle declaration owner");
        kinds = Set.copyOf(Objects.requireNonNull(kinds, "physical lifecycle declaration kinds"));
        schemas = Set.copyOf(Objects.requireNonNull(schemas, "physical lifecycle declaration schemas"));
        if (version != VERSION) throw new IllegalArgumentException("unsupported physical lifecycle declaration version: " + version);
        if (maxUnresolved < 0 || maxResolvedRetention < 0) {
            throw new IllegalArgumentException("physical lifecycle retention limits cannot be negative");
        }
        if (kinds.isEmpty() != schemas.isEmpty() || (kinds.isEmpty() && (maxUnresolved != 0 || maxResolvedRetention != 0))
                || (!kinds.isEmpty() && (maxUnresolved == 0 || maxResolvedRetention == 0))) {
            throw new IllegalArgumentException("physical lifecycle declaration has partial physical dimensions");
        }
        for (PhysicalIntentRoleSchema schema : schemas) {
            if (schema.owner() != owner || !kinds.contains(schema.kind())) {
                throw new IllegalArgumentException("physical lifecycle declaration has foreign owner/kind/schema tuple");
            }
        }
        for (PhysicalIntentKind kind : kinds) {
            boolean represented = false;
            for (PhysicalIntentRoleSchema schema : schemas) {
                if (schema.kind() == kind) { represented = true; break; }
            }
            if (!represented) {
                throw new IllegalArgumentException("physical lifecycle declaration has a kind without an exact role schema");
            }
        }
    }

    /** Family construction supplies every identity dimension; this factory never discovers one. */
    static PhysicalIntentLifecycleDeclaration physical(PhysicalIntentLifecycleOwner owner, Set<PhysicalIntentKind> kinds,
                                                        Set<PhysicalIntentRoleSchema> schemas) {
        return new PhysicalIntentLifecycleDeclaration(owner, VERSION, kinds, schemas, MAX_PER_OWNER, MAX_PER_OWNER);
    }

    boolean admits(PhysicalIntent intent) {
        return intent.lifecycleOwner() == owner && kinds.contains(intent.kind()) && schemas.contains(intent.roles().schema())
                && intent.roles().schema().kind() == intent.kind();
    }

    boolean unresolved(PhysicalIntent intent) {
        return intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING
                || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART || intent.status() == PhysicalIntentStatus.CONFLICTED;
    }

    String canonicalMaterial() {
        return owner.stableId() + ':' + version + ':' + kinds.stream().map(Enum::name).sorted()
                .collect(java.util.stream.Collectors.joining(",")) + ':' + maxUnresolved + ':' + maxResolvedRetention + ':'
                + schemas.stream().map(PhysicalIntentRoleSchema::wireTag).sorted()
                .map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }
}
