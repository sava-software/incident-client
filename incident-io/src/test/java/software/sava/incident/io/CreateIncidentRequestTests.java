package software.sava.incident.io;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class CreateIncidentRequestTests {

  @Test
  void accessors() {
    final var roleAssignments = List.of(
        new CreateIncidentRequest.IncidentRoleAssignment(
            "role-1", CreateIncidentRequest.UserReference.byId("assignee-1"))
    );
    final var customFieldValues = Map.of("field-1", "val-1");
    final var request = CreateIncidentRequest.requestBuilder()
        .idempotencyKey("idem-1")
        .name("name-1")
        .summary("summary-1")
        .incidentTypeId("type-1")
        .incidentRoleAssignments(roleAssignments)
        .mode(CreateIncidentRequest.Mode.test)
        .severityId("severity-1")
        .statusId("status-1")
        .visibility(CreateIncidentRequest.Visibility.PRIVATE)
        .slackTeamId("slack-1")
        .customFieldValues(customFieldValues)
        .build();

    assertEquals("idem-1", request.idempotencyKey());
    assertEquals("name-1", request.name());
    assertEquals("summary-1", request.summary());
    assertEquals("type-1", request.incidentTypeId());
    assertEquals(roleAssignments, List.copyOf(request.incidentRoleAssignments()));
    assertEquals("test", request.mode());
    assertEquals("severity-1", request.severityId());
    assertEquals("status-1", request.statusId());
    assertEquals("private", request.visibility());
    assertEquals("slack-1", request.slackTeamId());
    assertEquals(customFieldValues, request.customFieldValues());
  }

  @Test
  void fullBodyMatchesTheV2Payload() {
    final var request = CreateIncidentRequest.requestBuilder()
        .idempotencyKey("idem-1")
        .name("name-1")
        .summary("summary-1")
        .incidentTypeId("type-1")
        .incidentRoleAssignments(List.of(
            new CreateIncidentRequest.IncidentRoleAssignment(
                "r-1", new CreateIncidentRequest.UserReference("a-1", "a@example.com", "U1"))
        ))
        .mode(CreateIncidentRequest.Mode.test)
        .severityId("severity-1")
        .statusId("status-1")
        .visibility(CreateIncidentRequest.Visibility.PRIVATE)
        .slackTeamId("slack-1")
        .customFieldValues(Map.of("field-1", "val-1"))
        .build();
    assertEquals("""
        {"idempotency_key":"idem-1","name":"name-1","summary":"summary-1","incident_type_id":"type-1",\
        "incident_role_assignments":[{"incident_role_id":"r-1","assignee":{"id":"a-1","email":"a@example.com",\
        "slack_user_id":"U1"}}],"mode":"test","severity_id":"severity-1","incident_status_id":"status-1",\
        "visibility":"private","slack_team_id":"slack-1",\
        "custom_field_entries":[{"custom_field_id":"field-1","values":[{"value_text":"val-1"}]}]}""", request.body()
    );
  }

  @Test
  void nullEnumSettersClearFields() {
    final var request = CreateIncidentRequest.requestBuilder()
        .mode((CreateIncidentRequest.Mode) null)
        .visibility((CreateIncidentRequest.Visibility) null)
        .build();
    assertNull(request.mode());
    assertNull(request.visibility());
    assertEquals("{}", request.body());
  }

  @Test
  void bodyWithOnlyRoleAssignments() {
    final var request = CreateIncidentRequest.requestBuilder()
        .incidentRoleAssignments(List.of(
            new CreateIncidentRequest.IncidentRoleAssignment("r-1", "a-1")
        ))
        .build();
    assertEquals("""
        {"incident_role_assignments":[{"incident_role_id":"r-1","assignee":{"id":"a-1"}}]}""", request.body()
    );
  }

  @Test
  void roleAssignmentReferenceVariants() {
    final var request = CreateIncidentRequest.requestBuilder()
        .incidentRoleAssignments(List.of(
            new CreateIncidentRequest.IncidentRoleAssignment(
                "r-1", CreateIncidentRequest.UserReference.byEmail("a@example.com")),
            new CreateIncidentRequest.IncidentRoleAssignment(
                "r-2", CreateIncidentRequest.UserReference.bySlackUserId("U2")),
            // no assignee at all, and an all-blank reference: both omit the object
            new CreateIncidentRequest.IncidentRoleAssignment("r-3", (CreateIncidentRequest.UserReference) null),
            new CreateIncidentRequest.IncidentRoleAssignment(
                "r-4", new CreateIncidentRequest.UserReference(null, " ", ""))
        ))
        .build();
    assertEquals("""
        {"incident_role_assignments":[\
        {"incident_role_id":"r-1","assignee":{"email":"a@example.com"}},\
        {"incident_role_id":"r-2","assignee":{"slack_user_id":"U2"}},\
        {"incident_role_id":"r-3"},\
        {"incident_role_id":"r-4"}]}""", request.body()
    );
  }

  @Test
  void bodyWithOnlyCustomFieldValues() {
    final var request = CreateIncidentRequest.requestBuilder()
        .customFieldValues(Map.of("k-1", "v-1"))
        .build();
    assertEquals("""
        {"custom_field_entries":[{"custom_field_id":"k-1","values":[{"value_text":"v-1"}]}]}""", request.body()
    );
  }

  @Test
  void bodyOmitsEmptyCollections() {
    final var request = CreateIncidentRequest.requestBuilder()
        .incidentRoleAssignments(List.of())
        .customFieldValues(Map.of())
        .build();
    assertEquals("{}", request.body());
  }

  @Test
  void bodyCommaSeparatesOptionalSections() {
    final var request = CreateIncidentRequest.requestBuilder()
        .name("name-1")
        .incidentRoleAssignments(List.of(
            new CreateIncidentRequest.IncidentRoleAssignment("r-1", "a-1")
        ))
        .customFieldValues(Map.of("k-1", "v-1"))
        .build();
    assertEquals("""
        {"name":"name-1","incident_role_assignments":[{"incident_role_id":"r-1","assignee":{"id":"a-1"}}],\
        "custom_field_entries":[{"custom_field_id":"k-1","values":[{"value_text":"v-1"}]}]}""", request.body()
    );
  }

  @Test
  void bodyEscapesSpecialCharacters() {
    final var request = CreateIncidentRequest.requestBuilder()
        .name("na\"me\nline")
        .summary("sum\\mary\tend")
        .build();
    assertEquals("""
        {"name":"na\\"me\\nline","summary":"sum\\\\mary\\tend"}""", request.body()
    );
  }
}
