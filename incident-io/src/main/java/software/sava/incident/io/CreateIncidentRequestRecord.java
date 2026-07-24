package software.sava.incident.io;

import software.sava.incident.core.request.BaseRequest;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
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
  private final Map<String, String> customFieldValues;

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
                              final Map<String, String> customFieldValues) {
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
    this.customFieldValues = customFieldValues;
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
  public Map<String, String> customFieldValues() {
    return customFieldValues;
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
    if (!incidentRoleAssignments.isEmpty()) {
      if (sb.length() > 1) sb.append(',');
      sb.append("""
          "incident_role_assignments":[""");
      sb.append(incidentRoleAssignments.stream()
          .map(CreateIncidentRequestRecord::roleAssignmentJson)
          .collect(Collectors.joining(",")));
      sb.append(']');
    }
    appendField(sb, "mode", mode);
    appendField(sb, "severity_id", severityId);
    appendField(sb, "incident_status_id", statusId);
    appendField(sb, "visibility", visibility);
    appendField(sb, "slack_team_id", slackTeamId);
    if (!customFieldValues.isEmpty()) {
      if (sb.length() > 1) sb.append(',');
      sb.append("""
          "custom_field_entries":[""");
      sb.append(customFieldValues.entrySet().stream()
          .map(e -> String.format("""
                  {"custom_field_id":"%s","values":[{"value_text":"%s"}]}""",
              escapeJson(e.getKey()), escapeJson(e.getValue())
          ))
          .collect(Collectors.joining(",")));
      sb.append(']');
    }
    sb.append('}');
    return sb.toString();
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

  private static void appendField(final StringBuilder sb, final String field, final String value) {
    if (value != null && !value.isBlank()) {
      if (sb.length() > 1) sb.append(',');
      sb.append('"').append(field).append("""
          ":"%s\"""".formatted(escapeJson(value)));
    }
  }
}
