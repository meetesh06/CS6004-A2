import java.util.*;

import java_cup.assoc;
import soot.*;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.NullConstant;
import soot.jimple.ParameterRef;
import soot.jimple.StaticFieldRef;
import soot.jimple.ThisRef;
import soot.jimple.internal.JAddExpr;
import soot.jimple.internal.JArrayRef;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JDivExpr;
import soot.jimple.internal.JGotoStmt;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JIfStmt;
import soot.jimple.internal.JInstanceFieldRef;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JLengthExpr;
import soot.jimple.internal.JMulExpr;
import soot.jimple.internal.JNewArrayExpr;
import soot.jimple.internal.JNewExpr;
import soot.jimple.internal.JReturnStmt;
import soot.jimple.internal.JReturnVoidStmt;
import soot.jimple.internal.JSpecialInvokeExpr;
import soot.jimple.internal.JStaticInvokeExpr;
import soot.jimple.internal.JSubExpr;
import soot.jimple.internal.JVirtualInvokeExpr;
import soot.jimple.internal.JimpleLocal;
import soot.toolkits.graph.ClassicCompleteUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

interface DOTable {
    public void savePTGToPath(String path) throws IOException;
}

class PointRecorder {
    PatchingChain<Unit> units;
    String path;
    List<String> recorded;
    int count = 0;
    String name;

    public PointRecorder(String path, PatchingChain<Unit> units, String name) throws Exception {
        this.units = units;
        this.path = path;
        this.recorded = new ArrayList<>();
        this.name = name;

        Files.createDirectories(Paths.get(path));
        Path dirPath = Paths.get(path);
        if (!Files.exists(dirPath)) {
            Files.createDirectory(dirPath);
        }
    }

    public void save(Set<String> finalResult) throws Exception {
        String htmlPath = path + "/index.html";
        BufferedWriter writer = new BufferedWriter(new FileWriter(htmlPath));
        // Add html header
        writer.write("<!doctype html>\n<html lang=\"en\">\n");
        writer.write("<head>\n");

        writer.write("  <meta charset=\"utf-8\">");
        writer.write("  <title>Recorder session navigator</title>");

        writer.write("  <style>");

        writer.write(
                "  .slider{-webkit-appearance:none;width:100%;height:15px;border-radius:10px;background:#d3d3d3;outline:none;opacity:.7;-webkit-transition:.2s;transition:opacity .2s}.slider:hover{opacity:1}.slider::-webkit-slider-thumb{-webkit-appearance:none;appearance:none;width:25px;height:25px;border-radius:50%;background:#4CAF50;cursor:pointer}.slider::-moz-range-thumb{width:25px;height:25px;border-radius:50%;background:#4CAF50;cursor:pointer}");

        writer.write("  </style>");

        writer.write("  <script>");

        writer.write("    function updateSlider(slideAmount) {\n");
        writer.write("      console.log(\"updateSlider called\");");
        writer.write("      document.getElementById('AppFrame').setAttribute(\"src\",`" + "stateAt_${slideAmount}.html"
                + "`);\n");

        writer.write("    }\n");

        writer.write("  </script>");

        writer.write("</head>\n");

        writer.write("<body>\n");

        writer.write("  <p style=\"font-family: monospace; margin-bottom: 15px;\">\n");
        writer.write("  The recorder session recorded <b>" + count + "</b> distinct states.\n");
        writer.write(getEscapingObjects(finalResult));
        writer.write("  </p>\n");

        writer.write("  <input class=\"slider\" autofocus width=\"\" id=\"slide\" type=\"range\" min=\"1\" max=\""
                + count + "\" step=\"1\" value=\"1\" onInput=\"updateSlider(this.value)\">\n");
        writer.write(
                "  <iframe frameBorder=\"0\" id=\"AppFrame\" src=\"stateAt_1.html\" name=\"iframe_a\" style=\"height: 100vh;\" height=\"100vh\" width=\"100%\" title=\"State\"></iframe>\n");

        writer.write("</body>\n");

        writer.close();
    }

    String getEscapingObjects(Set<String> old) {
        ArrayList<Integer> escapingObjs = new ArrayList<>();

        old.forEach(s -> {
            try {
                if (s.charAt(0) != 'O') return;
                escapingObjs.add(Integer.parseInt(s.substring(1)));
            } catch (Exception e) {

            }
        });

        escapingObjs.sort((a, b) -> a - b);
        StringBuilder sb = new StringBuilder();
        for (Integer i : escapingObjs) {
            sb.append(i + " ");
        }
        return sb.toString();
    }

    public void recordState(Unit unitToHighlight, DOTable dotable) throws Exception {
        count++;
        String statePrefix = "stateAt_" + count;
        String htmlPath = path + "/" + statePrefix + ".html";
        String pngPath = path + "/" + "stateAt_" + count + ".DOT" + ".png";
        String dotPath = path + "/" + "stateAt_" + count + ".DOT";

        BufferedWriter writer = new BufferedWriter(new FileWriter(htmlPath));

        // Add html header
        writer.write("<!doctype html>\n<html lang=\"en\">\n");
        writer.write("<head>\n");

        writer.write("<meta charset=\"utf-8\">");
        writer.write("<title>" + statePrefix + "(" + count + ")" + "</title>");

        writer.write("<style>");

        writer.write(".mainContainer {display: flex;}\n");
        writer.write(".codeContainer{ background-color: lavender; padding: 10px; border-radius: 5px; }\n");
        writer.write(".selectedInstruction{ font-weight: bold; color: red; }\n");
        writer.write(".methodName { font-family: monospace; }\n");
        writer.write("code { display: block; white-space: pre-wrap }\n");

        writer.write("</style>");

        writer.write("</head>\n");

        writer.write("<body>\n");

        writer.write("<h1 class=\"methodName\"> " + name + "@" + count + " </h1>\n");

        if (dotable instanceof PointsToGraph) {
            writer.write("<h5> " + "escaping: " + getEscapingObjects(((PointsToGraph)dotable).objectsToMark) + " </h5>\n");
    
        }

        this.recorded.add(htmlPath);

        dotable.savePTGToPath(dotPath);

        Runtime.getRuntime().exec("dot -Tpng " + dotPath + " -O");

        writer.write(" <div class=\"mainContainer\">\n");

        writer.write("   <code class=\"codeContainer\">\n");
        // At a given time stamp, generate a page with unit highlighted and record the
        // event.
        for (Unit u : units) {
            String instr = u.getJavaSourceStartLineNumber() + " : "
                    + u.toString().replaceAll("\\<", "|").replaceAll("\\>", "|");
            if (u == unitToHighlight) {
                if (u instanceof JAssignStmt) {
                    writer.write("      <div class=\"selectedInstruction\">" + instr + " </br>"
                            + "<span style=\"color: cornflowerblue;\">->" + u.getClass() + " {Left: "
                            + ((JAssignStmt) u).leftBox.getValue().getClass() + ", Right: "
                            + ((JAssignStmt) u).rightBox.getValue().getClass() + "}" + "</span>" + "</div>\n");
                } else {
                    writer.write("      <div class=\"selectedInstruction\">" + instr + " -> "
                            + "<span style=\"color: cornflowerblue;\">" + u.getClass() + "</span>" + "</div>\n");
                }
            } else {
                writer.write("      " + instr + "\n");
            }
        }
        writer.write("   </code>\n"); // code container end

        // Add link to image
        writer.write("   <div class=\"imageContainer\">\n");
        // <img src="img_girl.jpg" alt="Girl in a jacket">
        writer.write("      <img src=\"" + pngPath + "\" alt=\"PTG " + statePrefix + "\">");

        writer.write("   </div>\n"); // image container end

        writer.write(" </div>\n"); // main container end

        writer.write("</body>\n");
        writer.close();

    }
}

class PointsToGraph implements DOTable {

    private HashMap<String, HashMap<String, Set<String>>> heap; // Heap mapping
    private HashMap<String, Set<String>> stack; // Stack mapping

    Set<String> objectsToMark; // Nodes to mark in the output

    final String GLOBAL_SYM = "\"@global\"";
    final String NULL_SYM = "\"@null\"";
    
    private void markRecursively(String marked) {
        if (objectsToMark.contains(marked))
            return;
        objectsToMark.add(marked);
        if (heap.containsKey(marked)) {
            for (String field : heap.get(marked).keySet()) {
                heap.get(marked).get(field).forEach((String o) -> markRecursively(o));
            }
        } else if (stack.containsKey(marked)) {
            stack.get(marked).forEach((String o) -> markRecursively(o));
        } else {
            assert (false);
        }
    }

    public void computeClosure() {
        Set<String> objectsToMarkCopy = new HashSet<>();
        objectsToMarkCopy.addAll(objectsToMark);
        objectsToMark.clear();
        objectsToMarkCopy.forEach((String o) -> markRecursively(o));
    }

    public PointsToGraph clone() {
        PointsToGraph clone = new PointsToGraph();
        for (String heapObj : heap.keySet()) {
            clone.heap.put(heapObj, new HashMap<>());
            for (String field : heap.get(heapObj).keySet()) {
                clone.heap.get(heapObj).put(field, new HashSet<>());
                clone.heap.get(heapObj).get(field).addAll(heap.get(heapObj).get(field));
            }
        }
        for (String stackVar : stack.keySet()) {
            clone.stack.put(stackVar, new HashSet<>());
            clone.stack.get(stackVar).addAll(stack.get(stackVar));
        }
        clone.objectsToMark.addAll(objectsToMark);

        return clone;
    }

    public boolean equals(PointsToGraph other) {
        if (!stack.keySet().equals(other.stack.keySet()))
            return false;
        if (!heap.keySet().equals(other.heap.keySet()))
            return false;

        // Compare stack
        for (String stackVar : stack.keySet()) {
            if (!stack.get(stackVar).equals(other.stack.get(stackVar)))
                return false;
        }

        // Compare heap
        for (String heapObj : heap.keySet()) {
            HashMap<String, Set<String>> fieldMap1 = heap.get(heapObj);
            HashMap<String, Set<String>> fieldMap2 = other.heap.get(heapObj);
            if (!fieldMap1.keySet().equals(fieldMap2.keySet()))
                return false;
            for (String field : fieldMap1.keySet()) {
                if (!fieldMap1.get(field).equals(fieldMap2.get(field)))
                    return false;
            }
        }

        // Compare marked
        if (!objectsToMark.equals(other.objectsToMark))
            return false;
        return true;
    }

    public void add(PointsToGraph other) {
        for (String heapObj : other.heap.keySet()) {
            if (!heap.containsKey(heapObj))
                heap.put(heapObj, new HashMap<>());
            for (String field : other.heap.get(heapObj).keySet()) {
                if (!heap.get(heapObj).containsKey(field))
                    heap.get(heapObj).put(field, new HashSet<>());
                heap.get(heapObj).get(field).addAll(other.heap.get(heapObj).get(field));
            }
        }
        for (String stackVar : other.stack.keySet()) {
            if (!stack.containsKey(stackVar))
                stack.put(stackVar, new HashSet<>());
            stack.get(stackVar).addAll(other.stack.get(stackVar));
        }

        objectsToMark.addAll(other.objectsToMark);

    }

    public PointsToGraph() {
        heap = new HashMap<>();
        stack = new HashMap<>();
        objectsToMark = new HashSet<>();
    }

    // a = new A(); Simple New
    public void handleSimpleNewStatement(String stackVar, String heapObj) {
        if (!stack.containsKey(stackVar))
            stack.put(stackVar, new HashSet<>());
        if (!heap.containsKey(heapObj))
            heap.put(heapObj, new HashMap<>());

        stack.get(stackVar).clear();
        stack.get(stackVar).add(heapObj);
    }

    // a = null
    public void handleSimpleNULLStatement(String stackVar) {
        if (!stack.containsKey(stackVar))
            stack.put(stackVar, new HashSet<>());
        if (!heap.containsKey(NULL_SYM))
            heap.put(NULL_SYM, new HashMap<>());

        stack.get(stackVar).clear();
        stack.get(stackVar).add(NULL_SYM);
    }

    // a = b; Copy statement
    public void handleCopyStatement(String stackVar1, String stackVar2) {
        if (!stack.containsKey(stackVar1))
            stack.put(stackVar1, new HashSet<>());
        if (!stack.containsKey(stackVar2))
            stack.put(stackVar2, new HashSet<>());

        // For all objects in the heap b points to, a will point to now.
        stack.get(stackVar1).clear();
        for (String heapObj : stack.get(stackVar2)) {
            stack.get(stackVar1).add(heapObj);
        }
    }

    // global = a
    public void handleAssignmentToGlobal(String stackVar1, String stackVar2) {
        if (!stack.containsKey(stackVar1)) {
            if (!heap.containsKey(GLOBAL_SYM)) {
                heap.put(GLOBAL_SYM, new HashMap<>());
                objectsToMark.add(GLOBAL_SYM);
            }
            stack.put(stackVar1, new HashSet<>());
            stack.get(stackVar1).add(GLOBAL_SYM);
            // Mark the object as escaping
            objectsToMark.add(stackVar1);
        }
        if (!stack.containsKey(stackVar2))
            stack.put(stackVar2, new HashSet<>());

        // For all objects in the heap b points to, a will point to now.
        stack.get(stackVar1).clear(); // we delibrately want to lose precision here
        for (String heapObj : stack.get(stackVar2)) {
            stack.get(stackVar1).add(heapObj);
        }
    }

    // a = global
    public void handleAssignmentFromGlobal(String stackVar1, String stackVar2) {
        if (!stack.containsKey(stackVar1)) {
            stack.put(stackVar1, new HashSet<>());
        }
        if (!stack.containsKey(stackVar2)) {
            if (!heap.containsKey(GLOBAL_SYM)) {
                heap.put(GLOBAL_SYM, new HashMap<>());
                objectsToMark.add(GLOBAL_SYM);
            }
            stack.put(stackVar2, new HashSet<>());
            stack.get(stackVar2).add(GLOBAL_SYM);
            // Mark the object as escaping
            objectsToMark.add(stackVar2);
        }

        stack.get(stackVar1).clear();
        for (String heapObj : stack.get(stackVar2)) {
            stack.get(stackVar1).add(heapObj);
        }
    }

    // a = b; Copy statement, handle without clear
    private void handleCopyConservative(String stackVar1, String stackVar2) {
        if (!stack.containsKey(stackVar1))
            stack.put(stackVar1, new HashSet<>());
        if (!stack.containsKey(stackVar2))
            stack.put(stackVar2, new HashSet<>());
        for (String heapObj : stack.get(stackVar2)) {
            stack.get(stackVar1).add(heapObj);
        }
    }

    private void anyFieldExcapes(String heapObj) {
        if (!heap.containsKey(heapObj))
            heap.put(heapObj, new HashMap<>());

        if (!heap.containsKey(GLOBAL_SYM)) {
            heap.put(GLOBAL_SYM, new HashMap<>());
            objectsToMark.add(GLOBAL_SYM);
        }        

        if (!heap.get(heapObj).containsKey("*"))
            heap.get(heapObj).put("*", new HashSet<>());

        heap.get(heapObj).get("*").add(GLOBAL_SYM);
    }

    private void anyFieldExcapesForStackVar(String stackVar) {
        if (!stack.containsKey(stackVar))
            stack.put(stackVar, new HashSet<>());

        for (String heapObj : stack.get(stackVar)) {
            anyFieldExcapes(heapObj);
        }
    }

    private void makeGlobalLinks(String stackVar) {
        if (!heap.containsKey(GLOBAL_SYM)) {
            heap.put(GLOBAL_SYM, new HashMap<>());
            objectsToMark.add(GLOBAL_SYM);
        }
        if (!heap.get(GLOBAL_SYM).containsKey("*"))
            heap.get(GLOBAL_SYM).put("*", new HashSet<>());
        heap.get(GLOBAL_SYM).get("*").addAll(stack.get(stackVar));
    }

    // a = <virtualInvoke>
    public void handleVirtualInvokeAssignmentStatement(String stackVar1, JVirtualInvokeExpr virtualInvoke) {
        if (!stack.containsKey(stackVar1))
            stack.put(stackVar1, new HashSet<>());
        if (!heap.containsKey(GLOBAL_SYM)) {
            heap.put(GLOBAL_SYM, new HashMap<>());
            objectsToMark.add(GLOBAL_SYM);
        }
        if (!heap.containsKey(NULL_SYM)) {
            heap.put(NULL_SYM, new HashMap<>());
        }

        handleVirtualInvoke(virtualInvoke);

        // It may point to some global or null
        stack.get(stackVar1).clear();
        stack.get(stackVar1).add(GLOBAL_SYM);
        stack.get(stackVar1).add(NULL_SYM);

        String receiverObj = ((JimpleLocal) virtualInvoke.getBase()).getName();

        handleCopyConservative(stackVar1, receiverObj);

        for (int i = 0; i < virtualInvoke.getArgCount(); i++) {
            Value vBox = virtualInvoke.getArgBox(i).getValue();
            if (vBox instanceof JimpleLocal) {
                handleCopyConservative(stackVar1, ((JimpleLocal) vBox).getName());
                makeGlobalLinks(((JimpleLocal) vBox).getName());
            }
        }

        // Any of these objects may be pointed to by global

        assert (stack.containsKey(receiverObj));

        makeGlobalLinks(receiverObj);
    }

    // a = <virtualInvoke>
    public void handleStaticInvokeAssignmentStatement(String stackVar1, JStaticInvokeExpr staticInvokeExpr) {
        if (!stack.containsKey(stackVar1))
            stack.put(stackVar1, new HashSet<>());
        if (!heap.containsKey(GLOBAL_SYM)) {
            heap.put(GLOBAL_SYM, new HashMap<>());
            objectsToMark.add(GLOBAL_SYM);
        }
        if (!heap.containsKey(NULL_SYM)) {
            heap.put(NULL_SYM, new HashMap<>());
        }

        handleStaticInvoke(staticInvokeExpr);

        // It may point to some global or null
        stack.get(stackVar1).clear();
        stack.get(stackVar1).add(GLOBAL_SYM);
        stack.get(stackVar1).add(NULL_SYM);

        for (int i = 0; i < staticInvokeExpr.getArgCount(); i++) {
            Value vBox = staticInvokeExpr.getArgBox(i).getValue();
            if (vBox instanceof JimpleLocal) {
                handleCopyConservative(stackVar1, ((JimpleLocal) vBox).getName());
                makeGlobalLinks(((JimpleLocal) vBox).getName());
            }
        }
    }

    // <virtualInvoke>
    public void handleVirtualInvoke(JVirtualInvokeExpr virtualInvoke) {
        String receiverObj = ((JimpleLocal) virtualInvoke.getBase()).getName();
        anyFieldExcapesForStackVar(receiverObj);
        makeGlobalLinks(receiverObj);
        for (int i = 0; i < virtualInvoke.getArgCount(); i++) {
            Value vBox = virtualInvoke.getArgBox(i).getValue();
            if (vBox instanceof JimpleLocal) {
                anyFieldExcapesForStackVar(((JimpleLocal) vBox).getName());
                makeGlobalLinks(((JimpleLocal) vBox).getName());
            }
        }

    }

    // <staticInvoke>
    public void handleStaticInvoke(JStaticInvokeExpr staticInvoke) {

        for (int i = 0; i < staticInvoke.getArgCount(); i++) {
            Value vBox = staticInvoke.getArgBox(i).getValue();
            if (vBox instanceof JimpleLocal) {
                anyFieldExcapesForStackVar(((JimpleLocal) vBox).getName());
                makeGlobalLinks(((JimpleLocal) vBox).getName());
            }
        }

    }

    // a = b.f; Load statement
    public void handleLoadStatement(String stackVar1, String stackVar2, String field) {
        if (!stack.containsKey(stackVar1))
            stack.put(stackVar1, new HashSet<>());
        if (!stack.containsKey(stackVar2))
            stack.put(stackVar2, new HashSet<>());

        Set<String> finalSetPointedToByBdotF = new HashSet<>();

        for (String heapObj : stack.get(stackVar2)) {
            // All additions to heap only happen when new assignment is called, it must
            // exist!
            assert (heap.containsKey(heapObj));

            // Any field may point to obj
            if (heap.get(heapObj).containsKey("*")) {
                finalSetPointedToByBdotF.addAll(heap.get(heapObj).get("*"));
            }

            // Add objects of {b1,b2,...}.f to resultset
            if (heap.get(heapObj).containsKey(field)) {
                Set<String> objsOfBdotF = heap.get(heapObj).get(field);
                finalSetPointedToByBdotF.addAll(objsOfBdotF);
            } else {
                if (!heap.containsKey(NULL_SYM)) {
                    heap.put(NULL_SYM, new HashMap<>());
                }
                heap.get(heapObj).put(field, new HashSet<>());
                heap.get(heapObj).get(field).add(NULL_SYM);
                finalSetPointedToByBdotF.add(NULL_SYM);
            }
        }
        // For all objects in the heap b points to, a will point to now.
        stack.get(stackVar1).clear();
        for (String heapObj : finalSetPointedToByBdotF) {
            stack.get(stackVar1).add(heapObj);
        }
    }

    // a.f = b; Store Statement
    public void handleStoreStatement(String stackVar1, String field, String stackVar2) {
        if (!stack.containsKey(stackVar1))
            stack.put(stackVar1, new HashSet<>());
        if (!stack.containsKey(stackVar2))
            stack.put(stackVar2, new HashSet<>());

        for (String heapObj : stack.get(stackVar1)) {
            // All additions to heap only happen when new assignment is called, it must
            // exist!
            assert (heap.containsKey(heapObj));
            if (!heap.get(heapObj).containsKey(field))
                heap.get(heapObj).put(field, new HashSet<>());
            heap.get(heapObj).get(field).addAll(stack.get(stackVar2));
            // Any field may point to obj
            if (heap.get(heapObj).containsKey("*")) {
                heap.get(heapObj).get("*").addAll(stack.get(stackVar2));
            }
        }
    }

    // a.f = null; Null Store Statement
    public void handleNULLStoreStatement(String stackVar1, String field) {
        if (!stack.containsKey(stackVar1))
            stack.put(stackVar1, new HashSet<>());

        if (!heap.containsKey(NULL_SYM))
            heap.put(NULL_SYM, new HashMap<>());

        for (String heapObj : stack.get(stackVar1)) {
            // All additions to heap only happen when new assignment is called, it must
            // exist!
            assert (heap.containsKey(heapObj));
            if (!heap.get(heapObj).containsKey(field))
                heap.get(heapObj).put(field, new HashSet<>());
            heap.get(heapObj).get(field).add(NULL_SYM);

            // Any field may point to obj
            if (heap.get(heapObj).containsKey("*")) {
                heap.get(heapObj).get("*").add(NULL_SYM);
            }
        }
    }

    public void savePTGToPath(String path) throws IOException {
        String rankDir = "LR";
        String fieldEdgeWeight = "0.2";

        BufferedWriter writer = new BufferedWriter(new FileWriter(path));

        writer.write("digraph sample {\n");
        writer.write("  rankDir=\"" + rankDir + "\";\n");

        writer.write("  subgraph cluster_0 {\n");
        writer.write("    label=\"Stack\"\n");
        writer.write("    ");
        for (String stackVar : stack.keySet()) {
            writer.write("\"" + stackVar + "\"" + "; ");
        }
        writer.write("\n");
        writer.write("  }\n");

        // a -- { o1, o2, o3 }
        for (String stackVar : stack.keySet()) {
            writer.write("  " + "\"" + stackVar + "\"");
            writer.write(" -> { ");

            for (String heapObj : stack.get(stackVar)) {
                // if (heapObj.contains("@")) {
                // writer.write(heapObj + "[shape=box, style=\"filled,dashed\"]");
                // } else {
                writer.write(heapObj + "[shape=box]");
                // }
            }
            writer.write("};\n");
        }

        for (String stackVar : stack.keySet()) {
            if (stackVar.contains("@")) {
                writer.write("  " + "\"" + stackVar + "\"" + "[style=\"filled,dashed\"]; \n");
            }
        }

        // o1 -> o2[label="f", weight="0.2"]
        for (String heapObj : heap.keySet()) {

            for (String field : heap.get(heapObj).keySet()) {
                for (String fieldObj : heap.get(heapObj).get(field)) {
                    writer.write("  " + heapObj + " -> " + fieldObj + "[label=\"" + field
                            + "\", weight=\"" + fieldEdgeWeight + "\"]\n");
                    // if (fieldObj.contains("@")) {
                    // writer.write(fieldObj + "[shape=box, style=\"filled,dashed\"] ");
                    // }
                }

            }

            if (heapObj.contains("@")) {
                writer.write("  " + heapObj + "[shape=box, style=\"filled,dashed\"];\n");
            }
        }

        for (String marked : objectsToMark) {
            writer.write("  " + marked + "[color=\"red\"];\n");
        }

        writer.write("}\n");
        writer.close();
    }
}

class PointsToAnalysis {
    PatchingChain<Unit> units;
    UnitGraph uGraph;
    String methodName;
    Set<String> analysisResult;

    PointsToAnalysis(Body body, String methodName) {
        this.uGraph = new ClassicCompleteUnitGraph(body);
        this.units = body.getUnits();
        this.methodName = methodName;
        analysisResult = new HashSet<>();
    }

    private void flowFunction(Unit u, PointsToGraph ptg) {
        if (u instanceof JAssignStmt) {
            JAssignStmt stmnt = (JAssignStmt) u;
            // a = new A()
            if (stmnt.leftBox.getValue() instanceof JimpleLocal && stmnt.rightBox.getValue() instanceof JNewExpr) {
                JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
                String heapObjName = "O" + u.getJavaSourceStartLineNumber();
                ptg.handleSimpleNewStatement(stackVal.getName(), heapObjName);
            }
            // a.f = b
            else if (stmnt.leftBox.getValue() instanceof JInstanceFieldRef
                    && stmnt.rightBox.getValue() instanceof JimpleLocal) {
                JInstanceFieldRef fieldref = (JInstanceFieldRef) stmnt.leftBox.getValue();
                JimpleLocal stackVal = (JimpleLocal) stmnt.rightBox.getValue();
                ptg.handleStoreStatement(fieldref.getBase().toString(), fieldref.getField().getName(),
                        stackVal.getName());
            }
            // a = b.f
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof JInstanceFieldRef) {
                JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
                JInstanceFieldRef fieldref = (JInstanceFieldRef) stmnt.rightBox.getValue();
                ptg.handleLoadStatement(stackVal.getName(), fieldref.getBase().toString(),
                        fieldref.getField().getName());
            }
            // a = b
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof JimpleLocal) {
                JimpleLocal stackVal1 = (JimpleLocal) stmnt.leftBox.getValue();
                JimpleLocal stackVal2 = (JimpleLocal) stmnt.rightBox.getValue();
                ptg.handleCopyStatement(stackVal1.getName(), stackVal2.getName());
            }
            // global = a
            else if (stmnt.leftBox.getValue() instanceof StaticFieldRef
                    && stmnt.rightBox.getValue() instanceof JimpleLocal) {
                StaticFieldRef staticFieldref = (StaticFieldRef) stmnt.leftBox.getValue();
                JimpleLocal stackVal = (JimpleLocal) stmnt.rightBox.getValue();

                ptg.handleAssignmentToGlobal(staticFieldref.getField().getName(), stackVal.getName());
                ptg.objectsToMark.add(staticFieldref.getField().getName());
            }
            // a = global
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof StaticFieldRef) {
                JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
                StaticFieldRef staticFieldref = (StaticFieldRef) stmnt.rightBox.getValue();

                ptg.handleAssignmentFromGlobal(stackVal.getName(), staticFieldref.getField().getName());
                ptg.objectsToMark.add(staticFieldref.getField().getName());
            }
            // a = lengthof b
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof JLengthExpr) {
                // Ignore
            }
            // a = null
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof NullConstant) {
                JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
                ptg.handleSimpleNULLStatement(stackVal.getName());
            }
            // a.f = null
            else if (stmnt.leftBox.getValue() instanceof JInstanceFieldRef
                    && stmnt.rightBox.getValue() instanceof NullConstant) {
                JInstanceFieldRef fieldref = (JInstanceFieldRef) stmnt.leftBox.getValue();
                ptg.handleNULLStoreStatement(fieldref.getBase().toString(), fieldref.getField().getName());
            }
            // a = <virtualInvoke>
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof JVirtualInvokeExpr) {
                JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
                JVirtualInvokeExpr virtualInvoke = (JVirtualInvokeExpr) stmnt.rightBox.getValue();
                ptg.handleVirtualInvokeAssignmentStatement(stackVal.getName(), virtualInvoke);
            }
            // a = new Array[]
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof JNewArrayExpr) {
                JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
                String heapObjName = "O" + u.getJavaSourceStartLineNumber();
                ptg.handleSimpleNewStatement(stackVal.getName(), heapObjName);
            }
            // a[] = int
            else if (stmnt.leftBox.getValue() instanceof JArrayRef
                    && stmnt.rightBox.getValue() instanceof IntConstant) {
                // ignore
            }
            // a = 10
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof IntConstant) {
                // ignore
            }
            // a = 1 + 6
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof JAddExpr) {
                // ignore
            }
            // a = 1 * 6
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof JMulExpr) {
                // ignore
            }
            // a = 1 / 6
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof JDivExpr) {
                // ignore
            }
            // a = 1 - 6
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof JSubExpr) {
                // ignore
            }
            // a = arr[10]
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof JArrayRef) {
                JimpleLocal stackVal1 = (JimpleLocal) stmnt.leftBox.getValue();
                JArrayRef arrayRef = (JArrayRef) stmnt.rightBox.getValue();
                ptg.handleLoadStatement(stackVal1.getName(), arrayRef.getBase().toString(), "*");
            } 
            // arr[10] = b
            else if (stmnt.leftBox.getValue() instanceof JArrayRef
                    && stmnt.rightBox.getValue() instanceof JimpleLocal) {
                JArrayRef fieldref = (JArrayRef) stmnt.leftBox.getValue();
                JimpleLocal stackVal = (JimpleLocal) stmnt.rightBox.getValue();
                ptg.handleStoreStatement(fieldref.getBase().toString(), "*",
                        stackVal.getName());
            }
            // r0.f = 10
            else if (stmnt.leftBox.getValue() instanceof JInstanceFieldRef
                    && stmnt.rightBox.getValue() instanceof IntConstant) {
            }
            // a = <static invoke>
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof JStaticInvokeExpr) {
                JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
                JStaticInvokeExpr staticInvokeExpr = (JStaticInvokeExpr) stmnt.rightBox.getValue();
                ptg.handleStaticInvokeAssignmentStatement(stackVal.getName(), staticInvokeExpr);
            }
            
            else {
                System.err.println("  UKNASSN--" + u);

                System.err.println("    Left: " + stmnt.leftBox.getValue().getClass() + ", Right: "
                        + stmnt.rightBox.getValue().getClass());
            }
        } else if (u instanceof JInvokeStmt) {
            JInvokeStmt invokeExpr = (JInvokeStmt) u;
            if (invokeExpr.getInvokeExpr() instanceof JSpecialInvokeExpr) {
                // Nothing to do here
            } else if (invokeExpr.getInvokeExpr() instanceof JVirtualInvokeExpr) {
                JVirtualInvokeExpr virtualInvoke = (JVirtualInvokeExpr) invokeExpr.getInvokeExpr();
                ptg.handleVirtualInvoke(virtualInvoke);
            } else if (invokeExpr.getInvokeExpr() instanceof JStaticInvokeExpr) {
                JStaticInvokeExpr staticInvoke = (JStaticInvokeExpr) invokeExpr.getInvokeExpr();
                ptg.handleStaticInvoke(staticInvoke);
            } else {
                System.out.println("UNHANDLED INVOKE STATEMENT");
            }
            // Ignore all invoke statements
        } else if (u instanceof JReturnStmt) {
            // Ignore all return statements
        } else if (u instanceof JReturnVoidStmt) {
            // Ignore return void statement
        } else if (u instanceof JIdentityStmt) {
            JIdentityStmt iden = (JIdentityStmt) u;
            if (iden.leftBox.getValue() instanceof JimpleLocal
                    && iden.rightBox.getValue() instanceof ParameterRef) {
                JimpleLocal stackVal = (JimpleLocal) iden.leftBox.getValue();
                ParameterRef paramref = (ParameterRef) iden.rightBox.getValue();
                String heapObjName = "\"@param" + paramref.getIndex() + "\"";
                ptg.handleSimpleNewStatement(stackVal.getName(), heapObjName);
                ptg.objectsToMark.add(heapObjName);
                ptg.objectsToMark.add(stackVal.getName());
            }
            // a = @thisptr
            else if (iden.leftBox.getValue() instanceof JimpleLocal
                    && iden.rightBox.getValue() instanceof ThisRef) {
                JimpleLocal stackVal = (JimpleLocal) iden.leftBox.getValue();
                ptg.handleSimpleNewStatement(stackVal.getName(), "\"@this\"");
                ptg.objectsToMark.add("\"@this\"");
            }

            else {
                System.err.println("  UKN--" + u);
                System.err.println("    Left: " + iden.leftBox.getValue().getClass() + ", Right: "
                        + iden.rightBox.getValue().getClass());
            }
        } else if (u instanceof JIfStmt || u instanceof JGotoStmt) {
            // Ignore all conditionals and jumps
        } else {
            System.err.println("UNHANDLED: " + u + " (" + u.getClass() + ")");
        }
    }

    public void doAnalysis() throws Exception {
        PointRecorder recorder = new PointRecorder(System.getProperty("user.dir") + "/recording/" + methodName, units,
                methodName);

        List<Unit> worklist = new ArrayList<>();
        HashMap<Unit, PointsToGraph> outSets = new HashMap<>();

        // Initialize flowvalues
        for (Unit u : units) {
            outSets.put(u, new PointsToGraph());
        }

        // First interation over the CFG, worklist initialization
        for (Unit currUnit : units) {
            PointsToGraph currentFlowSet = new PointsToGraph();
            PointsToGraph old = outSets.get(currUnit);

            // Check incoming edges
            for (Unit incoming : uGraph.getPredsOf(currUnit)) {
                currentFlowSet.add(outSets.get(incoming));
            }

            flowFunction(currUnit, currentFlowSet);
            currentFlowSet.computeClosure();
            recorder.recordState(currUnit, currentFlowSet); // Record new flow state

            // Add successors to worklist
            if (!old.equals(currentFlowSet)) {
                outSets.put(currUnit, currentFlowSet);
                worklist.addAll(uGraph.getSuccsOf(currUnit));
            }
        }

        while (!worklist.isEmpty()) {
            // Pop one unit from the worklist
            Unit currUnit = worklist.iterator().next();
            worklist.remove(currUnit);

            PointsToGraph old = outSets.get(currUnit);
            PointsToGraph currentFlowSet = new PointsToGraph();

            // Check incoming edges
            for (Unit incoming : uGraph.getPredsOf(currUnit)) {
                currentFlowSet.add(outSets.get(incoming));
            }

            flowFunction(currUnit, currentFlowSet);
            currentFlowSet.computeClosure();
            recorder.recordState(currUnit, currentFlowSet); // Record new flow state

            // Add successors to worklist
            if (!old.equals(currentFlowSet)) {
                outSets.put(currUnit, currentFlowSet);
                worklist.addAll(uGraph.getSuccsOf(currUnit));
            }

        }

        
        for (Unit currUnit : units) {
            analysisResult.addAll(outSets.get(currUnit).objectsToMark);
        }
        recorder.save(analysisResult);
    }
}

public class AnalysisTransformer extends BodyTransformer {
    ArrayList<String> finalResult = new ArrayList<String>();

    String getEscapingObjects(Set<String> old) {
        ArrayList<Integer> escapingObjs = new ArrayList<>();

        old.forEach(s -> {
            try {
                if (s.charAt(0) != 'O') return;
                escapingObjs.add(Integer.parseInt(s.substring(1)));
            } catch (Exception e) {

            }
        });

        escapingObjs.sort((a, b) -> a - b);
        StringBuilder sb = new StringBuilder();
        for (Integer i : escapingObjs) {
            sb.append(i + " ");
        }
        return sb.toString();
    }


    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
        // Construct CFG for the current method's body
        String methodName = body.getMethod().getName();
        PointsToAnalysis pta = new PointsToAnalysis(body, methodName);
        try {
            pta.doAnalysis();
            Set<String> result = new HashSet<>(pta.analysisResult);
            String escapingObjs = getEscapingObjects(result);
            finalResult.add(body.getMethod().getDeclaringClass().getName() + ":" + methodName + " " + escapingObjs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void printResult() {
        Collections.sort(finalResult);
        for (String r : finalResult) {
            System.out.println(r);
        }
    }

}
