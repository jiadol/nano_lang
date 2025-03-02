package meta;

import interperter.Interpreter;

import java.util.List;

public interface Callable {
    // Negative arity means "accept any number of args"
    int arity();
    Object call(Interpreter interpreter, List<Object> arguments);
}

