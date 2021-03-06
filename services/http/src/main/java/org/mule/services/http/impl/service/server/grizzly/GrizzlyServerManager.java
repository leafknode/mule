/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.http.impl.service.server.grizzly;

import static java.lang.Integer.valueOf;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.glassfish.grizzly.http.HttpCodecFilter.DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.api.config.MuleProperties.SYSTEM_PROPERTY_PREFIX;
import static org.mule.runtime.core.util.ClassUtils.withContextClassLoader;
import static org.mule.services.http.impl.service.client.HttpMessageLogger.LoggerType.LISTENER;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.service.http.api.server.HttpServer;
import org.mule.service.http.api.server.ServerAddress;
import org.mule.service.http.api.tcp.TcpServerSocketProperties;
import org.mule.services.http.impl.service.client.HttpMessageLogger;
import org.mule.services.http.impl.service.server.HttpListenerRegistry;
import org.mule.services.http.impl.service.server.HttpServerManager;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.http.KeepAlive;
import org.glassfish.grizzly.nio.RoundRobinConnectionDistributor;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrizzlyServerManager implements HttpServerManager {

  // Defines the maximum size in bytes accepted for the http request header section (request line + headers)
  public static final String MAXIMUM_HEADER_SECTION_SIZE_PROPERTY_KEY = SYSTEM_PROPERTY_PREFIX + "http.headerSectionSize";
  private static final int MAX_KEEP_ALIVE_REQUESTS = -1;
  private final GrizzlyAddressDelegateFilter<SSLFilter> sslFilterDelegate;
  private final GrizzlyAddressDelegateFilter<HttpServerFilter> httpServerFilterDelegate;
  private final TCPNIOTransport transport;
  private final GrizzlyRequestDispatcherFilter requestHandlerFilter;
  private final HttpListenerRegistry httpListenerRegistry;
  private final WorkManagerSourceExecutorProvider executorProvider;
  private final ExecutorService idleTimeoutExecutorService;
  private Logger logger = LoggerFactory.getLogger(GrizzlyServerManager.class);
  private Map<ServerAddress, GrizzlyHttpServer> servers = new ConcurrentHashMap<>();
  private Map<ServerAddress, IdleExecutor> idleExecutorPerServerAddressMap = new ConcurrentHashMap<>();
  private boolean transportStarted;

  public GrizzlyServerManager(ExecutorService selectorPool, ExecutorService workerPool,
                              ExecutorService idleTimeoutExecutorService, HttpListenerRegistry httpListenerRegistry,
                              TcpServerSocketProperties serverSocketProperties)
      throws IOException {
    this.httpListenerRegistry = httpListenerRegistry;
    requestHandlerFilter = new GrizzlyRequestDispatcherFilter(httpListenerRegistry);
    sslFilterDelegate = new GrizzlyAddressDelegateFilter<>();
    httpServerFilterDelegate = new GrizzlyAddressDelegateFilter<>();

    FilterChainBuilder serverFilterChainBuilder = FilterChainBuilder.stateless();
    serverFilterChainBuilder.add(new TransportFilter());
    serverFilterChainBuilder.add(sslFilterDelegate);
    serverFilterChainBuilder.add(httpServerFilterDelegate);
    serverFilterChainBuilder.add(requestHandlerFilter);

    // Initialize Transport
    executorProvider = new WorkManagerSourceExecutorProvider();
    TCPNIOTransportBuilder transportBuilder = TCPNIOTransportBuilder.newInstance().setOptimizedForMultiplexing(true)
        .setIOStrategy(new ExecutorPerServerAddressIOStrategy(executorProvider));

    configureServerSocketProperties(transportBuilder, serverSocketProperties);

    transport = transportBuilder.build();

    transport.setNIOChannelDistributor(new RoundRobinConnectionDistributor(transport, true, true));

    transport.setWorkerThreadPool(workerPool);
    transport.setKernelThreadPool(selectorPool);

    // Set filterchain as a Transport Processor
    transport.setProcessor(serverFilterChainBuilder.build());

    this.idleTimeoutExecutorService = idleTimeoutExecutorService;
  }

  private void configureServerSocketProperties(TCPNIOTransportBuilder transportBuilder,
                                               TcpServerSocketProperties serverSocketProperties) {
    if (serverSocketProperties.getKeepAlive() != null) {
      transportBuilder.setKeepAlive(serverSocketProperties.getKeepAlive());
    }
    if (serverSocketProperties.getLinger() != null) {
      transportBuilder.setLinger(serverSocketProperties.getLinger());
    }

    if (serverSocketProperties.getReceiveBufferSize() != null) {
      transportBuilder.setReadBufferSize(serverSocketProperties.getReceiveBufferSize());
    }

    if (serverSocketProperties.getSendBufferSize() != null) {
      transportBuilder.setWriteBufferSize(serverSocketProperties.getSendBufferSize());
    }

    if (serverSocketProperties.getClientTimeout() != null) {
      transportBuilder.setClientSocketSoTimeout(serverSocketProperties.getClientTimeout());
    }

    if (serverSocketProperties.getServerTimeout() != null) {
      transportBuilder.setServerSocketSoTimeout(serverSocketProperties.getServerTimeout());
    }

    transportBuilder.setReuseAddress(serverSocketProperties.getReuseAddress());
    transportBuilder.setTcpNoDelay(serverSocketProperties.getSendTcpNoDelay());
    transportBuilder.setServerConnectionBackLog(serverSocketProperties.getReceiveBacklog());
  }

  /**
   * Starts the transport if not started. This is because it should be started lazily when the first server is registered
   * (otherwise there will be Grizzly threads even if there is no HTTP usage in any app).
   */
  private void startTransportIfNotStarted() throws IOException {
    withContextClassLoader(this.getClass().getClassLoader(), () -> {
      if (!transportStarted) {
        transportStarted = true;
        transport.start();
      }
      return null;
    }, IOException.class, e -> {
      throw new IOException(e);
    });
  }

  @Override
  public boolean containsServerFor(final ServerAddress serverAddress) {
    return servers.containsKey(serverAddress) || containsOverlappingServerFor(serverAddress);
  }

  private boolean containsOverlappingServerFor(ServerAddress newServerAddress) {
    for (ServerAddress serverAddress : servers.keySet()) {
      if (serverAddress.overlaps(newServerAddress)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public HttpServer createSslServerFor(TlsContextFactory tlsContextFactory, Supplier<Scheduler> schedulerSupplier,
                                       final ServerAddress serverAddress, boolean usePersistentConnections,
                                       int connectionIdleTimeout)
      throws IOException {
    if (logger.isDebugEnabled()) {
      logger.debug("Creating https server socket for ip {} and port {}", serverAddress.getIp(), serverAddress.getPort());
    }
    if (servers.containsKey(serverAddress)) {
      throw new IllegalStateException(String.format("Could not create a server for %s since there's already one.",
                                                    serverAddress));
    }
    startTransportIfNotStarted();
    sslFilterDelegate.addFilterForAddress(serverAddress, createSslFilter(tlsContextFactory));
    httpServerFilterDelegate.addFilterForAddress(serverAddress,
                                                 createHttpServerFilter(serverAddress, connectionIdleTimeout,
                                                                        usePersistentConnections));
    final GrizzlyHttpServer grizzlyServer = new GrizzlyHttpServerWrapper(serverAddress, transport, httpListenerRegistry,
                                                                         schedulerSupplier,
                                                                         () -> executorProvider.removeExecutor(serverAddress));
    executorProvider.addExecutor(serverAddress, grizzlyServer);
    servers.put(serverAddress, grizzlyServer);
    return grizzlyServer;
  }

  @Override
  public HttpServer createServerFor(ServerAddress serverAddress, Supplier<Scheduler> schedulerSupplier,
                                    boolean usePersistentConnections, int connectionIdleTimeout)
      throws IOException {
    if (logger.isDebugEnabled()) {
      logger.debug("Creating http server socket for ip {} and port {}", serverAddress.getIp(), serverAddress.getPort());
    }
    if (servers.containsKey(serverAddress)) {
      throw new IllegalStateException(String.format("Could not create a server for %s since there's already one.",
                                                    serverAddress));
    }
    startTransportIfNotStarted();
    httpServerFilterDelegate.addFilterForAddress(serverAddress,
                                                 createHttpServerFilter(serverAddress, connectionIdleTimeout,
                                                                        usePersistentConnections));
    final GrizzlyHttpServer grizzlyServer = new GrizzlyHttpServerWrapper(serverAddress, transport, httpListenerRegistry,
                                                                         schedulerSupplier,
                                                                         () -> executorProvider.removeExecutor(serverAddress));
    executorProvider.addExecutor(serverAddress, grizzlyServer);
    servers.put(serverAddress, grizzlyServer);
    return grizzlyServer;
  }

  @Override
  public void dispose() {
    if (transportStarted) {
      transport.shutdown();
      servers.clear();
    }
  }

  private SSLFilter createSslFilter(final TlsContextFactory tlsContextFactory) {
    try {
      boolean clientAuth = tlsContextFactory.isTrustStoreConfigured();
      final SSLEngineConfigurator serverConfig =
          new SSLEngineConfigurator(tlsContextFactory.createSslContext(), false, clientAuth, false);
      final String[] enabledProtocols = tlsContextFactory.getEnabledProtocols();
      if (enabledProtocols != null) {
        serverConfig.setEnabledProtocols(enabledProtocols);
      }
      final String[] enabledCipherSuites = tlsContextFactory.getEnabledCipherSuites();
      if (enabledCipherSuites != null) {
        serverConfig.setEnabledCipherSuites(enabledCipherSuites);
      }
      final SSLEngineConfigurator clientConfig = serverConfig.copy().setClientMode(true);
      return new MuleSslFilter(serverConfig, clientConfig);
    } catch (Exception e) {
      throw new MuleRuntimeException(e);
    }
  }

  private HttpServerFilter createHttpServerFilter(ServerAddress serverAddress, int connectionIdleTimeout,
                                                  boolean usePersistentConnections) {
    KeepAlive ka = null;
    if (usePersistentConnections) {
      ka = new KeepAlive();
      ka.setMaxRequestsCount(MAX_KEEP_ALIVE_REQUESTS);
      ka.setIdleTimeoutInSeconds((int) MILLISECONDS.toSeconds(connectionIdleTimeout));
    }
    IdleExecutor idleExecutor = new IdleExecutor(idleTimeoutExecutorService);
    idleExecutorPerServerAddressMap.put(serverAddress, idleExecutor);
    HttpServerFilter httpServerFilter =
        new HttpServerFilter(true, retrieveMaximumHeaderSectionSize(), ka, idleExecutor.getIdleTimeoutDelayedExecutor());
    httpServerFilter.getMonitoringConfig().addProbes(new HttpMessageLogger(LISTENER));
    httpServerFilter.setAllowPayloadForUndefinedHttpMethods(true);
    return httpServerFilter;
  }

  private int retrieveMaximumHeaderSectionSize() {
    try {
      return valueOf(getProperty(MAXIMUM_HEADER_SECTION_SIZE_PROPERTY_KEY, String.valueOf(DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE)));
    } catch (NumberFormatException e) {
      throw new MuleRuntimeException(createStaticMessage(format("Invalid value %s for %s configuration",
                                                                getProperty(MAXIMUM_HEADER_SECTION_SIZE_PROPERTY_KEY),
                                                                MAXIMUM_HEADER_SECTION_SIZE_PROPERTY_KEY)),
                                     e);
    }
  }

  /**
   * Wrapper that adds startup and disposal of manager specific data.
   */
  private class GrizzlyHttpServerWrapper extends GrizzlyHttpServer {

    // TODO - MULE-11117: Cleanup GrizzlyServerManager server specific data

    public GrizzlyHttpServerWrapper(ServerAddress serverAddress, TCPNIOTransport transport,
                                    HttpListenerRegistry listenerRegistry, Supplier<Scheduler> schedulerSupplier,
                                    Runnable schedulerDisposer) {
      super(serverAddress, transport, listenerRegistry, schedulerSupplier, schedulerDisposer);
    }

    @Override
    public synchronized void start() throws IOException {
      idleExecutorPerServerAddressMap.get(this.getServerAddress()).start();
      super.start();
    }

    @Override
    public synchronized void dispose() {
      super.dispose();
      ServerAddress serverAddress = this.getServerAddress();
      servers.remove(serverAddress);
      httpListenerRegistry.removeHandlersFor(this);
      httpServerFilterDelegate.removeFilterForAddress(serverAddress);
      idleExecutorPerServerAddressMap.get(serverAddress).dispose();
      idleExecutorPerServerAddressMap.remove(serverAddress);
      sslFilterDelegate.removeFilterForAddress(serverAddress);
    }
  }

}
