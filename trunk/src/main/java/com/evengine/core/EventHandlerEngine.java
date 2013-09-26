package com.evengine.core;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.log4j.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
 * EventListener and EventListenerCallBack, 
 * the desired listener needs to have
 *    1. public no-args constructor 
 *    2. public method with single argument of the desired event type
 * the event types need to have
 *    1. public no-args constructor 
 *    2. must implement Serializable
 *    3. equals and hashcode methods
 * @author Sumeet Chhetri<br/>
 *
 */
@SuppressWarnings("rawtypes")
public class EventHandlerEngine
{
    private static Logger logger = Logger.getLogger(EventHandlerEngine.class.getName());
    
    @Autowired
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
     * threadSafe - is the event Listener threadsafe
     * poolSize - the event listener thread pool size
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
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface EventType
    {
        boolean idempotent() default false;
        boolean sequenceListenerPriority() default false;
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
    }
    
    private static class EventProperties
    {
        boolean idempotent;
        boolean sequenceListenerPriority;
        ExecutorService eventListenerExecutors = null;
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

    private Map<Class, List<EventListenerObject>> eventListenerMap = new HashMap<Class, List<EventListenerObject>>();
    private Map<Class, EventProperties> eventPropertiesMap = new HashMap<Class, EventProperties>();
    
    private Map<EventListenerSignature, Boolean> eventMap;
    
    private ExecutorService executors = null;
    
    private ExecutorService internalExecutors = null;
    
    private boolean findDuplicate(EventListenerSignature signature)
    {
        if(isPersistent()) {
            return ePersistenceInterface.findDuplicateEvent(signature);
        } else {
            return eventMap.containsKey(signature);
        }
    }
    
    private void storeEvent(EventListenerSignature signature)
    {
        signature.dispatchDate = new Date();
        signature.isDone = false;
        signature.id = signature.event.getClass().getSimpleName() + System.nanoTime();
        if(isPersistent()) {
            ePersistenceInterface.storeEvent(signature);
        } else {
            eventMap.put(signature, true);
        }
    }
    
    private void markEventDone(EventListenerSignature signature)
    {
        signature.isDone = true;
        signature.proessedDate = new Date();
        if(isPersistent()) {
            ePersistenceInterface.storeEvent(signature);
        } else {
            eventMap.remove(signature);
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
        
        if(isPersistent() && eventListenerMap.size() > 0 ) {
            int size = 100;
            Date startDate = new Date();
            for (Class eventClass : eventListenerMap.keySet())
            {
                registerEvent(eventClass);
                
                Query query = new Query(Criteria.where("isDone").is(false).
                        andOperator(Criteria.where("dispatchDate").lt(startDate)));
                List<EventListenerSignature> events = null;
                long count = ePersistenceInterface.getEventsCount(query, eventClass);
                if(count == 0)
                    continue;
                if(size > count)
                    size = (int)count;
                query.limit(size);
                while ((events = ePersistenceInterface.getEvents(query, eventClass)) != null)
                {
                    for (EventListenerSignature signature : events)
                    {
                        push(signature.event.getClass(), signature.event);
                        ePersistenceInterface.removeEvent(signature);
                    }
                    count -= size;
                    if(count <= 0)
                        break;
                    if(size > count)
                        size = (int)count;
                    query.limit(size);
                }
            }
        }
        
        initialized = true;
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
                    if(listannot.threadSafe()) {
                        try
                        {
                            eventListenerObject.eventListenerInstance = possEventListener.newInstance();
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
                    mapEventListener(callbackMethod.getParameterTypes()[0], eventListenerObject);
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
            if(evtType.idempotent()) {
                logger.info("Event Type " + eventClass.getSimpleName() + " will generate idempotent requests to the event engine");
            }
            if(evtType.sequenceListenerPriority()) {
                eventProperties.eventListenerExecutors = Executors.newFixedThreadPool(1);
            }
        }
        eventPropertiesMap.put(eventClass, eventProperties);
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
    }
    
    /**
     * Get the Event listener callback method for the class provided
     * 
     * @param possEventListener
     * @return
     */
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
                possEventListener.newInstance();
                logger.info("Registering EventListener for " + possEventListener.getSimpleName() + " callback method " + possibleMethod.getName());
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
            push(eventClas, event);
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
            Future<List<Future>> futureoffs = push(eventClas, event);
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
            Future<List<Future>> futureoffs = push(eventClas, event);
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
    private Future<List<Future>> push(final Class eventClas, final Object event)
    {
        Callable<List<Future>> mainpushcall = new Callable<List<Future>>() {
            public List<Future> call() throws Exception
            {
                List<Future> futures = new ArrayList<Future>();
                if(eventListenerMap.get(eventClas) != null) {
                    List<EventListenerObject> eventListeners = eventListenerMap.get(eventClas);
                    for (int index = 0;index < eventListeners.size(); index++)
                    {
                        final EventListenerObject eventListenerObject = eventListeners.get(index);
                        try
                        {
                            final Object oInstance = !eventListenerObject.isThreadSafe?eventListenerObject.eventListenerClass.newInstance()
                                                        :eventListenerObject.eventListenerInstance;
                            final Method oCallbackMeth = eventListenerObject.eventCallBackMethod;
                            
                            final EventListenerSignature signature = getSignature(event, index);
                            
                            if(eventPropertiesMap.get(eventClas).idempotent && findDuplicate(signature)) {
                                logger.info("The event is marked as idempotent and a pending event alreay exists, hence skipping event....");
                                break;
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
                                            push(oCallbackMeth.getReturnType(), result);
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
                            
                            if(eventPropertiesMap.get(eventClas)!=null && eventPropertiesMap.get(eventClas).eventListenerExecutors!=null) 
                            {
                                futures.add(eventPropertiesMap.get(eventClas).eventListenerExecutors.submit(callserve));
                            }
                            else if(eventListenerObject.eventListenerExecutors != null)
                            {
                                futures.add(eventListenerObject.eventListenerExecutors.submit(callserve));
                            }
                            else if(executors != null) 
                            {
                                futures.add(executors.submit(callserve));
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
        };
        
        return internalExecutors.submit(mainpushcall);
    }
    
    private EventListenerSignature getSignature(Object event, int index)
    {
        if(eventListenerMap.get(event.getClass()) != null) {
            List<EventListenerObject> eventListeners = eventListenerMap.get(event.getClass());
            if(eventListeners.size()>index) {
                EventListenerSignature signature = new EventListenerSignature();
                signature.event = event;
                signature.listenerClassName = eventListeners.get(index).eventListenerClass.getSimpleName();
                signature.listenerMethodName = eventListeners.get(index).eventCallBackMethod.getName();
                return signature;
            }
        }
        return null;
    }
    
    private ClearEventStatusHandler getClearStatusHandler(Future<List<Future>> futureoffs)
    {
        ClearEventStatusHandler clStatusHandler = new ClearEventStatusHandler();
        clStatusHandler.futureoffs = futureoffs;
        return clStatusHandler;
    }
}