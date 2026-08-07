package software.sava.incident.io;

import software.sava.incident.core.request.PostRequest;
import software.sava.incident.core.request.Request;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// A `POST /v2/incidents` request (`Incidents V2#Create`). Field names and shapes
/// follow `IncidentsCreatePayloadV2`. The API requires `idempotency_key` and
/// `visibility`: [Builder#build()] defaults the key to a random UUID and rejects a
/// missing visibility, so a request that reaches the wire is never missing either.
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

  /// Name of the Slack channel to create for this incident; serialized as
  /// `slack_channel_name_override`.
  String slackChannelNameOverride();

  /// Custom-field values, one entry per `custom_field_id`; serialized as
  /// `custom_field_entries`.
  Collection<CustomFieldEntry> customFieldEntries();

  Collection<IncidentTimestampValue> incidentTimestampValues();

  /// Only meaningful when `mode` is `retrospective`.
  RetrospectiveIncidentOptions retrospectiveIncidentOptions();

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

  /// A `CustomFieldValuePayloadV2`. Which field carries the value depends on the custom
  /// field's type: `valueText` for `text`, `valueOptionId` for `single_select` and
  /// `multi_select`, `valueCatalogEntryId` for catalog-backed fields, `valueLink` for
  /// `link`, and `valueNumeric` for `numeric` (the API takes the number as a string).
  /// Blank fields are omitted, and a value with nothing set at all is dropped from its
  /// entry rather than serialized as `{}`.
  ///
  /// The payload's deprecated `value_timestamp` is not supported; use
  /// [IncidentTimestampValue] instead.
  record CustomFieldValue(String id,
                          String valueCatalogEntryId,
                          String valueLink,
                          String valueNumeric,
                          String valueOptionId,
                          String valueText) {

    public static CustomFieldValue text(final String valueText) {
      return new CustomFieldValue(null, null, null, null, null, valueText);
    }

    public static CustomFieldValue optionId(final String valueOptionId) {
      return new CustomFieldValue(null, null, null, null, valueOptionId, null);
    }

    public static CustomFieldValue catalogEntryId(final String valueCatalogEntryId) {
      return new CustomFieldValue(null, valueCatalogEntryId, null, null, null, null);
    }

    public static CustomFieldValue link(final String valueLink) {
      return new CustomFieldValue(null, null, valueLink, null, null, null);
    }

    public static CustomFieldValue numeric(final String valueNumeric) {
      return new CustomFieldValue(null, null, null, valueNumeric, null, null);
    }
  }

  /// A `CustomFieldEntryPayloadV2`. A `multi_select` field takes one value per selected
  /// option; an empty `values` list unsets the field.
  record CustomFieldEntry(String customFieldId, List<CustomFieldValue> values) {

    public CustomFieldEntry {
      values = values == null ? List.of() : List.copyOf(values);
    }

    /// Convenience for the common single `text` value case.
    public CustomFieldEntry(final String customFieldId, final String valueText) {
      this(customFieldId, List.of(CustomFieldValue.text(valueText)));
    }
  }

  /// An `IncidentTimestampValuePayloadV2`. `value` serializes as RFC 3339 and is omitted
  /// when null.
  record IncidentTimestampValue(String incidentTimestampId, OffsetDateTime value) {
  }

  /// A `RetrospectiveIncidentOptionsV2`. `externalId` — the `123` in `INC-123` — is
  /// serialized as a JSON number and omitted when null; incident.io gates its use per
  /// organisation.
  record RetrospectiveIncidentOptions(Long externalId, String postmortemDocumentUrl, String slackChannelId) {
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
    private String slackChannelNameOverride;
    private Collection<CustomFieldEntry> customFieldEntries;
    private Collection<IncidentTimestampValue> incidentTimestampValues;
    private RetrospectiveIncidentOptions retrospectiveIncidentOptions;

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

    public Builder slackChannelNameOverride(final String slackChannelNameOverride) {
      this.slackChannelNameOverride = slackChannelNameOverride;
      return this;
    }

    public Builder customFieldEntries(final Collection<CustomFieldEntry> customFieldEntries) {
      this.customFieldEntries = customFieldEntries;
      return this;
    }

    /// Sugar for the text-only case: replaces the entries with one single-value `text`
    /// entry per map key, in the map's iteration order.
    public Builder customFieldValues(final Map<String, String> customFieldValues) {
      if (customFieldValues == null) {
        this.customFieldEntries = null;
        return this;
      }
      final var entries = new ArrayList<CustomFieldEntry>(customFieldValues.size());
      customFieldValues.forEach((customFieldId, valueText) ->
          entries.add(new CustomFieldEntry(customFieldId, valueText)));
      this.customFieldEntries = entries;
      return this;
    }

    public Builder incidentTimestampValues(final Collection<IncidentTimestampValue> incidentTimestampValues) {
      this.incidentTimestampValues = incidentTimestampValues;
      return this;
    }

    public Builder retrospectiveIncidentOptions(final RetrospectiveIncidentOptions retrospectiveIncidentOptions) {
      this.retrospectiveIncidentOptions = retrospectiveIncidentOptions;
      return this;
    }

    /// Throws [IllegalStateException] if `visibility` is unset: the API rejects a create
    /// without one and there is no safe default, since guessing between `public` and
    /// `private` would decide an incident's exposure. An unset `idempotencyKey` defaults to
    /// a random UUID — the key only has to be stable across retries of *this* request, and
    /// it is captured here at build time.
    public CreateIncidentRequest build() {
      if (visibility == null || visibility.isBlank()) {
        throw new IllegalStateException("CreateIncidentRequest 'visibility' is required.");
      }
      final var key = idempotencyKey == null || idempotencyKey.isBlank()
          ? UUID.randomUUID().toString()
          : idempotencyKey;
      return new CreateIncidentRequestRecord(
          timeout(),
          key,
          name,
          summary,
          incidentTypeId,
          incidentRoleAssignments == null ? List.of() : List.copyOf(incidentRoleAssignments),
          mode,
          severityId,
          statusId,
          visibility,
          slackTeamId,
          slackChannelNameOverride,
          customFieldEntries == null ? List.of() : List.copyOf(customFieldEntries),
          incidentTimestampValues == null ? List.of() : List.copyOf(incidentTimestampValues),
          retrospectiveIncidentOptions
      );
    }
  }
}
