package software.sava.incident.io;

import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

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

  public static CreateIncidentResponseRecord parse(final JsonIterator ji) {
    final var parser = new Parser();
    ji.testObject((buf, offset, len, ji1) -> {
      if (fieldEquals("incident", buf, offset, len)) {
        ji1.testObject(parser);
      } else {
        ji1.skip();
      }
      return true;
    });
    return parser.create();
  }

  private static final class Parser implements FieldBufferPredicate {

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
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("call_url", buf, offset, len)) {
        callUrl = ji.readString();
      } else if (fieldEquals("created_at", buf, offset, len)) {
        createdAt = OffsetDateTime.parse(ji.readString());
      } else if (fieldEquals("creator", buf, offset, len)) {
        creator = parseActorV2(ji);
      } else if (fieldEquals("custom_field_entries", buf, offset, len)) {
        customFieldEntries = new ArrayList<>();
        while (ji.readArray()) {
          customFieldEntries.add(parseCustomFieldEntryV2(ji));
        }
      } else if (fieldEquals("duration_metrics", buf, offset, len)) {
        durationMetrics = new ArrayList<>();
        while (ji.readArray()) {
          durationMetrics.add(parseDurationMetricV2(ji));
        }
      } else if (fieldEquals("external_issue_reference", buf, offset, len)) {
        externalIssueReference = parseExternalIssueReferenceV2(ji);
      } else if (fieldEquals("has_debrief", buf, offset, len)) {
        hasDebrief = ji.readBoolean();
      } else if (fieldEquals("id", buf, offset, len)) {
        id = ji.readString();
      } else if (fieldEquals("incident_role_assignments", buf, offset, len)) {
        incidentRoleAssignments = new ArrayList<>();
        while (ji.readArray()) {
          incidentRoleAssignments.add(parseIncidentRoleAssignmentV2(ji));
        }
      } else if (fieldEquals("incident_status", buf, offset, len)) {
        incidentStatus = parseIncidentStatusV2(ji);
      } else if (fieldEquals("incident_timestamp_values", buf, offset, len)) {
        incidentTimestampValues = new ArrayList<>();
        while (ji.readArray()) {
          incidentTimestampValues.add(parseIncidentTimestampWithValueV2(ji));
        }
      } else if (fieldEquals("incident_type", buf, offset, len)) {
        incidentType = parseIncidentTypeV2(ji);
      } else if (fieldEquals("mode", buf, offset, len)) {
        mode = Mode.valueOf(ji.readString());
      } else if (fieldEquals("name", buf, offset, len)) {
        name = ji.readString();
      } else if (fieldEquals("permalink", buf, offset, len)) {
        permalink = ji.readString();
      } else if (fieldEquals("postmortem_document_url", buf, offset, len)) {
        postmortemDocumentUrl = ji.readString();
      } else if (fieldEquals("reference", buf, offset, len)) {
        reference = ji.readString();
      } else if (fieldEquals("severity", buf, offset, len)) {
        severity = parseSeverityV2(ji);
      } else if (fieldEquals("slack_channel_id", buf, offset, len)) {
        slackChannelId = ji.readString();
      } else if (fieldEquals("slack_channel_name", buf, offset, len)) {
        slackChannelName = ji.readString();
      } else if (fieldEquals("slack_team_id", buf, offset, len)) {
        slackTeamId = ji.readString();
      } else if (fieldEquals("summary", buf, offset, len)) {
        summary = ji.readString();
      } else if (fieldEquals("updated_at", buf, offset, len)) {
        updatedAt = OffsetDateTime.parse(ji.readString());
      } else if (fieldEquals("visibility", buf, offset, len)) {
        visibility = Visibility.valueOf(ji.readString().toUpperCase());
      } else if (fieldEquals("workload_minutes_late", buf, offset, len)) {
        workloadMinutesLate = ji.readDouble();
      } else if (fieldEquals("workload_minutes_sleeping", buf, offset, len)) {
        workloadMinutesSleeping = ji.readDouble();
      } else if (fieldEquals("workload_minutes_total", buf, offset, len)) {
        workloadMinutesTotal = ji.readDouble();
      } else if (fieldEquals("workload_minutes_working", buf, offset, len)) {
        workloadMinutesWorking = ji.readDouble();
      } else {
        ji.skip();
      }
      return true;
    }

    private ActorV2 parseActorV2(final JsonIterator ji) {
      final var p = new Object() {
        String email, id, name, slackUserId;
      };
      ji.testObject((buf, offset, len, ji1) -> {
        if (fieldEquals("email", buf, offset, len)) {
          p.email = ji1.readString();
        } else if (fieldEquals("id", buf, offset, len)) {
          p.id = ji1.readString();
        } else if (fieldEquals("name", buf, offset, len)) {
          p.name = ji1.readString();
        } else if (fieldEquals("slack_user_id", buf, offset, len)) {
          p.slackUserId = ji1.readString();
        } else if (fieldEquals("user", buf, offset, len)) {
          ji1.testObject((buf2, offset2, len2, ji2) -> {
            if (fieldEquals("email", buf2, offset2, len2)) {
              p.email = ji2.readString();
            } else if (fieldEquals("id", buf2, offset2, len2)) {
              p.id = ji2.readString();
            } else if (fieldEquals("name", buf2, offset2, len2)) {
              p.name = ji2.readString();
            } else if (fieldEquals("slack_user_id", buf2, offset2, len2)) {
              p.slackUserId = ji2.readString();
            } else {
              ji2.skip();
            }
            return true;
          });
        } else {
          ji1.skip();
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
      ji.testObject((buf, offset, len, ji1) -> {
        if (fieldEquals("custom_field", buf, offset, len)) {
          ji1.testObject((buf2, offset2, len2, ji2) -> {
            if (fieldEquals("id", buf2, offset2, len2)) {
              p.customFieldId = ji2.readString();
            } else if (fieldEquals("name", buf2, offset2, len2)) {
              p.customFieldName = ji2.readString();
            } else if (fieldEquals("field_type", buf2, offset2, len2)) {
              p.customFieldType = ji2.readString();
            } else {
              ji2.skip();
            }
            return true;
          });
        } else if (fieldEquals("values", buf, offset, len)) {
          // V2 response for values is an array of objects
          if (ji1.readArray()) {
            ji1.testObject((buf2, offset2, len2, ji2) -> {
              if (fieldEquals("value_text", buf2, offset2, len2)) {
                p.value = ji2.readString();
              } else {
                ji2.skip();
              }
              return true;
            });
            ji1.skipRestOfArray();
          }
        } else {
          ji1.skip();
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
      ji.testObject((buf, offset, len, ji1) -> {
        if (fieldEquals("duration_metric", buf, offset, len)) {
          ji1.testObject((buf2, offset2, len2, ji2) -> {
            if (fieldEquals("id", buf2, offset2, len2)) {
              p.id = ji2.readString();
            } else if (fieldEquals("name", buf2, offset2, len2)) {
              p.name = ji2.readString();
            } else {
              ji2.skip();
            }
            return true;
          });
        } else if (fieldEquals("value_seconds", buf, offset, len)) {
          p.value = ji1.readLong();
        } else {
          ji1.skip();
        }
        return true;
      });
      return new IncidentDurationMetricWithValueV2(p.id, p.name, p.value);
    }

    private ExternalIssueReferenceV2 parseExternalIssueReferenceV2(final JsonIterator ji) {
      final var p = new Object() {
        String id, ref, title, url, provider;
      };
      ji.testObject((buf, offset, len, ji1) -> {
        if (fieldEquals("issue_id", buf, offset, len)) {
          p.id = ji1.readString();
        } else if (fieldEquals("issue_reference", buf, offset, len)) {
          p.ref = ji1.readString();
        } else if (fieldEquals("issue_name", buf, offset, len)) {
          p.title = ji1.readString();
        } else if (fieldEquals("issue_permalink", buf, offset, len)) {
          p.url = ji1.readString();
        } else if (fieldEquals("provider", buf, offset, len)) {
          p.provider = ji1.readString();
        } else {
          ji1.skip();
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
      ji.testObject((buf, offset, len, ji1) -> {
        if (fieldEquals("assignee", buf, offset, len)) {
          p.assignee = parseActorV2(ji1);
        } else if (fieldEquals("role", buf, offset, len)) {
          p.role = parseIncidentRoleV2(ji1);
        } else {
          ji1.skip();
        }
        return true;
      });
      return new IncidentRoleAssignmentV2(p.assignee, p.role);
    }

    private IncidentRoleV2 parseIncidentRoleV2(final JsonIterator ji) {
      final var p = new Object() {
        String id, name, description, roleType;
      };
      ji.testObject((buf, offset, len, ji1) -> {
        if (fieldEquals("id", buf, offset, len)) {
          p.id = ji1.readString();
        } else if (fieldEquals("name", buf, offset, len)) {
          p.name = ji1.readString();
        } else if (fieldEquals("description", buf, offset, len)) {
          p.description = ji1.readString();
        } else if (fieldEquals("role_type", buf, offset, len)) {
          p.roleType = ji1.readString();
        } else {
          ji1.skip();
        }
        return true;
      });
      return new IncidentRoleV2(p.id, p.name, p.description, p.roleType);
    }

    private IncidentStatusV2 parseIncidentStatusV2(final JsonIterator ji) {
      final var p = new Object() {
        String id, name, description, category;
      };
      ji.testObject((buf, offset, len, ji1) -> {
        if (fieldEquals("id", buf, offset, len)) {
          p.id = ji1.readString();
        } else if (fieldEquals("name", buf, offset, len)) {
          p.name = ji1.readString();
        } else if (fieldEquals("description", buf, offset, len)) {
          p.description = ji1.readString();
        } else if (fieldEquals("category", buf, offset, len)) {
          p.category = ji1.readString();
        } else {
          ji1.skip();
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
      ji.testObject((buf, offset, len, ji1) -> {
        if (fieldEquals("incident_timestamp", buf, offset, len)) {
          ji1.testObject((buf2, offset2, len2, ji2) -> {
            if (fieldEquals("id", buf2, offset2, len2)) {
              p.id = ji2.readString();
            } else if (fieldEquals("name", buf2, offset2, len2)) {
              p.name = ji2.readString();
            } else {
              ji2.skip();
            }
            return true;
          });
        } else if (fieldEquals("value", buf, offset, len)) {
          ji1.testObject((buf2, offset2, len2, ji2) -> {
            if (fieldEquals("value", buf2, offset2, len2)) {
              p.value = OffsetDateTime.parse(ji2.readString());
            } else {
              ji2.skip();
            }
            return true;
          });
        } else {
          ji1.skip();
        }
        return true;
      });
      return new IncidentTimestampWithValueV2(p.id, p.name, p.value);
    }

    private IncidentTypeV2 parseIncidentTypeV2(final JsonIterator ji) {
      final var p = new Object() {
        String id, name, description;
      };
      ji.testObject((buf, offset, len, ji1) -> {
        if (fieldEquals("id", buf, offset, len)) {
          p.id = ji1.readString();
        } else if (fieldEquals("name", buf, offset, len)) {
          p.name = ji1.readString();
        } else if (fieldEquals("description", buf, offset, len)) {
          p.description = ji1.readString();
        } else {
          ji1.skip();
        }
        return true;
      });
      return new IncidentTypeV2(p.id, p.name, p.description);
    }

    private SeverityV2 parseSeverityV2(final JsonIterator ji) {
      final var p = new Object() {
        String id, name, description;
      };
      ji.testObject((buf, offset, len, ji1) -> {
        if (fieldEquals("id", buf, offset, len)) {
          p.id = ji1.readString();
        } else if (fieldEquals("name", buf, offset, len)) {
          p.name = ji1.readString();
        } else if (fieldEquals("description", buf, offset, len)) {
          p.description = ji1.readString();
        } else {
          ji1.skip();
        }
        return true;
      });
      return new SeverityV2(p.id, p.name, p.description);
    }
  }
}
