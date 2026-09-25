package io.github.nhomble.zeebemock;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkerBootstrap {

  private static final Logger log = LoggerFactory.getLogger(WorkerBootstrap.class);

  private final WorkerResolver workerResolver;
  private final ZeebeClient zeebeClient;
  private final ZeebeMockConfigurationProperties zeebeMockConfigurationProperties;

  private final List<JobWorker> activeWorkers = new ArrayList<>();

  public WorkerBootstrap(
      WorkerResolver workerResolver,
      ZeebeClient camundaClient,
      ZeebeMockConfigurationProperties zeebeMockConfigurationProperties) {
    this.workerResolver = workerResolver;
    this.zeebeClient = camundaClient;
    this.zeebeMockConfigurationProperties = zeebeMockConfigurationProperties;
  }

  @Scheduled(fixedRateString = "#{@zeebeMockProperties.getWorkerRefreshInterval()}")
  void registerWorkers() {
    // Resolve and open the next generation first so that a failure (e.g. WireMock unreachable)
    // leaves the current generation of workers polling until the next successful refresh.
    List<JobWorker> nextWorkers = new ArrayList<>();
    try {
      for (WorkerDefinition worker : workerResolver.resolve()) {
        log.info(
            "Registering worker jobType={} tenantIds={}",
            worker.getJobType(),
            worker.getTenantIds());
        var building =
            zeebeClient
                .newWorker()
                .jobType(worker.getJobType())
                .handler(new MockJobHandler(zeebeMockConfigurationProperties.getWiremockURI()));
        if (!worker.getTenantIds().isEmpty()) {
          building = building.tenantIds(worker.getTenantIds());
        }
        nextWorkers.add(building.open());
      }
    } catch (RuntimeException e) {
      log.warn(
          "Failed to refresh workers, keeping existing active workers number={}",
          activeWorkers.size(),
          e);
      nextWorkers.forEach(JobWorker::close);
      throw e;
    }

    log.info("Closing existing active workers number={}", activeWorkers.size());
    activeWorkers.forEach(JobWorker::close);
    activeWorkers.clear();
    activeWorkers.addAll(nextWorkers);
  }
}
