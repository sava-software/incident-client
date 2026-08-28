package software.sava.incident.io;

import java.time.OffsetDateTime;
import java.util.Collection;

public interface CreateIncidentResponse {

  String callUrl();

  OffsetDateTime createdAt();

  ActorV2 creator();

  Collection<CustomFieldEntryV2> customFieldEntries();

  Collection<IncidentDurationMetricWithValueV2> durationMetrics();

  ExternalIssueReferenceV2 externalIssueReference();

  boolean hasDebrief();

  String id();

  Collection<IncidentRoleAssignmentV2> incidentRoleAssignments();

  IncidentStatusV2 incidentStatus();

  Collection<IncidentTimestampWithValueV2> incidentTimestampValues();

  IncidentTypeV2 incidentType();

  OffsetDateTime lastActivityAt();

  Mode mode();

  String msTeamsChannelUrl();

  String name();

  String permalink();

  Collection<String> postmortemDocumentIds();

  String postmortemDocumentUrl();

  String reference();

  SeverityV2 severity();

  String slackChannelId();

  String slackChannelName();

  String slackChannelUrl();

  String slackTeamId();

  String summary();

  Collection<String> teamIds();

  OffsetDateTime updatedAt();

  Visibility visibility();

  Double workloadMinutesLate();

  Double workloadMinutesSleeping();

  Double workloadMinutesTotal();

  Double workloadMinutesWorking();

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

  /// An `ActorV2`: a union naming whatever created the incident. At most one member is
  /// set — an incident created through this client carries an [#apiKey()], not a
  /// [#user()] — and members the response omits are null.
  record ActorV2(AlertActorV2 alert, ApiKeyActorV2 apiKey, UserV2 user, WorkflowActorV2 workflow) {
  }

  record AlertActorV2(String id, String title) {
  }

  record ApiKeyActorV2(String id, String name) {
  }

  record WorkflowActorV2(String id, String name) {
  }

  /// A `UserV2`. `role` is deprecated upstream and no longer updated.
  record UserV2(String email, String id, String name, String role, String slackUserId) {
  }

  /// A `CustomFieldEntryV2`, flattened: the nested `custom_field` type info is inlined as
  /// the `customField*` components, and `values` holds every value the field carries — a
  /// `multi_select` has one per selected option. `customFieldOptions` is the field's whole
  /// configured option set, which is not the same as the selected `values`.
  record CustomFieldEntryV2(String customFieldId,
                            String customFieldName,
                            String customFieldType,
                            String customFieldDescription,
                            Collection<CustomFieldOptionV2> customFieldOptions,
                            Collection<CustomFieldValueV2> values) {
  }

  /// A `CustomFieldValueV2`. Which component carries the value follows the entry's
  /// `customFieldType`: `valueText` for `text`, `valueOption` for `single_select` and
  /// `multi_select`, `valueCatalogEntry` for catalog-backed fields, `valueLink` for
  /// `link`, and `valueNumeric` for `numeric`.
  record CustomFieldValueV2(EmbeddedCatalogEntryV2 valueCatalogEntry,
                            String valueLink,
                            String valueNumeric,
                            CustomFieldOptionV2 valueOption,
                            String valueText) {
  }

  record CustomFieldOptionV2(String customFieldId, String id, long sortKey, String value) {
  }

  record EmbeddedCatalogEntryV2(Collection<String> aliases, String externalId, String id, String name) {
  }

  /// An `IncidentDurationMetricWithValueV2`. `status` says whether `valueSeconds` still
  /// matches the incident's current timestamps: only `success` means it does, and the other
  /// documented values (`timestamps_missing`, `calculating`, `invalid_timestamps`) mean the
  /// timestamps moved since it was computed. It is kept as a `String` rather than an enum so
  /// a value upstream adds later is reported rather than silently read as null.
  /// `valueSeconds` is absent when no value has ever been calculated.
  record IncidentDurationMetricWithValueV2(String durationMetricId, String durationMetricName,
                                           Long valueSeconds, String status) {
  }

  record ExternalIssueReferenceV2(String issueName, String issuePermalink, String provider) {
  }

  record IncidentRoleAssignmentV2(UserV2 assignee, IncidentRoleV2 role) {
  }

  /// An `EmbeddedIncidentRoleV2`. `roleType` is one of `lead`, `reporter` or `custom`, and
  /// `required` is the only optional member — it reads false when the response omits it.
  record IncidentRoleV2(String id,
                        String name,
                        String description,
                        String roleType,
                        OffsetDateTime createdAt,
                        String instructions,
                        boolean required,
                        String shortform,
                        OffsetDateTime updatedAt) {
  }

  /// An `IncidentStatusV2`. `category` is one of `triage`, `declined`, `merged`,
  /// `canceled`, `live`, `learning`, `closed` or `paused`; `rank` orders statuses within
  /// their category.
  record IncidentStatusV2(String id,
                          String name,
                          String description,
                          String category,
                          OffsetDateTime createdAt,
                          long rank,
                          OffsetDateTime updatedAt) {
  }

  /// An `IncidentTimestampWithValueV2`, flattened: the nested `incident_timestamp` is
  /// inlined as the `timestamp*` components. `value` is absent until the timestamp is set.
  record IncidentTimestampWithValueV2(String timestampId,
                                      String timestampName,
                                      long timestampRank,
                                      OffsetDateTime value) {
  }

  /// An `IncidentTypeV2`. `createInTriage` is `always` or `optional` — kept as a `String`
  /// rather than an enum so a value upstream adds later is reported rather than silently
  /// read as null.
  record IncidentTypeV2(String id,
                        String name,
                        String description,
                        String createInTriage,
                        OffsetDateTime createdAt,
                        boolean isDefault,
                        boolean privateIncidentsOnly,
                        OffsetDateTime updatedAt) {
  }

  /// A `SeverityV2`. `rank` orders severities against each other.
  record SeverityV2(String id,
                    String name,
                    String description,
                    OffsetDateTime createdAt,
                    long rank,
                    OffsetDateTime updatedAt) {
  }
}
