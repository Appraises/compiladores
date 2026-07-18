package semantico;

import dplusplus.analysis.DepthFirstAdapter;
import dplusplus.node.*;

import java.util.ArrayList;
import java.util.List;

public class DeclarationVisitor extends DepthFirstAdapter {

    private final SymbolTable symbolTable;
    private final List<SemanticError> errors;

    private int entryPointCount = 0;

    private Symbol currentClassSymbol = null;

    public DeclarationVisitor(SymbolTable symbolTable) {
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

    private List<Symbol> extractParameters(List<PParametro> paramNodes) {
        List<Symbol> params = new ArrayList<>();
        for (PParametro pParam : paramNodes) {
            if (pParam instanceof AParametro) {
                AParametro ap = (AParametro) pParam;
                Symbol param = new Symbol();
                param.setKind(Symbol.SymbolKind.PARAMETER);
                param.setName(ap.getId().getText().trim());

                PTipo tipo = ap.getTipo();
                param.setType(Type.fromTipo(tipo));
                param.setClassName(Type.classNameFromTipo(tipo));

                if (ap.getId().getLine() > 0) {
                    param.setLine(ap.getId().getLine());
                    param.setCol(ap.getId().getPos());
                }
                params.add(param);
            }
        }
        return params;
    }

    @Override
    public void inAPrograma(APrograma node) {
        symbolTable.pushScope(); // escopo global
    }

    @Override
    public void outAPrograma(APrograma node) {
        // Validação global: exatamente um ponto de entrada
        if (entryPointCount == 0) {
            error("Nenhum ponto de entrada (>>) encontrado no programa. " +
                    "Exatamente um procedure deve ser marcado com >>.");
        } else if (entryPointCount > 1) {
            error("Multiplos pontos de entrada (>>) encontrados no programa. " +
                    "Apenas um procedure pode ser marcado com >>.");
        }

        // Verificar classes abstratas que são instanciadas (via object)
        // (verificação já feita ao processar AObjDeclaracao)

        symbolTable.popScope();
    }

    // =========================================================
    // Genealogia (herança)
    // =========================================================

    @Override
    public void caseAGenealogia(AGenealogia node) {
        // Processar todas as relações sem descer nos filhos automaticamente
        // (evitamos double-processing com caseARelacao)
        for (PRelacao pRelacao : node.getRelacao()) {
            if (pRelacao instanceof ARelacao) {
                ARelacao rel = (ARelacao) pRelacao;
                String filha = rel.getFilha().getText().trim();
                String mae = rel.getMae().getText().trim();
                int line = rel.getFilha().getLine();
                int col = rel.getFilha().getPos();

                // Validar: a classe mãe não pode ser Root (não explicitamente)
                // e não pode criar herança múltipla
                if (symbolTable.getParentClass(filha) != null) {
                    error("Classe '" + filha + "' ja possui uma classe mae declarada. " +
                            "D++ suporta apenas heranca simples.", line, col);
                    continue;
                }

                symbolTable.registerInheritance(filha, mae);
            }
        }
    }

    @Override
    public void inADefClasse(ADefClasse node) {
        String className = node.getIdClasse().getText().trim();
        int line = node.getIdClasse().getLine();
        int col = node.getIdClasse().getPos();

        // Criar símbolo de classe
        Symbol classSym = new Symbol();
        classSym.setName(className);
        classSym.setType(Type.CLASS);
        classSym.setClassName(className);
        classSym.setKind(Symbol.SymbolKind.CLASS);
        classSym.setLine(line);
        classSym.setCol(col);

        // Registrar herança (se não veio da genealogia, é Root)
        String parentName = symbolTable.getParentClass(className);
        if (parentName == null) {
            parentName = "Root";
            symbolTable.registerInheritance(className, "Root");
        }
        classSym.setParentClassName(parentName);

        // Validar que a classe mãe existe
        if (!symbolTable.classExists(parentName)) {
            error("Classe mae '" + parentName + "' de '" + className +
                    "' nao foi declarada.", line, col);
        }

        // Verificar herança circular
        if (symbolTable.hasCircularInheritance(className)) {
            error("Heranca circular detectada envolvendo a classe '" + className + "'.", line, col);
        }

        // Registrar no mapa global
        if (!symbolTable.registerClass(classSym)) {
            error("Classe '" + className + "' ja foi declarada.", line, col);
        }

        // Herdar membros da classe mãe
        if (symbolTable.classExists(parentName)) {
            Symbol parentSym = symbolTable.lookupClass(parentName);
            if (parentSym != null) {
                for (Symbol member : parentSym.getMembers()) {
                    // Copia membro herdado (somente se não já existe com mesmo nome)
                    if (classSym.findMember(member.getName()) == null) {
                        classSym.getMembers().add(member);
                    }
                }
            }
        }

        // Empilhar escopo de classe
        symbolTable.pushScope();
        symbolTable.setCurrentClassName(className);
        currentClassSymbol = classSym;
    }

    @Override
    public void outADefClasse(ADefClasse node) {
        String className = node.getIdClasse().getText().trim();

        // Verificar se algum método é abstrato (sem corpo)
        Symbol classSym = symbolTable.lookupClass(className);
        if (classSym != null) {
            boolean hasAbstract = false;
            for (Symbol member : classSym.getMembers()) {
                if (member.isMethod() && member.isAbstract()) {
                    hasAbstract = true;
                    break;
                }
            }
            classSym.setHasAbstractMethods(hasAbstract);
        }

        symbolTable.popScope();
        symbolTable.setCurrentClassName(null);
        currentClassSymbol = null;
    }

    @Override
    public void outAObjDeclaracao(AObjDeclaracao node) {
        String className = node.getIdClasse().getText().trim();
        String varName = node.getId().getText().trim();
        int line = node.getId().getLine();
        int col = node.getId().getPos();

        // Validar que a classe existe
        if (!symbolTable.classExists(className)) {
            error("Classe '" + className + "' nao foi declarada.", line, col);
        } else {
            // Validar que a classe não é abstrata
            Symbol classSym = symbolTable.lookupClass(className);
            if (classSym != null && classSym.hasAbstractMethods()) {
                error("Nao e possivel instanciar a classe abstrata '" + className + "'.", line, col);
            }
        }

        // Criar símbolo de objeto
        Symbol sym = new Symbol();
        sym.setName(varName);
        sym.setType(Type.CLASS);
        sym.setClassName(className);
        sym.setKind(Symbol.SymbolKind.OBJECT);
        sym.setMutable(true);
        sym.setLine(line);
        sym.setCol(col);

        // Declarar no escopo atual
        if (!symbolTable.declare(sym)) {
            error("Identificador '" + varName + "' ja foi declarado neste escopo.", line, col);
            return;
        }

        // Se estamos dentro de uma classe, adicionar como membro
        if (currentClassSymbol != null) {
            currentClassSymbol.getMembers().add(sym);
        }
    }

    @Override
    public void outAVarDeclaracao(AVarDeclaracao node) {
        String varName = node.getId().getText().trim();
        int line = node.getId().getLine();
        int col = node.getId().getPos();

        Symbol sym = new Symbol();
        sym.setName(varName);
        sym.setType(Type.fromTipoPrimitivo(node.getTipoPrimitivo()));
        sym.setKind(Symbol.SymbolKind.VARIABLE);
        sym.setMutable(true); // alterable
        sym.setLine(line);
        sym.setCol(col);

        if (!symbolTable.declare(sym)) {
            error("Identificador '" + varName + "' ja foi declarado neste escopo.", line, col);
            return;
        }

        if (currentClassSymbol != null) {
            currentClassSymbol.getMembers().add(sym);
        }
    }

    @Override
    public void outAConsDeclaracao(AConsDeclaracao node) {
        String varName = node.getId().getText().trim();
        int line = node.getId().getLine();
        int col = node.getId().getPos();

        Symbol sym = new Symbol();
        sym.setName(varName);
        sym.setType(Type.fromTipoPrimitivo(node.getTipoPrimitivo()));
        sym.setKind(Symbol.SymbolKind.CONSTANT);
        sym.setMutable(false); // unalterable
        sym.setLine(line);
        sym.setCol(col);

        if (!symbolTable.declare(sym)) {
            error("Identificador '" + varName + "' ja foi declarado neste escopo.", line, col);
            return;
        }

        if (currentClassSymbol != null) {
            currentClassSymbol.getMembers().add(sym);
        }
    }

    @Override
    public void inAProcedimentoCmdMetodo(AProcedimentoCmdMetodo node) {
        String methodName = node.getId().getText().trim();
        int line = node.getId().getLine();
        int col = node.getId().getPos();
        boolean isEntry = node.getPontoEntrada() != null;

        if (isEntry) {
            entryPointCount++;
        }

        Symbol sym = new Symbol();
        sym.setName(methodName);
        sym.setType(Type.VOID);
        sym.setKind(Symbol.SymbolKind.PROCEDURE);
        sym.setEntryPoint(isEntry);
        sym.setAbstract(false);
        sym.setLine(line);
        sym.setCol(col);

        List<Symbol> params = extractParameters(node.getParametro());
        sym.setParameters(params);

        // Verificar sobrescrita: se membro herdado existe, deve ter mesma assinatura
        if (currentClassSymbol != null) {
            Symbol inherited = findInheritedMethod(currentClassSymbol, methodName);
            if (inherited != null) {
                validateSignatureCompatibility(inherited, sym, line, col);
            }
        }

        // Remover membro herdado (abstrato) com mesmo nome, pois estamos implementando
        if (currentClassSymbol != null) {
            currentClassSymbol.getMembers().removeIf(
                    m -> m.getName().equals(methodName) && m.isAbstract());
            currentClassSymbol.getMembers().add(sym);
        }

        // Registrar no escopo atual (para checar dupla declaração de métodos)
        if (!symbolTable.declare(sym)) {
            error("Metodo '" + methodName + "' ja foi declarado nesta classe.", line, col);
        }

        // Empilhar escopo de método e declarar parâmetros
        symbolTable.pushScope();
        symbolTable.setCurrentMethodName(methodName);
        for (Symbol param : params) {
            symbolTable.declare(param);
        }
    }

    @Override
    public void outAProcedimentoCmdMetodo(AProcedimentoCmdMetodo node) {
        symbolTable.popScope();
        symbolTable.setCurrentMethodName(null);
    }

    @Override
    public void caseAProcedimentoVazioMetodo(AProcedimentoVazioMetodo node) {
        String methodName = node.getId().getText().trim();
        int line = node.getId().getLine();
        int col = node.getId().getPos();

        Symbol sym = new Symbol();
        sym.setName(methodName);
        sym.setType(Type.VOID);
        sym.setKind(Symbol.SymbolKind.PROCEDURE);
        sym.setAbstract(true);
        sym.setLine(line);
        sym.setCol(col);

        List<Symbol> params = extractParameters(node.getParametro());
        sym.setParameters(params);

        if (currentClassSymbol != null) {
            if (currentClassSymbol.findMember(methodName) != null) {
                error("Metodo '" + methodName + "' ja foi declarado nesta classe.", line, col);
                return;
            }
            currentClassSymbol.getMembers().add(sym);
        }

        if (!symbolTable.declare(sym)) {
            error("Metodo '" + methodName + "' ja foi declarado nesta classe.", line, col);
        }
    }

    @Override
    public void inAFuncaoExpMetodo(AFuncaoExpMetodo node) {
        String methodName = node.getId().getText().trim();
        int line = node.getId().getLine();
        int col = node.getId().getPos();

        PTipo tipo = node.getTipo();
        Type returnType = Type.fromTipo(tipo);
        String returnClassName = Type.classNameFromTipo(tipo);

        Symbol sym = new Symbol();
        sym.setName(methodName);
        sym.setType(returnType);
        sym.setClassName(returnClassName);
        sym.setKind(Symbol.SymbolKind.FUNCTION);
        sym.setReturnType(returnType);
        sym.setReturnClassName(returnClassName);
        sym.setAbstract(false);
        sym.setLine(line);
        sym.setCol(col);

        List<Symbol> params = extractParameters(node.getParametro());
        sym.setParameters(params);

        // Verificar sobrescrita
        if (currentClassSymbol != null) {
            Symbol inherited = findInheritedMethod(currentClassSymbol, methodName);
            if (inherited != null) {
                validateSignatureCompatibility(inherited, sym, line, col);
            }
        }

        // Remover membro abstrato herdado
        if (currentClassSymbol != null) {
            currentClassSymbol.getMembers().removeIf(
                    m -> m.getName().equals(methodName) && m.isAbstract());
            currentClassSymbol.getMembers().add(sym);
        }

        if (!symbolTable.declare(sym)) {
            error("Metodo '" + methodName + "' ja foi declarado nesta classe.", line, col);
        }

        // Empilhar escopo
        symbolTable.pushScope();
        symbolTable.setCurrentMethodName(methodName);
        for (Symbol param : params) {
            symbolTable.declare(param);
        }
    }

    @Override
    public void outAFuncaoExpMetodo(AFuncaoExpMetodo node) {
        symbolTable.popScope();
        symbolTable.setCurrentMethodName(null);
    }

    @Override
    public void caseAFuncaoVaziaMetodo(AFuncaoVaziaMetodo node) {
        String methodName = node.getId().getText().trim();
        int line = node.getId().getLine();
        int col = node.getId().getPos();

        PTipo tipo = node.getTipo();
        Type returnType = Type.fromTipo(tipo);
        String returnClassName = Type.classNameFromTipo(tipo);

        Symbol sym = new Symbol();
        sym.setName(methodName);
        sym.setType(returnType);
        sym.setClassName(returnClassName);
        sym.setKind(Symbol.SymbolKind.FUNCTION);
        sym.setReturnType(returnType);
        sym.setReturnClassName(returnClassName);
        sym.setAbstract(true);
        sym.setLine(line);
        sym.setCol(col);

        List<Symbol> params = extractParameters(node.getParametro());
        sym.setParameters(params);

        if (currentClassSymbol != null) {
            if (currentClassSymbol.findMember(methodName) != null) {
                error("Metodo '" + methodName + "' ja foi declarado nesta classe.", line, col);
                return;
            }
            currentClassSymbol.getMembers().add(sym);
        }

        if (!symbolTable.declare(sym)) {
            error("Metodo '" + methodName + "' ja foi declarado nesta classe.", line, col);
        }
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

    /**
     * Busca um método herdado (não na própria classe, mas nos ancestrais).
     */
    private Symbol findInheritedMethod(Symbol classSym, String methodName) {
        String parentName = classSym.getParentClassName();
        if (parentName == null) return null;

        Symbol parentSym = symbolTable.lookupClass(parentName);
        while (parentSym != null) {
            Symbol member = parentSym.findMember(methodName);
            if (member != null && member.isMethod()) {
                return member;
            }
            String grandParent = parentSym.getParentClassName();
            if (grandParent == null) break;
            parentSym = symbolTable.lookupClass(grandParent);
        }
        return null;
    }

    /**
     * Valida que a assinatura do método sobrescrito é compatível com a do herdado.
     * D++ não permite sobrecarga, então a assinatura deve ser idêntica.
     */
    private void validateSignatureCompatibility(Symbol inherited, Symbol override,
                                                int line, int col) {
        List<Symbol> ip = inherited.getParameters();
        List<Symbol> op = override.getParameters();

        if (ip.size() != op.size()) {
            error("Metodo '" + override.getName() + "' sobrescreve metodo herdado com " +
                    "numero diferente de parametros (" + ip.size() + " esperado, " +
                    op.size() + " encontrado).", line, col);
            return;
        }

        for (int i = 0; i < ip.size(); i++) {
            Symbol iParam = ip.get(i);
            Symbol oParam = op.get(i);
            if (iParam.getType() != oParam.getType()) {
                error("Metodo '" + override.getName() + "': parametro " + (i + 1) +
                        " tem tipo incompativel com o metodo herdado.", line, col);
            } else if (iParam.getType() == Type.CLASS &&
                    !iParam.getClassName().equals(oParam.getClassName())) {
                error("Metodo '" + override.getName() + "': parametro " + (i + 1) +
                        " tem tipo de classe incompativel com o metodo herdado.", line, col);
            }
        }
    }
}
