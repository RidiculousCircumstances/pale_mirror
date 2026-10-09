package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

/** Disk coverage of the real eight adapters, not a mock acknowledgement of an effect. */
class FrontierV3PhysicalStoresTest {
    @BeforeAll static void installVersion() { net.minecraft.SharedConstants.tryDetectVersion(); }
    @TempDir Path directory;
    private static final WorldId WORLD = new WorldId("frontier:shared-storage-test");
    private static final ResourceLocation DIMENSION = ResourceLocation.parse("minecraft:overworld");
    private static final SubjectId SITE = new SubjectId("site:1-wheat-field");
    private record Case(String name, Supplier<FrontierV3JournaledSavedData> fresh,
                        Function<CompoundTag,FrontierV3JournaledSavedData> decode,
                        Consumer<FrontierV3JournaledSavedData> change) { }
    private static CompoundTag empty(int format, String... tables) {
        var root = new CompoundTag(); root.putInt("format",format);
        for (String table : tables) root.put(table,new ListTag());
        return root;
    }
    private static CompoundTag block() { var tag=new CompoundTag(); tag.putString("Name","minecraft:stone"); return tag; }
    private static ResourceSite field() {
        var slots=new ArrayList<BlockPosition>();
        for (int x=0;x<8;x++) for (int z=0;z<8;z++) slots.add(new BlockPosition(x,64,z));
        return new ResourceSite(SITE,new SubjectId("settlement:1"),new SubjectId("structure:1-farm"),
                ResourceSiteKind.WHEAT_FIELD,FrontierResourceSitePlan.initialGrayboxLayout(slots));
    }
    private static FrontierV3DepotClickWitness click() {
        return new FrontierV3DepotClickWitness(new SubjectId("container:1-depot"),new SubjectId("custody:container-1-depot"),
                new SubjectId("settlement:1"),1,UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),List.of(),Optional.empty());
    }
    private static List<Case> cases() {
        return List.of(
            new Case("fields", () -> { var value=FrontierV3ResourceSiteLedger.fixture();
                value.reserveFieldInitialization(field(),new PhysicalIntentId("intent:site-prepare-1-wheat-field")); return value; },
                tag -> FrontierV3ResourceSiteLedger.load(tag,null), value -> ((FrontierV3ResourceSiteLedger)value).conflict(SITE)),
            new Case("blocks", () -> { var value=FrontierV3GrayboxLedger.inMemory();
                value.applied(new BlockPos(1,64,1),"structure:1-farm",PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE.wireTag(),
                    GrayboxMaterial.values()[0].name(),GrayboxSemanticPart.values()[0].name()); return value; },
                tag -> FrontierV3GrayboxLedger.load(tag,null), value -> ((FrontierV3GrayboxLedger)value).defer(new BlockPos(1,64,1))),
            new Case("clicks", () -> { var value=FrontierV3DepotClickLedger.load(empty(1,"pending"),null); value.prepare(click()); return value; },
                tag -> FrontierV3DepotClickLedger.load(tag,null), value -> ((FrontierV3DepotClickLedger)value).retire(click())),
            new Case("boards", () -> { var value=FrontierV3ObjectBoardLedger.load(empty(1,"claims"),null);
                value.applied("structure:1-farm",17,UUID.randomUUID().toString()); return value; },
                tag -> FrontierV3ObjectBoardLedger.load(tag,null), value -> ((FrontierV3ObjectBoardLedger)value).conflict("structure:1-farm")),
            new Case("hoppers", () -> { var value=FrontierV3HopperCarrierLedger.load(empty(1,"carriers"),null);
                value.claim(new UUID(0,1),new BlockPos(1,64,1)); return value; },
                tag -> FrontierV3HopperCarrierLedger.load(tag,null), value -> ((FrontierV3HopperCarrierLedger)value).claim(new UUID(0,2),new BlockPos(2,64,1))),
            new Case("observations", () -> { var root=empty(3,"pending"); root.putLong("nextSequence",1);
                root.getList("pending",10).add(new FrontierV3PhysicalObservationLedger.Pending("effect:external-explosion:0",1,
                    List.of(new FrontierV3PhysicalObservationLedger.Candidate(1,block(),Optional.empty()))).save());
                return FrontierV3PhysicalObservationLedger.load(root,null); },
                tag -> FrontierV3PhysicalObservationLedger.load(tag,null), value -> { var ledger=(FrontierV3PhysicalObservationLedger)value;
                    ledger.resolve(ledger.nextReady(2).orElseThrow()); }),
            new Case("managed", () -> { var root=empty(5,"pending");
                root.getList("pending",10).add(new FrontierV3ManagedExplosionLedger.Pending("intent:explosion-test",1,
                    List.of(new FrontierV3ManagedExplosionLedger.BlockCandidate(1,block(),Optional.empty(),Optional.empty())),
                    List.of(),List.of(),List.of(),List.of(),1,0,0,0).save()); return FrontierV3ManagedExplosionLedger.load(root,null); },
                tag -> FrontierV3ManagedExplosionLedger.load(tag,null), value -> { var ledger=(FrontierV3ManagedExplosionLedger)value;
                    ledger.resolveBlock(ledger.nextBlock(new PhysicalIntentId("intent:explosion-test"),2).orElseThrow(),true); }),
            new Case("infection", () -> { var ledger=FrontierV3InfectionOverlayLedger.load(empty(6,"claims"),null);
                var positions=new ArrayList<BlockPos>(); for(int x=0;x<4;x++) for(int z=0;z<4;z++) positions.add(new BlockPos(x,64,z));
                ledger.prepare(new InfectionCell(0,0),positions,InfectionOverlayStage.values()[0]); return ledger; },
                tag -> FrontierV3InfectionOverlayLedger.load(tag,null), value -> ((FrontierV3InfectionOverlayLedger)value).activate(new InfectionCell(0,0)))
        );
    }
    @TestFactory Collection<DynamicTest> everyAdapterRecoversCheckpointAndTailAndNoopDoesNotWrite() {
        return cases().stream().map(item -> DynamicTest.dynamicTest(item.name, () -> {
            Path path=directory.resolve(item.name+".dat"); var ledger=item.fresh.get();
            var store=FrontierV3JournalStore.open(path,FrontierV3JournalStore.PHYSICAL);
            ledger.attachJournal(path,WORLD,DIMENSION,store); ledger.setDirty(); ledger.save(path.toFile(),null);
            byte[] checkpoint=Files.readAllBytes(path);
            item.change.accept(ledger); ledger.save(path.toFile(),null);
            assertArrayEquals(checkpoint,Files.readAllBytes(path),"delta must not rewrite the whole checkpoint");
            var recovered=FrontierV3JournaledSavedData.readFile(path,WORLD.value(),DIMENSION.toString(),item.fresh,
                    (tag,registries) -> item.decode.apply(tag),null);
            assertEquals(ledger.save(new CompoundTag(),null),recovered.save(new CompoundTag(),null));
            long sequence=store.pressure().durableSequence(); ledger.save(path.toFile(),null);
            assertEquals(sequence,store.pressure().durableSequence(),"clean fence must not append a redundant image");
            ledger.finish();
            assertThrows(java.io.UncheckedIOException.class, () -> FrontierV3JournaledSavedData.readFile(path,"frontier:wrong",
                    DIMENSION.toString(),item.fresh,(tag,registries) -> item.decode.apply(tag),null));
        })).toList();
    }
    @Test void failingDiskFenceKeepsDirtyStateAndCannotAcknowledgeTheWitness() throws Exception {
        var ledger=FrontierV3DepotClickLedger.load(empty(1,"pending"),null);
        Path path=directory.resolve("failed.dat");
        var store=FrontierV3JournalStore.open(path,FrontierV3JournalStore.PHYSICAL);
        ledger.attachJournal(path,WORLD,DIMENSION,store); ledger.prepare(click()); Files.createDirectory(path);
        assertThrows(java.io.UncheckedIOException.class, () -> ledger.save(path.toFile(),null));
        assertTrue(ledger.isDirty()); assertEquals(click(),ledger.pending(click().containerId()));
        assertThrows(java.io.IOException.class,store::checkHealthy);
    }
    @Test void fieldHeadersDoNotRebuildCellShardsAndRecoveryRequiresAllBuckets() {
        var cells=new ArrayList<BlockPosition>();
        for(int x=0;x<32;x++) for(int z=0;z<16;z++) cells.add(new BlockPosition(x,64,z));
        var layoutCells=new ArrayList<ResourceFieldLayout.Cell>();
        for(var crop:cells) {
            var soil=new SurfaceAnchor(crop.offset(0,-1,0));
            layoutCells.add(new ResourceFieldLayout.Cell(new ResourceFieldLayout.CellId(layoutCells.size()+1L),crop,soil,soil));
        }
        var site=new ResourceSite(SITE,new SubjectId("settlement:1"),new SubjectId("structure:1-farm"),
                ResourceSiteKind.WHEAT_FIELD,new ResourceFieldLayout(1,513,layoutCells,List.of()));
        var ledger=FrontierV3ResourceSiteLedger.fixture();
        ledger.reserveFieldInitialization(site,new PhysicalIntentId("intent:site-prepare-1-wheat-field"));
        var codec=new FrontierV3FieldJournalCodec(); var before=codec.split(SITE,ledger.fieldClaim(SITE));
        ledger.conflict(SITE); var after=codec.split(SITE,ledger.fieldClaim(SITE));
        assertEquals(3,after.size());
        assertSame(before.get("bucket:0"),after.get("bucket:0"));
        assertSame(before.get("bucket:1"),after.get("bucket:1"));
        assertEquals(FrontierV3ResourceSiteLedger.encodeField(ledger.fieldClaim(SITE)),codec.merge(after));
        var missing=new HashMap<>(after); missing.remove("bucket:1");
        assertThrows(NullPointerException.class, () -> codec.merge(missing));
        var foreign=new HashMap<>(after); var wrong=after.get("bucket:1").copy();
        wrong.getList("cells",10).getCompound(0).putLong("id",1); foreign.put("bucket:1",wrong);
        assertThrows(IllegalStateException.class, () -> codec.merge(foreign));
    }
    @Test void replacementWitnessRetainsItsExactPredecessorAcrossRecovery() {
        var ledger=FrontierV3InfectionOverlayLedger.load(empty(6,"claims"),null);
        var positions=new ArrayList<BlockPos>();
        for(int x=0;x<4;x++) for(int z=0;z<4;z++) positions.add(new BlockPos(x,64,z));
        var cell=new InfectionCell(0,0); var stages=InfectionOverlayStage.values();
        ledger.prepare(cell,positions,stages[0]); ledger.activate(cell);
        ledger.prepareReplacement(cell,stages[1]);
        var recovered=FrontierV3InfectionOverlayLedger.load(ledger.save(new CompoundTag(),null),null);
        assertEquals(FrontierV3InfectionOverlayLedger.Phase.PREPARED,recovered.claim(cell).phase());
        assertEquals(Optional.of(stages[0]),recovered.claim(cell).predecessor());
        recovered.activate(cell); recovered.clearedByEffect(cell); recovered.prepareReplacement(cell,stages[0]);
        assertTrue(recovered.claim(cell).predecessor().isEmpty(),"cleared patch has an explicit air baseline");
        var corrupt=recovered.save(new CompoundTag(),null);
        corrupt.getList("claims",10).getCompound(0).remove("baseline");
        assertThrows(IllegalStateException.class, () -> FrontierV3InfectionOverlayLedger.load(corrupt,null));
    }
    @Test void immutableQueueDropsHeadsWithoutCopyAndRetainsAbsoluteShardCursor() {
        var original=FrontierV3WitnessQueue.copy(java.util.stream.IntStream.range(0,600).boxed().toList());
        var tail=FrontierV3WitnessQueue.copy(original.subList(257,600));
        assertSame(original.imageIdentity(),tail.imageIdentity()); assertEquals(257,tail.start());
        assertEquals(257,tail.getFirst());
        var recovered=FrontierV3WitnessQueue.restored(tail.retained(),tail.start());
        assertEquals(tail,recovered); assertEquals(tail.start(),recovered.start());
        var empty=FrontierV3WitnessQueue.restored(List.of(),600); assertEquals(600,empty.start()); assertTrue(empty.isEmpty());
    }
    @Test void largeObservationResolutionWritesOnlyCursorUntilWholeShardRetires() throws Exception {
        var cells=java.util.stream.IntStream.range(0,600).mapToObj(i ->
                new FrontierV3PhysicalObservationLedger.Candidate(i,block(),Optional.empty())).toList();
        var root=empty(3,"pending"); root.putLong("nextSequence",1);
        root.getList("pending",10).add(new FrontierV3PhysicalObservationLedger.Pending("effect:external-explosion:0",1,cells).save());
        var ledger=FrontierV3PhysicalObservationLedger.load(root,null); Path path=directory.resolve("large.dat");
        var store=FrontierV3JournalStore.open(path,FrontierV3JournalStore.PHYSICAL);
        ledger.attachJournal(path,WORLD,DIMENSION,store); ledger.setDirty(); ledger.save(path.toFile(),null);
        var before=store.image(); ledger.resolve(ledger.nextReady(2).orElseThrow()); ledger.save(path.toFile(),null);
        var after=store.image();
        long changed=after.entrySet().stream().filter(entry -> !Arrays.equals(entry.getValue(),before.get(entry.getKey()))).count();
        assertEquals(1,changed,"only the small effect cursor changes, not immutable candidates or unchanged sequence");
        for(int i=1;i<257;i++) ledger.resolve(ledger.nextReady(2).orElseThrow());
        ledger.save(path.toFile(),null); var recovered=FrontierV3JournaledSavedData.readFile(path,WORLD.value(),DIMENSION.toString(),
                () -> FrontierV3PhysicalObservationLedger.load(empty(3,"pending"),null),FrontierV3PhysicalObservationLedger::load,null);
        assertEquals(257,recovered.nextReady(2).orElseThrow().candidate().position());
        assertEquals(ledger.save(new CompoundTag(),null),recovered.save(new CompoundTag(),null));
    }
}
