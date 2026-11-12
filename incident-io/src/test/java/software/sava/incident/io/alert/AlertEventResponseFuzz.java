package software.sava.incident.io.alert;

import systems.comodal.jsoniter.JsonIterator;

/// Jazzer entry point for the alert-event 202 response parser (`AlertEventsCreateHTTPResultV2`).
/// Malformed-input contract: "garbage in -> RuntimeException out" — Jazzer hunts hangs,
/// memory exhaustion, and any non-RuntimeException throwable.
///
/// Deliberately free of Jazzer imports so it compiles with the regular test sources.
///
/// Run with `./gradlew :incident-io:fuzzAlertEventResponse [-PmaxFuzzTime=<seconds>]`.
public final class AlertEventResponseFuzz {

  public static void fuzzerTestOneInput(final byte[] data) {
    try {
      AlertEventResponseRecord.parse(JsonIterator.parse(data));
    } catch (final RuntimeException expected) {
      // malformed responses must fail with a RuntimeException, never anything else
    }
  }

  private AlertEventResponseFuzz() {
  }
}
