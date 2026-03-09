package software.sava.incident.pagerduty.event.data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;

record PagerDutyChangeEventPayloadRecord(String summary,
                                         ZonedDateTime timestamp,
                                         String source,
                                         Map<String, Object> customDetails,
                                         List<PagerDutyLinkRef> links,
                                         List<PagerDutyImageRef> images,
                                         String json) implements PagerDutyChangeEventPayload {

  @Override
  public ZonedDateTime timestamp() {
    return timestamp;
  }

  @Override
  public String summary() {
    return summary;
  }

  @Override
  public String source() {
    return source;
  }

  @Override
  public Map<String, Object> customDetails() {
    return customDetails;
  }

  @Override
  public List<PagerDutyLinkRef> links() {
    return links;
  }

  @Override
  public List<PagerDutyImageRef> images() {
    return images;
  }

  @Override
  public String payloadJson() {
    return json;
  }

  @Override
  public String toString() {
    return payloadJson();
  }

  static class PagerDutyChangeEventPayloadBuilder implements PagerDutyChangeEventPayload.Builder {
    
    String summary;
    String source;
    ZonedDateTime timestamp;
    Map<String, Object> customDetails;
    List<PagerDutyLinkRef> links;
    List<PagerDutyImageRef> images;

    PagerDutyChangeEventPayloadBuilder() {
      this.customDetails = Map.of();
      this.links = List.of();
      this.images = List.of();
    }

    PagerDutyChangeEventPayloadBuilder(final PagerDutyChangeEventPayload prototype) {
      this.summary(prototype.summary());
      this.source(prototype.source());
      this.timestamp(prototype.timestamp());
      final var customDetails = prototype.customDetails();
      this.customDetails = customDetails == null || customDetails.isEmpty()
          ? Map.of()
          : customDetails.size() > 1
          ? new LinkedHashMap<>(customDetails)
          : Map.copyOf(customDetails);
      final var links = prototype.links();
      this.links = links == null || links.isEmpty()
          ? List.of()
          : links.size() > 1
          ? new ArrayList<>(links)
          : List.copyOf(links);
      final var images = prototype.images();
      this.images = images == null || images.isEmpty()
          ? List.of()
          : images.size() > 1
          ? new ArrayList<>(images)
          : List.copyOf(images);
    }

    @Override
    public PagerDutyChangeEventPayload create() {
      Objects.requireNonNull(summary, "'Summary' is a required payload field.");
      if (timestamp == null) {
        timestamp = ZonedDateTime.now(UTC);
      }
      final var json = payloadJson();
      return new PagerDutyChangeEventPayloadRecord(
          summary,
          timestamp,
          source,
          customDetails.size() > 1 ? Collections.unmodifiableMap(customDetails) : customDetails,
          links.size() > 1 ? Collections.unmodifiableList(links) : links,
          images.size() > 1 ? Collections.unmodifiableList(images) : images,
          json
      );
    }

    @Override
    public Builder summary(final String summary) {
      if (summary != null && !summary.isBlank()) {
        this.summary = summary.length() > 1_024 ? summary.substring(0, 1_024) : summary;
      }
      return this;
    }

    @Override
    public Builder source(final String source) {
      this.source = source;
      return this;
    }

    @Override
    public Builder timestamp(final ZonedDateTime timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    final Builder customDetailsObject(final String field, final Object fieldValue) {
      final var val = fieldValue == null ? "null" : fieldValue;
      if (customDetails == null || customDetails.isEmpty()) {
        customDetails = Map.of(field, val);
        return this;
      } else if (customDetails.size() == 1) {
        customDetails = new LinkedHashMap<>(customDetails);
      }
      customDetails.put(field, val);
      return this;
    }

    @Override
    public Builder customDetails(final String field, final String fieldValue) {
      return customDetailsObject(field, fieldValue);
    }

    @Override
    public Builder customDetails(final String field, final Boolean fieldValue) {
      return customDetailsObject(field, fieldValue);
    }

    @Override
    public Builder customDetails(final String field, final Number fieldValue) {
      return customDetailsObject(field, fieldValue);
    }

    @Override
    public Builder customDetails(final String field, final Object fieldValue) {
      return customDetailsObject(field, fieldValue == null ? null : fieldValue.toString());
    }

    @Override
    public Builder link(final PagerDutyLinkRef link) {
      if (links.isEmpty()) {
        links = List.of(link);
        return this;
      } else if (links.size() == 1) {
        links = new ArrayList<>(links);
      }
      links.add(link);
      return this;
    }

    @Override
    public Builder image(final PagerDutyImageRef image) {
      if (images.isEmpty()) {
        images = List.of(image);
        return this;
      } else if (images.size() == 1) {
        images = new ArrayList<>(images);
      }
      images.add(image);
      return this;
    }

    @Override
    public String summary() {
      return summary;
    }

    @Override
    public final String source() {
      return source;
    }

    @Override
    public final ZonedDateTime timestamp() {
      return timestamp;
    }

    @Override
    public final Map<String, Object> customDetails() {
      return customDetails;
    }

    @Override
    public final List<PagerDutyLinkRef> links() {
      return links;
    }

    @Override
    public final List<PagerDutyImageRef> images() {
      return images;
    }

    static void appendString(final StringBuilder jsonBuilder, final String field, final String str) {
      if (str != null && !str.isBlank()) {
        jsonBuilder.append(",\"");
        jsonBuilder.append(field);
        jsonBuilder.append("\":\"");
        jsonBuilder.append(str);
        jsonBuilder.append('"');
      }
    }

    private static String escapeQuotes(final String str) {
      final char[] chars = str.toCharArray();
      final char[] escaped = new char[chars.length << 1];
      char c;
      for (int escapes = 0, from = 0, dest = 0, to = 0; ; to++) {
        if (to == chars.length) {
          if (from == 0) {
            return str;
          } else {
            final int len = to - from;
            System.arraycopy(chars, from, escaped, dest, len);
            dest += len;
            return new String(escaped, 0, dest);
          }
        } else {
          c = chars[to];
          if (c == '\\') {
            escapes++;
          } else if (c == '"' && (escapes & 1) == 0) {
            final int len = to - from;
            System.arraycopy(chars, from, escaped, dest, len);
            dest += len;
            escaped[dest++] = '\\';
            from = to;
            escapes = 0;
          } else {
            escapes = 0;
          }
        }
      }
    }

    static String toJson(final Map<String, Object> object) {
      return object.entrySet().stream().map(entry -> {
        final var val = entry.getValue();
        final var stringOrRawVal = switch (val) {
          case null -> null;
          case BigDecimal bigDecimal -> bigDecimal.toPlainString();
          case BigInteger bigInteger -> bigInteger.toString();
          case Number number -> number;
          case Boolean bool -> bool;
          case Object obj -> {
            final var str = obj.toString();
            yield str.indexOf('"') < 0 ? str : escapeQuotes(str);
          }
        };
        final var key = entry.getKey();
        return switch (stringOrRawVal) {
          case null -> String.format("""
              "%s":null""", key
          );
          case String string -> String.format("""
              "%s":"%s\"""", key, string
          );
          case Object obj -> String.format("""
              "%s":%s""", key, obj
          );
        };
      }).collect(Collectors.joining(",", "{", "}"));
    }

    @Override
    public String payloadJson() {
      final var jsonBuilder = new StringBuilder(2_048);
      jsonBuilder.append(String.format("""
              {"summary":"%s\"""",
          summary
      ));
      appendString(jsonBuilder, "source", source);
      appendString(jsonBuilder, "timestamp", timestamp.toString());
      if (!customDetails.isEmpty()) {
        jsonBuilder.append("""
            ,"custom_details":""");
        jsonBuilder.append(toJson(customDetails));
      }
      return jsonBuilder.append('}').toString();
    }

    @Override
    public final String toString() {
      return payloadJson();
    }
  }
}
