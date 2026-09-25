package io.github.nhomble.zeebemock.wiremock.extensions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.WireMockServices;
import com.github.tomakehurst.wiremock.extension.responsetemplating.TemplateEngine;
import com.github.tomakehurst.wiremock.http.ImmutableRequest;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.camunda.zeebe.client.ZeebeClient;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CreateProcessInstanceExtensionTest {

  private final List<String> calls = new ArrayList<>();
  // the request model is irrelevant to command building; avoid needing a fully populated Request
  private final TemplateEngine templateEngine =
      new TemplateEngine(Map.of(), null, Set.of(), false, List.of()) {
        @Override
        public Map<String, Object> buildModelForRequest(Request request) {
          return Map.of();
        }
      };

  @Test
  void withResultTrueCallsWithResult() {
    run(Map.of("bpmnProcessId", "proc", "withResult", true));
    assertTrue(calls.contains("withResult"), calls.toString());
  }

  @Test
  void withResultFalseDoesNotCallWithResult() {
    run(Map.of("bpmnProcessId", "proc", "withResult", false));
    assertFalse(calls.contains("withResult"), calls.toString());
    assertTrue(calls.contains("send"), calls.toString());
  }

  @Test
  void withResultAbsentDoesNotCallWithResult() {
    run(Map.of("bpmnProcessId", "proc"));
    assertFalse(calls.contains("withResult"), calls.toString());
    assertTrue(calls.contains("send"), calls.toString());
  }

  private void run(Map<String, Object> params) {
    ZeebeClient zeebeClient = recording(ZeebeClient.class);
    WireMockServices services =
        (WireMockServices)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {WireMockServices.class},
                (proxy, method, args) ->
                    "getTemplateEngine".equals(method.getName()) ? templateEngine : null);
    ServeEvent serveEvent =
        ServeEvent.of(
            ImmutableRequest.create()
                .withAbsoluteUrl("http://localhost/start")
                .withMethod(RequestMethod.POST)
                .build());

    new CreateProcessInstanceExtension(services, zeebeClient)
        .beforeResponseSent(serveEvent, Parameters.from(new HashMap<>(params)));
  }

  /**
   * Returns a proxy that records every method name invoked on it and, for interface return types,
   * returns another recording proxy so the Zeebe fluent command chain can be followed.
   */
  @SuppressWarnings("unchecked")
  private <T> T recording(Class<T> type) {
    return (T)
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "equals":
                  return proxy == args[0];
                case "hashCode":
                  return System.identityHashCode(proxy);
                case "toString":
                  return type.getSimpleName() + "Proxy";
                default:
                  calls.add(method.getName());
              }
              Class<?> returnType = method.getReturnType();
              return returnType.isInterface() ? recording(returnType) : null;
            });
  }
}
