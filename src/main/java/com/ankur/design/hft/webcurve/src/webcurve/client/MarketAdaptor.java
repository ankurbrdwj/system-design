package webcurve.client;

import java.util.*;

import webcurve.common.*;
import webcurve.exchange.Exchange;
import webcurve.exchange.OrderBook;
import webcurve.test.Test1;
import webcurve.test.Test2;

/**
 * This class implements asynchronized updates for market data and orders.
 * @author dennis_d_chen@yahoo.com
 *
 */
public class MarketAdaptor implements Runnable{

	class ListenerKeeper<T> {
		HashMap<String, ArrayList<ExchangeEventListener<T>>> map 
			= new HashMap<String, ArrayList<ExchangeEventListener<T>>>();
		boolean subscribe(String stock, ExchangeEventListener<T> listener)
		{
			if (map.containsKey(stock))
			{
				ArrayList<ExchangeEventListener<T>> listeners = map.get(stock);
				synchronized(listeners)
				{
					if (listeners.contains(listener))
						return false;
					else
						listeners.add(listener);
				}
			}
			else
			{
				synchronized(map)
				{
					ArrayList<ExchangeEventListener<T>> listeners = new ArrayList<ExchangeEventListener<T>>();
					listeners.add(listener);
					map.put(stock, listeners);
				}
			}
			return true;
		}
		
		boolean unsubscribe(String stock, ExchangeEventListener<T> listener)
		{
			if (map.containsKey(stock))
			{
				synchronized(map)
				{
					ArrayList<ExchangeEventListener<T>> listeners = map.get(stock);
					if (listeners.contains(listener))
					{
						listeners.remove(listener);
						return true;
					}
					else
						return false;
				}
			}
			else
			{
				return false;
			}
		}
		
		ArrayList<ExchangeEventListener<T>> getListeners(String stock)
		{
			if (map.containsKey(stock))
			{
				return map.get(stock);
			}
			return null;
		}
	}

	ListenerKeeper<OrderBook> orderBookKeeper = new ListenerKeeper<OrderBook>();
	public boolean subscribeBook(String stock, ExchangeEventListener<OrderBook> listener)
	{
		return orderBookKeeper.subscribe(stock, listener);
	}
	
	public boolean unsubscribeBook(String stock, ExchangeEventListener<OrderBook> listener)
	{
		return orderBookKeeper.unsubscribe(stock, listener);
	}
	
	
	ListenerKeeper<Order> orderKeeper = new ListenerKeeper<Order>();
	public boolean subscribeOrder(String stock, ExchangeEventListener<Order> listener)
	{
		return orderKeeper.subscribe(stock, listener);
	}
	
	public boolean unsubscribeOrder(String stock, ExchangeEventListener<Order> listener)
	{
		return orderKeeper.unsubscribe(stock, listener);
	}
	
	ListenerKeeper<Trade> tradeKeeper = new ListenerKeeper<Trade>();
	public boolean subscribeTrade(String stock, ExchangeEventListener<Trade> listener)
	{
		return tradeKeeper.subscribe(stock, listener);
	}
	
	public boolean unsubscribeTrade(String stock, ExchangeEventListener<Trade> listener)
	{
		return tradeKeeper.unsubscribe(stock, listener);
	}
	
	LinkedList list = new LinkedList();

	private Exchange exchange;
	private Thread thread;
	public MarketAdaptor(Exchange exchange)
	{
		this.exchange = exchange;
		exchange.orderBookListenerKeeper.addExchangeListener(new ExchangeEventListener<OrderBook>(){

			//@Override
			public void onChangeEvent(OrderBook book) {
				synchronized(list)
				{
					list.add(book);
				}
				synchronized(MarketAdaptor.this)
				{
					MarketAdaptor.this.notify();
				}
			}
			
		});
		
		exchange.tradeListenerKeeper.addExchangeListener(new ExchangeEventListener<Trade>(){

			//@Override
			public void onChangeEvent(Trade trade) {
				synchronized(list)
				{
					list.add(trade);
				}
				synchronized(MarketAdaptor.this)
				{
					MarketAdaptor.this.notify();
				}
			}
			
		});
		
		exchange.orderListenerKeeper.addExchangeListener(new ExchangeEventListener<Order>(){

			//@Override
			public void onChangeEvent(Order order) {
				synchronized(list)
				{
					list.add(order);
				}
				synchronized(MarketAdaptor.this)
				{
					MarketAdaptor.this.notify();
				}
			}
			
		});
		
		thread = new Thread(this);  
		thread.start();
		

	}


	public void close()
	{
		running = false;
		try {
			thread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private boolean running = true;
	public void run() {
		while(running)
		{
			synchronized(this)
			{
				try {
					this.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			synchronized(list)
			{
				while(list.size()>0)
				{
					Object item = list.remove();
					
					if (item instanceof OrderBook)
					{
						OrderBook orderBook = (OrderBook)item;
						ArrayList<ExchangeEventListener<OrderBook>> listeners 
							= orderBookKeeper.getListeners(orderBook.getCode());
						for(ExchangeEventListener<OrderBook> listener: listeners)
						{
							listener.onChangeEvent(orderBook);
						}
					}
					else if (item instanceof Order)
					{
						Order order = (Order)item;
						ArrayList<ExchangeEventListener<Order>> listeners 
							= orderKeeper.getListeners(order.getCode());
						for(ExchangeEventListener<Order> listener: listeners)
						{
							listener.onChangeEvent(order);
						}						
					}
					else if (item instanceof Trade)
					{
						Trade trade = (Trade)item;
						ArrayList<ExchangeEventListener<Trade>> listeners 
							= tradeKeeper.getListeners(trade.getAskOrder().getCode());
						for(ExchangeEventListener<Trade> listener: listeners)
						{
							listener.onChangeEvent(trade);
						}						
					}
					else
					{
						System.out.println("I don't expect to see this");
					}
					
				}
			}


		}
		
	}
	

	
}
