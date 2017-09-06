package cache;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import core.Message;
import routing.MessageRouter;
import routing.ActiveRouter;

public abstract class cacheRouter extends ActiveRouter {
	
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
	protected cacheRouter(ActiveRouter r) {
		super(r);
		// TODO Auto-generated constructor stub
	}

}
