package com.coremaker.ledger.mcp.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import com.coremaker.ledger.mcp.config.LedgerProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AccountDirectoryResource {

    private final Path accountsFile;

    public AccountDirectoryResource(LedgerProperties properties) {

        this.accountsFile = Path.of(properties.accountsFile());
        log.info("The account directory is read from {}", accountsFile.toAbsolutePath().normalize());
    }

    @McpResource(uri = "ledger://accounts", name = "accounts", mimeType = "application/json", description = """
            The account directory: the name and id of every demo account, as [{"name", "id"}]. The ledger itself has \
            no account names and no way to list accounts.""")
    public String accounts() throws IOException {

        try {
            return Files.readString(accountsFile);
        } catch (NoSuchFileException notSeededYet) {
            return "[]";
        }
    }
}
