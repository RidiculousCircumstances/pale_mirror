package io.farfrontier.palemirror.frontier.v3.model.rest;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Objects;

/** One complete bed, not one place per bed half. Access is proved by shared navigation. */
public record RestPlace(SettlementBeds.Id id,SubjectId home,BlockPosition head,BlockPosition foot,RestPlaceProfile profile,boolean present){
    public RestPlace {
        Objects.requireNonNull(id);Objects.requireNonNull(home);Objects.requireNonNull(head);Objects.requireNonNull(foot);Objects.requireNonNull(profile);
        if(head.y()!=foot.y()||Math.abs(head.x()-foot.x())+Math.abs(head.z()-foot.z())!=1)throw new IllegalArgumentException("incomplete resting bed");
    }
}
