package semantico;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsula todas as informações de um identificador declarado na linguagem D++.
 */
public class Symbol {

    /**
     * Categorias de símbolos na linguagem D++.
     */
    public enum SymbolKind {
        VARIABLE,    // alterable tipo id << exp
        CONSTANT,    // unalterable tipo id << exp
        OBJECT,      // object ClassName id
        PROCEDURE,   // procedure id [params] bloco_cmd
        FUNCTION,    // function tipo id [params] bloco_exp
        CLASS,       // family ClassName start ... finish
        PARAMETER    // parâmetro de método
    }

    private String name;
    private Type type;
    private String className;       // nome da classe quando type == CLASS
    private SymbolKind kind;
    private boolean mutable;        // true = alterable, false = unalterable
    private List<Symbol> parameters; // lista de parâmetros (para métodos)
    private Type returnType;        // tipo de retorno (para function)
    private String returnClassName; // nome da classe de retorno (quando CLASS)
    private String parentClassName; // classe mãe (herança); default = "Root"
    private boolean entryPoint;     // true se marcado com >>
    private boolean isAbstract;     // true se o método não tem corpo
    private boolean hasAbstractMethods; // true se a classe tem métodos abstratos não implementados
    private int line;
    private int col;

    // Membros da classe (atributos + métodos) — usado apenas para kind == CLASS
    private List<Symbol> members;

    public Symbol() {
        this.parameters = new ArrayList<>();
        this.members = new ArrayList<>();
        this.mutable = true;
        this.parentClassName = "Root";
    }

    /**
     * Construtor de conveniência para símbolos simples.
     */
    public Symbol(String name, Type type, SymbolKind kind) {
        this();
        this.name = name;
        this.type = type;
        this.kind = kind;
    }

    // --- Getters e Setters ---

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public SymbolKind getKind() {
        return kind;
    }

    public void setKind(SymbolKind kind) {
        this.kind = kind;
    }

    public boolean isMutable() {
        return mutable;
    }

    public void setMutable(boolean mutable) {
        this.mutable = mutable;
    }

    public List<Symbol> getParameters() {
        return parameters;
    }

    public void setParameters(List<Symbol> parameters) {
        this.parameters = parameters;
    }

    public Type getReturnType() {
        return returnType;
    }

    public void setReturnType(Type returnType) {
        this.returnType = returnType;
    }

    public String getReturnClassName() {
        return returnClassName;
    }

    public void setReturnClassName(String returnClassName) {
        this.returnClassName = returnClassName;
    }

    public String getParentClassName() {
        return parentClassName;
    }

    public void setParentClassName(String parentClassName) {
        this.parentClassName = parentClassName;
    }

    public boolean isEntryPoint() {
        return entryPoint;
    }

    public void setEntryPoint(boolean entryPoint) {
        this.entryPoint = entryPoint;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public void setAbstract(boolean isAbstract) {
        this.isAbstract = isAbstract;
    }

    public boolean hasAbstractMethods() {
        return hasAbstractMethods;
    }

    public void setHasAbstractMethods(boolean hasAbstractMethods) {
        this.hasAbstractMethods = hasAbstractMethods;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public int getCol() {
        return col;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public List<Symbol> getMembers() {
        return members;
    }

    public void setMembers(List<Symbol> members) {
        this.members = members;
    }

    /**
     * Procura um membro pelo nome na lista de membros desta classe.
     */
    public Symbol findMember(String memberName) {
        for (Symbol m : members) {
            if (m.getName().equals(memberName)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Verifica se é um método (procedure ou function).
     */
    public boolean isMethod() {
        return kind == SymbolKind.PROCEDURE || kind == SymbolKind.FUNCTION;
    }

    /**
     * Verifica se é um atributo (variable, constant, ou object).
     */
    public boolean isAttribute() {
        return kind == SymbolKind.VARIABLE || kind == SymbolKind.CONSTANT || kind == SymbolKind.OBJECT;
    }

    @Override
    public String toString() {
        return "Symbol{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", kind=" + kind +
                (className != null ? ", className='" + className + "'" : "") +
                '}';
    }
}
