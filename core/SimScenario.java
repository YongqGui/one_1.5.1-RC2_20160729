/* 
 * Copyright 2011 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import input.EventQueue;
import input.EventQueueHandler;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import movement.MapBasedMovement;
import movement.MovementModel;
import movement.map.SimMap;
import routing.MessageRouter;
import core.DTNHost;

/**
 * A simulation scenario used for getting and storing the settings of a
 * simulation run.
 */
public class SimScenario implements Serializable {
	
	/** a way to get a hold of this... */	
	private static SimScenario myinstance=null;

	/** namespace of scenario settings ({@value})*/
	public static final String SCENARIO_NS = "Scenario";
	/** number of host groups -setting id ({@value})*/
	public static final String NROF_GROUPS_S = "nrofHostGroups";
	/** number of interface types -setting id ({@value})*/
	public static final String NROF_INTTYPES_S = "nrofInterfaceTypes";
	/** scenario name -setting id ({@value})*/
	public static final String NAME_S = "name";
	/** end time -setting id ({@value})*/
	public static final String END_TIME_S = "endTime";
	/** update interval -setting id ({@value})*/
	public static final String UP_INT_S = "updateInterval";
	/** simulate connections -setting id ({@value})*/
	public static final String SIM_CON_S = "simulateConnections";

	/** namespace for interface type settings ({@value}) */
	public static final String INTTYPE_NS = "Interface";
	/** interface type -setting id ({@value}) */
	public static final String INTTYPE_S = "type";
	/** interface name -setting id ({@value}) */
	public static final String INTNAME_S = "name";

	/** namespace for application type settings ({@value}) */
	public static final String APPTYPE_NS = "Application";
	/** application type -setting id ({@value}) */
	public static final String APPTYPE_S = "type";
	/** setting name for the number of applications */
	public static final String APPCOUNT_S = "nrofApplications";
	
	/** namespace for host group settings ({@value})*/
	public static final String GROUP_NS = "Group";
	/** group id -setting id ({@value})*/
	public static final String GROUP_ID_S = "groupID";
	/** number of hosts in the group -setting id ({@value})*/
	public static final String NROF_HOSTS_S = "nrofHosts";
	/** movement model class -setting id ({@value})*/
	public static final String MOVEMENT_MODEL_S = "movementModel";
	/** router class -setting id ({@value})*/
	public static final String ROUTER_S = "router";
	/** number of interfaces in the group -setting id ({@value})*/
	public static final String NROF_INTERF_S = "nrofInterfaces";
	/** interface name in the group -setting id ({@value})*/
	public static final String INTERFACENAME_S = "interface";
	/** application name in the group -setting id ({@value})*/
	public static final String GAPPNAME_S = "application";

	/** package where to look for movement models */
	private static final String MM_PACKAGE = "movement.";
	/** package where to look for router classes */
	private static final String ROUTING_PACKAGE = "routing.";

	/** package where to look for interface classes */
	private static final String INTTYPE_PACKAGE = "interfaces.";

	/** package where to look for application classes */
	private static final String APP_PACKAGE = "applications.";
	
	/** The world instance */
	private World world;
	/** List of hosts in this simulation */
	protected List<DTNHost> hosts;  		//节点链表
	/** Name of the simulation */
	private String name;
	/** number of host groups */
	int nrofGroups;  						//主机群组数
	/** Width of the world */
	private int worldSizeX;  				//世界尺寸
	/** Height of the world */
	private int worldSizeY;
	/** Largest host's radio range */
	private double maxHostRange; 			//最大传输间隔
	/** Simulation end time */
	private double endTime; 				//结束时间
	/** Update interval of sim time */
	private double updateInterval; 			//更新间隔
	/** External events queue */
	private EventQueueHandler eqHandler;	//SimScenario.java 私有成员变量  //事件产生
	/** Should connections between hosts be simulated */
	private boolean simulateConnections;
	/** Map used for host movement (if any) */
	private SimMap simMap; 					//重点 仿真地图

	/** Global connection event listeners */
	private List<ConnectionListener> connectionListeners;
	/** Global message event listeners */
	private List<MessageListener> messageListeners;
	/** Global movement event listeners */
	private List<MovementListener> movementListeners;
	/** Global update event listeners */
	private List<UpdateListener> updateListeners;
	/** Global application event listeners */
	private List<ApplicationListener> appListeners;
	/** 文件列表（test）*/
	private HashMap<String,Integer> FileHosts;
	/** 缓存空间  */
	private HashMap<String,file> FileBuffer;
	
	
	static {
		DTNSim.registerForReset(SimScenario.class.getCanonicalName());
		reset();
	}
	
	public static void reset() {
		myinstance = null;
	}

	/**
	 * Creates a scenario based on Settings object.
	 */
	protected SimScenario() {
		Settings s = new Settings(SCENARIO_NS);
		nrofGroups = s.getInt(NROF_GROUPS_S);						//得到集群数
		
		this.name = s.valueFillString(s.getSetting(NAME_S));
		this.endTime = s.getDouble(END_TIME_S); 					//确定结束时间
		this.updateInterval = s.getDouble(UP_INT_S);				//得倒仿真间隔
		this.simulateConnections = s.getBoolean(SIM_CON_S);			//是否连接显示

		s.ensurePositiveValue(nrofGroups, NROF_GROUPS_S);
		s.ensurePositiveValue(endTime, END_TIME_S);
		s.ensurePositiveValue(updateInterval, UP_INT_S);

		this.simMap = null;											//地图为空
		this.maxHostRange = 1;

		this.connectionListeners = new ArrayList<ConnectionListener>();
		this.messageListeners = new ArrayList<MessageListener>();
		this.movementListeners = new ArrayList<MovementListener>();
		this.updateListeners = new ArrayList<UpdateListener>();
		this.appListeners = new ArrayList<ApplicationListener>();
		this.eqHandler = new EventQueueHandler();					//创建EventQueueHandler //事件生成

		/* TODO: check size from movement models */
		s.setNameSpace(MovementModel.MOVEMENT_MODEL_NS);
		int [] worldSize = s.getCsvInts(MovementModel.WORLD_SIZE, 2);
		this.worldSizeX = worldSize[0];
		this.worldSizeY = worldSize[1];
		
		createHosts();// 生成节点
		
		this.world = new World(hosts, worldSizeX, worldSizeY, updateInterval, 
				updateListeners, simulateConnections, 
				eqHandler.getEventQueues());						//eqHandler.getEventQueues()
	}
	
	/**
	 * Returns the SimScenario instance and creates one if it doesn't exist yet
	 */
	public static SimScenario getInstance() {
		if (myinstance == null) {
			myinstance = new SimScenario();
		}
		return myinstance;
	}



	/**
	 * Returns the name of the simulation run
	 * @return the name of the simulation run
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Returns true if connections should be simulated
	 * @return true if connections should be simulated (false if not)
	 */
	public boolean simulateConnections() {
		return this.simulateConnections;
	}

	/**
	 * Returns the width of the world
	 * @return the width of the world
	 */
	public int getWorldSizeX() {
		return this.worldSizeX;
	}

	/**
	 * Returns the height of the world
	 * @return the height of the world
	 */
	public int getWorldSizeY() {
		return worldSizeY;
	}

	/**
	 * Returns simulation's end time
	 * @return simulation's end time
	 */
	public double getEndTime() {
		return endTime;
	}

	/**
	 * Returns update interval (simulated seconds) of the simulation
	 * @return update interval (simulated seconds) of the simulation
	 */
	public double getUpdateInterval() {
		return updateInterval;
	}

	/**
	 * Returns how long range the hosts' radios have
	 * @return Range in meters
	 */
	public double getMaxHostRange() {
		return maxHostRange;
	}

	/**
	 * Returns the (external) event queue(s) of this scenario or null if there 
	 * aren't any
	 * @return External event queues in a list or null
	 */
	public List<EventQueue> getExternalEvents() {//返回EventQueue接口类型的列表
		return this.eqHandler.getEventQueues();
	}

	/**
	 * Returns the SimMap this scenario uses, or null if scenario doesn't
	 * use any map
	 * @return SimMap or null if no map is used
	 */
	public SimMap getMap() {
		return this.simMap;
	}

	/**
	 * Adds a new connection listener for all nodes
	 * @param cl The listener
	 */
	public void addConnectionListener(ConnectionListener cl){
		this.connectionListeners.add(cl);
	}

	/**
	 * Adds a new message listener for all nodes
	 * @param ml The listener
	 */
	public void addMessageListener(MessageListener ml){
		this.messageListeners.add(ml);
	}

	/**
	 * Adds a new movement listener for all nodes
	 * @param ml The listener
	 */
	public void addMovementListener(MovementListener ml){
		this.movementListeners.add(ml);
	}

	/**
	 * Adds a new update listener for the world
	 * @param ul The listener
	 */
	public void addUpdateListener(UpdateListener ul) {
		this.updateListeners.add(ul);
	}

	/**
	 * Returns the list of registered update listeners
	 * @return the list of registered update listeners
	 */
	public List<UpdateListener> getUpdateListeners() {
		return this.updateListeners;
	}

	/** 
	 * Adds a new application event listener for all nodes.
	 * @param al The listener
	 */
	public void addApplicationListener(ApplicationListener al) {
		this.appListeners.add(al);
	}
	
	/**
	 * Returns the list of registered application event listeners
	 * @return the list of registered application event listeners
	 */
	public List<ApplicationListener> getApplicationListeners() {
		return this.appListeners;
	}
	
	/**
	 * Creates hosts for the scenario
	 */
	protected void createHosts() {
		this.hosts = new ArrayList<DTNHost>();//创建一个新的节点链表
		
		for (int i=1; i<=nrofGroups; i++) {//按主机组数
			List<NetworkInterface> interfaces = 
				new ArrayList<NetworkInterface>();
			Settings s = new Settings(GROUP_NS+i);//每一个主机组有一个配置对象，具体的可能和命名空间有关
			s.setSecondaryNamespace(GROUP_NS);
			String gid = s.getSetting(GROUP_ID_S);
			int nrofHosts = s.getInt(NROF_HOSTS_S);
			int nrofInterfaces = s.getInt(NROF_INTERF_S);
			int appCount;

			// creates prototypes of MessageRouter and MovementModel
			MovementModel mmProto = 
				(MovementModel)s.createIntializedObject(MM_PACKAGE + 
						s.getSetting(MOVEMENT_MODEL_S));
			MessageRouter mRouterProto = 
				(MessageRouter)s.createIntializedObject(ROUTING_PACKAGE + 
						s.getSetting(ROUTER_S));
			
			/* checks that these values are positive (throws Error if not) */
			s.ensurePositiveValue(nrofHosts, NROF_HOSTS_S);
			s.ensurePositiveValue(nrofInterfaces, NROF_INTERF_S);

			// setup interfaces
			for (int j=1;j<=nrofInterfaces;j++) {// 接口数
				String intName = s.getSetting(INTERFACENAME_S + j);
				Settings intSettings = new Settings(intName); 
				NetworkInterface iface = 
					(NetworkInterface)intSettings.createIntializedObject(
							INTTYPE_PACKAGE +intSettings.getSetting(INTTYPE_S));
				iface.setClisteners(connectionListeners);
				iface.setGroupSettings(s);
				interfaces.add(iface);
			}

			// setup applications
			if (s.contains(APPCOUNT_S)) {
				appCount = s.getInt(APPCOUNT_S);
			} else {
				appCount = 0;
			}
			for (int j=1; j<=appCount; j++) {
				String appname = null;
				Application protoApp = null;
				try {
					// Get name of the application for this group
					appname = s.getSetting(GAPPNAME_S+j);
					// Get settings for the given application
					Settings t = new Settings(appname);
					// Load an instance of the application
					protoApp = (Application)t.createIntializedObject(
							APP_PACKAGE + t.getSetting(APPTYPE_S));
					// Set application listeners
					protoApp.setAppListeners(this.appListeners);
					// Set the proto application in proto router
					//mRouterProto.setApplication(protoApp);
					mRouterProto.addApplication(protoApp);
				} catch (SettingsError se) {
					// Failed to create an application for this group
					System.err.println("Failed to setup an application: " + se);
					System.err.println("Caught at " + se.getStackTrace()[0]);
					System.exit(-1);
				}
			}

			if (mmProto instanceof MapBasedMovement) {//如果是MapBasedMovement 模型就得倒其地图
				this.simMap = ((MapBasedMovement)mmProto).getMap();
			}

			// creates hosts of ith group
			for (int j=0; j<nrofHosts; j++) {//建立节点
				ModuleCommunicationBus comBus = new ModuleCommunicationBus();

				// prototypes are given to new DTNHost which replicates
				// new instances of movement model and message router
				FileBuffer =new HashMap<String,file>(); // 定义每个节点的缓存区
				DTNHost host = new DTNHost(this.messageListeners, 
						this.movementListeners,	gid, interfaces, comBus, 
						mmProto, mRouterProto);
				host.setFileBuffer(FileBuffer);
				hosts.add(host);
				/** 如果在这里对初始的表进行遍历的话 效率感觉就太低了。。。或者在后面能够单独对某一个节点进行操作     ????????   */	
			}
		}
		
		//我们添加的文件列表部分
		Settings ss = new Settings(GROUP_NS+1);//每一个主机组有一个配置对象，具体的可能和命名空间有关
		ss.setSecondaryNamespace(GROUP_NS);
		int num = ss.getInt(NROF_HOSTS_S);
		this.FileHosts =new HashMap<String,Integer>();				//（添加）一个文件列表
		this.FileBuffer =new HashMap<String,file>(); 				// 定义每个节点的缓存区
		for(int ii=0; ii<20;ii++){
			//file ee= new  file(ii, num*nrofGroups);
			/** 这里先得到随机的host */
			file ee= new  file(ii, num*nrofGroups);      			//System.out.print(ee.getFromAddressID()+ "\n");
			this.FileHosts.put(ee.getId(),ee.getFromAddressID());
			
			//往对应节点的缓存空间中放入文件
			for(int i=0; i<num*nrofGroups; i++){
				if(i==ee.getFromAddressID()){
					this.hosts.get(i).getFileBuffer().put(ee.getId(),ee);
					//break;
				}
			}
		}	
		//还要对每个节点都放入全局的文件表，测试得到初始化后每个节点存在这张表
		for(int j=0; j<num*nrofGroups; j++){
			this.hosts.get(j).setFiles(this.FileHosts);         	//对每个节点设置总体的这张表
		}
		//test  证明初始化后缓存中确实加入了文件
		/**for(int j=0; j<num*nrofGroups; j++){
			System.out.print(this.hosts.get(j).getFileBuffer()+ "\n");
		}*/
	}

	
	/**
	 * Returns the list of nodes for this scenario.
	 * @return the list of nodes for this scenario.
	 */
	public List<DTNHost> getHosts() {
		return this.hosts;
	}
	
	/**
	 * Returns the World object of this scenario
	 * @return the World object
	 */
	public World getWorld() {
		return this.world;//得到整个world的所有参数
	}

}