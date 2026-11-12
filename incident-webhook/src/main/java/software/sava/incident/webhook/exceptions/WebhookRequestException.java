package software.sava.incident.webhook.exceptions;

import software.sava.incident.core.api.IncidentClientException;
import systems.comodal.jsoniter.JsonIterator;
import systems.comodal.jsoniter.ValueType;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

/// A non-2xx webhook response. Receivers signal failure by status code — Slack and
/// Telegram included — so the failure is never classified by its body, and the raw
/// response body is carried as-is. The one exception is [#retryAfter()], which also reads
/// the wait from a Telegram Bot API error body, leniently, as far as its first JSON object:
/// the body need not be well-formed JSON to count. It sits here, in the shared failure
/// rather than in a [software.sava.incident.webhook.WebhookFormat], so it works for
/// whatever format posted the request, a custom one posting to Telegram included. The message
/// deliberately omits the request URI: webhook URLs (e.g. Slack incoming webhooks) carry
/// the credential, and exception messages end up in logs.
public final class WebhookRequestException extends RuntimeException implements IncidentClientException {

  private final HttpResponse<?> httpResponse;
  private final String body;

  public WebhookRequestException(final HttpResponse<?> httpResponse, final byte[] body) {
    this(httpResponse, body == null ? "" : new String(body, UTF_8));
  }

  public WebhookRequestException(final HttpResponse<?> httpResponse, final String body) {
    super("Webhook request failed with status " + statusCode(httpResponse) + ": '" + (body == null ? "" : body) + "'");
    this.httpResponse = httpResponse;
    this.body = body == null ? "" : body;
  }

  private static int statusCode(final HttpResponse<?> httpResponse) {
    return httpResponse == null ? -1 : httpResponse.statusCode();
  }

  /// The raw response body, decoded as UTF-8; empty when the response had none.
  public String body() {
    return body;
  }

  @Override
  public boolean canBeRetried() {
    return httpResponse == null
        || httpResponse.statusCode() >= 500
        || httpResponse.statusCode() == 429;
  }

  @Override
  public HttpResponse<?> httpResponse() {
    return httpResponse;
  }

  /// The HTTP status code, or -1 when no response was observed.
  @Override
  public long errorCode() {
    return statusCode(httpResponse);
  }

  /// The wait the receiver asked for: the `Retry-After` header when it parses, in either of
  /// its forms ([IncidentClientException#retryAfter(HttpResponse)]), `0` included;
  /// otherwise the wait a [Telegram Bot API](https://core.telegram.org/bots/api) error body
  /// states, such as
  /// `{"ok":false,"error_code":429,"description":"Too Many Requests: retry after 35","parameters":{"retry_after":35}}`;
  /// otherwise null.
  ///
  /// The body is read leniently, as far as its first JSON object: the wait is read when that
  /// object's `ok` is the boolean `false` and its `parameters` is an object holding a
  /// `retry_after` number token of ASCII digits alone, without a leading zero, that fits a
  /// `long` and is above zero; that many seconds is the wait. When a field name repeats, its
  /// last occurrence decides. The body need not be well-formed JSON to count: text after that
  /// object and the content of the fields it skips are not validated, and json-iterator
  /// takes a `null` token as the end of an object, in the objects read here too. A body that
  /// stops with the object still open and nothing after it, such as
  /// `{"ok":false,"parameters":{"retry_after":35}`, is null. The Bot API documents the wait
  /// in the body (`ResponseParameters.retry_after`); its server also sends the header, which
  /// the documented contract leaves out. No status code is required, and the body is parsed
  /// on each call. Never throws.
  @Override
  public Duration retryAfter() {
    final var header = IncidentClientException.retryAfter(httpResponse);
    return header != null ? header : botApiRetryAfter(body);
  }

  /// `parameters.retry_after` of a Bot API error body, or null; see [#retryAfter()].
  private static Duration botApiRetryAfter(final String body) {
    try {
      final var ji = JsonIterator.parse(body);
      if (ji.whatIsNext() != ValueType.OBJECT) {
        return null;
      }
      final var error = new Object() {
        boolean notOk;
        long retryAfter;
      };
      ji.testObject((buf, offset, len, fieldJi) -> {
        if (fieldEquals("ok", buf, offset, len)) {
          if (fieldJi.whatIsNext() == ValueType.BOOLEAN) {
            error.notOk = !fieldJi.readBoolean();
          } else {
            error.notOk = false;
            fieldJi.skip();
          }
        } else if (fieldEquals("parameters", buf, offset, len)) {
          if (fieldJi.whatIsNext() == ValueType.OBJECT) {
            error.retryAfter = parametersRetryAfter(fieldJi);
          } else {
            error.retryAfter = 0;
            fieldJi.skip();
          }
        } else {
          fieldJi.skip();
        }
        return true;
      });
      return error.notOk && error.retryAfter > 0 ? Duration.ofSeconds(error.retryAfter) : null;
    } catch (final RuntimeException malformed) {
      return null;
    }
  }

  /// The `retry_after` of a `parameters` object as [#parseSeconds(String)] reads it, or 0 when
  /// it has none that is a number token; the caller takes only a value above zero.
  private static long parametersRetryAfter(final JsonIterator ji) {
    final var parameters = new Object() {
      long retryAfter;
    };
    ji.testObject((buf, offset, len, fieldJi) -> {
      if (fieldEquals("retry_after", buf, offset, len)) {
        if (fieldJi.whatIsNext() == ValueType.NUMBER) {
          parameters.retryAfter = parseSeconds(fieldJi.readNumberAsString());
        } else {
          parameters.retryAfter = 0;
          fieldJi.skip();
        }
      } else {
        fieldJi.skip();
      }
      return true;
    });
    return parameters.retryAfter;
  }

  /// `number`, a number token as json-iterator read it, as a `long`; 0 when it has a leading
  /// zero or [Long#parseLong(String)] refuses it, and negative for a sign, which the caller
  /// refuses. That this leaves ASCII digits alone rests on the tokenizer's alphabet: after
  /// [ValueType#NUMBER], [JsonIterator#readNumberAsString()] returns only characters from
  /// `0-9 . e E + -` and never starts with `+`, and of those `Long.parseLong` takes only
  /// digits and a leading `-`, so a fraction, an exponent or an inner sign fails the parse.
  /// The body is read as bytes, so no digit outside ASCII reaches the parse either.
  private static long parseSeconds(final String number) {
    if (number.startsWith("0")) {
      return 0;
    }
    try {
      return Long.parseLong(number);
    } catch (final NumberFormatException notALong) {
      return 0;
    }
  }

  @Override
  public List<String> errors() {
    return body.isBlank() ? List.of() : List.of(body);
  }

  @Override
  public String toString() {
    return "WebhookRequestException{errorCode=" + errorCode() + ", body='" + body + "'}";
  }
}
