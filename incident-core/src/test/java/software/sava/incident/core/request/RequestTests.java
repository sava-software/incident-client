package software.sava.incident.core.request;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/// Pins the exported request scaffolding. `CreateIncidentRequest.Builder` in `incident-io`
/// extends `Request.Builder` and `CreateIncidentRequestRecord` extends `BaseRequest`, but
/// those kills land in another module's suite.
final class RequestTests {

  private static final class TestRequest extends BaseRequest implements PostRequest {

    private TestRequest(final Duration timeout) {
      super(timeout);
    }

    @Override
    public String body() {
      return "{}";
    }
  }

  @Test
  void builderTimeoutRoundTripsAndChains() {
    final var builder = new Request.Builder();
    final var timeout = Duration.ofSeconds(11);

    assertSame(builder, builder.timeout(timeout));
    assertEquals(timeout, builder.timeout());
  }

  @Test
  void builderTimeoutStartsUnset() {
    assertNull(new Request.Builder().timeout());
  }

  @Test
  void baseRequestExposesTheConstructedTimeout() {
    final var timeout = Duration.ofSeconds(7);
    final Request request = new TestRequest(timeout);
    assertEquals(timeout, request.timeout());
  }
}
