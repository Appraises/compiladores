# Compilador D++

Compilador para a linguagem **D++**, uma linguagem orientada a objetos educacional com herança simples, tipos primitivos e controle de fluxo. Implementado em Java utilizando o framework [SableCC](https://sablecc.org/) para geração automática do analisador léxico, sintático e da AST tipada.

O compilador cobre três fases de análise:

1. **Análise Léxica** — tokenização do código-fonte (gerada pelo SableCC)
2. **Análise Sintática** — parsing LALR(1) e construção da AST (gerada pelo SableCC)
3. **Análise Semântica** — tabela de símbolos, verificação de tipos e regras da linguagem (implementada manualmente)

## Estrutura do Projeto

```
compiladores/
├── grupo_3.sable              ← Gramática SableCC (especificação da linguagem D++)
├── sablecc.jar                ← Ferramenta SableCC
├── src/
│   ├── dplusplus/             ← Código gerado pelo SableCC (lexer, parser, AST, visitors)
│   ├── semantico/             ← Análise semântica (tabela de símbolos + verificação de tipos)
│   └── Main.java              ← Ponto de entrada do compilador
├── testes_semantico/          ← Casos de teste da análise semântica
├── atividade1.dpp             ← Programa de exemplo (válido)
├── atividade2.dpp             ← Programa de exemplo (sem ponto de entrada)
├── atividade3.dpp             ← Programa de exemplo (erro sintático)
└── teste.dpp                  ← Programa de exemplo (válido)
```

## A Linguagem D++

D++ é uma linguagem orientada a objetos com a seguinte sintaxe:

```
-- Classes são definidas com 'family'
family Produto start
    unalterable number codigo << 182279.
    alterable number preco << 0.

    procedure definir_preco [number p]
    start
        preco << p.
    finish

    function number obter_preco []
    start
        preco
    finish .
finish

family Loja start
    object Periphericals io.
    object Produto item.

    >> procedure principal []
    start
        alterable number valor << io->capture[].
        item->definir_preco[valor].
        io->show[item->obter_preco[]].
    finish
finish
```

**Características da linguagem:**
- Tipos primitivos: `number` (inteiros e reais) e `answer` (`yes` / `no`)
- Mutabilidade explícita: `alterable` e `unalterable`
- Herança simples: `family Filho derives from Pai`
- Métodos abstratos: declarações sem corpo (ex: `procedure falar [].`)
- Ponto de entrada: exatamente um `>> procedure` por programa
- I/O via classe pré-definida `Periphericals` (`show[]` e `capture[]`)
- Acesso a membros via `->` (ex: `objeto->metodo[args]`)

## Pré-requisitos

- **Java JDK 8+** (testado com JDK 21)
- O `sablecc.jar` já está incluso no repositório

## Compilação

```bash
cd /home/zemarcos/Code/compiladores

mkdir -p bin
javac -cp sablecc.jar -sourcepath src -d bin $(find src -name "*.java")
cp src/dplusplus/lexer/lexer.dat bin/dplusplus/lexer/
cp src/dplusplus/parser/parser.dat bin/dplusplus/parser/
```

Alternativamente, basta abrir o projeto no **Eclipse** — os arquivos `.classpath` e `.project` já estão configurados.

## Execução

```bash
java -cp bin:sablecc.jar Main <arquivo.dpp>
```

Se nenhum arquivo for fornecido, o compilador usa `teste.dpp` como padrão.

### Exemplos

```bash
# Programa válido — análise semântica passa
java -cp bin:sablecc.jar Main atividade1.dpp

# Programa sem ponto de entrada (>>) — erro semântico
java -cp bin:sablecc.jar Main atividade2.dpp

# Programa com erro de sintaxe
java -cp bin:sablecc.jar Main atividade3.dpp
```

## Testes Semânticos

O diretório `testes_semantico/` contém casos de teste para validar a análise semântica:

| Arquivo | Cenário |
|---|---|
| `err_tipos.dpp` | Incompatibilidade de tipos em atribuição e condição |
| `err_unalterable.dpp` | Reatribuição de constante `unalterable` |
| `err_escopo.dpp` | Uso de identificador fora de escopo ou não declarado |
| `err_chamada.dpp` | Aridade errada, tipo errado de argumento, acesso a atributo privado |
| `err_heranca.dpp` | Herança múltipla e classe mãe inexistente |
| `err_ponto_entrada.dpp` | Múltiplos pontos de entrada `>>` |
| `err_classe_abstrata.dpp` | Instanciação de classe abstrata |
| `ok_heranca_polimorfismo.dpp` | Herança, polimorfismo e métodos herdados (deve passar sem erros) |

Para executar todos:

```bash
for f in testes_semantico/*.dpp; do
    echo "=== $(basename $f) ==="
    java -cp bin:sablecc.jar Main "$f" 2>&1 | tail -5
    echo
done
```

## Regenerar Código SableCC

Se a gramática `grupo_3.sable` for alterada:

```bash
java -jar sablecc.jar grupo_3.sable
rm -rf src/dplusplus
mv dplusplus src/dplusplus
```

Em seguida, recompile o projeto normalmente.
