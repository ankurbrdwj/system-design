# WebCurveSim — Stock Exchange Simulator

> Original project: http://code.google.com/p/webcurvesim/
> Author: Dennis Chen (dennis_d_chen@yahoo.com)
> License: Open source (see WEBCURVE-LICENSE.txt)
> Release: r1.0.0 — 3 June 2009

---

## Why an Exchange Simulator?

| Problem | How the simulator solves it |
|---|---|
| Test exchange gateways go offline after hours / weekends | Simulator runs 24/7, you own it |
| Shared test gateways — other brokers interfere with your tests | Isolated, private environment |
| Load testing prohibited on shared test gateways | No restrictions — stress test as hard as you like |
| Algo back-testing requires reproducible market data | Market replay: feed recorded data back exactly |
| Scenario testing (e.g. flash crash, gap fill) | "You own the market" — generate any sequence of orders |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Exchange Core                            │
│                                                                 │
│   Exchange ──────────────► OrderBook (per symbol)              │
│      │                         │                               │
│      │   enterOrder()          ├── bidOrders (price-time sort) │
│      │   cancelOrder()         ├── askOrders (price-time sort) │
│      │   amendOrder()          ├── trades (VWAP, last price)   │
│      │                         └── matching engine             │
│      │                                                         │
│      └── ListenerKeeper<OrderBook>  ← orderBookListenerKeeper  │
│      └── ListenerKeeper<Order>      ← orderListenerKeeper      │
│      └── ListenerKeeper<Trade>      ← tradeListenerKeeper      │
└─────────────────────────────────────────────────────────────────┘
         │                                    │
         ▼                                    ▼
┌─────────────────┐                ┌─────────────────────────────┐
│  MarketAdaptor  │                │    ExchangeFixGateway       │
│                 │                │                             │
│  Async dispatch │                │  QuickFIX/J SocketAcceptor  │
│  via wait/      │                │  FIX 4.2 on port 9876       │
│  notify queue   │                │  ExchangeFixManager handles │
│                 │                │  NewOrderSingle, OrderCancel│
│  subscribeBook()│                │  AmendRequest, ExecReport   │
│  subscribeTrade │                │                             │
│  subscribeOrder │                └─────────────────────────────┘
└─────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    GUI Applications                             │
│                                                                 │
│  ExchangeJFrame  — order entry, market making, replay, FIX GW  │
│  TradeJFrame     — simple OMS connecting via FIX 4.2           │
│  MarketDepthJFrame — live order book depth view                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
src/webcurve/
├── exchange/
│   ├── Exchange.java           Core simulator: enterOrder, cancelOrder, amendOrder
│   └── OrderBook.java          Per-symbol book: price-time priority matching, VWAP
│
├── client/
│   ├── MarketAdaptor.java      Async event dispatcher (wait/notify pattern)
│   ├── ClientOrder.java        OMS-side order (parent/child split)
│   ├── Execution.java          Fill record
│   ├── ExecutionListener.java  Callback interface for fills
│   ├── MarketDepth.java        Aggregated bid/ask depth snapshot
│   ├── MarketParticipant.java  Market maker base class
│   ├── MarketReplay.java       Replays recorded market data files
│   └── MarketTrade.java        Trade event from market feed
│
├── common/
│   ├── Order.java              Order entity (LIMIT/MARKET, BID/ASK, statuses)
│   ├── Trade.java              Trade entity (price, qty, bid/ask order refs)
│   ├── BaseOrder.java          Shared order fields
│   ├── ExchangeEventListener.java  Generic listener interface
│   ├── PriceQty.java           Price+quantity pair
│   ├── PriceStepTable.java     Tick size rules per price range
│   └── QuantityPrice.java      Qty+price pair (reverse of PriceQty)
│
├── fix/
│   ├── ExchangeFixGateway.java QuickFIX/J SocketAcceptor setup (FIX server)
│   ├── ExchangeFixManager.java FIX application: routes FIX messages → Exchange
│   ├── ExchangeFixAgent.java   Per-session FIX state manager
│   └── OrderFixManager.java    Client-side FIX order manager
│
├── marketdata/
│   ├── MarketDataSubscriptionManager.java  Topic-based MD fan-out
│   ├── MarketDataListener.java             MD callback interface
│   ├── Quote.java                          Bid/ask quote snapshot
│   └── Trade.java                          Market trade event
│
├── socket/
│   ├── SocketServer.java       Raw TCP server (non-FIX channel)
│   ├── SocketHandler.java      Per-connection handler
│   └── SocketThread.java       I/O thread per socket
│
├── ui/
│   ├── ExchangeJFrame.java     Exchange GUI: order entry + market simulation + replay
│   ├── TradeJFrame.java        Trade/OMS GUI: parent/child order management
│   ├── MarketDepthJFrame.java  Real-time order book depth display
│   ├── SplitJDialog.java       Child order split dialog
│   └── ExchangeJApplet.java    Applet wrapper
│
├── util/
│   ├── FixUtil.java            FIX tag/value helpers
│   ├── PriceUtils.java         Floating-point price comparison (BigDecimal safe)
│   └── Log4JSetup.java         Logging initialiser
│
├── generic/
│   ├── EventListener.java      Generic event listener
│   └── EventListenerKeeper.java  Listener registry
│
├── test/
│   ├── Test1.java  Direct Exchange API — enter orders, print order book
│   ├── Test2.java  Callback-style updates via ExchangeEventListener
│   ├── Test3.java  Market making simulation
│   ├── Test4.java  Market replay from file
│   └── Test5.java  FIX client connecting to Exchange FIX gateway
│
└── junit/
    ├── TestOrders.java     Unit tests: order entry, cancel, amend
    ├── TestExcution.java   Unit tests: trade matching, fill calculation
    └── TestMarketData.java Unit tests: market data subscription
```

---

## Core Components Explained

### Exchange + OrderBook — The Matching Engine

`Exchange` is the top-level facade. It holds one `OrderBook` per symbol in a `Hashtable<String, OrderBook>`.

`OrderBook` implements price-time priority matching:
- Bid orders sorted **descending** by price (best bid first)
- Ask orders sorted **ascending** by price (best ask first)
- Within the same price level, orders are sorted by arrival time (FIFO)
- Insertion uses **binary search** for O(log n) placement
- On each `enterOrder()`, the engine walks the opposite side and generates fills until the order is fully matched or no more matches exist

```
New BID @ 68.50 arrives:
  Scans askOrders from index 0 (best ask)
  If ask.price <= 68.50 → match, generate Trade, remove ask, reduce bid qty
  Repeat until bid qty = 0 (fully filled) or no more matching asks
  If residual qty > 0 and type = LIMIT → insert into bidOrders
  If residual qty > 0 and type = MARKET → cancel remainder
```

Order outcomes after `enterOrder()`:

| `order.getQuantity()` | `order.getStatus()` | Meaning |
|---|---|---|
| = original qty | NEW | Fully queued, no match |
| 0 < qty < original | FILLING | Partial fill, residual in book |
| 0 | FILLING | Fully filled |

### MarketAdaptor — Asynchronous Event Dispatch

`Exchange` fires events synchronously (on the caller's thread). `MarketAdaptor` wraps it with an async fan-out layer:

```
Exchange thread                 MarketAdaptor thread
──────────────                  ─────────────────────
enterOrder()
  → orderBookListenerKeeper     → list.add(book)
    .updateExchangeListeners()    → MarketAdaptor.notify()
                                     ↓
                                  wait() wakes up
                                  dispatch to per-stock subscribers
                                  subscribeBook("0005.HK", myListener)
```

This separates **matching latency** (on caller thread) from **notification latency** (background thread).

### ExchangeFixGateway — FIX 4.2 Protocol Layer

Uses QuickFIX/J's `SocketAcceptor`. Configuration (`exchange.cfg`):

```ini
ConnectionType=acceptor
SocketAcceptPort=9876
BeginString=FIX.4.2
DataDictionary=FIX42.xml
PersistMessages=N          # in-memory only, no disk store
HeartBtInt=300
ValidOrderTypes=1,2        # 1=Market, 2=Limit
```

`ExchangeFixManager` translates FIX messages to `Exchange` API calls:

| FIX Message | FIX MsgType | Exchange Call |
|---|---|---|
| NewOrderSingle | 35=D | `exchange.enterOrder()` |
| OrderCancelRequest | 35=F | `exchange.cancelOrder()` |
| OrderCancelReplaceRequest | 35=G | `exchange.amendOrder()` |
| ExecutionReport (fill) | 35=8 | sent back to client |
| ExecutionReport (cancel ack) | 35=8 | sent back to client |

---

## Dependencies

| Library | Version | JAR | Purpose |
|---|---|---|---|
| QuickFIX/J | 1.4.0 | `quickfixj-all-1.4.0.jar` | FIX protocol engine (session, parsing, sequencing) |
| Apache MINA | 1.1.0 | `mina-core-1.1.0.jar` | NIO transport layer used by QuickFIX/J |
| SLF4J API | 1.5.6 | `slf4j-api-1.5.6.jar` | Logging facade |
| SLF4J JDK14 | 1.5.6 | `slf4j-jdk14-1.5.6.jar` | Routes SLF4J to java.util.logging |
| JUnit | — | `junit.jar` | Unit testing |

---

## Sample Programs

### Test1 — Direct Exchange API (no FIX)

```java
Exchange exchange = new Exchange();
exchange.enterOrder("0005.HK", Order.TYPE.LIMIT, Order.SIDE.BID,
        3200, 68.50, "broker1", "myOrder1");
OrderBook book = exchange.getBook("0005.HK");
// book.getBestBid(), getBestAsk(), getBidOrders(), getAskOrders()
```

Run: `test1.bat`

### Test2 — Callback Updates

```java
exchange.orderListenerKeeper.addExchangeListener(order -> {
    System.out.println("Order update: " + order.getStatus());
});
exchange.tradeListenerKeeper.addExchangeListener(trade -> {
    System.out.println("Trade: " + trade.getPrice() + " x " + trade.getQuantity());
});
```

Run: `test2.bat`

### Exchange GUI

Full Swing GUI with:
- Interactive order entry (Enter Order button)
- Market making simulation (auto-generates orders to create a realistic market)
- Market replay (feed a recorded data file back through the engine)
- Built-in FIX 4.2 gateway server (clients connect on port 9876)

Run: `exchange.bat`

### Trade GUI (OMS Client)

Connects to the Exchange GUI via FIX 4.2. Demonstrates:
- Create parent orders with "Enter Order"
- Split parent orders into child orders with "Split Order" (allocate qty across venues/prices)
- Amend quantity or price with "Amend Order"
- Cancel parent or child with "Cancel Order"

Run: `trade.bat` (requires Exchange GUI running first)

---

## Running the Tests

```bash
# Windows
test1.bat       # basic Exchange API test
test2.bat       # callback test

# Start FIX server (Exchange GUI)
exchange.bat

# Start FIX client (Trade GUI) in a separate terminal
trade.bat
```

---

## Market Replay

Feed a recorded market data file through the engine for back-testing:

```
# Format (from "Replay Samples.txt"):
# timestamp, symbol, side, price, quantity
```

The `MarketReplay` class reads the file line-by-line and calls `Exchange.enterOrder()` at the recorded timestamps, recreating historical market conditions. Your strategy runs against this replay as if it were live.

---

## Known Limitations

- No validation of stock code, price steps, or lot sizes (would require a reference data database)
- FIX 4.2 only (no FIX 4.4 or FAST/ITCH)
- No persistence — all order state is in-memory, lost on restart
- Quantity check between parent and child orders not enforced (possible overfill)
- Occasional Swing repaint issues when expanding the order tree in the GUI

---

## Key Design Patterns Used

| Pattern | Where |
|---|---|
| Observer / Listener | `Exchange.ListenerKeeper<T>` — notifies all subscribers on order/trade/book events |
| Async Queue (Producer-Consumer) | `MarketAdaptor` — exchange thread produces, adaptor thread consumes and dispatches |
| Strategy | `Order.TYPE` (LIMIT vs MARKET) changes matching behaviour |
| Template Method | `ExchangeFixManager` implements QuickFIX/J's `Application` interface |
| Binary search insertion | `OrderBook.insertOrder()` — O(log n) sorted insertion |