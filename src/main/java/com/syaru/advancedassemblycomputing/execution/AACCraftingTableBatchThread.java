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
