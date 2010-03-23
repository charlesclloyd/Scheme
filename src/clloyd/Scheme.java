package clloyd;

import pmilne.SchemeTokenizer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;


/**
 * Created by IntelliJ IDEA.
 * User: clloyd
 * Date: Mar 13, 2010
 * Time: 11:59:04 AM
 *
 * todo: now that Scope with stacks has failed, need to try the index caching for fast lookup
 * todo: also can try caching the "slot" for the lexical scope since that is fixed once established
 * todo: consider implementing all of the BuiltinFunctionMap as anonymous inner classes -- makes them each look more like function pointers.
 * todo: convert SmallMap over to be an Environment itself?  Cache the index of each lookup on the Identifier (each identifier must be separate)
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
 * todo: about the only place where I feel like optimizations need to be made are in the area of the Environment and
 * todo: Identifier evaluation.  Some form of caching would be good (eg cache the index of the value in the env)
 * todo: however the fact that define can change the environment's keys presents problems.  Perhaps each env
 * todo: can maintain a "version" number that is increased when a define is executed and, if the version number is
 * todo: different, we ignore the cache.
 *
 * todo: look into changing the implementation of Environment to use Binding[] and then see if we an cache the index of the Binding in that array
 *       todo: and the index of the environment (how many environments up we need to go)  Then we can either walk that number or try to use an Environment[]
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
        private final Environment _globalEnvironment;
        
        public SchemeInterpreter() {
            _globalEnvironment = new MapEnvironment(null, 64);
            initGlobalEnvironment(_globalEnvironment);
            loadStandardFunctions();
        }

        private static void initGlobalEnvironment(final Environment globalEnvironment) {
            // Add all the singleton BuiltingFunctions and Contstants
            globalEnvironment.putAll(BuiltinFunctionMap);
            // Other
            globalEnvironment.put(Identifier.get("env"), globalEnvironment);
            globalEnvironment.put(Identifier.get("prompt"), new StringAtom("> "));
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
            final ReadFunction readFunction = (ReadFunction)_globalEnvironment.get(Identifier.get(ReadFunction.Name));
            // todo need to be able to pass a reader to the read function without this hack
            //final Atom parsedAtom = readFunction.apply(null, _globalEnvironment);
            final Atom parsedAtom = readFunction.apply(null, _globalEnvironment, reader);
            return parsedAtom;
        }

        public Atom readAndEval(final String string) {
            final Reader reader = new StringReader(string);
            final Atom parsedAtom = parseAtom(reader);
            final Atom resultAtom = parsedAtom.eval(_globalEnvironment);
            return resultAtom;
        }

        public void run(final Reader reader, final PrintStream printStream) {
            while(true) {
                final StringAtom prompt = (StringAtom)_globalEnvironment.get(Identifier.get("prompt"));
                printStream.print(prompt.string());
                final Atom parsedAtom = parseAtom(reader);
                if (parsedAtom instanceof Identifier && ((Identifier)parsedAtom).string().equals("quit")) {
                    break;
                }
                try {
                    final Atom resultAtom = parsedAtom.eval(_globalEnvironment);
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
        public Atom eval(final Environment environment);
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

        public Atom eval(final Environment environment) {
            // Note: The only time a ConsCell should receive the eval() message is when its the head of a list.
            // Therefore, we always assume we're doing expression evaluation.
            final Atom atomResult;
            if (this == EmptyList) {
                atomResult = EmptyList;
            }
            else {
                final Atom functionReference = car();
                final BuiltinFunction function = (BuiltinFunction)functionReference.eval(environment);
                final ConsCell args = cdr();
                atomResult = function.apply(args, environment);
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
     * todo this old version is based on Unique identifiers
     * Identifier knows how to look itself up in an Environment
     *
     */
    private static final class Identifier implements Atom {
        private final String _string;
        // statics
        private static final Map<String, Identifier> IdentifierMap = new HashMap<String, Identifier>();

        private static Identifier get(final String string) {
            Identifier identifier = IdentifierMap.get(string);
            if (identifier == null) {
                identifier = new Identifier(string);
                IdentifierMap.put(string, identifier);
            }
            return identifier;
        }

        /**
         * Do not call this directly -- always go through the static Identifier.get(String)
         * @param string id name
         */
        private Identifier(final String string) {
            _string = string;
        }

        public Atom eval(final Environment environment) {
            return environment.get(this);
        }

        private String string() {
            return _string;
        }

        @Override
        public int hashCode() {
            return _string.hashCode();
        }

        @Override
        @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
        public boolean equals(final Object otherObject) {
            // Identifiers are uniqued via IdentifierMap (see above) so can compare with ==.
            return this == otherObject;
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
     * Environment stores the symbol table for the current environment.
     * This is one of the few things in the system which is not an Atom.
     * todo is it kosher to make this an Atom?  Needed for "env" support.
     * todo do we really want to force all values to be Atoms?  Cannot store System.out in env, among others.
     * todo: Note that, as far as the interpreter itself is concerned, the creation and access of Environments
     * todo: is responsible for most garbage generation and cpu cycles compared to a compiled system.  Making the
     * todo: Environment leaner (ie not using Maps) if perhaps the one area to concentrate on for performance.
     * todo: perhaps moving some of the functionality of SmallMap into the Environment itself would help.
     * todo: Another speed improvement could be to cache the most recent lookup index on the Identifiers.
     * todo: but that means we would need to stop uniqueing the Identifiers.
     *
     * todo:  consider this possible optimization...
     *
     * Rather than storing the values directly in the Map, we wrap the value in a AtomHolder (or whatever)
     * and put that in the Map of the Env.  Then, each time we look up a value, can can pull the AtomHolder
     * out of its base environment, and cache the Holder in each of the closer environments (or possibly just the)
     * nearest one?)  So now we don't need to go so far to find the object, yet if we change its value, that change will
     * still affect its home location.  Assuming this works and doesn't have trouble with the define-inside-lambda
     * question, can we take it a step further and cache the AtomHolder on the Identifier themselves?  That is,
     * apply creates the Holders in the local env, and the first time an Identifier is used to look one up, we cache the
     * Holder on the Identifer -- Bzzzt!  Doesn't work because the functions would not be re-entrant.  However, we should
     * be able to cache the index of the Holder as cached in the local scope and use that as a shortcut to looking up
     * the Holder (cached in the local scope).  So we need to add an int _indexCache field to the Identifier and set
     * that each time we use the identifier to look it up.  We first use that index and see if the Identifier we're
     * looking for is there and, if so we use it, else we do the full scan.
     *
     */
    private static interface Environment extends Atom {
        public int size();
        public void put(final Identifier identifier, final Atom atom);
        public Atom get(final Identifier identifier);
        public boolean contains(final Identifier identifier);
        public Environment getParent();
        public void putAll(final Map<Identifier, Atom> map);
    }


    /********************************************************************
     *
     * MapEnvironment implementation using a Map
     */
    private static final class MapEnvironment implements Environment {
        private final Environment _parent;
        final Map<Identifier, Atom> _map;
        // todo Well, most Environments in my simple test cases only have one key/value pair so it seems a big
        // todo waste to use a Map for that (even the SmallMap).

        private MapEnvironment(final Environment parent, final int initialSize) {
            _parent = parent;
            _map = initialSize > 8 ? new HashMap<Identifier, Atom>(initialSize) : new SmallMap<Identifier, Atom>(initialSize);
        }

        public int size() {
            return _map.size();
        }

        public void put(final Identifier identifier, final Atom atom) {
            _map.put(identifier, atom);
        }

        public Atom get(final Identifier identifier) {
            Atom atom = _map.get(identifier);
            if (atom == null) {
                // this recurses up the entire Environment chain
                if (_parent != null) {
                    atom = _parent.get(identifier);
                }
                else {
                    throw new RuntimeException("Unable to locate identifier: " + identifier);
                }
            }
            return atom;
        }

        public boolean contains(final Identifier identifier) {
            return _map.containsKey(identifier);
        }

        public Environment getParent() {
            return _parent;
        }

        public void putAll(final Map<Identifier, Atom> map) {
            _map.putAll(map);
        }

        public Atom eval(final Environment environment) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(_map.toString());
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

        public Atom eval(final Environment environment) {
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

        public Atom eval(final Environment environment) {
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

        public Atom eval(final Environment environment) {
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

        public Atom eval(final Environment environment) {
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
        public Atom apply(final ConsCell args, final Environment environment);
    }

    // statics
    public static final Map<Identifier, Atom> BuiltinFunctionMap = new HashMap<Identifier, Atom>(32);

    static {
        initBuiltinFunctions(BuiltinFunctionMap);
    }

    private static void initBuiltinFunctions(final Map<Identifier, Atom> builtinFunctionsMap) {
        // Constants
        builtinFunctionsMap.put(Identifier.get(BooleanAtom.TrueName), BooleanAtom.True);
        builtinFunctionsMap.put(Identifier.get(BooleanAtom.FalseName), BooleanAtom.False);

        // Builtin Functions
        builtinFunctionsMap.put(Identifier.get(BinaryPlus.Name), new BinaryPlus());
        builtinFunctionsMap.put(Identifier.get(BinaryMinus.Name), new BinaryMinus());
        builtinFunctionsMap.put(Identifier.get(BinaryMultiply.Name), new BinaryMultiply());
        builtinFunctionsMap.put(Identifier.get(BinaryDivide.Name), new BinaryDivide());

        builtinFunctionsMap.put(Identifier.get(ReadFunction.Name), new ReadFunction());
        builtinFunctionsMap.put(Identifier.get(QuoteFunction.Name), new QuoteFunction());
        builtinFunctionsMap.put(Identifier.get(BeginFunction.Name), new BeginFunction());
        builtinFunctionsMap.put(Identifier.get(LambdaFunction.Name), new LambdaFunction());
        builtinFunctionsMap.put(Identifier.get(DefineFunction.Name), new DefineFunction());
        builtinFunctionsMap.put(Identifier.get(SetBangFunction.Name), new SetBangFunction());
        builtinFunctionsMap.put(Identifier.get(SimpleLetFunction.Name), new SimpleLetFunction());

        builtinFunctionsMap.put(Identifier.get(ConsFunction.Name), new ConsFunction());
        builtinFunctionsMap.put(Identifier.get(CarFunction.Name), new CarFunction());
        builtinFunctionsMap.put(Identifier.get(CdrFunction.Name), new CdrFunction());
        builtinFunctionsMap.put(Identifier.get(IfFunction.Name), new IfFunction());
        builtinFunctionsMap.put(Identifier.get(WhileFunction.Name), new WhileFunction());
        builtinFunctionsMap.put(Identifier.get(EvalFunction.Name), new EvalFunction());
        builtinFunctionsMap.put(Identifier.get(ApplyFunction.Name), new ApplyFunction());

        // predicates
        builtinFunctionsMap.put(Identifier.get(BinaryLessThanFunction.Name), new BinaryLessThanFunction());
        builtinFunctionsMap.put(Identifier.get(EqualsFunction.Name), new EqualsFunction());
        builtinFunctionsMap.put(Identifier.get(ListNullFunction.Name), new ListNullFunction());
        builtinFunctionsMap.put(Identifier.get(ListLengthFunction.Name), new ListLengthFunction());
    }


    /********************************************************************
     *
     * ReadFunction is the builtin function that implements the parser.
     *
     */
    private static final class ReadFunction implements BuiltinFunction {
        private static final String Name = "read";

        public Atom apply(final ConsCell args, final Environment environment) {
            throw new RuntimeException("Unsupported");
        }

        /**
         * todo THIS IS A HACK TO ALLOW FOR Reader to be passed.
         * @param args args
         * @param environment env
         * @param reader reader
         * @return parsed Atom
         */
        public Atom apply(final ConsCell args, final Environment environment, final Reader reader) {
            // This uses Phil Milne's SchemeTokenizer which is akin to Lexx.
            final SchemeTokenizer tokenizer = new SchemeTokenizer(reader);
            try {
                return parseAtom(tokenizer);
            }
            catch (IOException ioexception) {
                throw new RuntimeException(ioexception);
            }
        }

        public Atom eval(final Environment environment) {
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
            final QuoteFunction quoteFunction = (QuoteFunction) BuiltinFunctionMap.get(Identifier.get(QuoteFunction.Name));
            final ConsCell consCell = new ConsCell(quoteFunction, nextConsCell);
            return consCell;
        }

        public Atom apply(final ConsCell args, final Environment environment) {
            return args.car();
        }

        public Atom eval(final Environment environment) {
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
         * @param executionEnvironment the environment in which to evaluate each expression
         * @return the value of the last expression evaluated.
         */
        private static Atom _evalExpressions(final ConsCell expressionList, final Environment executionEnvironment) {
            final Atom resultAtom;
            if (expressionList != ConsCell.EmptyList) {
                final Atom expression = expressionList.car();
                final Atom currentResult = expression.eval(executionEnvironment);
                final ConsCell remainingExpressions = expressionList.cdr();
                final Atom nextResult = _evalExpressions(remainingExpressions, executionEnvironment);
                resultAtom = nextResult == null ? currentResult : nextResult;
            }
            else {
                resultAtom = null;
            }
            return resultAtom;
        }

        public Atom apply(final ConsCell args, final Environment environment) {
            return _evalExpressions(args, environment);
        }

        public Atom eval(final Environment environment) {
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
        private final Atom _formalParams;
        private final ConsCell _body;
        private final Environment _lexicalEnvironment;
        private final boolean _isFormalParamList;
        private int _environmentCapacity;

        private FunctionClosure(final Atom formalParams, final ConsCell body, final Environment lexicalEnvironment) {
            _formalParams = formalParams;
            _body = body;
            _lexicalEnvironment = lexicalEnvironment;
            _isFormalParamList = _formalParams instanceof ConsCell;
            _environmentCapacity = _isFormalParamList ? ((ConsCell)formalParams).length().intValue() : 1;
        }

        private static ConsCell _evalArgs(final ConsCell args, final Environment invocationEnvironment) {
            // For each arg, we eval in the invocationEnvironment and build a new list from the values.
            final ConsCell resultList;
            if (args != ConsCell.EmptyList) {
                final Atom arg = args.car();
                final Atom value = arg.eval(invocationEnvironment);
                final ConsCell argsRemainder = args.cdr();
                final ConsCell remainderValuesList = _evalArgs(argsRemainder, invocationEnvironment);
                resultList = new ConsCell(value, remainderValuesList);
            }
            else {
                resultList = ConsCell.EmptyList;
            }
            return resultList;
        }

        /**
         * This applies all the args for formalParam as a single list.  Unlike _applyArgs(...), this only walks
         * the args, computes values for each arg, constructs a new list of values, and puts that list into the
         * executionEnvironment bound to formalParam. 
         *
         * @param formalParam formalParam
         * @param args args
         * @param invocationEnvironment invocationEnvironment
         * @param executionEnvironment executionEnvironment
         */
        private static void _applyVarArgs(final Identifier formalParam, final ConsCell args,
                                          final Environment invocationEnvironment, final Environment executionEnvironment) {
            final ConsCell argsList = _evalArgs(args,  invocationEnvironment);
            executionEnvironment.put(formalParam, argsList);
        }
        
        private static final Identifier DotIdentifier = Identifier.get(".");

        /**
         * This applies the args for formalParams which is defined as a list.  Both the formalParms list and
         * the args list are walked in parallel
         * @param formalParams formalParams
         * @param args args
         * @param invocationEnvironment invocationEnvironment
         * @param executionEnvironment executionEnvironment
         */
        private static void _applyArgs(final ConsCell formalParams, final ConsCell args,
                                       final Environment invocationEnvironment, final Environment executionEnvironment) {
            // Recursively walk formalParams and args in parallel.  For each arg, we eval in the invocationEnvironment
            // and then bind the values of the args in the executionEnvironment using the formalParam as identifiers.
            if (formalParams != ConsCell.EmptyList) {
                final Identifier formalParam = (Identifier)formalParams.car();
                if (formalParam == DotIdentifier) {
                    // This handles case:  (lambda (a b . c) p q r s t)
                    // and assign the values as: a=p b=q c=(r s t)
                    final Identifier actualParam = (Identifier)formalParams.cadr();
                    _applyVarArgs(actualParam, args, invocationEnvironment, executionEnvironment);
                }
                else {
                    final Atom arg = args.car();
                    final Atom value = arg.eval(invocationEnvironment);
                    executionEnvironment.put(formalParam, value);
                    final ConsCell formalParamsRemainder = formalParams.cdr();
                    final ConsCell argsRemainder = args.cdr();
                    _applyArgs(formalParamsRemainder, argsRemainder, invocationEnvironment, executionEnvironment);
                }
            }
        }

        /**
         *
         * @param args a list of unevaluated Atoms which will be evaluated in the invocationEnvironment and
         * bound to their respective formalParams in the invocationEnvironment.
         * @param invocationEnvironment The environment which is currently active when the function is invoked.  The
         * args will be evaluated in this environment and bound to their formalParams within the executionEnvironment
         * (ie the environment where the body of the function will be evaluated).
         * @return currently, the body is assumed to be a single expression, so the return value is the value of that
         * expression when evaluated in the executionEnvironment.
         */
        public Atom apply(final ConsCell args, final Environment invocationEnvironment) {
            // Note: This is the ONLY place where new Environments are created (other than GlobalEnvironment).
            // Note that "let" is implemented using a FunctionClosure, so indirectly let also creates a new env.
            final Environment executionEnvironment = new MapEnvironment(_lexicalEnvironment, _environmentCapacity);
            if (_isFormalParamList) {
                _applyArgs((ConsCell)_formalParams, args,  invocationEnvironment, executionEnvironment);
            }
            else {
                // This handles the case: ((lambda a a) b c d e)
                // and assigns values as: a=(b c d e)
                // _formalParams must be an Identifier and not a ConsCell or constant.
                final Identifier formalParam = (Identifier)_formalParams;
                _applyVarArgs(formalParam, args, invocationEnvironment, executionEnvironment);
            }
            // The body of a FunctionClosure has an implicit (begin body) wrapped around it
            final Atom resultAtom = BeginFunction._evalExpressions(_body, executionEnvironment);
            // keep track of the environment size so, if it grows, we avoid its growing again.
            _environmentCapacity = executionEnvironment.size();
            return resultAtom;
        }

        public Atom eval(final Environment environment) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print('(');
            printStream.print(LambdaFunction.Name);
            printStream.print(' ');
            _formalParams.print(printStream);
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final Atom formalParameters = args.car();
            final ConsCell body = args.cdr();
            final FunctionClosure functionClosure = new FunctionClosure(formalParameters, body, environment);
            return functionClosure;
        }

        public Atom eval(final Environment environment) {
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
     * So, I have decided to create SimpleLetFunction that merely creates an Environment who parent is the
     * current environment and evaluate the body of the let in that new environment.  We'll then run the test
     * cases I have and see if anything goes haywire.  I do not think it would be possible to construct a test
     * that makes this fail, but I will keep my eyes open for examples.
     *
     */
    private static final class SimpleLetFunction implements BuiltinFunction {
        private static final String Name = "let";

        /**
         * Recursively walk the bindings list ((a b) (c d) (e f)) binding the value of b to a, the value of d to c etc
         * and placing these into the executionEnvironment.  The body of the let will evaluate in this newly created
         * environment.  The argument expressions [ie b d and f above] are evaluated in the invocationEnvironment.
         * @param bindingsList bindingsList
         * @param invocationEnvironment the environment in which the let evaluates (arg values will be evaluated in this environment)
         * @param executionEnvironment the environment in which the body of the let will be evaluated (arg values placed into this environment)
         */
        private static void _bindArgs(final ConsCell bindingsList, final Environment invocationEnvironment, final Environment executionEnvironment) {
            if (bindingsList != ConsCell.EmptyList) {
                final ConsCell binding = (ConsCell)bindingsList.car();
                final Identifier identifier = (Identifier)binding.car();
                final Atom expression = binding.cadr();
                final Atom value = expression.eval(invocationEnvironment);
                executionEnvironment.put(identifier, value);
                final ConsCell remainingBindings = bindingsList.cdr();
                _bindArgs(remainingBindings, invocationEnvironment, executionEnvironment);
            }
        }

        public Atom apply(final ConsCell args, final Environment invocationEnvironment) {
            // Note: bindingsList is of the form '((id1 arg1) (id2 arg2) (...))
            final ConsCell bindingsList = (ConsCell)args.car();
            final Environment executionEnvironment = new MapEnvironment(invocationEnvironment, bindingsList.length().intValue());
            _bindArgs(bindingsList, invocationEnvironment, executionEnvironment);
            final ConsCell body = args.cdr();
            final Atom resultAtom = BeginFunction._evalExpressions(body, executionEnvironment);
            return resultAtom;
        }

        public Atom eval(final Environment environment) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }

    /********************************************************************
     *
     * DefineFunction is the builtin function that puts a key/value pair into the current environment.
     *
     */
    private static final class DefineFunction implements BuiltinFunction {
        private static final String Name = "define";

        public Atom apply(final ConsCell args, final Environment environment) {
            final Identifier identifier = (Identifier)args.car();
            final Atom valueExpression = args.cadr();
            final Atom value = valueExpression.eval(environment);
            environment.put(identifier, value);
            return value;
        }

        public Atom eval(final Environment environment) {
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

        /**
         * Used to recursively locate and set an existing value in the first enclosing environment which already has the identifier.
         * @param environment environment
         * @param identifier id
         * @param value val
         */
        private static void _locateAndSet(final Environment environment, final Identifier identifier, final Atom value) {
            if (environment.contains(identifier)) {
                environment.put(identifier, value);
            }
            else {
                final Environment parent = environment.getParent();
                if (parent != null) {
                    _locateAndSet(parent, identifier, value);
                }
                else {
                    throw new RuntimeException("Unable to locate identifier for setBang: " + identifier);
                }
            }
        }

        public Atom apply(final ConsCell args, final Environment environment) {
            final Identifier identifier = (Identifier)args.car();
            final Atom valueExpression = args.cadr();
            final Atom value = valueExpression.eval(environment);
            _locateAndSet(environment, identifier, value);
            return value;
        }

        public Atom eval(final Environment environment) {
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final Atom firstArg = args.car();
            final Atom firstValue = firstArg.eval(environment);
            final Atom secondArg = args.cadr();
            final ConsCell secondValue = (ConsCell)secondArg.eval(environment);
            final ConsCell newConsCell = new ConsCell(firstValue, secondValue);
            return newConsCell;
        }

        public Atom eval(final Environment environment) {
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final Atom firstArg = args.car();
            final ConsCell list = (ConsCell)firstArg.eval(environment);
            return list.car();
        }

        public Atom eval(final Environment environment) {
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final Atom firstArg = args.car();
            final ConsCell list = (ConsCell)firstArg.eval(environment);
            return list.cdr();
        }

        public Atom eval(final Environment environment) {
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final Atom expressionReference = args.car();
            final Atom expression = expressionReference.eval(environment);
            final Atom resultAtom = expression.eval(environment);
            return resultAtom;
        }

        public Atom eval(final Environment environment) {
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final Atom functionReference = args.car();
            final BuiltinFunction function = (BuiltinFunction)functionReference.eval(environment);
            final Atom argsReference = args.cadr();
            final ConsCell argsList = (ConsCell)argsReference.eval(environment);
            final Atom resultAtom = function.apply(argsList, environment);
            return resultAtom;
        }

        public Atom eval(final Environment environment) {
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final Atom firstArg = args.car();
            final NumberAtom number1 = (NumberAtom)firstArg.eval(environment);
            final Atom secondArg = args.cadr();
            final NumberAtom number2 = (NumberAtom)secondArg.eval(environment);
            final NumberAtom result = number1.add(number2);
            return result;
        }

        public Atom eval(final Environment environment) {
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final NumberAtom number1 = (NumberAtom)args.car().eval(environment);
            final NumberAtom number2 = (NumberAtom)args.cadr().eval(environment);
            final NumberAtom result = number1.subtract(number2);
            return result;
        }

        public Atom eval(final Environment environment) {
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final NumberAtom number1 = (NumberAtom)args.car().eval(environment);
            final NumberAtom number2 = (NumberAtom)args.cadr().eval(environment);
            final NumberAtom result = number1.multiply(number2);
            return result;
        }

        public Atom eval(final Environment environment) {
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final NumberAtom number1 = (NumberAtom)args.car().eval(environment);
            final NumberAtom number2 = (NumberAtom)args.cadr().eval(environment);
            final NumberAtom result = number1.divide(number2);
            return result;
        }

        public Atom eval(final Environment environment) {
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final Atom leftSide = args.car();
            final NumberAtom leftSideValue = (NumberAtom)leftSide.eval(environment);
            final Atom rightSide = args.cadr();
            final NumberAtom rightSideValue = (NumberAtom)rightSide.eval(environment);
            final boolean isLessThan = leftSideValue.isLessThan(rightSideValue);
            final Atom resultAtom = BooleanAtom.getValue(isLessThan);
            return resultAtom;
        }

        public Atom eval(final Environment environment) {
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final Atom arg = args.car();
            final ConsCell argValue = (ConsCell)arg.eval(environment);
            final boolean isNullList = argValue == ConsCell.EmptyList;
            final Atom resultAtom = BooleanAtom.getValue(isNullList);
            return resultAtom;
        }

        public Atom eval(final Environment environment) {
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final Atom arg = args.car();
            final ConsCell argValue = (ConsCell)arg.eval(environment);
            final IntegerAtom integerAtom = argValue.length();
            return integerAtom;
        }

        public Atom eval(final Environment environment) {
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final Atom arg1 = args.car();
            final Atom argValue1 = arg1.eval(environment);

            final Atom arg2 = args.cadr();
            final Atom argValue2 = arg2.eval(environment);


            final boolean isEqual = argValue1.equals(argValue2);
            final Atom resultAtom = BooleanAtom.getValue(isEqual);
            return resultAtom;
        }

        public Atom eval(final Environment environment) {
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final Atom predicateExpression = args.car();
            final BooleanAtom booleanAtom = (BooleanAtom)predicateExpression.eval(environment);
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
            final Atom resultAtom = targetExpression.eval(environment);
            return resultAtom;
        }

        public Atom eval(final Environment environment) {
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

        public Atom apply(final ConsCell args, final Environment environment) {
            final Atom predicateExpression = args.car();
            final Atom trueBlock = args.cadr();
            Atom resultAtom = ConsCell.EmptyList;
            while (((BooleanAtom)predicateExpression.eval(environment)).isTrue()) {
                resultAtom = trueBlock.eval(environment);
            }
            return resultAtom;
        }

        public Atom eval(final Environment environment) {
            return this;
        }

        public void print(final PrintStream printStream) {
            printStream.print(Name);
        }
    }

    ///////////////////////
    // Util
    ///////////////////////

    /**
     * todo could use some junit tests for this
     * todo this ould become the environment itself and eliminate one level of indirection.
     * This is only a good idea for a very small number of key/value pairs.  Growth is slow (1 at a time), lookup
     * is linear.  Keys must be uniqued before using (this only uses == for comparison).
     * @param <K> key
     * @param <V> value
     */
    private static final class SmallMap<K, V> implements Map<K, V> {
        private Object[] _pairsArray;
        private int _size;
        // statics
        private static final int SlotsPerEntry = 2;
        private static final int NotFound = -1;

        private SmallMap(final int initialCapacity) {
            _pairsArray = new Object[initialCapacity * SlotsPerEntry];
            _size = 0;
        }

        private int _indexOfKey(final Object targetKey) {
            for (int index = 0, length = _size * SlotsPerEntry; index < length; index += SlotsPerEntry) {
                final Object key = _pairsArray[index];
                if (key == targetKey) {
                    return index;
                }
            }
            return NotFound;
        }

        @Override
        @SuppressWarnings("unchecked")
        public V get(final Object key) {
            final V value;
            final int indexOfKey = _indexOfKey(key);
            if (indexOfKey == NotFound) {
                value = null;
            }
            else {
                value = (V)_pairsArray[indexOfKey + 1];
            }
            return value;
        }

        private static Object[] _realloc(final Object[] array, final int newSize) {
            final Object[] newArray;
            final int oldSize = array.length;
            if (newSize <= oldSize) {
                newArray = array;
            }
            else {
                newArray = new Object[newSize];
                System.arraycopy(array, 0, newArray, 0, oldSize);
            }
            return newArray;
        }

        @Override
        public V put(final Object key, final Object value) {
            int indexOfKey = _indexOfKey(key);
            if (indexOfKey == NotFound) {
                indexOfKey = _size * SlotsPerEntry;
                _size += 1;
                _pairsArray = _realloc(_pairsArray, _size * SlotsPerEntry);
            }
            final int indexOfValue = indexOfKey + 1;
            @SuppressWarnings("unchecked")
            final V returnValue = (V)_pairsArray[indexOfValue];
            _pairsArray[indexOfKey] = key;
            _pairsArray[indexOfValue] = value;
            return returnValue;
        }

        @Override
        public int size() {
            return _size;
        }

        @Override
        public boolean isEmpty() {
            return _size == 0;
        }

        @Override
        public boolean containsKey(final Object key) {
            return _indexOfKey(key) != NotFound;
        }

        @Override
        public boolean containsValue(final Object targetValue) {
            for (int index = 1, length = _size * SlotsPerEntry; index < length; index += SlotsPerEntry) {
                final Object value = _pairsArray[index];
                if (value == targetValue || value != null && value.equals(targetValue)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Set<K> keySet() {
            final Set<K> keySet = new HashSet<K>();
            for (int index = 0, length = _size * SlotsPerEntry; index < length; index += SlotsPerEntry) {
                @SuppressWarnings("unchecked")
                final K key = (K)_pairsArray[index];
                keySet.add(key);
            }
            return keySet;
        }

        @Override
        public Collection<V> values() {
            final Collection<V> values = new ArrayList<V>();
            for (int index = 1, length = _size * SlotsPerEntry; index < length; index += SlotsPerEntry) {
                @SuppressWarnings("unchecked")
                final V value = (V)_pairsArray[index];
                values.add(value);
            }
            return values;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<Entry<K, V>> entrySet() {
            final Set<Entry<K, V>> entrySet = new HashSet<Entry<K, V>>();
            for (int index = 0, length = _size * SlotsPerEntry; index < length; index += SlotsPerEntry) {
                final K key = (K)_pairsArray[index];
                final V value = (V)_pairsArray[index + 1];
                final Entry<K, V> entry = new SimpleImmutableEntry<K, V>(key, value);
                entrySet.add(entry);
            }
            return entrySet;
        }

        @Override
        public V remove(final Object key) {
            throw new UnsupportedOperationException("Will never be supported");
        }

        @Override
        public void putAll(final Map map) {
            throw new UnsupportedOperationException("Currently not supported");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Currently not supported");
        }

    }
}
