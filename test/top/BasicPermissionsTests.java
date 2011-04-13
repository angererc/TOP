package top;

import static org.junit.Assert.*;

import org.junit.Test;

import top.Permissions;
import top.Permissions.KeyChainException;

public class BasicPermissionsTests {
	
	Thread now;
	Permissions<Thread> perm = new Permissions<Thread>() {		
		@Override
		public Thread now() {
			return now;
		}
		
	};

	private void readWrite(Object o) {
		perm.checkRead(o);
		perm.checkWrite(o);
	}
	private void readOnly(Object o) {
		perm.checkRead(o);
		try {
			perm.checkWrite(o);
			fail("shouldn't happen");
		} catch (KeyChainException e) {
			//
		}		
	}
	private void noAccess(Object o) {
		try {
			perm.checkRead(o);
			fail("shouldn't happen");
		} catch (KeyChainException e) {
			//
		}
		try {
			perm.checkWrite(o);
			fail("shouldn't happen");
		} catch (KeyChainException e) {
			//
		}
	}
	
	@Test
	public void testMultipleReaders() {		
		Thread task1 = new Thread();
		Thread task2 = new Thread();
		
		//task 1 begins		
		now = task1;
		
		Object o = new Object();
		readWrite(o);
		
		//hand over
		perm.addTask(o, task2);
		readOnly(o);
	
		//task 2 comes along 
		now = task2;
		readOnly(o);
		
	}
		
	@Test
	public void testHandoverOwnership() {
		
		Thread task1 = new Thread();
		Thread task2 = new Thread();
		
		//task 1 begins		
		now = task1;
		
		Object o = new Object();
		readWrite(o);
		
		//hand over
		perm.replaceNowWithTask(o, task2);
		//no write no read
		noAccess(o);
				
		//task 2 comes along 
		now = task2;
		readWrite(o);
	}
	
	@Test
	public void testImmutability() {
		
		Thread task1 = new Thread();
		Thread task2 = new Thread();
		
		//task 1 begins		
		now = task1;
		
		Object o = new Object();
		perm.makeImmutable(o);
		
		readOnly(o);
		
		//task 2 comes along 
		now = task2;
		readOnly(o);
		
	}
	
	@Test
	public void testShared() {
		
		Thread task1 = new Thread();
		Thread task2 = new Thread();
		
		//task 1 begins		
		now = task1;
		
		Object o = new Object();
		perm.makeShared(o);
		
		readWrite(o);
		
		//task 2 comes along 
		now = task2;
		readWrite(o);
		
	}
	
	@Test
	public void testLinking() {
		
		Thread task1 = new Thread();
		Thread task2 = new Thread();
		
		//task 1 begins		
		now = task1;
		
		Object o1 = new Object();
		Object o2 = new Object();
		
		perm.makeImmutable(o1);
		perm.linkKeychains(o1, o2);
		
		now = task2;
		assertTrue(perm.isImmutable(o2));
		
	}
}
