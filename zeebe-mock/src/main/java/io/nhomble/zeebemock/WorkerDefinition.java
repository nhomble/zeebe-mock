package io.nhomble.zeebemock;

import java.util.List;

public class WorkerDefinition {
  private final String jobType;
  private final List<String> tenantIds;

  public WorkerDefinition(String jobType, List<String> tenantIds) {
    this.jobType = jobType;
    this.tenantIds = tenantIds;
  }

  public String getJobType() {
    return jobType;
  }

  public List<String> getTenantIds() {
    return tenantIds;
  }
}
