package io.bootique.jdbc.test;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Binder;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import io.bootique.ConfigModule;
import io.bootique.config.ConfigurationFactory;
import io.bootique.jdbc.DataSourceFactory;
import io.bootique.jdbc.LazyDataSourceFactory;
import io.bootique.jdbc.TomcatDataSourceFactory;
import io.bootique.jdbc.instrumented.InstrumentedLazyDataSourceFactoryFactory;
import io.bootique.jdbc.test.derby.DerbyListener;
import io.bootique.jdbc.test.runtime.DataSourceListener;
import io.bootique.jdbc.test.runtime.DatabaseChannelFactory;
import io.bootique.jdbc.test.runtime.TestDataSourceFactory;
import io.bootique.log.BootLogger;
import io.bootique.shutdown.ShutdownManager;
import io.bootique.type.TypeRef;

import java.util.Map;
import java.util.Set;

/**
 * @since 0.12
 */
public class JdbcTestModule extends ConfigModule {

    public JdbcTestModule() {
        super("jdbc");
    }

    public JdbcTestModule(String configPrefix) {
        super(configPrefix);
    }

    public static Multibinder<DataSourceListener> contributeDataSourceListeners(Binder binder) {
        return Multibinder.newSetBinder(binder, DataSourceListener.class);
    }

    @Override
    public void configure(Binder binder) {

        // for now we only support Derby...
        contributeDataSourceListeners(binder).addBinding().to(DerbyListener.class);
    }

    @Singleton
    @Provides
    DataSourceFactory provideDataSourceFactory(ConfigurationFactory configFactory,
                                               BootLogger bootLogger,
                                               MetricRegistry metricRegistry,
                                               Set<DataSourceListener> dataSourceListeners,
                                               ShutdownManager shutdownManager) {

        // TODO: replace this with DI decoration of the base DataSourceFactory instead of repeating base module code

        Map<String, TomcatDataSourceFactory> configs = configFactory
                .config(new TypeRef<Map<String, TomcatDataSourceFactory>>() {
                }, configPrefix);

        LazyDataSourceFactory delegate =
                new InstrumentedLazyDataSourceFactoryFactory(configs)
                        .create(shutdownManager, bootLogger, metricRegistry);

        TestDataSourceFactory factory = new TestDataSourceFactory(delegate, dataSourceListeners, configs);
        shutdownManager.addShutdownHook(() -> {
            bootLogger.trace(() -> "shutting down TestDataSourceFactory...");
            factory.shutdown();
        });

        return factory;
    }

    @Singleton
    @Provides
    DatabaseChannelFactory provideDatabaseChannelFactory(DataSourceFactory dataSourceFactory) {
        return new DatabaseChannelFactory(dataSourceFactory);
    }

    @Singleton
    @Provides
    DerbyListener provideDerbyLifecycleListener(BootLogger bootLogger) {
        return new DerbyListener(bootLogger);
    }
}
