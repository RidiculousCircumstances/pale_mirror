package io.farfrontier.palemirror.internal.network;
/** Physical gesture vocabulary. No profession, job selection or resource rule. */
public enum ResidentWorkGesture {
    CLEAR(0),STRIKE(1),CULTIVATE(2),HANDLE(3);
    private final int tag;
    ResidentWorkGesture(int tag){this.tag=tag;}
    public int wireTag(){return tag;}
    public static ResidentWorkGesture require(int tag){for(var value:values())if(value.tag==tag)return value;throw new IllegalArgumentException("unknown resident work gesture");}
}
