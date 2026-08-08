package com.syaru.advancedassemblycomputing.execution;

import com.syaru.ae2craftingoptimizer.api.contract.BatchTargetRevision;
import com.syaru.ae2craftingoptimizer.api.contract.ExactCountLimits;
import com.syaru.ae2craftingoptimizer.api.contract.RevisionWakeupApi;
import java.util.Objects;

/**
 * AAC物理Targetの変更世代。
 *
 * <p>変化しないtickでは値を増やさず、変化したときだけACOの弱参照通知へ流す。
 * ACOの公開契約にまだcapacityRevision欄がないため、capacity世代はstateHintへ
 * 機械可読な補助値として含める。</p>
 */
public final class AACRevisionState {
    private long ownershipRevision;
    private long progressRevision;
    private long receiptRevision;
    private long capacityRevision;
    private long aggregateRevision;

    public synchronized Revision current() {
        return new Revision(
                ownershipRevision,
                progressRevision,
                receiptRevision,
                capacityRevision,
                aggregateRevision);
    }

    public synchronized BatchTargetRevision touchAndPublish(
            String targetIdentity,
            String runtimeIdentity,
            String transactionId,
            Change change,
            String stateHint) {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(stateHint, "stateHint");
        switch (change) {
            case OWNERSHIP -> ownershipRevision = increment(ownershipRevision);
            case PROGRESS -> progressRevision = increment(progressRevision);
            case RECEIPT -> receiptRevision = increment(receiptRevision);
            case CAPACITY -> capacityRevision = increment(capacityRevision);
        }
        aggregateRevision = increment(aggregateRevision);
        String wireHint = stateHint
                + ";capacityRevision="
                + capacityRevision;
        BatchTargetRevision revision = new BatchTargetRevision(
                targetIdentity,
                runtimeIdentity,
                transactionId,
                ownershipRevision,
                progressRevision,
                receiptRevision,
                wireHint,
                ExactCountLimits.defaults());
        AACRevisionMetrics.wakeupNotification();
        RevisionWakeupApi.publish(revision);
        return revision;
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE
                ? Long.MAX_VALUE
                : value + 1L;
    }

    public enum Change {
        OWNERSHIP,
        PROGRESS,
        RECEIPT,
        CAPACITY
    }

    public record Revision(
            long ownershipRevision,
            long progressRevision,
            long receiptRevision,
            long capacityRevision,
            long aggregateRevision) {
    }
}
