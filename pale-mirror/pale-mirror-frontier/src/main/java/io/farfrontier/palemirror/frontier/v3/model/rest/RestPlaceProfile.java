package io.farfrontier.palemirror.frontier.v3.model.rest;

/** Place properties, independently reusable by sleep and clinical recovery. */
public record RestPlaceProfile(Purpose purpose,int recoveryPermille){
    public enum Purpose { RESIDENTIAL(1), MEDICAL(2); private final int tag; Purpose(int tag){this.tag=tag;} public int wireTag(){return tag;} public static Purpose fromWireTag(int tag){for(var v:values())if(v.tag==tag)return v;
        throw new IllegalArgumentException("unknown rest purpose");} }
    public RestPlaceProfile {
        java.util.Objects.requireNonNull(purpose);
        if(recoveryPermille<1000||recoveryPermille>10000)throw new IllegalArgumentException("invalid rest recovery factor");
    }
    public static RestPlaceProfile ordinary(){return new RestPlaceProfile(Purpose.RESIDENTIAL,3000);}
    public static RestPlaceProfile medical(){return new RestPlaceProfile(Purpose.MEDICAL,5000);}
}
