package software.sava.incident.pagerduty.exceptions;

import java.net.http.HttpResponse;
import java.util.List;

public interface PagerDutyClientException {

  boolean canBeRetried();

  HttpResponse<?> httpResponse();

  long errorCode();

  List<String> errors();
}
