package com.syaru.advancedassemblycomputing.execution;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchMode;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.Tag;

/** 一つのNeoECO Threadが所有する、一パターン一仕事のAAC実行境界。 */
public interface AACCraftingTableBatchThread {
    boolean aac$acceptCraftingTableBatch(
            CraftingTableBatchRequest request,
            ECOCraftingSystemBlockEntity controller);

    boolean aac$ownsCraftingTableBatch(
            UUID transactionId,
            String payloadDigest);

    boolean aac$isManagedCraftingTableBatch();

    /** 隔離中のThreadはNeoECO上で空きに見えても再利用しない。 */
    boolean aac$isQuarantined();

    Optional<UUID> aac$ownerTransactionId();

    Optional<UUID> aac$quarantineTransactionId();

    /** 既知Transactionに対して、出力なしの隔離Snapshotだけを返す。 */
    Optional<CraftingTableBatchSnapshot>
            aac$quarantinedCraftingTableBatchSnapshot(UUID transactionId);

    /** Worker位置とThread番号へ付加する短い管理者向け診断。 */
    String aac$quarantineDiagnostic();

    /** 管理者が書き出せるraw sidecarの防御コピー。 */
    Optional<Tag> aac$quarantinedRawSidecar();

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
