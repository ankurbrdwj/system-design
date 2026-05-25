Summary
The video presents an in-depth overview of the evolution of Skyscanner’s Browse product, a system designed to provide indicative flight prices to travelers and partners worldwide. The speaker, Item, describes a multi-year journey of architectural redesigns undertaken to handle the ever-increasing load on their systems due to continuous growth in user demand and data volume.

Core Concepts and Challenges
Browse Product Functionality:
Provides indicative prices used across Skyscanner’s platform, including map views and Google Ads, processing:

Hundreds of thousands of price updates per second
Thousands of queries per second
A unified global dataset
Supports 64 different query types (e.g., cheapest day, month, or destination)
Initial Architecture:
Started with a simple relational database storing every price quote as a row. This worked initially but failed as the table grew, causing queries to slow down significantly.

Second Architecture Attempt:
Precomputed results for each query type and stored them in multiple SQL tables with cascading updates. Despite hardware improvements (fusion-io drives, GPUs), the bottleneck shifted to excessive write operations—every price update triggered many writes across tables.

Third Architecture - Batch Processing:
Moved to buffering price quotes, persisting them as files on Amazon S3, and running batch Spark jobs every 30 minutes to aggregate data. The output was stored in a read-optimized database. Challenges:

Keeping the entire dataset in memory was expensive.
Using Amazon Aurora (managed MySQL/Postgres) faced a single master write bottleneck.
Risk of corrupted aggregated data with no easy rollback or recovery.

Final Architecture - S3 as a Queryable Data Store:
Abandoned traditional databases for the read store. Instead, they:

Persisted the entire dataset in protobuf-encoded files on S3 every 30 minutes.
Created a partition map index to allow the API to efficiently fetch exact byte ranges from S3 files.
Used a Redis cache for frequently requested data to improve performance.
This unconventional architecture was surprisingly effective, leveraging cheaper storage costs versus database management complexity and providing easier deployment and rollback capabilities.

Quantitative Overview
Metric	Description
Price updates per second	Hundreds of thousands
Queries per second	Thousands
Query types supported	64
Dataset size	Terabytes range
Batch job frequency	Every 30 minutes
Key Insights and Lessons Learned
System scalability is crucial: Design for systems that can scale faster than your organization can maintain.
Optimize late and smart: Avoid premature optimization; the later you optimize, the more impactful it can be.
Redundancy and fault tolerance: Systems must be capable and redundant to handle failures gracefully.
Embrace unconventional solutions: Using S3 as a high-throughput, queryable data store, though initially counterintuitive, can be practical and cost-effective.
Operational simplicity matters: Eliminating the need for backups, migrations, and complex database maintenance significantly reduces operational overhead.
Trade-offs between compute and storage: It is sometimes better to “burn money” on storage rather than complicate the system with difficult-to-scale databases.
Conclusion
Skyscanner’s Browse product evolved through multiple architectural iterations driven by scaling and performance challenges. The final design uses S3 as a primary data store combined with protobuf encoding and partition indexing, providing a scalable, maintainable, and cost-effective solution. This journey highlights the value of continuous experimentation, embracing novel architectures, and prioritizing operational simplicity in large-scale data systems.

Keywords
Browse product
Indicative prices
Horizontal scalability
Batch processing
Amazon S3
Protobuf encoding
Partition map index
Redis cache
Data aggregation
System design lessons