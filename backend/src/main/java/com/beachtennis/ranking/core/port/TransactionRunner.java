package com.beachtennis.ranking.core.port;

import java.util.function.Supplier;

/**
 * Runs a unit of work atomically, keeping the core independent of the transaction framework.
 */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);
}
