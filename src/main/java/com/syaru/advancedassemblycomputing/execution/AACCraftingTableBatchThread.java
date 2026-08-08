package com.syaru.advancedassemblycomputing.execution;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchMode;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import java.util.Optional;
import java.util.UUID;

/** 一つのNeoECO Threadが所有する、一パターン一仕事のAAC実行境界。 */
public interface AACCraftingTableBatchThread {
    boolean aac$acceptCraftingTableBatch(
            CraftingTableBatchRequest request,
            ECOCraftingSystemBlockEntity controller);

    boolean aac$ownsCraftingTableBatch(
            UUID transactionId,
            String payloadDigest);

    boolean aac$isManagedCraftingTableBatch();

    /** 隔離中のThreadは空きThreadに見えても新しい仕事を受け付けない。 */
    boolean aac$isQuarantined();

    /** 既知のTransactionだけを、出力なしのQUARANTINED Snapshotとして公開する。 */
    Optional<CraftingTableBatchSnapshot>
            aac$quarantinedCraftingTableBatchSnapshot(UUID transactionId);

    /** Workerの位置・Thread番号と一緒に診断ログへ出す短い隔離情報。 */
    String aac$quarantineDiagnostic();

    CraftingTableBatchMode aac$craftingTableBatchMode();

    Optional<CraftingTableBatchSnapshot>
            aac$craftingTableBatchSnapshot(
                    UUID transactionId,
                    String payloadDigest);

    boolean aac$acknowledgeCraftingTableBatch(
            UUID transactionId,
            String payloadDigest);

    /**
     * BigInteger親仕事だけを、代表一回分のスタックをMEへ返さず取り消す。
     */
    boolean aac$cancelCraftingTableBatch(
            UUID transactionId,
            String payloadDigest);
}
