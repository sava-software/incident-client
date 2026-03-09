package software.sava.incident.pagerduty.config;

import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public record PagerDutyConfig(String routingKey, String authToken) {

  public static PagerDutyConfig parseConfig(final JsonIterator ji) {
    final var parser = new Parser();
    ji.testObject(parser);
    return parser.createConfig();
  }

  private static final class Parser implements FieldBufferPredicate {

    private String routingKey;
    private String authToken;

    private Parser() {
    }

    private PagerDutyConfig createConfig() {
      if (routingKey == null || routingKey.isBlank()) {
        throw new IllegalStateException("PagerdutyConfig routingKey is required.");
      }
      return new PagerDutyConfig(routingKey, authToken);
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("routingKey", buf, offset, len)) {
        routingKey = ji.readString();
      } else if (fieldEquals("authToken", buf, offset, len)) {
        authToken = ji.readString();
      } else {
        throw new IllegalStateException("Unknown PagerdutyConfig field " + new String(buf, offset, len));
      }
      return true;
    }
  }
}
