package io.nhomble.zeebemock.wiremock.extensions;

import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.MappingsLoaderExtension;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.stubbing.StubMappings;
import io.nhomble.zeebemock.WiremockStubParser;
import java.util.List;
import java.util.stream.Collectors;

public class ZeebeMockMappingsLoaderExtension implements MappingsLoaderExtension {

  private final WiremockStubParser stubParser = new WiremockStubParser();

  @Override
  public String getName() {
    return "zeebemock/mappings-loader";
  }

  @Override
  public void loadMappingsInto(StubMappings stubMappings) {
    List<StubMapping> relevantMappings =
        stubMappings.getAll().stream()
            .filter(stubParser::isZeebeMockEnabled)
            .collect(Collectors.toList());

    for (StubMapping stub : relevantMappings) {
      stubMappings.removeMapping(stub);
      // glorified deep copy
      StubMapping zeebeMockEnhanced = StubMapping.buildFrom(StubMapping.buildJsonStringFor(stub));

      zeebeMockEnhanced.setRequest(
          newRequestPattern(
                  RequestMethod.POST, WireMock.urlEqualTo("/" + stubParser.parseJobType(stub)))
              .build());

      stubMappings.addMapping(zeebeMockEnhanced);
    }
  }
}
