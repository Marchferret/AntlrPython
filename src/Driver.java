import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.*;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

public class Driver {
    public static void main(String[] args) throws Exception {
        String inputFile = null;
        if (args.length > 0) {
            inputFile = args[0];
        }
        InputStream is = System.in;
        if (inputFile != null) {
            is = new FileInputStream(inputFile);
        }
        //CharStream stream = new CharStream(is);
        PythonLexer lexer = new PythonLexer(CharStreams.fromStream(is, StandardCharsets.UTF_8));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PythonParser parser = new PythonParser(tokens);
        ParseTree tree = parser.prog(); // parse
        ParseTreeWalker walker = new ParseTreeWalker(); // create standard walker
        Converter converter = new Converter(parser);
        walker.walk(converter, tree); // initiate walk of tree with listener

        HashMap<String, ArrayList<String>> globalIdsMap = converter.getGlobalIdsMap();
        for (String id : globalIdsMap.keySet()) {
            System.out.println(id);
            for(String idSSA : globalIdsMap.get(id)) {
                System.out.println(idSSA);
            }
        }
        ArrayList<String> importedNames = converter.getImportedNames();
        for (String importedName : importedNames) {

            if (globalIdsMap.containsKey(importedName)) {
                globalIdsMap.remove(importedName);
            }
        }

        System.out.println("--------------Second walker---------------");
        Rewriter rewriter = new Rewriter(converter.functionNames, converter.idMap, converter.comp_forMap, globalIdsMap, parser);
        walker.walk(rewriter, tree);
//
//        System.out.println("--------------Causal Map---------------");
//        HashMap<String, ArrayList<String>> causalMap = rewriter.getCausalMap();
//        for (String key :causalMap.keySet()) {
//            System.out.println("id:" + key + " parent:" + causalMap.get(key));
//        }

    }
}
