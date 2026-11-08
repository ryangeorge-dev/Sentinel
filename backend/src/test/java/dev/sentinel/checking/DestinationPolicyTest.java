package dev.sentinel.checking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import java.net.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class DestinationPolicyTest {
    private final DestinationPolicy policy = new DestinationPolicy(Set.of(), host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")});
    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "ftp://example.com", "http://user:secret@example.com", "http://example.com/#secret", "http://", "http://bad host/", "http://example.com:0", "http://example.com:65536", "/relative", "http://2130706433", "http://127.1"})
    void rejectsMalformedOrAmbiguousUrls(String url) {
        // Integer/shortened IP forms are rejected by URI host parsing or the real resolver policy.
        if (url.endsWith("2130706433") || url.endsWith("127.1")) {
            var real = new DestinationPolicy(Set.of(), InetAddress::getAllByName);
            assertThatThrownBy(() -> real.validate(url)).isInstanceOfAny(DestinationPolicy.InvalidDestination.class, DestinationPolicy.BlockedDestination.class);
        } else assertThatThrownBy(() -> policy.parse(url)).isInstanceOf(DestinationPolicy.InvalidDestination.class);
    }
    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "10.1.2.3", "127.0.0.1", "100.64.0.1", "169.254.169.254", "172.16.0.1", "192.168.2.2", "192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1", "240.0.0.1", "255.255.255.255", "::", "::1", "fc00::1", "fe80::1", "ff02::1", "2001:db8::1", "2002:7f00:1::", "64:ff9b::7f00:1", "::ffff:127.0.0.1", "2001::1", "3fff::1"})
    void rejectsNonPublicAddresses(String ip) throws Exception {
        assertThat(DestinationPolicy.isPublic(InetAddress.getByName(ip))).isFalse();
    }
    @ParameterizedTest @ValueSource(strings = {"8.8.8.8", "93.184.216.34", "2606:4700:4700::1111"})
    void allowsPublicAddresses(String ip) throws Exception {
        assertThat(DestinationPolicy.isPublic(InetAddress.getByName(ip))).isTrue();
    }
    @Test void rejectsMixedDnsAnswers() {
        var mixed = new DestinationPolicy(Set.of(), host -> new InetAddress[]{InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1")});
        assertThatThrownBy(() -> mixed.validate("https://example.com")).isInstanceOf(DestinationPolicy.BlockedDestination.class);
    }
    @Test void revalidatesReboundDns() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var rebinding = new DestinationPolicy(Set.of(), host -> new InetAddress[]{InetAddress.getByName(calls.getAndIncrement() == 0 ? "8.8.8.8" : "127.0.0.1")});
        rebinding.validate("https://example.com");
        assertThatThrownBy(() -> rebinding.resolve("example.com")).isInstanceOf(DestinationPolicy.BlockedDestination.class);
    }
    @Test void localExceptionsAreExactAndRequireExplicitProfile() throws Exception {
        var dev = new DestinationPolicy(Set.of("localhost"), InetAddress::getAllByName);
        assertThat(dev.validate("http://localhost:8123")).isNotNull();
        assertThatThrownBy(() -> dev.validate("http://127.0.0.1")).isInstanceOf(DestinationPolicy.BlockedDestination.class);
        assertThatThrownBy(() -> new DestinationPolicy(new DestinationPolicy.Settings(Set.of("localhost")), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }
}
