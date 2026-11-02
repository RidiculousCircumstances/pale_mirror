package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;
/** Coverage owns a post bookmark; temporary response never overwrites the place to resume. */
public record GuardMission(ProtectedTarget.Kind targetKind, SubjectId target, SurfaceAnchor station, long visitOrdinal,
        long watchUntil, Optional<CombatTarget> response, Optional<Post> resume, Stage stage, Optional<Search> search) {
    public enum Stage {
        PATROL(1), WATCH(2), INTERCEPT(3), SEARCH(4), RETURN(5);
        private final int tag; Stage(int tag){this.tag=tag;} public int wireTag(){return tag;}
        public static Stage require(int tag){for(var s:values())if(s.tag==tag)return s;throw new IllegalArgumentException("unknown guard mission stage");}
    }
    public record Search(long sightingRevision, int probe, long pauseUntil, List<SurfaceAnchor> visited) {
        public Search {visited=List.copyOf(visited);if(sightingRevision<1 || probe<0 || probe>8 || pauseUntil<0 || visited.isEmpty() || visited.size()!=probe+1
                || visited.stream().distinct().count()!=visited.size())throw new IllegalArgumentException("invalid bounded guard search");}
    }
    public record Post(ProtectedTarget.Kind kind, SubjectId target, SurfaceAnchor station, long visitOrdinal) {
        public Post { Objects.requireNonNull(kind); Objects.requireNonNull(target); Objects.requireNonNull(station);
            if(visitOrdinal<0)throw new IllegalArgumentException("invalid patrol visitOrdinal"); }
        public GuardMission mission() { return new GuardMission(kind,target,station,visitOrdinal,0,Stage.RETURN); }
    }
    public GuardMission(ProtectedTarget.Kind kind, SubjectId target, SurfaceAnchor station, long visitOrdinal, long until, Stage stage) {
        this(kind,target,station,visitOrdinal,until,Optional.empty(),Optional.empty(),stage,Optional.empty());
    }
    public GuardMission {
        Objects.requireNonNull(stage); search=Objects.requireNonNull(search);
        response=Objects.requireNonNull(response); resume=Objects.requireNonNull(resume);
        Objects.requireNonNull(targetKind); Objects.requireNonNull(target); Objects.requireNonNull(station);
        if(visitOrdinal<0 || watchUntil<0 || response.isPresent()!=resume.isPresent()
                || response.isPresent()!=(stage==Stage.INTERCEPT || stage==Stage.SEARCH)
                || search.isPresent()!=(stage==Stage.SEARCH) || (watchUntil>0)!=(stage==Stage.WATCH))
            throw new IllegalArgumentException("invalid guard mission clock or response bookmark");
    }
    public Post post() { return resume.orElseGet(()->new Post(targetKind,target,station,visitOrdinal)); }
    public GuardMission responding(SurfaceAnchor position, CombatTarget threat) {
        return new GuardMission(targetKind,target,position,visitOrdinal,0,Optional.of(threat),Optional.of(post()),Stage.INTERCEPT,Optional.empty());
    }
    public GuardMission searching(SurfaceAnchor station, int probe, long pauseUntil, long sightingRevision) {
        var visited=new ArrayList<SurfaceAnchor>(search.filter(s -> s.sightingRevision()==sightingRevision).map(Search::visited).orElse(List.of()));
        if(!visited.contains(station))visited.add(station);
        return new GuardMission(targetKind,target,station,visitOrdinal,0,response,Optional.of(post()),Stage.SEARCH,
                Optional.of(new Search(sightingRevision,probe,pauseUntil,visited)));
    }
    public GuardMission watching(long until) {
        if(until<1 || response.isPresent())throw new IllegalArgumentException("invalid watch boundary");
        return new GuardMission(targetKind,target,station,visitOrdinal,until,Stage.WATCH);
    }
}
