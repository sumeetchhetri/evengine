package com.evengine.core;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
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
public class EventEngineMongoTemplate extends MongoTemplate
{
    static class EventEngineMongoConverter extends MappingMongoConverter
    {

        public EventEngineMongoConverter(MongoDbFactory mongoDbFactory,
                MappingContext<? extends MongoPersistentEntity<?>,MongoPersistentProperty> mappingContext)
        {
            super(mongoDbFactory, mappingContext);
        }
    }

    private final MongoConverter mongoConverter;

    private final MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext;


    public EventEngineMongoTemplate(MongoDbFactory mongoDbFactory)
    {
        super(mongoDbFactory);
        EventEngineMongoConverter converter = new EventEngineMongoConverter(mongoDbFactory, new MongoMappingContext());
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        converter.afterPropertiesSet();
        mongoConverter = converter;
        // We always have a mapping context in the converter, whether it's a simple one or not
        mappingContext = this.mongoConverter.getMappingContext();
    }

    private String determineCollectionName(final Class<?> entityClass) {

        if (entityClass == null) {
            throw new InvalidDataAccessApiUsageException(
                    "No class parameter provided, entity collection can't be determined!");
        }

        MongoPersistentEntity<?> entity = mappingContext.getPersistentEntity(entityClass);
        if (entity == null) {
            throw new InvalidDataAccessApiUsageException("No Persitent Entity information found for the class "
                    + entityClass.getName());
        }
        return entity.getCollection();
    }

    private <T> String determineEntityCollectionName(T obj) {
        if (null != obj) {
            return determineCollectionName(obj.getClass());
        }

        return null;
    }

    @Override
    public void save(Object objectToSave) {
        save(objectToSave, determineEntityCollectionName(objectToSave));
    }

    @Override
    public void save(Object objectToSave, String collectionName) {
        doSave(collectionName, objectToSave, this.mongoConverter);
    }
}
