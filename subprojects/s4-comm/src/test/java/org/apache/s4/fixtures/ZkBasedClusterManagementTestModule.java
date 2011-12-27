package org.apache.s4.fixtures;

import java.io.InputStream;

import org.apache.commons.configuration.ConfigurationConverter;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConfigurationUtils;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.s4.base.Emitter;
import org.apache.s4.base.Hasher;
import org.apache.s4.base.Listener;
import org.apache.s4.base.SerializerDeserializer;
import org.apache.s4.comm.DefaultHasher;
import org.apache.s4.comm.serialize.KryoSerDeser;
import org.apache.s4.comm.tcp.TCPEmitter;
import org.apache.s4.comm.tcp.TCPListener;
import org.apache.s4.comm.topology.Assignment;
import org.apache.s4.comm.topology.AssignmentFromZK;
import org.apache.s4.comm.topology.Cluster;
import org.apache.s4.comm.topology.Topology;
import org.apache.s4.comm.topology.TopologyFromZK;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.name.Names;

public class ZkBasedClusterManagementTestModule extends AbstractModule {

    protected PropertiesConfiguration config = null;

    private Class<? extends Emitter> emitterClass = null;
    private Class<? extends Listener> listenerClass = null;

    protected ZkBasedClusterManagementTestModule() {
    }

    protected ZkBasedClusterManagementTestModule(Class<? extends Emitter> emitterClass,
            Class<? extends Listener> listenerClass) {
        this.emitterClass = emitterClass;
        this.listenerClass = listenerClass;
    }

    private void loadProperties(Binder binder) {

        try {
            InputStream is = this.getClass().getResourceAsStream("/default.s4.properties");
            config = new PropertiesConfiguration();
            config.load(is);
            config.setProperty(
                    "cluster.zk_address",
                    config.getString("cluster.zk_address").replaceFirst("\\w+:\\d+",
                            "localhost:" + CommTestUtils.ZK_PORT));
            System.out.println(ConfigurationUtils.toString(config));
            // TODO - validate properties.

            /* Make all properties injectable. Do we need this? */
            Names.bindProperties(binder, ConfigurationConverter.getProperties(config));
        } catch (ConfigurationException e) {
            binder.addError(e);
            e.printStackTrace();
        }
    }

    @Override
    protected void configure() {
        if (config == null) {
            loadProperties(binder());
        }
        bind(Cluster.class);
        bind(Hasher.class).to(DefaultHasher.class);
        bind(SerializerDeserializer.class).to(KryoSerDeser.class);
        bind(Assignment.class).to(AssignmentFromZK.class);
        bind(Topology.class).to(TopologyFromZK.class);

        if (this.emitterClass != null) {
            bind(Emitter.class).to(this.emitterClass);
        } else {
            bind(Emitter.class).to(TCPEmitter.class);
        }

        if (this.listenerClass != null) {
            bind(Listener.class).to(this.listenerClass);
        } else {
            bind(Listener.class).to(TCPListener.class);
        }
    }
}
