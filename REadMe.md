# Getting Started

### Reference Documentation For the Delta Neutral Strategy 

Delta Neutral: 

every new Strategy has to implement the interface StrategiesImplementation
interface has three fallowing methods

```

    Signal runStrategy(Strategy strategy);
    
    void exitStrategy(Strategy strategy);
    
    void check(Strategy strategy);
    
```

# runStrategy 

In the strategy class which we implement the StrategiesImplementation can have the core logic of the implementation of the strategy 

# check 

In the check method is about the exit rules of the defined strategy 
if the rules has passed then we trigger the  `exitStrategy` of the same class 

# exitStrategy 
Which can exit the strategy


# core expectation 
When strategy is finally created the strategy Positions system is accepting the List of  Object of the `SignalMapperDto` 
