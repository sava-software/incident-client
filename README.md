# Incident Clients [![Gradle Check](https://github.com/sava-software/incident-client/actions/workflows/build.yml/badge.svg)](https://github.com/sava-software/incident-client/actions/workflows/build.yml)

Implementation of incident clients for various notification systems.

## Supported Clients

| Client                                                  | Module               | Java Module                        |
|---------------------------------------------------------|----------------------|------------------------------------|
| [PagerDuty V2 Events API](incident-pagerduty/README.md) | `incident-pagerduty` | `software.sava.incident_pagerduty` |
| [incident.io Incidents V2](incident-io/README.md)       | `incident-io`        | `software.sava.incident_io`        |
| [Generic Webhook / Slack / Telegram](incident-webhook/README.md) | `incident-webhook` | `software.sava.incident_webhook`  |

All depend on `incident-core` (`software.sava.incident_core`), which provides the
provider-neutral [`IncidentClient`](incident-core/src/main/java/software/sava/incident/core/api/IncidentClient.java)
API described [below](#provider-neutral-usage).

## Usage

### PagerDuty

[Full example](incident-examples/src/main/java/software/sava/incident/examples/PagerdutyExamples.java)

```java
final var client = PagerDutyEventClient.clientBuilder()
    .defaultClientName("CLIENT_NAME")
    .defaultRoutingKey("INTEGRATION_KEY")
    .authToken("AUTH_TOKEN")
    .createClient();

final var payload = PagerDutyEventPayload.build()
    .summary("ex-summary")
    .source("ex-source")
    .severity(PagerDutySeverity.critical)
    .timestamp(ZonedDateTime.now(UTC))
    .component("ex-component")
    .group("ex-group")
    .eventClass("ex-class")
    .customDetails("ex-num-metric", 1)
    .customDetails("ex-boolean", true)
    .customDetails("ex-string", "val")
    .link(PagerDutyLinkRef.build()
        .href("https://github.com/sava-software/incident-client")
        .text("Sava Incident PagerDuty Event Client")
        .create())
    .image(PagerDutyImageRef.build()
        .src("https://www.pagerduty.com/wp-content/uploads/2016/05/pagerduty-logo-green.png")
        .href("https://www.pagerduty.com/")
        .alt("pagerduty")
        .create())
    .create();

final var triggerResponseFuture = client.triggerDefaultRouteEvent(payload);

final var changeEventPayload = PagerDutyChangeEventPayload.build(payload).create();
final var changeEventResponseFuture = client.defaultRouteChangeEvent(changeEventPayload);

final var triggerResponse = triggerResponseFuture.join();

final var ackResponse = client.acknowledgeEvent(triggerResponse.dedupKey()).join();

final var resolveResponse = client.resolveEvent(triggerResponse.dedupKey()).join();

final var changeEventResponse = changeEventResponseFuture.join();
```

### incident.io

[Full example](incident-examples/src/main/java/software/sava/incident/examples/IncidentIoExamples.java)

`idempotencyKey` and `visibility` are required by the API; a request without them is
rejected. Severities, incident types, statuses, roles, and custom fields are all
workspace-specific ids — look them up in your incident.io workspace.

```java
try (final var httpClient = HttpClient.newHttpClient()) {
  final var client = IncidentIoClient.clientBuilder()
      .bearerToken("BEARER_TOKEN")
      .httpClient(httpClient)
      .createClient();

  final var request = CreateIncidentRequest.requestBuilder()
      .idempotencyKey(UUID.randomUUID().toString())
      .name("Test Incident")
      .summary("Test Java client")
      .visibility(CreateIncidentRequest.Visibility.PRIVATE)
      .mode(CreateIncidentRequest.Mode.test)
      .severityId("SEVERITY_ID")
      .incidentTypeId("INCIDENT_TYPE_ID")
      .incidentRoleAssignments(List.of(new CreateIncidentRequest.IncidentRoleAssignment(
          "INCIDENT_ROLE_ID",
          CreateIncidentRequest.UserReference.byEmail("responder@example.com"))))
      .customFieldValues(Map.of("CUSTOM_FIELD_ID", "value"))
      .build();

  final var response = client.createIncident(request).join();

  System.out.println(response.reference());
  System.out.println(response.permalink());
}
```

### Generic Webhook / Slack / Telegram

[Full example](incident-examples/src/main/java/software/sava/incident/examples/WebhookExamples.java)

`incident-webhook` POSTs a structured message to a single configured webhook endpoint —
fire-and-forget notification, not incident management. `WebhookFormats.GENERIC_JSON`
(provider id `webhook`) sends a canonical JSON document of the alert for receivers that
do their own mapping; `WebhookFormats.SLACK_TEXT` (provider id `slack`) sends a plain
`{"text":"..."}` Slack incoming-webhook message; `TelegramTextFormat` (provider id
`telegram`) sends a Bot API `sendMessage` body to
`https://api.telegram.org/bot<TOKEN>/sendMessage` with a configured `chatId`.

```java
final var client = WebhookClient.clientBuilder()
    .endpoint("https://hooks.slack.com/services/T000/B000/XXXX")
    .createClient();

final IncidentClient incidentClient = client.incidentClient(WebhookFormats.SLACK_TEXT);
```

## Provider-Neutral Usage

Service-level code can be written against `IncidentClient` and switch providers via
configuration. `IncidentClients` creates a client from configuration alone — provider
modules on the module or class path register themselves via `ServiceLoader`, and the
`provider` config value selects one:

```properties
incident.provider=pagerduty
incident.routingKey=INTEGRATION_KEY
```

```properties
incident.provider=incident.io
incident.bearerToken=BEARER_TOKEN
incident.visibility=private
incident.severityIds.CRITICAL=SEVERITY_ID
```

```java
final IncidentClient incidentClient = IncidentClients.createClient(properties, "incident");
```

The JSON equivalent wraps the provider's config object, with `provider` first:

```json
{"provider": "pagerduty", "config": {"routingKey": "INTEGRATION_KEY"}}
```

Adapters can also be created in code from a native client:

```java
final IncidentClient incidentClient = usePagerDuty
    ? pagerDutyEventClient.asIncidentClient()
    : incidentIoClient.incidentClientBuilder()
        .visibility(CreateIncidentRequest.Visibility.PRIVATE)
        .severityId(IncidentSeverity.CRITICAL, "SEVERITY_ID")
        .createClient();

final var alert = IncidentAlert.build()
    .summary("Validator missed its leader slot")
    .details("No block produced for slot 350000000.")
    .severity(IncidentSeverity.CRITICAL)
    .source("validator-01.example.com")
    .timestamp(ZonedDateTime.now(UTC))
    .customDetail("slot", 350000000L)
    .create();

final var response = incidentClient.reportIncident(alert).join();

if (incidentClient.supportsResolve()) {
  incidentClient.resolveIncident(response.key()).join();
}
```

The incident.io adapter maps `IncidentSeverity` values onto workspace severity ids
supplied at build time, and has no programmatic resolve: `supportsResolve()` returns
`false` and `resolveIncident(String)` fails the returned future with an
`UnsupportedOperationException`. Provider-specific features — PagerDuty links, images, and
change events; incident.io custom fields and role assignments — remain on the provider
clients.
