package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierV3ActorCarrierCompositionTest {
    @Test
    void closedInventoryCoversEveryProductionBodyCreatorAndEverySceneAdopter() throws Exception {
        Path source = Path.of("src/main/java/io/farfrontier/palemirror/internal/frontier/v3");
        Set<String> names = FrontierV3ActorCarrierComposition.inventory().stream()
                .map(FrontierV3ActorCarrierComposition.InventoryEntry::sourceType).collect(Collectors.toUnmodifiableSet());
        assertEquals(9, names.size(), "duplicate inventory ownership is invalid");
        Set<String> creators;
        Set<String> adopters;
        try (var paths = Files.list(source)) {
            creators = paths.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().endsWith("GameTests.java"))
                    .filter(path -> content(path).contains("EntityType.VILLAGER.create") || content(path).contains("EntityType.ZOMBIE.create"))
                    .map(path -> path.getFileName().toString().replace(".java", "")).collect(Collectors.toUnmodifiableSet());
        }
        try (var paths = Files.list(source)) {
            adopters = paths.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> content(path).contains("FrontierV3SceneExecutor.materializeBodies"))
                    .map(path -> path.getFileName().toString().replace(".java", "")).collect(Collectors.toUnmodifiableSet());
        }
        Set<String> declaredCreators = entries(FrontierV3ActorCarrierComposition.Role.PRODUCER);
        Set<String> declaredAdopters = entries(FrontierV3ActorCarrierComposition.Role.ADOPTER);
        assertEquals(declaredCreators, creators, "an exact body producer is missing, duplicate, or unregistered");
        assertEquals(declaredAdopters, adopters, "an exact body adopter is missing, duplicate, or unregistered");
        FrontierV3ActorCarrierComposition.inventory().forEach(entry -> assertEquals(true,
                content(source.resolve(entry.sourceType() + ".java")).contains("InventoryEntry." + entry.name()),
                "registered actor-carrier boundary is not composed by " + entry.sourceType()));
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
                entity, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, -1L, 1L));
        FrontierV3ActorCarrierComposition.Declaration live = new FrontierV3ActorCarrierComposition.Declaration(actor,
                FrontierV3ActorCarrierComposition.ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                entity, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 4L, 2L);
        assertEquals(FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, live.inactiveCarrier().representation());
        assertEquals(5L, live.liveBody(FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, 5L, 3L).authorityRevision());
    }

    private static Set<String> entries(FrontierV3ActorCarrierComposition.Role role) {
        return FrontierV3ActorCarrierComposition.inventory().stream().filter(entry -> entry.role() == role)
                .map(FrontierV3ActorCarrierComposition.InventoryEntry::sourceType).collect(Collectors.toUnmodifiableSet());
    }
    private static String content(Path path) {
        try { return Files.readString(path); } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
}
