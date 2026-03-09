package software.sava.incident.pagerduty.event.data;

import systems.comodal.jsoniter.FieldBufferPredicate;

public interface PagerDutyEventResponse {

  static Parser parser() {
    return new PagerDutyEventResponseVal.PagerDutyEventResponseParser();
  }

  String status();

  String message();

  String dedupKey();

  interface Parser extends PagerDutyEventResponse, FieldBufferPredicate {

    PagerDutyEventResponse create();

    Parser status(final String status);

    Parser message(final String message);

    Parser dedupKey(final String dedupKey);
  }
}
