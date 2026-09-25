package io.github.nhomble.zeebemock;

import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;

import com.github.tomakehurst.wiremock.admin.model.ListStubMappingsResult;
import com.github.tomakehurst.wiremock.client.HttpAdminClient;
import com.github.tomakehurst.wiremock.common.Metadata;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WiremockStubParser {

  private static final Logger log = LoggerFactory.getLogger(WiremockStubParser.class);

  static final String ZEEBEMOCK_KEY = "zeebemock";

  /**
   * Null-safe check for {@code metadata.zeebemock.enabled == true}. Stubs without metadata, without
   * a {@code zeebemock} block, or with a non-boolean {@code enabled} value are not zeebe-mock
   * stubs.
   */
  public boolean isZeebeMockEnabled(StubMapping stub) {
    Map<?, ?> zeebemock = zeebeMockMetadata(stub);
    return zeebemock != null && Boolean.TRUE.equals(zeebemock.get("enabled"));
  }

  /**
   * Whether a stub (expected to already be zeebemock-enabled) has a usable {@code jobType}. Callers
   * should filter on {@link #isZeebeMockEnabled} first. Logs and rejects a missing/blank jobType
   * instead of throwing, so a single malformed stub does not abort loading of all the others.
   */
  public boolean hasValidJobType(StubMapping stub) {
    Object jobType = zeebeMockMetadata(stub).get("jobType");
    if (jobType instanceof String && !((String) jobType).isBlank()) {
      return true;
    }
    log.warn(
        "Skipping zeebemock-enabled stub id={} name={}: metadata.zeebemock.jobType is missing or"
            + " not a non-blank string (got {})",
        stub.getId(),
        stub.getName(),
        jobType);
    return false;
  }

  private static Map<?, ?> zeebeMockMetadata(StubMapping stub) {
    Metadata metadata = stub == null ? null : stub.getMetadata();
    if (metadata == null) {
      return null;
    }
    Object zeebemock = metadata.get(ZEEBEMOCK_KEY);
    return zeebemock instanceof Map ? (Map<?, ?>) zeebemock : null;
  }

  public ListStubMappingsResult findAllStubsByMetadata(HttpAdminClient admin) {
    return admin.findAllStubsByMetadata(matchingJsonPath("$.zeebemock.enabled"));
  }

  public String parseJobType(StubMapping stub) {
    return stub.getMetadata().getMetadata("zeebemock", new Metadata()).getString("jobType");
  }

  public List<String> parseTenantIds(StubMapping stub) {
    Object o =
        stub.getMetadata().getMetadata("zeebemock").getOrDefault("tenantIds", new ArrayList<>());
    List<?> l = o instanceof List ? (List<?>) o : new ArrayList<>();
    List<String> ret = new ArrayList<>();
    l.stream().filter(t -> t instanceof String).forEach(t -> ret.add((String) t));
    return ret;
  }
}
