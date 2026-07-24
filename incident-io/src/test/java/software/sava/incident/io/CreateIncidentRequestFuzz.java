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
/// contract.
///
/// The input is split on NUL bytes into field values; the leading byte selects the
/// creator_out_of_hours flag.
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
        .description(part(parts, 2))
        .idempotencyKey(part(parts, 3))
        .incidentTypeId(part(parts, 4))
        .mode(part(parts, 5))
        .priorityId(part(parts, 6))
        .severityId(part(parts, 7))
        .statusId(part(parts, 8))
        .visibility(part(parts, 9))
        .slackTeamId(part(parts, 10));
    final Boolean creatorOutOfHours = switch (data[0] & 3) {
      case 1 -> Boolean.TRUE;
      case 2 -> Boolean.FALSE;
      default -> null;
    };
    builder.creatorOutOfHours(creatorOutOfHours);
    if (parts.length > 12) {
      builder.incidentRoleAssignments(List.of(
          new CreateIncidentRequest.IncidentRoleAssignment(parts[11], parts[12])
      ));
    }
    if (parts.length > 14) {
      builder.customFieldValues(Map.of(parts[13], parts[14]));
    }

    final var body = builder.build().body();
    assertNoRawControlChars(body);

    final var p = new Object() {
      String idempotencyKey, name, summary, description, incidentTypeId, mode,
          priorityId, severityId, statusId, visibility, slackTeamId;
      String roleId, assigneeId, cfKey, cfValue;
      Boolean creatorOutOfHours;
    };
    JsonIterator.parse(body).testObject((buf, offset, len, ji) -> {
      if (fieldEquals("idempotency_key", buf, offset, len)) {
        p.idempotencyKey = ji.readString();
      } else if (fieldEquals("name", buf, offset, len)) {
        p.name = ji.readString();
      } else if (fieldEquals("summary", buf, offset, len)) {
        p.summary = ji.readString();
      } else if (fieldEquals("description", buf, offset, len)) {
        p.description = ji.readString();
      } else if (fieldEquals("incident_type_id", buf, offset, len)) {
        p.incidentTypeId = ji.readString();
      } else if (fieldEquals("mode", buf, offset, len)) {
        p.mode = ji.readString();
      } else if (fieldEquals("priority_id", buf, offset, len)) {
        p.priorityId = ji.readString();
      } else if (fieldEquals("severity_id", buf, offset, len)) {
        p.severityId = ji.readString();
      } else if (fieldEquals("status_id", buf, offset, len)) {
        p.statusId = ji.readString();
      } else if (fieldEquals("visibility", buf, offset, len)) {
        p.visibility = ji.readString();
      } else if (fieldEquals("slack_team_id", buf, offset, len)) {
        p.slackTeamId = ji.readString();
      } else if (fieldEquals("creator_out_of_hours", buf, offset, len)) {
        p.creatorOutOfHours = ji.readBoolean();
      } else if (fieldEquals("incident_role_assignments", buf, offset, len)) {
        while (ji.readArray()) {
          ji.testObject((buf2, offset2, len2, ji2) -> {
            if (fieldEquals("incident_role_id", buf2, offset2, len2)) {
              p.roleId = ji2.readString();
            } else if (fieldEquals("assignee_id", buf2, offset2, len2)) {
              p.assigneeId = ji2.readString();
            } else {
              throw new AssertionError("unexpected role assignment field " + new String(buf2, offset2, len2));
            }
            return true;
          });
        }
      } else if (fieldEquals("custom_field_values", buf, offset, len)) {
        ji.testObject((buf2, offset2, len2, ji2) -> {
          p.cfKey = new String(buf2, offset2, len2);
          while (ji2.readArray()) {
            ji2.testObject((buf3, offset3, len3, ji3) -> {
              if (fieldEquals("value", buf3, offset3, len3)) {
                p.cfValue = ji3.readString();
              } else {
                throw new AssertionError("unexpected custom field entry field " + new String(buf3, offset3, len3));
              }
              return true;
            });
          }
          return true;
        });
      } else {
        throw new AssertionError("unexpected field " + new String(buf, offset, len));
      }
      return true;
    });

    assertOptionalField(part(parts, 0), p.name, "name");
    assertOptionalField(part(parts, 1), p.summary, "summary");
    assertOptionalField(part(parts, 2), p.description, "description");
    assertOptionalField(part(parts, 3), p.idempotencyKey, "idempotency_key");
    assertOptionalField(part(parts, 4), p.incidentTypeId, "incident_type_id");
    assertOptionalField(part(parts, 5), p.mode, "mode");
    assertOptionalField(part(parts, 6), p.priorityId, "priority_id");
    assertOptionalField(part(parts, 7), p.severityId, "severity_id");
    assertOptionalField(part(parts, 8), p.statusId, "status_id");
    assertOptionalField(part(parts, 9), p.visibility, "visibility");
    assertOptionalField(part(parts, 10), p.slackTeamId, "slack_team_id");
    assertEq(creatorOutOfHours, p.creatorOutOfHours, "creator_out_of_hours");
    if (parts.length > 12) {
      assertEq(parts[11], p.roleId, "incident_role_id");
      assertEq(parts[12], p.assigneeId, "assignee_id");
    }
    if (parts.length > 14) {
      assertEq(parts[13], p.cfKey, "custom field key");
      assertEq(parts[14], p.cfValue, "custom field value");
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
