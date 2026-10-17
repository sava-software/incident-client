package software.sava.incident.pagerduty.event.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DynamicTest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
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

  private static HttpServer createServer() {
    try {
      final var httpServer = HttpServer.create(new InetSocketAddress(0), 0);
      httpServer.setExecutor(ForkJoinPool.commonPool());
      httpServer.start();
      return httpServer;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
