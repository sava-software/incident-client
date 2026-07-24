package software.sava.incident.io;

import systems.comodal.jsoniter.JsonIterator;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

/// Jazzer entry point for the CreateIncidentRequest body serialization. Field values are
/// arbitrary caller-supplied strings, so the serialized body must always be valid JSON:
/// no raw control character may survive escaping, and parsing the body back must yield
/// exactly the values given to the builder. Blank optional fields are omitted by
/// contract; the wire shape is `IncidentsCreatePayloadV2` (`incident_status_id`, nested
/// `assignee` user references, `custom_field_entries` with `value_text` values).
///
/// The input is split on NUL bytes into field values.
///
/// Deliberately free of Jazzer imports so it compiles with the regular test sources.
///
/// Run with `./gradlew :incident-io:fuzzRequest [-PmaxFuzzTime=<seconds>]`.
public final class CreateIncidentRequestFuzz {

  private static final String DELIMITER = String.valueOf((char) 0);

  public static void fuzzerTestOneInput(final byte[] data) {
    if (data.length == 0) {
      return;
    }
    final var parts = new String(data, 1, data.length - 1, UTF_8).split(DELIMITER, -1);
    final var builder = CreateIncidentRequest.requestBuilder()
        .name(part(parts, 0))
        .summary(part(parts, 1))
        .idempotencyKey(part(parts, 2))
        .incidentTypeId(part(parts, 3))
        .mode(part(parts, 4))
        .severityId(part(parts, 5))
        .statusId(part(parts, 6))
        .visibility(part(parts, 7))
        .slackTeamId(part(parts, 8));
    if (parts.length > 12) {
      builder.incidentRoleAssignments(List.of(
          new CreateIncidentRequest.IncidentRoleAssignment(
              parts[9],
              new CreateIncidentRequest.UserReference(parts[10], parts[11], parts[12])
          )
      ));
    }
    if (parts.length > 14) {
      builder.customFieldValues(Map.of(parts[13], parts[14]));
    }

    final var body = builder.build().body();
    assertNoRawControlChars(body);

    final var p = new Object() {
      String idempotencyKey, name, summary, incidentTypeId, mode,
          severityId, statusId, visibility, slackTeamId;
      String roleId, assigneeId, assigneeEmail, assigneeSlackUserId, cfId, cfValue;
    };
    JsonIterator.parse(body).testObject((buf, offset, len, ji) -> {
      if (fieldEquals("idempotency_key", buf, offset, len)) {
        p.idempotencyKey = ji.readString();
      } else if (fieldEquals("name", buf, offset, len)) {
        p.name = ji.readString();
      } else if (fieldEquals("summary", buf, offset, len)) {
        p.summary = ji.readString();
      } else if (fieldEquals("incident_type_id", buf, offset, len)) {
        p.incidentTypeId = ji.readString();
      } else if (fieldEquals("mode", buf, offset, len)) {
        p.mode = ji.readString();
      } else if (fieldEquals("severity_id", buf, offset, len)) {
        p.severityId = ji.readString();
      } else if (fieldEquals("incident_status_id", buf, offset, len)) {
        p.statusId = ji.readString();
      } else if (fieldEquals("visibility", buf, offset, len)) {
        p.visibility = ji.readString();
      } else if (fieldEquals("slack_team_id", buf, offset, len)) {
        p.slackTeamId = ji.readString();
      } else if (fieldEquals("incident_role_assignments", buf, offset, len)) {
        while (ji.readArray()) {
          ji.testObject((buf2, offset2, len2, ji2) -> {
            if (fieldEquals("incident_role_id", buf2, offset2, len2)) {
              p.roleId = ji2.readString();
            } else if (fieldEquals("assignee", buf2, offset2, len2)) {
              ji2.testObject((buf3, offset3, len3, ji3) -> {
                if (fieldEquals("id", buf3, offset3, len3)) {
                  p.assigneeId = ji3.readString();
                } else if (fieldEquals("email", buf3, offset3, len3)) {
                  p.assigneeEmail = ji3.readString();
                } else if (fieldEquals("slack_user_id", buf3, offset3, len3)) {
                  p.assigneeSlackUserId = ji3.readString();
                } else {
                  throw new AssertionError("unexpected assignee field " + new String(buf3, offset3, len3));
                }
                return true;
              });
            } else {
              throw new AssertionError("unexpected role assignment field " + new String(buf2, offset2, len2));
            }
            return true;
          });
        }
      } else if (fieldEquals("custom_field_entries", buf, offset, len)) {
        while (ji.readArray()) {
          ji.testObject((buf2, offset2, len2, ji2) -> {
            if (fieldEquals("custom_field_id", buf2, offset2, len2)) {
              p.cfId = ji2.readString();
            } else if (fieldEquals("values", buf2, offset2, len2)) {
              while (ji2.readArray()) {
                ji2.testObject((buf3, offset3, len3, ji3) -> {
                  if (fieldEquals("value_text", buf3, offset3, len3)) {
                    p.cfValue = ji3.readString();
                  } else {
                    throw new AssertionError("unexpected custom field value field " + new String(buf3, offset3, len3));
                  }
                  return true;
                });
              }
            } else {
              throw new AssertionError("unexpected custom field entry field " + new String(buf2, offset2, len2));
            }
            return true;
          });
        }
      } else {
        throw new AssertionError("unexpected field " + new String(buf, offset, len));
      }
      return true;
    });

    assertOptionalField(part(parts, 0), p.name, "name");
    assertOptionalField(part(parts, 1), p.summary, "summary");
    assertOptionalField(part(parts, 2), p.idempotencyKey, "idempotency_key");
    assertOptionalField(part(parts, 3), p.incidentTypeId, "incident_type_id");
    assertOptionalField(part(parts, 4), p.mode, "mode");
    assertOptionalField(part(parts, 5), p.severityId, "severity_id");
    assertOptionalField(part(parts, 6), p.statusId, "incident_status_id");
    assertOptionalField(part(parts, 7), p.visibility, "visibility");
    assertOptionalField(part(parts, 8), p.slackTeamId, "slack_team_id");
    if (parts.length > 12) {
      assertEq(parts[9], p.roleId, "incident_role_id");
      // blank reference fields are omitted; an all-blank reference drops the assignee
      assertOptionalField(parts[10], p.assigneeId, "assignee.id");
      assertOptionalField(parts[11], p.assigneeEmail, "assignee.email");
      assertOptionalField(parts[12], p.assigneeSlackUserId, "assignee.slack_user_id");
    }
    if (parts.length > 14) {
      assertEq(parts[13], p.cfId, "custom_field_id");
      assertEq(parts[14], p.cfValue, "value_text");
    }
  }

  private static String part(final String[] parts, final int i) {
    return i < parts.length ? parts[i] : null;
  }

  private static void assertNoRawControlChars(final String json) {
    for (int i = 0; i < json.length(); ++i) {
      final char c = json.charAt(i);
      if (c < 0x20) {
        throw new AssertionError("raw control character 0x" + Integer.toHexString(c) + " in: " + json);
      }
    }
  }

  private static void assertOptionalField(final String expected, final String parsed, final String field) {
    if (expected != null && !expected.isBlank()) {
      assertEq(expected, parsed, field);
    } else if (parsed != null) {
      throw new AssertionError(field + " should have been omitted, parsed '" + parsed + '\'');
    }
  }

  private static void assertEq(final Object expected, final Object parsed, final String field) {
    if (!Objects.equals(expected, parsed)) {
      throw new AssertionError(field + " did not round-trip: expected '" + expected + "', parsed '" + parsed + '\'');
    }
  }

  private CreateIncidentRequestFuzz() {
  }
}
