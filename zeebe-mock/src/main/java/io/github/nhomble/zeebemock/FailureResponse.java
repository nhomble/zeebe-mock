package io.github.nhomble.zeebemock;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@JsonTypeName(FailureResponse.COMMAND)
public class FailureResponse implements ZeebeWiremockResponse {
  public static final String COMMAND = "FAILURE";

  private Integer retries = 0;
  private Duration retryBackoff = Duration.ofSeconds(1);
  private String errorMessage = "DEFAULT_ERROR_MESSAGE";
  private Map<String, Object> variables = new HashMap<>();

  public Integer getRetries() {
    return retries;
  }

  public Duration getRetryBackoff() {
    return retryBackoff;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Map<String, Object> getVariables() {
    return variables;
  }

  @Override
  public String command() {
    return COMMAND;
  }
}
