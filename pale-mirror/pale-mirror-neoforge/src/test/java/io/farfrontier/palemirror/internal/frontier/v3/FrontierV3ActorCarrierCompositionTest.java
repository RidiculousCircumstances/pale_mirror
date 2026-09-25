package io.farfrontier.palemirror.internal.frontier.v3;

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
    void closedInventoryUsesTheCompiledFactoryBoundaryWithoutProducerOrAdopterBypass() throws Exception {
        Set<Class<?>> boundaries = FrontierV3ActorCarrierComposition.inventory().stream()
                .map(FrontierV3ActorCarrierComposition.InventoryEntry::boundaryType).collect(Collectors.toUnmodifiableSet());
        assertEquals(9, boundaries.size(), "duplicate inventory ownership is invalid");
        for (FrontierV3ActorCarrierComposition.InventoryEntry entry : FrontierV3ActorCarrierComposition.inventory()) {
            assertTrue(classBytes(entry.boundaryType()).contains("FrontierV3ActorCarrierFactory"),
                    "registered boundary does not compose through the actor-carrier factory: " + entry.boundaryType().getName());
        }
        assertEquals(boundaries.stream().map(Class::getName).collect(Collectors.toUnmodifiableSet()), factoryCallBoundaries(),
                "only an explicitly registered boundary may present an actor-carrier capability to the factory");
        assertFalse(classBytes(FrontierV3ActorCarrierFactory.class).contains("StackWalker"),
                "actor-carrier authority must be supplied explicitly, never inferred from the runtime call stack");
        assertEquals(Set.of(FrontierV3ActorCarrierFactory.class.getName(),
                        "io.farfrontier.palemirror.internal.adapter.VanillaAnchorAdapter",
                        "io.farfrontier.palemirror.internal.integration.crimson.CrimsonActorProfile",
                        "io.farfrontier.palemirror.internal.integration.crimson.CrimsonSiegeProfile",
                        "io.farfrontier.palemirror.internal.world.SourceGrayboxActorMaterializer"),
                directResidentOrBioformConstructionBoundaries(),
                "a production class cannot directly create a resident or bioform body outside the closed carrier boundary or a declared non-XACT adapter");
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorCarrierFactory.create(null, null, null, null),
                "an unregistered helper cannot construct an actor carrier outside the closed inventory");
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorCarrierFactory.materializeSceneBodies(null, null, null, null),
                "an unregistered helper cannot adopt an actor carrier through another boundary");
    }
    @Test
    void canonicalProducerMustDeclareTheExactRosterKindBeforeBodyComposition() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:xact-carrier"), 71L));
        SubjectId resident = state.humanPopulation().residents().keySet().stream().findFirst().orElseThrow();
        UUID entity = FrontierV3AmbientActorExecutor.entityId(state, resident);

        FrontierV3ActorCarrierComposition.Declaration declaration = FrontierV3ActorCarrierComposition.fromCanonical(state, resident,
                FrontierV3ActorCarrierComposition.ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                entity, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 1L, 1L);

        assertEquals(resident, declaration.actorId());
        assertEquals(FrontierV3ActorCarrierComposition.ActorKind.RESIDENT, declaration.kind());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorCarrierComposition.fromCanonical(state, resident,
                FrontierV3ActorCarrierComposition.ActorKind.BIOFORM, FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                entity, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 1L, 1L),
                "a body producer cannot substitute a compatible Minecraft type for the declared canonical kind");
    }

    @Test
    void declarationRequiresOwnerRepresentationRevisionAndEpochAtItsFirstBoundary() {
        SubjectId actor = new SubjectId("resident:xact-7");
        UUID entity = UUID.fromString("7f65aa31-f6d9-42f7-9e05-8d1fe830644a");
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3ActorCarrierComposition.Declaration(actor,
                FrontierV3ActorCarrierComposition.ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                entity, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, -2L, 1L));
        FrontierV3ActorCarrierComposition.Declaration live = new FrontierV3ActorCarrierComposition.Declaration(actor,
                FrontierV3ActorCarrierComposition.ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                entity, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 4L, 2L);
        assertEquals(FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, live.inactiveCarrier().representation());
        assertEquals(5L, live.liveBody(FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, 5L, 3L).authorityRevision());
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
                                    && !bytes.contains("net/neoforged/neoforge/gametest/GameTestHolder");
                        })
                        .map(path -> classes.relativize(path).toString().replace('/', '.').replace('\\', '.').replaceAll("\\.class$", ""))
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (java.io.IOException | URISyntaxException failure) { throw new IllegalStateException(failure); }
    }
    private static Set<String> factoryCallBoundaries() {
        try {
            Path classes = Path.of(FrontierV3ActorCarrierComposition.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path packageRoot = classes.resolve("io/farfrontier/palemirror");
            try (var paths = Files.walk(packageRoot)) {
                return paths.filter(path -> path.toString().endsWith(".class"))
                        .filter(path -> {
                            String bytes = classBytesWithName(path);
                            return bytes.contains("FrontierV3ActorCarrierFactory")
                                    && !path.getFileName().toString().startsWith("FrontierV3ActorCarrierFactory$")
                                    && !path.getFileName().toString().equals("FrontierV3ActorCarrierFactory.class")
                                    && !bytes.contains("net/neoforged/neoforge/gametest/GameTestHolder");
                        })
                        .map(path -> classes.relativize(path).toString().replace('/', '.').replace('\\', '.').replaceAll("\\.class$", ""))
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (java.io.IOException | URISyntaxException failure) { throw new IllegalStateException(failure); }
    }
}
