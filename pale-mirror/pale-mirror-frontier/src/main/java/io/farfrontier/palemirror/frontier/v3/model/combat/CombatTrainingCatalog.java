package io.farfrontier.palemirror.frontier.v3.model.combat;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** One immutable, bounded authored skill/technique policy, included in the world's rules hash. */
public final class CombatTrainingCatalog {
    public enum Use { CONTACT,CLOSING,LATERAL_TARGET,CROWDED,LATERAL_STEP,RETREAT_STEP,FLANK_STEP,GROUP,COUNTER,EVADE_COUNTER,CONTINUATION }
    public record Technique(MeleeAttack id,int minimum,MeleeTechniques.Technique timing,int damagePermille,int knockbackPermille,int halfAngle,Use use,Optional<MeleeAttack> next) {
        public Technique {Objects.requireNonNull(id);Objects.requireNonNull(timing);Objects.requireNonNull(use);Objects.requireNonNull(next);
            if(minimum<0 || minimum>100 || damagePermille<1 || damagePermille>2000 || knockbackPermille<0 || knockbackPermille>1500 || halfAngle<1 || halfAngle>180)
                throw new IllegalArgumentException("invalid authored technique");}
    }
    private static final CombatTrainingCatalog STANDARD=load();
    private final Map<MeleeAttack,Technique> techniques;
    private final Map<String,Integer> numbers;
    private final List<Technique> ordered;
    private final int[] baselines,cumulative;
    private final String canonical;
    private CombatTrainingCatalog(Properties data){
        var parsed=new HashMap<String,Integer>();
        for(var key:data.stringPropertyNames())if(!key.startsWith("technique.") && !required(data,key).contains(","))parsed.put(key,Integer.parseInt(required(data,key)));
        numbers=Map.copyOf(parsed);baselines=numbers(data,"generation.baselines");cumulative=numbers(data,"generation.cumulative");
        if(baselines.length!=cumulative.length || baselines.length<1 || baselines.length>16 || cumulative[cumulative.length-1]!=100)
            throw new IllegalArgumentException("invalid combat generation bands");
        int prior=0;for(int i=0;i<baselines.length;i++){if(baselines[i]<0 || baselines[i]>100 || cumulative[i]<=prior)throw new IllegalArgumentException("invalid generation band");prior=cumulative[i];}
        var entries=new EnumMap<MeleeAttack,Technique>(MeleeAttack.class);
        for(var id:required(data,"techniques").split(",")){
            var attack=MeleeAttack.valueOf(id);var values=required(data,"technique."+id).split(",");
            if(values.length!=10)throw new IllegalArgumentException("invalid technique grammar");
            var t=new Technique(attack,Integer.parseInt(values[0]),new MeleeTechniques.Technique(Integer.parseInt(values[1]),Integer.parseInt(values[2]),Integer.parseInt(values[3]),Integer.parseInt(values[4])),
                Integer.parseInt(values[5]),Integer.parseInt(values[6]),Integer.parseInt(values[7]),Use.valueOf(values[8]),values[9].equals("NONE")?Optional.empty():Optional.of(MeleeAttack.valueOf(values[9])));
            if(entries.putIfAbsent(attack,t)!=null)throw new IllegalArgumentException("duplicate technique");
        }
        if(entries.size()!=MeleeAttack.values().length)throw new IllegalArgumentException("incomplete technique catalog");
        for(var t:entries.values())if(t.next().isPresent()){
            var next=entries.get(t.next().orElseThrow());if(next==null || next.next().isPresent() || next.use()!=Use.CONTINUATION || next.minimum()>t.minimum())
                throw new IllegalArgumentException("unbounded or incompatible technique continuation");
        }
        techniques=Map.copyOf(entries);ordered=Arrays.stream(MeleeAttack.values()).map(this::require).toList();
        for(var key:List.of("generation.spread","generation.attributeBaseline","generation.attributeSpread","attributes.skillDamageMin","attributes.skillDamageMax","attributes.effortMin","attributes.effortMax",
            "attributes.resilienceMin","attributes.resilienceMax","attributes.speedMin","attributes.speedMax","attributes.damageMin","attributes.damageMax","attributes.knockbackMin","attributes.knockbackMax",
                "defence.backstep","defence.sidestep","defence.diagonal","defence.advance","defence.counter","defence.chain","defence.block","defence.blockReduction","defence.blockAngle","defence.blockTicks",
            "defence.blockCooldown","observation.reactionMin","observation.reactionMax","observation.counterTicks"))
            if(number(key)<0 || number(key)>2000)throw new IllegalArgumentException("invalid combat policy "+key);
        if(number("footwork.flankRadius")<1 || number("footwork.flankRadius")>3 || number("footwork.flankForwardMicros")<0 || number("footwork.flankForwardMicros")>1_000_000)
            throw new IllegalArgumentException("unbounded attack footwork");
        if(generationSpread()>50 || number("generation.attributeSpread")>50 || number("generation.attributeBaseline")>100
                || number("attributes.speedMin")<250 || number("attributes.speedMax")<number("attributes.speedMin")
                || number("defence.blockReduction")>900 || number("defence.blockAngle")<1 || number("defence.blockAngle")>90
                || number("defence.blockTicks")<1 || number("defence.blockTicks")>60 || number("defence.blockCooldown")<number("defence.blockTicks")+2
                || number("observation.reactionMin")<1 || number("observation.reactionMax")<number("observation.reactionMin") || number("observation.reactionMax")>10)
            throw new IllegalArgumentException("invalid bounded physical combat policy");
        int last=-1;for(var key:List.of("defence.backstep","defence.sidestep","defence.diagonal","defence.advance","defence.counter","defence.chain")){
            int level=number(key);if(level<=last || level>100)throw new IllegalArgumentException("invalid defence unlock ladder");last=level;
        }
        canonical=data.stringPropertyNames().stream().sorted().map(k->k+"="+data.getProperty(k).trim()).collect(java.util.stream.Collectors.joining("|"));
    }
    public static CombatTrainingCatalog standard(){return STANDARD;}
    public Technique require(MeleeAttack attack){return Objects.requireNonNull(techniques.get(attack),"undeclared technique");}
    public Collection<Technique> techniques(){return ordered;}
    public int number(String key){return Objects.requireNonNull(numbers.get(key),"missing numeric combat policy "+key);}
    public int generationBaseline(int percentile){for(int i=0;i<cumulative.length;i++)if(percentile<cumulative[i])return baselines[i];throw new IllegalArgumentException("invalid percentile");}
    public int generationSpread(){return number("generation.spread");}
    public String canonicalText(){return canonical;}
    public static CombatTrainingCatalog read(InputStream in)throws IOException{
        byte[] bytes=in.readNBytes(32769);if(bytes.length>32768)throw new IllegalArgumentException("combat catalog exceeds bound");
        var p=new Properties(){@Override public synchronized Object put(Object k,Object v){if(containsKey(k))throw new IllegalArgumentException("duplicate combat property");return super.put(k,v);}};
        p.load(new InputStreamReader(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8));return new CombatTrainingCatalog(p);
    }
    private static CombatTrainingCatalog load(){try(var in=CombatTrainingCatalog.class.getResourceAsStream("/io/farfrontier/palemirror/frontier/v3/combat-training.properties")){
        if(in==null)throw new IllegalStateException("missing combat training");return read(in);
    }catch(IOException failure){throw new UncheckedIOException(failure);}}
    private static String required(Properties p,String key){return Objects.requireNonNull(p.getProperty(key),"missing combat policy "+key).trim();}
    private static int[] numbers(Properties p,String key){return Arrays.stream(required(p,key).split(",")).mapToInt(Integer::parseInt).toArray();}
}
