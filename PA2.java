import soot.*;

public class PA2 {
    public static void main(String[] args) {
        String classPath = "."; 	// change to appropriate path to the test class
        String dir = "./testcase";

        //Set up arguments for Soot
        String[] sootArgs = {
            "-cp", classPath, "-pp", // sets the class path for Soot
            "-keep-line-number",     // preserves line numbers in input Java files  
            "-main-class", "Test",	 // specify the main class
            "-process-dir", dir      // list the classes to analyze
        };

        // Create transformer for analysis
        AnalysisTransformer analysisTransformer = new AnalysisTransformer();

        // Add transformer to appropriate pack in PackManager; PackManager will run all packs when soot.Main.main is called
        PackManager.v().getPack("jtp").add(new Transform("jtp.dfa", analysisTransformer));

        try { Thread t = new Thread(() -> { soot.Main.main(sootArgs); }); t.start(); t.join(); } catch (Exception e) { }
        analysisTransformer.printFinalResult();
    }
}
