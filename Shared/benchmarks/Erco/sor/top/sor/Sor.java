package sor.top.sor;
/*
 * Copyright (C) 2000 by ETHZ/INF/CS
 * All rights reserved
 * 
 * @version $Id: Sor.java 2094 2003-01-30 09:41:18Z praun $
 * @author Florian Schneider
 */

import java.util.*;

import top.Task;
import static top.Permissions.perm;

public class Sor {

	public final static int N = 500;
	public final static int M = 500;
	public static int iterations = 100;

	public static float[][] black_ = new float[M + 2][N + 1];
	public static float[][] red_ = new float[M + 2][N + 1];

	public static int nprocs = 1;
	
	static SorWorker[] t;

	public static void main(String[] args) {

		boolean nop = false;

		try {
			if (args[0].equals("--nop"))
				nop = true;
			else {
				nprocs = Integer.parseInt(args[1]);
				iterations = Integer.parseInt(args[0]);
			}
		} catch (Exception e) {
			System.out
					.println("usage: java Sor <iterations> <number of threads>");
			System.out.println("    or java Sor --nop");
			System.exit(-1);
		}

		t = new SorWorker[nprocs];
		
		// initialize arrays
		int first_row = 1;
		int last_row = M;

		for (int i = first_row; i <= last_row; i++) {
			/*
			 * Initialize the top edge.
			 */
			if (i == 1)
				for (int j = 0; j <= N; j++)
					red_[0][j] = black_[0][j] = (float) 1.0;
			/*
			 * Initialize the left and right edges.
			 */
			if ((i & 1) != 0) {
				red_[i][0] = (float) 1.0;
				black_[i][N] = (float) 1.0;
			} else {
				black_[i][0] = (float) 1.0;
				red_[i][N] = (float) 1.0;
			}
			/*
			 * Initialize the bottom edge.
			 */
			if (i == M)
				for (int j = 0; j <= N; j++)
					red_[i + 1][j] = black_[i + 1][j] = (float) 1.0;
		}

		// start computation
		System.gc();
		long a = new Date().getTime();

		if (!nop) {
			new Sor().topMainTask_begin(new Task());
		}

		long b = new Date().getTime();

		System.out.println("Sor-" + nprocs + "\t" + Long.toString(b - a));

		// print out results
		float red_sum = 0, black_sum = 0;
		for (int i = 0; i < M + 2; i++)
			for (int j = 0; j < N + 1; j++) {
				red_sum += red_[i][j];
				black_sum += black_[i][j];
			}
		System.out.println("Exiting. red_sum = " + red_sum + ", black_sum = "
				+ black_sum);
	}

	public static void print(String s) {
		System.out.println(Thread.currentThread().getName() + ":" + s);
	}

	public void topMainTask_begin(Task now) {
		Task joinTask = new Task();
		this.topTask_end(joinTask);
		
		perm.makeShared(perm.newObject(black_));
		perm.makeShared(perm.newObject(red_));
		perm.newObject(t);		
		perm.newObject(Sor.class);
		
		Task iterationTask = new Task();
		this.topTask_iteration(iterationTask, joinTask);
		iterationTask.hb(joinTask);	
		
		//the iteration accesses the static field i
		perm.replaceNowWithTask(Sor.class, iterationTask);
		
		for (int proc_id = 0; proc_id < nprocs; proc_id++) {
			int first_row = (M * proc_id) / nprocs + 1;
			int last_row = (M * (proc_id + 1)) / nprocs;
			
			if ((first_row & 1) != 0) {
				perm.checkWrite(t);
				//create new worker and give the iteration task access rights to it
				SorWorker worker = new sor_first_row_odd(first_row, last_row);
				t[proc_id] = worker;
				perm.replaceNowWithTask(worker, iterationTask);
			} else {
				perm.checkWrite(t);
				SorWorker worker = new sor_first_row_even(first_row, last_row);
				t[proc_id] = worker;
				perm.replaceNowWithTask(worker, iterationTask);
			}
		}
		
		//we're done with t
		perm.makeImmutable(t);
	}
	
	public static volatile int i = -1;
	public void topTask_iteration(Task now, Task later) {
		perm.checkWrite(Sor.class);
		i++;
		//System.out.println("Setting up next iteration " + i);
		if(i < Sor.iterations) {
			Task nextIteration = new Task();
			Task barrier1 = new Task();
			Task barrier2 = new Task();
			
			this.topTask_iteration(nextIteration, later);
			this.topTask_barrier1(barrier1);
			this.topTask_barrier2(barrier2);
			
			//the iteration accesses the static field i
			perm.replaceNowWithTask(Sor.class, nextIteration);
			
			barrier1.hb(barrier2);
			barrier2.hb(nextIteration);
			nextIteration.hb(later);
					
			perm.checkRead(t);
			for (int proc_id = 0; proc_id < nprocs; proc_id++) {
				SorWorker worker = t[proc_id];
				
				//schedule second phase first because first phase needs reference so that it
				//can forward the permissions when it's done
				Task p2 = new Task();
				worker.topTask_phase2(p2, nextIteration);
				
				//schedule new worker task and forward our access rights to it
				Task p1 = new Task();				
				worker.topTask_phase1(p1, p2);
				perm.replaceNowWithTask(worker, p1);

				p1.hb(barrier1);
				barrier1.hb(p2);
				p2.hb(barrier2);
			}
		}		
	}
	
	public void topTask_barrier1(Task now) {
		//System.out.println("Barrier 1 of iteration " + i + " reached");
	}
	
	public void topTask_barrier2(Task now) {
		//System.out.println("Barrier 2 of iteration " + i + " reached");
	}
	
	public void topTask_end(Task now) {
		System.out.println("activations are done...");
	}
	
	public static interface SorWorker {
		public void topTask_phase1(Task now, Task later);
		public void topTask_phase2(Task now, Task later); 
	}
	
	public static class sor_first_row_odd implements SorWorker {

		int first_row;
		int end;
		int N = Sor.N;
		int M = Sor.M;
		float[][] black_ = Sor.black_;
		float[][] red_ = Sor.red_;

		public sor_first_row_odd(int a, int b) {
			first_row = a;
			end = b;
			perm.newObject(this);
		}

		public void topTask_phase1(Task now, Task later) {
			int j, k;
			
			//reading first_row and end etc primitive fields
			perm.checkRead(this);
			perm.checkWrite(black_);
			perm.checkRead(red_);
			
			//Sor.print("phase 1 iteration A "+i);
			for (j = first_row; j <= end; j++) {

				for (k = 0; k < N; k++) {

					black_[j][k] = (red_[j - 1][k] + red_[j + 1][k]
							+ red_[j][k] + red_[j][k + 1])
							/ (float) 4.0;
				}
				if ((j += 1) > end)
					break;

				for (k = 1; k <= N; k++) {

					black_[j][k] = (red_[j - 1][k] + red_[j + 1][k]
							+ red_[j][k - 1] + red_[j][k])
							/ (float) 4.0;
				}
			}
			
			perm.replaceNowWithTask(this, later);
		}
		
		public void topTask_phase2(Task now, Task later) {
			int j, k;
			//reading first_row and end etc primitive fields
			perm.checkRead(this);
			perm.checkRead(black_);
			perm.checkWrite(red_);
			
			//Sor.print("phase 2 iteration A "+i + ", first=" + first_row + " end=" + end);
			for (j = first_row; j <= end; j++) {

				for (k = 1; k <= N; k++) {

					red_[j][k] = (black_[j - 1][k] + black_[j + 1][k]
							+ black_[j][k - 1] + black_[j][k])
							/ (float) 4.0;
				}
				if ((j += 1) > end)
					break;

				for (k = 0; k < N; k++) {

					red_[j][k] = (black_[j - 1][k] + black_[j + 1][k]
							+ black_[j][k] + black_[j][k + 1])
							/ (float) 4.0;
				}
			}
			
			perm.replaceNowWithTask(this, later);
		}

	}

	public static class sor_first_row_even implements SorWorker {

		int first_row;
		int end;
		int N = Sor.N;
		int M = Sor.M;
		float[][] black_ = Sor.black_;
		float[][] red_ = Sor.red_;

		public sor_first_row_even(int a, int b) {
			first_row = a;
			end = b;
			perm.newObject(this);
		}

		public void topTask_phase1(Task now, Task later) {
			
			//reading first_row and end etc primitive fields
			perm.checkRead(this);
			perm.checkWrite(black_);
			perm.checkRead(red_);
			
			//Sor.print("phase 1 iteration B "+i);
			for (int j = first_row; j <= end; j++) {

				for (int k = 1; k <= N; k++) {

					black_[j][k] = (red_[j - 1][k] + red_[j + 1][k]
					                                             + red_[j][k - 1] + red_[j][k])
					                                             / (float) 4.0;
				}
				if ((j += 1) > end)
					break;

				for (int k = 0; k < N; k++) {

					black_[j][k] = (red_[j - 1][k] + red_[j + 1][k]
					                                             + red_[j][k] + red_[j][k + 1])
					                                             / (float) 4.0;
				}
			}
			
			perm.replaceNowWithTask(this, later);
		}
		
		public void topTask_phase2(Task now, Task later) {
			
			//reading first_row and end etc primitive fields
			perm.checkRead(this);
			perm.checkRead(black_);
			perm.checkWrite(red_);
			
			//Sor.print("phase 2 iteration B "+i);
			for (int j = first_row; j <= end; j++) {

				for (int k = 0; k < N; k++) {

					red_[j][k] = (black_[j - 1][k] + black_[j + 1][k]
					                                               + black_[j][k] + black_[j][k + 1])
					                                               / (float) 4.0;
				}
				if ((j += 1) > end)
					break;

				for (int k = 1; k <= N; k++) {

					red_[j][k] = (black_[j - 1][k] + black_[j + 1][k]
					                                               + black_[j][k - 1] + black_[j][k])
					                                               / (float) 4.0;
				}
			}
			
			perm.replaceNowWithTask(this, later);
		}
	}
}
