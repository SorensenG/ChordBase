package com.chordbase.infra.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationTest {

    @Test
    void allowsCurrentCloudflareQuickTunnelOrigins() {
        assertThat(SecurityConfiguration.isAllowedCorsOrigin(
                "https://orange-per-economy-unified.trycloudflare.com"
        )).isTrue();
        assertThat(SecurityConfiguration.isAllowedCorsOrigin(
                "https://motorola-couples-wait-remarkable.trycloudflare.com"
        )).isTrue();
    }

    @Test
    void keepsLocalDevelopmentOriginsAllowed() {
        assertThat(SecurityConfiguration.isAllowedCorsOrigin("http://localhost:8081")).isTrue();
        assertThat(SecurityConfiguration.isAllowedCorsOrigin("http://127.0.0.1:8081")).isTrue();
        assertThat(SecurityConfiguration.isAllowedCorsOrigin("http://192.168.15.5:8081")).isTrue();
    }

    @Test
    void rejectsUnlistedOrigins() {
        assertThat(SecurityConfiguration.isAllowedCorsOrigin("https://example.com")).isFalse();
        assertThat(SecurityConfiguration.isAllowedCorsOrigin("https://trycloudflare.com.evil.test")).isFalse();
        assertThat(SecurityConfiguration.isAllowedCorsOrigin(null)).isFalse();
    }
}
