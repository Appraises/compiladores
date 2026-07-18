package semantico;

import java.util.*;

/**
 * Tabela de símbolos implementada como pilha de tabelas hash.
 * Gerencia escopos (global, classe, método, bloco) e armazena
 * definições de classes com seus membros e relações de herança.
 */
public class SymbolTable {

    // Pilha de escopos: cada escopo é um mapa nome -> símbolo
    private final Deque<Map<String, Symbol>> scopeStack;

    // Mapa global de definições de classes: nomeClasse -> Symbol(kind=CLASS)
    private final Map<String, Symbol> classMap;

    // Mapa de herança: classeFilha -> classeMãe
    private final Map<String, String> inheritanceMap;

    // Nome da classe corrente (para contexto durante traversal)
    private String currentClassName;

    // Nome do método corrente
    private String currentMethodName;

    public SymbolTable() {
        this.scopeStack = new ArrayDeque<>();
        this.classMap = new LinkedHashMap<>();
        this.inheritanceMap = new HashMap<>();
        this.currentClassName = null;
        this.currentMethodName = null;

        // Pré-registrar a classe Root (ancestral implícito de todas)
        Symbol root = new Symbol("Root", Type.CLASS, Symbol.SymbolKind.CLASS);
        root.setClassName("Root");
        root.setParentClassName(null); // Root não tem mãe
        root.setHasAbstractMethods(false);
        classMap.put("Root", root);

        // Pré-registrar a classe Periphericals com show[] e capture[]
        Symbol periphericals = new Symbol("Periphericals", Type.CLASS, Symbol.SymbolKind.CLASS);
        periphericals.setClassName("Periphericals");
        periphericals.setParentClassName("Root");
        periphericals.setHasAbstractMethods(false);

        // procedure show [number/answer exp] — aceita qualquer primitivo
        // Tratamos show como aceitando um parâmetro de tipo NUMBER por padrão
        // mas na verificação semântica, show é tratado especialmente para aceitar qualquer primitivo
        Symbol showProc = new Symbol("show", Type.VOID, Symbol.SymbolKind.PROCEDURE);
        showProc.setMutable(false);
        Symbol showParam = new Symbol("exp", Type.NUMBER, Symbol.SymbolKind.PARAMETER);
        showProc.getParameters().add(showParam);
        periphericals.getMembers().add(showProc);

        // function number capture [] — sem parâmetros, retorna number
        Symbol captureFunc = new Symbol("capture", Type.NUMBER, Symbol.SymbolKind.FUNCTION);
        captureFunc.setReturnType(Type.NUMBER);
        captureFunc.setMutable(false);
        periphericals.getMembers().add(captureFunc);

        classMap.put("Periphericals", periphericals);
        inheritanceMap.put("Periphericals", "Root");
    }

    // ==================== Gerenciamento de Escopos ====================

    /**
     * Empilha um novo escopo vazio.
     */
    public void pushScope() {
        scopeStack.push(new LinkedHashMap<>());
    }

    /**
     * Desempilha o escopo do topo.
     */
    public Map<String, Symbol> popScope() {
        if (scopeStack.isEmpty()) {
            throw new RuntimeException("Tentativa de desempilhar escopo de pilha vazia.");
        }
        return scopeStack.pop();
    }

    /**
     * Declara um símbolo no escopo atual.
     * Retorna false se já existe um símbolo com o mesmo nome no escopo atual.
     */
    public boolean declare(Symbol symbol) {
        if (scopeStack.isEmpty()) {
            throw new RuntimeException("Nenhum escopo ativo para declaração.");
        }
        Map<String, Symbol> currentScope = scopeStack.peek();
        if (currentScope.containsKey(symbol.getName())) {
            return false; // dupla declaração
        }
        currentScope.put(symbol.getName(), symbol);
        return true;
    }

    /**
     * Busca um símbolo do topo para a base da pilha (respeita shadowing).
     * Retorna null se não encontrado.
     */
    public Symbol lookup(String name) {
        for (Map<String, Symbol> scope : scopeStack) {
            Symbol s = scope.get(name);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    /**
     * Busca apenas no escopo atual (para detectar dupla declaração).
     */
    public Symbol lookupCurrentScope(String name) {
        if (scopeStack.isEmpty()) {
            return null;
        }
        return scopeStack.peek().get(name);
    }

    // ==================== Classes ====================

    /**
     * Registra uma definição de classe no mapa global.
     * Retorna false se já existe.
     */
    public boolean registerClass(Symbol classSymbol) {
        if (classMap.containsKey(classSymbol.getName())) {
            return false;
        }
        classMap.put(classSymbol.getName(), classSymbol);
        return true;
    }

    /**
     * Busca uma classe pelo nome.
     */
    public Symbol lookupClass(String className) {
        return classMap.get(className);
    }

    /**
     * Verifica se uma classe existe.
     */
    public boolean classExists(String className) {
        return classMap.containsKey(className);
    }

    /**
     * Retorna todas as classes registradas.
     */
    public Collection<Symbol> getAllClasses() {
        return classMap.values();
    }

    // ==================== Herança ====================

    /**
     * Registra relação de herança: filha derives from mãe.
     */
    public void registerInheritance(String childClass, String parentClass) {
        inheritanceMap.put(childClass, parentClass);
    }

    /**
     * Retorna a classe mãe de uma classe, ou null se for Root.
     */
    public String getParentClass(String className) {
        return inheritanceMap.get(className);
    }

    /**
     * Retorna a cadeia de ancestrais de uma classe (da mãe até Root).
     */
    public List<String> getAncestorChain(String className) {
        List<String> chain = new ArrayList<>();
        String current = inheritanceMap.get(className);
        Set<String> visited = new HashSet<>();
        visited.add(className);
        while (current != null && !visited.contains(current)) {
            chain.add(current);
            visited.add(current);
            current = inheritanceMap.get(current);
        }
        return chain;
    }

    /**
     * Verifica se existe herança circular.
     */
    public boolean hasCircularInheritance(String className) {
        Set<String> visited = new HashSet<>();
        String current = className;
        while (current != null) {
            if (visited.contains(current)) {
                return true;
            }
            visited.add(current);
            current = inheritanceMap.get(current);
        }
        return false;
    }

    /**
     * Verifica se childClass é (direta ou indiretamente) filha de parentClass.
     */
    public boolean isSubclassOf(String childClass, String parentClass) {
        if (childClass == null || parentClass == null) return false;
        if (childClass.equals(parentClass)) return true;
        String current = inheritanceMap.get(childClass);
        Set<String> visited = new HashSet<>();
        visited.add(childClass);
        while (current != null && !visited.contains(current)) {
            if (current.equals(parentClass)) return true;
            visited.add(current);
            current = inheritanceMap.get(current);
        }
        return false;
    }

    // ==================== Resolução de Membros ====================

    /**
     * Busca um membro (atributo ou método) na classe e seus ancestrais.
     * Retorna null se não encontrado.
     */
    public Symbol lookupMember(String className, String memberName) {
        String current = className;
        Set<String> visited = new HashSet<>();
        while (current != null && !visited.contains(current)) {
            visited.add(current);
            Symbol classSym = classMap.get(current);
            if (classSym != null) {
                Symbol member = classSym.findMember(memberName);
                if (member != null) {
                    return member;
                }
            }
            current = inheritanceMap.get(current);
        }
        return null;
    }

    // ==================== Contexto ====================

    public String getCurrentClassName() {
        return currentClassName;
    }

    public void setCurrentClassName(String currentClassName) {
        this.currentClassName = currentClassName;
    }

    public String getCurrentMethodName() {
        return currentMethodName;
    }

    public void setCurrentMethodName(String currentMethodName) {
        this.currentMethodName = currentMethodName;
    }

    /**
     * Verifica se dois tipos são compatíveis para atribuição.
     * Considera polimorfismo: filha pode ser atribuída a variável do tipo mãe.
     */
    public boolean isTypeCompatible(Type targetType, String targetClassName,
                                     Type sourceType, String sourceClassName) {
        if (targetType != sourceType) {
            return false;
        }
        if (targetType == Type.CLASS) {
            // Polimorfismo: sourceClass deve ser igual ou filha de targetClass
            return isSubclassOf(sourceClassName, targetClassName);
        }
        return true; // NUMBER == NUMBER, ANSWER == ANSWER, VOID == VOID
    }
}
