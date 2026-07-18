package semantico;

/**
 * Classe simples para acumular erros semânticos com posição no código-fonte.
 */
public class SemanticError {
    private final String message;
    private final int line;
    private final int col;

    public SemanticError(String message, int line, int col) {
        this.message = message;
        this.line = line;
        this.col = col;
    }

    public SemanticError(String message) {
        this(message, 0, 0);
    }

    public String getMessage() {
        return message;
    }

    public int getLine() {
        return line;
    }

    public int getCol() {
        return col;
    }

    @Override
    public String toString() {
        if (line > 0) {
            return "ERRO SEMANTICO na linha " + line + ", coluna " + col + ": " + message;
        }
        return "ERRO SEMANTICO: " + message;
    }
}
