package io.farfrontier.palemirror.frontier.v3.model.clinical;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
public final class ClinicalContinuation {
    public static final String REVIEW="frontier.clinical.review",PROGRESS="frontier.clinical.progress";
    public static ScheduledAction review(SubjectId home,long tick){return new ScheduledAction(new ScheduleId("schedule:clinical-review-"+home.value().replace(':','-')),new SimInstant(tick),1,home,REVIEW,1);}
    public static ScheduledAction progress(SubjectId task,long tick){return new ScheduledAction(new ScheduleId("schedule:clinical-progress-"+task.value().replace(':','-')),new SimInstant(tick),2,task,PROGRESS,1);}
    public static ProposedEvent wake(SubjectId task,long tick){return new ProposedEvent(task,new ScheduleEffect.ReconsiderationRequested(progress(task,tick+1)));}
    private ClinicalContinuation(){}
}
