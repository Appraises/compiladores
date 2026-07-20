package semantico;

import dplusplus.node.*;

// Representa os tipos do sistema de tipos D++
public enum Type {
    NUMBER,   // tipo primitivo number (inteiros e reais)
    ANSWER,   // tipo primitivo answer (yes / no)
    VOID,     // para procedures (sem retorno)
    CLASS;    // tipo de classe — nome da classe armazenado no Symbol

    public static Type fromTipoPrimitivo(PTipoPrimitivo node) {
        if (node instanceof ANumberTipoPrimitivo) {
            return NUMBER;
        } else if (node instanceof AAnswerTipoPrimitivo) {
            return ANSWER;
        }
        throw new RuntimeException("Tipo primitivo desconhecido: " + node.getClass().getSimpleName());
    }

    public static Type fromTipo(PTipo node) {
        if (node instanceof APrimitivoTipo) {
            return fromTipoPrimitivo(((APrimitivoTipo) node).getTipoPrimitivo());
        } else if (node instanceof AClasseTipo) {
            return CLASS;
        }
        throw new RuntimeException("Tipo desconhecido: " + node.getClass().getSimpleName());
    }

    public static String classNameFromTipo(PTipo node) {
        if (node instanceof AClasseTipo) {
            return ((AClasseTipo) node).getIdClasse().getText().trim();
        }
        return null;
    }

    public static String describe(Type type, String className) {
        if (type == null) {
            return "?";
        }
        if (type == CLASS) {
            return className != null ? className : "classe";
        }
        return type.name();
    }
}
