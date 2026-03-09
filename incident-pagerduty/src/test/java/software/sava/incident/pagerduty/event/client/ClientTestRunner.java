package software.sava.incident.pagerduty.event.client;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;

final class ClientTestRunner {

  @TestFactory
  List<DynamicTest> runEventClientTests() {
    return List.of(ClientTests.createTest(new EventClientTests()));
  }
}
