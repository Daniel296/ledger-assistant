package com.coremaker.ledger.mcp.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import com.coremaker.ledger.mcp.config.LedgerProperties;

@Component
public class AccountDirectoryResource {

    private final Path accountsFile;

    public AccountDirectoryResource(LedgerProperties properties) {

        this.accountsFile = properties.accountsFile();
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
