## Introduction ##
<font size='3' face='Georgia, Arial'>
Java Based Persistence backed Event Bus/Engine<br>
</font>
## Features ##
<font size='3' face='Georgia, Arial'>
<ul><li>Event Engine/Bus for JVM<br>
</li><li>Supports persistence with mongodb and spring-data<br>
</li><li>Simple Annotation based configuration<br>
</li><li>Handles Distributed events<br>
</li><li>Supports Priority based Listeners<br>
</li><li>Keeps track of pending events with the help of persistence and processes them on initialization after a server crash/restart etc<br>
</font>
<h2>pom.xml configuration</h2>
Define the evengine maven repository to add the dependency jar to your project<br>
<pre><code>&lt;repositories&gt;<br>
	&lt;repository&gt;<br>
		&lt;id&gt;evengine&lt;/id&gt;<br>
		&lt;url&gt;http://evengine.googlecode.com/svn/maven2/&lt;/url&gt;<br>
	&lt;/repository&gt;<br>
&lt;/repositories&gt;<br>
<br>
&lt;dependencies&gt;<br>
	&lt;dependency&gt;<br>
		&lt;groupId&gt;com.evengine&lt;/groupId&gt;<br>
		&lt;artifactId&gt;event-engine&lt;/artifactId&gt;<br>
		&lt;version&gt;1.0&lt;/version&gt;<br>
	&lt;/dependency&gt;<br>
&lt;/dependencies&gt;<br>
</code></pre></li></ul>

## spring bean configuration ##
<u>Without persistence</u>
```
<bean id="eventHandlerEngine" class="com.evengine.core.EventHandlerEngine"
	init-method="initialize" destroy-method="destroy">
	<property name="packagePaths" value="your.listener.package.*,your.listener.class" />
	<property name="poolSize" value="50" />
	<property name="persistent" value="false" />
</bean>
```

<u>With mongodb persistence</u>
```
<mongo:mongo host="localhost" port="27017"/>

<mongo:db-factory dbname="dbname" username="user" password="pass" mongo-ref="mongo"/>

<bean id="eMongoPersistenceImpl" class="com.evengine.store.EventPersistentMongoDBImpl">
	<constructor-arg ref="mongoDbFactory" />
</bean>
<bean id="eventHandlerEngine" class="com.evengine.core.EventHandlerEngine" 
        init-method="initialize" destroy-method="destroy">
	<property name="packagePaths" value="your.listener.package.*,your.listener.class" />
	<property name="poolSize" value="50" />
	<property name="persistent" value="true" />
	<property name="ePersistenceInterface" ref="eMongoPersistenceImpl"/>
</bean>
```

## Event Example ##
<font size='3' face='Georgia, Arial'>
<ul><li>The Event Handler Engine, registers listeners using annotation markers <b>EventListener</b> and <b>EventListenerCallBack</b>
</li><li>The desired listener needs to have<br>
<ol><li>public no-args constructor<br>
</li><li>public method(s) with single argument of the desired event type<br>
</li></ol></li><li>The Event types need to have<br>
<ol><li>public no-args constructor<br>
</li><li>must implement Serializable<br>
</li><li>must implement equals and hashcode methods for idempotence<br>
</font></li></ol></li></ul>

<font size='3' face='Georgia, Arial'>
Create your desired event type/class<br>
<br>
The Event can be marked as idempotent with the help of the<br>
<ul><li><b>optional</b> @EventType - annotation to mark this class as an event type<br>
<ol><li><b>idempotent</b> - default false, but can be true if this event is idempotent (no duplicate events allowed when one is already in process/progress)<br>
</li><li><b>sequenceListenerPriority</b> - Sequence events to Listeners by priority<br>
</li><li><b>expireTime</b> - Expire this event if not processed within expiry time (seconds)<br>
</li><li><b>distributed</b> - Make this event distributed, all the event engine nodes connected to the persistence store will poll these event types<br>
</font></li></ol></li></ul>

Non Idempotent Event
```
package com.evengine.test.events;

public class DummyEvent1 implements Serializable
{
    //No args constructor - Required
    public DummyEvent1() {}
    
    /**
     * @param name
     * @param number
     */
    public DummyEvent1(String name, Integer number)
    {
        super();
        this.name = name;
        this.number = number;
    }

    private String name;
    
    private Integer number;

    @Override
    public String toString()
    {
        return "DummyEvent1 [name=" + name + ", number=" + number + "]";
    }
}

```

Idempotent Event
```
package com.evengine.test.events;

@EventType(idempotent=true)
public class DummyIdempotentEvent implements Serializable
{
    //No args constructor - Required
    public DummyIdempotentEvent() {}
    
    /**
     * @param name
     * @param number
     */
    public DummyIdempotentEvent(String name, Integer number)
    {
        super();
        this.name = name;
        this.number = number;
    }

    private String name;
    
    private Integer number;

    @Override
    public String toString()
    {
        return "DummyIdempotentEvent[name=" + name + ", number=" + number + "]";
    }
}

```

Distributed Event - With expiryTime 2 minutes
```
package com.evengine.test.events;

@EventType(distributed=true, expireTime=120)
public class DummyDistributedEvent1 implements Serializable
{
    //No args constructor - Required
    public DummyDistributedEvent1() {}

    /**
     * @param name
     * @param number
     */
    public DummyDistributedEvent1(String name, Integer number)
    {
        super();
        this.namedist = name;
        this.numberdist = number;
    }

    private String namedist;

    private Integer numberdist;

    @Override
    public String toString()
    {
        return "DummyDistributedEvent1 [namedist=" + namedist + ", numberdist=" + numberdist + "]";
    }
}

```


<font size='3' face='Georgia, Arial'>
Create your listener that will process this event<br>
<br>
There are 2 important annotations that we need to use here<br>
<br>
<ol><li><b>@EventListener</b> - Tells the event engine that this class is a listener and it has to look for callback methods<br>
<ol><li><b>threadSafe</b> - property that defines whether a single instance of this class can be used for invoking callback methods<br>
</li><li><b>poolSize</b> - defines the dedicated thread pool size for this listener<br>
</li></ol></li><li><b>@EventListenerCallBack</b> - Defines the callback method implementation or the event processing logic<br>
<ol><li><b>addResponseEvent</b> - if this callback method generates a response/return result then should the response also be added by default to the event engine?<br>
</li><li><b>priority</b> - The priority given to this callback method<br>
</li><li><b>delayNextPriorityListener</b> - the delay time in milliseconds after which the next priority listener callback method will be fired<br>
</font></li></ol></li></ol>

```
package com.evengine.test.listener;

import org.apache.log4j.Logger;
import com.evengine.core.EventHandlerEngine.EventListener;
import com.evengine.core.EventHandlerEngine.EventListenerCallBack;
import com.evengine.test.events.DummyEvent1;

@EventListener
//@EventListener(poolSize=10, threadSafe=false)
public class DummyEventListener
{

    private static Logger logger = Logger.getLogger(DummyEventListener.class.getName());
    
    @EventListenerCallBack
    public void processEvent(DummyEvent1 dummyEvent1)
    {
        logger.info("Hurray got a request for DummyEvent1 via Event Engine..." + dummyEvent1);
    }

    @EventListenerCallBack
    public void processEvent1(DummyEvent1 dummyEvent1)
    {
        logger.info("Hurray got another request for DummyEvent1 via Event Engine..." + dummyEvent1);
    }

    @EventListenerCallBack
    public void processDistributedEvent1(DummyDistributedEvent1 dummyEvent1)
    {
        logger.info("Hurray got another request for DummyDistributedEvent1 via Event Engine..." + dummyEvent1);
    }

    @EventListenerCallBack
    public void processDistributedEvent2(DummyDistributedEvent2 dummyEvent1)
    {
        logger.info("Hurray got another request for DummyDistributedEvent2 via Event Engine..." + dummyEvent1);
    }
}

package com.evengine.test.listener;

import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import com.evengine.core.EventHandlerEngine.EventListener;
import com.evengine.core.EventHandlerEngine.EventListenerCallBack;
import com.evengine.test.events.DummyEvent1;
import com.evengine.test.events.DummyEvent2;

@EventListener
public class DummyEventListener1
{

    private static Logger logger = Logger.getLogger(DummyEventListener1.class.getName());
    
    @EventListenerCallBack(addResponseEvent=true)
    public DummyEvent2 processEvent(DummyEvent1 dummyEvent1)
    {
        logger.info("Hurray got a request for DummyEvent1 via Event Engine..." + dummyEvent1);
        
        boolean flag = true;
        
        List<Integer> li = new ArrayList<Integer>();
        li.add(1);
        li.add(2);
        li.add(3);
        
        DummyEvent2 dEvent2 = new DummyEvent2(flag, li);
        return dEvent2;
    }
}

package com.evengine.test.listener;

import org.apache.log4j.Logger;
import com.evengine.core.EventHandlerEngine.EventListener;
import com.evengine.core.EventHandlerEngine.EventListenerCallBack;
import com.evengine.test.events.DummyEvent2;
import com.evengine.test.events.DummyEvent3;

@EventListener
public class DummyEventListener2
{

    private static Logger logger = Logger.getLogger(DummyEventListener2.class.getName());
    
    @EventListenerCallBack
    public void processEvent(DummyEvent2 dummyEvent2)
    {
        logger.info("Hurray got a request for DummyEvent2 via Event Engine..." + dummyEvent2);
    }
}


```

## Example Usage/Junit Tests ##

```
package com.evengine.test;

import java.util.List;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
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
```