package meta;

import interperter.Environment;
import interperter.Interpreter;
import parser.Parser;

import java.util.List;

public class FunctionValue extends Entity implements Callable {
    private final List<Parser.Stmt> body;
    private final List<lexer.Token> params;
    private final lexer.Token name;
    private final Environment closure;

    public FunctionValue(Parser.FunctionExpr declaration, Environment closure) {
        this.body = declaration.body;
        this.params = declaration.params;
        this.name = declaration.name;
        this.closure = closure;
    }

    public FunctionValue(Parser.FunctionStmt declaration, Environment closure) {
        this.body = declaration.body;
        this.params = declaration.params;
        this.name = declaration.name;
        this.closure = closure;
    }

    public List<Parser.Stmt> getBody() {
        return body;
    }

    public List<lexer.Token> getParams() {
        return params;
    }

    public lexer.Token getName() {
        return name;
    }

    @Override
    public int arity() {
        return params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        // We won’t throw an error, we just check and print if mismatch
        if (arity() >= 0 && arguments.size() != arity()) {
            System.err.println("Function expected " + arity() + " args, got " + arguments.size());
            return null;
        }
        Environment fnEnv = new Environment(closure);
        for (int i = 0; i < params.size(); i++) {
            fnEnv.define(params.get(i).lexeme, arguments.get(i));
        }
        try {
            interpreter.executeBlock(body, fnEnv);
        } catch (meta.Return ret) {
            return ret.value;
        }
        return null;
    }

    @Override
    public String toString() {
        if (name != null) {
            return "<fn " + name.lexeme + ">";
        }
        return "<fn anno>";
    }
}
