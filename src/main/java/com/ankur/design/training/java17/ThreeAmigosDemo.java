package com.ankur.design.training.java17;

import java.util.List;

/**
 * "The Three Amigos" — Records, Sealed Classes, Pattern Matching
 *
 * Run each section's main() or the top-level main() to see output.
 * Domain: Trade processing (Buy / Sell / Cancel) — mirrors the video examples.
 */
public class ThreeAmigosDemo {

    public static void main(String[] args) {
        Section1_Records.demo();
        Section2_SealedClasses.demo();
        Section3_PatternMatching.demo();
        Section4_OOP_vs_DOOP.demo();
        Section5_Destructuring.demo();
        Section6_NullHandling.demo();
    }


    // ════════════════════════════════════════════════════════════════════════
    // SECTION 1 — Records
    // ════════════════════════════════════════════════════════════════════════

    static class Section1_Records {

        // ── 1a. Basic record — "buy one get five free"
        //    Auto-generates: canonical constructor, component getters,
        //    toString(), equals(), hashCode()

        record Point(int x, int y) {}

        // ── 1b. Record with compact constructor (validation / transformation)
        //    Compact constructor intercepts before the canonical one.
        //    Do NOT assign this.field — the canonical constructor does that.

        record Trade(String ticker, int quantity, double price) {
            Trade {                                         // compact constructor
                if (ticker == null || ticker.isBlank())
                    throw new IllegalArgumentException("ticker required");
                if (quantity <= 0)
                    throw new IllegalArgumentException("quantity must be > 0");
                ticker = ticker.toUpperCase();             // transform before assignment
            }
        }

        // ── 1c. Immutability caveat — shallow only
        //    The record reference is final, but mutable objects inside are not guarded.

        record Portfolio(String owner, List<String> holdings) {
            Portfolio {
                holdings = List.copyOf(holdings);          // defensive copy → truly immutable
            }
        }

        // ── 1d. Records as tuples — group ad-hoc data without a named class

        record Pair<A, B>(A first, B second) {}

        // ── 1e. Records implementing interfaces — polymorphism without inheritance

        interface Describable { String describe(); }

        record Product(String name, double price) implements Describable {
            @Override
            public String describe() {
                return name + " @ $" + price;
            }
        }

        static void demo() {
            System.out.println("\n─── Section 1: Records ───");

            Point p = new Point(3, 4);
            System.out.println(p);               // Point[x=3, y=4]
            System.out.println(p.x() + ", " + p.y()); // component getters — no get prefix

            Trade t = new Trade("aapl", 100, 175.5);
            System.out.println(t);               // ticker auto-uppercased by compact ctor

            Portfolio pf = new Portfolio("Alice", List.of("AAPL", "GOOG"));
            System.out.println(pf);

            Pair<String, Integer> pair = new Pair<>("hello", 42);
            System.out.println(pair);

            Describable d = new Product("Widget", 9.99);
            System.out.println(d.describe());

            // equals/hashCode auto-generated — value-based
            System.out.println(new Point(1, 2).equals(new Point(1, 2))); // true
        }
    }


    // ════════════════════════════════════════════════════════════════════════
    // SECTION 2 — Sealed Classes
    // ════════════════════════════════════════════════════════════════════════

    static class Section2_SealedClasses {

        /**
         * Sealed hierarchy for trade types.
         *
         *  TradeOrder (sealed)
         *    ├── Buy    (final)        ← leaf, no further subclassing
         *    ├── Sell   (final)        ← leaf
         *    └── Block  (sealed)       ← intermediate — further controlled
         *          └── LargeBlock (final)
         *
         * Without sealed: anyone could add a new subtype — exhaustiveness in switch
         * would break silently. Sealed = compiler-enforced closed hierarchy.
         */
        sealed interface TradeOrder permits Buy, Sell, Block {}

        record Buy(String ticker, int qty)  implements TradeOrder {}
        record Sell(String ticker, int qty) implements TradeOrder {}

        sealed interface Block extends TradeOrder permits LargeBlock {}
        record LargeBlock(String ticker, int qty, String desk) implements Block {}

        // non-sealed: reopens for arbitrary extension — use sparingly (experimental code)
        // non-sealed class OpenExtension implements TradeOrder {}

        static void demo() {
            System.out.println("\n─── Section 2: Sealed Classes ───");

            TradeOrder buy  = new Buy("MSFT", 200);
            TradeOrder sell = new Sell("TSLA", 50);
            TradeOrder blk  = new LargeBlock("AMZN", 10_000, "block-desk-1");

            // switch is exhaustive — compiler forces all permits to be handled (no default needed)
            for (TradeOrder order : List.of(buy, sell, blk)) {
                String label = switch (order) {
                    case Buy b         -> "BUY  " + b.ticker() + " x" + b.qty();
                    case Sell s        -> "SELL " + s.ticker() + " x" + s.qty();
                    case LargeBlock lb -> "BLOCK " + lb.ticker() + " desk=" + lb.desk();
                };
                System.out.println(label);
            }
        }
    }


    // ════════════════════════════════════════════════════════════════════════
    // SECTION 3 — Pattern Matching + Enhanced Switch
    // ════════════════════════════════════════════════════════════════════════

    static class Section3_PatternMatching {

        // ── 3a. Pattern matching instanceof — no explicit cast needed

        static String describe(Object obj) {
            if (obj instanceof String s && s.length() > 5) {   // guarded binding
                return "long string: " + s.toUpperCase();
            } else if (obj instanceof Integer i) {
                return "integer: " + i;
            }
            return "other: " + obj;
        }

        // ── 3b. Switch expression with type patterns + guards

        static String classify(Object obj) {
            return switch (obj) {
                case Integer i  when i < 0   -> "negative int: " + i;
                case Integer i               -> "positive int: " + i;
                case String  s  when s.isEmpty() -> "empty string";
                case String  s               -> "string: " + s;
                case Double  d               -> "double: " + d;
                case null                    -> "null value";     // explicit null case
                default                      -> "unknown: " + obj.getClass().getSimpleName();
            };
        }

        // ── 3c. Text blocks (Java 15+) — JSON/SQL without escape noise

        static final String TRADE_JSON_TEMPLATE = """
                {
                    "ticker": "%s",
                    "quantity": %d,
                    "price": %.2f
                }
                """;

        static void demo() {
            System.out.println("\n─── Section 3: Pattern Matching ───");

            System.out.println(describe("Hello World"));  // long string
            System.out.println(describe(42));             // integer
            System.out.println(describe(3.14));           // other

            System.out.println(classify(-5));
            System.out.println(classify(10));
            System.out.println(classify(""));
            System.out.println(classify("hi"));
            System.out.println(classify(null));
            System.out.println(classify(List.of()));

            // text block — indentation stripped to the least-indented line
            System.out.println(TRADE_JSON_TEMPLATE.formatted("AAPL", 100, 175.5));
        }
    }


    // ════════════════════════════════════════════════════════════════════════
    // SECTION 4 — OOP vs DOOP trade processing
    // ════════════════════════════════════════════════════════════════════════

    static class Section4_OOP_vs_DOOP {

        // ── Shared sealed hierarchy used by both approaches ──────────────────

        sealed interface Trade permits OopBuy, OopSell {}
        record OopBuy(String ticker, int qty)  implements Trade {}
        record OopSell(String ticker, int qty) implements Trade {}

        // ── OOP approach ─────────────────────────────────────────────────────
        // Each trade type carries its own audit() — behavior is INTRINSIC.
        // Adding a new type = new class, zero changes to existing code (OCP honored).
        // BUT: parallel auditor hierarchies, factory pattern, boilerplate.

        interface Auditable { String audit(); }

        record BuyWithAudit(String ticker, int qty) implements Auditable {
            @Override
            public String audit() {
                return "[OOP-AUDIT] BUY  " + ticker + " qty=" + qty;
            }
        }

        record SellWithAudit(String ticker, int qty) implements Auditable {
            @Override
            public String audit() {
                return "[OOP-AUDIT] SELL " + ticker + " qty=" + qty;
            }
        }

        // ── DOOP approach ─────────────────────────────────────────────────────
        // Behavior is EXTRINSIC — lives in the switch, not in the data class.
        // No parallel hierarchies, no factory, no visitor.
        // Trade-off: adding new Trade subtype = must update this switch (OCP violated).
        // BUT sealed + no default = compiler error if you forget → compile-time safety.

        static String auditDoop(Trade trade) {
            return switch (trade) {
                case OopBuy  b -> "[DOOP-AUDIT] BUY  " + b.ticker() + " qty=" + b.qty();
                case OopSell s -> "[DOOP-AUDIT] SELL " + s.ticker() + " qty=" + s.qty();
                // no default — sealed type ensures exhaustiveness at compile time
            };
        }

        static void demo() {
            System.out.println("\n─── Section 4: OOP vs DOOP ───");

            // OOP
            List<Auditable> oop = List.of(new BuyWithAudit("GOOG", 10), new SellWithAudit("META", 5));
            oop.forEach(a -> System.out.println(a.audit()));

            // DOOP
            List<Trade> doop = List.of(new OopBuy("GOOG", 10), new OopSell("META", 5));
            doop.forEach(t -> System.out.println(auditDoop(t)));
        }
    }


    // ════════════════════════════════════════════════════════════════════════
    // SECTION 5 — Destructuring (Deconstruction) with Records
    // ════════════════════════════════════════════════════════════════════════

    static class Section5_Destructuring {

        sealed interface Order permits BuyOrder, SellOrder {}
        record BuyOrder(String ticker, int quantity, double limitPrice) implements Order {}
        record SellOrder(String ticker, int quantity)                   implements Order {}

        static String process(Order order) {
            return switch (order) {
                // destructuring: bind components directly in the case pattern
                case BuyOrder(var ticker, var qty, var limit) -> {
                    // qty and limit extracted — no manual .quantity() / .limitPrice() calls
                    yield "Buy %s qty=%d limit=%.2f".formatted(ticker, qty, limit);
                }
                case SellOrder(var ticker, var qty) -> {
                    // only ticker and qty matter — no limit on a sell
                    yield "Sell %s qty=%d".formatted(ticker, qty);
                }
            };
        }

        static void demo() {
            System.out.println("\n─── Section 5: Destructuring ───");
            System.out.println(process(new BuyOrder("NVDA", 50, 850.0)));
            System.out.println(process(new SellOrder("NVDA", 20)));
        }
    }


    // ════════════════════════════════════════════════════════════════════════
    // SECTION 6 — Null handling in pattern matching
    // ════════════════════════════════════════════════════════════════════════

    static class Section6_NullHandling {

        sealed interface Event permits TradeEvent, ErrorEvent, NoEvent {}
        record TradeEvent(String id) implements Event {}
        record ErrorEvent(String msg) implements Event {}
        record NoEvent()              implements Event {}

        static String handle(Event event) {
            return switch (event) {
                case TradeEvent(var id) -> "trade: " + id;
                case ErrorEvent(var msg) -> "error: " + msg;
                case NoEvent _          -> "no event";
                // without null handling here, a null arg would throw NPE
            };
        }

        // Guard against null at the entry point — explicit case null in switch
        static String handleNullable(Event event) {
            return switch (event) {
                case null               -> "received null event — ignored";
                case TradeEvent(var id) -> "trade: " + id;
                case ErrorEvent(var msg) -> "error: " + msg;
                case NoEvent _          -> "no event";
            };
        }

        static void demo() {
            System.out.println("\n─── Section 6: Null Handling ───");
            System.out.println(handle(new TradeEvent("T-001")));
            System.out.println(handle(new ErrorEvent("timeout")));
            System.out.println(handle(new NoEvent()));
            System.out.println(handleNullable(null));  // safe
        }
    }
}
