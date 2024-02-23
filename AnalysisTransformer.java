import java.util.*;

import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.ParameterRef;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JInstanceFieldRef;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JNewExpr;
import soot.jimple.internal.JReturnStmt;
import soot.jimple.internal.JReturnVoidStmt;
import soot.jimple.internal.JSpecialInvokeExpr;
import soot.jimple.internal.JimpleLocal;

import java.io.*;

class PointsToGraph<G> {

    public HashMap<String, G> properties;

    private HashMap<String, HashMap<String, Set<String>>> heap; // Heap mapping
    private HashMap<String, Set<String>> stack; // Stack mapping

    public PointsToGraph() {
        heap = new HashMap<>();
        stack = new HashMap<>();
        properties = new HashMap<>();
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

public class AnalysisTransformer extends BodyTransformer {
    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
        // Construct CFG for the current method's body
        PatchingChain<Unit> units = body.getUnits();

        String methodName = body.getMethod().getName();
        System.out.println("Printing Jimple for \"" + methodName + "\"");
        // System.out.println(units);

        PointsToGraph<NodeProp> ptg = new PointsToGraph();

        // Iterate over each unit of CFG.
        for (Unit u : units) {

            if (u instanceof JAssignStmt) {
                JAssignStmt stmnt = (JAssignStmt) u;

                // a = new A()
                if (stmnt.leftBox.getValue() instanceof JimpleLocal && stmnt.rightBox.getValue() instanceof JNewExpr) {
                    JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
                    // JNewExpr newClassName = (JNewExpr) stmnt.rightBox.getValue();
                    String heapObjName = "O" + u.getJavaSourceStartLineNumber();

                    System.out.println("  New:" + stackVal.getName() + " -> " + heapObjName);
                    ptg.handleSimpleNewStatement(stackVal.getName(), heapObjName);
                }
                // a.f = b
                else if (stmnt.leftBox.getValue() instanceof JInstanceFieldRef
                        && stmnt.rightBox.getValue() instanceof JimpleLocal) {
                    JInstanceFieldRef fieldref = (JInstanceFieldRef) stmnt.leftBox.getValue();
                    JimpleLocal stackVal = (JimpleLocal) stmnt.rightBox.getValue();

                    System.out.println("  Store: " + fieldref.getBase().toString() + "." + fieldref.getField().getName()
                            + "=" + stackVal.getName());
                    ptg.handleStoreStatement(fieldref.getBase().toString(), fieldref.getField().getName(),
                            stackVal.getName());
                }

                // a = b.f
                else if (stmnt.leftBox.getValue() instanceof JimpleLocal
                        && stmnt.rightBox.getValue() instanceof JInstanceFieldRef) {
                    JimpleLocal stackVal = (JimpleLocal) stmnt.leftBox.getValue();
                    JInstanceFieldRef fieldref = (JInstanceFieldRef) stmnt.rightBox.getValue();

                    System.out.println("  Load: " + stackVal.getName() + " = " + fieldref.getBase().toString() + "."
                            + fieldref.getField().getName());
                    ptg.handleLoadStatement(stackVal.getName(), fieldref.getBase().toString(),
                            fieldref.getField().getName());
                }

                else {
                    System.out.println("  UKN" + u);

                    System.out.println("    Left: " + stmnt.leftBox.getValue().getClass() + ", Right: "
                            + stmnt.rightBox.getValue().getClass());
                }

            } else if (u instanceof JInvokeStmt) {
                JInvokeStmt inv = (JInvokeStmt) u;
                InvokeExpr invokeExpr = inv.getInvokeExpr();
                if (invokeExpr instanceof JSpecialInvokeExpr) {

                } else {
                    System.out.println("  INVOKE: " + inv + " (" + inv.getInvokeExpr().getClass() + ")");
                }
            } else if (u instanceof JReturnStmt) {
                System.out.println("  RETURN: " + u + " (" + u.getClass() + ")");

            } else if (u instanceof JReturnVoidStmt) {
                System.out.println("  RETURNVOID: " + u + " (" + u.getClass() + ")");

            } else if (u instanceof JIdentityStmt) {
                JIdentityStmt iden = (JIdentityStmt) u;

                if (iden.leftBox.getValue() instanceof JimpleLocal && iden.rightBox.getValue() instanceof ParameterRef) {
                    JimpleLocal stackVal = (JimpleLocal) iden.leftBox.getValue();
                    ParameterRef paramref = (ParameterRef) iden.rightBox.getValue();
                    String heapObjName = "P" + paramref.getIndex();

                    System.out.println("  Param: " + stackVal.getName() + " -> " + heapObjName);
                    ptg.handleSimpleNewStatement(stackVal.getName(), heapObjName);
                } else {
                    System.out.println("  UKN" + u);
                    System.out.println("    Left: " + iden.leftBox.getValue().getClass() + ", Right: "
                            + iden.rightBox.getValue().getClass());
                }

            }

            else {
                System.out.println("UNHANDLED: " + u + " (" + u.getClass() + ")");
            }

            // if (u instanceof JAssignStmt) {
            // JAssignStmt stmt = (JAssignStmt) u;
            // Value rhs = stmt.getRightOp();
            // if (rhs instanceof JNewExpr) {
            // try {
            // System.out.println("Unit is " + u + " and the line number is : " +
            // u.getJavaSourceStartLineNumber());
            // } catch (Exception e) {
            // System.out.println("Unit is " + u + " and the line number is : " + -1);
            // }
            // }
            // }
        }
        System.out.println("-----------------------------------------------");

        try {
            ptg.savePTG(methodName + ".DOT");
        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
