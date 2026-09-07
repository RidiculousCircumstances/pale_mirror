package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneLeaseHandoff;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import org.spongepowered.asm.mixin.Mixins;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Pilot-only exact crash rendezvous at semantic crash boundaries.
 *
 * <p>This class is compiled only into the moddev {@code pilot} source set.  It is absent from
 * the distributable JAR, is created only by the isolated fixture bootstrap, and the normal
 * server has neither an arm nor a property-controlled fault path.</p>
 */
final class FrontierV3CrashBoundaryProbe {
    static final String LEASE_RECORDED_BEFORE_PHYSICAL_MATERIALIZATION = "lease_recorded_before_physical_materialization";
    static final String PHYSICAL_EFFECT_VISIBLE_BEFORE_TYPED_OBSERVATION = "physical_effect_visible_before_typed_observation";
    static final String TYPED_OBSERVATION_DURABLE_BEFORE_NEXT_PROCESS_CHECKPOINT = "typed_observation_durable_before_next_process_checkpoint";
    static final String HOT_CHECKPOINT_DURABLE_BEFORE_DRAIN_RELEASE = "hot_checkpoint_durable_before_drain_release";
    static final String RELEASE_DURABLE_BEFORE_COLD_RESUMPTION = "release_durable_before_cold_resumption";
    private static final java.util.Map<String, String> DURABLE_PAYLOADS = java.util.Map.of(
            LEASE_RECORDED_BEFORE_PHYSICAL_MATERIALIZATION, "frontier.resource_site_harvest_scene_lease_prepared",
            TYPED_OBSERVATION_DURABLE_BEFORE_NEXT_PROCESS_CHECKPOINT, "frontier.resource_site_harvest_progressed",
            HOT_CHECKPOINT_DURABLE_BEFORE_DRAIN_RELEASE, "frontier.resource_site_harvest_hot_traversal_advanced",
            RELEASE_DURABLE_BEFORE_COLD_RESUMPTION, "frontier.scene_lease_released_v2");
    private static final String PILOT_RUN_ID = "pale_mirror.frontier_v3.pilot.run_id";
    private static final String BOUNDARY = "pale_mirror.frontier_v3.pilot.crash.boundary";
    private static final String OWNER = "pale_mirror.frontier_v3.pilot.crash.owner";
    private static final String REVISION = "pale_mirror.frontier_v3.pilot.crash.revision";
    private static final String PAYLOAD = "pale_mirror.frontier_v3.pilot.crash.payload";
    /** A pilot arm may bind the exact revision only when its named semantic boundary is reached. */
    private static final String OBSERVED_AT_BOUNDARY = "observed_at_boundary";
    private static final long PARK_NANOS = 100_000_000L;

    private final Arm arm;
    private final Consumer<String> marker;
    /** Captured only by the pilot mixin after Minecraft has actually accepted the block write. */
    private final ThreadLocal<PhysicalCropEffect> visibleCropEffect = new ThreadLocal<>();
    /** One fresh-run prepare witness for the only job a release arm may name. */
    private LeaseWitness releaseWitness;
    /** Once two prepares name the armed job, further prepares cannot make that evidence unambiguous. */
    private boolean releaseWitnessAmbiguous;
    private boolean fired;

    private FrontierV3CrashBoundaryProbe(Arm arm, Consumer<String> marker) {
        this.arm = arm;
        this.marker = Objects.requireNonNull(marker, "crash marker");
    }

    static FrontierV3CrashBoundaryProbe fromSystemProperties() {
        return from(property -> System.getProperty(property, ""), value -> {
            // The supervising crash controller owns a pipe, not the asynchronous game log.
            // Flush the exact nonce-bound rendezvous before parking the server thread: otherwise
            // a logger drain can be stalled behind the intentionally parked tick and turn an
            // observed durable boundary into a watchdog crash rather than the declared JVM kill.
            System.out.println(value);
            System.out.flush();
            PaleMirrorMod.LOGGER.info("{}", value);
        });
    }

    static FrontierV3CrashBoundaryProbe from(Function<String, String> properties, Consumer<String> marker) {
        Objects.requireNonNull(properties, "crash properties");
        String boundary = properties.apply(BOUNDARY);
        if (blank(boundary)) return new FrontierV3CrashBoundaryProbe(null, marker);
        Arm arm = new Arm(required(properties.apply(PILOT_RUN_ID), "pilot run id"), required(boundary, "crash boundary"),
                required(properties.apply(OWNER), "crash owner"), parseRevision(properties.apply(REVISION)),
                required(properties.apply(PAYLOAD), "crash payload"));
        return new FrontierV3CrashBoundaryProbe(arm, marker);
    }

    /** Called exactly after a successful durable append and before canonical installation. */
    void afterDurableAppend(TransactionRecord transaction) {
        Objects.requireNonNull(transaction, "durable transaction");
        if (arm == null || fired || !matchesDurable(transaction)) return;
        announceAndPark(arm, transaction.revision().value());
    }

    /** Records one actual crop write; it never changes the Minecraft or canonical state. */
    void cropEffectBecameVisible(ResourceSiteHarvestJob job, BlockPosition cropSlot) {
        visibleCropEffect.set(new PhysicalCropEffect(Objects.requireNonNull(job, "harvest job").siteId().value(),
                Objects.requireNonNull(cropSlot, "crop slot")));
    }

    /**
     * Called by the pilot-only mixin on the caller side of the write, before the production
     * executor submits {@code ResourceSiteHarvestProgressed}.  The current durable checkpoint
     * supplies the exact revision; the owner remains the production resource-site aggregate.
     */
    void afterVisibleCropEffectBeforeObservation(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                 ResourceSiteHarvestJob job) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(job, "harvest job");
        PhysicalCropEffect effect = visibleCropEffect.get();
        visibleCropEffect.remove();
        if (effect == null || !effect.owner().equals(job.siteId().value()) || arm == null || fired) return;
        long revision = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("pilot v3 runtime is inactive"))
                .revision().value();
        if (!arm.matchesPhysical(effect, revision)) return;
        announceAndPark(arm, revision);
    }

    private void announceAndPark(Arm matched, long observedRevision) {
        fired = true;
        marker.accept("PMV3_CRASH_BOUNDARY runId=" + matched.runId + " boundary=" + matched.boundary + " owner=" + matched.owner
                + " revision=" + observedRevision + " payload=" + matched.payload);
        while (!Thread.currentThread().isInterrupted()) LockSupport.parkNanos(PARK_NANOS);
        throw new IllegalStateException("test-only crash rendezvous was interrupted instead of externally terminated");
    }

    boolean armed() { return arm != null; }
    boolean matches(TransactionRecord transaction) {
        return arm != null && matchesDurable(Objects.requireNonNull(transaction, "durable transaction"));
    }

    static void requireConfiguredMixinApplication() {
        requireConfiguredMixinApplication(property -> System.getProperty(property, ""), mixinClass -> Mixins.getMixinsForClass(mixinClass).stream()
                .map(info -> info.getClassName()).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    /**
     * Mixin 0.8.7 records a successful post-apply on the mixin class's {@code ClassInfo}, not
     * the target class cache entry. The declared pilot mixins each have one exact target, which
     * their classfile regression guards separately enforce.
     */
    static void requireConfiguredMixinApplication(Function<String, String> properties,
                                                  Function<String, Set<String>> appliedMixins) {
        Objects.requireNonNull(properties, "crash properties");
        Objects.requireNonNull(appliedMixins, "applied mixins");
        String boundary = properties.apply(BOUNDARY);
        if (blank(boundary)) return;
        MixinTarget target = mixinTarget(boundary);
        requireAppliedMixin(boundary, appliedMixins.apply(target.mixinClass()));
    }

    static void requireAppliedMixin(String boundary, Set<String> appliedMixinClasses) {
        MixinTarget target = mixinTarget(boundary);
        if (!Objects.requireNonNull(appliedMixinClasses, "applied mixin classes").contains(target.mixinClass())) {
            throw new IllegalStateException("pilot crash mixin was not applied to " + target.targetClass().getName());
        }
    }

    private boolean matchesDurable(TransactionRecord transaction) {
        if (!DURABLE_PAYLOADS.containsKey(arm.boundary)) return false;
        if (RELEASE_DURABLE_BEFORE_COLD_RESUMPTION.equals(arm.boundary)) return matchesRelease(transaction);
        return arm.matchesDurable(transaction);
    }

    private boolean matchesRelease(TransactionRecord transaction) {
        observeReleaseWitness(transaction);
        if (releaseWitness == null) return false;
        ReleaseAssessment assessment = arm.assessRelease(transaction, releaseWitness);
        if (assessment == ReleaseAssessment.MATCH) {
            if (releaseWitnessAmbiguous) {
                clearReleaseWitness();
                return false;
            }
            clearReleaseWitness();
            return true;
        }
        if (assessment == ReleaseAssessment.CONTRADICTORY) clearReleaseWitness();
        return false;
    }

    private void observeReleaseWitness(TransactionRecord transaction) {
        for (FrontierEvent event : transaction.events()) {
            io.farfrontier.palemirror.frontier.v3.model.SceneLease lease = resourceSiteHarvestAdmissionLease(event);
            if (lease == null || !FrontierSceneBehaviors.isResourceSiteHarvest(lease)) continue;
            SubjectId job = FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId();
            if (!job.value().equals(arm.owner)) continue;
            LeaseWitness observed = new LeaseWitness(transaction.worldId(), lease.id(), job, event.subject());
            if (releaseWitnessAmbiguous) continue;
            if (releaseWitness == null) releaseWitness = observed;
            else releaseWitnessAmbiguous = true;
        }
    }

    private static io.farfrontier.palemirror.frontier.v3.model.SceneLease resourceSiteHarvestAdmissionLease(FrontierEvent event) {
        if (event.payload() instanceof ResourceSiteHarvestSceneLeasePrepared prepared) return prepared.lease();
        if (event.payload() instanceof ResourceSiteHarvestSceneLeaseHandoff handoff) return handoff.lease();
        return null;
    }

    private void clearReleaseWitness() {
        releaseWitness = null;
        releaseWitnessAmbiguous = false;
    }

    private static MixinTarget mixinTarget(String boundary) {
        return switch (boundary) {
            case LEASE_RECORDED_BEFORE_PHYSICAL_MATERIALIZATION,
                    TYPED_OBSERVATION_DURABLE_BEFORE_NEXT_PROCESS_CHECKPOINT,
                    HOT_CHECKPOINT_DURABLE_BEFORE_DRAIN_RELEASE,
                    RELEASE_DURABLE_BEFORE_COLD_RESUMPTION -> new MixinTarget(FrontierStoreTransactionCommitter.class,
                    "io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3DurableCrashWindowMixin");
            case PHYSICAL_EFFECT_VISIBLE_BEFORE_TYPED_OBSERVATION -> new MixinTarget(FrontierV3ResourceSiteHarvestSceneExecutor.class,
                    "io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3HarvestCrashWindowMixin");
            default -> throw new IllegalArgumentException("test-only crash arm names a boundary without an installed pilot hook");
        };
    }

    private record Arm(String runId, String boundary, String owner, long revision, String payload) {
        private Arm {
            if (!runId.matches("[0-9a-f-]{36}") || !stable(boundary) || !stable(owner) || revision < -1L || !stable(payload)) {
                throw new IllegalArgumentException("test-only crash arm is malformed");
            }
            String durablePayload = DURABLE_PAYLOADS.get(boundary);
            if (durablePayload == null && !PHYSICAL_EFFECT_VISIBLE_BEFORE_TYPED_OBSERVATION.equals(boundary)) {
                throw new IllegalArgumentException("test-only crash arm names a boundary without an installed pilot hook");
            }
            String expectedPayload = durablePayload == null ? "frontier.resource_site_harvest_progressed" : durablePayload;
            if (!expectedPayload.equals(payload)) {
                throw new IllegalArgumentException("test-only crash arm payload does not match semantic boundary");
            }
        }
        private boolean matchesDurable(TransactionRecord transaction) {
            if (!DURABLE_PAYLOADS.containsKey(boundary)) return false;
            if (LEASE_RECORDED_BEFORE_PHYSICAL_MATERIALIZATION.equals(boundary)) {
                return (revision == -1L || transaction.revision().value() == revision) && transaction.events().stream()
                        // PREPARED is the durable lease-custody record before any physical
                        // materialization. An optional later ambient handoff is not guaranteed
                        // on the direct admission path and cannot replace that boundary.
                        .anyMatch(event -> event.payload() instanceof ResourceSiteHarvestSceneLeasePrepared prepared
                                && FrontierSceneBehaviors.isResourceSiteHarvest(prepared.lease())
                                && owner.equals(FrontierSceneBehaviors.resourceSiteHarvest(prepared.lease()).jobId().value()));
            }
            return (revision == -1L || transaction.revision().value() == revision) && transaction.events().stream().anyMatch(this::matches);
        }

        private ReleaseAssessment assessRelease(TransactionRecord transaction, LeaseWitness witness) {
            FrontierEvent release = transaction.events().stream().filter(event -> event.payload() instanceof SceneLeaseReleased).findFirst().orElse(null);
            if (release == null || !release.payload().type().equals(payload)) return ReleaseAssessment.NONE;
            if (!(release.payload() instanceof SceneLeaseReleased released)) return ReleaseAssessment.NONE;
            if (!released.leaseId().equals(witness.leaseId())) {
                return transaction.worldId().equals(witness.worldId()) && release.subject().equals(witness.owner())
                        ? ReleaseAssessment.CONTRADICTORY : ReleaseAssessment.NONE;
            }
            if (revision != -1L && transaction.revision().value() != revision) return ReleaseAssessment.CONTRADICTORY;
            if (!transaction.worldId().equals(witness.worldId()) || !released.leaseId().equals(witness.leaseId()) || !release.subject().equals(witness.owner())) {
                return ReleaseAssessment.CONTRADICTORY;
            }
            // A no-work release has already supplied the exact engine binding at command
            // admission.  Its durable transaction changes only lease custody; emitting a
            // schedule replacement here would manufacture a second deadline from release time.
            return transaction.events().size() == 1 && transaction.events().getFirst() == release
                    ? ReleaseAssessment.MATCH : ReleaseAssessment.CONTRADICTORY;
        }
        private boolean matchesPhysical(PhysicalCropEffect effect, long currentRevision) {
            return PHYSICAL_EFFECT_VISIBLE_BEFORE_TYPED_OBSERVATION.equals(boundary) && (revision == -1L || revision == currentRevision)
                    && owner.equals(effect.owner()) && payload.equals("frontier.resource_site_harvest_progressed");
        }
        private boolean matches(FrontierEvent event) {
            return event.subject().value().equals(owner) && event.payload().type().equals(payload);
        }
    }

    private record PhysicalCropEffect(String owner, BlockPosition cropSlot) { }
    private record LeaseWitness(io.farfrontier.palemirror.frontier.v3.api.WorldId worldId, SceneLeaseId leaseId, SubjectId job, SubjectId owner) { }
    private record MixinTarget(Class<?> targetClass, String mixinClass) { }
    private enum ReleaseAssessment { NONE, CONTRADICTORY, MATCH }

    private static long parseRevision(String value) {
        if (OBSERVED_AT_BOUNDARY.equals(value)) return -1L;
        try { return Long.parseLong(required(value, "crash revision")); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("test-only crash revision is malformed", invalid); }
    }
    private static String required(String value, String name) {
        if (blank(value)) throw new IllegalArgumentException("test-only " + name + " is required");
        return value;
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean stable(String value) { return value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{2,127}"); }
}
