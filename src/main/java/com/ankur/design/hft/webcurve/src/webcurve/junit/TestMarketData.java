package webcurve.junit;

import webcurve.common.BaseOrder;
import webcurve.common.ExchangeEventListener;
import webcurve.common.Order;
import webcurve.common.Trade;
import webcurve.exchange.Exchange;
import webcurve.exchange.OrderBook;
import webcurve.test.Test1;
import webcurve.test.Test2;
import junit.framework.TestCase;

public class TestMarketData extends TestCase {
	Exchange exchange = new Exchange();
	String symbol = "0005.HK";
	OrderBook book;
	Trade trade;
	
	public TestMarketData(String name) {
		super(name);
	}

	protected void setUp() throws Exception {
		super.setUp();
		exchange.orderBookListenerKeeper.addExchangeListener(new ExchangeEventListener<OrderBook>(){

			//@Override
			public void onChangeEvent(OrderBook book) {
				TestMarketData.this.book = book;
			}
			
		});
		
		exchange.tradeListenerKeeper.addExchangeListener(new ExchangeEventListener<Trade>(){

			//@Override
			public void onChangeEvent(Trade trade) {
				TestMarketData.this.trade = trade;
			}
			
		});		
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	public void testOrderBook()
	{
		exchange.enterOrder(symbol, Order.TYPE.LIMIT, BaseOrder.SIDE.BID, 2000, 85, "broker1", "CO-00000001");
		assertEquals(2000, book.getBestBidVol());
		exchange.enterOrder(symbol, Order.TYPE.LIMIT, BaseOrder.SIDE.ASK, 4000, 86, "broker1", "CO-00000001");
		assertEquals(2000, book.getBestBidVol());
		assertEquals(4000, book.getBestAskVol());
		exchange.enterOrder(symbol, Order.TYPE.LIMIT, BaseOrder.SIDE.BID, 1000, 86, "broker1", "CO-00000001");
		assertEquals(1000, trade.getQuantity());
	}
}
