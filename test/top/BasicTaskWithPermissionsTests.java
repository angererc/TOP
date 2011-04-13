package top;

import org.junit.Test;
import static top.Permissions.perm;

public class BasicTaskWithPermissionsTests {

	public void topTask_Task1(Task now, Task later, Integer id, Object shared) {
		System.out.println("Task 1 <" + id + ">");
		perm.checkRead(shared);
		//we give up our read rights
		perm.replaceNowWithTask(shared, later);
	}
	
	public void topTask_Task2(Task now, Object mine) {
		perm.checkWrite(mine);
		System.out.println("Task 2");
	}
	
	public void topMainTask_Main(Task now) {
		Object shared = new Object();
		
		Task join = new Task();
		topTask_Task2(join, shared);		
		
		for(int i = 100; i >= 0; i--) {
			Task fork = new Task();
			topTask_Task1(fork, join, i, shared);			
			//allow forked tasks to read shared
			perm.addTask(shared, fork);
			fork.hb(join);
		}
		
		//we allow join to become the owner of shared
		perm.replaceNowWithTask(shared, join);
	}
	
	@Test
	public void startTest() {
		topMainTask_Main(new Task());
	}
}
