package io.github.nhomble.zeebemock.wiremock.extensions;

import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionFactory;
import com.github.tomakehurst.wiremock.extension.WireMockServices;
import java.util.List;

public class PublishMessageExtensionFactory implements ExtensionFactory {
  @Override
  public List<Extension> create(WireMockServices services) {
    return List.of(new PublishMessageExtension(services));
  }
}
