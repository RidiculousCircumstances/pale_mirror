package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;
/** Same-owner cargo authority plus one existing transport/group declaration; no commercial contract. */
public record InternalTransportMissionStarted(TransportMissionStarted transport,List<ClaimAllocation> claims) implements FrontierPayload {
    public static final String TYPE="frontier.internal_transport_mission_started";
    public InternalTransportMissionStarted {Objects.requireNonNull(transport);claims=List.copyOf(claims);
        if(claims.isEmpty()||claims.size()!=transport.shipments().size()||claims.stream().map(ClaimAllocation::id).distinct().count()!=claims.size())
            throw new IllegalArgumentException("incomplete internal transport manifest");}
    public String type(){return TYPE;}
    public boolean requiresDurableBeforeEffect(){return true;}
}
