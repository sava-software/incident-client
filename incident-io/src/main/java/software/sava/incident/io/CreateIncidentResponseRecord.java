package software.sava.incident.io;

import systems.comodal.jsoniter.CharBufferFunction;
import systems.comodal.jsoniter.FieldIndexPredicate;
import systems.comodal.jsoniter.FieldMatcher;
import systems.comodal.jsoniter.JsonIterator;

import java.time.OffsetDateTime;
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
                                           Collection<String> postmortemDocumentIds,
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
        "postmortem_document_ids",
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

    // `ActorV2` is a union: whichever of these is present names the creator
    private static final FieldMatcher ACTOR_FIELDS = FieldMatcher.of("alert", "api_key", "user", "workflow");
    private static final FieldMatcher USER_FIELDS = FieldMatcher.of("email", "id", "name", "role", "slack_user_id");
    private static final FieldMatcher ID_TITLE_FIELDS = FieldMatcher.of("id", "title");
    private static final FieldMatcher CUSTOM_FIELD_ENTRY_FIELDS = FieldMatcher.of("custom_field", "values");
    private static final FieldMatcher CUSTOM_FIELD_FIELDS = FieldMatcher.of("id", "name", "field_type");
    private static final FieldMatcher CUSTOM_FIELD_VALUE_FIELDS = FieldMatcher.of(
        "value_catalog_entry", "value_link", "value_numeric", "value_option", "value_text"
    );
    private static final FieldMatcher CUSTOM_FIELD_OPTION_FIELDS = FieldMatcher.of(
        "custom_field_id", "id", "sort_key", "value"
    );
    private static final FieldMatcher CATALOG_ENTRY_FIELDS = FieldMatcher.of(
        "aliases", "external_id", "id", "name"
    );
    private static final FieldMatcher DURATION_METRIC_ENTRY_FIELDS = FieldMatcher.of(
        "duration_metric", "value_seconds", "status"
    );
    private static final FieldMatcher ID_NAME_FIELDS = FieldMatcher.of("id", "name");
    private static final FieldMatcher EXTERNAL_ISSUE_FIELDS = FieldMatcher.of(
        "issue_name", "issue_permalink", "provider"
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
    private Collection<String> postmortemDocumentIds;
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
          postmortemDocumentIds == null ? List.of() : postmortemDocumentIds,
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
        case 3 -> customFieldEntries = ji.readList(this::parseCustomFieldEntryV2);
        case 4 -> durationMetrics = ji.readList(this::parseDurationMetricV2);
        case 5 -> externalIssueReference = parseExternalIssueReferenceV2(ji);
        case 6 -> {
          if (ji.notNull()) {
            hasDebrief = ji.readBoolean();
          }
        }
        case 7 -> id = ji.readString();
        case 8 -> incidentRoleAssignments = ji.readList(this::parseIncidentRoleAssignmentV2);
        case 9 -> incidentStatus = parseIncidentStatusV2(ji);
        case 10 -> incidentTimestampValues = ji.readList(this::parseIncidentTimestampWithValueV2);
        case 11 -> incidentType = parseIncidentTypeV2(ji);
        case 12 -> mode = ji.applyChars(MODE_PARSER);
        case 13 -> name = ji.readString();
        case 14 -> permalink = ji.readString();
        case 15 -> postmortemDocumentIds = ji.readList(JsonIterator::readString);
        case 16 -> postmortemDocumentUrl = ji.readString();
        case 17 -> reference = ji.readString();
        case 18 -> severity = parseSeverityV2(ji);
        case 19 -> slackChannelId = ji.readString();
        case 20 -> slackChannelName = ji.readString();
        case 21 -> slackTeamId = ji.readString();
        case 22 -> summary = ji.readString();
        case 23 -> updatedAt = OffsetDateTime.parse(ji.readString());
        case 24 -> visibility = ji.applyChars(VISIBILITY_PARSER);
        case 25 -> {
          if (ji.notNull()) {
            workloadMinutesLate = ji.readDouble();
          }
        }
        case 26 -> {
          if (ji.notNull()) {
            workloadMinutesSleeping = ji.readDouble();
          }
        }
        case 27 -> {
          if (ji.notNull()) {
            workloadMinutesTotal = ji.readDouble();
          }
        }
        case 28 -> {
          if (ji.notNull()) {
            workloadMinutesWorking = ji.readDouble();
          }
        }
        default -> ji.skip();
      }
      return true;
    }

    private ActorV2 parseActorV2(final JsonIterator ji) {
      final var p = new Object() {
        AlertActorV2 alert;
        ApiKeyActorV2 apiKey;
        UserV2 user;
        WorkflowActorV2 workflow;
      };
      ji.testObject(ACTOR_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> {
            final var alert = parseIdTitle(ji1);
            p.alert = new AlertActorV2(alert.id, alert.name);
          }
          case 1 -> {
            final var apiKey = parseIdName(ji1);
            p.apiKey = new ApiKeyActorV2(apiKey.id, apiKey.name);
          }
          case 2 -> p.user = parseUserV2(ji1);
          case 3 -> {
            final var workflow = parseIdName(ji1);
            p.workflow = new WorkflowActorV2(workflow.id, workflow.name);
          }
          default -> ji1.skip();
        }
        return true;
      });
      return new ActorV2(p.alert, p.apiKey, p.user, p.workflow);
    }

    private UserV2 parseUserV2(final JsonIterator ji) {
      final var p = new Object() {
        String email, id, name, role, slackUserId;
      };
      ji.testObject(USER_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> p.email = ji1.readString();
          case 1 -> p.id = ji1.readString();
          case 2 -> p.name = ji1.readString();
          case 3 -> p.role = ji1.readString();
          case 4 -> p.slackUserId = ji1.readString();
          default -> ji1.skip();
        }
        return true;
      });
      return new UserV2(p.email, p.id, p.name, p.role, p.slackUserId);
    }

    /// Shared holder for the `{id, name}` and `{id, title}` actor shapes.
    private static final class IdName {

      private String id;
      private String name;
    }

    private IdName parseIdName(final JsonIterator ji) {
      final var p = new IdName();
      ji.testObject(ID_NAME_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> p.id = ji1.readString();
          case 1 -> p.name = ji1.readString();
          default -> ji1.skip();
        }
        return true;
      });
      return p;
    }

    private IdName parseIdTitle(final JsonIterator ji) {
      final var p = new IdName();
      ji.testObject(ID_TITLE_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> p.id = ji1.readString();
          case 1 -> p.name = ji1.readString();
          default -> ji1.skip();
        }
        return true;
      });
      return p;
    }

    private CustomFieldEntryV2 parseCustomFieldEntryV2(final JsonIterator ji) {
      final var p = new Object() {
        String customFieldId, customFieldName, customFieldType;
        Collection<CustomFieldValueV2> values;
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
          // a multi_select field carries one value per selected option
          case 1 -> p.values = ji1.readList(this::parseCustomFieldValueV2);
          default -> ji1.skip();
        }
        return true;
      });
      return new CustomFieldEntryV2(
          p.customFieldId,
          p.customFieldName,
          p.customFieldType,
          p.values == null ? List.of() : p.values
      );
    }

    private CustomFieldValueV2 parseCustomFieldValueV2(final JsonIterator ji) {
      final var p = new Object() {
        EmbeddedCatalogEntryV2 valueCatalogEntry;
        String valueLink, valueNumeric, valueText;
        CustomFieldOptionV2 valueOption;
      };
      ji.testObject(CUSTOM_FIELD_VALUE_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> p.valueCatalogEntry = parseEmbeddedCatalogEntryV2(ji1);
          case 1 -> p.valueLink = ji1.readString();
          case 2 -> p.valueNumeric = ji1.readString();
          case 3 -> p.valueOption = parseCustomFieldOptionV2(ji1);
          case 4 -> p.valueText = ji1.readString();
          default -> ji1.skip();
        }
        return true;
      });
      return new CustomFieldValueV2(
          p.valueCatalogEntry, p.valueLink, p.valueNumeric, p.valueOption, p.valueText
      );
    }

    private CustomFieldOptionV2 parseCustomFieldOptionV2(final JsonIterator ji) {
      final var p = new Object() {
        String customFieldId, id, value;
        long sortKey;
      };
      ji.testObject(CUSTOM_FIELD_OPTION_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> p.customFieldId = ji1.readString();
          case 1 -> p.id = ji1.readString();
          case 2 -> p.sortKey = ji1.readLong();
          case 3 -> p.value = ji1.readString();
          default -> ji1.skip();
        }
        return true;
      });
      return new CustomFieldOptionV2(p.customFieldId, p.id, p.sortKey, p.value);
    }

    private EmbeddedCatalogEntryV2 parseEmbeddedCatalogEntryV2(final JsonIterator ji) {
      final var p = new Object() {
        Collection<String> aliases;
        String externalId, id, name;
      };
      ji.testObject(CATALOG_ENTRY_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> p.aliases = ji1.readList(JsonIterator::readString);
          case 1 -> p.externalId = ji1.readString();
          case 2 -> p.id = ji1.readString();
          case 3 -> p.name = ji1.readString();
          default -> ji1.skip();
        }
        return true;
      });
      return new EmbeddedCatalogEntryV2(
          p.aliases == null ? List.of() : p.aliases, p.externalId, p.id, p.name
      );
    }

    private IncidentDurationMetricWithValueV2 parseDurationMetricV2(final JsonIterator ji) {
      final var p = new Object() {
        String id, name, status;
        Long value;
      };
      ji.testObject(DURATION_METRIC_ENTRY_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> {
            final var metric = parseIdName(ji1);
            p.id = metric.id;
            p.name = metric.name;
          }
          case 1 -> {
            if (ji1.notNull()) {
              p.value = ji1.readLong();
            }
          }
          case 2 -> p.status = ji1.readString();
          default -> ji1.skip();
        }
        return true;
      });
      return new IncidentDurationMetricWithValueV2(p.id, p.name, p.value, p.status);
    }

    private ExternalIssueReferenceV2 parseExternalIssueReferenceV2(final JsonIterator ji) {
      final var p = new Object() {
        String name, permalink, provider;
      };
      ji.testObject(EXTERNAL_ISSUE_FIELDS, (field, ji1) -> {
        switch (field) {
          case 0 -> p.name = ji1.readString();
          case 1 -> p.permalink = ji1.readString();
          case 2 -> p.provider = ji1.readString();
          default -> ji1.skip();
        }
        return true;
      });
      return new ExternalIssueReferenceV2(p.name, p.permalink, p.provider);
    }

    private IncidentRoleAssignmentV2 parseIncidentRoleAssignmentV2(final JsonIterator ji) {
      final var p = new Object() {
        UserV2 assignee;
        IncidentRoleV2 role;
      };
      ji.testObject(ROLE_ASSIGNMENT_FIELDS, (field, ji1) -> {
        switch (field) {
          // the assignee is a UserV2, not the ActorV2 union the creator is
          case 0 -> p.assignee = parseUserV2(ji1);
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
          case 0 -> {
            final var timestamp = parseIdName(ji1);
            p.id = timestamp.id;
            p.name = timestamp.name;
          }
          case 1 -> ji1.testObject(VALUE_FIELDS, (valueField, ji2) -> {
            if (valueField == 0) {
              p.value = ji2.readOrNull(j -> OffsetDateTime.parse(j.readString()));
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
