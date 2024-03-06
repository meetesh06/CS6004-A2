import java.io.*;
import java.nio.file.*;
import java.util.*;
import soot.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JReturnStmt;

public class PointRecorder {
  PatchingChain<Unit> units;
  String path;
  List<String> recorded;
  int count = 0;
  String name;

  static final boolean ENABLED = true;

  public static void generateIndexPage(HashMap<String, String> finalMap) {
    try {
      BufferedWriter writer = new BufferedWriter(
          new FileWriter(System.getProperty("user.dir") + "/recording/index.html"));
      // Add html header
      writer.write("<!doctype html>\n<html lang=\"en\">\n");
      writer.write("<head>\n");
      writer.write("<title> Recording session home </title>\n");
      writer.write("</head>\n");
      writer.write("<body>\n");
      List<String> sortedKeys = new ArrayList<>(); 
      sortedKeys.addAll(finalMap.keySet());
      Collections.sort(sortedKeys);
      for (String k : sortedKeys) {
        writer.write("<div>");
        writer.write("<span> " + finalMap.get(k) + "</span>");
        writer.write("<a target=\"_blank\" href=\"" + k + "/index.html\">  show recording </a> \n");
        writer.write("</div>");
      }
      writer.write("</body>\n");
      writer.close();
    } catch (Exception e) {

    }
  }

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
    writer.write(
        "  The recorder session recorded <b>" + count + "</b> distinct states. </br> <b>List of escaping objects: [ ");
    writer.write(getEscapingObjects(finalResult));
    writer.write("] </b>\n");

    writer.write("  </p>\n");

    writer.write("  <input class=\"slider\" autofocus width=\"\" id=\"slide\" type=\"range\" min=\"1\" max=\""
        + count + "\" step=\"1\" value=\"1\" onInput=\"updateSlider(this.value)\">\n");
    writer.write(
        "  <iframe frameBorder=\"0\" id=\"AppFrame\" src=\"stateAt_1.html\" name=\"iframe_a\" style=\"height: 100vh;\" height=\"100vh\" width=\"100%\" title=\"State\"></iframe>\n");

    writer.write("</body>\n");

    writer.close();
  }

  static String getEscapingObjects(Set<String> old) {
    ArrayList<String> escapingObjs = new ArrayList<>();

    old.forEach(s -> {
      try {
        if (s.charAt(0) == 'O')
          escapingObjs.add(Integer.parseInt(s.substring(1)) + "");
      } catch (Exception e) {

      }
    });

    Collections.sort(escapingObjs);

    // escapingObjs.sort((a, b) -> a - b);
    StringBuilder sb = new StringBuilder();
    for (String i : escapingObjs) {
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