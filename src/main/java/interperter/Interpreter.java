package interperter;

import lexer.Token;
import lexer.TokenType;
import meta.Callable;
import meta.FunctionValue;
import meta.Return;
import meta.Entity;
import parser.Parser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Interpreter implements Parser.Expr.Visitor<Object>, Parser.Stmt.Visitor<Void> {

    private final Environment globals = new Environment();
    private Environment environment = globals;

    public Interpreter() {
        // Standard boolean constants
        globals.define("true", Boolean.TRUE);
        globals.define("false", Boolean.FALSE);

        // Built-in print(...)
        globals.define("print", new Callable() {
            @Override
            public int arity() {
                return -1; // any number of args
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arguments.size(); i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(interpreter.stringify(arguments.get(i)));
                }
                System.out.println(sb);
                return null;
            }

            @Override
            public String toString() {
                return "<native print>";
            }
        });

        // Built-in inspect(...)
        globals.define("inspect", new Callable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);  // single argument
                // If it's an Entity, we do a deep (recursive) inspection
                if (arg instanceof meta.Entity entity) {
                    String info = interpreter.inspectEntityRecursive(entity, 0);
                    System.out.println(info);
                    return info;
                } else {
                    // Otherwise, just show basic info
                    StringBuilder sb = new StringBuilder();
                    sb.append("Type: ").append(arg == null ? "null" : arg.getClass().getSimpleName()).append("\n");
                    sb.append(interpreter.stringify(arg));
                    System.out.println(sb);
                    return sb.toString();
                }
            }

            @Override
            public String toString() {
                return "<native inspect>";
            }
        });

        // Built-in len(...)
        globals.define("len", new Callable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object first = arguments.get(0);
                if (!(first instanceof Entity entity)) {
                    System.err.println("Error: len() expects a table/array as argument.");
                    return BigDecimal.ZERO;
                }
                return BigDecimal.valueOf(entity.size());
            }

            @Override
            public String toString() {
                return "<native len>";
            }
        });
    }

    /**
     * Interpret a list of statements. We do not throw general exceptions;
     * we catch them, print them, and keep going.
     */
    public void interpret(List<Parser.Stmt> statements) {
        for (Parser.Stmt statement : statements) {
            try {
                execute(statement);
            } catch (Return ret) {
                // If a return is triggered at top-level, it's invalid:
                System.err.println("Error: 'return' used outside of function.");
            }
        }
    }

    // ----------------------
    // Statement Execution
    // ----------------------

    private void execute(Parser.Stmt stmt) {
        if (stmt == null) return;
        stmt.accept(this);
    }

    @Override
    public Void visitExpressionStmt(Parser.ExpressionStmt stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitBlockStmt(Parser.BlockStmt stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitIfStmt(Parser.IfStmt stmt) {
        Object condVal = evaluate(stmt.condition);
        if (isTruthy(condVal)) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Parser.WhileStmt stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }

    @Override
    public Void visitFunctionStmt(Parser.FunctionStmt stmt) {
        FunctionValue fn = new FunctionValue(stmt, environment);
        environment.define(stmt.name.lexeme, fn);
        return null;
    }

    @Override
    public Void visitReturnStmt(Parser.ReturnStmt stmt) {
        Object value = null;
        if (stmt.value != null) {
            value = evaluate(stmt.value);
        }
        // We throw Return to bubble up to function call
        throw new Return(value);
    }

    @Override
    public Void visitForStmt(Parser.ForStmt stmt) {
        Object iterable = evaluate(stmt.iterable);
        if (!(iterable instanceof Entity arr)) {
            System.err.println("Error: for-loop requires an array, got " + stringify(iterable));
            return null;
        }
        int length = arr.size();
        for (int i = 0; i < length; i++) {
            Object elem = arr.get(BigDecimal.valueOf(i));
            Environment loopEnv = new Environment(environment);
            loopEnv.define(stmt.varName.lexeme, elem);
            executeBlock(List.of(stmt.body), loopEnv);
        }
        return null;
    }

    // ----------------------
    // Expression Evaluation
    // ----------------------

    private Object evaluate(Parser.Expr expr) {
        if (expr == null) return null;
        return expr.accept(this);
    }

    @Override
    public Object visitLiteralExpr(Parser.LiteralExpr expr) {
        return expr.literal;
    }

    @Override
    public Object visitGroupingExpr(Parser.GroupingExpr expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitUnaryExpr(Parser.UnaryExpr expr) {
        Object right = evaluate(expr.right);
        if (expr.operator.type == TokenType.MINUS) {
            if (!(right instanceof BigDecimal)) {
                error("Operand of '-' must be numeric.");
                return null;
            }
            return ((BigDecimal)right).negate();
        } else if (expr.operator.type == TokenType.BANG) {
            return !isTruthy(right);
        }
        return null;
    }

    @Override
    public Object visitBinaryExpr(Parser.BinaryExpr expr) {
        Object left = evaluate(expr.left);

        // short-circuit for &&, ||
        if (expr.operator.type == TokenType.AND) {
            if (!isTruthy(left)) return left; // short-circuit
            return evaluate(expr.right);
        }
        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left; // short-circuit
            return evaluate(expr.right);
        }

        Object right = evaluate(expr.right);
        if (expr.operator.type == TokenType.PLUS) {
            if (left instanceof BigDecimal lNum && right instanceof BigDecimal rNum) {
                return lNum.add(rNum);
            }
            if (left instanceof String || right instanceof String) {
                return stringify(left) + stringify(right);
            }
            if (left instanceof Entity leftArr) {
                Entity combined = new Entity();
                int len = leftArr.size();
                // copy
                for (int i = 0; i < len; i++) {
                    combined.set(BigDecimal.valueOf(i), leftArr.get(BigDecimal.valueOf(i)));
                }
                // if right is array, extend
                if (right instanceof Entity rightArr) {
                    int rlen = rightArr.size();
                    for (int i = 0; i < rlen; i++) {
                        combined.set(BigDecimal.valueOf(len + i), rightArr.get(BigDecimal.valueOf(i)));
                    }
                } else {
                    // append single element
                    combined.set(BigDecimal.valueOf(len), right);
                }
                return combined;
            }
            error("Operands of '+' must be numbers, strings, or arrays.");
            return null;
        }
        if (expr.operator.type == TokenType.MINUS ||
                expr.operator.type == TokenType.STAR ||
                expr.operator.type == TokenType.SLASH ||
                expr.operator.type == TokenType.GREATER ||
                expr.operator.type == TokenType.GREATER_EQUAL ||
                expr.operator.type == TokenType.LESS ||
                expr.operator.type == TokenType.LESS_EQUAL) {

            if (!(left instanceof BigDecimal) || !(right instanceof BigDecimal)) {
                error("Operands must be numeric for arithmetic/comparison.");
                return null;
            }
            BigDecimal l = (BigDecimal) left;
            BigDecimal r = (BigDecimal) right;
            switch (expr.operator.type) {
                case MINUS: return l.subtract(r);
                case STAR:  return l.multiply(r);
                case SLASH:
                    if (r.compareTo(BigDecimal.ZERO) == 0) {
                        error("Division by zero.");
                        return null;
                    }
                    return l.divide(r, 10, BigDecimal.ROUND_HALF_EVEN);
                case GREATER:       return l.compareTo(r) > 0;
                case GREATER_EQUAL: return l.compareTo(r) >= 0;
                case LESS:          return l.compareTo(r) < 0;
                case LESS_EQUAL:    return l.compareTo(r) <= 0;
            }
        }
        if (expr.operator.type == TokenType.EQUAL_EQUAL) {
            return isEqual(left, right);
        }
        if (expr.operator.type == TokenType.BANG_EQUAL) {
            return !isEqual(left, right);
        }
        return null;
    }

    @Override
    public Object visitVariableExpr(Parser.VariableExpr expr) {
        return environment.get(expr.name);
    }

    @Override
    public Object visitAssignExpr(Parser.AssignExpr expr) {
        Object value = evaluate(expr.value);
        // Instead of throwing if not found, environment now prints an error and creates it
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Object visitCallExpr(Parser.CallExpr expr) {
        Object callee = evaluate(expr.callee);
        List<Object> args = new ArrayList<>();
        for (parser.Parser.Expr arg : expr.arguments) {
            args.add(evaluate(arg));
        }
        if (!(callee instanceof Callable fn)) {
            error("Can only call functions. Value: " + stringify(callee));
            return null;
        }
        return fn.call(this, args);
    }

    @Override
    public Object visitFunctionExpr(Parser.FunctionExpr expr) {
        FunctionValue fn = new FunctionValue(expr, environment);
        if (expr.name != null) {
            environment.define(expr.name.lexeme, fn);
        }
        return fn;
    }

    @Override
    public Object visitArrayExpr(Parser.ArrayExpr expr) {
        Entity arr = new Entity();
        for (int i = 0; i < expr.elements.size(); i++) {
            Object val = evaluate(expr.elements.get(i));
            arr.set(BigDecimal.valueOf(i), val);
        }
        return arr;
    }

    @Override
    public Object visitDictExpr(Parser.DictExpr expr) {
        Entity dict = new Entity();
        for (parser.Parser.DictEntry entry : expr.entries) {
            Object key = evaluate(entry.key);
            Object val = evaluate(entry.value);
            dict.set(key, val);
        }
        return dict;
    }

    @Override
    public Object visitGetExpr(Parser.GetExpr expr) {
        Object obj = evaluate(expr.object);
        Object idx = evaluate(expr.index);
        if (!(obj instanceof Entity ent)) {
            error("Only tables/arrays support indexing: got " + stringify(obj));
            return null;
        }
        return ent.get(idx);
    }

    @Override
    public Object visitSetExpr(Parser.SetExpr expr) {
        Object obj = evaluate(expr.object);
        Object idx = evaluate(expr.index);
        Object val = evaluate(expr.value);
        if (!(obj instanceof Entity ent)) {
            error("Only tables/arrays support index assignment: " + stringify(obj));
            return null;
        }
        ent.set(idx, val);
        return val;
    }

    @Override
    public Object visitTernaryExpr(Parser.TernaryExpr expr) {
        Object cond = evaluate(expr.condition);
        return isTruthy(cond) ? evaluate(expr.thenExpr) : evaluate(expr.elseExpr);
    }

    @Override
    public Object visitRangeExpr(Parser.RangeExpr expr) {
        Object startVal = evaluate(expr.start);
        Object endVal   = evaluate(expr.end);
        Object stepVal  = expr.step != null ? evaluate(expr.step) : null;

        BigDecimal startNum = toNumberOrZero(startVal);
        BigDecimal endNum   = toNumberOrZero(endVal);

        BigDecimal stepNum;
        if (stepVal == null) {
            // auto step
            stepNum = (startNum.compareTo(endNum) <= 0)
                    ? BigDecimal.ONE
                    : BigDecimal.ONE.negate();
        } else {
            stepNum = toNumberOrZero(stepVal);
            if (stepNum.compareTo(BigDecimal.ZERO) == 0) {
                error("Range step cannot be zero.");
                return new Entity();
            }
        }
        Entity arr = new Entity();
        int index = 0;
        BigDecimal current = startNum;
        if (stepNum.compareTo(BigDecimal.ZERO) > 0) {
            while (current.compareTo(endNum) <= 0) {
                arr.set(BigDecimal.valueOf(index++), current);
                current = current.add(stepNum);
            }
        } else {
            while (current.compareTo(endNum) >= 0) {
                arr.set(BigDecimal.valueOf(index++), current);
                current = current.add(stepNum);
            }
        }
        return arr;
    }

    @Override
    public Void visitClassStmt(Parser.ClassStmt stmt) {
        // 1) Create a new Entity for this class
        meta.Entity classEntity = new meta.Entity();

        // 2) If there's a parent, try setting metaentity
        if (stmt.parentName != null) {
            Object parent = environment.get(stmt.parentName);
            if (parent instanceof meta.Entity parentEntity) {
                classEntity.setMetaentity(parentEntity);
            } else if (parent != null) {
                System.err.println("Warning: parent '" + stmt.parentName.lexeme
                        + "' is not an Entity. Inheritance ignored.");
            }
        }

        // 3) Create an environment that stores/reads fields from classEntity
        //    but falls back on the current 'environment' for outer lookups
        Environment classEnv = new Environment(classEntity, environment);

        // 4) Execute each statement in the class body using that environment
        //    i.e. "a=1", "def sum(...)", etc., which become fields in classEntity.
        for (Parser.Stmt s : stmt.body) {
            // Temporarily switch to classEnv
            Environment old = this.environment;
            try {
                this.environment = classEnv;
                execute(s);
            } finally {
                this.environment = old;
            }
        }

        // 5) Finally, define the class name in the original environment
        environment.define(stmt.name.lexeme, classEntity);
        return null;
    }

    @Override
    public Object visitDotExpr(Parser.DotExpr expr) {
        // Evaluate the object first
        Object objectVal = evaluate(expr.object);

        if (!(objectVal instanceof meta.Entity entity)) {
            error("Only tables or class entities support '.' property access. Got " + stringify(objectVal));
            return null;
        }

        // Look up the property by expr.name.lexeme
        // If it's a function, we often want a "bound method" or just return the function entity
        Object field = entity.get(expr.name.lexeme);
        return field;
    }



    // ----------------------
    // Helpers
    // ----------------------

    public void executeBlock(List<Parser.Stmt> statements, Environment newEnv) {
        Environment oldEnv = this.environment;
        try {
            this.environment = newEnv;
            for (Parser.Stmt st : statements) {
                execute(st);
            }
        } finally {
            this.environment = oldEnv;
        }
    }

    private boolean isTruthy(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;
        return a.equals(b);
    }

    private BigDecimal toNumberOrZero(Object obj) {
        if (obj instanceof BigDecimal bd) {
            return bd;
        }
        if (obj == null) {
            error("Expected numeric range boundary, got null.");
        } else {
            error("Expected numeric range boundary, got " + obj);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Convert MicroPy value to string for printing.
     */
    public String stringify(Object object) {
        if (object == null) return "None";
        if (object instanceof BigDecimal bd) {
            String s = bd.toPlainString();
            // Drop trailing .0
            if (s.endsWith(".0")) {
                return s.substring(0, s.length()-2);
            }
            return s;
        }
        if (object instanceof String) return (String) object;
        return object.toString();
    }

    private void error(String msg) {
        System.err.println("Runtime Error: " + msg);
    }

    /**
     * Recursively inspects an Entity and its parent chain, indenting each level.
     */
    private String inspectEntityRecursive(meta.Entity entity, int depth) {
        String indent = "  ".repeat(depth);
        StringBuilder sb = new StringBuilder();

        // If it's a FunctionValue, print function info
        if (entity instanceof meta.FunctionValue func) {
            sb.append(indent).append("<FunctionValue>\n");
            if (func.getName() != null) {
                sb.append(indent).append("  Name: ").append(func.getName().lexeme).append("\n");
            } else {
                sb.append(indent).append("  Anonymous Function\n");
            }
            sb.append(indent).append("  Params: ");
            for (lexer.Token p : func.getParams()) {
                sb.append(p.lexeme).append(" ");
            }
            sb.append("\n").append(indent).append("  Body: ").append(func.getBody().toString()).append("\n");
        } else {
            // Generic entity
            sb.append(indent).append("<Entity>\n");
        }

        // Print all local entries
        sb.append(indent).append("Entries:\n");
        for (Map.Entry<Object, Object> e : entity.entries.entrySet()) {
            Object key = e.getKey();
            Object val = e.getValue();
            sb.append(indent).append("  ")
                    .append(stringify(key)).append(" : ")
                    .append(stringify(val)).append("\n");
        }

        // If there's a parent, recurse
        if (entity.getMetaentity() != null) {
            sb.append(indent).append("Parent =>\n");
            sb.append(inspectEntityRecursive(entity.getMetaentity(), depth + 1));
        }

        return sb.toString();
    }

}
