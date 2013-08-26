package com.evengine.store;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

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
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    public EventPersistentMongoDBImpl(MongoTemplate mongoTemplate)
    {
        this.mongoTemplate = mongoTemplate;
    }
    
    public void storeEvent(EventListenerSignature signature)
    {
        mongoTemplate.save(signature, signature.getEvent().getClass().getSimpleName());
    }
    
    public void removeEvent(EventListenerSignature signature)
    {
        Query query = new Query(Criteria.where("_id").is(signature.getId()));
        mongoTemplate.remove(query, signature.getEvent().getClass().getSimpleName());
    }

    @SuppressWarnings("rawtypes")
    public List<EventListenerSignature> getEvents(Query query, Class eventClass)
    {
        return mongoTemplate.find(query, EventListenerSignature.class, eventClass.getSimpleName());
    }

    @SuppressWarnings("rawtypes")
    public long getEventsCount(Query query, Class eventClass)
    {
        return mongoTemplate.count(query, eventClass.getSimpleName());
    }

    public boolean findDuplicateEvent(EventListenerSignature signature)
    {
        Query query = new Query(Criteria.where("event").is(signature.getEvent()));
        List<EventListenerSignature> result = mongoTemplate.find(query, EventListenerSignature.class, 
                signature.getEvent().getClass().getSimpleName());
        if(result!=null && result.size()>0)
            return true;
        return false;
    }
}
