package com.legent.platform.config;

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookWebClientConfigTest {

    @Test
    void webhookWebClientRejectsProductionDirectDispatchWithoutProxy() {
        WebhookOutboundProperties properties = new WebhookOutboundProperties();
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new WebClientConfig().webhookWebClient(
                WebClient.builder(),
                properties,
                environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("direct dispatch is disabled in production");
    }

    @Test
    void webhookWebClientAllowsProductionWhenOutboundProxyConfigured() {
        WebhookOutboundProperties properties = new WebhookOutboundProperties();
        properties.setProxyUrl("http://webhook-egress-proxy.internal:8080");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        WebhookWebClient webhookWebClient = new WebClientConfig().webhookWebClient(
                WebClient.builder(),
                properties,
                environment);

        assertThat(webhookWebClient).isNotNull();
    }

    @Test
    void validatingResolverRejectsPrivateAddressReturnedAtRequestTime() throws Exception {
        PublicAddressValidatingAddressResolverGroup group = new PublicAddressValidatingAddressResolverGroup(
                new FixedAddressResolverGroup(List.of(InetAddress.getByName("10.0.0.5"))),
                "webhook endpoint");

        try (AddressResolver<InetSocketAddress> resolver = group.getResolver(ImmediateEventExecutor.INSTANCE)) {
            Future<InetSocketAddress> future = resolver.resolve(
                    InetSocketAddress.createUnresolved("attacker.example", 443));

            assertThat(future.isSuccess()).isFalse();
            assertThat(future.cause())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("private or reserved");
        } finally {
            group.close();
        }
    }

    @Test
    void validatingResolverAllowsPublicAddressReturnedAtRequestTime() throws Exception {
        PublicAddressValidatingAddressResolverGroup group = new PublicAddressValidatingAddressResolverGroup(
                new FixedAddressResolverGroup(List.of(InetAddress.getByName("93.184.216.34"))),
                "webhook endpoint");

        try (AddressResolver<InetSocketAddress> resolver = group.getResolver(ImmediateEventExecutor.INSTANCE)) {
            Future<InetSocketAddress> future = resolver.resolve(
                    InetSocketAddress.createUnresolved("customer.example", 443));

            assertThat(future.isSuccess()).isTrue();
            assertThat(future.getNow().getAddress().getHostAddress()).isEqualTo("93.184.216.34");
        } finally {
            group.close();
        }
    }

    private static final class FixedAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {
        private final List<InetAddress> addresses;

        private FixedAddressResolverGroup(List<InetAddress> addresses) {
            this.addresses = addresses;
        }

        @Override
        protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
            return new FixedAddressResolver(executor, addresses);
        }
    }

    private static final class FixedAddressResolver implements AddressResolver<InetSocketAddress> {
        private final EventExecutor executor;
        private final List<InetAddress> addresses;

        private FixedAddressResolver(EventExecutor executor, List<InetAddress> addresses) {
            this.executor = executor;
            this.addresses = addresses;
        }

        @Override
        public boolean isSupported(SocketAddress address) {
            return address instanceof InetSocketAddress;
        }

        @Override
        public boolean isResolved(SocketAddress address) {
            return address instanceof InetSocketAddress inetSocketAddress && !inetSocketAddress.isUnresolved();
        }

        @Override
        public Future<InetSocketAddress> resolve(SocketAddress address) {
            return resolve(address, executor.newPromise());
        }

        @Override
        public Future<InetSocketAddress> resolve(SocketAddress address, Promise<InetSocketAddress> promise) {
            if (!(address instanceof InetSocketAddress inetSocketAddress) || addresses.isEmpty()) {
                return promise.setFailure(new IllegalArgumentException("unsupported address"));
            }
            return promise.setSuccess(new InetSocketAddress(addresses.get(0), inetSocketAddress.getPort()));
        }

        @Override
        public Future<List<InetSocketAddress>> resolveAll(SocketAddress address) {
            return resolveAll(address, executor.newPromise());
        }

        @Override
        public Future<List<InetSocketAddress>> resolveAll(
                SocketAddress address,
                Promise<List<InetSocketAddress>> promise) {
            if (!(address instanceof InetSocketAddress inetSocketAddress) || addresses.isEmpty()) {
                return promise.setFailure(new IllegalArgumentException("unsupported address"));
            }
            return promise.setSuccess(addresses.stream()
                    .map(resolved -> new InetSocketAddress(resolved, inetSocketAddress.getPort()))
                    .toList());
        }

        @Override
        public void close() {
        }
    }
}
