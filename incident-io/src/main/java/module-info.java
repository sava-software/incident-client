module software.sava.incident_io {
  requires java.net.http;

  requires transitive systems.comodal.json_iterator;
  requires software.sava.rpc;

  requires transitive software.sava.incident_core;

  exports software.sava.incident.io;
  exports software.sava.incident.io.config;
}
