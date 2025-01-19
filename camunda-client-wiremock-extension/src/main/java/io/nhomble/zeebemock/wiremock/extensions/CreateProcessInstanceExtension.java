package io.nhomble.zeebemock.wiremock.extensions;

import com.github.tomakehurst.wiremock.common.Metadata;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ServeEventListener;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.google.common.base.Preconditions;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;

public class CreateProcessInstanceExtension implements ServeEventListener {

    private final ZeebeClient zeebeClient;

    public CreateProcessInstanceExtension()  {
        this.zeebeClient = ZeebeClient.newClient();
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
        Preconditions.checkArgument(parameters.containsKey("bpmnProcessId"), "bpmnProcessId is required");

        boolean hasVersion = parameters.get("version") != null;
        boolean hasWithResult = parameters.get("withResult") != null;
        var withProcessId = zeebeClient.newCreateInstanceCommand()
                .bpmnProcessId(parameters.getString("bpmnProcessId"));
        CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 withVersion;
        if (hasVersion) {
            withVersion = withProcessId.version(parameters.getInt("version"));
        } else {
            withVersion = withProcessId.latestVersion();
        }

        var withVariables = withVersion.variables(parameters.getMetadata("variables", new Metadata()));

        if (hasWithResult) {
            withVariables.withResult().send().join();
        } else {
            withVariables.send().join();
        }
    }
}
