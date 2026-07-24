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

## Provider-Neutral Client

`IncidentIoIncidentClient` adapts an `IncidentIoClient` to the provider-neutral
`IncidentClient` from `incident-core`. Because incident.io ids are workspace-specific, the
mapping from `IncidentSeverity` to a severity id — and any default type, status,
visibility, and mode — is supplied at build time.

```java
final var incidentClient = IncidentIoIncidentClient.build(client)
    .visibility(CreateIncidentRequest.Visibility.PRIVATE)
    .severityId(IncidentSeverity.CRITICAL, "SEVERITY_ID")
    .severityId(IncidentSeverity.WARNING, "SEVERITY_ID")
    .incidentTypeId("INCIDENT_TYPE_ID")
    .createClient();

final var response = incidentClient.reportIncident(IncidentAlert.build()
    .summary("Validator missed its leader slot")
    .details("No block produced for slot 350000000.")
    .severity(IncidentSeverity.CRITICAL)
    .create()).join();
```

The alert's `summary` becomes the incident `name` and its `details` the incident
`summary`; `IncidentAlert#customDetails()` has no id-free incident.io equivalent and is
not sent. incident.io incidents are resolved by status updates, which this client does not
support: `supportsResolve()` returns `false` and `resolveIncident(String)` fails the
returned future with an `UnsupportedOperationException`.
