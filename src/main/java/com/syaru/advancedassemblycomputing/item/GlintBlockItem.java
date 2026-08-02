package com.syaru.advancedassemblycomputing.item;

import com.syaru.advancedassemblycomputing.config.AACConfig;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public final class GlintBlockItem extends BlockItem {
    public enum Role {
        CONTROLLER,
        PARALLEL_CORE,
        WORKER
    }

    private final Role role;

    public GlintBlockItem(Block block, Properties properties, Role role) {
        super(block, properties);
        this.role = role;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        // ControllerとWorkerは実際のCommon Config値を表示し、固定値との表示ずれを避ける。
        if (role == Role.CONTROLLER || role == Role.WORKER) {
            String amount = String.format(
                    Locale.ROOT,
                    "%.3E",
                    (double) AACConfig.maximumCraftingTableBatchExecutions());
            tooltip.add(Component.translatable(
                            "tooltip.advanced_assembly_computing.logical_executions_per_wave",
                            amount)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable(
                            "tooltip.advanced_assembly_computing.physical_threads_per_worker",
                            AACConfig.physicalThreadsPerWorker())
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable(
                        "tooltip.advanced_assembly_computing." + role.name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.GRAY));
    }
}
