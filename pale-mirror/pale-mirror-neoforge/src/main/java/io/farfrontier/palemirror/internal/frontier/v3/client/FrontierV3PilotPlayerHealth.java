package io.farfrontier.palemirror.internal.frontier.v3.client;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
/** Read-only client packet state. This observer cannot cause or confirm a domain damage outcome. */
final class FrontierV3PilotPlayerHealth {
    private final Map<String,Float> observations=new HashMap<>();
    void clear(){observations.clear();}
    boolean reached(float health,JsonObject action) {
        Float baseline=action.has("minimumFrom")?observations.get(action.get("minimumFrom").getAsString()):null;
        if(action.has("minimumFrom") && baseline==null)throw new IllegalArgumentException("missing player health observation");
        boolean result=(!action.has("minimum") || health>=action.get("minimum").getAsDouble())
                && (!action.has("maximum") || health<=action.get("maximum").getAsDouble())
                && (baseline==null || health>=baseline);
        if(result){
            if(action.has("captureAs")){
                var key=action.get("captureAs").getAsString();
                if(observations.size()>=64 || observations.putIfAbsent(key,health)!=null)
                    throw new IllegalArgumentException("duplicate or excessive player health observation");
            }
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info("PMV3_PILOT player_health value={} minimum={} maximum={} captureAs={} minimumFrom={} baseline={}",
                    health,action.get("minimum"),action.get("maximum"),action.get("captureAs"),action.get("minimumFrom"),baseline);
        }
        return result;
    }
}
