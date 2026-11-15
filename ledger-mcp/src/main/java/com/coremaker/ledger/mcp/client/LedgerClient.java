package com.coremaker.ledger.mcp.client;

import java.time.Instant;
import java.util.UUID;

public interface LedgerClient {

    String getAccount(UUID accountId);

    String listEntries(UUID accountId, String cursor);

    String getBalanceAsOf(UUID accountId, Instant asOf);

    String getTransaction(UUID transactionId);
}
