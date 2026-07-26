package software.sava.incident.pagerduty.event.data;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.*;

final class PagerDutyPayloadTests {

  private static final ZonedDateTime TIMESTAMP = ZonedDateTime.of(2018, 8, 1, 2, 3, 4, 0, UTC);

  @Test
  void eventPayloadJsonEscapes() {
    final var payload = PagerDutyEventPayload.build()
        .dedupKey("dk-1")
        .summary("sum\"mary\nline")
        .source("so\\urce")
        .severity(PagerDutySeverity.critical)
        .timestamp(TIMESTAMP)
        .component("com\"p")
        .group("gr\tp")
        .eventClass("cl\"ass")
        .customDetails("key\"1", "val\n1")
        .customDetails("num", 5)
        .customDetails("flag", Boolean.TRUE)
        .create();
    assertEquals(
        "{\"summary\":\"sum\\\"maryline\",\"source\":\"so\\\\urce\",\"severity\":\"critical\""
            + ",\"timestamp\":\"2018-08-01T02:03:04Z\",\"component\":\"com\\\"p\",\"group\":\"gr\\tp\""
            + ",\"class\":\"cl\\\"ass\",\"custom_details\":{\"key\\\"1\":\"val\\n1\",\"num\":5,\"flag\":true}}",
        payload.payloadJson()
    );
  }

  @Test
  void changeEventPayloadJsonEscapes() {
    final var payload = PagerDutyChangeEventPayload.build()
        .summary("change\"s\r\n")
        .source("src\nline")
        .timestamp(TIMESTAMP)
        .customDetails("k", "v\"")
        .create();
    assertEquals(
        "{\"summary\":\"change\\\"s\",\"source\":\"srcline\""
            + ",\"timestamp\":\"2018-08-01T02:03:04Z\",\"custom_details\":{\"k\":\"v\\\"\"}}",
        payload.payloadJson()
    );
  }

  @Test
  void linkToJson() {
    final var link = PagerDutyLinkRef.build()
        .href("https://ex.com/a?b=\"c\"")
        .text("te\nxt")
        .create();
    assertEquals("{\"href\":\"https://ex.com/a?b=\\\"c\\\"\",\"text\":\"text\"}", link.toJson());

    final var hrefOnly = PagerDutyLinkRef.build().href("h").text(" ").create();
    assertEquals("{\"href\":\"h\"}", hrefOnly.toJson());
  }

  @Test
  void imageToJson() {
    assertEquals("{\"src\":\"s\\\"1\"}",
        PagerDutyImageRef.build().src("s\"1").create().toJson());
    assertEquals("{\"src\":\"s\",\"alt\":\"a\\\"\"}",
        PagerDutyImageRef.build().src("s").alt("a\"").create().toJson());
    assertEquals("{\"src\":\"s\",\"href\":\"h\"}",
        PagerDutyImageRef.build().src("s").href("h\n").create().toJson());
    assertEquals("{\"src\":\"s\",\"href\":\"h\",\"alt\":\"a\"}",
        PagerDutyImageRef.build().src("s").href("h").alt("a").create().toJson());
  }

  @Test
  void customDetailsToJsonValueTypes() {
    final var details = new LinkedHashMap<String, Object>();
    details.put("n\"ull", null);
    details.put("dec", new BigDecimal("1.10"));
    details.put("int", new BigInteger("42"));
    details.put("bool", Boolean.FALSE);
    details.put("double", 1.5d);
    assertEquals(
        "{\"n\\\"ull\":null,\"dec\":\"1.10\",\"int\":\"42\",\"bool\":false,\"double\":1.5}",
        PagerDutyChangeEventPayloadRecord.PagerDutyChangeEventPayloadBuilder.toJson(details)
    );
  }

  @Test
  void summaryTruncatesAt1024() {
    final var payload = PagerDutyChangeEventPayload.build()
        .summary("a".repeat(2_000))
        .timestamp(TIMESTAMP)
        .create();
    assertEquals("a".repeat(1_024), payload.summary());
  }

  @Test
  void dedupKeyMaxLength() {
    final var builder = PagerDutyEventPayload.build();
    assertThrows(IllegalArgumentException.class, () -> builder.dedupKey("k".repeat(256)));
    builder.dedupKey("k".repeat(255));
    assertEquals("k".repeat(255), builder.dedupKey());
  }

  @Test
  void blankDedupKeyIsGenerated() {
    final var payload = PagerDutyEventPayload.build()
        .dedupKey(" ")
        .summary("summary")
        .source("source")
        .severity(PagerDutySeverity.info)
        .timestamp(TIMESTAMP)
        .create();
    assertEquals(36, payload.dedupKey().length());
  }

  @Test
  void summaryExactly1024IsNotCopied() {
    final var summary = "b".repeat(1_024);
    final var payload = PagerDutyChangeEventPayload.build()
        .summary(summary)
        .timestamp(TIMESTAMP)
        .create();
    assertSame(summary, payload.summary());
  }

  @Test
  void createdCollectionsAreImmutableWhenSizedAboveOne() {
    final var payload = PagerDutyEventPayload.build()
        .dedupKey("dk-3")
        .summary("summary")
        .source("source")
        .severity(PagerDutySeverity.error)
        .timestamp(TIMESTAMP)
        .customDetails("k1", "v1")
        .customDetails("k2", "v2")
        .link(PagerDutyLinkRef.build().href("h1").create())
        .link(PagerDutyLinkRef.build().href("h2").create())
        .image(PagerDutyImageRef.build().src("s1").create())
        .image(PagerDutyImageRef.build().src("s2").create())
        .create();
    assertThrows(UnsupportedOperationException.class, () -> payload.customDetails().put("k3", "v3"));
    assertThrows(UnsupportedOperationException.class, () -> payload.links().add(PagerDutyLinkRef.build().href("h3").create()));
    assertThrows(UnsupportedOperationException.class, () -> payload.images().add(PagerDutyImageRef.build().src("s3").create()));

    final var changePayload = PagerDutyChangeEventPayload.build(payload).create();
    assertThrows(UnsupportedOperationException.class, () -> changePayload.customDetails().put("k3", "v3"));
    assertThrows(UnsupportedOperationException.class, () -> changePayload.links().add(PagerDutyLinkRef.build().href("h3").create()));
    assertThrows(UnsupportedOperationException.class, () -> changePayload.images().add(PagerDutyImageRef.build().src("s3").create()));
  }

  @Test
  void changeCustomDetailsObjectOverload() {
    final var payload = PagerDutyChangeEventPayload.build()
        .summary("summary")
        .timestamp(TIMESTAMP)
        .customDetails("obj", new StringBuilder("v\"1"))
        .customDetails("nil", (Object) null)
        .create();
    assertEquals(
        "{\"summary\":\"summary\",\"timestamp\":\"2018-08-01T02:03:04Z\""
            + ",\"custom_details\":{\"obj\":\"v\\\"1\",\"nil\":\"null\"}}",
        payload.payloadJson()
    );
  }

  @Test
  void linkToJsonNullText() {
    assertEquals("{\"href\":\"h\"}", PagerDutyLinkRef.build().href("h").create().toJson());
  }

  @Test
  void imageToJsonBlankBranches() {
    assertEquals("{\"src\":\"s\"}",
        PagerDutyImageRef.build().src("s").href(" ").alt(" ").create().toJson());
    assertEquals("{\"src\":\"s\",\"alt\":\"a\"}",
        PagerDutyImageRef.build().src("s").href(" ").alt("a").create().toJson());
  }

  @Test
  void imageToJsonAltWithoutHref() {
    assertEquals("{\"src\":\"s\",\"alt\":\"a\"}",
        PagerDutyImageRef.build().src("s").alt("a").create().toJson());
    assertEquals("{\"src\":\"s\"}",
        PagerDutyImageRef.build().src("s").alt(" ").create().toJson());
  }

  @Test
  void changeCreateDefaultsTimestamp() {
    assertNotNull(PagerDutyChangeEventPayload.build().summary("s").create().timestamp());
  }

  @Test
  void eventCreateDefaultsTimestamp() {
    final var payload = PagerDutyEventPayload.build()
        .summary("s")
        .source("src")
        .severity(PagerDutySeverity.info)
        .create();
    assertNotNull(payload.timestamp());
  }

  @Test
  void changeCustomDetailsTypedOverloads() {
    final var payload = PagerDutyChangeEventPayload.build()
        .summary("s")
        .timestamp(TIMESTAMP)
        .customDetails("b", Boolean.TRUE)
        .customDetails("n", (Number) 42)
        .create();
    assertEquals(java.util.Map.of("b", (Object) Boolean.TRUE, "n", 42), payload.customDetails());
  }

  @Test
  void linkHrefAndImageSrcAreRequired() {
    // the Events API v2 schema marks link 'href' and image 'src' as required
    assertThrows(NullPointerException.class, () -> PagerDutyLinkRef.build().text("t").create());
    assertThrows(NullPointerException.class, () -> PagerDutyImageRef.build().href("h").alt("a").create());
  }

  @Test
  void imageToJsonHrefWithoutAlt() {
    assertEquals("{\"src\":\"s\",\"href\":\"h\"}",
        PagerDutyImageRef.build().src("s").href("h").create().toJson());
    assertEquals("{\"src\":\"s\",\"href\":\"h\"}",
        PagerDutyImageRef.build().src("s").href("h").alt(" ").create().toJson());
  }

  @Test
  void nullDedupKeyIsGenerated() {
    final var payload = PagerDutyEventPayload.build()
        .summary("summary")
        .source("source")
        .severity(PagerDutySeverity.info)
        .timestamp(TIMESTAMP)
        .create();
    assertEquals(36, payload.dedupKey().length());
  }

  @Test
  void summarySetterIgnoresNullAndBlank() {
    final var builder = PagerDutyChangeEventPayload.build().summary("keep");
    builder.summary(null);
    assertEquals("keep", builder.summary());
    builder.summary("  ");
    assertEquals("keep", builder.summary());
  }

  @Test
  void eventBuilderAccessors() {
    final var builder = PagerDutyEventPayload.build()
        .severity(PagerDutySeverity.error)
        .component("comp")
        .group("grp")
        .eventClass("cls");
    assertEquals(PagerDutySeverity.error, builder.severity());
    assertEquals("comp", builder.component());
    assertEquals("grp", builder.group());
    assertEquals("cls", builder.eventClass());
  }

  @Test
  void blankOptionalFieldsAreOmitted() {
    final var payload = PagerDutyEventPayload.build()
        .dedupKey("dk-b")
        .summary("summary")
        .source("source")
        .severity(PagerDutySeverity.info)
        .timestamp(TIMESTAMP)
        .component(" ")
        .group("")
        .eventClass("\t")
        .create();
    assertEquals(
        "{\"summary\":\"summary\",\"source\":\"source\",\"severity\":\"info\",\"timestamp\":\"2018-08-01T02:03:04Z\"}",
        payload.payloadJson()
    );
  }

  @Test
  void eventCustomDetailsObjectOverloadChains() {
    final var payload = PagerDutyEventPayload.build()
        .dedupKey("dk-o")
        .summary("s")
        .source("src")
        .severity(PagerDutySeverity.info)
        .timestamp(TIMESTAMP)
        .customDetails("a", (Object) "x")
        .customDetails("b", (Object) 2)
        .create();
    assertEquals(java.util.Map.of("a", (Object) "x", "b", 2), payload.customDetails());
  }

  @Test
  void prototypeWithNullCollections() {
    // a caller-supplied payload implementation may return null collections
    final var prototype = new PagerDutyChangeEventPayload() {
      @Override
      public ZonedDateTime timestamp() {
        return TIMESTAMP;
      }

      @Override
      public String summary() {
        return "proto";
      }

      @Override
      public String source() {
        return null;
      }

      @Override
      public java.util.Map<String, Object> customDetails() {
        return null;
      }

      @Override
      public java.util.List<PagerDutyLinkRef> links() {
        return null;
      }

      @Override
      public java.util.List<PagerDutyImageRef> images() {
        return null;
      }

      @Override
      public String payloadJson() {
        return "{}";
      }
    };
    final var copy = PagerDutyChangeEventPayload.build(prototype).create();
    assertEquals("proto", copy.summary());
    assertEquals(java.util.Map.of(), copy.customDetails());
    assertEquals(java.util.List.of(), copy.links());
    assertEquals(java.util.List.of(), copy.images());
  }

  @Test
  void singleEntryPrototypeCopyStaysImmutable() {
    final var prototype = PagerDutyChangeEventPayload.build()
        .summary("s")
        .timestamp(TIMESTAMP)
        .customDetails("k1", "v1")
        .link(PagerDutyLinkRef.build().href("h1").create())
        .image(PagerDutyImageRef.build().src("s1").create())
        .create();
    final var copy = PagerDutyChangeEventPayload.build(prototype).create();
    assertEquals(prototype.customDetails(), copy.customDetails());
    assertEquals(prototype.links(), copy.links());
    assertEquals(prototype.images(), copy.images());
    assertThrows(UnsupportedOperationException.class, () -> copy.customDetails().put("k2", "v2"));
    assertThrows(UnsupportedOperationException.class, () -> copy.links().add(PagerDutyLinkRef.build().href("h2").create()));
    assertThrows(UnsupportedOperationException.class, () -> copy.images().add(PagerDutyImageRef.build().src("s2").create()));
  }

  @Test
  void multiEntryPrototypeCopyIsIndependent() {
    final var l1 = PagerDutyLinkRef.build().href("h1").create();
    final var l2 = PagerDutyLinkRef.build().href("h2").create();
    final var l3 = PagerDutyLinkRef.build().href("h3").create();
    final var i1 = PagerDutyImageRef.build().src("s1").create();
    final var i2 = PagerDutyImageRef.build().src("s2").create();
    final var i3 = PagerDutyImageRef.build().src("s3").create();
    final var prototype = PagerDutyChangeEventPayload.build()
        .summary("s")
        .timestamp(TIMESTAMP)
        .customDetails("k1", "v1")
        .customDetails("k2", "v2")
        .link(l1)
        .link(l2)
        .image(i1)
        .image(i2)
        .create();
    final var copy = PagerDutyChangeEventPayload.build(prototype)
        .customDetails("k3", "v3")
        .link(l3)
        .image(i3)
        .create();
    assertEquals(java.util.Map.of("k1", (Object) "v1", "k2", "v2", "k3", "v3"), copy.customDetails());
    assertEquals(java.util.List.of(l1, l2, l3), copy.links());
    assertEquals(java.util.List.of(i1, i2, i3), copy.images());
    // the prototype is unchanged
    assertEquals(java.util.Map.of("k1", (Object) "v1", "k2", "v2"), prototype.customDetails());
    assertEquals(java.util.List.of(l1, l2), prototype.links());
    assertEquals(java.util.List.of(i1, i2), prototype.images());
  }

  @Test
  void refBuilderAccessors() {
    final var linkBuilder = PagerDutyLinkRef.build().href("h").text("t");
    assertEquals("h", linkBuilder.href());
    assertEquals("t", linkBuilder.text());
    final var link = linkBuilder.create();
    assertEquals("h", link.href());
    assertEquals("t", link.text());
    assertEquals(link.toJson(), link.toString());

    final var imageBuilder = PagerDutyImageRef.build().src("s").href("h").alt("a");
    assertEquals("s", imageBuilder.src());
    assertEquals("h", imageBuilder.href());
    assertEquals("a", imageBuilder.alt());
    final var image = imageBuilder.create();
    assertEquals("s", image.src());
    assertEquals("h", image.href());
    assertEquals("a", image.alt());
    assertEquals(image.toJson(), image.toString());
  }

  @Test
  void builderAccessors() {
    final var link = PagerDutyLinkRef.build().href("h").create();
    final var image = PagerDutyImageRef.build().src("s").create();
    final var builder = PagerDutyChangeEventPayload.build()
        .summary("summary")
        .source("source")
        .timestamp(TIMESTAMP)
        .customDetails("k", "v")
        .link(link)
        .image(image);
    assertEquals("summary", builder.summary());
    assertEquals("source", builder.source());
    assertEquals(TIMESTAMP, builder.timestamp());
    assertEquals(java.util.Map.of("k", (Object) "v"), builder.customDetails());
    assertEquals(java.util.List.of(link), builder.links());
    assertEquals(java.util.List.of(image), builder.images());
    assertEquals(builder.payloadJson(), builder.toString());

    final var payload = builder.create();
    assertEquals(payload.payloadJson(), payload.toString());

    assertEquals("{\"href\":\"h\"}", PagerDutyLinkRef.build().href("h").toString());
    assertEquals("{\"src\":\"s\"}", PagerDutyImageRef.build().src("s").toString());
  }

  @Test
  void blankSummaryIsIgnored() {
    final var builder = PagerDutyChangeEventPayload.build().summary(" ");
    assertNull(builder.summary());
    assertThrows(NullPointerException.class, builder::create);
  }

  @Test
  void regionZonedTimestampSerializesAsRfc3339() {
    final var timestamp = ZonedDateTime.of(2018, 8, 1, 2, 3, 4, 0, java.time.ZoneId.of("America/New_York"));
    final var changePayload = PagerDutyChangeEventPayload.build()
        .summary("summary")
        .timestamp(timestamp)
        .create();
    assertEquals("{\"summary\":\"summary\",\"timestamp\":\"2018-08-01T02:03:04-04:00\"}", changePayload.payloadJson());

    final var eventPayload = PagerDutyEventPayload.build()
        .dedupKey("dk-5")
        .summary("summary")
        .source("source")
        .severity(PagerDutySeverity.info)
        .timestamp(timestamp)
        .create();
    assertEquals(
        "{\"summary\":\"summary\",\"source\":\"source\",\"severity\":\"info\",\"timestamp\":\"2018-08-01T02:03:04-04:00\"}",
        eventPayload.payloadJson()
    );
  }

  @Test
  void changePayloadWithoutSource() {
    final var payload = PagerDutyChangeEventPayload.build()
        .summary("summary")
        .timestamp(TIMESTAMP)
        .create();
    assertEquals("{\"summary\":\"summary\",\"timestamp\":\"2018-08-01T02:03:04Z\"}", payload.payloadJson());
  }

  @Test
  void eventPayloadWithoutOptionalFields() {
    final var payload = PagerDutyEventPayload.build()
        .dedupKey("dk-4")
        .summary("summary")
        .source("source")
        .severity(PagerDutySeverity.info)
        .timestamp(TIMESTAMP)
        .create();
    assertEquals(
        "{\"summary\":\"summary\",\"source\":\"source\",\"severity\":\"info\",\"timestamp\":\"2018-08-01T02:03:04Z\"}",
        payload.payloadJson()
    );
  }

  @Test
  void prototypeCopyPreservesFields() {
    final var payload = PagerDutyEventPayload.build()
        .dedupKey("dk-2")
        .summary("summary")
        .source("source")
        .severity(PagerDutySeverity.warning)
        .timestamp(TIMESTAMP)
        .component("component")
        .group("group")
        .eventClass("class")
        .customDetails("k1", "v1")
        .customDetails("k2", 2)
        .link(PagerDutyLinkRef.build().href("href").text("text").create())
        .image(PagerDutyImageRef.build().src("src").href("href").alt("alt").create())
        .create();

    final var copy = PagerDutyEventPayload.build(payload).create();
    assertEquals(payload.dedupKey(), copy.dedupKey());
    assertEquals(payload.summary(), copy.summary());
    assertEquals(payload.source(), copy.source());
    assertEquals(payload.severity(), copy.severity());
    assertEquals(payload.timestamp(), copy.timestamp());
    assertEquals(payload.component(), copy.component());
    assertEquals(payload.group(), copy.group());
    assertEquals(payload.eventClass(), copy.eventClass());
    assertEquals(payload.customDetails(), copy.customDetails());
    assertEquals(payload.links(), copy.links());
    assertEquals(payload.images(), copy.images());
    assertEquals(payload.payloadJson(), copy.payloadJson());

    final var changeCopy = PagerDutyChangeEventPayload.build(payload).create();
    assertEquals(payload.summary(), changeCopy.summary());
    assertEquals(payload.source(), changeCopy.source());
    assertEquals(payload.timestamp(), changeCopy.timestamp());
    assertEquals(payload.customDetails(), changeCopy.customDetails());
    assertEquals(payload.links(), changeCopy.links());
    assertEquals(payload.images(), changeCopy.images());
  }
}
