package com.evengine.store;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.evengine.core.EventListenerSignature;

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
@SuppressWarnings("rawtypes")
public interface EventPersistenceInterface
{
    public class LockStatus
    {
        String id;
        boolean isLocked;
        String instanceId;
    }
    public boolean lockEventStore(String instanceId);
    public boolean unLockEventStore(String instanceId);
    public void storeEvent(EventListenerSignature signature);
    public void removeEvent(EventListenerSignature signature);
    public boolean findDuplicateEvents(EventListenerSignature signature, int expireTime);
    public List<EventListenerSignature> getEvents(Class eventClass, Date startDate, boolean isDistributed, String instanceId, int expireTime, int limit);
    public long getEventsCount(Class eventClass, Date startDate, boolean isDistributed, String instanceId, int expireTime);
    public void expireEvents(Map<String, Integer> eventExpireMap);
}
