package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.EquipmentDemand;
import java.util.List;

/** Work families declare required capabilities; equipment policy never examines their job internals. */
interface EquipmentDemandPort {
    enum Key { FIELD, EXTRACTION, FORESTRY, GUARD }
    Key key();
    List<EquipmentDemand> read(FrontierWorldState state, SubjectId home);
}
