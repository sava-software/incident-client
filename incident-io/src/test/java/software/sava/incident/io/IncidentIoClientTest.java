package software.sava.incident.io;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class IncidentIoClientTest {

  @Test
  void testBuilder() {
    final var endpoint = "https://api.incident.io/v2";
    final var client = IncidentIoClient.buildClient()
        .endpoint(URI.create(endpoint))
        .createClient();
    assertNotNull(client);
    assertNotNull(client.httpClient());
    assertEquals(endpoint, client.endpoint().toString());
  }

  @Test
  void testCreateIncidentRequestBody() {
    final var request = CreateIncidentRequest.builder()
        .name("Test Incident")
        .summary("Test Summary")
        .incidentTypeId("type-123")
        .priorityId("priority-123")
        .severityId("severity-123")
        .statusId("status-123")
        .visibility(CreateIncidentRequest.Visibility.PUBLIC)
        .build();

    final var body = request.body();
    assertNotNull(body);
    // Basic validation of the JSON body
    assertEquals("""
        {"name":"Test Incident","summary":"Test Summary","incident_type_id":"type-123","priority_id":"priority-123","severity_id":"severity-123","status_id":"status-123","visibility":"public"}""", body
    );
  }

  @Test
  void testCreateIncidentRequestBodyFull() {
    final var request = CreateIncidentRequest.builder()
        .name("Full Incident")
        .summary("Full Summary")
        .description("Full Description")
        .idempotencyKey("idem-123")
        .incidentTypeId("type-123")
        .mode(CreateIncidentRequest.Mode.manual)
        .priorityId("priority-123")
        .severityId("severity-123")
        .statusId("status-123")
        .visibility(CreateIncidentRequest.Visibility.PRIVATE)
        .slackTeamId("slack-123")
        .creatorOutOfHours(true)
        .incidentRoleAssignments(List.of(
            new CreateIncidentRequest.IncidentRoleAssignment("role-1", "assignee-1")
        ))
        .customFieldValues(Map.of(
            "field-1", "val-1"
        ))
        .build();

    final var body = request.body();
    assertNotNull(body);

    // Verify the presence of all fields. Order might vary due to Map, but we'll check substrings.
    assert (body.contains("""
        "idempotency_key":"idem-123\""""));
    assert (body.contains("""
        "name":"Full Incident\""""));
    assert (body.contains("""
        "summary":"Full Summary\""""));
    assert (body.contains("""
        "description":"Full Description\""""));
    assert (body.contains("""
        "incident_type_id":"type-123\""""));
    assert (body.contains("""
        "incident_role_assignments":[{"incident_role_id":"role-1","assignee_id":"assignee-1"}]"""));
    assert (body.contains("""
        "mode":"manual\""""));
    assert (body.contains("""
        "priority_id":"priority-123\""""));
    assert (body.contains("""
        "severity_id":"severity-123\""""));
    assert (body.contains("""
        "status_id":"status-123\""""));
    assert (body.contains("""
        "visibility":"private\""""));
    assert (body.contains("""
        "slack_team_id":"slack-123\""""));
    assert (body.contains("""
        "creator_out_of_hours":true"""));
    assert (body.contains("""
        "custom_field_values":{"field-1":[{"value":"val-1"}]}"""));
  }

  @Test
  void testCreateIncidentRequestBodyEmptyFields() {
    final var request = CreateIncidentRequest.builder()
        .name("Name")
        .summary("Summary")
        .description("") // Blank string should be excluded
        .idempotencyKey(null) // Null should be excluded
        .incidentTypeId("type-123")
        .priorityId("  ") // Blank string should be excluded
        .build();

    final String body = request.body();
    // name, summary, incident_type_id are present. description, idempotency_key, priority_id should be absent.
    assertEquals("""
        {"name":"Name","summary":"Summary","incident_type_id":"type-123"}""", body
    );
  }

  @Test
  void testCreateIncidentRequestBodyEmptyCollections() {
    final var request = CreateIncidentRequest.builder()
        .name("Name")
        .summary("Summary")
        .incidentTypeId("type-123")
        .incidentRoleAssignments(List.of()) // Empty collection should be excluded
        .customFieldValues(Map.of()) // Empty map should be excluded
        .build();

    final var body = request.body();
    assertEquals("""
        {"name":"Name","summary":"Summary","incident_type_id":"type-123"}""", body
    );
  }

  @Test
  void testCreateIncidentRequestBodyEnumsAndBooleans() {
    final var request = CreateIncidentRequest.builder()
        .name("Name")
        .summary("Summary")
        .incidentTypeId("type-123")
        .mode(null)
        .visibility(null)
        .creatorOutOfHours(null)
        .build();

    final var body = request.body();
    assertEquals("""
        {"name":"Name","summary":"Summary","incident_type_id":"type-123"}""", body
    );
  }

  @Test
  void testParseCreateIncidentResponse() {
    final var json = """
        {
          "incident": {
            "call_url": "https://zoom.us/foo",
            "created_at": "2021-08-17T13:28:57.801578Z",
            "creator": {
              "alert": {
                "id": "01GW2G3V0S59R238FAHPDS1R66",
                "title": "*errors.withMessage: PG::Error failed to connect"
              },
              "api_key": {
                "id": "01FCNDV6P870EA6S7TK1DSYDG0",
                "name": "My test API key"
              },
              "user": {
                "email": "lisa@incident.io",
                "id": "01FCNDV6P870EA6S7TK1DSYDG0",
                "name": "Lisa Karlin Curtis",
                "role": "viewer",
                "slack_user_id": "U02AYNF2XJM"
              },
              "workflow": {
                "id": "01FCNDV6P870EA6S7TK1DSYDG0",
                "name": "My little workflow"
              }
            },
            "custom_field_entries": [
              {
                "custom_field": {
                  "description": "Which team is impacted by this issue",
                  "field_type": "single_select",
                  "id": "01FCNDV6P870EA6S7TK1DSYDG0",
                  "name": "Affected Team",
                  "options": [
                    {
                      "custom_field_id": "01FCNDV6P870EA6S7TK1DSYDG0",
                      "id": "01FCNDV6P870EA6S7TK1DSYDG0",
                      "sort_key": 10,
                      "value": "Product"
                    }
                  ]
                },
                "values": [
                  {
                    "value_catalog_entry": {
                      "aliases": [
                        "lawrence@incident.io",
                        "lawrence"
                      ],
                      "external_id": "761722cd-d1d7-477b-ac7e-90f9e079dc33",
                      "id": "01FCNDV6P870EA6S7TK1DSYDG0",
                      "name": "Primary On-call"
                    },
                    "value_link": "https://google.com/",
                    "value_numeric": "123.456",
                    "value_option": {
                      "custom_field_id": "01FCNDV6P870EA6S7TK1DSYDG0",
                      "id": "01FCNDV6P870EA6S7TK1DSYDG0",
                      "sort_key": 10,
                      "value": "Product"
                    },
                    "value_text": "This is my text field, I hope you like it"
                  }
                ]
              }
            ],
            "duration_metrics": [
              {
                "duration_metric": {
                  "id": "01FCNDV6P870EA6S7TK1DSYD5H",
                  "name": "Lasted"
                },
                "value_seconds": 10800
              }
            ],
            "external_issue_reference": {
              "issue_name": "INC-123",
              "issue_permalink": "https://linear.app/incident-io/issue/INC-1609/find-copywriter-to-write-up",
              "provider": "asana"
            },
            "has_debrief": false,
            "id": "01FDAG4SAP5TYPT98WGR2N7W91",
            "incident_role_assignments": [
              {
                "assignee": {
                  "email": "lisa@incident.io",
                  "id": "01FCNDV6P870EA6S7TK1DSYDG0",
                  "name": "Lisa Karlin Curtis",
                  "role": "viewer",
                  "slack_user_id": "U02AYNF2XJM"
                },
                "role": {
                  "created_at": "2021-08-17T13:28:57.801578Z",
                  "description": "The person currently coordinating the incident",
                  "id": "01FCNDV6P870EA6S7TK1DSYDG0",
                  "instructions": "Take point on the incident; Make sure people are clear on responsibilities",
                  "name": "Incident Lead",
                  "required": false,
                  "role_type": "lead",
                  "shortform": "lead",
                  "updated_at": "2021-08-17T13:28:57.801578Z"
                }
              }
            ],
            "incident_status": {
              "category": "triage",
              "created_at": "2021-08-17T13:28:57.801578Z",
              "description": "Impact has been **fully mitigated**, and we're ready to learn from this incident.",
              "id": "01FCNDV6P870EA6S7TK1DSYD5H",
              "name": "Closed",
              "rank": 4,
              "updated_at": "2021-08-17T13:28:57.801578Z"
            },
            "incident_timestamp_values": [
              {
                "incident_timestamp": {
                  "id": "01FCNDV6P870EA6S7TK1DSYD5H",
                  "name": "Impact started",
                  "rank": 1
                },
                "value": {
                  "value": "2021-08-17T13:28:57.801578Z"
                }
              }
            ],
            "incident_type": {
              "create_in_triage": "always",
              "created_at": "2021-08-17T13:28:57.801578Z",
              "description": "Customer facing production outages",
              "id": "01FCNDV6P870EA6S7TK1DSYDG0",
              "is_default": false,
              "name": "Production Outage",
              "private_incidents_only": false,
              "updated_at": "2021-08-17T13:28:57.801578Z"
            },
            "mode": "standard",
            "name": "Our database is sad",
            "permalink": "https://app.incident.io/incidents/123",
            "postmortem_document_url": "https://docs.google.com/my_doc_id",
            "reference": "INC-123",
            "severity": {
              "created_at": "2021-08-17T13:28:57.801578Z",
              "description": "Issues with **low impact**.",
              "id": "01FCNDV6P870EA6S7TK1DSYDG0",
              "name": "Minor",
              "rank": 1,
              "updated_at": "2021-08-17T13:28:57.801578Z"
            },
            "slack_channel_id": "C02AW36C1M5",
            "slack_channel_name": "inc-165-green-parrot",
            "slack_team_id": "T02A1FSLE8J",
            "summary": "Our database is really really sad, and we don't know why yet.",
            "updated_at": "2021-08-17T13:28:57.801578Z",
            "visibility": "public",
            "workload_minutes_late": 40.7,
            "workload_minutes_sleeping": 0,
            "workload_minutes_total": 60.7,
            "workload_minutes_working": 20
          }
        }
        """;

    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse(json));

    assertNotNull(response);
    assertEquals("https://zoom.us/foo", response.callUrl());
    assertEquals("2021-08-17T13:28:57.801578Z", response.createdAt().toString());
    assertNotNull(response.creator());
    assertEquals("lisa@incident.io", response.creator().email());

    assertEquals(1, response.customFieldEntries().size());
    final var customField = response.customFieldEntries().iterator().next();
    assertEquals("01FCNDV6P870EA6S7TK1DSYDG0", customField.customFieldId());
    assertEquals("Affected Team", customField.customFieldName());
    assertEquals("single_select", customField.customFieldType());
    assertEquals("This is my text field, I hope you like it", customField.value());

    assertEquals(1, response.durationMetrics().size());
    final var durationMetric = response.durationMetrics().iterator().next();
    assertEquals("01FCNDV6P870EA6S7TK1DSYD5H", durationMetric.durationMetricId());
    assertEquals("Lasted", durationMetric.durationMetricName());
    assertEquals(10800L, durationMetric.valueSeconds());

    assertNotNull(response.externalIssueReference());
    assertEquals("INC-123", response.externalIssueReference().issueTitle());
    assertEquals("asana", response.externalIssueReference().provider());

    assertEquals("01FDAG4SAP5TYPT98WGR2N7W91", response.id());

    assertEquals(1, response.incidentRoleAssignments().size());
    final var roleAssignment = response.incidentRoleAssignments().iterator().next();
    assertEquals("lisa@incident.io", roleAssignment.assignee().email());
    assertEquals("Incident Lead", roleAssignment.role().name());

    assertNotNull(response.incidentStatus());
    assertEquals("Closed", response.incidentStatus().name());
    assertEquals("triage", response.incidentStatus().category());

    assertEquals(1, response.incidentTimestampValues().size());
    final var timestampValue = response.incidentTimestampValues().iterator().next();
    assertEquals("01FCNDV6P870EA6S7TK1DSYD5H", timestampValue.timestampId());
    assertEquals("Impact started", timestampValue.timestampName());
    assertEquals("2021-08-17T13:28:57.801578Z", timestampValue.value().toString());

    assertNotNull(response.incidentType());
    assertEquals("Production Outage", response.incidentType().name());

    assertEquals(CreateIncidentResponse.Mode.standard, response.mode());
    assertEquals("Our database is sad", response.name());
    assertEquals("INC-123", response.reference());

    assertNotNull(response.severity());
    assertEquals("Minor", response.severity().name());

    assertEquals("C02AW36C1M5", response.slackChannelId());
    assertEquals("public", response.visibility().toString());
    assertEquals(40.7, response.workloadMinutesLate());
    assertEquals(60.7, response.workloadMinutesTotal());
  }
}
