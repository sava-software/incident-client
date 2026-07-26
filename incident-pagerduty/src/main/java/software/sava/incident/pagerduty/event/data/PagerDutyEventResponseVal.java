package software.sava.incident.pagerduty.event.data;

import systems.comodal.jsoniter.FieldMatcher;
import systems.comodal.jsoniter.JsonIterator;

record PagerDutyEventResponseVal(String status,
                                 String message,
                                 String dedupKey) implements PagerDutyEventResponse {

  @Override
  public String status() {
    return status;
  }

  @Override
  public String message() {
    return message;
  }

  @Override
  public String dedupKey() {
    return dedupKey;
  }

  static final class PagerDutyEventResponseParser implements Parser {

    private static final FieldMatcher FIELDS = FieldMatcher.of("status", "message", "dedup_key");

    private String status;
    private String message;
    private String dedupKey;

    PagerDutyEventResponseParser() {
    }

    @Override
    public PagerDutyEventResponse create() {
      return new PagerDutyEventResponseVal(status, message, dedupKey);
    }

    @Override
    public Parser status(final String status) {
      this.status = status;
      return this;
    }

    @Override
    public Parser message(final String message) {
      this.message = message;
      return this;
    }

    @Override
    public Parser dedupKey(final String dedupKey) {
      this.dedupKey = dedupKey;
      return this;
    }

    @Override
    public String status() {
      return status;
    }

    @Override
    public String message() {
      return message;
    }

    @Override
    public String dedupKey() {
      return dedupKey;
    }

    @Override
    public String toString() {
      return "PagerDutyEventResponseBuilder{status='" + status + '\'' +
          ", message='" + message + '\'' +
          ", dedupKey='" + dedupKey + '\'' + '}';
    }

    @Override
    public Parser parse(final JsonIterator ji) {
      ji.testObject(FIELDS, this);
      return this;
    }

    @Override
    public boolean test(final int fieldIndex, final JsonIterator ji) {
      switch (fieldIndex) {
        case 0 -> this.status = ji.readString();
        case 1 -> this.message = ji.readString();
        case 2 -> this.dedupKey = ji.readString();
        default -> ji.skip();
      }
      return true;
    }
  }
}
