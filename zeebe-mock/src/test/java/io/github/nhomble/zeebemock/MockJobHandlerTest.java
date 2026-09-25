package io.github.nhomble.zeebemock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class MockJobHandlerTest {

  private static final String JOB_TYPE = "my-job";
  private static final long JOB_KEY = 42L;

  private HttpServer server;

  /** A recorded builder call, e.g. {@code retries [2]}. */
  static final class Call {
    private final String method;
    private final Object[] args;

    Call(String method, Object[] args) {
      this.method = method;
      this.args = args;
    }

    String method() {
      return method;
    }

    Object[] args() {
      return args;
    }
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  private URI serve(String body) throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/" + JOB_TYPE,
        exchange -> {
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    return URI.create("http://localhost:" + server.getAddress().getPort() + "/");
  }

  /** Proxy that records every call and returns a further recording proxy for fluent builders. */
  @SuppressWarnings("unchecked")
  private static <T> T recorder(Class<T> type, List<Call> calls) {
    return (T)
        Proxy.newProxyInstance(
            MockJobHandlerTest.class.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> {
              if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                  case "hashCode":
                    return System.identityHashCode(proxy);
                  case "equals":
                    return proxy == args[0];
                  default:
                    return type.getSimpleName() + "Recorder";
                }
              }
              calls.add(new Call(method.getName(), args == null ? new Object[0] : args));
              Class<?> ret = method.getReturnType();
              return ret.isInterface() ? recorder(ret, calls) : null;
            });
  }

  private static ActivatedJob job() {
    return (ActivatedJob)
        Proxy.newProxyInstance(
            MockJobHandlerTest.class.getClassLoader(),
            new Class<?>[] {ActivatedJob.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "getType":
                  return JOB_TYPE;
                case "getKey":
                  return JOB_KEY;
                case "getRetries":
                  return 3;
                case "toJson":
                  return "{}";
                case "hashCode":
                  return System.identityHashCode(proxy);
                case "equals":
                  return proxy == args[0];
                default:
                  return null;
              }
            });
  }

  private static Call find(List<Call> calls, String method) {
    return calls.stream()
        .filter(c -> c.method().equals(method))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no call to " + method + " in " + names(calls)));
  }

  private static List<String> names(List<Call> calls) {
    List<String> out = new ArrayList<>();
    calls.forEach(c -> out.add(c.method()));
    return out;
  }

  @Test
  void unknownCommandFailsJobWithClearMessage() throws Exception {
    URI uri = serve("{\"command\":\"COMPLETEE\",\"variables\":{\"a\":1}}");
    List<Call> calls = new ArrayList<>();

    // must not throw: the handler reports the malformed stub itself
    new MockJobHandler(uri).handle(recorder(JobClient.class, calls), job());

    assertEquals(JOB_KEY, find(calls, "newFailCommand").args()[0]);
    assertEquals(2, find(calls, "retries").args()[0]);
    assertEquals(Duration.ofSeconds(1), find(calls, "retryBackoff").args()[0]);
    String message = (String) find(calls, "errorMessage").args()[0];
    assertTrue(
        message.startsWith("zeebe-mock: could not parse mock response for jobType=" + JOB_TYPE),
        message);
    assertTrue(message.contains("COMPLETEE"), message);
    assertTrue(names(calls).contains("send"));
    assertFalse(names(calls).contains("newCompleteCommand"));
  }

  @Test
  void extraUnknownPropertyStillCompletesJob() throws Exception {
    URI uri = serve("{\"command\":\"COMPLETE\",\"variables\":{\"a\":1},\"retires\":5}");
    List<Call> calls = new ArrayList<>();

    new MockJobHandler(uri).handle(recorder(JobClient.class, calls), job());

    assertEquals(JOB_KEY, find(calls, "newCompleteCommand").args()[0]);
    assertEquals(Map.of("a", 1), find(calls, "variables").args()[0]);
    assertTrue(names(calls).contains("send"));
    assertFalse(names(calls).contains("newFailCommand"));
  }
}
