import java.util.*;

import java_cup.assoc;
import soot.*;
import soot.JastAddJ.SwitchStmt;
import soot.jimple.Constant;
import soot.jimple.IfStmt;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.NullConstant;
import soot.jimple.ParameterRef;
import soot.jimple.StaticFieldRef;
import soot.jimple.ThisRef;
import soot.jimple.internal.JAddExpr;
import soot.jimple.internal.JArrayRef;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JBreakpointStmt;
import soot.jimple.internal.JCastExpr;
import soot.jimple.internal.JCaughtExceptionRef;
import soot.jimple.internal.JDivExpr;
import soot.jimple.internal.JDynamicInvokeExpr;
import soot.jimple.internal.JEnterMonitorStmt;
import soot.jimple.internal.JExitMonitorStmt;
import soot.jimple.internal.JGotoStmt;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JIfStmt;
import soot.jimple.internal.JInstanceFieldRef;
import soot.jimple.internal.JInterfaceInvokeExpr;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JLengthExpr;
import soot.jimple.internal.JLookupSwitchStmt;
import soot.jimple.internal.JMulExpr;
import soot.jimple.internal.JNewArrayExpr;
import soot.jimple.internal.JNewExpr;
import soot.jimple.internal.JNopStmt;
import soot.jimple.internal.JRetStmt;
import soot.jimple.internal.JReturnStmt;
import soot.jimple.internal.JReturnVoidStmt;
import soot.jimple.internal.JSpecialInvokeExpr;
import soot.jimple.internal.JStaticInvokeExpr;
import soot.jimple.internal.JSubExpr;
import soot.jimple.internal.JTableSwitchStmt;
import soot.jimple.internal.JThrowStmt;
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
        if (s.charAt(0) != 'O')
          return;
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
    String pngPath = "./" + "stateAt_" + count + ".DOT" + ".png";
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
      writer.write(
          "<h5> " + "escaping: " + getEscapingObjects(((PointsToGraph) dotable).objectsToMark) + " </h5>\n");

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
        } else if (u instanceof JReturnStmt) {
          writer.write("      <div class=\"selectedInstruction\">" + instr + " </br>"
              + "<span style=\"color: cornflowerblue;\">->" + u.getClass() + " {getOp: "
              + ((JReturnStmt) u).getOp().getClass() + "}" + "</span>" + "</div>\n");

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

  // Helpers *************************************************
  public void ensureGlobalVar(String globalVar) {
    if (!stack.containsKey(globalVar)) {
      ensureStackVar(globalVar);
      ensureHeapObj(GLOBAL_SYM);
      stackStrongUpdate(globalVar, GLOBAL_SYM);
      // Mark the global variable as escaping
      objectsToMark.add(globalVar);
      // Mark the global object as escaping
      objectsToMark.add(GLOBAL_SYM);
    }
  }

  public void clearStackObj(String stackVar) {
    ensureStackVar(stackVar);
    stack.get(stackVar).clear();
    ;

  }

  public void ensureStackVar(String localVar) {
    if (!stack.containsKey(localVar))
      stack.put(localVar, new HashSet<>());
  }

  public void ensureHeapObj(String heapObj) {
    if (!heap.containsKey(heapObj))
      heap.put(heapObj, new HashMap<>());
  }

  final String STAR_FIELD = "\"*\"";

  public void ensureField(String heapObj, String field) {
    ensureHeapObj(heapObj);
    if (!heap.get(heapObj).containsKey(field))
      heap.get(heapObj).put(field, new HashSet<>());
  }

  // a -> OBJ
  public void stackStrongUpdate(String stackVar, String heapObj) {
    ensureStackVar(stackVar);
    ensureHeapObj(heapObj);
    stack.get(stackVar).clear();
    stack.get(stackVar).add(heapObj);
  }

  // a +-> OBJ
  public void stackWeakUpdate(String stackVar, String heapObj) {
    ensureStackVar(stackVar);
    ensureHeapObj(heapObj);
    stack.get(stackVar).add(heapObj);
  }

  public void anyFieldExcapes(String heapObj) {
    ensureHeapObj(heapObj);
    ensureHeapObj(GLOBAL_SYM);
    objectsToMark.add(GLOBAL_SYM);
    ensureField(heapObj, STAR_FIELD);
    heap.get(heapObj).get(STAR_FIELD).add(GLOBAL_SYM);
  }

  public void anyFieldExcapesForStackVar(String stackVar) {
    ensureStackVar(stackVar);
    for (String heapObj : stack.get(stackVar)) {
      anyFieldExcapes(heapObj);
    }
  }

  public void makeGlobalLinks(String stackVar) {
    ensureStackVar(stackVar);
    ensureHeapObj(GLOBAL_SYM);
    ensureField(GLOBAL_SYM, STAR_FIELD);
    heap.get(GLOBAL_SYM).get(STAR_FIELD).addAll(stack.get(stackVar));
  }

  // ********************************************************

  // a = new A(); Simple New
  public void handleSimpleNewStatement(String stackVar, String heapObj) {
    stackStrongUpdate(stackVar, heapObj);
  }

  // a = null
  public void handleSimpleNULLStatement(String stackVar) {
    stackStrongUpdate(stackVar, NULL_SYM);
  }

  // a = b; Copy statement (strong)
  public void handleCopyStatement(String stackVar1, String stackVar2) {
    ensureStackVar(stackVar1);
    ensureStackVar(stackVar2);
    stack.get(stackVar1).clear();
    stack.get(stackVar1).addAll(stack.get(stackVar2));
  }

  // a = b; Copy statement (weak)
  public void handleCopyStatementWeak(String stackVar1, String stackVar2) {
    ensureStackVar(stackVar1);
    ensureStackVar(stackVar2);

    for (String heapObj : stack.get(stackVar2)) {
      stack.get(stackVar1).add(heapObj);
    }
  }

  // global = a
  public void handleAssignmentToGlobal(String globalVar, String localVar) {
    ensureGlobalVar(globalVar);
    ensureStackVar(localVar);
    stack.get(globalVar).clear();
    stack.get(globalVar).addAll(stack.get(localVar));
  }

  // a = global
  public void handleAssignmentFromGlobal(String stackVar1, String stackVar2) {
    ensureStackVar(stackVar1);
    ensureGlobalVar(stackVar2);
    stack.get(stackVar1).clear();
    stack.get(stackVar1).addAll(stack.get(stackVar2));
  }

  // // a = <virtualInvoke>
  // public void handleVirtualInvokeAssignmentStatement(String stackVar1,
  // JVirtualInvokeExpr virtualInvoke) {
  // ensureStackVar(stackVar1);
  // ensureHeapObj(GLOBAL_SYM);
  // objectsToMark.add(GLOBAL_SYM);

  // ensureHeapObj(NULL_SYM);
  // handleVirtualInvoke(virtualInvoke);

  // // It may point to some global or null
  // stack.get(stackVar1).clear();
  // stack.get(stackVar1).add(GLOBAL_SYM);
  // stack.get(stackVar1).add(NULL_SYM);

  // String receiverObj = ((JimpleLocal) virtualInvoke.getBase()).getName();

  // handleCopyStatementWeak(stackVar1, receiverObj);

  // for (int i = 0; i < virtualInvoke.getArgCount(); i++) {
  // Value vBox = virtualInvoke.getArgBox(i).getValue();
  // if (vBox instanceof JimpleLocal) {
  // handleCopyStatementWeak(stackVar1, ((JimpleLocal) vBox).getName());
  // makeGlobalLinks(((JimpleLocal) vBox).getName());
  // }
  // }

  // // Any of these objects may be pointed to by global

  // assert (stack.containsKey(receiverObj));

  // makeGlobalLinks(receiverObj);
  // }

  // // a = <virtualInvoke>
  // public void handleStaticInvokeAssignmentStatement(String stackVar1,
  // JStaticInvokeExpr staticInvokeExpr) {
  // if (!stack.containsKey(stackVar1))
  // stack.put(stackVar1, new HashSet<>());
  // if (!heap.containsKey(GLOBAL_SYM)) {
  // heap.put(GLOBAL_SYM, new HashMap<>());
  // objectsToMark.add(GLOBAL_SYM);
  // }
  // if (!heap.containsKey(NULL_SYM)) {
  // heap.put(NULL_SYM, new HashMap<>());
  // }

  // handleStaticInvoke(staticInvokeExpr);

  // // It may point to some global or null
  // stack.get(stackVar1).clear();
  // stack.get(stackVar1).add(GLOBAL_SYM);
  // stack.get(stackVar1).add(NULL_SYM);

  // for (int i = 0; i < staticInvokeExpr.getArgCount(); i++) {
  // Value vBox = staticInvokeExpr.getArgBox(i).getValue();
  // if (vBox instanceof JimpleLocal) {
  // handleCopyStatementWeak(stackVar1, ((JimpleLocal) vBox).getName());
  // makeGlobalLinks(((JimpleLocal) vBox).getName());
  // }
  // }
  // }

  // a.f = b; Store Statement
  public void handleStoreStatement(String stackVar1, String field, String stackVar2) {
    ensureStackVar(stackVar1);
    ensureStackVar(stackVar2);
    for (String heapObj : stack.get(stackVar1)) {
      ensureField(heapObj, field);
      heap.get(heapObj).get(field).addAll(stack.get(stackVar2));
      if (heap.get(heapObj).containsKey(STAR_FIELD)) {
        heap.get(heapObj).get(STAR_FIELD).addAll(stack.get(stackVar2));
      }
    }
  }

  // a = b.f; Load statement
  public void handleLoadStatement(String stackVar1, String stackVar2, String field) {
    ensureStackVar(stackVar1);
    ensureStackVar(stackVar2);

    stack.get(stackVar1).clear();
    for (String heapObj : stack.get(stackVar2)) {
      ensureHeapObj(heapObj);
      ensureField(heapObj, STAR_FIELD);

      // Any field {'f', 'g'...} may also point to '*' -> {o1, o2...}
      stack.get(stackVar1).addAll(heap.get(heapObj).get(STAR_FIELD));

      // If obj does not contain the field, then create a NULL obj
      if (!heap.get(heapObj).containsKey(field)) {
        ensureHeapObj(NULL_SYM);
        ensureField(heapObj, field);
        heap.get(heapObj).get(field).add(NULL_SYM);
      }

      stack.get(stackVar1).addAll(heap.get(heapObj).get(field));
    }
  }

  // a.f = null; Null Store Statement
  public void handleNULLStoreStatement(String stackVar1, String field) {
    ensureStackVar(stackVar1);
    ensureHeapObj(NULL_SYM);

    for (String heapObj : stack.get(stackVar1)) {
      ensureHeapObj(heapObj);
      ensureField(heapObj, field);
      heap.get(heapObj).get(field).add(NULL_SYM);
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
      writer.write(stackVar + "; ");
    }
    writer.write("\n");
    writer.write("  }\n");

    // a -- { o1, o2, o3 }
    for (String stackVar : stack.keySet()) {
      writer.write("  " + stackVar);
      writer.write(" -> { ");

      for (String heapObj : stack.get(stackVar)) {
        writer.write(heapObj + "[shape=box]");
      }
      writer.write("};\n");
    }

    for (String stackVar : stack.keySet()) {
      if (stackVar.contains("@")) {
        writer.write("  " + stackVar + "[style=\"filled,dashed\"]; \n");
      }
    }

    // o1 -> o2[label="f", weight="0.2"]
    for (String heapObj : heap.keySet()) {

      for (String field : heap.get(heapObj).keySet()) {
        for (String fieldObj : heap.get(heapObj).get(field)) {
          writer.write("  " + heapObj + " -> " + fieldObj + "[label=" + field
              + ", weight=\"" + fieldEdgeWeight + "\"]\n");
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

  final boolean ASSERT_DEBUG = false;

  PointsToAnalysis(Body body, String methodName) {
    this.uGraph = new ClassicCompleteUnitGraph(body);
    this.units = body.getUnits();
    this.methodName = methodName;
    analysisResult = new HashSet<>();
  }

  private String wrapString(String s) {
    return "\"" + s + "\"";
  }

  // 1.
  // Statement: return a
  // Action: A stack variable at line return points to whatever the return var
  // points to
  private void handleReturnStmt(JReturnStmt retStmt, PointsToGraph ptg) {
    Value val = retStmt.getOp();
    if (val instanceof JimpleLocal) {
      JimpleLocal stackVal = (JimpleLocal) val;
      String retName = wrapString("@return_" + retStmt.getJavaSourceStartLineNumber());
      String wrappedStackVal = wrapString(stackVal.getName());
      ptg.handleAssignmentToGlobal(retName, wrappedStackVal);
    }

    else if (val instanceof Constant) {
      /* NONE */
    }

    else if (ASSERT_DEBUG) {
      System.err.println("Unhandled case reached in 'JReturnStmt'");
      assert (false);
    }
  }

  // 2.
  // Statement: return
  // Action: NONE
  private void handleReturnVoidStmt(JReturnVoidStmt retVoidStmt, PointsToGraph ptg) {
  }

  // 3.
  // Statement: a = @this, a = @parameter, a = @caughtexception
  // Action: Make these (kinda-stack)references point to some escaping global
  // object
  private void handleIdentityStmt(JIdentityStmt idenStmt, PointsToGraph ptg) {
    Value leftVal = idenStmt.leftBox.getValue();
    Value rightVal = idenStmt.rightBox.getValue();

    // a = @parameter n
    if (leftVal instanceof JimpleLocal && rightVal instanceof ParameterRef) {
      JimpleLocal stackVal = (JimpleLocal) leftVal;
      ParameterRef paramref = (ParameterRef) idenStmt.rightBox.getValue();
      String heapObjName = wrapString("@param_" + paramref.getIndex());
      String wrappedStackVal = wrapString(stackVal.getName());

      ptg.handleSimpleNewStatement(wrappedStackVal, heapObjName);
      ptg.objectsToMark.add(heapObjName);
    }

    // a = @thisptr
    else if (leftVal instanceof JimpleLocal && rightVal instanceof ThisRef) {
      JimpleLocal stackVal = (JimpleLocal) leftVal;
      String heapObjName = wrapString("@this");
      String wrappedStackVal = wrapString(stackVal.getName());

      ptg.handleSimpleNewStatement(wrappedStackVal, heapObjName);
      ptg.objectsToMark.add(heapObjName);
    }

    // a = @exception
    else if (leftVal instanceof JimpleLocal && rightVal instanceof JCaughtExceptionRef) {
      JimpleLocal stackVal = (JimpleLocal) leftVal;
      String heapObjName = wrapString("@caughtexception_" + idenStmt.getJavaSourceStartLineNumber());
      String wrappedStackVal = wrapString(stackVal.getName());

      ptg.handleSimpleNewStatement(wrappedStackVal, heapObjName);
      ptg.objectsToMark.add(heapObjName);
    }

    else if (ASSERT_DEBUG) {
      System.err.println("Unhandled case reached in 'IdentityStatement'");
      assert (false);
    }
  }

  // 4.
  // Statement: InvokeStatement
  // Action: At invoke statements
  private void handleInvokeStmt(JInvokeStmt invokeStmt, PointsToGraph ptg) {
    InvokeExpr invokeExpr = ((JInvokeStmt) invokeStmt).getInvokeExpr();

    if (invokeExpr instanceof JDynamicInvokeExpr) {
      // TODO
    }

    else if (invokeExpr instanceof JInterfaceInvokeExpr) {
      JInterfaceInvokeExpr interfaceInvoke = (JInterfaceInvokeExpr) invokeExpr;
      String receiverObj = wrapString(((JimpleLocal) interfaceInvoke.getBase()).getName());
      ptg.anyFieldExcapesForStackVar(receiverObj);
      ptg.makeGlobalLinks(receiverObj);
      for (int i = 0; i < interfaceInvoke.getArgCount(); i++) {
        Value vBox = interfaceInvoke.getArgBox(i).getValue();
        if (vBox instanceof JimpleLocal) {
          JimpleLocal s = (JimpleLocal) vBox;
          String w = wrapString(s.getName());

          ptg.anyFieldExcapesForStackVar(w);
          ptg.makeGlobalLinks(w);
        }
      }
    }

    else if (invokeExpr instanceof JSpecialInvokeExpr) {
      /* NONE */
    }

    else if (invokeExpr instanceof JStaticInvokeExpr) {
      JStaticInvokeExpr staticInvoke = (JStaticInvokeExpr) invokeExpr;
      for (int i = 0; i < staticInvoke.getArgCount(); i++) {
        Value vBox = staticInvoke.getArgBox(i).getValue();
        if (vBox instanceof JimpleLocal) {
          JimpleLocal s = (JimpleLocal) vBox;
          String w = wrapString(s.getName());

          ptg.anyFieldExcapesForStackVar(w);
          ptg.makeGlobalLinks(w);
        }
      }
    }

    else if (invokeExpr instanceof JVirtualInvokeExpr) {
      JVirtualInvokeExpr virtualInvoke = (JVirtualInvokeExpr) invokeExpr;
      String receiverObj = wrapString(((JimpleLocal) virtualInvoke.getBase()).getName());
      ptg.anyFieldExcapesForStackVar(receiverObj);
      ptg.makeGlobalLinks(receiverObj);
      for (int i = 0; i < virtualInvoke.getArgCount(); i++) {
        Value vBox = virtualInvoke.getArgBox(i).getValue();
        if (vBox instanceof JimpleLocal) {
          JimpleLocal s = (JimpleLocal) vBox;
          String w = wrapString(s.getName());

          ptg.anyFieldExcapesForStackVar(w);
          ptg.makeGlobalLinks(w);
        }
      }
    }

    else if (ASSERT_DEBUG) {
      System.err.println("Unhandled case reached in 'InvokeStatement'");
      assert (false);
    }
  }

  // 5.
  // Statement: goto X
  // Action: NONE
  private void handleGotoStmt(JGotoStmt gotoStmt, PointsToGraph ptg) {
  }

  // 6.
  // Statement: if cond == true goto X
  // Action: NONE
  private void handleIfStmt(JIfStmt ifStmt, PointsToGraph ptg) {
  }

  // 7.
  // Statement: EnterMonitorStmt
  // Action: NONE
  private void handleEnterMonitor(JEnterMonitorStmt enterMonitorStmt, PointsToGraph ptg) {
  }

  // 8.
  // Statement: JExitMonitorStmt
  // Action: NONE
  private void handleExitMonitor(JExitMonitorStmt exitMonitorStmt, PointsToGraph ptg) {
  }

  // 9.
  // Statement: JNopStmt
  // Action: NONE
  private void handleNopStmt(JNopStmt jNopStmt, PointsToGraph ptg) {
  }

  // 10.
  // Statement: throw X
  // Action: Object being thrown escapes
  private void handleThrowStmt(JThrowStmt jThrowStmt, PointsToGraph ptg) {

    Value val = jThrowStmt.getOp();
    if (val instanceof JimpleLocal) {
      JimpleLocal stackVal = (JimpleLocal) val;
      String retName = wrapString("@throw_" + jThrowStmt.getJavaSourceStartLineNumber());
      String wrappedStackVal = wrapString(stackVal.getName());
      ptg.handleAssignmentToGlobal(retName, wrappedStackVal);
    }

    else if (val instanceof Constant) {
      /* NONE */
    }

    else if (ASSERT_DEBUG) {
      System.err.println("Unhandled case reached in 'JReturnStmt'");
      assert (false);
    }
  }

  // 11.
  // Statement: JBreakpointStmt
  // Action: NONE
  private void handleBreakpointStmt(JBreakpointStmt jBreakpointStmt, PointsToGraph ptg) {
  }

  // 12.
  // Statement: JAssignStmt
  // Action: Handle all assignment cases
  private void handleAssignmentStmt(JAssignStmt stmnt, PointsToGraph ptg) {
    // a = new A()
    if (stmnt.leftBox.getValue() instanceof JimpleLocal && stmnt.rightBox.getValue() instanceof JNewExpr) {
      JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
      String heapObjName = "O" + stmnt.getJavaSourceStartLineNumber();
      String wrappedStackVal = wrapString(stackVal.getName());
      ptg.handleSimpleNewStatement(wrappedStackVal, heapObjName);
    }
    // a.f = b
    else if (stmnt.leftBox.getValue() instanceof JInstanceFieldRef
        && stmnt.rightBox.getValue() instanceof JimpleLocal) {
      JInstanceFieldRef fieldref = (JInstanceFieldRef) stmnt.leftBox.getValue();
      JimpleLocal stackVal = (JimpleLocal) stmnt.rightBox.getValue();
      String wrappedStackVal = wrapString(stackVal.getName());
      String wrapped_a = wrapString(fieldref.getBase().toString());
      String wrapped_f = wrapString(fieldref.getField().getName());
      ptg.handleStoreStatement(wrapped_a, wrapped_f, wrappedStackVal);
    }
    // a = b.f
    else if (stmnt.leftBox.getValue() instanceof JimpleLocal
        && stmnt.rightBox.getValue() instanceof JInstanceFieldRef) {
      JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
      JInstanceFieldRef fieldref = (JInstanceFieldRef) stmnt.rightBox.getValue();
      String wrappedStackVal = wrapString(stackVal.getName());
      String wrapped_b = wrapString(fieldref.getBase().toString());
      String wrapped_f = wrapString(fieldref.getField().getName());

      ptg.handleLoadStatement(wrappedStackVal, wrapped_b, wrapped_f);
    }
    // a = b
    else if (stmnt.leftBox.getValue() instanceof JimpleLocal
        && stmnt.rightBox.getValue() instanceof JimpleLocal) {
      JimpleLocal stackVal1 = (JimpleLocal) stmnt.leftBox.getValue();
      JimpleLocal stackVal2 = (JimpleLocal) stmnt.rightBox.getValue();
      String wrappedStackVal1 = wrapString(stackVal1.getName());
      String wrappedStackVal2 = wrapString(stackVal2.getName());
      ptg.handleCopyStatement(wrappedStackVal1, wrappedStackVal2);
    }
    // global = a
    else if (stmnt.leftBox.getValue() instanceof StaticFieldRef
        && stmnt.rightBox.getValue() instanceof JimpleLocal) {
      StaticFieldRef staticFieldref = (StaticFieldRef) stmnt.leftBox.getValue();
      JimpleLocal stackVal = (JimpleLocal) stmnt.rightBox.getValue();

      String wrappedGlobal = wrapString(staticFieldref.getField().getName());
      String wrappedStackVal = wrapString(stackVal.getName());

      ptg.handleAssignmentToGlobal(wrappedGlobal, wrappedStackVal);
      ptg.objectsToMark.add(wrappedGlobal);
    }
    // a = global
    else if (stmnt.leftBox.getValue() instanceof JimpleLocal
        && stmnt.rightBox.getValue() instanceof StaticFieldRef) {
      JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
      StaticFieldRef staticFieldref = (StaticFieldRef) stmnt.rightBox.getValue();

      String wrappedStackVal = wrapString(stackVal.getName());
      String wrappedGlobal = wrapString(staticFieldref.getField().getName());

      ptg.handleAssignmentFromGlobal(wrappedStackVal, wrappedGlobal);
      ptg.objectsToMark.add(wrappedGlobal);
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
      String wrappedStackVal = wrapString(stackVal.getName());
      ptg.handleSimpleNULLStatement(wrappedStackVal);
    }
    // a.f = null
    else if (stmnt.leftBox.getValue() instanceof JInstanceFieldRef
        && stmnt.rightBox.getValue() instanceof NullConstant) {
      JInstanceFieldRef fieldref = (JInstanceFieldRef) stmnt.leftBox.getValue();
      String wrapped_a = wrapString(fieldref.getBase().toString());
      String wrapped_f = wrapString(fieldref.getField().getName());

      ptg.handleNULLStoreStatement(wrapped_a, wrapped_f);
    }

    else if (stmnt.leftBox.getValue() instanceof JimpleLocal
        && stmnt.rightBox.getValue() instanceof JInterfaceInvokeExpr) {
      final String GLOBAL_SYM = "\"@global\"";
      final String NULL_SYM = "\"@null\"";

      JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
      JInterfaceInvokeExpr interfaceInvoke = (JInterfaceInvokeExpr) stmnt.rightBox.getValue();

      String wrappedStackVal = wrapString(stackVal.getName());
      String receiverObj = wrapString(((JimpleLocal) interfaceInvoke.getBase()).getName());

      ptg.ensureStackVar(wrappedStackVal);
      ptg.ensureHeapObj(GLOBAL_SYM);
      ptg.objectsToMark.add(GLOBAL_SYM);
      ptg.ensureHeapObj(NULL_SYM);

      { // Handle interface invoke method
        ptg.anyFieldExcapesForStackVar(receiverObj);
        ptg.makeGlobalLinks(receiverObj);
        for (int i = 0; i < interfaceInvoke.getArgCount(); i++) {
          Value vBox = interfaceInvoke.getArgBox(i).getValue();
          if (vBox instanceof JimpleLocal) {
            JimpleLocal s = (JimpleLocal) vBox;
            String w = wrapString(s.getName());

            ptg.anyFieldExcapesForStackVar(w);
            ptg.makeGlobalLinks(w);
          }
        }
      }

      // It may point to some global or null
      ptg.clearStackObj(wrappedStackVal);
      ptg.stackWeakUpdate(wrappedStackVal, GLOBAL_SYM);
      ptg.stackWeakUpdate(wrappedStackVal, NULL_SYM);
      ptg.handleCopyStatementWeak(wrappedStackVal, receiverObj);

      for (int i = 0; i < interfaceInvoke.getArgCount(); i++) {
        Value vBox = interfaceInvoke.getArgBox(i).getValue();
        if (vBox instanceof JimpleLocal) {
          JimpleLocal s = (JimpleLocal) vBox;
          String w = wrapString(s.getName());
          ptg.handleCopyStatementWeak(wrappedStackVal, w);
          ptg.makeGlobalLinks(w);
        }
      }
      ptg.makeGlobalLinks(receiverObj);
    }
    // a = <virtualInvoke>
    else if (stmnt.leftBox.getValue() instanceof JimpleLocal
        && stmnt.rightBox.getValue() instanceof JVirtualInvokeExpr) {
      final String GLOBAL_SYM = "\"@global\"";
      final String NULL_SYM = "\"@null\"";

      JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
      JVirtualInvokeExpr virtualInvoke = (JVirtualInvokeExpr) stmnt.rightBox.getValue();

      String wrappedStackVal = wrapString(stackVal.getName());
      String receiverObj = wrapString(((JimpleLocal) virtualInvoke.getBase()).getName());

      ptg.ensureStackVar(wrappedStackVal);
      ptg.ensureHeapObj(GLOBAL_SYM);
      ptg.objectsToMark.add(GLOBAL_SYM);
      ptg.ensureHeapObj(NULL_SYM);

      { // Handle virtual invoke inlined
        ptg.anyFieldExcapesForStackVar(receiverObj);
        ptg.makeGlobalLinks(receiverObj);
        for (int i = 0; i < virtualInvoke.getArgCount(); i++) {
          Value vBox = virtualInvoke.getArgBox(i).getValue();
          if (vBox instanceof JimpleLocal) {
            JimpleLocal s = (JimpleLocal) vBox;
            String w = wrapString(s.getName());

            ptg.anyFieldExcapesForStackVar(w);
            ptg.makeGlobalLinks(w);
          }
        }
      }

      // It may point to some global or null
      ptg.clearStackObj(wrappedStackVal);
      ptg.stackWeakUpdate(wrappedStackVal, GLOBAL_SYM);
      ptg.stackWeakUpdate(wrappedStackVal, NULL_SYM);
      ptg.handleCopyStatementWeak(wrappedStackVal, receiverObj);

      for (int i = 0; i < virtualInvoke.getArgCount(); i++) {
        Value vBox = virtualInvoke.getArgBox(i).getValue();
        if (vBox instanceof JimpleLocal) {
          JimpleLocal s = (JimpleLocal) vBox;
          String w = wrapString(s.getName());
          ptg.handleCopyStatementWeak(wrappedStackVal, w);
          ptg.makeGlobalLinks(w);
        }
      }
      ptg.makeGlobalLinks(receiverObj);
    }
    // a = new Array[]
    else if (stmnt.leftBox.getValue() instanceof JimpleLocal
        && stmnt.rightBox.getValue() instanceof JNewArrayExpr) {
      JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
      String wrappedStackVal = wrapString(stackVal.getName());
      String heapObjName = "O" + stmnt.getJavaSourceStartLineNumber();
      ptg.handleSimpleNewStatement(wrappedStackVal, heapObjName);
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
      JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
      JArrayRef arrayRef = (JArrayRef) stmnt.rightBox.getValue();

      final String STAR_FIELD = "\"*\"";
      String wrappedStackVal = wrapString(stackVal.getName());
      String wrappedArrayBase = wrapString(arrayRef.getBase().toString());

      ptg.handleLoadStatement(wrappedStackVal, wrappedArrayBase, STAR_FIELD);
    }
    // arr[10] = b
    else if (stmnt.leftBox.getValue() instanceof JArrayRef
        && stmnt.rightBox.getValue() instanceof JimpleLocal) {
      JArrayRef arrayRef = (JArrayRef) stmnt.leftBox.getValue();
      JimpleLocal stackVal = (JimpleLocal) stmnt.rightBox.getValue();

      final String STAR_FIELD = "\"*\"";
      String wrappedStackVal = wrapString(stackVal.getName());
      String wrappedArrayBase = wrapString(arrayRef.getBase().toString());

      ptg.handleStoreStatement(wrappedArrayBase, STAR_FIELD, wrappedStackVal);
    }
    // r0.f = 10
    else if (stmnt.leftBox.getValue() instanceof JInstanceFieldRef
        && stmnt.rightBox.getValue() instanceof IntConstant) {
      // ignore
    }
    // a = <static invoke>
    else if (stmnt.leftBox.getValue() instanceof JimpleLocal
        && stmnt.rightBox.getValue() instanceof JStaticInvokeExpr) {
      // JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
      // JStaticInvokeExpr staticInvokeExpr = (JStaticInvokeExpr)
      // stmnt.rightBox.getValue();

      final String GLOBAL_SYM = "\"@global\"";
      final String NULL_SYM = "\"@null\"";

      JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
      JStaticInvokeExpr staticInvoke = (JStaticInvokeExpr) stmnt.rightBox.getValue();

      String wrappedStackVal = wrapString(stackVal.getName());

      ptg.ensureStackVar(wrappedStackVal);
      ptg.ensureHeapObj(GLOBAL_SYM);
      ptg.objectsToMark.add(GLOBAL_SYM);
      ptg.ensureHeapObj(NULL_SYM);

      { // Handle static invoke inlined
        for (int i = 0; i < staticInvoke.getArgCount(); i++) {
          Value vBox = staticInvoke.getArgBox(i).getValue();
          if (vBox instanceof JimpleLocal) {
            JimpleLocal s = (JimpleLocal) vBox;
            String w = wrapString(s.getName());

            ptg.anyFieldExcapesForStackVar(w);
            ptg.makeGlobalLinks(w);
          }
        }
      }

      // It may point to some global or null
      ptg.clearStackObj(wrappedStackVal);
      ptg.stackWeakUpdate(wrappedStackVal, GLOBAL_SYM);
      ptg.stackWeakUpdate(wrappedStackVal, NULL_SYM);

      for (int i = 0; i < staticInvoke.getArgCount(); i++) {
        Value vBox = staticInvoke.getArgBox(i).getValue();
        if (vBox instanceof JimpleLocal) {
          JimpleLocal s = (JimpleLocal) vBox;
          String w = wrapString(s.getName());
          ptg.handleCopyStatementWeak(wrappedStackVal, w);
          ptg.makeGlobalLinks(w);
        }
      }

    } 
    // a = (A) b
    else if (stmnt.leftBox.getValue() instanceof JimpleLocal
    && stmnt.rightBox.getValue() instanceof JCastExpr) {
      JimpleLocal s = (JimpleLocal) stmnt.leftBox.getValue();
      String w1 = wrapString(s.getName());
      JCastExpr castExpr = (JCastExpr) stmnt.rightBox.getValue();
      String w2 = wrapString(castExpr.getOp().toString());
      ptg.handleCopyStatement(w1, w2);
    }
    
    else if (ASSERT_DEBUG) {
      System.err.println("Unhandled statement reached 'JAssignStmt'");
      System.err.println(stmnt);

      System.err.println("Left: " + stmnt.leftBox.getValue().getClass() + ", Right: "
          + stmnt.rightBox.getValue().getClass());

      assert (false);
    }
  }

  // 13.
  // Statement: JLookupSwitchStmt
  // Action: NONE
  private void handleLookupSwitchStmt(JLookupSwitchStmt jLookupSwitchStmt, PointsToGraph ptg) {
  }

  // 14.
  // Statement: JTableSwitchStmt
  // Action: NONE
  private void handleTableSwitchStmt(JTableSwitchStmt jTableSwitchStmt, PointsToGraph ptg) {
  }

  // 15.
  // Statement: JRetStmt
  // Action: ? UNSURE what the difference is between this and JReturnStmt
  private void handleRetStmt(JRetStmt jRetStmt, PointsToGraph ptg) {
    Value val = jRetStmt.getStmtAddress();
    if (val instanceof JimpleLocal) {
      JimpleLocal stackVal = (JimpleLocal) val;
      String retName = "\"@return" + jRetStmt.getJavaSourceStartLineNumber() + "\"";
      ptg.handleAssignmentToGlobal(retName, stackVal.getName());
      ptg.objectsToMark.add(retName);
    }

    else if (val instanceof Constant) {
      /* NONE */
    }

    else if (ASSERT_DEBUG) {
      System.err.println("Unhandled case reached in 'JRetStmt'");
      assert (false);
    }
  }

  // ***********************************************************************************************

  private void flowFunction(Unit u, PointsToGraph ptg) {

    // 1. ReturnStmt<JReturnStmt>
    if (u instanceof JReturnStmt)
      handleReturnStmt((JReturnStmt) u, ptg);
    // 2. ReturnVoid<JReturnVoidStmt>
    else if (u instanceof JReturnVoidStmt)
      handleReturnVoidStmt((JReturnVoidStmt) u, ptg);
    // 3. IdentityStmt<IdentityStmt>
    else if (u instanceof JIdentityStmt)
      handleIdentityStmt((JIdentityStmt) u, ptg);
    // 4. InvokeStmt<InvokeStmt> === invoke InvokeExpr
    else if (u instanceof JInvokeStmt)
      handleInvokeStmt((JInvokeStmt) u, ptg);
    // 5. gotoStmt<JGotoStmt>
    else if (u instanceof JGotoStmt)
      handleGotoStmt((JGotoStmt) u, ptg);
    // 6. ifStmt<JIfStmt>
    else if (u instanceof JIfStmt)
      handleIfStmt((JIfStmt) u, ptg);
    // 7. MonitorEnterStmt<JEnterMonitorStmt>
    else if (u instanceof JEnterMonitorStmt)
      handleEnterMonitor((JEnterMonitorStmt) u, ptg);
    // 8. MonitorExitStmt<JExitMonitorStmt>
    else if (u instanceof JExitMonitorStmt)
      handleExitMonitor((JExitMonitorStmt) u, ptg);
    // 9. nopStmt<JNopStmt>
    else if (u instanceof JNopStmt)
      handleNopStmt((JNopStmt) u, ptg);
    // 10. ThrowStmt<JthrowStmt>
    else if (u instanceof JThrowStmt)
      handleThrowStmt((JThrowStmt) u, ptg);
    // 11. BreakpointStmt<JBreakpointStmt>
    else if (u instanceof JBreakpointStmt)
      handleBreakpointStmt((JBreakpointStmt) u, ptg);
    // 12. AssignmentStatement<JAssignStmt>
    else if (u instanceof JAssignStmt)
      handleAssignmentStmt((JAssignStmt) u, ptg);
    // 13. LookupSwitch<JLookupSwitchStmt>
    else if (u instanceof JLookupSwitchStmt)
      handleLookupSwitchStmt((JLookupSwitchStmt) u, ptg);
    // 14. TableSwitch<JTableSwitchStmt>
    else if (u instanceof JTableSwitchStmt)
      handleTableSwitchStmt((JTableSwitchStmt) u, ptg);
    // 15. JRetStmt
    else if (u instanceof JRetStmt)
      handleRetStmt((JRetStmt) u, ptg);

    else if (ASSERT_DEBUG) {
      System.err.println("Unhandled statement reached '" + u.getClass() + "'");
      assert (false);
    }
  }

  // ***********************************************************************************************

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
  HashMap<String, String> finalMap = new HashMap<String, String>();

  String getEscapingObjects(Set<String> old) {
    ArrayList<Integer> escapingObjs = new ArrayList<>();

    old.forEach(s -> {
      try {
        if (s.charAt(0) != 'O')
          return;
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
      System.out.println(body.getMethod().getDeclaringClass().getName() + ":" + methodName + " " + escapingObjs);
      // finalResult.add(body.getMethod().getDeclaringClass().getName() + ":" +
      // methodName + " " + escapingObjs);
      // finalMap.put(methodName, escapingObjs);
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      BufferedWriter writer = new BufferedWriter(
          new FileWriter(System.getProperty("user.dir") + "/recording/index.html"));
      // Add html header
      writer.write("<!doctype html>\n<html lang=\"en\">\n");
      writer.write("<head>\n");
      writer.write("<title> Generic recording session </title>\n");
      writer.write("</head>\n");
      writer.write("<body>\n");
      for (String m : finalMap.keySet()) {
        writer.write("<a href=\"" + m + "/index.html\">" + m.replaceAll("<", "").replaceAll(">", "") + " -- "
            + finalMap.get(m) + "</a> </br> \n");
      }
      writer.write("</body>\n");
      writer.close();
    } catch (Exception e) {

    }
  }

  // void printResult() {
  // Collections.sort(finalResult);
  // // if (finalResult == null) {
  // // System.err.println("Got null finalResult");
  // // }
  // for (String r : finalResult) {
  // System.out.println(r);
  // }

  // try {
  // BufferedWriter writer = new BufferedWriter(
  // new FileWriter(System.getProperty("user.dir") + "/recording/index.html"));
  // // Add html header
  // writer.write("<!doctype html>\n<html lang=\"en\">\n");
  // writer.write("<head>\n");
  // writer.write("<title> Generic recording session </title>\n");
  // writer.write("</head>\n");
  // writer.write("<body>\n");
  // for (String m : finalMap.keySet()) {
  // writer.write("<a href=\"" + m + "/index.html\">" + m.replaceAll("<",
  // "").replaceAll(">", "") + " -- "
  // + finalMap.get(m) + "</a> </br> \n");
  // }
  // writer.write("</body>\n");
  // writer.close();
  // } catch (Exception e) {

  // }

  // }

}
