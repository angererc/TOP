package top;

import org.junit.Test;


public class BasicTaskTests {

	public void topTask_Task1(Task now, Integer id) {
		try {
			Thread.sleep((long) (Math.random() * (100-id)));
		} catch (InterruptedException e) {
		}
		System.out.println("Task 1 <" + id + ">");
	}
	
	public void topTask_Task2(Task now) {
		System.out.println("Task 2");
	}
	
	public void topMainTask_Main(Task now) {
		Task join = new Task();
		topTask_Task2(join);
		
		for(int i = 100; i >= 0; i--) {
			Task fork = new Task();
			topTask_Task1(fork, i);
			fork.hb(join);
		}
	}
	
	@Test
	public void testBasicTask() {
		topMainTask_Main(new Task());
	}
}
