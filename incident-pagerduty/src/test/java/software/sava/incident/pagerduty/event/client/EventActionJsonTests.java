package software.sava.incident.pagerduty.event.client;

import org.junit.jupiter.api.Test;
import software.sava.incident.pagerduty.event.data.PagerDutyEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutySeverity;

import java.time.ZonedDateTime;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Pins the acknowledge and resolve request bodies without a transport. Property: the body is
/// the routing key, the dedup key and the action, with both keys JSON-escaped and otherwise
/// sent as given, a line break included, so a key names the same alert on trigger,
/// acknowledge and resolve. Oracle: hand-written JSON from the field names of the Events API
/// v2 `createV2Event` (`POST /v2/enqueue`) request body and RFC 8259's escapes, not the
/// implementation's output.
final class EventActionJsonTests {

  @Test
  void plainKeys() {
    assertEquals("""
        {"routing_key":"rk-1","dedup_key":"dk-1","event_action":"acknowledge"}""",
        PagerDutyEventClientImpl.eventActionJson("rk-1", "dk-1", "acknowledge"));
    assertEquals("""
        {"routing_key":"rk-1","dedup_key":"dk-1","event_action":"resolve"}""",
        PagerDutyEventClientImpl.eventActionJson("rk-1", "dk-1", "resolve"));
  }

  @Test
  void aLineBreakInAKeyIsEscapedNotRemoved() {
    assertEquals("""
        {"routing_key":"rk\\n1","dedup_key":"dk\\n1\\r\\"2\\\\\\t\\u0001","event_action":"acknowledge"}""",
        PagerDutyEventClientImpl.eventActionJson("rk\n1", "dk\n1\r\"2\\\t\u0001", "acknowledge"));
    assertEquals("""
        {"routing_key":"rk\\n1","dedup_key":"dk\\n1\\r\\"2\\\\\\t\\u0001","event_action":"resolve"}""",
        PagerDutyEventClientImpl.eventActionJson("rk\n1", "dk\n1\r\"2\\\t\u0001", "resolve"));
  }

  /// The key a trigger sends is the key a resolve sends, byte for byte.
  @Test
  void aKeyIsSentTheSameOnTriggerAndResolve() {
    final var key = "dk\n1\r\"2";
    final var event = PagerDutyEventPayload.build()
        .dedupKey(key)
        .summary("summary")
        .source("source")
        .severity(PagerDutySeverity.warning)
        .timestamp(ZonedDateTime.of(2018, 8, 1, 2, 3, 4, 0, UTC))
        .create();
    final var sent = "\"dedup_key\":\"dk\\n1\\r\\\"2\"";
    assertTrue(PagerDutyEventClientImpl.triggerJson("rk-1", event, null, null).contains(sent));
    assertTrue(PagerDutyEventClientImpl.eventActionJson("rk-1", key, "resolve").contains(sent));
  }
}
