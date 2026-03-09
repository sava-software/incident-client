package software.sava.incident.pagerduty.event.data;

record PagerDutyImageRefVal(String src,
                            String href,
                            String alt) implements PagerDutyImageRef {

  @Override
  public String src() {
    return src;
  }

  @Override
  public String href() {
    return href;
  }

  @Override
  public String alt() {
    return alt;
  }

  static final class PagerDutyImageRefBuilder implements PagerDutyImageRef.Builder {

    private String src;
    private String href;
    private String alt;

    PagerDutyImageRefBuilder() {
    }

    @Override
    public PagerDutyImageRef create() {
      return new PagerDutyImageRefVal(src, href, alt);
    }

    @Override
    public Builder src(final String src) {
      this.src = src;
      return this;
    }

    @Override
    public Builder href(final String href) {
      this.href = href;
      return this;
    }

    @Override
    public Builder alt(final String alt) {
      this.alt = alt;
      return this;
    }

    @Override
    public String src() {
      return src;
    }

    @Override
    public String href() {
      return href;
    }

    @Override
    public String alt() {
      return alt;
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
