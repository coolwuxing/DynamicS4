package org.apache.s4.core;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.apache.s4.comm.DefaultCommModule;
import org.apache.s4.core.triggers.TriggeredApp;
import org.apache.s4.fixtures.CommTestUtils;
import org.apache.s4.fixtures.ZkBasedTest;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.NIOServerCnxn.Factory;
import org.junit.After;

import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * tests from subclasses are forked in separate VMs, an easy way to avoid conflict with unavailable resources when
 * instantiating new S4 nodes
 */
// NOTE: placed in this package so that App#start(), init() and close() can be called without modifying their visibility
public abstract class TriggerTest extends ZkBasedTest {

    private Factory zookeeperServerConnectionFactory;
    public static TriggerType triggerType;
    protected TriggeredApp app;

    public enum TriggerType {
        TIME_BASED, COUNT_BASED, NONE
    }

    @After
    public void cleanup() throws IOException, InterruptedException {
        if (app != null) {
            app.close();
            app = null;
        }
        cleanupZkBasedTest();
    }

    protected CountDownLatch createTriggerAppAndSendEvent() throws IOException, KeeperException, InterruptedException {
        final ZooKeeper zk = CommTestUtils.createZkClient();
        Injector injector = Guice.createInjector(
                new DefaultCommModule(Resources.getResource("default.s4.comm.properties").openStream(), "cluster1"),
                new DefaultCoreModule(Resources.getResource("default.s4.core.properties").openStream()));
        app = injector.getInstance(TriggeredApp.class);
        app.init();
        app.start();
        // app.close();

        String time1 = String.valueOf(System.currentTimeMillis());

        CountDownLatch signalEvent1Processed = new CountDownLatch(1);
        CommTestUtils.watchAndSignalCreation("/onEvent@" + time1, signalEvent1Processed, zk);

        CountDownLatch signalEvent1Triggered = new CountDownLatch(1);
        CommTestUtils.watchAndSignalCreation("/onTrigger[StringEvent]@" + time1, signalEvent1Triggered, zk);

        CommTestUtils.injectIntoStringSocketAdapter(time1);

        // check event processed
        Assert.assertTrue(signalEvent1Processed.await(5, TimeUnit.SECONDS));

        // return latch on trigger signal
        return signalEvent1Triggered;
    }

}
