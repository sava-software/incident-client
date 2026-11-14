package software.sava.incident.pagerduty.event.client;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;

/// The `http` endpoint of a wire test's server, from the address it is bound to: its literal
/// IP address, in brackets when it is IPv6 (the loopback address is `::1` under
/// `java.net.preferIPv6Addresses`), and its port. Never a host name, which can resolve to an
/// address the server is not listening on, and never a concatenation, which leaves an IPv6
/// literal without its brackets.
public final class TestEndpoint {

  public static URI http(final InetSocketAddress address) {
    return http(address, null);
  }

  /// As [#http(InetSocketAddress)], with `path`, which opens with a slash and carries no
  /// query.
  public static URI http(final InetSocketAddress address, final String path) {
    try {
      // this constructor puts an IPv6 literal in brackets
      return new URI("http", null, address.getAddress().getHostAddress(), address.getPort(), path, null, null);
    } catch (final URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  private TestEndpoint() {
  }
}
