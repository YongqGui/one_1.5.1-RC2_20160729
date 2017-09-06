/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import java.util.*;

import movement.MovementModel;
import movement.Path;
import routing.MessageRouter;
import routing.util.RoutingInfo;

import java.util.Random;
/**
 * A DTN capable host.
 */
public class DTNHost implements Comparable<DTNHost> {
	private static int nextAddress = 0;
	private int address; // int型数据，？？/

	private Coord location; 	// where is the host 当前坐标
	private Coord destination;	// where is it going  目的地坐标

	private MessageRouter router;    // 路由模式
	private MovementModel movement;//运动模式
	private Path path;// 路径
	private double speed;// 速度（在我们的项目中没有用到）
	private double nextTimeToMove;  //  返回一个double型数据，猜测应该是链表索引
	private String name;   //标识
	private List<MessageListener> msgListeners;
	private List<MovementListener> movListeners;
	private List<NetworkInterface> net;
	private ModuleCommunicationBus comBus;
	
	/**------------------------------   对 DTNHost 添加的变量       --------------------------------*/

	private  double []parameters= new double[6]; //我们添加的参数	卫星参数
	/** file中具体携带的内容 */
	private HashMap<String,Integer> files;	
	/** 做一个FileBuffer 对数据进行存储 */
	private HashMap<String,file> FileBuffer;
	/** 做一个ChunkBuffer 对数据进行缓存 */
	private HashMap<String, HashMap<String,file>> ChunkBuffer = new HashMap<String, HashMap<String,file>>();
	
	/**------------------------------   对  DTNHost 添加的变量       --------------------------------*/
	

	static {
		DTNSim.registerForReset(DTNHost.class.getCanonicalName());
		reset();
	}
	/**
	 * Creates a new DTNHost.
	 * @param msgLs Message listeners
	 * @param movLs Movement listeners
	 * @param groupId GroupID of this host
	 * @param interf List of NetworkInterfaces for the class
	 * @param comBus Module communication bus object
	 * @param mmProto Prototype of the movement model of this host
	 * @param mRouterProto Prototype of the message router of this host
	 */
	public DTNHost(List<MessageListener> msgLs,
			List<MovementListener> movLs,
			String groupId, List<NetworkInterface> interf,
			ModuleCommunicationBus comBus, 
			MovementModel mmProto, MessageRouter mRouterProto) {
		this.comBus = comBus;
		this.location = new Coord(0,0);  // 确定初始坐标
		this.address = getNextAddress();  //
		this.name = groupId+address; // 名字构造为群ID 加上address？？
		this.net = new ArrayList<NetworkInterface>();
        //随机构造卫星6个参数
		Random random = new Random();
		//int s = random.nextInt(max)%(max-min+1) + min;
		this.parameters[0]= random.nextInt(9000)%(2000+1) + 7000;
		//this.parameters[0]=8000.0;
		this.parameters[1]=  0.1;
		this.parameters[2]= random.nextInt(90)%(90+1) ;
		//this.parameters[2]= 15.0;
		this.parameters[3]= 0.0;
		this.parameters[4]= 0.0;
		this.parameters[5]= 0.0;
		//this.parameters={ random.nextInt(9000)%(2000+1) + 7000,random.nextInt(9000)%(2000+1) + 7000,random.nextInt(9000)%(2000+1) + 7000   ,0.0,0.0,0.0};


		//随机构造卫星6个参数

		for (NetworkInterface i : interf) {// 将接口初始化到host里去   // 本句语法上是什么意思？
			NetworkInterface ni = i.replicate();  //将interf中的元素复制一份
			ni.setHost(this); //   ？？？？
			net.add(ni);   //net链表里添加
		}
		

		// TODO - think about the names of the interfaces and the nodes

		this.msgListeners = msgLs;
		this.movListeners = movLs;

		// create instances by replicating the prototypes
		this.movement = mmProto.replicate();
		this.movement.setComBus(comBus);
		this.movement.setHost(this); //  每个节点与一个运动模型链接在一起
		setRouter(mRouterProto.replicate());

		this.location = movement.getInitialLocation();  //得倒初始位置

		this.nextTimeToMove = movement.nextPathAvailable();
		this.path = null;

		if (movLs != null) { // inform movement listeners about the location
			for (MovementListener l : movLs) {
				l.initialLocation(this, this.location);
			}
		}
	}
	
	/**
	 * Returns a new network interface address and increments the address for
	 * subsequent calls.
	 * @return The next address.
	 */
	private synchronized static int getNextAddress() {
		return nextAddress++;	
	}

	/**
	 * Reset the host and its interfaces
	 */
	public static void reset() {
		nextAddress = 0;
	}

	/**
	 * Returns true if this node is actively moving (false if not)
	 * @return true if this node is actively moving (false if not)
	 */
	public boolean isMovementActive() {
		return this.movement.isActive();
	}
	
	/**
	 * Returns true if this node's radio is active (false if not)
	 * @return true if this node's radio is active (false if not)
	 */
	public boolean isRadioActive() {
		/* TODO: make this work for multiple interfaces */
		return this.getInterface(1).isActive();
	}

	/**
	 * Set a router for this host
	 * @param router The router to set
	 */
	private void setRouter(MessageRouter router) { // 语法上不明白
		router.init(this, msgListeners);
		this.router = router;
	}

	/**
	 * Returns the router of this host
	 * @return the router of this host
	 */
	public MessageRouter getRouter() {
		return this.router;
	}  //得到

	/**
	 * Returns the network-layer address of this host.
	 */
	public int getAddress() {
		return this.address;
	}
	
	/**
	 * Returns this hosts's ModuleCommunicationBus
	 * @return this hosts's ModuleCommunicationBus
	 */
	public ModuleCommunicationBus getComBus() {
		return this.comBus;
	}
	
    /**
	 * Informs the router of this host about state change in a connection
	 * object.
	 * @param con  The connection object whose state changed
	 */
	public void connectionUp(Connection con) {
		this.router.changedConnection(con);
	}

	public void connectionDown(Connection con) {
		this.router.changedConnection(con);
	}

	/**
	 * Returns a copy of the list of connections this host has with other hosts
	 * @return a copy of the list of connections this host has with other hosts
	 */
	public List<Connection> getConnections() {
		List<Connection> lc = new ArrayList<Connection>();

		for (NetworkInterface i : net) {
			lc.addAll(i.getConnections());
		}

		return lc;
	}

	/**
	 * Returns the current location of this host. 
	 * @return The location
	 */
	public Coord getLocation() {
		return this.location;
	}

	/**
	 * Returns the Path this node is currently traveling or null if no
	 * path is in use at the moment.
	 * @return The path this node is traveling
	 */
	public Path getPath() {
		return this.path;
	}

	/**
	 * Sets the Node's location overriding any location set by movement model
	 * @param location The location to set
	 */
	public void setLocation(Coord location) {
		this.location = location.clone();
	}

	/**
	 * Sets the Node's name overriding the default name (groupId + netAddress)
	 * @param name The name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the messages in a collection.
	 * @return Messages in a collection
	 */
	public Collection<Message> getMessageCollection() {
		return this.router.getMessageCollection();
	}
	
	/**
	 * Returns the number of messages this node is carrying.
	 * @return How many messages the node is carrying currently.
	 */
	public int getNrofMessages() {
		return this.router.getNrofMessages();
	}

	/**
	 * Returns the buffer occupancy percentage. Occupancy is 0 for empty
	 * buffer but can be over 100 if a created message is bigger than buffer 
	 * space that could be freed.
	 * @return Buffer occupancy percentage
	 */
	public double getBufferOccupancy() {
		double bSize = router.getBufferSize();
		double freeBuffer = router.getFreeBufferSize();
		return 100*((bSize-freeBuffer)/bSize);
	}

	/**
	 * Returns routing info of this host's router.
	 * @return The routing info.
	 */
	public RoutingInfo getRoutingInfo() {
		return this.router.getRoutingInfo();
	}

	/**
	 * Returns the interface objects of the node
	 */
	public List<NetworkInterface> getInterfaces() {
		return net;
	}

	/**
	 * Find the network interface based on the index
	 */
	public NetworkInterface getInterface(int interfaceNo) {
		NetworkInterface ni = null;
		try {
			ni = net.get(interfaceNo-1);
		} catch (IndexOutOfBoundsException ex) {
			throw new SimError("No such interface: "+interfaceNo + 
					" at " + this);
		}
		return ni;
	}

	/**
	 * Find the network interface based on the interfacetype
	 */
	protected NetworkInterface getInterface(String interfacetype) {
		for (NetworkInterface ni : net) {
			if (ni.getInterfaceType().equals(interfacetype)) {
				return ni;
			}
		}
		return null;	
	}

	/**
	 * Force a connection event
	 */
	public void forceConnection(DTNHost anotherHost, String interfaceId, 
			boolean up) {
		NetworkInterface ni;
		NetworkInterface no;

		if (interfaceId != null) {
			ni = getInterface(interfaceId);
			no = anotherHost.getInterface(interfaceId);

			assert (ni != null) : "Tried to use a nonexisting interfacetype "+interfaceId;
			assert (no != null) : "Tried to use a nonexisting interfacetype "+interfaceId;
		} else {
			ni = getInterface(1);
			no = anotherHost.getInterface(1);
			
			assert (ni.getInterfaceType().equals(no.getInterfaceType())) : 
				"Interface types do not match.  Please specify interface type explicitly";
		}
		
		if (up) {
			ni.createConnection(no);
		} else {
			ni.destroyConnection(no);
		}
	}

	/**
	 * for tests only --- do not use!!!
	 */
	public void connect(DTNHost h) {
		System.err.println(
				"WARNING: using deprecated DTNHost.connect(DTNHost)" +
		"\n Use DTNHost.forceConnection(DTNHost,null,true) instead");
		forceConnection(h,null,true);
	}

	/**
	 * Updates node's network layer and router.
	 * @param simulateConnections Should network layer be updated too
	 */
	public void update(boolean simulateConnections) {
		if (!isRadioActive()) {
			// Make sure inactive nodes don't have connections
			tearDownAllConnections();
			return;
		}
		
		if (simulateConnections) {
			for (NetworkInterface i : net) {
				i.update();
			}
		}
		this.router.update();
	}
	
	/** 
	 * Tears down all connections for this host.
	 */
	private void tearDownAllConnections() {
		for (NetworkInterface i : net) {
			// Get all connections for the interface
			List<Connection> conns = i.getConnections();
			if (conns.size() == 0) continue;
			
			// Destroy all connections
			List<NetworkInterface> removeList =
				new ArrayList<NetworkInterface>(conns.size());
			for (Connection con : conns) {
				removeList.add(con.getOtherInterface(i));
			}
			for (NetworkInterface inf : removeList) {
				i.destroyConnection(inf);
			}
		}
	}

	/**
	 * Moves the node towards the next waypoint or waits if it is
	 * not time to move yet
	 * @param timeIncrement How long time the node moves
	 */
	public void move(double timeIncrement) {		
		double possibleMovement;//可能的位置
		double distance;// 距离
		double dx, dy; //位移偏移量

		if (!isMovementActive() || SimClock.getTime() < this.nextTimeToMove) {
			return; 
		}
		if (this.destination == null) {
			if (!setNextWaypoint()) {
				return;
			}
		}

		possibleMovement = timeIncrement * speed;
		distance = this.location.distance(this.destination);

		while (possibleMovement >= distance) {
			// node can move past its next destination   节点越过目的节点？？？
			this.location.setLocation(this.destination); // snap to destination 越过目标节点
			possibleMovement -= distance;
			if (!setNextWaypoint()) { // get a new waypoint
				return; // no more waypoints left
			}
			distance = this.location.distance(this.destination);
		}

		// move towards the point for possibleMovement amount
		dx = (possibleMovement/distance) * (this.destination.getX() -
				this.location.getX());
		dy = (possibleMovement/distance) * (this.destination.getY() -
				this.location.getY());
		this.location.translate(dx, dy); //此处修改 注释
		//*********************扩展函数
	//	double[] t = new double[]{8000,0.1,15,0.0,0.0,0.0};
	//	this.location.my_Test(timeIncrement+SimClock.getTime(),timeIncrement,this.parameters);
	}	

	/**
	 * Sets the next destination and speed to correspond the next waypoint
	 * on the path.
	 * @return True if there was a next waypoint to set, false if node still
	 * should wait
	 */
	private boolean setNextWaypoint() {
		if (path == null) {
			path = movement.getPath();
		}

		if (path == null || !path.hasNext()) {
			this.nextTimeToMove = movement.nextPathAvailable();
			this.path = null;
			return false;
		}

		this.destination = path.getNextWaypoint();
		this.speed = path.getSpeed();

		if (this.movListeners != null) {
			for (MovementListener l : this.movListeners) {
				l.newDestination(this, this.destination, this.speed);
			}
		}

		return true;
	}

	/**
	 * Sends a message from this host to another host
	 * @param id Identifier of the message
	 * @param to Host the message should be sent to
	 */
	public void sendMessage(String id, DTNHost to) {
		this.router.sendMessage(id, to);
	}

	/**
	 * Start receiving a message from another host
	 * @param m The message
	 * @param from Who the message is from
	 * @return The value returned by 
	 * {@link MessageRouter#receiveMessage(Message, DTNHost)}
	 */
	public int receiveMessage(Message m, DTNHost from) {//消息接收	
		int retVal = this.router.receiveMessage(m, from); 
		if (retVal == MessageRouter.RCV_OK) {
			m.addNodeOnPath(this);	// add this node on the messages path
		}
		return retVal;
	}

	/**
	 * Requests for deliverable message from this host to be sent trough a
	 * connection.
	 * @param con The connection to send the messages trough
	 * @return True if this host started a transfer, false if not
	 */
	public boolean requestDeliverableMessages(Connection con) {
		return this.router.requestDeliverableMessages(con);
	}

	/**
	 * Informs the host that a message was successfully transferred.
	 * @param id Identifier of the message
	 * @param from From who the message was from
	 */
	public void messageTransferred(String id, DTNHost from) {
		this.router.messageTransferred(id, from);
	}

	/**
	 * Informs the host that a message transfer was aborted.
	 * @param id Identifier of the message
	 * @param from From who the message was from
	 * @param bytesRemaining Nrof bytes that were left before the transfer
	 * would have been ready; or -1 if the number of bytes is not known
	 */
	public void messageAborted(String id, DTNHost from, int bytesRemaining) {
		this.router.messageAborted(id, from, bytesRemaining);
	}

	/**
	 * Creates a new message to this host's router
	 * @param m The message to create
	 */
	public void createNewMessage(Message m) {
		//System.out.print( " 文件名是：： "+m.getFilename()+"\n");
		this.router.createNewMessage(m);
	}

	/**
	 * Deletes a message from this host
	 * @param id Identifier of the message
	 * @param drop True if the message is deleted because of "dropping"
	 * (e.g. buffer is full) or false if it was deleted for some other reason
	 * (e.g. the message got delivered to final destination). This effects the
	 * way the removing is reported to the message listeners.
	 */
	public void deleteMessage(String id, boolean drop) {
		this.router.deleteMessage(id, drop);
	}

	/**
	 * Returns a string presentation of the host.
	 * @return Host's name
	 */
	public String toString() {
		return name;
	}

	/**
	 * Checks if a host is the same as this host by comparing the object
	 * reference
	 * @param otherHost The other host
	 * @return True if the hosts objects are the same object
	 */
	public boolean equals(DTNHost otherHost) {
		return this == otherHost;
	}

	/**
	 * Compares two DTNHosts by their addresses.
	 * @see Comparable#compareTo(Object)
	 */
	public int compareTo(DTNHost h) {
		return this.getAddress() - h.getAddress();
	}	
	
	/**------------------------------   对  DTNHost 添加的函数方法       --------------------------------*/	
	
	/** 获取DTNHost中的chunkBuffer*/
	public HashMap<String, HashMap<String,file>> getChunkBuffer() {
		return ChunkBuffer;
	}
	
	/** 对定义的表进行处理*/
	public void setFiles(HashMap<String, Integer> files) {
		this.files = files;
	}
	/** 获取节点中存放的关于file的表*/
	public HashMap<String, Integer> getFiles() {
		return files;
	}

	/** 对定义的缓存区进行处理*/
	public void setFileBuffer(HashMap<String, file> FileBuffer) {
		this.FileBuffer = FileBuffer;
	}
	/** 获取文件的缓存*/
	public HashMap<String, file> getFileBuffer() {
		return FileBuffer;
	}
	
	/** 看缓存FileBuffer中有没有文件  */
	public file getFileBufferForFile(Message aMessage) {

		if (this.FileBuffer.containsKey(aMessage.getFilename()))
			return this.FileBuffer.get(aMessage.getFilename());
		else
			return null;
		//return this.FileBuffer.get(aMessage.getFilename());
	}
	/** gyq_test 2016/07/08     用于得到当前节点缓存的剩余空间  */
	public int getFreeFileBufferSize(){
		int occupancy = 0;		
		
		//Settings s = new Settings("Group");
		//int bufferSize = s.getInt("filebuffersize");
		//System.out.println(bufferSize);
		if (this.router.getBufferSize() == Integer.MAX_VALUE) {
		//if (bufferSize == Integer.MAX_VALUE){
			return Integer.MAX_VALUE;
		}
		for (file File: getFileCollection()){
			occupancy += File.getSize();
		}
		// 这里FileBufferSize与Message的BufferSize一样大小     //修改之后的大小不再一样。
		return this.router.getFileBufferSize() - occupancy;
	}
	
	/** 得到的是存放文件的fileBuffersize  */
	public int getFileBufferSize(){
		return this.router.getFileBufferSize();
	}
	
	/** gyq_test 2016/07/08   用于删除缓存中被请求时间最久的文件  */
	public boolean makeRoomForNewFile(int size){
		if (size > this.router.getFileBufferSize()) {
			return false; 										// message too big for the buffer
		}
		int freeBuffer = this.getFreeFileBufferSize();
		/* delete messages from the buffer until there's enough space */
		while (freeBuffer < size) {			
			file File = getNextFileToRemove(true);
			if (File == null) {
				return false; 									// couldn't remove any more messages
			}			
			/* delete message from the buffer as "drop" */
			deleteFile(File.getId(), true);
			freeBuffer += File.getSize();
		}
		return true;
	}
	
	/** gyq_test 2016/07/08    找到被请求时间最长的文件，作为下一个待移除文件   */
	protected file getNextFileToRemove(boolean excludeMsgBeingSent){
		Collection<file> filebuffer = this.getFileCollection();
		file oldest = null;
		for (file f: filebuffer){
			//判断当前文件是否正在被传输？？？
			//if (excludeMsgBeingSent && isSending(f.getId())) {
			//	continue; // skip the message(s) that router is sending
			//}
			
			if(!f.getInitFile()){          // 如果不是初始化放入的文件，继续执行
				if (oldest == null ) {
					oldest = f;
				}
				else if (oldest.getTimeRequest()> f.getTimeRequest()) {
					oldest = f;
				}
			}
		}
		return oldest;
	}
	
	/**  gyq_test 2016/07/08    删除节点buffer中文件    */
	public void deleteFile(String id, boolean drop) {
		file removed = removeFromFileBuffer(id);
		if (removed == null) throw new SimError("no file for id " +
				id + " to remove at " + this.getAddress());
		
		//for (MessageListener ml : this.mListeners) {
		//ml.messageDeleted(removed, this.getAddress(), drop);
		//}														                      // 少了时间监听器
	}
	
	/** gyq_test 2016/07/08    用于从当前节点缓存空间中删除文件   */
	public file removeFromFileBuffer(String id){
		file f= this.FileBuffer.remove(id);
		return f;
	}
	
	/**　放当前消息进入待确认消息列表里  */
	public void putIntoJudgeForRetransfer(Message m){
		this.router.putJudgeForRetransfer(m);
	}
	
	/** gyq_test 2016/07/08      用于得到当前节点的fileCollection */
	public Collection<file> getFileCollection(){
		return this.FileBuffer.values();
	}
	
	/**------------------------------   对  DTNHost 添加的函数方法       --------------------------------*/	
}
