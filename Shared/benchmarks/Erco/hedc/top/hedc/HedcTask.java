package hedc.top.hedc;
/*
 * Copyright (C) 1998 by ETHZ/INF/CS
 * All rights reserved
 *
 * @version $Id: Task.java 3342 2003-07-31 09:36:46Z praun $
 * @author Christoph von Praun
 */


import top.Task;
import hedc.ethz.util.*;

public abstract class HedcTask implements Cloneable {

   

    /** 
     * Task will not be processed by the Worker 
     * executor if set
     */ 
    public boolean valid = true;

        
    public abstract void cancel();
    
    public void topTask_run(Task now) {
    	System.out.println("task running");
	try {
	    runImpl();
	} catch(Exception e) {
	    Messages.warn(-1, "Task::run exception=%1", e);
	    // e.printStackTrace();
	}
	
    }

    public abstract void runImpl() throws Exception;
}
