import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.TerminalNode;

import javax.sound.midi.SysexMessage;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.*;
/**
 * Created by Frank on 10/19/2017.
 */
public class Converter extends PythonBaseListener {
    HashMap<String, ArrayList<String>> globalIdsMap;
    ParseTreeProperty<ArrayList<Token>> ruleTokens;
    public PythonParser parser;
    public ParseTreeProperty<HashMap<String, ArrayList<String>>> idMap;
    public ParseTreeProperty<ArrayList<ArrayList<String>>> comp_forMap;
    public ParseTreeProperty<HashMap<String, ArrayList<String>>> forNameIdMap;
    public Stack<String> globalTestList;
    public String[] tokenTable = new String[100];
    public ArrayList<String> importedNames;
    public Stack<Integer> predPointStackforLoop;
    public ArrayList<String> functionNames;
    public ArrayList<String> importDottedNames;
    public Converter(PythonParser parser) {
        forNameIdMap = new ParseTreeProperty<>();
        importDottedNames = new ArrayList<>();
        functionNames = new ArrayList<>();
        predPointStackforLoop = new Stack<>();
        importedNames = new ArrayList<>();
        ruleTokens = new ParseTreeProperty<>();
        this.parser = parser;
        idMap = new ParseTreeProperty<>();
        comp_forMap = new ParseTreeProperty<>();
        globalIdsMap = new HashMap<>();
        globalTestList = new Stack<>();
        try {
            FileReader tsteam = new FileReader("src/Python.tokens");
            BufferedReader br = new BufferedReader(tsteam);
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
    private void setNodeIdsMap(ParseTree node, HashMap<String, ArrayList<String>> nodeIds) {
        idMap.put(node, nodeIds);
    }
    private HashMap<String, ArrayList<String>> getNodeIdsMap(ParseTree node) {
        return idMap.get(node);
    }
    private void setNodeForNameIdsMap(ParseTree node, HashMap<String, ArrayList<String>> nodeForIds) {
        forNameIdMap.put(node, nodeForIds);
    }
    private HashMap<String, ArrayList<String>> getNodeForNameIdsMap(ParseTree node) {
        return forNameIdMap.get(node);
    }
    private void setNodeComp_forLists(ParseTree node, ArrayList<ArrayList<String>> nodeComp_forLists) {
        comp_forMap.put(node, nodeComp_forLists);
    }
    private ArrayList<ArrayList<String>> getNodeComp_forLists(ParseTree node) {
        return comp_forMap.get(node);
    }
    private ArrayList<Token> getRuleTokens(ParseTree node) {
        return ruleTokens.get(node);
    }
    private void setRuleTokens(ParseTree node, ArrayList<Token> tokens) {
        ruleTokens.put(node, tokens);
    }
    public HashMap<String, ArrayList<String>> getGlobalIdsMap() {
        return this.globalIdsMap;
    }
    public ArrayList<String> getImportedNames() {
        return this.importedNames;
    }
    public Stack<String> getGlobalTestList() {
        return this.globalTestList;
    }
    private boolean isSpecial(String id) {
        return id.matches("__[A-Za-z_][A-Za-z_0-9]*__") || id.equals("self") || id.equals("cls") || id.equals("_");
    }
    private boolean isGlobal(ParserRuleContext ctx) {
        while (ctx.getParent() != null) {
            ParserRuleContext parent = ctx.getParent();
            if (parent instanceof PythonParser.FuncdefContext) {
                return false;
            }
            ctx = parent;
        }
        return true;
    }
    private ParserRuleContext findFirstSuiteParent(ParserRuleContext ctx) {
        while (ctx.getParent() != null) {
            ParserRuleContext parent = ctx.getParent();
            if (parent.getClass().getSimpleName().contains("Suite")) {
                return parent;
            }
            ctx = parent;
        }
        return null;
    }

    private ParserRuleContext findFirstExprParent(ParserRuleContext ctx) {
        while (ctx.getParent() != null) {
            ParserRuleContext parent = ctx.getParent();
            if (parent instanceof PythonParser.Expr_stmtContext) {
                return parent;
            }
            ctx = parent;
        }
        return null;
    }
    private ParserRuleContext findFirstReturnParent(ParserRuleContext ctx) {
        while (ctx.getParent() != null) {
            ParserRuleContext parent = ctx.getParent();
            if (parent instanceof PythonParser.Return_stmtContext) {
                return parent;
            }
            ctx = parent;
        }
        return null;
    }
    private ParserRuleContext findFirstTestParent(ParserRuleContext ctx) {
        while (ctx.getParent() != null) {
            ParserRuleContext parent = ctx.getParent();
            if (parent instanceof PythonParser.TestContext) {
                return parent;
            }
            ctx = parent;
        }
        return null;
    }
    private ParserRuleContext firstConditionTestParent(ParserRuleContext ctx) {
        while (ctx.getParent() != null) {
            ParserRuleContext parent = ctx.getParent();
            if ((parent instanceof PythonParser.TestContext && parent.getParent() instanceof PythonParser.If_stmtContext) ||
                    (parent instanceof PythonParser.TestContext && parent.getParent() instanceof PythonParser.While_stmtContext) ||
                    (parent instanceof PythonParser.TestContext && parent.getParent() instanceof PythonParser.For_stmtContext)){
                return parent;
            }
            ctx = parent;
        }
        return null;
    }
    private ParserRuleContext findFirstCompforParent(ParserRuleContext ctx) {
        while (ctx.getParent() != null) {
            ParserRuleContext parent = ctx.getParent();
            if (parent instanceof PythonParser.Comp_forContext) {
                return parent;
            }
            ctx = parent;
        }
        return null;
    }
    private ParserRuleContext findProgNode(ParserRuleContext ctx) {
        while (ctx.getParent() != null) {
            ParserRuleContext parent = ctx.getParent();
            if (parent.getClass().getSimpleName().contains("Prog")) {
                return parent;
            }
            ctx = parent;
        }
        return null;
    }
    private HashMap<String, ArrayList<String>> mergeSuitesMap(ArrayList<HashMap<String, ArrayList<String>>> suitesMaps,
                                                              ArrayList<String> predicates, ParserRuleContext ctx) {
        // need snapshot before enter if
        HashSet<String> suitesIdSet = new HashSet<>();
        for (HashMap<String, ArrayList<String>> suiteMap : suitesMaps) {
            for (String id : suiteMap.keySet()) {
                suitesIdSet.add(id);
            }
        }
        HashMap<String, String> snapshotBeforeIfMap = new HashMap<>();
        for (String id : suitesIdSet) {
            snapshotBeforeIfMap.put(id, findLastIdSSA(id, ctx.getParent()));
        }



        HashMap<String, ArrayList<String>> mergedMap = new HashMap<>();
        HashMap<String, ArrayList<String>> collideMap = new HashMap<>();
        //if node map contains all the phi produced by if stmt
        HashMap<String, ArrayList<String>> ifNodeMap = getNodeIdsMap(ctx);
        //if
        if (suitesMaps.size() == 1) {
            for (String id : suitesMaps.get(0).keySet()) {
                mergedMap.put(id, new ArrayList<>(suitesMaps.get(0).get(id)));
            }
            for (String id : mergedMap.keySet()) {
                String lastIdSSA = findLastIdSSA(id, ctx.getParent());
                if (lastIdSSA == null) {
                    lastIdSSA = "None";
                }
                //if (lastIdSSA != null) {
                ArrayList<String> idListInSuite = mergedMap.get(id);
                String lastIdSSAinSuite = idListInSuite.get(idListInSuite.size() - 1);
                String phiId = id +"_"+ globalIdsMap.get(id).size() + "?phiIf" +"(" + predicates.get(0) +"," + lastIdSSAinSuite+","+lastIdSSA+")";
                if (!ifNodeMap.containsKey(id)) {
                    ifNodeMap.put(id, new ArrayList<>());
                }
                ifNodeMap.get(id).add(phiId);
                mergedMap.get(id).add(phiId);
                globalIdsMap.get(id).add(phiId);
                //}
            }
            return mergedMap;
        }
        //if elif elif else
        for (String id: suitesIdSet) {
            String phiId = id +"_"+ globalIdsMap.get(id).size() + "?phiIf" + "(";
            for (int i = 0; i < suitesMaps.size(); i++) {

                HashMap<String, ArrayList<String>> crtSuiteMap = suitesMaps.get(i);

                if (!mergedMap.containsKey(id)) {
                    mergedMap.put(id, new ArrayList<>());
                }
                if (crtSuiteMap.containsKey(id)) {
                    ArrayList<String> idList = crtSuiteMap.get(id);
                    mergedMap.get(id).addAll(idList);
                }


                if (!mergedMap.containsKey(id)) {
                    mergedMap.put(id, new ArrayList<>());
                }
                String collideId = "";
                if (!crtSuiteMap.containsKey(id)) {
                    collideId = snapshotBeforeIfMap.get(id);
                    if (collideId == null) {
                        collideId = "None";
                    }
                } else {
                    collideId = crtSuiteMap.get(id).get(crtSuiteMap.get(id).size() - 1);
                    //
                    if (collideId.contains("?")) {
                        collideId = collideId.substring(0, collideId.indexOf('?'));
                    }
                }
                if (i < predicates.size()) {
                    collideId = predicates.get(i) + "," + collideId;
                }

                phiId += (collideId + ",");
            }
            //if elif
            if (predicates.size() == suitesMaps.size()) {
                String nonElseId = snapshotBeforeIfMap.get(id);
                if (nonElseId == null) {
                    nonElseId = "None";
                }
                phiId += (nonElseId + ",");

            }
            phiId = phiId.substring(0, phiId.length() - 1);
            phiId += ")";
            if (!ifNodeMap.containsKey(id)) {
                ifNodeMap.put(id, new ArrayList<>());
            }
            ifNodeMap.get(id).add(phiId);
            mergedMap.get(id).add(phiId);
            globalIdsMap.get(id).add(phiId);

        }
        return mergedMap;
    }



    public void enterProg(PythonParser.ProgContext ctx) {
        setNodeIdsMap(ctx, new HashMap<>());
        setNodeForNameIdsMap(ctx, new HashMap<>());
    }
    public void exitProg(PythonParser.ProgContext ctx) {
        importedNames.removeAll(importDottedNames);
    }
    public void enterImport_stmt(PythonParser.Import_stmtContext ctx) {
        setRuleTokens(ctx, new ArrayList<>());
    }
    public void exitImport_stmt(PythonParser.Import_stmtContext ctx) throws IOException {
//        String dottedNamesString = ctx.
        ArrayList<Token> tokens = getRuleTokens(ctx);
        for (Token token : tokens) {
            if (tokenTable[token.getType()].equals("NAME")) {
                importedNames.add(token.getText());
            }
        }
    }
    public void enterDotted_name(PythonParser.Dotted_nameContext ctx) {
        setRuleTokens(ctx, new ArrayList<>());
    }
    public void exitDotted_name(PythonParser.Dotted_nameContext ctx) {
        ArrayList<Token> tokens = getRuleTokens(ctx);
        for (Token token : tokens) {
            if (tokenTable[token.getType()].equals("NAME")) {
                importDottedNames.add(token.getText());
            }
        }
    }

    public void enterLambdef(PythonParser.LambdefContext ctx) {
        ParserRuleContext lambVarListNode = ctx.varargslist();
    }

    public void enterVarargslist(PythonParser.VarargslistContext ctx) {
        if (!(ctx.getParent() instanceof PythonParser.LambdefContext) && !(ctx.getParent() instanceof PythonParser.Lambdef_nocondContext)) {
            return;
        }
        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        String[] names = ctx.getText().split(",");
        String idSSA;
        for (String id : names) {
            if (isSpecial(id)) continue;

            if (!globalIdsMap.containsKey(id)) {
                globalIdsMap.put(id, new ArrayList<>());
            }
            idSSA = id + "_" + globalIdsMap.get(id).size();

            globalIdsMap.get(id).add(idSSA);
            if (firstSuiteParent == null) {
                HashMap<String, ArrayList<String>> progNodeMap = getNodeIdsMap(findProgNode(ctx));
                if (!progNodeMap.containsKey(id)) {
                    progNodeMap.put(id, new ArrayList<>());
                }
                progNodeMap.get(id).add(idSSA);
            } else {
                HashMap<String, ArrayList<String>> suiteNodeMap = getNodeIdsMap(firstSuiteParent);
                if (!suiteNodeMap.containsKey(id)) {
                    suiteNodeMap.put(id, new ArrayList<>());
                }
                suiteNodeMap.get(id).add(idSSA);
            }
        }

    }


    public void enterExpr_stmt(PythonParser.Expr_stmtContext ctx) {
        setNodeComp_forLists(ctx, new ArrayList<>());
        if (isGlobal(ctx)) {
            return;
        }
        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        String idSSA;
        if (ctx.testlist_star_expr().size() > 1 || ctx.augassign()!=null ) {
            int testlistStarExprLength = ctx.testlist_star_expr().size();
            if (ctx.augassign() != null) {
                testlistStarExprLength = 2;
            }
            for (int i = 0; i < testlistStarExprLength - 1; i++) {

                String defsAsString = ctx.testlist_star_expr().get(i).getText();
                defsAsString = defsAsString.replaceAll("[()]", "");
                String[] defs = defsAsString.split("[=,]");
                for (String id : defs) {

                    if (id.matches("[a-zA-Z_][a-zA-Z0-9_]*") && !isSpecial(id)) {
                        //System.out.println(id);
                        if (!globalIdsMap.containsKey(id)) {
                            globalIdsMap.put(id, new ArrayList<>());
                        }
                        idSSA = id + "_" + globalIdsMap.get(id).size();

                        //System.out.println(idSSA);
                        globalIdsMap.get(id).add(idSSA);
                        if (firstSuiteParent == null) {
                            HashMap<String, ArrayList<String>> progNodeMap = getNodeIdsMap(findProgNode(ctx));
                            if (!progNodeMap.containsKey(id)) {
                                progNodeMap.put(id, new ArrayList<>());
                            }
                            progNodeMap.get(id).add(idSSA);
                        } else {
                            HashMap<String, ArrayList<String>> suiteNodeMap = getNodeIdsMap(firstSuiteParent);
                            if (!suiteNodeMap.containsKey(id)) {
                                suiteNodeMap.put(id, new ArrayList<>());
                            }
                            suiteNodeMap.get(id).add(idSSA);
                        }
                    }
                }
            }
        }
    }

    public void enterClassdef(PythonParser.ClassdefContext ctx) {
        setNodeIdsMap(ctx, new HashMap<>());
    }
    public void exitClassdef(PythonParser.ClassdefContext ctx) {
        writeUp(getNodeIdsMap(ctx), ctx);
    }

    public void enterSuite(PythonParser.SuiteContext ctx) {
        if (getNodeIdsMap(ctx) == null) {
            setNodeIdsMap(ctx, new HashMap<>());
        }
        setNodeForNameIdsMap(ctx, new HashMap<>());
    }
    public void exitSuite(PythonParser.SuiteContext ctx) {
        if (ctx.getParent() instanceof PythonParser.If_stmtContext ||
                ctx.getParent() instanceof PythonParser.ClassdefContext ) {
            writeUp(getNodeIdsMap(ctx), ctx);
        }

        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        HashMap<String, ArrayList<String>> suiteForNameMap = getNodeForNameIdsMap(ctx);
        if (firstSuiteParent == null) {
            HashMap<String, ArrayList<String>> progForNameMap = getNodeForNameIdsMap(findProgNode(ctx));
            for (String id : suiteForNameMap.keySet()) {
                if (!progForNameMap.containsKey(id)) {
                    progForNameMap.put(id, new ArrayList<>());
                }
                progForNameMap.get(id).addAll(suiteForNameMap.get(id));
            }

        } else {
            HashMap<String, ArrayList<String>> suiteParentForNameMap = getNodeForNameIdsMap(firstSuiteParent);
            for (String id : suiteForNameMap.keySet()) {
                if (!suiteParentForNameMap.containsKey(id)) {
                    suiteParentForNameMap.put(id, new ArrayList<>());
                }
                suiteParentForNameMap.get(id).addAll(suiteForNameMap.get(id));
            }
        }

    }
    public void enterReturn_stmt(PythonParser.Return_stmtContext ctx) {
        setNodeComp_forLists(ctx, new ArrayList<>());
    }

    //comp_for appears at right side of an assign
    public void enterComp_for(PythonParser.Comp_forContext ctx) {

        ParserRuleContext firstExprParent = findFirstExprParent(ctx);
        ParserRuleContext firstConditionTestParent = firstConditionTestParent(ctx);
        ParserRuleContext firstReturnParent = findFirstReturnParent(ctx);
        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        ArrayList<ArrayList<String>> nodeComp_forLists;
        //expr_stmt may include test child
        if (firstReturnParent != null) {
            nodeComp_forLists = getNodeComp_forLists(firstReturnParent);
        } else {
            if (firstConditionTestParent != null) {
                nodeComp_forLists = getNodeComp_forLists(firstConditionTestParent);
            } else {
                nodeComp_forLists = getNodeComp_forLists(firstExprParent);
            }
        }

        String[] forNames = ctx.exprlist().getText().split(",");
        String idSSA;
        ArrayList<String> nodeComp_forList = new ArrayList<>();
        for (String id : forNames) {
            id = id.replaceAll("[()]","");
            if (id.matches("[a-zA-Z_][a-zA-Z0-9_]*") && !id.equals("self") && !isSpecial(id)) {
                if (!globalIdsMap.containsKey(id)) {
                    globalIdsMap.put(id, new ArrayList<>());
                }
                idSSA = id + "_" + globalIdsMap.get(id).size();
                globalIdsMap.get(id).add(idSSA);
                nodeComp_forList.add(idSSA);

                if (firstSuiteParent == null) {
                    HashMap<String, ArrayList<String>> progNodeForNameMap = getNodeForNameIdsMap(findProgNode(ctx));
                    if (!progNodeForNameMap.containsKey(id)) {
                        progNodeForNameMap.put(id, new ArrayList<>());
                    }
                    progNodeForNameMap.get(id).add(idSSA);
                } else {
                    HashMap<String, ArrayList<String>> suiteNodeForNameMap = getNodeForNameIdsMap(firstSuiteParent);
                    if (!suiteNodeForNameMap.containsKey(id)) {
                        suiteNodeForNameMap.put(id, new ArrayList<>());
                    }
                    suiteNodeForNameMap.get(id).add(idSSA);
                }
            }

        }
        if (findFirstCompforParent(ctx) != null) {
            nodeComp_forLists.get(nodeComp_forLists.size() - 1).addAll(nodeComp_forList);
        } else {
            nodeComp_forLists.add(new ArrayList<>(nodeComp_forList));
        }

    }

    public void enterIf_stmt(PythonParser.If_stmtContext ctx) {
        setNodeIdsMap(ctx, new HashMap<>());
    }

    public void enterTest(PythonParser.TestContext ctx) {
        setRuleTokens(ctx, new ArrayList<>());
        setNodeComp_forLists(ctx, new ArrayList<>());
    }
    public void visitTerminal(TerminalNode node) {
        writeTokenUp(node.getSymbol(), node);
    }

    private void writeTokenUp(Token token, TerminalNode node) {
        ParserRuleContext firstControlTestParent = findFirstControlTestParent(node);
        ParserRuleContext firstImportParent = findFirstImportParent(node);
        ParserRuleContext firstImportDottedNameParent = findFirstImportDottedNameParent(node);

        if (firstControlTestParent != null) {
            ArrayList<Token> firstControlTestParentTokens = getRuleTokens(firstControlTestParent);
            firstControlTestParentTokens.add(token);
            return;
        }
        if (firstImportDottedNameParent != null) {
            ArrayList<Token> firstImportDottedNameParentTokens = getRuleTokens(firstImportDottedNameParent);
            firstImportDottedNameParentTokens.add(token);
        }

        if (firstImportParent != null) {
            ArrayList<Token> firstControlTestParentTokens = getRuleTokens(firstImportParent);
            firstControlTestParentTokens.add(token);
            return;
        }

    }
    private ParserRuleContext findFirstImportDottedNameParent(TerminalNode node) {
        ParserRuleContext ctx = (ParserRuleContext)node.getParent();
        while (ctx != null) {
            if (ctx instanceof PythonParser.Dotted_nameContext){
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }
    private ParserRuleContext findFirstImportParent(TerminalNode node) {
        ParserRuleContext ctx = (ParserRuleContext)node.getParent();
        while (ctx != null) {
            if (ctx instanceof PythonParser.Import_stmtContext){
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }

    private ParserRuleContext findFirstControlTestParent(TerminalNode node) {
        ParserRuleContext ctx = (ParserRuleContext)node.getParent();
        while (ctx != null && ctx.getParent() != null) {
            if ((ctx instanceof PythonParser.TestContext && ctx.getParent() instanceof PythonParser.If_stmtContext) ||
                    (ctx instanceof PythonParser.TestContext && ctx.getParent() instanceof PythonParser.While_stmtContext) ||
                    (ctx instanceof PythonParser.TestContext && ctx.getParent() instanceof PythonParser.For_stmtContext)){
                return ctx;
            }
            ctx = ctx.getParent();
        }
        return null;
    }

    public void exitTest(PythonParser.TestContext ctx) {
        String parentNodeName = ctx.getParent().getClass().getSimpleName();
        if (parentNodeName.contains("If_stmt") || parentNodeName.contains("While_stmt") || parentNodeName.contains("For_stmt")) {
            ArrayList<Token> tokens = getRuleTokens(ctx);
            String pred = "";
            for (Token token : tokens) {
                if (tokenTable[token.getType()].equals("NAME") && token.getText().matches("[a-zA-Z_$][a-zA-Z_$0-9]*") && !isSpecial(token.getText())) {
                    String oldName = token.getText();
                    String newName = findLastIdSSA(oldName, ctx);
                    if (newName != null) {
                        if (newName.contains("?")) {
                            newName = newName.substring(0, newName.indexOf("?"));
                        }
                        pred += newName;
                        continue;
                    }
                }
                if (tokenTable[token.getType()].equals("NOT")) {
                    pred += " not ";
                    continue;
                }
                if (tokenTable[token.getType()].equals("IS")) {
                    pred += " is ";
                    continue;
                }
                if (tokenTable[token.getType()].equals("IN")) {
                    pred += " in ";
                    continue;
                }
                pred += token.getText();
            }

            this.globalTestList.push(pred);
            System.out.println(pred + " pred added");
        }
    }

    private String findLastIdSSAtoDefine(String id, ParserRuleContext ctx) {
        while (ctx != null) {
            if (ctx.getClass().getSimpleName().contains("Prog") || ctx.getClass().getSimpleName().contains("Suite") ||
                    ctx instanceof PythonParser.If_stmtContext || ctx instanceof PythonParser.Try_stmtContext) {
                HashMap<String, ArrayList<String>> nodeMap = getNodeIdsMap(ctx);
                String lastIdSSAtoDefine = "";
                if (nodeMap.containsKey(id)) {
                    ArrayList<String> ssaList = nodeMap.get(id);
                    lastIdSSAtoDefine = ssaList.get(ssaList.size() - 1);

                }
                if (ctx instanceof  PythonParser.SuiteContext) {
                    HashMap<String, ArrayList<String>> nodeForNameIdsMap = getNodeForNameIdsMap(ctx);
                    if (nodeForNameIdsMap.containsKey(id)) {
                        ArrayList<String> forSsaList = nodeForNameIdsMap.get(id);
                        String forNameToBeCompare = forSsaList.get(forSsaList.size() - 1);
                        if (lastIdSSAtoDefine.equals("") || forNameToBeCompare.compareTo(lastIdSSAtoDefine) > 1) {
                            lastIdSSAtoDefine = forNameToBeCompare;
                        }
                    }
                }
                if (!lastIdSSAtoDefine.equals("")) {
                    return lastIdSSAtoDefine;
                }
                //only for suites


            }
            ctx = ctx.getParent();
        }
        return null;
    }

    private String findLastIdSSA(String id, ParserRuleContext ctx) {
        while (ctx != null) {
            if (ctx.getClass().getSimpleName().contains("Prog") || ctx.getClass().getSimpleName().contains("Suite") ||
                    ctx instanceof PythonParser.If_stmtContext || ctx instanceof PythonParser.Try_stmtContext ||
                    ctx instanceof PythonParser.For_stmtContext) {
                HashMap<String, ArrayList<String>> nodeMap = getNodeIdsMap(ctx);
                if (nodeMap.containsKey(id)) {
                    ArrayList<String> ssaList = nodeMap.get(id);
                    return ssaList.get(ssaList.size() - 1);
                } else {
                    if (ctx instanceof PythonParser.SuiteContext &&
                            ctx.getParent() instanceof PythonParser.If_stmtContext &&
                                ((PythonParser.If_stmtContext) ctx.getParent()).suite().size() > 1) {
                        ctx = ctx.getParent();

                    }
                }
            }
            ctx = ctx.getParent();
        }
        return null;
    }

    public void exitIf_stmt(PythonParser.If_stmtContext ctx) {
        List<PythonParser.SuiteContext> suiteNodes = ctx.suite();
        int testCount = ctx.test().size();
        ArrayList<HashMap<String, ArrayList<String>>> suitesMaps = new ArrayList<>();
        ArrayList<String> predicates = new ArrayList<>();
        for (int i = 0; i < testCount; i++) {
            predicates.add(globalTestList.pop());
        }
        Collections.reverse(predicates);
        for (ParserRuleContext suiteNode : suiteNodes) {
            suitesMaps.add(getNodeIdsMap(suiteNode));
        }
        HashMap<String, ArrayList<String>> mergedIfMap = mergeSuitesMap(suitesMaps, predicates, ctx);
        //setNodeIdsMap(ctx, mergedIfNodeIdMap);
        writeUp(mergedIfMap, ctx);
    }


    //write up to most adjacent parent suite or main flow
    private void writeUp(HashMap<String, ArrayList<String>> mergedNodeIdMap, ParserRuleContext ctx) {
        if (ctx instanceof PythonParser.SuiteContext ||
                ctx instanceof PythonParser.ProgContext) {
            HashMap<String, ArrayList<String>> parentNodeMap = getNodeIdsMap(ctx.getParent());
            for (String id : mergedNodeIdMap.keySet()) {
                if (!parentNodeMap.containsKey(id)) {
                    parentNodeMap.put(id, new ArrayList<>());
                }
                parentNodeMap.get(id).addAll(mergedNodeIdMap.get(id));
                //System.out.println(mergedNodeIdMap.get(id) + "--");
            }
            return;
        }
        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        if (firstSuiteParent == null) {
            HashMap<String, ArrayList<String>> progNodeMap = getNodeIdsMap(findProgNode(ctx));
            for (String id : mergedNodeIdMap.keySet()) {
                if (!progNodeMap.containsKey(id)) {
                    progNodeMap.put(id, new ArrayList<>());
                }
                progNodeMap.get(id).addAll(mergedNodeIdMap.get(id));
            }
            return;
        }
        HashMap<String, ArrayList<String>> suiteNodeMap = getNodeIdsMap(firstSuiteParent);
        for (String id : mergedNodeIdMap.keySet()) {
            if (!suiteNodeMap.containsKey(id)) {
                suiteNodeMap.put(id, new ArrayList<>());
            }
            suiteNodeMap.get(id).addAll(mergedNodeIdMap.get(id));
        }
    }

    public void enterTfpdef(PythonParser.TfpdefContext ctx) {
        String id = ctx.getText();

        if (isSpecial(id)) {
            return;
        }
        if (!globalIdsMap.containsKey(id)) {
            globalIdsMap.put(id, new ArrayList<>());
        }
        String idSSA = id + "_" + globalIdsMap.get(id).size();
        globalIdsMap.get(id).add(idSSA);
        System.out.println(idSSA + " name in param");
        //for fundef None variable init
        HashMap<String, ArrayList<String>> funcdefNodeMap = getNodeIdsMap(findFuncdefParent(ctx));
        if (!funcdefNodeMap.containsKey(id)) {
            funcdefNodeMap.put(id, new ArrayList<>());
        }
        funcdefNodeMap.get(id).add(idSSA);

        ParserRuleContext findFuncdefSuiteSib = findFuncdefParent(ctx).suite();
        if (getNodeIdsMap(findFuncdefSuiteSib) == null) {
            setNodeIdsMap(findFuncdefSuiteSib, new HashMap<>());
        }

        HashMap<String, ArrayList<String>> funcdefSuiteNodeMap = getNodeIdsMap(findFuncdefSuiteSib);
        if (!funcdefSuiteNodeMap.containsKey(id)) {
            funcdefSuiteNodeMap.put(id, new ArrayList<>());
        }
        funcdefSuiteNodeMap.get(id).add(idSSA);
    }

    private PythonParser.FuncdefContext findFuncdefParent(PythonParser.TfpdefContext ctx) {
        ParserRuleContext node = ctx;
        while (node != null) {
            if (node.getClass().getSimpleName().contains("Funcdef")) {
                return (PythonParser.FuncdefContext)node;
            }
            node = node.getParent();
        }
        return null;
    }

    public void enterFuncdef(PythonParser.FuncdefContext ctx) {
        setNodeIdsMap(ctx, new HashMap<>());
        String funcName = ctx.NAME().getText();
        functionNames.add(funcName);

    }
    public void exitFuncdef(PythonParser.FuncdefContext ctx) {
        HashMap<String, ArrayList<String>> funcdefSuiteNodeMap = getNodeIdsMap(ctx.suite());
        writeUp(funcdefSuiteNodeMap, ctx);
    }

    public void enterTry_stmt(PythonParser.Try_stmtContext ctx) {
        setNodeIdsMap(ctx, new HashMap<>());
    }
    public void exitTry_stmt(PythonParser.Try_stmtContext ctx) {
        HashMap<String, ArrayList<String>> suiteNodeMap = getNodeIdsMap(ctx.suite(0));
        writeUp(suiteNodeMap, ctx);
    }

    public void enterWhile_stmt(PythonParser.While_stmtContext ctx) {
        setNodeIdsMap(ctx, new HashMap<>());

    }
    public void exitWhile_stmt(PythonParser.While_stmtContext ctx) {
        HashMap<String, ArrayList<String>> suiteNodeMap = getNodeIdsMap(ctx.suite(0));
        HashMap<String, ArrayList<String>> mergedWhileNodeMap = new HashMap<>(suiteNodeMap);
        HashMap<String, ArrayList<String>> whileNodeMap = getNodeIdsMap(ctx);
        for (String id : suiteNodeMap.keySet()) {

            String lastIdSSA = findLastIdSSA(id, ctx);
            //if (lastIdSSA != null) {
                ArrayList<String> idListInSuite = suiteNodeMap.get(id);
                String lastIdSSAinSuite = idListInSuite.get(idListInSuite.size() - 1);
                String lastIdSSAinSuiteIdOnly = lastIdSSAinSuite;
                String lastIdSSAidOnly = (lastIdSSA == null ? "None" :  lastIdSSA);

                if (lastIdSSAidOnly.contains("?")) {
                    lastIdSSAidOnly = lastIdSSAidOnly.substring(0, lastIdSSAidOnly.indexOf('?'));
                }

                if (lastIdSSAidOnly.contains("?")) {
                    lastIdSSAidOnly = lastIdSSAidOnly.substring(0, lastIdSSAidOnly.indexOf('?'));
                }
                if (lastIdSSAinSuite.contains("?")) {
                    lastIdSSAinSuiteIdOnly = lastIdSSAinSuite.substring(0, lastIdSSAinSuite.indexOf('?'));
                }
                String phiIdEntry = id +"_"+ globalIdsMap.get(id).size() + "?phiEntry" +"(" + lastIdSSAidOnly + "," + lastIdSSAinSuiteIdOnly + ")";
                if (!whileNodeMap.containsKey(id)) {
                    whileNodeMap.put(id, new ArrayList<>());
                }
                whileNodeMap.get(id).add(phiIdEntry);
                mergedWhileNodeMap.get(id).add(0, phiIdEntry);
                globalIdsMap.get(id).add(globalIdsMap.get(id).indexOf(findLastIdSSAtoDefine(id, ctx))  + 1, phiIdEntry);

                String exitId = suiteNodeMap.get(id).get(suiteNodeMap.get(id).size() - 1);
                if (exitId.contains("?")) {
                    exitId = exitId.substring(0, exitId.indexOf('?'));
                }
                String phidIdExit = id + "_" +  globalIdsMap.get(id).size() + "?phiExit" +"(" + lastIdSSAidOnly + ","+exitId + ")";
                if (!whileNodeMap.containsKey(id)) {
                    whileNodeMap.put(id, new ArrayList<>());
                }
                whileNodeMap.get(id).add(phidIdExit);
                mergedWhileNodeMap.get(id).add(phidIdExit);
                globalIdsMap.get(id).add(phidIdExit);
            //}
        }
        //
        this.globalTestList.pop();
        writeUp(mergedWhileNodeMap, ctx);
    }

    public void enterFor_stmt(PythonParser.For_stmtContext ctx) {

        ParserRuleContext forExprListNode = ctx.exprlist();
        //ParserRuleContext forSuiteNode = ctx.suite(0);
        setNodeIdsMap(ctx, new HashMap<>());
        HashMap<String, ArrayList<String>> forNodeMap = getNodeIdsMap(ctx);
        ParserRuleContext firstSuiteParent = findFirstSuiteParent(ctx);
        String[] exprList = forExprListNode.getText().split(",");
        for (String id : exprList) {
            id = id.replaceAll("[()]","");
            if (id.matches("[a-zA-Z_$][a-zA-Z_$0-9]*") && !isSpecial(id)) {
                String idSSA = "";
                if (!globalIdsMap.containsKey(id)) {
                    globalIdsMap.put(id, new ArrayList<>());
                }
                idSSA = id + "_" + globalIdsMap.get(id).size();
                globalIdsMap.get(id).add(idSSA);
//                if (!forNodeMap.containsKey(expr)) {
//                    forNodeMap.put(expr, new ArrayList<>());
//                }
//                forNodeMap.get(expr).add(idSSA);

                if (firstSuiteParent == null) {
                    HashMap<String, ArrayList<String>> progNodeForNameMap = getNodeForNameIdsMap(findProgNode(ctx));
                    if (!progNodeForNameMap.containsKey(id)) {
                        progNodeForNameMap.put(id, new ArrayList<>());
                    }
                    progNodeForNameMap.get(id).add(idSSA);
                } else {
                    HashMap<String, ArrayList<String>> suiteNodeForNameMap = getNodeForNameIdsMap(firstSuiteParent);
                    if (!suiteNodeForNameMap.containsKey(id)) {
                        suiteNodeForNameMap.put(id, new ArrayList<>());
                    }
                    suiteNodeForNameMap.get(id).add(idSSA);
                }
            }
        }



    }
    public void exitFor_stmt(PythonParser.For_stmtContext ctx) {
        HashMap<String, ArrayList<String>> suiteNodeMap = getNodeIdsMap(ctx.suite(0));
        HashMap<String, ArrayList<String>> mergedForNodeMap = new HashMap<>(suiteNodeMap);
        HashMap<String, ArrayList<String>> forNodeMap = getNodeIdsMap(ctx);
        //at this time forNodeMap only contains for exprlist

        writeUp(forNodeMap, ctx);
        for (String id : suiteNodeMap.keySet()) {
            String lastIdSSA = findLastIdSSA(id, ctx);
            //if (lastIdSSA != null) {
                ArrayList<String> idListInSuite = suiteNodeMap.get(id);
                String lastIdSSAinSuite = idListInSuite.get(idListInSuite.size() - 1);
                String lastIdSSAidOnly = (lastIdSSA == null ? "None" :  lastIdSSA);
                String lastIdSSAinSuiteOnly = lastIdSSAinSuite;
                if (lastIdSSAidOnly.contains("?")) {
                    lastIdSSAidOnly = lastIdSSAidOnly.substring(0, lastIdSSAidOnly.indexOf('?'));
                }
                if (lastIdSSAinSuite.contains("?")) {
                    lastIdSSAinSuiteOnly = lastIdSSAinSuite.substring(0, lastIdSSAinSuite.indexOf('?'));
                }
                String phiIdEntry = id +"_"+ globalIdsMap.get(id).size() + "?phiEntry" +"(" + lastIdSSAidOnly + "," + lastIdSSAinSuiteOnly + ")";
                if (!forNodeMap.containsKey(id)) {
                    forNodeMap.put(id, new ArrayList<>());
                }
                forNodeMap.get(id).add(phiIdEntry);
                mergedForNodeMap.get(id).add(0, phiIdEntry);
                //System.out.println(phiIdEntry + " insert point after" + lastIdSSA);
                globalIdsMap.get(id).add(globalIdsMap.get(id).indexOf(findLastIdSSAtoDefine(id, ctx))  + 1, phiIdEntry);

                String exitSSA = suiteNodeMap.get(id).get(suiteNodeMap.get(id).size() - 1);
                String exitSSAIdIdOnly = exitSSA;
                if (exitSSA.contains("?")) {
                    exitSSAIdIdOnly = exitSSA.substring(0, exitSSA.indexOf('?'));
                }
                String phidIdExit = id + "_" +  globalIdsMap.get(id).size() + "?phiExit" +"(" + lastIdSSAidOnly +","+exitSSAIdIdOnly + ")";
                if (!forNodeMap.containsKey(id)) {
                    forNodeMap.put(id, new ArrayList<>());
                }
                forNodeMap.get(id).add(phidIdExit);
                mergedForNodeMap.get(id).add(phidIdExit);
                globalIdsMap.get(id).add(phidIdExit);
            //}
        }
        writeUp(mergedForNodeMap, ctx);
    }
}