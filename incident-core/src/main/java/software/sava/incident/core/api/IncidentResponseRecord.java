package software.sava.incident.core.api;

record IncidentResponseRecord(String key,
                              String status,
                              String url) implements IncidentResponse {
}
