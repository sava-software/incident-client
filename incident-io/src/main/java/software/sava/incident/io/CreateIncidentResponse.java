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

  Mode mode();

  String name();

  String permalink();

  String postmortemDocumentUrl();

  String reference();

  SeverityV2 severity();

  String slackChannelId();

  String slackChannelName();

  String slackTeamId();

  String summary();

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

  record ActorV2(String email, String id, String name, String slackUserId) {
  }

  record CustomFieldEntryV2(String customFieldId, String customFieldName, String customFieldType,
                            Object value) {
  }

  record IncidentDurationMetricWithValueV2(String durationMetricId, String durationMetricName,
                                           Long valueSeconds) {
  }

  record ExternalIssueReferenceV2(String issueId, String issueReference, String issueTitle,
                                  String issueUrl, String provider) {
  }

  record IncidentRoleAssignmentV2(ActorV2 assignee, IncidentRoleV2 role) {
  }

  record IncidentRoleV2(String id, String name, String description, String roleType) {
  }

  record IncidentStatusV2(String id, String name, String description, String category) {
  }

  record IncidentTimestampWithValueV2(String timestampId, String timestampName, OffsetDateTime value) {
  }

  record IncidentTypeV2(String id, String name, String description) {
  }

  record SeverityV2(String id, String name, String description) {
  }
}
