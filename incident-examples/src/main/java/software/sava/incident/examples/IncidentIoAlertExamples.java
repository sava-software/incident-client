package software.sava.incident.examples;

import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentSeverity;
import software.sava.incident.io.alert.AlertEvent;
import software.sava.incident.io.alert.IncidentIoAlertEventClient;

import java.net.http.HttpClient;
import java.time.ZonedDateTime;

import static java.time.ZoneOffset.UTC;

public final class IncidentIoAlertExamples {

  static void main(final String[] args) {
    final var alertSourceConfigId = args[0];
    final var token = args[1];

    try (final var httpClient = HttpClient.newHttpClient()) {
      final var client = IncidentIoAlertEventClient.clientBuilder()
          .alertSourceConfigId(alertSourceConfigId)
          .token(token)
          .httpClient(httpClient)
          .createClient();

      // native: fire, then resolve with the same deduplication key
      final var firing = AlertEvent.build()
          .title("Disk full on db-1")
          .status(AlertEvent.Status.firing)
          .deduplicationKey("db-1-disk")
          .description("`/var` is at **99%**.")
          .sourceUrl("https://monitor.example.com/alerts/db-1-disk")
          .metadata("service", "db")
          .create();
      System.out.println(client.sendAlertEvent(firing).join().message());

      final var resolved = AlertEvent.build()
          .title("Disk space recovered on db-1")
          .status(AlertEvent.Status.resolved)
          .deduplicationKey("db-1-disk")
          .create();
      System.out.println(client.sendAlertEvent(resolved).join().message());

      // provider-neutral
      final var incidentClient = client.incidentClientBuilder()
          .resolveTitle("Recovered")
          .createClient();
      final var response = incidentClient.reportIncident(IncidentAlert.build()
          .key("validator-07-leader-slot")
          .summary("Validator missed its leader slot")
          .details("No block produced for slot 350000000.")
          .severity(IncidentSeverity.CRITICAL)
          .source("validator-07.example.com")
          .timestamp(ZonedDateTime.now(UTC))
          .customDetail("slot", 350000000L)
          .create()).join();
      incidentClient.resolveIncident(response.key()).join();
    }
  }
}
