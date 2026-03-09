package software.sava.incident.examples;

import software.sava.incident.io.CreateIncidentRequest;
import software.sava.incident.io.IncidentIoClient;

import java.net.http.HttpClient;
import java.util.UUID;

public final class IncidentIoExamples {

  static void main(final String[] args) {
    final var bearerToken = args[0];

    final var clientBuilder = IncidentIoClient.clientBuilder();
    clientBuilder.bearerToken(bearerToken);

    try (final var httpClient = HttpClient.newHttpClient()) {
      final var client = clientBuilder.httpClient(httpClient).createClient();

      final var request = CreateIncidentRequest
          .requestBuilder()
          .idempotencyKey(UUID.randomUUID().toString())
          .name("Test Incident")
          .summary("Test Java client")
          .visibility(CreateIncidentRequest.Visibility.PRIVATE)
          .build();

      final var responseFuture = client.createIncident(request);

      final var response = responseFuture.join();
      System.out.println(response);
    }
  }
}
