package io.nhomble.zeebemock.wiremock.extensions;

import com.github.tomakehurst.wiremock.common.Metadata;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ServeEventListener;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;

import java.time.Duration;

public class PublishMessageExtension implements ServeEventListener {

    private final ZeebeClient zeebeClient;

    public PublishMessageExtension() {
        this.zeebeClient = ZeebeClient.newClient();
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
        var withMessage = zeebeClient.newPublishMessageCommand()
                .messageName(parameters.getString("messageName"));
        PublishMessageCommandStep1.PublishMessageCommandStep3 withCorrelationKey;
        if (hasCorrelationKey) {
            withCorrelationKey = withMessage.correlationKey(parameters.getString("correlationKey"));
        } else {
            withCorrelationKey = withMessage.withoutCorrelationKey();
        }
        Duration duration = Duration.parse(parameters.getString("timeToLive", "PT0S"));

        withCorrelationKey
                .timeToLive(duration)
                .variables(parameters.getMetadata("variables", new Metadata())).send().join();
    }
}
