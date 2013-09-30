package com.evengine.store;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.evengine.core.EventEngineMongoTemplate;
import static com.evengine.core.EventHandlerEngine.*;
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
public class EventPersistentMongoDBImpl implements EventPersistenceInterface
{

    private EventEngineMongoTemplate mongoTemplate;

    public EventPersistentMongoDBImpl(MongoDbFactory mongoDbFactory)
    {
        this.mongoTemplate = new EventEngineMongoTemplate(mongoDbFactory);
    }

    public void storeEvent(EventListenerSignature signature)
    {
        mongoTemplate.save(signature, signature.getEvent().getClass().getSimpleName());
    }

    public void removeEvent(EventListenerSignature signature)
    {
        Query query = new Query(Criteria.where(ID).is(signature.getId()));
        mongoTemplate.remove(query, signature.getEvent().getClass().getSimpleName());
    }

    @SuppressWarnings("rawtypes")
    public List<EventListenerSignature> getEvents(Class eventClass, Date startDate, boolean isDistributed, String instanceId, int expireTime, int limit)
    {
        Query query = null;

        if(expireTime>0)
        {
            Calendar cal = Calendar.getInstance();
            cal.setTime(startDate);
            cal.add(Calendar.SECOND, -expireTime);

            if(!isDistributed)
                query = new Query(Criteria.where(STATUS).is(STATUS_PENDING).
                        andOperator(Criteria.where(DISTRIBUTED).is(isDistributed)).
                        andOperator(Criteria.where(DISPATCH_DATE).gt(startDate)));
            else
                query = new Query(Criteria.where(STATUS).is(STATUS_PARTIAL).
                        andOperator(Criteria.where(DISPATCH_DATE).gt(startDate)).
                        andOperator(Criteria.where(DISTRIBUTED).is(isDistributed)).
                        andOperator(Criteria.where(INSTANCES).nin(instanceId)));
        }
        else
        {
            if(!isDistributed)
                query = new Query(Criteria.where(STATUS).is(STATUS_PENDING).
                        andOperator(Criteria.where(DISTRIBUTED).is(isDistributed)));
            else
                query = new Query(Criteria.where(STATUS).is(STATUS_PARTIAL).
                        andOperator(Criteria.where(DISTRIBUTED).is(isDistributed)).
                        andOperator(Criteria.where(INSTANCES).nin(instanceId)));
        }
        query.limit(limit);
        return mongoTemplate.find(query, EventListenerSignature.class, eventClass.getSimpleName());
    }

    @SuppressWarnings("rawtypes")
    public long getEventsCount(Class eventClass, Date startDate, boolean isDistributed, String instanceId, int expireTime)
    {
        Query query = null;

        if(expireTime>0)
        {
            Calendar cal = Calendar.getInstance();
            cal.setTime(startDate);
            cal.add(Calendar.SECOND, -expireTime);

            if(!isDistributed)
                query = new Query(Criteria.where(STATUS).is(STATUS_PENDING).
                        andOperator(Criteria.where(DISTRIBUTED).is(isDistributed)).
                        andOperator(Criteria.where(DISPATCH_DATE).gt(startDate)));
            else
                query = new Query(Criteria.where(STATUS).is(STATUS_PARTIAL).
                        andOperator(Criteria.where(DISPATCH_DATE).gt(startDate)).
                        andOperator(Criteria.where(DISTRIBUTED).is(isDistributed)).
                        andOperator(Criteria.where(INSTANCES).nin(instanceId)));
        }
        else
        {
            if(!isDistributed)
                query = new Query(Criteria.where(STATUS).is(STATUS_PENDING).
                        andOperator(Criteria.where(DISTRIBUTED).is(isDistributed)));
            else
                query = new Query(Criteria.where(STATUS).is(STATUS_PARTIAL).
                        andOperator(Criteria.where(DISTRIBUTED).is(isDistributed)).
                        andOperator(Criteria.where(INSTANCES).nin(instanceId)));
        }
        return mongoTemplate.count(query, eventClass.getSimpleName());
    }

    public boolean findDuplicateEvents(EventListenerSignature signature, int expireTime)
    {
        Query query = null;
        Calendar cal = Calendar.getInstance();
        if(expireTime<=0)
        {
            query = new Query(Criteria.where(EVENT).is(signature.getEvent())
                .andOperator(Criteria.where(STATUS).in(STATUS_PENDING, STATUS_PARTIAL))
                .andOperator(Criteria.where(LISTENER_CLASSNAME).is(signature.getListenerClassName()))
                .andOperator(Criteria.where(LISTENER_METHNAME).is(signature.getListenerMethodName())));
        }
        else
        {
            cal.setTime(new Date());
            cal.add(Calendar.SECOND, -expireTime);

            query = new Query(Criteria.where(EVENT).is(signature.getEvent())
                    .andOperator(Criteria.where(STATUS).in(STATUS_PENDING, STATUS_PARTIAL))
                    .andOperator(Criteria.where(DISPATCH_DATE).gt(cal.getTime()))
                    .andOperator(Criteria.where(LISTENER_CLASSNAME).is(signature.getListenerClassName()))
                    .andOperator(Criteria.where(LISTENER_METHNAME).is(signature.getListenerMethodName())));
        }
        EventListenerSignature result = mongoTemplate.findOne(query, EventListenerSignature.class,
                signature.getEvent().getClass().getSimpleName());
        if(result!=null)
            return true;
        return false;
    }

    public boolean lockEventStore(String instanceId)
    {
        Query query = new Query(Criteria.where(IS_LOCKED).is(true));
        LockStatus status = mongoTemplate.findOne(query, LockStatus.class);
        if((status!=null && !status.isLocked) || status==null)
        {
            if(status==null)
            {
                status = new LockStatus();
            }
            status.instanceId = instanceId;
            status.isLocked = true;
            mongoTemplate.save(status);
            return true;
        }
        return false;
    }

    public boolean unLockEventStore(String instanceId)
    {
        Query query = new Query(Criteria.where(IS_LOCKED).is(true));
        LockStatus status = mongoTemplate.findOne(query, LockStatus.class);
        if(status!=null && status.isLocked)
        {
            status.instanceId = instanceId;
            status.isLocked = false;
            mongoTemplate.save(status);
            return true;
        }
        return false;
    }

    public void expireEvents(String evtClsName, String evtcollName, int expireTime)
    {
        if(expireTime>0)
        {
            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.SECOND, -expireTime);

            Query query = new Query(Criteria.where(EVENT_CLASSNAME).is(evtClsName)
                    .andOperator(Criteria.where(STATUS).is(STATUS_PENDING))
                    .andOperator(Criteria.where(DISTRIBUTED).is(false))
                    .andOperator(Criteria.where(DISPATCH_DATE).lt(cal.getTime())));

            Update update = new Update();
            update.set(STATUS, STATUS_EXPIRED);

            mongoTemplate.updateFirst(query, update, evtcollName);
        }
    }
}
