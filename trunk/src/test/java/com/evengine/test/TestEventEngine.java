package com.evengine.test;

import java.util.List;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;

import com.evengine.core.EventHandlerEngine;
import com.evengine.store.EventPersistentMongoDBImpl;
import com.evengine.test.events.DummyEvent1;
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

    public void setup()
    {
        BasicConfigurator.configure();
    }

    @Test
    public void testWithoutPersistence() throws Exception
    {
        EventHandlerEngine engine = new EventHandlerEngine();

        //Set the comma separated list of packages, classes to look for Event Listeners
        engine.setPackagePaths("com.evengine.test.listener.*");

        //Is the Event engine persistent
        engine.setPersistent(false);

        //Initialize the Event Engine, this will setup thread pools and also setup listeners
        engine.initialize();

        //Create a new Event
        DummyEvent1 dummyEvent1 = new DummyEvent1("event", 1);

        //Push this event off to the event engine
        engine.push(dummyEvent1);

        //Let the Event Engine process the events
        Thread.sleep(2000);

        //Clean up and destroy the event engine
        engine.destroy();
    }

    @Test
    public void testWithoutPersistenceGetResults() throws Exception
    {
        EventHandlerEngine engine = new EventHandlerEngine();

        //Set the comma separated list of packages, classes to look for Event Listeners
        engine.setPackagePaths("com.evengine.test.listener.*");

        //Is the Event engine persistent
        engine.setPersistent(false);

        //Initialize the Event Engine, this will setup thread pools and also setup listeners
        engine.initialize();

        //Create a new Event
        DummyEvent1 dummyEvent1 = new DummyEvent1("event", 1);

        //Push this event off to the event engine and wait for results from all the registered listeners
        List<Object> results = engine.pushAndGetResults(dummyEvent1);
        logger.info(results.toString());

        //Let the Event Engine process the events
        Thread.sleep(2000);

        //Clean up and destroy the event engine
        engine.destroy();
    }

    @Test
    @Ignore
    public void testWithoutPersistencePerf() throws Exception
    {
        EventHandlerEngine engine = new EventHandlerEngine();

        //Set the comma separated list of packages, classes to look for Event Listeners
        engine.setPackagePaths("com.evengine.test.listener.*");

        //Is the Event engine persistent
        engine.setPersistent(false);

        //Initialize the Event Engine, this will setup thread pools and also setup listeners
        engine.initialize();

        //Create a new Event and Push this event off to the event engine
        long stmilis = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++)
        {
            DummyEvent1 dummyEvent1 = new DummyEvent1("event", i+1);
            engine.push(dummyEvent1);
        }
        logger.info("Pusing 10000 events to engine completed in " + (System.currentTimeMillis() - stmilis) + " ms");

        //Let the Event Engine process the events
        Thread.sleep(10000);

        //Clean up and destroy the event engine
        engine.destroy();
    }

    @Test
    public void testWithPersistence() throws Exception
    {
        EventHandlerEngine engine = new EventHandlerEngine();

        //Set the comma separated list of packages, classes to look for Event Listeners
        engine.setPackagePaths("com.evengine.test.listener.*");

        //Is the Event engine persistent
        engine.setPersistent(true);

        //If the Event engine is persistent, set the persistent store, here the store is mongodb
        Mongo mongo = new Mongo("localhost", 27017);
        MongoDbFactory mongoDbFactory = new SimpleMongoDbFactory(mongo, "mongo");
        EventPersistentMongoDBImpl epersimpl = new EventPersistentMongoDBImpl(mongoDbFactory);
        engine.setePersistenceInterface(epersimpl);

        //Initialize the Event Engine, this will setup thread pools and also setup listeners
        engine.initialize();

        //Create a new Event
        DummyEvent1 dummyEvent1 = new DummyEvent1("event", 1);

        //Push this event off to the event engine
        engine.push(dummyEvent1);

        //Let the Event Engine process the events
        Thread.sleep(10000);

        //Clean up and destroy the event engine
        engine.destroy();
    }

    @Test
    public void testWithPersistenceGetResults() throws Exception
    {
        EventHandlerEngine engine = new EventHandlerEngine();

        //Set the comma separated list of packages, classes to look for Event Listeners
        engine.setPackagePaths("com.evengine.test.listener.*");

        //Is the Event engine persistent
        engine.setPersistent(true);

        //If the Event engine is persistent, set the persistent store, here the store is mongodb
        Mongo mongo = new Mongo("localhost", 27017);
        MongoDbFactory mongoDbFactory = new SimpleMongoDbFactory(mongo, "mongo");
        EventPersistentMongoDBImpl epersimpl = new EventPersistentMongoDBImpl(mongoDbFactory);
        engine.setePersistenceInterface(epersimpl);

        //Initialize the Event Engine, this will setup thread pools and also setup listeners
        engine.initialize();

        //Create a new Event
        DummyEvent1 dummyEvent1 = new DummyEvent1("event", 1);

        //Push this event off to the event engine and wait for results from all the registered listeners
        List<Object> results = engine.pushAndGetResults(dummyEvent1);
        logger.info(results.toString());

        //Let the Event Engine process the events
        Thread.sleep(10000);

        //Clean up and destroy the event engine
        engine.destroy();
    }

    @Test
    @Ignore
    public void testWithPersistencePerf() throws Exception
    {
        EventHandlerEngine engine = new EventHandlerEngine();

        //Set the comma separated list of packages, classes to look for Event Listeners
        engine.setPackagePaths("com.evengine.test.listener.*");

        //Is the Event engine persistent
        engine.setPersistent(true);

        //If the Event engine is persistent, set the persistent store, here the store is mongodb
        Mongo mongo = new Mongo("localhost", 27017);
        MongoDbFactory mongoDbFactory = new SimpleMongoDbFactory(mongo, "mongo");
        EventPersistentMongoDBImpl epersimpl = new EventPersistentMongoDBImpl(mongoDbFactory);
        engine.setePersistenceInterface(epersimpl);

        //Initialize the Event Engine, this will setup thread pools and also setup listeners
        engine.initialize();

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
