package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import com.google.gson.*;
/** Read-only shared trace/inspection projection, never an eligibility authority. */
final class FrontierV3CombatProfileView {
    static JsonObject json(CombatProfile profile){
        var row=new JsonObject();var a=profile.attributes();row.addProperty("strength",a.strength());row.addProperty("agility",a.agility());
        row.addProperty("endurance",a.endurance());row.addProperty("perception",a.perception());row.addProperty("toughness",a.toughness());
        var skills=new JsonObject();profile.skills().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(e->skills.addProperty(e.getKey().value(),e.getValue()));row.add("skills",skills);
        var techniques=new JsonArray();for(var t:CombatTrainingCatalog.standard().techniques())
            if(profile.knows(CombatSkillId.SWORD,t.minimum()))techniques.add(t.id().name());row.add("swordTechniques",techniques);
        var ranged=new JsonArray();if(profile.skills().containsKey(CombatSkillId.CROSSBOW))
            for(var t:RangedTechnique.values())if(t.unlocked(profile))ranged.add(t.name());
        row.add("crossbowTechniques",ranged);return row;
    }
    private FrontierV3CombatProfileView(){}
}
