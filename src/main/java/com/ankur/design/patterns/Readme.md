00:00:01
[Music] systems are nominal initialize Genesis sequence good morning welcome to the uh workshop on uh design patterns Revisited in modern Java my name is wenat subramanyam it's a great pleasure to see you all here today let's talk a little bit about what we're going to do here and then we'll get rolling we got about 3 hours but I also am sensitive to taking breaks so we'll run for about an hour and 20 minutes approximately we'll take a 20 minute break and then we'll go

00:00:35
for another hour and 20 minutes uh during the break make use of the time to refill your liquids use the facilities uh if you have any questions I'll be delighted to talk to you during the break as well so what we're going to talk about today are a few design patterns which I feel are really exciting to use with the evolution of java so we'll talk about some do DOS and we'll talk about some don'ts as well and then we'll talk a little bit about uh a few different directions design

00:01:03
abilities in Java is moving and how we can benefit from that as well uh we'll start first of all talking a little bit about design patterns now before we dive into the concept of design patterns we need to uh think about when and how to apply design patterns I've been writing code for a few decades and uh design patterns is something that got introduced uh a long time ago we have used patterns over time uh I would say in in in particular Java programmers have a special Fascination for design

00:01:35
patterns I know some of us have probably had it for breakfast this morning before we came here isn't it so we love to use design patterns but there is one problem I think with design patterns in general and and and to relate to this let's step back to an analogy for a minute now we all can agree that Grandma makes the best pie you've been talking about Grandma's pie forever everybody that you know and you've been saying how you enjoy Grandma's pie well your friends tell you one day if Grandma has made

00:02:09
such a great pie for you all these years we're going to come to your house on Saturday we want you to make that pie now pressure is on so you call Grandma and say Grandma I'm in trouble my friends are coming over I've been talking about the pie you make they want to make it can you give me the recipe so Grandma says take some flowers take some sugar you're like hold on hold on I need more help tell me how much sugar should I take and grandma says oh take about two and you're like is it teaspoons or

00:02:39
tablespoons you write all that down and then you tell Grandma can you tell me what are the best practices what are the dos and don'ts and then on that fateful Saturday you wake up early and you make the pie how did that turn out to be yeah I can I can hear your laughter right so that tells tells you how it went last time I tried something like that I lost the utensils with the stuff I was making as well so you make Grandma the next week and say Grandma your recipe did not work it was a disaster you need to

00:03:14
really tell me how to make the pie and grandma says why don't we make pie while we talk and Grandma's having a good time talking to you and she's making the pie and you notice how does she add sugar she takes two teaspoons of sugar measures it very accurately and puts it right no that's right she's having a good time talking to you she takes sugar tosses it in and does that you said whoo What was that and she says sugar and she you ask her how much did you put she says enough and that's when you realize

00:03:53
grandma doesn't follow things like the way we try to do as a narice well what I'm getting to is the grandparents of our industry wrote that fantastic book called design patterns about 30 years ago I know that's a bit scary right 30 years seems really long time ago I was a very young programmer back then I'm still a young programmer but I was a young programmer back then but I remember reading through the design patterns book and and they are the grandparents of our industry and I think

00:04:27
the worst thing they the four of them ever did in their life is to write that damn book because the minute they wrote it everybody thought I can read the book and I can do that too and what a disaster well now that I motivated you so much about design patterns let's talk about why design patterns are great and why they are disaster at the same time design patterns I feel is great communication tool design patterns unfortunately are not great design tools and the reason I say that is if you wake

00:05:04
up in the morning and the very first thought in your mind is I wonder what design pattern I can use when I go to work today I recommend you call the boss and say you're very sick don't go to work because systems never get designed by applying patterns that way you don't just wake up and ask the question I wonder what pattern can I use today it's like somebody sitting in a garage and asking I wonder what tools I can use today that is not the way you do your work you are doing something in the

00:05:39
garage and you realize hey I can use a hammer to drive this in oh maybe a chisel will be useful in this case maybe a a screwdriver is what I need for this one so you discover tools to use on the job based on what you're trying to do you don't plan your day asking what kind kind of tool could I use for today's work that's not generally the way we get things done sure we have a set of tools in our toolbox you reach into the tools as you need them but you don't really sit there and ask the question I got a

00:06:14
hammer on my hand anybody has a need for it so the point really is design patterns very rarely at least in my experience fit into what pattern can I use but they are extremely good in terms of a communication tool you're sitting down looking at a design talking to a colleague and the question is how does this work and you can take many words to explain how it works or you can say oh we're using an abstract Factory here or we're using a factory pattern here or we are using a visitor here so the point

00:06:50
really is you can use these to communicate what you're trying to do rather than trying to sit there and devise what you want to really implement reminds me of an experience I had a developer come to me and said hey I'm trying to use this pattern in the code and the more I try the harder it keeps getting I'm really getting confused at this point not exactly sure how to use this design pattern can you help me and I said I'll be delighted to let's sit down let's talk about what you're trying

00:07:22
to do and the gentleman starts explaining the problem they are trying to solve and as we are discussing that we go to the board start drawing a little diagram of the design idea that comes into our mind and and and a few minutes later we have a little design to solve the problem that he described and then I said what do you think of the solution he said gosh this is really simple I think I can get this implemented in the next 10 to 15 minutes thanks and he walks walks out and then quickly makes a turn and says wait a

00:07:52
second I came here asking how to use the design pattern and I don't think we ever talked about that and instead we looked at the solution to the problem I have and I asked him what is your goal is your goal to use a pattern or is your goal to solve a particular problem and he chuckled and said of course it's to solve the problem thanks this is actually something and get my work done and I can move forward so a lot of times we get tangled up on using a pattern my recommendation instead is don't try to

00:08:24
force fit a pattern into your design instead naturally think about design and work towards evolving your design and let the patterns emerge out of the design in a way you can gravitate towards a pattern rather than trying to force fit a pattern and that is often effective so having said all of that the patterns are still useful like I said to think about how to communicate a pattern but of course there are two kinds of patterns unfortunately the uh good patterns or the good use of patterns and of course the anti patterns as well

00:09:02
which are things we shouldn't be using but we often tend to use so it's important not only to understand how to recognize patterns in what we do but also to recognize anti-patterns as well so we don't fall into a trap of using them so let's start with one particular thing which is the optional and I find optional to be useful but at the same time it also can be very largely misused so I want to talk about that to begin with in here so what is really optional so let's say for a minute that I want to

00:09:36
create a function let's say in this case I'm going to say a a string let's call it as get name and I want to provide a ID for some name that I'm going to be requesting for from an application so let's just uh you know hypothetically speak about a particular function API in this case so I'm going to return a string for the get name but of course as we can imag imagine the ID may or may not exist so what happens if the ID doesn't exist so you could say for example if ID is equal to you know zero

00:10:12
just to simulate a fact that it doesn't exist versus if it were to exist let's say some name I want to return in this particular case so I'm going to say some name for a minute now the question is what do we do if the value does not exist so the problem in this case is a user of the code may be doing something like this they might be let's actually make this a bit static so it's easier to use it so excuse me in this case we could say that we are calling the get name and of course I'm going to provide

00:10:45
a one and I could say a two uppercase like so and and obviously in this particular case that's not going to be a problem we're going to get the sum name convert it to to uppercase and then we're going to Simply print it so that was not an issue on the other hand what are we going to do if we don't have the name so we could be tempted to return a null in this case now I'm going to say I often repeat this to my developers that null is a smell so essentially they both are four letters so essentially actually

00:11:17
smell this five letters off by one error so essentially the idea here is that when we use a null the gentleman who introduced null uh Tony her uh you know apologized publicly he called it this billion dooll mistake this is uh a really a terrible idea but yet we see this quite often in the code but the question is what's going to happen if we do have a null being returned well unfortunately though one of the things we want to think about from the design point of view is something I'm learning

00:11:52
quite often to do and I constantly think about it and and that virtue in my opinion is uh you know code should reveal uh its intentions so this is one of the uh very important design characteristics we want to use a code should reveal its intention now when you work with things that reveal intention you get along doing your work when you deal with things that don't reveal intention you feel like a you you you feel like an idiot you feel frustrated how many times have you seen this around you um years ago when my son went to

00:12:33
college I was traveling around the world he was away from home but there was one thing that connected us sadly I would say and that is we both in different parts of the world will constantly text each other photos of really bad design we will run into uh I would be walking through a a a a hotel and I would be looking at a door and literally not sure how to use the door because do you push the door to open it or do you pull the door to open it how many times have you seen that you go to a door and you're

00:13:09
like do I push it or do I pull it and you do it in the opposite direction and then you're not sure about it the worst thing humans have ever created is the USB have you ever inserted a USB correctly and I'm consistently wrong and and and I feel like an idiot every time I use one so these are examples of terrible design and and we see terrible design everywhere around us and and that should teach us a lesson how can we design something where it's obvious so people don't have to be cursing us and

00:13:45
that is one thing I think is common right uh one day my I I work off the basement and I usually I'm sitting alone writing code and my wife said to me one day she said wenet you are the politest person I know I said yeah thank you thank you she said no no no but there's always this but coming through right after the statement but I I've come along downstairs when you're working you don't know this but I listen to you when you work and all those filthy words that come out of your mouth and I told her

00:14:15
well honey that's called coding right because we get frustrated when we write code he like what does this code do and followed by all those you know curses we uh cursy words that we uh tend to say the point is you want to really create a design that reveals its intention the problem in this code is what if I were to Simply send a non-existent ID we get a null pointer exception right there uh tell me if you enjoy receiving null pointer exception how do you feel when you get null pointer exception if you're not sure

00:14:51
about it I've got a really good friend you should ask him and poor gentleman he had an entirely long day at the end of the day he goes to his hotel room and he turns on the TV and the TV threw a null pointer exception at him can you imagine this forget about horror movies to watch that is scary enough so clearly unhandled exception coming up to your TV box is the lowest point in anybody's life I think so the point really is we don't want to really slip through these things we want very clear deterministic

00:15:27
code so how do we handle that what one way we can do this in Java is to use optional well certain languages like cotlin and C to say to name a few have what are called nullable types nullable types are nice that they prevent you from accidentally getting into a null pointer exception well Java doesn't have a nullable type but what comes close to it is the concept of optional so I can go back to this code here and say optional of ring in this particular case and then we can say this obviously is

00:16:03
coming from java uh. util and I'm returning an optional of string so back here I can say optional of the value that I want to return whereas in here we can simply say optional of empty and we can return that to the caller as well now obviously in this case what we want to do is to be able to get the result of it let's take a look at that real quick and we can see that it gave us an optional of empty versus in this particular case if we do have a value it does say optional of some name so in a

00:16:44
sense we're using a level of indirection so rather than returning a reference to the caller we're returning a reference to an object which in turn holds a reference potentially now the benefit of this of course is how do we get the value out of this particular object so you could say for example result is equal to and you can assign the result in this particular case and you can have a couple of different options to get the data out of it now clearly you cannot do results result dot to uppercase because

00:17:19
two uppercase is not a method available on the optional so that doesn't work obviously so what you're going to do in this particular case is to say either an imperative solution so you can say imperatively you could say for example if result. e is present you can query it and if it is present you can get the value out of it otherwise you can do something about it so for example not found if it were not to be found you could do that so in this case you can see it says not found but if it were to

00:17:53
be available we can then say for example output result dot what well there are a couple of options available in this case one is a method called get and generally speaking I'm not a fan of the get method I recommend not to use get method so I will simply say uh please I would say uh for uh forget so don't use the method right that's not a great method the reason I say don't use forget is uh it it breaches on again that idea of of really being uh revealing intention get doesn't tell you that it's going to blow

00:18:35
up if the value doesn't exist literally they provided an alternative to uh get method which does exactly the same thing just with a different name and I recommend using that so you could say or uh you know uh uh R else you could say throw and and that's a way for you to get the value out of it so or else throw reveals the intention a lot better to say hey if it doesn't exist I'm going to blow up so you know so the intention is a lot clearer than using get so my recommendation is uh do not use get uh

00:19:12
instead use or else throw uh or or else uh you know with a default value right so you can say a default that you can provide as well so those are my preference use or else throw or use or else with a D default value that is much better just to emphasize that right here you could do that as some name or you can simply say instead of that you could have said you know R else instead of R else throw and you can you know say uh you know nobody right so essentially uh you can say uh whatever you want to say

00:19:49
and you can provide the value for it as an equivalent so those are some Alternatives so so this becomes a nice way so this is an imperatively you can do so on or what you can do is more uh functionally if you will you could have said for example result. uh you know if present and you can write in this case uh you have a value so name and I want to print the name out so that's one way you're providing a consumer or you can simply say in this particular case you could simply say not found to illustrate

00:20:24
that the value doesn't exist in this particular case and you can provide that as well so that becomes a different way of writing that code you can say uh sorry if present uh you know or uh else and you can do something about those two conditions if you will so if present by the way was introduced in Java 8 but that did not include the else part but they added a if present or else in Java 9 so you could use the else part as well so that becomes a little bit more concise code if you want to say what you

00:20:56
want to do for each of these parts just to illustrate that we can provide that as well so that is basically the pattern of how we can use optional now before we go a little further into this I do have uh uh uh one one one thing to clarify here I actually had somebody argue with me about this and they said I don't like optional well that's a fair statement you don't have to like everything but there's got to be a good reason why not to like something and I asked them why they don't like optional and they said

00:21:32
because it doesn't prevent null pointer exception for me and I'm was curious said really because if you use an optional you got to really check it before you can use it and they said aha but I write code like this they said so they said return null now uh you can't you know avoid uh fools right uh from using your language so this is what of the things to really think about so clearly that was not the intention but the person's argument was I'm going to turn a null where option is expected so

00:22:09
you cannot you cannot make everything foolproof because there's always a bigger fool and when you see them just smile at them and say you're awesome and just walk away it's not worth your time it's not worth your effort so clearly that's not supposed to be done so don't do that and if you do have to work with somebody like that well it's there's always another job to look for so the point really is that optional can be really useful when you return something now let's talk about some anti patterns

00:22:40
we want to consider one use optional to return values oh let's actually step back for a minute so return a reference a reference uh if the value will always exist so this is generally Norm that I would like to use in writing code now I understand we got a lot of Legacy code uh but as we are writing newer code and as we are you know evolving code there are a few practices we can follow and one of them is return a reference if the value will always exist so when I see a return type of string a return type of

00:23:22
person I don't want to question whether that's going to exist or not I don't want to perform null check if you perform null check everywhere that's called being a paranoid when programming you want to have confidence with the data that you're receiving So within a circle of your application you can do this and you can cleanse the data in the peripheral as it flows through so my first rule is return a reference if the value will always exist return an optional uh uh you know if the value May

00:23:58
or or may may not uh exist so if the value will exist or the value may not exist then you want to convey that by returning optional so the minute I see an optional I realize aha this value may or may not exist so I need to query it before I have to use it so again this comes back to the program with intention you are communicating the intent so the reader of your code or the API is very aware of what to expect in this particular case the third thing I would recommend is uh do not use optional uh

00:24:38
for Fields this actually makes very little sense so if I'm going to create a field within my class for example let's say I'm going to say over here a private let's say uh a string uh let's say config info of some sort well instead of doing so so should I say optional of really that particular value so that's a question we want to ask should I really provide a string or should I provide an optional I'm going to say uh this is overhead uh with little benefit so remember this is your internal code that

00:25:22
is encapsulated you are having a very small uh area of influence where this can affect your code that's why we call it encapsulation when you encapsulate it properly the spere of influence of that code is limited so by protecting yourselves from keeping optional gives you very little benefit and and the overhead of that though is that you're not storing a reference now you're storing a reference to an object that store a the reference that is an unnecessary overhead you are dealing with so there's really no

00:25:58
benefit to doing it I would in fact I would encourage you to rethink about your design if a config information is not provided I would literally set that to a default that might actually make sense so you will always have a value for the config info rather than it being null or empty because after all you have to deal with the situation when it doesn't exist what would you do if it doesn't exist well whatever that is just store it in there if it's not there so you don't have have to worry about it so

00:26:30
that's one recommendation is don't use it for a field and and the next recommendation is do not use optional for method parameters this also makes little sense let me explain why in this particular case let's say for a minute I have a public let's say a method void uh let's say set info and I have a string info like so and this is going to provide me some information that I want to set let's say now obviously in this case you are saying yeah but the color of this code will have to do the

00:27:08
following they will have to say uh set info and I'm going to say some info they're going to pass in or if they don't want to pass in some info they send a null and and we're not very happy with that they are sending null after all would be nice to not having to use null so what's a solution I'm I'm going to say uh the bad idea solution is the following you are going to change this to uh in this case literally optional of string like so info this I would say is really a bad idea why is this a bad idea

00:27:46
why shouldn't we do this well the reason I would argue is now imagine what we just did so if somebody wants to pass in an information they have to say optional off and provide the information they want to and if they don't want to provide an information they have to say uh optional of empty and and notice how much more effort it is to call that particular code uh is it Pleasant is it some do would you smile when you're writing code or will you curse saying oh gosh this is stupid why am I doing so

00:28:23
much effort into calling this function so the beauty of this is that we can easily use in this use overloading instead so take a little bit more extra effort but you're really helping your users of your code a lot more so what I would instead do rather than doing this is I would simply provide uh two functions like so I will provide a set info like so and maybe a static void set info which doesn't really take any data or even you can even call it as set default info if if if that makes it much more clearer well

00:29:04
the benefit of that is instead of doing so you're going to Simply say the following you're going to say set info some info when you do have some information to pass in or you can simply leave it out and not have to really clutter that and that becomes a lot easier to call it so it makes little sense to use optional as a parameter because it makes it easier for us now there's exception to that rule this is when a human is going to call your code on the other hand if your code is being

00:29:37
called by a machine generated code we don't care about it so if you're putting this on a controller in Spring that's fine but if you're putting this in a service in Spring I would have trouble with it so it depends on where you're putting this code as well so I would say for most part avoid this there could be a slight exceptions but in general that's a good rule to follow so I would recommend that we use optional for return types but only when a value may or may not exist don't use it for Fields

00:30:11
don't use it for method parameters as well uh it's a lot easier to fall into a trap and start using features where it may not be the right ways to use it I've seen developers use this for various purposes so word of caution when it could be useful and maybe when we should avoid that is generally a good thing to do so that is one thing I would recommend is keep your eyes out for it so you can decide when uh to use optional and maybe when it's actually a overuse and maybe you should avoid that

00:30:42
as well so with that said let's move on to talk about a few other patterns design patterns have been around for a long time but I'm going to say that I see patterns in the light of three different uh colors I would say one is there are certain patterns that have been around for a long time and we can continue to use those patterns there are a few patterns has been around for a while but I think we would be using them differently thanks to the evolution of the language and and thirdly I would say

00:31:16
uh there are some patterns we couldn't use before but we can use now because the language has evolved to provide some new capabilities but before we go into it I I do want to emphasize one other thing about design patterns I would say the more design uh patterns we talk about the less powerful uh you know uh a language is so essentially I would say the more powerful uh Lang uh uh language is the the the more uh uh you know we will use I would say integrated features than design patterns so generally

00:32:00
speaking I I remember way back in time I I used to program in C++ uh for developing commercial applications and we used to use a lot of patterns when I started learning Java I was quite amazed I was like wow all these things we talk about as patterns in C++ are natural features in Java I don't need to to celebrate them as patterns so so I consider Java to be a little bit more powerful than C++ those C+ programmers will get angry when I say that but they get angry no matter what I say so that's not a

00:32:42
problem so the point really is that Java really has some powerful features for example in Java we have reflection so you naturally do things as reflection whereas when you're programming in language C++ you got to put put extra effort to deal with some of those things that don't have you don't have as a capability now in a similar way when Lambda Expressions was introduced I had a aha moment because the patterns I would use in Java decide decided to kind of disappear and blend into the language

00:33:19
I guess that's a that's a name I should use that they they blend into the language so the more powerful a language is the patterns blend into the language as a feature rather than standing out as a design pattern and and that is one of the nice things about evolution of language is that more patterns May begin to blend into the language as time goes on which means it becomes natural for us to use them and we don't have to put extra effort to use them as well let's take a look at some examples of this

00:33:51
along the way well one of them is the iterator pattern now iterator pattern is pretty Broad and iterator pattern allows us to really iterate over a collection without having to know the specific type of the collection so essentially whether you are itating over a list or a set you don't have to worry about the iteration you can have a common abstraction to iterate that's basically what a iterator pattern provides for you but I'm going to take a tangent here and talk about something a little bit different in here

00:34:27
and and that that is when I program I often think about external iterators versus internal iterators so for example if I were to say I have names is equal to let's say list of let's say Tom uh comma Jerry let's say and I want to iterate over this particular collection you could have said far in I equal to Zer I less than names. size uh in this particular case and then is it less than or less than or equal to that is something you have to think about as well and i++ and you are wondering is it

00:35:08
i++ or Plus+ I all those decisions you have to make and then of course you can pull the elements out of it by calling a get method on it so that is one way to write the code uh and and that is essentially what is called an uh external uh iterator and we have used this all quite often in our code for a very long time now of course this evolved quite a bit in Java 5 but also an external iterator so you can say far you can say name coming from names in this case and you can output more easily the name that you have at hand so that

00:35:48
becomes a lot easier nevertheless that is still an external iterator but why is it an external iterator because in the case of an external erator you control every step of the Adoration for example you know things for example like a break continue Etc we tend to use them in the code as well that's basically what an external iteration is so for example uh in this particular case pardon me let's say we have a collection of names given to us and you have let's say Darla here and let's say uh you have you know Bob

00:36:28
and you have let's say Jack and maybe I want to iterate only as long as I don't see Bob so I could say for example if name is equals to let's say Bob in this case I want to Simply uh break out of this Loop and I could say break at that particular point so in this particular case as we can see as we iterate through the collection of names so we are going to say in this particular case oh names. get of I obviously in this case and you can refactor that we exit when we see Bob but that's kind of verbose isn't it

00:37:05
similarly you can come back here and say if name dot equals in this case let's say again a bob over here then I want to break as well so we are breaking out of that Loop uh in this particular case so you can ask it to uh provide that exit out of that at that particular instance so essentially the idea behind this is you can pretty much ask it to uh break out of the loop when it when it is time but we can also use an internal iterator I'm a big fan of using internal iterators because the code becomes a lot

00:37:41
more concise and uh the code begins to read like the problem statement it's easier to work with and this is also comes from the idea of functional programming versus external iterator we typically use in the case of imperative uh style of a style of programming now again there's no right and wrong way here it's just a question of what you are more comfortable with so if you're using external error errors you're going to write a bit more verbose code but I'm a big fan of using internal iterators so

00:38:16
I can say names dot in this case uh for each and you can simply say system do out and print line and you can simply print it out uh right there so when you run the code you can see it's printing those names as we can see right there now obviously in this case uh if you want to really exit out of this how do you really exit out of that well break is a statement this is something we need to draw a distinction to uh you know in uh imperative you could say style of programming we use statements uh you

00:38:54
could say statements and uh Expressions this this is another aha moment for me called me silly but I get excited about these kinds of stuff so when you think of a statement versus an expression an expression returns a result back to you a statement uh doesn't return anything in general uh a statement performs action but doesn't give you any result uh a statement will do some work cause a side effect cause a mutation change something in memory and then walk away and if if you want to know what it did

00:39:29
you got to go look at that memory to know what it did well on the other hand in functional programming we often use Expressions instead of uh you could say uh statements so essentially that's one part of the reason for that is in functional programming we don't want to cause side effect you don't want to mutate variables so as a result you think about it and say hm if I'm programming in a function style I would be doing fewer mutations doing fewer side effects which means I'm

00:40:04
going to move more towards Expressions than statements well as it turns out break is a statement so it doesn't play a role in functional programming style but the question is how do you break out of a loop in other words you want to manage the control flow but you cannot use a break so what do you do for that so in this case I'm going to Simply say that I'm going to start with the stream right there and then I say do take while so take while is the equivalent if you will so you could say in this case take

00:40:42
while uh is the functional programming equivalent uh of uh imperative style uh break so essentially it allows you to break out of a loop or break out of a iteration so you can say take while name and you can say name. equals in this case uh uh Bob is what we are looking for and of course in this case while name is not equal to Bob is what you really looking for so take while name is not equal to Bob keep continuing so you can see how in this case it walks through now clearly in this case the minute it hit bob it's exiting you have

00:41:26
to work a little bit through to go through that and and process that and maybe not you know eliminate it right at that moment but the point really is that is one option essentially there's an alternative to this as well which is called um a limit and a limit takes a number rather than a limit taking uh a a predicate so essentially in this particular case if you know how many to process you can use a limit this is where you would be iterating and if you process that many number number of elements you break or you can do a take

00:42:00
while which allows you to break out of a loop the minute you hit a certain value in this particular case for example so you can work through this kind of logic in this iteration and you can use a functional Style versus imperative style the the beauty of this is as you're writing code uh Java being a hybrid language you can pick and choose what may make sense depending on uh where you're writing the code and you can use imperative you are more comfortable with it functional style where you more

00:42:31
comfortable with it as well so with that said let's move on to the next pattern here which is the strategy pattern I think a lot of us would have used strategy pattern over time but I want to remind you of what a strategy uh pattern is so a a strategy pattern simply says so we have an algorithm of some sort and we want to a very uh small part of it so essentially this is where I would typically use a strategy pattern so I have a algorithm but I want to be able to vary a small part of the algorithm

00:43:13
keeping the rest of the things literally the same uh for example I'll give you an example of where I've used strategy in in my my experience um I have a a large application where we need to perform a lot of computations on various units that I have in my entire application uh imagine for a minute let's say you have a uh engineering application where you got devices and all these devices need to be uh calibrated based on some computation uh or you can think of an application where I've got a lot of

00:43:50
employees and I need to compute a raise for these employees based on certain calculations so in a sense you got algorithms to run across your entire application uh back to the engineering application where I have devices uh one of the things we had to do was uh a engineer who is working with the system might want to run through the calculations very quickly and their real concern is not accuracy at this point but what they want is converence so they want to converge towards a result very quickly rather than taking way too much

00:44:27
time to compute but at some point as they are working through the application they may come to a stage where they say I've got an approximation of what I want but it's time to get really accurate right now so they may flip a SED and press compute now we don't want a fast computation we want a more accurate computation so I would call that as a strategy because a user is controlling the strategy saying I want fast convergence as a strategy Now versus I want more accuracy as a strategy now but

00:45:07
the rest of the application doesn't care about those details knowing those details play a role in the execution of the algorithms so essentially that's where you typically use a strategy excuse me but the question is how did we Implement strategy now this is where it's important for us to really step back and not just look at the syntax of language but the evolution of the fundamentals and understand not just the shift in syntax but the shift in paradigms as well so let's think about

00:45:47
this for a minute Java if you will uh so back in time Java started out with an objectoriented ideology so this is and in general ideologies can be very problematic because ideologies can blind us into thinking there is one right way to do things and in in the beginning there were motivations to have that kind of uh ideology because when Java was being created the popularity was behind languages like C++ which was doing object program pramming not so elegantly so the the desire was to show clean

00:46:34
objectoriented programming but at the same time that can also backfire on us if we don't recognize that that's not necessarily the only way to do it so back in time we started with the O ideology back in Java I I believe it was 1.1 if I'm not mistaken we had anonymous uh so Anonymous uh in our classes so if you think about Anonymous in our classes I I often Define Anonymous inner class as a missed opportunity because imagine for a minute that back then rather than introducing Anonymous inner classes they

00:47:16
decided to introduce Lambda Expressions that would have set us nearly you know what 14 years or 15 years ahead of the curve but because because we were thinking about object programming Anonymous inner classes made a lot of sense at that time rather than Lambda Expressions making sense but how did we approach strategy back in time so you would create right so you would create an interface for a a a strategy that's a very first thing you would have done is you would have created an interface for

00:47:53
a strategy and then what do you do uh we create often times a bunch of uh you know classes are Anonymous inner classes uh you depending on what you're trying to do uh to implement if you will uh you know that uh interface so if you want to implement a strategy pattern you started creating an interface on a bunch of classes are We Done Yet oh no you probably need to create a factory to create those instances as well and by the time you start implementing strategy you got a few corporate meetings and

00:48:31
you're trying to create class interfaces and classes and you lose sight of why you started doing this along the way I would call that as a very heavyweight solution well thankfully we don't have to do that today but let's step back for a minute uh let's say what what is uh what is really uh strategy I want to really ask the question what is really a strategy let me put it this way it is definitely not a class so we were imagining this as a interface on a class only because of the ideology that we do

00:49:10
everything as classes sometimes when we are hung up on the syntax we can lose sight of the Paradigm and the paradigm shift and the Paradigm capabilities I seriously had a developer I was shocked when I heard this and the person said I I've been programming in Java for 20 years I never once had a need to pass a function to a function I was truly shocked at that statement because we've been passing functions to functions since Java 1.1 masquerading them into objects only because we were not allowed

00:49:49
to pass functions directly so passing functions to functions is something we've been doing for a very long time maybe without even realizing it so I would say fundamentally right so fun uh fundamentally I would say strategy a strategy is a function that's what it really is a strategy is simply put it's a function that is what it really is you are asking this function what strategy should I use and it tells you the strategy you should use so it doesn't have to be abstracted as a class

00:50:28
so typically a strategy is a function well then what what what can we do as it as it turns out naturally we could say uh strategies you could say uh you know uh can be implemented you could say uh in this particular case implemented uh using uh simply put lambdas so this is one of the things where we can benefit from lambdas as a natural way of using the strategy pattern so let's take a look at an example I'm going to keep this example really simple but we can understand where the strategy can be

00:51:06
effective in in using this so let's take a look at this real quick so let's say for a minute that you are working on a problem and you have a function called total values where the total values is going to take some values so let's say in this case we can call it as values equal to do let's say list of a bunch of numbers let's go ahead and say here that I want to use and I want to be able to take these values and I want to be able to Total those these could be whatever the values mean uh prices uh you know

00:51:42
size of something whatever that could be you want to be able to Total them so in this particular case what I want to do is to Simply say values and I want a total so let's imagine for a minute you're at work and this is Friday and you got a very interesting party to go to you've been talking about this for a long time your mind is entirely on the evening activity but you got to get some code done before you go so you quickly want to finish this and move on and one of your colleagues says I really need a

00:52:13
function that will total the values of these prices or whatever measures so you want to get this done quickly so what are you going to do right away you're like fine you want it really quickly I'll get this to you quickly so total B values list of let's say values in this case and what are we going to do let's go ahead and start with a very imperative solution result is equal to zero I want to return the result back to the caller at this particular point so what am I going to do within this excuse

00:52:43
me I'm going to say far let's go ahead and say a value coming from coming from values and we can say result plus equal to value and return the value back to the caller yeah that's done uh can I go home now well your colleague uh wants you to write one more method and you're not too thrilled about it because you really want to get going but they insist on having one more function you ask them what is it and they tell you I want to be able to Total even values and you're asking your colleague do you really need

00:53:19
both the functions want to Total all the values want to Total all the even values and they say adamantly yes I need both of those so what do you do you are you don't have patience for this you really want to go and so you are just looking at this there's a bug already there that that's how how that's a premonition right knowing what's going to happen I love it I love it that there's nature around us that's watching us all the time beautiful so so essentially in this case uh you're looking at this code and

00:53:53
you are thinking gosh I got to really go I don't want to write this this stupid code but I have to write this because my colleague wants it what do I do and then you get a immediate inspiration and you tell yourselves there's got to be a reason why they invented copy and pasting right I mean think about this for a minute if if you should not use copy paste why would that be even a feature in fact it is so cool that they not only is a feature they give you this so easily contrl C control V how cool

00:54:27
that is right so they even have a keyboard shortcut that's the most useful feature humans have ever created don't let anyone tell you copy pasting is bad they have have no clue how valuable that is right so you can just control C that and you got that in your clipboard and you do a control v on it how cool that is and now you have a total uh even values right so all you have to do here is to say if the value mark is equal to zero to know that it is even then you're going to perform the total not otherwise

00:55:04
so basically in this case we have uh copied and pasted that we made a small change to that code and you can see that it's working so you finish that code and and you're like here you go and as the Fate may have it before you say you're done your colleague says oh but wait I need one more function I need to Total odd value use as well now you know that copying and pasting code a third time is an act of criminal negligence but nobody is watching it's a Friday afternoon why not just committed you can always

00:55:41
refactor on Monday isn't it so excuse me you write the code you're on the way and and you're thinking about this and guilt kicks in and you know this is not the way you normally write code you are succumbing to the you know pressure of having to release this right away so pull up by the nearest coffee store you want to refactor this because when you go to the party you don't want to face friends who ask you how's it going at work and you're like oh uh and you're your mind is like just tell them you

00:56:15
copied and paste it you're like no I they wouldn't W want to hang around with me right so so guilt kicks in you want to really refactor the code well Let's Pretend This Never Happened let's roll back the code and let's do this the right way uh to to remove all that burden off our shoulders so here we are the code is existing and our colleague says I need a function to Total all the even values you say do you need both of those yep I do need both of those you say all right let's go ahead and do a

00:56:47
little bit of refactoring before we Implement that code so typically speaking this is one of the practices I follow often and that that is uh in generally speaking I say refactor before you add a feature or after you add uh uh uh you uh you add I would say uh my my fingers are not warmed up yet uh you add a feature so essentially the idea here is you refactor before you add a feature or after you add a feature but I would say but never in the middle of it so in other words I typically want to see

00:57:31
refactoring as a separate comet in my source code this is just a discipline but of course as you know 90 you know I this is something I I believe in uh programming is 10% skill and 90% discipline that's what it really is most of the time I feel right so it's all about how disciplined we are in what we do so so when I write code this is my discipline that needs to kick in and and check check about what I'm doing so I'm going to be writing this feature oh but if I really refactor this it'll make my

00:58:09
life a lot easier to write that code so I'm going to refactor this now and I'm going to make sure all the tests are passing and commit the code so once I commit the code I can then continue to write more code and this is another principle I follow a lot so uh com at uh often and commit uh you know in small uh I would say portions so why do I want to do this because I follow one very important rule uh and that is uh merge merge hell is what you give and should never receive so this is one rule I

00:58:49
follow right so I love giving merge heal to other people I don't want to ever receive merge heal so the best is to just keep going commit frequently commit in small portion let them have the merge hell not you so so the people who don't commit for a long time you they'll come to work and they'll scream because there's a lot of merge hell going on and you're like yes right so so they they got what they deserved no just kidding but only partly so the point really is that essentially in this case that you

00:59:20
want to really keep those comits uh you know as often that's a discipline I try to follow quite often is to refactor commit to the repository and then continue to write more code along the way so essentially in this case we're going to do a bit of a refactoring before we add the feature so going back to this code uh oh so this is one of the things I want to also emphasize about committing frequently so a frequent uh commits uh you know make the cost of undo uh near zero so this is the reason I commit frequently so I want

00:59:58
to commit frequently because the cost of undo becomes near zero uh when your cost of undo is zero you're able to innovate much better if youve not committed the code for a while and you come up with this great idea oh look at this I could change the Design This Way like oh hold on you don't want to lose the code you have let's be very careful moving forward so committing frequently makes you go faster committing less frequently slow you down in general these are just lessons we learn but sometimes when you

01:00:31
externalize those not thoughts in our mind it turns into discipline and and practices with discipline as well so so anyway coming back to the program so what I want to do here is to say given an element I want to turn true so we just added additional parameter to the function argument to the function which says given an element simply return true so I'm going to go back to this code and say I want to bring in a DOT function and in this case I'm going to bring in a predicate so I'm going to say a

01:01:07
predicate right there which takes an integer and I'm going to say selector you can call it as an INT predicate if you want to there's nothing wrong with it so then I come in here and say if the selector test of the value were to be true then go ahead and uh add it otherwise simply uh ignore that particular value so when I run the code the result is the same but we just refactored it in this particular case of course this is when I'll would be committing this change before I continue

01:01:40
to move forward after this refactoring so now that we modified that now we can simply say hey given this value I want to Simply say e 2 is equal to zero and we can simply total all the uh even values as well or alternatively you could also have in this case a Boolean is even as a function which takes a number if you will I can say number uh in this particular case and and uh number mod 2 is equal to zero and instead of this I could have nicely written this as uh a sample is even like so and I could have used a method

01:02:25
reference on it as well rather than having to use a Lambda so we could then what what's happening in this case I would argue that these functions literally are our strategies if you will so you can have a strategy which is e even you could also have a strategy in here which you could call it as e odd which takes a number again and it's simply going to return number mod 2 is not equal to zero as the case would be in this particular case and and you can provide that function here as well is an

01:03:04
e odd function so as a result you could simply replace this with an E odd and as a result you can be uh picking odd numbers to Total rather than even numbers and so that those become a strategy now obviously you can refactor the original code which was using imperative style into functional if you really wanted to that's perfectly fine so you could go back here and say well maybe I want to really say that I have these values so we could do a return values. stream. filter and I'm going to Simply

01:03:40
send the selector to it and then of course I want to uh perform a map operation maybe map to int of the element to element or you can use other uh functions in here and then eventually you can call a sum function on it a method on it and return the sum of the values if you will so you could refactor that as well if if the case may be and you can massage this a little bit to make it more smoother but you get the point of how you can provide the functional interface to do so so so I would argue that these become the

01:04:13
strategies in this case now clearly in this particular example we are passing a function in this case uh rather than having to worry about implementing Anonymous inner classes are implementing interfaces now you may say oh gosh but lambdas are really using functional interfaces in the back well that is more of the Java ISM in terms of how Java took the pain to provide backward compatibility but that's not because lambdas require it in general so that is more of a consequence of uh Java's route

01:04:49
it took but essentially what you're doing here is passing functions after all and and as you can see evidently here it is a function that you're passing using these method references as well so that becomes your strategy that you can provide so in a sense you can say that strategy pattern simply transforms to effectively using lambdas and and you can you can think of lambdas as strategies as well that can be very powerful so so we saw a little bit of a transition of a pattern from being more

01:05:22
heavyweight to something a lot more easier to work with as well so that is basically the natural transition here into more engaging lambdas for what we do uh rather than having to create classes but having said that uh certain methods can be useful to provide a certain capabilities now remember Java has default methods well we can use default methods uh as as a pattern uh to implement the factory method pattern but let's talk about Factory method pattern real quick before we dive into how we can use default

01:06:00
methods for it so so typically what do we do with a factory method so the word Factory and the word method so I want you to take those words uh two words separately uh in here for a minute so often times we get confused between abstract Factory and Factory method what's the difference so the factory method really the word fact simply you know tells us that uh an abstraction you could say uh abstraction uh to create an object that's what it really is Right a factory is an abstraction to create an object but what

01:06:41
about the word method what does that mean uh this literally means uh inheritance uh you know hierarchy if you will uh you know where uh we can override uh you know override a method uh to uh provide an alternative implementation uh that we return so this is why this uh Factory method typically is one that uses inheritance in what you do so if you're using Factory method typically you have in this case typically we have a base class generally an abstract Base Class and uh derived uh you know classes you could say derived

01:07:28
classes not just one multiple uh that override the uh you could say Factory method the method that instantiates object so typically the way I normally say is this if I'm using abstract Factory I look to my left or to my right because the factory is on the side that I'm depending on that's that's how an abstract Factory Works a factory method is where I look down on the hierarchy as a base class I look at my derived classes to provide the alternate implementation so you can think about it

01:08:05
as horizontal dependency in abstract Factory vertical dependency in terms of factory method that's a easy way to remember the difference between abstract Factory and Factory method now typically when you're using a factory method you are using an abstract Base Class and you are deriving from the abstract Base Class to provide implementations there's nothing wrong with it but in general I would say this in general interfaces are better than abstract base classes so again if you have no other important

01:08:42
reason to select an abstract based class I would typically program with an interface but the question then is if I have an interface I've got a bunch of Declarations of methods but what if I need really a factory method how can I benefit from that well this is where I would argue that interfaces with default methods are really useful to implement the factory method so let's take a look at how that could be the case I'm going to start with an interface and I'll call this interface as player interface and

01:09:19
the player interface says I have a default method called play and and the play Method is going to say uh I would put let's say I like uh playing so and then it has an implementation uh it simply says playing with and it's going to provide uh play with something so what is it going to play with well this is an interface but I want to capture some details with the interface but allow for me to vary the implementation how can I possibly do that so I'm going to say get pet right there so the player

01:09:58
is going to play with a pet so I say pet get pet is the method the abstract method that I'm going to provide in there so it's an interface we created and the interface contains the default method and the default method now relies on the factory methods so though this becomes your factory method on an interface so interfaces can provide implementation of the default methods but they don't carry a state but they allow a derived class to implement that state for you so that is the benefit you

01:10:36
see in this particular case excuse me so uh how do we provide this so I have an uh interface here called pet but I can then provide a class called Dog which implements the pet interface and I could also provide a cat in this case which implements a pet interface as well as you can see right there so now what I can do here is the following I can say a class let's say dog lover uh is implements in this case implements let's say a player so a dog lover implements a player interface and and because the

01:11:17
player interface contains a get pet method we need to provide that in here so public let's say uh pet and we can say get pet and this simply is going to let's say uh return a new dog and of course you can color that with more details uh with um names and such but let's not worry about it so in this case I'm going to say we have a a method we'll call it as play uh uh or call play and what does this do takes a player as an argument and all it's going to do is to Simply say player. playay and in

01:11:55
invoke the play method that we have up here so the default method be is being exercised but how do we use this one I can do a call playay new dog lover and in this case the dog lover is being sent over to the function I like playing playing with dog and you can see that it's able to call that particular method now you can abstract this out to your own applications where you can write a common method up here in the top which can do the work it wants to do but the variation of the objects that you want

01:12:28
to use can get abstracted in the get pet method and the get pet method can or or whatever function you're writing which can return the instances pardon me that you want to be able to use and that becomes a lot easier to uh abstract it out similarly you can have multiple methods you can have a method that is going to return something else and typically that's what you do in a factory method you have a base class which Prov proves a common operation and allows the derived classes to vary the parts that come together in

01:13:02
that particular operation in this case pet is one of them but you could have other things as well so you can think about an abstract Factory pardon me a factory method and then see how you can use uh default methods to achieve that now this also takes us to one other thing I want to quickly show you before the break which is is the uh Lambda Expressions serving as a lazy evaluation now if you want to execute a certain uh job uh it might take a certain amount of computation but how do we really deer the execution so

01:13:42
let's take a look at an example of this just to entertain the thought so I have a compute method and I want you to think about this compute as a very time consuming let's say a slow let's say operation so if it is a slow operation it's going to take some time and I'm going to return let's say uh times 100 right so nothing exciting but imagine this is a slow operation that you want to use so now if I want to provide a function like this so I say public let's say static let's say in this case uh

01:14:18
let's say we have an operate and this takes a value and it's going to do the following something like this let's say if math. random let's say is greater than 0.5 it's going to let's say use the value and it's going to use the value given otherwise let's say for example in this case if it it doesn't have a reason for it uh you know continue without using the value so imagine you're writing a piece of code that receives a certain data but it may or may not use

01:14:54
the data so if this were the case how would we call this operate function so you would do something like operate and you would say compute and you have a certain value you want to use and you can pass that value because that value comes out of some computation let's say so when you run the code what's going to happen in this particular uh case so in this particular case obviously uh when you call this particular function what is it going to do do well it's going to call the compute slow operation and

01:15:29
continue without using the value notice that was a total waste isn't it so when I run the code again you can see slow operation can without using the value and occasionally it uses the value but it may not use the value also which is a bummer isn't it so the question is how do you prevent wasteful computations well as it turns out uh computer is an amazing field but we find weird names for things that really doesn't make sense when we think about it so in programming we have two ways to pass

01:16:09
data to functions now you may think aha I know what you're talking about call by value and call by reference no that's not what I'm talking about so there are two ways to pass data to functions one is called what is called an applicative order and the other is called normal order what is really uh rare about this is uh applicative order is what most of us are used to everyone in this room every single one of us has used applicative order so what does that mean uh the order of evaluation is the order

01:16:49
of call or application so it's like you do what you see right what you see is what you get that's basically what applicative order is uh normal order uh normal order is rather very abnormal uh or unusual uh that's why they called it normal order I think because very few of us right so normal order is rather abnormal or very uh rare so it's not something we often see languages like hasal do this uh extensively so if you're writing code in language like hascal you will not really use the value

01:17:34
pardon me compute the value until you actually use it for just a minute Pretend This is hascal code well guess what's going to happen if this were hascal If This Were hascal code what's going to happen in this particular case is that hascal will when you call the function operate uh what ascal will do is it will not call compute I know that sounds a little unintuitive right hascal will not call compute so you tell hascal hey I want you to call compute and hascal is extremely lazy and H will look

01:18:14
at you and say you want to call compute why like why do you want to do that that's such a boring thing to do and you say well I want to call compute because I want to send the result to operate well then call operate why do you worry about computer right now okay I want to call operate and then it says but why do you want to call operate and then you say because I want to print the result okay now you have a good reason you want a user to see it look at a value based approach right I want the user to see

01:18:44
the result okay now let's get some work done so you literally will call operate but what you pass to hascal is not the result of compute but uh little pending request to call compute and when you get into the operate function you do some work and hascal bypasses the user value and it says Tada I never had to call compute I saved all the effort that is normal order a normal order is where a value is postponed until uh it is really needed or never called if you're super curious about seeing normal order uh

01:19:28
I'll be more than happy to show you either during the break or during lunch uh if you're like I really want to see haskal and look at normal order I'll be more than happy to show that to you but I won't spend the time during the session for that but you're most welcome to uh ask if you're interested in it now the question is we know why we want to do this you want to save on operations until you no longer can save it I'm not going to show you the solution right now I'll show you after after the break but

01:19:56
I want to talk about something before we go into it these are the things I get super excited about call me silly but this is what gets me excited in the morning and if you want to ever ever give an award in computer science I think that award should go to one person hands down unfortunately he passed away but he's a British computer scientist is his name is David wheeler and David wheeler in my opinion said the most most profound thing that every one of us live every day we write code so what did

01:20:33
David wheeler say he said in computer science he said uh we can solve almost any problem I think the word almost is he's being humble in my opinion uh almost any problem by using one more level of interaction so to me this is the most phenomenal uh observation that any human has made in my opinion because we live on indirection in in every code we write we have been using indirection how did we use indirection in C we use pointers as indirection and pointers gave us IND Direction isn't it rather than calling a

01:21:17
function you go through a pointer to call the function you can vary the pointer in objected programming polymorphism gave us IND Direction you can just see how deep his thinking was right he gave us the interaction as an abstraction now we using interaction in pointers in procedural programming we use polymorphism which is an interaction and that may make you wonder if procedural programming has pointers as interaction if object programming has pointers as polymorphism as interaction what about functional programming

01:21:48
shouldn't that have in IND direction as well I would say lambdas are uh indirect uh in functional programming as polymorphism I would say polymorphism um in let's say o op and pointers in uh procedural uh example for example in C and stuff like that so so we see this level of interaction all the time so my question is how can we leverage lambdas with level of interaction to provide normal order of execution so we're going to turn uh this code which is using this is using applicative uh order and I want to show

01:22:36
how uh we can use normal order instead of applicative order but we have to follow the order of having break before we do that so we'll take a 20 minutes break and then when we come back we are going to continue with this and explore a few more ideas so enjoy break see you in about 20 minutes thank you thank [Applause] you all right let's continue so I want to talk about this lazy evaluation that uh I alluded to so we have the applicative order where that's being called before we make the call as you

01:23:17
can see slow operation executes irrespective of whether the value is being used or not so the question is how could we really make this uh better in terms of postponing the operation so I'm going to take that function and show uh a different way of doing it so here is the operate we'll say uh normal let's say order so the operate normal order is going to postpone evaluation until the value is actually needed so that's going to be the uh normal order obviously just by changing the name it's not going to

01:23:55
do the work but let's uh start moving towards that direction so what are we going to do uh first of all I talked about the level of indirection and and look at the beautiful level of indirection you can introduce so there you go the arrow gives you the level of indirection there you are using the code uh value right away versus you are using the value with the level of indirection at this particular point so I want to really take this as a as a normal order so how do we do the normal order you

01:24:30
basically push it and say don't do this right now right so you're saying do that deer the evaluation of that so that's basically the normal order and you can really postpone that so in order to do this this is simply A supplier that's what you are working with so I'm going to bring in a function uh in this case the supplier so we'll go to the operator normal order and we will change this to A supplier of integer and we will say uh supplier right so the supplier is going to give us the Deferred evaluation of

01:25:04
that so what are we going to do then well we say if the value is greater than that we can then say use the value supplier. getet and that's when we're going to be getting the value at this point so if we go back and execute this code you can see slow operation the value is 2,000 but you run the code again continue without using the value we never incurred the uh expense of running it so so it's not really that much effort to do it but it really gives us the concept of defer evaluation so

01:25:37
essentially uh here's a way to think about it uh think about this like an like an apple not not the computer but the fruit well I can give you an apple and you got a couple of things you can do maybe you're really hungry the minute I give you an apple you eat it well or you can store the Apple away and you can need it at the later time or keep it around and you're like I don't really care for the Apple you can throw it away or you can pass it to somebody else and say here you go I got this

01:26:05
apple maybe you can you know eat it so when you pass a Lambda to a function think of it like an apple you pass around you can run it now you can run it later you can save it you can pass it around and so on so so that gives you the lazy evaluation ability so if you don't want to execute something right away you can simulate normal order by bringing this to execution at a later time rather than running it right away so so that basically is an example of the lazy evaluation uh so that takes us

01:26:36
to uh talking about a couple of more things I want to spend the time on this time I'm going to talk about The Decorator pattern then we'll talk a little bit about creating a fluent interface which will naturally blend into a execute around method pattern then I'll talk a little bit about sealed classes and then I'll spend a few minutes talking about oop versus data oriented programming and how we can benefit from that as well so we got a bunch of stuff to talk about uh as we go through the next part in this

01:27:04
presentation so let's go over to the next one which is The Decorator pattern right here now uh almost all of us maybe have been introduced to a decorator pattern in the past I want you to remind I want to remind you of one example remember that example from way back in time when we started learning decorator pattern you had something like for example buffer reader uh and uh you know buffer reader for example uh is equal to maybe a new buffer reader new uh maybe in this case uh new data reader and then

01:27:45
maybe new file reader or file input stream whatever that it was right so maybe uh buffer input stream uh yeah that's what it was I think right so buffer let's say uh input stream and you could say is equal to new and that's going to be the buffered input stream a data input stream if I remember correctly and then you're going to have a file uh in this case a file input stream uh that would sit here so uh file input stream and then you would specify whatever details you want to specify uh

01:28:17
remember that uh how many of us got super excited to use decorator pattern when we saw that example uh in fact you saw that as a decorated pattern and you looked up the dictionary what does the word decoration mean is it take something and make it scary and ugly right so that is not something we get really excited about but as it turns out a decorator pattern is very powerful but it doesn't have to be really scary like that it can be really effective so I'm going to give you an example of using a

01:28:53
decorator but just using lambdas so to understand this I'm going to give you an example of a decorator and I apologize to you before I give this example my mind is corrupt so almost there's a background thread all the time that is thinking about programming doesn't matter where I am I I love the outdoors as you can see the pictures on my screen I love the outdoors but just because I'm Outdoors doesn't mean I'm not thinking about programming especially some would have a very fancy camera and when I look

01:29:27
at the camera and and this is going to happen to you also so I apologize when I look at a camera all I can think of is decorator pattern because they have this camera and then they have all these lenses and they would take this fancy camera they'll remove their lenses and then they'll put more filters into it so they can take a shot of something in a distance or a Different Light condition and every time I look at a camera I'm like wow what a beautiful example of a decorator pattern right and and if that

01:29:55
person is a programmer then we both are doomed for the rest of the afternoon now we talk about coding outdoor rather than enjoying the nature so so the point really here is we can think of a camera as a nice decorator pattern so here is a class called camera and the class camera has a function let's go ahead and say uh import let's say java. awt docolor we'll bring that in pardon me and here I'm going to say a color snap takes uh uh color as an input and let's take start with really small

01:30:30
baby steps it's going to Simply return the color given to it nothing exciting so I'm going to call a process function new camera and just call it let's start with that so in this case I'm going to say that I want to start with let's say a public let's go ahead and say this is going to be a static method uh process which takes a a camera if you will and let's say camera and what it's going to do here is to Simply uh you know output uh camera. snap let's go ahead and say

01:31:02
in this case uh we'll pass to it let's say a new color let's say 125 125 and 125 a good little sample to play with to begin with so so in this example as you can see we are passing the color to the snap method and it just gave us 125 so nothing really exciting great so now I want to get go a little further with this and I want to really make the color brighter so how do I make the color brighter well here's an idea I'm going to say given a color I want to say color dot brighter so notice I'm

01:31:42
passing a Lambda to enhance the color alternatively I can also say right here a color I can simply send a method reference to brighter uh b r i g h t r so I can send the a method reference to it if you will rather than having to call a Lambda expression so the question is how do we really pass that color enhancer as a as a filter right again when I use the word filter right now I'm not talking about filter function and map function and function programming it's a filter as in a camera filter if you will so to make

01:32:22
that work I'm going to say over here a private let's say a function takes a color and returns a color we'll call it as a filter if you will so then I say a camera and the camera takes a function color comma color but I'm going to take the filters as an argument because bar AR allows me to send nothing or a variable number of arguments this works really nicely so in this case I'm going to say the filter is equal to to given a input return that as a output so this is like a function that's going to take an

01:33:01
input and just pass it on doesn't do anything interesting with it then I say far we'll say a filter coming from filters if you will then I can say the filter is equal to now this is one of the cool things about functional programming so let's take a quick detour and we'll come back to this so so functions uh you know are uh composable so this is one of the nice interesting things we can do with functional programming as well so for example you know if I were to you know ask you the

01:33:38
question you know what is this and you can say oh that's a pen sure we are really good at it as humans we abstract things out and you say that's a pen you're absolutely correct that's a pen but I can take this apart if you will and now I'm going to say hey look at this I I got ink on my uh hand so in this case I can pull this out if you will I'm not going to go that far and I can say hey look I can pull this out and what you're going to see here is a few different things right there's a casing

01:34:12
that you see here but inside this there's a spring the metal not the framework and then you have the tube which has an ink and this one has a little thing that you can press on and I can tear that apart and if I were to be able to manage this I would have turned this upon into 10 different things and I could put on the table and now I ask you what this is and you say well you got a metal you got a casing you got a you know uh a a rubber material there you got a metal here but what are those

01:34:41
things I can put them back together and then you got a pen well we know what this concept is called This is called object decomposition so in other words objects are made using other object objects well great but if objects can be made using other objects why can't we make functions using other functions so here I have a function and here is another function but I want to compose those two functions so I can connect the functions together and I can create a abstraction of a full function so I can

01:35:13
send a data here which can flow through this function the output of a function becomes an input of the next function and that can flow through so in other words we can compose functions just like we compose objects as well and that can be a very powerful way to do things so what I'm going to do here is to Simply say hey given this function I want to compose it so filter is equal to filter and then a filter so in this case I compose the functions together to form this bigger function if you will so this

01:35:49
is forming a chain of the function so in a sense what we achieved is the following I got a function F1 I got a function FS2 which function F1 is taking an input and providing an output similarly function two is taking an input and providing an output now I created a function F3 but the way the function F3 works is that it takes an input and then it passes the input to function F1 takes the output of FS1 passes to FS2 and then takes the output of that and returns to me so essentially that becomes our function F3 in this

01:36:30
particular case which is a composite of function F1 and function F2 so we can create a composite function where the composite function simply is taking the input calling F1 taking the output and calling the input on FS2 and taking the output and returning to us so that's basically what we did here is to perform a composite so we said filter is equal a filter and then a filter so now that we combine those into this filter what's the next thing we want to do so we say a filter dot apply and we're going to

01:37:05
apply that on the input and we're going to return the result from that particular call so that's basically what we are doing here is to bring that together so let's bring in the function right there for us to use and when you when you run this code what are we expecting it to do so filter right there so this is going to be ail filter pardon me so this is going to be a filter and this becomes a filter we are using as well in this case so uh let's see cannot find the symbol filter i f f i l t e r

01:37:36
too bad it requires me to type properly okay let's try this one more time so you can see how the color is enhanced right now in this filtering but not only that you can go back to this code and you can now say rather than brighter you can say darker as well and you can see that the color is a bit darker than it was to begin with but you know where I'm going with this because we combine this as a filter what you can do is you can potentially call this one right here by combining those two as well so you can

01:38:12
say for example here is a brighter comma color and you can then say darker as well so this becomes your decorators that you can add together so when you run the code right now you can see how the color was made brighter and darker at the same time so what's going on in this particular case so we can combine a series of policies uh you know you could say po policies uh you know filters uh you could say you know you could could call this one as uh you know cleansers you could have basically this is like

01:38:50
spring filters if you will so uh data you can say say uh cleansers uh you could also say various other things you can combine together so for example you could have a function that is doing handle uh data but you can say in this case uh uh a mask function you can say in this case uh function to uh you know un uncompress right so uncompress you could say unmask and uncompress and you could then say some other operations and then event you can say in this case for example a mask uh you know sensitive uh

01:39:29
data for example and then you can say compress so you can provide a lot of these filters in in your how you want to handle the data and those can become series of uh you know handlers that you can provide so what happened in this case essentially is that your are decorators are just functions that you are passing around and you can combine these things in your code so if you have a function application where you need to receive a data and maybe you can validate it and then you want to transform some data maybe you want to

01:40:03
replace some data with an alternate data maybe you want to enhance a certain piece of data you can just start throwing in these as decorators in your code and that becomes a lot easier you can leave this code at this point if you want to or you can say oo I really want to make this functional style well if you want to make this functional Style style sure you can let's see how we can do this so I'm going to take this and say I've got a collection of filters that are coming in here and I

01:40:34
want to take those filters and work with them so stream of filters gives me a stream of all those filters that are given to us and then I want to say bring them down to one single filter that I want to use so the filter is equal to and you're going to take a collection of filters and bring them to one filter here's a quick question I got a collection of data but I want to get one data out of it by somehow combining them what function would we use for that beautiful right reduce you nailed

01:41:10
it so what does the reduce function do notice the initial data you pass in given an input you want to return an input so that's your first data that you're starting with then what are we doing we performing and then operation so you can say result comma a filter and you can simply say result and then a filter and you're going to bring them together to work like so and and so in this case you're going to bring the stream so let's bring in the Stream in here as well so essentially in this case

01:41:45
we are transforming the data using the functional style rather than the imperative style you can leave it at this if you want to but something tells me we normally won't right because we want to refactor it even to more concise code so let's see how we can do that so I can go back to this code and say aha but what is this input Arrow input well let's go back to math School you probably remember uh we saw this right so you have operations like 0 + x giving you X and operations like 1 * X

01:42:20
returning X so for those operations like plus n and star those values 0er and one uh are normally called as what function the Bingo you nailed it those are called the identity function now you know why that's called function. identity so identity function simply is exactly that it takes the input and gives you that as an output just like 0 plus X is X and 1 * X is X those are identity functions so function. identity gives you that as you can see but of course you can also use a method reference as well so you can say

01:42:59
function and then which is what you want to use and you can remove that other part from here so that becomes a nice concise code you can write as well so that becomes a nice way to refactor that into a functional style so basically you take a stream of filters and you reduce on it and you got that one pipeline that you can use in your code very nicely so so that becomes a nice way to combine operations into that functional decorator that we can make use of so so that becomes your decorator pattern

01:43:32
which is pretty nice well the next two patterns I'm going to show you are fairly related to each other uh one is kind of an extension to another but I want to really show you how we can benefit from uh writing fluent code with um lambdas and how that can help us to write a very specialized code as well well to deal with certain uh uh scenarios of resource management but let's get get to that in a few minutes but let's take an example and play with it so when we're designing an API we not

01:44:06
only want to make the code fluent we also want to make sure that it's less error prone so let's think about a code I'm going to write here I call it a mailer if you will and the mailer is going to have a bunch of functions so I'm going to say a v vo and let's go ahead and call this one as from which takes a address so let's take this and return from here we'll say a from was called right so this is just purely to play with the API so I don't want to get into too much detail within the

01:44:42
functions so similarly we have a two function and that's going to tell us two then I have a function called subject and the subject function takes a line and it's just going to return a subject right so it's going to print subject then I'm going to have a function called body and we'll say message and it's going to Simply say that it's a body right there so nothing really exciting and finally I'm going to say send and the send function going to Simply say let's say sending so let's say we device

01:45:18
an API like this one so the mailer has a from a to a subject body and a send function so I want to be able to use this function so how do I use this so I'm going to say mailer is equal to new mailer and then I'm going to say mailer do from well who is this from uh I I wonder so this is going to be from let's go ahead and say this is going to be from my Builder if you will so my Builder is going to send me an email well who is this email being sent to let's say it's sending it to me right

01:45:54
there okay so far so good and then I have a subject so the subject uh unfortunately is what I get a lot of times from my automated Builder it said you uh your uh code sucks okay so thank you for that and I'm going to take the body and this the beauty of my code is it doesn't suck the way the same twice so every day it's different so it's going to send me the details of how it sucks today so so it tells me the code smell details and then I'm going to send a mailer do send and be able to send

01:46:34
that as well in this particular call so given this of course we have the code which is going to do the operation but when you look at this code there are a few problems the first problem is it is very noisy right so you look at this it's like whoa look at that mailer mailer mailer mailer that's really noisy I'm not too thrilled about the noise level in that code that's one problem the second problem in this code is that if you think about this code you're asking the question uh should I right so

01:47:08
should I uh reuse the mailer uh instance uh in instance uh or not well there's no Clarity to it if I should reuse it and if I don't I'm wasting resources if I shouldn't reuse it and I do I've got an error but the question is should that be my concern right and I would argue that should be facilitated by the creator of the abstraction rather than me trying to do that so the question really here is how do we approach that and and be able to make that more smooth so what I'm

01:47:48
going to do for that is to use a pattern which is going to allow us to to streamline this operation and we're going to put put those two concerns to rest so how do we accomplish that the first thing I'm going to do here is to make the Constructor of mailer private so if you make the Constructor of mailer private what's going to happen you cannot create an object of the mailer so notice line number 14 is failing and that's line 14 where we were trying to create an instance of the m so that

01:48:24
doesn't work so now you ask the question what gives how do I use it so I'm going to say this is a static method now so public static void send but the send method now takes a consumer of mailer and we'll call it as a block so what am I going to do with the mailer at this particular point so you can see how this send is taking a consumer a block which is going to take a mailer as an argument so we will bring in the function. consumer right there and in this function we're going to say mailer is

01:49:01
equal to new mailer uh create a new one or reuse reuse from a pool uh you know that is the job of the Creator right creator of the abstraction so as a user of the abstraction I don't need to worry about about it right there on that line I can create a pool and any mailer that's not being used I can pull it from the pool and I can use it if I wanted to right I can do that then I can say a block. accept and I can pass the mailer to it and so the mailer can do the initialization work it wants to do and

01:49:43
then I'm going to say sending and finish the operation in the end so how is this going to help so we can go back to this and say mailer do send so notice now we have a send method we are calling right there but then I'm going to come down all the way to the end and finish up that particular call right there as a parameter I'm going to say mailer arrow and and then I'm going to make one small change before we move forward I'm going to go back to this and say this becomes a from and it returns a

01:50:21
mailer and that returns a mailer and so do do those two things and then I'm going to say return this so I'm going to say return this as a as a response at the very end of those functions so as a result I can go back to this code and say mailer and I'm going to Simply call that on the mailer right there but guess what I can simply start chaining them so I can remove all those semicolons and I can simply remove those calls as well and start really combining them into one particular sequence of calls so

01:51:03
send says once you give me a once I give you a mailer you can call the from to subject and Body on the mailer so as a result when I run the code the output is exactly as it was before however there are two things we did well first of all the code is less noisy and secondly if mailer right if uh um mailer instance should be uh you can ask the question right reused or not is not my concern so I don't have to worry about it the the creator of the abstraction can take care of that if they feel like it should be

01:51:44
reused let them reuse it if they want to create a new instance let them create a new instance I don't have to worry about it so this gives us a fluent interface so we did couple of things we returned the current instance from these calls and then we also received a consumer from this and as a result the consumer is able to chain these operations together and then as a result we don't have that repetitive calls to that and we able to nicely chain this so this looks almost like a builder pattern that

01:52:17
you can use but this is more General than builer in terms of the fluency that it provides to us as well so with that said we're going to take on this idea but we're going to work on something very similar to this in in the next one so in the next one I'm going to use a pattern called the execute around method pattern this is one of the interesting things that we can learn and we can discover so other languages have had this capability for a while because other languages have had lambdas for a

01:52:52
while also and I came across this pattern in a very old book by Kent Beck called uh uh I think it's called The Small Talk best practices that's a book I think I I read a long time ago and when I was reading the small talk best practices I came across this pattern that truly inspired me to think about how to make use that of that in languages like Java so let's take a look at the problem first and then talk about the solution so this is a problem I ran into for a client so they called me up

01:53:27
and they said they got an application in production and the application fails and they don't know why the application is failing now imagine you are being called as a consultant they tell you come fix a problem and we don't know why the problem exist but you got to help us to fix it so I went and asked the boss and I said hey can you tell me about this problem you're having and the boss uh gave a answer you would expect from bosses and he said go talk to my programmers for the details I just want

01:53:59
you to help us to fix the problem like all right so I went to the programmers what puzzled me was no programmer would give me a straight answer I asked them hey I was told there's a problem in production can you tell me what what the problem is and most of them said I don't know what the problem is we have some problem I cannot tell you what it is so I'm like looking for somebody to help me and finally I found Joe Joe nailed the problem and Joe said the code works fine most of the time I said yes that's

01:54:33
awesome and then I said can we distill down to when it doesn't work he said oh that's easy it doesn't work between 7:15 and 9 in the morning and I said wow that clear 7:15 to 9: in the morning he said yep and I said every day he said no only Monday through Friday it works fine on Saturday and Sunday I'm like wow why would a code work on the weekends but not work on the weekdays and only between 7:15 and 9:00 so I said that's very helpful thank you and I will get back to you with more questions and I

01:55:10
started doing code reviews for their project one of the things they hired me for was also to help them with the code quality and I stumbled upon a piece of code and literally this is an example of what the code was doing so there was a class uh let's call that class as resource if you will for La a better wordss and in the resource they had a Constructor and in the Constructor let's go ahead and say open DB connection so that's what they were actually doing in the Constructor they were opening a

01:55:45
connection to a database then they had of course you know operations right so you have an operation we'll call it resource op one and this one was simply doing some operation we'll call it op one and it was returning in this case the current object so similarly they also had another function we'll call it operation two which was doing some work now you know where this is going and then I stumbled upon the next line of code that shocked me they had a function public void finalized and in the

01:56:20
finalized they had a beautiful call that did the following close DB connection and I looked at this code and I'm like wow really the problem is that in this particular case they were so so what was the reason why this could be creating a problem let's call a function new if use if you will where the use function let's go ahead and call this one as use and the use function does the following resource re resource is equal to new resource resource dot let's say op one and I call resource dot op two so I call

01:57:02
the two operations like so now here's the problem their application had a lot of memory most of the time and when you create objects after objects after object well if you have a lot of memory the garbage collector doesn't have a reason to run and with the garbage doesn't run what's going to happen the finalized method is never executed when the finalized is not executed the closing the database doesn't happen and as a result they were getting connection errors in the database that went

01:57:38
unnoticed now why did it have problems only between 7:15 and 9 in the morning uh that's another sad news uh this code was running within a portal and it doesn't matter whether anybody cares about it or not when they come to work between 7:15 and 9:00 in the morning they're logging into the portal and it was executing this particular code which means that during the day not many people were using the application but in the morning it was being executed when people log in so that was the reason for

01:58:08
it so when you run this code right now notice that it says open DB connection but it never released it so that's a problem what should we do to fix it well the first thing is to know that the finalize should have another name it is called a bad idea so this should not have been done in the first place why is finalized a bad idea this again I want to emphasize goes back to some very fundamental design ideas this is what I love about our field you can look at a problem you can walk away or you can

01:58:46
stare at a problem and you get a deeper understanding and a deeper realization so this is my understanding realization looking at this so one of the things that we should never do right a a common design uh I could say design flaw or anti- pattern uh you can call it as well Rich hicki the gentleman who created closure deviced an uh created a new name for this he calls it completing and that's not an English word but it's a completing is a new word he created I like to call it as uh intertwining so essentially you are

01:59:29
creating two separate things that shouldn't be combined together but you're putting them together this is a very common design flaw we all always do we bring two things that shouldn't belong together together because it was convenient now if you really think about it this finalize is an example of complecti or intertwining why is this a bad idea well remember you have this idea of garbage collection which takes care of memory at runtime but we're not dealing with memory right now finalize

02:00:07
is about external resources combining memory management with external resources is intertwining and anytime you interwine it's a question of when it's going to backfire on you so this is why they very cleverly finalize has been deprecated starting Java 9 so starting Java 9 if you use finalize you will get a warning you say dude why didn't you get a warning uh I just didn't show you the warning that's that's why but if I were to really run this in code it would show the warning I just kind of hid the

02:00:45
warning from you right now so so finalize is a bad idea we shouldn't use it so what's what's the solution what should we do now an answer to that question is a feature they introduced in Java 9 okay Java 9 deprecated right deprecated finalized but before this preparing for this in Java 7 uh they added a feature called armm which stands for automatic Resource Management so automatic Resource Management some of us may know this as also known as a try with resources so I'm not a fan of try

02:01:27
with resources I'll tell you why with this example so automatic Resource Management are try with resources so what can we do to avoid this problem you could have said implements autocloseable and you can then simply change this to a close method so now autocloseable and you have a close method given to you right here and what you can do then is to Simply say try like so and in this particular example you are bundling that into the operation right there and you can say resource op resource op to and you can put the Block

02:02:11
in here to say you are done with that particular resource the beauty of this is close is called Auto automatically for you here so even if there was an exception it will still call that method that's one of the benefits you have about this particular approach so if you run the code right now uh what you notice is the closed DB connection happens automatically so you say okay that works right so why are you complaining about it you could ask well the first thing I want to emphasize here is well this is great so far but what if

02:02:48
you don't call the try so what if you forgot for got seriously forgot to call the try so the question then is if you don't put a try will the compiler give you an error what do you think no it doesn't will the compiler giving you a warning no so is there a guarantee that you handle it not really but you know this already right because from the beginning there was a clue that was there maybe you missed it and that is never trust a feature with the word Management in its name so so we know

02:03:30
this already right so so the point really is that this is not going to give you a safeguard it just tells you if you follow these rules you will be able to benefit from that this is why I'm not a fan of try with resources I want to remove this burden from the hands of a programmer I want the programmer to sit back and say I don't need to worry about it because you're going to take care of it so how do we really deal with it so let's go back here and get rid of the autoc closable thank you no thanks what

02:04:05
I'm going to do instead is I'm going to go back to this code and say hey let's make this uh you know close well let's come back to that in a second I'm going to go back to this and say hey resource. close which is a terrible idea we don't want program prammer should do this why because if they do it if there was an exception this will never be called that's a disaster then they start putting tryan finally that's going to become verbose code no thank you but what I'm going to do is I'm going to

02:04:36
make the close method private so by making the close method private you cannot call close as you can see right there so now that you cannot call close all right fine I don't call close that didn't solve the problem for us now I'm going to make The Constructor itself private as well so I made the Constructor uh private so now I cannot even create an object of resource so line 22 fails so what gives so here's what I'm going to do I'm going to go back to this code and say resource. use just like we did in the

02:05:14
past example then I get a resource as an argument then I say simply resource do op one and then do op two and I'm going to call those methods in sequence just like the fluent interface example we saw we're going to Simply call those methods like so so what is the use method so I'm going to say a public let's say static let's say void use takes a consumer of resource we'll call it as a block like so so I'm going to bring in a consumer conser so that's a function. star so we

02:05:55
brought in a consumer what are we going to do now then I say try with a finally block as you would imagine then I say resource is equal to new resource and we will come back here and say resource. close on the finally block then within this I'm going to say the block do accept and we will pass the resource over to it so when you run the code this time you can see the close operations happens automatically so I've used this pattern quite a bit in my code and this is extremely powerful in terms of

02:06:38
capturing the essence of the things you want to do I'll give you some examples of this so in Scala they call this the loan pattern they call it a loan pattern because this is as if you get an object on loan for example I go rent a car before I get the car I hope they clean the car really nicely and when I return the car they check for damages so that's the loan pattern there's a pre-op and a post off op there's a before operation and an after operation so essentially in this case you can see that we are

02:07:13
calling the ReUse and we are given a resource before you come into this code the resource has been created you can do any pre- Ops in this case and when you leave the block of code what is it going to do it's going to do the postop I'll tell you where I used this I was working on an application where we had several controllers where we got to perform operations within a transaction and what I noticed developers do is they copy and paste code to start the transaction then in the middle is their code like a

02:07:50
sandwich and then there's code to do Post operations and I noticed anytime they start a new controller they would copy and paste code uh somebody told me that's called template driven development not the type of development you want to do right so so quickly we realize there was so much duplication in code how do we avoid it so here's what we did we wrote ourselves a class called transaction and within the class transaction we wrote a function called run in transaction action and we

02:08:24
provided a consumer of of actually a A runable or actually a consumer consumer of transaction object that we created a block and guess what we did within this code right so let's say within this code what we did was we would you know uh start a transaction and then block. accept right block. accept and we provided a transaction to it and then uh when you come back from it if okay commit else log and roll back so we wrote a message like that uh method like that in our transaction how did we use

02:09:09
it in our code we simply said transaction. run in transaction and we got a transaction object on our hand and we went about doing our work and when we finished it it was able to check a condition and decide what happened based on that particular condition within the transaction object so essentially you can start capturing these kinds of policies in your code and this can be a very very effective method I've seen this pattern used extensively in other languages like performing file IO operations you start open a file but you

02:09:46
don't have to remember to close it when you leave the block of code the close happens automatically that can be very powerful as well so that is basically the execute around method pattern like I said some languages like Scala call it as loan pattern if you ask me this is one of my most favorite patterns I like it for its Elegance I've also used it quite extensively in my own application development as well so this is one of my favorite patterns for that reason having said that I want to talk about two

02:10:19
things left here one one is about sealed interfaces and sealed classes excuse me and the other one is about a little bit about data oriented programming I'll keep the sealed minimum so we can spend a little bit more time on data oriented programming in here so let's talk about sealed really quickly so let's say for a minute I I'll give you an example of a use case where this can be really helpful there are times when you could be creating a piece of code with a certain number of uh classes

02:10:58
and interfaces imagine this for a minute imagine you are a creator of a open source library and you put this out there and say folks this is a service this Library provides go for it use it and now people are beginning to use your library and let's say uh your let's say your library has has uh two kinds of interfaces let's say so what are the two kinds of interfaces one for others to implement and two for others only to use so in other words when I create my library I want you to implement some

02:11:42
interfaces that's awesome when you pass an object to me I can look at your object it implements my interface that's great my methods can receive it I can use it but I've got a few interfaces that you can use you can call you can call upon services on those interfaces but I don't want you to ever Implement those interfaces now think about this for a minute look at the string class string class is very powerful but nobody can Implement string because it's final so final classes are awesome if you make

02:12:18
a class Final you're telling others you can use this but you are not allowed to extend it or implement it unfortunately interfaces are for extensibility so if you create an interface anyone can implement but now you're saying no no no dude don't Implement my interface I got a bunch of classes that implement the interface I marked all the classes final your job is to use any of the classes but I dare you not to implement the interface well how do you en force that well I've got clients with similar

02:12:55
problems here are a few Solutions I've seen people use one solution during runtime you can take an object look at the class of the object if it's not one of yours you could scream and fail good job you're going to make enemies out of people using your library they're going to say duh if you told me I shouldn't do this I would have saved time not implementing it and after all this effort I get a runtime error and you're not going to be happy with that well here's a second solution I've seen

02:13:30
people do the second solution you can Implement your own annotation processor and you can annotate your classes and make that annotation package friendly now if somebody were to implement your interface your annotation processor will wake up and say hey do you have that interface no you don't I'm going to fail the build well good news you got a compilation error so people are going to be less unhappy because they get a compilation error saying you're not allowed to implement this interface o so

02:14:05
I should use it not implement it great but you need to put a lot of effort to create that annotation processor code and then to maintain it so what can we do to avoid that problem well this is where as an example a sealed can be very helpful so to give an example of this I'm going to create a traffic light and I going I'm going to say traffic light is equal to new red light for a traffic light I'm going to Output the traffic light as well so very very simple code let's go back here and create a traffic

02:14:42
light. Java and I create an interface called traffic light and I've also created a final class called red light which implements the traffic light and likewise I also have a yellow light and I have a green light as well so these are three classes that Implement that interface now this is going to provide us the implementation uh for the three and I run the code you can see the traffic light but I want to limit who can have these classes uh extending from the class so I'm going to go back to the

02:15:24
traffic light and I say sealed interface traffic light so now I have limited the children of the traffic light to be only these three by default or you can say in here uh in this case you can say uh permits and you can say red light uh yellow light and also green light and you can add to the list if you want to so this says those are the only three that are permitted to implement that particular interface so so far so good if I go back to this code no problem however if I said final class broken

02:16:07
light so I create a broken light implements the uh traffic light you notice that gives us an error the error says class is not allowed to extend sealed class traffic light even though it's an interface as it is not listed in the permits class so you cannot Implement from traffic light why not because you don't belong to the list of classes that are allowed to implement that interface however if you really want to implement what's called let's say a flashing light you can ask for permission to implement

02:16:48
so you can go back to the traffic light and here you can say comma flashing light so now that you added a flashing light so what what does that give us so you can see in this particular case uh of this particular code when I run the code we don't have an error why because flashing light is a member of the hierarchy however if you were to take uh another one let's call this as broken light so in this case that's not a member and you got an error that you cannot implement it so as as you can see

02:17:28
this provides you a set of classes that you can Implement so the question is where can these implementation classes be there's two answers to that question so the first answer is uh as follows so the first answer is if you are using modules then the uh classes uh uh the child classes right child of sealed may be in any package but within uh within the same module uh if you are not using modules um you should um so if you're not using modules then the SE child uh of sealed uh you know has to be uh in

02:18:21
the same package so uh so essentially that's a rule that you have to know so if you're using modules then the CH child of sealed may be in any package but within the same module if you're not using modules then the child of sealed has to be in the same package it could be in a different file but it got to be in the same package so that is a rule you have to follow so so that is about sealed to give you that benefit but I want to take that discussion further into an area where we can benefit from

02:18:56
rethinking about how we write some code and this is where I think we need to be a bit be a bit careful so the first thing is uh paradigms uh are there to help us and not the other way around right so you don't have to use a paradigm the goal is to solve a problem and you want to use a technique an approach a way that makes sense for what you do and if we become adamant and say there's only one way to do things we are at the loss in general so I often think about these techniques as tools I don't

02:19:43
want to be attached to a tool I want to use a tool that Mak sense for a problem at hand if a hand camer is the right tool that's great if a scissor scissor is a better tool that's fine I want to use a different tool for the problem at hand and don't get me wrong there are some very powerful tools but that doesn't mean that's the only tool we would use our only approach we would use so o is awesome uh it helps us to develop extensible uh code so I'm not going to argue with that at all object

02:20:25
programming is absolutely awesome it helps us to develop extensible code but we are now hearing about what is called Data oriented programming what is data oriented programming but wait why should I use it when I have this beautiful object ored programming with me so here is a way to think about it object programming is awesome it can help us to develop extensible code but there are times when using o op may lead to bloated code with many levels of uh you could say hierarchy uh that makes the code you

02:21:11
could say cumbersome and hard to maintain so this is where you may want to really rethink about it op is great but I don't want to force myself into using it when I'm going to be creating way too much code than I really need to so let's think about an example of this and why you may want to think about moving away from just using oop you're not using instead of oop you're combining oop with something else because that gives you a better tool a better idea so let's think of this so

02:21:52
suppose so suppose you are creating a hierarchy uh of so hierarchy uh of classes so you're creating a hierarchy of classes so like what for example you have a vehicle a car a truck are the is the hierarchy you are creating so when you create this hierarchy what is the benef benefit your vehicle can have an abstract method right so it can have an abstract method which is nice to have and your car and truck can override that method so what are we doing here polymorphism right at work so that is the beauty of this code

02:22:48
so you have a vehicle interface or a vehicle abstra class and you're saying I got a car I got a truck but I can use a vehicle and I can simply call the method for example a vehicle you could say Drive uh where uh depending on a vehicle at runtime uh you know it may uh refer to a car or a truck and the appropriate you can say appropriate Drive is called for us so the code is very extensible that's beautiful as ended and here's the bonus right if we add another uh type of vehicle uh say a boat

02:23:41
we can uh rely on polymorphism so to make the code the calling code right extensible uh that's a beauty of it without having to change we celebrate this as open closed principle so we're not disputing any of that all of that is awesome we can use it but at some point you have to ask the question is this helping me are making things worse for me so let's take a different example right now suppose you have you are using a library with a hierarchy of classes so suppose you're using a third

02:24:44
party library with the hierarchy of classes so they provided that hierarchy of classes for you and what do you have to do you have to implement logic that is different for different classes in that hierarchy so essentially if you think about it you are getting a third party Library this third partyy Library maybe external or could be internal to your company you just don't have access to change that library that happens too right so you have an access to a library you can use but you don't have ability

02:25:32
to change the source code for it so suppose you're using a third party library and a hierarchy of classes are given to you but you have to implement Logic for different classes in different ways in that hierarchy what is the oop solution for this so how would we implement this in oop any ideas what was that um instance off will violate extensibility right visual thinking please visitor pattern raise your hand if you have used visitor pattern and you are happy at the end of the day not a single hand go went up one person

02:26:25
is flashing the camera at me are you asking for support rescue me I like your answer you nailed it but it makes a point isn't it it's a lot of code for you to write and after you write all that code your head is pinning and you're trying to explain this code to somebody and you're saying isn't it awesome and they are like not exactly the word I would use it was a lot of work isn't it so you could use visitor pattern so could use visitor pattern create a parallel hierarchy of

02:27:19
classes so what you end up in this case is the following right so suppose you had vehicle sitting up there and within that you have a car and a Truck and and what do we normally do you have a vehicle processor you may create a hierarchy and in this case you may create a car processor and a a truck processor cessor now the problem with this approach is the minute you see the hierarchy changing right so the minute you see the hierarchy changing you have a boat being introduced here you got to create a boat processor you have a

02:28:08
sports car you got to create a sports car processor so you create this parallel hierarchy of classes and that can become really messy so what's the problem there are two problems with this approach right one a lot of code uh to write two bloated it's got too much things going on and then hard to understand where all the logic is being done uh imagine explaining uh to a business person about the handling of the logic you need to be sifting between files to show oh look this is happening here let me show you there let me show

02:28:58
you there and they're like uh excuse me what was the other file that you showed me and little little code oh that's another thing right so little code little little code uh spread out in so many places so again I'm not suggesting o is bad I'm not suggesting don't use oop I'm just suggesting that there is a Tipping Point Beyond which it can become bur burdensome so this is where we could think of data oriented programming so what does data oriented programming say data oriented

02:29:43
programming says let's think of this as data let's take a procedural approach to to deal with logic related to multiple types of data this works I would say works really well when dealing with I would argue a thirdparty hierarchy of data so when you're dealing with a third party hierarchy of data but you need to do different things based on the type of data this can be really powerful so let's take a look at an example of what we can do with this and how it can benefit for us let's say we go to this example of

02:30:28
vehicles we talked about so imagine interface vehicle and I want you to think about so imagine these are from a thirdparty library right so we have vehicle we have a class called car which implements vehicle and we have a class called truck which implements vehicle as well so now uh imagine so imagine the following is our code right so that's a third party code but here we are in our code so what do we want to do with our code in here so you might say uh hand uh process uh right whatever that is new car or you

02:31:18
want to process a new V uh truck so you may bring in a truck for processing in here so we have a truck but you want to be able to process these objects how do you handle that how do you process it so you can say here public static let's say process actually let's go ahead and say uh string process and I want to Simply return the output of this code so we'll say output right so we'll output the result of that similarly we will simply say here is a truck I want to Output as well so what am I going to do with the

02:31:55
process the process takes a vehicle as an argument so we say vehicle and what are we going to do within this code so going back to your comment about let's use instance off right but instance of makes a code non-extensible this is where you deal with the tradeoff and you say yeah it's it's going to make the code non extens non-extensible but in reality it's not going to change that often right this is where tradeoffs are important we can take a certain recommendation and run with it and be

02:32:36
adamant about it or realize the in reality the number of times it affects us is not that much and so maybe it's not a bad idea maybe we can clean it up a little bit so it doesn't appear that strange so he nailed it when he said use instance off right because it's a tradeoff here is where we got to think of tradeoffs I'm I'm dating myself but about 40 years ago when I had to write code the tools available for me were very different from tools that people had available today there were no idees

02:33:16
back then I know I know some of the young programmers are like looking at me is like really you had no idees nope there were no IDE I mean hearing that he wants to leave the room right he just couldn't take it so so there were no idees literally back in time and and often people look at me and say so you had no IDs to work with like nope and then they asked me so what do you do with dinosaurs that came around when you coding right yeah and I was working on a large scale C++ application in C++ you may know this right you don't

02:33:52
have a file you have a header and a CPP file for everything you do you have two files not one because they believe in giving you twice the pain not just once so now imagine this you had to make a change to a certain function how do you do that you do a grap and you find every file that's affected now you have a list of files to to change and you got to open one file at a time and you got to change that manually if you think that was barbaric it was and if you told me design something I'm going to say let me think 700 times

02:34:39
before I design it because the cost of change is very very high today you're sitting around using an IDE and you're like E I don't like that function name you gently right click on it type the new name and before you could blink your eyes all the thousands of files are changed and it says would you like fries with it imagine the cost of change is extremely minimum so what we need to ask the question is you don't want to avoid change you want to ask the question I don't want to create change that is

02:35:21
expensive and if a change is not expensive then it's no big deal so we have to evaluate that tradeoff and we got to ask the question is this going to work cost me more or is going to cost me less not cost nothing because to cost nothing you're going to end up spending a lot more so you're not really costing nothing in the long run so that's a question we want to ask so here we say return and we use a pattern matching syntax on a vehicle and I'm going to say if the case if it is a car and if it's a case it's a

02:36:01
truck or a default it's something else so I'm going to Simply say processing car uh you know you can say logic right so you can say logic goes here so similarly you could say a processing truck and you can put the logic in here right so processing truck and uh this is going to be uh no clue whatever that is so essentially in this case what what are be dealing with is the question right so let me quickly capture this so this is going to be uh let me make sure I'm in the right file so I'm going to

02:36:46
move this to the sample. Java let's go back here so so essentially uh let's see uh pattern matching blah blah blah let's see a type uh uh type pattern expected line number 13 oh yeah of course so this is a car and that's a a truck so essentially in this case you are saying I want to process a car logic goes here so essentially what are we saying here we still have a object under hierarchy right that's fine and the hierarchy is in a third party not in our code and you are saying I want to bring in the

02:37:24
processing for a car or a processing for a truck nothing says you shouldn't do the following you could have said for example this could be elsewhere as well and this could be handle uh you could say process car right nothing wrong with it you can take a car and you can process it this could be returning Logic for car processing there's nothing wrong with it so you can come down here and you can simply say hey if it's a car I'm not going to put 50 lines of code there that's horrible so I'm going to call

02:38:01
Process car and pass the car to it absolutely you can do that but the beauty of that is you are providing these dispatchers to these functions and by providing the dispatch to the function you're keeping that Clarity in here but what you did was you flatten the hierarchy and you're not dealing with a parallel hierarchy or a visitor pattern and that is not to say you should never do visitor that is not to say you should never build a parallel hierarchy but ask the question are you gaining or are you losing that's the

02:38:38
question you want to ask and I'm a huge fan of having different tools to work with and generally speaking rather than letting my emotion drive it I like to prototype my code and when I prototype and I Implement using o one in one place I Implement using data driven in another place and then I compare it and say which one feels better what are the pros and cons I can start listing the pros and cons and then select which one feels better and here is the beauty if I don't bring my emotions into it if I like it

02:39:15
today I'll keep it if I feel that's not the right thing to do I can always refactor it and the beauty is it gives an opportunity for us to experience two different ways of coding and then we can take one that has better merits than the other ones and keep the one that is going to give us a better result so so not to be tight to data oriented programming not to be tight to oop but I would say evaluate so when you face a new problem next time when you feel like you can use a visitor pattern for

02:39:47
example give it a shot by prototyping two solutions and bring your team together and say hey let's not focus on which we like but let's focus on which one is easier to maintain which one is easier to understand which one is less bloated which one is easier to work with and based on that let's pick a solution so essentially I see this as a nice design pattern in that we can use the data oriented programming and leverage the power of pattern matching and reduce the complexity in the code and the bloat

02:40:24
in the code as well so to summarize what we talked about we talked about three different types of patterns those that can still be used in Java but things that are easier to use right now thanks to having Lambda expressions and things like that we talked about patterns where we can benefit from things we didn't ever do before but we can do now because we have these Concepts and we have newer ideas that can help us to reduce the blo and code as well and we can benefit from them as patterns as well hope that was

02:40:57
useful thank you so much for being [Applause] here all systems are nominal initialize Genesis sequence

