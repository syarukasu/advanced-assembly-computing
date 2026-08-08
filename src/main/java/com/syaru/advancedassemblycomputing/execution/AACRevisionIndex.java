package com.syaru.advancedassemblycomputing.execution;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Transaction UUIDから物理Worker/Threadへ引く索引。
 *
 * <p>lookupは現在の索引だけを確認する。全構造走査はload後に一度だけ、
 * または明示的な構造再構築要求時だけ行う。</p>
 */
public final class AACRevisionIndex<T> {
    private final Map<UUID, Entry<T>> entries = new HashMap<>();
    private boolean rebuildRequired = true;

    public synchronized void put(
            UUID transactionId,
            String payloadDigest,
            T owner) {
        entries.put(
                Objects.requireNonNull(transactionId, "transactionId"),
                new Entry<>(
                        payloadDigest,
                        Objects.requireNonNull(owner, "owner")));
    }

    public synchronized Optional<T> lookup(
            UUID transactionId,
            String payloadDigest,
            Function<T, Boolean> stillOwns) {
        Entry<T> entry = entries.get(transactionId);
        if (entry == null
                || !entry.payloadDigest().equals(payloadDigest)
                || !Boolean.TRUE.equals(stillOwns.apply(entry.owner()))) {
            if (entry != null) {
                entries.remove(transactionId);
            }
            return Optional.empty();
        }
        AACRevisionMetrics.pollAvoided();
        return Optional.of(entry.owner());
    }

    public synchronized void remove(UUID transactionId) {
        entries.remove(transactionId);
    }

    public synchronized void requestRebuild() {
        rebuildRequired = true;
    }

    public synchronized boolean rebuildRequired() {
        return rebuildRequired;
    }

    public synchronized int rebuild(
            Iterable<T> owners,
            Function<T, Optional<IndexedTarget<T>>> discover) {
        entries.clear();
        int found = 0;
        for (T owner : owners) {
            AACRevisionMetrics.threadScan();
            Optional<IndexedTarget<T>> target = discover.apply(owner);
            if (target.isEmpty()) {
                continue;
            }
            IndexedTarget<T> indexed = target.orElseThrow();
            entries.put(
                    indexed.transactionId(),
                    new Entry<>(
                            indexed.payloadDigest(),
                            indexed.owner()));
            found++;
        }
        rebuildRequired = false;
        AACRevisionMetrics.fullIndexRebuild();
        return found;
    }

    public synchronized int size() {
        return entries.size();
    }

    public record IndexedTarget<T>(
            UUID transactionId,
            String payloadDigest,
            T owner) {
        public IndexedTarget {
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(payloadDigest, "payloadDigest");
            Objects.requireNonNull(owner, "owner");
        }
    }

    private record Entry<T>(String payloadDigest, T owner) {
    }
}
