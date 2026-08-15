package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CuratedModuleCatalogTest {
    @Test void everyPinnedCuratedAssetIsPresentAndByteExact() {
        assertNull(AuthoredAssetCatalog.verify());
    }

    @Test void activeTownshipUsesARealRoleLibraryInsteadOfGenericHouseAliases() {
        var buildings = SettlementArchetypeCatalog.ironFrontier().active().buildings();
        Set<String> templates = buildings.stream().map(SettlementArchetypeCatalog.Building::template)
                .collect(Collectors.toSet());

        assertEquals(20, buildings.size());
        assertTrue(templates.size() >= 19, "the 20-building Township must retain curated visual diversity");
        assertEquals("bakery", template(buildings, "community_bakery"));
        assertEquals("smeltery", template(buildings, "smeltery"));
        assertEquals("forge", template(buildings, "smithy"));
        assertEquals("stable_yard", template(buildings, "stable"));
        assertEquals("assay_office", template(buildings, "assay_office"));
    }

    @Test void mineCopiesReuseTheExactPublicEntranceContractOfTheirCuratedSource() {
        assertEntranceCopy("mine/portal_hoist", "engineer_shop");
        assertEntranceCopy("mine/crew_outpost", "apothecary");
        assertEntranceCopy("mine/processing_hall", "smeltery");
        assertEntranceCopy("mine/power_house", "assay_office");
        assertEntranceCopy("mine/loading_yard", "stable_yard");
        assertEntranceCopy("mine/dispatch_shell", "smeltery");
        assertEntranceCopy("mine/dispatch_machinery", "assay_office");
        assertEntranceCopy("mine/dispatch_commissioning", "stable_yard");
    }

    @Test void everyCatalogDimensionMatchesThePinnedNbtExactly() throws Exception {
        for (String family : java.util.List.of("temperate", "cold_taiga", "dry_arid")) {
            for (var definition : FrontierModuleCatalog.allDefinitionsInOrder()) {
                String path = "/data/pale_mirror_visuals/structure/" + family + "/"
                        + definition.id() + ".nbt";
                try (var input = CuratedModuleCatalogTest.class.getResourceAsStream(path)) {
                    int[] size = readStructureSize(java.util.Objects.requireNonNull(input, path));
                    assertEquals(definition.sizeX(), size[0], path + " width");
                    assertEquals(definition.sizeY(), size[1], path + " height");
                    assertEquals(definition.sizeZ(), size[2], path + " depth");
                }
            }
        }
    }

    @Test void reportedPublicEntrancesUseTheExteriorFacingAuthoredDoors() {
        var house = FrontierModuleCatalog.require("residence_1");
        assertEquals(6, house.entranceX());
        assertEquals(1, house.entranceY());
        assertEquals(6, house.entranceZ());
        assertEquals(0, house.entranceOutward(), "the authored doormat identifies the exterior east threshold");

        var assay = FrontierModuleCatalog.require("assay_office");
        assertEquals(1, assay.entranceOutward(), "the clear authored porch exits south");

        var workshop = FrontierModuleCatalog.require("engineer_shop");
        assertEquals(4, workshop.entranceX());
        assertEquals(0, workshop.entranceY());
        assertEquals(2, workshop.entranceZ());
        assertEquals(3, workshop.entranceOutward(), "exterior andesite door faces north");
    }

    /** Reads only the root-level vanilla structure size without a Minecraft test runtime. */
    private static int[] readStructureSize(java.io.InputStream compressed) throws Exception {
        try (var input = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(compressed))) {
            assertEquals(10, input.readUnsignedByte(), "NBT root must be a compound");
            input.readUTF();
            while (true) {
                int type = input.readUnsignedByte();
                if (type == 0) throw new IllegalStateException("Structure NBT has no size tag");
                String name = input.readUTF();
                if (type == 9 && name.equals("size")) {
                    assertEquals(3, input.readUnsignedByte(), "size must be an int list");
                    int length = input.readInt();
                    assertEquals(3, length, "size must have three dimensions");
                    return new int[] {input.readInt(), input.readInt(), input.readInt()};
                }
                skipPayload(input, type);
            }
        }
    }

    private static void skipPayload(java.io.DataInputStream input, int type) throws Exception {
        switch (type) {
            case 1 -> input.skipNBytes(1);
            case 2 -> input.skipNBytes(2);
            case 3, 5 -> input.skipNBytes(4);
            case 4, 6 -> input.skipNBytes(8);
            case 7 -> input.skipNBytes(input.readInt());
            case 8 -> input.readUTF();
            case 9 -> {
                int element = input.readUnsignedByte();
                int length = input.readInt();
                for (int index = 0; index < length; index++) skipPayload(input, element);
            }
            case 10 -> {
                while (true) {
                    int child = input.readUnsignedByte();
                    if (child == 0) break;
                    input.readUTF();
                    skipPayload(input, child);
                }
            }
            case 11 -> input.skipNBytes(Math.multiplyExact(input.readInt(), 4L));
            case 12 -> input.skipNBytes(Math.multiplyExact(input.readInt(), 8L));
            default -> throw new IllegalStateException("Unsupported NBT tag " + type);
        }
    }

    private static String template(java.util.List<SettlementArchetypeCatalog.Building> buildings, String id) {
        return buildings.stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow().template();
    }

    private static void assertEntranceCopy(String copyId, String sourceId) {
        var copy = FrontierModuleCatalog.require(copyId);
        var source = FrontierModuleCatalog.require(sourceId);
        assertEquals(source.entranceX(), copy.entranceX(), copyId + " entrance x");
        assertEquals(source.entranceY(), copy.entranceY(), copyId + " entrance y");
        assertEquals(source.entranceZ(), copy.entranceZ(), copyId + " entrance z");
        assertEquals(source.entranceOutward(), copy.entranceOutward(), copyId + " entrance outward");
    }
}
