import java.util.*;

public class Scheduler extends Thread
{
  private Vector[] queues;
  // Multilevel feedback queue Q0, Q1, Q2
  private Vector Q0, Q1, Q2;
  private int timeSlice;
  private static final int DEFAULT_TIME_SLICE = 1000;

  // New data added to p161 
  private boolean[] tids; // Indicate which ids have been used
  private static final int DEFAULT_MAX_THREADS = 10000;

  // A new feature added to p161 
  // Allocate an ID array, each element indicating if that id has been used
  private int nextId = 0;
  private void initTid( int maxThreads ) {
    tids = new boolean[maxThreads];
	  for ( int i = 0; i < maxThreads; i++ )
      tids[i] = false;
  }

  // A new feature added to p161 
  // Search an available thread ID and provide a new thread with this ID
  private int getNewTid( ) {
    for ( int i = 0; i < tids.length; i++ ) {
	    int tentative = ( nextId + i ) % tids.length;
	    if ( tids[tentative] == false ) {
    		tids[tentative] = true;
    		nextId = ( tentative + 1 ) % tids.length;
    		return tentative;
	    }
    }
    return -1;
  }

  // A new feature added to p161 
  // Return the thread ID and set the corresponding tids element to be unused
  private boolean returnTid( int tid ) {
    if ( tid >= 0 && tid < tids.length && tids[tid] == true ) {
      tids[tid] = false;
      return true;
    }
    return false;
  }
  
  // Process threads from Q0
  private void processQ0(Thread current) {
    while (Q0.size() > 0) {
      try {
	      TCB currentTCB = (TCB)Q0.firstElement( );
  	    if ( currentTCB.getTerminated( ) == true ) {
    		  Q0.remove( currentTCB );
    		  returnTid( currentTCB.getTid( ) );
    		  continue;
    	  }
    		current = currentTCB.getThread( );
        if (current != null) {
        	if (current.isAlive())
        		current.resume();
        	else {
        		// Spawn must be controlled by Scheduler
        		// Scheduler must start a new thread
        		current.start();
        	}
        } 

        // Time slice for Q0 is timeSlice / 2
        schedulerSleep( timeSlice / 2 );
        // System.out.println("* * * Context Switch * * * ");

      	synchronized ( queues ) {
      	  if ( current != null && current.isAlive( ) )
      		  current.suspend();
      	  Q0.remove( currentTCB ); // move this TCB to the tail of Q1
      	  Q1.add( currentTCB );
    	  }
  	  } catch ( NullPointerException e3 ) { };
	  } 
  }
  
  // Process threads from Q1
  private void processQ1(Thread current) {
    while (Q0.size() > 0 || Q1.size() > 0) {
      while (Q0.size() > 0) {
        // Call processQ0 in case there are higher priority threads
        processQ0(current);
      }
      try {
        // if there is a thread in this queue proceed
        if ( Q1.size() > 0) {
  	      TCB currentTCB = (TCB)Q1.firstElement( );
    	    if ( currentTCB.getTerminated( ) == true ) {
      		  Q1.remove( currentTCB );
      		  returnTid( currentTCB.getTid( ) );
      		  continue;
      	  }
      		current = currentTCB.getThread( );
          if (current != null) {
          	if (current.isAlive())
          		current.resume();
          	else {
          		// Spawn must be controlled by Scheduler
          		// Scheduler must start a new thread
          		current.start();
          	}
          }

          // Time slice for Q1 is timeSlice
          schedulerSleep( timeSlice / 2 );
          // System.out.println("* * * Context Switch * * * ");
      
          while (Q0.size() > 0) {
            // Check again for higher priority threads
            processQ1(current);
          }
      
          schedulerSleep( timeSlice / 2 );

        	synchronized ( queues ) {
        	  if ( current != null && current.isAlive( ) )
        		  current.suspend();
        	  Q1.remove( currentTCB ); // move this TCB to the tail of Q2
        	  Q2.add( currentTCB );
      	  }
    	  }
  	  } catch ( NullPointerException e3 ) { };
	  }
  }
  
  // Process threads from Q2
  private void processQ2(Thread current) {
    while (Q0.size() > 0 || Q1.size() > 0 || Q2.size() > 0) {
      while (Q1.size() > 0 || Q0.size() > 0) {
        // Call processQ1 in case there are higher priority threads
        processQ1(current);
      }
      try {  
        // if there is a thread in this queue proceed
        if ( Q2.size() > 0) {
    	    TCB currentTCB = (TCB)Q2.firstElement( );
      	  if ( currentTCB.getTerminated( ) == true ) {
      		  Q2.remove( currentTCB );
      		  returnTid( currentTCB.getTid( ) );
      		  continue;
      	  }
      		current = currentTCB.getThread( );
          if (current != null) {
            if (current.isAlive())
          		current.resume();
          	else {
          		// Spawn must be controlled by Scheduler
          		// Scheduler must start a new thread
          		current.start();
          	}
          }
          for (int napTime = 0; napTime < timeSlice * 2; 
            napTime += timeSlice / 2) {
            // Time slice for Q2 is timeSlice * 2
            schedulerSleep( timeSlice / 2 );
            // System.out.println("* * * Context Switch * * * ");
            // check again for higher priority threads
            while (Q1.size() > 0 || Q0.size() > 0) {
              // Call processQ1 in case there are higher priority threads
              processQ1(current);
            }
          }
      
          schedulerSleep( timeSlice );

          synchronized ( queues ) {
            if ( current != null && current.isAlive( ) )
              current.suspend();
            Q2.remove( currentTCB ); // rotate this TCB to the end
            Q2.add( currentTCB );
          }
    	  }
  	  } catch ( NullPointerException e3 ) { };
	  }
  }

  // A new feature added to p161 
  // Retrieve the current thread's TCB from the queue
  public TCB getMyTcb( ) {
    Thread myThread = Thread.currentThread( ); // Get my thread object
    synchronized( queues ) {
      for (Vector queue : queues) {
	      for ( int i = 0; i < queue.size( ); i++ ) {
		      TCB tcb = ( TCB )queue.elementAt( i );
		      Thread thread = tcb.getThread( );
		      if ( thread == myThread )           // if this is my TCB, return it
		        return tcb;
	      }
	    }
	  }
    return null;
  }

  // A new feature added to p161 
  // Return the maximum number of threads to be spawned in the system
  public int getMaxThreads( ) {
    return tids.length;
  }

  public Scheduler( ) {
    timeSlice = DEFAULT_TIME_SLICE;
    queues = new Vector[3];
    Q0 = queues[0] = new Vector();
    Q1 = queues[1] = new Vector();
    Q2 = queues[2] = new Vector();
    initTid( DEFAULT_MAX_THREADS );
  }

  public Scheduler( int quantum ) {
    timeSlice = quantum;
    queues = new Vector[3];
    Q0 = queues[0] = new Vector();
    Q1 = queues[1] = new Vector();
    Q2 = queues[2] = new Vector();
    initTid( DEFAULT_MAX_THREADS );
  }

  // A new feature added to p161 
  // A constructor to receive the max number of threads to be spawned
  public Scheduler( int quantum, int maxThreads ) {
    timeSlice = quantum;
    queues = new Vector[3];
    Q0 = queues[0] = new Vector();
    Q1 = queues[1] = new Vector();
    Q2 = queues[2] = new Vector();
    initTid( maxThreads );
  }

  private void schedulerSleep( int milliseconds ) {
    try {
      Thread.sleep( milliseconds );
    } catch ( InterruptedException e ) {}
  }

  // A modified addThread of p161 example
  public TCB addThread( Thread t ) {
    TCB parentTcb = getMyTcb( );      // get my TCB and find my TID
    int pid = ( parentTcb != null ) ? parentTcb.getTid( ) : -1;
    int tid = getNewTid( );           // get a new TID
    if ( tid == -1)
      return null;
    TCB tcb = new TCB( t, tid, pid ); // create a new TCB
    Q0.add( tcb );                    // always enqueue into the tail of Q0
    return tcb;
  }

  // A new feature added to p161
  // Removing the TCB of a terminating thread
  public boolean deleteThread( ) {
    TCB tcb = getMyTcb( ); 
    if ( tcb!= null )
      return tcb.setTerminated( );
    else
      return false;
  }

  public void sleepThread( int milliseconds ) {
    try {
      sleep( milliseconds );
    } catch ( InterruptedException e ) { }
  }
  
  // A modified run of p161
  public void run( ) {
    Thread current = null;
    // get the next TCB and its thread from Q0, Q1, or Q2 
    while (true) {
      if (Q0.size() == 0 && Q1.size() == 0 && Q2.size() == 0)
        continue;
      processQ2(current);
    }
  }
}
