package org.apache.s4.example.twitter;

import java.io.File;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.s4.base.Event;
import org.apache.s4.core.adapter.AdapterApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.conf.ConfigurationBuilder;

public class TwitterInputAdapter extends AdapterApp {

    private static Logger logger = LoggerFactory.getLogger(TwitterInputAdapter.class);

    public TwitterInputAdapter() {
    }

    private LinkedBlockingQueue<Status> messageQueue = new LinkedBlockingQueue<Status>();

    protected ServerSocket serverSocket;

    private Thread t;

    @Override
    protected void onClose() {
    }

    @Override
    protected void onInit() {
        super.onInit();
        t = new Thread(new Dequeuer());
    }

    public void connectAndRead() throws Exception {

        ConfigurationBuilder cb = new ConfigurationBuilder();
        Properties twitterProperties = new Properties();
        File twitter4jPropsFile = new File(System.getProperty("user.home") + "/twitter4j.properties");
        if (!twitter4jPropsFile.exists()) {
            logger.error(
                    "Cannot find twitter4j.properties file in this location :[{}]. Make sure it is available at this place and includes user/password credentials",
                    twitter4jPropsFile.getAbsolutePath());
            return;
        }
        twitterProperties.load(new FileInputStream(twitter4jPropsFile));

        cb.setDebugEnabled(Boolean.valueOf(twitterProperties.getProperty("debug")))
                .setUser(twitterProperties.getProperty("user")).setPassword(twitterProperties.getProperty("password"));
        TwitterStream twitterStream = new TwitterStreamFactory(cb.build()).getInstance();
        StatusListener statusListener = new StatusListener() {

            @Override
            public void onException(Exception ex) {
                logger.error("error", ex);
            }

            @Override
            public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
                logger.error("error");
            }

            @Override
            public void onStatus(Status status) {
                messageQueue.add(status);

            }

            @Override
            public void onScrubGeo(long userId, long upToStatusId) {
                logger.error("error");
            }

            @Override
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
                logger.error("error");
            }
        };
        twitterStream.addListener(statusListener);
        twitterStream.sample();

    }

    @Override
    protected void onStart() {
        try {
            t.start();
            connectAndRead();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    class Dequeuer implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    Status status = messageQueue.take();
                    Event event = new Event();
                    event.put("statusText", String.class, status.getText());
                    getRemoteStream().put(event);
                } catch (Exception e) {

                }
            }

        }
    }
}