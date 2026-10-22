package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionCommitter;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import java.util.Objects;

/**
 * Writes first-body permission before the canonical birth WAL append and installation.
 * Failed append leaves an unused permission, never an actor or permission to recreate
 * a used body. A retry can repeat only the identical unused identity.
 */
final class FrontierV3ActorBirthCommitter implements TransactionCommitter {
    private final WorldId world;
    private final FrontierV3AmbientCarrierLedger ledger;
    private final Runnable persist;
    private final java.util.function.Supplier<Confirmation> preparePermission;
    private final TransactionCommitter canonical;
    FrontierV3ActorBirthCommitter(WorldId world, FrontierV3AmbientCarrierLedger ledger,
                                  Runnable persist, TransactionCommitter canonical) {
        this(world, ledger, persist, () -> { persist.run(); return () -> true; }, canonical);
    }
    FrontierV3ActorBirthCommitter(WorldId world, FrontierV3AmbientCarrierLedger ledger,
            Runnable persist, java.util.function.Supplier<Confirmation> preparePermission, TransactionCommitter canonical) {
        this.world = Objects.requireNonNull(world);
        this.ledger = Objects.requireNonNull(ledger);
        this.persist = Objects.requireNonNull(persist);
        this.preparePermission = Objects.requireNonNull(preparePermission);
        this.canonical = Objects.requireNonNull(canonical);
    }
    static FrontierV3ActorBirthCommitter forLevel(net.minecraft.server.level.ServerLevel level, WorldId world,
            FrontierV3AmbientCarrierLedger ledger, TransactionCommitter canonical) {
        return new FrontierV3ActorBirthCommitter(world, ledger, () -> ledger.persist(level, world), () -> {
            var receipt = ledger.persistAsync(level, world);
            return () -> { if (!receipt.isDone()) return false; receipt.join(); return true; };
        }, canonical);
    }
    @Override public void commit(TransactionRecord transaction, Durability durability) {
        if (prepareBirthPermission(transaction, durability)) persist.run();
        canonical.commit(transaction, durability);
    }
    @Override public Confirmation prepare(TransactionRecord transaction, Durability durability) {
        if (!prepareBirthPermission(transaction, durability)) return canonical.prepare(transaction, durability);
        var permission = preparePermission.get();
        return new Confirmation() {
            private Confirmation append;
            public boolean ready() {
                if (!permission.ready()) return false;
                if (append == null) append = canonical.prepare(transaction, durability);
                return append.ready();
            }
        };
    }
    private boolean prepareBirthPermission(TransactionRecord transaction, Durability durability) {
        if (!world.equals(transaction.worldId())) throw new IllegalArgumentException("foreign birth transaction");
        var births = transaction.events().stream().flatMap(event -> event.payload().actorBirth().stream()).toList();
        if (!births.isEmpty()) {
            if (durability != Durability.DURABLE_BEFORE_EFFECT)
                throw new IllegalArgumentException("birth requires durable canonical publication");
            var actors = new java.util.HashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
            for (var birth : births) {
                if (!actors.add(birth.actorId())) throw new IllegalArgumentException("duplicate birth in transaction");
            }
            var permissions = new java.util.ArrayList<FrontierV3ActorFirstAdmission>(births.size());
            for (var birth : births) {
                var kind = switch (birth.kind()) {
                    case RESIDENT -> ActorKind.RESIDENT;
                    case BIOFORM -> ActorKind.BIOFORM;
                };
                var identity = new FrontierV3ActorFirstAdmission.Identity(birth.actorId(), kind,
                        SceneLease.deterministicEntityId(world, birth.actorId()));
                permissions.add(FrontierV3ActorFirstAdmission.neverCreated(identity));
            }
            if (!ledger.registerFirstAdmissions(permissions))
                throw new IllegalStateException("birth transaction contradicts physical history");
            return true;
        }
        return false;
    }
}
