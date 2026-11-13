# Generic Webhook Client

POSTs a well-structured message to a single configured webhook endpoint — a custom
receiver, an automation platform (Zapier, n8n), or a product that accepts simple JSON
posts, such as a [Slack incoming webhook](https://docs.slack.dev/messaging/sending-messages-using-incoming-webhooks).

A webhook message is fire-and-forget notification, not incident management: there is no
incident id, permalink, or lifecycle, and `supportsResolve()` is `false`. Transport,
configuration, and error handling are shared across all formats; the only per-product
variance is the `WebhookFormat` that renders an `IncidentAlert` into the JSON body and
may read the wait a failed response's body asks for.

## Formats

- `WebhookFormats.GENERIC_JSON` (provider id `webhook`) — a canonical JSON document of
  the alert itself, for receivers that do their own mapping:

  ```json
  {"summary":"...","severity":"CRITICAL","key":"...","source":"...",
   "timestamp":"2026-07-26T12:30:45Z","details":"...","customDetails":{"slot":350000000}}
  ```

  `summary` and `severity` are always present; blank optional fields are omitted and the
  timestamp is RFC 3339.

- `WebhookFormats.SLACK_TEXT` (provider id `slack`) — a Slack incoming-webhook message,
  `{"text":"..."}`, rendering the alert as plain multi-line text with `&`/`<`/`>`
  entity-escaped per the Slack text contract. Deliberately plain — no blocks, no
  attachments; a workspace that needs rich formatting deserves a real Slack client. Slack
  workflow webhooks that accept arbitrary JSON can also consume `GENERIC_JSON`.

- `TelegramTextFormat` (provider id `telegram`) — a Telegram Bot API `sendMessage` body,
  `{"chat_id":"...","text":"..."}`, sharing the Slack format's plain-text rendering
  (minus the entity escaping — no `parse_mode` is sent, so no Telegram markup escaping
  applies). The endpoint is the bot's full `sendMessage` URL,
  `https://api.telegram.org/bot<TOKEN>/sendMessage` — the URL carries the credential,
  exactly like a Slack webhook — and `chatId` (a numeric chat id or `@channelusername`)
  is required configuration. An optional `messageThreadId` sends the message to a topic of
  a forum supergroup as `message_thread_id`,
  `{"chat_id":"...","message_thread_id":42,"text":"..."}`; absent or zero, no topic is
  sent and the message goes to the chat as a whole. Text over 4096 characters is truncated client-side, never
  between the two halves of a surrogate pair, since `sendMessage` rejects longer messages
  outright. A failure's wait is read from the Bot API error body's
  `parameters.retry_after` (`TelegramTextFormat.botApiRetryAfter`, public for a custom
  format that posts to the Bot API).

Supporting another product (Discord, Google Chat, ...) is one `WebhookFormat`
implementation plus a factory registration — see
[Writing Your Own Provider](#writing-your-own-provider); no changes to this library are
required.

## [Example Usage](../incident-examples/src/main/java/software/sava/incident/examples/WebhookExamples.java)

```java
try (final var httpClient = HttpClient.newHttpClient()) {
  final var client = WebhookClient.clientBuilder()
      .endpoint("https://hooks.slack.com/services/T000/B000/XXXX")
      .httpClient(httpClient)
      .createClient();

  final var incidentClient = client.incidentClient(WebhookFormats.SLACK_TEXT);

  final var response = incidentClient.reportIncident(IncidentAlert.build()
      .summary("Validator missed its leader slot")
      .details("No block produced for slot 350000000.")
      .severity(IncidentSeverity.CRITICAL)
      .source("validator-01.example.com")
      .customDetail("slot", 350000000L)
      .create()).join();

  System.out.println(response.status()); // "ok"
}
```

Receivers that authenticate by header rather than by URL can set static headers:

```java
final var client = WebhookClient.clientBuilder()
    .endpoint("https://hooks.example.com/notify")
    .header("X-Api-Key", "KEY")   // or .bearerToken("TOKEN")
    .createClient();
```

Direct `reportIncident` calls return the original alert key in `IncidentResponse.key()`.
When reporting through `IncidentService`, a null or blank key is replaced once per
logical report with a generated key, stable across retries. That key appears in the
`GENERIC_JSON` `key` field, the Slack/Telegram `Key:` line, and the response key. The
Telegram key line counts toward the existing 4096-character text limit and is subject
to truncation with the rest of the message.

`status()` is the receiver's stripped response body (`ok` for Slack) or `delivered`
when the body is empty, and `url()` is null. Non-2xx responses fail with a
`WebhookRequestException` whose message carries the status and response body but never
the endpoint URL — webhook URLs are credentials. Its `retryAfter()`, the wait
`IncidentService` serves before the next attempt, is the `Retry-After` header when it
parses (delay-seconds, or an HTTP-date measured from the response's `Date`); otherwise
the wait the request's format read from the response body
(`WebhookFormat.retryAfter(String)`, null by default; a format that throws a
`RuntimeException` there states no wait). `TelegramTextFormat` reads a Bot API error,
`{"ok":false,...,"parameters":{"retry_after":35}}`, as its `retry_after` in seconds; the
Bot API documents the wait in that body, and its server also sends the header. The
incident adapter posts with its format; a direct `client.post(json)` reads no wait from the body, and
`client.post(json, format)` hands the body to `format`.

## Writing Your Own Provider

`WebhookFormat` is the whole seam: one abstract method rendering an `IncidentAlert` into
the JSON body, and a `retryAfter(String)` hook, null by default, reading the wait from a
failed response's body. Transport, headers, and error handling never change per platform.
In code, a format that only renders is a lambda — `WebhookFormats.renderPlainText(alert)`
supplies the shared `[SEVERITY] summary / details / Source: ...` text so chat-style
platforms only wrap it:

```java
final WebhookFormat discord = alert -> String.format("""
    {"content":"%s"}""", JsonUtil.escapeJson(WebhookFormats.renderPlainText(alert)));

final IncidentClient client = WebhookClient.clientBuilder()
    .endpoint("https://discord.com/api/webhooks/...")
    .createClient()
    .incidentClient(discord);
```

A lambda cannot override the hook, so a product whose failures state a wait in the body
needs a class; one posting to the Telegram Bot API can reuse its reader:

```java
@Override
public Duration retryAfter(final String responseBody) {
  return TelegramTextFormat.botApiRetryAfter(responseBody);
}
```

To register your own provider id for config-driven use, publish an
`IncidentClientFactory` from your jar via `ServiceLoader` (a `provides` clause in
`module-info` plus a `META-INF/services` entry). A provider fully described by
`WebhookConfig` extends `BaseWebhookIncidentClientFactory` and returns its format; a
provider needing extra config fields implements the SPI directly and composes
`WebhookConfig.parser()`, keeping the shared field handling and the strict
unknown-field errors. The shared parser reads the keys `endpoint`, `requestTimeout`,
`headers`, `bearerToken`, `chatId` and `messageThreadId` under your prefix. From
Properties it reads and validates `messageThreadId` (an integer from 0 to 2147483647)
even when your factory reads that key too; in JSON a field your parser matches first never
reaches it. So name your own fields differently:

```java
@Override
public IncidentClient createClient(final JsonIterator ji) {
  final var parser = WebhookConfig.parser();
  ji.testObject((buf, offset, len, fieldJi) -> {
    if (fieldEquals("roomId", buf, offset, len)) {
      this.roomId = fieldJi.readString();
      return true;
    }
    return parser.test(buf, offset, len, fieldJi);   // endpoint, headers, ...
  });
  return parser.createConfig().createClientBuilder().createClient()
      .incidentClient(roomFormat(roomId));
}
```

`CustomProviderExtensionTests` in this module's test sources exercises both routes
end-to-end using only public API.

## Configuration

`WebhookConfig` parses a client configuration from `Properties` (optionally with a key
prefix) or from JSON. `endpoint` is required — it is your webhook URL, so there is no
default; `requestTimeout`, `headers`, and `bearerToken` are optional, as is
`messageThreadId`, the forum topic only the `telegram` provider reads (an integer from 0 to
2147483647; absent, a blank property, a JSON null or 0 means no topic; JSON takes a number
or null, never a string; anything else fails the parse, whichever provider is configured).

```properties
incident.provider=slack
incident.endpoint=https://hooks.slack.com/services/T000/B000/XXXX
```

```properties
incident.provider=webhook
incident.endpoint=https://hooks.example.com/notify
incident.headers.X-Api-Key=KEY
incident.requestTimeout=5S
```

```properties
incident.provider=telegram
incident.endpoint=https://api.telegram.org/bot<TOKEN>/sendMessage
incident.chatId=-1001234567890
incident.messageThreadId=42
```

```json
{"provider": "webhook", "config": {"endpoint": "https://hooks.example.com/notify",
 "headers": {"X-Api-Key": "KEY"}}}
```

```java
final IncidentClient incidentClient = IncidentClients.createClient(properties, "incident");
```
