package software.sava.incident.webhook;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// The endpoint is the server's address as a literal: an IPv6 one in brackets, never the
/// name an address happens to carry, with the path when one is given.
final class TestEndpointTests {

  @Test
  void theEndpointIsTheBoundAddressAsALiteral() throws UnknownHostException {
    final var v4 = new byte[]{127, 0, 0, 1};
    final var v6 = new byte[16];
    v6[15] = 1;
    assertEquals(URI.create("http://127.0.0.1:50123"),
        TestEndpoint.http(new InetSocketAddress(InetAddress.getByAddress(v4), 50123)));
    assertEquals(URI.create("http://[0:0:0:0:0:0:0:1]:50123"),
        TestEndpoint.http(new InetSocketAddress(InetAddress.getByAddress(v6), 50123)));
    assertEquals(URI.create("http://127.0.0.1:50123"),
        TestEndpoint.http(new InetSocketAddress(InetAddress.getByAddress("localhost", v4), 50123)));
    assertEquals(URI.create("http://127.0.0.1:50123/hook"),
        TestEndpoint.http(new InetSocketAddress(InetAddress.getByAddress(v4), 50123), "/hook"));
    assertEquals(URI.create("http://[0:0:0:0:0:0:0:1]:50123/v2/incidents"),
        TestEndpoint.http(new InetSocketAddress(InetAddress.getByAddress(v6), 50123), "/v2/incidents"));
  }
}
