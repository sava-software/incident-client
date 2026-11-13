package software.sava.incident.pagerduty.event.client;

import com.sun.net.httpserver.HttpServer;

public interface EventClientTest extends ClientTest {

  default void test(final HttpServer httpServer) {
    final var address = httpServer.getAddress();
    final int port = address.getPort();
    final var client = PagerDutyEventClient.clientBuilder()
        .defaultClientName("test-" + port)
        // the address the server is bound to, as a literal: never localhost, which can
        // resolve to another address family and reach another process's listener on the
        // same port number
        .endpoint(ClientTests.endpoint(address))
        .defaultRoutingKey("routing-key-" + port)
        .createClient();
    test(client);
  }

  void test(final PagerDutyEventClient client);
}
