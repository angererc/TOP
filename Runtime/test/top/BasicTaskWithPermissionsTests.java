package top;

import java.util.Vector;
import static org.junit.Assert.*;
import org.junit.Test;
import static top.Permissions.perm;

public class BasicTaskWithPermissionsTests {

	public void topTask_Task1(Task now, Task later, Integer id, Object readOnly) {
		System.out.println("Task 1 <" + id + ">");
		
		//we can read readOnly and write the shared children array
		perm.checkRead(readOnly);
		perm.checkWrite(results);
		
		//we create a result object and link its access rights to the readOnly object
		//we can do that because we are the owner of the result object
		Object result = perm.newObject(new Object());		
		perm.linkKeychains(readOnly, result);
		
		//store the result in the results array
		results.add(result);
		
		//we give up our read rights on the readonly object
		perm.replaceNowWithTask(readOnly, later);
	}
	
	public void topTask_Task2(Task now, Object mine) {
		//we can write the shared results array just like anybody else 
		perm.checkWrite(results);
		//we can write the mine object which used to be the readOnly object because everybody gave back the rights
		perm.checkWrite(mine);
		
		//we can therefore also read the result objects in result because they are linked to "mine"
		for(Object result : results) {
			perm.checkWrite(result);
			//because we own mine and the result we can unlink the result object from the keychain so that mine and result are separate again
			perm.unlinkKeychains(result);
			//now is the direct owner of 'result' again
		}
		
		//now pretend we share 'mine' again
		perm.addTask(mine, new Task());
		
		try {
			perm.checkWrite(mine);
			fail("shouldn't happen");
		} catch (Exception ex) {
			//ok, we can't write mine
		}
		
		//but we still can read the result objects because we unlinked them before
		for(Object child : results) {
			perm.checkWrite(child);		
		}
		
		System.out.println("Task 2");
	}
	
	Vector<Object> results;
	public void topMainTask_Main(Task now) {
		results = perm.newObject(new Vector<Object>());
		//we explicitly marke the children vector as shared
		perm.makeShared(results);
		
		Object readOnly = perm.newObject(new Object());
			
		
		Task join = new Task();
		topTask_Task2(join, readOnly);		
		
		for(int i = 100; i >= 0; i--) {
			Task fork = new Task();
			topTask_Task1(fork, join, i, readOnly);			
			//allow forked tasks to read shared
			perm.addTask(readOnly, fork);
			fork.hb(join);
		}
		
		//we allow join to become the owner of shared (i.e., we give up our right, since the task is over anyway...)
		perm.replaceNowWithTask(readOnly, join);
	}
	
	@Test
	public void startTest() {
		topMainTask_Main(new Task());
	}
}
