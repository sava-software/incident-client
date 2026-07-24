package software.sava.incident.io;

import systems.comodal.jsoniter.JsonIterator;

/// Jazzer entry point for the CreateIncidentResponse parser, which consumes incident.io
/// API responses. Malformed-input contract: "garbage in -> RuntimeException out" — Jazzer
/// hunts hangs, memory exhaustion, and any non-RuntimeException throwable.
///
/// Deliberately free of Jazzer imports so it compiles with the regular test sources.
///
/// Run with `./gradlew :incident-io:fuzzResponse [-PmaxFuzzTime=<seconds>]`.
public final class CreateIncidentResponseFuzz {

  public static void fuzzerTestOneInput(final byte[] data) {
    try {
      CreateIncidentResponseRecord.parse(JsonIterator.parse(data));
    } catch (final RuntimeException expected) {
      // malformed responses must fail with a RuntimeException, never anything else
    }
  }

  private CreateIncidentResponseFuzz() {
  }
}
