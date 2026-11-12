package software.sava.incident.pagerduty.event.data;

record PagerDutyLinkRefVal(String href, String text) implements PagerDutyLinkRef {

  /// The Events API v2 schema requires a link's `href`: null is refused with a
  /// [NullPointerException], blank with an [IllegalArgumentException].
  static String requireHref(final String href) {
    java.util.Objects.requireNonNull(href, "'href' is a required link field.");
    if (href.isBlank()) {
      throw new IllegalArgumentException("'href' is a required link field.");
    }
    return href;
  }

  @Override
  public String href() {
    return href;
  }

  @Override
  public String text() {
    return text;
  }

  static final class PagerDutyLinkRefBuilder implements PagerDutyLinkRef.Builder {

    private String href;
    private String text;

    PagerDutyLinkRefBuilder() {
    }

    @Override
    public PagerDutyLinkRef create() {
      return new PagerDutyLinkRefVal(requireHref(href), text);
    }

    @Override
    public Builder href(final String href) {
      this.href = href;
      return this;
    }

    @Override
    public Builder text(final String text) {
      this.text = text;
      return this;
    }

    @Override
    public String href() {
      return href;
    }

    @Override
    public String text() {
      return text;
    }

    @Override
    public String toString() {
      return toJson();
    }
  }

  @Override
  public String toString() {
    return toJson();
  }
}
