package software.sava.incident.core.client;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/// Pins the exported transport-builder base every provider client extends. The provider
/// modules exercise it only through their own concrete builders, in other Gradle modules,
/// so nothing here would otherwise observe it.
final class HttpApiClientBuilderTests {

  private static final class TestBuilder extends HttpApiClient.Builder<TestBuilder> {

    void applyDefaults() {
      setDefaults();
    }
  }

  @Test
  void everySetterReturnsTheSameBuilderSoChainingAccumulates() {
    final var builder = new TestBuilder();
    final var endpoint = URI.create("https://api.example.com");
    final var httpClient = HttpClient.newHttpClient();
    final Duration timeout = Duration.ofSeconds(3);
    final java.util.function.UnaryOperator<HttpRequest.Builder> extend = request -> request;
    final java.util.function.BiPredicate<HttpResponse<?>, byte[]> test = (response, body) -> true;

    assertSame(builder, builder.endpoint(endpoint));
    assertSame(builder, builder.endpoint("https://api.example.com"));
    assertSame(builder, builder.httpClient(httpClient));
    assertSame(builder, builder.requestTimeout(timeout));
    assertSame(builder, builder.extendRequest(extend));
    assertSame(builder, builder.testResponse(test));

    // the accessors read back exactly what was set
    assertEquals(endpoint, builder.endpoint());
    assertSame(httpClient, builder.httpClient());
    assertEquals(timeout, builder.requestTimeout());
    assertSame(extend, builder.extendRequest());
    assertSame(test, builder.testResponse());
  }

  @Test
  void stringEndpointOverloadParsesToTheSameUri() {
    final var fromString = new TestBuilder().endpoint("https://api.example.com/v2");
    assertEquals(URI.create("https://api.example.com/v2"), fromString.endpoint());
  }

  @Test
  void setDefaultsFillsOnlyTheUnsetTransportValues() {
    final var builder = new TestBuilder();
    assertNull(builder.httpClient());
    assertNull(builder.requestTimeout());

    builder.applyDefaults();

    assertNotNull(builder.httpClient());
    assertEquals(Duration.ofSeconds(8), builder.requestTimeout());
  }

  @Test
  void setDefaultsNeverOverwritesCallerSuppliedValues() {
    final var httpClient = HttpClient.newHttpClient();
    final var builder = new TestBuilder()
        .httpClient(httpClient)
        .requestTimeout(Duration.ofSeconds(30));

    builder.applyDefaults();

    // a defaults hook that overwrote these would silently discard the caller's transport
    assertSame(httpClient, builder.httpClient());
    assertEquals(Duration.ofSeconds(30), builder.requestTimeout());
  }
}
