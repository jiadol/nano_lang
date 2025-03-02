package parser;

import lexer.Token;
import lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for MicroPy with C-style syntax.
 */
public class Parser {
    private final List<Token> tokens;
    private int current = 0;
    private boolean hadError = false;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            Stmt s = declaration();
            if (s != null) {
                statements.add(s);
            }
        }
        return statements;
    }

    private Stmt declaration() {
        // Check if we have pattern: IDENTIFIER ":" ...
        if (checkClassDefinition()) {
            return classDefinition();
        }
        return statement();
    }

    // -----------
    private boolean checkClassDefinition() {
        // We only parse a class if the user wrote:
        // IDENTIFIER : [IDENTIFIER]? = {
        int save = current;
        if (!match(TokenType.IDENTIFIER)) {
            current = save;
            return false;
        }
        // Must see colon next
        if (!match(TokenType.COLON)) {
            // not a class
            current = save;
            return false;
        }
        // Optionally an IDENTIFIER for parent
        if (check(TokenType.IDENTIFIER)) {
            advance(); // consume parent name
        }
        // Must see '='
        if (!match(TokenType.EQUAL)) {
            current = save;
            return false;
        }
        // Must see '{'
        if (!check(TokenType.LEFT_BRACE)) {
            current = save;
            return false;
        }
        // We restore the parser to where we started
        current = save;
        return true;
    }

    // -----------
    private Stmt classDefinition() {
        // We already know the pattern matches
        Token className = consume(TokenType.IDENTIFIER, "Expect class name.");
        consume(TokenType.COLON, "Expect ':' after class name.");

        Token parentName = null;
        // If next is an IDENTIFIER, that's the parent
        if (check(TokenType.IDENTIFIER)) {
            parentName = advance();
        }

        consume(TokenType.EQUAL, "Expect '=' after class name.");
        consume(TokenType.LEFT_BRACE, "Expect '{' after '='.");

        List<Stmt> body = new ArrayList<>();
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            body.add(declaration()); // or statement()
        }
        consume(TokenType.RIGHT_BRACE, "Expect '}' after class body.");

        return new ClassStmt(className, parentName, body);
    }



    private Stmt functionStatement() {
        Token name = consume(TokenType.IDENTIFIER, "Expect function name.");
        consume(TokenType.LEFT_PAREN, "Expect '(' after function name.");
        List<Token> params = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                params.add(consume(TokenType.IDENTIFIER, "Expect parameter name."));
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.");
        consume(TokenType.LEFT_BRACE, "Expect '{' after function signature.");
        List<Stmt> body = block();
        return new FunctionStmt(name, params, body);
    }

    private Stmt statement() {
        if (match(TokenType.IF)) return ifStatement();
        if (match(TokenType.WHILE)) return whileStatement();
        if (match(TokenType.FOR)) return forStatement();
        if (match(TokenType.LEFT_BRACE)) return new BlockStmt(block());
        if (match(TokenType.RETURN)) return returnStatement();
        return expressionStatement();
    }


    private Stmt ifStatement() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.");
        consume(TokenType.LEFT_BRACE, "Expect '{' after if(...)'.");
        List<Stmt> thenBlock = block();
        Stmt thenStmt = new BlockStmt(thenBlock);

        Stmt elseStmt = null;
        if (match(TokenType.ELSE)) {
            consume(TokenType.LEFT_BRACE, "Expect '{' after 'else'.");
            elseStmt = new BlockStmt(block());
        }
        return new IfStmt(condition, thenStmt, elseStmt);
    }


    private Stmt whileStatement() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after while condition.");
        consume(TokenType.LEFT_BRACE, "Expect '{' after 'while(...)'.");
        List<Stmt> body = block();
        return new WhileStmt(condition, new BlockStmt(body));
    }

    private Stmt forStatement() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.");
        Token varName = consume(TokenType.IDENTIFIER, "Expect loop variable name.");
        consume(TokenType.IN, "Expect 'in' after loop variable.");
        Expr iterable = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after iterable expr.");
        consume(TokenType.LEFT_BRACE, "Expect '{' after for(...)'.");
        List<Stmt> bodyStmts = block();
        return new ForStmt(varName, iterable, new BlockStmt(bodyStmts));
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(TokenType.RIGHT_BRACE) && !check(TokenType.EOF)) {
            value = expression();
        }
        return new ReturnStmt(keyword, value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        return new ExpressionStmt(expr);
    }

    private List<Stmt> block() {
        List<Stmt> stmts = new ArrayList<>();
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            stmts.add(declaration());
        }
        consume(TokenType.RIGHT_BRACE, "Expect '}' after block.");
        return stmts;
    }

    // ---------- Expression parsing ----------

    private Expr expression() {
        return ternary();
    }

    private Expr ternary() {
        Expr expr = assignment();
        if (match(TokenType.QUESTION)) {
            Expr thenExpr = expression();
            consume(TokenType.COLON, "Expect ':' in ternary operator.");
            Expr elseExpr = ternary();
            return new TernaryExpr(expr, thenExpr, elseExpr);
        }
        return expr;
    }

    private Expr assignment() {
        Expr expr = orExpr();
        if (match(TokenType.EQUAL)) {
            Token equals = previous();
            Expr value = assignment();
            if (expr instanceof VariableExpr varExpr) {
                return new AssignExpr(varExpr.name, value);
            } else if (expr instanceof GetExpr getExpr) {
                return new SetExpr(getExpr.object, getExpr.index, equals, value);
            } else {
                error(equals, "Invalid assignment target.");
            }
        }
        return expr;
    }

    private Expr orExpr() {
        Expr expr = andExpr();
        while (match(TokenType.OR)) {
            Token op = previous();
            Expr right = andExpr();
            expr = new BinaryExpr(expr, op, right);
        }
        return expr;
    }

    private Expr andExpr() {
        Expr expr = equality();
        while (match(TokenType.AND)) {
            Token op = previous();
            Expr right = equality();
            expr = new BinaryExpr(expr, op, right);
        }
        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();
        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            Token op = previous();
            Expr right = comparison();
            expr = new BinaryExpr(expr, op, right);
        }
        return expr;
    }

    private Expr comparison() {
        Expr expr = term();
        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL,
                TokenType.LESS, TokenType.LESS_EQUAL)) {
            Token op = previous();
            Expr right = term();
            expr = new BinaryExpr(expr, op, right);
        }
        return expr;
    }

    private Expr term() {
        Expr expr = factor();
        while (match(TokenType.MINUS, TokenType.PLUS)) {
            Token op = previous();
            Expr right = factor();
            expr = new BinaryExpr(expr, op, right);
        }
        return expr;
    }

    private Expr factor() {
        Expr expr = unary();
        while (match(TokenType.SLASH, TokenType.STAR)) {
            Token op = previous();
            Expr right = unary();
            expr = new BinaryExpr(expr, op, right);
        }
        return expr;
    }

    private Expr unary() {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            Token op = previous();
            Expr right = unary();
            return new UnaryExpr(op, right);
        }
        return call();
    }

    private Expr call() {
        Expr expr = subscript();
        while (true) {
            if (match(TokenType.LEFT_PAREN)) {
                // function call
                expr = finishCall(expr);
            } else if (match(TokenType.DOT)) {
                // property/method access: expr.name
                Token name = consume(TokenType.IDENTIFIER, "Expect property name after '.'.");
                expr = new DotExpr(expr, name);
            } else {
                break;
            }
        }
        return expr;
    }



    private Expr finishCall(Expr callee) {
        List<Expr> args = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                args.add(expression());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.");
        return new CallExpr(callee, previous(), args);
    }

    private Expr subscript() {
        Expr expr = primary();
        while (match(TokenType.LEFT_BRACKET)) {
            Expr idx = expression();
            consume(TokenType.RIGHT_BRACKET, "Expect ']' after index.");
            expr = new GetExpr(expr, idx);
        }
        return expr;
    }

    private Expr primary() {
        if (match(TokenType.FALSE)) return new LiteralExpr(false);
        if (match(TokenType.TRUE)) return new LiteralExpr(true);
        if (match(TokenType.NONE)) return new LiteralExpr(null);
        if (match(TokenType.NUMBER, TokenType.STRING)) {
            return new LiteralExpr(previous().literal);
        }
        // def used for inline function expression?
        if (match(TokenType.DEF)) {
            return functionExpression();
        }
        // arrow-lambda check
        if (isLambda()) {
            return lambdaExpression();
        }
        if (match(TokenType.IDENTIFIER)) {
            return new VariableExpr(previous());
        }
        // check for array or range
        if (match(TokenType.LEFT_BRACKET)) {
            return arrayOrRangeLiteral();
        }
        if (match(TokenType.LEFT_BRACE)) {
            return dictLiteral();
        }
        if (match(TokenType.LEFT_PAREN)) {
            Expr e = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after group.");
            return new GroupingExpr(e);
        }
        error(peek(), "Expect expression.");
        return new LiteralExpr(null);
    }

    // Range syntax: [start :: end :: step], else normal array
    private Expr arrayOrRangeLiteral() {
        if (check(TokenType.RIGHT_BRACKET)) {
            // empty array
            advance();
            return new ArrayExpr(new ArrayList<>());
        }
        // parse first expression (start or first element)
        Expr first = expression();
        if (match(TokenType.COLON_COLON)) {
            // parse range: [start::end [::step]]
            Expr second = expression(); // end
            Expr third = null;
            if (match(TokenType.COLON_COLON)) {
                third = expression(); // step
            }
            consume(TokenType.RIGHT_BRACKET, "Expect ']' after range expression.");
            return new RangeExpr(first, second, third);
        }
        // otherwise, it's a normal array
        List<Expr> elements = new ArrayList<>();
        elements.add(first);
        while (match(TokenType.COMMA)) {
            if (check(TokenType.RIGHT_BRACKET)) break;
            elements.add(expression());
        }
        consume(TokenType.RIGHT_BRACKET, "Expect ']' after array literal.");
        return new ArrayExpr(elements);
    }

    private Expr dictLiteral() {
        List<DictEntry> entries = new ArrayList<>();
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            // parse key
            Expr key = parseDictKey();
            consume(TokenType.COLON, "Expect ':' after dict key.");
            // parse value
            Expr value = expression();
            entries.add(new DictEntry(key, value));

            if (!match(TokenType.COMMA)) {
                break;
            }
        }
        consume(TokenType.RIGHT_BRACE, "Expect '}' after dict literal.");
        return new DictExpr(entries);
    }

    // If you want unquoted keys like name: "Alice"
    private Expr parseDictKey() {
        if (match(TokenType.IDENTIFIER)) {
            // convert that identifier to a string literal
            Token ident = previous();
            return new LiteralExpr(ident.lexeme);
        }
        // else fallback: parse a normal expression, or string literal, etc.
        return expression();
    }


    private Expr functionExpression() {
        // if you want inline def ...
        Token name = consume(TokenType.IDENTIFIER, "Expect function name.");
        consume(TokenType.LEFT_PAREN, "Expect '(' after function name.");
        List<Token> params = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                params.add(consume(TokenType.IDENTIFIER, "Expect parameter name."));
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after params.");
        consume(TokenType.LEFT_BRACE, "Expect '{' for function body.");
        List<Stmt> body = block();
        return new FunctionExpr(name, params, body);
    }

    private boolean isLambda() {
        // e.g. x -> expr or (x,y)-> expr
        if (check(TokenType.IDENTIFIER) && peekNext().type == TokenType.ARROW) {
            return true;
        }
        if (check(TokenType.LEFT_PAREN)) {
            int save = current;
            advance(); // '('
            boolean valid = true;
            if (!check(TokenType.RIGHT_PAREN)) {
                do {
                    if (!check(TokenType.IDENTIFIER)) {
                        valid = false;
                        break;
                    }
                    advance();
                } while (match(TokenType.COMMA));
            }
            if (valid && check(TokenType.RIGHT_PAREN)) {
                advance(); // ')'
                boolean isArrow = check(TokenType.ARROW);
                current = save;
                return isArrow;
            }
            current = save;
        }
        return false;
    }

    private Expr lambdaExpression() {
        List<Token> params = new ArrayList<>();
        // single param: x -> ...
        if (check(TokenType.IDENTIFIER) && peekNext().type == TokenType.ARROW) {
            params.add(advance());
        } else if (match(TokenType.LEFT_PAREN)) {
            if (!check(TokenType.RIGHT_PAREN)) {
                do {
                    params.add(consume(TokenType.IDENTIFIER, "Expect parameter name in lambda."));
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RIGHT_PAREN, "Expect ')' after lambda params.");
        }
        consume(TokenType.ARROW, "Expect '->' in lambda.");
        Expr bodyExpr = expression();
        List<Stmt> body = new ArrayList<>();
        // we wrap it in a ReturnStmt
        body.add(new ReturnStmt(previous(), bodyExpr));
        return new FunctionExpr(null, params, body);
    }

    // ---------- Utility ----------

    private boolean match(TokenType... types) {
        for (TokenType t : types) {
            if (check(t)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        error(peek(), message);
        return new Token(type, "", null, peek().line); // dummy token
    }

    private void error(Token token, String message) {
        System.err.println("[Line " + token.line + "] Error at " +
                (token.type == TokenType.EOF ? "end" : "'" + token.lexeme + "'") +
                ": " + message);
        hadError = true;
        synchronize();
    }

    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) return;
            // some keywords that might indicate a new statement
            switch (peek().type) {
                case DEF:
                case IF:
                case FOR:
                case WHILE:
                case RETURN:
                    return;
            }
            advance();
        }
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token peekNext() {
        if (current + 1 >= tokens.size()) return tokens.get(tokens.size() - 1);
        return tokens.get(current + 1);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    // ---------- AST Classes ----------

    public static abstract class Expr {
        public interface Visitor<R> {
            R visitBinaryExpr(BinaryExpr expr);
            R visitGroupingExpr(GroupingExpr expr);
            R visitLiteralExpr(LiteralExpr expr);
            R visitUnaryExpr(UnaryExpr expr);
            R visitVariableExpr(VariableExpr expr);
            R visitAssignExpr(AssignExpr expr);
            R visitCallExpr(CallExpr expr);
            R visitFunctionExpr(FunctionExpr expr);
            R visitArrayExpr(ArrayExpr expr);
            R visitDictExpr(DictExpr expr);
            R visitGetExpr(GetExpr expr);
            R visitSetExpr(SetExpr expr);
            R visitTernaryExpr(TernaryExpr expr);
            R visitRangeExpr(RangeExpr expr);
            R visitClassStmt(ClassStmt stmt);
            R visitDotExpr(DotExpr expr);
        }
        public abstract <R> R accept(Visitor<R> visitor);
    }

    public static class BinaryExpr extends Expr {
        public final Expr left;
        public final Token operator;
        public final Expr right;
        public BinaryExpr(Expr left, Token operator, Expr right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBinaryExpr(this);
        }
    }

    public static class GroupingExpr extends Expr {
        public final Expr expression;
        public GroupingExpr(Expr expr) {
            this.expression = expr;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitGroupingExpr(this);
        }
    }

    public static class LiteralExpr extends Expr {
        public final Object literal;
        public LiteralExpr(Object literal) {
            this.literal = literal;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitLiteralExpr(this);
        }
    }

    public static class UnaryExpr extends Expr {
        public final Token operator;
        public final Expr right;
        public UnaryExpr(Token op, Expr right) {
            this.operator = op;
            this.right = right;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitUnaryExpr(this);
        }
    }

    public static class VariableExpr extends Expr {
        public final Token name;
        public VariableExpr(Token name) {
            this.name = name;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitVariableExpr(this);
        }
    }

    public static class AssignExpr extends Expr {
        public final Token name;
        public final Expr value;
        public AssignExpr(Token name, Expr value) {
            this.name = name;
            this.value = value;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitAssignExpr(this);
        }
    }

    public static class CallExpr extends Expr {
        public final Expr callee;
        public final Token paren;
        public final List<Expr> arguments;
        public CallExpr(Expr callee, Token paren, List<Expr> args) {
            this.callee = callee;
            this.paren = paren;
            this.arguments = args;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitCallExpr(this);
        }
    }

    public static class FunctionExpr extends Expr {
        public final Token name; // can be null
        public final List<Token> params;
        public final List<Stmt> body;
        public FunctionExpr(Token name, List<Token> params, List<Stmt> body) {
            this.name = name;
            this.params = params;
            this.body = body;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitFunctionExpr(this);
        }
    }

    public static class ArrayExpr extends Expr {
        public final List<Expr> elements;
        public ArrayExpr(List<Expr> elements) {
            this.elements = elements;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitArrayExpr(this);
        }
    }

    public static class DictExpr extends Expr {
        public final List<DictEntry> entries;
        public DictExpr(List<DictEntry> entries) {
            this.entries = entries;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitDictExpr(this);
        }
    }

    public static class GetExpr extends Expr {
        public final Expr object;
        public final Expr index;
        public GetExpr(Expr object, Expr index) {
            this.object = object;
            this.index = index;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitGetExpr(this);
        }
    }

    public static class SetExpr extends Expr {
        public final Expr object;
        public final Expr index;
        public final Token equals;
        public final Expr value;
        public SetExpr(Expr object, Expr index, Token eq, Expr value) {
            this.object = object;
            this.index = index;
            this.equals = eq;
            this.value = value;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitSetExpr(this);
        }
    }

    public static class TernaryExpr extends Expr {
        public final Expr condition;
        public final Expr thenExpr;
        public final Expr elseExpr;
        public TernaryExpr(Expr cond, Expr t, Expr e) {
            this.condition = cond;
            this.thenExpr = t;
            this.elseExpr = e;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitTernaryExpr(this);
        }
    }

    public static class RangeExpr extends Expr {
        public final Expr start;
        public final Expr end;
        public final Expr step;
        public RangeExpr(Expr start, Expr end, Expr step) {
            this.start = start;
            this.end = end;
            this.step = step;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitRangeExpr(this);
        }
    }

    public static class DotExpr extends Expr {
        public final Expr object;    // expression before the dot
        public final Token name;     // the identifier after the dot

        public DotExpr(Expr object, Token name) {
            this.object = object;
            this.name = name;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitDotExpr(this);
        }
    }


    public static class DictEntry {
        public final Expr key;
        public final Expr value;
        public DictEntry(Expr key, Expr value) {
            this.key = key;
            this.value = value;
        }
    }

    // ---------- Stmt classes ----------

    public static abstract class Stmt {
        public interface Visitor<R> {
            R visitExpressionStmt(ExpressionStmt stmt);
            R visitBlockStmt(BlockStmt stmt);
            R visitIfStmt(IfStmt stmt);
            R visitWhileStmt(WhileStmt stmt);
            R visitFunctionStmt(FunctionStmt stmt);
            R visitReturnStmt(ReturnStmt stmt);
            R visitForStmt(ForStmt stmt);
            R visitClassStmt(ClassStmt stmt);
        }
        public abstract <R> R accept(Visitor<R> visitor);
    }

    public static class ExpressionStmt extends Stmt {
        public final Expr expression;
        public ExpressionStmt(Expr expr) {
            this.expression = expr;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitExpressionStmt(this);
        }
    }

    public static class BlockStmt extends Stmt {
        public final List<Stmt> statements;
        public BlockStmt(List<Stmt> stmts) {
            this.statements = stmts;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBlockStmt(this);
        }
    }

    public static class IfStmt extends Stmt {
        public final Expr condition;
        public final Stmt thenBranch;
        public final Stmt elseBranch;
        public IfStmt(Expr cond, Stmt t, Stmt e) {
            this.condition = cond;
            this.thenBranch = t;
            this.elseBranch = e;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitIfStmt(this);
        }
    }

    public static class WhileStmt extends Stmt {
        public final Expr condition;
        public final Stmt body;
        public WhileStmt(Expr cond, Stmt body) {
            this.condition = cond;
            this.body = body;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitWhileStmt(this);
        }
    }

    public static class FunctionStmt extends Stmt {
        public final Token name;
        public final List<Token> params;
        public final List<Stmt> body;
        public FunctionStmt(Token name, List<Token> params, List<Stmt> body) {
            this.name = name;
            this.params = params;
            this.body = body;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitFunctionStmt(this);
        }
    }

    public static class ReturnStmt extends Stmt {
        public final Token keyword;
        public final Expr value;
        public ReturnStmt(Token kw, Expr val) {
            this.keyword = kw;
            this.value = val;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitReturnStmt(this);
        }
    }

    public static class ForStmt extends Stmt {
        public final Token varName;
        public final Expr iterable;
        public final Stmt body;
        public ForStmt(Token varName, Expr iter, Stmt b) {
            this.varName = varName;
            this.iterable = iter;
            this.body = b;
        }
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitForStmt(this);
        }
    }

    public static class ClassStmt extends Stmt {
        public final Token name;        // classA
        public final Token parentName;  // classB or null
        public final List<Stmt> body;   // the statements inside { ... }

        public ClassStmt(Token name, Token parentName, List<Stmt> body) {
            this.name = name;
            this.parentName = parentName;
            this.body = body;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitClassStmt(this);
        }
    }

}
