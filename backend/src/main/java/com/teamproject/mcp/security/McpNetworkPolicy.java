package com.teamproject.mcp.security;

import com.teamproject.common.exception.ApplicationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

@Component
public class McpNetworkPolicy {
    private final boolean enabled;
    private final List<Cidr> allowedNetworks;
    private final List<Cidr> trustedProxies;
    private final List<String> allowedOrigins;

    public McpNetworkPolicy(@Value("${app.mcp.enabled:false}") boolean enabled,
            @Value("${app.mcp.allowed-cidrs:127.0.0.1/32,::1/128}") String cidrs,
            @Value("${app.mcp.trusted-proxies:127.0.0.1/32,::1/128}") String proxies,
            @Value("${app.mcp.allowed-origins:}") String origins) {
        this.enabled = enabled;
        this.allowedNetworks = values(cidrs).stream().map(Cidr::parse).toList();
        this.trustedProxies = values(proxies).stream().map(Cidr::parse).toList();
        this.allowedOrigins = values(origins);
    }

    public String requireAllowed(String peerIp, String forwardedFor, String origin) {
        if (!enabled) throw failure("MCP_DISABLED", HttpStatus.SERVICE_UNAVAILABLE, "MCP gateway is disabled.");
        String sourceIp = effectiveSource(peerIp, forwardedFor);
        if (allowedNetworks.stream().noneMatch(cidr -> cidr.contains(sourceIp))) {
            throw failure("MCP_NETWORK_DENIED", HttpStatus.FORBIDDEN, "MCP source network is not allowed.");
        }
        if (origin != null && !origin.isBlank() && !allowedOrigins.contains(stripSlash(origin))) {
            throw failure("MCP_ORIGIN_DENIED", HttpStatus.FORBIDDEN, "MCP request origin is not allowed.");
        }
        return sourceIp;
    }

    private String effectiveSource(String peerIp, String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()
                || trustedProxies.stream().noneMatch(cidr -> cidr.contains(peerIp))) return peerIp;
        String first = forwardedFor.split(",", 2)[0].trim();
        return first.length() <= 64 ? first : "invalid";
    }

    private List<String> values(String raw) {
        return Arrays.stream(raw.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .map(this::stripSlash).toList();
    }
    private String stripSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
    private ApplicationException failure(String code, HttpStatus status, String message) {
        return new ApplicationException(code, status, message);
    }

    private record Cidr(byte[] network, int prefix) {
        static Cidr parse(String value) {
            try {
                String[] parts = value.split("/", -1);
                byte[] address = InetAddress.getByName(parts[0]).getAddress();
                int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : address.length * 8;
                if (prefix < 0 || prefix > address.length * 8) throw new IllegalArgumentException("Invalid MCP CIDR");
                return new Cidr(address, prefix);
            } catch (UnknownHostException | NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid MCP CIDR", exception);
            }
        }

        boolean contains(String candidate) {
            try {
                byte[] address = InetAddress.getByName(candidate).getAddress();
                if (address.length != network.length) return false;
                int fullBytes = prefix / 8;
                for (int index = 0; index < fullBytes; index++) if (address[index] != network[index]) return false;
                int remaining = prefix % 8;
                if (remaining == 0) return true;
                int mask = 0xff << (8 - remaining);
                return (address[fullBytes] & mask) == (network[fullBytes] & mask);
            } catch (UnknownHostException exception) { return false; }
        }
    }
}
