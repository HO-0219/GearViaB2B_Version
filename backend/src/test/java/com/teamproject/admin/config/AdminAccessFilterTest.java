package com.teamproject.admin.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tomcat can report the IPv6 loopback as "0:0:0:0:0:0:0:1" while operators
 * configure app.admin.allowed-ips as "::1" — a raw string comparison treats
 * those as different addresses and locks out a legitimate admin.
 */
class AdminAccessFilterTest {

    @Test
    @DisplayName("허용 목록의 압축형 IPv6와 요청의 전개형 IPv6가 같은 주소면 통과한다")
    void treatsEquivalentIpv6FormsAsTheSameAddress() throws Exception {
        AdminAccessFilter filter = new AdminAccessFilter(true, "::1", "127.0.0.1,::1");

        assertThat(status(filter, "0:0:0:0:0:0:0:1")).isEqualTo(200);
    }

    @Test
    @DisplayName("허용 목록에 없는 주소는 여전히 404로 막힌다")
    void stillBlocksAddressesNotOnTheAllowList() throws Exception {
        AdminAccessFilter filter = new AdminAccessFilter(true, "127.0.0.1", "127.0.0.1,::1");

        assertThat(status(filter, "10.0.0.5")).isEqualTo(404);
    }

    @Test
    @DisplayName("app.admin.enabled=false면 허용 목록에 있어도 404로 막힌다")
    void blocksEverythingWhenDisabled() throws Exception {
        AdminAccessFilter filter = new AdminAccessFilter(false, "127.0.0.1", "127.0.0.1,::1");

        assertThat(status(filter, "127.0.0.1")).isEqualTo(404);
    }

    @Test
    @DisplayName("CIDR 범위 안의 주소는 통과한다")
    void allowsAddressesInsideACidrRange() throws Exception {
        AdminAccessFilter filter = new AdminAccessFilter(true, "192.168.56.0/24", "127.0.0.1,::1");

        assertThat(status(filter, "192.168.56.101")).isEqualTo(200);
    }

    @Test
    @DisplayName("CIDR 범위 밖의 주소는 404로 막힌다")
    void blocksAddressesOutsideACidrRange() throws Exception {
        AdminAccessFilter filter = new AdminAccessFilter(true, "192.168.56.0/24", "127.0.0.1,::1");

        assertThat(status(filter, "192.168.57.10")).isEqualTo(404);
    }

    private int status(AdminAccessFilter filter, String remoteAddress) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/overview");
        request.setRemoteAddr(remoteAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        return response.getStatus();
    }
}
