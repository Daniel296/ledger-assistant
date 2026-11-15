package com.coremaker.ledger.mcp.unit.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.coremaker.ledger.mcp.client.LedgerUnavailableException;
import com.coremaker.ledger.mcp.client.RestLedgerClient;
import com.coremaker.ledger.mcp.config.LedgerProperties;

class RestLedgerClientShould {

    @Test
    void reportTheLedgerUnavailableWhenNothingAnswers() throws IOException {

        int closedPort;
        try (var socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        var client = new RestLedgerClient(RestClient.builder(),
                new LedgerProperties("http://localhost:%d".formatted(closedPort), null));

        assertThatThrownBy(() -> client.getAccount(UUID.randomUUID())).isInstanceOf(LedgerUnavailableException.class);
    }
}
