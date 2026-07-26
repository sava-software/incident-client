package software.sava.incident.pagerduty.event.data;

import systems.comodal.jsoniter.FieldIndexPredicate;
import systems.comodal.jsoniter.JsonIterator;

public interface PagerDutyEventResponse {

  static Parser parser() {
    return new PagerDutyEventResponseVal.PagerDutyEventResponseParser();
  }

  String status();

  String message();

  String dedupKey();

  interface Parser extends PagerDutyEventResponse, FieldIndexPredicate {

    /// Parses the response object `ji` is positioned at into this parser's fields.
    Parser parse(final JsonIterator ji);

    PagerDutyEventResponse create();

    Parser status(final String status);

    Parser message(final String message);

    Parser dedupKey(final String dedupKey);
  }
}
