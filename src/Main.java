import java.io.FileReader;
import java.io.IOException;
import java.io.PushbackReader;

import dplusplus.lexer.Lexer;
import dplusplus.lexer.LexerException;
import dplusplus.node.EOF;
import dplusplus.node.Token;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Forneça o caminho do arquivo .dpp como argumento, ou altere o código para um caminho fixo.");
            // Caminho fixo para facilitar o teste no Eclipse:
            args = new String[] { "atividade3.dpp" }; 
        }

        try {
            // Inicializa o leitor de arquivo e o Lexer do SableCC
            PushbackReader leitor = new PushbackReader(new FileReader(args[0]), 1024);
            Lexer lexer = new Lexer(leitor);

            Token token;
            // Itera solicitando o próximo token até encontrar o fim do arquivo (EOF)
            while (!((token = lexer.next()) instanceof EOF)) {
                
                // Opcional: Você pode querer ignorar a impressão de espaços em branco
                // if (token instanceof dplusplus.node.TEspacoVazio) continue;
                
                String nomeToken = token.getClass().getSimpleName();
                String lexema = token.getText();
                
                System.out.println(nomeToken + " : [" + lexema + "]");
            }
            System.out.println("Análise Léxica concluída com sucesso!");

        } catch (LexerException e) {
            // Captura e imprime erros léxicos (caracteres não reconhecidos na gramática)
            System.err.println("ERRO LÉXICO: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("ERRO DE I/O: " + e.getMessage());
        }
    }
}