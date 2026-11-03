package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SecurityStateCodecTest {
    @Test void allocationWireRejectsUnknownPurposeTruncationAndContradictoryTarget() throws IOException {
        var home=new SubjectId("settlement:wire");var duty=new SubjectId("guard-duty:wire");
        var target=new CombatTarget(CombatTarget.Kind.PLAYER,new UUID(1,2),"pale_mirror:frontier_graybox");
        var engagement=new CombatEnvelope(target,CombatEnvelope.Kind.SELF_DEFENCE,SurfaceAnchor.at(0,63,0),24);
        var response=new SecurityState.Response(duty,new SecurityState.ContactKey(home,target),10,engagement);
        var payload=new SecurityResponsesAllocated(home,Map.of(duty,response));var codec=FrontierWorldRuntimeDefinition.payloadCodecs();
        var bytes=codec.encode(payload);assertEquals(payload,codec.decode(payload.type(),bytes));
        assertThrows(IllegalArgumentException.class,()->codec.decode(payload.type(),Arrays.copyOf(bytes,bytes.length-1)));
        var header=new ByteArrayOutputStream();CombatWire.target(new DataOutputStream(header),target);
        var encoded=new ByteArrayOutputStream();CombatWire.engagement(new DataOutputStream(encoded),engagement);
        var bad=encoded.toByteArray();bad[header.size()]=127;
        assertThrows(IllegalArgumentException.class,()->CombatWire.engagement(new DataInputStream(new ByteArrayInputStream(bad))));
        var other=new CombatTarget(target.kind(),new UUID(3,4),target.dimension());
        assertThrows(IllegalArgumentException.class,()->new SecurityState.Response(duty,new SecurityState.ContactKey(home,other),10,engagement));
    }
    @Test void awarenessRejectsDuplicateEvidenceDuringRecovery() throws IOException {
        var key=new CombatAwareness.SightKey(new SubjectId("settlement:wire"),new SubjectId("resident:wire"),
                new CombatTarget(CombatTarget.Kind.HOSTILE_MOB,new UUID(1,2),"pale_mirror:frontier_graybox"));
        var sight=new CombatAwareness.Sight(key,new BlockPosition(1,63,0),new BlockPosition(0,63,0),10);
        var bytes=new ByteArrayOutputStream();CombatAwarenessCodec.write(new DataOutputStream(bytes),new CombatAwareness(Map.of(key,sight),Map.of()));
        var normal=bytes.toByteArray();var duplicate=new ByteArrayOutputStream();var out=new DataOutputStream(duplicate);
        out.writeInt(2);out.write(normal,4,normal.length-8);out.write(normal,4,normal.length-8);out.writeInt(0);
        assertThrows(IllegalArgumentException.class,()->CombatAwarenessCodec.read(new DataInputStream(new ByteArrayInputStream(duplicate.toByteArray()))));
    }
}
