package io.nhomble.zeebemock;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.Map;

@JsonTypeName(ErrorResponse.COMMAND)
public class ErrorResponse implements ZeebeWiremockResponse {
  public static final String COMMAND = "THROW_ERROR";
  private String errorCode = "DEFAULT_ERROR_CODE";
  private String errorMessage = "DEFAULT_ERROR_MESSAGE";

  public String getErrorCode() {
    return errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Map<String, Object> getVariables() {
    return variables;
  }

  private Map<String, Object> variables = new HashMap<>();

  @Override
  public String command() {
    return COMMAND;
  }
}
