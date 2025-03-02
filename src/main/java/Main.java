import interperter.Interpreter;
import lexer.Lexer;
import lexer.Token;
import parser.Parser;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        System.setOut(new PrintStream(System.out, true, "UTF-8"));

        String source = Files.readString(Paths.get(args[0]));

        // 词法分析：将源代码转换为 Token 列表
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        // 解析：将 Token 列表转换为 AST
        Parser parser = new Parser(tokens);
        List<Parser.Stmt> statements = parser.parse();

        // 解释执行：运行 AST 生成的程序
        Interpreter interpreter = new Interpreter();
        interpreter.interpret(statements);
    }
}
