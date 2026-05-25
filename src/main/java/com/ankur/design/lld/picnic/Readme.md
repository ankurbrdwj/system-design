# Picnic Recruitment Task #

Welcome to the Picnic Java programming assignment! In this document you will find the specification
for the assignment, and instructions how to get started and hand your solution in. Please read the
entire document carefully before you start.

# Table of Contents

1. [Task Description](#description)
    1. [Provided Functionality](#provided-functionality)
    2. [Functional Requirements](#requirements)
    3. [Specifications](#specifications)
        1. [Input Stream Specification](#input-stream)
        2. [Output Stream Specification](#output-stream)
    4. [Notes](#notes)
    5. [Tips](#tips)
2. [Submission Process](#submission-process)
    1. [How to get started](#get-started)
    2. [How to submit your solution](#submit)

## Task Description <a name="description"></a> ##

Your task is to read and process customer _orders_ from an `InputStream`, and process them before
writing them to an `OutputStream`. Reading orders is limited by time, or by the number of read
orders. Processing of orders consists of filtering, grouping and transformation.

The input stream sends each order as a single line of JSON adhering to the format described below,
but does not guarantee orders to be sorted or arrive in predictable intervals.

### Provided Functionality <a name="provided-functionality"></a> ###

As part of your task, you should extend the provided project in this repository. It is
a [Maven][maven] project and the code is structured as follows:

* Package `tech.picnic.assignment.api` contains classes that must **NOT** be modified. Of special
  interest in this package are the following two interfaces:
    * `OrderStreamProcessorFactory`: you will be asked to create an implementation of this interface.
      We've created a skeleton for you already and made it service-loadable with the `@AutoService`
      annotation in order to expedite our review process. You must not touch this annotation and
      also don't need to be aware of what it does.
    * `OrderStreamProcessor`: most of the assignment revolves around creating an implementation of
      this interface which meets various criteria. The aforementioned factory should be able to
      create one or more of these processor instances.
* Package `tech.picnic.assignment.impl` contains a skeleton implementation of the
  aforementioned `OrderStreamProcessorFactory` interface, whereas package `tech.picnic.assignment.impl.model`
  contains an incomplete `Order` represented as a [Record][java-record] (of course you are also free to
  replace it with a different approach). Your task is to change the code in these packages to meet the
  criteria described below.
* The `ObjectMapperFactory` provides a bare-bone
  [Jackson `ObjectMapper`][object-mapper] that can be used for JSON
  (de-)serialization, though you are free to choose another approach.
* The `pom.xml` file defines how [Maven][maven] can build the project. Some aspects of this file
  must not be changed; those are indicated using comments. Make sure that your project builds
  using `mvn clean verify`.
* Under `src/test` a disabled test is defined. Consider getting it to pass. At Picnic, we like
  unit-tested code. Thus, you are free to add more!

### Functional Requirements <a name="requirements"></a> ###

* An `OrderStreamProcessorFactory` implementation which produces
  `OrderStreamProcessor` instances capable of reading the order input stream described above.
* The factory should produce processors that respect the given `maxOrders` and
  `maxTime` parameters. For example,
  `myOrderStreamProcessorFactory.createProcessor(100, Duration.ofSeconds(30))` should produce
  an `OrderStreamProcessor` which for every invocation of `OrderStreamProcessor#process()`
  should read orders either for up to 30 seconds or until the order limit of 100 is
  reached, whichever condition is met first. The orders should then be processed and returned.
* A processor should process the orders it receives as follows:
    - Only orders with statuses `delivered` and `cancelled` are retained. Orders with other statuses
      still count towards the `maxOrders` limit but are otherwise ignored.
    - Orders must be grouped by delivery ID (see the grouped output format below).
    - A delivery's status can be either `delivered` or `cancelled`. It is delivered, iff at least
      one of its orders has status `delivered`.
    - A delivery's total amount is the sum of all orders with status
      `delivered`, or `null` if none exists.
    - Deliveries must be sorted chronologically (ascending) by their
      `delivery_time` timestamp, breaking ties by ID (ascending).
    - Orders must be sorted by their ID (descending).
* A processor must write the result of the aforementioned filter, group and sort operations to the
  provided `OutputStream` according to the format described below.

### Specifications <a name="specifications"></a> ###

#### Input Stream Specification <a name="input-stream"></a> ####

The input stream is specified as follows:

* No assumptions should be made about the source of this stream of messages. Perhaps they are
  read from a file, perhaps they arrive over a TCP stream from the other side of the world, or
  perhaps they are auto-generated by a test framework. ;)
* No assumptions should be made about the speed at which messages arrive. Multiple messages may arrive
  in brief succession, but it could also be that no messages arrive for extended periods of time.
* If no order messages are sent for a while, 'keep-alive' messages consisting of a single `\n` may be sent
  to ensure the input stream does not end prematurely.
* Each order adheres to the JSON type specification described below.
* Each order comprises a single line of JSON, terminated by a newline (`\n`).
* If the timeout expires while reading an incoming order, this order should be discarded. All previous
  orders should be processed and returned as specified. Your solution should never exceed the timeout duration while
  reading from the input stream.

Order JSON type specification

|     Field      | JSON Type |                  Description                  |
|:--------------:|:---------:|:---------------------------------------------:|
|   `order_id`   |  String   |             A unique identifier.              |
| `order_status` |  String   | Either `created`, `delivered` or `cancelled`. |
|   `delivery`   |  Object   |          Delivery object, see below.          |
|    `amount`    |  Number   |   The paid amount in cents; can be `null`.    |

Delivery JSON type specification

|      Field      | JSON Type |                                             Description                                             |
|:---------------:|:---------:|:---------------------------------------------------------------------------------------------------:|
|  `delivery_id`  |  String   |                                        A unique identifier.                                         |
| `delivery_time` |  String   | The time this delivery is scheduled for; formatted as an [ISO 8601][iso-8601] UTC date-time string. |

The following JSON object is an example of the kind of order that may be received from
the `InputStream`. Note how it matches the specification above. There is one difference: you may
assume that the orders read from the `InputStream` are on a single line, i.e. they are not formatted
using line breaks.

```json
{
  "order_id": "1234567890",
  "order_status": "delivered",
  "delivery": {
    "delivery_id": "d923jd29j91d1gh6",
    "delivery_time": "2022-05-20T11:50:48Z"
  },
  "amount": 6477
}
```

#### Output Stream Specification <a name="output-stream"></a> ####

A processor must write the result to the provided `OutputStream` according the following JSON
format:

```json
[
  {
    "delivery_id": "d923jd29j91d1gh6",
    "delivery_time": "2022-05-20T11:50:48Z",
    "delivery_status": "delivered",
    "orders": [
      {
        "order_id": "1234567890",
        "amount": 6477
      },
      ...
      more
      orders
      here
      ...
    ],
    "total_amount": 6477
  },
  ...
  more
  deliveries
  here
  ...
]
```

For a complete example of expected input and corresponding output, compare the contents of the
following two provided files:

- [`happy-path-input.json-stream`](./src/test/resources/tech/picnic/assignment/impl/happy-path-input.json-stream)
- [`happy-path-output.json`](./src/test/resources/tech/picnic/assignment/impl/happy-path-output.json)

### Notes <a name="notes"></a> ###

* Feel free to add or modify dependencies in `pom.xml`.
* Do **not** modify the existing plugin configurations in `pom.xml` (though you can add more if
  needed).
* Do **not** modify the `tech.picnic.assignment.api` package.

### Tips <a name="tips"></a> ###

* We value clean, readable, modern, and idiomatic Java. Your code will be reviewed by other
  developers, so make sure it is easy to follow and well-structured.
* Avoid doing manual JSON parsing. It's prone to errors and hard to read.
* Don't feel the need to over-engineer your solution. We don't expect you to build an entire system
  that can scale to billions of orders. Your solution should be tailored to the problem statement.
  We prefer concise and simple solutions over lengthy ones. However, it should be straightforward to
  let the program behave differently, such as have a different timeout, filter on a different order
  status, etc.
* If at any point you are not sure how to interpret the requirements, feel free to make a reasonable
  assumption on what is meant. When you submit your solution, you can use the pull request description
  to list the assumptions you made and explain why you made them. The reviewers will then be able to
  take those assumptions into account when reviewing adherence to the requirements.

## Submission Process <a name="submission-process"></a> ##

### How to get started <a name="get-started"></a> ###

Make a local clone of this repository on your machine, and do your work on a branch other
than `master`. Do not make any changes to the `master` branch.

Typically, candidates spend around four hours and write roughly 500 lines of non-test code to complete the
assignment. It is however possible to write a "perfect" solution using much less code than that. Of course,
we won't measure or in any way take into account the time you spent.

### How to submit your solution <a name="submit"></a> ###

1. Push your solution to `origin/<your-branch-name>`, and create a pull request to merge your
   changes back into the `master` branch. Please do not merge your pull request. Only you and developers at Picnic can
   see this pull request. We highly recommend you to fill in the template that will show up when opening the pull
   request to help us review your submission. It is not required to achieve a satisfactory result, but it can help
   the reviewers understand your reasoning behind choices made.
2. [Create and add][github-labels] the label `done` to your pull request. This will notify us that
   your code is ready to be reviewed. Please do **NOT** publish your solution on a publicly
   available location (such as a public GitHub repository, your personal website, etc.). Also,
   please refrain from tagging or assigning specific Picnic employees as reviewers of your PR; we
   have an internal round-robin system to determine who will review your code.
3. Once your pull request is created and labelled, **please send the link to the recruiter**, and we will perform an
   internal review.
4. In the next step, if we deem the assignment satisfactory, you are invited for a quick call to look over your solution
   together with the reviewers. Make sure to save a local copy (you will lose access to the repository after creating
   the pull request), you will be asked to keep it in front of you or to share your screen.

This process closely mimics our actual development and review cycle. We hope you enjoy it and wish you much success!


[iso-8601]: https://en.wikipedia.org/wiki/ISO_8601

[maven]: https://maven.apache.org

[github-labels]: https://help.github.com/articles/about-labels

[object-mapper]: https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-databind/2.17.0/index.html

[java-record]: https://docs.oracle.com/en/java/javase/17/language/records.html
