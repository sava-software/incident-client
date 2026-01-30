package software.sava.incident.io;

import software.sava.incident.core.request.PostRequest;
import software.sava.incident.core.request.Request;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface CreateIncidentRequest extends PostRequest {

  static Builder builder() {
    return new Builder();
  }

  String idempotencyKey();

  String name();

  String summary();

  String description();

  String incidentTypeId();

  Collection<IncidentRoleAssignment> incidentRoleAssignments();

  Mode mode();

  String priorityId();

  String severityId();

  String statusId();

  Visibility visibility();

  String slackTeamId();

  Boolean creatorOutOfHours();

  Map<String, String> customFieldValues();

  enum Mode {
    manual,
    realtime
  }

  enum Visibility {
    PUBLIC,
    PRIVATE;

    @Override
    public String toString() {
      return this.name().toLowerCase();
    }
  }

  record IncidentRoleAssignment(String incidentRoleId, String assigneeId) {
  }

  final class Builder extends Request.Builder {

    private String idempotencyKey;
    private String name;
    private String summary;
    private String description;
    private String incidentTypeId;
    private Collection<IncidentRoleAssignment> incidentRoleAssignments;
    private Mode mode;
    private String priorityId;
    private String severityId;
    private String statusId;
    private Visibility visibility;
    private String slackTeamId;
    private Boolean creatorOutOfHours;
    private Map<String, String> customFieldValues;

    public Builder idempotencyKey(final String idempotencyKey) {
      this.idempotencyKey = idempotencyKey;
      return this;
    }

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder summary(final String summary) {
      this.summary = summary;
      return this;
    }

    public Builder description(final String description) {
      this.description = description;
      return this;
    }

    public Builder incidentTypeId(final String incidentTypeId) {
      this.incidentTypeId = incidentTypeId;
      return this;
    }

    public Builder incidentRoleAssignments(final Collection<IncidentRoleAssignment> incidentRoleAssignments) {
      this.incidentRoleAssignments = incidentRoleAssignments;
      return this;
    }

    public Builder mode(final Mode mode) {
      this.mode = mode;
      return this;
    }

    public Builder priorityId(final String priorityId) {
      this.priorityId = priorityId;
      return this;
    }

    public Builder severityId(final String severityId) {
      this.severityId = severityId;
      return this;
    }

    public Builder statusId(final String statusId) {
      this.statusId = statusId;
      return this;
    }

    public Builder visibility(final Visibility visibility) {
      this.visibility = visibility;
      return this;
    }

    public Builder slackTeamId(final String slackTeamId) {
      this.slackTeamId = slackTeamId;
      return this;
    }

    public Builder creatorOutOfHours(final Boolean creatorOutOfHours) {
      this.creatorOutOfHours = creatorOutOfHours;
      return this;
    }

    public Builder customFieldValues(final Map<String, String> customFieldValues) {
      this.customFieldValues = customFieldValues;
      return this;
    }

    public CreateIncidentRequest build() {
      return new CreateIncidentRequestRecord(
          timeout(),
          idempotencyKey,
          name,
          summary,
          description,
          incidentTypeId,
          incidentRoleAssignments == null ? List.of() : List.copyOf(incidentRoleAssignments),
          mode,
          priorityId,
          severityId,
          statusId,
          visibility,
          slackTeamId,
          creatorOutOfHours,
          customFieldValues == null ? Map.of() : Map.copyOf(customFieldValues)
      );
    }
  }
}
