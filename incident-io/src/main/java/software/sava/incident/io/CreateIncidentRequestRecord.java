package software.sava.incident.io;

import software.sava.incident.core.json.Rfc3339;
import software.sava.incident.core.request.BaseRequest;

import java.time.Duration;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

import static software.sava.incident.core.json.JsonUtil.escapeJson;

final class CreateIncidentRequestRecord extends BaseRequest implements CreateIncidentRequest {

  private final String idempotencyKey;
  private final String name;
  private final String summary;
  private final String incidentTypeId;
  private final Collection<IncidentRoleAssignment> incidentRoleAssignments;
  private final String mode;
  private final String severityId;
  private final String statusId;
  private final String visibility;
  private final String slackTeamId;
  private final String slackChannelNameOverride;
  private final Collection<CustomFieldEntry> customFieldEntries;
  private final Collection<IncidentTimestampValue> incidentTimestampValues;
  private final RetrospectiveIncidentOptions retrospectiveIncidentOptions;

  CreateIncidentRequestRecord(final Duration timeout,
                              final String idempotencyKey,
                              final String name,
                              final String summary,
                              final String incidentTypeId,
                              final Collection<IncidentRoleAssignment> incidentRoleAssignments,
                              final String mode,
                              final String severityId,
                              final String statusId,
                              final String visibility,
                              final String slackTeamId,
                              final String slackChannelNameOverride,
                              final Collection<CustomFieldEntry> customFieldEntries,
                              final Collection<IncidentTimestampValue> incidentTimestampValues,
                              final RetrospectiveIncidentOptions retrospectiveIncidentOptions) {
    super(timeout);
    this.idempotencyKey = idempotencyKey;
    this.name = name;
    this.summary = summary;
    this.incidentTypeId = incidentTypeId;
    this.incidentRoleAssignments = incidentRoleAssignments;
    this.mode = mode;
    this.severityId = severityId;
    this.statusId = statusId;
    this.visibility = visibility;
    this.slackTeamId = slackTeamId;
    this.slackChannelNameOverride = slackChannelNameOverride;
    this.customFieldEntries = customFieldEntries;
    this.incidentTimestampValues = incidentTimestampValues;
    this.retrospectiveIncidentOptions = retrospectiveIncidentOptions;
  }

  @Override
  public String idempotencyKey() {
    return idempotencyKey;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String summary() {
    return summary;
  }

  @Override
  public String incidentTypeId() {
    return incidentTypeId;
  }

  @Override
  public Collection<IncidentRoleAssignment> incidentRoleAssignments() {
    return incidentRoleAssignments;
  }

  @Override
  public String mode() {
    return mode;
  }

  @Override
  public String severityId() {
    return severityId;
  }

  @Override
  public String statusId() {
    return statusId;
  }

  @Override
  public String visibility() {
    return visibility;
  }

  @Override
  public String slackTeamId() {
    return slackTeamId;
  }

  @Override
  public String slackChannelNameOverride() {
    return slackChannelNameOverride;
  }

  @Override
  public Collection<CustomFieldEntry> customFieldEntries() {
    return customFieldEntries;
  }

  @Override
  public Collection<IncidentTimestampValue> incidentTimestampValues() {
    return incidentTimestampValues;
  }

  @Override
  public RetrospectiveIncidentOptions retrospectiveIncidentOptions() {
    return retrospectiveIncidentOptions;
  }

  @Override
  public String body() {
    final var sb = new StringBuilder(1_024);
    sb.append('{');
    appendField(sb, "idempotency_key", idempotencyKey);
    appendField(sb, "name", name);
    appendField(sb, "summary", summary);
    appendField(sb, "incident_type_id", incidentTypeId);
    // the builder normalizes null collections to empty
    appendArray(sb, "incident_role_assignments", incidentRoleAssignments,
        CreateIncidentRequestRecord::roleAssignmentJson);
    appendField(sb, "mode", mode);
    appendField(sb, "severity_id", severityId);
    appendField(sb, "incident_status_id", statusId);
    appendField(sb, "visibility", visibility);
    appendField(sb, "slack_team_id", slackTeamId);
    appendField(sb, "slack_channel_name_override", slackChannelNameOverride);
    appendArray(sb, "custom_field_entries", customFieldEntries,
        CreateIncidentRequestRecord::customFieldEntryJson);
    appendArray(sb, "incident_timestamp_values", incidentTimestampValues,
        CreateIncidentRequestRecord::incidentTimestampValueJson);
    if (retrospectiveIncidentOptions != null) {
      // options with nothing set are omitted entirely
      final var options = retrospectiveOptionsJson(retrospectiveIncidentOptions);
      if (!options.isEmpty()) {
        if (sb.length() > 1) {
          sb.append(',');
        }
        sb.append("""
            "retrospective_incident_options":{""").append(options).append('}');
      }
    }
    sb.append('}');
    return sb.toString();
  }

  private static <T> void appendArray(final StringBuilder sb,
                                      final String field,
                                      final Collection<T> values,
                                      final Function<T, String> toJson) {
    if (values.isEmpty()) {
      return;
    }
    if (sb.length() > 1) {
      sb.append(',');
    }
    sb.append('"').append(field).append("""
        ":[""");
    sb.append(values.stream().map(toJson).collect(Collectors.joining(",")));
    sb.append(']');
  }

  private static String roleAssignmentJson(final IncidentRoleAssignment ira) {
    final var sb = new StringBuilder(128);
    sb.append("""
        {"incident_role_id":"%s\"""".formatted(escapeJson(ira.incidentRoleId())));
    final var assignee = ira.assignee();
    if (assignee != null) {
      // a reference whose fields are all blank is omitted entirely
      final var ref = new StringBuilder(96);
      appendField(ref, "id", assignee.id());
      appendField(ref, "email", assignee.email());
      appendField(ref, "slack_user_id", assignee.slackUserId());
      if (!ref.isEmpty()) {
        sb.append("""
            ,"assignee":{""").append(ref).append('}');
      }
    }
    return sb.append('}').toString();
  }

  /// `custom_field_id` and `values` are both required, so — like `incident_role_id` —
  /// they are emitted unconditionally; a blank id is the API's to reject, not this
  /// client's to drop. An empty `values` array unsets the field.
  private static String customFieldEntryJson(final CustomFieldEntry entry) {
    final var sb = new StringBuilder(160);
    sb.append("""
        {"custom_field_id":"%s","values":[""".formatted(escapeJson(entry.customFieldId())));
    sb.append(entry.values().stream()
        .map(CreateIncidentRequestRecord::customFieldValueJson)
        .filter(value -> !value.isEmpty())
        .collect(Collectors.joining(",")));
    return sb.append("]}").toString();
  }

  /// A value with every field blank carries nothing; it is dropped rather than
  /// serialized as `{}`.
  private static String customFieldValueJson(final CustomFieldValue value) {
    final var sb = new StringBuilder(96);
    appendField(sb, "id", value.id());
    appendField(sb, "value_catalog_entry_id", value.valueCatalogEntryId());
    appendField(sb, "value_link", value.valueLink());
    appendField(sb, "value_numeric", value.valueNumeric());
    appendField(sb, "value_option_id", value.valueOptionId());
    appendField(sb, "value_text", value.valueText());
    return sb.isEmpty() ? "" : sb.insert(0, '{').append('}').toString();
  }

  /// `incident_timestamp_id` is required and emitted unconditionally; only `value` is
  /// optional.
  private static String incidentTimestampValueJson(final IncidentTimestampValue timestampValue) {
    final var sb = new StringBuilder(96);
    sb.append("""
        {"incident_timestamp_id":"%s\"""".formatted(escapeJson(timestampValue.incidentTimestampId())));
    final var value = timestampValue.value();
    if (value != null) {
      // RFC 3339 output is digits and punctuation; nothing here needs escaping
      sb.append("""
          ,"value":"%s\"""".formatted(Rfc3339.format(value)));
    }
    return sb.append('}').toString();
  }

  private static String retrospectiveOptionsJson(final RetrospectiveIncidentOptions options) {
    final var sb = new StringBuilder(128);
    final var externalId = options.externalId();
    if (externalId != null) {
      sb.append("""
          "external_id":""").append(externalId.longValue());
    }
    appendField(sb, "postmortem_document_url", options.postmortemDocumentUrl());
    appendField(sb, "slack_channel_id", options.slackChannelId());
    return sb.toString();
  }

  private static void appendField(final StringBuilder sb, final String field, final String value) {
    if (value != null && !value.isBlank()) {
      if (sb.length() > 1) {
        sb.append(',');
      }
      sb.append('"').append(field).append("""
          ":"%s\"""".formatted(escapeJson(value)));
    }
  }
}
