package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;

/** One explicit legal declaration grammar shared by agreements and production rights. */
final class WorkEmployerCodec {
    private WorkEmployerCodec() { }
    static void write(DataOutputStream out, WorkEmployer employer) throws IOException {
        out.writeUTF(employer.id().value()); out.writeByte(employer.kind().wireTag());
        out.writeUTF(employer.settlementId().value());
    }
    static WorkEmployer read(DataInputStream in) throws IOException {
        return new WorkEmployer(new SubjectId(in.readUTF()),
                FrontierWireTags.require(EconomicOwnerKind.class, in.readUnsignedByte()), new SubjectId(in.readUTF()));
    }
}
