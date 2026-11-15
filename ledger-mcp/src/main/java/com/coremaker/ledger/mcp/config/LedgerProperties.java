package com.coremaker.ledger.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ledger")
public record LedgerProperties(String url, String accountsFile) {

}
