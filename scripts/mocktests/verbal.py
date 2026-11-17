"""Verbal ability: hand-written banks (original sentences and passages). Each pool yields questions without repeats."""
from common import fixed

# word, synonym, antonym, three distractors that are neither
SYN_ANT = [
    ("abundant", "plentiful", "scarce", "fragile", "hostile", "rapid"), ("candid", "frank", "deceptive", "gloomy", "fertile", "silent"),
    ("diligent", "hardworking", "lazy", "curious", "noisy", "timid"), ("frugal", "thrifty", "extravagant", "brave", "dull", "tidy"),
    ("obsolete", "outdated", "modern", "hidden", "fragrant", "costly"), ("benevolent", "kind", "cruel", "proud", "silent", "early"),
    ("meticulous", "careful", "careless", "angry", "famous", "hollow"), ("tranquil", "calm", "agitated", "huge", "rigid", "plain"),
    ("vivid", "bright", "dull", "honest", "rough", "late"), ("reluctant", "unwilling", "eager", "tired", "pure", "loud"),
    ("feeble", "weak", "strong", "narrow", "tasty", "smooth"), ("prudent", "wise", "reckless", "shy", "wealthy", "pale"),
    ("hostile", "unfriendly", "friendly", "ancient", "silent", "sharp"), ("lucid", "clear", "confusing", "lucky", "heavy", "warm"),
    ("transparent", "clear", "opaque", "temporary", "fierce", "wide"), ("augment", "increase", "reduce", "observe", "decorate", "borrow"),
    ("commence", "begin", "conclude", "comment", "compare", "collect"), ("conceal", "hide", "reveal", "conceive", "repair", "reject"),
    ("diminish", "decrease", "enlarge", "describe", "deliver", "discover"), ("expand", "enlarge", "contract", "explain", "export", "expect"),
    ("hasty", "hurried", "deliberate", "hungry", "healthy", "heavy"), ("humble", "modest", "arrogant", "humid", "helpful", "hollow"),
    ("immense", "huge", "tiny", "intense", "innocent", "idle"), ("jovial", "cheerful", "gloomy", "juvenile", "jealous", "judicial"),
    ("lenient", "tolerant", "strict", "lengthy", "lean", "lethal"), ("loquacious", "talkative", "reserved", "local", "loyal", "lofty"),
    ("mundane", "ordinary", "extraordinary", "muddy", "musical", "mutual"), ("novice", "beginner", "expert", "novel", "nurse", "notice"),
    ("optimistic", "hopeful", "pessimistic", "optional", "obvious", "ornate"), ("placid", "peaceful", "turbulent", "plastic", "plain", "polite"),
    ("prosperous", "wealthy", "poor", "proper", "precious", "prompt"), ("robust", "sturdy", "frail", "rustic", "rapid", "rigid"),
    ("scarce", "rare", "plentiful", "scary", "sacred", "secure"), ("serene", "calm", "chaotic", "severe", "sincere", "sudden"),
    ("timid", "shy", "bold", "tidy", "tiny", "tired"), ("trivial", "minor", "significant", "tribal", "typical", "tropical"),
    ("valiant", "brave", "cowardly", "valuable", "vacant", "vague"), ("verbose", "wordy", "concise", "vertical", "versatile", "vicious"),
    ("zealous", "enthusiastic", "apathetic", "jealous", "zany", "zonal"), ("adversity", "hardship", "prosperity", "advertisement", "advice", "adventure"),
    ("affluent", "rich", "needy", "fluent", "affable", "afraid"), ("ambiguous", "unclear", "definite", "ambitious", "amiable", "ample"),
    ("amicable", "friendly", "hostile", "ample", "amusing", "anxious"), ("arduous", "difficult", "easy", "ardent", "arid", "artful"),
    ("audacious", "daring", "timid", "auditory", "austere", "average"), ("brevity", "shortness", "lengthiness", "bravery", "beauty", "boldness"),
    ("cordial", "warm", "cold", "central", "cordless", "careful"), ("cumbersome", "bulky", "convenient", "cumulative", "cunning", "curious"),
    ("dearth", "shortage", "abundance", "death", "debt", "depth"), ("dexterous", "skilful", "clumsy", "dangerous", "dense", "devout"),
    ("eloquent", "expressive", "inarticulate", "elegant", "eligible", "elastic"), ("eminent", "distinguished", "unknown", "imminent", "empty", "ethical"),
    ("ephemeral", "short-lived", "permanent", "ethereal", "equal", "eventual"), ("exonerate", "acquit", "convict", "exaggerate", "exhale", "exhaust"),
    ("fallacy", "misconception", "truth", "failure", "fable", "fame"), ("fervent", "passionate", "indifferent", "fertile", "festive", "feverish"),
    ("gregarious", "sociable", "solitary", "gracious", "greedy", "gradual"), ("impeccable", "flawless", "faulty", "impatient", "impolite", "imperial"),
    ("inevitable", "unavoidable", "avoidable", "invisible", "inedible", "infinite"), ("jeopardy", "danger", "safety", "journey", "jealousy", "justice"),
    ("lethargic", "sluggish", "energetic", "lethal", "legal", "literal"), ("magnanimous", "generous", "petty", "magnetic", "massive", "majestic"),
    ("negligent", "careless", "attentive", "negative", "neutral", "nimble"), ("obstinate", "stubborn", "flexible", "obvious", "obscure", "obedient"),
    ("ostentatious", "showy", "modest", "orthodox", "obnoxious", "occasional"), ("pragmatic", "practical", "idealistic", "dramatic", "pristine", "problematic"),
    ("profound", "deep", "shallow", "profitable", "proper", "prominent"), ("rebuke", "scold", "praise", "rebuild", "recall", "refuse"),
    ("resilient", "tough", "fragile", "resident", "reluctant", "relevant"), ("sagacious", "wise", "foolish", "spacious", "salty", "sacred"),
    ("scrutinize", "examine", "overlook", "scribble", "scramble", "secure"), ("superficial", "shallow", "thorough", "superior", "supreme", "surplus"),
    ("tenacious", "persistent", "yielding", "tender", "tense", "tedious"), ("vindicate", "justify", "blame", "vibrate", "violate", "visualize"),
    ("wane", "decline", "grow", "wander", "wake", "warn"), ("acquiesce", "agree", "resist", "acquire", "accuse", "adjust"),
    ("alleviate", "relieve", "aggravate", "allocate", "allude", "alter"), ("animosity", "hatred", "goodwill", "anxiety", "audacity", "atrocity"),
    ("benign", "harmless", "harmful", "beneath", "boring", "brittle"), ("cogent", "convincing", "weak", "cognate", "collective", "costly"),
    ("deter", "discourage", "encourage", "detach", "devour", "dictate"), ("enigmatic", "mysterious", "obvious", "energetic", "emphatic", "erratic"),
    ("exacerbate", "worsen", "improve", "exaggerate", "excavate", "exceed"), ("futile", "useless", "useful", "fertile", "fragile", "festive"),
    ("hamper", "hinder", "help", "hammer", "handle", "harvest"), ("impartial", "fair", "biased", "impatient", "imperfect", "immortal"),
    ("innate", "inborn", "acquired", "inmate", "inner", "insane"), ("lavish", "luxurious", "meagre", "lavender", "lawful", "lazy"),
    ("mitigate", "lessen", "intensify", "migrate", "imitate", "motivate"), ("nonchalant", "casual", "anxious", "nominal", "noble", "notable"),
    ("pertinent", "relevant", "irrelevant", "persistent", "pertinacious", "petty"), ("reticent", "reserved", "outspoken", "recent", "resilient", "remote"),
    ("sporadic", "occasional", "frequent", "spiritual", "splendid", "stable"), ("vigilant", "watchful", "careless", "vigorous", "violent", "virtual"),
]

# sentence with ____, correct, three wrong
FILL = [
    ("She has been working here ____ 2019.", "since", "for", "from", "by"), ("He is good ____ solving puzzles.", "at", "in", "on", "with"),
    ("The meeting was called ____ because of the storm.", "off", "out", "up", "in"), ("Neither the manager nor the clerks ____ present.", "were", "was", "is", "has been"),
    ("I prefer tea ____ coffee.", "to", "than", "over", "from"), ("He insisted ____ paying the bill.", "on", "at", "for", "to"),
    ("By the time we reached, the train ____.", "had left", "has left", "left", "leaves"), ("If I ____ you, I would accept the offer.", "were", "am", "was being", "will be"),
    ("She is ____ honest woman.", "an", "a", "the", "no article"), ("The committee ____ divided in its opinion.", "is", "are", "were", "have"),
    ("He has been ill ____ two weeks.", "for", "since", "from", "during"), ("The book is ____ the table.", "on", "at", "in", "over"),
    ("We look forward ____ you soon.", "to meeting", "to meet", "meeting", "for meeting"), ("Hardly had he left ____ it began to rain.", "when", "than", "then", "that"),
    ("No sooner did she arrive ____ the lights went out.", "than", "when", "then", "that"), ("He is senior ____ me by two years.", "to", "than", "from", "over"),
    ("The police ____ arrested the thief.", "have", "has", "is", "was"), ("Each of the students ____ given a prize.", "was", "were", "are", "have been"),
    ("She objected ____ his rude remarks.", "to", "on", "against", "at"), ("The project was completed ____ time.", "on", "at", "by", "with"),
    ("He apologised ____ being late.", "for", "of", "about", "on"), ("Please refrain ____ smoking here.", "from", "of", "to", "with"),
    ("The new policy will come ____ effect next month.", "into", "in", "to", "on"), ("They were accused ____ cheating.", "of", "for", "with", "about"),
    ("I am not used ____ such cold weather.", "to", "with", "for", "at"), ("He was the ____ of the two brothers.", "taller", "tallest", "most tall", "more tall"),
    ("This is the ____ film I have ever seen.", "best", "better", "good", "most good"), ("She sings ____ than her sister.", "better", "best", "more good", "well"),
    ("Mathematics ____ my favourite subject.", "is", "are", "were", "have been"),
    ("He has a ____ knowledge of finance.", "thorough", "through", "though", "threw"), ("The ____ of the river was very strong.", "current", "currant", "curent", "currency"),
    ("Her ____ to the plan surprised everyone.", "objection", "objective", "object", "objecting"), ("The manager gave his ____ to the proposal.", "consent", "concent", "content", "consensus"),
    ("You should ____ your doctor before taking this medicine.", "consult", "insult", "result", "exult"), ("The thief tried to ____ the police.", "elude", "allude", "delude", "illude"),
    ("Please ____ the form and submit it today.", "fill in", "fill up of", "fill on", "fill at"), ("The workers went ____ strike yesterday.", "on", "in", "at", "for"),
    ("He made ____ effort to finish the work early.", "every", "all", "each of", "whole"), ("She has ____ friends in the new city.", "few", "little", "less", "much"),
    ("There is ____ water left in the bottle.", "little", "few", "many", "a few of"), ("The children ____ playing in the park since morning.", "have been", "has been", "are", "were"),
    ("Do you know where ____?", "she lives", "does she live", "she does live", "lives she"), ("He asked me what time ____.", "it was", "was it", "is it", "it is being"),
    ("I wish I ____ more time to prepare.", "had", "have", "will have", "am having"), ("The report must be submitted ____ Friday.", "by", "until", "till", "upto"),
    ("She is capable ____ handling the pressure.", "of", "for", "to", "at"), ("The company is famous ____ its customer service.", "for", "of", "with", "by"),
    ("He divided the cake ____ the four children.", "among", "between", "within", "amid of"), ("The bridge was built ____ the river.", "across", "along", "through", "into"),
]

# sentence parts A-D, index of the wrong part (4 = no error), correction
ERRORS = [
    (["Each of the boys", "have brought", "his own", "lunch today."], 1, "‘Each’ is singular: ‘has brought’."),
    (["She is one of those", "people who", "always speaks", "the truth."], 2, "‘who’ refers to ‘people’ (plural): ‘speak’."),
    (["The number of students", "in the class", "are increasing", "every year."], 2, "Subject is ‘the number’: ‘is increasing’."),
    (["He did not", "knew the answer", "to the last", "question."], 1, "After ‘did not’ use the base form: ‘know’."),
    (["Neither of the two", "candidates", "were selected", "for the job."], 2, "‘Neither’ is singular: ‘was selected’."),
    (["I have seen", "him yesterday", "at the", "market."], 0, "With ‘yesterday’ use the simple past: ‘I saw’."),
    (["The furnitures", "in this room", "are very", "expensive."], 0, "‘Furniture’ is uncountable: ‘The furniture … is’."),
    (["He is", "more cleverer", "than his", "brother."], 1, "Double comparative: ‘cleverer’."),
    (["She has", "returned back", "from Delhi", "this morning."], 1, "‘Returned back’ is redundant: ‘returned’."),
    (["Unless you", "do not work hard,", "you will not", "succeed."], 1, "‘Unless’ is already negative: ‘work hard’."),
    (["The teacher", "as well as the students", "were present", "at the function."], 2, "Subject is ‘the teacher’: ‘was present’."),
    (["One of my friend", "is going", "to Canada", "next week."], 0, "‘One of my friends’."),
    (["I am", "looking forward", "to see you", "soon."], 2, "‘Looking forward to seeing’."),
    (["He gave me", "an advice", "which proved", "very useful."], 1, "‘Advice’ is uncountable: ‘a piece of advice’."),
    (["If he would have", "studied", "he would have", "passed."], 0, "Third conditional: ‘If he had studied’."),
    (["The news", "are", "too good", "to be true."], 1, "‘News’ is singular: ‘is’."),
    (["She sings", "very good", "for a", "beginner."], 1, "Adverb needed: ‘very well’."),
    (["He is", "senior than me", "by", "three years."], 1, "‘Senior to me’."),
    (["We discussed", "about the problem", "for", "an hour."], 1, "‘Discuss’ takes no ‘about’: ‘discussed the problem’."),
    (["The police", "has arrested", "the two", "suspects."], 1, "‘Police’ takes a plural verb: ‘have arrested’."),
    (["Hardly had", "I reached the station", "than the train", "left."], 2, "‘Hardly … when’."),
    (["She is", "the most", "smartest girl", "in the class."], 2, "Double superlative: ‘the smartest girl’."),
    (["Please", "let him", "to go", "home."], 2, "After ‘let’ use the bare infinitive: ‘let him go’."),
    (["My brother", "and I", "am going", "to the fair."], 2, "Compound subject: ‘are going’."),
    (["The quality", "of these apples", "are not", "good."], 2, "Subject is ‘quality’: ‘is not’."),
    (["He has", "been working here", "since", "five years."], 2, "Duration takes ‘for’: ‘for five years’."),
    (["I", "will call you", "when I will reach", "home."], 2, "Time clause takes the present: ‘when I reach’."),
    (["Mathematics", "are", "my favourite", "subject."], 1, "‘Mathematics’ is singular: ‘is’."),
    (["She", "prefers coffee", "than", "tea."], 2, "‘Prefer … to’."),
    (["The book", "which I bought", "yesterday", "is very interesting."], 4, "The sentence is correct."),
    (["He", "insisted to go", "there", "alone."], 1, "‘Insisted on going’."),
    (["Ten kilometres", "are", "a long distance", "to walk."], 1, "A distance as one amount: ‘is’."),
    (["Neither Ravi", "nor his friends", "was invited", "to the party."], 2, "The verb agrees with the nearer subject ‘friends’: ‘were invited’."),
    (["The manager", "along with his team", "have arrived", "at the venue."], 2, "Subject is ‘the manager’: ‘has arrived’."),
    (["I have", "less friends", "than", "my sister."], 1, "Countable noun: ‘fewer friends’."),
    (["She", "did not", "came to", "the office."], 2, "After ‘did not’: ‘come’."),
    (["Between you", "and I,", "this plan", "will fail."], 1, "Object pronoun after a preposition: ‘and me’."),
    (["The scenery", "of Kashmir", "are", "beautiful."], 2, "‘Scenery’ is singular: ‘is’."),
    (["He is", "an honest", "and hardworking", "man."], 4, "The sentence is correct."),
    (["He", "is working", "in this company", "for ten years."], 1, "Duration up to now: ‘has been working’."),
    (["We", "reached", "at the station", "on time."], 2, "‘Reach’ takes no ‘at’: ‘reached the station’."),
    (["The jury", "was divided", "in their", "opinions."], 1, "Members acting separately: ‘were divided’."),
    (["She", "has", "a lot of", "knowledges."], 3, "‘Knowledge’ is uncountable: ‘knowledge’."),
    (["It is", "high time", "we leave", "for the airport."], 2, "‘It is high time we left’."),
    (["The doctor", "advised him", "to give up", "smoking."], 4, "The sentence is correct."),
]

# sentence with a [bracketed] part, best replacement first, then three others (the last option is often "No improvement")
IMPROVE = [
    ("He [has went] to the market.", "has gone", "had went", "goes", "No improvement"), ("She is [more stronger] than her brother.", "stronger", "most strong", "strongest", "No improvement"),
    ("I [am knowing] him for years.", "have known", "knew", "know", "No improvement"), ("We [had a hard time to find] the hotel.", "had a hard time finding", "had hard time find", "have hard time found", "No improvement"),
    ("[Despite of the rain], we went out.", "Despite the rain", "Inspite the rain", "Although of the rain", "No improvement"), ("He [did not went] to school.", "did not go", "does not went", "not went", "No improvement"),
    ("The reason he failed [is because] he was lazy.", "is that", "was because of", "being", "No improvement"), ("She [cannot help to laugh] at his jokes.", "cannot help laughing", "can help not laughing", "cannot helping laugh", "No improvement"),
    ("[Being a rainy day], we stayed at home.", "As it was a rainy day", "Rainy day being", "Being it rainy day", "No improvement"), ("He [is used to work] late at night.", "is used to working", "used to working", "uses to work", "No improvement"),
    ("I [would have came] if you had called.", "would have come", "will have come", "would came", "No improvement"), ("The children [was playing] in the garden.", "were playing", "is playing", "has played", "No improvement"),
    ("She [suggested him to take] a break.", "suggested that he take", "suggested him taking", "suggest him take", "No improvement"), ("[Who] did you give the book to?", "Whom", "Whose", "Which", "No improvement"),
    ("He walked [very slow] to the door.", "very slowly", "much slow", "slower", "No improvement"), ("She is [one of the best singer] in the city.", "one of the best singers", "one of best singer", "the one best singer", "No improvement"),
    ("They [have finished] the work last week.", "finished", "had been finishing", "have been finished", "No improvement"), ("[If I was you], I would apologise.", "If I were you", "If I am you", "Were I am you", "No improvement"),
    ("The work [was done by me and him].", "was done by him and me", "done by me and him", "was did by him and me", "No improvement"), ("We [discussed about] the new plan.", "discussed", "discussed on", "discussing about", "No improvement"),
    ("I prefer reading [than] watching TV.", "to", "over to", "rather", "No improvement"),
    ("She [laid] on the bed because she was tired.", "lay", "lied", "layed", "No improvement"), 
    ("Please [return back] the book tomorrow.", "return", "returned back", "back return", "No improvement"),
]

# four sentences in the right order (a short paragraph)
JUMBLES = [
    ["Rahul wanted to learn to cook.", "He watched a few simple recipe videos.", "Then he bought the vegetables he needed.", "By evening he had made his first dal."],
    ["The company launched a new phone.", "Early reviews praised its battery life.", "Sales doubled in the first month.", "Rivals soon cut their prices in response."],
    ["Water evaporates from oceans and lakes.", "The vapour rises and cools in the air.", "It condenses into tiny droplets that form clouds.", "When the droplets grow heavy, they fall as rain."],
    ["Meera applied for an internship.", "She was called for an interview the next week.", "The interview went better than she expected.", "Two days later she received the offer letter."],
    ["The old bridge had developed cracks.", "Engineers inspected it and declared it unsafe.", "Traffic was diverted to another route.", "A new bridge was completed a year later."],
    ["First, collect the requirements from the users.", "Next, design the system based on them.", "Then write and test the code.", "Finally, deploy the software and gather feedback."],
    ["The farmer prepared his field in June.", "He sowed the seeds after the first rains.", "Through the summer he watered and weeded the crop.", "In October he harvested a rich yield."],
    ["Arjun lost his wallet on the bus.", "He reported it at the depot office.", "A conductor had found it under a seat.", "Arjun got it back with everything inside."],
    ["The city faced a severe water shortage.", "The council asked residents to save water.", "Rainwater harvesting was made compulsory for new buildings.", "Within three years the groundwater level began to rise."],
    ["A small startup built a delivery app.", "Users liked how fast it was.", "Investors noticed its rapid growth.", "The startup raised money to expand to ten cities."],
    ["The alarm rang at six.", "Sneha got up and went for a run.", "After a shower she had breakfast.", "She reached the office before nine."],
    ["The scientist noticed an unusual result.", "She repeated the experiment to be sure.", "The result appeared again every time.", "She then published her findings in a journal."],
    ["Customers complained about slow service.", "The manager studied the order process.", "He found that billing caused most delays.", "A new billing system cut waiting time by half."],
    ["The library announced a reading challenge.", "Students signed up in large numbers.", "Each read at least ten books in three months.", "The winners were honoured at the annual day."],
    ["Heavy rain lashed the coast for two days.", "Several low-lying areas were flooded.", "Rescue teams moved families to safer places.", "Once the water receded, cleaning work began."],
    ["Kiran wanted to buy a laptop.", "He compared models online for a week.", "He chose one with a good battery and keyboard.", "He placed the order during a festival sale."],
    ["The team missed the project deadline.", "The leader called a meeting to find the cause.", "Unclear requirements had led to rework.", "The team now confirms requirements before starting."],
    ["A seed falls on moist soil.", "It absorbs water and swells.", "A tiny root grows downwards.", "Soon a shoot pushes up towards the light."],
    ["The government announced a scholarship scheme.", "Students applied through an online portal.", "Applications were screened on merit and income.", "Selected students received the first instalment in July."],
    ["Priya started saving a part of her salary.", "She invested the savings in a mutual fund.", "Over the years the investment grew steadily.", "She used it to pay for her higher studies."],
]

# passage, then (question, correct, three wrong)
PASSAGES = [
    ("Remote work was once rare in Indian companies. The pandemic forced firms to try it at scale, and many found that productivity did not fall. "
     "Employees saved hours of commuting, and companies saved on office space. However, new problems appeared: junior staff missed informal "
     "learning from seniors, and some people found it hard to separate work from home life. Many firms have now settled on a hybrid model, "
     "with two or three office days a week.",
     [("What made companies try remote work at scale?", "The pandemic", "Lower salaries", "Government orders on office space", "Employee strikes"),
      ("Which problem of remote work does the passage mention?", "Juniors missed informal learning from seniors", "Productivity fell sharply", "Offices became more expensive", "Commuting time increased"),
      ("What have many firms finally chosen?", "A hybrid model", "Fully remote work", "Full-time office work", "Four-day weeks"),
      ("The tone of the passage is best described as", "balanced", "angry", "humorous", "fearful")]),
    ("Plastic is cheap, light and durable, which is why it is everywhere. The same durability makes it a problem: most plastic takes hundreds "
     "of years to break down. Much of it ends up in rivers and oceans, where it harms animals that swallow it or get trapped. Recycling helps, "
     "but only a small share of plastic is actually recycled. Experts say the most effective step is to use less plastic in the first place.",
     [("Why is plastic widely used?", "It is cheap, light and durable", "It breaks down quickly", "It is easy to recycle", "It is good for animals"),
      ("According to the passage, the most effective step is to", "use less plastic", "recycle all plastic", "burn plastic waste", "make plastic heavier"),
      ("The word ‘durable’ in the passage means", "long-lasting", "dangerous", "cheap", "flexible"),
      ("What does the passage say about recycling?", "Only a small share of plastic is recycled", "It solves the problem completely", "It is banned in most places", "It makes plastic more durable")]),
    ("Good sleep is essential for learning. While we sleep, the brain sorts and stores what we learned during the day. Students who stay up "
     "all night before an exam often remember less than those who sleep well, even if they studied for more hours. Regular sleep times, less "
     "screen use before bed and a dark, quiet room all improve sleep quality.",
     [("What does the brain do during sleep, according to the passage?", "Sorts and stores what was learned", "Forgets the day's events", "Stops all activity", "Plans the next day"),
      ("Students who stay up all night before an exam", "often remember less", "always score higher", "learn faster", "sleep better later"),
      ("Which is NOT given as a way to improve sleep?", "Studying in bed", "Regular sleep times", "Less screen use before bed", "A dark, quiet room"),
      ("The main idea of the passage is that", "sleep supports learning", "exams are harmful", "screens improve memory", "students study too little")]),
    ("India's digital payment system has grown remarkably. UPI allows instant transfers between bank accounts using a phone, and small "
     "shopkeepers now accept payments through QR codes. This has reduced the use of cash and made it easier to track transactions. Yet "
     "challenges remain: older people and those without smartphones can be left out, and online frauds have increased.",
     [("What does UPI allow?", "Instant transfers between bank accounts by phone", "Free international travel", "Loans without banks", "Cash withdrawals without a card only"),
      ("One benefit mentioned is that", "transactions are easier to track", "shops need more cash", "frauds have disappeared", "phones became cheaper"),
      ("Who may be left out, according to the passage?", "People without smartphones", "Small shopkeepers", "Banks", "Young students"),
      ("The word ‘remarkably’ is closest in meaning to", "strikingly", "slowly", "secretly", "partly")]),
    ("Teamwork is often judged in interviews through group discussions. Candidates who speak the most do not always do best. Evaluators look "
     "for people who listen, build on others' points, bring the group back to the topic and help reach a conclusion. Interrupting others or "
     "repeating the same point usually counts against a candidate.",
     [("In a group discussion, evaluators look for people who", "listen and build on others' points", "speak the most", "interrupt to show confidence", "repeat their main point often"),
      ("What usually counts against a candidate?", "Interrupting others", "Helping reach a conclusion", "Listening carefully", "Bringing the group back to the topic"),
      ("The passage is mainly about", "how teamwork is judged in group discussions", "how to write a resume", "why interviews are unfair", "how to speak loudly"),
      ("According to the passage, speaking the most", "does not guarantee doing best", "always wins", "is required", "is banned")]),
    ("Electric vehicles are becoming more common in Indian cities. They produce no exhaust and are cheaper to run than petrol vehicles. Their "
     "main drawbacks are a higher purchase price and a shortage of charging stations. As battery prices fall and more chargers are installed, "
     "experts expect electric two-wheelers and buses to spread fastest.",
     [("Which is an advantage of electric vehicles mentioned in the passage?", "Lower running cost", "Lower purchase price", "More charging stations", "Longer roads"),
      ("A drawback mentioned is", "a shortage of charging stations", "loud engines", "high exhaust", "lack of buyers in villages"),
      ("Which vehicles are expected to spread fastest?", "Two-wheelers and buses", "Trucks", "Luxury cars", "Tractors"),
      ("What will help electric vehicles spread?", "Falling battery prices and more chargers", "Rising battery prices", "Fewer roads", "Higher petrol taxes only")]),
    ("A good resume is short and specific. Recruiters spend less than a minute on a first look, so the most important facts must be easy to "
     "find. Instead of listing duties, strong resumes show results with numbers, such as ‘reduced page load time by 40%’. Spelling mistakes "
     "and long paragraphs create a poor impression.",
     [("Why must key facts be easy to find in a resume?", "Recruiters spend less than a minute on a first look", "Resumes are read many times", "Recruiters prefer long resumes", "Facts are not important"),
      ("Strong resumes", "show results with numbers", "list every duty", "use long paragraphs", "avoid specifics"),
      ("What creates a poor impression?", "Spelling mistakes", "Numbers", "Short sentences", "Clear headings"),
      ("The word ‘specific’ means", "precise", "general", "lengthy", "colourful")]),
    ("Many rivers in India are fed by Himalayan glaciers. As temperatures rise, these glaciers are shrinking. In the short term this can "
     "increase river flow and the risk of floods; in the long term, rivers may carry less water in dry months. Scientists stress the need to "
     "monitor glaciers and to plan water use carefully.",
     [("What is happening to Himalayan glaciers?", "They are shrinking", "They are growing", "They are moving south", "They have stopped melting"),
      ("In the short term, shrinking glaciers can", "increase river flow and flood risk", "reduce floods", "stop river flow", "lower temperatures"),
      ("What may happen in the long term?", "Rivers may carry less water in dry months", "Rivers will freeze", "Floods will end", "Glaciers will return"),
      ("Scientists stress the need to", "monitor glaciers and plan water use", "build more glaciers", "ignore river flow", "stop all farming")]),
    ("Open-source software is written by communities and shared freely. Anyone can read the code, suggest changes or report bugs. Because many "
     "people review it, problems are often found quickly. Companies use open-source tools to save costs and avoid being locked to one vendor, "
     "and many pay their engineers to contribute back.",
     [("What is true of open-source software?", "Anyone can read the code", "Only one company can change it", "It must be bought", "Its code is secret"),
      ("Why are problems often found quickly?", "Many people review the code", "It has no bugs", "Vendors fix them", "It is never used"),
      ("Companies use open-source tools to", "save costs and avoid vendor lock-in", "increase costs", "hide their code", "stop hiring engineers"),
      ("‘Contribute back’ most likely means", "add improvements to the shared project", "return the software", "pay a licence fee", "remove features")]),
    ("Public libraries are changing. Besides books, many now offer free internet, study spaces and workshops on coding or job skills. For "
     "students who lack a quiet place at home, the library becomes a second classroom. Libraries that adapted in this way have seen visitor "
     "numbers rise even as reading habits shift online.",
     [("Besides books, many libraries now offer", "free internet and workshops", "paid movies", "only magazines", "restaurant services"),
      ("For some students the library becomes", "a second classroom", "a sports ground", "a market", "a hostel"),
      ("What happened to libraries that adapted?", "Visitor numbers rose", "They closed", "They stopped lending books", "They became private"),
      ("The passage suggests that reading habits are", "shifting online", "disappearing completely", "unchanged", "moving to radio")]),
]

IDIOMS = [
    ("break the ice", "start a conversation in an awkward situation", "break something valuable", "end a friendship", "feel very cold"),
    ("a blessing in disguise", "something good that first seemed bad", "a hidden enemy", "a costly gift", "a religious ceremony"),
    ("hit the nail on the head", "describe exactly what is causing a problem", "work as a carpenter", "hurt someone badly", "make a loud noise"),
    ("once in a blue moon", "very rarely", "every month", "at night", "during festivals"),
    ("cost an arm and a leg", "be very expensive", "cause an injury", "be cheap", "need hard work"),
    ("bite off more than you can chew", "take on more than you can handle", "eat too quickly", "speak rudely", "save food for later"),
    ("let the cat out of the bag", "reveal a secret", "free an animal", "make a mistake in shopping", "go on a trip"),
    ("burn the midnight oil", "work late into the night", "waste fuel", "cook dinner late", "light a lamp for worship"),
    ("under the weather", "slightly ill", "out in the rain", "very happy", "worried about the news"),
    ("on the same page", "in agreement", "reading together", "at the same level of a building", "equally rich"),
    ("cut corners", "do something cheaply or carelessly", "take a shortcut while walking", "decorate a room", "cut paper neatly"),
    ("in hot water", "in trouble", "taking a bath", "very busy", "very successful"),
    ("spill the beans", "reveal secret information", "waste food", "cook carelessly", "be clumsy"),
    ("a piece of cake", "something very easy", "a dessert", "a small share", "a reward"),
    ("throw in the towel", "give up", "clean the house", "start a fight", "go swimming"),
    ("see eye to eye", "agree fully", "stare at someone", "be of the same height", "have good eyesight"),
    ("the ball is in your court", "it is your turn to act", "you have won the match", "you are the referee", "you must leave"),
    ("keep your chin up", "stay positive in difficulty", "look at the sky", "be proud", "sit straight"),
    ("go the extra mile", "make more effort than expected", "travel far", "walk slowly", "change jobs"),
    ("miss the boat", "lose an opportunity", "arrive late at a port", "fail a swimming test", "forget a name"),
    ("pull someone's leg", "joke with someone", "injure someone", "help someone walk", "argue with someone"),
    ("call it a day", "stop working for the day", "name a holiday", "make a phone call", "start early"),
    ("back to square one", "back to the beginning", "a geometry problem", "a winning position", "a new house"),
    ("hit the sack", "go to bed", "lose a job", "go shopping", "punch hard"),
    ("a drop in the ocean", "a very small amount compared to what is needed", "a heavy rain", "a sea voyage", "a big mistake"),
    ("by the skin of your teeth", "just barely", "with great pain", "with a big smile", "very slowly"),
]

OWS = [
    ("A person who can speak many languages", "polyglot", "philatelist", "orator", "librarian"),
    ("A list of items to be discussed at a meeting", "agenda", "minutes", "memo", "itinerary"),
    ("A place where government records are kept", "archives", "museum", "library", "treasury"),
    ("One who is new to a profession", "novice", "veteran", "expert", "mentor"),
    ("A speech made without preparation", "extempore", "monologue", "lecture", "sermon"),
    ("That which cannot be read", "illegible", "invisible", "inaudible", "incredible"),
    ("A person who looks on the bright side", "optimist", "pessimist", "realist", "cynic"),
    ("Fear of heights", "acrophobia", "claustrophobia", "hydrophobia", "xenophobia"),
    ("A government by the people", "democracy", "monarchy", "autocracy", "oligarchy"),
    ("A person who does not believe in God", "atheist", "theist", "pilgrim", "priest"),
    ("Something that cannot be avoided", "inevitable", "invincible", "infallible", "invaluable"),
    ("A record of one's life written by oneself", "autobiography", "biography", "anthology", "diary"),
    ("A person who leaves his country to settle in another", "emigrant", "immigrant", "tourist", "native"),
    ("One who works for no payment", "volunteer", "mercenary", "employee", "contractor"),
    ("A sound that cannot be heard", "inaudible", "illegible", "inedible", "invisible"),
    ("A word that means the opposite of another", "antonym", "synonym", "homonym", "acronym"),
    ("The study of the stars and planets", "astronomy", "astrology", "geology", "botany"),
    ("A disease that spreads by contact", "contagious", "chronic", "congenital", "fatal"),
    ("Killing of a king", "regicide", "homicide", "genocide", "suicide"),
    ("Medicine that kills germs", "antiseptic", "antidote", "sedative", "vaccine"),
    ("A person who is easily deceived", "gullible", "cynical", "sceptical", "vigilant"),
    ("Something that can be carried easily", "portable", "potable", "palpable", "durable"),
    ("A person who has retired from work", "pensioner", "apprentice", "freelancer", "trainee"),
    ("An animal that eats only plants", "herbivore", "carnivore", "omnivore", "insectivore"),
    ("A period of ten years", "decade", "century", "millennium", "jubilee"),
    ("A person who spends very little", "miser", "spendthrift", "philanthropist", "donor"),
]


def build_pool(rng):
    """Every verbal question this bank can make, grouped by kind (each kind shuffled)."""
    pool = {k: [] for k in ("synonym", "antonym", "fill", "error", "improve", "jumble", "rc", "idiom", "ows")}
    for w, syn, ant, d1, d2, d3 in SYN_ANT:
        pool["synonym"].append(fixed(f"Choose the word closest in meaning to ‘{w.upper()}’.", [syn, d1, d2, d3], 0,
                                     f"‘{w}’ means {syn}.", rng))
        pool["antonym"].append(fixed(f"Choose the word most nearly OPPOSITE in meaning to ‘{w.upper()}’.", [ant, syn, d1, d2], 0,
                                     f"‘{w}’ means {syn}; its opposite is ‘{ant}’.", rng))
    for s, c, a, b, d in FILL:
        pool["fill"].append(fixed(f"Fill in the blank: {s}", [c, a, b, d], 0, f"Correct: {s.replace('____', c)}", rng))
    for parts, wrong, fix in ERRORS:
        labels = [f"({chr(65 + i)}) {p}" for i, p in enumerate(parts)]
        text = "Spot the part of the sentence that has an error:\n" + " ".join(labels)
        opts = [f"Part {chr(65 + i)}" for i in range(4)] + ["No error"]
        pool["error"].append(fixed(text, opts, wrong, fix))
    for s, best, a, b, c in IMPROVE:
        pool["improve"].append(fixed(f"Choose the best replacement for the part in brackets: {s}", [best, a, b, c], 0,
                                     f"Correct: {s.replace(s[s.index('['):s.index(']') + 1], best)}", None))
    for sentences in JUMBLES:
        labels = "PQRS"
        order = list(range(4))
        while order == [0, 1, 2, 3]:
            rng.shuffle(order)  # order[i] = which sentence is shown as label i; never already in order
        shown = "\n".join(f"{labels[i]}: {sentences[order[i]]}" for i in range(4))
        correct = "".join(labels[order.index(k)] for k in range(4))
        wrong = set()
        while len(wrong) < 3:
            p = list(correct)
            rng.shuffle(p)
            p = "".join(p)
            if p != correct:
                wrong.add(p)
        pool["jumble"].append(fixed(f"Arrange the sentences in the correct order:\n{shown}", [correct] + sorted(wrong), 0,
                                    "Correct order: " + " ".join(sentences), rng))
    for passage, qs in PASSAGES:
        for q, c, a, b, d in qs:
            pool["rc"].append(fixed(f"Read the passage and answer.\n\n{passage}\n\n{q}", [c, a, b, d], 0, f"The passage supports: {c}.", rng))
    for idiom, meaning, a, b, c in IDIOMS:
        pool["idiom"].append(fixed(f"What does the idiom ‘{idiom}’ mean?", [meaning, a, b, c], 0, f"‘{idiom}’ means: {meaning}.", rng))
    for phrase, word, a, b, c in OWS:
        pool["ows"].append(fixed(f"One word for: {phrase}", [word, a, b, c], 0, f"{phrase}: {word}.", rng))
    for v in pool.values():
        rng.shuffle(v)
    return pool
