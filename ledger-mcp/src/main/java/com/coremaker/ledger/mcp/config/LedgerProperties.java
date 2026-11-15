package com.coremaker.ledger.mcp.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ledger")
public record LedgerProperties(String url, Path accountsFile) {

}
