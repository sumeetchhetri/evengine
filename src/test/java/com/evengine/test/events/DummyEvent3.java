package com.evengine.test.events;

import java.math.BigInteger;
import java.util.Map;

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
public class DummyEvent3
{
    
    public DummyEvent3() {}

    private Map<String, Double> mapsd;
    
    private BigInteger bi;

    /**
     * @param mapsd
     * @param bi
     */
    public DummyEvent3(Map<String,Double> mapsd, BigInteger bi)
    {
        super();
        this.mapsd = mapsd;
        this.bi = bi;
    }

    @Override
    public String toString()
    {
        return "DummyEvent3 [mapsd=" + mapsd + ", bi=" + bi + "]";
    }
}
