package com.coremaker.ledger.mcp.client;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.coremaker.ledger.mcp.config.LedgerProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RestLedgerClient implements LedgerClient {

    private final RestClient restClient;

    public RestLedgerClient(RestClient.Builder restClientBuilder, LedgerProperties properties) {

        this.restClient = restClientBuilder.baseUrl(properties.url()).build();
    }

    @Override
    public String getAccount(UUID accountId) {

        return read(restClient.get().uri("/v1/accounts/{accountId}", accountId));
    }

    @Override
    public String listEntries(UUID accountId, String cursor) {

        return read(restClient.get().uri(uri -> uri.path("/v1/accounts/{accountId}/entries")
                .queryParamIfPresent("cursor", Optional.ofNullable(cursor).map(present -> "{cursor}"))
                .build(accountId, cursor)));
    }

    @Override
    public String getBalanceAsOf(UUID accountId, Instant asOf) {

        return read(restClient.get().uri(uri -> uri.path("/v1/accounts/{accountId}/balance")
                .queryParam("asOf", asOf)
                .build(accountId)));
    }

    @Override
    public String getTransaction(UUID transactionId) {

        return read(restClient.get().uri("/v1/transactions/{transactionId}", transactionId));
    }

    private static String read(RestClient.RequestHeadersSpec<?> request) {

        try {
            return request.retrieve().body(String.class);
        } catch (HttpClientErrorException refusal) {
            throw new LedgerRefusalException(refusal.getResponseBodyAsString());
        } catch (RestClientException failure) {
            log.warn("The ledger is unavailable: {}", failure.getMessage());
            throw new LedgerUnavailableException(failure);
        }
    }
}
