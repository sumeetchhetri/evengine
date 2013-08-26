package com.evengine.test.listener;

import org.apache.log4j.Logger;
import com.evengine.core.EventHandlerEngine.EventListener;
import com.evengine.core.EventHandlerEngine.EventListenerCallBack;
import com.evengine.test.events.DummyEvent2;
import com.evengine.test.events.DummyEvent3;

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
@EventListener
public class DummyEventListener2
{

    private static Logger logger = Logger.getLogger(DummyEventListener2.class.getName());
    
    @EventListenerCallBack
    public void processEvent(DummyEvent2 dummyEvent2)
    {
        logger.info("Hurray got a request for DummyEvent2 via Event Engine..." + dummyEvent2);
    }
    
    @EventListenerCallBack
    public void processEvent1(DummyEvent3 dummyEvent3)
    {
        logger.info("Hurray got a request for DummyEvent3 via Event Engine..." + dummyEvent3);
        /*
         * If the event engine is persistent then these events will keep getting added
         * to the persistence store as it is a never ending while loop
         */
        while(true){}
    }
}
