package io.github.nhomble.zeebemock;

import com.github.tomakehurst.wiremock.client.HttpAdminClient;
import java.net.URI;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ZeebeMockConfig {

  @Bean
  public WorkerResolver wiremockWorkerResolver(ZeebeMockConfigurationProperties properties) {
    URI uri = properties.getWiremockURI();
    return new WiremockWorkerResolver(
        new HttpAdminClient(uri.getScheme(), uri.getHost(), effectivePort(uri), pathPrefix(uri)));
  }

  /**
   * {@link URI#getPort()} is -1 when no port is given (e.g. {@code http://wiremock});
   * HttpAdminClient would format that literally into {@code http://wiremock:-1/__admin}, so use the
   * scheme default.
   */
  static int effectivePort(URI uri) {
    if (uri.getPort() != -1) {
      return uri.getPort();
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    switch (scheme) {
      case "http":
        return 80;
      case "https":
        return 443;
      default:
        throw new IllegalArgumentException(
            "zeebemock.wiremockURI has no port and unknown scheme: " + uri);
    }
  }

  /**
   * Path of the URI without trailing slashes ("" if none). HttpAdminClient appends "/__admin"
   * itself, so {@code http://h:8080/wiremock/} must yield "/wiremock", not "/wiremock/".
   */
  static String pathPrefix(URI uri) {
    String path = uri.getPath() == null ? "" : uri.getPath();
    while (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return path;
  }
}
