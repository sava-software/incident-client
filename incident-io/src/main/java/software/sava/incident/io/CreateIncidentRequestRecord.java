package software.sava.incident.io;

import software.sava.incident.core.request.BaseRequest;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

final class CreateIncidentRequestRecord extends BaseRequest implements CreateIncidentRequest {

  private final String idempotencyKey;
  private final String name;
  private final String summary;
  private final String description;
  private final String incidentTypeId;
  private final Collection<IncidentRoleAssignment> incidentRoleAssignments;
  private final String mode;
  private final String priorityId;
  private final String severityId;
  private final String statusId;
  private final String visibility;
  private final String slackTeamId;
  private final Boolean creatorOutOfHours;
  private final Map<String, String> customFieldValues;

  CreateIncidentRequestRecord(final Duration timeout,
                              final String idempotencyKey,
                              final String name,
                              final String summary,
                              final String description,
                              final String incidentTypeId,
                              final Collection<IncidentRoleAssignment> incidentRoleAssignments,
                              final String mode,
                              final String priorityId,
                              final String severityId,
                              final String statusId,
                              final String visibility,
                              final String slackTeamId,
                              final Boolean creatorOutOfHours,
                              final Map<String, String> customFieldValues) {
    super(timeout);
    this.idempotencyKey = idempotencyKey;
    this.name = name;
    this.summary = summary;
    this.description = description;
    this.incidentTypeId = incidentTypeId;
    this.incidentRoleAssignments = incidentRoleAssignments;
    this.mode = mode;
    this.priorityId = priorityId;
    this.severityId = severityId;
    this.statusId = statusId;
    this.visibility = visibility;
    this.slackTeamId = slackTeamId;
    this.creatorOutOfHours = creatorOutOfHours;
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
  public String description() {
    return description;
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
  public String priorityId() {
    return priorityId;
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
  public Boolean creatorOutOfHours() {
    return creatorOutOfHours;
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
    appendField(sb, "description", description);
    appendField(sb, "incident_type_id", incidentTypeId);
    if (incidentRoleAssignments != null && !incidentRoleAssignments.isEmpty()) {
      if (sb.length() > 1) sb.append(',');
      sb.append("""
          "incident_role_assignments":[""");
      sb.append(incidentRoleAssignments.stream()
          .map(ira -> String.format("""
                  {"incident_role_id":"%s","assignee_id":"%s"}""",
              escapeJson(ira.incidentRoleId()), escapeJson(ira.assigneeId())
          ))
          .collect(Collectors.joining(",")));
      sb.append(']');
    }
    appendField(sb, "mode", mode);
    appendField(sb, "priority_id", priorityId);
    appendField(sb, "severity_id", severityId);
    appendField(sb, "status_id", statusId);
    appendField(sb, "visibility", visibility);
    appendField(sb, "slack_team_id", slackTeamId);
    if (creatorOutOfHours != null) {
      if (sb.length() > 1) sb.append(',');
      sb.append("""
          "creator_out_of_hours":""").append(creatorOutOfHours);
    }
    if (customFieldValues != null && !customFieldValues.isEmpty()) {
      if (sb.length() > 1) sb.append(',');
      sb.append("""
          "custom_field_values":{""");
      sb.append(customFieldValues.entrySet().stream()
          .map(e -> String.format("""
                  "%s":[{"value":"%s"}]""",
              escapeJson(e.getKey()), escapeJson(e.getValue())
          ))
          .collect(Collectors.joining(",")));
      sb.append('}');
    }
    sb.append('}');
    return sb.toString();
  }

  private static void appendField(final StringBuilder sb, final String field, final String value) {
    if (value != null && !value.isBlank()) {
      if (sb.length() > 1) sb.append(',');
      sb.append('"').append(field).append("""
          ":"%s\"""".formatted(escapeJson(value)));
    }
  }

  private static String escapeJson(final String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\")
        .replace("""
            \"""", """
            \\\""""
        )
        .replace("\b", "\\b")
        .replace("\f", "\\f")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
