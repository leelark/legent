package com.legent.platform.config;

import com.legent.common.security.OutboundUrlGuard;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

final class PublicAddressValidatingAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {

    private final AddressResolverGroup<InetSocketAddress> delegate;
    private final String label;

    PublicAddressValidatingAddressResolverGroup(AddressResolverGroup<InetSocketAddress> delegate, String label) {
        this.delegate = delegate;
        this.label = label;
    }

    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
        return new ValidatingAddressResolver(executor, delegate.getResolver(executor), label);
    }

    private static final class ValidatingAddressResolver implements AddressResolver<InetSocketAddress> {
        private final EventExecutor executor;
        private final AddressResolver<InetSocketAddress> delegate;
        private final String label;

        private ValidatingAddressResolver(
                EventExecutor executor,
                AddressResolver<InetSocketAddress> delegate,
                String label) {
            this.executor = executor;
            this.delegate = delegate;
            this.label = label;
        }

        @Override
        public boolean isSupported(SocketAddress address) {
            return delegate.isSupported(address);
        }

        @Override
        public boolean isResolved(SocketAddress address) {
            return delegate.isResolved(address);
        }

        @Override
        public Future<InetSocketAddress> resolve(SocketAddress address) {
            return resolve(address, executor.newPromise());
        }

        @Override
        public Future<InetSocketAddress> resolve(SocketAddress address, Promise<InetSocketAddress> promise) {
            Future<InetSocketAddress> future = delegate.resolve(address);
            future.addListener(ignored -> completeSingle(future, promise, address));
            return promise;
        }

        @Override
        public Future<List<InetSocketAddress>> resolveAll(SocketAddress address) {
            return resolveAll(address, executor.newPromise());
        }

        @Override
        public Future<List<InetSocketAddress>> resolveAll(
                SocketAddress address,
                Promise<List<InetSocketAddress>> promise) {
            Future<List<InetSocketAddress>> future = delegate.resolveAll(address);
            future.addListener(ignored -> completeAll(future, promise, address));
            return promise;
        }

        @Override
        public void close() {
            delegate.close();
        }

        private void completeSingle(
                Future<InetSocketAddress> future,
                Promise<InetSocketAddress> promise,
                SocketAddress originalAddress) {
            if (!future.isSuccess()) {
                promise.setFailure(future.cause());
                return;
            }
            try {
                promise.setSuccess(validate(future.getNow(), originalAddress, label));
            } catch (RuntimeException ex) {
                promise.setFailure(ex);
            }
        }

        private void completeAll(
                Future<List<InetSocketAddress>> future,
                Promise<List<InetSocketAddress>> promise,
                SocketAddress originalAddress) {
            if (!future.isSuccess()) {
                promise.setFailure(future.cause());
                return;
            }
            try {
                List<InetSocketAddress> resolvedAddresses = future.getNow();
                if (resolvedAddresses == null || resolvedAddresses.isEmpty()) {
                    throw new IllegalArgumentException(labelFor(originalAddress, label) + " host could not be resolved");
                }
                for (InetSocketAddress resolvedAddress : resolvedAddresses) {
                    validate(resolvedAddress, originalAddress, label);
                }
                promise.setSuccess(resolvedAddresses);
            } catch (RuntimeException ex) {
                promise.setFailure(ex);
            }
        }

        private static InetSocketAddress validate(
                InetSocketAddress resolvedAddress,
                SocketAddress originalAddress,
                String label) {
            if (resolvedAddress == null || resolvedAddress.getAddress() == null) {
                throw new IllegalArgumentException(labelFor(originalAddress, label) + " host could not be resolved");
            }
            OutboundUrlGuard.requirePublicResolvedAddress(
                    resolvedAddress.getAddress(),
                    labelFor(originalAddress, label));
            return resolvedAddress;
        }

        private static String labelFor(SocketAddress originalAddress, String label) {
            String displayLabel = label == null || label.isBlank() ? "outbound URL" : label;
            if (originalAddress instanceof InetSocketAddress inetSocketAddress) {
                String host = inetSocketAddress.getHostString();
                if (host != null && !host.isBlank()) {
                    return displayLabel + " " + host;
                }
            }
            return displayLabel;
        }
    }
}
