package bt.module;

import bt.data.ChunkVerifier;
import bt.data.DataDescriptorFactory;
import bt.data.DefaultChunkVerifier;
import bt.data.IDataDescriptorFactory;
import bt.data.digest.Digester;
import bt.data.digest.JavaSecurityDigester;
import bt.event.EventBus;
import bt.event.EventSink;
import bt.event.EventSource;
import bt.metainfo.IMetadataService;
import bt.metainfo.MetadataService;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.net.MessageDispatcher;
import bt.net.PeerConnectionPool;
import bt.peer.IPeerRegistry;
import bt.peer.PeerRegistry;
import bt.peer.PeerSourceFactory;
import bt.processor.ProcessorFactory;
import bt.processor.TorrentProcessorFactory;
import bt.runtime.Config;
import bt.service.ApplicationService;
import bt.service.ClasspathApplicationService;
import bt.service.ExecutorServiceProvider;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.IdentityService;
import bt.service.RuntimeLifecycleBinder;
import bt.service.VersionAwareIdentityService;
import bt.torrent.AdhocTorrentRegistry;
import bt.torrent.TorrentRegistry;
import bt.torrent.data.DataWorkerFactory;
import bt.torrent.data.IDataWorkerFactory;
import bt.tracker.ITrackerService;
import bt.tracker.TrackerFactory;
import bt.tracker.TrackerService;
import bt.tracker.udp.UdpTrackerFactory;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;

import java.util.concurrent.ExecutorService;

/**
 * This module contributes all core services,
 * shared among all clients attached to a runtime.
 *
 * @since 1.0
 */
public class ServiceModule implements Module {

    /**
     * Returns the extender for contributing custom extensions to the ServiceModule.
     * Should be invoked from the dependent Module's {@link Module#configure(Binder)} method.
     *
     * @param binder DI binder passed to the Module that invokes this method.
     * @return Extender for contributing custom extensions
     * @since 1.5
     */
    public static ServiceModuleExtender extend(Binder binder) {
        return new ServiceModuleExtender(binder);
    }

    /**
     * Contribute a peer source factory.
     *
     * @since 1.0
     * @deprecated since 1.5 in favor of {@link ServiceModuleExtender#addPeerSourceFactory(Class)} and its' overloaded versions
     */
    @Deprecated
    public static Multibinder<PeerSourceFactory> contributePeerSourceFactory(Binder binder) {
        return Multibinder.newSetBinder(binder, PeerSourceFactory.class);
    }

    /**
     * Contribute a messaging agent.
     *
     * @since 1.0
     * @deprecated since 1.5 in favor of {@link ServiceModuleExtender#addMessagingAgentType(Class)}
     *             and {@link ServiceModuleExtender#addMessagingAgent(Object)}
     */
    @Deprecated
    public static Multibinder<Object> contributeMessagingAgent(Binder binder) {
        return Multibinder.newSetBinder(binder, Object.class, MessagingAgents.class);
    }

    /**
     * Contribute a tracker factory for some protocol.
     *
     * @since 1.0
     * @deprecated since 1.5 in favor of {@link ServiceModuleExtender#addTrackerFactory(Class, String, String...)}
     *             and its' overloaded versions
     */
    @Deprecated
    public static MapBinder<String, TrackerFactory> contributeTrackerFactory(Binder binder) {
        return MapBinder.newMapBinder(binder, String.class, TrackerFactory.class, TrackerFactories.class);
    }

    private Config config;

    public ServiceModule() {
        this.config = new Config();
    }

    public ServiceModule(Config config) {
        this.config = config;
    }

    @Override
    public void configure(Binder binder) {

        ServiceModule.extend(binder).initAllExtensions()
                .addTrackerFactory(UdpTrackerFactory.class, "udp");

        binder.bind(Config.class).toInstance(config);

        // core services that contribute startup lifecycle bindings and should be instantiated eagerly
        binder.bind(IMessageDispatcher.class).to(MessageDispatcher.class).asEagerSingleton();
        binder.bind(IPeerConnectionPool.class).to(PeerConnectionPool.class).asEagerSingleton();
        binder.bind(IPeerRegistry.class).to(PeerRegistry.class).asEagerSingleton();

        // other services
        binder.bind(IMetadataService.class).to(MetadataService.class).in(Singleton.class);
        binder.bind(ApplicationService.class).to(ClasspathApplicationService.class).in(Singleton.class);
        binder.bind(IdentityService.class).to(VersionAwareIdentityService.class).in(Singleton.class);
        binder.bind(ITrackerService.class).to(TrackerService.class).in(Singleton.class);
        binder.bind(IMetadataService.class).to(MetadataService.class).in(Singleton.class);
        binder.bind(TorrentRegistry.class).to(AdhocTorrentRegistry.class).in(Singleton.class);
        binder.bind(IRuntimeLifecycleBinder.class).to(RuntimeLifecycleBinder.class).in(Singleton.class);
        binder.bind(ProcessorFactory.class).to(TorrentProcessorFactory.class).in(Singleton.class);

        // single instance of event bus provides two different injectable services
        binder.bind(EventSink.class).to(EventBus.class).in(Singleton.class);
        binder.bind(EventSource.class).to(EventBus.class).in(Singleton.class);

        // TODO: register a shutdown hook in the runtime
        binder.bind(ExecutorService.class).annotatedWith(ClientExecutor.class)
                .toProvider(ExecutorServiceProvider.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    public Digester provideDigester() {
        int step = 2 << 22; // 8 MB
        return new JavaSecurityDigester("SHA-1", step);
    }

    @Provides
    @Singleton
    public ChunkVerifier provideVerifier(Config config, Digester digester) {
        return new DefaultChunkVerifier(digester, config.getNumOfHashingThreads());
    }

    @Provides
    @Singleton
    public IDataDescriptorFactory provideDataDescriptorFactory(Config config, ChunkVerifier verifier) {
        return new DataDescriptorFactory(verifier, config.getTransferBlockSize());
    }

    @Provides
    @Singleton
    public IDataWorkerFactory provideDataWorkerFactory(IRuntimeLifecycleBinder lifecycleBinder, ChunkVerifier verifier) {
        return new DataWorkerFactory(lifecycleBinder, verifier, config.getMaxIOQueueSize());
    }

    @Provides
    @Singleton
    public EventBus provideEventBus() {
        return new EventBus();
    }
}
