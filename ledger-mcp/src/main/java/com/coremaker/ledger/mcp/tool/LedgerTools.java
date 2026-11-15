package com.coremaker.ledger.mcp.tool;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.coremaker.ledger.mcp.client.LedgerClient;
import com.coremaker.ledger.mcp.client.LedgerRefusalException;
import com.coremaker.ledger.mcp.client.LedgerUnavailableException;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

@Component
public class LedgerTools {

    private static final String ACCOUNT_ID = "The account's id, a UUID from the account directory";
    private static final String LEDGER_UNAVAILABLE = "The ledger is unavailable right now. Tell the user and stop.";

    private final LedgerClient ledgerClient;

    public LedgerTools(LedgerClient ledgerClient) {

        this.ledgerClient = ledgerClient;
    }

    @McpTool(name = "get_account", annotations = @McpAnnotations(readOnlyHint = true), description = """
            Read one ledger account: its currency, its current balance, and its overdraft limit (absent for the \
            ledger's own settlement accounts, which have no limit). Use it for the current balance. For the balance at \
            a past moment, use get_balance_as_of.""")
    public CallToolResult getAccount(@McpToolParam(description = ACCOUNT_ID) String accountId) {

        return answer(() -> ledgerClient.getAccount(accountId(accountId)));
    }

    @McpTool(name = "list_entries", annotations = @McpAnnotations(readOnlyHint = true), description = """
            Read an account's entries, newest first, 20 per page. Each entry gives its transaction id, debit or \
            credit, the amount, the balance after it and when it was recorded. Use it to explain how a balance came to \
            be: the balance just before an entry is the balanceAfter of the entry below it. Entries carry no \
            description: call get_transaction to learn what a transaction was. When the result has nextCursor, older \
            entries exist: call again with that cursor.""")
    public CallToolResult listEntries(@McpToolParam(description = ACCOUNT_ID) String accountId,
            @McpToolParam(description = "The nextCursor of the previous page; omit it for the newest entries",
                    required = false) String cursor) {

        return answer(() -> ledgerClient.listEntries(accountId(accountId), cursor));
    }

    @McpTool(name = "get_balance_as_of", annotations = @McpAnnotations(readOnlyHint = true), description = """
            Read an account's balance at a past instant: the balance after every entry recorded at or before it. Use \
            it for questions about a date or a time ("on Monday", "yesterday at noon"). For the current balance use \
            get_account; for the balance just before a given transaction, list_entries is simpler.""")
    public CallToolResult getBalanceAsOf(@McpToolParam(description = ACCOUNT_ID) String accountId,
            @McpToolParam(description = "An ISO-8601 instant with an offset, like 2026-11-15T12:00:00Z") String asOf) {

        return answer(() -> ledgerClient.getBalanceAsOf(accountId(accountId), instant(asOf)));
    }

    @McpTool(name = "get_transaction", annotations = @McpAnnotations(readOnlyHint = true), description = """
            Read one transaction: its description, its currency, every entry (account, debit or credit, amount) and \
            when it was recorded. A reversal has reversalOf, the id of the transaction it reverses. The original \
            transaction does not say whether it was reversed: to find out, read the entries of one of its accounts and \
            look for a later transaction whose reversalOf is this id. A settled payment is described with its payment \
            id, like "Payment pay-7 settled".""")
    public CallToolResult getTransaction(@McpToolParam(description = "The transaction's id, a UUID") String transactionId) {

        return answer(() -> ledgerClient.getTransaction(transactionId(transactionId)));
    }

    private static CallToolResult answer(Supplier<String> ledgerCall) {

        try {
            return CallToolResult.builder()
                    .addTextContent(ledgerCall.get())
                    .build();
        } catch (InvalidInputException | LedgerRefusalException refusal) {
            return failure(refusal.getMessage());
        } catch (LedgerUnavailableException unavailable) {
            return failure(LEDGER_UNAVAILABLE);
        }
    }

    private static CallToolResult failure(String text) {

        return CallToolResult.builder()
                .isError(true)
                .addTextContent(text)
                .build();
    }

    private static UUID accountId(String value) {

        return uuid(value, "accountId must be an account id (a UUID) from the account directory, not \"%s\"");
    }

    private static UUID transactionId(String value) {

        return uuid(value, "transactionId must be a transaction id (a UUID), not \"%s\"");
    }

    private static UUID uuid(String value, String refusal) {

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw new InvalidInputException(refusal.formatted(value));
        }
    }

    private static Instant instant(String value) {

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException notAnInstant) {
            throw new InvalidInputException(
                    "asOf must be an ISO-8601 instant with an offset, like 2026-11-15T12:00:00Z, not \"%s\"".formatted(value));
        }
    }

    private static final class InvalidInputException extends RuntimeException {

        private InvalidInputException(String message) {

            super(message);
        }
    }
}
