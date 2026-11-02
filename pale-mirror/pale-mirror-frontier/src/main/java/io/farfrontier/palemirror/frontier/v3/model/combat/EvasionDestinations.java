package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;
/** Pure bounded ranking; shared known geometry/routes supply the candidates. */
public final class EvasionDestinations {
    public static List<SurfaceAnchor> rank(SurfaceAnchor origin,Optional<SurfaceAnchor> threat,List<SurfaceAnchor> candidates,List<SurfaceAnchor> protection,EvasionRules rules){
        return candidates.stream().distinct().filter(p->!p.equals(origin))
                .filter(p->threat.isEmpty() || ((double)p.x()-origin.x())*(threat.orElseThrow().x()-(double)origin.x())
                    +((double)p.z()-origin.z())*(threat.orElseThrow().z()-(double)origin.z())<=0)
                .filter(p->threat.isEmpty() || distance(p,threat.orElseThrow())>distance(origin,threat.orElseThrow())+1)
                .sorted(Comparator.comparingDouble((SurfaceAnchor p)->score(origin,threat,p,protection,rules))
                        .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y)).toList();
    }
    private static double score(SurfaceAnchor origin,Optional<SurfaceAnchor> threat,SurfaceAnchor point,List<SurfaceAnchor> protection,EvasionRules rules){
        double separation=threat.map(t->Math.min(rules.safeRadius(),distance(t,point))).orElse(0.0);
        double friendly=protection.stream().mapToDouble(p->distance(p,point)).min().orElse(rules.searchRadius());
        return -4*separation+distance(origin,point)+0.5*friendly;
    }
    public static double distance(SurfaceAnchor a,SurfaceAnchor b){double dx=(double)a.x()-b.x(),dy=(double)a.y()-b.y(),dz=(double)a.z()-b.z();return Math.sqrt(dx*dx+dy*dy+dz*dz);}
    private EvasionDestinations(){}
}
