module software.sava.pagerduty {
  requires java.net.http;

  requires transitive systems.comodal.json_iterator;
  requires software.sava.rpc;
  requires transitive software.sava.incident_core;

  exports software.sava.incident.pagerduty.event.client;
  exports software.sava.incident.pagerduty.event.data;
  exports software.sava.incident.pagerduty.event.service;
  exports software.sava.incident.pagerduty.exceptions;
}
