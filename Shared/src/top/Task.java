package top;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jsr166y.ForkJoinPool;
import jsr166y.RecursiveAction;

public final class Task extends RecursiveAction {
	
	public static boolean DEBUG = false;
	public static boolean CHECK_WELLFORMEDNESS = true;
	
	public static final String MainTaskMethodPrefix = "topMainTask_";
	public static final String NormalTaskMethodPrefix = "topTask_";
	
	//we use our own thread pool that shuts down automatically once the worklist is empty
	static ForkJoinPool Pool = null; //Executors.newCachedThreadPool();
	static AtomicInteger numTasksScheduled;
	
	//the task that is currently executing in this thread
	private static final ThreadLocal<Task> Now = new ThreadLocal<Task>();
	
	/*
	 * 
	 */
	private Object receiver;
	private Method method;
	private Object[] params;
	private volatile AtomicReference<Object> result;
	
	public static final Task now() {
		return Now.get();
	}
	/*
	 * States: 
	 * during init: retainCount == 0
	 * retained/in future: retain count > 0
	 * about to execute: retain count = 0
	 * executing
	 * retired
	 * 
	 * State changers:
	 * during init: init by creator task
	 * retain count up/down: by any task that directly or indirectly retains this
	 * retain count = 0: by the last task that retained this; there must be no other tasks now that increase/decrease retain counts
	 * executing: the worker thread that executes this task
	 * retired: worker thread that executes this task
	 * 
	 * retained tasks are released after this has retired; so this is the point when added hb edges don't have any retain effect
	 * 
	 *  invariant:
	 *  the retain count is equal to the number of occurrences of this in other task's retainedTasks lists together
	 */
	/** retain count; once retain count drops to 0 the activation can start */
	private static final int DURING_INIT = -99;
	private static final int EXECUTING = -42; //flag to indicate that this activation has been executed
	private static final int RETIRED = -84; //flag to indicate that this activation has been executed
	
	private final AtomicInteger retainCount = new AtomicInteger(DURING_INIT);
	private ArrayList<Task> retainedTasks; //somebody called this.hb(other) so we retain other
	
	public Task() {
		
	}

	//called by xsched.Runtime.schedule()
	//args contain this at position 0
	void scheduleAsNormalTask(Object receiver, String taskName, Object... params) {
		init_unsynchronized(receiver, taskName, params);
		Task now = Now.get();
		assert(now != null) : "no 'now' task found! Maybe you didn't start a root topMainTask_ ?";
		now.retain_unsynchronized(this);
		
		//if(DEBUG)
			//System.out.println("scheduled normal task " + this);
	}
	
	void scheduleAsMainTask(Object receiver, String taskName, Object... params) {
		init_unsynchronized(receiver, taskName, params);
		assert(Now.get() == null) : "main task must be the first task to be scheduled";			
		try {
			this.retainCount.set(0);
			if(DEBUG)
				System.out.println("scheduled main task " + this);
			//Phaser = new Phaser();
			numTasksScheduled = new AtomicInteger(1);
			
			Pool = new ForkJoinPool(java.lang.Runtime.getRuntime().availableProcessors(), ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
			
			Pool.invoke(this);
						
			Pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
			Pool = null;			
			
		} catch (Exception e) {
			Pool.shutdownNow();
			throw new RuntimeException(e);
		}
	}
	
	private void init_unsynchronized(Object receiver, String taskName, Object... params) {
		assert params[0] == this;
		if(this.receiver != null) {
			throw new RuntimeException("Can only call init once!");
		}
		this.receiver = receiver;
		this.params = params;
		
		//splice in "this" as the first parameter
		int len = params.length;
		Class<?>[] paramTypes = new Class<?>[len];
		
		for(int i = 0; i < len; i++) {			
			paramTypes[i] = params[i].getClass();
		}
		
		Method found = null;
		//find the corresponding method
		try {
			Method[] methods = receiver.getClass().getMethods();
			//iterate all methods
			findMethod: for(Method m : methods) {
				//name must be equal
				if(m.getName().equals(taskName)) {
					Class<?>[] methodParams = m.getParameterTypes();
					//param lengths must be equal
					if(methodParams.length == paramTypes.length) {
						//check that all param types are OK
						for(int i = 0; i < methodParams.length; i++) {
							Class<?> methodParam = methodParams[i];
							Class<?> myParam = paramTypes[i];
							if( ! methodParam.isAssignableFrom(myParam)) {
								//no, continue with a different method
								continue findMethod;
							}
						}
						//params are OK, keep the method and finish
						found = m;
						break findMethod;
					}
				}
			}
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}	
		
		if(found == null) {
			throw new RuntimeException("didn't find valid method for " + taskName + " in " + receiver);
		}
		this.method = found;
	}
	//null during initialization
	public Object receiver() {
		//may see incomplete state during init; force null everywhere
		if(this.isInInit())
			return null;
		
		return this.receiver;
	}
	
	public String taskName() {
		//may see incomplete state during init; force null everywhere
		if(this.isInInit())
			return null;
		
		return this.method.getName();
	}

	public Object[] parameters() {
		//may see incomplete state during init; force null everywhere
		if(this.isInInit())
			return null;
		
		return this.params;
	}
	
	public Object result() {
		return result.get();
	}

	public void setResult(Object value) {
		if(! result.compareAndSet(null, value)) {
			throw new RuntimeException("Result value of task " + this + " has been already set. Old value: " + result.get() + ", new value: " + value);
		}
	}

	public boolean isAboutToExecute() {
		return this.retainCount.get() == 0;
	}

	public boolean isExecuting() {
		return this.retainCount.get() == EXECUTING;
	}

	public boolean isInInit() {
		return this.retainCount.get() == DURING_INIT;
	}
	
	public boolean isInFuture() {
		return this.retainCount.get() > 0;
	}
	
	public boolean hasRetired() {
		return this.retainCount.get() == RETIRED;
	}

	//called by any task; synchronize to protect our array list
	public synchronized boolean isOrderedBefore(Task later) {
		if(this.retainedTasks == null) {
			return false;
		} else if(this.retainedTasks.contains(later)) {
			return true;
		} else {
			for(Task succ : this.retainedTasks) {
				if(succ.isOrderedBefore(later)) {
					return true;
				}
			}
			return false;
		}
	}

	private void retain_unsynchronized(Task later) {
		//we know that other happens after now and therefore it's retain count is > 0 and will remain so until we're done;
		//we just have to make sure that our increment to later isn't swallowed, therefore we use an atomic integer
		if(this.retainedTasks==null)
			this.retainedTasks = new ArrayList<Task>();
		 
		this.retainedTasks.add(later);
		
		if(later.retainCount.get() == DURING_INIT) {
			later.retainCount.set(1);
		} else {
			later.retainCount.incrementAndGet();
		}
		
		if(DEBUG)
			System.out.println(this + " retains " + later);
	}
		
	//called from any task. we know that (when well formed) later is in the future
	//but we do'nt know much about "this"
	//synchronize so to be sure that this task doesn't retire while we are retaining
	public synchronized void hb(Task later) {			
		if(CHECK_WELLFORMEDNESS && later.isOrderedBefore(this))
			throw new RuntimeException("HB edge would result in cycle: " + this + "->" + later);
		
		if(CHECK_WELLFORMEDNESS && !Now.get().isOrderedBefore(later))
			throw new RuntimeException("Now must happen before right hand side of HB edge: " + Now.get() + ": " + this + "->" + later);
		
		if(CHECK_WELLFORMEDNESS)
			assert(later.isInFuture()) : "rhs of happens-before must be in future";
		
		if(!this.hasRetired()) {			
			this.retain_unsynchronized(later);
		}
	}
	
	private void releaseRetained_unsynced() {
		if(this.retainedTasks == null)
			return;
		//release retained
		for(Task succ : this.retainedTasks) {			
			int count = succ.retainCount.decrementAndGet();
			if(DEBUG)
				System.out.println(this + " released " + succ + "; new retain count is " + count);
			if(count == 0) {
				numTasksScheduled.incrementAndGet();
				succ.fork();				
			}
		}
	}
	
	@Override
	public void compute() {
		
		assert(this.retainCount.get() == 0) : "retain count must be 0 but was " + this.retainCount;
		
		this.retainCount.set(EXECUTING);
		
		//if(DEBUG)
			//System.out.println("executing " + this);
		
		Now.set(this);
		
		try {
			this.method.invoke(this.receiver, this.params);
			//synchronize to make sure we don't retire while somebody else adds HB relationships
			synchronized(this) {
				this.retainCount.set(RETIRED);
			}
			//clean up and give successors a chance to execute				
			this.releaseRetained_unsynced();
		} catch (Exception e) {
			Pool.shutdown();
			//we kill the program if there is ever an unhandled exception
			//so we know that either all works according to the schedule or we die
			e.printStackTrace();
			throw new Error(e);
		} finally {
			//we're out'a here
			Now.set(null);
			//could set fields to null but why should we... GC will do that sooner or later
			//and if the user keeps the thread around he might have a reason.
			//we are just clearing the array lists to avoid keeping everything alive when only retaining one task
			if(!DEBUG)
				this.retainedTasks = null;
			
			//Phaser.arrive();
			int waiting = numTasksScheduled.decrementAndGet();
			if(waiting == 0) {
				Pool.shutdown();
			}
		}
	}
	
	public String stateAsString() {
		int count = this.retainCount.get();
		if(count == DURING_INIT) {
			return "DURING_INIT";
		} else if (count > 0) {
			return "RETAINED(" + count + ")";
		} else if (count == 0) {
			return "ABOUT_TO_EXECUTE";
		} else if (count == EXECUTING) {
			return "EXECUTING";
		} else if (count == RETIRED) {
			return "RETIRED";
		} else {
			throw new RuntimeException("Illegal task state: " + count);
		}
	}
	@Override
	public String toString() {
		return "Task@" + System.identityHashCode(this) + "(" + this.taskName() + ", " + this.stateAsString() + ")";
	}
	
}
