package com.evengine.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import org.apache.log4j.Logger;

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
public class ClearEventStatusHandler implements Runnable
{
    private static Logger logger = Logger.getLogger(ClearEventStatusHandler.class.getName());
    
    @SuppressWarnings("rawtypes")
    Future<List<Future>> futureoffs;
    public void run()
    {
        getResults();
    }
    
    public List<Object> getResults()
    {
        while(!futureoffs.isDone()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }
        }
        @SuppressWarnings("rawtypes")
        List<Future> futures = new ArrayList<Future>();
        try {
            futures = futureoffs.get();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        List<Object> results = new ArrayList<Object>();
        boolean done = false;
        while(!done)
        {
            done = true;
            try
            {
                for (int i=0;i<futures.size();i++)
                {
                    done &= futures.get(i).isDone();
                }
                Thread.sleep(1);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
        for (int i=0;i<futures.size();i++)
        {
            try
            {
                results.add(futures.get(i).get());
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
        return results;
    }
}
