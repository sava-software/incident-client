package software.sava.incident.io;

import software.sava.incident.io.exceptions.IncidentIoRequestException;
import systems.comodal.jsoniter.JsonIterator;

/// Jazzer entry point for the incident.io success and error response parsers.
/// Malformed-input contract: "garbage in -> RuntimeException out" — Jazzer
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
    try {
      IncidentIoRequestException.parse(null, data);
    } catch (final RuntimeException expected) {
      // Exercise errors independently even when the success parser rejects the input.
    }
  }

  private CreateIncidentResponseFuzz() {
  }
}
