module software.sava.incident_core {
  requires java.net.http;
  requires transitive systems.comodal.json_iterator;

  exports software.sava.incident.core.client;
  exports software.sava.incident.core.config;
  exports software.sava.incident.core.request;
}
