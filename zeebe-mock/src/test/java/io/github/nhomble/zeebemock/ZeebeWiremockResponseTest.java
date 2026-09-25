package io.github.nhomble.zeebemock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public class ZeebeWiremockResponseTest {

  private ObjectMapper objectMapper = MockJobHandler.defaultObjectMapper();

  @Test
  void parseCompleteNoVariables() throws JsonProcessingException {
    String s = "{\"command\":\"COMPLETE\"}";
    ZeebeWiremockResponse response = objectMapper.readValue(s, ZeebeWiremockResponse.class);
    assertEquals("COMPLETE", response.command());
    assertTrue(response instanceof CompleteResponse);
    assertEquals(0, ((CompleteResponse) response).getVariables().size());
  }

  @Test
  void parseErrorNoVariables() throws JsonProcessingException {
    String s = "{\"command\":\"THROW_ERROR\"}";
    ZeebeWiremockResponse response = objectMapper.readValue(s, ZeebeWiremockResponse.class);
    assertEquals("THROW_ERROR", response.command());
    assertTrue(response instanceof ErrorResponse);
    assertEquals(0, ((ErrorResponse) response).getVariables().size());
  }

  @Test
  void parseFailureNoVariables() throws JsonProcessingException {
    String s = "{\"command\":\"FAILURE\"}";
    ZeebeWiremockResponse response = objectMapper.readValue(s, ZeebeWiremockResponse.class);
    assertEquals("FAILURE", response.command());
    assertTrue(response instanceof FailureResponse);
    assertEquals(0, ((FailureResponse) response).getVariables().size());
  }

  @Test
  void parseFailureWithRetryBackoff() throws JsonProcessingException {
    String s = "{\"command\":\"FAILURE\",\"retries\":2,\"retryBackoff\":\"PT5S\"}";
    ZeebeWiremockResponse response = objectMapper.readValue(s, ZeebeWiremockResponse.class);
    assertTrue(response instanceof FailureResponse);
    FailureResponse failure = (FailureResponse) response;
    assertEquals(Duration.ofSeconds(5), failure.getRetryBackoff());
    assertEquals(2, failure.getRetries());
  }
}
