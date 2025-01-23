package io.github.nhomble.zeebemock;

import com.github.tomakehurst.wiremock.admin.model.ListStubMappingsResult;
import com.github.tomakehurst.wiremock.client.HttpAdminClient;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** resolve zeebe worker jobTypes by extracting the stubs from the configured */
public class WiremockWorkerResolver implements WorkerResolver {

  private final HttpAdminClient httpAdminClient;
  private final WiremockStubParser stubParser = new WiremockStubParser();

  public WiremockWorkerResolver(HttpAdminClient httpAdminClient) {
    this.httpAdminClient = httpAdminClient;
  }

  @Override
  public List<WorkerDefinition> resolve() {
    ListStubMappingsResult stubs = stubParser.findAllStubsByMetadata(httpAdminClient);
    Map<String, List<String>> relevantMappings =
        stubs.getMappings().stream()
            .filter(stubParser::isZeebeMockEnabled)
            .filter(stub -> RequestMethod.POST.equals(stub.getRequest().getMethod()))
            .collect(
                Collectors.toMap(
                    stubParser::parseJobType,
                    stubParser::parseTenantIds,
                    (curr, next) -> {
                      curr.addAll(next);
                      return curr;
                    }));
    List<WorkerDefinition> ret = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : relevantMappings.entrySet()) {
      ret.add(new WorkerDefinition(entry.getKey(), entry.getValue()));
    }
    return ret;
  }
}
