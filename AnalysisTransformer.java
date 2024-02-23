import java.util.*;

import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.ParameterRef;
import soot.jimple.StaticFieldRef;
import soot.jimple.ThisRef;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JGotoStmt;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JIfStmt;
import soot.jimple.internal.JInstanceFieldRef;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JLengthExpr;
import soot.jimple.internal.JNewExpr;
import soot.jimple.internal.JReturnStmt;
import soot.jimple.internal.JReturnVoidStmt;
import soot.jimple.internal.JSpecialInvokeExpr;
import soot.jimple.internal.JimpleLocal;
import soot.toolkits.graph.ClassicCompleteUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.io.*;

interface Cloneable {
    public Cloneable clone();
}

class PointsToGraph {

    private HashMap<String, HashMap<String, Set<String>>> heap; // Heap mapping
    private HashMap<String, Set<String>> stack; // Stack mapping

    public PointsToGraph clone() {
        PointsToGraph clone = new PointsToGraph();
        for (String heapObj : heap.keySet()) {
            clone.heap.put(heapObj, new HashMap<>());
            for(String field : heap.get(heapObj).keySet()) {
                clone.heap.get(heapObj).put(field, new HashSet<>());
                clone.heap.get(heapObj).get(field).addAll(heap.get(heapObj).get(field));
            }
        }
        for (String stackVar : stack.keySet()) {
            clone.stack.put(stackVar, new HashSet<>());
            clone.stack.get(stackVar).addAll(stack.get(stackVar));
        }
        return clone;
    }

    public boolean equals(PointsToGraph other) {
        if (!stack.keySet().equals(other.stack.keySet())) return false;
        if (!heap.keySet().equals(other.heap.keySet())) return false;
        
        // Compare stack
        for (String stackVar : stack.keySet()) {
            if (!stack.get(stackVar).equals(other.stack.get(stackVar))) return false;
        }

        // Compare heap
        for (String heapObj : heap.keySet()) {
            HashMap<String, Set<String>> fieldMap1 = heap.get(heapObj);
            HashMap<String, Set<String>> fieldMap2 = other.heap.get(heapObj);
            if (!fieldMap1.keySet().equals(fieldMap2.keySet())) return false;
            for (String field : fieldMap1.keySet()) {
                if (!fieldMap1.get(field).equals(fieldMap2.get(field))) return false;
            }
        }
        return true;
    }
    public void add(PointsToGraph other) {
        for (String heapObj : other.heap.keySet()) {
            if (!heap.containsKey(heapObj)) heap.put(heapObj, new HashMap<>());
            for(String field : other.heap.get(heapObj).keySet()) {
                if (!heap.get(heapObj).containsKey(field)) heap.get(heapObj).put(field, new HashSet<>());
                heap.get(heapObj).get(field).addAll(other.heap.get(heapObj).get(field));
            }
        }
        for (String stackVar : other.stack.keySet()) {
            if (!stack.containsKey(stackVar)) stack.put(stackVar, new HashSet<>());
            stack.get(stackVar).addAll(other.stack.get(stackVar));
        }

    }

    public PointsToGraph() {
        heap = new HashMap<>();
        stack = new HashMap<>();
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

            // Add objects of {b1,b2,...}.f to resultset
            if (heap.get(heapObj).containsKey(field)) {
                Set<String> objsOfBdotF = heap.get(heapObj).get(field);
                finalSetPointedToByBdotF.addAll(objsOfBdotF);
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
        }
    }

    public void savePTG(String path) throws IOException {
        if (path == null) {
            // TODO
            return;
        }

        String rankDir = "LR";
        String fieldEdgeWeight = "0.2";

        BufferedWriter writer = new BufferedWriter(new FileWriter(path));

        writer.write("digraph sample {\n");
        writer.write("  rankDir=\"" + rankDir + "\";\n");

        writer.write("  subgraph cluster_0 {\n");
        writer.write("    label=\"Stack\"\n");
        writer.write("    ");
        for (String stackVar : stack.keySet()) {
            writer.write(stackVar.replaceAll("\\$", "") + "; ");
        }
        writer.write("\n");
        writer.write("  }\n");

        // a -- { o1, o2, o3 }
        for (String stackVar : stack.keySet()) {
            writer.write("  " + stackVar.replaceAll("\\$", "") + " -> { ");
            for (String heapObj : stack.get(stackVar)) {
                writer.write(heapObj + "[shape=box] " + " ");
            }
            writer.write("};\n");
        }

        // o1 -> o2[label="f", weight="0.2"]
        for (String heapObj : heap.keySet()) {
            for (String field : heap.get(heapObj).keySet()) {
                for (String fieldObj : heap.get(heapObj).get(field)) {
                    writer.write("  " + heapObj + " -> " + fieldObj + "[label=\"" + field
                            + "\", weight=\"" + fieldEdgeWeight + "\"];\n");
                }

            }
        }

        writer.write("}\n");
        writer.close();
    }
}

class NodeProp {
    // 0 - unknown
    // 1 - not-escape
    // 2 - escapes
    int escapeStatus = 0;
}

class PointsToAnalysis {
    PatchingChain<Unit> units;
    UnitGraph uGraph;
    String methodName;

    PointsToAnalysis(Body body, String methodName) {
        this.uGraph = new ClassicCompleteUnitGraph(body);
        this.units = body.getUnits();
        this.methodName = methodName;
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

                ptg.handleCopyStatement(staticFieldref.getField().getName(), stackVal.getName());
            }
            // a = global
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof StaticFieldRef) {
                JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
                StaticFieldRef staticFieldref = (StaticFieldRef) stmnt.rightBox.getValue();

                ptg.handleCopyStatement(stackVal.getName(), staticFieldref.getField().getName());
            } 
            // a = lengthof b
            else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                    && stmnt.rightBox.getValue() instanceof JLengthExpr) {
                        // Ignore
            }
            else {
                System.err.println("  UKNASSN--" + u);

                System.err.println("    Left: " + stmnt.leftBox.getValue().getClass() + ", Right: "
                        + stmnt.rightBox.getValue().getClass());
            }
        } else if (u instanceof JInvokeStmt) {
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
            }
            // a = @thisptr
            else if (iden.leftBox.getValue() instanceof JimpleLocal
                    && iden.rightBox.getValue() instanceof ThisRef) {
                JimpleLocal stackVal = (JimpleLocal) iden.leftBox.getValue();
                ptg.handleSimpleNewStatement(stackVal.getName(), "\"@this\"");
            }

            else {
                System.err.println("  UKN--" + u);
                System.err.println("    Left: " + iden.leftBox.getValue().getClass() + ", Right: "
                        + iden.rightBox.getValue().getClass());
            }
        } else if (u instanceof JIfStmt || u instanceof JGotoStmt) {
            // Ignore all conditionals and jumps
        }
        else {
            System.err.println("UNHANDLED: " + u + " (" + u.getClass() + ")");
        }
    }

    public void doAnalysis() {
        Set<Unit> worklist = new HashSet<>();
        HashMap<Unit, PointsToGraph> outSets = new HashMap<>();
        // Initialize worklist, add all the nodes so that each node is visited atleast once

        for (Unit u : units) {
            worklist.add(u);
            outSets.put(u, new PointsToGraph());
        }

        while (!worklist.isEmpty()) {
            // Pop one unit from the worklist
            Unit currUnit = worklist.iterator().next();
            worklist.remove(currUnit);

            // 
            PointsToGraph currentFlowSet = new PointsToGraph(); 
            // Check incoming edges
            for(Unit incoming : uGraph.getPredsOf(currUnit)) {
                currentFlowSet.add(outSets.get(incoming));
            }

            flowFunction(currUnit, currentFlowSet);

            PointsToGraph old = outSets.get(currUnit);
            // Add successors to worklist
            if (!old.equals(currentFlowSet)) {
                outSets.put(currUnit, currentFlowSet);
                worklist.addAll(uGraph.getSuccsOf(currUnit));
            }

        }

        int i = 0;

        for (Unit u : units) {
            try {
                outSets.get(u).savePTG(methodName + "_" + (i++) + ".DOT");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}

public class AnalysisTransformer extends BodyTransformer {
    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
        // Construct CFG for the current method's body
        String methodName = body.getMethod().getName();
        PointsToAnalysis pta = new PointsToAnalysis(body, methodName);
        System.out.println("Working on method " + methodName);
        pta.doAnalysis();
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(methodName + ".jimple"));
            writer.write(body.toString());
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
