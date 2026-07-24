package software.sava.incident.io;

import software.sava.incident.core.request.PostRequest;
import software.sava.incident.core.request.Request;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/// A `POST /v2/incidents` request (`Incidents V2#Create`). Field names and shapes
/// follow `IncidentsCreatePayloadV2`; the API requires `idempotency_key` and
/// `visibility`, which this builder does not enforce — the provider adapter supplies
/// both.
public interface CreateIncidentRequest extends PostRequest {

  static Builder requestBuilder() {
    return new Builder();
  }

  String idempotencyKey();

  String name();

  String summary();

  String incidentTypeId();

  Collection<IncidentRoleAssignment> incidentRoleAssignments();

  String mode();

  String severityId();

  /// Serialized as `incident_status_id`.
  String statusId();

  /// Required by the API; a request without it is rejected.
  String visibility();

  String slackTeamId();

  /// Text custom-field values keyed by `custom_field_id`; serialized as
  /// `custom_field_entries` with one `value_text` value per entry.
  Map<String, String> customFieldValues();

  enum Mode {
    standard,
    retrospective,
    test,
    tutorial
  }

  enum Visibility {
    PUBLIC,
    PRIVATE;

    @Override
    public String toString() {
      return this.name().toLowerCase();
    }
  }

  /// A `UserReferencePayloadV2`: exactly one of the fields identifies the user; blank
  /// fields are omitted from the serialized `assignee` object.
  record UserReference(String id, String email, String slackUserId) {

    public static UserReference byId(final String id) {
      return new UserReference(id, null, null);
    }

    public static UserReference byEmail(final String email) {
      return new UserReference(null, email, null);
    }

    public static UserReference bySlackUserId(final String slackUserId) {
      return new UserReference(null, null, slackUserId);
    }
  }

  record IncidentRoleAssignment(String incidentRoleId, UserReference assignee) {

    /// Convenience for the common case of assigning by incident.io user id.
    public IncidentRoleAssignment(final String incidentRoleId, final String assigneeId) {
      this(incidentRoleId, UserReference.byId(assigneeId));
    }
  }

  final class Builder extends Request.Builder {

    private String idempotencyKey;
    private String name;
    private String summary;
    private String incidentTypeId;
    private Collection<IncidentRoleAssignment> incidentRoleAssignments;
    private String mode;
    private String severityId;
    private String statusId;
    private String visibility;
    private String slackTeamId;
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

    public Builder incidentTypeId(final String incidentTypeId) {
      this.incidentTypeId = incidentTypeId;
      return this;
    }

    public Builder incidentRoleAssignments(final Collection<IncidentRoleAssignment> incidentRoleAssignments) {
      this.incidentRoleAssignments = incidentRoleAssignments;
      return this;
    }

    public Builder mode(final Mode mode) {
      this.mode = mode == null ? null : mode.name();
      return this;
    }

    public Builder mode(final String mode) {
      this.mode = mode;
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
      this.visibility = visibility == null ? null : visibility.toString();
      return this;
    }

    public Builder visibility(final String visibility) {
      this.visibility = visibility;
      return this;
    }

    public Builder slackTeamId(final String slackTeamId) {
      this.slackTeamId = slackTeamId;
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
          incidentTypeId,
          incidentRoleAssignments == null ? List.of() : List.copyOf(incidentRoleAssignments),
          mode,
          severityId,
          statusId,
          visibility,
          slackTeamId,
          customFieldValues == null ? Map.of() : Map.copyOf(customFieldValues)
      );
    }
  }
}
