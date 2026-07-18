package semantico;

import dplusplus.node.*;

/**
 * Representa os tipos do sistema de tipos D++.
 */
public enum Type {
    NUMBER,   // tipo primitivo number (inteiros e reais)
    ANSWER,   // tipo primitivo answer (yes / no)
    VOID,     // para procedures (sem retorno)
    CLASS;    // tipo de classe — nome da classe armazenado no Symbol

    /**
     * Converte um nó AST PTipoPrimitivo para o enum Type correspondente.
     */
    public static Type fromTipoPrimitivo(PTipoPrimitivo node) {
        if (node instanceof ANumberTipoPrimitivo) {
            return NUMBER;
        } else if (node instanceof AAnswerTipoPrimitivo) {
            return ANSWER;
        }
        throw new RuntimeException("Tipo primitivo desconhecido: " + node.getClass().getSimpleName());
    }

    /**
     * Converte um nó AST PTipo para o enum Type correspondente.
     * Retorna CLASS se o tipo é uma classe (o nome da classe deve ser extraído separadamente).
     */
    public static Type fromTipo(PTipo node) {
        if (node instanceof APrimitivoTipo) {
            return fromTipoPrimitivo(((APrimitivoTipo) node).getTipoPrimitivo());
        } else if (node instanceof AClasseTipo) {
            return CLASS;
        }
        throw new RuntimeException("Tipo desconhecido: " + node.getClass().getSimpleName());
    }

    /**
     * Extrai o nome da classe de um nó PTipo, ou null se for primitivo.
     */
    public static String classNameFromTipo(PTipo node) {
        if (node instanceof AClasseTipo) {
            return ((AClasseTipo) node).getIdClasse().getText().trim();
        }
        return null;
    }
}
