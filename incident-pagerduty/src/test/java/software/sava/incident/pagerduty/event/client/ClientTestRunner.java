package software.sava.incident.pagerduty.event.client;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientTestRunner {

  /// The harness's server listens on the loopback address alone, for the reason
  /// [ClientTests#createServer()] gives, and the harness points its client at the address
  /// the server is bound to.
  @Test
  void theHarnessServerListensOnLoopbackAloneAndItsClientTargetsIt() {
    final var httpServer = ClientTests.createServer();
    try {
      final var bound = httpServer.getAddress();
      assertTrue(bound.getAddress().isLoopbackAddress(), "bound to " + bound.getAddress());
      final var endpoint = new URI[1];
      final EventClientTest harness = new EventClientTest() {
        @Override
        public void createContext(final HttpServer server, final BiConsumer<String, HttpHandler> contexts) {
        }

        @Override
        public void test(final PagerDutyEventClient client) {
          endpoint[0] = client.endpoint();
        }
      };
      harness.test(httpServer);
      assertEquals(ClientTests.endpoint(bound), endpoint[0]);
    } finally {
      httpServer.stop(0);
    }
  }

  /// The client's endpoint is the server's address as a literal: an IPv6 one in brackets,
  /// and never the name an address happens to carry.
  @Test
  void theEndpointIsTheBoundAddressAsALiteral() throws UnknownHostException {
    final var v4 = new byte[]{127, 0, 0, 1};
    final var v6 = new byte[16];
    v6[15] = 1;
    assertEquals(URI.create("http://127.0.0.1:50123"),
        ClientTests.endpoint(new InetSocketAddress(InetAddress.getByAddress(v4), 50123)));
    assertEquals(URI.create("http://[0:0:0:0:0:0:0:1]:50123"),
        ClientTests.endpoint(new InetSocketAddress(InetAddress.getByAddress(v6), 50123)));
    assertEquals(URI.create("http://127.0.0.1:50123"),
        ClientTests.endpoint(new InetSocketAddress(InetAddress.getByAddress("localhost", v4), 50123)));
  }

  @TestFactory
  List<DynamicTest> runEventClientTests() {
    return List.of(
        ClientTests.createTest(new EventClientTests()),
        ClientTests.createTest(new EventClientEdgeCaseTests())
    );
  }
}
