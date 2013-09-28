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
    public List<EventListenerSignature> getEvents(Class eventClass, Date startDate, int expireTime, int limit)
    {
        Query query = null;

        if(expireTime>0)
        {
            Calendar cal = Calendar.getInstance();
            cal.setTime(startDate);
            cal.add(Calendar.SECOND, -expireTime);

            query = new Query(Criteria.where(STATUS).is(STATUS_PENDING).
                    andOperator(Criteria.where(DISPATCH_DATE).gt(startDate)));
        }
        else
        {
            query = new Query(Criteria.where(STATUS).is(STATUS_PENDING).
                    andOperator(Criteria.where(DISPATCH_DATE).lt(startDate)));
        }
        query.limit(limit);
        return mongoTemplate.find(query, EventListenerSignature.class, eventClass.getSimpleName());
    }

    @SuppressWarnings("rawtypes")
    public long getEventsCount(Class eventClass, Date startDate, int expireTime)
    {
        Query query = null;

        if(expireTime>0)
        {
            Calendar cal = Calendar.getInstance();
            cal.setTime(startDate);
            cal.add(Calendar.SECOND, -expireTime);

            query = new Query(Criteria.where(STATUS).is(STATUS_PENDING).
                    andOperator(Criteria.where(DISPATCH_DATE).gt(startDate)));
        }
        else
        {
            query = new Query(Criteria.where(STATUS).is(STATUS_PENDING).
                    andOperator(Criteria.where(DISPATCH_DATE).lt(startDate)));
        }
        return mongoTemplate.count(query, eventClass.getSimpleName());
    }

    public boolean findAndUpdateDuplicateEvents(EventListenerSignature signature, int expireTime)
    {
        Query query = null;
        Calendar cal = Calendar.getInstance();
        if(expireTime<=0)
        {
            query = new Query(Criteria.where(EVENT).is(signature.getEvent())
                .andOperator(Criteria.where(STATUS).is(STATUS_PENDING)));
        }
        else
        {
            cal.setTime(new Date());
            cal.add(Calendar.SECOND, -expireTime);

            query = new Query(Criteria.where(EVENT).is(signature.getEvent())
                    .andOperator(Criteria.where(STATUS).is(STATUS_PENDING))
                    .andOperator(Criteria.where(DISPATCH_DATE).gt(cal.getTime())));
        }
        EventListenerSignature result = mongoTemplate.findOne(query, EventListenerSignature.class,
                signature.getEvent().getClass().getSimpleName());
        if(expireTime>0)
        {
            query = new Query(Criteria.where(EVENT).is(signature.getEvent())
                    .andOperator(Criteria.where(STATUS).is(STATUS_PENDING))
                    .andOperator(Criteria.where(DISPATCH_DATE).lt(cal.getTime())));
            Update update = new Update();
            update.set(STATUS, STATUS_EXPIRED);

            mongoTemplate.updateFirst(query, update, signature.getEvent().getClass().getSimpleName());
        }
        if(result!=null)
            return true;
        return false;
    }
}
