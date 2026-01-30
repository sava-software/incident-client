package software.sava.incident.core.request;

import java.time.Duration;

public interface Request {

  Duration timeout();

  class Builder {

    private Duration timeout;

    public Builder timeout(final Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    public Duration timeout() {
      return timeout;
    }
  }
}
