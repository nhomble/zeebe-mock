package io.github.nhomble.zeebemock;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "zeebemock")
@Component("zeebeMockProperties")
public class ZeebeMockConfigurationProperties {
  private URI wiremockURI;

  // refresh worker subscriptions in ms
  private long workerRefreshInterval = 30 * 1_000;

  public URI getWiremockURI() {
    return wiremockURI;
  }

  public void setWiremockURI(URI wiremockURI) {
    this.wiremockURI = wiremockURI;
  }

  public long getWorkerRefreshInterval() {
    return workerRefreshInterval;
  }

  public void setWorkerRefreshInterval(long workerRefreshInterval) {
    this.workerRefreshInterval = workerRefreshInterval;
  }
}
