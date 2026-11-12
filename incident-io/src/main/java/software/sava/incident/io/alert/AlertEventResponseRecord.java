package software.sava.incident.io.alert;

import systems.comodal.jsoniter.FieldMatcher;
import systems.comodal.jsoniter.JsonIterator;

record AlertEventResponseRecord(String status,
                                String message,
                                String deduplicationKey) implements AlertEventResponse {

  private static final FieldMatcher FIELDS = FieldMatcher.of("status", "message", "deduplication_key");

  /// Unknown members are skipped; malformed input fails with a `RuntimeException`.
  static AlertEventResponseRecord parse(final JsonIterator ji) {
    final var parsed = new Object() {
      String status, message, deduplicationKey;
    };
    ji.testObject(FIELDS, (fieldIndex, value) -> {
      switch (fieldIndex) {
        case 0 -> parsed.status = value.readString();
        case 1 -> parsed.message = value.readString();
        case 2 -> parsed.deduplicationKey = value.readString();
        default -> value.skip();
      }
      return true;
    });
    return new AlertEventResponseRecord(parsed.status, parsed.message, parsed.deduplicationKey);
  }
}
