package io.github.nhomble.zeebemock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.List;
import org.junit.jupiter.api.Test;

public class WiremockStubParserTest {

  static final String NO_METADATA =
      "{\"request\":{\"method\":\"GET\",\"url\":\"/plain\"},\"response\":{\"status\":200}}";
  static final String METADATA_WITHOUT_ZEEBEMOCK =
      "{\"request\":{\"method\":\"GET\",\"url\":\"/other\"},\"response\":{\"status\":200},"
          + "\"metadata\":{\"owner\":\"someone-else\"}}";
  static final String DISABLED =
      "{\"request\":{\"method\":\"POST\",\"url\":\"/disabled\"},\"response\":{\"status\":200},"
          + "\"metadata\":{\"zeebemock\":{\"enabled\":false,\"jobType\":\"disabled\"}}}";
  static final String MISSING_JOB_TYPE =
      "{\"request\":{\"method\":\"POST\",\"url\":\"/broken\"},\"response\":{\"status\":200},"
          + "\"metadata\":{\"zeebemock\":{\"enabled\":true}}}";
  static final String VALID =
      "{\"request\":{\"method\":\"POST\",\"url\":\"/ignored\"},\"response\":{\"status\":200},"
          + "\"metadata\":{\"zeebemock\":{\"enabled\":true,\"jobType\":\"payment\","
          + "\"tenantIds\":[\"t1\"]}}}";

  private final WiremockStubParser parser = new WiremockStubParser();

  private static StubMapping stub(String json) {
    return StubMapping.buildFrom(json);
  }

  @Test
  void notEnabledWithoutMetadata() {
    assertFalse(parser.isZeebeMockEnabled(stub(NO_METADATA)));
  }

  @Test
  void notEnabledWithoutZeebeMockKey() {
    assertFalse(parser.isZeebeMockEnabled(stub(METADATA_WITHOUT_ZEEBEMOCK)));
  }

  @Test
  void notEnabledWhenDisabled() {
    assertFalse(parser.isZeebeMockEnabled(stub(DISABLED)));
  }

  @Test
  void enabledButMissingJobTypeIsInvalid() {
    assertTrue(parser.isZeebeMockEnabled(stub(MISSING_JOB_TYPE)));
    assertFalse(parser.hasValidJobType(stub(MISSING_JOB_TYPE)));
  }

  @Test
  void validStub() {
    StubMapping s = stub(VALID);
    assertTrue(parser.isZeebeMockEnabled(s));
    assertTrue(parser.hasValidJobType(s));
    assertEquals("payment", parser.parseJobType(s));
    assertEquals(List.of("t1"), parser.parseTenantIds(s));
  }

  @Test
  void resolverSkipsMalformedAndUnrelatedStubs() {
    List<StubMapping> stubs =
        List.of(
            stub(NO_METADATA),
            stub(METADATA_WITHOUT_ZEEBEMOCK),
            stub(MISSING_JOB_TYPE),
            stub(DISABLED),
            stub(VALID));
    List<WorkerDefinition> workers = new WiremockWorkerResolver(null).resolve(stubs);

    assertEquals(1, workers.size());
    assertEquals("payment", workers.get(0).getJobType());
    assertEquals(List.of("t1"), workers.get(0).getTenantIds());
  }
}
