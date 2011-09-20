package elevator.top.elevator;
/*
 * Copyright (C) 2000 by ETHZ/INF/CS
 * All rights reserved
 * 
 * @version $Id: Controls.java 2094 2003-01-30 09:41:18Z praun $
 * @author Roger Karrer
 */

import java.lang.*;
import java.util.*;
import java.io.*;
import static top.Permissions.perm;

// class of the shared control object
class Controls {
	Floor[] floors;
	private boolean terminated;

	public Controls(int numFloors) {
		floors = new Floor[numFloors + 1];
		for (int i = 0; i <= numFloors; i++)
			floors[i] = new Floor();
	}
	
	public synchronized void setTerminated() {
		perm.checkWrite(this);
		terminated = true;
	}
	
	public synchronized boolean terminated() {
		perm.checkRead(this);
		return terminated;
	}

	// this is called to inform the control object of a down call on floor
	// onFloor
	public void pushDown(int onFloor, int toFloor) {
		perm.checkRead(floors);
		synchronized (floors[onFloor]) {
			System.out.println("*** Someone on floor " + onFloor
					+ " wants to go to " + toFloor);
			perm.checkWrite(floors[onFloor].downPeople);
			floors[onFloor].downPeople.addElement(new Integer(toFloor));
			if (floors[onFloor].downPeople.size() == 1) {
				perm.checkWrite(floors[onFloor]);
				floors[onFloor].downFlag = false;
			}
		}
	}

	// this is called to inform the control object of an up call on floor
	// onFloor
	public void pushUp(int onFloor, int toFloor) {
		perm.checkRead(floors);
		synchronized (floors[onFloor]) {
			System.out.println("*** Someone on floor " + onFloor
					+ " wants to go to " + toFloor);
			perm.checkWrite(floors[onFloor].upPeople);
			floors[onFloor].upPeople.addElement(new Integer(toFloor));
			if (floors[onFloor].upPeople.size() == 1) {
				perm.checkWrite(floors[onFloor]);
				floors[onFloor].upFlag = false;
			}
		}
	}

	// An elevator calls this if it wants to claim an up call
	// Sets the floor's upFlag to true if he has not already been set to true
	// Returns true if the elevator has successfully claimed the call, and
	// False if the call was already claimed (upFlag was already true)
	public boolean claimUp(String lift, int floor) {
		if (checkUp(floor)) {
			perm.checkRead(floors);
			synchronized (floors[floor]) {
				perm.checkWrite(floors[floor]);
				if (!floors[floor].upFlag) {
					floors[floor].upFlag = true;
					return true;
				}
			}
		}
		return false;
	}

	// An elevator calls this if it wants to claim an down call
	// Sets the floor's downFlag to true if he has not already been set to true
	// Returns true if the elevator has successfully claimed the call, and
	// False if the call was already claimed (downFlag was already true)
	public boolean claimDown(String lift, int floor) {
		if (checkDown(floor)) {
			perm.checkRead(floors);
			synchronized (floors[floor]) {
				perm.checkWrite(floors[floor]);
				if (!floors[floor].downFlag) {
					floors[floor].downFlag = true;
					return true;
				}
			}
		}
		return false;
	}

	// An elevator calls this to see if an up call has occured on the given
	// floor. If another elevator has already claimed the up call on the
	// floor, checkUp() will return false. This prevents an elevator from
	// wasting its time trying to claim a call that has already been claimed
	public boolean checkUp(int floor) {
		perm.checkRead(floors);
		synchronized (floors[floor]) {
			perm.checkRead(floors[floor]);
			perm.checkRead(floors[floor].upPeople);
			boolean ret = floors[floor].upPeople.size() != 0;
			ret = ret && !floors[floor].upFlag;
			return ret;
		}
	}

	// An elevator calls this to see if a down call has occured on the given
	// floor. If another elevator has already claimed the down call on the
	// floor, checkUp() will return false. This prevents an elevator from
	// wasting its time trying to claim a call that has already been claimed
	public boolean checkDown(int floor) {
		perm.checkRead(floors);
		synchronized (floors[floor]) {
			perm.checkRead(floors[floor]);
			boolean ret = floors[floor].downPeople.size() != 0;
			ret = ret && !floors[floor].downFlag;
			return ret;
		}
	}

	// An elevator calls this to get the people waiting to go up. The
	// returned Vector contains Integer objects that represent the floors
	// to which the people wish to travel. The floors vector and upFlag
	// are reset.
	public Vector getUpPeople(int floor) {
		perm.checkRead(floors);
		synchronized (floors[floor]) {
			perm.checkWrite(floors[floor]);
			Vector temp = floors[floor].upPeople;
			floors[floor].upPeople = new Vector();
			perm.linkKeychains(floors[floor], perm.newObject(floors[floor].upPeople));
			floors[floor].upFlag = false;
			return temp;
		}
	}

	// An elevator calls this to get the people waiting to go down. The
	// returned Vector contains Integer objects that represent the floors
	// to which the people wish to travel. The floors vector and downFlag
	// are reset.
	public Vector getDownPeople(int floor) {
		perm.checkRead(floors);
		synchronized (floors[floor]) {
			perm.checkWrite(floors[floor]);
			Vector temp = floors[floor].downPeople;
			floors[floor].downPeople = new Vector();
			perm.linkKeychains(floors[floor], perm.newObject(floors[floor].downPeople));
			floors[floor].downFlag = false;
			return temp;
		}
	}
}
