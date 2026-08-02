package com.syaru.advancedassemblycomputing.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** NeoECO Configの読込順にかかわらず、構造反復上限を正数へ保つ試験。 */
class AACMultiBlocksTest {
    @Test
    void unresolvedServerConfigUsesNeoEcoDefaultLength() {
        assertEquals(11, AACMultiBlocks.resolveExpandMax(0));
    }

    @Test
    void minimumValidStructureKeepsOneRepeat() {
        assertEquals(1, AACMultiBlocks.resolveExpandMax(5));
    }

    @Test
    void loadedServerConfigControlsMaximumLength() {
        assertEquals(11, AACMultiBlocks.resolveExpandMax(15));
        assertEquals(28, AACMultiBlocks.resolveExpandMax(32));
    }
}
