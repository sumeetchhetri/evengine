package com.evengine.store;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
        mongoTemplate.save(signature);
    }

    public void removeEvent(EventListenerSignature signature)
    {
        Query query = new Query(Criteria.where(ID).is(signature.getId()));
        mongoTemplate.remove(query, EventListenerSignature.class);
    }

    @SuppressWarnings("rawtypes")
    public List<EventListenerSignature> getEvents(Class eventClass, Date startDate, boolean isDistributed, String instanceId, int expireTime, int limit)
    {
        Query query = null;

        List<Criteria> criteriaList = new ArrayList<Criteria>();

        criteriaList.add(Criteria.where(DISTRIBUTED).is(isDistributed));
        criteriaList.add(Criteria.where(IS_LOCKED).is(false));
        criteriaList.add(Criteria.where(EVENT_CLASSNAME).is(eventClass.getCanonicalName()));

        if(expireTime>0)
        {
            Calendar cal = Calendar.getInstance();
            cal.setTime(startDate);
            cal.add(Calendar.SECOND, -expireTime);

            if(!isDistributed)
            {
                criteriaList.add(Criteria.where(STATUS).is(STATUS_PENDING));
                criteriaList.add(Criteria.where(DISPATCH_DATE).gt(cal.getTime()));
            }
            else
            {
                criteriaList.add(Criteria.where(STATUS).is(STATUS_PARTIAL));
                criteriaList.add(Criteria.where(DISPATCH_DATE).gt(cal.getTime()));
                criteriaList.add(Criteria.where(INSTANCES).nin(instanceId));
            }
        }
        else
        {
            if(!isDistributed)
            {
                criteriaList.add(Criteria.where(STATUS).is(STATUS_PENDING));
            }
            else
            {
                criteriaList.add(Criteria.where(STATUS).is(STATUS_PARTIAL));
                criteriaList.add(Criteria.where(INSTANCES).nin(instanceId));
            }
        }

        Criteria mainCriteria = new Criteria();
        mainCriteria.andOperator(criteriaList.toArray(new Criteria[criteriaList.size()]));
        query = new Query(mainCriteria);

        query.limit(limit);
        return mongoTemplate.find(query, EventListenerSignature.class);
    }

    @SuppressWarnings("rawtypes")
    public long getEventsCount(Class eventClass, Date startDate, boolean isDistributed, String instanceId, int expireTime)
    {
        Query query = null;

        List<Criteria> criteriaList = new ArrayList<Criteria>();

        criteriaList.add(Criteria.where(DISTRIBUTED).is(isDistributed));
        criteriaList.add(Criteria.where(IS_LOCKED).is(false));
        criteriaList.add(Criteria.where(EVENT_CLASSNAME).is(eventClass.getCanonicalName()));

        if(expireTime>0)
        {
            Calendar cal = Calendar.getInstance();
            cal.setTime(startDate);
            cal.add(Calendar.SECOND, -expireTime);

            if(!isDistributed)
            {
                criteriaList.add(Criteria.where(STATUS).is(STATUS_PENDING));
                criteriaList.add(Criteria.where(DISPATCH_DATE).gt(cal.getTime()));
            }
            else
            {
                criteriaList.add(Criteria.where(STATUS).is(STATUS_PARTIAL));
                criteriaList.add(Criteria.where(DISPATCH_DATE).gt(cal.getTime()));
                criteriaList.add(Criteria.where(INSTANCES).nin(instanceId));
            }
        }
        else
        {
            if(!isDistributed)
            {
                criteriaList.add(Criteria.where(STATUS).is(STATUS_PENDING));
            }
            else
            {
                criteriaList.add(Criteria.where(STATUS).is(STATUS_PARTIAL));
                criteriaList.add(Criteria.where(INSTANCES).nin(instanceId));
            }
        }

        Criteria mainCriteria = new Criteria();
        mainCriteria.andOperator(criteriaList.toArray(new Criteria[criteriaList.size()]));
        query = new Query(mainCriteria);

        return mongoTemplate.count(query, EventListenerSignature.class);
    }

    public boolean findDuplicateEvents(EventListenerSignature signature, int expireTime)
    {
        Query query = null;
        Calendar cal = Calendar.getInstance();
        if(expireTime<=0)
        {
            query = getCriteriaAndQuery(Criteria.where(EVENT).is(signature.getEvent()),
                        Criteria.where(EVENT_CLASSNAME).is(signature.getEvent().getClass().getCanonicalName()),
                        Criteria.where(STATUS).in(STATUS_PENDING, STATUS_PARTIAL),
                        Criteria.where(LISTENER_CLASSNAME).is(signature.getListenerClassName()),
                        Criteria.where(LISTENER_METHNAME).is(signature.getListenerMethodName()));
        }
        else
        {
            cal.setTime(new Date());
            cal.add(Calendar.SECOND, -expireTime);

            query = getCriteriaAndQuery(Criteria.where(EVENT).is(signature.getEvent()),
                        Criteria.where(EVENT_CLASSNAME).is(signature.getEvent().getClass().getCanonicalName()),
                        Criteria.where(STATUS).in(STATUS_PENDING, STATUS_PARTIAL),
                        Criteria.where(LISTENER_CLASSNAME).is(signature.getListenerClassName()),
                        Criteria.where(LISTENER_METHNAME).is(signature.getListenerMethodName()),
                        Criteria.where(DISPATCH_DATE).gt(cal.getTime()));
        }
        EventListenerSignature result = mongoTemplate.findOne(query, EventListenerSignature.class);
        if(result!=null)
            return true;
        return false;
    }

    public boolean lockEventStore(String instanceId)
    {
        LockStatus status = mongoTemplate.findOne(new Query(), LockStatus.class);
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

    public void expireEvents(Map<String, Integer> eventExpireMap)
    {
        if(eventExpireMap!=null)
        {
            List<Criteria> criteriaList = new ArrayList<Criteria>();

            for (String evtClsName : eventExpireMap.keySet())
            {
                if(eventExpireMap.get(evtClsName)>0)
                {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(new Date());
                    cal.add(Calendar.SECOND, -eventExpireMap.get(evtClsName));

                    criteriaList.add(Criteria.where(EVENT_CLASSNAME).is(evtClsName).and(DISPATCH_DATE).lt(cal.getTime()));
                }
            }

            Criteria mainCriteria = new Criteria();
            mainCriteria.orOperator(criteriaList.toArray(new Criteria[criteriaList.size()]));

            Query query = getCriteriaAndQuery(mainCriteria,
                            Criteria.where(STATUS).in(STATUS_PENDING, STATUS_PARTIAL),
                            Criteria.where(CAN_EXPIRE).is(true),
                            Criteria.where(IS_LOCKED).is(false));

            Update update = new Update();
            update.set(STATUS, STATUS_EXPIRED);

            mongoTemplate.updateFirst(query, update, EventListenerSignature.class);
        }
    }

    private Query getCriteriaAndQuery(Criteria... criterias)
    {
        Criteria mainCriteria = new Criteria();
        mainCriteria.andOperator(criterias);
        Query query = new Query(mainCriteria);
        return query;
    }
}
