package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import java.io.*;
import java.util.*;
/** Shared exact grammar for actor creation payloads and population snapshots. */
final class CombatProfileCodec {
    static void write(DataOutputStream out,CombatProfile profile)throws IOException{
        var a=profile.attributes();for(int value:new int[]{a.strength(),a.agility(),a.endurance(),a.perception(),a.toughness()})out.writeByte(value);
        out.writeByte(profile.skills().size());
        for(var e:profile.skills().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){
            FrontierWorldStateCodec.writeString(out,e.getKey().value());out.writeByte(e.getValue());
        }
    }
    static CombatProfile read(DataInputStream in)throws IOException{
        var a=new CombatAttributes(in.readUnsignedByte(),in.readUnsignedByte(),in.readUnsignedByte(),in.readUnsignedByte(),in.readUnsignedByte());
        int count=in.readUnsignedByte();if(count<1 || count>32)throw new IllegalArgumentException("invalid combat skill count");
        var skills=new LinkedHashMap<CombatSkillId,Integer>();for(int i=0;i<count;i++)
            if(skills.putIfAbsent(new CombatSkillId(FrontierWorldStateCodec.readString(in)),in.readUnsignedByte())!=null)throw new IllegalArgumentException("duplicate combat skill");
        return new CombatProfile(a,skills);
    }
    private CombatProfileCodec(){}
}
