import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.CamundaOperateClient.Builder;
import io.camunda.operate.auth.SimpleAuthentication;
import io.camunda.operate.dto.Incident;
import io.camunda.operate.search.IncidentFilter;
import io.camunda.operate.search.SearchQuery;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
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
          .withPull(true);

  static ZeebeClient zeebeClient = ZeebeClient.newClientBuilder().usePlaintext().build();
  static CamundaOperateClient operateClient;

  @BeforeAll
  static void startup() {
    awaitTopology();
    awaitOperateClient();
  }

  static void awaitOperateClient() {
    // defined in docker-compose.integration-test.yaml
    String operateURL = "http://localhost:8081";
    await("await operate client bootstrap")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () -> {
              try {
                operateClient =
                    new Builder()
                        .operateUrl(operateURL)
                        // default operate login
                        .authentication(new SimpleAuthentication("demo", "demo", operateURL))
                        .build();
                return true;
              } catch (Exception e) {
                return false;
              }
            });
  }

  static void awaitTopology() {
    await()
        .alias("await zeebe topology (healthcheck) has brokers")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () -> {
              try {
                return zeebeClient.newTopologyRequest().send().join().getClusterSize() == 1;
              } catch (Exception e) {
                return false;
              }
            });
  }

  void awaitProcessDefinitionDeployment(DeploymentEvent event) {
    // assume just 1 process deployed at a time, which is typical
    long key = event.getProcesses().get(0).getProcessDefinitionKey();
    await("await process definition key=" + key + " exists")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () -> {
              try {
                return operateClient.getProcessDefinition(key).getKey().equals(key);
              } catch (Exception e) {
                return false;
              }
            });
  }

  void deployResource(Path path) {
    DeploymentEvent deploy =
        zeebeClient
            .newDeployResourceCommand()
            .addResourceFile(path.toFile().getAbsolutePath())
            .send()
            .join();
    awaitProcessDefinitionDeployment(deploy);
  }

  @Test
  void simpleWorkflow() {
    deployResource(ZEEBE_RESOURCES.resolve("complete-simple-workflow.bpmn"));

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
    deployResource(ZEEBE_RESOURCES.resolve("parent-awaits-child.bpmn"));
    deployResource(ZEEBE_RESOURCES.resolve("child-events-parent.bpmn"));

    String id = UUID.randomUUID().toString();
    ProcessInstanceResult result =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("ParentAwaitsChild")
            .latestVersion()
            .variable("id", id)
            .withResult()
            .requestTimeout(Duration.ofSeconds(30))
            .send()
            .join();
    assertEquals(id, result.getVariable("id"));
    assertEquals("triggered child workflow", result.getVariable("parent"));
    assertEquals("trigger event", result.getVariable("child"));
  }

  @Test
  void simpleThrowWorkflow() {
    deployResource(ZEEBE_RESOURCES.resolve("will-throw.bpmn"));

    ProcessInstanceResult result =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("WillThrow")
            .latestVersion()
            .withResult()
            .send()
            .join();
    assertEquals("present", result.getVariable("test"));
  }

  @Test
  void simpleFailureWorkflow() {
    deployResource(ZEEBE_RESOURCES.resolve("will-fail.bpmn"));

    ProcessInstanceEvent result =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("WillFail")
            .latestVersion()
            .send()
            .join();

    IncidentFilter filter = new IncidentFilter();
    filter.setProcessInstanceKey(result.getProcessInstanceKey());
    SearchQuery query = new SearchQuery();
    query.setFilter(filter);

    await()
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () -> {
              List<Incident> incidents = operateClient.searchIncidents(query);
              if (incidents.size() != 1) {
                return false;
              }
              Incident incident = incidents.get(0);
              return "Failure message".equals(incident.getMessage());
            });
  }
}
