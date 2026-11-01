package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;
/** Coverage owns a post bookmark; temporary response never overwrites the place to resume. */
public record GuardMission(ProtectedTarget.Kind targetKind, SubjectId target, SurfaceAnchor station, long visitOrdinal,
        long watchUntil, Optional<CombatTarget> response, Optional<Post> resume) {
    public record Post(ProtectedTarget.Kind kind, SubjectId target, SurfaceAnchor station, long visitOrdinal) {
        public Post { Objects.requireNonNull(kind); Objects.requireNonNull(target); Objects.requireNonNull(station);
            if(visitOrdinal<0)throw new IllegalArgumentException("invalid patrol visitOrdinal"); }
        public GuardMission mission() { return new GuardMission(kind,target,station,visitOrdinal,0); }
    }
    public GuardMission(ProtectedTarget.Kind kind, SubjectId target, SurfaceAnchor station, long visitOrdinal, long until) {
        this(kind,target,station,visitOrdinal,until,Optional.empty(),Optional.empty());
    }
    public GuardMission {
        response=Objects.requireNonNull(response); resume=Objects.requireNonNull(resume);
        Objects.requireNonNull(targetKind); Objects.requireNonNull(target); Objects.requireNonNull(station);
        if(visitOrdinal<0 || watchUntil<0 || response.isPresent()!=resume.isPresent() || response.isPresent() && watchUntil!=0)
            throw new IllegalArgumentException("invalid guard mission clock or response bookmark");
    }
    public Post post() { return resume.orElseGet(()->new Post(targetKind,target,station,visitOrdinal)); }
    public GuardMission responding(SurfaceAnchor position, CombatTarget threat) {
        return new GuardMission(targetKind,target,position,visitOrdinal,0,Optional.of(threat),Optional.of(post()));
    }
    public GuardMission watching(long until) {
        if(until<1 || response.isPresent())throw new IllegalArgumentException("invalid watch boundary");
        return new GuardMission(targetKind,target,station,visitOrdinal,until);
    }
}
