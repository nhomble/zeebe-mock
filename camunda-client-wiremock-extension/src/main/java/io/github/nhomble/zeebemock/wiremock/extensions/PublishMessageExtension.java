package io.github.nhomble.zeebemock.wiremock.extensions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.common.Metadata;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ServeEventListener;
import com.github.tomakehurst.wiremock.extension.WireMockServices;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import java.time.Duration;
import java.util.Map;

public class PublishMessageExtension implements ServeEventListener {

  private final ZeebeClient zeebeClient;
  private final WireMockServices wireMockServices;
  private final ObjectMapper objectMapper;

  public PublishMessageExtension(WireMockServices wireMockServices) {
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
    return "zeebemock/publish-message";
  }

  @Override
  public void beforeResponseSent(ServeEvent serveEvent, Parameters parameters) {
    boolean hasCorrelationKey = parameters.get("correlationKey") != null;
    var withMessage =
        zeebeClient.newPublishMessageCommand().messageName(parameters.getString("messageName"));
    PublishMessageCommandStep1.PublishMessageCommandStep3 withCorrelationKey;
    Map<String, Object> context =
        this.wireMockServices.getTemplateEngine().buildModelForRequest(serveEvent.getRequest());
    if (hasCorrelationKey) {
      String key =
          this.wireMockServices
              .getTemplateEngine()
              .getUncachedTemplate(parameters.getString("correlationKey"))
              .apply(context);
      withCorrelationKey = withMessage.correlationKey(key);
    } else {
      withCorrelationKey = withMessage.withoutCorrelationKey();
    }
    Duration duration = Duration.parse(parameters.getString("timeToLive", "PT0S"));

    try {
      String json =
          objectMapper.writeValueAsString(parameters.getMetadata("variables", new Metadata()));
      String templated =
          this.wireMockServices.getTemplateEngine().getUncachedTemplate(json).apply(context);
      withCorrelationKey.timeToLive(duration).variables(templated).send().join();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
