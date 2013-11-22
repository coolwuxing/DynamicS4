/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.s4.example.twitter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.s4.base.Event;
import org.apache.s4.core.adapter.AdapterApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;

import org.apache.commons.math3.distribution.ZipfDistribution;

import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.StallWarning;

public class TwitterInputAdapter extends AdapterApp {

    private static Logger logger = LoggerFactory.getLogger(TwitterInputAdapter.class);

    public TwitterInputAdapter() {
    }

    private LinkedBlockingQueue<Status> messageQueue = new LinkedBlockingQueue<Status>();

    protected ServerSocket serverSocket;

    private Thread t;

    ZipfDistribution zd = new ZipfDistribution(5000, 0.5);

    @Override
    protected void onClose() {
    }

    @Override
    protected void onInit() {
        super.onInit();
        try {
            zd.reseedRandomGenerator(42);
            prepareMetricsOutputs();
        } catch (Exception e) {
            logger.error("Cannot start metrics");
        }
        t = new Thread(new Dequeuer());
    }

    public void connectAndRead() throws Exception {

        ConfigurationBuilder cb = new ConfigurationBuilder();
        Properties twitterProperties = new Properties();
        File twitter4jPropsFile = new File(System.getProperty("user.home") + "/twitter4j.properties");
        if (!twitter4jPropsFile.exists()) {
            logger.error(
                    "Cannot find twitter4j.properties file in this location :[{}]. Make sure it is available at this place and includes oauth credentials",
                    twitter4jPropsFile.getAbsolutePath());
            return;
        }
        twitterProperties.load(new FileInputStream(twitter4jPropsFile));

        cb.setDebugEnabled(false)
                .setOAuthConsumerKey(twitterProperties.getProperty("oauth.consumerKey"))
                .setOAuthConsumerSecret(twitterProperties.getProperty("oauth.consumerSecret"))
                .setOAuthAccessToken(twitterProperties.getProperty("oauth.accessToken"))
                .setOAuthAccessTokenSecret(twitterProperties.getProperty("oauth.accessTokenSecret"));
        TwitterStream twitterStream = new TwitterStreamFactory(cb.build()).getInstance();
        StatusListener statusListener = new StatusListener() {

            @Override
            public void onException(Exception ex) {
//                logger.error("error", ex);
            }

            @Override
            public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
//                logger.error("error");
            }

            @Override
            public void onStatus(Status status) {
                messageQueue.add(status);

            }

            @Override
            public void onScrubGeo(long userId, long upToStatusId) {
//                logger.error("error");
            }
            
            @Override
            public void onStallWarning(StallWarning arg0) {
//                logger.error("error");
            }
            
            @Override
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
//                logger.error("error");
            }
        };
        twitterStream.addListener(statusListener);
        twitterStream.sample();

    }

    @Override
    protected void onStart() {
        try {
            t.start();
            //connectAndRead();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void prepareMetricsOutputs() throws IOException {
        final Graphite graphite = new Graphite(new InetSocketAddress("10.1.1.3", 2003));
        final GraphiteReporter reporter = GraphiteReporter.forRegistry(this.getMetricRegistry()).prefixedWith("S4-" + getClusterName() + "-" + getPartitionId())
                .convertRatesTo(TimeUnit.SECONDS).convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL).build(graphite);
        reporter.start(1, TimeUnit.MINUTES);
    }

    class Dequeuer implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    //logger.debug("try sending a event.");
                    //Status status = messageQueue.take();
                    
                    Event event = new Event();
                    event.put("statusText", String.class, "This is test #topic" + zd.sample() + " generated by apache zipf distribution.");//"#woailuo haha");
                    for (int i = 1; i < 2000; i++) {
                        getRemoteStream().put(event);
                    }
                    Thread.sleep(10);
                } catch (Exception e) {

                }
            }

        }
    }
}
