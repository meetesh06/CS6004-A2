### About

Assignment using SOOT to perform intraprocedural escape analysis.
It implements classes to generate points to graphs using Object Allocation Site abstraction. 
It also implements a class to save the state the points to graphs for easier debugging purposes.

Submitted to Dr. Manas Thakur @ IITB as a part of Assignment 2, CS6004, Spring 24.
Made public on 18th March 2024.

#### Using the debugging classes

The state recorder basically uses graphviz to convert DOT files into PNG and then embeds them into an HTML.

```
dot -Tpng dotFile.DOT -O
```

This package can be installed on ubuntu via apt. 

```bash
sudo apt install graphviz
```

### Kick the Tires

Modify the exec.sh to point the JAVA path to your JDK 18 download.

```bash
bash exec.sh
```

After executing this command you will find a `recording` folder. 
You can view the Escape Analysis results by opening `recording/index.html`.

Demo: [See here](https://meetesh06.github.io/CS6004_mee_reference/)

## AnalysisTransformer.java

#### Class PointsToAnalysis

This class contains the point to analysis function, interesting functions are:

1. **doAnalysis** : This function iterates over the control flow graph and implements a simple worklist based fixed point analysis.
1. **flowFunction** : This if the flow function that handles all the different Jimple statements.


## PointsToGraph.java

#### Class PointsToGraph

This class contains the implementation of the PTG.

```java
// 
// Important fields
// 
private HashMap<String, HashMap<String, Set<String>>> heap; // Heap mapping
// 
// HeapObj -> { field1: {HeapObj...}, field2: {HeapObj...} ... }
// 
private HashMap<String, Set<String>> stack;                 // Stack mapping
//
// StackVar -> { HeapObj... }
//

Set<String> objectsToMark;                                  // Nodes to mark in the output
// 
// Objects/StackVars inside this set will be marked red,  
// 

public void computeClosure()
// 
// This function recursively adds all reachable objects from objectsToMark into objectsToMark.
// 

private void markRecursively(String marked)
// 
// Reachability starting from 'marked' (marked can be a stackVar or HeapObj)
// 

public boolean equals(PointsToGraph other)
// 
// Compare two graphs for equality
// 

public PointsToGraph clone()
// 
// Returns a clone of the current graph; it creates a deep copy (i.e. modifying the clone does not affect the original graph)
// 

public void add(PointsToGraph other)
// 
// Take a union of two graphs. THIS_GRAPH = THIS_GRAPH + OTHER
// 

public void savePTGToPath(String path)
// 
// Generates a viewable graphviz DOT file and saves it to the given 'path'
// 
```

## PointRecorder.java

#### Class PointRecorder

This class allows users to generate viewable recording sessions of the PTG as seen here [See here](https://meetesh06.github.io/CS6004_mee_reference/)

```java

public PointRecorder(String path, PatchingChain<Unit> units, String name)
//
// The constructor takes the path where the recording should be saved, a Jimple-Unit Chain and name of the function
// 

public void recordState(Unit unitToHighlight, DOTable dotable)
// 
// Takes a unit to highlight and an Object implementing the DOTable inferface (in this case the PointsToGraph) and saves the state of the graph as a viewable HTML file.
// 

public void save(Set<String> finalResult)
// 
// Generates the index page for a function (should be called when analysis for a function has been completed)
// 

public static void generateIndexPage(HashMap<String, String> finalMap)
// 
// Generate the final index file for easily viewing all the states (should be called when all the analysis is completed; calling it multiple times is safe)
// The 'finalMap' is a mapping from 'functionName' to anything (I use it to show the result).
// 

```
