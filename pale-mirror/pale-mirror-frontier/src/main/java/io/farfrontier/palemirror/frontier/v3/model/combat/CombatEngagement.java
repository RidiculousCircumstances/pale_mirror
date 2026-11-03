package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;

/** Sole retained cooperative plan. Participants retain explicit identity; body/equipment/positions stay with their existing owners. */
public record CombatEngagement(Key key, long revision, SurfaceAnchor anchor, int axisX, int axisZ,
        Map<SubjectId,Participant> participants, long assemblySince, Optional<Wave> wave) {
    public static final int MAX_PARTICIPANTS = 64;
    public record Key(CombatPartyId party, CombatTarget target) {
        public Key { Objects.requireNonNull(party); Objects.requireNonNull(target); }
        public static Key of(CombatOrder order) { return new Key(order.party(),order.target()); }
    }
    public record Participant(Key engagement, ActorExecutionId execution, MeleeSector.Role role, long renewedAt) {
        public Participant { Objects.requireNonNull(engagement); Objects.requireNonNull(execution); Objects.requireNonNull(role);
            if(renewedAt<0)throw new IllegalArgumentException("invalid combat participation time"); }
    }
    public record Slot(ActorActuationId participant, long opensAt, long closesAt, boolean committed) {
        public Slot { Objects.requireNonNull(participant); if(opensAt<0 || closesAt<opensAt)throw new IllegalArgumentException("invalid attack slot"); }
        public ActorExecutionId execution(){return participant.execution();}
    }
    public record Wave(SurfaceAnchor targetPosition, List<Slot> slots) {
        public Wave { Objects.requireNonNull(targetPosition); slots=List.copyOf(slots);
            if(slots.isEmpty() || slots.size()>8 || slots.stream().map(Slot::execution).distinct().count()!=slots.size())
                throw new IllegalArgumentException("invalid attack wave"); }
        public long endsAt() { return slots.stream().mapToLong(Slot::closesAt).max().orElseThrow(); }
    }
    public CombatEngagement {
        Objects.requireNonNull(key);Objects.requireNonNull(anchor);Objects.requireNonNull(wave);participants=ImmutableLookupMap.copyOf(participants);
        if(revision<1 || assemblySince<0 || axisX==0 && axisZ==0 || Math.abs((long)axisX)>1000 || Math.abs((long)axisZ)>1000
                || participants.isEmpty() || participants.size()>MAX_PARTICIPANTS)throw new IllegalArgumentException("invalid engagement plan");
        for(var entry:participants.entrySet())if(!entry.getKey().equals(entry.getValue().execution().activityOwnerId()) || !key.equals(entry.getValue().engagement()))
            throw new IllegalArgumentException("foreign encounter participant");
        for(var slot:wave.stream().flatMap(value->value.slots().stream()).toList()){
            var member=participants.get(slot.execution().activityOwnerId());
            if(member==null || !member.execution().equals(slot.execution()))throw new IllegalArgumentException("wave lost its exact participant");
        }
    }
    public static CombatEngagement first(CombatOrder order,long tick) {
        var key=Key.of(order);var bearing=MeleeSector.pressure(order.actorPosition(),order.targetPosition());
        var member=new Participant(key,order.execution(),MeleeSector.Role.PRESSURE,tick);
        return new CombatEngagement(key,1,order.targetPosition(),bearing.axisX(),bearing.axisZ(),Map.of(order.owner(),member),0,Optional.empty());
    }
    public CombatEngagement admitted(CombatOrder order,long tick) {
        if(!key.equals(Key.of(order)))throw new IllegalArgumentException("foreign encounter admission");
        var existing=participants.get(order.owner());var role=existing!=null && existing.execution().equals(order.execution())?existing.role():nextRole();
        var members=ImmutableLookupMap.withEntry(participants,order.owner(),new Participant(key,order.execution(),role,tick));
        // Replacing an execution cannot inherit its predecessor's coordinated attack slot.
        var retained=existing!=null && !existing.execution().equals(order.execution())?Optional.<Wave>empty():wave;
        return new CombatEngagement(key,revision+1,anchor,axisX,axisZ,members,assemblySince,retained);
    }
    private MeleeSector.Role nextRole() {
        var counts=new EnumMap<MeleeSector.Role,Integer>(MeleeSector.Role.class);
        participants.values().forEach(value->counts.merge(value.role(),1,Integer::sum));
        return Arrays.stream(MeleeSector.Role.values()).min(Comparator.comparingInt((MeleeSector.Role role)->counts.getOrDefault(role,0))
                .thenComparingInt(MeleeSector.Role::wireTag)).orElseThrow();
    }
    public MeleeSector sector(ActorExecutionId execution) {
        var member=participants.get(execution.activityOwnerId());
        if(member==null || !member.execution().equals(execution))throw new IllegalArgumentException("missing exact encounter participant");
        return new MeleeSector(member.role(),axisX,axisZ);
    }
    public boolean permits(CombatOrder order,ActorActuationId observer,long tick,EngagementRules rules) {
        return key.equals(Key.of(order)) && wave.filter(value->distance(value.targetPosition(),order.targetPosition())<=rules.targetDriftMicros()/1_000_000.0)
                .stream().flatMap(value->value.slots().stream()).anyMatch(slot->slot.participant().equals(observer) && observer.execution().equals(order.execution()) && !slot.committed() && tick>=slot.opensAt() && tick<=slot.closesAt());
    }
    public CombatEngagement committed(ActorActuationId observer) {
        var value=wave.orElseThrow(()->new IllegalArgumentException("strike has no coordinated wave"));
        if(value.slots().stream().noneMatch(slot->slot.participant().equals(observer) && !slot.committed()))throw new IllegalArgumentException("missing exact unspent attack slot");
        var slots=value.slots().stream().map(slot->slot.participant().equals(observer)?new Slot(observer,slot.opensAt(),slot.closesAt(),true):slot).toList();
        return new CombatEngagement(key,revision+1,anchor,axisX,axisZ,participants,assemblySince,Optional.of(new Wave(value.targetPosition(),slots)));
    }
    public static double distance(SurfaceAnchor a,SurfaceAnchor b) { return Math.sqrt(Math.pow((double)a.x()-b.x(),2)+Math.pow((double)a.y()-b.y(),2)+Math.pow((double)a.z()-b.z(),2)); }
}
