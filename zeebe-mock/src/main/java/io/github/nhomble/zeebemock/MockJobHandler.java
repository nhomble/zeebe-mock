package io.github.nhomble.zeebemock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockJobHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(MockJobHandler.class);
  // same default as FailureResponse.retryBackoff
  private static final Duration MALFORMED_RESPONSE_RETRY_BACKOFF = Duration.ofSeconds(1);

  static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

  // HttpClient is thread-safe and owns a connection pool + selector thread, so share one across
  // all handlers. WorkerBootstrap builds a new MockJobHandler per jobType on every refresh cycle,
  // so a per-instance client would still be recreated (and leaked) every refresh.
  private static final HttpClient SHARED_HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();

  private final URI mockURI;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public MockJobHandler(URI mockURI) {
    this(mockURI, defaultObjectMapper());
  }

  static ObjectMapper defaultObjectMapper() {
    // Tolerate extra/typo'd fields in stub bodies; an unknown "command" still fails via
    // InvalidTypeIdException and is reported explicitly in handle().
    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public MockJobHandler(URI mockURI, ObjectMapper objectMapper) {
    this(mockURI, objectMapper, SHARED_HTTP_CLIENT);
  }

  MockJobHandler(URI mockURI, ObjectMapper objectMapper, HttpClient httpClient) {
    this.mockURI = mockURI;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  HttpClient httpClient() {
    return httpClient;
  }

  /**
   * Build {@code <base>/<jobType>} by explicit path concatenation. {@code URI.resolve} is not used
   * because under RFC 3986 a base without a trailing slash ({@code http://h/wiremock}) has its last
   * segment replaced ({@code http://h/myjob}), and a jobType containing a colon (e.g. {@code
   * io.camunda:http-json:1}) parses as an absolute URI with its own scheme.
   */
  static URI endpointFor(URI base, String jobType) {
    String basePath = base.getPath() == null ? "" : base.getPath();
    while (basePath.endsWith("/")) {
      basePath = basePath.substring(0, basePath.length() - 1);
    }
    try {
      return new URI(base.getScheme(), base.getAuthority(), basePath + "/" + jobType, null, null);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(
          "cannot build mock endpoint from base=" + base + " jobType=" + jobType, e);
    }
  }

  @Override
  public void handle(JobClient client, ActivatedJob job) throws Exception {
    var endpoint = endpointFor(mockURI, job.getType());
    var request =
        HttpRequest.newBuilder(endpoint)
            .timeout(HTTP_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(job.toJson()))
            .header("Content-Type", "application/json")
            .build();
    var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    log.info("Received mock response={}", response.body());
    ZeebeWiremockResponse wiremockResponse;
    try {
      wiremockResponse = objectMapper.readValue(response.body(), ZeebeWiremockResponse.class);
    } catch (JsonProcessingException e) {
      failMalformedMock(
          client,
          job,
          "could not parse mock response for jobType="
              + job.getType()
              + ": "
              + e.getOriginalMessage());
      return;
    }
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
          .variables(errorResponse.getVariables())
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
    } else {
      failMalformedMock(
          client,
          job,
          "unsupported mock command="
              + wiremockResponse.command()
              + " for jobType="
              + job.getType());
    }
  }

  /**
   * Explicitly fail the job with a readable message so a malformed stub surfaces in the
   * job/incident history, rather than letting the JobWorker report a raw stack trace. Mirrors the
   * worker's own convention of decrementing retries; at 0 Zeebe raises an incident.
   */
  private static void failMalformedMock(JobClient client, ActivatedJob job, String reason) {
    String message = "zeebe-mock: " + reason;
    log.warn("Failing jobKey={}: {}", job.getKey(), message);
    client
        .newFailCommand(job.getKey())
        .retries(Math.max(0, job.getRetries() - 1))
        .errorMessage(message)
        .retryBackoff(MALFORMED_RESPONSE_RETRY_BACKOFF)
        .send()
        .join();
  }
}
