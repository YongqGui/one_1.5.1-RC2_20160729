/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import routing.util.RoutingInfo;
import util.Tuple;
import core.Application;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.SettingsError;
import core.SimClock;
import core.SimError;
import core.file;
/**
 * Superclass for message routers.
 */
public abstract class MessageRouter {
	/** Message buffer size -setting id ({@value}). Integer value in bytes.*/
	public static final String B_SIZE_S = "bufferSize";
	/**
	 * Message TTL -setting id ({@value}). Value is in minutes and must be
	 * an integer. 
	 */ 
	public static final String MSG_TTL_S = "msgTtl";
	/**
	 * Message/fragment sending queue type -setting id ({@value}). 
	 * This setting affects the order the messages and fragments are sent if the
	 * routing protocol doesn't define any particular order (e.g, if more than 
	 * one message can be sent directly to the final recipient). 
	 * Valid values are<BR>
	 * <UL>
	 * <LI/> 1 : random (message order is randomized every time; default option)
	 * <LI/> 2 : FIFO (most recently received messages are sent last)
	 * </UL>
	 */ 
	public static final String SEND_QUEUE_MODE_S = "sendQueue";
	/** Setting value for random queue mode */
	public static final int Q_MODE_RANDOM = 1;
	/** Setting value for FIFO queue mode */
	public static final int Q_MODE_FIFO = 2;
	
	/**
	 *  Return values when asking to start a transmission:
	 * RCV_OK (0) means that the host accepts the message and transfer started, 
	 * values < 0 mean that the  receiving host will not accept this 
	 * particular message (right now), 
	 * values > 0 mean the host will not right now accept any message. 
	 * Values in the range [-100, 100] are reserved for general return values
	 * (and specified here), values beyond that are free for use in 
	 * implementation specific cases */
	
	/** Receive return value for OK */
	public static final int RCV_OK = 0;
	/** Receive return value for busy receiver */
	public static final int TRY_LATER_BUSY = 1;
	/** Receive return value for an old (already received) message */
	public static final int DENIED_OLD = -1;
	/** Receive return value for not enough space in the buffer for the msg */
	public static final int DENIED_NO_SPACE = -2;
	/** Receive return value for messages whose TTL has expired */
	public static final int DENIED_TTL = -3;
	/** Receive return value for a node low on some resource(s) */
	public static final int DENIED_LOW_RESOURCES = -4;
	/** Receive return value for a node low on some resource(s) */
	public static final int DENIED_POLICY = -5;
	/** Receive return value for unspecified reason */
	public static final int DENIED_UNSPECIFIED = -99;
	
	protected List<MessageListener> mListeners;
	/** The messages being transferred with msgID_hostName keys */
	private HashMap<String, Message> incomingMessages;
	/** The messages this router is carrying */
	private HashMap<String, Message> messages;
	/** The messages this router has received as the final recipient */
	protected HashMap<String, Message> deliveredMessages; //已传
	/** The messages that Applications on this router have blacklisted */
	protected HashMap<String, Object> blacklistedMessages;
	/** Host where this router belongs to */
	private DTNHost host;
	/** size of the buffer */
	private int bufferSize;
	/** 文件缓存大小*/
	private int filebuffersize;
	/** TTL for all messages */
	protected int msgTtl;
	/** Queue mode for sending messages */
	private int sendQueueMode;
	/** applications attached to the host */
	private HashMap<String, Collection<Application>> applications = null;
	
	/**------------------------------   对MessageRouter添加的变量       --------------------------------*/
	
	/** bitMap用于对chunkID进行映射    */
	private ArrayList<Integer> bitMap = new ArrayList<Integer>();
	/**定义一个临时的队列，用于对中继节点得到chunk传输消息存储 */
	protected Queue<Message> tempQueue = new LinkedList<Message>();
	/** 需要定义多维的链表形式，来对数据进行存储 */
	protected HashMap<String,HashMap<String,Message>> MessageHashMap = new HashMap<String,HashMap<String,Message>>();
	/** 新的文件缓存,仿写messages*/
	private HashMap<String, Message> myMessages;
	/** 用于判断文件是否得到确认，从而决定是否需要重传  */
	private HashMap<String, ArrayList<Object>> judgeForRetransfer 
						= new HashMap<String, ArrayList<Object>>();	
	/** 用于判断重传时间，这里设定为100s */
	protected double time_out = 20;
	/** 用于判断重传次数，初始为0，设定最多重传3次*/
	protected int reTransTimes = 3;
	/**　用于ack包的time_wait时间 */
	protected double time_wait = 40;
	/** 用于应答包的等待时间 time_free */
	protected double time_free = 3.5*time_out;
	/** 响应消息前缀 */
	public static final String RESPONSE_PREFIX = "R_";
	/** 用于判断包的类型 */
	public static final String SelectLabel = "SelectLabel";
	/** 新建一个文件buffer */
	public static final String F_SIZE_S = "filebuffersize";
	
	/** ------------------------------   对MessageRouter添加的变量       --------------------------------*/
	
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object. Size of the message buffer is read from
	 * {@link #B_SIZE_S} setting. Default value is Integer.MAX_VALUE.
	 * @param s The settings object
	 */
	public MessageRouter(Settings s) {
		this.bufferSize = Integer.MAX_VALUE; // defaults to rather large buffer	
		this.filebuffersize = Integer.MAX_VALUE; 	// 对文件缓存大小 仿写
		
		this.msgTtl = Message.INFINITE_TTL;
		this.applications = new HashMap<String, Collection<Application>>();

		if (s.contains(B_SIZE_S)) {
			this.bufferSize = s.getInt(B_SIZE_S);
		}
		if (s.contains(F_SIZE_S)) {
			this.filebuffersize = s.getInt(F_SIZE_S);
		}
		if (s.contains(MSG_TTL_S)) {
			this.msgTtl = s.getInt(MSG_TTL_S);
		}
		if (s.contains(SEND_QUEUE_MODE_S)) {
			this.sendQueueMode = s.getInt(SEND_QUEUE_MODE_S);
			if (sendQueueMode < 1 || sendQueueMode > 2) {
				throw new SettingsError("Invalid value for " + 
						s.getFullPropertyName(SEND_QUEUE_MODE_S));
			}
		}
		else {
			sendQueueMode = Q_MODE_RANDOM;
		}
		
	}
	
	/**
	 * Initializes the router; i.e. sets the host this router is in and
	 * message listeners that need to be informed about message related
	 * events etc.
	 * @param host The host this router is in
	 * @param mListeners The message listeners
	 */
	public void init(DTNHost host, List<MessageListener> mListeners) {
		this.incomingMessages = new HashMap<String, Message>();
		this.messages = new HashMap<String, Message>();
		this.myMessages = new HashMap<String, Message>();
		this.deliveredMessages = new HashMap<String, Message>();
		this.blacklistedMessages = new HashMap<String, Object>();
		this.mListeners = mListeners;
		this.host = host;
	}
	
	/**
	 * Copy-constructor.
	 * @param r Router to copy the settings from.
	 */
	protected MessageRouter(MessageRouter r) {
		this.bufferSize = r.bufferSize;
		this.filebuffersize = r.filebuffersize;  // 仿写文件缓存大小
		this.msgTtl = r.msgTtl;
		this.sendQueueMode = r.sendQueueMode;

		this.applications = new HashMap<String, Collection<Application>>();
		for (Collection<Application> apps : r.applications.values()) {
			for (Application app : apps) {
				addApplication(app.replicate());
			}
		}
	}
	

	/**
	 * Informs the router about change in connections state.
	 * @param con The connection that changed
	 */
	public abstract void changedConnection(Connection con);	
	
	/**
	 * Returns a message by ID.
	 * @param id ID of the message
	 * @return The message
	 */
	protected Message getMessage(String id) {
		return this.messages.get(id);
	}
	
	
	/**
	 * Checks if this router has a message with certain id buffered.
	 * @param id Identifier of the message
	 * @return True if the router has message with this id, false if not
	 */
	public boolean hasMessage(String id) {//缂撳瓨涓槸鍚︽湁璇ユ秷鎭�
		return this.messages.containsKey(id);
	}

	/**
	 * Returns true if a full message with same ID as the given message has been
	 * received by this host as the <strong>final</strong> recipient 
	 * (at least once).
	 * @param m message we're interested of
	 * @return true if a message with the same ID has been received by 
	 * this host as the final recipient.
	 */
	protected boolean isDeliveredMessage(Message m) {
		return (this.deliveredMessages.containsKey(m.getId()));
	}
	
	/** 
	 * Returns <code>true</code> if the message has been blacklisted. Messages
	 * get blacklisted when an application running on the node wants to drop it.
	 * This ensures the peer doesn't try to constantly send the same message to
	 * this node, just to get dropped by an application every time.
	 * 
	 * @param id	id of the message
	 * @return <code>true</code> if blacklisted, <code>false</code> otherwise.
	 */
	protected boolean isBlacklistedMessage(String id) {
		return this.blacklistedMessages.containsKey(id);
	}
	
	/**
	 * Returns a reference to the messages of this router in collection.
	 * <b>Note:</b> If there's a chance that some message(s) from the collection
	 * could be deleted (or added) while iterating through the collection, a
	 * copy of the collection should be made to avoid concurrent modification
	 * exceptions. 
	 * @return a reference to the messages of this router in collection
	 */
	public Collection<Message> getMessageCollection() {
		return this.messages.values();
	}

	/**
	 * Returns the number of messages this router has
	 * @return How many messages this router has
	 */
	public int getNrofMessages() {
		return this.messages.size();
	}

	/**
	 * Returns the size of the message buffer.
	 * @return The size or Integer.MAX_VALUE if the size isn't defined.
	 */
	public int getBufferSize() {
		return this.bufferSize;
	}
	
	/**
	 * Returns the amount of free space in the buffer. May return a negative
	 * value if there are more messages in the buffer than should fit there
	 * (because of creating new messages).
	 * @return The amount of free space (Integer.MAX_VALUE if the buffer
	 * size isn't defined)
	 */
	public int getFreeBufferSize() {
		int occupancy = 0;
		
		if (this.getBufferSize() == Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		
		for (Message m : getMessageCollection()) {
			occupancy += m.getSize();
		}
		
		return this.getBufferSize() - occupancy;
	}
	
	/**
	 * Returns the host this router is in
	 * @return The host object
	 */
	protected DTNHost getHost() {
		return this.host;
	}
	
	/**
	 * Start sending a message to another host.
	 * @param id Id of the message to send
	 * @param to The host to send the message to
	 */
	public void sendMessage(String id, DTNHost to) {
		Message m = getMessage(id);
		Message m2;
		if (m == null) throw new SimError("no message for id " +
				id + " to send at " + this.host);
 
		m2 = m.replicate();	// send a replicate of the message
		to.receiveMessage(m2, this.host);
	}
	
	/**
	 * Requests for deliverable message from this router to be sent trough a
	 * connection.
	 * @param con The connection to send the messages trough
	 * @return True if this router started a transfer, false if not
	 */
	public boolean requestDeliverableMessages(Connection con) {
		return false; // default behavior is to not start -- subclasses override
	}
	
	/**
	 * Try to start receiving a message from another host.
	 * @param m Message to put in the receiving buffer
	 * @param from Who the message is from
	 * @return Value zero if the node accepted the message (RCV_OK), value less
	 * than zero if node rejected the message (e.g. DENIED_OLD), value bigger
	 * than zero if the other node should try later (e.g. TRY_LATER_BUSY).
	 */
	public int receiveMessage(Message m, DTNHost from) {
		Message newMessage = m.replicate();				// 复制消息
		this.putToIncomingBuffer(newMessage, from);		// 将消息放入到IncomingBuffer中
		newMessage.addNodeOnPath(this.host);			// 将消息放入到节点经过的路径中
		
		for (MessageListener ml : this.mListeners) {
			ml.messageTransferStarted(newMessage, from, getHost());
		}
		
		return RCV_OK; // superclass always accepts messages
	}
	
	/**
	 * Updates router.
	 * This method should be called (at least once) on every simulation
	 * interval to update the status of transfer(s). 
	 */
	public void update(){
		updateReTransfer();
		for (Collection<Application> apps : this.applications.values()) {
			for (Application app : apps) {
				app.update(this.host);		
			}
		}
	}
	

	/**
	 * This method should be called (on the receiving host) after a message
	 * was successfully transferred. The transferred message is put to the
	 * message buffer unless this host is the final recipient of the message.
	 * @param id Id of the transferred message
	 * @param from Host the message was from (previous hop)
	 * @return The message that this host received
	 */
	public Message messageTransferred(String id, DTNHost from) {
		Message incoming = removeFromIncomingBuffer(id, from);			//将消息从incomingMessages删除
		boolean isFinalRecipient;										//判断消息传递到目的节点
		boolean isFirstDelivery; 										//is this first delivered instance of the msg //消息传递到该节点	
		if (incoming == null) {
			throw new SimError("No message with ID " + id + " in the incoming "+
					"buffer of " + this.host);
		}
		incoming.setReceiveTime(SimClock.getTime());					//设置消息接收时间		
		
		System.out.println("IB成功接收文件："+"  "+this.getHost()+"   "+incoming.getProperty(SelectLabel)+ "  "
				+incoming.getFilename()+" "+incoming.getChunkID()+"  "
					+incoming.getId()+" "+incoming.getFrom()+"  "+incoming.getTo()+"  "+"初始消息名称："+"  "+incoming.getInitMsgId());
				
		// Pass the message to the application (if any) and get outgoing message
		/*** 将消息交给应用层处理(如果有的话)， 有些应用会丢弃该消息 ***/
		Message outgoing = incoming;
		for (Application app : getApplications(incoming.getAppID())) {
			// Note that the order of applications is significant		
			// since the next one gets the output of the previous.
			outgoing = app.handle(outgoing, this.host);
			if (outgoing == null) break; 								// Some app want to drop the message
		}
		
		Message aMessage = (outgoing==null)?(incoming):(outgoing);
		// If the application re-targets the message (changes 'to')
		// then the message is not considered as 'delivered' to this host.
		isFinalRecipient = aMessage.getTo() == this.host;
		isFirstDelivery = isFinalRecipient && !isDeliveredMessage(aMessage);  	//判断是否为目的节点且为第一次到达
		
		/*** 将消息根据实际情况放入相应的缓冲区 ***/
		if (!isFinalRecipient && outgoing!=null) {								//不是目的节点，应用层也不想丢掉这个消息
			addToMessages(aMessage, false);      
			this.notDestinationCache(aMessage);			// 这里接收到消息对消息进行判定： 如果是请求消息则查看缓存,如果是应答消息则查看缓存中是否有该文件，如果没有则存储 
		}	
		else if (isFirstDelivery) {						// 这是目的节点且是第一次到达
			this.deliveredMessages.put(id, aMessage);	
			this.DestinationCache(aMessage);			// 若是请求消息，看缓存中是否有文件，有则传文件，给应答消息；     没有给出报错处理，用于程序检错。
		} 
		else if (outgoing == null) {			
			// Blacklist messages that an app wants to drop.
			// Otherwise the peer will just try to send it back again.
			this.blacklistedMessages.put(id, null);											
		}		
		for (MessageListener ml : this.mListeners) {
			ml.messageTransferred(aMessage, from, this.host,isFirstDelivery);
		}		
		return aMessage;
	}
	
	/**
	 * Puts a message to incoming messages buffer. Two messages with the
	 * same ID are distinguished by the from host.
	 * @param m The message to put
	 * @param from Who the message was from (previous hop).
	 */
	protected void putToIncomingBuffer(Message m, DTNHost from) {
		this.incomingMessages.put(m.getId() + "_" + from.toString(), m);
	}
	
	/**
	 * Removes and returns a message with a certain ID from the incoming 
	 * messages buffer or null if such message wasn't found. 
	 * @param id ID of the message
	 * @param from The host that sent this message (previous hop)
	 * @return The found message or null if such message wasn't found
	 */
	protected Message removeFromIncomingBuffer(String id, DTNHost from) {
		return this.incomingMessages.remove(id + "_" + from.toString());
	}
	
	/**
	 * Returns true if a message with the given ID is one of the
	 * currently incoming messages, false if not
	 * @param id ID of the message
	 * @return True if such message is incoming right now
	 */
	protected boolean isIncomingMessage(String id) {
		return this.incomingMessages.containsKey(id);
	}
	
	/**
	 * Adds a message to the message buffer and informs message listeners
	 * about new message (if requested).
	 * @param m The message to add
	 * @param newMessage If true, message listeners are informed about a new
	 * message, if false, nothing is informed.
	 */
	protected void addToMessages(Message m, boolean newMessage) {
		//System.out.print( " 第二层文件名是 ： "+m.getFilename()+"test"+"\n");
		this.messages.put(m.getId(), m);							//放到消息缓冲区messages中
		
		if (newMessage) {
			for (MessageListener ml : this.mListeners) {
				ml.newMessage(m);
			}
		}
	}
	
	/**
	 * Removes and returns a message from the message buffer.
	 * @param id Identifier of the message to remove
	 * @return The removed message or null if message for the ID wasn't found
	 */
	protected Message removeFromMessages(String id) {
		Message m = this.messages.remove(id);
		return m;
	}

	/**
	 * This method should be called (on the receiving host) when a message 
	 * transfer was aborted.
	 * @param id Id of the message that was being transferred
	 * @param from Host the message was from (previous hop)
	 * @param bytesRemaining Nrof bytes that were left before the transfer
	 * would have been ready; or -1 if the number of bytes is not known
	 */
	public void messageAborted(String id, DTNHost from, int bytesRemaining) {
		Message incoming = removeFromIncomingBuffer(id, from);
		if (incoming == null) {
			throw new SimError("No incoming message for id " + id + 
					" to abort in " + this.host);
		}		
		
		for (MessageListener ml : this.mListeners) {
			ml.messageTransferAborted(incoming, from, this.host);
		}
	}
	
	/**
	 * Creates a new message to the router.
	 * @param m The message to create
	 * @return True if the creation succeeded, false if not (e.g.
	 * the message was too big for the buffer)
	 */
	public boolean createNewMessage(Message m) {
		Random random = new Random();
		int r = random.nextInt(50);
		if(r!=0){
			m.setTtl(this.msgTtl);
			addToMessages(m, true);		
			return true;
		} else{
			System.out.println("++++++++++++++++++++创建失败的消息是："+"  "+this.getHost()+"   "+m.getProperty(SelectLabel)+ "  "
							+m.getFilename()+" "+m.getChunkID()+"  "+m.getId()+" "+m.getFrom()+"  "+m.getTo());
			return false;
		}
	}

	/**
	 * Deletes a message from the buffer and informs message listeners
	 * about the event
	 * @param id Identifier of the message to delete
	 * @param drop If the message is dropped (e.g. because of full buffer) this 
	 * should be set to true. False value indicates e.g. remove of message
	 * because it was delivered to final destination.  
	 */
	public void deleteMessage(String id, boolean drop) {
		Message removed = removeFromMessages(id);
		if (removed == null) throw new SimError("no message for id " +
				id + " to remove at " + this.host);
			
		for (MessageListener ml : this.mListeners) {
			ml.messageDeleted(removed, this.host, drop);
		}
	}
	
	/**
	 * Sorts/shuffles the given list according to the current sending queue 
	 * mode. The list can contain either Message or Tuple<Message, Connection> 
	 * objects. Other objects cause error. 
	 * @param list The list to sort or shuffle
	 * @return The sorted/shuffled list
	 */
	@SuppressWarnings(value = "unchecked") /* ugly way to make this generic */
	protected List sortByQueueMode(List list) {
		switch (sendQueueMode) {
		case Q_MODE_RANDOM:
			Collections.shuffle(list, new Random(SimClock.getIntTime()));
			break;
		case Q_MODE_FIFO:
			Collections.sort(list, 
					new Comparator() {
				/** Compares two tuples by their messages' receiving time */
				public int compare(Object o1, Object o2) {
					double diff;
					Message m1, m2;
					
					if (o1 instanceof Tuple) {
						m1 = ((Tuple<Message, Connection>)o1).getKey();
						m2 = ((Tuple<Message, Connection>)o2).getKey();
					}
					else if (o1 instanceof Message) {
						m1 = (Message)o1;
						m2 = (Message)o2;
					}
					else {
						throw new SimError("Invalid type of objects in " + 
								"the list");
					}
					
					diff = m1.getReceiveTime() - m2.getReceiveTime();
					if (diff == 0) {
						return 0;
					}
					return (diff < 0 ? -1 : 1);
				}
			});
			break;
		/* add more queue modes here */
		default:
			throw new SimError("Unknown queue mode " + sendQueueMode);
		}
		
		return list;
	}

	/**
	 * Gives the order of the two given messages as defined by the current
	 * queue mode 
	 * @param m1 The first message
	 * @param m2 The second message
	 * @return -1 if the first message should come first, 1 if the second 
	 *          message should come first, or 0 if the ordering isn't defined
	 */
	protected int compareByQueueMode(Message m1, Message m2) {
		switch (sendQueueMode) {
		case Q_MODE_RANDOM:
			/* return randomly (enough) but consistently -1, 0 or 1 */
			return (m1.hashCode()/2 + m2.hashCode()/2) % 3 - 1; 
		case Q_MODE_FIFO:
			double diff = m1.getReceiveTime() - m2.getReceiveTime();
			if (diff == 0) {
				return 0;
			}
			return (diff < 0 ? -1 : 1);
		/* add more queue modes here */
		default:
			throw new SimError("Unknown queue mode " + sendQueueMode);
		}
	}
	
	/**
	 * Returns routing information about this router.
	 * @return The routing information.
	 */
	public RoutingInfo getRoutingInfo() {
		RoutingInfo ri = new RoutingInfo(this);
		RoutingInfo incoming = new RoutingInfo(this.incomingMessages.size() + 
				" incoming message(s)");
		RoutingInfo delivered = new RoutingInfo(this.deliveredMessages.size() +
				" delivered message(s)");
		
		RoutingInfo cons = new RoutingInfo(host.getConnections().size() + 
			" connection(s)");
				
		ri.addMoreInfo(incoming);
		ri.addMoreInfo(delivered);
		ri.addMoreInfo(cons);
		
		for (Message m : this.incomingMessages.values()) {
			incoming.addMoreInfo(new RoutingInfo(m));
		}
		
		for (Message m : this.deliveredMessages.values()) {
			delivered.addMoreInfo(new RoutingInfo(m + " path:" + m.getHops()));
		}
		
		for (Connection c : host.getConnections()) {
			cons.addMoreInfo(new RoutingInfo(c));
		}

		return ri;
	}
	
	/** 
	 * Adds an application to the attached applications list.
	 * 
	 * @param app	The application to attach to this router.
	 */
	public void addApplication(Application app) {
		if (!this.applications.containsKey(app.getAppID())) {
			this.applications.put(app.getAppID(),
					new LinkedList<Application>());
		}
		this.applications.get(app.getAppID()).add(app);
	}
	
	/** 
	 * Returns all the applications that want to receive messages for the given
	 * application ID.
	 * 
	 * @param ID	The application ID or <code>null</code> for all apps.
	 * @return		A list of all applications that want to receive the message.
	 */
	public Collection<Application> getApplications(String ID) {
		LinkedList<Application>	apps = new LinkedList<Application>();
		// Applications that match
		Collection<Application> tmp = this.applications.get(ID);
		if (tmp != null) {
			apps.addAll(tmp);
		}
		// Applications that want to look at all messages
		if (ID != null) {
			tmp = this.applications.get(null);
			if (tmp != null) {
				apps.addAll(tmp);
			}
		}
		return apps;
	}

	/**
	 * Creates a replicate of this router. The replicate has the same
	 * settings as this router but empty buffers and routing tables.
	 * @return The replicate
	 */
	public abstract MessageRouter replicate();
	
	/**
	 * Returns a String presentation of this router
	 * @return A String presentation of this router
	 */
	public String toString() {
		return getClass().getSimpleName() + " of " + 
			this.getHost().toString() + " with " + getNrofMessages() 
			+ " messages";
	}
	

	
	/**  ------------------------- 戴师兄对代码的修改 ------------------------ */
	
	/** 获取仿写的消息缓存中内容*/
	protected Message getmyMessage(String id) {
		return this.myMessages.get(id);
	}
	/** 获取消息缓存*/
	public Collection<Message> getmyMessageCollection() {
		return this.myMessages.values();
	}
	/** 判断是否存在这个消息*/
	public boolean hasmyMessages(String id) {
		return this.myMessages.containsKey(id);
	}
	/** 获取消息缓存的大小 */
	public int getNrofmyMessages() {
		return this.myMessages.size();
	}
	/** 添加到对应的文件缓冲区内  addToFileBuffer() */
	protected void addTomyMessages(Message m, boolean newMessage) {
		this.myMessages.put(m.getFilename(), m);								// 放到消息缓冲区messages中
		//这里没有加入事件监听器
	}
	/**　将消息从新创建的缓存中移除　*/
	protected Message removeFrommyMessages(String id) {
		Message m = this.myMessages.remove(id);
		return m;
	}
	/**  ------------------------- 戴师兄对代码的修改 ------------------------ */
	
	
	/** -------------------------- 我对代码的修改  --------------------------- */
	
	/** 添加到对应的文件缓冲区内  addToFileBuffer() */       
	protected void addToFileBuffer(Message m, boolean newMessage) {
		if ( m.getResponseSize() ==0){											//从message中取出file类
			file ee = m.getFile();				
			this.getHost().getFileBuffer().put(m.getFilename(), ee);			// 放到消息缓冲区FileBuffer中
		}
	}	
	/** 添加chunk到对应的chunkBuffer中，  	*/
	protected void addToChunkBuffer(Message m, boolean newMessage){
		if(m.getProperty(SelectLabel)== (Object)1){
			
			if(this.getHost().getChunkBuffer().containsKey(m.getFilename())){
				this.getHost().getChunkBuffer().get(m.getFilename()).put(m.getChunkID(),m.getFile());
			}	else{
				HashMap<String,file> NewHashMap = new HashMap<String,file>();
				NewHashMap.put(m.getChunkID(),m.getFile());
				this.getHost().getChunkBuffer().put(m.getFilename(), NewHashMap);
			}
		}
	}
	/** 对bitMap中所有元素置零操作   */
	public void setZeroForBitMap(){
		this.bitMap.clear();
		for(int i=0;i<10;i++)
			bitMap.add(0);
	}
    /** 得到存放文件的缓存大小filebuffersize */
	public int getFileBufferSize(){
		return this.filebuffersize;
	}
	/** 得到当前路由的重传buffer。*/
	public HashMap<String,ArrayList<Object>> getJudgeForRetransfer(){
		return this.judgeForRetransfer;
	}
	
	/** 将刚创建的消息放入到判断是否需要重传buffer中 */
	public void putJudgeForRetransfer(Message m){		
		switch((int) m.getProperty(SelectLabel)){
		case 0:{
			ArrayList<Object> arraylist = new ArrayList<Object>();
			arraylist.add(0, m);
			arraylist.add(1, this.time_out);
			arraylist.add(2, this.reTransTimes);
			this.judgeForRetransfer.put(m.getId(), arraylist);
			return;
		}
		case 1:{
			ArrayList<Object> arraylist = new ArrayList<Object>();
			arraylist.add(0, m);
			arraylist.add(1, this.time_free);
			arraylist.add(2, -1);
			this.judgeForRetransfer.put("Chunk"+m.getInitMsgId(), arraylist);
			return;
		}
		case 2:{
			ArrayList<Object> arraylist = new ArrayList<Object>();
			arraylist.add(0, m);
			arraylist.add(1, this.time_out);
			arraylist.add(2, this.reTransTimes);
			this.judgeForRetransfer.put(m.getId(), arraylist);
			return;
		}
		case 3:{
			ArrayList<Object> arraylist = new ArrayList<Object>();
			arraylist.add(0, m);
			arraylist.add(1, this.time_wait);
			arraylist.add(2, -1);
			this.judgeForRetransfer.put(m.getId(), arraylist);
			return;
		}
		case 4:{
			ArrayList<Object> arraylist = new ArrayList<Object>();
			arraylist.add(0, m);
			arraylist.add(1, this.time_wait);
			arraylist.add(2, -1);
			this.judgeForRetransfer.put(m.getId(), arraylist);
			return;
		}
		}
	}
	
	/** 更新待确认消息buffer中的消息 */
	public void updateReTransfer(){
		
		/**	这里需要对待确认消息缓存中的消息待确认时间更新；并判断是否到期，到期重传。*/
		
		for( ArrayList<Object> reTrans : this.judgeForRetransfer.values()){
			reTrans.set(1, (double)reTrans.get(1)-0.1);
			Message n = (Message)reTrans.get(0);
			String s = n.getId();
			if((double)reTrans.get(1)<=0){	//判断生存时间是否到期？
				Message m = (Message)reTrans.get(0);
				switch((int) m.getProperty(SelectLabel)){
				case 0:{
					if(this.getHost().getFileBuffer().containsKey(m.getFilename())==false){										// 如果缓存已有，就不再重发请求消息，没有才发
						if((int)reTrans.get(2)>0){	//判断重传次数是否用完
							Message reqMessage = new Message(m.getFrom(),m.getTo(),m.getId(), m.getResponseSize());
							reqMessage.setInitMsgId(m.getInitMsgId());
							reqMessage.updateProperty(SelectLabel, 0);															//	标识为控制包
							reqMessage.setFilename(m.getFilename());
							reqMessage.setZeroForBitMap();
							reqMessage.setTime(SimClock.getTime()+0.01, SimClock.getTime()+0.01);								//	更新消息生成时间
							
							this.judgeForRetransfer.get(m.getInitMsgId()).set(1, this.time_out);
							int i = (int) this.judgeForRetransfer.get(m.getInitMsgId()).get(2);
			                this.judgeForRetransfer.get(m.getInitMsgId()).set(2, i-1); 											//	重传次数减少一次
			                this.createNewMessage(reqMessage);
						}
						else{
							this.judgeForRetransfer.remove(m.getInitMsgId());
						}
					}
					else{
						this.judgeForRetransfer.remove(m.getInitMsgId());
					}
					return;
				}
				/** 对于应答包的time_free到期，首先判断内存中是否有对应的应答包？ 有的话删了，然后删除待确认消息缓存中的此消息。*/
				case 1:{ 		
					if(this.MessageHashMap.containsKey(n.getFilename())){
						this.MessageHashMap.remove(n.getFilename());
					}
					if(this.getHost().getChunkBuffer().containsKey(n.getFilename())){
						this.getHost().getChunkBuffer().remove(n.getFilename());
					}
					this.judgeForRetransfer.remove("Chunk"+n.getInitMsgId());
				}
				
				case 2:{																										// 如果缓存已有，就不再重发控制消息，没有才发
					if(this.getHost().getFileBuffer().containsKey(m.getFilename())==false){
						if((int)reTrans.get(2)>0){	//判断重传次数是否用完，重传控制包
							Message ctrMessage = new Message(m.getFrom(),m.getTo(),
									RESPONSE_PREFIX + m.getId(), m.getResponseSize());
							
							ctrMessage.setInitMsgId(m.getInitMsgId());
							ctrMessage.updateProperty(SelectLabel, 2);															//	标识为控制包
							ctrMessage.setFilename(m.getFilename());
							ctrMessage.setZeroForBitMap();
							ctrMessage.setTime(SimClock.getTime()+0.01, SimClock.getTime()+0.01);								//	更新消息生成时间
							
							this.judgeForRetransfer.get(RESPONSE_PREFIX +"ctr_"+m.getInitMsgId()).set(1, this.time_out);		//	刷新重传时间
			                int j = (int) this.judgeForRetransfer.get(RESPONSE_PREFIX +"ctr_"+m.getInitMsgId()).get(2);
			                this.judgeForRetransfer.get(RESPONSE_PREFIX +"ctr_"+m.getInitMsgId()).set(2, j-1); 					//	重传次数减少一次
			                this.createNewMessage(ctrMessage);
						}
						else{
							this.judgeForRetransfer.remove(RESPONSE_PREFIX +"ctr_"+m.getInitMsgId());
						}	
					}
					else{
						this.judgeForRetransfer.remove(RESPONSE_PREFIX +"ctr_"+m.getInitMsgId());
					}			
					return;
				}
				case 3:{		// 对控制包的确认消息
					this.judgeForRetransfer.remove(s);
					return;
				}
				case 4:{		// 对请求包的确认消息：
					this.judgeForRetransfer.remove(s);
					return;
				}
				}
			}
		}	
	}
	
	/**
	 * 消息到达目的节点，而且是第一次到达
	 * @param m 为对应到达的消息
	 */
	protected void DestinationCache(Message aMessage){
		if(aMessage.getProperty(SelectLabel)== (Object) 0){		// 这是一个请求包			
			
			// 如果这个请求包是由于ack确认包丢失造成的重发，需要time_wait处理
			if(this.judgeForRetransfer.containsKey(RESPONSE_PREFIX +"ackr_" + aMessage.getInitMsgId())){
				
				// 也即是由于ack确认包丢失造成的重发控制包，这里直接回复确认包即可
				Message m = (Message)this.judgeForRetransfer
							.get(RESPONSE_PREFIX +"ackr_" + aMessage.getInitMsgId()).get(0);

				Message ackMessage = new Message(m.getFrom(),m.getTo(),RESPONSE_PREFIX + m.getId(), m.getResponseSize());
				ackMessage.setInitMsgId(m.getInitMsgId());
				ackMessage.updateProperty(SelectLabel, 4);															//	标识为控制包
				ackMessage.setFilename(m.getFilename());
				ackMessage.setZeroForBitMap();
				ackMessage.setTime(SimClock.getTime()+0.01, SimClock.getTime()+0.01);								//	更新消息生成时间
				
				this.judgeForRetransfer.get(RESPONSE_PREFIX +"ackr_"+m.getInitMsgId()).set(1, this.time_wait);		//	刷新重传时间
                this.createNewMessage(ackMessage);
			} else{
				
				//收到请求消息，需要对请求消息进行确认
				Message ackMessage =new Message(this.getHost(),aMessage.getHops().get(aMessage.getHopCount()-1),
						RESPONSE_PREFIX +"ackr_"+ aMessage.getId(), aMessage.getResponseSize());
				ackMessage.setInitMsgId(aMessage.getInitMsgId());
				ackMessage.updateProperty(SelectLabel, 4);												//	标识为对请求的确认包
				ackMessage.setFilename(aMessage.getFilename());
				ackMessage.setTime(SimClock.getTime()+0, SimClock.getTime()+0);				         
				this.putJudgeForRetransfer(ackMessage);
				this.createNewMessage(ackMessage);  			

				if (this.getHost().getFileBufferForFile(aMessage)!=null) {
	            	// 需要在这里加对文件分片的处理，然后再将对应的生成消息放入待发送序列中
					this.getHost().getFileBufferForFile(aMessage).setTimeRequest(SimClock.getTime());  	//缓存中文件更新请求时间					
					file f = this.getHost().getFileBufferForFile(aMessage);
					for(int i=0;i<10;i++){							// each file is divided into 10 chunks
						file chunk = f.copyFrom(f);
						for(int j=i*10;j<i*10+10;j++){
							chunk.getData().add(j-i*10,f.getData().get(j));
						}
						
						Message res = new Message(this.getHost(), aMessage.getFrom(),
								RESPONSE_PREFIX + aMessage.getId()+i, aMessage.getResponseSize(),chunk);	
						res.setInitMsgId(aMessage.getInitMsgId());
						res.setResponseSize(0);						//这一句应该没用
						res.setFilename(aMessage.getFilename());
						res.setChunkID(aMessage.getFilename()+"ChunkID"+i);									//设置chunkID，在message中设置。
						res.updateProperty(SelectLabel,1);													//说明这是一个应答包						
						res.setTime(SimClock.getTime()+0.01*(i+1), SimClock.getTime()+0.01*(i+1));
						this.createNewMessage(res);
					}

					//应答消息发完之后，应该发送一个控制包
					Message ctrMessage =new Message(this.getHost(),aMessage.getFrom(),
							RESPONSE_PREFIX + "ctr_"+ aMessage.getId(), aMessage.getResponseSize());
					ctrMessage.setInitMsgId(aMessage.getInitMsgId());
					ctrMessage.updateProperty(SelectLabel, 2);												//标识为控制包
					ctrMessage.setFilename(aMessage.getFilename());
					ctrMessage.setZeroForBitMap();
					ctrMessage.setTime(SimClock.getTime()+0.11, SimClock.getTime()+0.11);	              
					this.createNewMessage(ctrMessage);  					
            		this.putJudgeForRetransfer(ctrMessage);
	            } else {
					System.out.print("当为目的节点时，出现错误，目的节点中没有对应的文件。"+"\n");
				}
			}
		}	
		
		else if (aMessage.getProperty(SelectLabel)== (Object) 1){	// 这是一个应答消息，携带的是chunk文件  				
			
			// 为应答包加上计时器 Time_free,首先判断应答包的计时器在待确认消息中是否存在？ 存在的话更新，若不存在，则新添一个 
			if(this.judgeForRetransfer.containsKey("Chunk"+aMessage.getInitMsgId())){				
				this.judgeForRetransfer.get("Chunk"+aMessage.getInitMsgId()).set(1, this.time_free);
			}
			else{
				this.putJudgeForRetransfer(aMessage);
			}
			
			// 往chunkBuffer中放入文件 
			if (this.getHost().getFileBufferForFile(aMessage)==null){
				addToChunkBuffer(aMessage,false);
			}
			// 当消息是由于丢包重发,将中继节点当做目的节点进行处理
			if (this.MessageHashMap.containsKey(aMessage.getFilename())){
				this.MessageHashMap.get(aMessage.getFilename()).put(aMessage.getChunkID(), aMessage);
			}
		} 
		
		else if(aMessage.getProperty(SelectLabel)== (Object) 2){										// 判断收到的为控制包，回复确认
			if(this.getHost().getFileBuffer().containsKey(aMessage.getFilename())==false){				// 目的节点中不存在文件
				
				// 也即是由于ack确认包丢失造成的重发控制包，这里直接回复确认包即可.区别在于此时的chunkBuffer中已经没有了chunk文件。
				boolean a = false;
				for(int i=0;i<10;i++){
					a = this.getHost().getChunkBuffer().get(aMessage.getFilename()).containsKey(aMessage.getFilename()+"ChunkID"+i);
					if (a == true)
						break;
				}
				
				if(	a==false ){			//证明一个文件都没有，是由于ack包丢失造成的。
					Message m = (Message)this.judgeForRetransfer.get(RESPONSE_PREFIX +RESPONSE_PREFIX 
																		+"ctr_" + aMessage.getInitMsgId()).get(0);

					Message ackMessage = new Message(m.getFrom(),m.getTo(),RESPONSE_PREFIX + m.getId(), m.getResponseSize());			
					ackMessage.setInitMsgId(m.getInitMsgId());
					ackMessage.updateProperty(SelectLabel, 3);															//	标识为控制包
					ackMessage.setFilename(m.getFilename());
					ackMessage.setZeroForBitMap();
					ackMessage.setTime(SimClock.getTime()+0.01, SimClock.getTime()+0.01);									//	更新消息生成时间					
					this.judgeForRetransfer.get(RESPONSE_PREFIX + RESPONSE_PREFIX +"ctr_"+m.getInitMsgId()).set(1, this.time_wait);		//	刷新重传时间
	                this.createNewMessage(ackMessage);
				} else{

					// 遍历chunkBuffer看chunk是否收齐，回复确认包
					boolean b = true;						//b=1 默认为收齐
					this.setZeroForBitMap();
					for(int i=0;i<10;i++){
						if(this.getHost().getChunkBuffer().get(aMessage.getFilename()).containsKey(aMessage.getFilename()+"ChunkID"+i))
							this.bitMap.set(i, 1);
						else 
							b = false;  
					} 
					if(b){	//  判断收齐的情况下
						if (this.getHost().getFreeFileBufferSize() < 0) {	//判断内存是否满，若满的话删除内存,根据文件接收时间
							this.getHost().makeRoomForNewFile(0);       										//必要时，删除那些最早接收到且不正在传输的消息
							System.out.print("+++++++++++++++++++++		删除成功	 ++++++++++++++++++++"+"\n");
						}						
						file NewFile = this.getHost().getChunkBuffer().get(aMessage.getFilename()).get(aMessage.getFilename()+"ChunkID"+0);					
						for(int i=1;i<10;i++){
							file temp = this.getHost().getChunkBuffer().get(aMessage.getFilename()).get(aMessage.getFilename()+"ChunkID"+i);
							ArrayList<Integer> c = temp.getData();
							NewFile.getData().addAll(i*10, c);
						}
						this.getHost().getChunkBuffer().remove(aMessage.getFilename());	//	清空chunkbuffer						
						NewFile.setInitFile(NewFile);  					//	往FileBuffer中放文件
						NewFile.setTimeRequest(SimClock.getTime());
						
						System.out.println(this.getHost()+"  "+this.getHost().getFileBuffer());	//	输出放入前节点中文件
						
						this.getHost().getFileBuffer().put(aMessage.getFilename(), NewFile);
						
						System.out.println(this.getHost()+"  "+this.getHost().getFileBuffer());	//	输出放入后节点中文件
						System.out.println("++++++++++	目的节点中放入缓存成功	++++++++");
						
						// 解决当向目的节点重传文件与控制包时候的bug
						if(MessageHashMap.containsKey(aMessage.getFilename())){
							this.MessageHashMap.get(aMessage.getFilename()).put(aMessage.getId(), aMessage);
							
							// 证明这是曾经的目的节点，接着需要对MessageHashMap中消息进行重传
							HashMap<String,Message> NewHashMap = MessageHashMap.get(aMessage.getFilename());
							// 解决当节点一致时候的情况
							DTNHost temp = null;
							for(int i=0;i<10;i++){
								Message m = NewHashMap.get(aMessage.getFilename()+"ChunkID"+i);
								if(this.getHost()!= m.getTo())
									temp = m.getTo();
							}
							
							for(int i=0;i<10;i++){
								Message m = NewHashMap.get(aMessage.getFilename()+"ChunkID"+i);
								DTNHost thisHost = this.getHost();						//	源地址 
								DTNHost thisto = m.getTo();								//	当前消息的目的节点
								
								if(thisHost == m.getTo()){
									thisto = temp;
								}
								
								Message newMessage = new Message(thisHost,thisto,m.getId(),m.getSize());
								newMessage.copyFrom(m);									//  copy当前消息的内容
								newMessage.setFilename(m.getFilename());	        			
								newMessage.setBitMap(m.getBitMap());
								newMessage.setInitMsgId(m.getInitMsgId());
								newMessage.setFile(m.getFile());
								newMessage.setTime(SimClock.getTime()+0.01*(i+1), SimClock.getTime()+0.01*(i+1));
			 		        
								this.createNewMessage(newMessage);
							}
							Message m = NewHashMap.remove(aMessage.getId());		//  这个是控制包
							//	这里如果做逐跳确认的话，可以将拿出来的消息进行修改，可以用copyFrom的方法。
		        		
							DTNHost thisHost = this.getHost();						//	源地址 
							DTNHost thisto = m.getTo();								//	当前消息的目的节点
							
							if(thisHost == m.getTo()){
								thisto = temp;
							}
							String ID = RESPONSE_PREFIX + "ctr_"+m.getInitMsgId();
							Message newMessage = new Message(thisHost,thisto,ID,m.getSize());
							newMessage.copyFrom(m);									//  copy当前消息的内容
							newMessage.setFilename(m.getFilename());	        			
							newMessage.setBitMap(m.getBitMap());
							newMessage.setInitMsgId(m.getInitMsgId());
							newMessage.setTime(SimClock.getTime()+0.11, SimClock.getTime()+0.11);
		        		
							this.createNewMessage(newMessage);
							this.putJudgeForRetransfer(newMessage);					//  由当前节点发出的控制包，放入当前节点的待确认缓存中
		        		
							this.MessageHashMap.remove(aMessage.getFilename());
						}
					}
					
					// 回复确认包
					Message ackMessage =new Message(this.getHost(),aMessage.getFrom(),
							RESPONSE_PREFIX + aMessage.getId(), aMessage.getResponseSize());
					ackMessage.setInitMsgId(aMessage.getInitMsgId());
					ackMessage.updateProperty(SelectLabel,3);										//说明这是一个确认包
					ackMessage.getBitMap().clear();             									//先清空bitmap
					ackMessage.getBitMap().addAll(this.bitMap);										//回复bitMap
					ackMessage.setFilename(aMessage.getFilename());				
					this.putJudgeForRetransfer(ackMessage);
					System.out.println("目的节点bitmap确认："+"  "+ackMessage.getBitMap());
	                
					this.createNewMessage(ackMessage);  
				}
			}
		}
		
		else if(aMessage.getProperty(SelectLabel)== (Object) 3){									//判断收到的为确认包
			
			boolean b = true;											//用于判断是否需要再回复一个控制包？默认为不需要。
			file f = this.getHost().getFileBufferForFile(aMessage);				
					
			for(int i=0; i<10; i++){
				file chunk = f.copyFrom(f);
				if(aMessage.getBitMap().get(i)!=1){						//重发chunkID为i的包
					for(int j=i*10;j<i*10+10;j++){					
						chunk.getData().add(j-i*10,f.getData().get(j));
					}
					Message res = new Message(this.getHost(), aMessage.getFrom(),RESPONSE_PREFIX 
									+ aMessage.getId()+i, aMessage.getResponseSize(),chunk);	
					res.setInitMsgId(aMessage.getInitMsgId());
					res.setResponseSize(0);				
					res.setFilename(aMessage.getFilename());
					res.setChunkID(aMessage.getFilename()+"ChunkID"+i);									//设置chunkID，在message中设置。
					res.updateProperty(SelectLabel,1);													//说明这是一个应答包	
    				this.createNewMessage(res);
					b = false;
				}	
			}	
			
			if(b==false){																				//当b=false，证明有包丢失，此时需要再发送一个控制包
				Message ctrMessage =new Message(this.getHost(),aMessage.getFrom(),
						RESPONSE_PREFIX + aMessage.getId(), aMessage.getResponseSize());
				ctrMessage.setInitMsgId(aMessage.getInitMsgId());
				ctrMessage.updateProperty(SelectLabel, 2);															//	标识为控制包
				ctrMessage.setFilename(aMessage.getFilename());
				ctrMessage.setZeroForBitMap();
				ctrMessage.setTime(SimClock.getTime()+0.01, SimClock.getTime()+0.01);								//	更新消息生成时间
            
				this.judgeForRetransfer.get(RESPONSE_PREFIX +"ctr_"+aMessage.getInitMsgId()).set(1, this.time_out);	//	刷新重传时间
                int m = (int) this.judgeForRetransfer.get(RESPONSE_PREFIX +"ctr_"+aMessage.getInitMsgId()).get(2);
                this.judgeForRetransfer.get(RESPONSE_PREFIX +"ctr_"+aMessage.getInitMsgId()).set(2, m-1); 			//	重传次数减少一次
                this.createNewMessage(ctrMessage);
            } 	else{
				this.judgeForRetransfer.remove(RESPONSE_PREFIX +"ctr_"+aMessage.getInitMsgId());
			}
		}
		
		else if(aMessage.getProperty(SelectLabel)== (Object) 4){									//	证明这是对请求包的确认包			
			this.judgeForRetransfer.remove(aMessage.getInitMsgId());								//  删除保留的用于重传的请求消息
		} 
	}
	
	/**
	 * 消息并未到达目的节点，对消息类型做出判断，做出相应操作
	 * @param m 为对应到达的消息
	 */
	protected void notDestinationCache(Message aMessage){
		
		if(aMessage.getProperty(SelectLabel)== (Object) 0 ){	
			if(this.judgeForRetransfer.containsKey(RESPONSE_PREFIX +"ackr_" + aMessage.getInitMsgId())){
				Message m = (Message)this.judgeForRetransfer
							.get(RESPONSE_PREFIX +"ackr_" + aMessage.getInitMsgId()).get(0);
				Message ackMessage = new Message(m.getFrom(),m.getTo(),
									 RESPONSE_PREFIX + m.getId(), m.getResponseSize());
				ackMessage.setInitMsgId(m.getInitMsgId());
				ackMessage.updateProperty(SelectLabel, 4);															//标识为控制包
				ackMessage.setFilename(m.getFilename());
				ackMessage.setZeroForBitMap();
				ackMessage.setTime(SimClock.getTime()+0.01, SimClock.getTime()+0.01);								//更新消息生成时间				
				this.judgeForRetransfer.get(RESPONSE_PREFIX +"ackr_"+m.getInitMsgId()).set(1, this.time_wait);		//刷新重传时间
                this.createNewMessage(ackMessage);

			}
			else{
				Message ackMessage =new Message(this.getHost(),aMessage.getHops().get(aMessage.getHopCount()-1),
						RESPONSE_PREFIX +"ackr_"+ aMessage.getInitMsgId(), aMessage.getResponseSize());
				ackMessage.setInitMsgId(aMessage.getInitMsgId());
				ackMessage.updateProperty(SelectLabel, 4);															//标识为对请求的确认包
				ackMessage.setFilename(aMessage.getFilename());
				ackMessage.setTime(SimClock.getTime()+0, SimClock.getTime()+0);				              
				this.putJudgeForRetransfer(ackMessage);
				this.createNewMessage(ackMessage); 
				this.messages.get(aMessage.getId()).setTime(SimClock.getTime()+0.01, SimClock.getTime()+0.01); 		//对创建时间与接收时间进行重新设定
				
				if (this.getHost().getFileBufferForFile(aMessage)!=null) {
	            	
					// 需要在这里加对文件分片的处理，然后再将对应的生成消息放入待发送序列中
					this.getHost().getFileBufferForFile(aMessage).setTimeRequest(SimClock.getTime());  				//为缓存中文件更新请求时间					
					file f = this.getHost().getFileBufferForFile(aMessage);
					for(int i=0;i<10;i++){
						file chunk = f.copyFrom(f);
						for(int j=i*10;j<i*10+10;j++){
							chunk.getData().add(j-i*10,f.getData().get(j));
						}
						Message res = new Message(this.getHost(),aMessage.getHops().get(aMessage.getHopCount()-1),
								RESPONSE_PREFIX + aMessage.getId()+i, aMessage.getResponseSize(), chunk);	
						res.setInitMsgId(aMessage.getInitMsgId());
						res.setResponseSize(0);					
						res.setFilename(aMessage.getFilename());
						res.setChunkID(aMessage.getFilename()+"ChunkID"+i);
						res.updateProperty(SelectLabel, 1);											
						res.setTime(SimClock.getTime()+0.01*(i+2), SimClock.getTime()+0.01*(i+2));
						this.createNewMessage(res);               
					}         	
					
					//应答消息发完之后，发送一个控制包
					Message ctrMessage =new Message(this.getHost(),aMessage.getHops().get(aMessage.getHopCount()-1),
							RESPONSE_PREFIX +"ctr_"+  aMessage.getInitMsgId(), aMessage.getResponseSize());
					ctrMessage.setInitMsgId(aMessage.getInitMsgId());
					ctrMessage.updateProperty(SelectLabel, 2);								
					ctrMessage.setFilename(aMessage.getFilename());
					ctrMessage.setZeroForBitMap();
					ctrMessage.setTime(SimClock.getTime()+0.12, SimClock.getTime()+0.12);
	                this.createNewMessage(ctrMessage);  	             
            		this.putJudgeForRetransfer(ctrMessage);
	                this.removeFromMessages(aMessage.getId());	
	            }
			}
		}	
		else if (aMessage.getProperty(SelectLabel)== (Object) 1) {							//应答包
			//为应答包加上计时器 Time_free
			//首先判断应答包的计时器在待确认消息中是否存在？ 存在的话更新，若不存在，则新添一个
			if(this.judgeForRetransfer.containsKey("Chunk"+aMessage.getInitMsgId())){		
				this.judgeForRetransfer.get("Chunk"+aMessage.getInitMsgId()).set(1, this.time_free);
			} else{
				this.putJudgeForRetransfer(aMessage);
			}

			if (this.getHost().getFileBufferForFile(aMessage)==null){				
				//在添加到缓存中之前，需要先对缓存做判断，是否满？ 若未满，直接加入缓存；若满，则先对缓存中内容进行删除，再加入缓存
				//不用队列对信息进行存储，统一用一个多维的HashMap进行存储。
				addToChunkBuffer(aMessage,false);				
				if(MessageHashMap.containsKey(aMessage.getFilename())){
					this.MessageHashMap.get(aMessage.getFilename()).put(aMessage.getChunkID(), aMessage);
				}	else{
					HashMap<String,Message> NewHashMap = new HashMap<String,Message>();
					NewHashMap.put(aMessage.getChunkID(), aMessage);
					this.MessageHashMap.put(aMessage.getFilename(), NewHashMap);
				}
				this.removeFromMessages(aMessage.getId());		           
			}
		} 
		else if(aMessage.getProperty(SelectLabel)== (Object) 2){								//控制包
			if(this.getHost().getFileBuffer().containsKey(aMessage.getFilename())==false){
				// 也即是由于ack确认包丢失造成的重发控制包，这里直接回复确认包即可.区别在于此时的chunkBuffer中已经没有了chunk文件。
				boolean a = false;
				for(int i=0;i<10;i++){
					a = this.getHost().getChunkBuffer().get(aMessage.getFilename()).containsKey(aMessage.getFilename()+"ChunkID"+i);
					if (a == true)
						break;
				}
				if(	a==false ){						
				//证明一个文件都没有，是由于ack包丢失造成的。
					Message m = (Message)this.judgeForRetransfer
								.get(RESPONSE_PREFIX +RESPONSE_PREFIX +"ctr_" + aMessage.getInitMsgId()).get(0);
					Message ackMessage = new Message(m.getFrom(),m.getTo(),
										RESPONSE_PREFIX + m.getId(), m.getResponseSize());
					ackMessage.setInitMsgId(m.getInitMsgId());
					ackMessage.updateProperty(SelectLabel, 3);															
					ackMessage.setFilename(m.getFilename());
					ackMessage.setZeroForBitMap();
					ackMessage.setTime(SimClock.getTime()+0.01, SimClock.getTime()+0.01);	//更新消息生成时间
					this.judgeForRetransfer.get(RESPONSE_PREFIX + RESPONSE_PREFIX +"ctr_"+m.getInitMsgId()).set(1, this.time_wait);
	                this.createNewMessage(ackMessage);
				} else{
					if (this.getHost().getFileBufferForFile(aMessage)==null) {
						if(MessageHashMap.containsKey(aMessage.getFilename())){
							this.MessageHashMap.get(aMessage.getFilename()).put(aMessage.getId(), aMessage);
						}	else{
							HashMap<String,Message> NewHashMap = new HashMap<String,Message>();
							NewHashMap.put(aMessage.getChunkID(), aMessage);
							this.MessageHashMap.put(aMessage.getId(), NewHashMap);
						}
		            	this.removeFromMessages(aMessage.getId());
					
		            	// 遍历chunkBuffer看chunk是否收齐，b=1 默认为收齐
		            	boolean b = true;						
		            	this.setZeroForBitMap();
		            	for(int i=0;i<10;i++){
							if(this.getHost().getChunkBuffer().get(aMessage.getFilename()).containsKey(aMessage.getFilename()+"ChunkID"+i))
		            			this.bitMap.set(i, 1);
		            		else 
		            			b = false;
		            	}
		            	if(b){						
		            	//往里面放之前，需要先判断内存是否满，若满的话需要删除内存
		            		if (this.getHost().getFreeFileBufferSize() < 0) {
		            			this.getHost().makeRoomForNewFile(0);       		//必要时，删除那些最早接收到且不正在传输的消息
		            			System.out.print("+++++++++++++++++++++		删除成功	++++++++++++++++++++"+"\n");
		            		}				
							file NewFile = this.getHost().getChunkBuffer().get(aMessage.getFilename()).get(aMessage.getFilename()+"ChunkID"+0);
		            		for(int i=1;i<10;i++){
		            			file temp = this.getHost().getChunkBuffer().get(aMessage.getFilename()).get(aMessage.getFilename()+"ChunkID"+i);
		            			ArrayList<Integer> c = temp.getData();
		            			NewFile.getData().addAll(i*10, c);
		            		}
							//chunkBuffer取完之后，需要将其中内容清空
							this.getHost().getChunkBuffer().remove(aMessage.getFilename());
		            		NewFile.setInitFile(NewFile);
							NewFile.setTimeRequest(SimClock.getTime());
							
							System.out.println(this.getHost()+"  "+this.getHost().getFileBuffer());							
		            		this.getHost().getFileBuffer().put(aMessage.getFilename(), NewFile);
							System.out.println(this.getHost()+"  "+this.getHost().getFileBuffer());
							System.out.println("++++++++++中继节点中放入缓存成功++++++++");
							
							//	收到控制包之后，需要做两件事，一件回复上一跳，一件是往目的节点发
		            		// 	1、判断收齐的情况下，将MessageHashMap  中消息顺序取出，往下一跳发 
		            		HashMap<String,Message> NewHashMap = MessageHashMap.get(aMessage.getFilename());
			            	for(int i=0;i<10;i++){
			            		Message m = NewHashMap.get(aMessage.getFilename()+"ChunkID"+i);
			            		// 主要是改变源地址 
			            		DTNHost thisHost = this.getHost();						//	源地址 
			            		DTNHost thisto = m.getTo();								//	当前消息的目的节点
			            		Message newMessage = new Message(thisHost,thisto,m.getId(),m.getSize());
			            		newMessage.copyFrom(m);									//  copy当前消息的内容
			        			newMessage.setFilename(m.getFilename());	        			
			        			newMessage.setBitMap(m.getBitMap());
			        			newMessage.setInitMsgId(m.getInitMsgId());
				 		        newMessage.setFile(m.getFile());
				 		        newMessage.setTime(SimClock.getTime()+0.01*(i+1), SimClock.getTime()+0.01*(i+1));
			            		this.createNewMessage(newMessage);
			            	}
			            	Message m = NewHashMap.remove(aMessage.getId());		//  这个是控制包		            		
			            	DTNHost thisHost = this.getHost();						//	源地址 
		            		DTNHost thisto = m.getTo();								//	当前消息的目的节点
		            		Message newMessage = new Message(thisHost,thisto,m.getId(),m.getSize());
		            		newMessage.copyFrom(m);									//  copy当前消息的内容
		        			newMessage.setFilename(m.getFilename());	        			
		        			newMessage.setBitMap(m.getBitMap());
		        			newMessage.setInitMsgId(m.getInitMsgId());
		        			newMessage.setTime(SimClock.getTime()+0.11, SimClock.getTime()+0.11);
			 		        this.createNewMessage(newMessage);
		            		this.putJudgeForRetransfer(newMessage);					//  由当前节点发出的控制包，放入当前节点的待确认缓存中
		            		
		            		this.MessageHashMap.remove(aMessage.getFilename());	
		            	}
		            	// 	2、回复确认包			
		            	Message ackMessage =new Message(this.getHost(),aMessage.getHops().get(aMessage.getHopCount()-1),
		            			RESPONSE_PREFIX + aMessage.getId(), aMessage.getResponseSize());
						ackMessage.setInitMsgId(aMessage.getInitMsgId());
		            	ackMessage.updateProperty(SelectLabel,3);													//说明这是一个确认包
						ackMessage.getBitMap().clear();             												//先清空bitmap
		            	ackMessage.getBitMap().addAll(this.bitMap);													//回复bitMap
		            	System.out.println("中继中bitmap进行测试："+"  "+ ackMessage.getBitMap());
		            	
		            	ackMessage.setFilename(aMessage.getFilename());
						this.putJudgeForRetransfer(ackMessage);
		            	this.createNewMessage(ackMessage);
		            }
				}
			}
		}

		else if(aMessage.getProperty(SelectLabel)== (Object) 4){									//	证明这是对请求包的确认包			
			this.judgeForRetransfer.remove(aMessage.getInitMsgId());								//  删除保留的用于重传的请求消息
		} 
	}
	
	/** ------------------------ 我对代码的修改  ----------------------------  */
	
}
