package com.evengine.core;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.log4j.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import com.evengine.store.EventPersistenceInterface;

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
/**
 * The Event Handler Engine, registers listeners using annotation markers
 * EventListener and EventListenerCallBack,<br/>
 * the desired listener needs to have<br/>
 *    1. public no-args constructor<br/>
 *    2. public method with single argument of the desired event type<br/>
 * the event types need to have<br/>
 *    1. public no-args constructor<br/>
 *    2. must implement Serializable<br/>
 *    3. equals and hashcode methods<br/>
 * @author Sumeet Chhetri<br/>
 *
 */
@SuppressWarnings("rawtypes")
public class EventHandlerEngine
{
    private static Logger logger = Logger.getLogger(EventHandlerEngine.class.getName());

    public static final String STATUS_PENDING = "PENDING";

    public static final String STATUS_SUCCESS = "SUCCESS";

    public static final String STATUS_FAILED = "FAILED";

    public static final String STATUS_EXPIRED = "EXPIRED";

    public static final String STATUS_PARTIAL = "PARTIAL";

    public static final String ID = "_id";

    public static final String EVENT = "event";

    public static final String STATUS = "status";

    public static final String DISPATCH_DATE = "dispatchDate";

    public static final String LISTENER_CLASSNAME = "listenerClassName";

    public static final String LISTENER_METHNAME = "listenerMethodName";

    public static final String IS_LOCKED = "isLocked";

    public static final String INSTANCES = "instances";

    public static final String DISTRIBUTED = "distributed";

    public static final String EVENT_CLASSNAME = "eventClassName";

    public static final String CAN_EXPIRE = "canExpire";

    public static final String UNDER_SCORE = "_";

    private String instanceId = "INSTANCE_" + UUID.randomUUID();

    @Autowired
    private ApplicationContext appContext;

    private EventPersistenceInterface ePersistenceInterface;

    public EventPersistenceInterface getePersistenceInterface()
    {
        return ePersistenceInterface;
    }

    public void setePersistenceInterface(EventPersistenceInterface ePersistenceInterface)
    {
        this.ePersistenceInterface = ePersistenceInterface;
    }

    /**
     * The EventListener Marker to register Event Listeners
     *
     * @author Sumeet Chhetri<br/>
     * threadSafe - is the event Listener threadsafe<br/>
     * poolSize - the event listener thread pool size<br/>
     *
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface EventListener
    {
        boolean threadSafe() default true;
        int poolSize() default 0;
    }

    /**
     * The EventListener Callback method
     *
     * @author Sumeet Chhetri<br/>
     * addResponseEvent - add the response of the callback back to the event engine<br/>
     * priority - define listener callback priority<br/>
     * delayNextPriorityListener - delay the next priority listener callback by ms<br/>
     *
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface EventListenerCallBack
    {
        boolean addResponseEvent() default false;
        int priority() default 0;
        long delayNextPriorityListener() default 0;
    }

    /**
     * The Event Type
     *
     * @author Sumeet Chhetri<br/>
     * idempotent - is the event idempotent (pending duplicates not allowed)<br/>
     * sequenceListenerPriority - do we want to sequence the callback execution<br/>
     * expireTime - when does the pending event expire<br/>
     * distributed - is the event distributed, participating nodes will all get this event and process it,
     *               the expiryTime is mandatory in this case<br/>
     * processOnce - if an event is distributed, process event only once (once the event is processed by a participating
     *               node, no other nodes can process it)
     *
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface EventType
    {
        boolean idempotent() default false;
        boolean sequenceListenerPriority() default false;
        int expireTime() default 0;
        boolean distributed() default false;
        boolean processOnce() default false;
    }

    /**
     * The internal representation of an Event Listener Object
     * @author Sumeet Chhetri<br/>
     *
     */
    private static class EventListenerObject
    {
        Object eventListenerInstance;
        Class eventListenerClass;
        Method eventCallBackMethod;
        boolean isThreadSafe;
        boolean addResponseEvent;
        ExecutorService eventListenerExecutors = null;
        Integer priority;
        Long delayNextPriorityListener;
        @Override
        public String toString()
        {
            return "EventListener [eventListenerClass=" + eventListenerClass + ", eventCallBackMethod="
                    + eventCallBackMethod + ", isThreadSafe=" + isThreadSafe + ", addResponseEvent=" + addResponseEvent
                    + ", priority=" + priority + ", delayNextPriorityListener=" + delayNextPriorityListener + "]";
        }

    }

    /**
     * The event properties object
     * @author Sumeet Chhetri<br/>
     *
     */
    private static class EventProperties
    {
        boolean idempotent;
        boolean sequenceListenerPriority;
        ExecutorService eventListenerExecutors = null;
        int expireTime;
        boolean isDistributed;
        boolean isProcessOnce;
        @Override
        public String toString()
        {
            return "EventProperties [idempotent=" + idempotent + ", sequenceListenerPriority="
                    + sequenceListenerPriority + ", expireTime=" + expireTime + ", isDistributed=" + isDistributed
                    + ", isProcessOnce=" + isProcessOnce + "]";
        }
    }

    private boolean initialized;

    /**
     * The comma separated list of package paths/classes that need to be looked up for possible
     * Event Listener waiting to be registered
     */
    private String packagePaths;

    /**
     * The Global Event thread pool size
     */
    private int poolSize;

    private boolean persistent;

    public boolean isPersistent()
    {
        return persistent;
    }

    public void setPersistent(boolean persistent)
    {
        this.persistent = persistent;
    }

    public int getPoolSize()
    {
        return poolSize;
    }

    public void setPoolSize(int poolSize)
    {
        this.poolSize = poolSize;
    }

    public String getPackagePaths()
    {
        return packagePaths;
    }

    public void setPackagePaths(String packagePaths)
    {
        this.packagePaths = packagePaths;
    }

    public String getInstanceId()
    {
        return instanceId;
    }

    public void setInstanceId(String instanceId)
    {
        this.instanceId = instanceId;
    }

    private boolean primary = true;

    public boolean isPrimary()
    {
        return primary;
    }

    public void setPrimary(boolean primary)
    {
        this.primary = primary;
    }

    private Map<Class, List<EventListenerObject>> eventListenerMap = new ConcurrentHashMap<Class, List<EventListenerObject>>();
    private Map<Class, EventProperties> eventPropertiesMap = new ConcurrentHashMap<Class, EventProperties>();
    protected Map<String, Integer> eventExpireClassMap = new ConcurrentHashMap<String, Integer>();

    private Map<EventListenerSignature, Boolean> eventMap;

    private ExecutorService executors = null;

    private ExecutorService internalExecutors = null;

    private EventPollExpireHandler distributedEventHandler = null;

    protected void expireEvents(Map<String, Integer> eventExpireMap)
    {
        if(isPersistent()) {
            ePersistenceInterface.expireEvents(eventExpireMap);
        }
    }

    /**
     * Find all possible classes for a given package name
     *
     * @param directory
     * @param packageName
     * @return
     * @throws ClassNotFoundException
     */
    private static List<Class> findClasses(File directory, String packageName)
            throws ClassNotFoundException {
        List<Class> classes = new ArrayList<Class>();
        if (!directory.exists()) {
            return classes;
        }
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                assert !file.getName().contains(".");
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                Class claz = Thread.currentThread().getContextClassLoader()
                        .loadClass(packageName + '.' + file.getName().substring(0, file.getName().length() - 6));
                if (claz != null) {
                    classes.add(claz);
                } else {
                    classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
                }
            }
        }
        return classes;
    }

    /**
     * Find all possible classes for a given package name
     *
     * @param packageName
     * @return
     * @throws ClassNotFoundException
     * @throws IOException
     */
    private static List<Class> getClasses(String packageName) throws ClassNotFoundException, IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<File>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }
        ArrayList<Class> classes = new ArrayList<Class>();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }
        return classes;
    }

    /**
     * Initialize the Event Engine default global thread pool if configured
     * Register all the event listeners found in the package path
     */
    public void initialize()
    {
        if(initialized)
            return;

        if(instanceId==null)
        {
            instanceId = "INSTANCE_" + UUID.randomUUID();
        }

        if(poolSize > 0)
        {
            executors = Executors.newFixedThreadPool(poolSize);
        }

        if(executors == null && poolSize <=0 )
        {
            executors = Executors.newFixedThreadPool(50);
        }

        internalExecutors = Executors.newFixedThreadPool(50);

        if(isPersistent()) {
        } else {
            eventMap = new ConcurrentHashMap<EventListenerSignature, Boolean>();
        }

        if(isPersistent() && ePersistenceInterface == null) {
            persistent = false;
            logger.error("Could not find a valid instance of EventPersistenceInterface implementation, switching to non-persistent mode");
        }

        if(packagePaths != null)
        {
            String[] packages = packagePaths.split(",");
            for (String pckgpth : packages)
            {
                try
                {
                    List<Class> classes = new ArrayList<Class>();
                    if(pckgpth.endsWith(".*"))
                    {
                        classes = getClasses(pckgpth.substring(0, pckgpth.indexOf(".*")));
                    }
                    else
                    {
                        try {
                            classes.add(Thread.currentThread().getContextClassLoader().loadClass(pckgpth));
                        } catch (ClassNotFoundException e) {
                            logger.error("The class specified was not found - " + pckgpth);
                        }
                    }
                    for (Class possEventListener : classes)
                    {
                        registerListener(possEventListener);
                    }
                } catch (ClassNotFoundException e){
                } catch (IOException e) {
                }
            }
        }

        for (Class eventClass : eventListenerMap.keySet())
        {
            registerEvent(eventClass);
        }

        handleExistingEvents(false);

        distributedEventHandler = new EventPollExpireHandler(this);
        new Thread(distributedEventHandler).start();

        initialized = true;

        logger.info("Event Engine - Initialized...");
    }


    /**
     * Handle the existing events and process them
     * @param isDistributed
     */
    @SuppressWarnings("unchecked")
    protected void handleExistingEvents(boolean isDistributed)
    {
        if(isPersistent() && eventPropertiesMap.size() > 0 ) {
            int size = 100;
            Date startDate = new Date();

            if(ePersistenceInterface.lockEventStore(instanceId))
            {
                for (Class eventClass : eventPropertiesMap.keySet())
                {
                    int expiryTime = eventPropertiesMap.get(eventClass).expireTime;
                    List<EventListenerSignature> events = null;

                    while ((events = ePersistenceInterface.getEvents(eventClass, startDate,
                            isDistributed, instanceId, expiryTime, size)) != null)
                    {
                        if(events.size()==0)
                            break;

                        logger.info("Got " + events.size() + " events of type " + eventClass.getSimpleName() + ", processing....");

                        for (EventListenerSignature signature : events)
                        {
                            if(signature.event instanceof Map)
                            {
                                signature.event = getEventObject((Map<String, Object>)signature.event, eventClass);
                                push(null, null, signature);
                                ePersistenceInterface.removeEvent(signature);
                            }
                        }
                    }
                }
                ePersistenceInterface.unLockEventStore(instanceId);
            }
        }
    }

    private Object getEventObject(Map<String, Object> propMap, Class eventClass)
    {
        Field[] fields = eventClass.getDeclaredFields();

        Object eventObj = null;
        try
        {
            eventObj = eventClass.newInstance();
            for (Field field : fields)
            {
                field.setAccessible(true);
                field.set(eventObj, propMap.get(field.getName()));
            }
        }
        catch (InstantiationException e)
        {
            logger.info("Error creating object of type " + eventClass.getSimpleName()
                    + ", reason = no nullary constructor found..");
        }
        catch (IllegalAccessException e)
        {
            logger.info("Error creating object of type " + eventClass.getSimpleName()
                    + ", reason = IllegalAccessException ");
        }

        return eventObj;
    }

    /**
     * Register Event Listener
     * @param possEventListener
     */
    @SuppressWarnings("unchecked")
    public void registerListener(Class possEventListener)
    {
        if(possEventListener != null && possEventListener.isAnnotationPresent(EventListener.class))
        {
            EventListener listannot = (EventListener)possEventListener.getAnnotation(EventListener.class);
            Method[] methods = possEventListener.getMethods();
            boolean callbackFound = false;
            for (Method possibleMethod : methods)
            {
                Method callbackMethod = getEventListenerCallback(possEventListener, possibleMethod);
                if(callbackMethod != null)
                {
                    callbackFound = true;

                    EventListenerCallBack callbackanot = callbackMethod.getAnnotation(EventListenerCallBack.class);
                    boolean addResponseEvent = callbackanot.addResponseEvent();

                    EventListenerObject eventListenerObject = new EventListenerObject();
                    eventListenerObject.eventListenerClass = possEventListener;
                    eventListenerObject.eventCallBackMethod = callbackMethod;
                    eventListenerObject.isThreadSafe = listannot.threadSafe();
                    eventListenerObject.addResponseEvent = addResponseEvent;
                    eventListenerObject.priority = callbackanot.priority();
                    eventListenerObject.delayNextPriorityListener = callbackanot.delayNextPriorityListener();
                    if(listannot.poolSize() > 0) {
                        eventListenerObject.eventListenerExecutors = Executors.newFixedThreadPool(listannot.poolSize());
                    }
                    boolean isSpringManaged = false;
                    if(appContext!=null) {
                        try {
                            eventListenerObject.eventListenerInstance = appContext.getBean(possEventListener);
                            isSpringManaged = true;
                        } catch (Exception e) {
                        }
                    }
                    if(eventListenerObject.eventListenerInstance==null && listannot.threadSafe())
                    {
                        try
                        {
                            eventListenerObject.eventListenerInstance = possEventListener.newInstance();
                        }
                        catch (InstantiationException e)
                        {
                            logger.info("Could not register " + eventListenerObject
                                    + ", reason = No nullary constructor found..");
                        }
                        catch (IllegalAccessException e)
                        {
                            logger.info("Could not register " + eventListenerObject
                                    + ", reason = IllegalAccessException ");
                        }
                    }
                    mapEventListener(callbackMethod.getParameterTypes()[0], eventListenerObject);
                    logger.info("Registered " + eventListenerObject + ", isSpringManaged = " + isSpringManaged);
                }
            }
            if(!callbackFound) {
                logger.error("No callback method found in the Listener.. skipping EventListener..." + possEventListener.getSimpleName());
            }
        }
    }

    /**
     * Register Event class
     * @param eventClass
     */
    @SuppressWarnings({ "unchecked" })
    private void registerEvent(Class eventClass)
    {
        EventProperties eventProperties = new EventProperties();
        if(eventClass.isAnnotationPresent(EventType.class))
        {
            EventType evtType = (EventType)eventClass.getAnnotation(EventType.class);
            eventProperties.idempotent = evtType.idempotent();
            eventProperties.sequenceListenerPriority = evtType.sequenceListenerPriority();
            eventProperties.isDistributed = evtType.distributed();
            eventProperties.expireTime = evtType.expireTime();
            if(evtType.distributed() && evtType.expireTime()<=0) {
                logger.info("Event Type " + eventClass.getSimpleName() + " is of distributed type but does not define expireTime...skipping");
                return;
            }
            if(evtType.distributed() && evtType.processOnce()) {
                eventProperties.isProcessOnce = evtType.processOnce();
            }
            if(eventProperties.sequenceListenerPriority) {
                eventProperties.eventListenerExecutors = Executors.newFixedThreadPool(1);
            }
        }
        logger.info("Registered " + eventProperties);
        eventPropertiesMap.put(eventClass, eventProperties);
        eventExpireClassMap.put(eventClass.getCanonicalName(), eventProperties.expireTime);
    }

    /**
     * Clean up the global thread pool and the listener level thread pools
     */
    public void destroy()
    {
        if(executors != null) {
            executors.shutdown();
        }

        if(internalExecutors != null) {
            internalExecutors.shutdown();
        }

        for (Map.Entry<Class, List<EventListenerObject>> entry : eventListenerMap.entrySet())
        {
            List<EventListenerObject> listeners = entry.getValue();
            for (EventListenerObject eventListenerObject : listeners)
            {
                if(eventListenerObject.eventListenerExecutors != null) {
                    eventListenerObject.eventListenerExecutors.shutdown();
                }
            }
        }

        for (Map.Entry<Class, EventProperties> entry : eventPropertiesMap.entrySet())
        {
            EventProperties eventProperties = entry.getValue();
            if(eventProperties.sequenceListenerPriority && eventProperties.eventListenerExecutors!=null)
            {
                eventProperties.eventListenerExecutors.shutdown();
            }
        }

        initialized = false;
        distributedEventHandler.done.set(false);

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
        }

        logger.info("Event Engine - Destroyed...");
    }

    /**
     * Get the Event listener callback method for the class provided
     *
     * @param possEventListener
     * @return
     */
    @SuppressWarnings("unchecked")
    private Method getEventListenerCallback(Class possEventListener, Method possibleMethod)
    {
        if(possibleMethod.isAnnotationPresent(EventListenerCallBack.class))
        {
            if(possibleMethod.getParameterTypes() == null || possibleMethod.getParameterTypes().length == 0)
            {
                logger.error("No callback method with a single argument found.. skipping EventListener..." + possEventListener.getSimpleName());
                return null;
            }
            else if(possibleMethod.getParameterTypes().length > 1)
            {
                logger.error("Callback method with more than one argument found.. skipping EventListener..." + possEventListener.getSimpleName());
                return null;
            }
            try {
                if(appContext!=null) {
                    try {
                        appContext.getBean(possEventListener);
                        return possibleMethod;
                    } catch (Exception e) {
                    }
                }
                possEventListener.newInstance();
                return possibleMethod;
            } catch (InstantiationException e) {
                logger.error("No no-args constructor provided.. skipping EventListener..." + possEventListener.getSimpleName());
            } catch (IllegalAccessException e) {
                logger.error("No access to the constructor available.. skipping EventListener..." + possEventListener.getSimpleName());
            }
        }
        return null;
    }

    /**
     * Add the Event Listener object for a given Event object
     * @param eventClas
     * @param eventListenerObject
     */
    private void mapEventListener(Class eventClas, EventListenerObject eventListenerObject)
    {
        if(eventListenerMap.get(eventClas) == null) {
            List<EventListenerObject> eventListenerObjects = new ArrayList<EventListenerObject>();
            eventListenerMap.put(eventClas, eventListenerObjects);
            eventListenerObjects.add(eventListenerObject);
        } else {
            eventListenerMap.get(eventClas).add(eventListenerObject);
        }
        Collections.sort(eventListenerMap.get(eventClas), new Comparator<EventListenerObject>() {
            public int compare(EventListenerObject o1, EventListenerObject o2)
            {
                return o2.priority.compareTo(o1.priority);
            }
        });
    }

    /**
     * Push the desired event to the event Handler Engine.
     * @param event
     */
    public <T extends Serializable> void push(final T event)
    {
        if(event != null) {
            Class eventClas = event.getClass();
            push(eventClas, event, null);
        }
    }

    /**
     * Push the desired event to the event Handler Engine and get back a list of final Result Objects
     * @param event
     * @return
     */
    public <T extends Serializable> List<Object> pushAndGetResults(final T event)
    {
        if(event != null) {
            Class eventClas = event.getClass();
            Future<List<Future>> futureoffs = push(eventClas, event, null);
            return getClearStatusHandler(futureoffs).getResults();
        }
        return null;
    }

    /**
     * Push the desired event to the event Handler Engine and get back a list of Futures
     * @param event
     * @return
     */
    public <T extends Serializable> List<Future> pushAndGetFutures(final T event)
    {
        if(event != null) {
            Class eventClas = event.getClass();
            Future<List<Future>> futureoffs = push(eventClas, event, null);
            return getClearStatusHandler(futureoffs).getFutures();
        }
        return null;
    }

    /**
     * @param eventClas
     * @param event
     * @param isReturns
     * @return list of Future if we need to return results
     */
    private Future<List<Future>> push(final Class evtCls, final Object evObj, final EventListenerSignature eventSig)
    {
        EventProcessor mainpushcall = new EventProcessor(this, evtCls, evObj, eventSig);
        return internalExecutors.submit(mainpushcall);
    }

    private ClearEventStatusHandler getClearStatusHandler(Future<List<Future>> futureoffs)
    {
        ClearEventStatusHandler clStatusHandler = new ClearEventStatusHandler();
        clStatusHandler.futureoffs = futureoffs;
        return clStatusHandler;
    }


    /**
     * Processes the events pushed to the event engine<br/>
     * If it was an already existing event, it looks at the signature and makes sure all other
     * listener callbacks are skipped<br/>
     * @author Sumeet Chhetri<br/>
     *
     */
    private static final class EventProcessor implements Callable<List<Future>>
    {
        private EventHandlerEngine eventEngine;

        private Class evtCls;

        private Object evObj;

        private EventListenerSignature eventSig;

        /**
         * @param eventEngine
         * @param evtCls
         * @param evObj
         * @param eventSig
         */
        private EventProcessor(EventHandlerEngine eventEngine, Class evtCls, Object evObj,
                EventListenerSignature eventSig)
        {
            super();
            this.eventEngine = eventEngine;
            this.evtCls = evtCls;
            this.evObj = evObj;
            this.eventSig = eventSig;
        }

        public List<Future> call() throws Exception
        {
            List<Future> futures = new ArrayList<Future>();
            final Class eventClas = eventSig!=null?eventSig.getEvent().getClass():evtCls;
            final Object event = eventSig!=null?eventSig.getEvent():evObj;
            if(eventEngine.eventListenerMap.get(eventClas) != null) {
                List<EventListenerObject> eventListeners = eventEngine.eventListenerMap.get(eventClas);
                for (int index = 0;index < eventListeners.size(); index++)
                {
                    final EventListenerObject eventListenerObject = eventListeners.get(index);

                    if(!shouldProcessListenerCallback(eventSig, eventListenerObject)) {
                        continue;
                    }

                    try
                    {
                        Object nfoInstance = null;
                        if(eventListenerObject.eventListenerInstance==null && !eventListenerObject.isThreadSafe)
                        {
                            nfoInstance = eventListenerObject.eventListenerClass.newInstance();
                        }
                        else
                        {
                            nfoInstance = eventListenerObject.eventListenerInstance;
                        }

                        final Object oInstance = nfoInstance;
                        final Method oCallbackMeth = eventListenerObject.eventCallBackMethod;

                        final EventListenerSignature signature = eventSig!=null?eventSig:getSignature(event, index);

                        if(eventEngine.eventPropertiesMap.get(eventClas).idempotent &&
                                findDuplicate(signature, eventEngine.eventPropertiesMap.get(eventClas).expireTime))
                        {
                            logger.info("The event of type " + eventClas.getSimpleName() + " is marked as idempotent " +
                                    "and a pending event alreay exists, hence skipping event....");
                            break;
                        }

                        if(eventSig!=null) {
                            signature.isLocked = true;
                        }
                        storeEvent(signature);

                        Callable<Object> callserve = new Callable<Object>() {
                            public Object call() throws Exception
                            {
                                Object result = null;
                                try
                                {
                                    result = oCallbackMeth.invoke(oInstance, new Object[]{event});
                                    if(!oCallbackMeth.getReturnType().equals(Void.class) && eventListenerObject.addResponseEvent)
                                    {
                                        eventEngine.push(oCallbackMeth.getReturnType(), result, null);
                                    }
                                }
                                catch (IllegalArgumentException e)
                                {
                                    signature.error = ExceptionUtils.getStackTrace(e);
                                    logger.error("Invalid argument passed to method " + eventListenerObject.eventListenerClass.getSimpleName()
                                                    + "." + oCallbackMeth.getName());
                                }
                                catch (IllegalAccessException e)
                                {
                                    signature.error = ExceptionUtils.getStackTrace(e);
                                    logger.error("IllegalAccessException " + e.getMessage());
                                }
                                catch (InvocationTargetException e)
                                {
                                    signature.error = ExceptionUtils.getStackTrace(e);
                                    logger.error("Got exception while invoking method " + e.getMessage());
                                }
                                markEventDone(signature);
                                return result;
                            }
                        };

                        if(index+1<eventListeners.size()) {
                            if(eventListenerObject.delayNextPriorityListener>0 &&
                                    eventListenerObject.priority>eventListeners.get(index+1).priority) {
                                Thread.sleep(eventListenerObject.delayNextPriorityListener);
                            }
                        }

                        if(eventEngine.eventPropertiesMap.get(eventClas)!=null
                                && eventEngine.eventPropertiesMap.get(eventClas).eventListenerExecutors!=null)
                        {
                            futures.add(eventEngine.eventPropertiesMap.get(eventClas).eventListenerExecutors.submit(callserve));
                        }
                        else if(eventListenerObject.eventListenerExecutors != null)
                        {
                            futures.add(eventListenerObject.eventListenerExecutors.submit(callserve));
                        }
                        else if(eventEngine.executors != null)
                        {
                            futures.add(eventEngine.executors.submit(callserve));
                        }
                    }
                    catch (InstantiationException e)
                    {
                        logger.error("No nullary constructor found..");
                    }
                    catch (IllegalAccessException e)
                    {
                        logger.error("IllegalAccessException " + e.getMessage());
                    }
                }
            }
            return futures;
        }

        /**
         * Check whether the event signature defines the listener correctly, if yes process only that listener callback
         * otherwise skip oher callbacks
         * @param eventSig
         * @param eventListenerObject
         * @return
         */
        private boolean shouldProcessListenerCallback(EventListenerSignature eventSig, EventListenerObject eventListenerObject)
        {
            if(eventSig!=null) {
                if(eventSig.getListenerClassName()!=null && eventSig.getListenerMethodName()!=null) {
                    if(!eventSig.getListenerClassName().equals(eventListenerObject.eventListenerClass.getSimpleName())
                            || !eventSig.getListenerMethodName().equals(eventListenerObject.eventCallBackMethod.getName()))
                    {
                        return false;
                    }
                }
            }
            return true;
        }


        /**
         * Store the event in the event store
         * @param signature
         */
        private void storeEvent(EventListenerSignature signature)
        {
            signature.dispatchDate = new Date();
            if(signature.isDistributed()) {
                signature.status = STATUS_PARTIAL;
            } else {
                signature.status = STATUS_PENDING;
            }
            signature.id = signature.event.getClass().getSimpleName() + UNDER_SCORE
                    + eventEngine.getInstanceId() + UNDER_SCORE + System.nanoTime();
            if(eventEngine.isPersistent()) {
                eventEngine.ePersistenceInterface.storeEvent(signature);
            } else {
                eventEngine.eventMap.put(signature, true);
            }
        }

        /**
         * Mark the event as processed in the store
         * @param signature
         */
        private void markEventDone(EventListenerSignature signature)
        {
            signature.status = signature.error==null?STATUS_SUCCESS:STATUS_FAILED;
            signature.processedDate = new Date();
            if(signature.instances==null) {
                signature.instances = new ArrayList<String>();
            }
            signature.instances.add(eventEngine.instanceId);
            if(signature.isDistributed() && !eventEngine.eventPropertiesMap.get(signature.getEvent().getClass()).isProcessOnce) {
                signature.status = STATUS_PARTIAL;
            }
            signature.isLocked = false;
            if(eventEngine.isPersistent()) {
                eventEngine.ePersistenceInterface.storeEvent(signature);
            } else {
                eventEngine.eventMap.remove(signature);
            }
        }

        /**
         * Check if there are any duplicates for this event signature in the store
         * @param signature
         * @param expireTime
         * @return
         */
        private boolean findDuplicate(EventListenerSignature signature, int expireTime)
        {
            if(eventEngine.isPersistent()) {
                return eventEngine.ePersistenceInterface.findDuplicateEvents(signature, expireTime);
            } else {
                return eventEngine.eventMap.containsKey(signature);
            }
        }

        /**
         * Create the signature for this event
         * @param event
         * @param index
         * @return
         */
        private EventListenerSignature getSignature(Object event, int index)
        {
            if(eventEngine.eventListenerMap.get(event.getClass()) != null) {
                List<EventListenerObject> eventListeners = eventEngine.eventListenerMap.get(event.getClass());
                if(eventListeners.size()>index) {
                    EventListenerSignature signature = new EventListenerSignature();
                    signature.event = event;
                    signature.listenerClassName = eventListeners.get(index).eventListenerClass.getSimpleName();
                    signature.listenerMethodName = eventListeners.get(index).eventCallBackMethod.getName();
                    signature.eventClassName = event.getClass().getCanonicalName();
                    signature.distributed = eventEngine.eventPropertiesMap.get(event.getClass()).isDistributed;
                    signature.isLocked = false;
                    if(eventEngine.eventPropertiesMap.get(event.getClass()).expireTime>0) {
                        signature.canExpire = true;
                    }
                    return signature;
                }
            }
            return null;
        }

    }
}