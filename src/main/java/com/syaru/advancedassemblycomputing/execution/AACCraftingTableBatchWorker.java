package com.syaru.advancedassemblycomputing.execution;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import java.util.Set;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import java.util.Optional;
import java.util.UUID;

/** AAC Pattern BusがNeoECO実Workerへ一括作業台仕事を渡す内部境界。 */
public interface AACCraftingTableBatchWorker {
    boolean aac$acceptCraftingTableBatch(
            CraftingTableBatchRequest request,
            ECOCraftingSystemBlockEntity controller);

    boolean aac$ownsCraftingTableBatch(
            UUID transactionId,
            String payloadDigest);

    Optional<CraftingTableBatchSnapshot>
            aac$craftingTableBatchSnapshot(
                    UUID transactionId,
                    String payloadDigest);

    boolean aac$acknowledgeCraftingTableBatch(
            UUID transactionId,
            String payloadDigest);

    boolean aac$forgetCraftingTableBatch(
            UUID transactionId,
            String payloadDigest);

    boolean aac$cancelCraftingTableBatch(
            UUID transactionId,
            String payloadDigest);

    /** Wake Neo ECO only after an ownership, receipt, or cancellation change. */
    void aac$wakeForBatchChange();

    /** Mark the ready set used by the custom output flush path. */
    void aac$markOutputReady(ECOCraftingThread thread);

    void aac$unmarkOutputReady(ECOCraftingThread thread);

    /** Active and terminal transaction keys known by this Worker index. */
    Set<UUID> aac$knownTransactionIds();
}
