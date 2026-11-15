package com.coremaker.ledger.mcp.integration;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class LedgerMcpApplicationShould {

    @Test
    void startItsWebServer() {

    }
}
