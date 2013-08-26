package com.evengine.core;

import java.io.Serializable;
import java.util.Date;

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

@SuppressWarnings("serial")
public class EventListenerSignature implements Serializable
{
    String id;
    Object event;
    String listenerClassName;
    String listenerMethodName;
    boolean isDone;
    Date dispatchDate;
    Date proessedDate;
    String error;
    public String getId()
    {
        return id;
    }
    public Object getEvent()
    {
        return event;
    }
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((dispatchDate == null) ? 0 : dispatchDate.hashCode());
        result = prime * result + ((error == null) ? 0 : error.hashCode());
        result = prime * result + ((event == null) ? 0 : event.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + (isDone ? 1231 : 1237);
        result = prime * result + ((listenerClassName == null) ? 0 : listenerClassName.hashCode());
        result = prime * result + ((listenerMethodName == null) ? 0 : listenerMethodName.hashCode());
        result = prime * result + ((proessedDate == null) ? 0 : proessedDate.hashCode());
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
        if (dispatchDate == null)
        {
            if (other.dispatchDate != null)
                return false;
        }
        else if (!dispatchDate.equals(other.dispatchDate))
            return false;
        if (error == null)
        {
            if (other.error != null)
                return false;
        }
        else if (!error.equals(other.error))
            return false;
        if (event == null)
        {
            if (other.event != null)
                return false;
        }
        else if (!event.equals(other.event))
            return false;
        if (id == null)
        {
            if (other.id != null)
                return false;
        }
        else if (!id.equals(other.id))
            return false;
        if (isDone != other.isDone)
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
        if (proessedDate == null)
        {
            if (other.proessedDate != null)
                return false;
        }
        else if (!proessedDate.equals(other.proessedDate))
            return false;
        return true;
    }
    
}
