package io.nhomble.zeebemock;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CompleteResponse.class, name = CompleteResponse.COMMAND),
  @JsonSubTypes.Type(value = ErrorResponse.class, name = ErrorResponse.COMMAND),
  @JsonSubTypes.Type(value = FailureResponse.class, name = FailureResponse.COMMAND)
})
public interface ZeebeWiremockResponse {

  String command();
}
