package philo.top.philo;
import java.util.*;

import top.Task;
import static top.Permissions.perm;

class Table {
    boolean forks[];
    int eatctr = 0;
    final int MAX_EAT = 12;

    Table() {
	forks = perm.newObject(new boolean[Philo.NUM_PHIL]);
	perm.makeShared(forks);
	
	//we access the primitive field eatctr concurrently
	perm.makeShared(perm.newObject(this));
	
	for (int i = 0; i < Philo.NUM_PHIL; ++i)
	    forks[i] = true;
    }

    synchronized void getForks(int id) throws InterruptedException {
	int id1 = id;
	int id2 = (id + 1) % Philo.NUM_PHIL;
	System.out.println(id + " check forks[" + id1 + "]=" + forks[id1] +" and forks[" + id2 + "]="+forks[id2]);
	System.out.flush();
	
	perm.checkRead(forks);
	while(! (forks[id1] && forks[id2])) {
	    System.out.println(id + " wait for forks");
	    wait();
	}
	
	perm.checkWrite(forks);
	forks[id1] = forks[id2] = false;
	System.out.println(id + " got forks");
	
	perm.checkWrite(this);
	if (eatctr++ > MAX_EAT)
	    System.exit(0);
    }
  
    synchronized void putForks(int id) {
	System.out.println(id + " putforks");
	perm.checkWrite(forks);
	forks[id] = forks[(id + 1) % Philo.NUM_PHIL] = true;
	notify();
	System.out.println(id + " notify done");
    }
}

public class Philo {
    static final int NUM_PHIL = 2;
    int id;
    Table t;
    Object o;
    
    
    Philo(int id, Table t) {
	this.id = id;
	this.t = t;
    }
                
    public void topTask_run(Task now) {
	System.out.println(id + " run start");
	o = perm.newObject(new Hashtable());
	perm.checkWrite(o);
	
	try {
	    while (true) {
		System.out.println(id + " let's try to get the forks"); // eat
		t.getForks(id);
		System.out.println(id + " have the forks now"); // eat
		long l = (int)(Math.random() * 500) + 20;
		System.gc();
		System.out.println(id + " eating (" + l + ")"); // eat
		Thread.sleep(l);
		System.out.println(id + " that was good"); // eat
		t.putForks(id);
	    }
	} catch(InterruptedException e) {
	    System.exit(1);
	}
    }

    public static class Driver {
    	public void topMainTask_begin(Task now) {        	
        	Table tab = new Table();
        	Philo p;
        	for (int i=0; i < NUM_PHIL; ++i) {
        	    p = new Philo(i, tab);
        	    
        	    p.topTask_run(new Task());        	    
        	}
        }
    }
    public static void main(String args[]) {    
    	new Driver().topMainTask_begin(new Task());
    }
}







