Summary of the Video Content: Vectorization, Dark Silicon, and Java Performance
This talk provides an insightful exploration into dark silicon, vector processing, and their implications for modern CPU design, with a practical focus on how these concepts relate to Java performance optimization. The speaker combines hardware fundamentals, architectural challenges, and Java Vector API usage to explain how vectorization can dramatically improve computational speed, especially in data-intensive applications like AI embeddings.

Key Concepts and Timeline of Topics
Time Range	Topic	Key Points
00:00 - 00:04	Introduction and background	Lighthearted opening, disclaimer about unsafe code use at work, speaker’s affiliation with database startup led by Michael Stonebraker.
00:04 - 00:10	Evolution of CPUs and Moore’s Law	- CPUs evolved via complex instruction decoding, branch prediction, and micro-op scheduling.
- Moore’s Law predicts transistor doubling every 1.5-2 years.
- Dennard scaling allowed power and frequency improvements with smaller transistors.
- Dennard scaling is now failing, causing power constraints despite transistor growth.
  00:10 - 00:14	Dark Silicon Phenomenon	- Dark silicon: transistor area that cannot be powered simultaneously due to power limits.
- Only a small fraction of chip area (e.g., ~7%) can be active at once.
- Solutions include smaller CPUs, downclocking parts, heterogeneous cores (performance + efficiency cores), and custom accelerators.
- Examples: Apple M1 chip with custom blocks optimized for specific tasks.
- Specialized instructions and accelerators (e.g., JavaScript FP conversion, string comparison) exist but adoption varies.
  00:14 - 00:18	Vector Registers and Vector Processing Basics	- Vector registers are memory areas with multiple “lanes” or “buckets” for parallel data processing.
- Vector units execute the same operation simultaneously across all lanes (SIMD).
- Vector instructions evolved over time with gaps and inconsistencies in the ISA.
- Vector size (“shape”) and element type define the vector data layout.
- Java Vector API abstracts vector size and element type but requires awareness of underlying hardware capabilities.
  00:18 - 00:27	Java Vector API Demonstration	- Loading data into vectors from arrays or memory segments.
- Use of species constants to specify vector size.
- Operations like addition, multiplication, fused multiply-add (FMA).
- Vectors are mutable value-like objects in Java.
- Debugging and inspecting vector contents is possible, with generated assembly being simple and allocation-free when optimized.
  00:27 - 00:38	Practical Example: Computing Cosine Similarity Using Vectors	- Cosine similarity algorithm involves dot product and magnitude calculation.
- Java Vector API implementation multiplies, accumulates, and reduces vectors.
- Reduction of vector lanes to a scalar is surprisingly expensive.
- Benchmarking shows vectorized code runs approximately 20x faster than scalar implementation on large arrays.
- Vectorization is powerful but requires careful data layout and algorithm design.
  00:38 - 00:47	Data Layout Challenges and Memory Considerations	- Most real-world applications use objects, not primitive arrays.
- Copying data from objects to arrays for vector processing is costly and defeats purpose.
- Columnar data layout (e.g., columnar databases) is better for vectorization.
- Compression in columnar data complicates direct vector processing due to decompression overhead.
- Memory bandwidth often limits vectorized algorithms more than CPU compute.
- Trade-offs exist between compression, decompression, and vector processing speed.
  00:47 - 00:53	Advanced Vector Instruction Features	- Evolution from MMX to SSE, AVX, AVX-512 with increasing register sizes (up to 512 bits).
- Mask registers (k0-k7) allow selective lane activation, enabling partial vector operations safely.
- Shuffles enable rearranging vector elements.
- Gather/scatter instructions allow non-contiguous memory access but are slower.
- Instruction latency (delay before result ready) and throughput (operations per cycle) are critical for performance.
- Superscalar architecture can pipeline instructions for efficiency.
- Efficient vectorization requires operating on large data chunks to amortize overhead.
  00:53 - 00:55	Java-Specific Vectorization Limitations	- Java vectors are real objects unless optimized away, potentially causing huge allocations and performance cliffs.
- Future improvements expected with Valhalla project enabling value objects to eliminate this overhead.
- Vector registers persist across operations unless context switches or explicit zeroing occur.
- JVM generates assembly instructions to load and operate on vector registers directly.
  Core Insights
  Dark silicon is a fundamental constraint on modern chip design caused by power limits that prevent activating all transistors simultaneously, leading to heterogeneous and specialized cores.
  Vector processing uses SIMD (Single Instruction, Multiple Data) to perform parallel operations on multiple data points simultaneously, greatly accelerating workloads like AI embedding similarity calculations.
  Java Vector API provides an abstraction to harness SIMD capabilities with automatic selection of optimal vector sizes and data types, enabling significant performance improvements (up to 20x in benchmarks).
  Data layout and memory access patterns are critical for vectorization effectiveness; columnar storage and minimizing data copying are essential.
  Advanced vector instructions and architectural features (masking, shuffles, gather/scatter) offer flexibility but add complexity.
  Java’s current vector API performance can degrade if JVM fails to optimize; value types (Valhalla) promise to fix this by eliminating object overhead.
  Careful algorithm and data structure design upfront is necessary to fully exploit vectorization benefits.
  Definitions and Comparisons
  Term	Definition	Notes
  Dark Silicon	Portion of silicon area on a chip that must remain unpowered due to power constraints.	Results from breakdown of Dennard scaling and power density limits.
  Vector Register	CPU register that holds multiple data elements (lanes) for parallel SIMD operations.	Size varies (e.g., 128-bit, 256-bit, 512-bit); Java API abstracts size via “species”.
  Fused Multiply-Add	CPU instruction that multiplies two numbers and adds a third in one step with higher precision and speed.	Improves accuracy and performance over separate multiply and add instructions.
  Mask Registers	Special registers that enable selective activation of vector lanes during SIMD operations.	Useful for processing data sizes not divisible by vector length safely.
  Latency vs Throughput	Latency: time before instruction result is ready.
  Throughput: number of instructions issued per cycle.	Both affect how vector instructions should be scheduled for optimal CPU utilization.
  Practical Recommendations
  Design data models and storage formats for direct SIMD compatibility (e.g., columnar arrays rather than object arrays).
  Utilize Java Vector API for workloads with large primitive arrays and repetitive numeric calculations.
  Prefer fused multiply-add (FMA) instructions where available for efficiency.
  Use mask registers to handle edge cases in vectorized loops safely.
  Benchmark and profile vectorized code to verify JVM optimizations and avoid unexpected overhead.
  Anticipate improvements from upcoming Java projects (e.g., Valhalla) to mitigate current vector API limitations.
  This talk bridges hardware evolution challenges with practical software solutions, particularly in Java, highlighting how embracing vectorization and understanding dark silicon constraints enables significant performance gains in modern computing tasks.
  00:00:06
  hello everybody uh these are some basic stuff I guess you know uh right now how you can ask the questions please do if there's something you want to interrupt me with go ahead no problem uh I guess you already have your 10 coffee right and want to go drink something so it's very nice that the organizers put this talk at the end of the day because it's you know relaxing talk there will be only like four equations only up to 100 lines of assembly so just relax and uh we go through uh dark silicon and uh try to

00:00:52
understand what is this about and how it's related to Java and since you guys are tired and you know little knowledge is a dangerous thing so I want if you're going out of this conference uh or this talk with just one uh one thing you to remember then this is it the title was LIF from the Doctor Strange Love right so uh how I learned to stop worrying and love the bomb and spoiler alert for those who didn't see the film you aren't supposed to love the bomb it's a warning not encouragement so like

00:01:34
this talk don't try things you see here at work kids please because someone will see this vectorized code that doesn't work and they will find me and they will have questions so try it at home it's really really fascinating it's a whole new word of algorithms of different mindset and uh it's great try it at home but please don't do it at work and you see why so uh I work for a database uh startup company that was started uh more than 10 years ago by uh Professor Michael Stonebreaker is the guy who

00:02:20
bring brought uh you databases like Ingress and post Ingress also known as postgress and there are some uh some information about me if someone wants to find me after and uh uh so let's start from the beginning at the beginning there was a for Loop and it was you see state-of-the-art algorithm you can run it on your laptop and most of the computers what do they do they uh calculate hashes they uh do memory access and they do this all in for Loops so if uh if you get this for Loop like let's say C++ you compile it to some

00:03:09
assembly and you torture this GCC to not optimize it too much because if it optimized too much you get a lot of assembly so if it doesn't optimize it this is the basic structure of the loop so you start with comparing the loop variable with some condition and if like if it's 100 then their next instruction is Jump if equals and this means that basically uh if uh the break condition is met you jump outside of the loop if not you execute the body you increase the I variable right and jump B to the

00:03:48
beginning of the loop and uh through the decades processor designers try to make this run faster and they employ various Tech techniques so that this code and all the other constructs are with each generation of processors run faster and how do they do do it well typically uh in in x86 uh processor or x64 processors you get uh there's a front end that will decode instruction from memory it will convert it to Micro Ops uh usually it's one to one it we issue Micro Ops to backend back end we schedule the instructions

00:04:31
the microbs it will track dependencies between them it will rename the registers it will do various stuff it will work with Branch predictor to fit information to the front end saying where's is the next instruction you need to fetch right the branch predictor is very important also to actually tell the front end that the code goes that way you need to fetch a code from that part of memory and all this was gradually improved and uh there were is introduced in various parts of the system to make

00:05:02
itun faster and all these required more transistors if you have limited number of transistors let's say 1 billion and you want to design a processor with it it can you can get to run it fast then you can redesign it once second time and so on but at some point to do to make caches larger or to do it make it run faster you need more transistors and throughout the decades it worked basically this is this plots on a logarithmic scale the complexity of chips we started with thousands of transistors right now we are in about 50

00:05:42
billion transistor range and because it draws a straight line but it's logarithmic scale so it's exponential right so this is M more slow I guess you know it but if you ask people everyone has different definition but basically it says that uh every one and a half to two years something will double well this something is the number of transistors on square millimeter on a chip the complexity of chips however you call it uh something will be double uh every iteration of M's law this is

00:06:20
obviously not a physical law it's more like a marketing self full thing prophecy that says uh this will happen and actually uh companies deliver on that but what's the uh issue of having more transistors uh it's like with the lights let's say LEDs on uh Christmas um lamps if you get every year or every two year you get double the number of LEDs on Christmas lamps uh it's fine you can create better arrange them better but it's fine until they use too much energy because if you if you start with 100 Watts for

00:07:06
example uh that are required to power that uh LED chain in two years you get twice that much LEDs and suddenly you need to spend 200 Watts than 400 watts and well you might say that's not a big deal it's okay but it's exponential and every two years you get more and more power uh Power usage of those LEDs so how is it that muslo gave us so much great processors while we are were using exponentially Mor transistor well this is denner scaling denner scaling was defined by Robert dard who was the

00:07:48
invertor inventor of the original dram chip and he created a model of a transistor and how it behaves as it SC down so this uh model is there's are some equations defines was the static and dynamic power of of transistor but gen in general he uh predicted that as transistor are scaled down their power usage will go down uh the frequency they can operate on will go up so General everything we've seen throughout the decades that we got uh chips with more transistors using roughly the same

00:08:29
amount of energy sometimes it went out of line but then get back to the same trend line and they all switch faster uh but unfortunately this is the uh effect that is that M low is not necessarily dead right now but this effect that denner described that gave us more transistors at for free is that and it doesn't no longer works because at as the transistors are so small there are other effect effects that use cause it to use uh energy so in general we cannot add more transistors to the designs that

00:09:11
we be able that we will be able to use at the same time so if we have a CPU and there are like let's say two epochs of the mo laow so in four years this CPU is shrunked and and occupies only 1/4 of the original chip area so we have so much area that we can use to add bigger caches add bigger uh engines into the CPU to make that foru run faster the problem is that because uh this small CPU on the right uses the same roughy amount of energy that the CPU on the left we cannot populate this dark part

00:09:53
with transistors that are powered and this is the dark silicon era so dark silicon is the Silicon that you cannot power in a CPU at the same time as everything else and uh consider that it's exponential so after four EPO you basically get to that size of the CPU and you can in the effect power just 7% of the area uh so Michael B Taylor uh was one of the first researchers in into these effects and he predicted that there will be about generally four uh results of this uh these effects that we can see uh

00:10:40
one result would be that uh uh you can make you can just make smaller CPUs and that's it right we've seen that you can make smaller CPUs they are nice but this doesn't give you any uh Advantage it's just smaller maybe you spend a little less on secon but in general there's no Advantage especially if your competitor fights finds a creative way to use those transistors then no one will buy your processors because your uh competitor actually found a way around that so the other solution is to downclock those

00:11:18
transistors so basically you have a chip and some of of it is not used not always used some of it is downclocked sometimes some of it runs on uh high frequency always do do you have any uh does it resemble any recent designs of processors that you might know like uh the uh phone processors or maybe some of the Intel processor when you have performance cores and efficient course right and especially if there's a GPU built into the processor it's clocked differently usually it's a very big part

00:11:53
of the processor and is clogged much lower than the uh than the rest of the chip so this is the example right we have M1 chip which is old chip right now and it's not F that had all those blocks that do some custom processing and in uh in effect what happens here is we trade Energy Efficiency for uh transistor area so we pay with transistors creating custom segments doing just small thing just to gain Energy Efficiency so it's like when you're watching a movie there's part of the CPU that we that we

00:12:38
be working on decoding it and not a general pipeline uh that that is decoding it there's um uh if you uh compare something if you uh maybe uh using SSH there are specific instruction that are used there are specific parts of the CPU that can be used to uh uh to power. functionality uh examples like the very obvious examples that are quite old is for example this instruction this is arm instruction that just makes a floating point to uh fix Point uh conversion that's mandated according to

00:13:20
the spec of JavaScript so this instruction was speciically designed to be used in JavaScript run times um Intel create an instruction that does the comparison of uh string comparison so basically if you want to compare things you have a special mini accelerator in CPU that does it uh if it does it good that's a another question because as far as I know this didn't catch catch up in the uh you know people didn't start using it but in general you have accelerators on top level you might think about

00:13:58
accelerator as a GPU but there are accelerators of many sizes there are the blocks of CPU that does video and coding but there are also accelerators that are represented by specific assembly instruction if we're talking about Vector accelerators then they also occupy a significant amount of processor uh this picture is I think uh it's Sky Lake and you have you see here uh this is the uh Vector execution units uh they occupy quite a significant part of the processor so getting to actual uh Vector

00:14:44
registers and Vector processing what is uh so we can quickly jump to the code what is the vector register well uh I present to you actually it's maybe like that we have a register and register is register it's just part some memory that is that you can access inside processor uh with Specific Instructions um because it's memory so it's uh contains bits right so we can represent it like that there are some buckets that contain bits and the idea is that around this uh Vector registers you have a bun of

00:15:30
accelerators that can process data that you put into those registers so these accelerators are what's characteristic here is that the you can consider this register as a belt conveyor belt when you have Machines working on stuff that you put there but those machines work in a way that they work in unison they do exactly the same thing so there's no configurability you can't have uh conditional flow at least in B basic uh Vector instructions you have this Machinery that will execute on Accord

00:16:09
something you tell them on all those entries that you can see here so let's for example let's say we have some abstract register and we can put numbers there and we ourself interpreted like that we have here eight numbers we put them in the uh in uh in one register and we tell processor that these are numbers please consider this a numbers let's say integers and execute addition and that's it so that's one instruction integer addition and it's executed on all the buckets or Lanes of this Vector uh

00:16:50
register you get two registers you you execute an operation and you get some result so so um these kinds of operation you can do there's a lot of uh a lot of kinds of operations they were added if we're talking about x86 it has a long history of instruction set that were added to to it uh to allow more and more flexibility to this processing and sometimes when you look at those instructions you might think that they were designed by some drunken monkeys because they are really uh strange instructions and they are not

00:17:29
very complete in a sense you think that if you can do addition or maybe beat manipulations this will be consistent across uh Vector sizes across data types but now there are usually some gaps in this API well in this API in the ISA right so in the instruction set uh which is are then fixed in the next iteration of the instruction set so before we start coding uh there is uh a few terms that I need to know uh we might have different kinds of different sizes of vector uh registers and uh this is called in Java epis shape

00:18:15
so there can be a shape 64bit shape 128bit shape and so on uh that's if you have a shape which is basically a size of the vector register you need to add to it an element type so you might say you have element types of java basically all element types are apart from characters so you have bytes integers Longs uh floats and so on and if you say that's the shape of the vector register and that's the data type that's everything that the jvm needs to know to allow uh processing of that data because

00:18:54
if you know the shape like let's say 512 bits so it's 64 byte register and I'm operating on bytes it means that I have 64 buckets in this register if I'm operating on Longs which are eight bytes this means that there will be eight Longs in the register let's try act some actual code let's see if this works guess it's almost there okay great so um now uh this runs uh I hope the internet will be stable because this will be running on my server at home that contains all the uh vector

00:20:29
register I want to show you so let's do a Hello word with the registers so as you can see let's assume we have some flows to process so let's see how we can actually load them into registers and how to process them uh because Java is a high level language you have very rich uh set of types to represent all the concepts we seen but it should be straightforward so we have a float Vector let's say uh Vector one and now we want to load into this Vector uh actual data so it's pretty

00:21:08
simple we get from array there are two ways to load uh data into a vector from array or from memory segment memory segment is the new API uh and previously you could use bite buffers basically on the unsafe stuff it's no longer available so the API was clean up with the view on uh future future Java like more cleaner Java um so that's the only two ways that you can access the data right now so let's load floats and so we provide an r and index but there's still something missing and

00:21:50
this is the uh species that I was telling you about so actually I need to tell jvm uh what to optimize for I'm know that I'm running on the I have 52 register bit registers so let's see if there's a species for that well yeah there are species and I can select specific species like for 128bit registers or I can use the magic constants called preferred which means that the jbm is selected from for me so in general when you design this algorithm it's very important what kind of uh instructions set you Target and

00:22:34
what are the sizes of the vector registers Java tries to uh abstract it from you in a way and in simple cases it works but you can't really escape from that I will uh make it a constant so we have this species and we have this Vector here and that's how you load it we can now load a second Vector from the second from the second array and we can do some operation on it so there's a ton of operations here so there's like some basic operations have name so if you want to add multiply subtract there are

00:23:22
special methods for that like you would expect there are several operations that allow you to do do something um more fancy that we look later at later and there are some uh Escape hatches when you can say say something like Lane Wise which means to every lane of the which means to every bucket of the vector register please apply this operation and there's some constant listing a lot of operations and I guess this is the escape hatch allowing uh the Java folks to add new operations here but right now we just want to add

00:24:05
one vector to another and uh that's it we might want to print out something to so no so we know that it works let's print species information let's print the vector one and Vector 2 and theum but we need to create the call this hello method first and I have some helper helpers here to just um generate some some data for us so let's call it and see what breaks okay so it worked and we can see here that we printed species species says okay I'm uh I'm working with floats and it's a 512 bit register and there

00:25:18
are 16 floats there and here we print out the contents of the register so actually I generated uh arise that contain values from zero to to n and you can see that this is the first register there's second and there's a some that's uh correct and what's nice here is this is like plain Java you can look at the float Vector you have normal hierarchy of objects there's some abstract vector class and so on so basically you can also debu to it and you can put a break point here and

00:26:02
you can inspect the contents of those objects these are objects uh from the point of view of the jbm um they're not necessary objects from the point of view of the C2 Optimizer and uh uh actually uh when you look at the assembly that's generated this are very simple assembly instruction there's no allocation of any of those objects you see here there's just uh loading from memory there's an instruction to load memory uh from this floats there's an instruction to load uh from those floats so and Summit so this

00:26:44
that's basic key free instructions and this is how it works uh okay uh let's try to do something useful with this sorry for that so uh let's do something useful for it there was a lot of AI talks at this conference and they were talking about embeddings so I guess some of you might know what are the vector embeddings of the um in in AI speak so basically they tell you how similar uh two pieces of text are so you have two pieces of text you run it through the ending model and you get some kind of

00:27:47
vectors and a lot of uh rag algorithms work on that so in order to say which uh of those uh how uh close those text in the meaning are you take those two vectors and compute um uh you want to find the angle between them so basically it's a DOT product of those two vectors and this is very simple algorithm basically you get a you get a dot dot product of you make a DOT product of two vectors and divide them by their magnitude so in terms of pure like algorithm it's just uh you need to multiply elements of those two vectors

00:28:30
and under the uh uh in the denominator you need to compute the squares of each of the elements of those vectors and then sum them up and have make uh square root of that so this is like one of the simplest thing you can do with uh these vectors and we can see how this works this is called coine similarity uh and this is scalar this is scalar implementation of this so basically you uh you calculate numerator by multiplying elements of those vectors and you have two terms that you need to track in denominator which means that

00:29:17
the the the below part of the fraction and that's uh square of each of the vector elements and at the end you just do this this m with square root and division so how to do this in well when working with vectors so uh basically we have the species already it's the same uh one as before let's start from we need start from zero right because we we are accumulating those values so instead of having those floats here we replace them with float vectors and there's a handy uh method that will

00:30:09
allow us to create an empty Vector with zero inside we also of course we need to provide species now let's Loop through this data before before we just has loaded part of the data into vectors now we would like to go through all the data so it's not really complicated and we can do something like this actually well the size of the vector is larger and now what we need to do uh let's load we SE species we select a vector and use this here we have um how to call that maybe Vector a and then we have

00:31:16
Vector B and one thing to note because these are vectors we're advancing not by one element here right we are advancing by uh more ele elements and this is also abstract uh based on the type and Vector size so we can ask the species to tell us the length and that's it even this will run on different kinds of vector registers sizes and it will correctly Advance uh through the AR so we have those two uh vectors and what we have to do so numerator is now equal usually we would uh we would have to do let's copy this

00:32:01
and see how it works right so we need to multiply Vector a with Vector B and now uh of course this there's no overloading of operators so we need to we need to do add and this is important thing from the point of view of the API all those vectors are mutable so they are like value objects and uh if you want to obviously they represent the vector registers that are mutable and underneath the jvm will make sure that it works nicely without any allocations if it can but here in the API if we do

00:32:49
this addition we need to store it in the original variable referencing the original Vector because otherwise it will have no effect this is similar here we need to do this um so this is uh a vector a squared and so this is this represents this computation but now we are advancing every eight or 16 variables at once uh slots at once what's interesting is that uh in uh vectors have a lot of additional operations that for doing MAF and this API surface them so actually here you can substitute this multiply ad

00:34:03
with a special instruction which is called FMA which is fused multiply at the idea here is that uh on the assemble level actually inside the the the CP would uh do this fused multiply ad in a sense that uh we're working on single Precision float it will do multiplications and with higher Precision it will add add the same time it will add this one value and the result will be uh cast in a sense back to low Precision that we all working with which means float single Precision but the ACT mathematical operation is

00:34:42
more uh it's faster than just adding and multiplying and it's more accurate so we need to I think uh this works in a way that we need to do VA FMA VB and at here that's how it works and similar here we need to a fused multiply by itself and add this Vector there are uh Bas in most of those architecture there are at least 16 but usually 32 registers and there's a lot of architecture registers a lot more so we don't need to worry about you know um overrunning the uh register file we can do a lot of calculations

00:35:43
like this without speaking to to the stack and uh okay so this is the code here and at the end we should uh uh we need to return this right so actually we need to return one number and this one number can be returned by uh in the end we have vectors here that contain parts of the computation that already done and they have variable number of uh of buckets based on architecture so right now we would like to collapse them into one value and this happens by doing something like numerator and now I want

00:36:24
to reduce length to one value which is actually a surprisingly expensive operation so uh I thought that this is should be easy and fast but no it's actually this one generates a lot of assembly so this is part of beauty of using Java that it will not only uh uh adapt this algorithm to variable sizes of vector register but also provide some useful um useful things that methods that are not necessary one to one with some assembly instructions uh and they you have reasonable um certainty that the they

00:37:11
are implemented in an efficient manner so we have let's say similar with so that's our algorithm here and let's try uh let's try compiling it and making a jmh a micro Benchmark of out of it we'll see how it Compares those two algorithms so basically I have some code written so if I annotated it with my Benchmark The jmh Benchmark will autogenerate itself and this is The Benchmark that was generated it doesn't matter what how it looks like because it just Plumbing uh code that will try to run uh

00:38:20
the code we just wrote and um so if you used jmh you probably uh recognize all this output it just says what are the starting conditions and now we're running this uh we running this uh Benchmark um okay so I think the uh like by default it will put in I something like 10 million elements array so it's not necessarily open AI based uh embedding uh vectors it's larger but we see the difference better then so here it warms up and executes this method coine simility which is a scalar version there are some stats and we see

00:39:13
that basically it runs in 3 to four micros seconds and now we running vectors if you can read this output you already know the answer but we see it's soon soon enough so this is the scolar version and this is the vector version so basically it's more than uh it's almost 25 uh 20 times faster so this is the power of the vectors when they are used actually used correctly and in a sense that there's nothing that would break the um jvm logic that optimizes them and this is a pretty pretty easy algorithm actually to

00:40:13
to implement so if you're doing something like this this is definitely a good way uh to start uh and do uh vectorization um but there are other things that should you should be wor uh worried about and since we have just 10 minutes I will go through it without uh writing code but uh if notice that in all those examples we were operating on on array of loads how often do you find yourself in your application that actually you have arise of uh primitive types like you have 10 million of records and they are represented by

00:40:53
Longs or ins usually you would have some domain object you would have orders clients flights or something like that and if you want to apply them to Vector algorithm you what would you do uh I guess allocate an AR primitive AR go through your domain objects copy the values into the AR and then run the run the algorithm and in the you seen that we're talking about microsc here so in this uh word the fact that you need to allocate aray or copy something go through millions of objects to do this

00:41:32
it makes that those algorithms are useless so it's very important that if you want to use those you need to design your model your uh your data structures UPF front just for this use uh because converting data to suit this format is usually defeats the purpose so an example is a row versus column layout in database if you have a database table like this which gives you let's say these are database of flights you have origin destination uh airports flight number and so on and if you want to put do some

00:42:14
operations on flight numbers you want to put them into in Vector you can't easily do that you have rows of data each row contains different contains strings the contains information about carriers and so on and from each row you need to pick just this one integer or even maybe short to put into a vector register and what works is that uh CPU operates on cash lines right so if you want to load one bite from somewhere from memory CPU will take in 64 bytes that surround that one bite so actually the memory traffic

00:42:56
will be enormous to just pick those one bytes will be transferring a lot of unnecessary data to your cach in order to pick just this one things so it's completely useless that way that's why we have stuff like a column databases when every when data for each column is packed uh in separately and in that way you actually can easily especially if you know that you want to do Vector processing you can easily just copy part of memory into the register but in column databases they usually employ a

00:43:34
lot of compression schemes because if you have data like kov origin airport and it repeats th 10,000 times it's much better to just store it as 10,000 times kov right it's no there's no point in storing it in uh clearly and this poses another uh another problem for those algorith because if they are compressed in some way the data is light out light out nicely in columns format but it's compressed and if you put it into in Vector it's not necessary ready to use so this poses like you need to decide

00:44:16
what are the Trad uh tradeoffs between uh compression and is is of De compressing and putting that data into register and remember if you are like modern processor can issue two two uh 32 byte loads uh from memory every clock cycle so this means that usually you can fill that uh uh register uh every clock cycle it has a long latency actually it takes a long time but every uh every clock cycle will allow you to issue a load from memory and uh and most of the time if you're do we're doing such

00:44:58
algorithms like just multiplying two arrays uh most of the time you will be waiting on memory so if you run this algorithm and it's already 20 times faster it's limited in most cases by memory access it could run faster what we can do actually we can compress data in memory so we can bring it faster to our algorithm and then decompress it in place because if our algorithm let's say it uses is 10% of CPU we have 90% of CPU in like it's Ry to De compress that data and it's still will run that fast and if

00:45:37
it's emitted by memory we might make it run faster so it's tradeoffs everywhere uh quick uh information about what other operation you might uh use on the vector registers this is these are vector registers from x86 uh or x64 uh ining there were xmm registers then they were extended to be uh larger so there were like uh first uh first instruction or MMX you might remember when we were all young there were MMX they didn't include introduce new registers but there were uh extension like SS AVX introduce new

00:46:20
kinds of register and those register map onto to each other so actually Old Register were mm then you get got ymm and then Z zmm the zmm registers are 5 12 bits so that's the newest uh best and largest uh version of registers and there's one uh great thing that comes with the newest AVX uh specification is uh mask registers so actually there are eight mask registers called k0 to K7 that will allow you to mask operations so like we've seen there were robots working on those buckets and The Mask

00:47:02
allow us to turn off turn them off selectively uh saying no the let's say every second uh bucket is not active so this is uh uh very nice it's free basically those uh most of those instructions have musk ad version and why you would use them for example if you have memory that ends not as a multip multiple of a vector size you have like four stray bytes or four stray integers and you have a vector of size eight you can load it because you will go out outside of R maybe you uh this will uh introduce uh segmentation fault

00:47:47
because the uh memory outside that array doesn't even belong to your process so masking uh can solve this masking can uh add flavors to a lot of algorithm that's just one example if you put a mask here you actually can load only that part of the memory that you're interested in and similar with stores so actually I don't know if the if loads uh suppress this segmentation faults but I F found that stores do so actually you can store data that Vector to a memory that's partially

00:48:23
not yours as long as you mask it out uh there are shuffles which are also very important shuffles allow you to just rearrange the elements in the vector so you might have some uh for example precompute some shuffles and know that you want to exchange specific buckets and this will happen and uh that's like the other kind of big uh operation that is very useful uh there aren't any very general other operations there's a lot of very specific ones that you might learn about if you want to do some

00:49:06
specific thing there are also guts and scuts from memory so basically you can do something like this when you selectively load data uh by index into memory not continuous that's these are gutters and scatters uh store it in memory but it's still very very slow compared to anything else uh important thing when you look at this uh uh code and operations is trut and latency of the uh instruction Intel has a lot of materials on this and uh what is means the latency is basically how long uh the

00:49:49
next instruction needs to wait for the result of the instruction you're looking up so if you're looking uh you want to get a result of add instruction add instruction has one cycle of latency this means that you need to wait one cycle before the result of that operation will be ready there are operations that say it's six Cycles or maybe there are some operation that are even even longer for example division like General division is very expensive it can be 20 30 or 40 um clock Cycles

00:50:24
throughput is how many in uh such operation you can issue and this is very interest interesting because the processor are super scalar right they can when they process a microb or the operation there are several steps they do to process them so actually if the instruction takes many cycles each cycle they push the instruction to this next stage of execution and they can take another one so actually even if stuff like Lo from memory is slow and let's say it takes five uh cycles of uh to actually load data to uh register if it

00:51:07
can if the throughput is one it means that every cycle you can issue another load uh that's just an example usually is with some arithmetic operations but basically uh the in order to make this work fast you need to issue a lot of operations that they will be scheduled and send to uh ports that don't confict and that uh take uh that use this this uh uh supercal architecture so this means actually that there's no point in doing like load add store and and we go away to do other things uh you should do this in a loop

00:51:52
in larger on larger amount of data then everything will like amortise all the cost of initially starting this will amortise okay so uh there's there are sorting networks that we not talk about which is uh look it up it's really a crazy algorithm sorting networks and to sum sum it up uh you need to design up front to use this whether you WR writing in C++ on in Java uh the layout of memory for those uh algorithm to work is really crucial and the algorthm are pretty complicated as what the as for Java that only

00:52:36
applies to Java is that actually they optimize beautifully until they don't and then you're there's a catastrophic Cliff uh and all the performance goes to crap because if it doesn't optimize it will allocate all those float vectors you've seen it will allocate on a hep as real objects and execute all the Java code you've seen you can look up that's inside which is crazy it's absolutely it's not only not efficient will clear up up basically it will allocate gigabytes of data per second so uh

00:53:12
that's a big problem right now and this is because these are real objects right and unless they are optimized away these are real objects that's why this will be a preview feature as long as there are no value objects from valava when there are value objects from valhala they promise they will make this feature gener available because the vectors then there will be uh value objects and there will no not be ever uh Ray fight uh into actual fat objects thank you okay uh so uh the question is if it

00:54:19
unloads the vector register after operation was done or it possible to execute man operation it doesn't unload it so if you create a vector object and you actually operate on it you pretty sure that it's there in the uh Vector register uh the only moment when it's unloaded is actually stuff like context switch which needs to clear out the vector registers or maybe they are just jumping to other method and there's are zero there there's an assembly instruction that zero outs all the all

00:54:55
the vector registers but you can assume that it's there unless you uh well it's there because it's very expensive that it needs to be there and you kept as long as possible uh no this jvm because jvm issues an assembly instruction load data from memory to this register and unless you clear it by something else or override it it's there right

