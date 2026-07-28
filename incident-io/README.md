# incident.io Client

This client aims to be compliant with the latest version of the
[incident.io Incidents API](https://api-docs.incident.io/tag/Incidents-V2), currently V2.
It also only aims to be supported against the latest GA OpenJDK release.

Currently `POST /v2/incidents` (`Incidents V2#Create`) is implemented. `idempotencyKey`
and `visibility` are required by the API; a request without them is rejected. Severities,
incident types, statuses, roles, and custom fields are all workspace-specific ids — look
them up in your incident.io workspace.

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

`CreateIncidentResponse` follows `IncidentV2`. Two shapes are worth knowing before you
read from it:

- `creator()` is an `ActorV2` **union** — `alert()`, `apiKey()`, `user()`, `workflow()`,
  with the unset variants null. An incident created through this client is created by an
  API key, so `creator().apiKey()` is what you get back, not `creator().user()`. Role
  assignees are a plain `UserV2`, not this union.
- `customFieldEntries()` carries every value the field holds (a `multi_select` has one
  per selected option), and which component of `CustomFieldValueV2` is populated follows
  the entry's `customFieldType`: `valueText`, `valueOption`, `valueCatalogEntry`,
  `valueLink`, or `valueNumeric`.

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
    .timestamp(ZonedDateTime.now())
    .create()).join();
```

The alert's `summary` becomes the incident `name` and its `details` the incident
`summary`.

`IncidentAlert#timestamp()` is sent as an `incident_timestamp_values` entry, but only
when `incidentTimestampId` names the workspace's incident timestamp to set — incident.io
has no id-free slot for it, and stamps its own `created_at` either way. Unset, or with a
blank id, the alert timestamp is dropped. `IncidentAlert#customDetails()` and `source()`
are likewise not sent: both would need a custom-field id mapping, and only `text`,
`link`, and `numeric` fields could take an arbitrary value anyway — select and catalog
fields need option ids, not values.

incident.io incidents are resolved by status updates, which this client does not
support: `supportsResolve()` returns `false` and `resolveIncident(String)` fails the
returned future with an `UnsupportedOperationException`.
