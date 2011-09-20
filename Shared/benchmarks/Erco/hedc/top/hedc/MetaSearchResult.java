package hedc.top.hedc;
/*
 * Copyright (C) 1998 by ETHZ/INF/CS
 * All rights reserved
 *
 * @version $Id: MetaSearchResult.java 3342 2003-07-31 09:36:46Z praun $
 * @author Christoph von Praun 
 */

import java.util.*;

import top.Task;
import hedc.ethz.util.*;
import static top.Permissions.perm;

public abstract class MetaSearchResult extends HedcTask implements Cloneable {
	public Date date = null;
	public List results = null;
	public MetaSearchRequest request = null;
	private boolean completed_ = false;
	
	public MetaSearchResult() {
		perm.newObject(this);
	}

	protected abstract class MetaSearchResultIterator implements Iterator {
		protected Hashtable h_ = new Hashtable();
		protected Iterator resultIterator_ = null;
		protected boolean firstSeen_ = false;

		protected MetaSearchResultIterator () {
			perm.newObject(this);
			resultIterator_ = (results == null || results.size() == 0) ? null : results.iterator();
		}

		public boolean hasNext() {
			perm.checkWrite(this);
			perm.checkWrite(h_);

			boolean ret = false;
			if (resultIterator_ != null) 
				ret = resultIterator_.hasNext();
			else if (firstSeen_ == false) {
				if (!completed_) // item was canceled
					h_.put("MESSAGE", "timed out");
				else 
					h_.put("MESSAGE", "no results");
				firstSeen_= ret = true;
			}
			return ret;
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	public abstract Iterator getInfo();

	public synchronized void cancel() {	
		//perm.checkWrite(this);
		request = null;
		valid = false;
	}

	@Override
	public void topTask_run(Task now) {
		System.out.println("task running");
		try {
			runImpl();
		} catch(Exception e) {
			Messages.warn(-1, "Task::run exception=%1", e);
			// e.printStackTrace();
		}
		perm.checkWrite(this);
		completed_ = true;
		//request is set to null when the thread is cancelled
		if(request != null) {
			request.countDownInterrupt();
		}
	}

	public static MetaSearchResult cloneTask(MetaSearchResult t) {
		MetaSearchResult ret = null;
		if (t != null && t instanceof HedcTask) 
			try { 
				ret = (MetaSearchResult) t.clone();
				perm.newObject(ret);
				ret.results = null;
				ret.request = null;
				ret.completed_ = false;
				ret.date = null;
			} catch (CloneNotSupportedException e) {
				Messages.error("MetaSearchResult::CloneNotSupported exception=%1", e);
			}
			return ret;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("##");
		for (Iterator it = getInfo(); it.hasNext(); ) {
			Hashtable h = (Hashtable) it.next();
			sb.append(h);
			sb.append("#");
		}
		sb.append("#");
		return sb.toString();
	}
}







