import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import io.camunda.zeebe.client.ZeebeClient;
import java.nio.file.Path;
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

  // create via dc because that's how I am going to debug the compose anyway instead of learning yet
  // another
  // testcontainer builder syntax
  @Container
  static ComposeContainer dc =
      new ComposeContainer(Path.of("..", "local", "docker-compose.integration-test.yaml").toFile())
          .withLogConsumer("zeebe-mock", new Slf4jLogConsumer(log))
          .withLogConsumer("zeebe", new Slf4jLogConsumer(log))
          .withLocalCompose(true);

  static ZeebeClient zeebeClient = ZeebeClient.newClientBuilder().usePlaintext().build();

  @Test
  void checkTopology() {
    await()
        .until(
            () -> {
              try {
                return zeebeClient.newTopologyRequest().send().join().getClusterSize() == 1;
              } catch (Exception e) {
                return false;
              }
            });
  }
}
