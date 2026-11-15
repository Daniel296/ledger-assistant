package com.coremaker.ledger.mcp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.coremaker.ledger.mcp.integration.support.FakeLedger;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.spec.McpSchema.Tool;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class LedgerToolsShould {

    private static final String ACCOUNT_ID = "1caefc9b-582c-4eb6-9c10-b1f6de292293";
    private static final String LEDGER_UNAVAILABLE = "The ledger is unavailable right now. Tell the user and stop.";

    private static final FakeLedger ledger = FakeLedger.start();

    @TempDir
    private static Path directory;

    @LocalServerPort
    private int port;

    private McpSyncClient client;

    @DynamicPropertySource
    static void pointAtTheFakeLedger(DynamicPropertyRegistry registry) {

        registry.add("ledger.url", ledger::url);
        registry.add("ledger.accounts-file", () -> accountsFile().toString());
        registry.add("spring.http.clients.read-timeout", () -> "500ms");
    }

    @BeforeEach
    void connect() throws IOException {

        ledger.forget();
        Files.deleteIfExists(accountsFile());
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:%d".formatted(port)).build();
        client = McpClient.sync(transport).build();
        client.initialize();
    }

    @AfterEach
    void disconnect() {

        client.closeGracefully();
    }

    @Test
    void offerOnlyTheFourReadTools() {

        var tools = client.listTools().tools();

        assertThat(tools).extracting(Tool::name)
                .containsExactlyInAnyOrder("get_account", "list_entries", "get_balance_as_of", "get_transaction");
        assertThat(tools).allSatisfy(tool -> assertThat(tool.annotations().readOnlyHint()).isTrue());
    }

    @Test
    void describeEveryToolInTheWordsOfTheContract() throws IOException {

        var contract = contractDescriptions();

        assertThat(client.listTools().tools()).allSatisfy(tool ->
                assertThat(tool.description()).isEqualTo(contract.get(tool.name())));
        assertThat(contract).hasSize(4);
    }

    @ParameterizedTest
    @MethodSource("ledgerReads")
    void returnTheLedgersAnswerUnchanged(String tool, Map<String, Object> input, String request) {

        var answer = """
                {"id":"%s","currency":"EUR","overdraftLimit":"0.00","balance":"125.50"}""".formatted(ACCOUNT_ID);
        ledger.answer(request, 200, answer);

        var result = client.callTool(new CallToolRequest(tool, input));

        assertThat(result.isError()).isNotEqualTo(true);
        assertThat(text(result)).isEqualTo(answer);
        assertThat(ledger.requests()).containsExactly("GET %s".formatted(request));
    }

    static Stream<Arguments> ledgerReads() {

        var transactionId = "a367f8be-b59f-4cc3-a42b-b94ce3d437be";

        return Stream.of(
                arguments("get_account", Map.of("accountId", ACCOUNT_ID), "/v1/accounts/%s".formatted(ACCOUNT_ID)),
                arguments("list_entries", Map.of("accountId", ACCOUNT_ID),
                        "/v1/accounts/%s/entries".formatted(ACCOUNT_ID)),
                arguments("list_entries", Map.of("accountId", ACCOUNT_ID, "cursor", "41"),
                        "/v1/accounts/%s/entries?cursor=41".formatted(ACCOUNT_ID)),
                arguments("list_entries", Map.of("accountId", ACCOUNT_ID, "cursor", "{41}"),
                        "/v1/accounts/%s/entries?cursor=%%7B41%%7D".formatted(ACCOUNT_ID)),
                arguments("get_balance_as_of", Map.of("accountId", ACCOUNT_ID, "asOf", "2026-11-15T12:00:00+02:00"),
                        "/v1/accounts/%s/balance?asOf=2026-11-15T10:00:00Z".formatted(ACCOUNT_ID)),
                arguments("get_transaction", Map.of("transactionId", transactionId),
                        "/v1/transactions/%s".formatted(transactionId)));
    }

    @Test
    void passTheLedgersRefusalOnAsAToolError() {

        var refusal = """
                {"errorCode":"ACCOUNT_NOT_FOUND","message":"Account %s does not exist"}""".formatted(ACCOUNT_ID);
        ledger.answer("/v1/accounts/%s".formatted(ACCOUNT_ID), 404, refusal);

        var result = client.callTool(new CallToolRequest("get_account", Map.of("accountId", ACCOUNT_ID)));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo(refusal);
    }

    @ParameterizedTest
    @MethodSource("inputsTheLedgerCannotRead")
    void refuseAnInputTheLedgerCannotReadWithoutCallingIt(String tool, Map<String, Object> input, String refusal) {

        var result = client.callTool(new CallToolRequest(tool, input));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo(refusal);
        assertThat(ledger.requests()).isEmpty();
    }

    static Stream<Arguments> inputsTheLedgerCannotRead() {

        return Stream.of(
                arguments("get_account", Map.of("accountId", "Merchant 42 (EUR)"),
                        "accountId must be an account id (a UUID) from the account directory, not \"Merchant 42 (EUR)\""),
                arguments("get_transaction", Map.of("transactionId", "pay-7"),
                        "transactionId must be a transaction id (a UUID), not \"pay-7\""),
                arguments("get_balance_as_of", Map.of("accountId", ACCOUNT_ID, "asOf", "yesterday at noon"), """
                        asOf must be an ISO-8601 instant with an offset, like 2026-11-15T12:00:00Z, \
                        not "yesterday at noon\""""));
    }

    @Test
    void refuseAMissingInputBeforeTheToolRuns() {

        var result = client.callTool(new CallToolRequest("get_account", Map.of()));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("required property 'accountId' not found");
        assertThat(ledger.requests()).isEmpty();
    }

    @Test
    void tellTheModelToStopWhenTheLedgerFails() {

        ledger.answer("/v1/accounts/%s".formatted(ACCOUNT_ID), 500, "{}");

        var result = client.callTool(new CallToolRequest("get_account", Map.of("accountId", ACCOUNT_ID)));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo(LEDGER_UNAVAILABLE);
    }

    @Test
    void tellTheModelToStopWhenTheLedgerAnswersTooLate() {

        ledger.answerAfter(Duration.ofSeconds(1), "/v1/accounts/%s".formatted(ACCOUNT_ID));

        var result = client.callTool(new CallToolRequest("get_account", Map.of("accountId", ACCOUNT_ID)));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo(LEDGER_UNAVAILABLE);
    }

    @Test
    void readTheAccountDirectoryFileAtEveryRead() throws IOException {

        var empty = accountDirectory();
        Files.writeString(accountsFile(), """
                [{"name":"Merchant 42 (EUR)","id":"%s"}]""".formatted(ACCOUNT_ID));
        var seeded = accountDirectory();

        assertThat(empty.text()).isEqualTo("[]");
        assertThat(seeded.text()).isEqualTo(Files.readString(accountsFile()));
        assertThat(seeded.mimeType()).isEqualTo("application/json");
    }

    private TextResourceContents accountDirectory() {

        var contents = client.readResource(new ReadResourceRequest("ledger://accounts"))
                .contents()
                .getFirst();

        return (TextResourceContents) contents;
    }

    private static Path accountsFile() {

        return directory.resolve("accounts.json");
    }

    private static String text(CallToolResult result) {

        var content = result.content().getFirst();

        return ((TextContent) content).text();
    }

    private static Map<String, String> contractDescriptions() throws IOException {

        var descriptions = new HashMap<String, String>();
        String tool = null;
        for (var line : Files.readAllLines(Path.of("../docs/tools.md"))) {

            if (line.startsWith("## ")) {
                tool = line.startsWith("## `") ? line.substring(4, line.length() - 1) : null;
            } else if (tool != null && line.startsWith("> ")) {
                descriptions.merge(tool, line.substring(2), "%s %s"::formatted);
            }
        }

        return descriptions;
    }
}
