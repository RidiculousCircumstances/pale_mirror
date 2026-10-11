package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** Closed causal source exit protocol, independent of the allocation owner's business rules. */
public sealed interface GroundStockExit extends FrontierPayload permits GroundResourceDestroyed, GroundResourcePlayerWithdrawal {
    SubjectId sourceAccountId();
    ResourceCustody.Ground source();
    long epoch();
    SubjectId economicOwner();
    UUID witnessId();
    Set<SubjectId> forfeitedClaims();
    FrontierWorldState apply(FrontierWorldState state, SubjectId owner);
}
