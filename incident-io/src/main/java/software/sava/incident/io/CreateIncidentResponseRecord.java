package software.sava.incident.io;

import systems.comodal.jsoniter.CharBufferFunction;
import systems.comodal.jsoniter.FieldIndexPredicate;
import systems.comodal.jsoniter.FieldMatcher;
import systems.comodal.jsoniter.JsonIterator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record CreateIncidentResponseRecord(String callUrl,
                                           OffsetDateTime createdAt,
                                           ActorV2 creator,
                                           Collection<CustomFieldEntryV2> customFieldEntries,
                                           Collection<IncidentDurationMetricWithValueV2> durationMetrics,
                                           ExternalIssueReferenceV2 externalIssueReference,
                                           boolean hasDebrief,
                                           String id,
                                           Collection<IncidentRoleAssignmentV2> incidentRoleAssignments,
                                           IncidentStatusV2 incidentStatus,
                                           Collection<IncidentTimestampWithValueV2> incidentTimestampValues,
                                           IncidentTypeV2 incidentType,
                                           Mode mode,
                                           String name,
                                           String permalink,
                                           String postmortemDocumentUrl,
                                           String reference,
                                           SeverityV2 severity,
                                           String slackChannelId,
                                           String slackChannelName,
                                           String slackTeamId,
                                           String summary,
                                           OffsetDateTime updatedAt,
                                           Visibility visibility,
                                           Double workloadMinutesLate,
                                           Double workloadMinutesSleeping,
                                           Double workloadMinutesTotal,
                                           Double workloadMinutesWorking) implements CreateIncidentResponse {

  private static final FieldMatcher ENVELOPE_FIELDS = FieldMatcher.of("incident");

  public static CreateIncidentResponseRecord parse(final JsonIterator ji) {
    final var parser = new Parser();
    ji.testObject(ENVELOPE_FIELDS, (fieldIndex, ji1) -> {
      if (fieldIndex == 0) {
        ji1.testObject(Parser.FIELDS, parser);
      } else {
        ji1.skip();
      }
      return true;
    });
    return parser.create();
  }

  private static final class Parser implements FieldIndexPredicate {

    static final FieldMatcher FIELDS = FieldMatcher.of(
        "call_url",
        "created_at",
        "creator",
        "custom_field_entries",
        "duration_metrics",
        "external_issue_reference",
        "has_debrief",
        "id",
        "incident_role_assignments",
        "incident_status",
        "incident_timestamp_values",
        "incident_type",
        "mode",
        "name",
        "permalink",
        "postmortem_document_url",
        "reference",
        "severity",
        "slack_channel_id",
        "slack_channel_name",
        "slack_team_id",
        "summary",
        "updated_at",
        "visibility",
        "workload_minutes_late",
        "workload_minutes_sleeping",
        "workload_minutes_total",
        "workload_minutes_working"
    );

    // the inner "user" object holds the same first four fields; the outer lambda alone
    // handles index 4
    private static final FieldMatcher ACTOR_FIELDS = FieldMatcher.of(
        "email", "id", "name", "slack_user_id", "user"
    );
    private static final FieldMatcher CUSTOM_FIELD_ENTRY_FIELDS = FieldMatcher.of("custom_field", "values");
    private static final FieldMatcher CUSTOM_FIELD_FIELDS = FieldMatcher.of("id", "name", "field_type");
    private static final FieldMatcher VALUE_TEXT_FIELDS = FieldMatcher.of("value_text");
    private static final FieldMatcher DURATION_METRIC_ENTRY_FIELDS = FieldMatcher.of("duration_metric", "value_seconds");
    private static final FieldMatcher ID_NAME_FIELDS = FieldMatcher.of("id", "name");
    private static final FieldMatcher EXTERNAL_ISSUE_FIELDS = FieldMatcher.of(
        "issue_id", "issue_reference", "issue_name", "issue_permalink", "provider"
    );
    private static final FieldMatcher ROLE_ASSIGNMENT_FIELDS = FieldMatcher.of("assignee", "role");
    private static final FieldMatcher ROLE_FIELDS = FieldMatcher.of("id", "name", "description", "role_type");
    private static final FieldMatcher STATUS_FIELDS = FieldMatcher.of("id", "name", "description", "category");
    private static final FieldMatcher ID_NAME_DESCRIPTION_FIELDS = FieldMatcher.of("id", "name", "description");
    private static final FieldMatcher TIMESTAMP_ENTRY_FIELDS = FieldMatcher.of("incident_timestamp", "value");
    private static final FieldMatcher VALUE_FIELDS = FieldMatcher.of("value");

    // unknown wire values resolve to null rather than throwing, matching the parser's
    // skip-unknown-fields policy
    private static final CharBufferFunction<Mode> MODE_PARSER = FieldMatcher.enumMatcher(Mode.values());
    private static final CharBufferFunction<Visibility> VISIBILITY_PARSER =
        FieldMatcher.enumMatcherIgnoreCase(Visibility.values());

    private String callUrl;
    private OffsetDateTime createdAt;
    private ActorV2 creator;
    private Collection<CustomFieldEntryV2> customFieldEntries;
    private Collection<IncidentDurationMetricWithValueV2> durationMetrics;
    private ExternalIssueReferenceV2 externalIssueReference;
    private boolean hasDebrief;
    private String id;
    private Collection<IncidentRoleAssignmentV2> incidentRoleAssignments;
    private IncidentStatusV2 incidentStatus;
    private Collection<IncidentTimestampWithValueV2> incidentTimestampValues;
    private IncidentTypeV2 incidentType;
    private Mode mode;
    private String name;
    private String permalink;
    private String postmortemDocumentUrl;
    private String reference;
    private SeverityV2 severity;
    private String slackChannelId;
    private String slackChannelName;
    private String slackTeamId;
    private String summary;
    private OffsetDateTime updatedAt;
    private Visibility visibility;
    private Double workloadMinutesLate;
    private Double workloadMinutesSleeping;
    private Double workloadMinutesTotal;
    private Double workloadMinutesWorking;

    private CreateIncidentResponseRecord create() {
      return new CreateIncidentResponseRecord(
          callUrl,
          createdAt,
          creator,
          customFieldEntries == null ? List.of() : customFieldEntries,
          durationMetrics == null ? List.of() : durationMetrics,
          externalIssueReference,
          hasDebrief,
          id,
          incidentRoleAssignments == null ? List.of() : incidentRoleAssignments,
          incidentStatus,
          incidentTimestampValues == null ? List.of() : incidentTimestampValues,
          incidentType,
          mode,
          name,
          permalink,
          postmortemDocumentUrl,
          reference,
          severity,
          slackChannelId,
          slackChannelName,
          slackTeamId,
          summary,
          updatedAt,
          visibility,
          workloadMinutesLate,
          workloadMinutesSleeping,
          workloadMinutesTotal,
          workloadMinutesWorking
      );
    }

    @Override
    public boolean test(final int fieldIndex, final JsonIterator ji) {
      switch (fieldIndex) {
        case 0 -> callUrl = ji.readString();
        case 1 -> createdAt = OffsetDateTime.parse(ji.readString());
        case 2 -> creator = parseActorV2(ji);
        case 3 -> {
          customFieldEntries = new ArrayList<>();
          while (ji.readArray()) {
            customFieldEntries.add(parseCustomFieldEntryV2(ji));
          }
        }
        case 4 -> {
          durationMetrics = new ArrayList<>();
          while (ji.readArray()) {
            durationMetrics.add(parseDurationMetricV2(ji));
          }
        }
        case 5 -> externalIssueReference = parseExternalIssueReferenceV2(ji);
        case 6 -> hasDebrief = ji.readBoolean();
        case 7 -> id = ji.readString();
        case 8 -> {
          incidentRoleAssignments = new ArrayList<>();
          while (ji.readArray()) {
            incidentRoleAssignments.add(parseIncidentRoleAssignmentV2(ji));
          }
        }
        case 9 -> incidentStatus = parseIncidentStatusV2(ji);
        case 10 -> {
          incidentTimestampValues = new ArrayList<>();
          while (ji.readArray()) {
            incidentTimestampValues.add(parseIncidentTimestampWithValueV2(ji));
          }
        }
        case 11 -> incidentType = parseIncidentTypeV2(ji);
        case 12 -> mode = ji.applyChars(MODE_PARSER);
        case 13 -> name = ji.readString();
        case 14 -> permalink = ji.readString();
        case 15 -> postmortemDocumentUrl = ji.readString();
        case 16 -> reference = ji.readString();
        case 17 -> severity = parseSeverityV2(ji);
        case 18 -> slackChannelId = ji.readString();
        case 19 -> slackChannelName = ji.readString();
        case 20 -> slackTeamId = ji.readString();
        case 21 -> summary = ji.readString();
        case 22 -> updatedAt = OffsetDateTime.parse(ji.readString());
        case 23 -> visibility = ji.applyChars(VISIBILITY_PARSER);
        case 24 -> workloadMinutesLate = ji.readDouble();
        case 25 -> workloadMinutesSleeping = ji.readDouble();
        case 26 -> workloadMinutesTotal = ji.readDouble();
        case 27 -> workloadMinutesWorking = ji.readDouble();
        default -> ji.skip();
      }
      return true;
    }

    private ActorV2 parseActorV2(final JsonIterator ji) {
      final var p = new Object() {
        String email, id, name, slackUserId;
      };
      ji.testObject(ACTOR_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> p.email = ji1.readString();
          case 1 -> p.id = ji1.readString();
          case 2 -> p.name = ji1.readString();
          case 3 -> p.slackUserId = ji1.readString();
          case 4 -> ji1.testObject(ACTOR_FIELDS, (userField, ji2) -> {
            switch (userField) {
              case 0 -> p.email = ji2.readString();
              case 1 -> p.id = ji2.readString();
              case 2 -> p.name = ji2.readString();
              case 3 -> p.slackUserId = ji2.readString();
              default -> ji2.skip();
            }
            return true;
          });
          default -> ji1.skip();
        }
        return true;
      });
      return new ActorV2(p.email, p.id, p.name, p.slackUserId);
    }

    private CustomFieldEntryV2 parseCustomFieldEntryV2(final JsonIterator ji) {
      final var p = new Object() {
        String customFieldId, customFieldName, customFieldType;
        Object value;
      };
      ji.testObject(CUSTOM_FIELD_ENTRY_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> ji1.testObject(CUSTOM_FIELD_FIELDS, (customField, ji2) -> {
            switch (customField) {
              case 0 -> p.customFieldId = ji2.readString();
              case 1 -> p.customFieldName = ji2.readString();
              case 2 -> p.customFieldType = ji2.readString();
              default -> ji2.skip();
            }
            return true;
          });
          case 1 -> {
            // V2 response for values is an array of objects
            if (ji1.readArray()) {
              ji1.testObject(VALUE_TEXT_FIELDS, (valueField, ji2) -> {
                if (valueField == 0) {
                  p.value = ji2.readString();
                } else {
                  ji2.skip();
                }
                return true;
              });
              ji1.skipRestOfArray();
            }
          }
          default -> ji1.skip();
        }
        return true;
      });
      return new CustomFieldEntryV2(p.customFieldId, p.customFieldName, p.customFieldType, p.value);
    }

    private IncidentDurationMetricWithValueV2 parseDurationMetricV2(final JsonIterator ji) {
      final var p = new Object() {
        String id, name;
        Long value;
      };
      ji.testObject(DURATION_METRIC_ENTRY_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> ji1.testObject(ID_NAME_FIELDS, (metricField, ji2) -> {
            switch (metricField) {
              case 0 -> p.id = ji2.readString();
              case 1 -> p.name = ji2.readString();
              default -> ji2.skip();
            }
            return true;
          });
          case 1 -> p.value = ji1.readLong();
          default -> ji1.skip();
        }
        return true;
      });
      return new IncidentDurationMetricWithValueV2(p.id, p.name, p.value);
    }

    private ExternalIssueReferenceV2 parseExternalIssueReferenceV2(final JsonIterator ji) {
      final var p = new Object() {
        String id, ref, title, url, provider;
      };
      ji.testObject(EXTERNAL_ISSUE_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> p.id = ji1.readString();
          case 1 -> p.ref = ji1.readString();
          case 2 -> p.title = ji1.readString();
          case 3 -> p.url = ji1.readString();
          case 4 -> p.provider = ji1.readString();
          default -> ji1.skip();
        }
        return true;
      });
      return new ExternalIssueReferenceV2(p.id, p.ref, p.title, p.url, p.provider);
    }

    private IncidentRoleAssignmentV2 parseIncidentRoleAssignmentV2(final JsonIterator ji) {
      final var p = new Object() {
        ActorV2 assignee;
        IncidentRoleV2 role;
      };
      ji.testObject(ROLE_ASSIGNMENT_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> p.assignee = parseActorV2(ji1);
          case 1 -> p.role = parseIncidentRoleV2(ji1);
          default -> ji1.skip();
        }
        return true;
      });
      return new IncidentRoleAssignmentV2(p.assignee, p.role);
    }

    private IncidentRoleV2 parseIncidentRoleV2(final JsonIterator ji) {
      final var p = new Object() {
        String id, name, description, roleType;
      };
      ji.testObject(ROLE_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> p.id = ji1.readString();
          case 1 -> p.name = ji1.readString();
          case 2 -> p.description = ji1.readString();
          case 3 -> p.roleType = ji1.readString();
          default -> ji1.skip();
        }
        return true;
      });
      return new IncidentRoleV2(p.id, p.name, p.description, p.roleType);
    }

    private IncidentStatusV2 parseIncidentStatusV2(final JsonIterator ji) {
      final var p = new Object() {
        String id, name, description, category;
      };
      ji.testObject(STATUS_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> p.id = ji1.readString();
          case 1 -> p.name = ji1.readString();
          case 2 -> p.description = ji1.readString();
          case 3 -> p.category = ji1.readString();
          default -> ji1.skip();
        }
        return true;
      });
      return new IncidentStatusV2(p.id, p.name, p.description, p.category);
    }

    private IncidentTimestampWithValueV2 parseIncidentTimestampWithValueV2(final JsonIterator ji) {
      final var p = new Object() {
        String id, name;
        OffsetDateTime value;
      };
      ji.testObject(TIMESTAMP_ENTRY_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> ji1.testObject(ID_NAME_FIELDS, (timestampField, ji2) -> {
            switch (timestampField) {
              case 0 -> p.id = ji2.readString();
              case 1 -> p.name = ji2.readString();
              default -> ji2.skip();
            }
            return true;
          });
          case 1 -> ji1.testObject(VALUE_FIELDS, (valueField, ji2) -> {
            if (valueField == 0) {
              p.value = OffsetDateTime.parse(ji2.readString());
            } else {
              ji2.skip();
            }
            return true;
          });
          default -> ji1.skip();
        }
        return true;
      });
      return new IncidentTimestampWithValueV2(p.id, p.name, p.value);
    }

    private IncidentTypeV2 parseIncidentTypeV2(final JsonIterator ji) {
      final var p = new Object() {
        String id, name, description;
      };
      ji.testObject(ID_NAME_DESCRIPTION_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> p.id = ji1.readString();
          case 1 -> p.name = ji1.readString();
          case 2 -> p.description = ji1.readString();
          default -> ji1.skip();
        }
        return true;
      });
      return new IncidentTypeV2(p.id, p.name, p.description);
    }

    private SeverityV2 parseSeverityV2(final JsonIterator ji) {
      final var p = new Object() {
        String id, name, description;
      };
      ji.testObject(ID_NAME_DESCRIPTION_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> p.id = ji1.readString();
          case 1 -> p.name = ji1.readString();
          case 2 -> p.description = ji1.readString();
          default -> ji1.skip();
        }
        return true;
      });
      return new SeverityV2(p.id, p.name, p.description);
    }
  }
}
