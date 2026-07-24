package software.sava.incident.io.exceptions;

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

final class IncidentIoParseExceptionTests {

  private record StubResponse(int statusCode) implements HttpResponse<byte[]> {

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<byte[]>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(Map.of(), (_, _) -> true);
    }

    @Override
    public byte[] body() {
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
      return HttpClient.Version.HTTP_2;
    }
  }

  @Test
  void retriableStatusCodes() {
    assertTrue(new IncidentIoParseException(null, "no response").canBeRetried());
    assertTrue(new IncidentIoParseException(new StubResponse(500), "server error").canBeRetried());
    assertTrue(new IncidentIoParseException(new StubResponse(503), "unavailable").canBeRetried());
    assertTrue(new IncidentIoParseException(new StubResponse(429), "rate limited").canBeRetried());
    // 499 sits just below the 5xx boundary and is not the 429 rate-limit case
    assertFalse(new IncidentIoParseException(new StubResponse(499), "client closed").canBeRetried());
    assertFalse(new IncidentIoParseException(new StubResponse(400), "bad request").canBeRetried());
  }

  @Test
  void accessors() {
    final var response = new StubResponse(400);
    final var cause = new RuntimeException("parse failure");
    final var exception = new IncidentIoParseException(response, "message-1", cause);
    assertSame(response, exception.httpResponse());
    assertSame(cause, exception.getCause());
    assertEquals("message-1", exception.getMessage());
    assertEquals(0, exception.errorCode());
    assertEquals(java.util.List.of(), exception.errors());
  }
}
