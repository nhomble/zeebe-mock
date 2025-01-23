package io.github.nhomble.zeebemock.wiremock.extensions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.common.Metadata;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ServeEventListener;
import com.github.tomakehurst.wiremock.extension.WireMockServices;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.google.common.base.Preconditions;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import java.util.Map;

public class CreateProcessInstanceExtension implements ServeEventListener {

  private final ZeebeClient zeebeClient;
  private final WireMockServices wireMockServices;
  private final ObjectMapper objectMapper;

  public CreateProcessInstanceExtension(WireMockServices wireMockServices) {
    this.zeebeClient = ZeebeClient.newClient();
    this.wireMockServices = wireMockServices;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public boolean applyGlobally() {
    return false;
  }

  @Override
  public String getName() {
    return "zeebemock/create-process-instance";
  }

  @Override
  public void beforeResponseSent(ServeEvent serveEvent, Parameters parameters) {
    Preconditions.checkArgument(
        parameters.containsKey("bpmnProcessId"), "bpmnProcessId is required");

    boolean hasVersion = parameters.get("version") != null;
    boolean hasWithResult = parameters.get("withResult") != null;
    var withProcessId =
        zeebeClient.newCreateInstanceCommand().bpmnProcessId(parameters.getString("bpmnProcessId"));
    Map<String, Object> context =
        this.wireMockServices.getTemplateEngine().buildModelForRequest(serveEvent.getRequest());
    CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 withVersion;
    if (hasVersion) {
      withVersion = withProcessId.version(parameters.getInt("version"));
    } else {
      withVersion = withProcessId.latestVersion();
    }

    try {
      String json =
          objectMapper.writeValueAsString(parameters.getMetadata("variables", new Metadata()));
      String templated =
          this.wireMockServices.getTemplateEngine().getUncachedTemplate(json).apply(context);
      var withVariables = withVersion.variables(templated);

      if (hasWithResult) {
        withVariables.withResult().send().join();
      } else {
        withVariables.send().join();
      }
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
