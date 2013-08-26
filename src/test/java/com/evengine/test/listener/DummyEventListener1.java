package com.evengine.test.listener;

import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import com.evengine.core.EventHandlerEngine.EventListener;
import com.evengine.core.EventHandlerEngine.EventListenerCallBack;
import com.evengine.test.events.DummyEvent1;
import com.evengine.test.events.DummyEvent2;

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
