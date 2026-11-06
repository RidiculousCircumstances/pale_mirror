package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
/** Cross-owner protocol only: the registered family validates/reduces its consequence; storage validates its actual whole image. */
final class PhysicalIntentStorageConfirmation {
    static CommandPlan plan(FrontierWorldState state,FrontierCommand command,PhysicalIntentStorageConfirmed receipt){
        try {
            var intent=state.physicalIntents().get(receipt.expected().id());var lease=receipt.storage();
            if(!receipt.expected().equals(intent) || !lease.equals(state.replicaCustody().custodyByScope().get(lease.scopeId())))
                return FrontierWorldCommandPlanner.rejected("physical storage receipt has stale intent or custody");
            var transition=new PhysicalIntentTransition(intent.id(),PhysicalIntentStatus.CONFIRMED,Optional.of(receipt.observation()));
            var owners=FrontierWorldProcessCatalog.physicalLifecycles();
            var plan=owners.planTransition(state,command,intent,transition);
            if(!(plan instanceof CommandPlan.Accepted accepted))return plan;
            var terminal=accepted.events().stream().filter(e->e.payload() instanceof PhysicalIntentTransition t && t.intentId().equals(intent.id()) && t.status()==PhysicalIntentStatus.CONFIRMED).findFirst().orElseThrow();
            var successor=owners.reduceTransition(state,terminal.subject(),intent,(PhysicalIntentTransition)terminal.payload());
            var events=new ArrayList<>(accepted.events());var replica=state.replicaCustody().replicas().get(lease.objectId());
            if(replica==null)throw new IllegalArgumentException("physical storage receipt lost its reference replica");
            if(ReferenceContainerCustody.canonicalFingerprint(successor,lease.objectId()).equals(receipt.fingerprint()) && replica.provenance().equals(receipt.provenance()))
                events.addAll(ConfirmedContainerMutationPlan.close(successor,List.of(lease.objectId()),command.expectedRevision().next().value()));
            else events.add(new ProposedEvent(lease.scopeId(),ReplicaCustodyDiagnosticProducer.unresolved(lease.scopeId(),lease.authorityEpoch(),lease.expectedCanonicalRevision(),lease.expectedReplicaRevision(),
                    PhysicalCustodyUnresolvedReason.OBSERVATION_MISMATCH)));
            return new CommandPlan.Accepted(List.copyOf(events));
        }catch(IllegalArgumentException invalid){return FrontierWorldCommandPlanner.rejected(invalid.getMessage());}
    }
    private PhysicalIntentStorageConfirmation(){}
}
