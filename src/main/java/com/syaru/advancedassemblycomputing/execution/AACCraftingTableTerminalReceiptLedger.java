package com.syaru.advancedassemblycomputing.execution;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatchCodec;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * NeoECO Workerと同じNBTへ保存する、完了済み物理作業台仕事の台帳。
 *
 * <p>Thread解放とReceipt保存を同じBlock Entityの保存単位へ置くことで、
 * ACO親Jobの保存順が前後しても実出力を再照合できる。</p>
 */
public final class AACCraftingTableTerminalReceiptLedger {
    private static final int SCHEMA_VERSION = 1;
    /** 親Jobが停止したままでも、有限メモリで安全に待てる終端Receipt件数。 */
    private static final int MAXIMUM_RECEIPTS = 16_384;
    /** 一Receiptに保存できる主出力と返却物のAEKey数。 */
    private static final int MAXIMUM_OUTPUT_KEYS = 65_536;

    private final Map<UUID, Receipt> receipts =
            new LinkedHashMap<>();
    private boolean corrupted;
    private CompoundTag lockedPayload;

    public static int schemaVersion() {
        return SCHEMA_VERSION;
    }

    public synchronized boolean isHealthy() {
        return !corrupted;
    }

    public synchronized boolean contains(
            UUID transactionId,
            String payloadDigest) {
        Receipt receipt =
                receipts.get(
                        Objects.requireNonNull(
                                transactionId,
                                "transactionId"));
        return receipt != null
                && receipt.payloadDigest()
                        .equals(
                                checkedDigest(
                                        payloadDigest));
    }

    public synchronized Optional<CraftingTableBatchSnapshot> snapshot(
            UUID transactionId,
            String payloadDigest) {
        Receipt receipt =
                receipts.get(
                        Objects.requireNonNull(
                                transactionId,
                                "transactionId"));
        // 同じUUIDでもPayloadが違うReceiptを別仕事の完了証明として返さない。
        if (receipt == null
                || !receipt.payloadDigest()
                        .equals(
                                checkedDigest(
                                        payloadDigest))) {
            return Optional.empty();
        }
        return Optional.of(
                new CraftingTableBatchSnapshot(
                        receipt.transactionId(),
                        receipt.payloadDigest(),
                        CraftingTableBatchSnapshot.State.ACKNOWLEDGED,
                        1,
                        1,
                        receipt.exactOutputs(),
                        "NeoECO worker completed and retained a durable receipt"));
    }

    public synchronized boolean record(
            UUID transactionId,
            String payloadDigest,
            Map<AEKey, BigInteger> exactOutputs) {
        // 破損台帳へ新しい完了証明を混ぜず、Workerを出力待ちのまま保持する。
        if (corrupted) {
            return false;
        }
        Receipt replacement =
                new Receipt(
                        transactionId,
                        payloadDigest,
                        exactOutputs);
        Receipt existing =
                receipts.get(
                        replacement.transactionId());
        // 再送されたackは、完全に同じReceiptなら成功として扱う。
        if (existing != null) {
            return existing.equals(
                    replacement);
        }
        // 上限時は古い証明を推測で捨てず、親Jobの明示forgetを待つ。
        if (receipts.size()
                >= MAXIMUM_RECEIPTS) {
            return false;
        }
        receipts.put(
                replacement.transactionId(),
                replacement);
        return true;
    }

    public synchronized boolean forget(
            UUID transactionId,
            String payloadDigest) {
        // 破損台帳は管理者調査用の原文を保持し、個別削除を受理しない。
        if (corrupted) {
            return false;
        }
        UUID checkedId =
                Objects.requireNonNull(
                        transactionId,
                        "transactionId");
        Receipt existing =
                receipts.get(
                        checkedId);
        // 既に削除済みなら、親Jobの再送を冪等な成功として扱う。
        if (existing == null) {
            return true;
        }
        // 同じUUIDの別Payloadを削除しない。
        if (!existing.payloadDigest()
                .equals(
                        checkedDigest(
                                payloadDigest))) {
            return false;
        }
        receipts.remove(
                checkedId);
        return true;
    }

    public synchronized boolean isEmpty() {
        return receipts.isEmpty()
                && !corrupted;
    }

    public synchronized CompoundTag save() {
        // 破損NBTは書き換えず、そのまま管理者が回収できるように保持する。
        if (lockedPayload != null) {
            return lockedPayload.copy();
        }
        CompoundTag owner =
                new CompoundTag();
        owner.putInt(
                "schema",
                SCHEMA_VERSION);
        ListTag entries =
                new ListTag();
        // 一件の物理段につき一件だけ保存し、注文数量ではNBT件数を増やさない。
        for (Receipt receipt :
                receipts.values()) {
            CompoundTag entry =
                    new CompoundTag();
            entry.putUUID(
                    "transactionId",
                    receipt.transactionId());
            entry.putString(
                    "payloadDigest",
                    receipt.payloadDigest());
            entry.put(
                    "exactOutputs",
                    encodeCounts(
                            receipt.exactOutputs()));
            entries.add(
                    entry);
        }
        owner.put(
                "entries",
                entries);
        return owner;
    }

    public synchronized void load(
            CompoundTag owner) {
        receipts.clear();
        corrupted =
                false;
        lockedPayload =
                null;
        // 初回導入前のWorkerには台帳NBTがない。
        if (owner.isEmpty()) {
            return;
        }
        Tag rawEntries =
                owner.get(
                        "entries");
        // 未知schema、型違い、過大件数は部分復元せず台帳全体をロックする。
        if (owner.getInt(
                            "schema")
                        != SCHEMA_VERSION
                || !(rawEntries
                        instanceof ListTag entries)
                || (!entries.isEmpty()
                        && entries.getElementType()
                                != Tag.TAG_COMPOUND)
                || entries.size()
                        > MAXIMUM_RECEIPTS) {
            lock(
                    owner);
            return;
        }
        // 全件が正しい場合だけ復元を確定し、途中までのReceiptを公開しない。
        for (int index = 0;
                index < entries.size();
                index++) {
            try {
                CompoundTag entry =
                        entries.getCompound(
                                index);
                // UUID欠落は別仕事との照合ができないため台帳をロックする。
                if (!entry.hasUUID(
                        "transactionId")) {
                    throw new IllegalArgumentException(
                            "terminal receipt has no transaction id");
                }
                Receipt receipt =
                        new Receipt(
                                entry.getUUID(
                                        "transactionId"),
                                entry.getString(
                                        "payloadDigest"),
                                decodeCounts(
                                        entry.get(
                                                "exactOutputs")));
                // 同一UUIDの二重Receiptはどちらを正本にするか推測しない。
                if (receipts.putIfAbsent(
                                receipt.transactionId(),
                                receipt)
                        != null) {
                    throw new IllegalArgumentException(
                            "duplicate terminal receipt");
                }
            } catch (RuntimeException | LinkageError invalid) {
                lock(
                        owner);
                return;
            }
        }
    }

    private void lock(
            CompoundTag owner) {
        receipts.clear();
        corrupted =
                true;
        lockedPayload =
                owner.copy();
    }

    private static ListTag encodeCounts(
            Map<AEKey, BigInteger> counts) {
        ListTag result =
                new ListTag();
        // AEKeyごとに正確なBigInteger量を一件ずつ保存する。
        for (Map.Entry<AEKey, BigInteger> entry :
                counts.entrySet()) {
            CompoundTag encoded =
                    new CompoundTag();
            encoded.put(
                    "key",
                    entry.getKey()
                            .toTagGeneric());
            PreparedVectorBatchCodec.putNonNegative(
                    encoded,
                    "amount",
                    entry.getValue());
            result.add(
                    encoded);
        }
        return result;
    }

    private static Map<AEKey, BigInteger> decodeCounts(
            Tag raw) {
        // 出力一覧はCompound Listだけを受理し、過大な配列確保を拒否する。
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty()
                        && list.getElementType()
                                != Tag.TAG_COMPOUND)
                || list.size()
                        > MAXIMUM_OUTPUT_KEYS) {
            throw new IllegalArgumentException(
                    "invalid terminal receipt output list");
        }
        Map<AEKey, BigInteger> result =
                new LinkedHashMap<>();
        // キーと数量を全件検証し、重複キーを合算して隠さない。
        for (int index = 0;
                index < list.size();
                index++) {
            CompoundTag encoded =
                    list.getCompound(
                            index);
            AEKey key =
                    AEKey.fromTagGeneric(
                            encoded.getCompound(
                                    "key"));
            BigInteger amount =
                    PreparedVectorBatchCodec.readNonNegative(
                            encoded,
                            "amount");
            // 完了Receiptは正数・API上限内・一意キーだけを保存する。
            if (key == null
                    || amount.signum() <= 0
                    || amount.bitLength()
                            > CraftingTableBatchRequest
                                    .MAXIMUM_COUNT_BITS
                    || result.putIfAbsent(
                                    key,
                                    amount)
                            != null) {
                throw new IllegalArgumentException(
                        "invalid terminal receipt output");
            }
        }
        // 出力のない完了仕事は存在しない。
        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "terminal receipt has no outputs");
        }
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(
                        result));
    }

    private static String checkedDigest(
            String payloadDigest) {
        String checked =
                Objects.requireNonNull(
                                payloadDigest,
                                "payloadDigest")
                        .trim();
        // ACO Requestと同じ識別上限を超える文字列を台帳へ保存しない。
        if (checked.isEmpty()
                || checked.length()
                        > 128) {
            throw new IllegalArgumentException(
                    "invalid terminal receipt payload digest");
        }
        return checked;
    }

    private record Receipt(
            UUID transactionId,
            String payloadDigest,
            Map<AEKey, BigInteger> exactOutputs) {
        private Receipt {
            Objects.requireNonNull(
                    transactionId,
                    "transactionId");
            payloadDigest =
                    checkedDigest(
                            payloadDigest);
            Map<AEKey, BigInteger> checked =
                    new LinkedHashMap<>();
            Objects.requireNonNull(
                            exactOutputs,
                            "exactOutputs")
                    .forEach(
                            (key, amount) -> {
                                Objects.requireNonNull(
                                        key,
                                        "exact output key");
                                // 実出力は正数かつACO APIの固定bit上限内だけを受理する。
                                if (amount == null
                                        || amount.signum()
                                                <= 0
                                        || amount.bitLength()
                                                > CraftingTableBatchRequest
                                                        .MAXIMUM_COUNT_BITS
                                        || checked.putIfAbsent(
                                                        key,
                                                        amount)
                                                != null) {
                                    throw new IllegalArgumentException(
                                            "invalid exact terminal output");
                                }
                            });
            // 実出力なしのThreadを完了済みにしてはならない。
            if (checked.isEmpty()) {
                throw new IllegalArgumentException(
                        "terminal receipt has no exact outputs");
            }
            exactOutputs =
                    Collections.unmodifiableMap(
                            new LinkedHashMap<>(
                                    checked));
        }
    }
}
