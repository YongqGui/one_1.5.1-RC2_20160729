/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package input;

import core.DTNHost;
import core.Message;
import core.World;
import core.file;

import java.util.Random;

/**
 * External event for creating a message.
 */
public class MessageCreateEvent extends MessageEvent {
	private int size;
	private int responseSize;
	
	/**------------------------------   对 MessageCreateEvent 添加的参量       --------------------------------*/
	
	private String fileID; 			// 添加了文件的ID名
	public static final String SelectLabel = "SelectLabel";
	
	/**------------------------------   对 MessageCreateEvent 添加的参量       --------------------------------*/
	
	/**
	 * Creates a message creation event with a optional response request
	 * @param from The creator of the message
	 * @param to Where the message is destined to
	 * @param id ID of the message
	 * @param size Size of the message
	 * @param responseSize Size of the requested response message or 0 if
	 * no response is requested
	 * @param time Time, when the message is created
	 */
	public MessageCreateEvent(int from, int to, String id, int size,
			int responseSize, double time) {
		super(from,to, id, time);
		this.size = size;
		this.responseSize = responseSize;
	}
	
	@Override
	public String toString() {
		return super.toString() + " [" + fromAddr + "->" + toAddr + "] " +
		"size:" + size + " CREATE";
	}
	
	/**------------------------------   对 MessageCreateEvent 添加的函数方法       --------------------------------*/
	
	/** 有关于文件相关修改的部分       */
	public String getFileID() {
		Random random = new Random();
		int id =random.nextInt(20);
		return "filename" +id;		//return filename;
	}
	/**
	 * Creates the message this event represents. 
	 */
	@Override
	public void processEvent(World world) {
		
        this.fileID=getFileID();
        DTNHost from = world.getNodeByAddress(this.fromAddr);
		this.toAddr=from.getFiles().get(this.fileID);							// 修改
		DTNHost to= world.getNodeByAddress(this.toAddr);
		this.responseSize= to.getFileBuffer().get(this.fileID).getSize();		// responseSize设定的是文件的大小，
		
		Message m = new Message(from, to, this.id, this.size);
		m.setResponseSize(this.responseSize);
		m.setFilename(this.fileID);
		
		m.updateProperty(SelectLabel, 0);													//标识为控制包
		
		// 如果目的节点和源节点不同，才创建消息，因为取得文件是随机的；     同时如果节点缓存有文件，不再发生请求。
		if(this.toAddr!=this.fromAddr && !from.getFileBuffer().containsKey(this.fileID)) {	
			from.createNewMessage(m); 														// 把消息放进缓存中去
			from.putIntoJudgeForRetransfer(m);												// 需要将消息放入到判断消息是否重传的buffer中
		}
	}
	
	/**------------------------------   对 MessageCreateEvent 添加的函数方法       --------------------------------*/

}
