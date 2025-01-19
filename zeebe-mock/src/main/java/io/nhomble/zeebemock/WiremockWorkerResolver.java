package io.nhomble.zeebemock;

import com.github.tomakehurst.wiremock.admin.model.ListStubMappingsResult;
import com.github.tomakehurst.wiremock.client.HttpAdminClient;
import com.github.tomakehurst.wiremock.http.RequestMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;

/**
 * resolve zeebe worker jobTypes by extracting the stubs from the configured
 */
public class WiremockWorkerResolver implements WorkerResolver {

    private final HttpAdminClient httpAdminClient;

    public WiremockWorkerResolver(HttpAdminClient httpAdminClient) {
        this.httpAdminClient = httpAdminClient;
    }

    @Override
    public List<WorkerDefinition> resolve() {
        ListStubMappingsResult stubs = httpAdminClient.findAllStubsByMetadata(matchingJsonPath("$.zeebemock.enabled"));
        Map<String, List<String>> relevantMappings = stubs.getMappings().stream()
                .filter(stub -> stub
                        .getMetadata()
                        .getMetadata("zeebemock")
                        .getBoolean("enabled")
                )
                .filter(stub -> RequestMethod.POST.equals(stub.getRequest().getMethod()))
                .collect(Collectors.toMap(
                        stub -> {
                            return stub.getRequest().getUrl().substring(1); // strip leading /
                        },
                        stub -> {
                            Object o = stub.getMetadata().getMetadata("zeebemock").getOrDefault("tenantIds", new ArrayList<>());
                            List<?> l = o instanceof List ? (List<?>) o : new ArrayList<>();
                            List<String> ret = new ArrayList<>();
                            l.stream().filter(t -> t instanceof String).forEach(t -> ret.add((String) t));
                            return ret;
                        },
                        (curr, next) -> {
                            curr.addAll(next);
                            return curr;
                        }
                ));
        List<WorkerDefinition> ret = new ArrayList<>();
        for(Map.Entry<String, List<String>> entry : relevantMappings.entrySet()) {
            ret.add(new WorkerDefinition(entry.getKey(), entry.getValue()));
        }
        return ret;
    }
}
