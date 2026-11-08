package dev.sentinel.checking;

import org.apache.hc.client5.http.DnsResolver;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.*;
import java.util.*;

@Component
public class DestinationPolicy implements DnsResolver {
    @ConfigurationProperties("sentinel.security")
    public record Settings(Set<String> approvedLocalHosts) {
        public Settings { approvedLocalHosts = approvedLocalHosts == null ? Set.of() : Set.copyOf(approvedLocalHosts); }
    }
    private final Set<String> approvedHosts;
    private final Resolver resolver;

    @org.springframework.beans.factory.annotation.Autowired
    public DestinationPolicy(Settings settings, Environment environment) {
        this(settings.approvedLocalHosts(), InetAddress::getAllByName);
        if (!approvedHosts.isEmpty() && !environment.matchesProfiles("dev", "test")) {
            throw new IllegalStateException("Local destination exceptions require the dev or test profile");
        }
    }
    // Injection seam for deterministic DNS tests; production always uses the system resolver.
    DestinationPolicy(Set<String> approvedHosts, Resolver resolver) {
        this.approvedHosts = approvedHosts.stream().map(h -> h.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.resolver = resolver;
    }
    @FunctionalInterface interface Resolver { InetAddress[] resolve(String host) throws UnknownHostException; }

    public URI parse(String value) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || host == null || host.isBlank() || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535 || value.length() > 2048) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new InvalidDestination("Use an absolute HTTP/HTTPS URL without credentials or fragments");
        }
    }
    public URI validate(String value) throws UnknownHostException {
        URI uri = parse(value);
        resolve(uri.getHost());
        return uri;
    }
    @Override public InetAddress[] resolve(String hostname) throws UnknownHostException {
        String host = hostname.toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        boolean approved = approvedHosts.contains(host);
        if (!approved && (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local"))) {
            throw new BlockedDestination();
        }
        InetAddress[] addresses = resolver.resolve(host);
        if (addresses.length == 0) throw new UnknownHostException("No destination addresses");
        // Reject mixed public/private answers rather than picking a supposedly safe subset.
        for (InetAddress address : addresses) {
            if (!approved && !isPublic(address)) throw new BlockedDestination();
        }
        return addresses;
    }
    @Override public String resolveCanonicalHostname(String host) throws UnknownHostException {
        resolve(host);
        return host;
    }
    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        int a = Byte.toUnsignedInt(b[0]), c = Byte.toUnsignedInt(b[1]);
        if (b.length == 4) {
            return !(a == 0 || a == 10 || a == 127 || a >= 224
                || (a == 100 && c >= 64 && c <= 127)
                || (a == 169 && c == 254) || (a == 172 && c >= 16 && c <= 31)
                || (a == 192 && (c == 0 || c == 168 || (c == 88 && Byte.toUnsignedInt(b[2]) == 99)))
                || (a == 198 && (c == 18 || c == 19 || (c == 51 && Byte.toUnsignedInt(b[2]) == 100)))
                || (a == 203 && c == 0 && Byte.toUnsignedInt(b[2]) == 113));
        }
        // Conservative IPv6 allowlist: global unicast only, excluding special-use,
        // documentation, 6to4 (embedded IPv4), Teredo and other transition ranges.
        if ((a & 0xe0) != 0x20) return false;
        int prefix = (a << 8) | c;
        int next = (Byte.toUnsignedInt(b[2]) << 8) | Byte.toUnsignedInt(b[3]);
        return !(prefix == 0x2002 || (prefix == 0x2001 && (next <= 0x1ff || next == 0xdb8))
                || (prefix == 0x3fff && next <= 0x0fff));
    }
    public static class InvalidDestination extends IllegalArgumentException {
        public InvalidDestination(String message) { super(message); }
    }
    public static class BlockedDestination extends UnknownHostException {
        public BlockedDestination() { super("Destination is not publicly routable"); }
    }
}
