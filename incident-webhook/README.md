# Generic Webhook Client

POSTs a well-structured message to a single configured webhook endpoint — a custom
receiver, an automation platform (Zapier, n8n), or a product that accepts simple JSON
posts, such as a [Slack incoming webhook](https://docs.slack.dev/messaging/sending-messages-using-incoming-webhooks).

A webhook message is fire-and-forget notification, not incident management: there is no
incident id, permalink, or lifecycle, and `supportsResolve()` is `false`. Transport,
configuration, and error handling are shared across all formats; the only per-product
variance is the `WebhookFormat` that renders an `IncidentAlert` into the JSON body.

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
  is required configuration. Text over 4096 characters is truncated client-side, since
  `sendMessage` rejects longer messages outright.

Supporting another product (Discord, Google Chat, ...) is one `WebhookFormat`
implementation plus a factory registration.

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

`IncidentResponse.key()` echoes the alert's key, `status()` is the receiver's stripped
response body (`ok` for Slack) or `delivered` when the body is empty, and `url()` is
null. Non-2xx responses fail with a `WebhookRequestException` whose message carries the
status and response body but never the endpoint URL — webhook URLs are credentials.

## Configuration

`WebhookConfig` parses a client configuration from `Properties` (optionally with a key
prefix) or from JSON. `endpoint` is required — it is your webhook URL, so there is no
default; `requestTimeout`, `headers`, and `bearerToken` are optional.

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
```

```json
{"provider": "webhook", "config": {"endpoint": "https://hooks.example.com/notify",
 "headers": {"X-Api-Key": "KEY"}}}
```

```java
final IncidentClient incidentClient = IncidentClients.createClient(properties, "incident");
```
