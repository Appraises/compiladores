package semantico;

import dplusplus.analysis.DepthFirstAdapter;
import dplusplus.node.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DeclarationVisitor extends DepthFirstAdapter {

    private final SymbolTable symbolTable;
    private final List<SemanticError> errors;

    private int entryPointCount = 0;

    private Symbol currentClassSymbol = null;

    // Profundidade de aninhamento em método/bloco. Uma declaração só é
    // atributo da classe quando localDepth == 0 (corpo da classe). Declarações
    // dentro de métodos/blocos são variáveis locais e NÃO viram membros.
    private int localDepth = 0;

    private boolean isClassAttributeContext() {
        return currentClassSymbol != null && localDepth == 0;
    }

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
    // Pré-passe: registro de classes e herança (independente de ordem)
    // =========================================================

    /**
     * Primeiro passe: registra TODAS as classes e suas relações de herança
     * antes de qualquer resolução de membros. Isso torna a análise independente
     * da ordem textual das declarações de classe (uma classe filha pode ser
     * declarada antes da sua classe mãe no arquivo).
     */
    public void collectClasses(Node ast) {
        // 1. Registrar relações de herança (genealogia) e símbolos de classe
        ast.apply(new DepthFirstAdapter() {
            @Override
            public void caseAGenealogia(AGenealogia node) {
                for (PRelacao pRelacao : node.getRelacao()) {
                    if (pRelacao instanceof ARelacao) {
                        ARelacao rel = (ARelacao) pRelacao;
                        String filha = rel.getFilha().getText().trim();
                        String mae = rel.getMae().getText().trim();
                        int line = rel.getFilha().getLine();
                        int col = rel.getFilha().getPos();

                        // D++ suporta apenas herança simples
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

                Symbol classSym = new Symbol();
                classSym.setName(className);
                classSym.setType(Type.CLASS);
                classSym.setClassName(className);
                classSym.setKind(Symbol.SymbolKind.CLASS);
                classSym.setLine(line);
                classSym.setCol(col);

                // Herança: se não veio da genealogia, a mãe implícita é Root
                String parentName = symbolTable.getParentClass(className);
                if (parentName == null) {
                    parentName = "Root";
                    symbolTable.registerInheritance(className, "Root");
                }
                classSym.setParentClassName(parentName);

                if (!symbolTable.registerClass(classSym)) {
                    error("Classe '" + className + "' ja foi declarada.", line, col);
                }
            }
        });

        // 2. Com todas as classes registradas, validar mãe-existe e herança circular
        for (Symbol classSym : symbolTable.getAllClasses()) {
            String className = classSym.getName();
            if (className.equals("Root") || className.equals("Periphericals")) {
                continue;
            }
            String parentName = classSym.getParentClassName();
            if (parentName != null && !symbolTable.classExists(parentName)) {
                error("Classe mae '" + parentName + "' de '" + className +
                        "' nao foi declarada.", classSym.getLine(), classSym.getCol());
            }
            if (symbolTable.hasCircularInheritance(className)) {
                error("Heranca circular detectada envolvendo a classe '" + className + "'.",
                        classSym.getLine(), classSym.getCol());
            }
        }
    }

    /**
     * Passe final: executado após a coleta de membros de TODAS as classes.
     * Valida sobrescrita de métodos e determina quais classes são abstratas
     * (considerando métodos abstratos herdados e não implementados).
     * Independente da ordem textual das classes.
     */
    public void finalizeInheritance() {
        for (Symbol classSym : symbolTable.getAllClasses()) {
            String className = classSym.getName();
            if (className.equals("Root")) {
                continue;
            }
            // Verificar compatibilidade de assinatura em sobrescritas
            for (Symbol member : classSym.getMembers()) {
                if (!member.isMethod()) {
                    continue;
                }
                Symbol inherited = findInheritedMethod(classSym, member.getName());
                if (inherited != null) {
                    validateSignatureCompatibility(inherited, member, member.getLine(), member.getCol());
                }
            }
            // Uma classe é abstrata se possui método abstrato (próprio ou herdado)
            // sem implementação concreta na cadeia até ela.
            classSym.setHasAbstractMethods(computeIsAbstract(className));
        }
    }

    /**
     * Determina se uma classe é abstrata: para cada nome de método visível
     * (próprio ou herdado), a declaração mais derivada (a que prevalece) é
     * abstrata? Se sim, a classe não pode ser instanciada.
     */
    private boolean computeIsAbstract(String className) {
        Set<String> methodNames = new LinkedHashSet<>();
        Symbol classSym = symbolTable.lookupClass(className);
        if (classSym != null) {
            for (Symbol m : classSym.getMembers()) {
                if (m.isMethod()) methodNames.add(m.getName());
            }
        }
        for (String ancestor : symbolTable.getAncestorChain(className)) {
            Symbol a = symbolTable.lookupClass(ancestor);
            if (a != null) {
                for (Symbol m : a.getMembers()) {
                    if (m.isMethod()) methodNames.add(m.getName());
                }
            }
        }
        for (String name : methodNames) {
            // lookupMember retorna a declaração mais derivada (a que prevalece)
            Symbol effective = symbolTable.lookupMember(className, name);
            if (effective != null && effective.isMethod() && effective.isAbstract()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void inADefClasse(ADefClasse node) {
        String className = node.getIdClasse().getText().trim();

        // A classe já foi criada e registrada no pré-passe (collectClasses)
        Symbol classSym = symbolTable.lookupClass(className);

        // Empilhar escopo de classe
        symbolTable.pushScope();
        symbolTable.setCurrentClassName(className);
        currentClassSymbol = classSym;
    }

    @Override
    public void outADefClasse(ADefClasse node) {
        // Determinação de classe abstrata é feita em finalizeInheritance(),
        // após a coleta de membros de todas as classes.
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
        // (a verificação de instanciação de classe abstrata é feita no
        //  SemanticCheckerVisitor, após finalizeInheritance())
        if (!symbolTable.classExists(className)) {
            error("Classe '" + className + "' nao foi declarada.", line, col);
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

        // Somente atributos da classe (não objetos locais de métodos) viram membros
        if (isClassAttributeContext()) {
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

        if (isClassAttributeContext()) {
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

        if (isClassAttributeContext()) {
            currentClassSymbol.getMembers().add(sym);
        }
    }

    @Override
    public void inAProcedimentoCmdMetodo(AProcedimentoCmdMetodo node) {
        String methodName = node.getId().getText().trim();
        int line = node.getId().getLine();
        int col = node.getId().getPos();
        boolean isEntry = node.getPontoEntrada() != null;

        localDepth++;

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

        // Registrar como membro próprio da classe (sobrescrita/abstrato tratados
        // em finalizeInheritance). Detecção de método duplicado na mesma classe:
        if (currentClassSymbol != null) {
            if (currentClassSymbol.findMember(methodName) != null) {
                error("Metodo '" + methodName + "' ja foi declarado nesta classe.", line, col);
            } else {
                currentClassSymbol.getMembers().add(sym);
            }
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
        localDepth--;
    }

    @Override
    public void caseAProcedimentoVazioMetodo(AProcedimentoVazioMetodo node) {
        String methodName = node.getId().getText().trim();
        int line = node.getId().getLine();
        int col = node.getId().getPos();

        // Um procedimento sem corpo não pode ser o ponto de entrada do programa.
        if (node.getPontoEntrada() != null) {
            entryPointCount++;
            error("O ponto de entrada '>>' nao pode ser um procedimento abstrato " +
                    "(sem corpo): '" + methodName + "'.", line, col);
        }

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

        localDepth++;

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

        // Registrar como membro próprio da classe (sobrescrita/abstrato tratados
        // em finalizeInheritance). Detecção de método duplicado na mesma classe:
        if (currentClassSymbol != null) {
            if (currentClassSymbol.findMember(methodName) != null) {
                error("Metodo '" + methodName + "' ja foi declarado nesta classe.", line, col);
            } else {
                currentClassSymbol.getMembers().add(sym);
            }
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
        localDepth--;
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
        localDepth++;
    }

    @Override
    public void outABloco(ABloco node) {
        symbolTable.popScope();
        localDepth--;
    }

    @Override
    public void inABlocoExp(ABlocoExp node) {
        symbolTable.pushScope();
        localDepth++;
    }

    @Override
    public void outABlocoExp(ABlocoExp node) {
        symbolTable.popScope();
        localDepth--;
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
