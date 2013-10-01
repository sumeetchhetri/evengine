package com.evengine.core;

import java.util.concurrent.atomic.AtomicBoolean;


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
 * Get the pending events on startup and process them
 * Poll for any distributed event notifications and process them
 * Expire events which are past the expiry time
 * @author Sumeet Chhetri<br/>
 *
 */
public class EventPollExpireHandler implements Runnable
{

    protected AtomicBoolean done;

    private EventHandlerEngine eventEngine;

    protected EventPollExpireHandler(EventHandlerEngine engine)
    {
        this.eventEngine = engine;
        done = new AtomicBoolean(true);
    }

    /*
     * Handles distributed event processing
     * Expires events from the store
     *
     * (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    public void run()
    {
        while(done.get()) {
            eventEngine.handleExistingEvents(true);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }

            if(eventEngine.isPrimary()) {
                eventEngine.expireEvents(eventEngine.eventExpireClassMap);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }
            }
        }
    }

}
