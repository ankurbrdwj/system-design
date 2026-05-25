[00:29]
The discussion begins with clarifying the project scope: designing a flight search and booking system similar to kayak.com, Google Flights, or booking.com. The key considerations identified are:

The system must handle financial transactions, implying strict consistency and reliability.
It involves integration with legacy airline data systems for flight information.
A question is posed regarding the scope of airline partnerships—whether the system will include all airlines or only select partners.
[01:20]
For modeling purposes, the assumption is made of five partner airlines, which is considered a manageable scope for initial design.

Each airline is assumed to operate flights connecting about 100 to 200 global hub cities—a rough estimate to guide further calculations.
The number of flights per city per airline is roughly estimated between 5 to 10 flights daily, with a working assumption of 3 to 5 flights per city per airline per day to keep calculations conservative.
[03:35]
Functional and non-functional requirements are discussed with emphasis on:

High data consistency for flight information due to transactional nature (ticket booking).
Pricing information is expected to be variable and possibly updated periodically, not necessarily real-time.
The system is primarily a back-end ticketing and flight information service, with no explicit requirement for user login or access control at this stage.
There is interest in understanding if features like ticket holding or temporary reservation windows (similar to movie ticket systems) are desired; the answer leans towards a rudimentary design without such features for now.
[06:26]
The conversation shifts to pricing strategies:

Pricing is dynamic and competitive, with possible personalization based on user metadata (e.g., cookies indicating interest over time).
The system might use personalization to either offer sales promotions or optimize revenue by adjusting prices based on user behavior patterns.
[08:15]
User interaction flow is clarified:

Users search flights by specifying departure, arrival locations and dates.
The system returns search results sorted by price, from lowest to highest, consistent with standard industry practice.
Additional search filters such as departure times and user preferences will be supported to refine results.
[11:12]
Initial load estimates and throughput calculations begin:

With 5 airlines, 100 cities, and 3 flights daily per city per airline, there are about 1500 flights per day.
Each flight is assumed to have roughly 100 seats, with a typical occupancy close to 99%.
This yields approximately 15,000 ticket sales per day.
[17:16]
Search query volume estimation:

On average, a buyer performs about 10 search queries before purchasing.
This results in approximately 150,000 search queries per day.
Applying a Pareto principle factor (2 to 5), the volume could be as high as 750,000 daily queries in a peak scenario.
Metric	Value	Notes
Airlines	5	Partner airlines
Cities (hubs)	100	Global hub cities
Flights per airline per city	3	Conservative estimate
Total flights per day	1,500	Calculated as 5 x 100 x 3
Seats per flight	100	Average plane capacity
Daily seat sales	15,000	99% occupancy assumed
Searches per purchase	10	Average user behavior
Daily search queries	150,000 to 750,000	Range considering user pattern
[19:35]
Throughput per second:

Given about 2.5 million seconds in a month, the system expects roughly 1 to 10 search queries per second.
This load is significantly lower than large-scale systems like Twitter or Google, indicating the system can be managed with vertical scaling initially.
The system design can prioritize SQL databases for transactional consistency, with potential caching layers to optimize read latency.
[22:08]
Database design considerations:

A relational (SQL) database is recommended for the core flight and ticketing data due to the need for consistency and structured queries.
A NoSQL or in-memory datastore (e.g., Redis, Memcached) can handle frequently updated pricing information and caching to improve query speed.
Flight prices are assumed to update approximately every four hours, not in real-time, allowing caching strategies to be effective.
[24:45]
Caching strategy:

Caches will store popular flight search results, particularly for the top 20% of routes following the Pareto principle.
User metadata (e.g., recent searches, geolocation) will guide personalized caching, storing relevant flight data for locations frequently searched by the user.
Caching will reduce database load and improve response latency five- to tenfold compared to querying the backend directly.
[30:08]
High-level system architecture is sketched out:

Component	Description
Client	User interface where flight searches are initiated
Application Layer	Hosts business logic, processes search queries and caching
Cache Layer	In-memory datastore (e.g., Redis) for fast retrieval of common/popular queries
External Airline APIs	Source for raw flight and pricing data from partner airlines
Database Layer	Relational databases storing flights, pricing, and user metadata
User Metadata Service	Tracks user geolocation and search history to optimize cache
The system interfaces with external airline APIs or data exchanges, similar to stock exchange models, to retrieve flight and pricing data.
The application layer handles caching logic, query routing, and updates to ensure low latency and data freshness.
[34:44]
Cache update and eviction policies:

The system implements a Least Recently Used (LRU) eviction policy, removing unpopular or stale flight data from the cache.
Popular routes (e.g., New York to LAX) are cached based on aggregated user geospatial data and search patterns.
Caching decisions consider both user location (user geo) and popular flight routes (route geo) to maximize cache hit rates across different regions.
[42:30]
Query mechanics and data flow:

Flight search queries include parameters such as departure city, arrival city, date range, and user geolocation.
The system first checks the cache for matching queries; if found, it returns cached results immediately.
If not cached, the query is executed against the SQL database, and the result is returned and recorded in the user metadata service.
When a route becomes popular enough, it is added to the cache to improve response times for subsequent users.
Example pseudo-SQL query structure:

SELECT
∗
FROM flights
WHERE lat
=
p
1
AND long
=
p
2
AND date BETWEEN
d
a
t
e
a
AND
d
a
t
e
b
LIMIT
100
SELECT∗FROM flightsWHERE lat=p
1
​
AND long=p
2
​
AND date BETWEEN date
a
​
AND date
b
​
LIMIT 100

p
1
,
p
2
p
1
​
,p
2
​
represent latitude and longitude parameters.
Date filters include both departure and return flight windows.
[44:27]
Cache design refinement:

To balance performance and accuracy, the caching system uses a combination of user geo and route geo data.
For example, users in a city searching popular routes benefit from cached data aggregated by location and route popularity.
This multi-dimensional caching improves cache hits even if users are geographically dispersed but interested in common routes.
[49:31]
Replication and regional caching:

The system architecture supports regional cache instances (e.g., North America, Europe) to reduce latency for users in different geographical zones.
A replication or updater service synchronizes popular flight data across regions on a periodic basis (e.g., hourly or bi-hourly).
This service uses a pub/sub model with topics based on geographical location or city pairs to propagate cache updates efficiently.
[51:20]
Handling round-trip flights and bidirectional routes:

Since flights are typically round-trip, separate cache updates handle departure and return flights.
The updater service consolidates data from both directions (e.g., Toronto to Berlin and Berlin to Toronto) to avoid redundant queries.
This optimized data handling further reduces backend load and improves cache freshness.
[52:38]
Final notes:

The system design is modular, allowing replication of the core components for different geographical regions globally.
The combination of vertical scaling SQL databases, strategic caching, and metadata-driven personalization addresses both consistency and latency requirements.
The design currently omits user authentication and advanced ticket-holding features but can be extended as needed.
Key Insights and Conclusions
Core system designed as a flight aggregator and ticketing backend with high consistency for flight data and variable, cache-friendly pricing updates.
Assumed scope: 5 partner airlines, 100 hub cities, 3 flights per city per airline daily, 100 seats per flight.
Estimated load: up to 10 search queries per second, manageable with vertical scaling and caching.
SQL database for transactional flight and ticket data, with an in-memory cache (Redis/Memcached) for popular queries and pricing data.
Caching informed by user geolocation and route popularity, employing LRU eviction.
Architecture supports regional cache replication and synchronization via a pub/sub updater service.
Flight price updates assumed every 4 hours, not real-time, relaxing cache invalidation constraints.
The design balances data consistency, latency, and scalability while remaining extensible for future features.
This comprehensive analysis provides a foundation for a scalable, performant flight search and booking backend system grounded entirely in the source transcript.

Smart Summary
