/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package ui;

import java.util.Vector;

import report.Report;
import core.ApplicationListener;
import core.ConnectionListener;
import core.MessageListener;
import core.MovementListener;
import core.Settings;
import core.SettingsError;
import core.SimClock;
import core.SimError;
import core.SimScenario;
import core.UpdateListener;
import core.World;

/**
 * Abstract superclass for user interfaces; contains also some simulation
 * settings.
 */
public abstract class DTNSimUI {
	/** 
	 * Number of reports -setting id ({@value}). Defines how many reports
	 * are loaded. 
	 */
	public static final String NROF_REPORT_S = "Report.nrofReports";
	/** 
	 * Report class name -setting id prefix ({@value}). Defines name(s) of
	 * the report classes to load. Must be suffixed with numbers starting from
	 * one.
	 */
	public static final String REPORT_S = "Report.report";
	/**
	 * Movement model warmup time -setting id ({@value}). Defines how many
	 * seconds of movement simulation is run without connectivity etc. checks 
	 * before starting the real simulation.
	 */
	public static final String MM_WARMUP_S = 
		movement.MovementModel.MOVEMENT_MODEL_NS + ".warmup";
	
	/** report class' package name */
	private static final String REPORT_PAC = "report.";

	/** The World where all actors of the simulator are */
	protected World world;
	/** Reports that are loaded for this simulation */
	protected Vector<Report> reports;
	/** has simulation terminated normally */
	protected boolean simDone;
	/** is simulation termination requested */
	protected boolean simCancelled;
	/** Scenario of the current simulation */
	protected SimScenario scen;
	/** simtime of last UI update */
	protected double lastUpdate;
	
	/**
	 * Constructor.
	 */
	public DTNSimUI() {
		this.lastUpdate = 0;
		this.reports = new Vector<Report>();
		this.simDone = false;
		this.simCancelled = false;
	}
	
	/**
	 * Starts the simulation.
	 */
	public void start() {
		initModel();// 创建仿真模型  //增加reports，设置仿真时间（减去warmupTime）
		runSim(); // 开始仿真 //抽象函数，定位到DTNSimTextUI.runSim()
	}
	
	/**
	 * Runs simulation after the model has been initialized.
	 */
	protected abstract void runSim();
	
	/**
	 * Initializes the simulator model.
	 */
	private void initModel() {
		Settings settings = null;
				
		try {
			settings = new Settings();   //建立一个新的配置对象
			this.scen = SimScenario.getInstance();// 创建场景，包括创建DTNHost //这里的this 指的是什么对象？？

			// add reports
			for (int i=1, n = settings.getInt(NROF_REPORT_S); i<=n; i++){// 建立report 输出参数的
				String reportClass = settings.getSetting(REPORT_S + i);// REPORT_S="Report.report,想象配置文件"
				addReport((Report)settings.createObject(REPORT_PAC +
						reportClass));	//REPORT_PAC="report"，包名前缀。
			}

			double warmupTime = 0; //热身时间
			if (settings.contains(MM_WARMUP_S)) {
				warmupTime = settings.getDouble(MM_WARMUP_S);
				if (warmupTime > 0) {
					/***    处理热身，既不把热身这段时间的仿真统计信息计算在内         ***/
					SimClock c = SimClock.getInstance();
					c.setTime(-warmupTime);
				}
			}
			//包含所有节点，更新节点的位置和连接状态
			this.world = this.scen.getWorld(); //得倒整个环境配置
			world.warmupMovementModel(warmupTime);// 运行暖身时间吧
		}
		catch (SettingsError se) {
			System.err.println("Can't start: error in configuration file(s)");
			System.err.println(se.getMessage());
			System.exit(-1);			
		}
		catch (SimError er) {
			System.err.println("Can't start: " + er.getMessage());
			System.err.println("Caught at " + er.getStackTrace()[0]);
			System.exit(-1);
		}		
	}
	
	/**
	 * Runs maintenance jobs that are needed before exiting.
	 */
	public void done() {
		for (Report r : this.reports) {
			r.done();
		}
	}
	
	/**
	 * Adds a new report for simulator
	 * @param r Report to add
	 */
	protected void addReport(Report r) {
		if (r instanceof MessageListener) {
			scen.addMessageListener((MessageListener)r);
		}
		if (r instanceof ConnectionListener) {
			scen.addConnectionListener((ConnectionListener)r);
		}
		if (r instanceof MovementListener) {
			scen.addMovementListener((MovementListener)r);
		}
		if (r instanceof UpdateListener) {
			scen.addUpdateListener((UpdateListener)r);
		}
		if (r instanceof ApplicationListener) {
			scen.addApplicationListener((ApplicationListener)r);
		}

		this.reports.add(r);
	}
}
