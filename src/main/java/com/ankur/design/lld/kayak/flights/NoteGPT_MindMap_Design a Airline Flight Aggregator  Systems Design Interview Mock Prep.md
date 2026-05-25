# Flight Search and Booking System Design

## ✈️ Key Insights

The system aims to design a flight search and booking platform similar to kayak.com, google flights, or booking.com, focusing on five partner airlines with approximately 100 hub cities globally.

Each airline operates about 3-10 flights per day per hub city, resulting in roughly 1,500 flights daily, with around 100 seats per flight, leading to significant daily and monthly search and purchase volumes.

Flight data must be highly consistent, while pricing is variable and typically updated every few hours, not in real-time.

The user experience includes search queries filtered by price, time, and ratings, with results sorted from lowest to highest price to accommodate price elasticity in the airline industry.

To handle system throughput and reduce latency, a vertically scalable SQL database is proposed, augmented with caching strategies (e.g., Redis or Memcached) for popular search queries based on user metadata and geospatial data.

Caching is optimized using Least Recently Used (LRU) eviction policies and region-based replication to improve response times globally.

The architecture involves an application layer interfacing with both caching layers and airline data exchanges through APIs, with user metadata driving intelligent cache updates to reflect popular routes and searches.

## 💡 System Scope and Requirements

Design a backend system for flight search and ticket pricing aggregation.

Focus on 5 partner airlines and ~100 hub cities globally.

Support ~3-10 flights per day per airline per city.

Ensure high data consistency for flight availability.

Pricing is variable with periodic updates (approx. every 4 hours).

No user login or access control needed at this stage.

Provide search filters: price, rating, time of day, etc.

Sort search results by price (lowest to highest).

## 📊 Traffic and Load Estimations

Estimated 1,500 flights daily, 100 seats each → ~150,000 ticket sales per day.

Approx. 10 searches per purchase → ~1.5 million daily search queries.

Roughly 222.5 million search queries per month.

Average search query rate: ~10 queries per second.

System manageable with vertical scaling before horizontal scaling is needed.

## 🗄️ Database and Caching Design

Use SQL database for flight and user data.

Separate pricing data possibly stored in NoSQL for frequent updates.

Implement caching for popular routes using Redis/Memcached.

Cache based on user geospatial metadata and popular flight routes.

Employ LRU eviction to manage cache size and keep popular data fresh.

Cache replication across geographic regions to reduce latency globally.

Use pub/sub or update services for cache synchronization between regions.

## ⚙️ High-Level Architecture

Client interacts with an application layer of worker services.

Application layer queries cache first; falls back to database if cache miss.

External airline data API feeds flight and pricing updates into the database.

User metadata service tracks search patterns and geospatial info.

Cache updated dynamically based on user queries and popularity metrics.

Regional caches replicate popular data using update services to optimize latency.

## 🔍 Search Query Handling

Search query includes departure/arrival cities, dates, price filters, and user location.

SQL queries filter flights by geospatial data and time range.

Query results limited to top 100 to ensure fast response.

Search results cached if popular, reducing database load.

User metadata helps personalize cache contents.

## 🔄 Cache Update and Replication Strategy

Cache stores most frequent routes per user location.

Least Recently Used (LRU) eviction removes unpopular routes.

Replication service periodically syncs cache data across regions.

Routes are treated as directional (departure → arrival).

Return flight data updated separately and synchronized.

Replication uses pub/sub messaging based on geography.

