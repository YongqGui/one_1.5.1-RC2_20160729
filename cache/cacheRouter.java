package cache;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import core.Message;
import routing.MessageRouter;
import routing.ActiveRouter;

public abstract class cacheRouter extends ActiveRouter {
	
	/**------------------------------   ��MessageRouter��ӵı���       --------------------------------*/
	
	/** bitMap���ڶ�chunkID����ӳ��    */
	private ArrayList<Integer> bitMap = new ArrayList<Integer>();
	/**����һ����ʱ�Ķ��У����ڶ��м̽ڵ�õ�chunk������Ϣ�洢 */
	protected Queue<Message> tempQueue = new LinkedList<Message>();
	/** ��Ҫ�����ά��������ʽ���������ݽ��д洢 */
	protected HashMap<String,HashMap<String,Message>> MessageHashMap = new HashMap<String,HashMap<String,Message>>();
	/** �µ��ļ�����,��дmessages*/
	private HashMap<String, Message> myMessages;
	/** �����ж��ļ��Ƿ�õ�ȷ�ϣ��Ӷ������Ƿ���Ҫ�ش�  */
	private HashMap<String, ArrayList<Object>> judgeForRetransfer 
						= new HashMap<String, ArrayList<Object>>();	
	/** �����ж��ش�ʱ�䣬�����趨Ϊ100s */
	protected double time_out = 20;
	/** �����ж��ش���������ʼΪ0���趨����ش�3��*/
	protected int reTransTimes = 3;
	/**������ack����time_waitʱ�� */
	protected double time_wait = 40;
	/** ����Ӧ����ĵȴ�ʱ�� time_free */
	protected double time_free = 3.5*time_out;
	/** ��Ӧ��Ϣǰ׺ */
	public static final String RESPONSE_PREFIX = "R_";
	/** �����жϰ������� */
	public static final String SelectLabel = "SelectLabel";
	/** �½�һ���ļ�buffer */
	public static final String F_SIZE_S = "filebuffersize";
	protected cacheRouter(ActiveRouter r) {
		super(r);
		// TODO Auto-generated constructor stub
	}

}
