# NANO Language Reference

## 1) Data Types

- **Number**: Uses high-precision `BigDecimal`. Examples: `42`, `3.14159`.

- String

  : Text in double quotes, supports escape sequences like 

  ```
  \n
  ```

  , 

  ```
  \t
  ```

  .

  - Example: `"Hello\nWorld"`.

- **Boolean**: `true` or `false`.

- **None**: Represents the absence of a value or `null`.

### Truthiness

- `false` and `None` are **falsy**.
- Everything else is **truthy** (including `0`).

## 2) Variables & Assignment

- **Weakly typed**: Assign anything to a variable without prior declaration.
- **Syntax**: `name = expression`.
- If a variable does not exist, MicroPy automatically creates it when you assign.

Examples:

```plaintext
x = 10
y = "Hello"
z = true
```

No error if you do: `a = 5` and `a = "world"` – the interpreter just overwrites.

## 3) Operators

### Arithmetic

- `+` (add), `-` (subtract), `*` (multiply), `/` (divide)
- All are **BigDecimal** operations, so `0.1 + 0.2 == 0.3` exactly.

### Comparison

- `==` (equal), `!=` (not equal)
- `>`, `<`, `>=`, `<=`
- These compare numbers if both operands are numeric. Otherwise, `==`/`!=` can compare strings or arrays for equality, etc.

### Logical

- `&&` (AND, short-circuit)
- `||` (OR, short-circuit)
- `!`  (NOT/unary)

### Ternary

- `cond ? thenValue : elseValue`

- Example:

  ```plaintext
  x = (score > 50) ? "pass" : "fail"
  ```

### String Concatenation

- ```
  +
  ```

   can also combine strings:

  ```plaintext
  greeting = "Hello " + "World"
  ```

- If one operand is a string, the interpreter concatenates.

## 4) Control Flow

### If/Else

```plaintext
if (condition) {
    // statements
} else {
    // statements
}
```

- Condition is any expression. If truthy, execute block after `if`, else block after `else`.

### While

```plaintext
while (condition) {
    // statements
}
```

- Repeats as long as `condition` is truthy.

### For

```plaintext
for (item in iterable) {
    // statements
}
```

- MicroPy supports a “for-each” loop. `iterable` must be an array or something that can be iterated.
- Each iteration binds `item` to the current element.

## 5) Functions & Lambdas

### Function Definition

```plaintext
def functionName(p1, p2) {
    // body
    return something
}
```

- Zero or more parameters inside `( )`.
- Optionally `return` a value. If you omit `return`, the function returns `None`.

Example:

```plaintext
def add(a, b) {
    return a + b
}
```

### Lambda (Arrow) Functions

- **Single param**: `x -> x * x`

- **Multi param**: `(x, y) -> x + y`

- They are 

  anonymous

   but can be assigned to variables:

  ```plaintext
  square = x -> x*x
  sum2   = (a, b) -> a + b
  ```

- A lambda’s body is a **single expression** automatically returned.

### Recursion

Functions or lambdas can call themselves:

```plaintext
def fib(n) {
    if (n < 2) {
        return n
    }
    return fib(n-1) + fib(n-2)
}
```

## 6) Arrays & Dictionary Literals

### Arrays

- `[value, value, ...]`

- Access an element: `arr[index]`

- **Concatenate** arrays with `+`.

- Example:

  ```plaintext
  nums = [10, 20, 30]
  nums[1] = 999
  big = nums + [40, 50]
  ```

### Dictionary Literals (With **Unquoted** Keys)

- **Syntax**: `{key: value, key: value, ...}`
- Keys can be **bare identifiers** (like `name: "Alice"`) or expressions, and typically the parser treats an unquoted identifier as a string key.

Example:

```plaintext
person = {name: "Alice", age: 30}
person["age"] = person["age"] + 1
```

No quotes needed around `name` or `age`. The parser interprets them as string keys.

## 7) Classes & Prototype Inheritance

### Class Definition

MicroPy uses a simple “prototype” approach with **`classA:classB = { ... }`**:

- If you have 

  no parent

  , you do:

  ```plaintext
  classA: = {
      // fields, methods
  }
  ```

  or some variation your grammar allows (like omitting the colon if you prefer).

- If you have a 

  parent

   class, do:

  ```plaintext
  classChild:classParent = {
      // fields, methods
  }
  ```

- The body is a block of statements:

  ```plaintext
  fieldX = 123
  def someMethod() {
      // ...
  }
  ```

  etc. Each assignment or function definition becomes a 

  field

   on that class entity.

### Inheritance

When you write:

```plaintext
classChild:classParent = {
    ...
}
```

- The interpreter sets `classParent` as the **`metaentity`** for `classChild`.
- If a field or method isn’t found on `classChild`, it checks `classParent`.
- This chain can continue upward multiple levels.

### Accessing Fields/Methods

- Use 

  dot

   syntax for methods:

  ```plaintext
  classChild.someMethod()
  ```

- Or for fields:

  ```plaintext
  classChild.fieldX
  ```

- The language checks `classChild` entries first. If not found, it checks `classChild.metaentity`.

## 8) Built-in Functions

- **`print(...)`**
  - Accepts any number of arguments, prints them separated by spaces, ends with newline.
- **`inspect(value)`**
  - Prints detailed info. If `value` is an `Entity` (like a class or function), it shows fields. If the entity has a parent, it recursively inspects that.
  - If `value` is a function, it shows parameters and body.
- **`len(value)`**
  - If `value` is an array-like `Entity`, returns BigDecimal length.
  - If you pass something else, it might print an error or return `0` depending on your implementation.

## 9) Comments

- Single-line

   comment: 

  ```
  #
  ```

  - Interpreter ignores everything after `#` on the same line.

Example:

```plaintext
# This is a comment
x = 10  # inline comment
```

No multi-line comment syntax is built in, so use multiple `#` lines for multi-line commentary.

## 10) Example Code

Below is a short example combining many features:

```plaintext
# 1) Create a dictionary with unquoted keys
person = {name:"Alice", age:30}
person["age"] = person["age"] + 1
print("person:", person)

# 2) while loop
count = 3
txt = ""
while (count > 0) {
    txt = txt + count
    count = count - 1
}
print("txt after countdown:", txt)  # "321"

# 3) function & lambda
def add(a, b) {
    return a + b
}
doub = x -> x * 2

print("add(2,3) =", add(2,3))  # 5
print("doub(4) =", doub(4))    # 8

# 4) class with no parent
classB: = {
    valB = 100
    def showB() {
        print("showB says, valB =", valB)
    }
}

# 5) class with parent
classA:classB = {
    valA = 999
    def showA() {
        print("showA says, valA =", valA, ", valB =", valB)
    }
}

# 6) usage
classA.showA()     # uses valA=999, inherits valB=100
classA.showB()     # from parent classB
inspect(classA)    # show fields & parent's fields

# 7) Ternary operator
x = 10
status = (x > 5) ? "High" : "Low"
print("x>5?", status)
```

**Output** might be:

```
person: <table { name=Alice, age=31 }>
txt after countdown: 321
add(2,3) = 5
doub(4) = 8
showA says, valA = 999 , valB = 100
showB says, valB = 100
<MetaEntity Inspect>
Entries:
  valA : 999
  showA : <function showA>
Parent =>
  <MetaEntity Inspect>
  Entries:
    valB : 100
    showB : <function showB>
x>5? High
```

