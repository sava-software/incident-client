package software.sava.incident.io;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class CreateIncidentResponseTests {

  private static final String FULL_RESPONSE = """
      {
        "type": "incident",
        "incident": {
          "call_url": "https://zoom.us/j/123",
          "created_at": "2024-05-01T12:30:00Z",
          "creator": {
            "user": {
              "email": "creator@example.com",
              "id": "actor-1",
              "name": "Creator Name",
              "role": "owner",
              "slack_user_id": "U123",
              "unknown_user_field": true
            },
            "unknown_actor_field": true
          },
          "custom_field_entries": [
            {
              "custom_field": {
                "id": "cf-1",
                "name": "Affected Team",
                "field_type": "single_select",
                "unknown": null
              },
              "values": [
                {"value_text": "Payments", "unknown": 1},
                {"value_text": "second-entry"}
              ],
              "unknown": []
            }
          ],
          "duration_metrics": [
            {
              "duration_metric": {"id": "dm-1", "name": "Time to Resolve", "unknown": "x"},
              "value_seconds": 3600,
              "status": "timestamps_missing",
              "unknown": {}
            }
          ],
          "external_issue_reference": {
            "issue_name": "Issue Title",
            "issue_permalink": "https://jira.example.com/JIRA-1",
            "provider": "jira",
            "unknown": "y"
          },
          "has_debrief": true,
          "id": "incident-id-1",
          "incident_role_assignments": [
            {
              "assignee": {
                "email": "lead@example.com",
                "id": "actor-2",
                "name": "Lead Name",
                "role": "responder",
                "slack_user_id": "U456",
                "unknown": "z"
              },
              "role": {
                "id": "role-1",
                "name": "Incident Lead",
                "description": "Leads the incident",
                "role_type": "lead",
                "unknown": 2
              },
              "unknown": "w"
            }
          ],
          "incident_status": {
            "id": "status-1",
            "name": "Investigating",
            "description": "We are investigating",
            "category": "live",
            "unknown": false
          },
          "incident_timestamp_values": [
            {
              "incident_timestamp": {"id": "ts-1", "name": "Reported At", "unknown": "v"},
              "value": {"value": "2024-05-01T12:00:00Z", "unknown": "u"},
              "unknown": "t"
            }
          ],
          "incident_type": {
            "id": "type-1",
            "name": "Default",
            "description": "Default type",
            "unknown": "s"
          },
          "mode": "standard",
          "name": "Incident Name",
          "permalink": "https://app.incident.io/incidents/1",
          "postmortem_document_ids": ["pm-1", "pm-2"],
          "postmortem_document_url": "https://docs.example.com/postmortem",
          "reference": "INC-123",
          "severity": {
            "id": "sev-1",
            "name": "Major",
            "description": "Major severity",
            "unknown": "r"
          },
          "slack_channel_id": "C123",
          "slack_channel_name": "#inc-123",
          "slack_team_id": "T123",
          "summary": "Incident Summary",
          "updated_at": "2024-05-01T13:45:00Z",
          "visibility": "public",
          "workload_minutes_late": 1.5,
          "workload_minutes_sleeping": 2.5,
          "workload_minutes_total": 10.25,
          "workload_minutes_working": 6.25,
          "unknown_incident_field": {"nested": [1, 2, 3]}
        },
        "unknown_top_level": "ignored"
      }""";

  @Test
  void parseFullResponse() {
    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse(FULL_RESPONSE));

    assertEquals("https://zoom.us/j/123", response.callUrl());
    assertEquals(OffsetDateTime.parse("2024-05-01T12:30:00Z"), response.createdAt());

    final var creator = response.creator();
    assertNull(creator.alert());
    assertNull(creator.apiKey());
    assertNull(creator.workflow());
    assertEquals("creator@example.com", creator.user().email());
    assertEquals("actor-1", creator.user().id());
    assertEquals("Creator Name", creator.user().name());
    assertEquals("owner", creator.user().role());
    assertEquals("U123", creator.user().slackUserId());

    final var customFieldEntries = List.copyOf(response.customFieldEntries());
    assertEquals(1, customFieldEntries.size());
    final var customFieldEntry = customFieldEntries.getFirst();
    assertEquals("cf-1", customFieldEntry.customFieldId());
    assertEquals("Affected Team", customFieldEntry.customFieldName());
    assertEquals("single_select", customFieldEntry.customFieldType());
    // every value is kept, not just the first
    final var values = List.copyOf(customFieldEntry.values());
    assertEquals(2, values.size());
    assertEquals("Payments", values.getFirst().valueText());
    assertEquals("second-entry", values.get(1).valueText());

    final var durationMetrics = List.copyOf(response.durationMetrics());
    assertEquals(1, durationMetrics.size());
    final var durationMetric = durationMetrics.getFirst();
    assertEquals("dm-1", durationMetric.durationMetricId());
    assertEquals("Time to Resolve", durationMetric.durationMetricName());
    assertEquals(3600L, durationMetric.valueSeconds());
    // status is spec-required and says whether valueSeconds still matches the incident's
    // timestamps; anything but "success" means the value is stale
    assertEquals("timestamps_missing", durationMetric.status());

    final var externalIssueReference = response.externalIssueReference();
    assertEquals("Issue Title", externalIssueReference.issueName());
    assertEquals("https://jira.example.com/JIRA-1", externalIssueReference.issuePermalink());
    assertEquals("jira", externalIssueReference.provider());

    assertTrue(response.hasDebrief());
    assertEquals("incident-id-1", response.id());

    final var roleAssignments = List.copyOf(response.incidentRoleAssignments());
    assertEquals(1, roleAssignments.size());
    final var roleAssignment = roleAssignments.getFirst();
    assertEquals("lead@example.com", roleAssignment.assignee().email());
    assertEquals("actor-2", roleAssignment.assignee().id());
    assertEquals("Lead Name", roleAssignment.assignee().name());
    assertEquals("responder", roleAssignment.assignee().role());
    assertEquals("U456", roleAssignment.assignee().slackUserId());
    assertEquals("role-1", roleAssignment.role().id());
    assertEquals("Incident Lead", roleAssignment.role().name());
    assertEquals("Leads the incident", roleAssignment.role().description());
    assertEquals("lead", roleAssignment.role().roleType());

    final var incidentStatus = response.incidentStatus();
    assertEquals("status-1", incidentStatus.id());
    assertEquals("Investigating", incidentStatus.name());
    assertEquals("We are investigating", incidentStatus.description());
    assertEquals("live", incidentStatus.category());

    final var timestampValues = List.copyOf(response.incidentTimestampValues());
    assertEquals(1, timestampValues.size());
    final var timestampValue = timestampValues.getFirst();
    assertEquals("ts-1", timestampValue.timestampId());
    assertEquals("Reported At", timestampValue.timestampName());
    assertEquals(OffsetDateTime.parse("2024-05-01T12:00:00Z"), timestampValue.value());

    final var incidentType = response.incidentType();
    assertEquals("type-1", incidentType.id());
    assertEquals("Default", incidentType.name());
    assertEquals("Default type", incidentType.description());

    assertEquals(CreateIncidentResponse.Mode.standard, response.mode());
    assertEquals("Incident Name", response.name());
    assertEquals("https://app.incident.io/incidents/1", response.permalink());
    assertEquals(List.of("pm-1", "pm-2"), List.copyOf(response.postmortemDocumentIds()));
    assertEquals("https://docs.example.com/postmortem", response.postmortemDocumentUrl());
    assertEquals("INC-123", response.reference());

    final var severity = response.severity();
    assertEquals("sev-1", severity.id());
    assertEquals("Major", severity.name());
    assertEquals("Major severity", severity.description());

    assertEquals("C123", response.slackChannelId());
    assertEquals("#inc-123", response.slackChannelName());
    assertEquals("T123", response.slackTeamId());
    assertEquals("Incident Summary", response.summary());
    assertEquals(OffsetDateTime.parse("2024-05-01T13:45:00Z"), response.updatedAt());
    assertEquals(CreateIncidentResponse.Visibility.PUBLIC, response.visibility());
    assertEquals(1.5, response.workloadMinutesLate());
    assertEquals(2.5, response.workloadMinutesSleeping());
    assertEquals(10.25, response.workloadMinutesTotal());
    assertEquals(6.25, response.workloadMinutesWorking());
  }

  @Test
  void parseEmptyIncident() {
    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{}}"""));

    assertNull(response.callUrl());
    assertNull(response.createdAt());
    assertNull(response.creator());
    assertEquals(List.of(), response.customFieldEntries());
    assertEquals(List.of(), response.durationMetrics());
    assertNull(response.externalIssueReference());
    assertFalse(response.hasDebrief());
    assertNull(response.id());
    assertEquals(List.of(), response.incidentRoleAssignments());
    assertNull(response.incidentStatus());
    assertEquals(List.of(), response.incidentTimestampValues());
    assertNull(response.incidentType());
    assertNull(response.mode());
    assertNull(response.name());
    assertNull(response.permalink());
    assertEquals(List.of(), response.postmortemDocumentIds());
    assertNull(response.postmortemDocumentUrl());
    assertNull(response.reference());
    assertNull(response.severity());
    assertNull(response.slackChannelId());
    assertNull(response.slackChannelName());
    assertNull(response.slackTeamId());
    assertNull(response.summary());
    assertNull(response.updatedAt());
    assertNull(response.visibility());
    assertNull(response.workloadMinutesLate());
    assertNull(response.workloadMinutesSleeping());
    assertNull(response.workloadMinutesTotal());
    assertNull(response.workloadMinutesWorking());
  }

  /// An incident created through this client is created by an API key, so `api_key` — not
  /// `user` — is the actor variant the create response actually carries.
  @Test
  void parseApiKeyCreator() {
    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{"creator":{"api_key":{"id":"key-1","name":"My test API key"}},"name":"after"}}"""));
    final var creator = response.creator();
    assertNull(creator.alert());
    assertNull(creator.user());
    assertNull(creator.workflow());
    assertEquals("key-1", creator.apiKey().id());
    assertEquals("My test API key", creator.apiKey().name());
    assertEquals("after", response.name());
  }

  @Test
  void parseAlertCreator() {
    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{"creator":{"alert":{"id":"alert-1","title":"PG::Error failed to connect"}},"name":"after"}}"""));
    final var creator = response.creator();
    assertNull(creator.apiKey());
    assertNull(creator.user());
    assertNull(creator.workflow());
    assertEquals("alert-1", creator.alert().id());
    assertEquals("PG::Error failed to connect", creator.alert().title());
    assertEquals("after", response.name());
  }

  @Test
  void parseWorkflowCreator() {
    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{"creator":{"workflow":{"id":"wf-1","name":"My little workflow"}},"name":"after"}}"""));
    final var creator = response.creator();
    assertNull(creator.alert());
    assertNull(creator.apiKey());
    assertNull(creator.user());
    assertEquals("wf-1", creator.workflow().id());
    assertEquals("My little workflow", creator.workflow().name());
    assertEquals("after", response.name());
  }

  @Test
  void parseCreatorWithNoKnownVariant() {
    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{"creator":{"future_actor":{"id":"x"}},"name":"after"}}"""));
    final var creator = response.creator();
    assertNull(creator.alert());
    assertNull(creator.apiKey());
    assertNull(creator.user());
    assertNull(creator.workflow());
    assertEquals("after", response.name());
  }

  @Test
  void parseCustomFieldValueVariants() {
    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{"custom_field_entries":[{
          "custom_field":{"id":"cf-1","name":"Owner","field_type":"single_select"},
          "values":[
            {"value_option":{"custom_field_id":"cf-1","id":"opt-1","sort_key":10,"value":"Product","unknown":1}},
            {"value_catalog_entry":{"aliases":["lawrence@example.com","lawrence"],\
        "external_id":"ext-1","id":"cat-1","name":"Primary On-call","unknown":2}},
            {"value_link":"https://example.com/","value_numeric":"123.456","value_text":"text","unknown":3}
          ]}],"name":"after"}}"""));

    final var entries = List.copyOf(response.customFieldEntries());
    assertEquals(1, entries.size());
    final var values = List.copyOf(entries.getFirst().values());
    assertEquals(3, values.size());

    final var option = values.getFirst().valueOption();
    assertEquals("cf-1", option.customFieldId());
    assertEquals("opt-1", option.id());
    assertEquals(10L, option.sortKey());
    assertEquals("Product", option.value());
    assertNull(values.getFirst().valueCatalogEntry());
    assertNull(values.getFirst().valueText());

    final var catalogEntry = values.get(1).valueCatalogEntry();
    assertEquals(List.of("lawrence@example.com", "lawrence"), List.copyOf(catalogEntry.aliases()));
    assertEquals("ext-1", catalogEntry.externalId());
    assertEquals("cat-1", catalogEntry.id());
    assertEquals("Primary On-call", catalogEntry.name());
    assertNull(values.get(1).valueOption());

    assertEquals("https://example.com/", values.get(2).valueLink());
    assertEquals("123.456", values.get(2).valueNumeric());
    assertEquals("text", values.get(2).valueText());

    assertEquals("after", response.name());
  }

  @Test
  void parseCatalogEntryWithoutAliases() {
    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{"custom_field_entries":[{"custom_field":{"id":"cf-1"},
          "values":[{"value_catalog_entry":{"id":"cat-1","name":"Primary On-call"}}]}],"name":"after"}}"""));
    final var values = List.copyOf(List.copyOf(response.customFieldEntries()).getFirst().values());
    assertEquals(List.of(), List.copyOf(values.getFirst().valueCatalogEntry().aliases()));
    assertEquals("after", response.name());
  }

  /// An entry with no `values` key at all is a different path from `"values":[]` — the
  /// accumulator is never allocated, so the absent-collection default is what runs.
  @Test
  void parseCustomFieldEntryWithoutValues() {
    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{"custom_field_entries":[{"custom_field":{"id":"cf-5","name":"Owner"}}],"name":"after"}}"""));
    final var entries = List.copyOf(response.customFieldEntries());
    assertEquals(1, entries.size());
    assertEquals("cf-5", entries.getFirst().customFieldId());
    assertEquals(List.of(), List.copyOf(entries.getFirst().values()));
    assertEquals("after", response.name());
  }

  @Test
  void parseEmptyCustomFieldValuesArray() {
    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{"custom_field_entries":[{"custom_field":{"id":"cf-2"},"values":[]}],"name":"after"}}"""));
    final var entries = List.copyOf(response.customFieldEntries());
    assertEquals(1, entries.size());
    assertEquals("cf-2", entries.getFirst().customFieldId());
    assertEquals(List.of(), List.copyOf(entries.getFirst().values()));
    assertEquals("after", response.name());
  }

  @Test
  void parseEmptyCustomFieldValuesFollowedByAnotherField() {
    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{"custom_field_entries":[{"custom_field":{"id":"cf-4"},"values":[],"tail":"t"}],"name":"after-tail"}}"""));
    final var entries = List.copyOf(response.customFieldEntries());
    assertEquals(1, entries.size());
    assertEquals("cf-4", entries.getFirst().customFieldId());
    assertEquals(List.of(), List.copyOf(entries.getFirst().values()));
    assertEquals("after-tail", response.name());
  }

  @Test
  void parseNullCustomFieldValues() {
    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{"custom_field_entries":[{"custom_field":{"id":"cf-3"},"values":null}],"name":"after-null"}}"""));
    final var entries = List.copyOf(response.customFieldEntries());
    assertEquals(1, entries.size());
    assertEquals("cf-3", entries.getFirst().customFieldId());
    assertEquals(List.of(), List.copyOf(entries.getFirst().values()));
    assertEquals("after-null", response.name());
  }

  @Test
  void parseEmptyPostmortemDocumentIds() {
    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{"postmortem_document_ids":[],"name":"after"}}"""));
    assertEquals(List.of(), List.copyOf(response.postmortemDocumentIds()));
    assertEquals("after", response.name());
  }

  @Test
  void parsePrivateVisibility() {
    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{"visibility":"private","mode":"retrospective","has_debrief":false}}"""));
    assertEquals(CreateIncidentResponse.Visibility.PRIVATE, response.visibility());
    assertEquals(CreateIncidentResponse.Mode.retrospective, response.mode());
    assertFalse(response.hasDebrief());
  }

  @Test
  void explicitNullsOnOptionalFieldsReadAsAbsentRatherThanAbortingTheParse() {
    // no schema in the Create Incident closure is marked nullable, but a server that
    // sends "field": null for an absent optional must not take down the whole response:
    // json-iterator's readBoolean/readLong/readDouble all throw on a JSON null, and
    // OffsetDateTime.parse would NPE on one.
    final var response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{"id":"inc-1","has_debrief":null,\
        "workload_minutes_late":null,"workload_minutes_sleeping":null,\
        "workload_minutes_total":null,"workload_minutes_working":null,\
        "duration_metrics":[{"duration_metric":{"id":"dm-1","name":"Lasted"},\
        "value_seconds":null,"status":"calculating"}],\
        "incident_timestamp_values":[{"incident_timestamp":{"id":"ts-1","name":"Reported"},\
        "value":{"value":null}}]}}"""));

    assertEquals("inc-1", response.id());
    assertFalse(response.hasDebrief(), "a null boolean reads as its absent default");
    assertNull(response.workloadMinutesLate());
    assertNull(response.workloadMinutesSleeping());
    assertNull(response.workloadMinutesTotal());
    assertNull(response.workloadMinutesWorking());

    final var metric = List.copyOf(response.durationMetrics()).getFirst();
    assertEquals("dm-1", metric.durationMetricId());
    assertNull(metric.valueSeconds(), "a null value_seconds is absent, not zero");
    assertEquals("calculating", metric.status());

    final var timestamp = List.copyOf(response.incidentTimestampValues()).getFirst();
    assertEquals("ts-1", timestamp.timestampId());
    assertNull(timestamp.value());
  }
}
