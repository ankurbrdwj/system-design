### Summary of Video Transcript: REST API Design Pitfalls by Victor

---

#### [00:00:01 ~ 00:01:15] Introduction

Victor introduces himself as a Java expert with 18 years of experience, specializing in Spring Framework for over 10 years. He expresses excitement about presenting at Spring IO in Barcelona, sharing that his talk focuses on common pitfalls in REST API design. He mentions his involvement in workshops and webinars on writing clean, maintainable code.

---

#### [00:01:18 ~ 00:04:31] Backwards Compatibility and Breaking Changes in REST APIs

Victor begins with a foundational principle from John Postel, a TCP/IP creator: systems should be **conservative in what they do but liberal in what they accept**. This principle essentially means APIs should be **backwards compatible** to avoid breaking clients.

He presents a real API request and response example and invites the audience to suggest changes that would cause breaking changes requiring client upgrades. Examples of breaking changes discussed:

- Making an optional field mandatory (e.g., phone number).
- Changing the type of a field (e.g., age from number to string).
- Renaming properties.
- Changing data formats (e.g., date formats).
- Adding or changing validation rules.
- Modifying enums (e.g., removing or renaming enum values).

Victor emphasizes that **adding new optional fields or outputs is not a breaking change** and is safe for backwards compatibility. However, evolving data formats or field structures in inconsistent ways (such as accepting either a single string or a complex structured phone object) leads to *semantic technical debt* and API degradation.

He notes that at some point, when maintaining backwards compatibility becomes too complex, teams introduce a **new API version (e.g., v2)**. However, this often leads to:

- Code duplication (copy-pasting DTOs and mappers).
- Increased maintenance burden.
- The risk of supporting multiple parallel API versions (he recommends supporting at most two).

---

#### [00:05:15 ~ 00:08:28] Managing API Versions and Client Upgrades

Victor discusses strategies to identify and manage API clients:

- Checking OAuth token service accounts.
- Using observability tools like **trace IDs** and distributed tracing (e.g., Zipkin) to analyze which clients use which API fields.
- Tools like Pact.io can help identify which clients depend on specific fields in responses.

He describes the often difficult process of migrating clients to newer API versions, warning against aggressive tactics like throttling or withholding features. Instead, he advocates:

- Providing clear **migration guidelines**.
- Offering **automated migration tools** or rewrite recipes.
- Collaborating directly with clients, especially if in the same organization.

To avoid future breaking changes, Victor warns against prematurely overcomplicating APIs (e.g., representing an email as an array to anticipate future multiple emails). This leads to unnecessary complexity and the dreaded use of **metadata maps** (key-value pairs with arbitrary structure), which signify a lack of schema and cause projects to fail.

Regarding versioning schemes, he critiques the common practice of placing the version in the URI path (e.g., `/v1/`) as unfriendly to clients, citing Roy Fielding’s negative opinion about this approach.

He shares a dramatic viewpoint from a CTO who suggested that if a breaking change is necessary, it might justify creating a **new microservice with its own database** and retiring the old one within a short timeframe—an approach dubbed **design by deletion**.

---

#### [00:08:49 ~ 00:13:19] Contract Testing and Synchronizing Client-Server Changes

Victor stresses the importance of **contract testing** to detect breaking changes early:

- Use your API documentation (OpenAPI/Swagger) as an **approval test** by comparing the current API schema against a stored previous version in Git.
- If a breaking change is detected, the build should fail, prompting fixes before deployment.

For ensuring client-server contract synchronization across different repositories and languages, he recommends:

- **Spring Cloud Contract** or **Pact**, where build failures occur if contracts mismatch.
- In mono-language environments (e.g., Java-only), clients can be distributed as JAR libraries, enabling automatic updates via bots like **Dependabot** or **Renovate** that propose pull requests for dependency upgrades.

This approach helps keep clients close to the server’s API changes, easing migrations and reducing fragmentation.

---

#### [00:13:50 ~ 00:21:44] Performance Pitfalls with REST APIs

Victor highlights a common **performance anti-pattern**: databases with millions of records but only supporting **GET by ID** endpoint, forcing clients to loop over requests in batches. This leads to:

- Network overload.
- High CPU/memory usage.
- Connection churn.

He suggests batch fetching multiple IDs at once, but notes URL length limits (~2,000 characters) restrict GET requests with large query parameters. The common workaround is **POST to get**, where the client sends a list of IDs in the request body despite violating typical REST expectations.

He warns about excessively large responses (e.g., gigabytes of JSON) causing out-of-memory errors, and recommends keeping batch sizes reasonable (e.g., under 1,000 IDs).

Victor discusses the possibility of using GET with a body, which is a recent HTTP standard change but risky due to inconsistent support by proxies and routers.

He shares an example where a client purposely sends one ID at a time to avoid batch usage, prompting the server to rate-limit and encourage batch endpoint usage.

He advises **premature optimization** should be avoided: instead, monitor production for excessive requests and implement batch APIs only when necessary.

Victor then questions the **responsibility boundaries**: why does a client need to download hundreds of thousands of records? Maybe they are doing client-side work that should be server-side.

He also recounts a pen tester’s approach exploiting sequential IDs to crawl data, reinforcing the need for **scrambled or unscannable identifiers**.

---

#### [00:19:45 ~ 00:23:29] Data Aggregation and Microservices

Victor describes a common scenario where clients must call multiple microservices to render a single page, causing performance issues especially on slow networks.

The solution is to introduce a **Back-end for Front-end (BFF)** or aggregator service that fetches data from multiple services and returns a unified response, reducing round trips.

He mentions Netflix’s approach: they use **GraphQL at the boundary** with devices to aggregate data efficiently, backed by gRPC calls to downstream services. However, he cautions that most organizations are *not* Netflix, so adopting GraphQL requires careful consideration.

He also warns about pointless proxying: service A calling service B and just forwarding B’s response without adding value. This wastes resources and complicates the architecture. Teams should analyze call chains (via tracing tools like Zipkin) to identify and remove unnecessary hops.

Victor recalls the performance problems of **stateful APIs** and sticky sessions that prevent load balancers from distributing load evenly, urging stateless API designs for scalability.

---

#### [00:23:50 ~ 00:36:46] Scaling Complexity and Domain Model Exposure

Victor turns to **scalability in complexity**, i.e., keeping the codebase maintainable as features grow.

He presents a typical **domain model** (often an ORM entity) directly exposed as JSON through the REST API, which is problematic because:

- The domain model is tightly coupled to clients; changing it breaks clients.
- Developers end up freezing the domain model to maintain backward compatibility.
- Leaking domain entities in APIs causes maintenance nightmares and often leads to projects failing.

Common “fixes” like using `@JsonIgnore` or adding JSON-specific annotations increase class responsibilities and clutter the code.

He warns of **lazy loading** issues when collections are serialized unintentionally, causing database queries during JSON serialization, which is a notorious anti-pattern.

The recommended approach is to **separate the API contract from the internal model** by introducing DTOs (Data Transfer Objects). This decouples the API schema from the domain model, allowing independent evolution.

However, DTO usage often results in verbose, repetitive code (e.g., `dto.setName(entity.getName())`) which is tedious and error-prone.

---

#### [00:27:46 ~ 00:35:29] Mapping DTOs and Domain Models

Victor discusses **mapping tools** like ModelMapper and MapStruct to automate DTO-to-entity conversions.

- Over half the audience uses such tools.
- MapStruct is preferred because it generates plain Java code (no reflection), making it fast, debuggable, and easy to modify if needed.

However, as APIs evolve, **breaking changes in DTOs should cause build failures** so developers are forced to update mappings immediately.

Excessive complexity in mappings might require switching from automatic to manual mapping for some DTOs.

---

#### [00:30:39 ~ 00:35:29] CQRS and Separating Read/Write Models

Victor highlights that **create and update operations** should use different DTOs than read operations, following **CQRS (Command Query Responsibility Segregation)** principles.

He explains that:

- Using the same DTO for both creates and reads confuses clients (e.g., why send an ID or creation date on create?).
- CQRS allows distinct data structures optimized for command (write) and query (read) operations.

He also applies CQRS between application and database layers, advocating against selecting full ORM entities for queries.

Instead, query only the fields needed using projections (e.g., Spring Data projections), improving performance and clarity.

---

#### [00:35:30 ~ 00:39:22] Eventual Consistency and API Response Design

Victor discusses the trade-off between consistency and availability/performance:

- At some point, strong consistency causes latency and availability challenges.
- Systems may adopt **eventual consistency** with materialized views, caches (Redis, Elasticsearch), or precomputed JSON payloads stored for fast responses.

He advocates for careful consideration of **PUT/POST responses**:

- POST should return identifiers or created objects.
- PUT responses should ideally **not return full enriched data** to avoid mixing read and write models (violating CQRS), exposing sensitive data, or coupling responses.

He warns that mixing read and write responses can cause **security vulnerabilities**, including exposure of Personally Identifiable Information (PII).

---

#### [00:38:49 ~ 00:44:59] UI Design and Concurrency Pitfalls in API Design

Victor critiques typical **edit screens** that bundle many fields, including volatile ones like stock quantity, leading to concurrency issues:

- Users editing stale data can overwrite newer values (lost updates).
- Solutions include:
    - Sending **deltas** (changes) rather than absolute values.
    - Using **optimistic or pessimistic locking**.
    - Rethinking UI to avoid large edit forms.

He suggests moving towards **action buttons** instead of generic edit forms:

- Separate endpoints for distinct user intents (e.g., deactivate item, update cost, adjust stock).
- This aligns API semantics with user intentions and reduces concurrency risks.

However, this requires **full-stack teams** who understand both frontend and backend concerns.

---

#### [00:44:59 ~ 00:48:40] RESTful API Design and PATCH

Victor explains that some client actions require semantics beyond simple CRUD and verbs may appear in URLs (e.g., `/deactivate`):

- Using POST for non-idempotent actions and PUT for idempotent updates reflects HTTP semantics.
- Although REST purists dislike verbs in URLs, practical API design sometimes requires them.

He discusses **PATCH** as a method to partially update resources by sending only changed fields, often using **JSON Patch** format for structured updates.

PATCH is useful for opaque data where backend has no business rules, but it lacks explicit semantics about user intent, which can cause maintainability challenges.

---

#### [00:48:40 ~ 00:50:55] Error Handling and Validation

Victor highlights poor error handling practices:

- Returning one error at a time (e.g., "missing email," then "missing phone") frustrates clients.
- Best practice is to return **all validation errors at once**.
- He references a rarely used standard for error reporting that can help communicate multiple errors.

He also discusses the difference between:

- **Payload validation errors** (client mistakes, 400/422 errors).
- **Business rule errors** (server errors, 500 errors), which might be transient.

Proper error handling improves client experience and reduces retry storms.

---

#### [00:50:55 ~ 00:52:09] Sarcastic Summary and Final Thoughts

Victor humorously summarizes common bad REST API practices to avoid:

- Ignoring backwards compatibility.
- Exposing internal domain models directly.
- Forcing clients to loop over GET by ID calls.
- Reusing the same DTOs everywhere violating separation of concerns.
- Returning full data on PUT responses.
- Relying solely on CRUD operations for complex domains.
- Ignoring GDPR and security concerns.

He closes thanking the audience and sharing his website.

---

### Key Takeaways

- **Backwards compatibility** is critical to avoid forcing client upgrades.
- Use **optional fields** and avoid breaking changes by careful schema evolution.
- Support at most two parallel API versions; consider **design by deletion**.
- Use **observability and contract testing** to monitor API usage and detect breaking changes early.
- Avoid performance anti-patterns like forcing clients to loop over millions of IDs; implement batch endpoints when needed.
- Introduce a **BFF or aggregator** layer to reduce client-side fan-out.
- Never expose internal domain entities in APIs; use **DTOs** and separate read/write models (CQRS).
- Use **mapping tools** judiciously, and fail builds on incompatible changes.
- Carefully design APIs for concurrency and user intent; prefer **action endpoints** over large edit forms.
- Use **PATCH** for partial updates with structured semantics, but beware of losing intent.
- Return **comprehensive validation errors** to clients.
- Be pragmatic in REST design; pure REST constraints sometimes need relaxation for real-world needs.

---

This comprehensive summary captures the detailed insights Victor shared about REST API design pitfalls, focusing on maintainability, performance, versioning, contract management, and user-centric API semantics.