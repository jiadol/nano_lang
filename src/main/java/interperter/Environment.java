package interperter;

import lexer.Token;
import meta.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * A unified Environment that can act either as:
 *  1) A normal lexical environment, or
 *  2) A class environment storing fields in 'classEntity'.
 *
 * If 'classEntity' is non-null, this environment is in class mode.
 * Otherwise, it uses the standard 'values' map plus optional 'enclosing' environment.
 */
public class Environment {
    // For normal environments:
    private final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    // For class environments:
    private final Entity classEntity;
    private final Environment outerEnv;

    /**
     * 1) Constructor for a top-level environment (globals):
     */
    public Environment() {
        this.enclosing = null;
        this.classEntity = null;
        this.outerEnv = null;
    }

    /**
     * 2) Constructor for a nested lexical environment:
     *    e.g. new Environment(someEnclosingEnv).
     */
    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
        this.classEntity = null;
        this.outerEnv = null;
    }

    /**
     * 3) Constructor for a class environment:
     *    If classEntity != null, we store new definitions in that entity
     *    and fallback reads in outerEnv if needed.
     */
    public Environment(Entity classEntity, Environment outerEnv) {
        // Typically no 'enclosing' chain in a class environment,
        // but you could wire them if you prefer.
        this.enclosing = null;

        this.classEntity = classEntity;
        this.outerEnv = outerEnv;
    }

    // ---------------------------------------------------------
    // Public Interface
    // ---------------------------------------------------------

    public void define(String name, Object value) {
        // If in class mode, define into 'classEntity'
        if (classEntity != null) {
            classEntity.set(name, value);
        }
        // Otherwise, define in the local 'values' map
        else {
            values.put(name, value);
        }
    }

    public Object get(Token name) {
        // Class mode
        if (classEntity != null) {
            Object val = classEntity.get(name.lexeme);
            if (val != null) {
                return val;
            }
            // If not found in the class entity, fallback to outerEnv if it exists
            if (outerEnv != null) {
                return outerEnv.get(name);
            }
            // Not found at all
            return null;
        }

        // Normal environment mode
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }
        if (enclosing != null) {
            return enclosing.get(name);
        }
        System.err.println("Undefined variable '" + name.lexeme + "' at line " + name.line);
        return null;
    }

    public void assign(Token name, Object value) {
        // Class mode
        if (classEntity != null) {
            // If the field is already in the entity's entries, update it
            if (classEntity.entries.containsKey(name.lexeme)) {
                classEntity.set(name.lexeme, value);
                return;
            }
            // Otherwise store it newly. Or fallback to outerEnv if you like
            // (but typically you'd keep class fields local in the entity).
            classEntity.set(name.lexeme, value);
            return;
        }

        // Normal environment mode
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }
        if (enclosing != null && enclosing.contains(name.lexeme)) {
            enclosing.assign(name, value);
            return;
        }
        // If not found at all, create it (weakly typed).
        values.put(name.lexeme, value);
    }

    /**
     * Helper to see if this environment (or any parent) contains the variable name.
     */
    public boolean contains(String name) {
        if (classEntity != null) {
            // If in class mode, check classEntity first
            if (classEntity.entries.containsKey(name)) {
                return true;
            }
            // Then fallback to outerEnv if present
            if (outerEnv != null && outerEnv.contains(name)) {
                return true;
            }
            return false;
        }

        // Normal mode
        if (values.containsKey(name)) {
            return true;
        }
        return enclosing != null && enclosing.contains(name);
    }

    // If you want direct access to "enclosing" or "outerEnv":
    public Environment getEnclosing() {
        return enclosing;
    }
    public Environment getOuterEnv() {
        return outerEnv;
    }
    public Entity getClassEntity() {
        return classEntity;
    }

    /**
     * A quick check: are we in class mode or normal mode?
     */
    public boolean isClassEnvironment() {
        return classEntity != null;
    }
}
