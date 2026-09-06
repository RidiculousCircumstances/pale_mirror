package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

/** Explicit codec for one immutable payload kind; unknown kinds fail recovery closed. */
public interface PayloadCodec {
    String type();

    byte[] encode(FrontierPayload payload);

    FrontierPayload decode(byte[] encoded);
}
