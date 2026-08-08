package com.syaru.advancedassemblycomputing.execution;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatchCodec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private static final int SCHEMA_VERSION = 2;
    private static final int LEGACY_SCHEMA_VERSION = 1;
    /** 親Jobが停止したままでも、有限メモリで安全に待てる終端Receipt件数。 */
    private static final int MAXIMUM_RECEIPTS = 16_384;
    /** 一Receiptに保存できる主出力と返却物のAEKey数。 */
    private static final int MAXIMUM_OUTPUT_KEYS = 65_536;

    private final Map<UUID, Receipt> receipts =
            new LinkedHashMap<>();
    private final Map<UUID, String> reservations =
            new LinkedHashMap<>();
    private final Map<UUID, CompoundTag> quarantinedEntries =
            new LinkedHashMap<>();
    private final List<CompoundTag> unknownQuarantinedEntries =
            new ArrayList<>();
    private boolean corrupted;
    private boolean identityUncertain;
    private CompoundTag lockedPayload;

    public static int schemaVersion() {
        return SCHEMA_VERSION;
    }

    public synchronized boolean isHealthy() {
        return !corrupted;
    }

    public synchronized int acknowledgedCount() {
        return receipts.size();
    }

    public synchronized int reservedCount() {
        return reservations.size();
    }

    public synchronized int quarantinedCount() {
        return quarantinedEntries.size()
                + unknownQuarantinedEntries.size()
                + (corrupted ? 1 : 0);
    }

    public synchronized boolean hasIdentityUncertainty() {
        return identityUncertain || corrupted;
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
                && !quarantinedEntries.containsKey(
                        transactionId)
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
        return record(
                transactionId,
                null,
                payloadDigest,
                exactOutputs);
    }

    public synchronized boolean record(
            UUID transactionId,
            UUID ownerTransactionId,
            String payloadDigest,
            Map<AEKey, BigInteger> exactOutputs) {
        // 破損台帳へ新しい完了証明を混ぜず、Workerを出力待ちのまま保持する。
        if (corrupted) {
            return false;
        }
        Receipt replacement =
                new Receipt(
                        transactionId,
                        ownerTransactionId,
                        "ACKNOWLEDGED",
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
        String reservationDigest =
                reservations.get(
                        replacement.transactionId());
        if (quarantinedEntries.containsKey(
                replacement.transactionId())
                || reservationDigest != null
                && !reservationDigest.equals(
                        replacement.payloadDigest())) {
            return false;
        }
        // 上限時は古い証明を推測で捨てず、親Jobの明示forgetを待つ。
        if (reservationDigest == null
                && receipts.size()
                >= MAXIMUM_RECEIPTS) {
            return false;
        }
        reservations.remove(
                replacement.transactionId());
        receipts.put(
                replacement.transactionId(),
                replacement);
        return true;
    }

    /**
     * 受理前に、完了時のReceipt枠をTransaction単位で予約する。
     * 同一Transactionの同一Payloadだけは冪等に再利用できる。
     */
    public synchronized boolean reserve(
            UUID transactionId,
            String payloadDigest) {
        if (corrupted) {
            return false;
        }
        UUID checkedId =
                Objects.requireNonNull(
                        transactionId,
                        "transactionId");
        String checkedDigest =
                checkedDigest(
                        payloadDigest);
        Receipt existing =
                receipts.get(checkedId);
        if (existing != null) {
            return existing.payloadDigest()
                    .equals(checkedDigest);
        }
        if (quarantinedEntries.containsKey(checkedId)
                || identityUncertain) {
            return false;
        }
        String reserved =
                reservations.get(checkedId);
        if (reserved != null) {
            return reserved.equals(checkedDigest);
        }
        if (receipts.size() + reservations.size()
                >= MAXIMUM_RECEIPTS) {
            return false;
        }
        reservations.put(
                checkedId,
                checkedDigest);
        return true;
    }

    /** 受理失敗・取消時に、まだ完了Receiptになっていない枠だけを戻す。 */
    public synchronized boolean releaseReservation(
            UUID transactionId,
            String payloadDigest) {
        UUID checkedId =
                Objects.requireNonNull(
                        transactionId,
                        "transactionId");
        String reserved =
                reservations.get(checkedId);
        if (reserved == null) {
            return true;
        }
        if (!reserved.equals(
                checkedDigest(
                        payloadDigest))) {
            return false;
        }
        reservations.remove(checkedId);
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
        if (quarantinedEntries.containsKey(checkedId)) {
            // 隔離entryは管理者の明示操作なしにforgetしない。
            return false;
        }
        // 既に削除済みなら、親Jobの再送を冪等な成功として扱う。
        if (existing == null) {
            return releaseReservation(
                    checkedId,
                    payloadDigest);
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
                && reservations.isEmpty()
                && quarantinedEntries.isEmpty()
                && unknownQuarantinedEntries.isEmpty()
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
                    "state",
                    receipt.state());
            if (receipt.ownerTransactionId() != null) {
                entry.putUUID(
                        "ownerTransactionId",
                        receipt.ownerTransactionId());
            }
            entry.putString(
                    "payloadDigest",
                    receipt.payloadDigest());
            entry.put(
                    "exactOutputs",
                    encodeCounts(
                            receipt.exactOutputs()));
            entry.putString(
                    "entryFingerprint",
                    fingerprint(entry));
            entries.add(
                    entry);
        }
        owner.put(
                "entries",
                entries);
        ListTag pendingReservations =
                new ListTag();
        for (Map.Entry<UUID, String> reservation :
                reservations.entrySet()) {
            CompoundTag entry =
                    new CompoundTag();
            entry.putUUID(
                    "transactionId",
                    reservation.getKey());
            entry.putString(
                    "payloadDigest",
                    reservation.getValue());
            pendingReservations.add(
                    entry);
        }
        owner.put(
                "reservations",
                pendingReservations);
        ListTag quarantined =
                new ListTag();
        for (Map.Entry<UUID, CompoundTag> entry :
                quarantinedEntries.entrySet()) {
            CompoundTag record =
                    new CompoundTag();
            record.putUUID(
                    "transactionId",
                    entry.getKey());
            record.putString(
                    "state",
                    "QUARANTINED");
            record.put(
                    "rawEntry",
                    entry.getValue().copy());
            quarantined.add(
                    record);
        }
        for (CompoundTag raw : unknownQuarantinedEntries) {
            CompoundTag record =
                    new CompoundTag();
            record.putString(
                    "state",
                    "QUARANTINED");
            record.put(
                    "rawEntry",
                    raw.copy());
            quarantined.add(
                    record);
        }
        owner.put(
                "quarantinedEntries",
                quarantined);
        return owner;
    }

    public synchronized void load(
            CompoundTag owner) {
        receipts.clear();
        reservations.clear();
        quarantinedEntries.clear();
        unknownQuarantinedEntries.clear();
        corrupted =
                false;
        identityUncertain =
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
        Tag rawReservations =
                owner.get(
                        "reservations");
        Tag rawQuarantined =
                owner.get(
                        "quarantinedEntries");
        int schema =
                owner.getInt(
                        "schema");
        // 全体構造が壊れている場合だけ台帳全体をロックする。
        if ((schema != SCHEMA_VERSION
                && schema != LEGACY_SCHEMA_VERSION)
                || !(rawEntries
                        instanceof ListTag entries)
                || (!entries.isEmpty()
                        && entries.getElementType()
                                != Tag.TAG_COMPOUND)
                || entries.size()
                        > MAXIMUM_RECEIPTS
                || (rawReservations != null
                        && (!(rawReservations instanceof ListTag pending)
                                || (!pending.isEmpty()
                                        && pending.getElementType()
                                                != Tag.TAG_COMPOUND)
                                || pending.size()
                                        > MAXIMUM_RECEIPTS))
                || entries.size()
                        + (rawReservations instanceof ListTag pending
                                ? pending.size()
                                : 0)
                        + (rawQuarantined instanceof ListTag quarantined
                                ? quarantined.size()
                                : 0)
                        > MAXIMUM_RECEIPTS
                || (rawQuarantined != null
                        && (!(rawQuarantined instanceof ListTag quarantined)
                                || (!quarantined.isEmpty()
                                        && quarantined.getElementType()
                                                != Tag.TAG_COMPOUND)
                                || quarantined.size()
                                        > MAXIMUM_RECEIPTS))) {
            lock(
                    owner);
            return;
        }
        // 壊れたentryだけをraw隔離し、完全なReceiptはそのまま復元する。
        for (int index = 0;
                index < entries.size();
                index++) {
            CompoundTag entry =
                    entries.getCompound(
                            index);
            try {
                // UUID欠落は別仕事との照合ができないため台帳をロックする。
                if (!entry.hasUUID(
                        "transactionId")) {
                    quarantineEntry(
                            entry);
                    continue;
                }
                Receipt receipt =
                        decodeReceipt(
                                entry,
                                schema);
                if (schema == SCHEMA_VERSION
                        && !entry.getString(
                                        "entryFingerprint")
                                .equals(
                                        fingerprintWithoutFingerprint(
                                                entry))) {
                    throw new IllegalArgumentException(
                            "terminal receipt fingerprint mismatch");
                }
                // 同一UUIDの二重Receiptはどちらを正本にするか推測しない。
                if (receipts.putIfAbsent(
                                receipt.transactionId(),
                                receipt)
                        != null) {
                    quarantineEntry(
                            entry);
                }
            } catch (RuntimeException | LinkageError invalid) {
                quarantineEntry(
                        entry);
            }
        }
        if (rawReservations instanceof ListTag pending) {
            for (int index = 0;
                    index < pending.size();
                    index++) {
                CompoundTag entry =
                        pending.getCompound(
                                index);
                try {
                    if (!entry.hasUUID(
                            "transactionId")) {
                        quarantineEntry(
                                entry);
                        continue;
                    }
                    UUID transactionId =
                            entry.getUUID(
                                    "transactionId");
                    String digest =
                            checkedDigest(
                                    entry.getString(
                                            "payloadDigest"));
                    if (receipts.containsKey(transactionId)
                            || quarantinedEntries.containsKey(
                                    transactionId)
                            || reservations.putIfAbsent(
                                            transactionId,
                                            digest)
                                    != null) {
                        throw new IllegalArgumentException(
                                "duplicate receipt reservation");
                    }
                } catch (RuntimeException | LinkageError invalid) {
                    quarantineEntry(
                            entry);
                }
            }
        }
        if (rawQuarantined instanceof ListTag quarantined) {
            for (int index = 0;
                    index < quarantined.size();
                    index++) {
                CompoundTag record =
                        quarantined.getCompound(
                                index);
                Tag raw =
                        record.get(
                                "rawEntry");
                if (!(raw instanceof CompoundTag rawEntry)) {
                    identityUncertain = true;
                    unknownQuarantinedEntries.add(
                            record.copy());
                    continue;
                }
                quarantineEntry(
                        rawEntry);
            }
        }
    }

    private static Receipt decodeReceipt(
            CompoundTag entry,
            int schema) {
        if (!entry.hasUUID(
                "transactionId")) {
            throw new IllegalArgumentException(
                    "terminal receipt has no transaction id");
        }
        String state =
                entry.getString(
                        "state");
        // Schema 1 had no state field and contained only terminal receipts.
        if (state.isEmpty()
                && schema == LEGACY_SCHEMA_VERSION) {
            state = "ACKNOWLEDGED";
        }
        if (!"ACKNOWLEDGED".equals(state)) {
            throw new IllegalArgumentException(
                    "terminal receipt has an invalid state");
        }
        return new Receipt(
                entry.getUUID(
                        "transactionId"),
                entry.hasUUID(
                        "ownerTransactionId")
                        ? entry.getUUID(
                                "ownerTransactionId")
                        : null,
                state,
                entry.getString(
                        "payloadDigest"),
                decodeCounts(
                        entry.get(
                                "exactOutputs")));
    }

    private void quarantineEntry(
            CompoundTag entry) {
        CompoundTag raw =
                entry.copy();
        if (!entry.hasUUID(
                "transactionId")) {
            identityUncertain = true;
            unknownQuarantinedEntries.add(
                    raw);
            return;
        }
        UUID transactionId =
                entry.getUUID(
                        "transactionId");
        quarantinedEntries.putIfAbsent(
                transactionId,
                raw);
    }

    private static String fingerprint(
            CompoundTag entry) {
        return fingerprintWithoutFingerprint(
                entry);
    }

    private static String fingerprintWithoutFingerprint(
            CompoundTag entry) {
        CompoundTag copy =
                entry.copy();
        copy.remove(
                "entryFingerprint");
        return fingerprintRaw(
                copy);
    }

    private static String fingerprintRaw(
            CompoundTag entry) {
        try {
            byte[] digest =
                    MessageDigest.getInstance(
                                    "SHA-256")
                            .digest(
                                    entry.toString()
                                            .getBytes(
                                                    StandardCharsets.UTF_8));
            StringBuilder result =
                    new StringBuilder(
                            digest.length * 2);
            for (byte value : digest) {
                result.append(
                        String.format(
                                "%02x",
                                value));
            }
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    impossible);
        }
    }

    private void lock(
            CompoundTag owner) {
        receipts.clear();
        reservations.clear();
        quarantinedEntries.clear();
        unknownQuarantinedEntries.clear();
        corrupted =
                true;
        identityUncertain =
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
            UUID ownerTransactionId,
            String state,
            String payloadDigest,
            Map<AEKey, BigInteger> exactOutputs) {
        private Receipt {
            Objects.requireNonNull(
                    transactionId,
                    "transactionId");
            state =
                    Objects.requireNonNull(
                            state,
                            "state");
            if (!"ACKNOWLEDGED".equals(state)) {
                throw new IllegalArgumentException(
                        "invalid terminal receipt state");
            }
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
