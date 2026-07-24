package software.sava.incident.pagerduty.event.client;

import com.sun.net.httpserver.HttpServer;

public interface EventClientTest extends ClientTest {

  default void test(final HttpServer httpServer) {
    final int port = httpServer.getAddress().getPort();
    final var client = PagerDutyEventClient.clientBuilder()
        .defaultClientName("test-" + port)
        // 127.0.0.1, never localhost: a ::1 resolution can reach another JVM's
        // wildcard bind on the same port number under parallel module runs
        .endpoint("http://127.0.0.1:" + port)
        .defaultRoutingKey("routing-key-" + port)
        .authToken("auth-token-" + port)
        .createClient();
    test(client);
  }

  void test(final PagerDutyEventClient client);
}
