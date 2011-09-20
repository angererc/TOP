package tsp.top.tsp;

import java.util.PriorityQueue;
import static top.Permissions.perm;

public class Config {
	
	private final PriorityQueue<TourElement> queue;	
	final int numNodes;
	final int[][] weights;
	
	int startNode;
	int nodesFromEnd;
	
	int minTourLength;
	int[] minTour;
	
	Config(int tspSize) {
		queue = new PriorityQueue<TourElement>();
		perm.makeShared(perm.newObject(queue));
		
		numNodes = tspSize;
		
		weights = new int[numNodes + 1][numNodes + 1];
		perm.makeShared(perm.newObject(weights));
		
		minTour = new int[numNodes + 1];
		perm.makeShared(perm.newObject(minTour));
		
		minTourLength = Integer.MAX_VALUE;
		nodesFromEnd = 12;
	}
	
	TourElement getTour() {
		perm.checkRead(this);
		perm.checkWrite(queue);
		synchronized(queue) {
			return queue.remove();
		}		
	}

	public void enqueue(final TourElement newTour) {
		perm.checkRead(this);
		perm.checkWrite(queue);
		synchronized(queue) {
			queue.add(newTour);
		}		
	}
	
	public void setBest(final int curDist, final int[] path) {
		perm.checkWrite(minTour);
		synchronized(this) {
//			System.err.printf("curDist: %d minTourLength: %d tour: %s\n", 
//			curDist, minTourLength, Arrays.toString(path));
			if(curDist < minTourLength) {
				System.arraycopy(path, 0, minTour, 0, minTour.length);
				minTourLength = curDist;
		
//				System.err.printf("  BEST SO FAR\n");
			}
		}		
	}

}
