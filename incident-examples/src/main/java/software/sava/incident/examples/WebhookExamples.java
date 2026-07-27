package software.sava.incident.examples;

import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentSeverity;
import software.sava.incident.webhook.WebhookClient;
import software.sava.incident.webhook.WebhookFormats;

import java.net.http.HttpClient;

public final class WebhookExamples {

  static void main(final String[] args) {
    final var webhookUrl = args[0];

    try (final var httpClient = HttpClient.newHttpClient()) {
      final var client = WebhookClient.clientBuilder()
          .endpoint(webhookUrl)
          .httpClient(httpClient)
          .createClient();

      final var incidentClient = client.incidentClient(WebhookFormats.SLACK_TEXT);

      final var response = incidentClient.reportIncident(IncidentAlert.build()
          .summary("Validator missed its leader slot")
          .details("No block produced for slot 350000000.")
          .severity(IncidentSeverity.CRITICAL)
          .source("validator-01.example.com")
          .customDetail("slot", 350000000L)
          .create()).join();

      System.out.println(response);
    }
  }
}
