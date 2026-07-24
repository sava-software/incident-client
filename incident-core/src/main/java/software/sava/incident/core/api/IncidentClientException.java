package software.sava.incident.core.api;

import java.net.http.HttpResponse;
import java.util.List;

/// Implemented by each provider's request/parse exceptions so retry logic can be written
/// against the common client interface.
public interface IncidentClientException {

  boolean canBeRetried();

  HttpResponse<?> httpResponse();

  long errorCode();

  List<String> errors();
}
