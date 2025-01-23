package io.github.nhomble.zeebemock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockJobHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(MockJobHandler.class);

  private final URI mockURI;
  private final ObjectMapper objectMapper;

  public MockJobHandler(URI mockURI) {
    this(mockURI, new ObjectMapper());
  }

  public MockJobHandler(URI mockURI, ObjectMapper objectMapper) {
    this.mockURI = mockURI;
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(JobClient client, ActivatedJob job) throws Exception {
    String suffix = job.getType();
    var endpoint = mockURI.resolve(suffix);
    var httpClient = HttpClient.newHttpClient();
    var request =
        HttpRequest.newBuilder(endpoint)
            .POST(HttpRequest.BodyPublishers.ofString(job.toJson()))
            .header("Content-Type", "application/json")
            .build();
    var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    log.info("Received mock response={}", response.body());
    ZeebeWiremockResponse wiremockResponse =
        objectMapper.readValue(response.body(), ZeebeWiremockResponse.class);
    log.info("Received mock command={}", wiremockResponse.command());
    if (CompleteResponse.COMMAND.equalsIgnoreCase(wiremockResponse.command())) {
      CompleteResponse completeResponse = (CompleteResponse) wiremockResponse;
      client
          .newCompleteCommand(job.getKey())
          .variables(completeResponse.getVariables())
          .send()
          .join();
    } else if (ErrorResponse.COMMAND.equalsIgnoreCase(wiremockResponse.command())) {
      ErrorResponse errorResponse = (ErrorResponse) wiremockResponse;
      client
          .newThrowErrorCommand(job.getKey())
          .errorCode(errorResponse.getErrorCode())
          .errorMessage(errorResponse.getErrorMessage())
          .send()
          .join();
    } else if (FailureResponse.COMMAND.equalsIgnoreCase(wiremockResponse.command())) {
      FailureResponse failResponse = (FailureResponse) wiremockResponse;
      client
          .newFailCommand(job.getKey())
          .retries(failResponse.getRetries())
          .errorMessage(failResponse.getErrorMessage())
          .retryBackoff(failResponse.getRetryBackoff())
          .variables(failResponse.getVariables())
          .send()
          .join();
    }
  }
}
