package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import java.io.*;
import java.util.*;
final class RangedPayloadCodecs {
    static PayloadCodecs combat(){return new PayloadCodecs(List.of(codec(RangedEvent.Observed.TYPE),codec(RangedEvent.Prepared.TYPE),codec(RangedEvent.Completed.TYPE)));}
    static PayloadCodecs ammunition(){return new PayloadCodecs(List.of(codec(GuardAmmunitionEvent.Selected.TYPE),codec(GuardAmmunitionEvent.Prepared.TYPE),codec(GuardAmmunitionEvent.Loaded.TYPE),
            codec(GuardAmmunitionEvent.Cancelled.TYPE),codec(GuardAmmunitionEvent.Aborted.TYPE)));}
    private static PayloadCodec codec(String type){return new PayloadCodec(){
        public String type(){return type;}
        public byte[] encode(FrontierPayload value){if(!type.equals(value.type()))throw new IllegalArgumentException("foreign ranged codec");try{
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            switch(value){
                case RangedEvent.Observed v->RangedWire.stance(out,v.stance());
                case RangedEvent.Prepared v->RangedWire.shot(out,v.shot());
                case RangedEvent.Completed v->{CombatWire.id(out,v.owner());out.writeLong(v.attemptOrdinal());out.writeBoolean(v.emitted());PhysicalObservationStackCodec.writeStacks(out,v.remaining());}
                case GuardAmmunitionEvent.Selected v->{CombatWire.id(out,v.duty());out.writeLong(v.revision());RangedWire.supply(out,v.supply());}
                case GuardAmmunitionEvent.Aborted v->{CombatWire.id(out,v.duty());out.writeLong(v.revision());ActorItemTransferStepCodec.write(out,v.step());}
                case GuardAmmunitionEvent.Prepared v->{CombatWire.id(out,v.duty());out.writeLong(v.revision());ActorItemTransferStepCodec.write(out,v.step());}
                case GuardAmmunitionEvent.Loaded v->{CombatWire.id(out,v.duty());out.writeLong(v.revision());out.writeBoolean(v.step().isPresent());if(v.step().isPresent())ActorItemTransferStepCodec.write(out,
                        v.step().orElseThrow());PhysicalObservationStackCodec.writeStacks(out,v.source());PhysicalObservationStackCodec.writeStacks(out,v.destination());}
                case GuardAmmunitionEvent.Cancelled v->{CombatWire.id(out,v.duty());out.writeLong(v.revision());}
                default->throw new IllegalArgumentException("unsupported ranged schema");
            }return bytes.toByteArray();
        }catch(IOException error){throw new IllegalArgumentException("ranged encoding failed",error);}}
        public FrontierPayload decode(byte[] data){try{var in=new DataInputStream(new ByteArrayInputStream(data));FrontierPayload v=switch(type){
            case RangedEvent.Observed.TYPE->new RangedEvent.Observed(RangedWire.stance(in));
            case RangedEvent.Prepared.TYPE->new RangedEvent.Prepared(RangedWire.shot(in));
            case RangedEvent.Completed.TYPE->new RangedEvent.Completed(CombatWire.id(in),in.readLong(),in.readBoolean(),PhysicalObservationStackCodec.readStacks(in));
            case GuardAmmunitionEvent.Selected.TYPE->new GuardAmmunitionEvent.Selected(CombatWire.id(in),in.readLong(),RangedWire.supply(in));
            case GuardAmmunitionEvent.Aborted.TYPE->new GuardAmmunitionEvent.Aborted(CombatWire.id(in),in.readLong(),ActorItemTransferStepCodec.read(in));
            case GuardAmmunitionEvent.Prepared.TYPE->new GuardAmmunitionEvent.Prepared(CombatWire.id(in),in.readLong(),ActorItemTransferStepCodec.read(in));
            case GuardAmmunitionEvent.Loaded.TYPE->new GuardAmmunitionEvent.Loaded(CombatWire.id(in),in.readLong(),in.readBoolean()?Optional.of(ActorItemTransferStepCodec.read(in)):Optional.empty(),
                    PhysicalObservationStackCodec.readStacks(in),PhysicalObservationStackCodec.readStacks(in));
            case GuardAmmunitionEvent.Cancelled.TYPE->new GuardAmmunitionEvent.Cancelled(CombatWire.id(in),in.readLong());
            default->throw new IllegalArgumentException("unknown ranged codec");};
            if(in.available()!=0)throw new IllegalArgumentException("trailing ranged data");return v;
        }catch(IOException error){throw new IllegalArgumentException("truncated ranged data",error);}}
    };}
    private RangedPayloadCodecs(){}
}
