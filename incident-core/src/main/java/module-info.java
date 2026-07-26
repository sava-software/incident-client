module software.sava.incident_core {
  requires java.net.http;
  requires transitive systems.comodal.json_iterator;

  uses software.sava.incident.core.api.IncidentClientFactory;

  exports software.sava.incident.core.api;
  exports software.sava.incident.core.client;
  exports software.sava.incident.core.config;
  exports software.sava.incident.core.json;
  exports software.sava.incident.core.request;
}
