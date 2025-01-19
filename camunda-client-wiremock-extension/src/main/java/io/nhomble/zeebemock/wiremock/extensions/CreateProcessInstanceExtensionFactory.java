package io.nhomble.zeebemock.wiremock.extensions;

import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionFactory;
import com.github.tomakehurst.wiremock.extension.WireMockServices;
import java.util.List;

public class CreateProcessInstanceExtensionFactory implements ExtensionFactory {
  @Override
  public List<Extension> create(WireMockServices services) {
    return List.of(new CreateProcessInstanceExtension(services));
  }
}
