package io.farfrontier.palemirror.internal.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResidentResourcePacksTest {
    private static final String HASH = "a".repeat(128);
    private static ResidentResourcePacks.Archive archive(String name) { return new ResidentResourcePacks.Archive(name, HASH); }
    private static ResidentResourcePacks.Manifest manifest(List<ResidentResourcePacks.Archive> packs) {
        return new ResidentResourcePacks.Manifest(1, true, "human_era_3_91_5", packs);
    }
    @Test void acceptsCompletePinnedProfile() {
        assertDoesNotThrow(() -> ResidentResourcePacks.validate(manifest(List.of(archive("fresh.zip"), archive("human.zip"), archive("activator.zip")))));
    }
    @Test void rejectsEscapingPathsDuplicateArchivesAndUnpinnedContentBeforeLoading() {
        assertThrows(IllegalArgumentException.class, () -> ResidentResourcePacks.validate(manifest(List.of(archive("../outside.zip"), archive("human.zip"), archive("activator.zip")))));
        assertThrows(IllegalArgumentException.class, () -> ResidentResourcePacks.validate(manifest(List.of(archive("human.zip"), archive("human.zip"), archive("activator.zip")))));
        assertThrows(IllegalArgumentException.class, () -> ResidentResourcePacks.validate(manifest(List.of(new ResidentResourcePacks.Archive("fresh.zip", ""), archive("human.zip"), archive("activator.zip")))));
        assertThrows(IllegalArgumentException.class, () -> ResidentResourcePacks.validate(new ResidentResourcePacks.Manifest(1, true, "unknown", List.of(archive("fresh.zip"), archive("human.zip"), archive("activator.zip")))));
    }
}
