/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package ui;

import core.SimClock;

/**
 * Simple text-based user interface.
 */
public class DTNSimTextUI extends DTNSimUI {
	private long lastUpdateRt;	// real time of last ui update
	private long startTime; // simulation start time
	/** How often the UI view is updated (milliseconds) */           //仿真时 UI View 是 60s 更新一次吗？
	public static final long UI_UP_INTERVAL = 60000;

	protected void runSim() {
		double simTime = SimClock.getTime();						//得到时间
		double endTime = scen.getEndTime();							//结束时间
	
		print("Running simulation '" + scen.getName()+"'");

		startTime = System.currentTimeMillis();  					//开始时间
		lastUpdateRt = startTime;
		
		while (simTime < endTime && !simCancelled){
			try {
				world.update();  									//世界更新
			} catch (AssertionError e) {
				e.printStackTrace();
				done();
				return;
			}
			simTime = SimClock.getTime();
			this.update(false); //如果下一个updateInterval还没到，就什么都不做。
		}
		
		double duration = (System.currentTimeMillis() - startTime)/1000.0;
		/***  整个仿真结束 ***/
		simDone = true;
		done();//所有Report r.close()
		this.update(true); // force final UI update  更新时间统计值
		
		print("Simulation done in " + String.format("%.2f", duration) + "s");
	
	}
	
	/**
	 * Updates user interface if the long enough (real)time (update interval)
	 * has passed from the previous update.
	 * @param forced If true, the update is done even if the next update
	 * interval hasn't been reached.
	 */
	private void update(boolean forced) {
		long now = System.currentTimeMillis();
		long diff = now - this.lastUpdateRt;
		double dur = (now - startTime)/1000.0;
		if (forced || (diff > UI_UP_INTERVAL)) {
			// simulated seconds/second calc
			double ssps = ((SimClock.getTime() - lastUpdate)*1000) / diff;
			print(String.format("%.1f %d: %.2f 1/s", dur, 
					SimClock.getIntTime(),ssps));
			
			this.lastUpdateRt = System.currentTimeMillis();
			this.lastUpdate = SimClock.getTime();
		}		
	}
	
	private void print(String txt) {
		System.out.println(txt);
	}
	
}
