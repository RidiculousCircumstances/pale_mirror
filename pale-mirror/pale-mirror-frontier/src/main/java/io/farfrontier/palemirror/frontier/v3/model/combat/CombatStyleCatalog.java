package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable mod definitions; birth assigns an explicit retained reference, never a role-derived style. */
public final class CombatStyleCatalog {
    private static final CombatStyleCatalog STANDARD=load();
    private final Map<CombatStyleId,CombatStyle> entries;
    private final List<CombatStyleId> birthStyles;
    private final String canonical;
    private CombatStyleCatalog(Properties data){
        var definitions=new LinkedHashMap<CombatStyleId,CombatStyle>();
        for(var key:required(data,"styles").split(",")){
            var id=new CombatStyleId(key);var prefix="style."+key+".";
            var style=new CombatStyle(id,required(data,prefix+"name"),number(data,prefix+"gap"),number(data,prefix+"attack"),
                    number(data,prefix+"run"),number(data,prefix+"burst"),number(data,prefix+"lateral"),number(data,prefix+"retreat"),
                    number(data,prefix+"approach"),number(data,prefix+"reserve"),number(data,prefix+"rear"));
            if(definitions.putIfAbsent(id,style)!=null)throw new IllegalArgumentException("duplicate combat style");
        }
        if(definitions.isEmpty() || definitions.size()>32)throw new IllegalArgumentException("unbounded combat style catalog");
        entries=Map.copyOf(definitions);birthStyles=List.copyOf(definitions.keySet());
        canonical=data.stringPropertyNames().stream().sorted().map(k->k+"="+data.getProperty(k).trim()).collect(java.util.stream.Collectors.joining("|"));
    }
    public static CombatStyleCatalog standard(){return STANDARD;}
    public CombatStyle require(CombatStyleId id){var result=entries.get(id);if(result==null)throw new IllegalArgumentException("unknown combat style "+id.value());return result;}
    public CombatStyleId forBirth(long seed,SubjectId person){
        long value=seed^person.value().hashCode();value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;value=(value^(value>>>27))*0x94d049bb133111ebL;value^=value>>>31;
        return birthStyles.get((int)Math.floorMod(value,birthStyles.size()));
    }
    public String canonicalText(){return canonical;}
    public static CombatStyleCatalog read(InputStream input)throws IOException{
        byte[] bytes=input.readNBytes(16385);if(bytes.length>16384)throw new IllegalArgumentException("combat styles exceed bound");
        var data=new Properties(){@Override public synchronized Object put(Object k,Object v){if(containsKey(k))throw new IllegalArgumentException("duplicate style property "+k);return super.put(k,v);}};
        data.load(new InputStreamReader(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8));return new CombatStyleCatalog(data);
    }
    private static CombatStyleCatalog load(){
        try(var in=CombatStyleCatalog.class.getResourceAsStream("/io/farfrontier/palemirror/frontier/v3/combat-styles.properties")){
            if(in==null)throw new IllegalStateException("missing combat styles");return read(in);
        }catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    private static String required(Properties data,String key){return Objects.requireNonNull(data.getProperty(key),"missing combat style property "+key).trim();}
    private static int number(Properties data,String key){return Integer.parseInt(required(data,key));}
}
