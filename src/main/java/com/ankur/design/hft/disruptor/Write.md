Dissecting the Disruptor: Writing to the ring buffer
July 04, 2011
This is the missing piece in the end-to-end view of the Disruptor.  Brace yourselves, it's quite long.  But I decided to keep it in a single blog so you could have the context in one place.

The important areas are: not wrapping the ring; informing the consumers; batching for producers; and how multiple producers work.

ProducerBarriers
The Disruptor code has interfaces and helper classes for the Consumers, but there's no interface for your producer, the thing that writes to the ring buffer.  That's because nothing else needs to access your producer, only you need to know about it.  However, like the consuming side, a ProducerBarrier is created by the ring buffer and your producer will use this to write to it.

Writing to the ring buffer involves a two-phase commit.  First, your producer has to claim the next slot on the buffer.  Then, when the producer has finished writing to the slot, it will call commit on the ProducerBarrier.

So let's look at the first bit.  It sounds easy - "get me the next slot on the ring buffer".  Well, from your producer's point of view it is easy.  You simply call nextEntry() on the ProducerBarrier.  This will return you an Entry object which is basically the next slot in the ring buffer.

The ProducerBarrier makes sure the ring buffer doesn't wrap
Under the covers, the ProducerBarrier is doing all the negotiation to figure out what the next slot is, and if you're allowed to write to it yet.



(I'm not convinced the shiny new graphics tablet is helping the clarity of my pictures, but it's fun to use).

For this illustration, we're going to assume there's only one producer writing to the ring buffer.  We will deal with the intricacies of multiple producers later.

The ConsumerTrackingProducerBarrier has a list of all the Consumers that are accessing the ring buffer.  Now to me this seemed a bit odd - I wouldn't expect the ProducerBarrier to know anything about the consuming side. But wait, there is a reason.  Because we don't want the "conflation of concerns" a queue has (it has to track the head and tail which are sometimes the same point), our consumers are responsible for knowing which sequence number they're up to, not the ring buffer.  So, if we want to make sure we don't wrap the buffer, we need to check where the consumers have got to.

In the diagram above, one Consumer is happily at the same point as the highest sequence number (12, highlighted in red/pink). The second Consumer is a bit behind - maybe it's doing I/O operations or something - and it's at sequence number 3.  Therefore consumer 2 has the whole length of the buffer to go before it catches up with consumer 1.

The producer wants to write to the slot on the ring buffer currently occupied by sequence 3, because this slot is the one after the current ring buffer cursor.  But the ProducerBarrier knows it can't write here because a Consumer is using it.  So the ProducerBarrier sits and spins, waiting, until the consumers move on.

Claiming the next slot
Now imagine consumer 2 has finished that batch of entries, and moves its sequence number on. Maybe it got as far as sequence 9 (in real life I expect it will make it as far as 12 because of the way consumer batching works, but that doesn't make the example as interesting).



The diagram above shows what happens when consumer 2 updates to sequence number 9.  I've slimmed down the ConsumerBarrier in this picture because it takes no active part in this scene.

The ProducerBarrier sees that the next slot, the one that had sequence number 3, is now available.  It grabs the Entry that sits in this slot (I've not talked specifically about the Entry class, but it's basically a bucket for stuff you want to put into the ring buffer slot which has a sequence number), sets the sequence number on the Entry to the next sequence number (13) and returns this entry to your producer.  The producer can then write whatever value it wants into this Entry.

Committing the new value
The second phase of the two-stage commit is, well, the commit.



The green represents our newly updated Entry with sequence 13 - yeah, I'm sorry, I'm red-green colour-blind too.  But other colours were even more rubbish.

When the producer has finished writing stuff into the entry it tells the ProducerBarrier to commit it.

The ProducerBarrier waits for the ring buffer cursor to catch up to where we are (for a single producer this will always be a bit pointless - e.g. we know the cursor is already at 12, nothing else is writing to the ring buffer).  Then the ProducerBarrier updates the ring buffer cursor to the sequence number on the updated Entry - 13 in our case.  Next, the ProducerBarrier lets the consumers know there's something new in the buffer.  It does this by poking the WaitStrategy on the ConsumerBarrier - "Oi, wake up! Something happened!" (note - different WaitStrategy implementations deal with this in different ways, depending upon whether it's blocking or not).

Now consumer 1 can get entry 13, consumer 2 can get everything up to and including 13, and they all live happily ever after.

ProducerBarrier batching
Interestingly the disruptor can batch on the producer side as well as on the Consumer side.  Remember when consumer 2 finally got with the programme and found itself at sequence 9?  There is a very cunning thing the ProducerBarrier can do here - it knows the size of the buffer, and it knows where the slowest Consumer is.  So it can figure out which slots are now available.



If the ProducerBarrier knows the ring buffer cursor is at 12, and the slowest Consumer is at 9, it can let producers write to slots 3, 4, 5, 6, 7 and 8 before it needs to check where the consumers are.

Multiple producers
You thought I was done, but there's more.

I slightly lied in some of the above drawings.  I implied that the sequence number the ProducerBarrier deals with comes directly from the ring buffer's cursor.  However, if you look at the code you'll see that it uses the ClaimStrategy to get this.  I skipped this to simplify the diagrams, it's not so important in the single-producer case.

With multiple producers, you need yet another thing tracking a sequence number.  This is the sequence that is available for writing to.  Note that this is not the same as ring-buffer-cursor-plus-one - if you have more than one producer writing to the buffer, it's possible there are entries in the process of being written that haven't been committed yet.


Let's revisit claiming a slot.  Each producer asks the ClaimStrategy for the next available slot.  Producer 1 gets sequence 13, like in the single producer case above.  Producer 2 gets sequence 14, even though the ring buffer cursor is still only pointing to 12, because the ClaimSequence is dishing out the numbers and has been keeping track of what's been allocated.

So each producer has its own slot with a shiny new sequence number.

I'm going colour producer 1 and its slot in green, and producer 2 and its slot in a suspiciously pink-looking purple.


Now imaging producer 1 is away with the fairies, and hasn't got around to committing for whatever reason.  Producer 2 is ready to commit, and asks the ProducerBarrier to do so.

As we saw in the earlier commit diagram, the ProducerBarrier is only going to commit when the ring buffer cursor reaches the slot behind the one it wants to commit into.  In this case, the cursor needs to reach 13 so that we can commit 14.  But we can't, because producer 1 is staring at something shiny and hasn't committed yet.  So the ClaimStrategy sits there spinning until the ring buffer cursor gets to where it should be.


Now producer 1 wakes up from its coma and asks to commit entry 13 (green arrows are sparked by the request from producer 1).  The ProducerBarrier tells the ClaimStrategy to wait for the ring buffer cursor to get to 12, which it already had of course.  So the ring buffer cursor is incremented to 13, and the ProducerBarrier pokes the WaitStrategy to let everything know the ring buffer was updated.  Now the ProducerBarrier can finish the request from producer 2, increment the ring buffer cursor to 14, and let everyone know that we're done.

You'll see that the ring buffer retains the ordering implied by the order of the initial nextEntry() calls, even if the producers finish writing at different times.  It also means that if a producer is causing a pause in writing to the ring buffer, when it unblocks any other pending commits can happen immediately.

Phew.  And I managed to describe all that without mentioning a memory barrier once.


EDIT: The most recent version of the RingBuffer hides away the Producer Barrier.  If you can't see a ProducerBarrier in the code you're looking at, then assume where I say "producer barrier" I mean "ring buffer"

EDIT 2: Note that version 2.0 of the Disruptor uses different names to the ones in this article.  Please see my summary of the changes if you are confused about class names.