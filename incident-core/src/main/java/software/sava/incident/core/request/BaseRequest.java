package software.sava.incident.core.request;

import java.time.Duration;

public abstract class BaseRequest implements Request {

  protected final Duration timeout;

  protected BaseRequest(final Duration timeout) {
    this.timeout = timeout;
  }

  @Override
  public final Duration timeout() {
    return timeout;
  }
}
