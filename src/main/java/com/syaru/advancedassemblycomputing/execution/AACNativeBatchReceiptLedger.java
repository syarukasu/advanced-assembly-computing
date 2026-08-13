package com.syaru.advancedassemblycomputing.execution;

import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceipt;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** ACO公開Receipt型だけを保存するPattern Bus単位の永続台帳。 */
public final class AACNativeBatchReceiptLedger {
    /** payloadDigestを必須化した現行NBT schema。 */
    private static final int SCHEMA_VERSION = 2;
    /** 一つのPattern Bus NBTを有限に保つReceipt上限。 */
    private static final int MAX_RECEIPTS = 256;
    /** 20 TPSで10分に相当する、確認済み終端Receiptの保持期間。 */
    private static final long TERMINAL_RETENTION_TICKS = 12_000L;

    private final Map<UUID, NativeBatchReceipt> receipts = new LinkedHashMap<>();
    private boolean corrupted;
    private CompoundTag lockedPayload;

    public synchronized boolean isHealthy() {
        return !corrupted;
    }

    public synchronized NativeBatchReceipt get(UUID id) {
        return receipts.get(id);
    }

    public synchronized boolean isEmpty() {
        return receipts.isEmpty() && !corrupted;
    }

    public synchronized boolean prepare(NativeBatchReceipt receipt) {
        // 破損台帳へ新しい所有権を混ぜず、raw NBTを保全する。
        if (corrupted) {
            return false;
        }
        // 新しいReceiptは完全なpayloadを持つPENDING状態だけを受け付ける。
        if (!isValidCurrentReceipt(receipt)
                || receipt.state() != NativeBatchReceipt.State.PENDING) {
            throw new IllegalArgumentException(
                    "new native batch receipts must be valid and begin in PENDING");
        }
        NativeBatchReceipt existing = receipts.get(receipt.transactionId());
        // 完全に同じprepare再送だけを冪等な成功として扱う。
        if (existing != null) {
            return samePayload(existing, receipt);
        }
        evictExpiredTerminalReceipts(receipt.updatedTick());
        // 未解決Receiptを追い出して容量を作ることはしない。
        if (receipts.size() >= MAX_RECEIPTS) {
            return false;
        }
        receipts.put(receipt.transactionId(), receipt);
        return true;
    }

    public synchronized void finish(UUID id, NativeBatchReceipt.State state, long updatedTick) {
        // raw NBTを隔離中は状態遷移を許可しない。
        if (corrupted) {
            throw new IllegalStateException("native batch receipt ledger is malformed");
        }
        // finishはACCEPTEDまたはREJECTEDへの終端遷移だけを表す。
        if (state == null || state == NativeBatchReceipt.State.PENDING) {
            throw new IllegalArgumentException("finish state must be terminal");
        }
        NativeBatchReceipt current = receipts.get(id);
        // 未知のTransactionを終端化すると所有権を捏造するため拒否する。
        if (current == null) {
            throw new IllegalStateException("unknown native batch receipt " + id);
        }
        // 異なる終端結果への上書きは複製・消失の原因になるため拒否する。
        if (current.state() != NativeBatchReceipt.State.PENDING && current.state() != state) {
            throw new IllegalStateException(
                    "native batch receipt already completed as " + current.state());
        }
        receipts.put(
                id,
                new NativeBatchReceipt(
                        id,
                        state,
                        current.executions(),
                        current.patternFingerprint(),
                        current.payloadDigest(),
                        Math.max(current.updatedTick(), updatedTick)));
    }

    public synchronized boolean removeTerminal(UUID id) {
        // 破損台帳や未完了Receiptをforgetで消さない。
        if (corrupted) {
            return false;
        }
        NativeBatchReceipt receipt = receipts.get(id);
        // PENDINGの所有権は実行・回収判断が終わるまで保持する。
        if (receipt == null || receipt.state() == NativeBatchReceipt.State.PENDING) {
            return false;
        }
        receipts.remove(id);
        return true;
    }

    public synchronized CompoundTag save() {
        // 隔離payloadは正規化せず、読み込んだ形の防御copyを返す。
        if (lockedPayload != null) {
            return lockedPayload.copy();
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putBoolean("corrupted", false);
        ListTag entries = new ListTag();
        // insertion順を維持し、再起動後も同じ追い出し順序にする。
        for (NativeBatchReceipt receipt : receipts.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", receipt.transactionId());
            entry.putString("state", receipt.state().name());
            entry.putLong("executions", receipt.executions());
            entry.putString("pattern", receipt.patternFingerprint());
            entry.putString("payloadDigest", receipt.payloadDigest());
            entry.putLong("updatedTick", receipt.updatedTick());
            entries.add(entry);
        }
        tag.put("entries", entries);
        return tag;
    }

    public synchronized void load(CompoundTag tag) {
        receipts.clear();
        corrupted = false;
        lockedPayload = null;
        // NBTがない新規Block Entityは空台帳として開始する。
        if (tag.isEmpty()) {
            return;
        }
        int schema = tag.getInt("schema");
        // schema 1にはpayloadDigestがなく所有権を証明できないため、原文のまま隔離する。
        if (schema != SCHEMA_VERSION || tag.getBoolean("corrupted")) {
            lock(tag);
            return;
        }
        Tag rawEntries = tag.get("entries");
        // 不正型、混在要素、過剰件数は部分的に解釈せず全体を隔離する。
        if (!(rawEntries instanceof ListTag entries)
                || (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND)
                || entries.size() > MAX_RECEIPTS) {
            lock(tag);
            return;
        }
        // 一件でも不正なら部分台帳を公開せず、owner全体をrawのまま隔離する。
        for (int index = 0; index < entries.size(); index++) {
            try {
                NativeBatchReceipt receipt = readReceipt(entries.getCompound(index));
                // 同じTransaction IDの二重所有は復旧不能なのでraw全体を隔離する。
                if (receipts.putIfAbsent(receipt.transactionId(), receipt) != null) {
                    throw new IllegalArgumentException(
                            "duplicate native receipt id " + receipt.transactionId());
                }
            } catch (RuntimeException failure) {
                lock(tag);
                return;
            }
        }
    }

    private static NativeBatchReceipt readReceipt(CompoundTag entry) {
        NativeBatchReceipt receipt =
                new NativeBatchReceipt(
                        entry.getUUID("id"),
                        NativeBatchReceipt.State.valueOf(entry.getString("state")),
                        entry.getLong("executions"),
                        entry.getString("pattern"),
                        entry.getString("payloadDigest"),
                        entry.getLong("updatedTick"));
        // payloadDigestまで揃った現行形式だけを所有権台帳へ入れる。
        if (!isValidCurrentReceipt(receipt)) {
            throw new IllegalArgumentException("invalid native batch receipt payload");
        }
        return receipt;
    }

    private static boolean isValidLegacyReceipt(NativeBatchReceipt receipt) {
        return receipt != null
                && receipt.transactionId() != null
                && receipt.state() != null
                && receipt.executions() > 0L
                && !receipt.patternFingerprint().isBlank();
    }

    private static boolean isValidCurrentReceipt(NativeBatchReceipt receipt) {
        return isValidLegacyReceipt(receipt) && !receipt.payloadDigest().isBlank();
    }

    private static boolean samePayload(NativeBatchReceipt left, NativeBatchReceipt right) {
        return left.executions() == right.executions()
                && left.patternFingerprint().equals(right.patternFingerprint())
                && left.payloadDigest().equals(right.payloadDigest());
    }

    private void lock(CompoundTag tag) {
        receipts.clear();
        corrupted = true;
        lockedPayload = tag.copy();
    }

    private void evictExpiredTerminalReceipts(long currentTick) {
        Iterator<NativeBatchReceipt> iterator = receipts.values().iterator();
        // 上限時だけ、保持期間を過ぎた終端Receiptを古い順に除去する。
        while (receipts.size() >= MAX_RECEIPTS && iterator.hasNext()) {
            NativeBatchReceipt receipt = iterator.next();
            // PENDINGは所有権の正本なので、保持期間を過ぎても自動削除しない。
            if (receipt.state() != NativeBatchReceipt.State.PENDING
                    && elapsedAtLeast(
                            currentTick, receipt.updatedTick(), TERMINAL_RETENTION_TICKS)) {
                iterator.remove();
            }
        }
    }

    private static boolean elapsedAtLeast(long now, long then, long duration) {
        return now >= then && now - then >= duration;
    }
}
