package hedc.top.hedc;
/*
 * Copyright (C) 1998 by ETHZ/INF/CS
 * All rights reserved
 * 
 * @version $Id: MetaSearchRequest.java 3342 2003-07-31 09:36:46Z praun $
 * @author Christoph von Praun
 */


import java.util.*;
import java.io.*;

import top.Task;
import static top.Permissions.perm;

public class MetaSearchRequest {

	private long size_ = -1;
	private Writer wrt_ = null;
	private Hashtable params_ = null;
	private MetaSearchImpl msi_ = null;
	public List results = null;
	private int counter_ = 0;
	private Thread thread_ = null;

	public MetaSearchRequest(Writer w, MetaSearchImpl msi, Hashtable params) {
		perm.newObject(this);
		wrt_ = w;
		msi_ = msi;
		params_ = params;
		perm.linkKeychains(this, perm.newObject(params));
	}

	public void registerInterrupt(Thread t, int ctr) {
		perm.checkWrite(this);
		counter_ = ctr;
		thread_ = t;
	}

	public synchronized void countDownInterrupt() {
		//In MetaSearchImpl.topTask_search() we schedule multiple MetaSearchResult.topTask_run()
		//tasks which then call countDownInterrupt
		//because we synchronize we don't check here even though that's a little more dangerous that I would like it to be...
		//perm.checkWrite(this);
		if (thread_ != null && --counter_ == 0)
			thread_.interrupt();
	}

	public void topTask_go(Task now, Task later) throws Exception {
		perm.checkRead(this);
		if (wrt_ != null) {
			Task searchTask = new Task();
			Task writeBackTask = new Task();
			
			msi_.topTask_search(searchTask, writeBackTask, params_, wrt_, this);
			this.topTask_go_2(writeBackTask, later, searchTask);

			perm.replaceNowWithTask(this, searchTask);
			perm.replaceNowWithTask(msi_, searchTask);
			
			searchTask.hb(writeBackTask);
			writeBackTask.hb(later);
		} else { 
			Task searchTask = new Task();
			Task writeBackTask = new Task();

			msi_.topTask_search(searchTask, writeBackTask, params_, this);
			this.topTask_go_3(writeBackTask, later, searchTask);

			perm.replaceNowWithTask(this, searchTask);
			perm.replaceNowWithTask(msi_, searchTask);
			
			searchTask.hb(writeBackTask);
			writeBackTask.hb(later);
		}
		
	}

	public void topTask_go_2(Task now, Task later, Task result) {
		perm.checkWrite(this);
		System.out.println("MetaSearchRequest: writing back size result");
		size_ = (Long) result.result();
		
		perm.replaceNowWithTask(this, later);
		perm.replaceNowWithTask(msi_, later);
	}

	public void topTask_go_3(Task now, Task later, Task result) {
		perm.checkWrite(this);
		System.out.println("MetaSearchRequest: writing back lists result");
		results = (List) result.result();
		
		perm.replaceNowWithTask(this, later);
		perm.replaceNowWithTask(msi_, later);
	}

	public String printResults() {
		perm.checkRead(this);
		String ret;
		if (results != null) {
			StringBuffer sb = new StringBuffer();
			sb.append("[");
			for (Iterator it = results.iterator(); it.hasNext(); ) {
				sb.append(it.next());
				if (it.hasNext())
					sb.append(",");
			}
			sb.append("]");
			ret = sb.toString();
		} else
			ret = "none";
		return ret;
	}
}





