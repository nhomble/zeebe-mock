package io.github.nhomble.zeebemock;

import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;

import com.github.tomakehurst.wiremock.admin.model.ListStubMappingsResult;
import com.github.tomakehurst.wiremock.client.HttpAdminClient;
import com.github.tomakehurst.wiremock.common.Metadata;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.ArrayList;
import java.util.List;

public class WiremockStubParser {

  public boolean isZeebeMockEnabled(StubMapping stub) {
    return stub.getMetadata().getMetadata("zeebemock").getBoolean("enabled");
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
