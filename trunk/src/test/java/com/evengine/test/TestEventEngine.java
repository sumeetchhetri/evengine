package com.evengine.test;

import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;
import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;

import com.evengine.core.EventHandlerEngine;
import com.evengine.core.EventListenerSignature;
import com.evengine.store.EventPersistentMongoDBImpl;
import com.evengine.test.events.DummyDistributedEvent1;
import com.evengine.test.events.DummyDistributedEvent2;
import com.evengine.test.events.DummyEvent1;
import com.evengine.test.listener.DummyEventListener;
import com.mongodb.Mongo;

/*
    Copyright 2013-2014, Sumeet Chhetri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
public class TestEventEngine
{
    private static Logger logger = Logger.getLogger(TestEventEngine.class.getName());

    private EventPersistentMongoDBImpl epersimpl;

    @Before
    public void setup()
    {
        try
        {
            Mongo mongo = new Mongo("localhost", 27017);
            MongoDbFactory mongoDbFactory = new SimpleMongoDbFactory(mongo, "mongo");
            epersimpl = new EventPersistentMongoDBImpl(mongoDbFactory);

            mongo.getDB("mongo").getCollection("eventListenerSignature").drop();
            mongo.getDB("mongo").getCollection("lockStatus").drop();
        }
        catch (UnknownHostException e)
        {
        }
    }

    private EventHandlerEngine getEngine(boolean isPersistent)
    {
        EventHandlerEngine engine = new EventHandlerEngine();

        //Is the Event engine persistent
        engine.setPersistent(isPersistent);

        //Set the comma separated list of packages, classes to look for Event Listeners
        engine.setPackagePaths("com.evengine.test.listener.*");

        if(isPersistent) {
            //If the Event engine is persistent, set the persistent store, here the store is mongodb
            engine.setePersistenceInterface(epersimpl);
        }

        //Initialize the Event Engine, this will setup thread pools and also setup listeners
        engine.initialize();

        return engine;
    }

    private EventHandlerEngine getEngineNoInit(boolean isPersistent)
    {
        EventHandlerEngine engine = new EventHandlerEngine();

        //Is the Event engine persistent
        engine.setPersistent(isPersistent);

        //Set the comma separated list of packages, classes to look for Event Listeners
        engine.setPackagePaths("com.evengine.test.listener.*");

        if(isPersistent) {
            //If the Event engine is persistent, set the persistent store, here the store is mongodb
            engine.setePersistenceInterface(epersimpl);
        }

        return engine;
    }

    @Test
    public void testWithoutPersistence() throws Exception
    {
        EventHandlerEngine engine = getEngine(false);

        //Create a new Event
        DummyEvent1 dummyEvent1 = new DummyEvent1("event", 1);

        //Push this event off to the event engine
        engine.push(dummyEvent1);

        Thread.sleep(5000);

        //Clean up and destroy the event engine
        engine.destroy();
    }

    @Test
    public void testWithoutPersistenceGetResults() throws Exception
    {
        EventHandlerEngine engine = getEngine(false);

        //Create a new Event
        DummyEvent1 dummyEvent1 = new DummyEvent1("event", 1);

        //Push this event off to the event engine and wait for results from all the registered listeners
        List<Object> results = engine.pushAndGetResults(dummyEvent1);
        Assert.assertEquals(results.size(), 3);

        //Clean up and destroy the event engine
        engine.destroy();
    }

    @Test
    @Ignore
    public void testWithoutPersistencePerf() throws Exception
    {
        EventHandlerEngine engine = getEngine(false);

        //Create a new Event and Push this event off to the event engine
        long stmilis = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++)
        {
            DummyEvent1 dummyEvent1 = new DummyEvent1("event", i+1);
            engine.push(dummyEvent1);
        }
        logger.info("Pusing 10000 events to engine completed in " + (System.currentTimeMillis() - stmilis) + " ms");

        Thread.sleep(10000);

        //Clean up and destroy the event engine
        engine.destroy();
    }

    @Test
    public void testWithPersistence() throws Exception
    {
        EventHandlerEngine engine = getEngine(true);

        //Create a new Event
        DummyEvent1 dummyEvent1 = new DummyEvent1("event", 1);

        //Push this event off to the event engine
        engine.push(dummyEvent1);

        Thread.sleep(5000);

        //Clean up and destroy the event engine
        engine.destroy();

        long count = epersimpl.getEventsCount(dummyEvent1.getClass(), new Date(), false, null, 0);
        Assert.assertEquals(count, 0);
    }

    @Test
    public void testWithPersistenceGetResults() throws Exception
    {
        EventHandlerEngine engine = getEngine(true);

        //Create a new Event
        DummyEvent1 dummyEvent1 = new DummyEvent1("event", 1);

        //Push this event off to the event engine and wait for results from all the registered listeners
        List<Object> results = engine.pushAndGetResults(dummyEvent1);
        Assert.assertEquals(results.size(), 3);

        //Clean up and destroy the event engine
        engine.destroy();

        Thread.sleep(5000);

        long count = epersimpl.getEventsCount(dummyEvent1.getClass(), new Date(), false, null, 0);
        Assert.assertEquals(count, 0);
    }

    @Test
    public void testWithPersistenceProcessExisting() throws Exception
    {
        //Create a new Event
        DummyEvent1 dummyEvent1 = new DummyEvent1("event", 1);

        String id = dummyEvent1.getClass().getSimpleName() + "_" + System.nanoTime();

        EventListenerSignature signature = new EventListenerSignature(id, dummyEvent1,
                DummyEventListener.class.getSimpleName(), "processEvent", EventHandlerEngine.STATUS_PENDING,
                false, null, false);

        epersimpl.storeEvent(signature);

        long count = epersimpl.getEventsCount(dummyEvent1.getClass(), new Date(), false, null, 0);
        Assert.assertEquals(count, 1);

        EventHandlerEngine engine = getEngine(true);

        Thread.sleep(5000);

        //Clean up and destroy the event engine
        engine.destroy();

        count = epersimpl.getEventsCount(dummyEvent1.getClass(), new Date(), false, null, 0);
        Assert.assertEquals(count, 0);
    }


    @Test
    public void testWithPersistenceProcessExistingDistributedEvent() throws Exception
    {
        //Create a new Event
        DummyDistributedEvent1 dummyDistributedEvent1 = new DummyDistributedEvent1("distevent", 1);

        String id = dummyDistributedEvent1.getClass().getSimpleName() + "_" + System.nanoTime();

        EventListenerSignature signature = new EventListenerSignature(id, dummyDistributedEvent1,
                DummyEventListener.class.getSimpleName(), "processDistributedEvent1", EventHandlerEngine.STATUS_PARTIAL,
                true, null, true);

        epersimpl.storeEvent(signature);

        Date stDate = new Date();

        EventHandlerEngine engine = getEngineNoInit(true);

        long count = epersimpl.getEventsCount(dummyDistributedEvent1.getClass(), stDate, true, engine.getInstanceId(), 60);
        Assert.assertEquals(count, 1);

        engine.initialize();

        Thread.sleep(5000);

        //Clean up and destroy the event engine
        engine.destroy();

        stDate = new Date();

        count = epersimpl.getEventsCount(dummyDistributedEvent1.getClass(), stDate, true, engine.getInstanceId(), 60);
        Assert.assertEquals(count, 0);

        engine = getEngineNoInit(true);

        count = epersimpl.getEventsCount(dummyDistributedEvent1.getClass(), stDate, true, engine.getInstanceId(), 60);
        Assert.assertEquals(count, 1);

        engine.initialize();

        Thread.sleep(65000);

        //Clean up and destroy the event engine
        engine.destroy();

        count = epersimpl.getEventsCount(dummyDistributedEvent1.getClass(), stDate, true, engine.getInstanceId(), 60);
        Assert.assertEquals(count, 0);

        engine = getEngineNoInit(true);

        count = epersimpl.getEventsCount(dummyDistributedEvent1.getClass(), stDate, true, engine.getInstanceId(), 60);
        Assert.assertEquals(count, 0);
    }


    @Test
    public void testWithPersistenceProcessExistingDistributedEventProcessOnce() throws Exception
    {
        //Create a new Event
        DummyDistributedEvent2 dummyDistributedEvent2 = new DummyDistributedEvent2("distevent2", 2);

        String id = dummyDistributedEvent2.getClass().getSimpleName() + "_" + System.nanoTime();

        EventListenerSignature signature = new EventListenerSignature(id, dummyDistributedEvent2,
                DummyEventListener.class.getSimpleName(), "processDistributedEvent2", EventHandlerEngine.STATUS_PARTIAL,
                true, null, true);

        epersimpl.storeEvent(signature);

        Date stDate = new Date();

        EventHandlerEngine engine = getEngineNoInit(true);

        long count = epersimpl.getEventsCount(dummyDistributedEvent2.getClass(), stDate, true, engine.getInstanceId(), 60);
        Assert.assertEquals(count, 1);

        engine.initialize();

        Thread.sleep(5000);

        //Clean up and destroy the event engine
        engine.destroy();

        stDate = new Date();

        count = epersimpl.getEventsCount(dummyDistributedEvent2.getClass(), stDate, true, engine.getInstanceId(), 60);
        Assert.assertEquals(count, 0);

        engine = getEngineNoInit(true);

        count = epersimpl.getEventsCount(dummyDistributedEvent2.getClass(), stDate, true, engine.getInstanceId(), 60);
        Assert.assertEquals(count, 0);
    }

    @Test
    @Ignore
    public void testWithPersistencePerf() throws Exception
    {
        EventHandlerEngine engine = getEngine(true);

        //Create a new Event and Push this event off to the event engine
        long stmilis = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++)
        {
            DummyEvent1 dummyEvent1 = new DummyEvent1("event", i+1);
            engine.push(dummyEvent1);
        }
        logger.info("Pusing 10000 events to persistent engine completed in " + (System.currentTimeMillis() - stmilis) + " ms");

        //Let the Event Engine process the events
        Thread.sleep(10000);

        //Clean up and destroy the event engine
        engine.destroy();
    }
}
