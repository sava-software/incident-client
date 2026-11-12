package software.sava.incident.io.alert;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Pins the `AlertEventsCreateHTTPPayloadV2` serialization: member order, omission of blank
/// optional members, full RFC 8259 escaping, and the one-level metadata value model.
final class AlertEventTests {

  private static AlertEvent.Builder firing() {
    return AlertEvent.build()
        .title("Disk full")
        .status(AlertEvent.Status.firing)
        .deduplicationKey("key-1");
  }

  @Test
  void everyMemberIsSerializedInSchemaOrder() {
    final var event = firing()
        .description("**Markdown** detail\nsecond line")
        .sourceUrl("https://monitor.example.com/alerts/1")
        .metadata("service", "db-1")
        .create();

    // description is markdown: its newline is escaped, never removed
    final var expected = """
        {"title":"Disk full","status":"firing","deduplication_key":"key-1",\
        "description":"**Markdown** detail\\nsecond line",\
        "source_url":"https://monitor.example.com/alerts/1","metadata":{"service":"db-1"}}""";
    assertEquals(expected, event.body());
    assertEquals(expected, event.toString());
    assertEquals("Disk full", event.title());
    assertEquals(AlertEvent.Status.firing, event.status());
    assertEquals("key-1", event.deduplicationKey());
    assertEquals("**Markdown** detail\nsecond line", event.description());
    assertEquals("https://monitor.example.com/alerts/1", event.sourceUrl());
    assertEquals(Map.of("service", "db-1"), event.metadata());
  }

  @Test
  void eachOptionalMemberIsOmittedInTurn() {
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"key-1",\
        "source_url":"https://s","metadata":{"m":"v"}}""", firing()
        .sourceUrl("https://s").metadata("m", "v").create().body());
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"key-1",\
        "description":"d","metadata":{"m":"v"}}""", firing()
        .description("d").metadata("m", "v").create().body());
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"key-1",\
        "description":"d","source_url":"https://s"}""", firing()
        .description("d").sourceUrl("https://s").create().body());
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"key-1"}""", firing().create().body());
  }

  @Test
  void whitespaceOnlyOptionalMembersAreOmitted() {
    final var event = firing().description(" \t\n").sourceUrl("  ").create();
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"key-1"}""", event.body());
    // the accessors keep what the caller gave; only the wire omits it
    assertEquals(" \t\n", event.description());
    assertEquals("  ", event.sourceUrl());
  }

  @Test
  void anEmptyMetadataMapIsOmitted() {
    final var event = firing().metadata(Map.of()).create();
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"key-1"}""", event.body());
    assertTrue(event.metadata().isEmpty());
  }

  @Test
  void aResolvedEventSerializesItsStatus() {
    final var event = AlertEvent.build()
        .title("Disk full")
        .status(AlertEvent.Status.resolved)
        .deduplicationKey("key-1")
        .create();
    assertEquals("""
        {"title":"Disk full","status":"resolved","deduplication_key":"key-1"}""", event.body());
  }

  private static void assertUuid(final String key) {
    assertNotNull(key);
    assertEquals(UUID.fromString(key).toString(), key, "a generated key is a UUID");
  }

  @Test
  void aFiringEventWithoutAKeyIsGivenAUuid() {
    for (final var key : new String[]{null, "", " \t"}) {
      final var builder = AlertEvent.build()
          .title("Disk full")
          .status(AlertEvent.Status.firing)
          .deduplicationKey(key);
      final var event = builder.create();
      assertUuid(event.deduplicationKey());
      assertEquals("""
          {"title":"Disk full","status":"firing","deduplication_key":"%s"}""".formatted(event.deduplicationKey()),
          event.body());
      // create() does not change the builder, so each create draws a new key
      assertEquals(key, builder.deduplicationKey());
      assertNotEquals(event.deduplicationKey(), builder.create().deduplicationKey());
    }
  }

  @Test
  void aResolvedEventWithoutAKeyIsRefused() {
    for (final var key : new String[]{null, "", " \t"}) {
      final var builder = AlertEvent.build()
          .title("Disk full")
          .status(AlertEvent.Status.resolved)
          .deduplicationKey(key);
      final var thrown = assertThrows(IllegalArgumentException.class, builder::create);
      assertTrue(thrown.getMessage().contains("'deduplicationKey'"), thrown.getMessage());
    }
  }

  @Test
  void titleAndStatusAreRequired() {
    final var noTitle = AlertEvent.build().status(AlertEvent.Status.firing).deduplicationKey("k");
    assertEquals("'title' is a required alert event field.",
        assertThrows(NullPointerException.class, noTitle::create).getMessage());

    final var blankTitle = AlertEvent.build().title(" \n").status(AlertEvent.Status.firing).deduplicationKey("k");
    assertEquals("'title' is a required alert event field.",
        assertThrows(IllegalArgumentException.class, blankTitle::create).getMessage());

    final var noStatus = AlertEvent.build().title("t").deduplicationKey("k");
    assertEquals("'status' is a required alert event field.",
        assertThrows(NullPointerException.class, noStatus::create).getMessage());
  }

  @Test
  void builderAccessorsReturnWhatWasSet() {
    final var builder = AlertEvent.build()
        .title("t-1")
        .status(AlertEvent.Status.resolved)
        .deduplicationKey("k-1")
        .description("d-1")
        .sourceUrl("u-1");
    assertEquals("t-1", builder.title());
    assertEquals(AlertEvent.Status.resolved, builder.status());
    assertEquals("k-1", builder.deduplicationKey());
    assertEquals("d-1", builder.description());
    assertEquals("u-1", builder.sourceUrl());
  }

  @Test
  void stringsAreFullyEscaped() {
    final var event = AlertEvent.build()
        .title("a \"quoted\" \\ title\r\n\u0001")
        .status(AlertEvent.Status.firing)
        .deduplicationKey("k\"\\")
        .description("tab\there")
        .sourceUrl("https://x/?q=\"y\"")
        .create();
    assertEquals("""
        {"title":"a \\"quoted\\" \\\\ title\\r\\n\\u0001","status":"firing","deduplication_key":"k\\"\\\\",\
        "description":"tab\\there","source_url":"https://x/?q=\\"y\\""}""", event.body());
  }

  /// A `BigInteger` subclass that is not a number on the wire, to pin the exact-class rule.
  private static final class WordyInteger extends BigInteger {

    WordyInteger() {
      super("5");
    }

    @Override
    public String toString() {
      return "five";
    }
  }

  /// A `BigDecimal` subclass that is not a number on the wire, to pin the exact-class rule.
  private static final class WordyDecimal extends BigDecimal {

    WordyDecimal() {
      super("5.5");
    }

    @Override
    public String toPlainString() {
      return "five and a half";
    }

    @Override
    public String toString() {
      return "five-point-five";
    }
  }

  @Test
  void everyMetadataValueKind() {
    final var withNull = new ArrayList<>();
    withNull.add("a");
    withNull.add(null);
    final var event = firing()
        .metadata("string", "a \"b\" \\ c\nd")
        .metadata("true", Boolean.TRUE)
        .metadata("false", Boolean.FALSE)
        .metadata("byte", (byte) -7)
        .metadata("short", (short) 300)
        .metadata("int", 70_000)
        .metadata("long", 9_000_000_000L)
        .metadata("bigInteger", new BigInteger("123456789012345678901234567890"))
        .metadata("bigDecimal", new BigDecimal("1E+3"))
        .metadata("smallDecimal", new BigDecimal("1E-7"))
        .metadata("double", 1.5d)
        .metadata("negativeZero", -0.0d)
        .metadata("float", 2.25f)
        .metadata("null", null)
        .metadata("list", List.of("x", 1, true, 2.5d))
        .metadata("set", new LinkedHashSet<>(List.of("b", "a")))
        .metadata("emptyList", List.of())
        .metadata("listWithNull", withNull)
        .metadata("atomic", new AtomicInteger(42))
        .metadata("wordyInteger", new WordyInteger())
        .metadata("wordyDecimal", new WordyDecimal())
        .metadata("object", new Object() {
          @Override
          public String toString() {
            return "custom \"object\"";
          }
        })
        .create();

    final var expected = """
        {"title":"Disk full","status":"firing","deduplication_key":"key-1","metadata":{\
        "string":"a \\"b\\" \\\\ c\\nd",\
        "true":true,\
        "false":false,\
        "byte":-7,\
        "short":300,\
        "int":70000,\
        "long":9000000000,\
        "bigInteger":123456789012345678901234567890,\
        "bigDecimal":1000,\
        "smallDecimal":0.0000001,\
        "double":1.5,\
        "negativeZero":-0.0,\
        "float":2.25,\
        "null":null,\
        "list":["x",1,true,2.5],\
        "set":["b","a"],\
        "emptyList":[],\
        "listWithNull":["a",null],\
        "atomic":"42",\
        "wordyInteger":"five",\
        "wordyDecimal":"five-point-five",\
        "object":"custom \\"object\\""}}""";
    assertEquals(expected, event.body());
    // the body is a JSON document the parser reads back member by member
    final var members = new ArrayList<String>();
    JsonIterator.parse(event.body()).testObject((buf, offset, len, ji) -> {
      members.add(new String(buf, offset, len));
      ji.skip();
      return true;
    });
    assertEquals(List.of("title", "status", "deduplication_key", "metadata"), members);
  }

  @Test
  void nonFiniteNumbersAreRefused() {
    for (final Object value : new Object[]{
        Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
        Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
      final var builder = firing();
      final var thrown = assertThrows(IllegalArgumentException.class, () -> builder.metadata("n", value));
      assertTrue(thrown.getMessage().contains("'n'"), thrown.getMessage());
      // nothing was stored
      assertEquals("""
          {"title":"Disk full","status":"firing","deduplication_key":"key-1"}""", builder.create().body());
    }
  }

  @Test
  void nestedContainersAreRefused() {
    final Object[] refused = {
        Map.of("a", 1),
        new String[]{"a"},
        new int[]{1},
        List.of(List.of(1)),
        List.of(Map.of("a", 1)),
        List.of((Object) new String[]{"a"}),
        List.of("ok", Double.NaN),
        List.of(Float.POSITIVE_INFINITY),
        List.of(new LinkedHashSet<>(List.of(1)))
    };
    for (final var value : refused) {
      final var builder = firing();
      assertThrows(IllegalArgumentException.class, () -> builder.metadata("m", value), String.valueOf(value));
      assertTrue(AlertEventRecord.refusesMetadataValue(value), String.valueOf(value));
    }
  }

  @Test
  void theRefusalPredicateAcceptsEveryModelledValue() {
    final var withNull = new ArrayList<>();
    withNull.add(null);
    final Object[] accepted = {
        null, "s", true, 1, 1L, 1.5d, 2.5f, -0.0d, Double.MAX_VALUE, new BigDecimal("1"),
        new BigInteger("1"), new AtomicInteger(1), new Object(),
        List.of(), List.of("a", 1, 1.5d, 2.5f), withNull
    };
    for (final var value : accepted) {
      assertFalse(AlertEventRecord.refusesMetadataValue(value), String.valueOf(value));
    }
  }

  @Test
  void metadataKeysAreEscapedAndMayBeEmpty() {
    final var event = firing()
        .metadata("k \"q\" \\ \n", "v1")
        .metadata("", "v2")
        .create();
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"key-1",\
        "metadata":{"k \\"q\\" \\\\ \\n":"v1","":"v2"}}""", event.body());
  }

  @Test
  void aNullMetadataKeyIsRefusedWhenAdded() {
    final var builder = firing();
    assertEquals("A metadata key must not be null.",
        assertThrows(NullPointerException.class, () -> builder.metadata(null, "v")).getMessage());
    final var map = new LinkedHashMap<String, Object>();
    map.put(null, "v");
    assertThrows(NullPointerException.class, () -> builder.metadata(map));
    assertThrows(NullPointerException.class, () -> builder.metadata((Map<String, ?>) null));
  }

  @Test
  void metadataKeepsInsertionOrderAndReplacesInPlace() {
    final var source = new LinkedHashMap<String, Object>();
    source.put("z", 1);
    source.put("a", 2);
    final var event = firing()
        .metadata("m", "first")
        .metadata(source)
        .metadata("m", "replaced")
        .create();
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"key-1",\
        "metadata":{"m":"replaced","z":1,"a":2}}""", event.body());
    assertEquals(List.of("m", "z", "a"), List.copyOf(event.metadata().keySet()));
  }

  @Test
  void metadataIsSnapshotted() {
    final var list = new ArrayList<Object>(List.of("a"));
    final var source = new LinkedHashMap<String, Object>();
    source.put("list", list);
    final var builder = firing().metadata(source);
    // the list was snapshotted when added
    list.add("added-after-metadata");
    source.put("late", "added-after-metadata");
    final var event = builder.create();
    list.add("added-after-create");
    builder.metadata("later", "added-after-create");

    final var expected = """
        {"title":"Disk full","status":"firing","deduplication_key":"key-1","metadata":{"list":["a"]}}""";
    assertEquals(expected, event.body());
    assertEquals(Map.of("list", List.of("a")), event.metadata());
    assertThrows(UnsupportedOperationException.class, () -> event.metadata().put("x", 1));
    final var storedList = assertInstanceOf(List.class, event.metadata().get("list"));
    assertThrows(UnsupportedOperationException.class, () -> storedList.add("x"));
  }

  @Test
  void metadataAllowsNullValues() {
    final var event = firing().metadata("a", null).metadata("b", "v").create();
    assertTrue(event.metadata().containsKey("a"));
    assertNull(event.metadata().get("a"));
    assertEquals(Arrays.asList("a", "b"), List.copyOf(event.metadata().keySet()));
  }
}
