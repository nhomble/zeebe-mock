package io.github.nhomble.zeebemock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ZeebeMockConfigTest {

  @Test
  void explicitPortIsKept() {
    assertEquals(8080, ZeebeMockConfig.effectivePort(URI.create("http://wiremock:8080")));
  }

  @Test
  void missingPortFallsBackToSchemeDefault() {
    assertEquals(80, ZeebeMockConfig.effectivePort(URI.create("http://wiremock")));
    assertEquals(443, ZeebeMockConfig.effectivePort(URI.create("https://wiremock/prefix")));
    assertEquals(443, ZeebeMockConfig.effectivePort(URI.create("HTTPS://wiremock")));
  }

  @Test
  void missingPortWithUnknownSchemeFails() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ZeebeMockConfig.effectivePort(URI.create("ftp://wiremock")));
  }

  @Test
  void pathPrefixStripsTrailingSlash() {
    assertEquals("", ZeebeMockConfig.pathPrefix(URI.create("http://wiremock:8080")));
    assertEquals("", ZeebeMockConfig.pathPrefix(URI.create("http://wiremock:8080/")));
    assertEquals("/wiremock", ZeebeMockConfig.pathPrefix(URI.create("http://h:8080/wiremock")));
    assertEquals("/wiremock", ZeebeMockConfig.pathPrefix(URI.create("http://h:8080/wiremock/")));
  }
}
