package software.sava.incident.core.config;

import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.time.Duration;
import java.util.Properties;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public abstract class HttpApiClientConfig {

  protected final URI endpoint;
  protected final Duration requestTimeout;

  protected HttpApiClientConfig(final URI endpoint, final Duration requestTimeout) {
    this.endpoint = endpoint;
    this.requestTimeout = requestTimeout;
  }

  public URI endpoint() {
    return endpoint;
  }

  public Duration requestTimeout() {
    return requestTimeout;
  }

  public abstract static class Parser implements FieldBufferPredicate {

    protected final String prefix;
    protected URI endpoint;
    protected Duration requestTimeout;

    protected Parser(final String prefix) {
      this.prefix = prefix == null || prefix.isBlank() ? "" : prefix.endsWith(".") ? prefix : prefix + '.';
    }

    protected static URI parseEndpoint(final String endpoint) {
      return endpoint == null || endpoint.isBlank() ? null : URI.create(endpoint);
    }

    protected static Duration parseRequestTimeout(final String requestTimeout) {
      if (requestTimeout == null || requestTimeout.isBlank()) {
        return null;
      } else {
        return Duration.parse(requestTimeout.startsWith("PT") ? requestTimeout : "PT" + requestTimeout);
      }
    }

    protected void parseConfig(final Properties properties) {
      this.endpoint = parseEndpoint(properties.getProperty(prefix + "endpoint"));
      this.requestTimeout = parseRequestTimeout(properties.getProperty(prefix + "requestTimeout"));
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("endpoint", buf, offset, len)) {
        this.endpoint = parseEndpoint(ji.readString());
      } else if (fieldEquals("requestTimeout", buf, offset, len)) {
        this.requestTimeout = parseRequestTimeout(ji.readString());
      } else {
        throw new IllegalStateException("Unknown HttpApiClientConfig field " + new String(buf, offset, len));
      }
      return true;
    }
  }
}
