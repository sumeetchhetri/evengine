package com.evengine.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
 * The event listener signature that is persisted in the event store
 * Exactly defines this event with status and the callback details
 * @author Sumeet Chhetri<br/>
 *
 */
@SuppressWarnings("serial")
public class EventListenerSignature implements Serializable
{
    /**
     * @param id
     * @param event
     * @param eventClassName
     * @param listenerClassName
     * @param listenerMethodName
     * @param status
     * @param dispatchDate
     * @param processedDate
     * @param error
     * @param distributed
     * @param instances
     */
    public EventListenerSignature(String id, Object event, String listenerClassName,
            String listenerMethodName, String status, boolean distributed, List<String> instances,
            boolean isCanExpire)
    {
        super();
        this.id = id;
        this.event = event;
        this.eventClassName = event.getClass().getCanonicalName();
        this.listenerClassName = listenerClassName;
        this.listenerMethodName = listenerMethodName;
        this.status = status;
        this.dispatchDate = new Date();
        this.distributed = distributed;
        this.instances = instances;
        this.canExpire = isCanExpire;
    }
    protected EventListenerSignature()
    {

    }
    String id;
    Object event;
    String eventClassName;
    String listenerClassName;
    String listenerMethodName;
    String status;
    Date dispatchDate;
    Date processedDate;
    String error;
    boolean distributed;
    boolean isLocked;
    boolean canExpire;
    List<String> instances = new ArrayList<String>();
    public String getStatus()
    {
        return status;
    }
    public List<String> getInstances()
    {
        return instances;
    }
    public void setListenerClassName(String listenerClassName)
    {
        this.listenerClassName = listenerClassName;
    }
    public String getId()
    {
        return id;
    }
    public Object getEvent()
    {
        return event;
    }
    public String getListenerClassName()
    {
        return listenerClassName;
    }
    public String getListenerMethodName()
    {
        return listenerMethodName;
    }
    public String getEventClassName()
    {
        return eventClassName;
    }
    public Date getDispatchDate()
    {
        return dispatchDate;
    }
    public Date getProcessedDate()
    {
        return processedDate;
    }
    public String getError()
    {
        return error;
    }
    public boolean isDistributed()
    {
        return distributed;
    }
    public boolean isLocked()
    {
        return isLocked;
    }

    public boolean isCanExpire()
    {
        return canExpire;
    }
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + (canExpire ? 1231 : 1237);
        result = prime * result + (distributed ? 1231 : 1237);
        result = prime * result + ((event == null) ? 0 : event.hashCode());
        result = prime * result + ((eventClassName == null) ? 0 : eventClassName.hashCode());
        result = prime * result + (isLocked ? 1231 : 1237);
        result = prime * result + ((listenerClassName == null) ? 0 : listenerClassName.hashCode());
        result = prime * result + ((listenerMethodName == null) ? 0 : listenerMethodName.hashCode());
        result = prime * result + ((status == null) ? 0 : status.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        EventListenerSignature other = (EventListenerSignature) obj;
        if (canExpire != other.canExpire)
            return false;
        if (distributed != other.distributed)
            return false;
        if (event == null)
        {
            if (other.event != null)
                return false;
        }
        else if (!event.equals(other.event))
            return false;
        if (eventClassName == null)
        {
            if (other.eventClassName != null)
                return false;
        }
        else if (!eventClassName.equals(other.eventClassName))
            return false;
        if (isLocked != other.isLocked)
            return false;
        if (listenerClassName == null)
        {
            if (other.listenerClassName != null)
                return false;
        }
        else if (!listenerClassName.equals(other.listenerClassName))
            return false;
        if (listenerMethodName == null)
        {
            if (other.listenerMethodName != null)
                return false;
        }
        else if (!listenerMethodName.equals(other.listenerMethodName))
            return false;
        if (status == null)
        {
            if (other.status != null)
                return false;
        }
        else if (!status.equals(other.status))
            return false;
        return true;
    }

}
