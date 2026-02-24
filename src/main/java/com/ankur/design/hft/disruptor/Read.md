Dissecting the Disruptor: How do I read from the ring buffer?
June 28, 2011
The next in the series of understanding the Disruptor pattern developed at LMAX.

After the last post we all understand ring buffers and how awesome they are.  Unfortunately for you, I have not said anything about how to actually populate them or read from them when you're using the Disruptor.

ConsumerBarriers and Consumers
I'm going to approach this slightly backwards, because it's probably easier to understand in the long run.  Assuming that some magic has populated it: how do you read something from the ring buffer?



(OK, I'm starting to regret using Paint/Gimp.  Although it's an excellent excuse to purchase a graphics tablet if I do continue down this road.  Also UML gurus are probably cursing my name right now.)

Your Consumer is the thread that wants to get something off the buffer.  It has access to a ConsumerBarrier, which is created by the RingBuffer and interacts with it on behalf of the Consumer.  While the ring buffer obviously needs a sequence number to figure out what the next available slot is, the consumer also needs to know which sequence number it's up to - each consumer needs to be able to figure out which sequence number it's expecting to see next.  So in the case above, the consumer has dealt with everything in the ring buffer up to and including 8, so it's expecting to see 9 next.

The consumer calls waitFor on the ConsumerBarrier with the sequence number it wants next

    final long availableSeq = consumerBarrier.waitFor(nextSequence);

and the ConsumerBarrier returns the highest sequence number available in the ring buffer - in the example above, 12.  The ConsumerBarrier has a WaitStrategy which it uses to decide how to wait for this sequence number - I won't go into details of that right now, the code has comments in outlining the advantages and disadvantages of each.

Now what?
So the consumer has been hanging around waiting for more stuff to get written to the ring buffer, and it's been told what has been written - entries 9, 10, 11 and 12.  Now they're there, the consumer can ask the ConsumerBarrier to fetch them.



As it's fetching them, the Consumer is updating its own cursor.

You should start to get a feel for how this helps to smooth latency spikes - instead of asking "Can I have the next one yet?  How about now?  Now?" for every individual item, the Consumer simply says "Let me know when you've got more than this number", and is told in return how many more entries it can grab.  Because these new entries have definitely been written (the ring buffer's sequence has been updated), and because the only things trying to get to these entries can only read them and not write to them, this can be done without locks.  Which is nice.  Not only is it safer and easier to code against, it's much faster not to use a lock.

And the added bonus - you can have multiple Consumers reading off the same RingBuffer, with no need for locks and no need for additional queues to coordinate between the different threads.  So you can really run your processing in parallel with the Disruptor coordinating the effort.

The BatchConsumer is an example of consumer code, and if you implement the BatchHandler you can get the BatchConsumer to do the heavy lifting I've outlined above.  Then it's easy to deal with the whole batch of entries processed (e.g. from 9-12 above) without having to fetch each one individually.

EDIT: Note that version 2.0 of the Disruptor uses different names to the ones in this article.  Please see my summary of the changes if you are confused about class names.