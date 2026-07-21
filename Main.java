import java.io.FileReader;
import java.io.IOException;
import java.io.PushbackReader;

import dplusplus.analysis.DepthFirstAdapter;
import dplusplus.lexer.Lexer;
import dplusplus.lexer.LexerException;
import dplusplus.node.*;
import dplusplus.parser.Parser;
import dplusplus.parser.ParserException;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Forneca o caminho do arquivo .dpp como argumento.");
            System.out.println("Usando teste.dpp como arquivo padrao.");
            args = new String[] { "teste.dpp" };
        }

        try (PushbackReader leitor = new PushbackReader(new FileReader(args[0]), 1024)) {
            Lexer lexer = new Lexer(leitor);
            Parser parser = new Parser(lexer);
            Start ast = parser.parse();

            System.out.println("=== Analise Sintatica Abstrata concluida com sucesso! ===");
            System.out.println();
            System.out.println("--- Arvore Sintatica Abstrata ---");
            ast.apply(new TreePrinter());

        } catch (ParserException e) {
            Token token = e.getToken();
            System.err.println("ERRO SINTATICO na linha " + token.getLine()
                    + ", coluna " + token.getPos() + ": " + e.getMessage());
        } catch (LexerException e) {
            System.err.println("ERRO LEXICO: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("ERRO DE I/O: " + e.getMessage());
        }
    }

    private static class TreePrinter extends DepthFirstAdapter {
        private int nivel = 0;

        @Override
        public void defaultIn(Node node) {
            print(node.getClass().getSimpleName());
            nivel++;
        }

        @Override
        public void defaultOut(Node node) {
            nivel--;
        }

        @Override
        public void defaultCase(Node node) {
            if (node instanceof EOF) return;
            if (node instanceof Token) {
                Token token = (Token) node;
                print(token.getClass().getSimpleName() + " : \"" + token.getText().trim() + "\"");
                return;
            }
            print(node.getClass().getSimpleName());
        }

        private void print(String texto) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < nivel; i++) sb.append("  ");
            sb.append(texto);
            System.out.println(sb.toString());
        }
    }
}
