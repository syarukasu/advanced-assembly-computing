package com.syaru.advancedassemblycomputing.execution;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** AAC-local numeric and identity helpers; no ACO implementation package is used. */
public final class AACNativePatternBatchSupport {
    private AACNativePatternBatchSupport() {
    }

    public static KeyCounter[] scaleInputs(PatternBatchContext context, long executions) {
        KeyCounter[] source = context.copyInputsPerExecution();
        KeyCounter[] scaled = new KeyCounter[source.length];
        for (int index = 0; index < source.length; index++) {
            KeyCounter counter = scaled[index] = new KeyCounter();
            for (var entry : source[index]) {
                counter.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), executions));
            }
        }
        return scaled;
    }

    public static List<GenericStack> flatten(KeyCounter[] counters) {
        List<GenericStack> result = new ArrayList<>();
        for (KeyCounter counter : counters) {
            for (var entry : counter) {
                result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        return List.copyOf(result);
    }

    public static List<GenericStack> scaleAllExpectedOutputs(
            PatternBatchContext context,
            long executions) {
        List<GenericStack> result = new ArrayList<>();
        for (var entry : context.copyOutputsPerExecution()) {
            result.add(new GenericStack(
                    entry.getKey(),
                    Math.multiplyExact(entry.getLongValue(), executions)));
        }
        for (var entry : context.copyRemainingOutputsPerExecution()) {
            result.add(new GenericStack(
                    entry.getKey(),
                    Math.multiplyExact(entry.getLongValue(), executions)));
        }
        return List.copyOf(result);
    }

    /** Stable identity for the exact pattern inputs, outputs, and provider ownership. */
    public static String fingerprint(PatternBatchContext context) {
        StringBuilder value = new StringBuilder(256);
        value.append(context.pattern().getDefinition().getId());
        for (KeyCounter counter : context.copyInputsPerExecution()) {
            value.append("|i");
            for (var entry : counter) {
                value.append(':')
                        .append(entry.getKey().toTagGeneric())
                        .append('@')
                        .append(entry.getLongValue());
            }
        }
        for (var entry : context.copyOutputsPerExecution()) {
            value.append("|o:")
                    .append(entry.getKey().toTagGeneric())
                    .append('@')
                    .append(entry.getLongValue());
        }
        for (var entry : context.copyRemainingOutputsPerExecution()) {
            value.append("|r:")
                    .append(entry.getKey().toTagGeneric())
                    .append('@')
                    .append(entry.getLongValue());
        }
        value.append("|owner=").append(context.providerOwnedTarget());
        return sha256(value.toString()) + ':' + context.pattern().getDefinition().getId();
    }

    /** Mirrors ACO's public payload digest without depending on its internals. */
    public static String payloadDigest(
            long executions,
            Iterable<GenericStack> inputs,
            Iterable<GenericStack> outputs) {
        StringBuilder value = new StringBuilder(256);
        value.append("executions=").append(executions);
        append(value, "inputs", inputs);
        append(value, "outputs", outputs);
        return sha256(value.toString());
    }

    private static void append(
            StringBuilder target,
            String name,
            Iterable<GenericStack> stacks) {
        target.append('|').append(name);
        for (GenericStack stack : stacks) {
            target.append('|')
                    .append(stack.what().toTagGeneric())
                    .append('@')
                    .append(stack.amount());
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
