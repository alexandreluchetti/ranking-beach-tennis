package com.lucchetta.core.support;

import com.lucchetta.core.port.TransactionRunner;

import java.util.function.Supplier;

/** Runs the work inline; for unit tests of use cases. */
public class DirectTransactionRunner implements TransactionRunner {

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }
}
