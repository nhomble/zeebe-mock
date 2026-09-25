package io.github.nhomble.zeebemock.wiremock.extensions;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.stubbing.StubMappings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class ZeebeMockMappingsLoaderExtensionTest {

  private static final String NO_METADATA = readStub("no-metadata.json");
  private static final String METADATA_WITHOUT_ZEEBEMOCK =
      readStub("metadata-without-zeebemock.json");
  private static final String MISSING_JOB_TYPE = readStub("missing-job-type.json");
  private static final String VALID = readStub("valid.json");

  private final ZeebeMockMappingsLoaderExtension loader = new ZeebeMockMappingsLoaderExtension();

  @Test
  void ignoresUnrelatedStubsAndStillRewritesValidOne() {
    FakeStubMappings mappings =
        new FakeStubMappings(NO_METADATA, METADATA_WITHOUT_ZEEBEMOCK, VALID);

    assertDoesNotThrow(() -> loader.loadMappingsInto(mappings));

    assertEquals(3, mappings.getAll().size());
    assertEquals(List.of("/plain", "/other", "/payment"), urls(mappings));
    StubMapping rewritten = mappings.getAll().get(2);
    assertEquals(RequestMethod.POST, rewritten.getRequest().getMethod());
  }

  @Test
  void skipsEnabledStubMissingJobTypeAndStillRewritesValidOne() {
    FakeStubMappings mappings = new FakeStubMappings(MISSING_JOB_TYPE, VALID, NO_METADATA);

    assertDoesNotThrow(() -> loader.loadMappingsInto(mappings));

    // malformed stub left untouched (not rewritten, not removed); valid stub rewritten
    assertEquals(List.of("/broken", "/plain", "/payment"), urls(mappings));
    assertEquals(RequestMethod.GET, mappings.getAll().get(0).getRequest().getMethod());
  }

  private static List<String> urls(StubMappings mappings) {
    return mappings.getAll().stream()
        .map(s -> s.getRequest().getUrl())
        .collect(Collectors.toList());
  }

  private static String readStub(String resourceName) {
    String path = "/stubs/" + resourceName;
    try (var in = ZeebeMockMappingsLoaderExtensionTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("test resource not found: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Minimal ordered in-memory StubMappings; only the methods the loader uses are implemented. */
  private static final class FakeStubMappings implements StubMappings {
    private final List<StubMapping> stubs = new ArrayList<>();

    FakeStubMappings(String... json) {
      for (String j : json) {
        stubs.add(StubMapping.buildFrom(j));
      }
    }

    @Override
    public void addMapping(StubMapping stubMapping) {
      stubs.add(stubMapping);
    }

    @Override
    public void removeMapping(StubMapping stubMapping) {
      stubs.remove(stubMapping);
    }

    @Override
    public List<StubMapping> getAll() {
      return new ArrayList<>(stubs);
    }

    @Override
    public ServeEvent serveFor(ServeEvent serveEvent) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void editMapping(StubMapping stubMapping) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {
      stubs.clear();
    }

    @Override
    public void resetScenarios() {}

    @Override
    public Optional<StubMapping> get(UUID id) {
      return stubs.stream().filter(s -> id.equals(s.getId())).findFirst();
    }

    @Override
    public List<Scenario> getAllScenarios() {
      return List.of();
    }

    @Override
    public List<StubMapping> findByMetadata(StringValuePattern pattern) {
      throw new UnsupportedOperationException();
    }
  }
}
