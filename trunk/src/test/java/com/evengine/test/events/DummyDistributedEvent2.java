package com.evengine.test.events;

import java.io.Serializable;

import com.evengine.core.EventHandlerEngine.EventType;

@SuppressWarnings("serial")
@EventType(distributed=true, expireTime=60, processOnce=true)
public class DummyDistributedEvent2 implements Serializable
{

    public DummyDistributedEvent2() {}

    /**
     * @param name
     * @param number
     */
    public DummyDistributedEvent2(String name, Integer number)
    {
        super();
        this.namedist2 = name;
        this.numberdist2 = number;
    }

    private String namedist2;

    private Integer numberdist2;

    @Override
    public String toString()
    {
        return "DummyDistributedEvent2 [namedist2=" + namedist2 + ", numberdist2=" + numberdist2 + "]";
    }
}
