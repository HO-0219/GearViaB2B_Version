package com.teamproject.deployment;

import com.teamproject.deployment.application.PublicUrlProvider;
import com.teamproject.deployment.domain.DeploymentSettings;
import com.teamproject.deployment.domain.DeploymentSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class PublicUrlProviderTest {

    private static final URI BOOTSTRAP = URI.create("https://bootstrap.local");

    @Autowired
    private DeploymentSettingsRepository repository;

    private PublicUrlProvider provider;

    @BeforeEach
    void setUp() {
        provider = new PublicUrlProvider(repository, BOOTSTRAP.toString());
    }

    @Test
    @DisplayName("설정이 없으면 부트스트랩 URL로 폴백한다")
    void fallsBackToBootstrapWhenUnset() {
        assertThat(provider.current()).isEqualTo(BOOTSTRAP);
        assertThat(provider.isAllowedOrigin("https://bootstrap.local")).isTrue();
        assertThat(provider.isAllowedOrigin("https://gearvia.corp")).isFalse();
    }

    @Test
    @DisplayName("DB 공개 URL이 부트스트랩 URL을 덮어쓴다")
    void databasePublicUrlOverridesBootstrapUrl() {
        repository.save(new DeploymentSettings("https://gearvia.corp"));
        assertThat(provider.current()).isEqualTo(URI.create("https://gearvia.corp"));
        assertThat(provider.isAllowedOrigin("https://gearvia.corp")).isTrue();
        assertThat(provider.isAllowedOrigin("https://evil.example")).isFalse();
    }

    @Test
    @DisplayName("캐시 없이 매 조회마다 최신 값을 읽는다")
    void readsLatestValueWithoutCaching() {
        repository.save(new DeploymentSettings("https://one.corp"));
        assertThat(provider.current()).isEqualTo(URI.create("https://one.corp"));
        repository.save(new DeploymentSettings("https://two.corp"));
        assertThat(provider.current()).isEqualTo(URI.create("https://two.corp"));
    }

    @Test
    @DisplayName("HTTPS가 아니거나 사용자정보·경로·쿼리·기본포트가 붙은 URL은 거부한다")
    void rejectsNonHttpsAndDecoratedUrls() {
        assertThatThrownBy(() -> new DeploymentSettings("http://gearvia.corp"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeploymentSettings("https://user@gearvia.corp"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeploymentSettings("https://gearvia.corp/app"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeploymentSettings("https://gearvia.corp?x=1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeploymentSettings("https://gearvia.corp:443"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeploymentSettings("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("출처 비교는 대소문자와 기본 포트를 정규화한다")
    void originComparisonFoldsDefaultPortAndCase() {
        repository.save(new DeploymentSettings("https://gearvia.corp"));
        assertThat(provider.isAllowedOrigin("https://GEARVIA.corp")).isTrue();
        assertThat(provider.isAllowedOrigin("https://gearvia.corp:443")).isTrue();
        assertThat(provider.isAllowedOrigin("https://gearvia.corp:8443")).isFalse();
        assertThat(provider.isAllowedOrigin("http://gearvia.corp")).isFalse();
        assertThat(provider.isAllowedOrigin("not a uri")).isFalse();
        assertThat(provider.isAllowedOrigin(null)).isFalse();
        assertThat(provider.isAllowedOrigin("")).isFalse();
    }
}
