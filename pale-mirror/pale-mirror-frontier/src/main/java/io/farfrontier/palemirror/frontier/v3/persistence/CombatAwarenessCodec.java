package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.security.*;
import java.io.*;
import java.util.*;

/** Dated native knowledge, separate from duty/effect ownership. */
final class CombatAwarenessCodec {
    static void write(DataOutputStream out,CombatAwareness value)throws IOException {
        out.writeInt(value.sights().size());
        var order=Comparator.comparing((CombatAwareness.Sight s)->s.key().scope()).thenComparing(s->s.key().observer())
                .thenComparing(s->s.key().target().kind().wireTag()).thenComparing(s->s.key().target().entity()).thenComparing(s->s.key().target().dimension());
        for(var s:value.sights().values().stream().sorted(order).toList()) {
            SecurityStateCodec.id(out,s.key().scope());SecurityStateCodec.id(out,s.key().observer());SecurityStateCodec.target(out,s.key().target());
            FrontierWorldStateCodec.writePosition(out,s.position());FrontierWorldStateCodec.writePosition(out,s.observerPosition());out.writeLong(s.atTick());
        }
        out.writeInt(value.harms().size());
        var harms=Comparator.comparing((CombatAwareness.Harm h)->h.key().scope()).thenComparing(h->h.key().victim())
                .thenComparing(h->h.key().attacker().kind().wireTag()).thenComparing(h->h.key().attacker().entity()).thenComparing(h->h.key().attacker().dimension());
        for(var h:value.harms().values().stream().sorted(harms).toList()) {
            SecurityStateCodec.id(out,h.key().scope());SecurityStateCodec.id(out,h.key().victim());SecurityStateCodec.target(out,h.key().attacker());
            FrontierWorldStateCodec.writePosition(out,h.victimPosition());out.writeLong(h.atTick());
        }
    }
    static CombatAwareness read(DataInputStream in)throws IOException {
        var sights=new LinkedHashMap<CombatAwareness.SightKey,CombatAwareness.Sight>();
        int n=UnitGroupStateCodec.count(in,CombatAwareness.MAX_RECORDS);
        for(int i=0;i<n;i++) {
            var key=new CombatAwareness.SightKey(SecurityStateCodec.id(in),SecurityStateCodec.id(in),SecurityStateCodec.target(in));
            var s=new CombatAwareness.Sight(key,FrontierWorldStateCodec.readPosition(in),FrontierWorldStateCodec.readPosition(in),in.readLong());
            if(sights.putIfAbsent(key,s)!=null)throw new IllegalArgumentException("duplicate personal sight");
        }
        var harms=new LinkedHashMap<CombatAwareness.HarmKey,CombatAwareness.Harm>();
        n=UnitGroupStateCodec.count(in,CombatAwareness.MAX_RECORDS);
        for(int i=0;i<n;i++) {
            var key=new CombatAwareness.HarmKey(SecurityStateCodec.id(in),SecurityStateCodec.id(in),SecurityStateCodec.target(in));
            var h=new CombatAwareness.Harm(key,FrontierWorldStateCodec.readPosition(in),in.readLong());
            if(harms.putIfAbsent(key,h)!=null)throw new IllegalArgumentException("duplicate harm observation");
        }
        return new CombatAwareness(sights,harms);
    }
    private CombatAwarenessCodec() { }
}
