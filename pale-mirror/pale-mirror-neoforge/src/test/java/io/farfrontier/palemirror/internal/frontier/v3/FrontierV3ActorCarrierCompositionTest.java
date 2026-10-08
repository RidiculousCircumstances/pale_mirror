package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.nio.file.Path;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3ActorCarrierCompositionTest {
    @Test
    void commonReadinessCannotPromoteAnUnobservedOrStaleBody() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:body-readiness"), 91L));
        var actor = new SubjectId("resident:1-1");
        var lease = io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess.nextLease(initial, actor,
                io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO);
        var prepared = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.prepare(initial, lease);
        var expected = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(prepared, actor);
        assertFalse(FrontierV3ActorBodyController.readyForExecution(null, prepared, java.util.List.of(expected)),
                "construction demand is not independent physical body confirmation");
        var stale = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(actor, expected.physicalEpoch() + 1L);
        assertFalse(FrontierV3ActorBodyController.readyForExecution(null, prepared, java.util.List.of(stale)),
                "a different incarnation cannot become ready through the current scope");
    }

    @Test
    void closedInventoryUsesTheCompiledFactoryBoundaryWithoutProducerOrAdopterBypass() throws Exception {
        Set<Class<?>> boundaries = FrontierV3ActorCarrierComposition.inventory().stream()
                .map(FrontierV3ActorCarrierComposition.InventoryEntry::boundaryType).collect(Collectors.toUnmodifiableSet());
        assertEquals(10, boundaries.size(), "duplicate inventory ownership is invalid");
        assertEquals(java.util.List.of(FrontierV3ActorCarrierComposition.InventoryEntry.ACTOR_BODY),
                FrontierV3ActorCarrierComposition.inventory().stream()
                        .filter(entry -> entry.role() == FrontierV3ActorCarrierComposition.Role.PRODUCER).toList(),
                "scene and ambient executors must not have a body producer capability");
        for (FrontierV3ActorCarrierComposition.InventoryEntry entry : FrontierV3ActorCarrierComposition.inventory()) {
            String bytes = classBytes(entry.boundaryType());
            assertTrue(entry.role() == FrontierV3ActorCarrierComposition.Role.PRODUCER
                            ? bytes.contains("FrontierV3ActorCarrierFactory")
                            : bytes.contains("FrontierV3ActorBodyController") || bytes.contains("FrontierV3SceneExecutor"),
                    "registered boundary does not compose through shared body admission: " + entry.boundaryType().getName());
        }
        assertEquals(Set.of(FrontierV3ActorBodyController.class.getName()),
                methodCallBoundaries(FrontierV3ActorCarrierFactory.class, "create"),
                "only the shared body controller may construct an exact actor body");
        assertEquals(Set.of(FrontierV3ActorBodyController.class.getName()),
                methodCallBoundaries(FrontierV3ActorCarryProjection.class, "prepareNew"),
                "every new body receives carried inventory at common admission, not an optional activity callback");
        assertEquals(Set.of(FrontierV3ActorBodyController.class.getName()),
                methodCallBoundaries(FrontierV3BodyPlacement.class, "select"),
                "activity executors cannot re-place a body under a new activity or scene");
        assertEquals(Set.of(FrontierV3BodyPlacement.class.getName()),
                methodCallBoundaries(FrontierV3BodyPlacement.class, "assess"),
                "production placement probes enter the common provider, not a family handoff");
        assertEquals(Set.of(FrontierV3AmbientActorExecutor.class.getName(), FrontierV3SceneExecutor.class.getName()),
                methodCallBoundaries(FrontierV3ActorBodyController.class, "materialize"),
                "every activity body demand must enter shared admission through the registered adapters");
        assertEquals(Set.of(FrontierV3SceneExecutor.class.getName()),
                methodCallBoundaries(FrontierV3SceneExecutor.class, "materializeBodiesForFixture"),
                "fixture-only translated poses cannot become a production activity-position bypass");
        assertEquals(Set.of(FrontierV3ActorBodyController.class.getName()),
                methodCallBoundaries(FrontierV3ActorFirstAdmissionBoundary.class, "admit"),
                "first insertion is not an activity-owned physical operation");
        assertEquals(Set.of(FrontierV3ActorBodyController.class.getName()),
                methodCallBoundaries(FrontierV3ActorAdoptionAdmission.class, "admit"),
                "reconstruction is not an activity-owned physical operation");
        assertEquals(Set.of(FrontierV3ActorBodyController.class.getName()),
                methodCallBoundaries(FrontierV3AmbientCarrierLedger.class, "beginBodyResidence"),
                "only the common body owner may start a new loaded residency; an activity cannot reset unload proof");
        assertEquals(Set.of(FrontierV3ActorBodyController.class.getName()),
                methodCallBoundaries(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyPresent.class, "<init>"),
                "physical admission/inspection must be reported by the common body boundary, not an activity executor");
        assertEquals(Set.of(FrontierV3ActorBodyController.class.getName()),
                methodCallBoundaries(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied.class, "<init>"),
                "physical death must carry exact body/execution evidence from the common boundary");
        assertEquals(Set.of(FrontierV3ActorBodyController.class.getName()),
                methodCallBoundaries(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.class, "<init>"),
                "family recovery cannot independently restore physical permission or position");
        assertEquals(Set.of(FrontierV3ActorBodyController.class.getName()),
                methodCallBoundaries(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyUnloaded.class, "<init>"),
                "scope closure cannot independently certify physical absence");
        assertEquals(Set.of(FrontierV3ActorBodyController.class.getName(), FrontierV3AmbientCarrierLedger.class.getName()),
                methodCallBoundaries(FrontierV3AmbientCarrierLedger.class, "fence"),
                "only the body controller and the store's own atomic batch primitive may fence an actor");
        assertTrue(methodCallBoundaries(FrontierV3AmbientCarrierLedger.class, "fenceAll").isEmpty(),
                "an old scene batch fence must not remain an active production caller");
        assertEquals(Set.of(FrontierV3ActorBodyController.class.getName(), FrontierV3ActorBodyDeparture.class.getName()),
                methodCallBoundaries(FrontierV3ActorBodyDeparture.class, "<init>"),
                "callback and no-load disk recovery both capture body-owned evidence; only its codec may hydrate it");
        assertEquals(Set.of(FrontierV3AmbientCarrierLedger.class.getName()),
                methodCallBoundaries(FrontierV3AmbientCarrierLedger.class, "recordDeparture"),
                "family departure records are read views/retained hydration, not a parallel physical capture source");
        assertEquals(Set.of(FrontierV3ServerLifecycle.class.getName()),
                methodCallBoundaries(FrontierV3ActorBodyController.class, "observeDeath"),
                "the actual death listener enters one common body observer, not a family observer");
        assertEquals(Set.of(FrontierV3ActorBodyController.class.getName()),
                methodCallBoundaries(FrontierV3ActorDeathResourceComposition.class, "prepare"),
                "pre-loot resource owners enter only after common fatality provenance validation");
        assertEquals(Set.of(FrontierV3ResidentMealDeathResources.class.getName()),
                methodCallBoundaries(io.farfrontier.palemirror.frontier.v3.model.ResidentMealResourceEffectObserved.class, "<init>"),
                "retired meal receipts have one positively indexed pre-loot observation producer");
        assertEquals(Set.of(FrontierV3ResidentMealDeathResources.class.getName()),
                methodCallBoundaries(io.farfrontier.palemirror.frontier.v3.model.ResidentMealPortionDispositionObserved.class, "<init>"),
                "retired portion dispositions are published by the food resource owner, never body/scene controllers");
        assertFalse(classBytes(FrontierV3ActorBodyController.class).contains("ResidentMeal"),
                "the common body observer cannot own food phases or nutrition");
        assertFalse(io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs().types().contains("frontier.actor_died"),
                "the obsolete scene death decoder must not remain an unversioned recovery bypass");
        assertFalse(io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs().types().contains("frontier.ambient_actor_died"),
                "the obsolete ambient death decoder must not remain an unversioned recovery bypass");
        assertFalse(classBytes(FrontierV3ActorCarrierFactory.class).contains("StackWalker"),
                "actor-carrier authority must be supplied explicitly, never inferred from the runtime call stack");
        assertSourceJoinUsesOnlyCommonBodyRecognition();
        assertEquals(Set.of(FrontierV3ActorCarrierFactory.class.getName(),
                        "io.farfrontier.palemirror.internal.adapter.VanillaAnchorAdapter",
                        "io.farfrontier.palemirror.internal.integration.crimson.CrimsonActorProfile",
                        "io.farfrontier.palemirror.internal.integration.crimson.CrimsonSiegeProfile",
                        "io.farfrontier.palemirror.internal.world.SourceGrayboxActorMaterializer"),
                directResidentOrBioformConstructionBoundaries(),
                "a production class cannot directly create a resident or bioform body outside the closed carrier boundary or a declared non-XACT adapter");
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorCarrierFactory.create(null, null, null, null),
                "an unregistered helper cannot construct an actor carrier outside the closed inventory");
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorCarrierFactory.create(
                FrontierV3ActorCarrierComposition.InventoryEntry.AMBIENT_BODY, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorCarrierFactory.create(
                FrontierV3ActorCarrierComposition.InventoryEntry.SCENE_BODY, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3SceneExecutor.materializeBodies(null, null, null, null, null),
                "an unregistered helper cannot adopt an actor carrier through another boundary");
    }
    @Test
    void canonicalProducerMustDeclareTheExactRosterKindBeforeBodyComposition() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:xact-carrier"), 71L));
        SubjectId resident = state.humanPopulation().residents().keySet().stream().findFirst().orElseThrow();
        UUID entity = FrontierV3AmbientActorExecutor.entityId(state, resident);

        FrontierV3ActorCarrierComposition.Declaration declaration = FrontierV3ActorCarrierComposition.fromCanonical(state, resident,
                ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                entity, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, 1L);

        assertEquals(resident, declaration.actorId());
        assertEquals(ActorKind.RESIDENT, declaration.kind());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorCarrierComposition.fromCanonical(state, resident,
                ActorKind.BIOFORM, FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                entity, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, 1L),
                "a body producer cannot substitute a compatible Minecraft type for the declared canonical kind");
    }

    @Test
    void declarationRequiresOwnerRepresentationRevisionAndEpochAtItsFirstBoundary() {
        SubjectId actor = new SubjectId("resident:xact-7");
        UUID entity = UUID.fromString("7f65aa31-f6d9-42f7-9e05-8d1fe830644a");
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3ActorCarrierComposition.Declaration(actor,
                ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                entity, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, -2L, 1L));
        FrontierV3ActorCarrierComposition.Declaration live = new FrontierV3ActorCarrierComposition.Declaration(actor,
                ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                entity, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, 2L);
        assertEquals(FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, live.inactiveCarrier().representation());
        assertEquals(0L, live.liveBody(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, 0L, 3L).authorityRevision());
    }

    @Test
    void commonBirthAdmissionRequiresExactPreparedIncarnationAndUnusedDurablePermission() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:uae-body-birth"), 71L));
        SubjectId actor = initial.humanPopulation().residents().keySet().stream().findFirst().orElseThrow();
        var state = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.demand(initial, actor);
        var body = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, actor);
        UUID uuid = FrontierV3AmbientActorExecutor.entityId(state, actor);
        var declaration = FrontierV3ActorCarrierComposition.fromCanonical(state, actor, ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, uuid,
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, body.physicalEpoch());
        var binding = FrontierV3ActorOwnerBinding.body(declaration);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertEquals(FrontierV3ActorBodyController.Admission.CONFLICT,
                FrontierV3ActorBodyController.admission(state, binding, ledger, false),
                "empty lookup without retained first-insertion permission is not birth authority");
        assertTrue(ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                new FrontierV3ActorFirstAdmission.Identity(actor, ActorKind.RESIDENT, uuid))));
        assertEquals(FrontierV3ActorBodyController.Admission.FIRST,
                FrontierV3ActorBodyController.admission(state, binding, ledger, false));
        assertEquals(FrontierV3ActorBodyController.Admission.CONFLICT,
                FrontierV3ActorBodyController.admission(state, binding, ledger, true));
        var running = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.running(state, body);
        assertEquals(FrontierV3ActorBodyController.Admission.CONFLICT,
                FrontierV3ActorBodyController.admission(running, binding, ledger, false),
                "an absent lookup cannot reconstruct a still-running incarnation");
        assertTrue(ledger.beginFirstAdmission(binding));
        var recovered = FrontierV3AmbientCarrierLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertEquals(FrontierV3ActorBodyController.Admission.CONFLICT,
                FrontierV3ActorBodyController.admission(state, binding, recovered, false),
                "interrupted insertion must be recovered, not issued again");
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorBodyController.admission(state,
                FrontierV3ActorOwnerBinding.body(declaration.liveBody(declaration.owner(), 0L, body.physicalEpoch() + 1L)),
                recovered, false), "a new activity revision cannot allocate a new body epoch");
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorBodyController.admission(state,
                FrontierV3ActorOwnerBinding.body(new FrontierV3ActorCarrierComposition.Declaration(actor,
                        ActorKind.RESIDENT, declaration.owner(), new UUID(3, 4), declaration.representation(), 0L, body.physicalEpoch())),
                recovered, false), "physical identity is the canonical actor UUID, not a caller-selected body");
        assertTrue(recovered.fence(declaration.inactiveCarrier(), 7L, 7L));
        assertEquals(FrontierV3ActorFirstAdmission.Phase.ESTABLISHED, recovered.firstAdmission(actor).orElseThrow().phase(),
                "exact absence of an interrupted first incarnation establishes its history without a second birth");
        var reconstructionLedger = FrontierV3AmbientCarrierLedger.load(recovered.save(new net.minecraft.nbt.CompoundTag(), null), null);
        var next = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.demand(
                io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.released(state, body), actor);
        var nextBody = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(next, actor);
        assertEquals(body.physicalEpoch() + 1L, nextBody.physicalEpoch());
        var reconstruction = FrontierV3ActorOwnerBinding.body(declaration.liveBody(declaration.owner(), 0L, nextBody.physicalEpoch()));
        assertEquals(FrontierV3ActorBodyController.Admission.RECONSTRUCTION,
                FrontierV3ActorBodyController.admission(next, reconstruction, reconstructionLedger, false),
                "released and recovered incarnation must use the common reconstruction protocol, not first birth");
        assertEquals(FrontierV3ActorBodyController.Admission.CONFLICT,
                FrontierV3ActorBodyController.admission(next, reconstruction, reconstructionLedger, true),
                "inactive evidence plus an indexed body is concurrent custody, not a reconstruction opportunity");
        // The same canonical epoch allocation also occurs when preparation was
        // cancelled before insertion. It must not strand the unused birth permit.
        var unused = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(unused.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                new FrontierV3ActorFirstAdmission.Identity(actor, ActorKind.RESIDENT, uuid))));
        assertEquals(FrontierV3ActorBodyController.Admission.FIRST,
                FrontierV3ActorBodyController.admission(next, reconstruction, unused, false));
        assertThrows(IllegalArgumentException.class,
                () -> FrontierV3ActorBodyController.admission(next, binding, unused, false),
                "an unused first permission cannot override the current canonical epoch");
        assertTrue(unused.beginFirstAdmission(reconstruction));
        assertEquals(reconstruction, unused.firstAdmission(actor).orElseThrow().attempt().orElseThrow());
        assertFalse(unused.acknowledgeFirstAdmission(unused.firstAdmission(actor).orElseThrow(), binding));
        assertTrue(unused.acknowledgeFirstAdmission(unused.firstAdmission(actor).orElseThrow(), reconstruction));
    }

    private static void assertSourceJoinUsesOnlyCommonBodyRecognition() throws java.io.IOException {
        var recognition = new java.util.HashSet<String>();
        try (var stream = FrontierV3ServerLifecycle.class.getResourceAsStream("FrontierV3ServerLifecycle.class")) {
            new org.objectweb.asm.ClassReader(stream).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (!(name.equals("observeSourceJoin") || name.startsWith("lambda$observeSourceJoin$"))
                            || !descriptor.contains("net/minecraft/world/entity/Entity;")) return null;
                    return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                            assertFalse(owner.equals(FrontierV3SceneExecutor.class.getName().replace('.', '/'))
                                            && name.equals("recognizesDeclaration")
                                    || owner.equals(FrontierV3AmbientActorExecutor.class.getName().replace('.', '/'))
                                            && name.equals("retainsPendingJoin")
                                    || name.equals("recognizesManagedAmbientCarrier"),
                                    "a family projection cannot grant source-join permission behind the common lifetime");
                            if (owner.equals(FrontierV3ActorBodyController.class.getName().replace('.', '/'))
                                    && (name.equals("retainsRecordedBody") || name.equals("recognizesRecordedBody")))
                                recognition.add(name);
                        }
                    };
                }
            }, org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);
        }
        assertEquals(Set.of("retainsRecordedBody", "recognizesRecordedBody"), recognition);
    }

    private static String classBytes(Class<?> type) {
        return classBytesWithName(Path.of(type.getResource(type.getSimpleName() + ".class").getPath()));
    }
    private static String classBytesWithName(Path path) {
        try { return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.ISO_8859_1); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    /**
     * This is a compiled-call/constructor census, not a source-spelling search.
     * The non-XACT exceptions are independent adapter/graybox actors; none
     * declares or stamps a Frontier actor carrier.
     */
    private static Set<String> directResidentOrBioformConstructionBoundaries() {
        try {
            Path classes = Path.of(FrontierV3ActorCarrierComposition.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path packageRoot = classes.resolve("io/farfrontier/palemirror");
            try (var paths = Files.walk(packageRoot)) {
                return paths.filter(path -> path.toString().endsWith(".class"))
                        .filter(path -> {
                            String bytes = classBytesWithName(path);
                            return bytes.contains("net/minecraft/world/entity/EntityType")
                                    && (bytes.contains("VILLAGER") || bytes.contains("ZOMBIE"))
                                    && callsBodyConstruction(path)
                                    && !bytes.contains("net/neoforged/neoforge/gametest/GameTestHolder");
                        })
                        .map(path -> classes.relativize(path).toString().replace('/', '.').replace('\\', '.').replaceAll("\\.class$", ""))
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (java.io.IOException | URISyntaxException failure) { throw new IllegalStateException(failure); }
    }
    private static boolean callsBodyConstruction(Path path) {
        boolean[] found = { false };
        try {
            new org.objectweb.asm.ClassReader(Files.readAllBytes(path)).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor,
                                                                             String signature, String[] exceptions) {
                    return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                            if (owner.equals("net/minecraft/world/entity/EntityType") && method.equals("create")) found[0] = true;
                            if (method.equals("<init>") && (owner.equals("net/minecraft/world/entity/npc/Villager")
                                    || owner.equals("net/minecraft/world/entity/monster/Zombie"))) found[0] = true;
                        }
                    };
                }
            }, org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        return found[0];
    }
    static Set<String> methodCallBoundaries(Class<?> target, String method) {
        return methodCallBoundaries(target, method, descriptor -> true);
    }
    static Set<String> methodCallBoundaries(Class<?> target, String method,
                                           java.util.function.Predicate<String> descriptorPredicate) {
        try {
            Path classes = Path.of(FrontierV3ActorCarrierComposition.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path packageRoot = classes.resolve("io/farfrontier/palemirror");
            try (var paths = Files.walk(packageRoot)) {
                return paths.filter(path -> path.toString().endsWith(".class"))
                        .filter(path -> {
                            if (classBytesWithName(path).contains("net/neoforged/neoforge/gametest/GameTestHolder")) return false;
                            boolean[] found = { false };
                            try {
                                new org.objectweb.asm.ClassReader(Files.readAllBytes(path)).accept(
                                        new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                                            @Override public org.objectweb.asm.MethodVisitor visitMethod(int access, String name,
                                                    String descriptor, String signature, String[] exceptions) {
                                                return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                                                    @Override public void visitMethodInsn(int opcode, String owner, String name,
                                                            String descriptor, boolean isInterface) {
                                                        if (owner.equals(target.getName().replace('.', '/')) && name.equals(method)
                                                                && descriptorPredicate.test(descriptor)) found[0] = true;
                                                    }
                                                };
                                            }
                                        }, org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);
                            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                            return found[0];
                        })
                        .map(path -> classes.relativize(path).toString().replace('/', '.').replace('\\', '.').replaceAll("\\.class$", ""))
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (java.io.IOException | URISyntaxException failure) { throw new IllegalStateException(failure); }
    }
}
