package webcurve.exchange;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Vector;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import webcurve.common.Order;
import webcurve.common.Trade;
import webcurve.fix.ExchangeFixAgent;
import webcurve.util.PriceUtils;


/**
 * This class implements the back bone of the exchange simulator. 
 * It encapsulate the market depth object of each stock
 * @author dennis_d_chen@yahoo.com
 */
public class OrderBook implements Serializable, Cloneable {
    private static Logger log = LoggerFactory.getLogger(OrderBook.class);

	protected String code;
	protected Vector<Order> bidOrders = new Vector<Order>();
	protected Vector<Order> askOrders = new Vector<Order>();

	protected Vector<Trade> trades = new Vector<Trade>();
	protected Double VWAP = 0.0;
	protected Double tradedVolume = 0.0;
	
	public Double getTradedVolume() {
		return tradedVolume;
	}

	/**
	 * @return the last trade price
	 */	
	public double getLast()
	{
		if (trades.size() == 0)
			return 0.0;
		
		return trades.get(trades.size()-1).getPrice();
	}
	
	/**
	 * @return the last trade volume
	 */	
	public long getLastVol()
	{
		if (trades.size() == 0)
			return 0;
		
		return trades.get(trades.size()-1).getQuantity();
		
	}
	
	/**
	 * @return the VWAP of this stock since trading
	 */
	public Double getVWAP()
	{
		return VWAP;
	}
	
	/**
	 * @return the best bid price
	 */
	public double getBestBid()
	{
		if (bidOrders.size()>0)
			return bidOrders.get(0).getPrice();
		return 0.0;
	}
	

	/**
	 * @return the best ask price
	 */
	public double getBestAsk()
	{
		if (askOrders.size()>0)
			return askOrders.get(0).getPrice();
		return 0.0;
	}

	/**
	 * @return the best bid volume
	 */
	public long getBestBidVol()
	{
		if (bidOrders.size()==0)
			return 0;
		
		double price = bidOrders.get(0).getPrice();
		long total = 0;
		for (Order order : bidOrders)
		{
			if(new BigDecimal(price).equals(new BigDecimal(order.getPrice())))
			{
				total += order.getQuantity();
			}
			else
				break;
		}
		return total;
	}
	

	/**
	 * @return the best ask price
	 */
	public long getBestAskVol()
	{
		if (askOrders.size()==0)
			return 0;
		
		double price = askOrders.get(0).getPrice();
		long total = 0;
		for (Order order : askOrders)
		{
			if(new BigDecimal(price).equals(new BigDecimal(order.getPrice())))
			{
				total += order.getQuantity();
			}
			else
				break;
		}
		return total;
	}
	
	/**
	 * @param code Stock code
	 */
	public OrderBook(String code)
	{
		this.code = code;
	}
	
	/**
	 * @return returns the trades done
	 */
	public Vector<Trade> getTrades() {
		return trades;
	}
	
	/**
	 * @return returns the stock code of this market depth object
	 */
	public String getCode() {
		return code;
	}

	/**
	 * @return returns the buy order list of the order book.
	 */
	public Vector<Order> getBidOrders() {
		return bidOrders;
	}


	/**
	 * @return returns the buy orders of the order book per price level.
	 */
	public Vector<Order> getSumBidOrders() {
		return getSumOrders(bidOrders);
	}

	/**
	 * @return returns the sell list of the order book.
	 */
	public Vector<Order> getAskOrders() {
		return askOrders;
	}

	/**
	 * @return returns the Sell orders of the order book per price level.
	 */
	public Vector<Order> getSumAskOrders() {
		return getSumOrders(askOrders);
	}

	private Vector<Order> getSumOrders(Vector<Order> orders)
	{
		Vector<Order> sumOrders = new Vector<Order>();
		double price = 0.0;
		for(Order order: orders)
		{
			if (PriceUtils.Equal(order.getPrice(), price))
			{
				if (sumOrders.size()>0)
				{
					Order currOrder = sumOrders.get(sumOrders.size()-1);
					currOrder.setQuantity(currOrder.getQuantity() + order.getQuantity());
					currOrder.setBroker("");
				}
				else
					sumOrders.add((Order) order.clone());
			}
			else
			{
				sumOrders.add((Order) order.clone());
				price = order.getPrice();
			}
			
		}
		return sumOrders;	
	}
	
	private boolean compareOrders(Order order1, Order order2)
	{
		if (order1.getSide() == Order.SIDE.BID)
			return !(order1.getPrice() < order2.getPrice());
		else
			return !(order1.getPrice() > order2.getPrice());
	}

	protected void addTrade(Trade trade)
	{
		trades.add(trade);
		//recalculate VWAP
    	VWAP = VWAP * tradedVolume /(tradedVolume + trade.getQuantity())
				+ trade.getPrice() * trade.getQuantity() / (tradedVolume + trade.getQuantity());
    	tradedVolume += trade.getQuantity(); 
	}
	
	private void orderUpdate(Exchange exchange, Order order, Order.STATUS status)
	{
		order.setStatus(status);
		Order updateOrder = (Order)order.clone();
        exchange.touchOrder(updateOrder);
        exchange.orderListenerKeeper.updateExchangeListeners(updateOrder);
	}
	
	private void tradeUpdate(Exchange exchange, Trade trade)
	{
		exchange.tradeListenerKeeper.updateExchangeListeners(trade);
	}
	
	protected synchronized void enterOrder (Order order, Exchange exchange, boolean isReenter)
	{
		order.setOrderID(exchange.getNextOrderID());	
//		order.setOriginalQuantity(order.getQuantity());
//		order.setClOrderId(clientTranID);
		
		// post update
		if (isReenter)
		    orderUpdate(exchange, order, Order.STATUS.AMENDED);
		else
			orderUpdate(exchange, order, Order.STATUS.NEW);
		
        // firstly search the opposite side for orders of matching price
        Vector<Order> oppositeOrders;
        if (order.getSide() == Order.SIDE.BID)
            oppositeOrders = askOrders;
        else
            oppositeOrders = bidOrders;

        boolean hasResidual = true;
        while (hasResidual && oppositeOrders.size() > 0)
        {
        	Order thisOrder = oppositeOrders.get(0);
            if (
            	order.getType() == Order.TYPE.MARKET ||
                ((order.getSide() == Order.SIDE.BID) && (order.getPrice() >= thisOrder.getPrice())) ||
                ((order.getSide() == Order.SIDE.ASK) && (order.getPrice() <= thisOrder.getPrice()))
               )
            {
            	int tradeQty = 0;
                if (thisOrder.getQuantity() <= order.getQuantity())
                {
                	oppositeOrders.remove(0);
                	order.setQuantity(order.getQuantity() - thisOrder.getQuantity());
                	tradeQty = thisOrder.getQuantity();
                    order.calOrder(thisOrder.getQuantity(), thisOrder.getPrice());
                    thisOrder.calOrder(thisOrder.getQuantity(), thisOrder.getPrice());
                	thisOrder.setQuantity(0);
                	
                }
                else
                {
                	thisOrder.setQuantity(thisOrder.getQuantity() - order.getQuantity());                	
                	tradeQty = order.getQuantity();
                    order.calOrder(order.getQuantity(), thisOrder.getPrice());
                    thisOrder.calOrder(order.getQuantity(), thisOrder.getPrice());
                	order.setQuantity(0);
                }
                
                // updating order
                orderUpdate(exchange, order, Order.STATUS.FILLING);
                orderUpdate(exchange, thisOrder, Order.STATUS.FILLING);
                
            	Trade trade = new Trade(exchange.getNextOrderID(), tradeQty, thisOrder.getPrice(), 
						(Order)order.clone(), (Order)thisOrder.clone() );
				addTrade(trade);
				tradeUpdate(exchange, trade);
				
				if (order.getQuantity() <= 0)
				{
					hasResidual = false;
					break;
				} 
            }
            else // order is sorted by price/time, break right away if we cant find the matching opportunity
            {
            	break;
            }
        }

        if (hasResidual && order.getType() == Order.TYPE.LIMIT)
        {
			insertOrder(order);
			//orders.put(order.orderID, order);
        }
        
        if(hasResidual && order.getType() == Order.TYPE.MARKET)
            orderUpdate(exchange, order, Order.STATUS.CANCELLED);
        
	}
	
	protected synchronized void insertOrder(Order order)
	{
		Vector<Order> orders;
		if (order.getSide() == Order.SIDE.BID)
			orders = bidOrders;
		else
			orders = askOrders;
		
		
		int startIndex = 0;
		int endIndex = orders.size();

		//binary search and insert
		while(endIndex > startIndex)
		{
			int midIndex = (endIndex + startIndex)/2;
			if (compareOrders(orders.get(midIndex), order))
				startIndex = midIndex+1;
			else
				endIndex = midIndex;				
		}
		
		orders.add(endIndex, order);	
	}
	
	protected synchronized Order cancelOrder(long orderID, Order.SIDE side, Exchange exchange, String clientOrderID)
	{
		Vector<Order> orders;
		if (side == Order.SIDE.BID)
			orders = bidOrders;
		else
			orders = askOrders;

		for (int i=0; i<orders.size(); i++)
		{
			Order order = orders.get(i);
			if(order.getOrderID() == orderID)
			{
				order.setOrigClOrderId(order.getClOrderId());
				order.setClOrderId(clientOrderID);
	            orderUpdate(exchange, order, Order.STATUS.CANCELLED);
				return orders.remove(i);
			}
		}
		
		return null;
	}

	protected synchronized boolean amendOrder(long orderID, Order.SIDE side, 
			int quantity, double price, 
			String newClOrdId, 
			Exchange exchange)
	{
		if (0 == quantity && 0.0 == price)
			return false;

		Vector<Order> orders;
		if (side == Order.SIDE.BID)
			orders = bidOrders;
		else
			orders = askOrders;
		
		// find the order in depth
		int pos = 0;
		Order thisOrder = null;
		for (int i=0; i<orders.size(); i++)
		{
			if(orders.get(i).getOrderID() == orderID)
			{
				thisOrder = orders.get(i);
				pos = i;
				break;
			}
		}
		
		// return if not found
		if (null == thisOrder)
			return false;
		
		
		if (0.0 != price && price != thisOrder.getPrice()) //price has been changed
		{
			log.info("price amending: " + thisOrder.getPrice() + ", " + price);
			orders.remove(pos);
			thisOrder.setPrice(price);
			if ( 0 != quantity )
			{
				thisOrder.amendQuantity(quantity);
			}
			thisOrder.setOrigClOrderId(thisOrder.getClOrderId());
			thisOrder.setClOrderId(newClOrdId);
			thisOrder.setPrevOrderID(thisOrder.getOrderID());
			enterOrder(thisOrder, exchange, true);
			return true;
		}
		else if (0 != quantity && quantity != thisOrder.getQuantity()) //only quantity has been changed.
		{
			log.info("quantity amending: " + thisOrder.getQuantity() + ", " + quantity);
			//check whether the order is the last order in the same price queue
			boolean lastOrderInQueue = true;
			if (pos < orders.size()-1)
			{
				Order nextOrder = orders.get(pos+1);
				if (nextOrder.getPrice() == thisOrder.getPrice())
					lastOrderInQueue = false;
			}
			
			if (lastOrderInQueue || (quantity < thisOrder.getQuantity()) ) // simple change the order quantity
			{
				thisOrder.amendQuantity(quantity);
				thisOrder.setOrigClOrderId(thisOrder.getClOrderId());
				thisOrder.setClOrderId(newClOrdId);
	            orderUpdate(exchange, thisOrder, Order.STATUS.AMENDED);
			}
			else // create a new order if the order isn't the last order in the price queue
			{
				Order order = new Order(code, thisOrder.getType(), side, quantity-thisOrder.getQuantity(), 
						price, thisOrder.getBroker());
				order.setParentOrderID(thisOrder.getOrderID());
				thisOrder.setOrigClOrderId(thisOrder.getClOrderId());
				thisOrder.setClOrderId(newClOrdId);
				enterOrder(order, exchange, false);
			}
			return true;
		}
		
		log.warn("Can't amend order: " + orderID + ", " + quantity + ", " + price + " : " + thisOrder.getQuantity() + ", " + thisOrder.getPrice());
		return false;
	}

}
