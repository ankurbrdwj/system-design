package webcurve.client;

import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import webcurve.common.Order;

/**
 * @author dennis_d_chen@yahoo.com
 */
public class ClientOrder implements Cloneable {
	private static final long serialVersionUID = 8463848083656731711L;
	
	private static int orderIDSeed = 0;
	public synchronized static String nextOrderID()
	{
		Calendar cal = Calendar.getInstance();
		int num = cal.get(Calendar.HOUR) * 10000 + cal.get(Calendar.MINUTE) * 100 + cal.get(Calendar.SECOND);
		return "O" + num + "-" + ++orderIDSeed;
	}
	
	public static enum STATUS { NEW, LIVE, ERROR, DONE, CANCELLED, REJECTED }
	public enum SIDE { BID, ASK }
	public enum TYPE {LIMIT, MARKET}
	protected boolean shortSell;
	protected String clientOrderID;

	protected String serverOrderID;
	protected String code;

	protected SIDE side;
	protected TYPE type;
	protected Integer quantity = 0;
	protected Double price = 0.0;
	protected String client;
	protected String account;
	protected STATUS status;
	protected String strategy;
	protected Double pov;
	
	
	protected Vector<ClientOrder> childOrders = new Vector<ClientOrder>();
	protected Vector<Execution> executions = new Vector<Execution>();


	protected ClientOrder parentOrder;
	protected Date createTime;
	protected Date amendTime;
	
	// FIX related
	protected String fixClOrdId;
	protected String fixOrigClOrderId;
	protected Double avgPx = 0.0;
	protected Integer cumQty = 0;
	protected Vector<String> messages = new Vector<String>();
	
	
	public Object clone (){
		ClientOrder order;
		try {
			order = (ClientOrder)super.clone();
			order.setChildOrders(new Vector<ClientOrder>());
			order.setExecutions(new Vector<Execution>());
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
			return null;
		}
		return order;
	}
	
	public ClientOrder()
	{
		
	}
	public ClientOrder(String clientOrderID)
	{
		this.clientOrderID = clientOrderID;
	}
	
	@Override
	public String toString()
	{
		return clientOrderID;
	}
	/**
	 * 
	 */
	
	public static String sideToString(SIDE side, boolean shortSell)
	{
		if (side == SIDE.ASK)
		{
			if(shortSell)
				return "SS";
			else
				return "S";
		}
		else if (side == SIDE.BID)
			return "B";
		else
			return "";
	}
	
	public static String typeToString(TYPE type)
	{
		if (type == TYPE.LIMIT)
			return "LIMIT";
		else if (type == TYPE.MARKET)
			return "MARKET";
		else
			return "";
	}
		static int getOrderCount(Vector<ClientOrder> orders, int count)
	{
		for (ClientOrder order: orders)
		{
			count++;
			return getOrderCount(order.getChildOrders(), count);
		}
		return count;
	}
	
	// get/set
		
	public String getServerOrderID() {
		return serverOrderID;
	}
	public boolean isShortSell() {
		return shortSell;
	}

	public void setShortSell(boolean shortSell) {
		this.shortSell = shortSell;
	}

	public String getClient() {
		return client;
	}
	public String getAccount() {
		return account;
	}
	public Date getCreateTime() {
		return createTime;
	}
	public Date getAmendTime() {
		return amendTime;
	}
	public String getFixClOrdId() {
		return fixClOrdId;
	}
	public String getFixOrigClOrderId() {
		return fixOrigClOrderId;
	}
	public Vector<String> getMessages() {
		return messages;
	}
	public void setServerOrderID(String serverOrderID) {
		this.serverOrderID = serverOrderID;
	}
	public void setQuantity(Integer quantity) {
		this.quantity = quantity;
	}
	public void setPrice(Double price) {
		this.price = price;
	}
	public void setClient(String client) {
		this.client = client;
	}
	public void setAccount(String account) {
		this.account = account;
	}
	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}
	public void setAmendTime(Date amendTime) {
		this.amendTime = amendTime;
	}
	public void setFixClOrdId(String fixClOrdId) {
		this.fixClOrdId = fixClOrdId;
	}
	public void setFixOrigClOrderId(String fixOrigClOrderId) {
		this.fixOrigClOrderId = fixOrigClOrderId;
	}
	public void setMessages(Vector<String> messages) {
		this.messages = messages;
	}
	public Integer getCumQty() {
		return cumQty;
	}
	public void setCumQty(Integer cumQty) {
		this.cumQty = cumQty;
	}

	public Double getAvgPx() {
		return avgPx;
	}
	public void setAvgPx(Double avgPx) {
		this.avgPx = avgPx;
	}

	public TYPE getType() {
		return type;
	}
	public void setType(TYPE type) {
		this.type = type;
	}
	public void setChildOrders(Vector<ClientOrder> childOrders) {
		this.childOrders = childOrders;
	}

	public String getClientOrderID() {
		return clientOrderID;
	}

	public void setClientOrderID(String clientOrderID) {
		this.clientOrderID = clientOrderID;
	}
	public STATUS getStatus() {
		return status;
	}

	public void setStatus(STATUS status) {
		this.status = status;
	}

	public Vector<ClientOrder> getChildOrders() {
		return childOrders;
	}

	public ClientOrder getParentOrder() {
		return parentOrder;
	}

	public void setParentOrder(ClientOrder parentOrder) {
		this.parentOrder = parentOrder;
	}
	
	public String getCode() {
		return code;
	}

	public SIDE getSide() {
		return side;
	}

	public Integer getQuantity() {
		return quantity;
	}

	public Double getPrice() {
		return price;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public void setSide(SIDE side) {
		this.side = side;
	}

	public Vector<Execution> getExecutions() {
		return executions;
	}
	public void setExecutions(Vector<Execution> executions) {
		this.executions = executions;
	}

	
	public String getStrategy() {
		return strategy;
	}

	public void setStrategy(String strategy) {
		this.strategy = strategy;
	}

	public Double getPov() {
		return pov;
	}

	public void setPov(Double pov) {
		this.pov = pov;
	}

	public void getAllExecutions(Vector<Execution> execs)
	{
		for (Execution exec: executions)
		{
			execs.add(exec);
		}
		for (ClientOrder order: childOrders)
		{
			order.getAllExecutions(execs);
		}
	}
	
	public Integer getLeftQuantity()
	{
		Integer leftQuantity = this.quantity;

		for (Execution exec: executions)
		{
			leftQuantity -= exec.getQuantity();
		}
		for (ClientOrder order: childOrders)
		{
			if (order.getStatus() != ClientOrder.STATUS.CANCELLED)
				leftQuantity -= order.getQuantity();
		}		
		return leftQuantity;
	}
	
	public static String statusToString(ClientOrder.STATUS status)
	{
		if ( status == ClientOrder.STATUS.NEW) 
			return "NEW";
		if ( status == ClientOrder.STATUS.LIVE) 
			return "LIVE";
		if ( status == ClientOrder.STATUS.DONE) 
			return "DONE";
		if ( status == ClientOrder.STATUS.ERROR) 
			return "ERROR";
		if ( status == ClientOrder.STATUS.CANCELLED) 
			return "CANCELLED";
		if ( status == ClientOrder.STATUS.REJECTED) 
			return "REJECTED";

		return "UNKNOWN";
	}
}
