package com.evengine.test.events;

import java.io.Serializable;

import com.evengine.core.EventHandlerEngine.EventType;

@SuppressWarnings("serial")
@EventType(distributed=true, expireTime=60)
public class DummyDistributedEvent1 implements Serializable
{

    public DummyDistributedEvent1() {}

    /**
     * @param name
     * @param number
     */
    public DummyDistributedEvent1(String name, Integer number)
    {
        super();
        this.namedist = name;
        this.numberdist = number;
    }

    private String namedist;

    private Integer numberdist;

    @Override
    public String toString()
    {
        return "DummyDistributedEvent1 [namedist=" + namedist + ", numberdist=" + numberdist + "]";
    }
}
