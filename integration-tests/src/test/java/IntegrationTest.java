import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class IntegrationTest {

  static Logger log = LoggerFactory.getLogger(IntegrationTest.class);
  static Path PROJECT_ROOT = Path.of("..");
  static Path ZEEBE_RESOURCES = PROJECT_ROOT.resolve(Path.of("local", "zeebe-resources"));

  static {
    log.info("PROJECT_ROOT={}", PROJECT_ROOT.toAbsolutePath());
  }

  // create via dc because that's how I am going to debug the compose anyway instead of learning yet
  // another testcontainer builder syntax
  @Container
  static ComposeContainer dc =
      new ComposeContainer(
              PROJECT_ROOT
                  .resolve(Path.of("local", "docker-compose.integration-test.yaml"))
                  .toFile())
          .withCopyFilesInContainer(PROJECT_ROOT.toString())
          .withLogConsumer("zeebe-mock", new Slf4jLogConsumer(log).withPrefix("zeebe-mock"))
          .withLogConsumer("zeebe", new Slf4jLogConsumer(log).withPrefix("zeebe"))
          .withPull(true);

  static ZeebeClient zeebeClient = ZeebeClient.newClientBuilder().usePlaintext().build();

  @BeforeEach
  void setup() {
    awaitTopology();
  }

  void awaitTopology() {
    await()
        .alias("zeebe topology (healthcheck)")
        .atMost(Duration.ofSeconds(60))
        .until(
            () -> {
              try {
                return zeebeClient.newTopologyRequest().send().join().getClusterSize() == 1;
              } catch (Exception e) {
                return false;
              }
            });
  }

  @Test
  void simpleWorkflow() {
    zeebeClient
        .newDeployResourceCommand()
        .addResourceFile(
            ZEEBE_RESOURCES.resolve("complete-simple-workflow.bpmn").toFile().getAbsolutePath())
        .send()
        .join();
    ProcessInstanceResult result =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("SimpleComplete")
            .latestVersion()
            .withResult()
            .send()
            .join();
    assertEquals("world", result.getVariable("hello"));
  }

  @Test
  void parentTriggersChildWorkflow() {
    zeebeClient
        .newDeployResourceCommand()
        .addResourceFile(
            ZEEBE_RESOURCES.resolve("parent-awaits-child.bpmn").toFile().getAbsolutePath())
        .send()
        .join();
    zeebeClient
        .newDeployResourceCommand()
        .addResourceFile(
            ZEEBE_RESOURCES.resolve("child-events-parent.bpmn").toFile().getAbsolutePath())
        .send()
        .join();
    String id = UUID.randomUUID().toString();
    ProcessInstanceResult result =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("ParentAwaitsChild")
            .latestVersion()
            .variable("id", id)
            .withResult()
            .send()
            .join();
    assertEquals(id, result.getVariable("id"));
    assertEquals("triggered child workflow", result.getVariable("parent"));
    assertEquals("trigger event", result.getVariable("child"));
  }
}
