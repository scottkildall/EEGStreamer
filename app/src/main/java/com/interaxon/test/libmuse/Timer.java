package com.interaxon.test.libmuse;


/**
 * 	Timer
 * 
 *  Timing class which let's you specify a time, in milliseconds. This is event-driven, i.e. needs to be called by the
 *  draw() function rather than an interrupt-driven function
 *
 *  Written by Scott Kildall
 *  
 */

public class Timer {
	//-------- PUBLIC FUNCTIONS --------/
	public Timer(long _duration ) {
		setTimer(_duration);
	}
	
	public void setTimer(long _duration) { duration = _duration; }
	
	public void start() { 
		startTime = System.currentTimeMillis();
	}
	
	public Boolean expired() {
		return ((startTime + duration) < System.currentTimeMillis());
	}
	
	public float percentage() {
		if( expired() )
			return 1.0f;
		else
			return (float)(System.currentTimeMillis()-startTime)/(float)duration;
	}
	
	//-------- PRIVATE VARIABLES --------/
	private long duration;
	protected long startTime = 0;
}
