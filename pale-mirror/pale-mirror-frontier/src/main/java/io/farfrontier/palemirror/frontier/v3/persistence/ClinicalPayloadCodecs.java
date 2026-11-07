package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.clinical.*;
import io.farfrontier.palemirror.frontier.v3.model.rest.*;
import java.io.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.CombatWire.*;
/** Stable current-format care and generic rest payloads; no inferred activity or ordinal tags. */
final class ClinicalPayloadCodecs {
    static PayloadCodecs care(){return codecs(List.of(ClinicalEvent.Vitality.TYPE,ClinicalEvent.Started.TYPE,ClinicalEvent.Destination.TYPE,ClinicalEvent.Ended.TYPE,
            ClinicalEvent.SourceSelected.TYPE,ClinicalEvent.SupplyPrepared.TYPE,ClinicalEvent.Supplied.TYPE,ClinicalEvent.SupplyAborted.TYPE,
            ClinicalEvent.TendPrepared.TYPE,ClinicalEvent.Tended.TYPE,ClinicalEvent.HealingPrepared.TYPE,ClinicalEvent.Recovered.TYPE));}
    static PayloadCodecs rest(){return codecs(List.of(RestEvent.Availability.TYPE,RestEvent.Reserved.TYPE,RestEvent.Prepared.TYPE,RestEvent.Changed.TYPE,RestEvent.Released.TYPE));}
    private static PayloadCodecs codecs(List<String> types){return new PayloadCodecs(types.stream().map(ClinicalPayloadCodecs::codec).toList());}
    private static PayloadCodec codec(String type){return new PayloadCodec(){
        public String type(){return type;}
        public byte[] encode(FrontierPayload value){if(!type.equals(value.type()))throw new IllegalArgumentException("foreign clinical/rest codec");try{
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            switch(value){
                case ClinicalEvent.Vitality v->{id(out,v.actor());out.writeBoolean(v.expected().isPresent());if(v.expected().isPresent())ClinicalStateCodec.wound(out,v.expected().orElseThrow());out.writeLong(v.atTick());}
                case ClinicalEvent.Started v->ClinicalStateCodec.task(out,v.task());
                case ClinicalEvent.Destination v->{id(out,v.task());out.writeLong(v.revision());out.writeByte(v.phase().wireTag());FrontierWorldStateCodec.writePosition(out,v.station().support());}
                case ClinicalEvent.Ended v->{id(out,v.task());out.writeLong(v.revision());}
                case ClinicalEvent.SourceSelected v->{id(out,v.task());out.writeLong(v.revision());ActorSupplyVisitCodec.write(out,v.visit());}
                case ClinicalEvent.SupplyPrepared v->{id(out,v.task());out.writeLong(v.revision());ActorItemTransferStepCodec.write(out,v.step());}
                case ClinicalEvent.Supplied v->{id(out,v.task());out.writeLong(v.revision());step(out,v.step());PhysicalObservationStackCodec.writeStacks(out,v.source());PhysicalObservationStackCodec.writeStacks(out,v.destination());}
                case ClinicalEvent.SupplyAborted v->{id(out,v.task());out.writeLong(v.revision());step(out,v.step());}
                case ClinicalEvent.TendPrepared v->{id(out,v.task());out.writeLong(v.revision());ClinicalStateCodec.tend(out,v.tend());}
                case ClinicalEvent.Tended v->{id(out,v.task());out.writeLong(v.revision());out.writeBoolean(v.tend().isPresent());if(v.tend().isPresent())ClinicalStateCodec.tend(out,v.tend().orElseThrow());
                    out.writeBoolean(v.coldAccount().isPresent());if(v.coldAccount().isPresent()){id(out,v.coldAccount().orElseThrow());id(out,v.coldLot().orElseThrow());}out.writeBoolean(v.applied());
                        PhysicalObservationStackCodec.writeStacks(out,v.remaining());}
                case ClinicalEvent.HealingPrepared v->ClinicalStateCodec.healing(out,v.healing());
                case ClinicalEvent.Recovered v->{id(out,v.actor());out.writeBoolean(v.healing().isPresent());if(v.healing().isPresent())ClinicalStateCodec.healing(out,v.healing().orElseThrow());out.writeLong(v.throughTick());
                    out.writeLong(v.amount());out.writeBoolean(v.applied());}
                case RestEvent.Availability v->{ClinicalStateCodec.place(out,v.place());out.writeBoolean(v.available());}
                case RestEvent.Reserved v->ClinicalStateCodec.rest(out,v.use());
                case RestEvent.Prepared v->{id(out,v.actor());out.writeLong(v.revision());actuation(out,v.body());out.writeByte(v.action().wireTag());}
                case RestEvent.Changed v->{id(out,v.actor());out.writeLong(v.revision());out.writeBoolean(v.body().isPresent());if(v.body().isPresent())actuation(out,v.body().orElseThrow());out.writeByte(v.action().wireTag());
                    out.writeBoolean(v.applied());FrontierWorldPayloadCodecs.writeBody(out,v.observed());}
                case RestEvent.Released v->{id(out,v.actor());out.writeLong(v.revision());}
                default->throw new IllegalArgumentException("unsupported clinical/rest schema");
            }return bytes.toByteArray();
        }catch(IOException error){throw new IllegalArgumentException("clinical/rest encoding failed",error);}}
        public FrontierPayload decode(byte[] data){try{var in=new DataInputStream(new ByteArrayInputStream(data));FrontierPayload v=switch(type){
            case ClinicalEvent.Vitality.TYPE->new ClinicalEvent.Vitality(id(in),in.readBoolean()?Optional.of(ClinicalStateCodec.wound(in)):Optional.empty(),in.readLong());
            case ClinicalEvent.Started.TYPE->new ClinicalEvent.Started(ClinicalStateCodec.task(in));
            case ClinicalEvent.Destination.TYPE->new ClinicalEvent.Destination(id(in),in.readLong(),ClinicalTask.Phase.fromWireTag(in.readUnsignedByte()),new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)));
            case ClinicalEvent.Ended.TYPE->new ClinicalEvent.Ended(id(in),in.readLong());
            case ClinicalEvent.SourceSelected.TYPE->new ClinicalEvent.SourceSelected(id(in),in.readLong(),ActorSupplyVisitCodec.read(in));
            case ClinicalEvent.SupplyPrepared.TYPE->new ClinicalEvent.SupplyPrepared(id(in),in.readLong(),ActorItemTransferStepCodec.read(in));
            case ClinicalEvent.Supplied.TYPE->new ClinicalEvent.Supplied(id(in),in.readLong(),step(in),PhysicalObservationStackCodec.readStacks(in),PhysicalObservationStackCodec.readStacks(in));
            case ClinicalEvent.SupplyAborted.TYPE->new ClinicalEvent.SupplyAborted(id(in),in.readLong(),step(in));
            case ClinicalEvent.TendPrepared.TYPE->new ClinicalEvent.TendPrepared(id(in),in.readLong(),ClinicalStateCodec.tend(in));
            case ClinicalEvent.Tended.TYPE->{var owner=id(in);long revision=in.readLong();var tend=in.readBoolean()?Optional.of(ClinicalStateCodec.tend(in)):Optional.<ClinicalTend>empty();
                boolean cold=in.readBoolean();var account=cold?Optional.of(id(in)):Optional.<SubjectId>empty();var lot=cold?Optional.of(id(in)):Optional.<SubjectId>empty();yield new ClinicalEvent.Tended(owner,revision,tend,
                    account,lot,in.readBoolean(),PhysicalObservationStackCodec.readStacks(in));}
            case ClinicalEvent.HealingPrepared.TYPE->new ClinicalEvent.HealingPrepared(ClinicalStateCodec.healing(in));
            case ClinicalEvent.Recovered.TYPE->new ClinicalEvent.Recovered(id(in),in.readBoolean()?Optional.of(ClinicalStateCodec.healing(in)):Optional.empty(),in.readLong(),in.readLong(),in.readBoolean());
            case RestEvent.Availability.TYPE->new RestEvent.Availability(ClinicalStateCodec.place(in),in.readBoolean());
            case RestEvent.Reserved.TYPE->new RestEvent.Reserved(ClinicalStateCodec.rest(in));
            case RestEvent.Prepared.TYPE->new RestEvent.Prepared(id(in),in.readLong(),actuation(in),RestUse.Action.fromWireTag(in.readUnsignedByte()));
            case RestEvent.Changed.TYPE->new RestEvent.Changed(id(in),in.readLong(),in.readBoolean()?Optional.of(actuation(in)):Optional.empty(),RestUse.Action.fromWireTag(in.readUnsignedByte()),in.readBoolean(),
                FrontierWorldPayloadCodecs.readBody(in));
            case RestEvent.Released.TYPE->new RestEvent.Released(id(in),in.readLong());
            default->throw new IllegalArgumentException("unknown clinical/rest codec");};
            if(in.available()!=0)throw new IllegalArgumentException("trailing clinical/rest data");return v;
        }catch(IOException error){throw new IllegalArgumentException("truncated clinical/rest data",error);}}
    };}
    private static void step(DataOutputStream out,Optional<ActorItemTransferStep> value)throws IOException{out.writeBoolean(value.isPresent());if(value.isPresent())ActorItemTransferStepCodec.write(out,value.orElseThrow());}
    private static Optional<ActorItemTransferStep> step(DataInputStream in)throws IOException{return in.readBoolean()?Optional.of(ActorItemTransferStepCodec.read(in)):Optional.empty();}
    private ClinicalPayloadCodecs(){}
}
