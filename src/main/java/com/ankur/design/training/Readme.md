00:00:01
[Music] all right let's get started uh welcome to the session on exception handling and function and reactive programming my name is vanat subrahmanyam uh thank you so much for joining uh let's talk a little bit about what we're going to do here today and uh we'll get going after that uh we have about 3 hours so I'll go for roughly about an hour and 20 minutes then we'll take about a 20 minutes break enough time for you to refill your liquids the facilities and then we'll go

00:00:32
for another about hour and 20 minutes after that so uh what we're going to talk about is uh obviously about exception handling but we'll talk about uh uh it from three different aspects of it one from functional programming we'll talk a little bit about asynchronous and completable Futures and then we'll talk about reactive programming as well and and see how these are handled in all of these three different uh you know ways of programming but also we'll talk about some of the uh consequences of those as

00:01:00
as well so we got a fairly good amount of details to dive into as we go through but a few uh fundamentals uh just to set us on the same page before we get going the first of course is to talk about functional programming uh I've been excited about functional programming for a while uh you know I've been programming for I'm going to say roughly about 35 years now and for more than I would say 60% of the time I've been programming in imperative style as a as a very young programmer I started

00:01:32
programming in imperative style uh what I'm telling you in so many words is I had a really troubling and terrible childhood but eventually I found the light light of functional programming started programming with it and got really excited about it and of course uh one of the key things for us to keep in mind is that these are tools that's what they really are and if we really get too excited about some tools we get misguided we then become fanatic about it and then we tend to really say that's

00:02:01
the only way to do things and I'm hoping in this talk today we'll also draw lines on maybe when it's good to go use a certain way of programming maybe when it is not a great idea and maybe consider alternatives as well so we'll talk about some of those things along the way now uh in terms of the programming model itself really quickly as we all uh you know know uh in imperative style we tell what to do and also how to do it so that is B basically what the imperative style of programming is and in Java we've been

00:02:34
doing imperative style of programming from the beginning we've been programming with imperative style and object programming together as a mix on the other hand in the declarative uh style of programming uh we tell uh what to do uh and more important not how to do it and and that is one of the big biggest distinctions between imperative style and declarative style but what I really get is excited about functional style is you could say a functional style is actually a declarative style plus the use of higher order uh

00:03:10
functions so that's basically what um a functional style of programming is it really brings forward the amazing power of declarative style and then of course you mix it with higher order functions to uh build on functions that can receive other functions as arguments or uh you know do a functional decomposition as well so it's a very powerful model of programming that we are all very used to just to entertain the thought a little bit further one of the key things about functional style of

00:03:38
programming is the functional pipeline so just to emphasize that really well here let's say we have some numbers as a little example to work with and we'll start with a list of numbers let's say 1 to 10 to begin with and then in this case given these collection of numbers I want to start with let's say I want to really uh print out let's say the double of all the even numbers right a fairly simple example so you could say a far for example a VAR you know I or or number in this case that could come from

00:04:12
the numbers collection this is obviously the imperative style of writing code and you could say if the number mod 2 is equal to zero then of course you could output the double of the number you could say number time two so that's the imperative Loop that we are writing but the code is not very cohesive we all have written code with this uh for a long time uh but that's the imperative style of programming the nature of uh imperative style code but we can also write the code in more of a functional

00:04:43
style so we could simply say numbers. stream for example using Java here and we could say a filter and given a number we can say uh we want to take the number and get the even numbers from this collect uh from this uh value and so we could say hey give me only the numbers that are even and discard the rest and we could do a map and say number and of course in this case uh times two and finally we can do a for each and system.out and we can do a print line on it and and we could print it out so this

00:05:17
is an example of just looping through and printing the values using the functional style now the reason I get exited about functional style is it's got less accidental complexity the code becomes easier to understand easier to maintain uh but when you look at this particular code you often may be wondering okay this is nice and it's nice and cute and expressive but what about performance now obviously in this particular case you are noticing we don't have any extra collection we simply Loop through and we are producing

00:05:51
the output maybe we just wanted the first uh element in this case we could even do a break and it prints out the value four but but but in this particular case we could similarly do a DOT limit and ask for one value from The Collection that gives us a four as well but the unfortunate situation here is to understand you know what is really going on here are we taking a collection and creating one other collection of all the even numbers and then one more collection of double of the even numbers only to take the first element and throw

00:06:26
everything else because if we do that that'll be rather very inefficient so from the functional programming point of view what is really exciting about this is the the following and that is notice very carefully I chose the word functional style in here I didn't use the word functional programming and the reason I want to say that is I'm going to say functional programming is really two parts to it is equal to I would say functional composition that you are seeing right here and functional

00:06:57
composition is really powerful out about it but it's plus the use of in in addition to it a lazy evaluation so you really want to bring lazy evaluation without it you're dealing with a functional style code not a functional programming code so functional composition plus the use of lazy valuation becomes very necessary so in this particular uh case just to emphasize that point if I just look at the functional style code alone and if I were to go back here and and simply say that in this particular case I want to

00:07:33
uh take this code and say uh let's say a sample and we will call is even as a function and and we'll take the map and say a sample we'll call it as a double it as a function so if I were to write a a function right here as a static oh in this particular case let's go ahead and say a Boolean uh is even which takes a number as an argument and then of course I'm going to Simply say return uh let's say the number uh times two and and likewise I want to just take this particular function but I want to call

00:08:07
this as uh integer and we'll call it as a double it and we'll simply return the times two in this case of obviously uh a mod uh mod two uh is equal to zero in this particular uh function so we can uh you know take this particular code and take the numbers collection and we can iterate over it we can call the EAS even function called the double it function and ask it to produce the output uh based on what we are trying to do uh in terms of the uh collection of data we are working with so this becomes a

00:08:38
functional programming why because we want to really emphasize the laziness of evaluation in this particular case because that becomes very necessary let's fix the error here I think I removed the numbers let's put that back in so this is going to be the numbers collection is equal to a list of let's say bunch of values we want to work with now this could be a very large collection after all what if there are a thousand values that are after this you know how are we going to deal with it

00:09:06
well the result of course is a four but what's really cool about this particular code as we know is that if you say is even called for and you're going to put the number for which it's called and similarly if you say the double it is called for you can see that it's calling it for only the one and two and double the value two but it never actually touch the values three four or five or six or any number of values that may follow this it's not going to touch it well that is the lazy evaluation that is

00:09:38
extremely important uh for performance reasons and efficiency so that is definitely part of the functional style of programming as I was picking up reactive programming I was struggling to understand what does reactive programming really mean and and of course I read a lot of material about what reactive means but I'm a programmer I want to see things in code and I want to see how these things actually behave and I was getting really frustrated not really figuring out what does it mean and call me silly but one day I realized

00:10:08
and I kind of screamed out saying oh my gosh reactive programming is functional programming Plus+ and the reason I say reactive programming is functional programming Plus+ because it's really built on the foundations of functional programming so think of reactive programming as a higher level of abstraction starting from functional programming now I me mentioned a few minutes ago that functional programming is function composition plus lazy evaluation so obviously we cannot imagine doing reactive programming

00:10:40
without function composition and without a lazy evaluation isn't it so if I go back to this particular code you can see the code right here is a functional style code as we saw but let's turn that around a little bit just to consider another alternative so I'm going to just take one example here let's go ahead and import for example uh let's say io. reactive let's say uh x. star so I'm bringing the RX Java in here so I could say for example a flowable DOT from iterable and I could provide the numbers

00:11:16
collection right there and we can bring in a flowable into the mix so once I get the flowable literally as you can see here I'm going to take these two values in here that you see I'm going to take those two lines and just copy it down below that's all I did so I just moved that right in here and rather than the for each I'm going to Simply say do subscribe and in this case I'm going to Simply say system.out and print line and I'm going to just have it printed right there from the functional pipeline so

00:11:50
you can see that evaluate right there but I'm going to say take and ask it to get me just one of the values and you can see the result is pretty much the identical between the two calls it is lazy evaluation at the very root of it so you can see the similarity between the two pieces of code both are functional pipeline functional composition both are doing Lazy evaluation as well just the apis are a little bit different and but the concept is pretty much the same this is one of the reasons I often say that reactive

00:12:22
programming is functional programming Plus+ but of course the similarity ends at that point and we go off in terms of the semantical differences so it's a good starting point to think about they both have functional pipeline they both are using lazy valuation but then the abstractions deviate from there as we move on we'll focus on that a lot more as we go through uh further into this but that is one of the concepts that really helps to think about how these things kind of relate to each other now

00:12:53
let's talk about exception handling well unfortunately though when we write code in languages like Java we deal with exceptions all the time and our code is often filled with exceptions in so many places and we have to deal with not only exceptions we have to deal with nested levels of exceptions as well you uh deal with an exception and then you try to do something when you have an exception and if that were to go wrong what do you do and you tend to really have exception handling along that line of code as well

00:13:24
so exception handling is something we do all the time but when it comes to exceptions we often wonder how does this really work so I want to first of all you know start out by saying uh something we need to really consider in this particular case so uh uh pardon me for a second just a quick note um uh that I almost forgot to mention uh as I'm writing the code here I will continuously save the code assuming I remember to do as as I just forgot but I'm going to eventually put that into a uh uh uh you know my my website so I'll

00:13:57
I'll put this URL towards the end of the talk as well but you can download the code examples from there if you're really interested it's not there right now but I'll post it 5 minutes after my talk is over so you should be able to download it and I'll post this URL towards the end as well if you want to take a look at it so so on that spirit I want to go ahead and start pushing this in so this is a functional and reactive let's say programming and I want to just put a note in here about this as well so

00:14:24
I'm going to say functional style is equal to uh you know a functional you could say uh composition is essentially declarative plus the use of higher order functions as I mentioned but I want to say a functional programming is equal to uh and and I want to emphasize that it is actually functional uh composition you want to bring in uh but you also want to uh you know add on to it the lazy evaluation and then finally of course I'm going to say reactive programming is really uh functional

00:14:58
programming uh Plus plus as I want to claim uh as a relationship between those two and that's what we are illustrating in this particular example so I'll push that into the uh website uh after the stock and you can play with that code if you are interested further down so so we talked about the uh you know General nature of it but when it comes to exception handling uh I want to really step back and think about this a little bit further so we can clearly understand what we are getting into and I'm going

00:15:25
to say exception handling is a fun fundamentally an imperative uh style uh of programming uh idea so it's important to embrace the thought from the get-go because if we don't we try to really fight a battle that we tend to often lose uh if we are really not uh you know focused on the overall you know issues we are dealing with so so essentially it's important to understand that exception handling is predominantly an imperative style of program idea in fact I'll go even further to say that uh that functional

00:16:05
uh programming uh uh and uh exception handling uh I'm going to say are mutually uh exclusive so um they are mutually exclusive and uh it's very difficult actually uh to mix functional programming exclusive uh and it's very difficult to really match functional programming with exception handling in general so so exception handling is an imperative style of programming idea and and functional style of programming is uh predominantly uh you know a functional pipeline lazy valuation a flow of you know data through the

00:16:44
pipeline and exception handling is fundamentally opposed to that kind of model it will not work very well with this at all so I titled this talk as exception handling in functional and reactive programming and I just mentioned functional programming and ex exception handling are mutually exclusive I hope you found this talk useful thanks for coming well of course right so knowing that it is it has limitations are extremely important for us to uh keep that in mind because if we try to fit something that doesn't fit we're not

00:17:18
going to get the best results and we feel you know disappointed we feel uh confused we feel uh you know the code is getting convoluted but if we step back can ask the question why it becomes a lot easier to think about it but this is also even more important to uh uh reason with uh and and I'm not exaggerating this this is actually a true story I got an email from somebody who said hey venet the uh stream API in the jdk does not support checked exceptions so I forked the jdk and modified the entire

00:17:56
stream API to handle checked exception would you please do a code revieww for me and I replied to the person saying I'll be happy to do the code review but I want you to just answer one question before I do and that is if you can Fork it and change it I think the people who wrote the jdk are as capable if not more to do the same thing can you tell me why they didn't do then we can talk about how to actually do it well that is the problem isn't it because we tend to really jump on Solutions sometimes but

00:18:29
it's important to really understand the problem before we jump on to the solutions otherwise we feel disappointed with the solutions we seek and the problem is still left intact you know strong without being solved if we try to solve problems without understanding it so so that is one of the things I want to really focus on today is to really think about some of these concerns as well so exceptions uh and uh functional or or mutually exclusive keep that in mind we'll come back to this a little

00:18:55
bit later but let's start with a little bit of uh imperative style of code uh in Java to begin with and see how we can proceed with this so let's take a little example and play with it uh as as one step at a time so let's start with a little example code uh as you can see after lunch I write code really fast so so right there is a little function I call as get name of airport as you can see I'm going to lower the font a little bit so you can see the code a little bit better on the screen right here so this

00:19:26
is basically um a function that says well go out to a you know URL uh find out the IAT code for a airport given that code find me the name of the airport again it's a code where I'm a little lazy to really implement the full code so I'm just going to grab the name of an airport and return uh return it upon the call let's just start with a little example and play with it so I'm going to call the get name of airport and then I'm going to provide uh you know IAH here for example which is uh

00:19:58
which is the airport in Houston and I want to be able to get to the uh details of that so I'm going to bring in a scanner here uh that is needed for this so I'll just simply say a star on that one and maybe a net as well so let's just go ahead and bring those in and and try it so in this case I have an oh iio exception as well sure so let's go ahead and provide that and see how that goes so this says exception must be CAU or must be handled well as experienced Java programmers we know exactly how to take

00:20:28
care of that so we'll just go ahead and say throws exception for now we'll come back and talk about that a little bit more so run the code and you can see it says George Bush inter Continental Houston so that Court seems to have done its job in getting the airport name uh given the airport well okay well I I don't promise that it'll work for all the airports but it seems to work for a few at least so right there is a little piece of code we wrote but when you look at this code this is typical of a lot of

00:20:55
code we write where we call a get name of airport and you can see it takes a code for an airport potentially it may throw an IO exception and then we go off to a URL we access the content from that URL we are taking the content out of it and then stripping out some data we want and we are returning that and in the process it gave us the name of the airport so that's good so far now let's say we want to work with a collection of airports right now rather than just one airport so we have an iata codes a bunch

00:21:27
of airports in Texas and and what I want to do is to be able to print out the names of each of these uh airports and I'll come back to that in just a minute so how do we go about uh you know using that information you could say for and and uh in this particular case let's go ahead and um you know say for uh you know uh iata code uh we'll say and that comes from the iata codes obviously and then for each of the code given to us I'm going to Simply uh print out let's say the name of the airport very simple case

00:22:01
right so I'm going to say get name of airport and say I A A code and and simply uh print it so if I go back and uh run the code uh for each of these airports you can see there's Austin and there's Houston there's Dallas and San Antonio so you can see the values come through pretty nicely not a problem however going back to this particular code what if I remove the throws exception right there for a second obviously the code is not going to compile I need to handle the exception well it's a good time for us to put this

00:22:34
over here and say I want to do a catch and and maybe an exception in this case and all I will do is simply print out the exception get message maybe I'll I'll convey what happened right there so that's basically the code I'm going to write for for that piece of code the compiler was happy with us gave us the result we are expecting as well but what if I make a mistake here and I say Tas and I don't think there's an airport with that name uh in there uh that's another thing from my life story is uh

00:23:06
we are a family where we travel a lot and and that's when you know when the entire family can communicate using airport codes that becomes very disturbing but we seem to know these airport codes quite often in terms of where we are so running this code obviously you can see it gives us the Houston and uh you know Houston and and Austin and Houston and Dallas and and then in the end it gives an anonio but right in the middle it says invalid airport code uh T and it it prints that particular message and that's because

00:23:37
obviously we got that from here when the request was successful but the there was no such airport obviously you could had Network failure rather than a failure of the response so if the network had a communication problem even for a valid airport you may get a network error in that case it's going to display that you have a network error for that it's going to deal with it however looking at this a bit more carefully we have a collection of airports now in this case we were looping through one at a time when there

00:24:08
was a failure for a airport we reported the failure gracefully and moved on to other airports well if you were to take a write a program you cannot tell your users look I'm really cool I'm writing code in functional style but it won't work the same way as you expected it that's not going to cut it so it the US of don't care whether the code is imperative style or functional style the users care that the code produces the results they expect it to produce so if I were to take this code from imperative

00:24:39
style and write it in functional style I have to preserve the behavior of the code I cannot give you a different output and say this is good enough I got to really make it consistent after all so the question is how do I really be able to get to that particular point of consistency in the code well we come to that in just a minute let's go ahead and save it as an imperative uh style code and and we'll just save it away so that's basically the code we just wrote and we are dealing with an exception as

00:25:10
well and I think we did it fairly well and and if you really ask me what is one strength of imperative style of programming I would say it is dealing with the exceptions because we are used to doing doing this quite often we write code and we deal with an exception and then of course we can deal with multiple nested levels of exception as well so this is something we are really good at doing when it comes to imperative style of programming uh we have we have been doing this for a long time we can handle

00:25:41
that now on the other hand though what if we want to take this code and we want to convert this code into a functional style well before we go I do want to say let's step back a little bit and talk think about the history some of you probably have been programming long enough that you remember the history some of you are probably relatively young maybe you were born with exception handling around you uh in the way that it's done and you probably don't realize the pain that the old folks like me had

00:26:10
to go through back in time uh so if you think about the way back in time we wrote functions which were performing some calculations or computations they returned the data and then we said gosh what about errors I know this is a bit naive but once upon a time we said positive values are data negative values are errors as if values cannot be negative in general that worked a little bit until we realize that results can be negative as well oh Dar it that doesn't work really well let's try it again then

00:26:44
we said well here's an idea a function returns a zero if everything is good and returns an error code if something is wrong you say that seems like an interesting idea so you got an error code as a response but what about the data how do you get it it oh you can send a poin or a reference to a function and the function can fill it in and return the value now obviously this complicated the API when we had to make a function call and and that was not very pleasant way to write the code nevertheless even more people could

00:27:16
ignore the returned error code and the program could be misbehaving and we didn't have a really good way to handle it so we struggled with that for a while and then we said well here's another idea what if we were to maybe uh call a function and and the function is running just fine then there's no details of error to deal with but if anything were to go wrong we'll blow up the call stack and throw an exception and uh you know unfortunately that idea took a lot of uh you know popularity over time and and we

00:27:53
started running with it my biggest complaint is that we should have never called it exception handling we should have called it normal handling because if we call it normal handling maybe we'll think about it differently now we call it exception handling and we look very surprised when it happens and and that is not a really good way of programming in fact uh this reminds me of own experience which which is you know unfortunate in my in my mind but I was speaking in a conference not too long ago and and one of the uh persons

00:28:24
in the conference came to me and said hey venet you probably don't remember but I took your distributed object Computing course back in 1998 I said oh my gosh I remember teaching that course back uh in '98 well it's great to see a student from the past and he said but I got a story to tell you and I said I love stories tell me what story you have and he said well I was in your you know distribut object Computing course and we had a project demo where we have to demo the project we built the entire semester

00:28:54
and we were doing the final demo when you walked up to my machine and remember the is 98 right we had these things called ethernet cables and uh so he said you walked up to my machine and you disconnected my internet connection and said continue and my program crashed wildly and I failed the course and I said that's a tragedy why do you want to tell the story to me right now I feel terrible that you failed the course and he said no no no there's actually a happy ending I said how could there be a

00:29:22
happy ending after you failed the course I I'm really curious to know about it and he said well that's the last day I ever programmed the happy path so nowadays when I sit on my computer I just don't think of the happy path first I actually uh you know think through the code a lot more deeper thanks to you and he said he went on to say in my office now on the cubicle I have a picture of an evil wenet and I look at the picture every day before I code and I never get this problem again I don't want to know

00:29:55
how this picture of evil wenet looks like but the point is that but we need to really handle exceptions as well otherwise it's really uh uh going to be a problem for us in the code so we cannot just focus on Happy path well exceptions unfortunately you know tend to really make us think about this as oh my God shouldn't that shouldn't happen but exceptions can be normal it could be a sequence of operation that could happen so now that leaves us again thinking how do we handle normal errors

00:30:23
versus exceptional things that are showstoppers and there's no real good way so the short thing I would say is as a field I don't think we have an answer yet so it's very important to keep in mind that exception handling is not the way to solve the problem you could ask me how do you then solve the problem I don't have a clue but but I think it's important to at least recognize that we don't have a good solution yet and I think we should continue as an industry as a field to explore other ideas to

00:30:53
maybe make this better one day but we cannot really become complacent and say we figured this out no we haven't so we really need to take the time to understand how to handle uh errors and the flow of code that's still quite messy in what we do but let's think about handling this in a functional style code how do we really approach it so as you can see in this particular case the code executes and produces the result we got the airport names for valid airports we got an invalid airport

00:31:23
when it is not and then the code continued to produce more results that's really awesome well that's good so far but let's go a little further with this now what I want to do is to say I have an iata codes right there and I'm going to say do stream uh to get the uh stream of the data in the functional style so that's good so far then we can say a for each and we'll say system.out and do a print line on it so we can say I want to print out the uh data for each of those and you can see the code being printed

00:32:01
that's great but then what I want to do here is to say a a map function in this case I'm going to say hey I really want the names of the airport let's actually make this a little bit verbos just to handle this so I got a code an iata code uh and given the iata code I want to take this code that's given to me and I want to do some work with it so what do I want to do you can see the code is happy with me so far but I'm going to say get name of airport and say iata code right there so this is very

00:32:38
beautiful it's elegant it's concise it's something you want to keep looking at but it doesn't work and and that's the sad part of this code isn't it well what is the problem with this code the compiler is not too happy with us and it gives us an error unreported exception IO exception must be CAU or declared to be thrown that's what it says so you're thinking hm what do I do with this exception well one immed I'm sure almost every one of us have tried this at one

00:33:09
point right you said aha no worries I can take care of it and you put a little try block around it and then you put a catch exception and you say I want to Simply print out the exception uh talking about design patterns this one has a pattern as well it's called an act of desperation so you can try that as well right like I'm going to handle the exception right away and you could try something like this but unfortunately though the reason I say Act of desperation is after all that effort you

00:33:41
compile the code and the compiler says the same damn thing again right it's completely merciless isn't it the compiler doesn't at least say I know I know you tried something my dear programmer but explaining to me it's like no I'm going to say the same thing again right how cruel it is right it's as if you never did anything just gives you back the response and says deal with it and you're sitting and scratching your head the first time you ran into it maybe you're thinking huh what's

00:34:06
happening here so we will just save it as an act of uh desperation and that did not really help us so let's try this one more time let's undo that change and say what should I do right now in order to handle it now at this point uh is is called a realization point because this is a great time for do Vel opers to stop and say maybe I should think about this a little bit more but you know some programmers are absolutely eager to write code they don't want to stop thinking so what do they do they say aha

00:34:40
I know exactly how to fix this problem and they go out here and they say the problem is the checked exception here isn't it so what they do immediately is and and I'm kind of writing a little bit of an easier code to explain here they could say a string get name of airport 2 which takes an iata code right and then of course they say return uh get name of airport with the iata code but then they say I want to call that function isn't it so I'm going to call the iata code to right now but then within this function

00:35:17
I'm going to go ahead and uh take this uh you know exception that's given here a catch uh you know exception obviously that's given and we're going to then say uh throw new uh runtime exception right uh and uh exception ex have you ever seen anybody do this before yeah we all have right but never want to admit in public who did this right so so we do this now you kind of wonder what is this called This is this also has a name for a pattern and I often do this this is called a curl up in uh in a corner and

00:35:54
cry pattern because when you look at this code you're like done it what did we do because when you run this code right now what does it do it blows up with a runtime exception and that's what you get right a big call stack on your face not a very fun experience so I will just simply say uh don't do this obviously right just to make it very clear right uh not a good idea right so don't do this so this is not a great idea uh and and we have seen people do this often time is they what did they do

00:36:27
they are saying oh compiler you don't want to let me have a checked exception I will show you how to deal with a unchecked exception that doesn't solve the problem actually in fact the compiler was trying to help us and we just said shut up and the compiler is like okay I hope you know what you're doing and and you know it doesn't get really complain after that so not really a a great idea so this is an anti- pattern rather than being a pattern in general so we should definitely not go

00:36:56
this route so so so this is you know avoid uh rolling uh into a runtime exception this is not a good idea so let's save that away so so what actually went wrong in this particular case now obviously when you look at it you had to write another function in this particular example and and that particular function uh had to catch the exception and blow up with a runtime exception but people are Relentless they would when you write bad code it's important to stop stop writing bad code but sometimes people take that

00:37:31
bad code and they generalize it and now they want to have a way to repeat bad code over and over without much effort at all so this is where it gets even more dangerous where they say hey here's a great idea let me create my own function to convert exceptions to runtime this is anti-pattern going gone terribly wrong right so so then they have a nice library of function that will convert all functions to runtime exceptions and this is when all hope is gone right it's it's dark and it's going

00:38:03
to be really gloomy there is no way to recover from this but you can try this as well you could write a more generalized function to do it uh if you want to take that up a little further and this is even more terrible so for example rather than introducing that that separate function we could say for example here is a a function we are returning and convert to runtime exception and you can try to take a function on hand and what are we going to do in here well obviously a function ex what in the world is a function ex so

00:38:37
we create for ourselves an interface right now uh which is our own interface which can handle exception so you could say interface a function ex which takes a you know a type T and an exception e extends maybe an exception and and of course in this case we could say that's our functional interface we just cooked up and this functional interface contains a public and let's say a result that it's going to return in this case so pardon me this is going to be a result R so we'll just use an exception

00:39:08
inside of this and that's going to be an apply function and what does that do takes an input but it throws an exception in this case so so when it throws an exception in this case uh how are we going to use that particular function well the good news right now is we can now call the uh two but not in the map but so we can then go back here and say this is going to be convert to a runtime exception and we can provide into it the uh the data that we want to provide so that's going to be the iata

00:39:42
code so so paron me so let's go back to this one here and it's going to be that function we're going to call the convert to iata code and then we can take the code right there and then we can call that particular function in there and that's going to say hey I will return to you a function that the map will be you know happy to receive so the problem in this case is if you look at the definition of map map takes a function but a function's apply method doesn't throw any exception checked exception as

00:40:14
a result the compiler doesn't allow us to deal with it so taking the bad idea and extracting into a reusable function uh you know sometimes you see people go overboard with it and you can say in this particular case what do you you do you want to return obviously a function in this call so you could say I want a function that takes a data so you could say input and what are we going to do with that input you can then say in here uh this is going to become the function you want to call so you can put a tri

00:40:45
block around it and a catch exception let's say ex and this is going to Simply say a throw new runtime exception uh and then of course in this case you can throw that away and this becomes a return a fun. apply and take the input and you know return it from there now again before I go further uh you know don't do this so I always try to put these in talks these days because otherwise somebody takes a photo puts on Twitter and they say this guy's an idiot right so it's always better to say don't

00:41:15
do this right this is not a good idea so it's better not to encourage that kind of programming though you probably have seen people really go overboard with it and try to extract that in in some case uh so it's giving us a compilation error but I won't bother fixing it right so don't go this route not a good idea so uh so uh more anti pattern not a good idea so what should I do if that is the case in this particular case well the problem really is uh the when this blows up what's going to happen the answer is

00:41:49
uh if there is an exception uh in the functional pipeline uh it blows blows up the uh pipeline so so that's one of the problems is it's going to blow up the pipeline so what that means is one we have a messy uh call stack to deal with that's the first problem second problem is we lost the uh ability to process uh so the ability to process the uh values that follow uh in the collection that's another problem and a third problem is uh don't even ask how does parallel work right so that is like oops it's already

00:42:38
messed up and now we want to know how does it work in a parallel situation so the problem seems to get deeper and deeper and deeper as we go through this not uh not a very pleasant experience uh to deal with as you can see in this particular case so so again that's that's basically again I'm sure you have probably seen people do this as well is to put a curly brace around it and try to handle the exception right there this is even more worst syntax where we are trying to really put a little code and

00:43:09
put a try and catch around it and and More Mess right so More Mess not not a good idea so once again you want to really avoid such code so so it's really uh you know no good solution uh going this uh route so we definitely def itely want to avoid it so what's the what's the you know solution as we saw in parallel it gets even worse so um this is where I I think often about what does life become when we do these things and as a way to express my feeling uh I think I tweeted this last October and

00:43:47
and my tweet basically is this uh for this Halloween I have chosen a costume of exception within functional style code right so this is a nice way to scare everybody around us and you're like oh my gosh how do I really you know handle this exception in this code the code becomes a mess to deal with not not a very pleasant experience in general so definitely avoid that that's not a way you want to go so what gives what do we do um so let's step back a little bit and think about this again and I

00:44:16
mentioned that exception handling is um imperative style of programming idea and in uh functional programming and exception handling do not go well with each other so so then the question is what do we do you know how to handle this uh and so the answer to that question is um I never so I I'm I'm a big fan of giving analogies uh when I when I talk about programming I want to talk about analogies from from real life and I want to say oh this is as if whatever I never ever thought I would

00:44:52
use a programming uh metaphor in the real life this is nature in Reverse so this was right before the pandemic hit us this was January of 2020 and uh I spoke this was January I spoke in the Boston New England Java User Group amazing user group had a great time and uh it was maybe about 9900 p.m. maybe 9:30 p.m. and I said to myself maybe it's time to drag myself back to the hotel and I had a flight to Montreal to speak in a conference over there uh uh the next morning morning and I said to myself Montreal from Boston

00:45:31
yes it's a neighboring country but it's international flight but it's not that far of an international country I'm a traveler I can handle it I don't need to worry about this I told myself so I checked in into my hotel about 11:00 p.m. my flight was at 6:30 in the morning so I said I'll get up at about 3:30 uh leave about 4:00 that's plenty of time to go to the airport so this was about 4:00 a.m. I steep into my car start driving and it had snowed that night and so the entire Road was filled

00:46:03
with with snow and you can imagine the entire road is empty and this is about 4:15 in the morning in the night and I'm driving when I hear a little thump and the next thing you know my car doesn't want to go as easily as it did so I stop my car in the middle of the road got out and looked at it my front right tire was busted and I'm like uhoh I've got a flat tire what do I do and I look around there's nobody around so I immediately called the renting company and you know wanted to say I got a flat tire I need

00:46:38
help my flight is in nearly two and a half you know 2 hours and 15 minutes and I called them and their automated service was amazing they said your call is important to us which means nobody cares about you and so they said your call is important to us we'll be with you in about 2 and 1/2 hours I'm like no that's not going to cut it I'm going to miss my flight and I'm going to talk right as soon as I land over there it's important I take this flight so sitting in the middle of the road not knowing

00:47:04
what to do not having anybody to call at that time of the night and and honestly I'm not exaggerating I thought about exception handling and functional programming and I said to myself what do you do in functional style of programming and the short answer in function programming is very simple uh uh treat uh you say treat error uh error as data and deal with it Downstream and I repeated this a few more times in my ears and that gave me a new courage and I said yes and I turned up the ignition put my foot on to the

00:47:43
pedal and drove all the way to the airport and you should have seen the people at the airport because they heard loud noise rolling in the people working in the in the uh uh rental car company and one person and stopped everything and came back and said are you okay I said oh I'm just fine just the car tire is flat but that's okay here's the key and I just bolted off to take the flight and took the flight went there gave the talk amazing no problem about a month went by and my wife called me I was in

00:48:13
Europe at the time and she called me from home and said hey remember you said this experience in Boston yes I I I I remember that and she said well you just got the bill from the uh car rental company I said let me sit down let me know what the deal is and she said oh they charge you $65 I'm like I can handle that so so the model of the story for you from now on is if you have a flat tire don't stop right no just kidding don't take driving advises from me so but the point is that's exactly

00:48:41
what I did I just drove to the airport and and of course dropped the car off and and you know took the flight but this is really a motivation was how do you deal with it the answer is deal with the downstream so essentially the first idea is treat error as data and deal with it Downstream so you want to treat error as data so what does that really mean to treat error as data that's a first uh you know uh thought we need to uh you know see uh that we have a data but we want to be able to treat a error

00:49:14
as a form of data and then of course deal with that Downstream so remember say that you have a good friend of yours and your friend calls you in desperation and says hey I'm driving on the middle of the freeway I have have a flat tire what should I do the worst thing you can ever say especially if you don't want this person as a friend anymore is you can say blow up and drive in Reverse right you don't drive in reverse on a freeway just because of a flat tire but that's what an imperative style of

00:49:43
programmer probably would do right hey I've got a flat tire I'm going to start driving in reverse the cop catches and what are you doing driving in Reverse well I'm handling my exception right no you don't blow up your call stack and keep going back to the call stack that doesn't work so what would you tell your friend you would say hey first of all you know safety is important pull over to the shoulder and exit safely that's a good recommendation so in other words you always keep the flow moving forward

00:50:11
you don't have the luxury of going in reverse in the call stack so essentially you have a functional pipeline but you want to keep going forward in the functional pipeline so it deal with it Downstream and keep uh going forward word that is extremely important for us to do so let's come back to this in a few minutes so how to uh deal is the question so we'll come back and look at this in a few minutes and how we can do it so how do functional programming languages or languages that tout a much

00:50:44
better functional style at least you know do deal with this in general we could think about the hascal way you know how do hascal programmers deal with exceptions well the cool thing about hascal is you will you know uh uh you will think about hll and say this is going really well until they use the word monad and now you have no clue what's happening right well basically this becomes a monadic style well they' call it a maybe monad oh well there's data but when things were going wrong

00:51:14
maybe we'll give you a maybe and you can deal with it but essentially the idea behind this is you really want to capture the errors and you want to move it forward that's what you're really trying to do and and how do we really deal with it let's take a look uh in this case using a little example from Scala and then we'll relate this back to Java and see how we can do this in Java after after that so let's go over to a little example here and and give it a try so let's go over to this particular

00:51:46
function we have right in there which is the uh you know get name of airport so let's start with with that function uh that we have right now so let's let's grab this one so I'm going to just convert this code over just for a minute so let's go over to um let's say over here we'll do a sample. Scala right here and and then we will go ahead and and see how we can you know do this in scolar so here's the function so what I'm going to do simply say that this is a function called get name of airport

00:52:21
that's going to take a iata code which is a which is a string obviously we want to take a string and just as a quick note Java is the only language I know on the jvm which really cares about check the exception all the other languages simply say and we don't care so they all treat exceptions the same uh it's a compiler feature than a jvm feature so scholar doesn't care about checked exceptions so in this case I'm going to Simply say here is a URL I'm going to bring in and and this URL obviously is

00:52:55
going to be the uh data that I want to bring in uh to this particular uh you know field that I want to bring in actually I'll keep it very simple here iata code let's go over here to uh the scanner I can just keep that the same as it is right now we'll just simply use a a Val instead of a VAR for these and then of course I get a response from this particular call and and once I get the response from it I want to see if it contains the name uh if it contains the name that's great otherwise I want to

00:53:26
throw uh exception right now otherwise I'll simply come here and we'll return the value which is the uh you know split of that value uh from the call itself so let's go ahead and put a little parenthesis for the indexing over there and that's going to be the value we're going to return so so that becomes my uh little function I want to call and and return the response from it and uh and I don't need the tri block obviously if the exception were to happen we can simply blow it up from this call and and

00:53:54
move forward so so that becomes the little nice little code uh to respond with the call and again I'm not going to handle much at all let's see if that actually works so I'm going to call the get name of airport and we'll try this again like we did before and see if it reports the uh airport name uh in this case it's giving me an error on the scanner because I didn't import it let's go ahead and see import right here and this is going to be java.util uh. star we'll just bring that

00:54:25
in right here uh as well so that we can use those two in here uh so a syntax error again on line number seven let's see what error did I make on line seven this is going to be a open URL yes an extra parenthesis let's get rid of that so so that's basically the code uh we're getting there so essentially uh line number 18 is unhappy with me so print line and I'm going to print out the get airport name right there from the get airport and that's going to report the value this is going to be the end of it

00:54:55
uh anyone sees the error uh so in this case we are looking at uh let's see what the error says in here so uh it says syntax error on line number 18 it's not happy with my uh print line we we'll get to that in just a second so that's my uh iata code I want to return back that particular response so okay so uh no main so that's good so far so this is going to be uh a call to uh printline and I want to print line this particular value get airport name and that's going to be I a h let's say and I want to

00:55:28
print it and maybe that's what it's complaining about that it couldn't find the main okay so uh awesome so with that code you how do you really handle exceptions uh in this particular case well scolar doesn't have checked exceptions right it's everything is unchecked exception uh so to say and you just need to deal with them when they happen but the question is how do we really make this work properly in the case of uh of an exception uh what are we going to do how do we really deal

00:55:54
with exceptions uh properly so the answer to that question is you can do a few different things in scalar to make this work properly I'm going to skip that part so so you can ask it to uh execute the code with um a little bit of uh uh handling in here so you could use a what is called a try and a try is a very interesting uh object so think of a try as a union more than anything else if you will so if you think of it as a union so a try May potenti have a a success or a failure so it can carry a data or it can have an error

00:56:34
that it can carry through uh between those two so what you could potentially do in this case is you're saying look I'm calling this particular function and it could potentially blow up may have an exception that it's going to throw so how I going to handle that particular exception again remember the Tweet error as a form of data so what are we going to do with it so what you can do is you can say I want to write yes get another function right here so let's say the function I want to write here is uh try

00:57:02
to get airport name and I simply roll it up into a try object like so get name of airport and iata code and you can ask it to Bubble Up that particular value as a try now what this basically does is it can return a try with a data which case it'll be a success or it return returns a try with a failure in which case it say there is an error I want to capture this error and move it forward well so that seems like an interesting idea to begin with but what does this take us well when you are trying to deal with

00:57:37
the code you may do something along these lines for example let's say you have a collection of airport codes you could say I a codes is equal to let's say a list and in this case I want to say let's say this is going to be a us let's say IAH and maybe a t which is not valid and then you could say uh you know uh uh sat a well in this case you could say I have an iata codes but once the codes are given you could do a map on it and in this case you could simply say given a code I want to call the try uh

00:58:17
to get airport and then you can pass the code to it so what this is going to do is it's going to return a tri object as a response to this call uh to the map and when it returns a try object what you wanted to do in the in the past was the following right you wanted to take this one and you had to map and you wanted to let's say you want to take this name and convert to uppercase and you print it well you could say the you know name of the airport and you could have said name. two uppercase like so

00:58:51
but you cannot do that anymore the reason is you're not dealing with the name you're deal with the try that potentially wraps the name into it so what you're going to end up doing right now is something along these lines so you're going to say in here I want to take this name given to me uh into this particular object and I want to say that if that object contains a value transform it if it doesn't have the value simply move the error forward so so this simply uh is taking the

00:59:26
exception or the error rather which is of of type exception and moving it down the pipeline or if it's a data performs transformation well that's the that's a good step right now but what's the next step after this we want to then take the data and print it out maybe potentially or print the error if there was an error but that's going to become a little bit more messy on the next call as you can see in here so what you're going to then do is the following you're going to say at this point I'm

00:59:59
going to take the data excuse me and and match it if it's a success I want to get the name of the airport if it's a failure I want to get the message out of it so then you can get the value and finally of course you can uh print it out at that particular point so that could be an approach that you can take in a language like Scala uh to do this so a lot of programmers were you know excited and influenc about this particular approach and the question is how can we do something like this if you will in

01:00:36
the case of uh Java so let's take those ideas the spirit of that and see how we can apply that in here uh with with the Java so we want to treat uh error as data right that's what we're trying to do and and we want to be able to trate error as data and push it down the pipeline so let's explore this idea so let's say we want to have a interface we want to create and we'll call it as a TR interface which is going to deal with some type T that you want to really work with so in this case the type T and I

01:01:09
can say in the in this I can have a class called success and and the success basically can say it implements the uh TR type as you can see here that you can bring in from the from the uh base similarly you could also come up with with a class called failure which implements let's say in this case the uh try as well you can bring that in so now of course you want the functions to be able to provide you a a try when a failure were to happen let's take an incremental step towards this and see of course you could

01:01:46
use third party libraries for example like vaver and you can do some of these things a little bit more easily but I really want to focus on the core syntax to think about how this is actually going to happen happen so you can take the iata codes right now and you can say in this case I want to really perform a map operation where I take an IIA uh ta code but I want to then uh come back with a a try as a response so in this case you can say try uh you know you could say get airport name which is the

01:02:20
function we have right here get a name of airport rather so I want to be able to uh try get name of airport let's call that that function and in this case the result of that is going to be a try that you're going to get back from that particular call so we can say here is the iata code I want to send to that particular function so we'll start with that first and then we'll expand on that as we move on so what am I going to do with the function so public let's say in this case here is a uh a static let's

01:02:51
say a try and and this is going to be try of what well that's going to be a string after all because you're going to get the airport name if everything was going well and here is a try get airport name which takes um uh you know iata code as a as a data so now of course we want to go call our function we are interested in in running now before we go further uh let's take a quick uh you know detour on this uh uh you know sometimes we use things and we may not really pay attention to things but in

01:03:23
the java.util do concur uh you remember seeing a a function interface called callable t uh and on the other hand you have a java.util do a function dot you know uh package and you have a supplier uh which has a t in it so if you remember the callable that that contains a function called call and uh you have a function in supplier which is called the get so you kind of look at these two and say huh why would we have a supplier when we had a callable already right so I mean sometimes we do this right we have a code but we also

01:04:05
it's my code I write this right but clearly I don't expect that from the jdk team right so there's got to be a good reason why they have these two well as it turns out the cbles call has a throw exception attached to it so when you deal with the functional pipeline you you cannot deal with the an exception so the supplier is much like a callable except that it doesn't have any exceptions it throws that's kind of the reason why they went this route uh to have a supplier so for all practical

01:04:39
purposes they are fairly similar to each other in terms of what they do they both return to you an object maybe with a connotation that A supplier May potentially create an object and return to you but in a sense they both are giving you an object that's what it really is so so something to think along those lines but how do we really make this work so we clearly want to call this function called get name of airport which is going to return the data to us so we could try something along these

01:05:09
lines and we could say for example return we could say try Dot and we could you know create an object from from this one so you could say try and you could pass maybe the iata code and you can get a object from it so you could do something along the lines of maybe in this case you can say get name of airport and you can provide the iata code for the airport and ask it to provide that so so what is a try function try could be a static method in the try that is going to return the object to you and without trying to get

01:05:45
uh you know too concise on that I'll simply say try. try for now and and that can be refactored out later on uh if we desire to so so this can return a tri object uh forest and which is a TR string in this case we are expecting so we could build up on that right and we can say hey my try object is going to uh simply have a a function called try so you could say a static uh in this case we could say uh a public let's say a static uh try and this is going to take uh some kind of a function to uh work

01:06:20
with and this is going to be uh maybe a supplier or a callable potentially we could say a callable of of T and and this could be a function uh we could say callable so that's going to return a data back to us on the call and so then what we can do is with that we could say something along the lines of let's say concurrent star because that's where it's sitting and we could say I want to return a object on that particular call so what are we going to return so we can simply say in this case we want to

01:06:49
return a success right that's what we want to create a new success and what are we going to return for the success ESS that's going to be a success of whatever the result of the callable do call is and we can wrap this up into the call and and return it so so this gives an idea of how we could potentially write a tri function to elevate this into a success but these basically are uh derived classes from here uh so as a result a try May potentially return a success or if there's a failure it may

01:07:22
potentially return um an error as well uh so so this code becomes a lot more simpler as a result we can simply say return try. try and just pass the function to it now obvious question is what's going to happen if there is a failure right so we can say it is a tri block and we can then say I want to handle a exception if it were to happen and and so in this case we can say catch uh and and here is a exception I want to receive and what am I going to do in this case I could say return a new a

01:07:56
failure of T and in this case we could simply say exception we could just take the exception and roll it in and and pass it around as a as a failure object so potentially we could return those two which means the success needs to take uh data as a Constructor argument so we could say here's a success and the success is going to be containing the object T will say value and we could simply save it away right this value equal to value and store it away and that's the value we are interested in

01:08:31
well if it's an exception obviously you want to store the exception and you want to Bubble it up to the to the caller after that so so that's a you know first step you could take well okay so that seems like I whether you use a third party library or whether you hand Tas it you probably just eliminated some you know boilerplate code you would have written otherwise but you're getting there at this particular point now what about the failure in this case you could again say in this case a failure and and

01:08:58
this could potentially take a exception that you are interested in dealing with right so you can store this exception right here and you could say this dot maybe in this case exception is equal to exception you can store that away uh if you want to carry it forward you could say here is the exception I want to carry it right we'll come back and refine this a little bit later so that could be an exception you are propagating through from this particular call so seems like we could either get a

01:09:24
failure or a success then what do we do well obviously we need a way to perform the next level of mapping so this is going to give us a tri object which is a successor a failure well what's the next step maybe I want to convert the name to uppercase so you can then say I want to take this and this becomes a try uh object and what I going to do with the try object you could do a tr. map and you can then say in the map if it's a data maybe you want to take this data and say data two uppercase right so you

01:09:57
could convert this to a uppercase if it's a data so what would a map function do think of a map much like a map in optional so if you think of a map in optional the map in optional says I will let you transform a data if it exists otherwise simply return an optional empty moving forward this is very similar to this instead of sending the optional empty it's going to send the failure over so the map is going to transform a success from a success to another success but it's going to take

01:10:29
the failure and just move it over but of course there's the type conversion when it comes to the failure as well from one type to another that becomes necessary as well so you can then transform the data through that well great we got the data transformed then what's going to happen after this step well the next step after this is you could say I want to take the data and maybe I want to print it but when I want to print print it I want to know if it is an error or if it's a data well if the types are the

01:11:02
same you you luck out a little bit if the types are not the same you may have to convert it even further so this becomes a try obviously you know try let's say uh uh uh that maybe a syntax error but just look at it conceptually for now and you could say if there is an error you want to get the data otherwise you want to get the error from it and so that may be a transformation so this step you could say try Dot and you could ask the question right is it an error so if uh you know uh error you want to

01:11:34
perform one transformation if it is not an error you want to perform another transformation once you do that what's the next step you can do a for each and you can take the data and you can print the data out again like I said if the data is not the same type you may have to do one more transformation to get the string or representation and print it out let's say we go this route and make this work what's going to be the consequence of this particular approach well first of all this code will work

01:12:05
right so you're going to look at this in code and say yeah that works well will it give you the data and the error and then the data the chances are yep that's going to work as well because you're going to transform this into a object which carries the error and you're going to take the error and move it forward so as a result if it's a data it goes through a transformation if it's an error it in this case is going to just move it move it forward to the end where you're going to handle it so the good

01:12:38
news is you're dealing with it in the in the uh you know end of the pipeline what is the disadvantage of this particular code the first and foremost I would say it doesn't matter what language you're using to do this I would say the code is is no longer cohesive so this is to me one of the biggest challenges with this particular approach the code is no longer cohesive so what I mean by that is at every level every step of the way you are asking the question is it data or is it error is it data or is it error

01:13:17
and you're going back and forth with this and you are trying to determine what to do with the data and if it's an error what do you do with it so your code is not as clean as it once was right so the functional pipeline uh is no longer uh as clean as it was before so that is another disadvantage you are running into in this particular case as well and and to me the the essence of right so the so in a sense when you look at it you can say uh that with this kind of nature of code I would argue the

01:13:58
Simplicity uh the clarity uh the uh you know the ease uh and and various other benefits I would normally say you know are much lower uh with this uh than when uh when we deal uh with uh you know pure functions that do not throw uh any exceptions so that's another point to really think about it so the question you want to ask is does exceptions make the code impure right and and from the fundamental point of view of functional programming we want to really focus on purity of function and transformation

01:14:40
this s seems to really bring a level of impurity to it the second question you want to ask is you know how do we deal with multiple levels of uh exceptions so you deal with an exception and when you get an exception you want to do something else with it well that layer in the pipeline is going to become even more complicated when you are dealing with multiple levels of exception and and the code no longer is going to be as simple as it once was so the code the benefits of functional style begins to

01:15:16
diminish at this particular point and if you then take a look at this code and you want to maintain it you are you're you're looking at the and saying wow is this code really as maintainable as it was before when we dealt with functional style of programming so one of the questions you want to ask is is there a a range in which maybe functional style is suitable solution and maybe there's a range when functional style is no longer the most suitable solution and and I would say uh to me when my my code

01:15:51
doesn't deal with exceptions uh I love functional style of program pramming but when I have to start dealing with multiple levels of exception and the code becomes really uh you know spread out with multiple levels of exception handling I tend to really back out of functional style in general I am much more comfortable writing the code in imperative style I'm not in a competition I don't have to prove to anybody that the code has to be in functional style remember the code has to be maintainable that's our goal and

01:16:24
to be able B to release a product that is easier to maintain in the long run so I don't think we should shy away from the imperative style all the way through and in general I would say try these ideas but don't force yourselves to stick to it to say I've got to write this code in functional style if the code is easier to understand if the code is easier to maintain I would say go with it but the minute the code is no longer easy to maintain the code begins to smell the code becomes less cohesive

01:16:56
much more difficult to maintain then trying to compel ourselves to using functional style may actually work against us in this case this is one of the reasons I emphasize the word we're not in a competition it's very important not to get into a mode where we have to defend against a particular uh type of solution or you know speak in favor of it these are just tools and and I generally tend to really use the tools where it may make sense and if a tool is not not the most appropriate in a s

01:17:26
certain situation I think it's more pragmatic to pick the right tool for the job than to stick with the wrong Tool uh even though that may not be the most effective in a given situation so this is basically if you're doing purely uh stream based solution uh with functional programming I would say that's my general recommendation is you probably don't want to continue with this kind of approach when you are dealing with a stream API be mindful of why exceptions are not really handled by the stream API

01:17:59
they were not naive about this they were thinking through this quite a bit and there's a reason why they didn't really introduce a solution not because they can't really add a solution that others have added it is because in my opinion it's not the right solution and and and I think it's a really prudent and wise of of the jdk team in my opinion is that they Shi away from providing a solution which not be the most appropriate solution uh because it doesn't really favor the functional style of code now

01:18:32
so that is basically in terms of how the functional API is I'll clean up this code and push it into the repository so you can look at it but with the caveats I mentioned are are really important to keep in mind when it comes to uh this this style of programming so so what do we where do we go from here like I said it's rather unpleasant and and you don't want to really do this unless you have Clarity in the code so if you're going this path and things are getting really treacherous don't

01:19:02
hesitate to fall back into imperative style because I think that's much better and manageable at that particular Point well when we come back from the break we're going to talk about two more things we'll talk about how completable Futures deal with it and then we'll talk about the reactive stream API and how they deal with it as well so we're ready for about 20 minutes break and I'll see you right after that thank you [Applause] [Music] all right hope you had a good break

01:19:41
let's talk a little bit about uh exception handling and how that translates to uh a few other different programming models well first of all uh JavaScript has a concept of promises and Java has completed future and it turns out the ideas of completable future are very similar to the ideas of promises in JavaScript so the question is how uh how does that model work and how are exceptions handled in that particular model that's what I want to focus on uh for a little bit uh in this in this part

01:20:13
so let's talk about uh promises are uh you could say uh you know are completable uh future so essentially uh the idea is if you're dealing with the asynchronous piece of code so you want to write a non-blocking code you want to make a call you want that to go off and do things and when the processing is done you want to perform the next stage in your computation and you want to be able to do that well that's a very powerful uh programming model but the question is how does this actually work

01:20:44
and and how do we deal with uh the data and the exceptions in this particular model uh the the name here is uh a railway like a railway track model so think about this as a as a railway track so you have um a railway track as you would imagine and uh in in this case of a railway track let's say you have a top level track and you also have a bottom level track so you have these two tracks that you have uh in here so essentially you are looking at stages of computation so when you're in a stages of

01:21:19
computation so you could first of all for example take a piece of data that that you can work with and you can call a function with that piece of data now the function could perform some operation and what it's going to do is to return a promise or a completable future so think of a promise or a completable future as an object that would yield the result back to you uh so you could have three different uh States if you will uh of the completable future or a promise so the states are really one of you could be in a pending state

01:21:57
so if you're in a pending State you haven't finished it you haven't resolved it and it can can be an a pending state for a long time uh waiting for something to happen uh uh for it to resolve or reject and and the two other things are resolved State and uh rejected State and you can say these two are really a terminal uh States uh in this case so once a promise gets to a result or reject State isn't change but from a pending state it could potentially change so those are really the stages of

01:22:29
computation in which a particular computable completable future might be at any any given time so you could be in a pending State and then once the computation is over it becomes result if something were to fail it becomes rejected so so far so good but how does this really take us uh forward well the beauty of this is you can be in a nice pipeline so you could be taking a completable future and call a function uh uh as a next stage and that may take you to the next completable future and you can have this pipeline of processing

01:23:03
that you can go through uh in this particular sequence of computations you want to perform so once again you see the evidence of this functional style uh functional composition that you're going to be looking at but the semantics are quite different in this compared to what you saw before one of the biggest differences is when it comes to a stream you you know may have uh zero one or any number of data on the other hand when you look at a completable future may have zero or one piece of data so this

01:23:39
is a single pipeline uh single use pipeline so in other words you're going to be going through this pipeline once from the top to the finish and and that's basically execution of the pipeline uh to transform the data so so you're not really stre streaming data through this it's one data transformation that goes through in a completable future uh in a sense so but in that case how does this really deal with data and how does it deal with error let's say you are in a function like this one and and it says hey this

01:24:12
is great I was able to perform the computation it resolved and I have um good response a good data well if it's going to resolve and get a good data you could say you are on the top path you can call this the happy path if you will so you are going to go from one function to the next function and you're going to have this happy path that it produces right here so maybe this was resolved and you have a data right there so you could say that this is a data that you're going to carry forward so this

01:24:49
data now because this uh this complet future was resolved goes to the next function the function says hey I'll take the data you give me and I'm going to move it forward to the next phase and in this case it becomes the transformation of the data again to the next phase so you could be hopping from one to the next to the next to the next and you could be on the top level quite uh quite a bit moving forward because everything was successful so far so that looks pretty exciting in terms of carrying

01:25:20
through with the data let let's take a look at this really quickly before we we go any further with this and and in this case I'll I'll simply you know uh show how we can deal with the exceptions I'll come back to this example a little later on but let's just entertain this thought for a second so let's say we have a a completable future is equal to we'll say create and I'm going to call a function called create which is going to return to me a completable future so I'll go

01:25:47
ahead and bring in a concurrent star uh which is where the completable future is sitting so we'll say public let's say static a completable uh uh future let's say in this case is going to be create and and what does this do it simply says let's keep it very simple right now return completable future let's say do Supply a sync and I'm going to Simply return a a a little value let's say uh we from a function called compute so the function let's say in this case uh compute is going to Simply oh let's say

01:26:25
return uh let's say a value of two right very simple uh it may take a little longer to run but I'm going to return that particular value so if I were to go back to this code just to illustrate this point and I'm going to say print the details of the completable future notice it says not completed well that's a pending State as you would expect that's what it tells us right the the completed future has not completed yet that's what it tells us uh and as they say time cures so let's put a little

01:26:54
little time here let's put a thread. sleep for just about a little uh don't need that much time but let's just give a little delay in here uh just for uh uh you know seeing the output of this and then after that we'll ask the completable future what what it wants to tell us and in this particular case you can see it went from not completed to completed normally that's what you're seeing here so we saw basically in this case we saw the penting state we saw the resolved State as well it was not done

01:27:27
and it's it's completed that's what we are seeing right here so that's the completable future we are creating but what I can then do is I can say in here here is my completable future but I'm going to say a DOT then apply and I can take the data and I can do a transformation of the data data times let's say uh 10 and I'm going to transform the data so so this becomes so this is where you are in one stage of the pipeline you go to the next stage of the pipeline when it was successful so

01:28:02
you went from this stage here to this stage saying yep everything was great I want to move forward so in this case you can then say uh dot then uh accept and you can take the data and you can simply print out the data at that particular step and that's your display of the data so you're taking the value of two that you have in here so so then apply let's make sure I got this right so uh I want to take the data from the completable future that's given here and I want to apply that particular transformation and

01:28:36
then accept the data and I want to uh print out the data eventually right that's what you're trying to do so so essentially in this case you are uh you know transforming the data and then you are saying once the data transformation is done I want to Simply display the data uh at the at the you know result in the end so what ises this completable future type is this is of course Java so you better say this is an integer type that I'm dealing with and you are trying to multiply the 10 with that value and

01:29:05
you are displaying the result of 20 so you take the two multiply multiply with the 20 and you print the result with a 10 the result is a 20 and that's the result you are seeing right here so this is the happy path that you are journeying through uh as you go through in this case so what is nice about this you are able to focus on just the data as you can see in here you can then go a little further with this and you can say maybe I want to do a plus one of the data and you can see a 21 being printed

01:29:38
so this is simply saying you are then taking the data that you got here and you're going to the next function which is next resolving and you can keep moving forward that's your happy path but what if something were to go wrong what do you do well if something were to go wrong your function is going to now return an error rather than um uh you know success right so let's say go going back to this let's say this was a failure it returns an error so this is going to be an error that you're going to propagate at this

01:30:15
point when an error happens you come down to the unhappy path so think of this like a railway track and you're just moving forward but you can be bouncing back and forth right you're on the happy path unhappy path happy path unhappy path and you can keep going back and forth As you move forward so now you are in the error right here and it says look that failed I'm sorry but we met with the failure what do you want to do well you can take this failure and you can send it to the next function the

01:30:47
next function can take the failure and say I'm sorry this is beyond any reasonable repair I can't do anything with it it can propagate yet another error moving forward so that is one option or a function may take the error and say hey I can recover from this in which case you can bounce back to the Happy path and keep moving forward so in a sense you can build up the stages so that you can go from uh success to a success or success to an error an error to a success and back and forth and you

01:31:23
can keep moving moving forward like that so the way to think about this is here is a way to uh you know uh look at it let's say you have um a promise or a completable future as we talked about and that is in a resolved State let's say uh in this case or it is in a rejected state so it could be in one of those two stages if it's neither one of them your pipeline is not running right it's pending but the minute a pipeline uh stage results or rejects it's ready to go to the next stage moving forward

01:31:57
so you are here now you're in a function let's say your function is receiving that particular completable future what does it do it says I'm going to fix it I'm going to resolve it uh uh or I'm going to cause a failure because I couldn't handle it right so that's going to be just a failure to reported so what's going to happen at this particular point now in this case what's going to happen is uh what is next what's going to happen is uh go to the next then so if you're in resolve you

01:32:33
are into a function if the function was able to recover from give you a positive result you go to the next to them well you are in a reject and the function says I'm going to mend it guess what's going to happen go to the next to then so in any stage you're going to get to the next then when there is a dat data that you are able to process on the other hand when you are in the resolved State and you run into a failure you say oops we got a problem what do we do well go to the next uh I'm going to use the

01:33:09
word catch for now we'll come back and refine that a little bit later similarly you are already in the rejected State you ran into a problem what do you do go to the next catch so in other words in every single step you make a decision I am here if it's a success I go to the next then if it's a failure I go to the next catch so essentially you keep moving forward and you decide where you want to go so what is this a catch that we are talking about here well the developers behind the compatable future

01:33:44
had a weird sense of humor they decided to call it not as a catch but they called it exceptionally so exceptionally block is uh part of the code is called whenever you run into some difficulty so essentially you know what exceptionally is it's when something goes wrong you want to handle it right that's what exceptionally is so let's take a look at how this is going to work uh in here so this is a simple example as you can see but I'm going to go back here and say uh something like a public let's say static

01:34:20
int uh transform and that's going to take a number let's say and in this case I'm going to say return a number let's say time 10 and that's what we're going to use now obviously when you come in here we will simply say uh transform called so you know that this call was executed so we can see the behavior of this code and what I'm going to say here it's a times 10 so I'm going to Simply say here comes a sample and I will call the transform function right in there to

01:34:51
call this you can see transform called and it said 21 so that part is good however let's go back to this code and say something along the lines of if let's say math. random is greater than 0.5 let's say in this case uh throw new runtime exception and we will send a message uh let's give something which we often see in production right uh something went wrong right so that's very useful message so we going to just blow up with the message something went wrong and and provide that so in this

01:35:27
particular case I'm going to Simply say you know uh a transform let say failed right that's what I'm going to say in this case the transform failed now as you can see when I run the code this time it says transform called transform failed and and run this again a few times now notice in here when the transform is successful you can see the value is 21 but if the transform is not successful what happened well remember what this code is going to do when a failure happens it goes to the nearest

01:36:01
catch or nearest exceptionally but there is none so it didn't do anything so how do we deal with this let's go back to this code for a second and in the very end of this I'm going to say dot exceptionally and this says throwable and I'm going to say handle exception and provide the probable uh to it right now so this is basically saying I want to provide an exceptionally block and provide a throwable and call a handle exception let's give it a try so what does the handle exception do so I'm

01:36:38
going to say in here uh handle exception uh takes a throwable let's say throwable and uh that's a throwable that I'm going to receive right there and and within this I'm going to Simply say let's start with baby steps right now so what am I going to return from this let's say I'm going to return a type T right now and I'm going to Simply return a null for now let's come back and refine that a bit later and in this case I'm going to say output will say uh you know handle

01:37:08
exception uh and of course I'm going to provide the throwable so I can uh display uh that particular information so we'll say handling exception and the throwable so so we provide uh that particular call right there in in the sequence now notice what it displays as an output it says transform called transform failed and it says the completion exception runtime exception something went wrong that's what it tells us so it's it's printing the information about the exception right

01:37:42
there and tells us something went wrong so if you again look at this one the transformation uh you know transformation failed and it report something went wrong but if I run it again no there's no message it simply says 21 in fact let me just put this as a DOT getet message right here to see if it's a little less noisy in there so that's about uh when it's successful that's when it's failure it says handling exception and it says something went wrong so what's going on here well

01:38:11
you can see that the failure happens in this code right there when the if there's no failure what did it do the transform succeeded returning 2 * 10 is 20 it went to the 20 + 1 is 21 and it printed the 21 that's a success path when everything worked really well that's what you saw in here as an output as you can see with the value of 21 that's going to you know be printed if it is successful that is it doesn't want to go into that route uh but you get the point right so that's basically when

01:38:44
when it's successful it's going to print that result but if it were to fail on the other hand what does it do it notices that you have an exception in transform and and so when the exception is in transform what does it do that's result facing a problem so it goes to the nearest exceptionally so at this point in code it it skips this and this and ends up directly right here into the exceptionally block right uh now we just want to the other one so right there you can execute it so uh so there right

01:39:18
there it's the output as you can see something went wrong so essentially the idea behind this is it goes to the nearest exceptionally block as you can see now how do we structure the code with this now like I said this is a weird name for a function but but it's not uh you know that um uh you know um um you know not uh that unuseful to have a method like that because you could always know one thing right because you know what exceptionally means right now it really means you are dealing with a

01:39:52
mess that happened up in the pipeline so when the boss comes to you and asks you how are things going you can simply say exceptionally right so you know what that means uh and and everybody around you who are programmers know that as well so it's an exceptional code at this point absolutely but you can now see where you can handle this pipeline you can take this exceptionally and you can move it up here if you really wanted to and and now you are saying I want to receive this block of code right there

01:40:26
as a handle exception and in this particular case maybe I want to transform the data and send something down the lane if the failure were to happen it's really up to you as to what you want to do in such a situation and and you can say look if there was a failure I want to propagate that failure uh as a as a data to process down the stream you could potentially do that as well uh in this particular case so this gives you a bit of a handle on how you want to process that information as well so in this particular case for example

01:41:02
let's say if you run into a problem like so if I were to say this is an integer that I want to return from here into this handle exception I could potentially say I want to return a 100 maybe if this was to handle it and return a data from there so in this case what's going to happen uh right now so this is going to uh if there was no exception the 20 comes to here as a 21 and it prints 21 if there was an exception you're going to go to the exceptionally right if there was no exception you bypass exceptionally right

01:41:37
this is the happy path you go from then to the next to then you're not going to go to the exceptionally so so in this case if it was a success you're going to go straight through to 21 uh which which I hope I'll get sometime when I try this and you will see the value 21 there you go so the transform completed the result was 21 the model of the story is you bypass the exceptionally however if the failure were to happen you went into the exceptionally it printed something went wrong gave a value of 100 instead and

01:42:10
then we took the 100 and added one to it and the result of that as you can see here is 1001 as a response so essentially this is the railway track pattern where you can provide the exceptionally blocks at various uh points in time for you to transform the data so now obviously the question is what's going to happen if there was a failure in this area of code well then it's going to go to the nearest exceptionally from there so in other words you if if the failure were to happen in another stage you'll go to the

01:42:45
next exceptionally and then you move forward that's basically the way this is organized so what's the good news and what's the bad news the good news in my opinion here is that a bit of a Clarity for these functions and I have to say I really like that part so the part I like about this way of writing code is the then focuses on the data that you want to work with the then is not struggling with data versus error hey I'm in the den I focus on the data that's all I care about right I don't have to worry

01:43:18
about the error that's not my concern what does the exceptionally say the exceptionally says I worry only about the error I don't have to worry about the data so it's a nice separation of concern the code is a little bit more cohesive so so definitely better than we trying to do the try and and and success and failure in the code as we tried to do before you don't want to get into that kind of mess in this particular case so so this is a lot better unfortunately though this is not perfect

01:43:50
the reason this is not perfect is so let's take this a little further excuse me and try this out let's say we have a data that we have and then we will also say in this case we want to perform a a value for the transformation so we'll say you know multiplier and we'll bring in the multiplier in here and we will provide the multiplier so just to entertain this thought if we go back here and say here is the data that we are receiving and we call transform and pass the data and a to pass in so that's going to transform

01:44:26
that data and return that that's great however if I come back here and say here is a transform but that's going to multiply with a two let's say now what's going to happen in this particular code you can see that transform failed something went wrong transform was called and transform failed again so there's two failures that you see but why is there two failures the reason is this one failed so the exceptional kicked in it returned 100 and this one again failed and when this failed what

01:45:00
happens it tries to go to the nearest exceptionally but there is none so as a result there was nothing to report the failure so in other words you hop over the exceptionally if it's success you hop over the then if it's a failure but this can be in multiple levels that that's the part that makes the code a little harder as well because when you look at the pipeline the pipeline is now interleaved with all this code that could come in like so so in this particular example what's happening you

01:45:33
can see that it failed you come into the uh some uh something went wrong that succeeded so you bypass this exceptionally and you got here on the other hand if you come in here the first one failed well same as before when you run it again the first failed and uh let's see the second when we're to fail again uh it's going to be oh there we go so the first failed the second failed but you report at 100 why is this because when this failed you go here it returns 100 when that failed you go here

01:46:08
to the exceptionally which return 100 which you printed here so essentially you are interleaving these exceptionally and then and you in your mind you have to separate these two pipelines that are inter leaved in here and at every stage of the pipeline you get to decide do you want to go up and deal with the happy path or do you want to go down and deal with the error and you get to decide whether you want to come back up or or stay in the bottom and you're going to go back and forth through this so that

01:46:41
is basically your your uh you know pattern here which is the railway track pattern that you see that you're propagating through so on one hand what I really like about this approach is that you can have a nice little code that is uh separated and and and uh cohesive at each level but the point I don't like about this as much is the interleaving of these exceptions I'm going to just save this here and and and uh you know uh save it uh into the file so you can take a look at it when I when

01:47:12
I post it later on so so that's the part I don't really like as much is the fact that it actually interleaves and can make the code a little harder to uh follow and harder to understand as well so again not a perfect solution if you will in terms of what it can provide and uh and and you have to really deal with this back and forth we'll we'll come back and talk about the other example in just a minute and and see how we can implement this using that solution but we'll say completable uh future uh you

01:47:42
know uh and exceptions so essentially that's the path you are going through back and forth so how do you build code with this particular approach the way you build a the code with this approach is you get to decide what you want to do as an action for the happy path if that stage doesn't have any failure you can immediately have a next happy path next you know after it if there is potentially a failure you want to ask the question do I want to handle this now or do I want to handle this in the

01:48:13
end and you have a way to really choose between them so the the nice thing I really like about this is as I said I'm not dealing with the data and the exception together they are separated on each line but the disadvantage is deciding where to place this exceptionally that you have to decide based on the stage at every stage you ask the question a is there a failure possibility and if there is what do you want to do about it do you want to deal with it now or do you want to bypass a few stages and deal with it later that's

01:48:46
a decision you have to make of course the answer to that question depends on your business logic right it depends on what your application is supposed to do it's not something arbitrary for you to decide let's think about this a little bit further now to see how we can handle this with the problem we have at our hand now keep in mind a completable future is useful only for one pipeline uh execution right one flow there's not multiple stream of data going through it only one flow of data from the top to

01:49:18
the bottom that's what you're dealing with in this particular case so what I can do here is something along these lines so let's get rid of some of these functions we don't care about for this example so what I will do here is to go back to the main function and in the main function let's take baby steps on this so I could say something along the lines of let's say uh output let's say process let's call it as a iata and and send let's say IAH here as a value so

01:49:49
what does process iata do so we could say public let's say static and in this case uh a function is going to return a string uh process iata and that takes uh iata code and and how I'm we going to use that in here well we could say a completable future so we can say completable uh future do Supply let's say async so you want to make an asynchronous call uh to this particular function so you can go ahead and say sample get name of airport and you can call that particular function well actually that's not quite

01:50:28
true so because that's a A supplier so you can say get name of airport and pass the iata code to it and and that's going to give you a completable future so once you get the completable future what do I want to do I can then say a DOT then apply and I can take the name of the airport and convert to uppercase if I that's what I wanted to do right so I can convert to uppercase like so so then apply the next step after this is I can say do then accept and and in this case I can simply say uh system do let's say

01:51:05
uh system.out and uh uh and I can print it right there uh we could say print line and we can print it so obviously in this case I'll make it a void method like so and we'll simply call that for the process iata and I have it printed in the very end so but obviously you know the problem already right Supply async is going to take a supplier well supplier cannot deal with check the exceptions so that code is going to give us trouble saying I don't deal with IO exceptions what do we do so we are back

01:51:39
to square one on this one you might want to structure your code so you deal with really uh unchecked exceptions if you will so you could say for example in here uh I have this function but I do want to handle the exception if that were to happen but we are not in a a multi uh you know uh element pipeline right now we are dealing with only one element in this Pipeline and and we have a different way of handling with the exception as well so there's a bit of a relief uh in this case compared to the

01:52:11
stream API so what you could potentially do here is the following you could say catch exception and in this case you could say uh simply bubble it up and you could say Throw new let's say in this case a runtime exception and bubble it all to the top so this is simply saying I want to run through this particular uh exception and and handle it uh like so so uh let's see what it's going to do so let's make sure that I've got this straight so this is going to be the tri block that I have inside of this which

01:52:45
is going to supposed to end right here so we'll bring that in and of course this is going to be the try for the outside TR block that I'm interested in handling and and the catch for that and return the exception uh as a as a value eventually so so that's basically our uh function we're going to call uh of course in this case I'm going to just give it a little delay for it to wait so we'll say thread. sleep and uh one second because it's asynchronous I want to give it some time for it to finish

01:53:13
and there are other ways you can wait on the response but this is good enough for our discussion right now so when I run this code what do you expect this to actually do so want this to go and fire off a request and you can see the uh airport code in all uppercase coming back but what if something were to go wrong what's what what do we do about it so back in here if I said for example T which is not a valid airport code what's going to happen just for us to know this actually is calling it we'll say a get

01:53:46
name of airport let's say uh called we'll say uh get name of airport uh called for and let's simply say iata so we can see that being executed so when I run the code you can see get name of airport call for T but you did not handle the exception so it never reported anything so what gives what do we do now well clearly you have an exception that's being thrown towards you a couple of things you can do you could pretty much say uh in here well not not over there because you don't want the exception to

01:54:20
be transformed to uppercase you can say exceptionally and you can take the throwable that's given to you and what can you do you have an exception given to you you could simply say throwable uh that is given uh do get message right so you could get the message out of the throw a b when that were to fail and you could return it so in this case it says get name of airport called and it gives you that value but the exceptionally should have transformed the data Maybe network is really slow it's Tak a little

01:54:53
longer to run let me just give it a little bit more delay here to see it so so when we run it notice the exception is invalid airport code T that's what it tells us right there but that's coming from the message right here that's what you are receiving right so you could then say I could potentially extract the method message from that exception and maybe print it out much more so you can say invalid uh you know airport code uh that's the exception you're receiving uh in the exceptionally block and you can

01:55:24
handle that you're transforming that to a string and then you can pass it on for printing purposes in here so that's potentially what you could do so so this gives you an idea how you can uh transform this now obviously if I move this up here what's going to happen well if I move it up there that's going to be giving everything in uppercase so you need to ask the question where do you place this right do you want the invalid airport code T in uppercase then you would want to put this here but you're

01:55:55
saying no no no no I want to just wait until I get to this stage if the data Ares from here I want to print it if there was an exception anywhere before I want to handle the exception and print the message so you get to structure your handling based on where you want to so to say merge this path and move forward right so this this this exceptionally is converting from an exception to a um happy path so you're going back to the Happy path and so the then accept will run uh in no matter what in this

01:56:30
particular case so that so essentially what's going to happen here is if this was successful you run this and that's a pure function nothing to fail really in that case so it'll bypass exceptionally and you will get to this if this were to fail you will skip the then apply come to the exceptionally extract the exception from there and and this will print it so so that's the way we are structuring this in terms of how we are going to handle it uh again uh I won't say this is awesome or perfect it's a

01:57:04
not better than what we saw in the functional pipeline in terms of how this is being handled uh in in this transformation path now obviously the question is what if I have a collection of data that I want to deal with how do I you know provide that uh particular feature uh in here well you could try something along these lines right so you could say for example uh I'm going to just increase this a little bit more so we can see the result so I'm going to say in this case a list of let's say in

01:57:34
this case IAT codes is equal to and let's say in this case I want to call this as a a list of oh let's provide some code here so we got a bunch of them and then of course let's say I will introduce uh also uh incorrect value so t uh as s and of course sat a and and now I could do something like this I can say IAA codes and remember my function is not going to throw an exception at me right so stream I could call upon a stream and and then in this particular case I could say a map and we could take

01:58:15
the iata code and we could instead call the process iata code so we could say for example uh you know sample and we could call the process iata code and and this could be potentially because it's a terminal operation uh you could run it at the very end of this as a data or you can ask this to return a piece of data uh through the transformation and and as a result you could just work with this as a completable future itself so this could be here's the beauty of completable Futures is they never seem

01:58:50
to die right always get back completable future from this so you could say here's a completable future of void because I'm not really uh you know uh returning any useful data I could return a compilable future of the Void from the end over here so then you can go back and do a map on this one and and that's going to transform from this to a a collection of completable Futures if you will and you can get that back and then you can then say in here well what else do you want to do you can say for each and you can

01:59:23
process this or you can do a collect on it as well into a completable future collection and then you can wait for all of them to finish potentially as well and then call it call it done at that time so for example let's say if I were to map this over you could do a uh to list on this so that's going to give you a list of completable Futures so we could say this is you know tasks that you are executing so you could say I want to run off these tasks in this this by launching a map on it and get the

01:59:55
list and potentially you can block on it and wait on it to complete it as well so you can write a few more lines of code to say I want to wait on those and display it but it's going to go through the pipeline and start executing uh the stream values one at a time so so as a result in this particular case I'm going to remove this message right here we don't need that anymore so you can see in this particular case the airport names being displayed and and like I like I said earlier you don't want the

02:00:22
uh you know thread dot sleep that's kind of wild but we can get away from that very easily in a minute so notice now it is telling us Austin and Bush and you have a San Antonio and a Dallas Fort Worth but the ordering is not the same that's because they are pretty much is executing asynchronously so they could be running in any order that's the whole point about asynchronous computations and and if you really care about the order you'll have to implement a little bit more logic behind it but this is

02:00:51
mixing the street with the completable future to get the work done so this is a little bit of a better handling like I mentioned not a perfect solution by any stretch of imagination but but definitely you don't have that issue of having the every every you know function in the pipeline having to deal with the data and error back and forth a little bit better separation so I'll put a little note here a a bit of uh uh you know a bit better with a good a separation of concern for each function so essentially

02:01:28
then uh focuses on uh data uh exceptionally uh we can say uh focuses uh focuses on uh on error uh of course but we still have to deal with a mixture of uh you know uh data handling uh uh hand uh uh uh handling and error handling um you know handling uh in uh different uh stages uh of the pipeline so not the cleanest solution but definitely a notch better than what we saw with the functional style approach um I I still won't say that I I'll give a clean ticket to say this is great we want to go this route I would say maybe

02:02:18
maybe it's still worth considering going back to doing imperative Style if we have a lot of these multiple levels of exception to handle maybe that becomes a win even there so so this is again uh using uh using so handling let's say handling exception in the uh pipeline right uh of a completable future so that's basically what we saw in here let's move a little further now so we saw how this pipeline pattern works and we talked about what's nice about it and what's unpleasant about it as well so

02:02:51
that's that's the next solution in completable future uh but having said that one of the trends you are noticing probably is that in Java Script we have the promises and this approach was quite uh you know promising a while ago but most people writing JavaScript code probably have moved away from this approach right now so what are people doing mostly when they're writing JavaScript most likely they are using a rather than saying right A A A Promise do uh you know then and you're writing

02:03:26
some code and then a DOT then and writing some code and in the case of JavaScript they do a catch to introduce that code like that that can become really combersome so rather than doing that what you are doing mostly these days is you are trying to say for example constant result result is equal to uh you know a result is equal to a weight and you are calling the some function fun fub1 and then you are saying constant result 2 is equal to a weight and you are calling function two with the result one in this case but the

02:04:02
beauty of this is you can put a try around it and a nice catch around it as well uh to handle your exceptions so in this case it's an imperative style code but a lot of people writing JavaScript code have moved away from using promises to what is called an async and a wait and and the reason why async and a wait is a bit more popular as it gives a much better way of handling the exceptions again my point about maybe imperative style code is better when it comes to dealing with exceptions that's an

02:04:33
Evidence you see in the JavaScript side that people have kind of shied away from promises even though the functions are still returning promises to be asynchronous we are using more and more asynchron weit for the code and that's a trend we are moving towards now you may ask the question if that's a trend we are moving towards in JavaScript what do you expect to see in the case of java maybe in a few years from now well that change is already happening as you know uh in in the case of java so you

02:05:03
could say in Java 8 what you're dealing with right now is of course uh for example the competible future and you say do then uh you know apply for example and then you're dealing with exceptionally and you are dealing with whatever you want to deal with and then you're writing code like this again that's a lot like the JavaScript way of uh you know dealing with uh the uh promises isn't it but again if you think about it in Java 19 we are going to go back to a different way of programming

02:05:37
which is you're going to say a try and result one is equal to and you're going to call the uh function here F1 and and you're going to say a catch over here a exception and you're going to be able to handle that exception here and potentially you could go back and say you know here is a result one and you may you know try again if it makes sense and you could do uh you know F2 and result two maybe a result one which could store a value in a result two for example and so on and you may provide a

02:06:12
catch block so this is of course excuse me this is of course as you may recognize uh imperative style of programming and you say well how is this any different from what we did well the difference is thanks to uh project Loom you can have these functions potentially being executed a asynchronously so you can be in a completely different thread of operation and when you call the function F1 it can become non-blocking and then when the function completes its work then the exception can happen if there is one or you can move on to the

02:06:49
next stage so this brings back life to uh exceptional handling in imperative style a lot better so I'm going to say the trend I'm going to see is uh those of us who have been writing more and more functional style code probably are going to switch over to writing a bit more imperative style code moving forward when it comes to using exceptions and and that is because we can you know make use of threading more effectively and we can do more asynchronous programming a lot more effectively so if you are doing more so

02:07:22
so you know somebody was asking me the other day uh is Project Loom going to put an end to uh reactive programming my gut feeling is Project Loom is going to put an end to completable future because the as much as I like completable future uh still it's a bit of a mess dealing with exceptions and I can much easily do uh asynchronous code using project Loom compared to completable Future so so my uh you know uh my my um prediction is that project Loom within 5 years is most likely going to kill completable future

02:08:00
because uh this is a much easier way to write the asynchronous code than trying to write code with the completable future so that is that is what I think is going to happen in terms of how we structure the code and how we program with it so with that said let's switch over to one other model which is the reactive stream API so so how is the to stream API different from the uh both of the completable future and the uh stream API well clearly one distinction is when it comes to stream you know it's a zero

02:08:35
uh one or more pieces of data when it comes to completable Future on the other hand uh we talked about how completable future is really uh dealing with uh zero or one piece of data well in the the case of a reactive stream you can say it is again a 01 or more pieces uh of data so this is basically uh the difference between all of this so you're going to deal with the zero or one when it comes to completable Future whereas a stream is uh 0 one or more uh in general in terms of the data that you're going to

02:09:17
be uh dealing with so so that's the difference between these three different types of apis so uh reactive stream API is a lot similar to the Java stream API in that regard that it can deal with the stream of data however there are there's one clear distinction between these two so when it comes to a stream versus a reactive stream you can say there are some similarities the first is this is a pipeline of functions and so is the reactive stream API is a pipeline of functions as well secondly this is going

02:09:56
to have a lazy evaluation and the good news is this is an lazy evaluation as well so you got the similarities to both of those you saw me show this in the beginning where they both wear pipelines they both were lazy in evaluation the similarity though ends right there so the question is how do we uh handle uh exceptions so ENT entally the uh answer to that question in streams is uh you know good luck so this is not easy we saw this already right so it's not easy it's messy it can become complicated uh

02:10:36
the code is not cohesive not not really a great idea uh and again I'm a big fan of functional programming don't get me wrong but the time I quit really going that route is when I have to deal with a lot of exception I scale back to imperative most of the time time because my code is a a pile of mess at that point so that's why I say it's a good luck I'm not really interested in functional programming at that point when I have to deal with exceptions a lot but on the other hand the reactive

02:11:04
programming has some really beautiful ideas in here uh treat uh error uh as data that's the first thing the second thing is in the reactive stream API unlike the uh stream API there is one uh channel uh uh of data that's what you're dealing with in the case of a stream API you're dealing with one channel of data whereas in the reactive stream API this is quite different we have three channels of communication so the three channels of communication are the first is you have What's called the data

02:11:42
channel the second is you have what is called an error Channel and the third one is you have what is called uh complete channel so you have these three channels of uh communication so it's a it's a much cleaner API in that regard in terms of having these three different channels to communicate the data in a in a reactive uh stream API so what's happening in the reactive stream API so data flows through the data channel so that's obvious right that's what the data channel is for data flows through

02:12:19
data Channel at some point uh if no more data a signal uh May flow you can say through uh uh through in this case uh the uh complete channel uh and of course in this case and the data channel uh is closed so essentially you will not get any more data through the data Channel Once the complete channel is closed you're not going to get any more data through it done however this is also Al true if if there is an there is an error then uh you know first of all uh uh an an error uh flows through uh flows

02:13:06
through the uh error channel uh and the uh data channel uh uh at this point uh channel is a closed so no more data will ever come through this at this this particular Point uh anymore in the pipeline processing so uh so essentially the data channel is closed at this time and you will not get any more data through the data channel uh in this particular case so that's a behavior of the reactive stream API which is very distinctive from the way a stream API works as you can see so this is a much

02:13:44
more better level of abstraction on top of it it's intended to provide it again I want to emphasize not a perfect solution right uh none of the solutions here is going to be perfect unfortunately uh like I said in the earlier in the talk uh we don't have a clue how to deal with this yet we are still struggling so so I'm not here to tell you this is great go home and do this well treat everything with with a bit of a suspicion but this one is a better abstraction compared to the other

02:14:12
things not the best one yet so so essentially you have a stream of data and the data flows to the data Channel if something were to go wrong you're going to get a error through the error Channel and the data Channel closes up the disadvantage in this case is once the data Channel closes no more processing of data is possible so that is the consequence of this particular model so as long as things are flowing and things are happy you're having a great time but the minute you hit a problem that's it you're stopping the

02:14:45
show you're not going to get any more data flowing through it so good news you're handling exception really well good news the code is cohesive good news you're dealing with the error at the end of the pipeline the bad news is if something were to go wrong you're not going to continue to process the next piece of data in this particular model you're going to just stop right there and not propagate it anymore you could argue hey why not create a reactive stream where you only propagate one

02:15:18
piece of data and then wrap that with maybe a stre stream on the top of it that might be an Overkill but it might give you a bit of a relief from that in that you can have this one pipeline if it were to fail well that's a failure report but another pipeline for the next piece of data in that case you're look using the reactor stream API more like a completable future uh to process this data that may be worth looking at to see if it gives you some relief especially if you're able to take this all the way

02:15:49
down and handle it and obviously in this case the error may happen in any stage in the pipeline and you're going to go to the end of the pipeline to handle it so let's take a look at how this is going to manifest itself in the code right here just to get a feel for it and and see how we can use this so so here is the let's go ahead and uh take the function that we have the get name of airport so what am I going to do here well we're not going to use the completable future right now so we have

02:16:19
this list of data right there and once again I'm going to Simply get rid of this T and keep those good airport codes for a second so what am I going to do here I'm going to first of all bring in let's say import let's say iio reactive x. star and we will say uh I want to start with the reactive stream API so let's go ahead and say a flowable let's start with the baby steps here so flowable uh Dot and we will say in this particular case uh from iterable and we will say iata codes right there to start with

02:16:59
then you say do subscribe and here comes the output so system.out and we will say print line and we will just print it out for a second we'll take baby steps towards this so I want to just print that particular data out so you have those three well guess what this is basically your uh data channel that you have right there so your data channel is going to give you the data that we talked about then you say is's an error and I want to really print out maybe that error right uh if that's all you want to do you can

02:17:33
print it out or you can say error and then you can print out the error like so that to you is the error channel that you have so you are saying if something were to go wrong I want to handle that error and print it out that's what I want to do and then the last thing of of course in here is the complete channel so you can simply say something along the lines of maybe a done to say you are done processing so when you run the code you can see the done in the bottom that's because a complete signal was

02:18:05
received so you have the airport codes and are done in the very end so the complete channel was activated as well however going back to this code I want to say do map and I could take the code and take the data and turn it to uppercase so I could say two uppercase like so and convert to an uppercase and send it obviously it was already an uppercase not a whole lot to really do but that's a pipeline we are seeing again but then I can say a do map oh here comes a charm by the way huh no go back to this and I'm going to remove the

02:18:39
try I I almost forgot to mention that so I'm going to remove the try from here uh entirely and uh this this can throw a runtime exception internally but it's going to actually be the codee we saw earlier that we're going to use with the uh Tri as as in there and I will get rid of the catch block and the wrapping we did earlier so so this is going to be the get name of airport that you saw and obviously that better throw uh IO exception right so throws IO exception uh right there so the function now is not just wrapping

02:19:19
things into a runtime exception but I go back here and say map and I'm going to take the iata code and and then uh call this function as you can see right here I'm going to say get name of airport and pass the iata code uh to it now the first and uh interesting good news here is that you can actually call the function directly in here so it says cannot convert to string it's complaining to me let let's see where oh sorry thank you uh thank you very much so essentially when I run the code

02:19:55
notice there was no compilation error right that's the first observation so what's a good news unlike the stream API your uh transformation functions can deal with checked exceptions this is a departure from what you saw in the Stream API uh so if you're are blowing up with checked exceptions you don't have to wrap them into unchecked exceptions bring it on and you're able to deal with it just fine that's one clear distinction in the pipeline you didn't have to really hide away from the

02:20:26
checked exceptions as you can see in full Glory you have throw IO exception and this code is like no problem bring it on I'm not going to give you an error and the reason for that is the functions you pass to the map in the functional API uh uh support uh exceptions so you could blow up in them if you really want to when you run the code this time what are we seeing as an output you see the airport in upper case you have Austin George Bush Dallas and San Antonio so far so good isn't it however the what if

02:20:59
something were to go wrong so if you were to go back here and say here comes a t which is an invalid airport code and you run the code this time what does it do notice it gives you the right result for the ones that worked when it runs into an error it says error the uppercase error because that's what I printed in the terminal and it says runtime exception invalid airport code T and it gives you that error but notice what it didn't do it did not process sat that's because a reactive stream API

02:21:33
will stop the minute it runs to an error it doesn't continue with your pipeline I mean you could get creative like I said to really use this for one pipeline but maybe that's an Overkill to to use it for the wrong purpose right so but but that's the nature of the reactive stream API is that it's going to be stream in data until uh you know nothing goes wrong if something goes wrong the data Channel closes the error channel gets the error the complete signal is not received so you can see when you run the

02:22:04
code you don't have the done in the bottom that's because it didn't finish favorably right so as a result it simply says sorry uh it was abnormal termination I'm not going to give you done on the other hand as you can see in here uh if there is no exception then that that that's great and you're not dealing with exceptions in each of the stage so by far I would say this is the cleanest I can come close to keeping the exception separate from the data that you're handling but again it's not

02:22:35
perfect like I said because it doesn't continue with the pipeline once there's a termination even though gives you a better handle in handling uh the exceptions itself so that's example of how you are able to handle that in the reactive stream API so let's go ahead and save this away uh you know exceptions in uh reactive streams oh I want to save the notes I wrote here as well before I uh you know uh save that away so let's take a look at this uh really quickly uh before we uh go to

02:23:06
that as well so I'm going to just grab this notes and copy that over here so uh that's there at the bottom of this if you download and you want to take a look at it at least you have so I want to make one more observation here uh while we are here so uh map filter Etc can't you know call call uh you know uh lambdas that have a checked exception right so on the other hand here uh the map filter Etc uh you know can have uh you know uh lambdas uh that may uh throw exception right throw checked exception so that's

02:23:48
another difference uh in this case so we will save that away so so that's basically uh uh a difference between those and I'm going to go ahead and push that in there and save it so so let's uh summarize what what we talked about so far and and where we want to maybe consider uh this uh and and the pros and cons of what we talked about again I'll push this into this uh you know uh soon after the talk so if you want to download and play with the examples you'll be able to do so as well now the

02:24:17
model of the story really is that unfortunately though exception handling is predominantly an imperative style of programming idea so when we deal with exceptions by Nature we've been writing more exceptional code in mainstream languages and and exceptions generally blow up the call stack and then they propagate uh to the functions you called from until you get to a catch block while that model works really well for imperative Styler programming that doesn't go well with functional style at all the model I want

02:24:48
you to visualize is is free way in which you're driving a car if you have a problem what do you do well you definitely want to exit safely you don't want to ever go in reverse so there's no luxury of rolling back your call stack in a functional style it's a pipeline you have to deal with that's absolutely essential so it's worth really asking the question what's going to happen as we move forward in the future uh I would say the pendulum is swinging if you will uh and the Pendulum was all the way to

02:25:20
to the to the you know one side where we did imperative style almost exclusively in mainstream languages uh the pendulum started swinging this way and we've been exploring more and more a functional style of programming now how far do you think the pendulum will go I would say to me at least that limit is until we hit exceptions and so there's a sweet spot I think in between where I'm happy to leave out imperative style and programming fun functional style but to me I don't want to go overboard with it

02:25:54
and say that's the only way to write code or the best way to write code because like everything else say there is limits to what we can do and I think the functional pipeline that pendulum is swinging far enough but the minute we hit the areas of exception handling we want to kind of you know slow down and question what we do and say is this still the right way to do but to me the best thing is the power to the programmers the power to you and me and that is we get to use the tool that makes the most sense for what we do we

02:26:29
don't have to convince ourselves that it has to be one way or the other we there are times I would do one way there are times I would do the other way and I'm willing to change and evaluate it but I want to pick the best I know for a given situation and for me imperative style is not great when the code is very complex and has mutability it can become really hard but if I'm able to write that code in a functional style I'm reducing The Accidental complexity my code is easy to understand code is easier to uh you know

02:26:59
maintain easy to paralyze but unfortunately that limit is exceptions and when I hit the exceptions and then I ask the question what kind of API am I working with if I'm working with the streams API I generally try to shy away from exceptions in streams API I would much rather scale back and start programming and imperative style at that point because my benefits of functional style are not really uh there anymore to the extent it was before I get into exceptions if I'm dealing with a compatable future it's a notch better

02:27:34
but I'm still not convinced about it but I'm almost certain like I said completable future completable future would would probably die away in about five years that's my prediction right I don't have any wishes for it to go away but but given that you have completable future and you have project Loom I think Loom is going to give us a better way to do asynchronized programming compared to a complet completable future so I think uh you know with the mess up having to deal with the exceptions in it I think

02:28:04
we'll find a better relief in imperative style uh and of course with the reactor programming I think that's probably the best of the three in my opinion though not the best in general and it gives a nice separation but still has the flaw that it cannot continue the pipeline when when it's it's blown up and and that's the one of the disadvantages of that but I think moving forward uh we have a situation where uh you know those of us who got really excited about functional style of programming in in

02:28:31
all honesty I think we will pass a little bit and say maybe imperative is not as bad as it is in situations where we have to deal with a lot of impurity and exceptions and maybe it's a balance we want to strike and maybe a certain situation where we may choose imperative more than functional and certain situation where we may CH functional more than imperative I think having that option to balance is is a power to us as programmers I think we would uh leverage that uh to to get the big win out of it

02:29:00
so hope that was useful that's all I have thank you [Applause] [Music]

