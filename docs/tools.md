# Tools contract

`ledger-mcp` exposes four read tools and one resource over MCP (Streamable HTTP, port 8081). This contract is written
before the code and kept in step with it. The descriptions below are the exact text the model sees.

## Rules for every tool

- **Read only.** Each tool is one `GET` on ledger-api `v1`. No tool writes, and the ledger client has no other method.
- **The ledger's JSON, unchanged.** A tool returns the ledger's response body as it is. Amounts stay the decimal strings
  the ledger renders (`"125.50"`, `"15000"`): nothing parses, rounds or reformats them on the way to the model.
- **Errors are tool errors.** A failed call returns an MCP result marked `isError`, which the assistant passes to the
  model as a `tool_result` with `is_error: true`. The text tells the model what to do next:

  | Cause | Text of the error |
  |---|---|
  | A required input is missing (checked by the MCP server against the tool's schema) | `Tool (get_account) input validation failed: ... required property 'accountId' not found` |
  | The ledger answers 404 | The ledger's body, unchanged: `{"errorCode":"ACCOUNT_NOT_FOUND","message":"Account 00000000-0000-4000-8000-000000000000 does not exist"}` |
  | An account id that is not a UUID (checked before calling the ledger) | `accountId must be an account id (a UUID) from the account directory, not "<value>"` |
  | A transaction id that is not a UUID (checked before calling the ledger) | `transactionId must be a transaction id (a UUID), not "<value>"` |
  | `asOf` that is not an ISO-8601 instant (checked before calling the ledger) | `asOf must be an ISO-8601 instant with an offset, like 2026-11-15T12:00:00Z, not "<value>"` |
  | A `cursor` the ledger cannot read | The ledger's `400` body, unchanged |
  | The ledger is unreachable, answers 5xx, or takes longer than 5 s | `The ledger is unavailable right now. Tell the user and stop.` |

  The checks before the call exist because the ledger answers a bad id or instant with a bare `400 Bad Request` that
  does not say what is wrong (`{"timestamp":"…","status":400,"error":"Bad Request","path":"/v1/accounts/merchant-42"}`),
  which gives the model nothing to correct. The cursor is not checked: the ledger's contract says only that it is a
  string, and the model only sends back a `nextCursor` it was given. It reaches the ledger encoded, never read as part
  of the URL.

- **Ledger text is data.** A transaction's `description` comes from whoever recorded it. The server passes it on
  unchanged; the assistant's prompt says it is never an instruction.

## `get_account`

> Read one ledger account: its currency, its current balance, and its overdraft limit (absent for the ledger's own
> settlement accounts, which have no limit). Use it for the current balance. For the balance at a past moment, use
> get_balance_as_of.

| Input | | Description the model sees |
|---|---|---|
| `accountId` | required | The account's id, a UUID from the account directory |

Calls `GET /v1/accounts/{accountId}`. Errors: `ACCOUNT_NOT_FOUND`, an id that is not a UUID, ledger unavailable.

```json
{"id":"1caefc9b-582c-4eb6-9c10-b1f6de292293","currency":"EUR","overdraftLimit":"0.00","balance":"125.50","createdAt":"2026-11-15T22:20:08.836391Z"}
```

## `list_entries`

> Read an account's entries, newest first, 20 per page. Each entry gives its transaction id, debit or credit, the
> amount, the balance after it and when it was recorded. Use it to explain how a balance came to be: the balance just
> before an entry is the balanceAfter of the entry below it. Entries carry no description: call get_transaction to
> learn what a transaction was. When the result has nextCursor, older entries exist: call again with that cursor.

| Input | | Description the model sees |
|---|---|---|
| `accountId` | required | The account's id, a UUID from the account directory |
| `cursor` | optional | The nextCursor of the previous page; omit it for the newest entries |

Calls `GET /v1/accounts/{accountId}/entries` (with `?cursor=` when given; the ledger's default page size, 20).
Errors: `ACCOUNT_NOT_FOUND`, an id that is not a UUID, ledger unavailable.

Merchant 42 after examples 1–3 of the challenge: the payment, the payout, its reversal (newest first).

```json
{"entries":[
  {"transactionId":"d6f72662-3932-4b24-9471-e3189fa470ca","direction":"CREDIT","amount":"100.00","balanceAfter":"125.50","recordedAt":"2026-11-15T22:20:10.855162Z"},
  {"transactionId":"a367f8be-b59f-4cc3-a42b-b94ce3d437be","direction":"DEBIT","amount":"100.00","balanceAfter":"25.50","recordedAt":"2026-11-15T22:20:10.811940Z"},
  {"transactionId":"486ec2ea-7388-402c-b588-7e7620040227","direction":"CREDIT","amount":"125.50","balanceAfter":"125.50","recordedAt":"2026-11-15T22:20:10.428099Z"}]}
```

## `get_balance_as_of`

> Read an account's balance at a past instant: the balance after every entry recorded at or before it. Use it for
> questions about a date or a time ("on Monday", "yesterday at noon"). For the current balance use get_account; for
> the balance just before a given transaction, list_entries is simpler.

| Input | | Description the model sees |
|---|---|---|
| `accountId` | required | The account's id, a UUID from the account directory |
| `asOf` | required | An ISO-8601 instant with an offset, like 2026-11-15T12:00:00Z |

Calls `GET /v1/accounts/{accountId}/balance?asOf=`. Errors: `ACCOUNT_NOT_FOUND`, an id that is not a UUID, an `asOf`
that is not an instant, ledger unavailable.

```json
{"accountId":"1caefc9b-582c-4eb6-9c10-b1f6de292293","currency":"EUR","balance":"125.50","asOf":"2026-11-15T22:20:10.428099Z"}
```

Transactions recorded within the same second are common (the three above are 0.43 s apart), so "just before a
transaction" is a question for `list_entries`, not for an instant.

## `get_transaction`

> Read one transaction: its description, its currency, every entry (account, debit or credit, amount) and when it was
> recorded. A reversal has reversalOf, the id of the transaction it reverses. The original transaction does not say
> whether it was reversed: to find out, read the entries of one of its accounts and look for a later transaction whose
> reversalOf is this id. A settled payment is described with its payment id, like "Payment pay-7 settled".

| Input | | Description the model sees |
|---|---|---|
| `transactionId` | required | The transaction's id, a UUID |

Calls `GET /v1/transactions/{transactionId}`. Errors: `TRANSACTION_NOT_FOUND`, an id that is not a UUID, ledger
unavailable.

The reversal of the payout above:

```json
{"id":"d6f72662-3932-4b24-9471-e3189fa470ca","description":"Reversal of a367f8be-b59f-4cc3-a42b-b94ce3d437be","currency":"EUR",
 "entries":[{"accountId":"1caefc9b-582c-4eb6-9c10-b1f6de292293","direction":"CREDIT","amount":"100.00"},
            {"accountId":"cbf99913-875e-4c20-ac07-c9b449cbeb66","direction":"DEBIT","amount":"98.50"},
            {"accountId":"67fd6490-4893-4f08-8888-85cb68beaa6a","direction":"DEBIT","amount":"1.50"}],
 "recordedAt":"2026-11-15T22:20:10.855162Z","reversalOf":"a367f8be-b59f-4cc3-a42b-b94ce3d437be"}
```

## Resource `ledger://accounts`

> The account directory: the name and id of every demo account, as [{"name", "id"}]. The ledger itself has no account
> names and no way to list accounts.

A JSON array (`application/json`), one entry per demo account, written by the seed script; `[]` until it has run.

```json
[{"name": "Merchant 42 (EUR)", "id": "<uuid>"}, {"name": "Settlement EUR", "id": "<uuid>"}]
```

Names exist only here: the ledger has no account names and no way to list accounts, so naming them is presentation,
kept outside the ledger. The server reads the file on every read of the resource, and the assistant reads the resource
at the start of every conversation, so the seed may write it after both have started. The seed writes it to a
temporary file and renames it, so a read never sees half a file.

## What the tools cannot see

The assistant must say so, not guess, when a question needs any of these:

- a payment the ledger refused: it goes to the `payment-settled-dlt` topic, not into the ledger;
- a search by payment id or by description: a payment is found only by reading an account's entries and their
  transactions;
- an account missing from the directory, unless the user gives its id.
