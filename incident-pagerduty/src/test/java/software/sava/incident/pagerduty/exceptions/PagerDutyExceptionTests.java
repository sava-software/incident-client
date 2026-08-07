package software.sava.incident.pagerduty.exceptions;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/// Pins the PagerDuty exception envelope. `incident-io` and `incident-webhook` both had
/// exception tests; this module did not.
final class PagerDutyExceptionTests {

  private record TestHttpResponse(int statusCode) implements HttpResponse<Object> {

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<Object>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(Map.of(), (name, value) -> true);
    }

    @Override
    public Object body() {
      return null;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return null;
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }

  // ── PagerDutyParseException ────────────────────────────────────────────────

  @Test
  void unhandledFieldNamesTheContextAndField() {
    final var response = new TestHttpResponse(200);
    final var ex = PagerDutyParseException.unhandledField(response, "event response", "bogus", "{\"bogus\":1}");
    assertNotNull(ex);
    assertEquals("Unhandled event response field 'bogus'", ex.getMessage());
    assertSame(response, ex.httpResponse());
    assertEquals("{\"bogus\":1}", ex.buffer());
  }

  @Test
  void parseExceptionRetriesOnlyOnAbsentServerOrThrottledResponses() {
    // retry is a property of the response class, not of parsing: 5xx and 429 are transient,
    // every other 4xx is a permanent contract mismatch
    assertTrue(new PagerDutyParseException(null, "no response").canBeRetried());
    assertTrue(new PagerDutyParseException(new TestHttpResponse(500), "server").canBeRetried());
    assertTrue(new PagerDutyParseException(new TestHttpResponse(503), "server").canBeRetried());
    assertTrue(new PagerDutyParseException(new TestHttpResponse(429), "throttled").canBeRetried());

    assertFalse(new PagerDutyParseException(new TestHttpResponse(499), "boundary").canBeRetried());
    assertFalse(new PagerDutyParseException(new TestHttpResponse(400), "bad request").canBeRetried());
    assertFalse(new PagerDutyParseException(new TestHttpResponse(428), "just below 429").canBeRetried());
    assertFalse(new PagerDutyParseException(new TestHttpResponse(430), "just above 429").canBeRetried());
    assertFalse(new PagerDutyParseException(new TestHttpResponse(202), "accepted").canBeRetried());
  }

  @Test
  void parseExceptionCarriesItsResponseCauseAndBuffer() {
    final var response = new TestHttpResponse(422);
    final var cause = new IllegalStateException("bad json");

    final var withMessageAndBuffer = new PagerDutyParseException(response, "message", "raw-buffer");
    assertSame(response, withMessageAndBuffer.httpResponse());
    assertEquals("message", withMessageAndBuffer.getMessage());
    assertEquals("raw-buffer", withMessageAndBuffer.buffer());

    final var withCause = new PagerDutyParseException(response, "message", cause);
    assertSame(cause, withCause.getCause());
    assertNull(withCause.buffer());

    final var messageOnly = new PagerDutyParseException(response, "message");
    assertNull(messageOnly.getCause());
    assertNull(messageOnly.buffer());

    final var causeOnly = new PagerDutyParseException(response, cause);
    assertSame(cause, causeOnly.getCause());
    assertNull(causeOnly.buffer());

    final var causeAndBuffer = new PagerDutyParseException(response, cause, "raw-2");
    assertSame(cause, causeAndBuffer.getCause());
    assertEquals("raw-2", causeAndBuffer.buffer());

    // a parse failure carries no service error code or error list of its own
    assertEquals(0L, messageOnly.errorCode());
    assertTrue(messageOnly.errors().isEmpty());
  }

  // ── PagerDutyRequestException ─────────────────────────────────────────────

  @Test
  void requestExceptionExposesTheParsedEnvelope() {
    final var response = new TestHttpResponse(400);
    final var builder = PagerDutyRequestException.build(response);
    builder.status("invalid event action");
    assertSame(builder, builder.message("Event object is invalid"));
    assertSame(builder, builder.errorCode(2001));
    builder.error("Length of 'routing_key' is incorrect");

    final var ex = builder.create();
    assertEquals("invalid event action", ex.status());
    assertEquals("Event object is invalid", ex.getMessage());
    assertEquals(2001L, ex.errorCode());
    assertSame(response, ex.httpResponse());
    assertEquals(1, ex.errors().size());
    assertEquals("Length of 'routing_key' is incorrect", ex.errors().getFirst());
  }

  @Test
  void requestExceptionRetriesOnlyOnAbsentServerOrThrottledResponses() {
    assertTrue(PagerDutyRequestException.build(null).create().canBeRetried());
    assertTrue(PagerDutyRequestException.build(new TestHttpResponse(500)).create().canBeRetried());
    assertTrue(PagerDutyRequestException.build(new TestHttpResponse(429)).create().canBeRetried());
    assertFalse(PagerDutyRequestException.build(new TestHttpResponse(499)).create().canBeRetried());
    assertFalse(PagerDutyRequestException.build(new TestHttpResponse(400)).create().canBeRetried());
    assertFalse(PagerDutyRequestException.build(new TestHttpResponse(428)).create().canBeRetried());
    assertFalse(PagerDutyRequestException.build(new TestHttpResponse(430)).create().canBeRetried());
  }

  @Test
  void errorListGrowsFromNoneToOneToManyAndIsImmutableAboveOne() {
    final var none = PagerDutyRequestException.build(null).create();
    assertTrue(none.errors().isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> none.errors().add("x"));

    final var oneBuilder = PagerDutyRequestException.build(null);
    oneBuilder.error("only");
    final var one = oneBuilder.create();
    assertEquals(1, one.errors().size());
    assertThrows(UnsupportedOperationException.class, () -> one.errors().add("x"));

    // above one the accumulated list must not escape mutable
    final var manyBuilder = PagerDutyRequestException.build(null);
    manyBuilder.error("first");
    manyBuilder.error("second");
    manyBuilder.error("third");
    final var many = manyBuilder.create();
    assertEquals(3, many.errors().size());
    assertEquals("first", many.errors().getFirst());
    assertEquals("third", many.errors().get(2));
    assertThrows(UnsupportedOperationException.class, () -> many.errors().add("x"));
  }

  @Test
  void requestExceptionDefaultsAreZeroAndNull() {
    final var ex = PagerDutyRequestException.build(null).create();
    assertEquals(0L, ex.errorCode(), "an unparsed error code stays zero, not a sentinel");
    assertNull(ex.httpResponse());
    assertNull(ex.status());
  }

  @Test
  void toStringNamesEveryEnvelopeField() {
    final var builder = PagerDutyRequestException.build(new TestHttpResponse(429));
    builder.status("throttled");
    builder.message("Too many requests");
    builder.errorCode(2002);
    builder.error("slow down");
    final var text = builder.create().toString();

    assertTrue(text.startsWith("PagerDutyRequestException{status='throttled'"));
    assertTrue(text.contains("errorCode=2002"));
    assertTrue(text.contains("slow down"));
    assertTrue(text.contains("httpResponse="));
  }
}
