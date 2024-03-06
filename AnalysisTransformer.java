import java.util.*;

import soot.*;
import soot.jimple.BinopExpr;
import soot.jimple.ClassConstant;
import soot.jimple.Constant;
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

interface DOTable {
  public void savePTGToPath(String path) throws IOException;
}

class PointsToAnalysis {
  PatchingChain<Unit> units;
  UnitGraph uGraph;
  String methodName;
  Set<String> analysisResult;

  final boolean ASSERT_DEBUG = true;

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
      System.err.println("Unhandled case reached in 'JReturnStmt' : " + retStmt.getClass());
      System.exit(1);
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

    String heapObjName = null;
    String wrappedStackVal = null;

    // a = @parameter n
    if (leftVal instanceof JimpleLocal && rightVal instanceof ParameterRef) {
      JimpleLocal stackVal = (JimpleLocal) leftVal;
      ParameterRef paramref = (ParameterRef) idenStmt.rightBox.getValue();
      heapObjName = wrapString("@param_" + paramref.getIndex());
      wrappedStackVal = wrapString(stackVal.getName());
    }

    // a = @thisptr
    else if (leftVal instanceof JimpleLocal && rightVal instanceof ThisRef) {
      JimpleLocal stackVal = (JimpleLocal) leftVal;
      heapObjName = wrapString("@this");
      wrappedStackVal = wrapString(stackVal.getName());
    }

    // a = @exception
    else if (leftVal instanceof JimpleLocal && rightVal instanceof JCaughtExceptionRef) {
      JimpleLocal stackVal = (JimpleLocal) leftVal;
      heapObjName = wrapString("@caughtexception_" + idenStmt.getJavaSourceStartLineNumber());
      wrappedStackVal = wrapString(stackVal.getName());
    }

    else if (ASSERT_DEBUG) {
      System.err.println("Unhandled case reached in 'IdentityStatement' : " + idenStmt.getClass());
      System.exit(1);
    }

    // Update points to graph
    if (wrappedStackVal != null && heapObjName != null) {
      ptg.handleSimpleNewStatement(wrappedStackVal, heapObjName);
      ptg.objectsToMark.add(heapObjName);
    }

  }

  // 4.
  // Statement: InvokeStatement
  // Action: At invoke statements
  private void handleInvokeStmt(JInvokeStmt invokeStmt, PointsToGraph ptg) {
    InvokeExpr invokeExpr = ((JInvokeStmt) invokeStmt).getInvokeExpr();

    if (invokeExpr instanceof JDynamicInvokeExpr) {
      /* NONE */
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
      System.exit(1);
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
      System.exit(1);
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
    // a = b.foo()
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
      String arrayStore = "A" + stmnt.getJavaSourceStartLineNumber();
      ptg.handleArrayNewStatement(wrappedStackVal, heapObjName, arrayStore); 
      // ptg.handleSimpleNewStatement(wrappedStackVal, heapObjName); 
    }
    
    // [any] = constant
    else if (stmnt.rightBox.getValue() instanceof Constant) {
      /* ignore */
    } 
    
    // [any] = a binop b
    else if (stmnt.rightBox.getValue() instanceof BinopExpr) {
      /* ignore */
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
    // arr[10] = class "Ltestcase/Test4;"
    else if (stmnt.leftBox.getValue() instanceof JArrayRef
        && stmnt.rightBox.getValue() instanceof ClassConstant) {
      JArrayRef arrayRef = (JArrayRef) stmnt.leftBox.getValue();
      ClassConstant classConst = (ClassConstant) stmnt.rightBox.getValue();

      String wrappedArrayBase = wrapString(arrayRef.getBase().toString());
      String classConstStr = wrapString(classConst.getValue());
      String classConstStrObj = wrapString("@" + classConst.getValue());

      ptg.stackStrongUpdate(classConstStr, classConstStrObj);
      final String STAR_FIELD = "\"*\"";
      ptg.handleStoreStatement(wrappedArrayBase, STAR_FIELD, classConstStr);

      ptg.objectsToMark.add(classConstStr);
      ptg.objectsToMark.add(classConstStrObj);

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

      // System.exit(1);
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
  // Action: NONE
  private void handleRetStmt(JRetStmt jRetStmt, PointsToGraph ptg) {
  }

  // ***********************************************************************************************

  private void flowFunction(Unit u, PointsToGraph ptg) {

    // 1. ReturnStmt<JReturnStmt>
    if (u instanceof JReturnStmt)
      handleReturnStmt((JReturnStmt) u, ptg);
    // 2. ReturnVoid<JReturnVoidStmt>
    else if (u instanceof JReturnVoidStmt)
      handleReturnVoidStmt((JReturnVoidStmt) u, ptg);
    // 3. IdentityStmt<JIdentityStmt>
    else if (u instanceof JIdentityStmt)
      handleIdentityStmt((JIdentityStmt) u, ptg);
    // 4. InvokeStmt<JInvokeStmt> === invoke InvokeExpr
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
    // 10. ThrowStmt<JThrowStmt>
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
    // 15. JRetStmt -- deprecated
    // Wiki(https://en.wikipedia.org/wiki/List_of_Java_bytecode_instructions#endnote_Deprecated)
    else if (u instanceof JRetStmt)
      handleRetStmt((JRetStmt) u, ptg);

    else if (ASSERT_DEBUG) {
      System.err.println("Unhandled statement reached '" + u.getClass() + "'");
      assert (false);
    }
  }

  // ***********************************************************************************************

  public void doAnalysis() throws Exception {
    PointRecorder recorder;

    if (PointRecorder.ENABLED)
      recorder = new PointRecorder(System.getProperty("user.dir") + "/recording/" + methodName, units,
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
      if (PointRecorder.ENABLED)
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
      if (PointRecorder.ENABLED)
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
    if (PointRecorder.ENABLED)
      recorder.save(analysisResult);
  }
}

public class AnalysisTransformer extends BodyTransformer {
  ArrayList<String> finalResult = new ArrayList<String>();
  HashMap<String, String> finalMap = new HashMap<String, String>();

  @Override
  protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
    // Construct CFG for the current method's body
    String methodName = body.getMethod().getName();
    PointsToAnalysis pta = new PointsToAnalysis(body,
        body.getMethod().getDeclaringClass().getName() + "/" + methodName);
    try {
      pta.doAnalysis();
      Set<String> result = new HashSet<>(pta.analysisResult);
      String resultString = body.getMethod().getDeclaringClass().getName() + ":" + methodName + " "
      + PointRecorder.getEscapingObjects(result);
      if (!PointRecorder.getEscapingObjects(result).equals("")) {
        System.out.println(resultString);
        finalMap.put(body.getMethod().getDeclaringClass().getName() + "/" + methodName, resultString);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    if (PointRecorder.ENABLED)
      PointRecorder.generateIndexPage(finalMap);
  }

}
