package software.sava.incident.io;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
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
    final var customFieldEntries = List.of(new CreateIncidentRequest.CustomFieldEntry("field-1", "val-1"));
    final var timestampValues = List.of(new CreateIncidentRequest.IncidentTimestampValue(
        "ts-1", OffsetDateTime.parse("2024-05-01T12:00:00Z")));
    final var retrospectiveOptions = new CreateIncidentRequest.RetrospectiveIncidentOptions(
        123L, "https://docs.example.com/pm", "C1");
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
        .slackChannelNameOverride("inc-1-database-down")
        .customFieldEntries(customFieldEntries)
        .incidentTimestampValues(timestampValues)
        .retrospectiveIncidentOptions(retrospectiveOptions)
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
    assertEquals("inc-1-database-down", request.slackChannelNameOverride());
    assertEquals(customFieldEntries, List.copyOf(request.customFieldEntries()));
    assertEquals(timestampValues, List.copyOf(request.incidentTimestampValues()));
    assertEquals(retrospectiveOptions, request.retrospectiveIncidentOptions());
  }

  @Test
  void builtCollectionsAreImmutable() {
    final var assignments = new ArrayList<>(List.of(
        new CreateIncidentRequest.IncidentRoleAssignment("r-1", "a-1"),
        new CreateIncidentRequest.IncidentRoleAssignment("r-2", "a-2")
    ));
    final var entries = new ArrayList<>(List.of(
        new CreateIncidentRequest.CustomFieldEntry("cf-1", "v-1"),
        new CreateIncidentRequest.CustomFieldEntry("cf-2", "v-2")
    ));
    final var timestampValues = new ArrayList<>(List.of(
        new CreateIncidentRequest.IncidentTimestampValue("ts-1", null),
        new CreateIncidentRequest.IncidentTimestampValue("ts-2", null)
    ));
    final var request = CreateIncidentRequest.requestBuilder()
        .incidentRoleAssignments(assignments)
        .customFieldEntries(entries)
        .incidentTimestampValues(timestampValues)
        .visibility("public")
        .idempotencyKey("idem-1")
        .build();

    // the builder copies, so mutating the caller's collections cannot reach the request
    assignments.clear();
    entries.clear();
    timestampValues.clear();
    assertEquals(2, request.incidentRoleAssignments().size());
    assertEquals(2, request.customFieldEntries().size());
    assertEquals(2, request.incidentTimestampValues().size());

    assertThrows(UnsupportedOperationException.class, () -> request.incidentRoleAssignments().clear());
    assertThrows(UnsupportedOperationException.class, () -> request.customFieldEntries().clear());
    assertThrows(UnsupportedOperationException.class, () -> request.incidentTimestampValues().clear());
    assertThrows(UnsupportedOperationException.class,
        () -> request.customFieldEntries().iterator().next().values().clear());
  }

  @Test
  void singletonCollectionsAreAlsoImmutable() {
    final var request = CreateIncidentRequest.requestBuilder()
        .incidentRoleAssignments(new ArrayList<>(List.of(
            new CreateIncidentRequest.IncidentRoleAssignment("r-1", "a-1"))))
        .customFieldEntries(new ArrayList<>(List.of(
            new CreateIncidentRequest.CustomFieldEntry("cf-1", "v-1"))))
        .incidentTimestampValues(new ArrayList<>(List.of(
            new CreateIncidentRequest.IncidentTimestampValue("ts-1", null))))
        .visibility("public")
        .idempotencyKey("idem-1")
        .build();

    assertThrows(UnsupportedOperationException.class, () -> request.incidentRoleAssignments().clear());
    assertThrows(UnsupportedOperationException.class, () -> request.customFieldEntries().clear());
    assertThrows(UnsupportedOperationException.class, () -> request.incidentTimestampValues().clear());
    assertThrows(UnsupportedOperationException.class,
        () -> request.customFieldEntries().iterator().next().values().clear());
  }

  @Test
  void customFieldEntryCopiesAndNormalizesItsValues() {
    final var values = new ArrayList<>(List.of(CreateIncidentRequest.CustomFieldValue.text("v-1")));
    final var entry = new CreateIncidentRequest.CustomFieldEntry("cf-1", values);
    values.clear();
    assertEquals(1, entry.values().size());
    assertThrows(UnsupportedOperationException.class, () -> entry.values().clear());

    assertEquals(List.of(), new CreateIncidentRequest.CustomFieldEntry("cf-2", (List<CreateIncidentRequest.CustomFieldValue>) null).values());
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
        .slackChannelNameOverride("inc-1-database-down")
        .customFieldValues(Map.of("field-1", "val-1"))
        .incidentTimestampValues(List.of(new CreateIncidentRequest.IncidentTimestampValue(
            "ts-1", OffsetDateTime.parse("2024-05-01T12:00:00Z"))))
        .retrospectiveIncidentOptions(new CreateIncidentRequest.RetrospectiveIncidentOptions(
            123L, "https://docs.example.com/pm", "C1"))
        .build();
    assertEquals("""
        {"idempotency_key":"idem-1","name":"name-1","summary":"summary-1","incident_type_id":"type-1",\
        "incident_role_assignments":[{"incident_role_id":"r-1","assignee":{"id":"a-1","email":"a@example.com",\
        "slack_user_id":"U1"}}],"mode":"test","severity_id":"severity-1","incident_status_id":"status-1",\
        "visibility":"private","slack_team_id":"slack-1","slack_channel_name_override":"inc-1-database-down",\
        "custom_field_entries":[{"custom_field_id":"field-1","values":[{"value_text":"val-1"}]}],\
        "incident_timestamp_values":[{"incident_timestamp_id":"ts-1","value":"2024-05-01T12:00:00Z"}],\
        "retrospective_incident_options":{"external_id":123,\
        "postmortem_document_url":"https://docs.example.com/pm","slack_channel_id":"C1"}}""", request.body()
    );
  }

  @Test
  void nullEnumSettersClearFields() {
    // the typed setters accept null as "clear", rather than NPEing or writing "null"
    final var request = CreateIncidentRequest.requestBuilder()
        .mode((CreateIncidentRequest.Mode) null)
        .visibility(CreateIncidentRequest.Visibility.PUBLIC)
        .idempotencyKey("idem-1")
        .build();
    assertNull(request.mode());
    assertEquals("""
        {"idempotency_key":"idem-1","visibility":"public"}""", request.body());

    // clearing visibility is legal on the builder but not buildable: the API requires it
    final var cleared = CreateIncidentRequest.requestBuilder()
        .visibility(CreateIncidentRequest.Visibility.PRIVATE)
        .visibility((CreateIncidentRequest.Visibility) null)
        .idempotencyKey("idem-1");
    assertThrows(IllegalStateException.class, cleared::build);
  }

  @Test
  void bodyWithOnlyRoleAssignments() {
    final var request = CreateIncidentRequest.requestBuilder()
        .incidentRoleAssignments(List.of(
            new CreateIncidentRequest.IncidentRoleAssignment("r-1", "a-1")
        ))
        .visibility("public")
        .idempotencyKey("idem-1")
        .build();
    assertEquals("""
        {"idempotency_key":"idem-1","incident_role_assignments":[{"incident_role_id":"r-1","assignee":{"id":"a-1"}}],"visibility":"public"}""", request.body()
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
        .visibility("public")
        .idempotencyKey("idem-1")
        .build();
    assertEquals("""
        {"idempotency_key":"idem-1","incident_role_assignments":[{"incident_role_id":"r-1","assignee":{"email":"a@example.com"}},{"incident_role_id":"r-2","assignee":{"slack_user_id":"U2"}},{"incident_role_id":"r-3"},{"incident_role_id":"r-4"}],"visibility":"public"}""", request.body()
    );
  }

  @Test
  void bodyWithOnlyCustomFieldValues() {
    final var request = CreateIncidentRequest.requestBuilder()
        .customFieldValues(Map.of("k-1", "v-1"))
        .visibility("public")
        .idempotencyKey("idem-1")
        .build();
    assertEquals("""
        {"idempotency_key":"idem-1","visibility":"public","custom_field_entries":[{"custom_field_id":"k-1","values":[{"value_text":"v-1"}]}]}""", request.body()
    );
  }

  @Test
  void customFieldValueVariants() {
    final var request = CreateIncidentRequest.requestBuilder()
        .customFieldEntries(List.of(
            new CreateIncidentRequest.CustomFieldEntry("cf-text",
                List.of(CreateIncidentRequest.CustomFieldValue.text("some text"))),
            // a multi_select takes one value per selected option
            new CreateIncidentRequest.CustomFieldEntry("cf-multi", List.of(
                CreateIncidentRequest.CustomFieldValue.optionId("opt-1"),
                CreateIncidentRequest.CustomFieldValue.optionId("opt-2"))),
            new CreateIncidentRequest.CustomFieldEntry("cf-catalog",
                List.of(CreateIncidentRequest.CustomFieldValue.catalogEntryId("cat-1"))),
            new CreateIncidentRequest.CustomFieldEntry("cf-link",
                List.of(CreateIncidentRequest.CustomFieldValue.link("https://example.com/"))),
            new CreateIncidentRequest.CustomFieldEntry("cf-numeric",
                List.of(CreateIncidentRequest.CustomFieldValue.numeric("123.456"))),
            new CreateIncidentRequest.CustomFieldEntry("cf-every-field",
                List.of(new CreateIncidentRequest.CustomFieldValue(
                    "v-1", "cat-2", "https://example.com/2", "7", "opt-3", "text-2")))
        ))
        .visibility("public")
        .idempotencyKey("idem-1")
        .build();
    assertEquals("""
        {"idempotency_key":"idem-1","visibility":"public","custom_field_entries":[{"custom_field_id":"cf-text","values":[{"value_text":"some text"}]},{"custom_field_id":"cf-multi","values":[{"value_option_id":"opt-1"},{"value_option_id":"opt-2"}]},{"custom_field_id":"cf-catalog","values":[{"value_catalog_entry_id":"cat-1"}]},{"custom_field_id":"cf-link","values":[{"value_link":"https://example.com/"}]},{"custom_field_id":"cf-numeric","values":[{"value_numeric":"123.456"}]},{"custom_field_id":"cf-every-field","values":[{"id":"v-1","value_catalog_entry_id":"cat-2","value_link":"https://example.com/2","value_numeric":"7","value_option_id":"opt-3","value_text":"text-2"}]}]}""", request.body()
    );
  }

  @Test
  void customFieldEntryKeepsAnEmptyValuesArrayAndDropsBlankValues() {
    final var request = CreateIncidentRequest.requestBuilder()
        .customFieldEntries(List.of(
            // an empty values array unsets the field, so it must survive serialization
            new CreateIncidentRequest.CustomFieldEntry("cf-unset", List.of()),
            // a value with nothing set carries nothing: dropped rather than emitted as {}
            new CreateIncidentRequest.CustomFieldEntry("cf-blank", List.of(
                CreateIncidentRequest.CustomFieldValue.text(" "),
                new CreateIncidentRequest.CustomFieldValue(null, "", " ", null, "", null))),
            new CreateIncidentRequest.CustomFieldEntry("cf-mixed", List.of(
                CreateIncidentRequest.CustomFieldValue.text(""),
                CreateIncidentRequest.CustomFieldValue.text("kept")))
        ))
        .visibility("public")
        .idempotencyKey("idem-1")
        .build();
    assertEquals("""
        {"idempotency_key":"idem-1","visibility":"public","custom_field_entries":[{"custom_field_id":"cf-unset","values":[]},{"custom_field_id":"cf-blank","values":[]},{"custom_field_id":"cf-mixed","values":[{"value_text":"kept"}]}]}""", request.body()
    );
  }

  @Test
  void incidentTimestampValueOmitsABlankTimestamp() {
    final var request = CreateIncidentRequest.requestBuilder()
        .incidentTimestampValues(List.of(
            new CreateIncidentRequest.IncidentTimestampValue("ts-1", null),
            new CreateIncidentRequest.IncidentTimestampValue(
                "ts-2", OffsetDateTime.parse("2024-05-01T12:00:00.123456+02:00"))
        ))
        .visibility("public")
        .idempotencyKey("idem-1")
        .build();
    assertEquals("""
        {"idempotency_key":"idem-1","visibility":"public","incident_timestamp_values":[{"incident_timestamp_id":"ts-1"},{"incident_timestamp_id":"ts-2","value":"2024-05-01T12:00:00.123456+02:00"}]}""", request.body()
    );
  }

  /// Regression for a `fuzzRequest` finding: a blank required id was silently dropped,
  /// which produced an entry the API can only reject with no trace of what was asked for.
  /// `custom_field_id` and `incident_timestamp_id` are required, so — like
  /// `incident_role_id` — they serialize whatever the caller gave, blank or null.
  @Test
  void blankRequiredIdsAreSerializedRatherThanDropped() {
    final var request = CreateIncidentRequest.requestBuilder()
        .customFieldEntries(List.of(
            new CreateIncidentRequest.CustomFieldEntry("", List.of(
                CreateIncidentRequest.CustomFieldValue.text("v-1"))),
            new CreateIncidentRequest.CustomFieldEntry(
                (String) null, List.<CreateIncidentRequest.CustomFieldValue>of())
        ))
        .incidentTimestampValues(List.of(
            new CreateIncidentRequest.IncidentTimestampValue(
                " ", OffsetDateTime.parse("2024-05-01T12:00:00Z")),
            new CreateIncidentRequest.IncidentTimestampValue(null, null)
        ))
        .incidentRoleAssignments(List.of(
            new CreateIncidentRequest.IncidentRoleAssignment("", "a-1")
        ))
        .visibility("public")
        .idempotencyKey("idem-1")
        .build();
    assertEquals("""
        {"idempotency_key":"idem-1","incident_role_assignments":[{"incident_role_id":"","assignee":{"id":"a-1"}}],"visibility":"public","custom_field_entries":[{"custom_field_id":"","values":[{"value_text":"v-1"}]},{"custom_field_id":"","values":[]}],"incident_timestamp_values":[{"incident_timestamp_id":" ","value":"2024-05-01T12:00:00Z"},{"incident_timestamp_id":""}]}""", request.body()
    );
  }

  @Test
  void retrospectiveOptionsOmitBlankMembers() {
    assertEquals("""
        {"idempotency_key":"idem-1","visibility":"public","retrospective_incident_options":{"external_id":0}}""",
        CreateIncidentRequest.requestBuilder()
            .retrospectiveIncidentOptions(new CreateIncidentRequest.RetrospectiveIncidentOptions(0L, null, " "))
            .visibility("public")
            .idempotencyKey("idem-1")
            .build().body()
    );
    assertEquals("""
        {"idempotency_key":"idem-1","visibility":"public","retrospective_incident_options":{"postmortem_document_url":"https://docs.example.com/pm"}}""",
        CreateIncidentRequest.requestBuilder()
            .retrospectiveIncidentOptions(new CreateIncidentRequest.RetrospectiveIncidentOptions(
                null, "https://docs.example.com/pm", null))
            .visibility("public")
            .idempotencyKey("idem-1")
            .build().body()
    );
    assertEquals("""
        {"idempotency_key":"idem-1","visibility":"public","retrospective_incident_options":{"slack_channel_id":"C1"}}""",
        CreateIncidentRequest.requestBuilder()
            .retrospectiveIncidentOptions(new CreateIncidentRequest.RetrospectiveIncidentOptions(null, "", "C1"))
            .visibility("public")
            .idempotencyKey("idem-1")
            .build().body()
    );
    // options with nothing set are omitted entirely, as is a null options object
    assertEquals("""
        {"idempotency_key":"idem-1","visibility":"public"}""",
        CreateIncidentRequest.requestBuilder()
            .retrospectiveIncidentOptions(new CreateIncidentRequest.RetrospectiveIncidentOptions(null, null, null))
            .visibility("public")
            .idempotencyKey("idem-1")
            .build().body()
    );
    assertEquals("""
        {"idempotency_key":"idem-1","visibility":"public"}""",
        CreateIncidentRequest.requestBuilder().retrospectiveIncidentOptions(null).visibility("public")
        .idempotencyKey("idem-1")
        .build().body()
    );
  }

  @Test
  void bodyOmitsEmptyCollections() {
    final var request = CreateIncidentRequest.requestBuilder()
        .incidentRoleAssignments(List.of())
        .customFieldValues(Map.of())
        .incidentTimestampValues(List.of())
        .visibility("public")
        .idempotencyKey("idem-1")
        .build();
    assertEquals("""
        {"idempotency_key":"idem-1","visibility":"public"}""", request.body());
  }

  @Test
  void nullCollectionSettersClearFields() {
    final var request = CreateIncidentRequest.requestBuilder()
        .customFieldValues(Map.of("k-1", "v-1"))
        .customFieldValues(null)
        .incidentRoleAssignments(null)
        .incidentTimestampValues(null)
        .visibility("public")
        .idempotencyKey("idem-1")
        .build();
    assertEquals(List.of(), List.copyOf(request.customFieldEntries()));
    assertEquals(List.of(), List.copyOf(request.incidentRoleAssignments()));
    assertEquals(List.of(), List.copyOf(request.incidentTimestampValues()));
    assertEquals("""
        {"idempotency_key":"idem-1","visibility":"public"}""", request.body());
  }

  @Test
  void customFieldValuesReplacesPreviouslySetEntries() {
    final var request = CreateIncidentRequest.requestBuilder()
        .customFieldEntries(List.of(new CreateIncidentRequest.CustomFieldEntry(
            "cf-1", List.of(CreateIncidentRequest.CustomFieldValue.optionId("opt-1")))))
        .customFieldValues(Map.of("cf-2", "v-2"))
        .visibility("public")
        .idempotencyKey("idem-1")
        .build();
    assertEquals("""
        {"idempotency_key":"idem-1","visibility":"public","custom_field_entries":[{"custom_field_id":"cf-2","values":[{"value_text":"v-2"}]}]}""", request.body()
    );
  }

  @Test
  void bodyCommaSeparatesOptionalSections() {
    final var request = CreateIncidentRequest.requestBuilder()
        .name("name-1")
        .incidentRoleAssignments(List.of(
            new CreateIncidentRequest.IncidentRoleAssignment("r-1", "a-1")
        ))
        .customFieldValues(Map.of("k-1", "v-1"))
        .incidentTimestampValues(List.of(new CreateIncidentRequest.IncidentTimestampValue(
            "ts-1", OffsetDateTime.parse("2024-05-01T12:00:00Z"))))
        .visibility("public")
        .idempotencyKey("idem-1")
        .build();
    assertEquals("""
        {"idempotency_key":"idem-1","name":"name-1","incident_role_assignments":[{"incident_role_id":"r-1","assignee":{"id":"a-1"}}],"visibility":"public","custom_field_entries":[{"custom_field_id":"k-1","values":[{"value_text":"v-1"}]}],"incident_timestamp_values":[{"incident_timestamp_id":"ts-1","value":"2024-05-01T12:00:00Z"}]}""",
        request.body()
    );
  }

  @Test
  void retrospectiveOptionsFollowEveryOtherSection() {
    final var request = CreateIncidentRequest.requestBuilder()
        .name("name-1")
        .retrospectiveIncidentOptions(new CreateIncidentRequest.RetrospectiveIncidentOptions(
            -1L, null, null))
        .visibility("public")
        .idempotencyKey("idem-1")
        .build();
    assertEquals("""
        {"idempotency_key":"idem-1","name":"name-1","visibility":"public","retrospective_incident_options":{"external_id":-1}}""", request.body()
    );
  }

  @Test
  void bodyEscapesSpecialCharacters() {
    final var request = CreateIncidentRequest.requestBuilder()
        .name("na\"me\nline")
        .summary("sum\\mary\tend")
        .slackChannelNameOverride("chan\"nel")
        .customFieldEntries(List.of(new CreateIncidentRequest.CustomFieldEntry(
            "cf\"1", List.of(CreateIncidentRequest.CustomFieldValue.text("va\nl")))))
        .incidentTimestampValues(List.of(new CreateIncidentRequest.IncidentTimestampValue("ts\"1", null)))
        .retrospectiveIncidentOptions(new CreateIncidentRequest.RetrospectiveIncidentOptions(
            null, "url\"1", "chan\ttab"))
        .visibility("public")
        .idempotencyKey("idem-1")
        .build();
    assertEquals("""
        {"idempotency_key":"idem-1","name":"na\\"me\\nline","summary":"sum\\\\mary\\tend","visibility":"public","slack_channel_name_override":"chan\\"nel",\
        "custom_field_entries":[{"custom_field_id":"cf\\"1","values":[{"value_text":"va\\nl"}]}],\
        "incident_timestamp_values":[{"incident_timestamp_id":"ts\\"1"}],\
        "retrospective_incident_options":{"postmortem_document_url":"url\\"1","slack_channel_id":"chan\\ttab"}}""",
        request.body()
    );
  }
}
