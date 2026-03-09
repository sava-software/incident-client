package software.sava.incident.pagerduty.event.data;

import systems.comodal.jsoniter.JsonIterator;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

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
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("status", buf, offset, len)) {
        this.status = ji.readString();
      } else if (fieldEquals("message", buf, offset, len)) {
        this.message = ji.readString();
      } else if (fieldEquals("dedup_key", buf, offset, len)) {
        this.dedupKey = ji.readString();
      } else {
        ji.skip();
      }
      return true;
    }
  }
}
