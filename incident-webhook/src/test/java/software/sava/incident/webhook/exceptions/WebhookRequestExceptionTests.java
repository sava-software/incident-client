package software.sava.incident.webhook.exceptions;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

final class WebhookRequestExceptionTests {

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
      return URI.create("https://hooks.example.com/T000/B000/secret");
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_2;
    }
  }

  @Test
  void carriesStatusAndBody() {
    final var response = new StubResponse(400);
    final var exception = new WebhookRequestException(response, "invalid_payload".getBytes(UTF_8));
    assertEquals(400L, exception.errorCode());
    assertSame(response, exception.httpResponse());
    assertEquals("invalid_payload", exception.body());
    assertEquals(List.of("invalid_payload"), exception.errors());
    assertEquals("Webhook request failed with status 400: 'invalid_payload'", exception.getMessage());
    assertEquals("WebhookRequestException{errorCode=400, body='invalid_payload'}", exception.toString());
    assertFalse(exception.canBeRetried());
  }

  @Test
  void nullBodyBytesReadAsEmpty() {
    final var exception = new WebhookRequestException(new StubResponse(502), (byte[]) null);
    assertEquals("", exception.body());
    assertEquals(List.of(), exception.errors());
    assertEquals("Webhook request failed with status 502: ''", exception.getMessage());
  }

  @Test
  void messageOmitsTheRequestUri() {
    final var exception = new WebhookRequestException(new StubResponse(404), "not found");
    // the webhook URL is the credential; it must never leak through the message
    assertFalse(exception.getMessage().contains("hooks.example.com"));
    assertFalse(exception.toString().contains("hooks.example.com"));
  }

  @Test
  void retryBoundaries() {
    assertFalse(new WebhookRequestException(new StubResponse(400), "").canBeRetried());
    assertFalse(new WebhookRequestException(new StubResponse(404), "").canBeRetried());
    assertFalse(new WebhookRequestException(new StubResponse(499), "").canBeRetried());
    assertTrue(new WebhookRequestException(new StubResponse(500), "").canBeRetried());
    assertTrue(new WebhookRequestException(new StubResponse(503), "").canBeRetried());
    assertTrue(new WebhookRequestException(new StubResponse(429), "").canBeRetried());
  }

  @Test
  void nullResponseCanBeRetried() {
    final var exception = new WebhookRequestException(null, (String) null);
    assertTrue(exception.canBeRetried());
    assertEquals(-1L, exception.errorCode());
    assertNull(exception.httpResponse());
    assertEquals("", exception.body());
    assertEquals(List.of(), exception.errors());
    assertEquals("Webhook request failed with status -1: ''", exception.getMessage());
  }

  @Test
  void emptyBodyYieldsNoErrors() {
    final var exception = new WebhookRequestException(new StubResponse(500), new byte[0]);
    assertEquals("", exception.body());
    assertEquals(List.of(), exception.errors());
  }
}
