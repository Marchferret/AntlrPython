import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.TerminalNode;


import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Rewriter extends PythonBaseListener{
    class Pair {
        int start;
        int end;
        public Pair(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
    ParseTreeProperty<HashMap<String, ArrayList<String>>> idMap;
    ParseTreeProperty<Set<String>> loopRulePredNames;
    public ParseTreeProperty<ArrayList<ArrayList<String>>> comp_forMap;
    ParseTreeProperty<ArrayList<Token>> ruleTokens;
    HashMap<String, ArrayList<String>> globalIdsMap;
    ParseTreeProperty<HashMap<String, Integer>> tempSSAPointerMap;
    PythonParser parser;
    BufferedWriter out = null;
    FileWriter fstream;
    HashMap<String, Integer> ssaPointerMap;
    Stack<String> phiHolder;
    HashMap<String, ArrayList<String>> causalMap;
    ArrayList<String> addedPhiNames;
    ArrayList<String> functionNames;
    Map<String, Set<String>> ifPredNameMap;

    int tabCount;
    ArrayList<String> predHolder;
    public String[] tokenTable = new String[100];
    public Rewriter(ArrayList<String> functionNames,
                    ParseTreeProperty<HashMap<String, ArrayList<String>>> idMap,
                    ParseTreeProperty<ArrayList<ArrayList<String>>> comp_forMap,
                    HashMap<String, ArrayList<String>> globalIdsMap,
                    PythonParser parser) {
        this.ifPredNameMap = new HashMap<>();
        this.addedPhiNames = new ArrayList<>();
        this.functionNames = functionNames;
        this.causalMap = new HashMap<>();
        this.comp_forMap = comp_forMap;
        this.parser = parser;
        this.idMap = idMap;
        this.predHolder = new ArrayList<>();
        ruleTokens = new ParseTreeProperty<>();
        loopRulePredNames = new ParseTreeProperty<>();
        tempSSAPointerMap = new ParseTreeProperty<>();
        this.globalIdsMap = globalIdsMap;
        tabCount = 0;
        phiHolder = new Stack<>();
        ssaPointerMap = new HashMap<>();
        for (String id : globalIdsMap.keySet()) {
            ssaPointerMap.put(id, -1);
        }
        try {
            fstream = new FileWriter("out.py", false);
            out = new BufferedWriter(fstream);
        }
        catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
        try {
            FileReader tstream = new FileReader("src/Python.tokens");
            BufferedReader br = new BufferedReader(tstream);
            String line;
            for (int i = 1; i <= 99; i++) {
                line = br.readLine();
                String[] strs = line.split("=");
                tokenTable[Integer.valueOf(strs[1])] = strs[0];
            }
        }
        catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
    public HashMap<String, ArrayList<String>> getCausalMap() {
        return this.causalMap;
    }
    private boolean isSpecial(String id) {
        return id.matches("__[A-Za-z_][A-Za-z_0-9]*__") || id.equals("self") || id.equals("cls") || id.equals("_");
    }
    private void setNodeComp_forLists(ParseTree node, ArrayList<ArrayList<String>> nodeComp_forLists) {
        comp_forMap.put(node, nodeComp_forLists);
    }
    private ArrayList<ArrayList<String>> getNodeComp_forLists(ParseTree node) {
        return comp_forMap.get(node);
    }
    public HashMap<String, Integer> getTempSSAPointerMap(ParseTree node) {
        return tempSSAPointerMap.get(node);
    }
    public void setTempSSAPointerMap(ParseTree node, HashMap<String, Integer> map) {
        tempSSAPointerMap.put(node, map);
    }
    public ArrayList<Token> getRuleTokens(ParseTree node) {
        return ruleTokens.get(node);
    }
    public void setRuleTokens(ParseTree node, ArrayList<Token> tokens) {
        ruleTokens.put(node, tokens);
    }
    private void setNodeIdsMap(ParseTree node, HashMap<String, ArrayList<String>> nodeIds) {
        idMap.put(node, nodeIds);
    }
    private HashMap<String, ArrayList<String>> getNodeIdsMap(ParseTree node) {
        return idMap.get(node);
    }

    //Sep 3 added: storing pred name map to while node
    private void setLoopRulePredNameMap(ParseTree node, Set<String> set) {
        loopRulePredNames.put(node, set);
    }
    private Set<String> getLoopRulePredNameMap(ParseTree node) {
        return loopRulePredNames.get(node);
    }

    public void enterSmall_stmt(PythonParser.Small_stmtContext ctx) {
        setRuleTokens(ctx, new ArrayList<>());
    }
    public void exitSmall_stmt(PythonParser.Small_stmtContext ctx) {
    }
    public void enterImport_stmt(PythonParser.Import_stmtContext ctx) {
    }
    public void exitImport_stmt(PythonParser.Import_stmtContext ctx) throws IOException {
        StringBuilder sb = new StringBuilder();
        ArrayList<Token> tokens = getRuleTokens(findFirstSmallStmtNode(ctx));
        if (ctx.getText().contains("from")) {
            for (Token t : tokens) {
                if (tokenTable[t.getType()].equals("FROM")) {
                    sb.append("from ");
                    continue;
                }
                if (tokenTable[t.getType()].equals("IMPORT")) {
                    sb.append(" import ");
                    continue;
                }
                if (tokenTable[t.getType()].equals("AS")) {
                    sb.append(" as ");
                    continue;
                }
                sb.append(t.getText());
            }
        } else {
            for (Token t : tokens) {
                if (tokenTable[t.getType()].equals("IMPORT")) {
                    sb.append("import ");
                    continue;
                }
                if (tokenTable[t.getType()].equals("AS")) {
                    sb.append(" as ");
                    continue;
                }
                sb.append(t.getText());
            }
        }
        out.write(sb.toString());
    }
    public void enterTestlist(PythonParser.TestlistContext ctx) {
        setRuleTokens(ctx, new ArrayList<>());
    }

    public void exitTestlist(PythonParser.TestlistContext ctx) throws IOException {
        if (ctx.getParent() instanceof  PythonParser.For_stmtContext) {
            HashMap<String, Integer> forNodePointerMap = getTempSSAPointerMap(ctx.getParent());
            ArrayList<Token> exprlistTokens = getRuleTokens(((PythonParser.For_stmtContext) ctx.getParent()).exprlist());
            ArrayList<String> exprlistTokensAsString = new ArrayList<>();
            for (Token t : exprlistTokens) {
                exprlistTokensAsString.add(t.getText());
            }

            ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
            HashMap<String, Integer> parentSuitePointerMap;
            if (firstSuiteParent != null) {
                parentSuitePointerMap = new HashMap<>(getTempSSAPointerMap(firstSuiteParent));
            } else {
                parentSuitePointerMap = new HashMap<>(ssaPointerMap);
            }
            String testList = "";
            ArrayList<Token> testListTokens = getRuleTokens(ctx);
            Set<String> loopPredNames = getLoopRulePredNameMap(ctx.getParent());
            for (int i = 0; i< testListTokens.size(); i++) {
                if (tokenTable[testListTokens.get(i).getType()].equals("NAME") &&
                        ((i == 0)|| (i > 0 && !tokenTable[testListTokens.get(i - 1).getType()].equals("DOT")))) {
                    String id = testListTokens.get(i).getText();
                    if (globalIdsMap.containsKey(id)) {
                        String ssa;
                        if (firstSuiteParent == null && exprlistTokensAsString.contains(testListTokens.get(i).getText())) {
                            ssa = globalIdsMap.get(id).get(parentSuitePointerMap.get(id) - 1);
                        } else {
                            if (i + 1 < testListTokens.size() && testListTokens.get(i + 1).getText().equals("=") &&
                                    parentSuitePointerMap.get(id) == -1 ) {
                                ssa = testListTokens.get(i).getText();
                            } else {
                                ssa = globalIdsMap.get(id).get(parentSuitePointerMap.get(id));
                            }
                        }
                        if (ssa.contains("?")) {
                            ssa = ssa.substring(0, ssa.indexOf('?'));
                        }
                        testList += ssa;
                        loopPredNames.add(ssa);
                        continue;
                    }
                }
                testList += testListTokens.get(i).getText();
            }
            testList += ":";
            out.write(testList);
            out.write("\n");
        }


    }

    private void addLambdaRange(ArrayList<Token> tokens, int j, ArrayList<Pair> lambdaRanges) {

        int right = j + 1;
        int rightIndicator = 0;
        while (right < tokens.size()) {


            if (tokenTable[tokens.get(right).getType()].equals("OPEN_BRACE") || tokenTable[tokens.get(right).getType()].equals("OPEN_PAREN") ||
                    tokenTable[tokens.get(right).getType()].equals("OPEN_BRACK")) {
                rightIndicator--;
            }
            if (tokenTable[tokens.get(right).getType()].equals("CLOSE_BRACE") || tokenTable[tokens.get(right).getType()].equals("CLOSE_PAREN") ||
                    tokenTable[tokens.get(right).getType()].equals("CLOSE_BRACK")) {
                rightIndicator++;
            }
            if (rightIndicator == 1) {
                break;
            }

            right++;
        }
        lambdaRanges.add(new Pair(j, right));
    }


    private void addComp_forRange(ArrayList<Token> tokens, int j, ArrayList<Pair> comp_forRanges) {
        int left = j - 1;
        int right = j + 1;
        int leftIndicator = 0;
        int rightIndicator = 0;
        while (true) {
            if (tokenTable[tokens.get(left).getType()].equals("OPEN_BRACE") || tokenTable[tokens.get(left).getType()].equals("OPEN_PAREN") ||
                    tokenTable[tokens.get(left).getType()].equals("OPEN_BRACK")) {
                leftIndicator--;
            }
            if (tokenTable[tokens.get(left).getType()].equals("CLOSE_BRACE") || tokenTable[tokens.get(left).getType()].equals("CLOSE_PAREN") ||
                    tokenTable[tokens.get(left).getType()].equals("CLOSE_BRACK")) {
                leftIndicator++;
            }

            if (leftIndicator == -1) {
                break;
            }

            left--;
        }
        while (true) {
            if (tokenTable[tokens.get(right).getType()].equals("OPEN_BRACE") || tokenTable[tokens.get(right).getType()].equals("OPEN_PAREN") ||
                    tokenTable[tokens.get(right).getType()].equals("OPEN_BRACK")) {
                rightIndicator--;
            }
            if (tokenTable[tokens.get(right).getType()].equals("CLOSE_BRACE") || tokenTable[tokens.get(right).getType()].equals("CLOSE_PAREN") ||
                    tokenTable[tokens.get(right).getType()].equals("CLOSE_BRACK")) {
                rightIndicator++;
            }
            if (rightIndicator == 1) {
                break;
            }

            right++;
        }
        comp_forRanges.add(new Pair(left, right));
    }

    private int indexOfLambdaList(int j, ArrayList<Pair> lambdaRanges) {
        for (Pair p : lambdaRanges) {
            if (j >= p.start && j <= p.end) {
                return lambdaRanges.indexOf(p);
            }
        }
        return -1;
    }

    private int indexOfComp_forList(int j, ArrayList<Pair> comp_forRanges) {
        for (Pair p : comp_forRanges) {
            if (j >= p.start && j <= p.end) {
                return comp_forRanges.indexOf(p);
            }
        }
        return -1;
    }

    private ArrayList<String> getAllQualifiedComp_fors(int j, ArrayList<Pair> comp_forRanges, ArrayList<ArrayList<String>> nodeComp_forLists) {
        ArrayList<String> allQualifiedComp_fors = new ArrayList<>();
        for (int i = 0; i < comp_forRanges.size(); i++) {
            if (i > 0 && comp_forRanges.get(i).start >= comp_forRanges.get(i - 1).start &&
                    comp_forRanges.get(i).end <= comp_forRanges.get(i - 1).end) {
                continue;
            }
            if (j >= comp_forRanges.get(i).start && j <= comp_forRanges.get(i).end) {
                if (i > 0 && comp_forRanges.get(i).start == comp_forRanges.get(i - 1).start &&
                        comp_forRanges.get(i).end == comp_forRanges.get(i - 1).end) {
                    continue;
                }

                allQualifiedComp_fors.addAll(nodeComp_forLists.get(i));
            }
        }
        return allQualifiedComp_fors;
    }
    public void enterExpr_stmt(PythonParser.Expr_stmtContext ctx) throws IOException {
    }
    private boolean isInBrack(int j, ArrayList<Pair> brackRangeList) {
        for (Pair brackRange : brackRangeList) {
            if (j >= brackRange.start && j <= brackRange.end) {
                return true;
            }
        }
        return false;
    }

    public void exitExpr_stmt(PythonParser.Expr_stmtContext ctx) throws IOException {
        //get temp pointer map from parent suite or main flow
        ArrayList<String> causalList = new ArrayList<>();
        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> firstSuiteParentPointerMap;
        if (firstSuiteParent == null) {
            firstSuiteParentPointerMap = new HashMap<>(ssaPointerMap);
        } else {
            firstSuiteParentPointerMap = getTempSSAPointerMap(firstSuiteParent);
        }

        ArrayList<ArrayList<String>> nodeComp_forLists = getNodeComp_forLists(ctx);

        for (ArrayList<String> nodeComp_forList : nodeComp_forLists) {
            for (String ssa : nodeComp_forList) {
                String id = ssa.substring(0, ssa.lastIndexOf('_'));
                globalIdsMap.get(id).remove(globalIdsMap.get(id).indexOf(ssa));
            }
        }

        //augassign
        String output = "";
        if (ctx.augassign() != null) {
            //this if may not necessary
            ArrayList<Token> tokens = getRuleTokens(ctx.testlist_star_expr(0));

            String newRightPart = "";
            for (int i = 0; i < tokens.size(); i++) {
                if (tokenTable[tokens.get(i).getType()].equals("NAME") && (i == 0 || !tokenTable[tokens.get(i - 1).getType()].equals("DOT"))) {
                    String name = tokens.get(i).getText();
                    if (globalIdsMap.containsKey(name)) {
                        String idInSSA;
                        if (firstSuiteParentPointerMap.get(name) == -1 && functionNames.contains(name)) {
                            idInSSA = tokens.get(i).getText();
                        } else {
                            if (i + 1 < tokens.size() && tokens.get(i + 1).getText().equals("=") &&
                                    firstSuiteParentPointerMap.get(name) == -1 ) {
                                idInSSA = tokens.get(i).getText();
                            } else {

                                idInSSA = globalIdsMap.get(name).get(firstSuiteParentPointerMap.get(name));
                            }
                        }
                        if (idInSSA.contains("?")) {
                            idInSSA = idInSSA.substring(0, idInSSA.indexOf('?'));
                        }
                        causalList.add(idInSSA);
                        newRightPart += idInSSA;
                        continue;
                    }
                }
                newRightPart += tokens.get(i).getText();

            }
            output += newRightPart;
            String operator = ctx.augassign().getText().substring(0, ctx.augassign().getText().indexOf('='));
            output += operator;
            String suboutput = "";
            tokens = getRuleTokens(ctx.testlist());
            for (int i = 0; i < tokens.size(); i++) {
                if (tokenTable[tokens.get(i).getType()].equals("NAME") && (i == 0 || !tokenTable[tokens.get(i - 1).getType()].equals("DOT"))) {
                    String name = tokens.get(i).getText();
                    if (globalIdsMap.containsKey(name)) {
                        String idInSSA;
                        if (firstSuiteParentPointerMap.get(name) == -1 && functionNames.contains(name)) {
                            idInSSA = tokens.get(i).getText();
                        } else {
                            if (i + 1 < tokens.size() && tokens.get(i + 1).getText().equals("=") &&
                                    firstSuiteParentPointerMap.get(name) == -1 ) {
                                idInSSA = tokens.get(i).getText();
                            } else {

                                idInSSA = globalIdsMap.get(name).get(firstSuiteParentPointerMap.get(name));
                            }
                        }
                        if (idInSSA.contains("?")) {
                            idInSSA = idInSSA.substring(0, idInSSA.indexOf('?'));
                        }
                        causalList.add(idInSSA);
                        suboutput += idInSSA;
                        continue;
                    }
                }
                if (tokens.get(i).getText().equals("for")) {
                    suboutput += " for ";
                    continue;
                }
                if (tokens.get(i).getText().equals("or")) {

                    suboutput += " or ";
                    continue;
                }
                if (tokens.get(i).getText().equals("in")) {
                    suboutput += " in ";
                    continue;
                }
                suboutput += tokens.get(i).getText();
            }
            output += suboutput;
            String newLeftPart = ctx.testlist_star_expr(0).getText();

            //temp fix
            if (newLeftPart.matches("[a-zA-Z_][a-zA-Z0-9_]*") &&
                    globalIdsMap.containsKey(newLeftPart)) {
                String idSSA = newLeftPart;
                newLeftPart = globalIdsMap.get(newLeftPart).get(ssaPointerMap.get(newLeftPart) + 1);
                causalMap.put(newLeftPart, causalList);
                ssaPointerMap.put(idSSA, ssaPointerMap.get(idSSA) + 1);
                //no + 1
                firstSuiteParentPointerMap.put(idSSA, ssaPointerMap.get(idSSA));
            } else {
                newLeftPart = newRightPart;
            }

            output = newLeftPart + " = " + output;

            out.write(output);
        } else {

            //normal assign
            if (ctx.testlist_star_expr().size() > 1) {
                List<PythonParser.Testlist_star_exprContext> testListStarExprContexts = ctx.testlist_star_expr();
                for (int i = testListStarExprContexts.size() - 1; i >= 0; i--) {
                    ArrayList<Token> tokens = getRuleTokens(testListStarExprContexts.get(i));
                    //identify []
                    ArrayList<Pair> brackRangeList = new ArrayList<>();

                    if (i != testListStarExprContexts.size() - 1) {
                        int cnt = 0;
                        for (int j = 0; j < tokens.size(); j++) {
                            if (tokens.get(j).getText().equals("[")) {
                                int k = j + 1;
                                cnt++;
                                while (k < tokens.size() && cnt != 0) {
                                    if (tokens.get(k).getText().equals("[")) {
                                        cnt++;
                                    }
                                    if (tokens.get(k).getText().equals("]")) {
                                        cnt--;
                                    }
                                    k++;
                                }

                                brackRangeList.add(new Pair(j + 1, k - 1));
                                j = k;
                            }
                        }
                    }
                    String suboutput = "";


                    ArrayList<Pair> comp_forRanges = new ArrayList<>();
                    for (int j = 0; j < tokens.size(); j++) {
                        if (tokenTable[tokens.get(j).getType()].equals("FOR")) {
                            //find parentheses surround for
                            addComp_forRange(tokens ,j, comp_forRanges);
                        }
                    }

                    ArrayList<Pair> lambdaRanges = new ArrayList<>();
                    for (int j = 0; j < tokens.size(); j++) {
                        if (tokenTable[tokens.get(j).getType()].equals("LAMBDA")) {
                            //find parentheses surround for
                            addLambdaRange(tokens ,j, lambdaRanges);
                        }
                    }
                    ArrayList<ArrayList<String>> nodeLambdaNames = new ArrayList<>();
                    for (int j = 0; j < tokens.size(); j++) {
                        //handling lambda in assign
                        if (tokenTable[tokens.get(j).getType()].equals("LAMBDA")) {
                            ArrayList<String> namesAfterLambda = new ArrayList<>();
                            suboutput += " ";
                            int k = 0;
                            while (!tokenTable[tokens.get(j + k).getType()].equals("COLON")) {
                                if (tokenTable[tokens.get(j + k).getType()].equals("NAME")) {
                                    String id = tokens.get(j + k).getText();
                                    String ssa = globalIdsMap.get(id).get(ssaPointerMap.get(id) + 1);
                                    //should not contain "?"
                                    ssaPointerMap.put(id, ssaPointerMap.get(id) + 1);
                                    firstSuiteParentPointerMap.put(id, ssaPointerMap.get(id));
                                    namesAfterLambda.add(ssa);
                                    suboutput += ssa;
                                    suboutput += " ";
                                    k++;

                                    continue;
                                }
                                suboutput += tokens.get(j + k).getText();
                                suboutput += " ";
                                k++;

                            }
                            nodeLambdaNames.add(namesAfterLambda);
                            j = j + k - 1;
                            continue;
                        }
                        boolean isLambda = false;
                        if (tokenTable[tokens.get(j).getType()].equals("NAME") && indexOfLambdaList(j, lambdaRanges) > -1) {
                            String id = tokens.get(j).getText();

                            ArrayList<String> namesAfterLambda = nodeLambdaNames.get(indexOfLambdaList(j, lambdaRanges));

                            for (String name : namesAfterLambda) {

                                if (name.substring(0, name.indexOf('_')).equals(id)) {
                                    suboutput += name;
                                    isLambda = true;
                                }
                            }
                        }
                        if (isLambda) {
                            continue;
                        }


                        if (!isLambda && tokenTable[tokens.get(j).getType()].equals("NAME") && (j == 0 || !tokenTable[tokens.get(j - 1).getType()].equals("DOT"))) {
                            String id = tokens.get(j).getText();
                            //names in comp_for
                            boolean isComp_forName = false;
                            if (indexOfComp_forList(j, comp_forRanges) != -1) {
//                                System.out.println(indexOfComp_forList(j, comp_forRanges) + " " + nodeComp_forLists.size() + " " + j);
                                //ArrayList<String> comp_forList = nodeComp_forLists.get(indexOfComp_forList(j, comp_forRanges));
                                System.out.println(nodeComp_forLists);
                                ArrayList<String> comp_forList = getAllQualifiedComp_fors(j, comp_forRanges, nodeComp_forLists);
                                for (String ssa : comp_forList) {
                                    if (tokens.get(j).getText().equals(ssa.substring(0, ssa.lastIndexOf('_')))) {
                                        isComp_forName = true;
                                        suboutput += ssa;

                                        break;
                                    }
                                }

                            }
                            if (isComp_forName) {
                                continue;
                            }

                            //not in comp_for range and lambda range
                            if (globalIdsMap.containsKey(id)) {

                                //temp fix for key-worded parameter!!!!
                                if (j + 2 < tokens.size() && j - 1 >= 0 && tokens.get(j + 1).getText().equals("=")
                                        && tokenTable[tokens.get(j + 2).getType()].equals("NAME")
                                        && (tokens.get(j - 1).getText().equals(",") || tokens.get(j - 1).getText().equals("("))) {
                                    suboutput += id;
                                    continue;
                                }
                                //right of assign
                                if (i == testListStarExprContexts.size() - 1) {
                                    String ssa;
                                    //funtion name in right side
                                    if (firstSuiteParentPointerMap.get(id) == -1 &&
                                            (functionNames.contains(id) || tokens.get(j + 1).getText().equals("("))) {
                                        ssa = tokens.get(j).getText();
                                    } else {
                                        if (j + 1 < tokens.size() && tokens.get(j + 1).getText().equals("=") &&
                                                firstSuiteParentPointerMap.get(id) == -1 ) {
                                            ssa = tokens.get(j).getText();
                                        } else {
                                            System.out.println(ctx.getText());
                                            System.out.println(id);
                                            ssa = globalIdsMap.get(id).get(firstSuiteParentPointerMap.get(id));
                                        }
                                    }

                                    if (ssa.contains("?")) {
                                        ssa = ssa.substring(0, ssa.indexOf('?'));
                                    }
                                    causalList.add(ssa);
                                    suboutput += ssa;
                                    continue;
                                } else { //left of assign : name + 1
                                    String ssa;
                                    boolean isDef = false;
                                    if ((j + 1 < tokens.size() && tokens.get(j + 1).getText().equals(".")) ||
                                            (j + 1 < tokens.size() && tokens.get(j + 1).getText().equals("[")) ||
                                            (isInBrack(j, brackRangeList))) {

                                        ssa = globalIdsMap.get(id).get(firstSuiteParentPointerMap.get(id));
                                    } else {

                                        ssa = globalIdsMap.get(id).get(ssaPointerMap.get(id) + 1);
                                        isDef = true;
                                    }
                                    if (ssa.contains("?")) {
                                        ssa = ssa.substring(0, ssa.indexOf('?'));
                                    }
                                    suboutput += ssa;
                                    if (isDef) {
                                        causalMap.put(ssa, causalList);
                                        ssaPointerMap.put(id, ssaPointerMap.get(id) + 1);
                                        //no + 1
                                        firstSuiteParentPointerMap.put(id, ssaPointerMap.get(id));
                                    }
                                    continue;
                                }
                            } else {
                                suboutput += id;
                                continue;
                            }
                        }
                        if (tokens.get(j).getText().equals("if")) {
                            suboutput += " if ";
                            continue;
                        }
                        if (tokens.get(j).getText().equals("not")) {
                            suboutput += " not ";
                            continue;
                        }
                        if (tokens.get(j).getText().equals("else")) {
                            suboutput += " else ";
                            continue;
                        }
                        if (tokens.get(j).getText().equals("None")) {
                            suboutput += " None ";
                            continue;
                        }
                        if (tokens.get(j).getText().equals("is")) {
                            suboutput += " is ";
                            continue;
                        }
                        if (tokens.get(j).getText().equals("for")) {
                            suboutput += " for ";
                            continue;
                        }
                        if (tokens.get(j).getText().equals("or")) {

                            suboutput += " or ";
                            continue;
                        }
                        if (tokens.get(j).getText().equals("in")) {
                            suboutput += " in ";
                            continue;
                        }
                        if (tokens.get(j).getText().equals("and")) {
                            suboutput += " and ";
                            continue;
                        }

                        suboutput += tokens.get(j).getText();
                    }
                    output = suboutput + ("=" + output);
                }
                output = output.substring(0, output.length() - 1);
            } else { //no assign, normal expr
                ArrayList<Token> tokens = getRuleTokens(ctx.testlist_star_expr().get(0));
                ArrayList<Pair> comp_forRanges = new ArrayList<>();
                for (int i = 0; i < tokens.size(); i++) {
                    if (tokenTable[tokens.get(i).getType()].equals("FOR")) {
                        //find parentheses surround for
                        addComp_forRange(tokens ,i, comp_forRanges);
                    }
                }
                for (int i = 0; i < tokens.size();i++) {
                    if (tokenTable[tokens.get(i).getType()].equals("NAME") &&
                            ((i == 0)|| (i > 0 && !tokenTable[tokens.get(i - 1).getType()].equals("DOT"))) && !isSpecial(tokens.get(i).getText())) {
                        String id = tokens.get(i).getText();
                        //names in comp_for
                        boolean isComp_forName = false;
                        if (indexOfComp_forList(i, comp_forRanges) != -1) {
                            ArrayList<String> comp_forList = getAllQualifiedComp_fors(i, comp_forRanges, nodeComp_forLists);
                            for (String ssa : comp_forList) {
                                if (tokens.get(i).getText().equals(ssa.substring(0, ssa.lastIndexOf('_')))) {
                                    isComp_forName = true;
                                    output += ssa;
                                    break;
                                }
                            }
                        }
                        if (isComp_forName) {
                            continue;
                        }
                        if (globalIdsMap.containsKey(id)) {
                            //temp fix for key worded parameter!!!!
                            if (i + 2 < tokens.size() && i - 1 >= 0 && tokens.get(i + 1).getText().equals("=")
                                    && tokenTable[tokens.get(i + 2).getType()].equals("NAME")
                                    && (tokens.get(i - 1).getText().equals(",") || tokens.get(i - 1).getText().equals("("))) {
                                output += id;
                                continue;
                            }
                            String idSSA;
                            if (firstSuiteParentPointerMap.get(id) == -1 && functionNames.contains(id)) {
                                idSSA = tokens.get(i).getText();
                            } else {

                                if (i + 1 < tokens.size() && tokens.get(i + 1).getText().equals("=") &&
                                        firstSuiteParentPointerMap.get(id) == -1 ) {
                                    idSSA = tokens.get(i).getText();
                                } else {
                                    idSSA = globalIdsMap.get(id).get(firstSuiteParentPointerMap.get(id));
                                }
                            }

                            if (idSSA.contains("?")) {
                                idSSA= idSSA.substring(0, idSSA.indexOf('?'));
                            }
                            output += idSSA;
                            continue;
                        }

                    }
                    if (tokens.get(i).getText().equals("if")) {
                        output += " if ";
                        continue;
                    }
                    if (tokens.get(i).getText().equals("not")) {
                        output += " not ";
                        continue;
                    }
                    if (tokens.get(i).getText().equals("else")) {
                        output += " else ";
                        continue;
                    }
                    if (tokens.get(i).getText().equals("for")) {
                        output += " for ";
                        continue;
                    }
                    if (tokens.get(i).getText().equals("or")) {
                        output += " or ";
                        continue;
                    }
                    if (tokens.get(i).getText().equals("lambda")) {
                        output += " lambda ";
                        continue;
                    }
                    if (tokens.get(i).getText().equals("in")) {
                        output += " in ";
                        continue;
                    }
                    output += tokens.get(i).getText();
                }
            }
            out.write(output);
            out.write(" ");
        }
    }

    public void enterTestlist_star_expr(PythonParser.Testlist_star_exprContext ctx) {
        setRuleTokens(ctx, new ArrayList<>());
    }

    public void enterParameters(PythonParser.ParametersContext ctx) throws IOException {
        setRuleTokens(ctx, new ArrayList<>());
    }
    public void exitParameters(PythonParser.ParametersContext ctx) throws IOException {
        HashMap<String, Integer> fundefPointerMap = getTempSSAPointerMap(ctx.getParent());
        String output = "";
        String paramString = "";
        ArrayList<Token> tokens = getRuleTokens(ctx);
        for (Token t : tokens) {
            System.out.println(t.getText());
        }
        Set<String> pointersTobeUpdate = new HashSet<>();
        for (int i = 0; i < tokens.size(); i++) {
            System.out.println(tokens.get(i).getText() + " type is " + tokenTable[tokens.get(i).getType()] + " " +
                    globalIdsMap.containsKey(tokens.get(i).getText()));
            if (tokenTable[tokens.get(i).getType()].equals("NAME") && !tokens.get(i - 1).getText().equals(".")) {
                //possibly defs
                if (tokens.get(i - 1).getText().equals(",") || tokens.get(i - 1).getText().equals("(")
                        || tokens.get(i - 1).getText().contains("*")) {
                    String id = tokens.get(i).getText();

                    if (globalIdsMap.containsKey(id)) {
                        //output += (globalIdsMap.get(id).get(ssaPointerMap.get(id) + 1));
                        output += tokens.get(i).getText();

                        String newId = globalIdsMap.get(id).get(ssaPointerMap.get(id) + 1);
                        paramString += (newId + " = " + tokens.get(i).getText() + ";");
                        pointersTobeUpdate.add(id);
                        continue;
                    }
                } else {
                    //possibly uses
                    String id = tokens.get(i).getText();

                    if (globalIdsMap.containsKey(id)) {
                        System.out.println(id + "///");
                        output += (globalIdsMap.get(id).get(fundefPointerMap.get(id)));
                        continue;
                    }
                }
            }
            output += tokens.get(i).getText();
        }
        //update pointer
        for (String id : pointersTobeUpdate) {
            ssaPointerMap.put(id,ssaPointerMap.get(id) + 1 );
            fundefPointerMap.put(id, ssaPointerMap.get(id));
        }
        output += ":";
        out.write(output);

        tabCount++;
        out.write("\n");
        writeTabs();
        out.write(paramString);
        tabCount--;

    }
    //need to be revised
    public void enterDecorator(PythonParser.DecoratorContext ctx) throws IOException {
        writeTabs();
        out.write("@" + ctx.dotted_name().getText());

    }

    public void enterFuncdef(PythonParser.FuncdefContext ctx) throws IOException {
        if (ctx.getParent() instanceof  PythonParser.DecoratedContext &&
                ((PythonParser.DecoratedContext) ctx.getParent()).decorators() != null) {
            writeTabs();
        }

        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> pointerMap = null;
        if (firstSuiteParent != null) {
            pointerMap = new HashMap<>(getTempSSAPointerMap(firstSuiteParent));
        } else {
            pointerMap = new HashMap<>(ssaPointerMap);
        }
        HashMap<String, Integer> fundefTempPointerMap = new HashMap<>(pointerMap);
        setTempSSAPointerMap(ctx, fundefTempPointerMap);

        String output = "";
        output += "def ";
        output += (ctx.getChild(1));
        out.write(output);
    }
    public void exitFuncdef(PythonParser.FuncdefContext ctx) throws IOException {
        HashMap<String, Integer> funcNodePointerMap = getTempSSAPointerMap(ctx);
        HashMap<String, Integer> funcNodePointerMapCopy = new HashMap<>(funcNodePointerMap);
        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> funcSuitePointerMap = new HashMap<>(getTempSSAPointerMap(ctx.suite()));
        updatePoninterMap(funcNodePointerMap, funcSuitePointerMap);

        HashMap<String, Integer> parentSuitePointerMap;
        if (firstSuiteParent == null) {
            parentSuitePointerMap = ssaPointerMap;
        } else {
            parentSuitePointerMap = getTempSSAPointerMap(firstSuiteParent);
        }

        updatePoninterMap(parentSuitePointerMap, funcNodePointerMap);
        out.write("\n");
    }

    public void enterWhile_stmt(PythonParser.While_stmtContext ctx) throws IOException {

        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> pointerMap = null;
        if (firstSuiteParent != null) {
            pointerMap = new HashMap<>(getTempSSAPointerMap(firstSuiteParent));
        } else {
            pointerMap = new HashMap<>(ssaPointerMap);
        }
        HashMap<String, Integer> whileTempPointerMap = new HashMap<>(pointerMap);
        setTempSSAPointerMap(ctx, whileTempPointerMap);

        String phiLine = "phi" + phiHolder.size();
        phiHolder.push(phiLine);
        phiLine += " = Phi()";
        out.write(phiLine + "\n");
        writeTabs();

        String whileLine = "while ";
        out.write(whileLine);

    }


    public void enterFor_stmt(PythonParser.For_stmtContext ctx) throws IOException {

        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> parentSuitePointerMap;
        if (firstSuiteParent != null) {
            parentSuitePointerMap = new HashMap<>(getTempSSAPointerMap(firstSuiteParent));
        } else {
            parentSuitePointerMap = new HashMap<>(ssaPointerMap);
        }
        HashMap<String, Integer> forTempPointerMap = new HashMap<>(parentSuitePointerMap);
        setTempSSAPointerMap(ctx, forTempPointerMap);
        //new phi obj
        String phiLine = "phi" + phiHolder.size();
        phiHolder.push(phiLine);
        phiLine += " = Phi()";
        out.write(phiLine + "\n");

        writeTabs();
        String forLine = "";
        forLine += "for ";
        out.write(forLine);

    }
    public void enterClassdef(PythonParser.ClassdefContext ctx) throws IOException {

        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> pointerMap;
        if (firstSuiteParent != null) {
            pointerMap = new HashMap<>(getTempSSAPointerMap(firstSuiteParent));
        } else {
            pointerMap = new HashMap<>(ssaPointerMap);
        }
        HashMap<String, Integer> classdefTempPointerMap = new HashMap<>(pointerMap);
        setTempSSAPointerMap(ctx, classdefTempPointerMap);

        String arglist = "";
        if (ctx.arglist() != null) {
            arglist += "(";
            arglist += ctx.arglist().getText();
            arglist += ")";
        }

        String output = "";
        output += "class ";
        output += (ctx.getChild(1));
        output += arglist;
        output += ":";
        out.write(output);
    }

    public void enterSuite(PythonParser.SuiteContext ctx) throws IOException {
        //get snapshot from parent if OR while
        HashMap<String, Integer> pointerMap = getTempSSAPointerMap(ctx.getParent());
        setTempSSAPointerMap(ctx, new HashMap<>(pointerMap));

        HashMap<String, Integer> suitetempSSAPointerMap = getTempSSAPointerMap(ctx);


        tabCount++;

        if (ctx.getParent() instanceof  PythonParser.FuncdefContext) {
            ParserRuleContext funcdefNode = ctx.getParent();
            HashMap<String, ArrayList<String>> funcDefMap = getNodeIdsMap(funcdefNode);
            ArrayList<String> collides = new ArrayList<>();
            for (String id : funcDefMap.keySet()) {
                for (String ssa : funcDefMap.get(id)) {
                    collides.add(ssa);
                }
            }
            String idInit ="";
            HashMap<String, ArrayList<String>> funcDefSuiteMap = getNodeIdsMap(ctx);
            for (String id : funcDefSuiteMap.keySet()) {
                for (String ssa : funcDefSuiteMap.get(id)) {
                    if (collides.contains(ssa)) {
                        continue;
                    }
                    if (ssa.contains("?")) {
                        ssa = ssa.substring(0, ssa.indexOf('?'));
                    }
                    idInit += (ssa + "=None;");
                }
            }
            out.write("\n");
            writeTabs();
            out.write(idInit);
            out.write("\n");
        }

        if (ctx.getParent() instanceof PythonParser.While_stmtContext ||
                ctx.getParent() instanceof PythonParser.For_stmtContext) {
            writeTabs();
            List<PythonParser.SuiteContext> suiteList = new ArrayList<>();
            if (ctx.getParent() instanceof PythonParser.While_stmtContext) {
                suiteList = ((PythonParser.While_stmtContext) ctx.getParent()).suite();
            }
            if (ctx.getParent() instanceof PythonParser.For_stmtContext) {
                suiteList = ((PythonParser.For_stmtContext) ctx.getParent()).suite();
            }
            if (suiteList.indexOf(ctx) != 1) {
                out.write(phiHolder.peek() + ".set()" + "\n");
            }

            //out.write(phiHolder.peek() + ".set()" + "\n");
            HashMap<String, ArrayList<String>> suiteMap = getNodeIdsMap(ctx);
            for (String id: suiteMap.keySet()) {
                if (suiteMap.get(id).get(0).contains("phiEntry") && globalIdsMap.containsKey(id)) {
                    ArrayList<String> causalList = new ArrayList<>();
                    String phiIdLine = suiteMap.get(id).get(0);
                    String idBeforeLoop = "None";
                    if (suitetempSSAPointerMap.get(id) >= 0) {
                        idBeforeLoop = globalIdsMap.get(id).get(suitetempSSAPointerMap.get(id));
                    }

                    if (!idBeforeLoop.equals("None") && !isInScope(ctx, idBeforeLoop)) {
                        idBeforeLoop = "None";
                    }
                    if (idBeforeLoop.contains("?")) {
                        idBeforeLoop = idBeforeLoop.substring(0, idBeforeLoop.indexOf('?'));
                    }
                    if (!idBeforeLoop.equals("None")) {
                        causalList.add(idBeforeLoop);
                    }

                    String idInLoop = phiIdLine.substring(phiIdLine.indexOf(',') + 1, phiIdLine.lastIndexOf(')'));
                    causalList.add(idInLoop);
                    causalList.addAll(getLoopRulePredNameMap(ctx.getParent()));
                    String phiEntryId = phiIdLine.substring(0, phiIdLine.indexOf('?'));
                    phiIdLine = phiIdLine.substring(0, phiIdLine.indexOf('?')) + " = " + phiHolder.peek() + ".phiEntry(" +
                            idBeforeLoop + phiIdLine.substring(phiIdLine.indexOf(','));
                    causalMap.put(phiEntryId, causalList);
                    addedPhiNames.add(phiEntryId);
                    writeTabs();
                    out.write(phiIdLine + "\n");
                    ssaPointerMap.put(id, ssaPointerMap.get(id)+1);
                    //
                    suitetempSSAPointerMap.put(id, ssaPointerMap.get(id));

                }
            }
        }
    }//

    public void enterRaise_stmt(PythonParser.Raise_stmtContext ctx) throws IOException {

        setRuleTokens(ctx, new ArrayList<>());
    }
    public void exitRaise_stmt(PythonParser.Raise_stmtContext ctx) throws IOException {
        String raiseLine = "";

        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> firstSuiteParentPointerMap;
        ArrayList<ArrayList<String>> nodeComp_forLists = getNodeComp_forLists(ctx);
        if (firstSuiteParent != null) {
            firstSuiteParentPointerMap = new HashMap<>(getTempSSAPointerMap(firstSuiteParent));
        } else {
            firstSuiteParentPointerMap = new HashMap<>(ssaPointerMap);
        }

        ArrayList<Token> tokens = getRuleTokens(ctx);
        for (int i = 0; i < tokens.size(); i++) {

            if (tokenTable[tokens.get(i).getType()].equals("NAME")) {
                String id = tokens.get(i).getText();
                if (globalIdsMap.containsKey(id) &&
                        ((i == 0)|| (i > 0 && !tokenTable[tokens.get(i - 1).getType()].equals("DOT")))) {

                    String ssa;
                    if (firstSuiteParentPointerMap.get(id) == -1) {
                        ssa = tokens.get(i).getText();
                    } else {
                        ssa = globalIdsMap.get(id).get(firstSuiteParentPointerMap.get(id));
                    }
                    if (ssa.contains("?")) {
                        ssa = ssa.substring(0, ssa.indexOf('?'));
                    }
                    raiseLine += ssa;
                    continue;
                }

            }
            if (tokens.get(i).getText().equals("raise")) {
                raiseLine += "raise ";
                continue;
            }
            if (tokens.get(i).getText().equals("from")) {
                raiseLine += " from ";
                continue;
            }
            raiseLine += tokens.get(i).getText();
        }
        out.write(raiseLine);

    }
    public void enterAssert_stmt(PythonParser.Assert_stmtContext ctx) throws IOException {

        setRuleTokens(ctx, new ArrayList<>());
    }

    public void exitAssert_stmt(PythonParser.Assert_stmtContext ctx) throws IOException {
        String assertLine = "";
        ArrayList<Token> tokens = getRuleTokens(ctx);
        for (int i = 0; i < tokens.size(); i++) {
            if (tokenTable[tokens.get(i).getType()].equals("NAME") &&
                    ((i == 0)|| (i > 0 && !tokenTable[tokens.get(i - 1).getType()].equals("DOT")))) {
                String id = tokens.get(i).getText();
                if (globalIdsMap.containsKey(id)) {
                    System.out.println(id + "***");
                    String ssa = globalIdsMap.get(id).get(ssaPointerMap.get(id));
                    if (ssa.contains("?")) {
                        ssa = ssa.substring(0, ssa.indexOf('?'));
                    }
                    assertLine += ssa;
                    continue;
                }

            }
            if (tokens.get(i).getText().equals("assert")) {
                assertLine += "assert ";
                continue;
            }
            if (tokens.get(i).getText().equals("None")) {
                assertLine += " None ";
                continue;
            }
            if (tokens.get(i).getText().equals("for")) {
                assertLine += " for ";
                continue;
            }
            if (tokens.get(i).getText().equals("in")) {
                assertLine += " in ";
                continue;
            }
            if (tokens.get(i).getText().equals("and")) {
                assertLine += " and ";
                continue;
            }
            if (tokens.get(i).getText().equals("is")) {
                assertLine += " is ";
                continue;
            }
            if (tokens.get(i).getText().equals("not")) {
                assertLine += " not ";
                continue;
            }
            assertLine += tokens.get(i).getText();
        }
        out.write(assertLine);
    }


    public void enterReturn_stmt(PythonParser.Return_stmtContext ctx) throws IOException {
        setRuleTokens(ctx, new ArrayList<>());

    }

    public void exitReturn_stmt(PythonParser.Return_stmtContext ctx) throws IOException {
        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> firstSuiteParentPointerMap;
        ArrayList<ArrayList<String>> nodeComp_forLists = getNodeComp_forLists(ctx);

        for (ArrayList<String> nodeComp_forList : nodeComp_forLists) {
            for (String ssa : nodeComp_forList) {
                String id = ssa.substring(0, ssa.lastIndexOf('_'));
                globalIdsMap.get(id).remove(globalIdsMap.get(id).indexOf(ssa));
            }
        }
        if (firstSuiteParent != null) {
            firstSuiteParentPointerMap = new HashMap<>(getTempSSAPointerMap(firstSuiteParent));
        } else {
            firstSuiteParentPointerMap = new HashMap<>(ssaPointerMap);
        }
        String returnLine = "";
        ArrayList<Token> tokens = getRuleTokens(ctx);

        ArrayList<Pair> comp_forRanges = new ArrayList<>();
        for (int j = 0; j < tokens.size(); j++) {
            if (tokenTable[tokens.get(j).getType()].equals("FOR")) {
                //find parentheses surround for
                addComp_forRange(tokens ,j, comp_forRanges);

            }
        }

        ArrayList<Pair> lambdaRanges = new ArrayList<>();
        for (int j = 0; j < tokens.size(); j++) {
            if (tokenTable[tokens.get(j).getType()].equals("LAMBDA")) {
                //find parentheses surround for
                addLambdaRange(tokens ,j, lambdaRanges);
            }
        }
        ArrayList<ArrayList<String>> nodeLambdaNames = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            //lambda
            if (tokenTable[tokens.get(i).getType()].equals("LAMBDA")) {
                ArrayList<String> namesAfterLambda = new ArrayList<>();
                returnLine += " ";
                int k = 0;
                HashSet<String> lambdaPointAddOneRecord = new HashSet<>();
                while (!tokenTable[tokens.get(i + k).getType()].equals("COLON")) {
                    if (tokenTable[tokens.get(i + k).getType()].equals("NAME")) {

                        String id = tokens.get(i + k).getText();
                        //lanmbda name needs to increment by 1
                        if (!lambdaPointAddOneRecord.contains(id)) {
                            ssaPointerMap.put(id, ssaPointerMap.get(id) + 1);
                            lambdaPointAddOneRecord.add(id);
                        }
                        String ssa = globalIdsMap.get(id).get(ssaPointerMap.get(id));
                        //should not contain "?"
                        firstSuiteParentPointerMap.put(id, ssaPointerMap.get(id));
                        namesAfterLambda.add(ssa);
                        returnLine += ssa;
                        returnLine += " ";
                        k++;
                        continue;
                    }
                    returnLine += tokens.get(i + k).getText();
                    returnLine += " ";
                    k++;

                }
                nodeLambdaNames.add(namesAfterLambda);
                i = i + k - 1;
                continue;
            }


            boolean isLambda = false;
            if (tokenTable[tokens.get(i).getType()].equals("NAME") && indexOfLambdaList(i, lambdaRanges) > -1) {
                String id = tokens.get(i).getText();
                ArrayList<String> namesAfterLambda = nodeLambdaNames.get(indexOfLambdaList(i, lambdaRanges));
                for (String name : namesAfterLambda) {
                    if (name.substring(0, name.indexOf('_')).equals(id)) {
                        returnLine += name;
                        isLambda = true;
                    }
                }
            }


            //for

            //names in comp_for
            boolean isComp_forName = false;
            if (!isLambda && tokenTable[tokens.get(i).getType()].equals("NAME") && indexOfComp_forList(i, comp_forRanges) != -1) {
                ArrayList<String> comp_forList = nodeComp_forLists.get(indexOfComp_forList(i, comp_forRanges));
                for (String ssa : comp_forList) {
                    String id = tokens.get(i).getText();
                    if (tokens.get(i).getText().equals(ssa.substring(0, ssa.lastIndexOf('_')))) {
                        isComp_forName = true;
                        returnLine += ssa;

                        break;
                    }
                }

            }
            if (isComp_forName) {
                continue;
            }


            if (!isLambda && tokenTable[tokens.get(i).getType()].equals("NAME")) {
                String id = tokens.get(i).getText();
                if (globalIdsMap.containsKey(id) &&
                        ((i == 0)|| (i > 0 && !tokenTable[tokens.get(i - 1).getType()].equals("DOT")))) {
                    System.out.println(id);
                    String ssa;
                    if (firstSuiteParentPointerMap.get(id) == -1) {
                        ssa = tokens.get(i).getText();
                    } else {
                        ssa = globalIdsMap.get(id).get(firstSuiteParentPointerMap.get(id));
                    }
                    if (ssa.contains("?")) {
                        ssa = ssa.substring(0, ssa.indexOf('?'));
                    }
                    returnLine += ssa;
                    continue;
                }

            }
            if (tokens.get(i).getText().equals("return")) {
                returnLine += "return ";
                continue;
            }
            if (tokens.get(i).getText().equals("for")) {
                returnLine += " for ";
                continue;
            }
            if (tokens.get(i).getText().equals("in")) {
                returnLine += " in ";
                continue;
            }
            if (tokens.get(i).getText().equals("if")) {
                returnLine += " if ";
                continue;
            }
            if (tokens.get(i).getText().equals("or")) {
                returnLine += " or ";
                continue;
            }
            if (tokens.get(i).getText().equals("and")) {
                returnLine += " and ";
                continue;
            }
            if (tokens.get(i).getText().equals("is")) {
                returnLine += " is ";
                continue;
            }
            if (tokens.get(i).getText().equals("not")) {
                returnLine += " not ";
                continue;
            }
            if (tokens.get(i).getText().equals("else")) {
                returnLine += " else ";
                continue;
            }
            if (!isLambda) {

                returnLine += tokens.get(i).getText();
            }
        }
        out.write(returnLine);
    }

    public void exitArglist(PythonParser.ArglistContext ctx) {

    }

    public void exitSuite(PythonParser.SuiteContext ctx) {
        tabCount--;
    }

    public void exitWhile_stmt(PythonParser.While_stmtContext ctx) throws IOException {
        //init merged pointer map as suite map
        HashMap<String, Integer> whileNodePointerMap = getTempSSAPointerMap(ctx);
        HashMap<String, Integer> whileNodePointerMapCopy = new HashMap<>(whileNodePointerMap);
        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);

        //HashMap<String, Integer> parentSuitePointerMap = getTempSSAPointerMap(firstSuiteParent);


        HashMap<String, Integer> whileSuitePointerMap = new HashMap<>(getTempSSAPointerMap(ctx.suite(0)));
        updatePoninterMap(whileNodePointerMap, whileSuitePointerMap);


        HashMap<String, ArrayList<String>> whileNodeMap = getNodeIdsMap(ctx);
        for (String id : whileNodeMap.keySet()) {
            ArrayList<String> causalList = new ArrayList<>();
            String lastSsa = whileNodeMap.get(id).get(whileNodeMap.get(id).size() - 1);
            if (lastSsa.contains("phiExit") && globalIdsMap.containsKey(id)) {

                String idBeforeLoop = "None";
                if (whileNodePointerMapCopy.get(id) >= 0) {
                    idBeforeLoop = globalIdsMap.get(id).get(whileNodePointerMapCopy.get(id));
                }
                if (!idBeforeLoop.equals("None") && !isInScope(ctx, idBeforeLoop)) {
                    idBeforeLoop = "None";
                }
                if (idBeforeLoop.contains("?")) {
                    idBeforeLoop = idBeforeLoop.substring(0, idBeforeLoop.indexOf('?'));
                }

                if (!idBeforeLoop.equals("None")) {
                    causalList.add(idBeforeLoop);
                }
                String phiIdLine = lastSsa;
                String idInLoop = phiIdLine.substring(phiIdLine.indexOf(',') + 1, phiIdLine.lastIndexOf(')'));
                causalList.add(idInLoop);
                causalList.addAll(getLoopRulePredNameMap(ctx));
                String phiExitId = phiIdLine.substring(0, phiIdLine.indexOf('?'));
                phiIdLine = phiExitId + " = " + phiHolder.peek() + ".phiExit(" +
                        idBeforeLoop + phiIdLine.substring(phiIdLine.indexOf(','));
                causalMap.put(phiExitId, causalList);
                addedPhiNames.add(phiExitId);
                writeTabs();
                out.write(phiIdLine + "\n");
                ssaPointerMap.put(id, ssaPointerMap.get(id) + 1);
                whileNodePointerMap.put(id, ssaPointerMap.get(id));

            }
        }
        phiHolder.pop();

        HashMap<String, Integer> parentSuitePointerMap;
        if (firstSuiteParent == null) {
            parentSuitePointerMap = ssaPointerMap;
        } else {
            parentSuitePointerMap = getTempSSAPointerMap(firstSuiteParent);
        }

        updatePoninterMap(parentSuitePointerMap, whileNodePointerMap);
    }

    public void exitFor_stmt(PythonParser.For_stmtContext ctx) throws IOException {

        //update for node pointer map with suite pointer map first
        HashMap<String, Integer> forNodePointerMap = getTempSSAPointerMap(ctx);
        HashMap<String, Integer> forNodePointerMapCopy = new HashMap<>(forNodePointerMap);
        HashMap<String,Integer> forSuitePointerMap = getTempSSAPointerMap(ctx.suite(0));
        HashMap<String, Integer> forElseSuitePointerMap = getTempSSAPointerMap(ctx.suite(1));
        updatePoninterMap(forNodePointerMap, forSuitePointerMap);

        HashMap<String, ArrayList<String>> ForNodeMap = getNodeIdsMap(ctx);
        for (String id : ForNodeMap.keySet()) {
            //then update with phi
            ArrayList<String> causalList = new ArrayList<>();
            String lastSsa = ForNodeMap.get(id).get(ForNodeMap.get(id).size() - 1);
            if (lastSsa.contains("phiExit") && globalIdsMap.containsKey(id)) {
                String idBeforeLoop = "None";

                if (forNodePointerMapCopy.get(id) >= 0) {
                    idBeforeLoop = globalIdsMap.get(id).get(forNodePointerMapCopy.get(id));
                }
                //String idBeforeLoop = globalIdsMap.get(id).get(forNodePointerMapCopy.get(id));
                if (idBeforeLoop.contains("?")) {
                    idBeforeLoop = idBeforeLoop.substring(0, idBeforeLoop.indexOf('?'));
                }
                if (!idBeforeLoop.equals("None") && !isInScope(ctx, idBeforeLoop)) {
                    idBeforeLoop = "None";
                }
                if (!idBeforeLoop.equals("None")) {
                    causalList.add(idBeforeLoop);
                }
                String phiIdLine = lastSsa;
                String idInLoop = phiIdLine.substring(phiIdLine.indexOf(',') + 1, phiIdLine.lastIndexOf(')'));
                causalList.add(idInLoop);
                causalList.addAll(getLoopRulePredNameMap(ctx));
                String phiExitId = phiIdLine.substring(0, phiIdLine.indexOf('?'));
                phiIdLine = phiExitId + " = " + phiHolder.peek() + ".phiExit(" +
                        idBeforeLoop + phiIdLine.substring(phiIdLine.indexOf(','));
                causalMap.put(phiExitId, causalList);
                addedPhiNames.add(phiExitId);
                writeTabs();
                out.write(phiIdLine + "\n");

                ssaPointerMap.put(id, ssaPointerMap.get(id) + 1);
                forNodePointerMap.put(id, ssaPointerMap.get(id));

            }
        }

        phiHolder.pop();
        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> parentSuitePointerMap;

        //don't new here
        if (firstSuiteParent == null) {
            parentSuitePointerMap  = ssaPointerMap;
        } else {
            parentSuitePointerMap = getTempSSAPointerMap(firstSuiteParent);
        }
        updatePoninterMap(parentSuitePointerMap, forNodePointerMap);


    }
    public void enterExcept_clause(PythonParser.Except_clauseContext ctx) {
        setRuleTokens(ctx, new ArrayList<>());
    }
    public void exitExcept_clause(PythonParser.Except_clauseContext ctx) throws IOException {
        //snap shot
        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> firstSuiteParentPointerMap;
        if (firstSuiteParent == null) {
            firstSuiteParentPointerMap = new HashMap<>(ssaPointerMap);
        } else {
            firstSuiteParentPointerMap = getTempSSAPointerMap(firstSuiteParent);
        }
        String exceptStr = "";
        ArrayList<Token> tokens = getRuleTokens(ctx);
        for (Token token : tokens) {
            if (tokenTable[token.getType()].equals("NAME")) {
                String id = token.getText();
                if (globalIdsMap.containsKey(id)) {
                    String ssa = globalIdsMap.get(id).get(firstSuiteParentPointerMap.get(id));
                    if (ssa.contains("?")) {
                        ssa = ssa.substring(0, ssa.indexOf('?'));
                    }
                    exceptStr += ssa;
                    continue;
                }
            }
            if (token.getText().equals("not")) {
                exceptStr += "not ";
                continue;
            }
            if (token.getText().equals("except")) {
                exceptStr += "except ";
                continue;
            }
            if (token.getText().equals("as")) {
                exceptStr += " as ";
                continue;
            }
            exceptStr += token.getText();
        }
        exceptStr += ":";
        writeTabs();

        out.write(exceptStr);
    }




    //write test in if
    //don't miss semicolon followed
    public void enterTest(PythonParser.TestContext ctx) {
        setRuleTokens(ctx, new ArrayList<>());
    }
    public void exitTest(PythonParser.TestContext ctx) throws IOException {
        //snap shot
        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> firstSuiteParentPointerMap;
        ArrayList<ArrayList<String>> nodeComp_forLists = getNodeComp_forLists(ctx);
        if (firstSuiteParent == null) {
            firstSuiteParentPointerMap = new HashMap<>(ssaPointerMap);
        } else {
            firstSuiteParentPointerMap = getTempSSAPointerMap(firstSuiteParent);
        }

        if (ctx.getParent() instanceof PythonParser.If_stmtContext )  {
            String testStr = "";
            ArrayList<Token> tokens = getRuleTokens(ctx);
            Set<String> ifPredNames = new HashSet<>();
            ArrayList<Pair> comp_forRanges = new ArrayList<>();
            for (int j = 0; j < tokens.size(); j++) {
                if (tokenTable[tokens.get(j).getType()].equals("FOR")) {
                    //find parentheses surround for
                    addComp_forRange(tokens ,j, comp_forRanges);

                }
            }
            for (int i = 0; i< tokens.size(); i++) {
                if (tokenTable[tokens.get(i).getType()].equals("NAME") &&
                        ((i == 0)|| (i > 0 && !tokenTable[tokens.get(i - 1).getType()].equals("DOT")))) {
                    String id = tokens.get(i).getText();

                    boolean isComp_forName = false;
                    if (indexOfComp_forList(i, comp_forRanges) != -1) {
                        System.out.println(id + " " + nodeComp_forLists.size());
                        ArrayList<String> comp_forList = nodeComp_forLists.get(indexOfComp_forList(i, comp_forRanges));
                        for (String ssa : comp_forList) {
                            if (tokens.get(i).getText().equals(ssa.substring(0, ssa.lastIndexOf('_')))) {
                                isComp_forName = true;
                                testStr += ssa;
                                break;
                            }
                        }
                        if (isComp_forName) {
                            continue;
                        }
                    }

                    if (globalIdsMap.containsKey(id)) {
                        String ssa;
                        if (i + 1 < tokens.size() && tokens.get(i + 1).getText().equals("=") &&
                                firstSuiteParentPointerMap.get(id) == -1 ) {
                            ssa = tokens.get(i).getText();
                        } else {
                            ssa = globalIdsMap.get(id).get(firstSuiteParentPointerMap.get(id));
                        }


                        if (ssa.contains("?")) {
                            ssa = ssa.substring(0, ssa.indexOf('?'));
                        }
                        testStr += ssa;
                        ifPredNames.add(ssa);
                        continue;
                    }
                }
                if (tokens.get(i).getText().equals("not")) {
                    testStr += " not ";
                    continue;
                }
                if (tokens.get(i).getText().equals("for")) {
                    testStr += " for ";
                    continue;
                }
                if (tokens.get(i).getText().equals("is")) {
                    testStr += " is ";
                    continue;
                }
                if (tokens.get(i).getText().equals("in")) {
                    testStr += " in ";
                    continue;
                }
                if (tokens.get(i).getText().equals("and")) {
                    testStr += " and ";
                    continue;
                }
                if (tokens.get(i).getText().equals("or")) {
                    testStr += " or ";
                    continue;
                }
                testStr += tokens.get(i).getText();
            }
            if (getNodeIdsMap(ctx.getParent()).size() != 0) {
                predHolder.add(testStr);
                ifPredNameMap.put(testStr, ifPredNames);
            }
            testStr += ":";
            out.write(testStr);

        }
        if (ctx.getParent() instanceof PythonParser.While_stmtContext) {

            String testStr = "";
            Set<String> loopPredNames = new HashSet<>();
            PythonParser.While_stmtContext whileNode = (PythonParser.While_stmtContext)ctx.getParent();
            HashMap<String, Integer>whileTempPointerMap = getTempSSAPointerMap(whileNode);
            HashMap<String, ArrayList<String>> whileSuiteMap = getNodeIdsMap(whileNode.suite(0));
            ParserRuleContext whileTestNode = whileNode.test();
            ArrayList<Token> whileTestTokens = getRuleTokens(whileTestNode);

            ArrayList<Pair> comp_forRanges = new ArrayList<>();
            for (int i = 0; i < whileTestTokens.size(); i++) {
                if (tokenTable[whileTestTokens.get(i).getType()].equals("FOR")) {
                    //find parentheses surround for
                    addComp_forRange(whileTestTokens ,i, comp_forRanges);
                }
            }

            for (int i = 0; i < whileTestTokens.size(); i++) {
                if (tokenTable[whileTestTokens.get(i).getType()].equals("NAME")) {
                    String oldName = whileTestTokens.get(i).getText();

                    boolean isComp_forName = false;
                    if (indexOfComp_forList(i, comp_forRanges) != -1) {

                        //ArrayList<String> comp_forList = nodeComp_forLists.get(indexOfComp_forList(j, comp_forRanges));
                        ArrayList<String> comp_forList = getAllQualifiedComp_fors(i, comp_forRanges, nodeComp_forLists);
                        for (String ssa : comp_forList) {
                            if (whileTestTokens.get(i).getText().equals(ssa.substring(0, ssa.lastIndexOf('_')))) {
                                isComp_forName = true;
                                testStr += ssa;
                                break;
                            }
                        }

                    }
                    if (isComp_forName) {
                        continue;
                    }

                    if (globalIdsMap.containsKey(oldName)) {
                        String newName;
                        if (oldName.equals("self") || i > 0 && tokenTable[whileTestTokens.get(i - 1).getType()].equals("DOT")) {
                            testStr += oldName;
                            continue;
                        }
                        if (!whileSuiteMap.containsKey(oldName)) {
                            newName = globalIdsMap.get(oldName).get(whileTempPointerMap.get(oldName));
                            if (newName.contains("?")) {
                                newName = newName.substring(0, newName.indexOf('?'));
                            }
                            loopPredNames.add(newName);
                        } else {

                            String phiIdLine = whileSuiteMap.get(oldName).get(0);
                            String idInLoop = phiIdLine.substring(phiIdLine.indexOf(',') + 1, phiIdLine.lastIndexOf(')'));

                            String idBeforeLoop = "None";
                            if (whileTempPointerMap.get(oldName) >= 0) {
                                idBeforeLoop = globalIdsMap.get(oldName).get(whileTempPointerMap.get(oldName));
                            }

                            if (!idBeforeLoop.equals("None") && !isInScope(ctx, idBeforeLoop)) {
                                idBeforeLoop = "None";
                            }
                            if (idBeforeLoop.contains("?")) {
                                idBeforeLoop = idBeforeLoop.substring(0, idBeforeLoop.indexOf('?'));
                            }
                            if (!idBeforeLoop.equals("None")) {
                                loopPredNames.add(idBeforeLoop);
                            }
                            loopPredNames.add(idInLoop);
                            newName = "phiLoopTest(" + idBeforeLoop + "," +idInLoop + ")";
                            newName = phiHolder.peek() + "." + newName;



//                            newName = whileSuiteMap.get(oldName).get(0);
//
//                            newName = newName.substring(newName.indexOf('?') + 1);
//                            if (newName.contains("?")) {
//                                newName = newName.substring(0, newName.indexOf('?'));
//                                newName += ")";
//                            }
//                            newName = phiHolder.peek() + "." + newName;
                        }
                        testStr += newName;
                        continue;
                    }
//                    //--------
//                    if (suiteList.indexOf(ctx) != 1) {
//                        out.write(phiHolder.peek() + ".set()" + "\n");
//                    }
//
//                    for (String id: suiteMap.keySet()) {
//                        if (suiteMap.get(id).get(0).contains("phiEntry") && globalIdsMap.containsKey(id)) {
//                            ArrayList<String> causalList = new ArrayList<>();
//                            String phiIdLine = suiteMap.get(id).get(0);
//                            String idBeforeLoop = "None";
//                            if (suitetempSSAPointerMap.get(id) >= 0) {
//                                idBeforeLoop = globalIdsMap.get(id).get(suitetempSSAPointerMap.get(id));
//                            }
//
//                            if (!idBeforeLoop.equals("None") && !isInScope(ctx, idBeforeLoop)) {
//                                idBeforeLoop = "None";
//                            }
//                            if (idBeforeLoop.contains("?")) {
//                                idBeforeLoop = idBeforeLoop.substring(0, idBeforeLoop.indexOf('?'));
//                            }
//                            if (!idBeforeLoop.equals("None")) {
//                                causalList.add(idBeforeLoop);
//                            }
//
//                            String idInLoop = phiIdLine.substring(phiIdLine.indexOf(',') + 1, phiIdLine.lastIndexOf(')'));
//                            causalList.add(idInLoop);
//                            causalList.addAll(getLoopRulePredNameMap(ctx.getParent()));
//                            String phiEntryId = phiIdLine.substring(0, phiIdLine.indexOf('?'));
//                            phiIdLine = phiIdLine.substring(0, phiIdLine.indexOf('?')) + " = " + phiHolder.peek() + ".phiEntry(" +
//                                    idBeforeLoop + phiIdLine.substring(phiIdLine.indexOf(','));
//                            causalMap.put(phiEntryId, causalList);
//                            addedPhiNames.add(phiEntryId);
//                            writeTabs();
//                            out.write(phiIdLine + "\n");
//                            ssaPointerMap.put(id, ssaPointerMap.get(id)+1);
//                            //
//                            suitetempSSAPointerMap.put(id, ssaPointerMap.get(id));
//
//                        }
//                    }
//
//
//
//                    //---------
                }

                if (whileTestTokens.get(i).getText().equals("not")) {
                    testStr += " not ";
                    continue;
                }
                if (whileTestTokens.get(i).getText().equals("for")) {
                    testStr += " for ";
                    continue;
                }
                if (whileTestTokens.get(i).getText().equals("is")) {
                    testStr += " is ";
                    continue;
                }
                if (whileTestTokens.get(i).getText().equals("in")) {
                    testStr += " in ";
                    continue;
                }
                if (whileTestTokens.get(i).getText().equals("and")) {
                    testStr += " and ";
                    continue;
                }
                if (whileTestTokens.get(i).getText().equals("or")) {
                    testStr += " or ";
                    continue;
                }
                testStr += whileTestTokens.get(i).getText();
            }

            if (testStr.contains("phiEntry")) {
                testStr = testStr.replace("phiEntry", "phiLoopTest");
            }

            setLoopRulePredNameMap(ctx.getParent(), loopPredNames);
            testStr += ":";
            out.write(testStr);
            out.write("\n");
        }
    }
    private ParserRuleContext findFirstSuiteParent(ParserRuleContext ctx) {
        ctx = ctx.getParent();
        while (ctx != null) {
            if (ctx instanceof PythonParser.SuiteContext) {
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }


    public void enterIf_stmt(PythonParser.If_stmtContext ctx) throws IOException {

        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> pointerMap = null;
        if (firstSuiteParent != null) {
            pointerMap = new HashMap<>(getTempSSAPointerMap(firstSuiteParent));
        } else {
            pointerMap = new HashMap<>(ssaPointerMap);
        }
        setTempSSAPointerMap(ctx, new HashMap<>(pointerMap));

    }
    private String findLastIdSSA(String id, ParserRuleContext ctx) {
        ctx = ctx.getParent();
        while (ctx != null) {
            if (ctx.getClass().getSimpleName().contains("Prog") || ctx.getClass().getSimpleName().contains("Suite") ||
                    ctx instanceof PythonParser.If_stmtContext || ctx instanceof PythonParser.Try_stmtContext) {
                HashMap<String, ArrayList<String>> nodeMap = getNodeIdsMap(ctx);
                if (nodeMap.containsKey(id)) {
                    ArrayList<String> ssaList = nodeMap.get(id);
                    return ssaList.get(ssaList.size() - 1);
                } else {
                    if (ctx instanceof PythonParser.SuiteContext &&
                            ctx.getParent() instanceof PythonParser.If_stmtContext) {
                        ctx = ctx.getParent();

                    }
                }
            }
            ctx = ctx.getParent();
        }
        return null;
    }

    public void exitIf_stmt(PythonParser.If_stmtContext ctx) throws IOException {


        HashMap<String, ArrayList<String>> ifNodeMap = getNodeIdsMap(ctx);
        //merge if pointer map to parent suite
        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> parentSuitePointerMap;
        if (firstSuiteParent != null) {
            parentSuitePointerMap = new HashMap<>(getTempSSAPointerMap(firstSuiteParent));
        } else {
            parentSuitePointerMap = new HashMap<>(ssaPointerMap);
        }
        HashMap<String, Integer> mergedIfTempPointerMap = new HashMap<>();
        ArrayList<HashMap<String, Integer>> ifSuiteMaps = new ArrayList<>();
        for (ParserRuleContext suiteNode:ctx.suite()) {
            ifSuiteMaps.add(getTempSSAPointerMap(suiteNode));
        }
        for (HashMap<String, Integer> ifSuiteMap : ifSuiteMaps) {
            updatePoninterMap(mergedIfTempPointerMap, ifSuiteMap);
        }
        updatePoninterMap(parentSuitePointerMap, mergedIfTempPointerMap);

        //generate if phi
        int preHolderPointer = predHolder.size() - 1;
        for (String id : ifNodeMap.keySet()) {

            if (ifNodeMap.get(id).get(ifNodeMap.get(id).size() - 1).contains("phiIf") && globalIdsMap.containsKey(id)) {
                ArrayList<String> causalList = new ArrayList<>();
                preHolderPointer = predHolder.size() - 1;
                //update merged pointer map

                //for (String ssa : ifNodeMap.get(id)) {
                String ssa = ifNodeMap.get(id).get(ifNodeMap.get(id).size() - 1);

                String rightPart = ssa.substring(ssa.indexOf('?') + 1);
                String paramPart = (rightPart.substring(rightPart.indexOf('(') + 1, rightPart.lastIndexOf(')')));
                //split(",(?![^()]*\\))");

                ArrayList<String> paramPartList = new ArrayList<>();
                for (int i = 0; i < paramPart.length(); i++) {
                    int j = i;
                    int countParen = 0;
                    while (j < paramPart.length()) {
                        if ((paramPart.charAt(j) == ',' && countParen == 0)) {
                            paramPartList.add(paramPart.substring(i, j));
                            i = j;
                            break;
                        }
                        if (paramPart.charAt(j) == '(') {
                            countParen++;
                        }
                        if (paramPart.charAt(j) == ')') {
                            countParen--;
                        }
                        if (paramPart.charAt(j) == '[') {
                            countParen++;
                        }
                        if (paramPart.charAt(j) == ']') {
                            countParen--;
                        }
                        if (paramPart.charAt(j) == '{') {
                            countParen++;
                        }
                        if (paramPart.charAt(j) == '}') {
                            countParen--;
                        }

                        j++;
                    }
                    if (j == paramPart.length()) {
                        paramPartList.add(paramPart.substring(i, j));
                        break;
                    }
                }
                String preds = "";
                String names = "phiNames = [";
                Set<String> predNamesSet = new HashSet<>();
                //trying to resolve the problem of phi entry injection
                for (int i = 0; i < paramPartList.size(); i++) {
                    if (i % 2 == 1 || i == paramPartList.size() - 1) {
                        String name = paramPartList.get(i);

                        if (i == paramPartList.size() - 1 && ctx.suite().size() == ctx.test().size()) {

                            //temp fix!!

                            if (getTempSSAPointerMap(firstSuiteParent).get(id) == -1) {
                                name = "None";
                            } else {
                                name = globalIdsMap.get(id).get(getTempSSAPointerMap(firstSuiteParent).get(id));
                                if (!isInScope(ctx, name)) {
                                    name = "None";
                                }
                            }
                        } else {

                            if (!getNodeIdsMap(ctx.suite().get(i / 2)).containsKey(id)) {
                                System.out.println(id);
                                //temp fix!!
                                if (getTempSSAPointerMap(firstSuiteParent).get(id) == -1) {
                                    name = "None";
                                } else {
                                    name = globalIdsMap.get(id).get(getTempSSAPointerMap(firstSuiteParent).get(id));
                                    if (!isInScope(ctx, name)) {
                                        name = "None";
                                    }
                                }
                            }

                        }
                        //}
                        if (name.contains("?")) {
                            name = name.substring(0, name.indexOf('?'));
                        }
                        if (!name.equals("None")) {
                            causalList.add(name);
                        }
                        names += name;
                        names += ",";
                    } else {
                        String pred = predHolder.get(preHolderPointer);
                        if (ifPredNameMap.containsKey(pred)) {
                            predNamesSet.addAll(ifPredNameMap.get(pred));
                        }
                        preds = pred + preds;
                        preHolderPointer--;
                        preds = "," + preds;

                    }
                }
                causalList.addAll(predNamesSet);
                preds = "phiPreds = [" + preds.substring(1) + "]";
                names = names.substring(0, names.length() - 1) + "]";
                writeTabs();
                out.write(preds);
                out.write("\n");
                writeTabs();
                out.write(names);
                out.write("\n");
                causalMap.put(ssa.substring(0, ssa.indexOf('?')), causalList);
                addedPhiNames.add(ssa.substring(0, ssa.indexOf('?')));
                String phiLine = ssa.substring(0, ssa.indexOf('?')) + "= phiIf(phiPreds, phiNames)";
                writeTabs();
                out.write(phiLine);
                out.write("\n");
                //ssaPointerMap.put(id, globalIdsMap.get(id).indexOf(ssa));
                ssaPointerMap.put(id, ssaPointerMap.get(id) + 1);

                getTempSSAPointerMap(firstSuiteParent).put(id, ssaPointerMap.get(id));
            }

        }
        int size = predHolder.size();
        for (int i = 0; i < size - 1 - preHolderPointer; i++) {
            predHolder.remove(predHolder.size() - 1);
        }
    }

    private void updatePoninterMap(HashMap<String, Integer> mergedIfTempPointerMap, HashMap<String, Integer> ifSuiteMap) {
        for (Map.Entry<String, Integer> entry : ifSuiteMap.entrySet()) {
            if (!mergedIfTempPointerMap.containsKey(entry.getKey())) {
                mergedIfTempPointerMap.put(entry.getKey(), entry.getValue());
            }
            if (entry.getValue() > mergedIfTempPointerMap.get(entry.getKey())) {
                mergedIfTempPointerMap.put(entry.getKey(), entry.getValue());
            }
        }
    }


    public void enterTry_stmt(PythonParser.Try_stmtContext ctx) {

        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> pointerMap;
        if (firstSuiteParent != null) {
            pointerMap = new HashMap<>(getTempSSAPointerMap(firstSuiteParent));
        } else {
            pointerMap = new HashMap<>(ssaPointerMap);
        }
        HashMap<String, Integer> tryTempPointerMap = new HashMap<>(pointerMap);
        setTempSSAPointerMap(ctx, tryTempPointerMap);

    }
    public void exitTry_stmt(PythonParser.Try_stmtContext ctx) {

        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> firstSuiteParentPointerMap;
        if (firstSuiteParent == null) {
            // no parent suite
            firstSuiteParentPointerMap = new HashMap<>(ssaPointerMap);
        } else {
            firstSuiteParentPointerMap = getTempSSAPointerMap(firstSuiteParent);
        }

        HashMap<String, Integer> mergedTryTempPointerMap = new HashMap<>();
        ArrayList<HashMap<String, Integer>> trySuiteMaps = new ArrayList<>();
        for (ParserRuleContext suiteNode:ctx.suite()) {
            trySuiteMaps.add(getTempSSAPointerMap(suiteNode));
        }
        for (HashMap<String, Integer> trySuiteMap : trySuiteMaps) {
            for (Map.Entry<String, Integer> entry : trySuiteMap.entrySet()) {
                System.out.println("printing out try write up..");
                System.out.println(entry.getKey() + " + "+ entry.getValue());
                if (!mergedTryTempPointerMap.containsKey(entry.getKey())) {
                    mergedTryTempPointerMap.put(entry.getKey(), -1);
                }
                if (entry.getValue() > mergedTryTempPointerMap.get(entry.getKey())) {
                    mergedTryTempPointerMap.put(entry.getKey(), entry.getValue());
                }
            }
        }
        for (Map.Entry<String, Integer> entry : mergedTryTempPointerMap.entrySet()) {

            if (!firstSuiteParentPointerMap.containsKey(entry.getKey())) {
                firstSuiteParentPointerMap.put(entry.getKey(), -1);
            }
            if (entry.getValue() > firstSuiteParentPointerMap.get(entry.getKey())) {
                firstSuiteParentPointerMap.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public void enterStmt(PythonParser.StmtContext ctx) throws IOException {
        if (ctx.compound_stmt() != null) {
            if (ctx.compound_stmt().decorated() != null) {
                return;
            }
        }
        writeTabs();

    }

    public void enterYield_expr(PythonParser.Yield_exprContext ctx) {
        setRuleTokens(ctx, new ArrayList<>());
    }
    public void exitYield_expr(PythonParser.Yield_exprContext ctx) throws IOException {
        String yieldLine = "";
        ArrayList<Token> tokens = getRuleTokens(ctx);
        for (Token token : tokens) {
            if (tokenTable[token.getType()].equals("NAME")) {
                String id = token.getText();
                if (globalIdsMap.containsKey(id)) {
                    String ssa = globalIdsMap.get(id).get(ssaPointerMap.get(id));
                    if (ssa.contains("?")) {
                        ssa = ssa.substring(0, ssa.indexOf('?'));
                    }
                    yieldLine += ssa;
                    continue;
                }

            }
            if (token.getText().equals("yield")) {
                yieldLine += "yield ";
                continue;
            }
            if (token.getText().equals("from")) {
                yieldLine += " from ";
                continue;
            }
            yieldLine += token.getText();
        }
        out.write(yieldLine);
    }
    public void enterExprlist(PythonParser.ExprlistContext ctx) {
        setRuleTokens(ctx, new ArrayList<>());
    }

    public void exitExprlist(PythonParser.ExprlistContext ctx) throws IOException {
        if (ctx.getParent() instanceof  PythonParser.For_stmtContext) {
            HashMap<String, Integer> forNodePointerMap = getTempSSAPointerMap(ctx.getParent());
            ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
            HashMap<String, Integer> parentSuitePointerMap;
            if (firstSuiteParent != null) {
                parentSuitePointerMap = new HashMap<>(getTempSSAPointerMap(firstSuiteParent));
            } else {
                parentSuitePointerMap = new HashMap<>(ssaPointerMap);
            }

            Set<String> loopPredNames = new HashSet<>();
            String exprList = "";
            ArrayList<Token> exprlistTokens = getRuleTokens(ctx);
            for (int i = 0; i < exprlistTokens.size(); i++) {
                if (tokenTable[exprlistTokens.get(i).getType()].equals("NAME") &&
                        ((i == 0) || (i > 0 && !tokenTable[exprlistTokens.get(i - 1).getType()].equals("DOT")))) {
                    String id = exprlistTokens.get(i).getText();
                    if (globalIdsMap.containsKey(id)) {
                        System.out.println("--------" + id + " " + ssaPointerMap.get(id));

                        String ssa = globalIdsMap.get(id).get(ssaPointerMap.get(id) + 1);
                        if (ssa.contains("?")) {
                            ssa = ssa.substring(0, ssa.indexOf('?'));
                        }
                        exprList += ssa;
                        loopPredNames.add(ssa);
                        ssaPointerMap.put(id, ssaPointerMap.get(id) + 1);
                        forNodePointerMap.put(id, ssaPointerMap.get(id));
                        continue;
                    }
                }
                exprList += exprlistTokens.get(i).getText();
            }
            exprList += " in ";
            out.write(exprList);
            setLoopRulePredNameMap(ctx.getParent(), loopPredNames);
        }
    }

    public void enterDel_stmt(PythonParser.Del_stmtContext ctx) {
    }
    public void exitDel_stmt(PythonParser.Del_stmtContext ctx) throws IOException {
        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, Integer> parentSuitePointerMap = getTempSSAPointerMap(firstSuiteParent);
        String delLine = "del ";
        ArrayList<Token> exprlistTokens = getRuleTokens(ctx.exprlist());
        for (int i = 0; i< exprlistTokens.size(); i++) {
            if (tokenTable[exprlistTokens.get(i).getType()].equals("NAME") &&
                    ((i == 0)|| (i > 0 && !tokenTable[exprlistTokens.get(i - 1).getType()].equals("DOT")))) {
                String id = exprlistTokens.get(i).getText();
                if (globalIdsMap.containsKey(id)) {
                    String ssa = globalIdsMap.get(id).get(parentSuitePointerMap.get(id));
                    if (ssa.contains("?")) {
                        ssa = ssa.substring(0, ssa.indexOf('?'));
                    }
                    delLine += ssa;
                    continue;
                }
            }
            delLine += exprlistTokens.get(i).getText();
        }

        out.write(delLine);

    }

    public void enterProg(PythonParser.ProgContext ctx) throws IOException {
        setTempSSAPointerMap(ctx, new HashMap<>());
        out.write("from phi import *\n");
    }

    //to do : null all ssa that are append with phi
    public void exitProg(PythonParser.ProgContext ctx) throws IOException {
        out.write("\n");
        out.write("\n");
        out.write("#generate python causal map");
        out.write("\n");
        String causalLine = "causal_map = {";
        for (String key : causalMap.keySet()) {
            ArrayList<String> parents = causalMap.get(key);
            String parentList = "[";
            for (String p : parents) {
                parentList += ("'" + p + "'" + ",");
            }
            if (parentList.charAt(parentList.length() - 1) == ',') {
                parentList = parentList.substring(0, parentList.length() - 1);
            }
            parentList += "]";
            causalLine += (  "'"+key+"'"   + ":" + parentList + "," );
        }
        causalLine+= "}";
        out.write(causalLine);


        out.write("\n");
        out.write("\n");
        out.write("#added phi names");
        out.write("\n");
        String phiNamesLine = "phi_names_set = {";
        for (String phiName : addedPhiNames) {
            phiNamesLine += ("'" + phiName + "'" + ',');
        }
        phiNamesLine += "}";
        out.write(phiNamesLine);


        out.close();
    }
    public void visitTerminal(TerminalNode node) {
        //newline
        if (node.getSymbol().getText().equals("if") && !(node.getParent() instanceof PythonParser.TestContext)
                && !(node.getParent() instanceof PythonParser.Comp_ifContext)){
            try {
                out.write("if ");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (node.getSymbol().getText().equals("elif")){
            try {
                writeTabs();
                out.write("elif ");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (node.getSymbol().getText().equals("else") && !(node.getParent() instanceof PythonParser.TestContext)){
            try {
                writeTabs();
                out.write("else:");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (node.getSymbol().getText().equals("try")){
            try {
                out.write("try:");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (node.getSymbol().getText().equals("continue")){
            try {
                out.write("continue");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (node.getSymbol().getText().equals("break")){
            try {
                out.write("break");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (node.getSymbol().getText().equals("finally")){
            try {
                writeTabs();
                out.write("finally:");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (node.getSymbol().getText().equals("pass")){
            try {
                out.write("pass");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        if (node.getSymbol().getType() == 39)
        {
            try {
                out.write("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        writeTokenUp(node.getSymbol(), node);
    }

    //    public void enterAtom
    //trying write token up
//
    private boolean isInScope(ParserRuleContext ctx, String ssa) {
        ParserRuleContext firstFuncSuiteParent = findFirstFuncSuiteParent(ctx);
        HashMap<String, ArrayList<String>> nodeMap = getNodeIdsMap(firstFuncSuiteParent);
        String ssaWithoutPhi = ssa;
        if (ssaWithoutPhi.indexOf('?') != -1) {
            ssaWithoutPhi = ssaWithoutPhi.substring(0, ssaWithoutPhi.indexOf('?'));
        }
        String id = ssaWithoutPhi.substring(0, ssaWithoutPhi.lastIndexOf('_'));
        return nodeMap.get(id).contains(ssa);

    }
    private ParserRuleContext findFirstFuncSuiteParent(ParserRuleContext ctx) {
        ctx = ctx.getParent();
        while (ctx != null) {
            if (ctx instanceof PythonParser.SuiteContext && ctx.getParent() instanceof  PythonParser.FuncdefContext) {
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }
    private ParserRuleContext findFirstSmallStmtNode(TerminalNode node) {
        ParserRuleContext ctx = (ParserRuleContext)node.getParent();
        while (ctx != null) {
            if (ctx instanceof PythonParser.Small_stmtContext) {
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }
    private ParserRuleContext findFirstSmallStmtNode(ParserRuleContext ctx) {
        ParserRuleContext node  =ctx;
        while (node != null) {
            if (node instanceof PythonParser.Small_stmtContext) {
                return node;
            }
            node = node.getParent();
        }
        return null;
    }
    private ParserRuleContext findTestListStarExprNode(TerminalNode node) {
        ParserRuleContext ctx = (ParserRuleContext)node.getParent();
        while (ctx != null) {
            if (ctx instanceof PythonParser.Testlist_star_exprContext) {
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }
    private void writeTokenUp(Token token, TerminalNode node) {
        ParserRuleContext firstSmallStmtParent = findFirstSmallStmtNode(node);
        ArrayList<Token> firstSmallStmtParentTokens = getRuleTokens(firstSmallStmtParent);

        ParserRuleContext firstTestListStarExprParent = findTestListStarExprNode(node);
        ArrayList<Token> firstTestListStarExprParentTokens = getRuleTokens(firstTestListStarExprParent);

        ParserRuleContext firstParamParent = findFirstParamParent(node);
        ArrayList<Token> firstParamParentTokens = getRuleTokens(firstParamParent);

        ParserRuleContext firstTestListParent = findFirstTestListParent(node);
        ArrayList<Token> firstTestListTokens = getRuleTokens(firstTestListParent);


        ParserRuleContext firstAssertParent = findFirstAssertParent(node);
        ArrayList<Token> firstAssertParentTokens = getRuleTokens(firstAssertParent);

        ParserRuleContext firstReturnParent = findFirstReturnParent(node);
        ArrayList<Token> firstReturnParentTokens = getRuleTokens(firstReturnParent);

        ParserRuleContext firstRaiseParent = findFirstRaiseParent(node);
        ArrayList<Token> firstRaiseParentTokens = getRuleTokens(firstRaiseParent);

        ParserRuleContext firstIfOrWhileTestParent = findFirstIfOrWhileTestParent(node);
        ArrayList<Token> firstIfOrWhileTestParentTokens = getRuleTokens(firstIfOrWhileTestParent);


        ParserRuleContext firstExprTestlistParent = findFirstExprTestlistParent(node);
        ArrayList<Token> firstExprTestlistParentTokens = getRuleTokens(firstExprTestlistParent);

        ParserRuleContext firstExceptParent = findFirstExceptParent(node);
        ArrayList<Token> firstExceptParentTokens = getRuleTokens(firstExceptParent);

        ParserRuleContext firstYieldParent = findFirstYieldParent(node);
        ArrayList<Token> firstYieldParentTokens = getRuleTokens(firstYieldParent);

        ParserRuleContext firstExprlistParent = findFirstExprlistParent(node);
        ArrayList<Token> firstExprlistParentTokens = getRuleTokens(firstExprlistParent);




        if (firstAssertParent != null) {
            firstAssertParentTokens.add(token);
            return;
        }

        if (firstYieldParent != null) {
            firstYieldParentTokens.add(token);
            return;
        }

        if (firstIfOrWhileTestParentTokens != null) {
            firstIfOrWhileTestParentTokens.add(token);
            return;
        }
        if (firstExceptParent != null) {
            firstExceptParentTokens.add(token);
            return;
        }

        if (firstReturnParent != null) {
            firstReturnParentTokens.add(token);
            return;
        }
        if (firstRaiseParent != null) {
            firstRaiseParentTokens.add(token);
            return;
        }

        if (firstParamParent != null) {
            firstParamParentTokens.add(token);
            return;
        }

        if (firstExprTestlistParent != null) {
            firstExprTestlistParentTokens.add(token);
            return;
        }

        //don't return yet, for the case "for..in.." in an assign stmt
        if (firstExprlistParent != null) {
            firstExprlistParentTokens.add(token);

        }
        if (firstSmallStmtParent != null) {
            firstSmallStmtParentTokens.add(token);
        }


        if (firstTestListParent != null) {
            firstTestListTokens.add(token);
        }

        if (firstTestListStarExprParent != null) {
            firstTestListStarExprParentTokens.add(token);
        }

    }

    private ParserRuleContext findFirstExprlistParent(TerminalNode node) {
        ParserRuleContext ctx = (ParserRuleContext)node.getParent();
        while (ctx != null) {
            if (ctx instanceof PythonParser.ExprlistContext) {
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }

    private ParserRuleContext findFirstTestListParent(TerminalNode node) {
        ParserRuleContext ctx = (ParserRuleContext)node.getParent();
        while (ctx != null) {
            if (ctx instanceof PythonParser.TestlistContext) {
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }


    private ParserRuleContext findFirstYieldParent(TerminalNode node) {
        ParserRuleContext ctx = (ParserRuleContext)node.getParent();
        while (ctx != null) {
            if (ctx instanceof PythonParser.Yield_exprContext) {
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }


    private ParserRuleContext findFirstAssertParent(TerminalNode node) {
        ParserRuleContext ctx = (ParserRuleContext)node.getParent();
        while (ctx != null) {
            if (ctx instanceof PythonParser.Assert_stmtContext) {
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }
    private ParserRuleContext findFirstReturnParent(TerminalNode node) {
        ParserRuleContext ctx = (ParserRuleContext)node.getParent();
        while (ctx != null) {
            if (ctx instanceof PythonParser.Return_stmtContext) {
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }
    private ParserRuleContext findFirstRaiseParent(TerminalNode node) {
        ParserRuleContext ctx = (ParserRuleContext)node.getParent();
        while (ctx != null) {
            if (ctx instanceof PythonParser.Raise_stmtContext) {
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }

    private ParserRuleContext findFirstParamParent(TerminalNode node) {
        ParserRuleContext ctx = (ParserRuleContext)node.getParent();
        while (ctx != null) {
            if (ctx instanceof PythonParser.ParametersContext) {
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }

    private ParserRuleContext findFirstIfOrWhileTestParent(TerminalNode node) {
        ParserRuleContext ctx = (ParserRuleContext)node.getParent();
        while (ctx != null && ctx.getParent() != null) {
            if ((ctx instanceof PythonParser.TestContext && ctx.getParent() instanceof PythonParser.If_stmtContext) ||
                    (ctx instanceof PythonParser.TestContext && ctx.getParent() instanceof PythonParser.While_stmtContext)){
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }

    private ParserRuleContext findFirstExceptParent(TerminalNode node) {
        ParserRuleContext ctx = (ParserRuleContext)node.getParent();
        while (ctx != null ) {
            if (ctx instanceof PythonParser.Except_clauseContext) {
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }

    private ParserRuleContext findFirstExprTestlistParent(TerminalNode node) {
        ParserRuleContext ctx = (ParserRuleContext)node.getParent();
        while (ctx != null && ctx.getParent() != null) {
            if (ctx instanceof PythonParser.TestlistContext && ctx.getParent() instanceof PythonParser.Expr_stmtContext) {
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }

    private void writeTabs() throws IOException {
        for (int i = 0; i < tabCount; i++) {
            out.write("    ");
        }
    }

}