package clloyd;

import pmilne.SchemeTokenizer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;


/**
 * Created by IntelliJ IDEA.
 * User: clloyd
 * Date: Mar 13, 2010
 * Time: 11:59:04 AM
 *
 * todo: search for appearances of "._" to indicate direct access to ivars and cleanup.
 * todo: consider implementing all of the BuiltinFunctionMap as anonymous inner classes -- makes them each look more like function pointers.
 * todo: convert SmallMap over to be an Scope itself?  Cache the index of each lookup on the Identifier (each identifier must be separate)
 * todo: when caching is used, we must be able to invalidate the cache if some operation ocurs that 
 * todo: right now all values must be Atoms.  This constrains what can be passed in and out.  Reconsider.
 * todo: add the other primitive predicates (eq?, etc)
 * todo: add support for Vector data type and add code to convert lists into Vectors (see SchemeDefinition)
 * todo: add support for the ListFunction ie: (list a b c)  (See SchemeDef)
 * todo: need to fix the way eval works and make it posible to acces the proper environment.
 * todo: how does one grab the environment in Scheme, if at all? (See SchemeDefinition.pdf)
 * todo: review the course assignment pdf so I can see what their requirements were
 * todo: modify read to allow for specifying the input stream (see what SchemeDefinition says for read function)
 * todo: see if I can use TokenStream rather than Phil's thing (something from Java libraries)
 * todo: do we need support for "macro" (ie which doesn't eval the args) Look in SchemeDefinition
 * todo: should I add static initializers so each BuiltinFunction can self register with the global scope?
 * todo: perhaps "quit" should be a function that sets a flag for the readEvalPrint loop?
 * todo: handle exceptions better -- currently showing java stack trace.
 * todo: add "procedure definition syntax"  to define  ie (define (foo x y) body) equivalent to (define foo (lambda (x y) body))
 *

 * todo: Optimizations Optimizations Optimizations Optimizations Optimizations
 *
 * todo: about the only place where I feel like optimizations need to be made are in the area of the Scope and
 * todo: Identifier evaluation.  Some form of caching would be good (eg cache the index of the value in the env)
 * todo: however the fact that define can change the environment's keys presents problems.  Perhaps each env
 * todo: can maintain a "version" number that is increased when a define is executed and, if the version number is
 * todo: different, we ignore the cache.
 *
 * todo: look into changing the implementation of Scope to use Binding[] and then see if we an cache the index of the Binding in that array
 *       todo: and the index of the environment (how many environments up we need to go)  Then we can either walk that number or try to use an Scope[]
 *       todo: which maintains the stack of environments.  Given that the length of these arrays will general be very short, I think it probbably makes sense
 *       todo: to just use the chaining.  However, a large number of refs will come from the Global scope so this could speed up those lookups considerably.
 */
public final class Scheme {

    public static void main(final String[] args) {
        System.out.println("A Crazy Scheme!");
        final SchemeInterpreter schemeInterpreter = new SchemeInterpreter();
        schemeInterpreter.run(new InputStreamReader(System.in), System.out);
    }

    /********************************************************************
     *
     * SchemeInterpreter:  Creates a global environment and allows for execution of a run loop.
     *
     */
    public static final class SchemeInterpreter {
        private final Scope _globalScope;
        private final Identifier _readFunctionIdentifier;

        public SchemeInterpreter() {
            _globalScope = new Scope(null, ConsCell.EmptyList);
            initGlobalEnvironment(_globalScope);
            _readFunctionIdentifier = Identifier.get("read");  // todo this is not cool
            loadStandardFunctions();  // todo get this working
        }

        private static void initGlobalEnvironment(final Scope globalScope) {
            // Constants
            globalScope.define(Identifier.get(BooleanAtom.TrueName), BooleanAtom.True);
            globalScope.define(Identifier.get(BooleanAtom.FalseName), BooleanAtom.False);
            
            // Add all the singleton BuiltingFunctions and Contstants
            for (final Map.Entry<String, BuiltinFunction>entry : BuiltinFunctionMap.entrySet()) {
                final Identifier identifier = Identifier.get(entry.getKey());
                globalScope.define(identifier, entry.getValue());
            }
            // Other
            globalScope.define(Identifier.get("prompt"), new StringAtom("> "));
        }

        private void loadStandardFunctions() {
            // variadic +
            readAndEval("(define + (lambda list" +
                    "    (if (eq? 0 (length list))" +
                    "        0" +
                    "        (b+ (car list) (apply + (cdr list))))))");

            // convenience for binary arithmetic (not requiring b* b- b/) -- todo some day implement the variadic versions?
            readAndEval("(define - (lambda (x y) (b- x y)))");
            readAndEval("(define * (lambda (x y) (b* x y)))");
            readAndEval("(define / (lambda (x y) (b/ x y)))");

            readAndEval("(define map (lambda (fn items) " +
                    "(if (null? items) " +
                    "   '() " +
                    "   (cons (fn (car items)) (map fn (cdr items))))))");

        }

        private Atom parseAtom(final Reader reader) {
            final ReadFunction readFunction = (ReadFunction)_readFunctionIdentifier.eval(_globalScope);
            // todo need to be able to pass a reader to the read function without this hack
            //final Atom parsedAtom = readFunction.apply(null, _globalScope);
            final Atom parsedAtom = readFunction.apply(null, _globalScope, reader);
            return parsedAtom;
        }

        public Atom readAndEval(final String string) {
            final Reader reader = new StringReader(string);
            final Atom parsedAtom = parseAtom(reader);
            final Atom resultAtom = parsedAtom.eval(_globalScope);
            return resultAtom;
        }

        public void run(final Reader reader, final PrintStream printStream) {
            while(true) {
                //final StringAtom prompt = (StringAtom) _globalScope.get(Identifier.get("prompt"));
                printStream.print("> "); // todo use prompt var as above
                final Atom parsedAtom = parseAtom(reader);
                if (parsedAtom instanceof Identifier && ((Identifier)parsedAtom).string().equals("quit")) {
                    break;
                }
                try {
                    final Atom resultAtom = parsedAtom.eval(_globalScope);
                    resultAtom.print(printStream);
                    printStream.print('\n');
                }
                catch (RuntimeException runtimeException) {
                    // todo do I need to do something about the input still in System.in? (or should the Reader do that?)
                    // todo the java stack trace is not meaningful to the scheme programmer.  Need better error reporting
                    runtimeException.printStackTrace(System.err);
                }
            }
        }
    }

    
    /********************************************************************
     *
     * Atom anything that is created by the parser.  Lists (ConsCells), Strings, Numbers, Identifiers.
     *
     */
    public static interface Atom {
        public Atom eval(final Scope scope);
        public void print(final PrintStream printStream);
    }


    /********************************************************************
     *
     * ConsCell the basis for the Linked List.
     * Note: we can cache the length of the list a given ConsCell heads because _nextConsCell is final/immutable -- once the
     * list is formed, it will never change, so its length is known at construction time.  Also note that small IntegerAtoms
     * are cached so we will not have much garbage there and equals() will be that much faster.
     *
     */
    public static final class ConsCell implements Atom {
        private final Atom _atom;
        private final ConsCell _nextConsCell;
        private final IntegerAtom _length;
        // statics
        public static final ConsCell EmptyList = new ConsCell();

        /**
         * Never use this other than to create the singleton EmptyList
         */
        private ConsCell() {
            if (EmptyList != null) {
                throw new RuntimeException("Never use this constructor other than to create the singleton EmptyList");
            }
            _atom = this;
            _nextConsCell = this;
            _length = IntegerAtom.get(0);
        }

        private ConsCell(final Atom atom, final ConsCell nextConsCell) {
            assert atom != null;
            assert nextConsCell != null;
            _atom = atom;
            _nextConsCell = nextConsCell;
            final int nextConsCellLength = nextConsCell.length().intValue();
            _length = IntegerAtom.get(nextConsCellLength + 1);
        }

        public IntegerAtom length() {
            return _length;
        }

        private Atom car() {
            return _atom;
        }

        private ConsCell cdr() {
            return _nextConsCell;
        }

        private Atom cadr() {
            return cdr().car();
        }

        private Atom caddr() {
            return cdr().cdr().car();
        }

        public Atom eval(final Scope scope) {
            // Note: The only time a ConsCell should receive the eval() message is when its the head of a list.
            // Therefore, we always assume we're doing expression evaluation.
            final Atom atomResult;
            if (this == EmptyList) {
                atomResult = EmptyList;
            }
            else {
                final Atom functionReference = car();
                final BuiltinFunction function = (BuiltinFunction)functionReference.eval(scope);
                final ConsCell args = cdr();
                atomResult = function.apply(args, scope);
            }
            return atomResult;
        }

        private void printAtoms(final PrintStream printStream, final String prefix) {
            if (this != EmptyList) {
                printStream.print(prefix);
                _atom.print(printStream);
                _nextConsCell.printAtoms(printStream, " ");
            }
        }

        public void print(final PrintStream printStream) {
            printStream.print("(");
            printAtoms(printStream, "");
            printStream.print(")");
        }
    }


    /********************************************************************
     *
     * Scope maps identifiers to their storage stacks.  Storahe stacks
     * can be cached on each identifer within a given body of a lambda.
     *
     */
    private static final class Scope {
        private final Scope _parentScope;
        private ConsCell _formalParams;

        private Scope(final Scope parentScope, final ConsCell formalParams) {
            _parentScope = parentScope;
            _formalParams = formalParams;
            initStacks(formalParams);
        }

        private static void initStacks(final ConsCell formalParams) {
            if (formalParams != ConsCell.EmptyList) {
                final Identifier identifier = (Identifier)formalParams.car();
                identifier._valueStack = new Stack<Atom>();
                final ConsCell remainder = formalParams.cdr();
                initStacks(remainder);
            }
        }

        private static Identifier _scanThisScopeForIdentifier(final String uniqueString, final ConsCell paramsList) {
            // todo should make this use recursion. OR USE A HASHTABLE WHERE LARGE
            ConsCell nextParam = paramsList;
            while (nextParam != ConsCell.EmptyList) {
                final Identifier identifier = (Identifier)nextParam.car();
                if (identifier._string == uniqueString) {
                    return identifier;
                }
                nextParam = nextParam.cdr();
            }
            return null;
        }

        // todo need better naming.
        private Identifier scanScopeChainForIdentifier(final String uniqueString) {
            Identifier param = _scanThisScopeForIdentifier(uniqueString, _formalParams);
            if (param == null) {
                if (_parentScope == null) {
                    throw new RuntimeException("Unbound identifier: " + uniqueString);
                }
                param = _parentScope.scanScopeChainForIdentifier(uniqueString);
            }
            return param;
        }

        private void define(final Identifier identifier, final Atom value) {
            System.out.println("DEFINE: " + identifier + " value: " + value);
            final Identifier existingIdentifer = _scanThisScopeForIdentifier(identifier.string(), _formalParams);
            if (existingIdentifer == null) {
                // todo should not create stacks in two differnt places -- unify with the initStaks above
                identifier._valueStack = new Stack<Atom>();
                _formalParams = new ConsCell(identifier, _formalParams);
                identifier.setValue(value);
            }
            else {
                existingIdentifer.setValue(value);
            }
        }

        private void set(final Identifier identifier, final Atom value) {
            System.out.println("SET: " + identifier + " value: " + value);
            final Identifier existingIdentifer = scanScopeChainForIdentifier(identifier.string());
            // Note that existingIdentifer cannot be null -- a RuntimeException will be thrown.
            existingIdentifer.setValue(value);
        }
    }


    /********************************************************************
     * Identifier
     *
     * In this version, an Identifier has the usual name string (which is guaranteed unique so can be compared with ==)
     * but it also has a stack of values.  When apply is called, rather than applying the values to a map inside the
     * Scope, we simply push the values onto the stacks of the Identifiers.  Now, how do the other identifiers
     * out there get a hold of these stacks?  Well, the first time they need to access the stack, if stack is null
     * it needs to be resolved.  I'd rather not be putting the values into the environment, so what if we pass around
     * the FunctionClosure itself?  So we'd eliminate the notion of Scope entirely and only have current Functions.
     * Does this work for BuiltinFunctions?  Does it matter?  I don't think so.  A builtin function doesn't use any
     * identifiers of its own (all code is Java).  All the args are evaluated in the normal way -- eval(FunctionClosure)
     * This leaves just one problem -- the global environment.  We'll need a Scope object that holds the Identifiers
     * for either the global environment OR a FunctionClosure (in essence the formalParams list for FunctionClosures).
     * SO basically, rather than passing around environments, we need to pass around scopes.  A scope is a list of the
     * identifiers that are introduced in this new lexical scope.  A FunctionClosure will hold its scope rather than an
     * environment.  Scopes are created once and are static so do not generate garbage.  Just like the formal params.
     * The scopes are used to locate the identifiers which hold the stacks, and the stacks are shared across all
     * identifers within a given scope.
     *
     * When we enter a new scope, we apply the values to the identifiers by pushing the values into the identifier
     * and then we pop the values off the identifiers as we leave the scope. The popping MUST be done in a
     * finally block to ensure we keep the stacks at the proper level.
     *
     */
    private static final class Identifier implements Atom {
        private final String _string;
        private Stack<Atom> _valueStack; // todo probably want a simpler/leaner implementation of this
        // statics
        private static final Map<String, String> IdentifierNameMap = new HashMap<String, String>();

        private static Identifier get(final String string) {
            String uniqueString = IdentifierNameMap.get(string);
            if (uniqueString == null) {
                uniqueString = string;
                IdentifierNameMap.put(string, uniqueString);
            }
            return new Identifier(uniqueString);
        }

        /**
         * Do not call this directly -- always go through the static Identifier.get(String)
         * @param string id name
         */
        private Identifier(final String string) {
            _string = string;
        }

        private void _checkValueStack(final Scope scope) {
            if (_valueStack == null) {
                System.out.println("*** looking up the value stack again for: " + _string);
                // todo too bad this has to be lazily initialized
                final Identifier param = scope.scanScopeChainForIdentifier(_string);
                _valueStack = param._valueStack;
            }
        }

        public Atom eval(final Scope scope) {
            _checkValueStack(scope);
            final Atom value = _valueStack.peek();
            return value;
        }

//        public void push(final Atom value, final Scope scope) {
//            _checkValueStack(scope);
//            _valueStack.push(value);
//            // todo remove this *********************************************************
//            if (_valueStack.size() > 5) {
//                System.out.println(" STACK HAS GROWN TO > 5: " + _valueStack.size() + " " + _string);
//            }
//        }

        public void push(final Atom value) {
            _valueStack.push(value);
            // todo remove this *********************************************************
            if (_valueStack.size() > 5) {
                System.out.println(" STACK HAS GROWN TO > 5: " + _valueStack.size() + " " + _string);
            }
        }

        public void pop() {
            System.out.println("Identifier.pop(): " + _string + " : " + _valueStack.peek() );
            _valueStack.pop();
        }

        public void setValue(final Atom value) {
            if (!_valueStack.isEmpty()) {
                _valueStack.pop();
            }
            _valueStack.push(value);
        }

        private String string() {
            return _string;
        }

        public void print(final PrintStream printStream) {
            printStream.print(_string);
        }

        @Override
        public String toString() {
            return _string;
        }
    }


    /********************************************************************
     *
     * BooleanAtom wraps a boolean and can be used in predicate expressions
     *
     */
    public static final class BooleanAtom implements Atom {
        private final Boolean _boolean;
        // statics
        private static final String TrueName = "#t";
        private static final String FalseName = "#f";
        private static final BooleanAtom True = new BooleanAtom(Boolean.TRUE);
        private static final BooleanAtom False = new BooleanAtom(Boolean.FALSE);

        public static BooleanAtom getValue(final boolean booleanValue) {
            return booleanValue ? BooleanAtom.True : BooleanAtom.False;
        }

        private BooleanAtom(final Boolean value) {
            _boolean = value;
        }

        public Boolean isTrue() {
            return _boolean;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            final String string = _boolean ? TrueName : FalseName;
            printStream.print(string);
        }

        @Override
        public boolean equals(final Object otherObject) {
            final boolean isEqual;
            if (otherObject instanceof BooleanAtom) {
                isEqual = isTrue() == ((BooleanAtom)otherObject).isTrue();
            }
            else {
                isEqual = false;
            }

            return isEqual;
        }
    }


    /********************************************************************
     *
     * NumberAtom interface for handling all types of numbers.
     *
     */
    public static interface NumberAtom extends Atom {
        public int intValue();
        public float floatValue();

        public NumberAtom add(final NumberAtom otherNumber);
        public NumberAtom subtract(final NumberAtom otherNumber);
        public NumberAtom multiply(final NumberAtom otherNumber);
        public NumberAtom divide(final NumberAtom otherNumber);

        public boolean isLessThan(final NumberAtom otherNumber);
    }


    /********************************************************************
     *
     * IntegerAtom
     *
     */
    public static final class IntegerAtom implements NumberAtom {
        private final int _value;
        // Statics
        private static final int SmallIntegerCount = 128;
        private static final IntegerAtom[] SmallIntegerAtomsCache = new IntegerAtom[SmallIntegerCount * 2];

        static {
            for (int intValue = -SmallIntegerCount; intValue < SmallIntegerCount; intValue++) {
                SmallIntegerAtomsCache[intValue + SmallIntegerCount] = new IntegerAtom(intValue);
            }
        }

        public static IntegerAtom get(final int value) {
            final int index = value + SmallIntegerCount;
            final IntegerAtom integerAtom = (value >= -SmallIntegerCount && value < SmallIntegerCount) ?
                    SmallIntegerAtomsCache[index] :
                    new IntegerAtom(value);
            return integerAtom;
        }

        /**
         * Do not call this directly -- always go through IntegerAtom.get(value)
         * @param value the intValue of the resultant IntegerAtom
         */
        private IntegerAtom(final int value) {
            _value = value;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(_value);
        }

        @Override
        public String toString() {
            return Integer.toString(_value);
        }

        ///////////////////////
        // NumberAtom
        ///////////////////////

        public int intValue() {
            return _value;
        }

        public float floatValue() {
            return (float)_value;
        }

        public NumberAtom add(final NumberAtom otherNumber) {
            if (otherNumber instanceof FloatAtom) {
                final float value = floatValue() + otherNumber.floatValue();
                return new FloatAtom(value);
            }
            else {
                final int value = intValue() + otherNumber.intValue();
                return IntegerAtom.get(value);
            }
        }

        public NumberAtom subtract(final NumberAtom otherNumber) {
            if (otherNumber instanceof FloatAtom) {
                final float value = floatValue() - otherNumber.floatValue();
                return new FloatAtom(value);
            }
            else {
                final int value = intValue() - otherNumber.intValue();
                return IntegerAtom.get(value);
            }
        }

        public NumberAtom multiply(final NumberAtom otherNumber) {
            if (otherNumber instanceof FloatAtom) {
                final float value = floatValue() * otherNumber.floatValue();
                return new FloatAtom(value);
            }
            else {
                final int value = intValue() * otherNumber.intValue();
                return IntegerAtom.get(value);
            }
        }

        public NumberAtom divide(final NumberAtom otherNumber) {
            if (otherNumber instanceof FloatAtom) {
                final float value = floatValue() / otherNumber.floatValue();
                return new FloatAtom(value);
            }
            else {
                final int value = intValue() / otherNumber.intValue();
                return IntegerAtom.get(value);
            }
        }

        public boolean isLessThan(final NumberAtom otherNumber) {
            if (otherNumber instanceof FloatAtom) {
                return floatValue() < otherNumber.floatValue();
            }
            else {
                return intValue() < otherNumber.intValue();
            }
        }

        @Override
        public boolean equals(final Object otherObject) {
            final boolean isEqual;
            if (this == otherObject) {
                isEqual = true;
            }
            else if (otherObject instanceof FloatAtom) {
                isEqual = floatValue() == ((FloatAtom)otherObject).floatValue();
            }
            else if (otherObject instanceof IntegerAtom) {
                isEqual = intValue() == ((IntegerAtom)otherObject).intValue();
            }
            else {
                isEqual = false;
            }
            return isEqual;
        }
    }

    
    /********************************************************************
     *
     * FloatAtom
     *
     */
    public static final class FloatAtom implements NumberAtom {
        private final float _value;

        private FloatAtom(final float value) {
            _value = value;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(_value);
        }

        @Override
        public String toString() {
            return Float.toString(_value);
        }

        ///////////////////////
        // NumberAtom
        ///////////////////////

        public int intValue() {
            throw new RuntimeException("do not downgrade float to int, for now");
        }

        public float floatValue() {
            return _value;
        }

        public NumberAtom add(final NumberAtom otherNumber) {
            final float value = floatValue() + otherNumber.floatValue();
            return new FloatAtom(value);
        }

        public NumberAtom subtract(final NumberAtom otherNumber) {
            final float value = floatValue() - otherNumber.floatValue();
            return new FloatAtom(value);
        }

        public NumberAtom multiply(final NumberAtom otherNumber) {
            final float value = floatValue() * otherNumber.floatValue();
            return new FloatAtom(value);
        }

        public NumberAtom divide(final NumberAtom otherNumber) {
            final float value = floatValue() / otherNumber.floatValue();
            return new FloatAtom(value);
        }

        public boolean isLessThan(final NumberAtom otherNumber) {
            return floatValue() < otherNumber.floatValue();
        }

        @Override
        public boolean equals(final Object otherObject) {
            final boolean isEqual;
            if (this == otherObject) {
                isEqual = true;
            }
            else if (otherObject instanceof FloatAtom) {
                isEqual = floatValue() == ((FloatAtom)otherObject).floatValue();
            }
            else if (otherObject instanceof IntegerAtom) {
                isEqual = intValue() == ((IntegerAtom)otherObject).intValue();
            }
            else {
                isEqual = false;
            }
            return isEqual;
        }
    }


    /********************************************************************
     *
     * StringAtom wraps a String
     *
     */
    private static final class StringAtom implements Atom {
        private final String _string;

        private StringAtom(final String string) {
            _string = string;
        }

        private String string() {
            return _string;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print('"');
            printStream.print(_string);
            printStream.print('"');
        }

        @Override
        public boolean equals(final Object otherObject) {
            final boolean isEqual;
            if (this == otherObject) {
                isEqual = true;
            }
            else if (otherObject instanceof StringAtom) {
                isEqual = string().equals(((StringAtom)otherObject).string());
            }
            else {
                isEqual = false;
            }
            return isEqual;
        }
    }


    /********************************************************************
     *
     * BuiltinFunction
     * todo: this may warrant an abstract class status
     *
     */
    private static interface BuiltinFunction extends Atom {
        public Atom apply(final ConsCell args, final Scope scope);
    }

    // statics
    public static final Map<String, BuiltinFunction> BuiltinFunctionMap = new HashMap<String, BuiltinFunction>(32);

    static {
        initBuiltinFunctions(BuiltinFunctionMap);
    }

    private static void initBuiltinFunctions(final Map<String, BuiltinFunction> builtinFunctionsMap) {
        // Builtin Functions
        builtinFunctionsMap.put(BinaryPlus.Name, new BinaryPlus());
        builtinFunctionsMap.put(BinaryMinus.Name, new BinaryMinus());
        builtinFunctionsMap.put(BinaryMultiply.Name, new BinaryMultiply());
        builtinFunctionsMap.put(BinaryDivide.Name, new BinaryDivide());

        builtinFunctionsMap.put(ReadFunction.Name, new ReadFunction());
        builtinFunctionsMap.put(QuoteFunction.Name, new QuoteFunction());
        builtinFunctionsMap.put(BeginFunction.Name, new BeginFunction());
        builtinFunctionsMap.put(LambdaFunction.Name, new LambdaFunction());
        builtinFunctionsMap.put(DefineFunction.Name, new DefineFunction());
        builtinFunctionsMap.put(SetBangFunction.Name, new SetBangFunction());
        builtinFunctionsMap.put(LambdaBasedLetFunction.Name, new LambdaBasedLetFunction());
//        builtinFunctionsMap.put(SimpleLetFunction.Name, new SimpleLetFunction());  todo add Let support

        builtinFunctionsMap.put(ConsFunction.Name, new ConsFunction());
        builtinFunctionsMap.put(CarFunction.Name, new CarFunction());
        builtinFunctionsMap.put(CdrFunction.Name, new CdrFunction());
        builtinFunctionsMap.put(IfFunction.Name, new IfFunction());
        builtinFunctionsMap.put(WhileFunction.Name, new WhileFunction());
        builtinFunctionsMap.put(EvalFunction.Name, new EvalFunction());
        builtinFunctionsMap.put(ApplyFunction.Name, new ApplyFunction());

        // predicates
        builtinFunctionsMap.put(BinaryLessThanFunction.Name, new BinaryLessThanFunction());
        builtinFunctionsMap.put(EqualsFunction.Name, new EqualsFunction());
        builtinFunctionsMap.put(ListNullFunction.Name, new ListNullFunction());
        builtinFunctionsMap.put(ListLengthFunction.Name, new ListLengthFunction());
    }


    /********************************************************************
     *
     * ReadFunction is the builtin function that implements the parser.
     *
     */
    private static final class ReadFunction implements BuiltinFunction {
        private static final String Name = "read";

        public Atom apply(final ConsCell args, final Scope scope) {
            throw new RuntimeException("Unsupported");
        }

        /**
         * todo THIS IS A HACK TO ALLOW FOR Reader to be passed.
         * @param args args
         * @param scope env
         * @param reader reader
         * @return parsed Atom
         */
        public Atom apply(final ConsCell args, final Scope scope, final Reader reader) {
            // This uses Phil Milne's SchemeTokenizer which is akin to Lexx.
            final SchemeTokenizer tokenizer = new SchemeTokenizer(reader);
            try {
                return parseAtom(tokenizer);
            }
            catch (IOException ioexception) {
                throw new RuntimeException(ioexception);
            }
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        ////////////////////////////////////////////////////////
        //  Parsing Code
        ////////////////////////////////////////////////////////

        private static String nextToken(final SchemeTokenizer tokenizer) throws IOException {
            // Skip Whitespace
            while (true) {
                final String token = tokenizer.readToken();
                if (token.length() > 0 && !tokenizer.isWhiteSpace()) {
                    return token;
                }
            }
        }

        /*
        todo this is the recursive version which uses too many stack frames.  Converted to iterative version below.
        private static ConsCell parseList(final SchemeTokenizer tokenizer) throws IOException {
            final ConsCell list;
            final Atom atom = parseAtom(tokenizer);
            if (atom == null) {
                list = ConsCell.EmptyList;
            }
            else {
                final ConsCell nextConsCell = parseList(tokenizer);
                list = new ConsCell(atom, nextConsCell);
            }
            return list;
        }
        */

        private static ConsCell parseList(final SchemeTokenizer tokenizer) throws IOException {
            final Stack<Atom> atomStack = new Stack<Atom>();
            Atom atom;
            while ((atom = parseAtom(tokenizer)) != null) {
                atomStack.push(atom);
            }
            ConsCell consCell = ConsCell.EmptyList;
            while (!atomStack.isEmpty()) {
                atom = atomStack.pop();
                consCell = new ConsCell(atom, consCell);
            }
            return consCell;
        }

        private static Atom parseAtom(final SchemeTokenizer tokenizer) throws IOException {
            final Atom atom;
            final String token = nextToken(tokenizer);
            if (token.equals("(")) {
                atom = parseList(tokenizer);
            }
            else if (token.equals(")")) {
                // Note: Do NOT return ConsCell.EmptyList here as it confuses the parseList(...) function
                // because parseList() may return an EmptyList and then we can't tell if we're at the end
                // of a list or the entire list was empty.
                atom = null;
            }
            else if (token.equals("'")) {
                final Atom quotedAtom = parseAtom(tokenizer);
                final ConsCell nextConsCell = new ConsCell(quotedAtom, ConsCell.EmptyList);
                atom = QuoteFunction.quotedConsCell(nextConsCell);
            }
            else if (tokenizer.isInt()) {
                final Integer number = Integer.parseInt(token);
                atom = IntegerAtom.get(number);
            }
            else if (tokenizer.isFloat() || tokenizer.isNumber()) {
                final Float number = Float.parseFloat(token);
                atom = new FloatAtom(number);
            }
            else if (token.startsWith("\"")) {
                atom = new StringAtom(token.substring(1, token.length() - 1));
            }
            else {
                atom = Identifier.get(token);
            }
            return atom;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * QuoteFunction is the builtin function supports escaping evaluation.
     * The parser supports the form '(x y z) but that is just syntactic sugar
     * for (quote (x y z)), which evaluates to (x y z).
     *
     */
    private static final class QuoteFunction implements BuiltinFunction {
        private static final String Name = "quote";

        private static ConsCell quotedConsCell(final ConsCell nextConsCell) {
            final QuoteFunction quoteFunction = (QuoteFunction)BuiltinFunctionMap.get(Identifier.get(QuoteFunction.Name).string());
            final ConsCell consCell = new ConsCell(quoteFunction, nextConsCell);
            return consCell;
        }

        public Atom apply(final ConsCell args, final Scope scope) {
            return args.car();
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }



    /********************************************************************
     *
     * BeginFunction is the builtin function supports blocks of code with mutiple expressions that each need to be
     * evaluated in order.  The value of the function is the value of the final exprssion.
     *
     */
    private static final class BeginFunction implements BuiltinFunction {
        private static final String Name = "begin";

        /**
         * The args for (begin a b c) are just like the body of a FunctionClosure.
         * A FunctionClosure body is actually a list of Atoms/expressions.  Each must be evaluated in order
         * and the result of the final evaluation is returned.  This is a recursive method which processes each
         * expression of the _body, and peels off the remainder to be evaluated after the current expression.
         * @param expressionList the remaining Atoms from teh _body to be evaluated.
         * @param executionScope the environment in which to evaluate each expression
         * @return the value of the last expression evaluated.
         */
        private static Atom _evalExpressions(final ConsCell expressionList, final Scope executionScope) {
            final Atom resultAtom;
            if (expressionList != ConsCell.EmptyList) {
                final Atom expression = expressionList.car();
                final Atom currentResult = expression.eval(executionScope);
                final ConsCell remainingExpressions = expressionList.cdr();
                final Atom nextResult = _evalExpressions(remainingExpressions, executionScope);
                resultAtom = nextResult == null ? currentResult : nextResult;
            }
            else {
                resultAtom = null;
            }
            return resultAtom;
        }

        public Atom apply(final ConsCell args, final Scope scope) {
            return _evalExpressions(args, scope);
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * FunctionClosure created via the LambdaFunction.
     * todo: may want to add flag _isMacro to allow for skipping the evaluation of the args.
     * todo: such a FunctionClosure could be created by the lambda-macro function.
     *
     */
    public static final class FunctionClosure implements BuiltinFunction {
        private final Scope _scope;
        private final ConsCell _body;
        private final boolean _isFormalParamList;

        private FunctionClosure(final Atom formalParams, final ConsCell body, final Scope parentScope) {
            _body = body;
            _isFormalParamList = formalParams instanceof ConsCell;
            if (_isFormalParamList) {
                _scope = new Scope(parentScope, (ConsCell)formalParams);
            }
            else {
                final ConsCell wrappedParams = new ConsCell(formalParams, ConsCell.EmptyList);
                _scope = new Scope(parentScope, wrappedParams);
            }
        }

        private static ConsCell _evalArgs(final ConsCell args, final Scope invocationScope) {
            // For each arg, we eval in the invocationScope and build a new list from the values.
            final ConsCell resultList;
            if (args != ConsCell.EmptyList) {
                final Atom arg = args.car();
                final Atom value = arg.eval(invocationScope);
                final ConsCell argsRemainder = args.cdr();
                final ConsCell remainderValuesList = _evalArgs(argsRemainder, invocationScope);
                resultList = new ConsCell(value, remainderValuesList);
            }
            else {
                resultList = ConsCell.EmptyList;
            }
            return resultList;
        }
        
        private static final String DotIdentifier = Identifier.get(".").string();
        
        /**
         * This applies the args for formalParams which is defined as a list.  Both the formalParms list and
         * the args list are walked in parallel
         * @param formalParams formalParams
         * @param args args
         * @param invocationScope invocationScope
         */
        private static void _applyArgs(final ConsCell formalParams, final ConsCell args,
                                       final Scope invocationScope) {
            // Recursively walk formalParams and args in parallel.  For each arg, we eval in the invocationScope
            // and then bind the values of the args in the executionScope using the formalParam as identifiers.
            if (formalParams != ConsCell.EmptyList) {
                final Identifier formalParam = (Identifier)formalParams.car();
                if (formalParam.string() == DotIdentifier) {
                    // This handles case:  (lambda (a b . c) p q r s t)
                    // and assign the values as: a=p b=q c=(r s t)
                    final Identifier actualParam = (Identifier)formalParams.cadr();
                    final ConsCell argsList = _evalArgs(args, invocationScope);
                    actualParam.push(argsList);
                }
                else {
                    final Atom arg = args.car();
                    final Atom value = arg.eval(invocationScope);
                    formalParam.push(value);
                    final ConsCell formalParamsRemainder = formalParams.cdr();
                    final ConsCell argsRemainder = args.cdr();
                    _applyArgs(formalParamsRemainder, argsRemainder, invocationScope);
                }
            }
        }

        /**
         * This is done at the conclusion of the apply method and balances the stacks for each formalArg.
         * @param argsList argsList
         */
        private static void _popArgs(final ConsCell argsList) {
            if (argsList != ConsCell.EmptyList) {
                final Identifier identifier = (Identifier)argsList.car();
                if (identifier.string() != DotIdentifier) {
                    identifier.pop();
                }
                final ConsCell remainder = argsList.cdr();
                _popArgs(remainder);
            }
        }

        /**
         *
         * @param args a list of unevaluated Atoms which will be evaluated in the invocationScope and
         * bound to their respective formalParams in the invocationScope.
         * @param invocationScope The environment which is currently active when the function is invoked.  The
         * args will be evaluated in this environment and bound to their formalParams within the executionEnvironment
         * (ie the environment where the body of the function will be evaluated).
         * @return currently, the body is assumed to be a single expression, so the return value is the value of that
         * expression when evaluated in the executionEnvironment.
         */
        public Atom apply(final ConsCell args, final Scope invocationScope) {
            // todo note that we push args outside the try/finally.  Some args may get pushed and not popped if
            // todo an exception happens.  Need to figure out how to handle keeping the stacks balanced in the
            // todo event that an exception happens when evaluating the args
            // todo the API to _applyArgs is a bit weird -- need to clean up
            if (_isFormalParamList) {
                _applyArgs(_scope._formalParams, args, invocationScope);
            }
            else {
                // This handles the case: ((lambda a a) b c d e)
                // and assigns values as: a=(b c d e)
                final Identifier formalParam = (Identifier)_scope._formalParams.car();
                final ConsCell argsList = _evalArgs(args, invocationScope);
                formalParam.push(argsList);
            }

            // The body of a FunctionClosure has an implicit (begin body) wrapped around it
            final Atom resultAtom;
            try {
                resultAtom = BeginFunction._evalExpressions(_body, _scope);
            }
            finally {
                _popArgs(_scope._formalParams);
            }
            return resultAtom;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print('(');
            printStream.print(LambdaFunction.Name);
            printStream.print(' ');
            final ConsCell params = _scope._formalParams;
            if (_isFormalParamList) {
                params.print(printStream);
            }
            else {
                final Identifier identifier = (Identifier)params.car();
                identifier.print(printStream);
            }
            printStream.print(' ');
            // todo: this isn't going to print correctly as _body isn't required to be wrapped in a list
            // todo: need to add recursive print or list print that skips the parentheses
            _body.print(printStream);
            printStream.print(')');
        }
    }


    /********************************************************************
     *
     * LambdaFunction is the builtin function that constructs a FunctionClosure.
     *
     */
    private static final class LambdaFunction implements BuiltinFunction {
        private static final String Name = "lambda";

        public Atom apply(final ConsCell args, final Scope scope) {
            final Atom formalParameters = args.car();
            final ConsCell body = args.cdr();
            final FunctionClosure functionClosure = new FunctionClosure(formalParameters, body, scope);
            return functionClosure;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }

    /********************************************************************
     *
     * LambdaBasedLetFunction is the builtin function that puts a series of key/value pair into a new environment
     * and then evaluates the body.
     * (let ((a x)) (b y) (c z)) atom)
     * or
     * (let bindings-list body)
     * todo this is quick and dirty hack -- real implementation should be done in scheme code
     * todo using lambda and probably requires a modification to FunctionClosure so that it
     * todo can be used as a "macro" facility which does not evaluate its args before the function
     * todo evaluates.
     *
     * todo: rather than implement this in scheme (since its such a basic part of the system)
     * todo: I should modify LambdaFunction to allow for passing in a completed executionEnv
     * todo: Or is it the case that FunctionClosure should be implemented in terms of a let construct?
     *
     */
    private static final class LambdaBasedLetFunction implements BuiltinFunction {
        private static final String Name = "let";

        // todo, this code is a mess -- we should simply build the Env directly without repackaging the formals and args.
        private static ConsCell _extractCars(final ConsCell bindingsList) {
            final ConsCell consCell;
            if (bindingsList == ConsCell.EmptyList) {
                consCell = ConsCell.EmptyList;
            }
            else {
                final ConsCell binding = (ConsCell)bindingsList.car();
                final Atom id = binding.car();
                final ConsCell remainingBindings = bindingsList.cdr();
                final ConsCell nextConsCell = _extractCars(remainingBindings);
                consCell = new ConsCell(id, nextConsCell);
            }
            return consCell;
        }

        // todo, this code is a mess -- we should simply build the Env directly without repackaging the formals and args.
        private static ConsCell _extractCadrs(final ConsCell bindingsList) {
            final ConsCell consCell;
            if (bindingsList == ConsCell.EmptyList) {
                consCell = ConsCell.EmptyList;
            }
            else {
                final ConsCell binding = (ConsCell)bindingsList.car();
                final Atom arg = binding.cadr();
                final ConsCell remainingBindings = bindingsList.cdr();
                final ConsCell nextConsCell = _extractCadrs(remainingBindings);
                consCell = new ConsCell(arg, nextConsCell);
            }
            return consCell;
        }

        public Atom apply(final ConsCell args, final Scope scope) {
            // Note: bindingsList is of the form '((id1 arg1) (id2 arg2) (...))
            final ConsCell bindingsList = (ConsCell)args.car();
            final ConsCell body = args.cdr();

            final ConsCell formalParams = _extractCars(bindingsList);
            final ConsCell functionArgs = _extractCadrs(bindingsList);

            final FunctionClosure functionClosure = new FunctionClosure(formalParams, body, scope);
            return functionClosure.apply(functionArgs, scope);
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }

    /********************************************************************
     *
     * SimpleLetFunction is the builtin function that puts a series of key/value pair into a new environment
     * and then evaluates the body.
     * (let ((a x)) (b y) (c z)) atom)
     * or
     * (let bindings-list body)
     *
     * I am struggling with the use of "lambda" for this.  I see no need to allocate a new FunctionClosure
     * each time we eval a let.  I don't think its possible to pass the FunctionClosure to any other part
     * of the program -- the let evaluates to the value of an expression, not a Function that can be evaluated
     * in other contexts.
     *
     * So, I have decided to create SimpleLetFunction that merely creates an Scope who parent is the
     * current environment and evaluate the body of the let in that new environment.  We'll then run the test
     * cases I have and see if anything goes haywire.  I do not think it would be possible to construct a test
     * that makes this fail, but I will keep my eyes open for examples.
     *
     */
//    private static final class SimpleLetFunction implements BuiltinFunction {
//        private static final String Name = "let";
//
//        /**
//         * Recursively walk the bindings list ((a b) (c d) (e f)) binding the value of b to a, the value of d to c etc
//         * and placing these into the executionScope.  The body of the let will evaluate in this newly created
//         * environment.  The argument expressions [ie b d and f above] are evaluated in the invocationScope.
//         * @param bindingsList bindingsList
//         * @param invocationScope the environment in which the let evaluates (arg values will be evaluated in this environment)
//         * @param executionScope the environment in which the body of the let will be evaluated (arg values placed into this environment)
//         */
//        private static void _bindArgs(final ConsCell bindingsList, final Scope invocationScope, final Scope executionScope) {
//            if (bindingsList != ConsCell.EmptyList) {
//                final ConsCell binding = (ConsCell)bindingsList.car();
//                final Identifier identifier = (Identifier)binding.car();
//                final Atom expression = binding.cadr();
//                final Atom value = expression.eval(invocationScope);
//                executionScope.put(identifier, value);
//                final ConsCell remainingBindings = bindingsList.cdr();
//                _bindArgs(remainingBindings, invocationScope, executionScope);
//            }
//        }
//
//        public Atom apply(final ConsCell args, final Scope invocationScope) {
//            // Note: bindingsList is of the form '((id1 arg1) (id2 arg2) (...))
//            final ConsCell bindingsList = (ConsCell)args.car();
//            final Scope executionScope = new Scope(invocationScope, bindingsList.length().intValue());
//            _bindArgs(bindingsList, invocationScope, executionScope);
//            final ConsCell body = args.cdr();
//            final Atom resultAtom = BeginFunction._evalExpressions(body, executionScope);
//            return resultAtom;
//        }
//
//        public Atom eval(final Scope scope) {
//            return this;
//        }
//
//        public void print(final PrintStream printStream) {
//            printStream.print(Name);
//        }
//    }
    
    /********************************************************************
     *
     * DefineFunction is the builtin function that puts a key/value pair into the current environment.
     *
     */
    private static final class DefineFunction implements BuiltinFunction {
        private static final String Name = "define";

        public Atom apply(final ConsCell args, final Scope scope) {
            final Identifier identifier = (Identifier)args.car();
            final Atom valueExpression = args.cadr();
            final Atom value = valueExpression.eval(scope);
            scope.define(identifier, value);
            return value;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * SetBangFunction is the builtin function that puts the value into the environment where its currently defined.
     * form:  (set! foo value)
     *
     */
    private static final class SetBangFunction implements BuiltinFunction {
        private static final String Name = "set!";

        public Atom apply(final ConsCell args, final Scope scope) {
            final Identifier identifier = (Identifier)args.car();
            final Atom valueExpression = args.cadr();
            final Atom value = valueExpression.eval(scope);
            scope.set(identifier, value);
            return value;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * ConsFunction form:
     *
     *           (cons alpha '(beta delta gamma))
     *
     * returns:  (alpha beta delta gamma)
     */
    private static final class ConsFunction implements BuiltinFunction {
        private static final String Name = "cons";

        public Atom apply(final ConsCell args, final Scope scope) {
            final Atom firstArg = args.car();
            final Atom firstValue = firstArg.eval(scope);
            final Atom secondArg = args.cadr();
            final ConsCell secondValue = (ConsCell)secondArg.eval(scope);
            final ConsCell newConsCell = new ConsCell(firstValue, secondValue);
            return newConsCell;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * CarFunction
     *
     */
    private static final class CarFunction implements BuiltinFunction {
        private static final String Name = "car";

        public Atom apply(final ConsCell args, final Scope scope) {
            final Atom firstArg = args.car();
            final ConsCell list = (ConsCell)firstArg.eval(scope);
            return list.car();
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * CdrFunction
     *
     */
    private static final class CdrFunction implements BuiltinFunction {
        private static final String Name = "cdr";

        public Atom apply(final ConsCell args, final Scope scope) {
            final Atom firstArg = args.car();
            final ConsCell list = (ConsCell)firstArg.eval(scope);
            return list.cdr();
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * EvalFunction
     * Expects form: (eval '(b+ 5 6))
     * todo: well it turns out we're required to pass in an environment.  Need to study the spec more to figure out where/how to get the env
     *
     */
    private static final class EvalFunction implements BuiltinFunction {
        private static final String Name = "eval";

        public Atom apply(final ConsCell args, final Scope scope) {
            final Atom expressionReference = args.car();
            final Atom expression = expressionReference.eval(scope);
            final Atom resultAtom = expression.eval(scope);
            return resultAtom;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * ApplyFunction
     * Expects form: (apply fn '(5 6))
     * where fn is a function expecting 2 args
     *
     */
    private static final class ApplyFunction implements BuiltinFunction {
        private static final String Name = "apply";

        public Atom apply(final ConsCell args, final Scope scope) {
            final Atom functionReference = args.car();
            final BuiltinFunction function = (BuiltinFunction)functionReference.eval(scope);
            final Atom argsReference = args.cadr();
            final ConsCell argsList = (ConsCell)argsReference.eval(scope);
            final Atom resultAtom = function.apply(argsList, scope);
            return resultAtom;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * BinaryPlus is the builtin function that implements addition.
     *
     */
    private static final class BinaryPlus implements BuiltinFunction {
        private static final String Name = "b+";

        public Atom apply(final ConsCell args, final Scope scope) {
            final Atom firstArg = args.car();
            final NumberAtom number1 = (NumberAtom)firstArg.eval(scope);
            final Atom secondArg = args.cadr();
            final NumberAtom number2 = (NumberAtom)secondArg.eval(scope);
            final NumberAtom result = number1.add(number2);
            return result;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * BinaryPlus is the builtin function that implements subtraction.
     *
     */
    private static final class BinaryMinus implements BuiltinFunction {
        private static final String Name = "b-";

        public Atom apply(final ConsCell args, final Scope scope) {
            final NumberAtom number1 = (NumberAtom)args.car().eval(scope);
            final NumberAtom number2 = (NumberAtom)args.cadr().eval(scope);
            final NumberAtom result = number1.subtract(number2);
            return result;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * BinaryPlus is the builtin function that implements multiplication.
     *
     */
    private static final class BinaryMultiply implements BuiltinFunction {
        private static final String Name = "b*";

        public Atom apply(final ConsCell args, final Scope scope) {
            final NumberAtom number1 = (NumberAtom)args.car().eval(scope);
            final NumberAtom number2 = (NumberAtom)args.cadr().eval(scope);
            final NumberAtom result = number1.multiply(number2);
            return result;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * BinaryPlus is the builtin function that implements division.
     *
     */
    private static final class BinaryDivide implements BuiltinFunction {
        private static final String Name = "b/";

        public Atom apply(final ConsCell args, final Scope scope) {
            final NumberAtom number1 = (NumberAtom)args.car().eval(scope);
            final NumberAtom number2 = (NumberAtom)args.cadr().eval(scope);
            final NumberAtom result = number1.divide(number2);
            return result;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * BinaryLessThanFunction
     * Expects form: (< x y)
     *
     */
    private static final class BinaryLessThanFunction implements BuiltinFunction {
        private static final String Name = "b<";

        public Atom apply(final ConsCell args, final Scope scope) {
            final Atom leftSide = args.car();
            final NumberAtom leftSideValue = (NumberAtom)leftSide.eval(scope);
            final Atom rightSide = args.cadr();
            final NumberAtom rightSideValue = (NumberAtom)rightSide.eval(scope);
            final boolean isLessThan = leftSideValue.isLessThan(rightSideValue);
            final Atom resultAtom = BooleanAtom.getValue(isLessThan);
            return resultAtom;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * ListNullFunction
     * Expects form: (null? '())
     *
     */
    private static final class ListNullFunction implements BuiltinFunction {
        private static final String Name = "null?";

        public Atom apply(final ConsCell args, final Scope scope) {
            final Atom arg = args.car();
            final ConsCell argValue = (ConsCell)arg.eval(scope);
            final boolean isNullList = argValue == ConsCell.EmptyList;
            final Atom resultAtom = BooleanAtom.getValue(isNullList);
            return resultAtom;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * ListLengthFunction
     * Expects form: (length '(1 2 3))
     *
     */
    private static final class ListLengthFunction implements BuiltinFunction {
        private static final String Name = "length";

        public Atom apply(final ConsCell args, final Scope scope) {
            final Atom arg = args.car();
            final ConsCell argValue = (ConsCell)arg.eval(scope);
            final IntegerAtom integerAtom = argValue.length();
            return integerAtom;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }

    
    /********************************************************************
     *
     * EqualsFunction
     * Expects form: (eq? arg1 arg2)
     *
     */
    private static final class EqualsFunction implements BuiltinFunction {
        private static final String Name = "eq?";

        public Atom apply(final ConsCell args, final Scope scope) {
            final Atom arg1 = args.car();
            final Atom argValue1 = arg1.eval(scope);

            final Atom arg2 = args.cadr();
            final Atom argValue2 = arg2.eval(scope);


            final boolean isEqual = argValue1.equals(argValue2);
            final Atom resultAtom = BooleanAtom.getValue(isEqual);
            return resultAtom;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * IfFunction
     * Expects form: (if (> x y) trueBlock falseBlock)
     *
     */
    private static final class IfFunction implements BuiltinFunction {
        private static final String Name = "if";

        public Atom apply(final ConsCell args, final Scope scope) {
            final Atom predicateExpression = args.car();
            final BooleanAtom booleanAtom = (BooleanAtom)predicateExpression.eval(scope);
            final Atom targetExpression;
            if (booleanAtom.isTrue()) {
                targetExpression = args.cadr();
            }
            else if (args.length().intValue() > 2) {
                targetExpression = args.caddr();
            }
            else {
                // todo is this the correct thing to do in the case of one conditional expression that is not evaluated.
                targetExpression = ConsCell.EmptyList;
            }
            final Atom resultAtom = targetExpression.eval(scope);
            return resultAtom;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }


    /********************************************************************
     *
     * WhileFunction
     * Expects form: (while (> x y) trueBlock)
     * // todo this needs to be tested
     * // todo is this part of the spec?  see SchemeDefinition.pdf
     */
    private static final class WhileFunction implements BuiltinFunction {
        private static final String Name = "while";

        public Atom apply(final ConsCell args, final Scope scope) {
            final Atom predicateExpression = args.car();
            final Atom trueBlock = args.cadr();
            Atom resultAtom = ConsCell.EmptyList;
            while (((BooleanAtom)predicateExpression.eval(scope)).isTrue()) {
                resultAtom = trueBlock.eval(scope);
            }
            return resultAtom;
        }

        public Atom eval(final Scope scope) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }
}
