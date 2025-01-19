package io.nhomble.zeebemock;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "zeebemock")
public class ZeebeMockConfigurationProperties {
    private URI wiremockURI;

    public URI getWiremockURI() {
        return wiremockURI;
    }

    public void setWiremockURI(URI wiremockURI) {
        this.wiremockURI = wiremockURI;
    }
}
