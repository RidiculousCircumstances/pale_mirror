package io.farfrontier.palemirror.frontier.v3.persistence;
import static org.junit.jupiter.api.Assertions.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSeat;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class SeatTableCodecTest {
    @Test void explicitTablePresenceRoundTripsForBothNativeFurnitureKinds() throws Exception {
        for(var kind:BuildingSeat.Kind.values()) for(boolean table:List.of(false,true)) {
            var place=new SeatPlace(new SeatPlace.Id(new SubjectId("structure:1-kitchen"),"rest"),new SubjectId("settlement:1"),kind,
                    kind==BuildingSeat.Kind.HANDCRAFTED_CHAIR?"handcrafted:spruce_chair":"handcrafted:spruce_bench",new BlockPosition(5,6,7),
                    table?Optional.of(new SeatTable("handcrafted:spruce_table",new BlockPosition(5,6,6))):Optional.empty(),
                    new SurfaceAnchor(new BlockPosition(5,5,8)),new SurfaceAnchor(new BlockPosition(4,5,7)),FacilityFacing.NORTH);
            var bytes=new ByteArrayOutputStream();SeatStateCodec.place(new DataOutputStream(bytes),place);
            var input=new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
            assertEquals(place,SeatStateCodec.place(input));assertEquals(0,input.available());
            var truncated=Arrays.copyOf(bytes.toByteArray(),bytes.size()-1);
            assertThrows(IOException.class,()->SeatStateCodec.place(new DataInputStream(new ByteArrayInputStream(truncated))));
        }
    }
}
