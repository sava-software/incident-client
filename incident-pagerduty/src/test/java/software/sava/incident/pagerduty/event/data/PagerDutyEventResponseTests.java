package software.sava.incident.pagerduty.event.data;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import static org.junit.jupiter.api.Assertions.*;

final class PagerDutyEventResponseTests {

  @Test
  void parseResponse() {
    final var parser = PagerDutyEventResponse.parser().parse(JsonIterator.parse("""
        {"status":"success","message":"Event processed","dedup_key":"dk-1","unknown":[1,2]}"""));

    assertEquals("success", parser.status());
    assertEquals("Event processed", parser.message());
    assertEquals("dk-1", parser.dedupKey());

    final var response = parser.create();
    assertEquals("success", response.status());
    assertEquals("Event processed", response.message());
    assertEquals("dk-1", response.dedupKey());
  }

  @Test
  void parserSetters() {
    final var parser = PagerDutyEventResponse.parser()
        .status("throttled")
        .message("Rate limited")
        .dedupKey("dk-2");
    final var toString = parser.toString();
    assertTrue(toString.contains("throttled"));
    assertTrue(toString.contains("Rate limited"));
    assertTrue(toString.contains("dk-2"));

    final var response = parser.create();
    assertEquals("throttled", response.status());
    assertEquals("Rate limited", response.message());
    assertEquals("dk-2", response.dedupKey());
  }

  @Test
  void parseEmptyResponse() {
    final var response = PagerDutyEventResponse.parser().parse(JsonIterator.parse("{}")).create();
    assertNull(response.status());
    assertNull(response.message());
    assertNull(response.dedupKey());
  }
}
