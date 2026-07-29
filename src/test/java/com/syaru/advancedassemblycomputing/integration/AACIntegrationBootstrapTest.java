package com.syaru.advancedassemblycomputing.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class AACIntegrationBootstrapTest {
    @Test
    void acceptsOnlyTheExactRegisteredAdapterInstance() {
        Object expected =
                AACCraftingTableBatchAdapter.INSTANCE;
        Object replacement =
                new Object();

        assertDoesNotThrow(
                () ->
                        AACIntegrationBootstrap
                                .requireRegisteredAdapterIdentity(
                                        expected,
                                        expected));
        assertThrows(
                IllegalStateException.class,
                () ->
                        AACIntegrationBootstrap
                                .requireRegisteredAdapterIdentity(
                                        expected,
                                        replacement));
    }
}
