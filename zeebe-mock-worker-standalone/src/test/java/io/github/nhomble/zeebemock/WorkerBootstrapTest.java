package io.github.nhomble.zeebemock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep2;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkerBootstrapTest {

  private final Deque<Supplier<List<WorkerDefinition>>> resolutions = new ArrayDeque<>();
  private final Deque<Supplier<JobWorker>> opens = new ArrayDeque<>();
  private WorkerBootstrap bootstrap;

  @BeforeEach
  void setUp() {
    WorkerResolver resolver = () -> resolutions.removeFirst().get();
    ZeebeMockConfigurationProperties props = new ZeebeMockConfigurationProperties();
    props.setWiremockURI(URI.create("http://localhost:8080"));
    bootstrap = new WorkerBootstrap(resolver, fakeClient(), props);
  }

  @Test
  void refreshClosesPreviousGenerationExactlyOnceAndOpensNewOne() {
    FakeWorker first = new FakeWorker();
    FakeWorker second = new FakeWorker();
    FakeWorker third = new FakeWorker();

    resolveTo(def("a"));
    openReturns(first);
    bootstrap.registerWorkers();
    assertEquals(0, first.closeCount);

    resolveTo(def("a"));
    openReturns(second);
    bootstrap.registerWorkers();
    assertEquals(1, first.closeCount);
    assertEquals(0, second.closeCount);

    resolveTo(def("a"));
    openReturns(third);
    bootstrap.registerWorkers();
    // stale workers from earlier ticks are not re-closed
    assertEquals(1, first.closeCount);
    assertEquals(1, second.closeCount);
    assertEquals(0, third.closeCount);
  }

  @Test
  void resolveFailureLeavesExistingWorkersOpen() {
    FakeWorker existing = new FakeWorker();
    resolveTo(def("a"));
    openReturns(existing);
    bootstrap.registerWorkers();

    RuntimeException boom = new RuntimeException("wiremock unreachable");
    resolutions.add(
        () -> {
          throw boom;
        });
    RuntimeException thrown = assertThrows(RuntimeException.class, bootstrap::registerWorkers);
    assertSame(boom, thrown);
    assertEquals(0, existing.closeCount);

    // next successful tick swaps out the surviving generation
    FakeWorker replacement = new FakeWorker();
    resolveTo(def("a"));
    openReturns(replacement);
    bootstrap.registerWorkers();
    assertEquals(1, existing.closeCount);
    assertEquals(0, replacement.closeCount);
  }

  @Test
  void openFailureClosesPartiallyOpenedWorkersAndKeepsExisting() {
    FakeWorker existing = new FakeWorker();
    resolveTo(def("a"));
    openReturns(existing);
    bootstrap.registerWorkers();

    FakeWorker partial = new FakeWorker();
    resolveTo(def("a"), def("b"));
    openReturns(partial);
    opens.add(
        () -> {
          throw new IllegalStateException("open failed");
        });
    assertThrows(IllegalStateException.class, bootstrap::registerWorkers);
    assertEquals(0, existing.closeCount);
    assertEquals(1, partial.closeCount);
  }

  private void resolveTo(WorkerDefinition... defs) {
    resolutions.add(() -> List.of(defs));
  }

  private void openReturns(JobWorker worker) {
    opens.add(() -> worker);
  }

  private static WorkerDefinition def(String jobType) {
    return new WorkerDefinition(jobType, List.of());
  }

  private ZeebeClient fakeClient() {
    JobWorkerBuilderStep3 step3 =
        proxy(
            JobWorkerBuilderStep3.class,
            name -> {
              if (name.equals("open")) {
                return opens.removeFirst().get();
              }
              return null;
            });
    JobWorkerBuilderStep2 step2 = proxy(JobWorkerBuilderStep2.class, name -> step3);
    JobWorkerBuilderStep1 step1 = proxy(JobWorkerBuilderStep1.class, name -> step2);
    return proxy(ZeebeClient.class, name -> name.equals("newWorker") ? step1 : null);
  }

  private interface Answer {
    Object answer(String methodName);
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, Answer answer) {
    return (T)
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (self, method, args) -> {
              if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                  case "equals":
                    return self == args[0];
                  case "hashCode":
                    return System.identityHashCode(self);
                  default:
                    return type.getSimpleName() + "Fake";
                }
              }
              Object result = answer.answer(method.getName());
              if (result == null && method.getReturnType().isInstance(self)) {
                return self; // fluent builder methods such as tenantIds(...)
              }
              if (result == null) {
                throw new UnsupportedOperationException(method.getName());
              }
              return result;
            });
  }

  private static final class FakeWorker implements JobWorker {
    int closeCount;

    @Override
    public boolean isOpen() {
      return closeCount == 0;
    }

    @Override
    public boolean isClosed() {
      return closeCount > 0;
    }

    @Override
    public void close() {
      closeCount++;
    }
  }
}
