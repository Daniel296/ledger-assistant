package com.coremaker.ledger.mcp.client;

public class LedgerRefusalException extends RuntimeException {

    public LedgerRefusalException(String ledgerAnswer) {

        super(ledgerAnswer);
    }
}
