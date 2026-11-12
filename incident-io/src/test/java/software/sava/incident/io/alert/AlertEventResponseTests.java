package software.sava.incident.io.alert;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import static org.junit.jupiter.api.Assertions.*;

/// Pins the `AlertEventsCreateHTTPResultV2` parser: every member, unknown members skipped,
/// an omitted member read as null, and malformed input failing with a `RuntimeException`.
final class AlertEventResponseTests {

  private static AlertEventResponse parse(final String json) {
    return AlertEventResponseRecord.parse(JsonIterator.parse(json));
  }

  @Test
  void theSchemaExampleParses() {
    final var response = parse("""
        {"deduplication_key":"unique-key","message":"Event accepted for processing","status":"success"}""");
    assertEquals("success", response.status());
    assertEquals("Event accepted for processing", response.message());
    assertEquals("unique-key", response.deduplicationKey());
  }

  @Test
  void unknownMembersAreSkipped() {
    final var response = parse("""
        {"status":"accepted","extra":{"nested":[1,{"x":null}],"s":"t"},"message":"m-1",\
        "more":[true,false],"deduplication_key":"k-1"}""");
    assertEquals("accepted", response.status());
    assertEquals("m-1", response.message());
    assertEquals("k-1", response.deduplicationKey());
  }

  @Test
  void anOmittedMemberReadsAsNull() {
    final var response = parse("""
        {"status":"success","message":"m-2"}""");
    assertEquals("success", response.status());
    assertEquals("m-2", response.message());
    assertNull(response.deduplicationKey());

    final var empty = parse("{}");
    assertNull(empty.status());
    assertNull(empty.message());
    assertNull(empty.deduplicationKey());
  }

  @Test
  void malformedInputFailsWithARuntimeException() {
    for (final var json : new String[]{"{\"status\":", "{\"status\":\"x\",", "not json", "{\"message\":[1,"}) {
      assertThrows(RuntimeException.class, () -> parse(json), json);
    }
  }
}
