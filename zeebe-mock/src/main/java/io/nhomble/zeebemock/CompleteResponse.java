package io.nhomble.zeebemock;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.Map;

@JsonTypeName(CompleteResponse.COMMAND)
public class CompleteResponse implements ZeebeWiremockResponse {

  public static final String COMMAND = "COMPLETE";
  private Map<String, Object> variables = new HashMap<>();

  public Map<String, Object> getVariables() {
    return variables;
  }

  @Override
  public String command() {
    return COMMAND;
  }
}
