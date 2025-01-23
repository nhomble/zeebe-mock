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
    log.info("Closing existing active workers number={}", activeWorkers.size());
    activeWorkers.forEach(JobWorker::close);
    for (WorkerDefinition worker : workerResolver.resolve()) {
      log.info(
          "Registering worker jobType={} tenantIds={}", worker.getJobType(), worker.getTenantIds());
      var building =
          zeebeClient
              .newWorker()
              .jobType(worker.getJobType())
              .handler(new MockJobHandler(zeebeMockConfigurationProperties.getWiremockURI()));
      if (!worker.getTenantIds().isEmpty()) {
        building = building.tenantIds(worker.getTenantIds());
      }
      var jobWorker = building.open();
      activeWorkers.add(jobWorker);
    }
  }
}
