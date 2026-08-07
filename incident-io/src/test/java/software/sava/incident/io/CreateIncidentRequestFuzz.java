package software.sava.incident.io;

import systems.comodal.jsoniter.JsonIterator;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

/// Jazzer entry point for the CreateIncidentRequest body serialization. Field values are
/// arbitrary caller-supplied strings, so the serialized body must always be valid JSON:
/// no raw control character may survive escaping, and parsing the body back must yield
/// exactly the values given to the builder. Blank optional fields are omitted by
/// contract; the wire shape is `IncidentsCreatePayloadV2` (`incident_status_id`, nested
/// `assignee` user references, `custom_field_entries` with typed values,
/// `incident_timestamp_values`, and `retrospective_incident_options`).
///
/// The input is split on NUL bytes into field values.
///
/// Deliberately free of Jazzer imports so it compiles with the regular test sources.
///
/// Run with `./gradlew :incident-io:fuzzRequest [-PmaxFuzzTime=<seconds>]`.
public final class CreateIncidentRequestFuzz {

  private static final String DELIMITER = String.valueOf((char) 0);

  // non-zero fixed origins: the harness pins escaping of caller strings, and a
  // zero-valued timestamp or id would make "mutated to 0" indistinguishable
  private static final OffsetDateTime TIMESTAMP_VALUE = OffsetDateTime.parse("2024-05-01T12:00:00.123456Z");
  private static final String TIMESTAMP_VALUE_JSON = "2024-05-01T12:00:00.123456Z";
  private static final long EXTERNAL_ID = 4242L;

  public static void fuzzerTestOneInput(final byte[] data) {
    if (data.length == 0) {
      return;
    }
    final var parts = new String(data, 1, data.length - 1, UTF_8).split(DELIMITER, -1);
    // build() requires a visibility and defaults a blank idempotency key to a random UUID.
    // Both are preconditions of constructing a request, not part of what this target
    // explores, so a blank fuzz part falls back to a fixed value: the fields stay fuzzed
    // for escaping coverage while the round-trip stays deterministic.
    final var idempotencyKey = required(part(parts, 2), "fuzz-idempotency-key");
    final var visibility = required(part(parts, 7), "public");
    final var builder = CreateIncidentRequest.requestBuilder()
        .name(part(parts, 0))
        .summary(part(parts, 1))
        .idempotencyKey(idempotencyKey)
        .incidentTypeId(part(parts, 3))
        .mode(part(parts, 4))
        .severityId(part(parts, 5))
        .statusId(part(parts, 6))
        .visibility(visibility)
        .slackTeamId(part(parts, 8))
        .slackChannelNameOverride(part(parts, 9));
    if (parts.length > 13) {
      builder.incidentRoleAssignments(List.of(
          new CreateIncidentRequest.IncidentRoleAssignment(
              parts[10],
              new CreateIncidentRequest.UserReference(parts[11], parts[12], parts[13])
          )
      ));
    }
    if (parts.length > 20) {
      builder.customFieldEntries(List.of(new CreateIncidentRequest.CustomFieldEntry(
          parts[14],
          List.of(new CreateIncidentRequest.CustomFieldValue(
              parts[15], parts[16], parts[17], parts[18], parts[19], parts[20]))
      )));
    }
    if (parts.length > 21) {
      builder.incidentTimestampValues(List.of(
          new CreateIncidentRequest.IncidentTimestampValue(parts[21], TIMESTAMP_VALUE)
      ));
    }
    if (parts.length > 23) {
      builder.retrospectiveIncidentOptions(new CreateIncidentRequest.RetrospectiveIncidentOptions(
          EXTERNAL_ID, parts[22], parts[23]
      ));
    }

    final var body = builder.build().body();
    assertNoRawControlChars(body);

    final var p = new Object() {
      String idempotencyKey, name, summary, incidentTypeId, mode,
          severityId, statusId, visibility, slackTeamId, slackChannelNameOverride;
      String roleId, assigneeId, assigneeEmail, assigneeSlackUserId;
      String cfId, cfValueId, cfCatalogEntryId, cfLink, cfNumeric, cfOptionId, cfText;
      boolean cfValuesPresent, cfValueObjectPresent;
      String timestampId, timestampValue;
      String retrospectivePostmortemUrl, retrospectiveSlackChannelId;
      long retrospectiveExternalId;
      boolean retrospectivePresent;
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
      } else if (fieldEquals("slack_channel_name_override", buf, offset, len)) {
        p.slackChannelNameOverride = ji.readString();
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
              p.cfValuesPresent = true;
              while (ji2.readArray()) {
                p.cfValueObjectPresent = true;
                ji2.testObject((buf3, offset3, len3, ji3) -> {
                  if (fieldEquals("id", buf3, offset3, len3)) {
                    p.cfValueId = ji3.readString();
                  } else if (fieldEquals("value_catalog_entry_id", buf3, offset3, len3)) {
                    p.cfCatalogEntryId = ji3.readString();
                  } else if (fieldEquals("value_link", buf3, offset3, len3)) {
                    p.cfLink = ji3.readString();
                  } else if (fieldEquals("value_numeric", buf3, offset3, len3)) {
                    p.cfNumeric = ji3.readString();
                  } else if (fieldEquals("value_option_id", buf3, offset3, len3)) {
                    p.cfOptionId = ji3.readString();
                  } else if (fieldEquals("value_text", buf3, offset3, len3)) {
                    p.cfText = ji3.readString();
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
      } else if (fieldEquals("incident_timestamp_values", buf, offset, len)) {
        while (ji.readArray()) {
          ji.testObject((buf2, offset2, len2, ji2) -> {
            if (fieldEquals("incident_timestamp_id", buf2, offset2, len2)) {
              p.timestampId = ji2.readString();
            } else if (fieldEquals("value", buf2, offset2, len2)) {
              p.timestampValue = ji2.readString();
            } else {
              throw new AssertionError("unexpected timestamp value field " + new String(buf2, offset2, len2));
            }
            return true;
          });
        }
      } else if (fieldEquals("retrospective_incident_options", buf, offset, len)) {
        p.retrospectivePresent = true;
        ji.testObject((buf2, offset2, len2, ji2) -> {
          if (fieldEquals("external_id", buf2, offset2, len2)) {
            p.retrospectiveExternalId = ji2.readLong();
          } else if (fieldEquals("postmortem_document_url", buf2, offset2, len2)) {
            p.retrospectivePostmortemUrl = ji2.readString();
          } else if (fieldEquals("slack_channel_id", buf2, offset2, len2)) {
            p.retrospectiveSlackChannelId = ji2.readString();
          } else {
            throw new AssertionError("unexpected retrospective option " + new String(buf2, offset2, len2));
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
    assertEq(idempotencyKey, p.idempotencyKey, "idempotency_key");
    assertOptionalField(part(parts, 3), p.incidentTypeId, "incident_type_id");
    assertOptionalField(part(parts, 4), p.mode, "mode");
    assertOptionalField(part(parts, 5), p.severityId, "severity_id");
    assertOptionalField(part(parts, 6), p.statusId, "incident_status_id");
    assertEq(visibility, p.visibility, "visibility");
    assertOptionalField(part(parts, 8), p.slackTeamId, "slack_team_id");
    assertOptionalField(part(parts, 9), p.slackChannelNameOverride, "slack_channel_name_override");
    if (parts.length > 13) {
      assertEq(parts[10], p.roleId, "incident_role_id");
      // blank reference fields are omitted; an all-blank reference drops the assignee
      assertOptionalField(parts[11], p.assigneeId, "assignee.id");
      assertOptionalField(parts[12], p.assigneeEmail, "assignee.email");
      assertOptionalField(parts[13], p.assigneeSlackUserId, "assignee.slack_user_id");
    }
    if (parts.length > 20) {
      assertEq(parts[14], p.cfId, "custom_field_id");
      // the values array is always emitted — empty unsets the field — but a value with
      // every field blank is dropped rather than serialized as {}
      if (!p.cfValuesPresent) {
        throw new AssertionError("custom field entry lost its values array");
      }
      assertOptionalField(parts[15], p.cfValueId, "value.id");
      assertOptionalField(parts[16], p.cfCatalogEntryId, "value_catalog_entry_id");
      assertOptionalField(parts[17], p.cfLink, "value_link");
      assertOptionalField(parts[18], p.cfNumeric, "value_numeric");
      assertOptionalField(parts[19], p.cfOptionId, "value_option_id");
      assertOptionalField(parts[20], p.cfText, "value_text");
      final boolean anyValueSet = anySet(parts[15], parts[16], parts[17], parts[18], parts[19], parts[20]);
      if (anyValueSet != p.cfValueObjectPresent) {
        throw new AssertionError("custom field value object presence " + p.cfValueObjectPresent
            + " did not match whether any field was set: " + anyValueSet);
      }
    }
    if (parts.length > 21) {
      // required id: emitted even when blank, exactly like incident_role_id
      assertEq(parts[21], p.timestampId, "incident_timestamp_id");
      assertEq(TIMESTAMP_VALUE_JSON, p.timestampValue, "incident_timestamp_values.value");
    }
    if (parts.length > 23) {
      if (!p.retrospectivePresent) {
        throw new AssertionError("retrospective_incident_options was dropped");
      }
      assertEq(EXTERNAL_ID, p.retrospectiveExternalId, "external_id");
      assertOptionalField(parts[22], p.retrospectivePostmortemUrl, "postmortem_document_url");
      assertOptionalField(parts[23], p.retrospectiveSlackChannelId, "slack_channel_id");
    }
  }

  private static boolean anySet(final String... values) {
    for (final var value : values) {
      if (value != null && !value.isBlank()) {
        return true;
      }
    }
    return false;
  }

  private static String part(final String[] parts, final int i) {
    return i < parts.length ? parts[i] : null;
  }

  /// A spec-required field: the fuzzed value when it is usable, else `fallback`.
  private static String required(final String value, final String fallback) {
    return value == null || value.isBlank() ? fallback : value;
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
