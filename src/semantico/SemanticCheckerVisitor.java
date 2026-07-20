package semantico;

import dplusplus.analysis.DepthFirstAdapter;
import dplusplus.node.*;

import java.util.ArrayList;
import java.util.List;

public class SemanticCheckerVisitor extends DepthFirstAdapter {

    private final SymbolTable symbolTable;
    private final List<SemanticError> errors;

    // Auxiliar para rastrear o tipo associado a expressões usando setOut/getOut
    // (herdado do AnalysisAdapter como HashMap<Node, Object>)

    public SemanticCheckerVisitor(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        this.errors = new ArrayList<>();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<SemanticError> getErrors() {
        return errors;
    }

    private void error(String msg, int line, int col) {
        errors.add(new SemanticError(msg, line, col));
    }

    private void error(String msg) {
        errors.add(new SemanticError(msg));
    }

    // Obtém o tipo anotado de um nó (via setOut).
    private Type typeOf(Node node) {
        Object val = getOut(node);
        if (val instanceof Type) return (Type) val;
        return null;
    }

    // Obtém o nome de classe anotado de um nó (via setOut com prefixo "C:").
    private String classNameOf(Node node) {
        Object val = getOut(node);
        if (val instanceof String) return (String) val;
        return null;
    }

    // Anota tipo simples no nó.
    private void annotate(Node node, Type type) {
        setOut(node, type);
    }

    // Anota tipo CLASS com nome de classe no nó usando dois campos.
    private void annotateClass(Node node, String className) {
        setOut(node, className); // className stored as String
        setIn(node, Type.CLASS); // type stored in setIn
    }

    // Obtém o tipo de forma unificada (tanto primitivo quanto CLASS).
    private Type getType(Node node) {
        Object inVal = getIn(node);
        if (inVal instanceof Type) return (Type) inVal;
        Object outVal = getOut(node);
        if (outVal instanceof Type) return (Type) outVal;
        if (outVal instanceof String) return Type.CLASS;
        return null;
    }

    // Obtém o className de um nó seja de que forma for anotado.
    private String getClassName(Node node) {
        Object inVal = getIn(node);
        if (inVal instanceof Type) {
            // tipo está no in, classname no out
            Object outVal = getOut(node);
            if (outVal instanceof String) return (String) outVal;
        }
        return null;
    }

    // Verifica se dois tipos são compatíveis (inclui polimorfismo)
    private boolean typesCompatible(Type t1, String cn1, Type t2, String cn2) {
        if (t1 == null || t2 == null) return true; // erro já reportado antes
        if (t1 != t2) return false;
        if (t1 == Type.CLASS) {
            // Permite polimorfismo: t2's class pode ser subclasse de t1's class
            return cn1 != null && cn2 != null &&
                    (cn1.equals(cn2) || symbolTable.isSubclassOf(cn2, cn1));
        }
        return true;
    }

    @Override
    public void inAPrograma(APrograma node) {
        symbolTable.pushScope();
    }

    @Override
    public void outAPrograma(APrograma node) {
        symbolTable.popScope();
    }

    @Override
    public void inADefClasse(ADefClasse node) {
        String className = node.getIdClasse().getText().trim();
        symbolTable.pushScope();
        symbolTable.setCurrentClassName(className);

        // Injetar membros da classe no escopo corrente
        Symbol classSym = symbolTable.lookupClass(className);
        if (classSym != null) {
            for (Symbol member : classSym.getMembers()) {
                symbolTable.declare(member);
            }
        }
    }

    @Override
    public void outADefClasse(ADefClasse node) {
        symbolTable.popScope();
        symbolTable.setCurrentClassName(null);
    }

    @Override
    public void inAProcedimentoCmdMetodo(AProcedimentoCmdMetodo node) {
        symbolTable.pushScope();
        symbolTable.setCurrentMethodName(node.getId().getText().trim());
        // Declarar parâmetros no escopo do método
        for (PParametro p : node.getParametro()) {
            if (p instanceof AParametro) {
                AParametro ap = (AParametro) p;
                Symbol param = new Symbol();
                param.setName(ap.getId().getText().trim());
                param.setType(Type.fromTipo(ap.getTipo()));
                param.setClassName(Type.classNameFromTipo(ap.getTipo()));
                param.setKind(Symbol.SymbolKind.PARAMETER);
                symbolTable.declare(param);
            }
        }
    }

    @Override
    public void outAProcedimentoCmdMetodo(AProcedimentoCmdMetodo node) {
        symbolTable.popScope();
        symbolTable.setCurrentMethodName(null);
    }

    @Override
    public void inAFuncaoExpMetodo(AFuncaoExpMetodo node) {
        symbolTable.pushScope();
        symbolTable.setCurrentMethodName(node.getId().getText().trim());
        for (PParametro p : node.getParametro()) {
            if (p instanceof AParametro) {
                AParametro ap = (AParametro) p;
                Symbol param = new Symbol();
                param.setName(ap.getId().getText().trim());
                param.setType(Type.fromTipo(ap.getTipo()));
                param.setClassName(Type.classNameFromTipo(ap.getTipo()));
                param.setKind(Symbol.SymbolKind.PARAMETER);
                symbolTable.declare(param);
            }
        }
    }

    @Override
    public void outAFuncaoExpMetodo(AFuncaoExpMetodo node) {
        symbolTable.popScope();
        symbolTable.setCurrentMethodName(null);
    }

    @Override
    public void inABloco(ABloco node) {
        symbolTable.pushScope();
    }

    @Override
    public void outABloco(ABloco node) {
        symbolTable.popScope();
    }

    @Override
    public void inABlocoExp(ABlocoExp node) {
        symbolTable.pushScope();
    }

    @Override
    public void outABlocoExp(ABlocoExp node) {
        symbolTable.popScope();
    }

    @Override
    public void outAVarDeclaracao(AVarDeclaracao node) {
        String varName = node.getId().getText().trim();
        int line = node.getId().getLine();
        int col = node.getId().getPos();

        Type declaredType = Type.fromTipoPrimitivo(node.getTipoPrimitivo());
        Type expType = getType(node.getExp());
        String expCN = getClassName(node.getExp());

        if (expType != null && expType != declaredType) {
            error("Tipo da expressao de inicializacao de '" + varName +
                    "' incompativel: esperado " + Type.describe(declaredType, null) +
                    ", encontrado " + Type.describe(expType, expCN) + ".", line, col);
        }

        // Registrar no escopo
        Symbol sym = new Symbol();
        sym.setName(varName);
        sym.setType(declaredType);
        sym.setKind(Symbol.SymbolKind.VARIABLE);
        sym.setMutable(true);
        sym.setLine(line);
        sym.setCol(col);
        symbolTable.declare(sym);
    }

    @Override
    public void outAConsDeclaracao(AConsDeclaracao node) {
        String varName = node.getId().getText().trim();
        int line = node.getId().getLine();
        int col = node.getId().getPos();

        Type declaredType = Type.fromTipoPrimitivo(node.getTipoPrimitivo());
        Type expType = getType(node.getExp());
        String expCN = getClassName(node.getExp());

        if (expType != null && expType != declaredType) {
            error("Tipo da expressao de inicializacao de '" + varName +
                    "' incompativel: esperado " + Type.describe(declaredType, null) +
                    ", encontrado " + Type.describe(expType, expCN) + ".", line, col);
        }

        Symbol sym = new Symbol();
        sym.setName(varName);
        sym.setType(declaredType);
        sym.setKind(Symbol.SymbolKind.CONSTANT);
        sym.setMutable(false);
        sym.setLine(line);
        sym.setCol(col);
        symbolTable.declare(sym);
    }

    @Override
    public void outAObjDeclaracao(AObjDeclaracao node) {
        String varName = node.getId().getText().trim();
        int line = node.getId().getLine();
        int col = node.getId().getPos();
        String className = node.getIdClasse().getText().trim();

        // Não é possível instanciar uma classe abstrata (com método abstrato
        // próprio ou herdado sem implementação). hasAbstractMethods já foi
        // calculado em DeclarationVisitor.finalizeInheritance().
        Symbol classSym = symbolTable.lookupClass(className);
        if (classSym != null && classSym.hasAbstractMethods()) {
            error("Nao e possivel instanciar a classe abstrata '" + className + "'.", line, col);
        }

        Symbol sym = new Symbol();
        sym.setName(varName);
        sym.setType(Type.CLASS);
        sym.setClassName(className);
        sym.setKind(Symbol.SymbolKind.OBJECT);
        sym.setMutable(true);
        sym.setLine(line);
        sym.setCol(col);
        symbolTable.declare(sym);
    }

    @Override
    public void outAInteiroExp(AInteiroExp node) {
        annotate(node, Type.NUMBER);
    }

    @Override
    public void outARealExp(ARealExp node) {
        annotate(node, Type.NUMBER);
    }

    @Override
    public void outAYesExp(AYesExp node) {
        annotate(node, Type.ANSWER);
    }

    @Override
    public void outANoExp(ANoExp node) {
        annotate(node, Type.ANSWER);
    }

    @Override
    public void outAIdExp(AIdExp node) {
        String name = node.getId().getText().trim();
        int line = node.getId().getLine();
        int col = node.getId().getPos();

        Symbol sym = resolveIdentifier(name, line, col);
        if (sym == null) return;

        if (sym.getType() == Type.CLASS) {
            annotateClass(node, sym.getClassName());
        } else {
            annotate(node, sym.getType());
        }
    }

    // Busca um identificador no escopo local + membros da classe atual + ancestrais
    private Symbol resolveIdentifier(String name, int line, int col) {
        // Escopo de variáveis locais / parâmetros
        Symbol sym = symbolTable.lookup(name);
        if (sym != null) return sym;

        // Membro da classe corrente e seus ancestrais
        String currentClass = symbolTable.getCurrentClassName();
        if (currentClass != null) {
            sym = symbolTable.lookupMember(currentClass, name);
            if (sym != null) return sym;
        }

        error("Identificador '" + name + "' nao foi declarado.", line, col);
        return null;
    }

    @Override
    public void outASomaExp(ASomaExp node) {
        checkBinaryNumeric(node, node.getEsq(), node.getDir(), "+");
        annotate(node, Type.NUMBER);
    }

    @Override
    public void outASubtracaoExp(ASubtracaoExp node) {
        checkBinaryNumeric(node, node.getEsq(), node.getDir(), "-");
        annotate(node, Type.NUMBER);
    }

    @Override
    public void outAMultExp(AMultExp node) {
        checkBinaryNumeric(node, node.getEsq(), node.getDir(), "*");
        annotate(node, Type.NUMBER);
    }

    @Override
    public void outADivisaoExp(ADivisaoExp node) {
        checkBinaryNumeric(node, node.getEsq(), node.getDir(), "/");
        annotate(node, Type.NUMBER);
    }

    @Override
    public void outANegativoExp(ANegativoExp node) {
        Type t = getType(node.getExp());
        if (t != null && t != Type.NUMBER) {
            error("Operador unario '-' requer operando do tipo NUMBER, encontrado " + t + ".");
        }
        annotate(node, Type.NUMBER);
    }

    private void checkBinaryNumeric(Node parent, PExp left, PExp right, String op) {
        Type lt = getType(left);
        Type rt = getType(right);
        if (lt != null && lt != Type.NUMBER) {
            error("Operando esquerdo do operador '" + op + "' deve ser NUMBER, encontrado " + lt + ".");
        }
        if (rt != null && rt != Type.NUMBER) {
            error("Operando direito do operador '" + op + "' deve ser NUMBER, encontrado " + rt + ".");
        }
    }

    @Override
    public void outAMenorExp(AMenorExp node) {
        checkBinaryNumeric(node, node.getEsq(), node.getDir(), "<");
        annotate(node, Type.ANSWER);
    }

    @Override
    public void outAMaiorExp(AMaiorExp node) {
        checkBinaryNumeric(node, node.getEsq(), node.getDir(), ">");
        annotate(node, Type.ANSWER);
    }

    @Override
    public void outAIgualExp(AIgualExp node) {
        Type lt = getType(node.getEsq());
        Type rt = getType(node.getDir());
        String lcn = getClassName(node.getEsq());
        String rcn = getClassName(node.getDir());

        if (lt != null && rt != null) {
            if (!typesCompatible(lt, lcn, rt, rcn) && !typesCompatible(rt, rcn, lt, lcn)) {
                error("Operador '=' requer operandos do mesmo tipo: " +
                        Type.describe(lt, lcn) + " != " + Type.describe(rt, rcn) + ".");
            }
        }
        annotate(node, Type.ANSWER);
    }

    @Override
    public void outAAndExp(AAndExp node) {
        checkBinaryBoolean(node.getEsq(), node.getDir(), "and");
        annotate(node, Type.ANSWER);
    }

    @Override
    public void outAOrExp(AOrExp node) {
        checkBinaryBoolean(node.getEsq(), node.getDir(), "or");
        annotate(node, Type.ANSWER);
    }

    @Override
    public void outANegacaoExp(ANegacaoExp node) {
        Type t = getType(node.getExp());
        if (t != null && t != Type.ANSWER) {
            error("Operador '!' requer operando do tipo ANSWER, encontrado " + t + ".");
        }
        annotate(node, Type.ANSWER);
    }

    private void checkBinaryBoolean(PExp left, PExp right, String op) {
        Type lt = getType(left);
        Type rt = getType(right);
        if (lt != null && lt != Type.ANSWER) {
            error("Operando esquerdo de '" + op + "' deve ser ANSWER, encontrado " + lt + ".");
        }
        if (rt != null && rt != Type.ANSWER) {
            error("Operando direito de '" + op + "' deve ser ANSWER, encontrado " + rt + ".");
        }
    }

    @Override
    public void outATernarioExp(ATernarioExp node) {
        Type cond = getType(node.getCondicao());
        if (cond != null && cond != Type.ANSWER) {
            error("Condicao do operador ternario deve ser ANSWER, encontrado " + cond + ".");
        }

        Type ifType = getType(node.getExpIf());
        Type elseType = getType(node.getExpElse());
        String ifCN = getClassName(node.getExpIf());
        String elseCN = getClassName(node.getExpElse());

        if (ifType != null && elseType != null &&
                !typesCompatible(ifType, ifCN, elseType, elseCN) &&
                !typesCompatible(elseType, elseCN, ifType, ifCN)) {
            error("Ramos do operador ternario devem ter tipos compativeis: " +
                    Type.describe(ifType, ifCN) + " vs " + Type.describe(elseType, elseCN) + ".");
        }

        if (ifType != null) {
            if (ifType == Type.CLASS) {
                annotateClass(node, ifCN);
            } else {
                annotate(node, ifType);
            }
        }
    }

    @Override
    public void outAChamadaExp(AChamadaExp node) {
        if (node.getChamada() instanceof AChamada) {
            AChamada chamada = (AChamada) node.getChamada();
            Symbol method = resolveChamada(chamada);
            if (method != null) {
                if (method.getKind() != Symbol.SymbolKind.FUNCTION) {
                    error("'" + method.getName() + "' e um procedure e nao pode ser usado como expressao.",
                            chamada.getBase().getLine(), chamada.getBase().getPos());
                } else {
                    validateArguments(chamada, method);
                    if (method.getReturnType() == Type.CLASS) {
                        annotateClass(node, method.getReturnClassName());
                    } else {
                        annotate(node, method.getReturnType() != null ? method.getReturnType() : Type.VOID);
                    }
                }
            }
        }
    }

    @Override
    public void outAAcessoExp(AAcessoExp node) {
        if (node.getAcesso() instanceof AAcesso) {
            AAcesso acesso = (AAcesso) node.getAcesso();
            Symbol member = resolveAcesso(acesso);
            if (member != null) {
                if (member.getType() == Type.CLASS) {
                    annotateClass(node, member.getClassName());
                } else {
                    annotate(node, member.getType());
                }
            }
        }
    }

    @Override
    public void outABlocoRetornoExp(ABlocoRetornoExp node) {
        // O bloco_exp tem a forma: start (decls)* exp finish
        // O tipo do bloco é o tipo da expressão final
        if (node.getBlocoExp() instanceof ABlocoExp) {
            ABlocoExp blocoExp = (ABlocoExp) node.getBlocoExp();
            Type t = getType(blocoExp.getExp());
            String cn = getClassName(blocoExp.getExp());
            if (t == Type.CLASS) {
                annotateClass(node, cn);
            } else {
                annotate(node, t);
            }
        }
    }

    @Override
    public void outAAtribuicaoComando(AAtribuicaoComando node) {
        String varName = node.getId().getText().trim();
        int line = node.getId().getLine();
        int col = node.getId().getPos();

        Symbol sym = resolveIdentifier(varName, line, col);
        if (sym == null) return;

        // Verificar mutabilidade
        if (!sym.isMutable()) {
            error("Nao e possivel reatribuir a variavel unalterable '" + varName + "'.", line, col);
        }

        // Verificar compatibilidade de tipo
        Type expType = getType(node.getExp());
        String expCN = getClassName(node.getExp());

        if (expType != null && !typesCompatible(sym.getType(), sym.getClassName(), expType, expCN)) {
            error("Tipo incompativel na atribuicao de '" + varName + "': esperado " +
                    Type.describe(sym.getType(), sym.getClassName()) +
                    ", encontrado " + Type.describe(expType, expCN) + ".", line, col);
        }
    }

    @Override
    public void outAAtribuicaoObjComando(AAtribuicaoObjComando node) {
        if (!(node.getAcesso() instanceof AAcesso)) return;
        AAcesso acesso = (AAcesso) node.getAcesso();

        String base = acesso.getBase().getText().trim();
        int line = acesso.getBase().getLine();
        int col = acesso.getBase().getPos();

        // Resolver a cadeia de acessos até o membro alvo
        Symbol member = resolveAcesso(acesso);
        if (member == null) return;

        // Verificar mutabilidade
        if (!member.isMutable()) {
            error("Nao e possivel reatribuir o atributo unalterable '" +
                    member.getName() + "'.", line, col);
        }

        // Verificar que atributos de outro objeto não são acessados via ->
        // (atributos são privados em D++)
        if (acesso.getAcessos().size() > 0 && member.isAttribute()) {
            // acesso via -> a atributo de outro objeto
            String currentClass = symbolTable.getCurrentClassName();
            Symbol baseSym = symbolTable.lookup(base);
            if (baseSym != null && baseSym.getType() == Type.CLASS) {
                String baseClass = baseSym.getClassName();
                // Acesso direto é permitido se baseClass é a própria classe ou ancestral
                if (!symbolTable.isSubclassOf(currentClass, baseClass) &&
                        !symbolTable.isSubclassOf(baseClass, currentClass)) {
                    error("Atributo '" + member.getName() + "' e privado e nao pode ser " +
                            "acessado via '->' de fora da hierarquia da classe '" +
                            baseClass + "'.", line, col);
                }
            }
        }

        // Verificar tipo da expressão
        Type expType = getType(node.getExp());
        String expCN = getClassName(node.getExp());

        if (expType != null && !typesCompatible(member.getType(), member.getClassName(), expType, expCN)) {
            error("Tipo incompativel na atribuicao a '" + member.getName() +
                    "': esperado " + Type.describe(member.getType(), member.getClassName()) +
                    ", encontrado " + Type.describe(expType, expCN) + ".", line, col);
        }
    }

    @Override
    public void outACondicionalComando(ACondicionalComando node) {
        Type condType = getType(node.getExp());
        if (condType != null && condType != Type.ANSWER) {
            error("Condicao do 'in case that' deve ser do tipo ANSWER, encontrado " + condType + ".");
        }
    }

    @Override
    public void outALacoComando(ALacoComando node) {
        Type condType = getType(node.getExp());
        if (condType != null && condType != Type.ANSWER) {
            error("Condicao do 'as long as' deve ser do tipo ANSWER, encontrado " + condType + ".");
        }
    }

    @Override
    public void outAChamadaComando(AChamadaComando node) {
        if (node.getChamada() instanceof AChamada) {
            AChamada chamada = (AChamada) node.getChamada();
            Symbol method = resolveChamada(chamada);
            if (method != null) {
                validateArguments(chamada, method);
            }
        }
    }

    
    // Resolve uma chamada (base.->.acessos[args]) e retorna o símbolo do método
    // Suporta chamadas encadeadas: obj->metodo[] ou a->b->c[]
    private Symbol resolveChamada(AChamada chamada) {
        String baseName = chamada.getBase().getText().trim();
        int line = chamada.getBase().getLine();
        int col = chamada.getBase().getPos();

        List<TId> acessos = chamada.getAcessos();

        if (acessos.isEmpty()) {
            // Chamada local: método da própria classe ou ancestrais
            String currentClass = symbolTable.getCurrentClassName();
            if (currentClass != null) {
                Symbol method = symbolTable.lookupMember(currentClass, baseName);
                if (method != null && method.isMethod()) {
                    return method;
                }
            }
            error("Metodo '" + baseName + "' nao encontrado.", line, col);
            return null;
        }

        // Chamada encadeada: baseName é um objeto, acessos[0..n-2] são objetos intermediários,
        // acessos[n-1] é o método chamado.
        Symbol objSym = resolveIdentifier(baseName, line, col);
        if (objSym == null) return null;

        String currentClass = objSym.getClassName();
        if (objSym.getType() != Type.CLASS || currentClass == null) {
            error("'" + baseName + "' nao e um objeto de classe.", line, col);
            return null;
        }

        // Navegar pelos acessos intermediários
        for (int i = 0; i < acessos.size() - 1; i++) {
            String memberName = acessos.get(i).getText().trim();
            Symbol member = symbolTable.lookupMember(currentClass, memberName);
            if (member == null) {
                error("Membro '" + memberName + "' nao encontrado na classe '" + currentClass + "'.",
                        acessos.get(i).getLine(), acessos.get(i).getPos());
                return null;
            }
            if (member.getType() != Type.CLASS) {
                error("'" + memberName + "' nao e um objeto, nao pode ser usado em acesso encadeado.",
                        acessos.get(i).getLine(), acessos.get(i).getPos());
                return null;
            }
            currentClass = member.getClassName();
        }

        // Último acesso: deve ser o método
        String methodName = acessos.get(acessos.size() - 1).getText().trim();
        Symbol method = symbolTable.lookupMember(currentClass, methodName);
        if (method == null) {
            error("Metodo '" + methodName + "' nao encontrado na classe '" + currentClass + "'.",
                    acessos.get(acessos.size() - 1).getLine(),
                    acessos.get(acessos.size() - 1).getPos());
            return null;
        }
        if (!method.isMethod()) {
            error("'" + methodName + "' nao e um metodo em '" + currentClass + "'.",
                    acessos.get(acessos.size() - 1).getLine(),
                    acessos.get(acessos.size() - 1).getPos());
            return null;
        }

        return method;
    }

    // Resolve um acesso a atributo (base.->acessos) e retorna o símbolo do membro final
    private Symbol resolveAcesso(AAcesso acesso) {
        String baseName = acesso.getBase().getText().trim();
        int line = acesso.getBase().getLine();
        int col = acesso.getBase().getPos();

        List<TId> acessos = acesso.getAcessos();

        Symbol objSym = resolveIdentifier(baseName, line, col);
        if (objSym == null) return null;

        if (acessos.isEmpty()) {
            // Acesso direto ao próprio identificador (não via ->)
            return objSym;
        }

        String currentClass = objSym.getClassName();
        if (objSym.getType() != Type.CLASS || currentClass == null) {
            error("'" + baseName + "' nao e um objeto de classe.", line, col);
            return null;
        }

        // Navegar pelos acessos
        Symbol lastMember = null;
        for (int i = 0; i < acessos.size(); i++) {
            String memberName = acessos.get(i).getText().trim();
            Symbol member = symbolTable.lookupMember(currentClass, memberName);
            if (member == null) {
                error("Membro '" + memberName + "' nao encontrado na classe '" + currentClass + "'.",
                        acessos.get(i).getLine(), acessos.get(i).getPos());
                return null;
            }

            // Verificar visibilidade: atributos de outra classe são privados
            if (member.isAttribute() && i < acessos.size() - 1) {
                // atributo usado como intermediário (precisa ser objeto)
                if (member.getType() != Type.CLASS) {
                    error("'" + memberName + "' nao e um objeto e nao pode ser usado em cadeia de acesso.",
                            acessos.get(i).getLine(), acessos.get(i).getPos());
                    return null;
                }
            }

            lastMember = member;
            if (member.getType() == Type.CLASS && i < acessos.size() - 1) {
                currentClass = member.getClassName();
            }
        }

        return lastMember;
    }

    // Valida a aridade e os tipos dos argumentos de uma chamada
    private void validateArguments(AChamada chamada, Symbol method) {
        List<PExp> args = chamada.getArgumentos();
        List<Symbol> params = method.getParameters();

        // Caso especial: Periphericals.show aceita qualquer tipo primitivo
        if (method.getName().equals("show") &&
                symbolTable.lookupClass("Periphericals") != null) {
            Symbol classSym = symbolTable.lookupClass("Periphericals");
            if (classSym.findMember("show") == method) {
                if (args.size() != 1) {
                    error("Metodo 'show' espera exatamente 1 argumento, encontrado " + args.size() + ".");
                }
                // Aceita qualquer primitivo (NUMBER ou ANSWER)
                return;
            }
        }

        if (args.size() != params.size()) {
            error("Metodo '" + method.getName() + "' espera " + params.size() +
                    " argumento(s), encontrado " + args.size() + ".",
                    chamada.getBase().getLine(), chamada.getBase().getPos());
            return;
        }

        for (int i = 0; i < params.size(); i++) {
            Type argType = getType(args.get(i));
            String argCN = getClassName(args.get(i));
            Symbol param = params.get(i);

            if (argType != null && !typesCompatible(param.getType(), param.getClassName(),
                    argType, argCN)) {
                error("Argumento " + (i + 1) + " do metodo '" + method.getName() +
                        "' tem tipo incompativel: esperado " +
                        Type.describe(param.getType(), param.getClassName()) +
                        ", encontrado " + Type.describe(argType, argCN) + ".",
                        chamada.getBase().getLine(), chamada.getBase().getPos());
            }
        }
    }
}
