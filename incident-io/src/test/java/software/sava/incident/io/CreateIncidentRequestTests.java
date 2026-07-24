package software.sava.incident.io;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class CreateIncidentRequestTests {

  @Test
  void accessors() {
    final var roleAssignments = List.of(
        new CreateIncidentRequest.IncidentRoleAssignment("role-1", "assignee-1")
    );
    final var customFieldValues = Map.of("field-1", "val-1");
    final var request = CreateIncidentRequest.requestBuilder()
        .idempotencyKey("idem-1")
        .name("name-1")
        .summary("summary-1")
        .description("description-1")
        .incidentTypeId("type-1")
        .incidentRoleAssignments(roleAssignments)
        .mode(CreateIncidentRequest.Mode.test)
        .priorityId("priority-1")
        .severityId("severity-1")
        .statusId("status-1")
        .visibility(CreateIncidentRequest.Visibility.PRIVATE)
        .slackTeamId("slack-1")
        .creatorOutOfHours(Boolean.TRUE)
        .customFieldValues(customFieldValues)
        .build();

    assertEquals("idem-1", request.idempotencyKey());
    assertEquals("name-1", request.name());
    assertEquals("summary-1", request.summary());
    assertEquals("description-1", request.description());
    assertEquals("type-1", request.incidentTypeId());
    assertEquals(roleAssignments, List.copyOf(request.incidentRoleAssignments()));
    assertEquals("test", request.mode());
    assertEquals("priority-1", request.priorityId());
    assertEquals("severity-1", request.severityId());
    assertEquals("status-1", request.statusId());
    assertEquals("private", request.visibility());
    assertEquals("slack-1", request.slackTeamId());
    assertEquals(Boolean.TRUE, request.creatorOutOfHours());
    assertEquals(customFieldValues, request.customFieldValues());
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
        {"incident_role_assignments":[{"incident_role_id":"r-1","assignee_id":"a-1"}]}""", request.body()
    );
  }

  @Test
  void bodyWithOnlyCreatorOutOfHours() {
    final var request = CreateIncidentRequest.requestBuilder()
        .creatorOutOfHours(Boolean.FALSE)
        .build();
    assertEquals("""
        {"creator_out_of_hours":false}""", request.body()
    );
  }

  @Test
  void bodyWithOnlyCustomFieldValues() {
    final var request = CreateIncidentRequest.requestBuilder()
        .customFieldValues(Map.of("k-1", "v-1"))
        .build();
    assertEquals("""
        {"custom_field_values":{"k-1":[{"value":"v-1"}]}}""", request.body()
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
        .creatorOutOfHours(Boolean.FALSE)
        .customFieldValues(Map.of("k-1", "v-1"))
        .build();
    assertEquals(Boolean.FALSE, request.creatorOutOfHours());
    assertEquals("""
        {"name":"name-1","incident_role_assignments":[{"incident_role_id":"r-1","assignee_id":"a-1"}],"creator_out_of_hours":false,"custom_field_values":{"k-1":[{"value":"v-1"}]}}""", request.body()
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
