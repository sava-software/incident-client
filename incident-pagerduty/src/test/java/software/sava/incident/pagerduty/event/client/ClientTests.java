package software.sava.incident.pagerduty.event.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DynamicTest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

final class ClientTests {

  private ClientTests() {
  }

  static DynamicTest createTest(final ClientTest test) {
    return dynamicTest(test.getClass().getSimpleName(), () -> runTest(test));
  }

  static DynamicTest createTest(final ServiceLoader.Provider<? extends ClientTest> testProvider) {
    return dynamicTest(testProvider.type().getSimpleName(), () -> runTest(testProvider.get()));
  }

  private static void runTest(final ClientTest clientTest) {
    final var httpServer = createServer();
    try {
      final var testFuture = createContext(clientTest, httpServer);
      // Run async within the future in case the future is terminated on the server side.
      // This will ensure an early termination and proper reporting in the event of a server side exception.
      testFuture.completeAsync(() -> {
        try {
          clientTest.test(httpServer);
        } catch (final IOException e) {
          throw new UncheckedIOException(e);
        }
        return null;
      }).join();
    } finally {
      httpServer.stop(0);
    }
  }

  private static CompletableFuture<Void> createContext(final ClientTest clientTest,
                                                       final HttpServer httpServer) {
    final var testFuture = new CompletableFuture<Void>();
    clientTest.createContext(httpServer, (path, httpHandler) -> {
          httpServer.createContext(path, httpExchange -> {
                try {
                  httpHandler.handle(httpExchange);
                } catch (final Throwable throwable) {
                  testFuture.completeExceptionally(throwable);
                }
              }
          );
        }
    );
    return testFuture;
  }

  /// The `http` endpoint of a server bound to `address`: its literal IP address, in brackets
  /// when it is IPv6 (the loopback address is `::1` under `java.net.preferIPv6Addresses`),
  /// and its port. Never a host name, which can resolve to an address the server is not
  /// listening on.
  static URI endpoint(final InetSocketAddress address) {
    try {
      // this constructor puts an IPv6 literal in brackets
      return new URI("http", null, address.getAddress().getHostAddress(), address.getPort(), null, null, null);
    } catch (final URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  /// Listens on the loopback address alone, never the wildcard address. macOS can give a
  /// wildcard bind of port 0 a port that another process's loopback listener already holds,
  /// and a connection to 127.0.0.1 then reaches that listener instead of this server: the
  /// test fails at once, or waits out the client's request timeout, which PIT reports as
  /// TIMED_OUT. A second loopback bind of a held port is refused, and a loopback listener
  /// takes 127.0.0.1 connections ahead of a wildcard one, so this bind cannot be captured.
  static HttpServer createServer() {
    try {
      final var httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      httpServer.setExecutor(ForkJoinPool.commonPool());
      httpServer.start();
      return httpServer;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
