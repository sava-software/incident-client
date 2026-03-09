package software.sava.incident.pagerduty.event.client;

import com.sun.net.httpserver.HttpServer;

public interface EventClientTest extends ClientTest {

  default void test(final HttpServer httpServer) {
    final int port = httpServer.getAddress().getPort();
    final var client = PagerDutyEventClient.clientBuilder()
        .defaultClientName("test-" + port)
        .endpoint("http://localhost:" + port)
        .defaultRoutingKey("routing-key-" + port)
        .authToken("auth-token-" + port)
        .createClient();
    test(client);
  }

  void test(final PagerDutyEventClient client);
}
