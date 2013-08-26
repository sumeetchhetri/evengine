package com.evengine.test.events;

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
public class DummyEvent1
{

    public DummyEvent1() {}
    
    /**
     * @param name
     * @param number
     */
    public DummyEvent1(String name, Integer number)
    {
        super();
        this.name = name;
        this.number = number;
    }

    private String name;
    
    private Integer number;

    @Override
    public String toString()
    {
        return "DummyEvent1 [name=" + name + ", number=" + number + "]";
    }
}
