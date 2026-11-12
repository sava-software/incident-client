# incident.io Client

This client aims to be compliant with the latest version of the
[incident.io Incidents API](https://api-docs.incident.io/tag/Incidents-V2), currently V2.
It also only aims to be supported against the latest GA OpenJDK release.

Currently `POST /v2/incidents` (`Incidents V2#Create`) is implemented. `idempotencyKey`
and `visibility` are required by the API; a request without them is rejected. Severities,
incident types, statuses, roles, and custom fields are all workspace-specific ids — look
them up in your incident.io workspace.

`POST /v2/alert_events/http/{alert_source_config_id}` (`Alert Events V2#CreateHTTP`), the
HTTP alert source that incident.io recommends for monitor signals, is implemented too; see
[HTTP Alert Sources](#http-alert-sources).

## [Example Usage](../incident-examples/src/main/java/software/sava/incident/examples/IncidentIoExamples.java)

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
      .statusId("INCIDENT_STATUS_ID")
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

Role assignees may be referenced by incident.io user id, email, or Slack user id:
`UserReference.byId(..)`, `UserReference.byEmail(..)`, `UserReference.bySlackUserId(..)`.

The rest of `IncidentsCreatePayloadV2` is available on the same builder:
`slackTeamId`, `slackChannelNameOverride`, `incidentTimestampValues`, and
`retrospectiveIncidentOptions` (which only applies with `Mode.retrospective`).

### Custom fields

`customFieldValues(Map<String, String>)` is sugar for the text-only case. Every other
custom-field type needs the value shape that matches it, via `customFieldEntries`:

```java
.customFieldEntries(List.of(
    new CreateIncidentRequest.CustomFieldEntry("TEXT_FIELD_ID", "some text"),
    // single_select / multi_select take one value per selected option
    new CreateIncidentRequest.CustomFieldEntry("SELECT_FIELD_ID", List.of(
        CreateIncidentRequest.CustomFieldValue.optionId("OPTION_ID_1"),
        CreateIncidentRequest.CustomFieldValue.optionId("OPTION_ID_2"))),
    new CreateIncidentRequest.CustomFieldEntry("CATALOG_FIELD_ID",
        List.of(CreateIncidentRequest.CustomFieldValue.catalogEntryId("CATALOG_ENTRY_ID"))),
    new CreateIncidentRequest.CustomFieldEntry("LINK_FIELD_ID",
        List.of(CreateIncidentRequest.CustomFieldValue.link("https://example.com/"))),
    // numeric goes over the wire as a string
    new CreateIncidentRequest.CustomFieldEntry("NUMERIC_FIELD_ID",
        List.of(CreateIncidentRequest.CustomFieldValue.numeric("123.456")))))
```

An entry with an empty `values` list unsets the field. The payload's deprecated
`value_timestamp` is not supported — use `incidentTimestampValues` instead.

### Response

`CreateIncidentResponse` follows `IncidentV2`. Useful response shapes:

- `creator()` is an `ActorV2` **union** — `alert()`, `apiKey()`, `user()`, `workflow()`,
  with the unset variants null. An incident created through this client is created by an
  API key, so `creator().apiKey()` is what you get back, not `creator().user()`. Role
  assignees are a plain `UserV2`, not this union.
- `customFieldEntries()` carries every value the field holds (a `multi_select` has one
  per selected option), and which component of `CustomFieldValueV2` is populated follows
  the entry's `customFieldType`: `valueText`, `valueOption`, `valueCatalogEntry`,
  `valueLink`, or `valueNumeric`.
- `debriefs()` contains scheduled `IncidentDebriefV2` entries with `id`, `startAt`, and
  `endAt` (`OffsetDateTime`). It preserves the server's start-time order and excludes
  cancelled calendar events. Missing, null, and empty lists read as an empty immutable
  collection. The other response collection accessors do not guarantee immutability.

### Errors

`IncidentIoRequestException` exposes the HTTP response, error type, request id, and
status. `errors()` contains readable error descriptions (`code: message` when both are
present). `getMessage()` joins them with semicolons, falling back to `type` when the
error list is empty.
For structured diagnostics, use:

- `errorDetails()` — an immutable list of `ErrorDetail` records, each with `code`,
  `message`, a string-to-string `metadata` map, and an optional `ErrorSource` (`field`
  and JSON `pointer`). Missing metadata reads as an empty immutable map. An omitted
  source member reads as null, including `pointer` in a field-only source.
- `rateLimit()` — optional `RateLimit` with `name`, `limit`, `remaining`, and
  `retryAfter`. The latter preserves the API's RFC 3339 UTC timestamp string. For
  scheduling, use the exception's `retryAfter()`: the `Retry-After` HTTP header, which
  upstream recommends, in seconds or as a date measured from the response's `Date` header,
  or else this timestamp measured from that `Date`.
- `debug()` — optional `DebugInfo` with the server's internal `message` and immutable
  `stacktrace` list. Missing stack frames read as an empty list.

Optional objects read as null when missing or explicitly null. Unknown fields are
skipped, and a recognized field the parser cannot read raises `IncidentIoParseException`.
Null metadata values and null stack-frame elements are rejected.
`IncidentService` waits out `retryAfter()` before its next attempt, up to the service's
limit, and past it fails the call at once with this exception.

## Configuration

`IncidentIoConfig` parses a client configuration from `Properties` (optionally with a key
prefix) or from JSON, and creates a pre-configured client builder. `bearerToken` is
required; `endpoint` and `requestTimeout` are optional.

```java
final var config = IncidentIoConfig.parseConfig(properties);
final var client = config.createClientBuilder()
    .httpClient(httpClient)
    .createClient();
```

The config also carries the workspace-specific mapping the provider-neutral adapter
needs — `severityIds` keyed by `IncidentSeverity` name, default `incidentTypeId`,
`statusId`, `visibility`, and `mode`, and the `incidentTimestampId` the alert timestamp
is written to:

```properties
bearerToken=BEARER_TOKEN
visibility=private
severityIds.CRITICAL=SEVERITY_ID
incidentTypeId=INCIDENT_TYPE_ID
incidentTimestampId=INCIDENT_TIMESTAMP_ID
```

```java
final var incidentClient = config.createIncidentClientBuilder(client).createClient();
```

## Provider-Neutral Client

`IncidentIoIncidentClient` adapts an `IncidentIoClient` to the provider-neutral
`IncidentClient` from `incident-core`. Because incident.io ids are workspace-specific, the
mapping from `IncidentSeverity` to a severity id — and any default type, status,
visibility, and mode — is supplied at build time.

```java
final var incidentClient = client.incidentClientBuilder()
    .visibility(CreateIncidentRequest.Visibility.PRIVATE)
    .severityId(IncidentSeverity.CRITICAL, "SEVERITY_ID")
    .severityId(IncidentSeverity.WARNING, "SEVERITY_ID")
    .incidentTypeId("INCIDENT_TYPE_ID")
    .incidentTimestampId("INCIDENT_TIMESTAMP_ID")
    .createClient();

final var response = incidentClient.reportIncident(IncidentAlert.build()
    .summary("Validator missed its leader slot")
    .details("No block produced for slot 350000000.")
    .severity(IncidentSeverity.CRITICAL)
    .source("validator-07.example.com")
    .customDetail("region", "us-east-1")
    .timestamp(ZonedDateTime.now())
    .create()).join();
```

The alert's `summary` becomes the incident `name` and its `details` the incident
`summary`, with `source()` and `customDetails()` appended to that summary as text:

```
No block produced for slot 350000000.

Source: validator-07.example.com
region: us-east-1
slot: 350000000
```

An incident.io custom field is a workspace schema object referenced by id — not an
arbitrary key/value like PagerDuty's `custom_details` — so there is nowhere structured to
put them without a per-key id mapping. Appending keeps them attached to the incident
rather than dropping them. (Mapping detail keys onto real custom fields, so they become
filterable in incident.io, is a possible future addition; it would need a configured
`key -> custom field id` table, and only `text`, `link`, and `numeric` field types can
take an arbitrary value — select and catalog fields want option ids.)

`IncidentAlert#timestamp()` is sent as an `incident_timestamp_values` entry, but only
when `incidentTimestampId` names the workspace's incident timestamp to set — incident.io
has no id-free slot for it, and stamps its own `created_at` either way. Unset, or with a
blank id, the alert timestamp is dropped.

When reporting through `IncidentService`, a null or blank alert key is generated once
per report and reused across retries. Separate report calls receive separate keys;
provide an explicit key to deduplicate across calls.

incident.io incidents are resolved by status updates, which this client does not
support: `supportsResolve()` returns `false` and `resolveIncident(String)` fails the
returned future with an `UnsupportedOperationException`.

## HTTP Alert Sources

[Example](../incident-examples/src/main/java/software/sava/incident/examples/IncidentIoAlertExamples.java)

incident.io's guidance is that a monitor's signal belongs on an HTTP alert source rather
than Create Incident: the alert source deduplicates on a key, a `resolved` event with the
same key resolves the alert, and the workspace's alert routes decide whether and how the
alert becomes an incident. `IncidentIoAlertEventClient` (package
`software.sava.incident.io.alert`) sends `AlertEventsCreateHTTPPayloadV2` events to one
alert source. It authenticates with the alert source's own secret as
`Authorization: Bearer <token>`, not with an API key.

```java
final var client = IncidentIoAlertEventClient.clientBuilder()
    .alertSourceConfigId("ALERT_SOURCE_CONFIG_ID")
    .token("ALERT_SOURCE_SECRET")
    .createClient();

final var firing = AlertEvent.build()
    .title("Disk full on db-1")
    .status(AlertEvent.Status.firing)
    .deduplicationKey("db-1-disk")
    .description("`/var` is at **99%**.")
    .sourceUrl("https://monitor.example.com/alerts/db-1-disk")
    .metadata("service", "db")
    .metadata("team", List.of("storage"))
    .create();
client.sendAlertEvent(firing).join();

client.sendAlertEvent(AlertEvent.build()
    .title("Disk space recovered on db-1")
    .status(AlertEvent.Status.resolved)
    .deduplicationKey("db-1-disk")
    .create()).join();
```

The endpoint is `https://api.incident.io/v2/alert_events/http/<id>`, the id
percent-encoded as one path segment; an `endpoint(..)` set on the builder is the full
request URL instead, and the id is then not needed. `title` and `status` are required.
A firing event without a deduplication key is given a random UUID, and a resolved event
without one is refused, since it must carry the key of the alert it resolves.
`description` is markdown and, with `sourceUrl`, is omitted when blank.

`metadata` is the free-form object the alert source is configured to parse, modelled one
level deep: strings, booleans, integer types and `BigInteger`, `BigDecimal` (in plain
notation), finite `Double`/`Float`, null, and collections of those as JSON arrays. Any
other object is sent as its `toString()`. A map, a Java array, a nested collection and a
nonfinite number are refused with an `IllegalArgumentException` when added.

A non-2xx response fails the future with `IncidentIoRequestException`, whose
`retryAfter()` reads a 429's `Retry-After` header, in seconds or as a date, or, failing
that, the body's `rate_limit.retry_after`; a date is measured from the response's `Date`. A 2xx body that cannot be
parsed fails with `IncidentIoParseException`, which is not retried.

### Provider-neutral adapter

`IncidentIoAlertIncidentClient` adapts the alert-source client to `IncidentClient`, under
provider id `incident.io-alerts`:

```java
final var incidentClient = client.incidentClientBuilder()
    .resolveTitle("Recovered")
    .createClient();
```

A report sends a firing event: the alert's `summary` is the `title`, its `details` the
`description`, and its key the `deduplication_key` (a blank key is generated and returned
in the response). `metadata` carries `severity` (lower case), `source` when not blank,
`timestamp` in RFC 3339 when set, then every custom detail; a custom detail named like one
of those three replaces it in place, and one the event would refuse is sent as text. A
resolve sends a resolved event with the same key, and `supportsResolve()` is `true`.

Two caveats:

- The key and the resolve address the **alert's** lifecycle. Whether an incident is
  created from the alert, and what happens to it when the alert resolves, depends on the
  workspace's alert routes, so `supportsResolve()` does not promise that an incident
  closes.
- The schema requires a `title` on every event, and neither the schema nor incident.io's
  documentation says whether a resolve event's title replaces the alert's. A resolve
  therefore sends `resolveTitle` when one is configured, and the key otherwise.

### Configuration

`IncidentIoAlertSourceConfig` parses `token` (required), `alertSourceConfigId` (required
unless `endpoint` is set), `resolveTitle`, `endpoint`, and `requestTimeout` from
`Properties` or JSON:

```properties
incident.provider=incident.io-alerts
incident.token=ALERT_SOURCE_SECRET
incident.alertSourceConfigId=ALERT_SOURCE_CONFIG_ID
incident.resolveTitle=Recovered
```

```java
final IncidentClient incidentClient = IncidentClients.createClient(properties, "incident");
```
