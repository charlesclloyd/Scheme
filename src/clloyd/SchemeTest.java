package clloyd;

import clloyd.Scheme.FloatAtom;
import clloyd.Scheme.FunctionClosure;
import clloyd.Scheme.IntegerAtom;
import clloyd.Scheme.SchemeInterpreter;
import clloyd.Scheme.ConsCell;
import clloyd.Scheme.BooleanAtom;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Created by IntelliJ IDEA.
 * User: clloyd
 * Date: Mar 18, 2010
 * Time: 3:42:48 PM
 */
public class SchemeTest {
    @Test
    public void testMain() throws Exception {
        System.out.println("Running Scheme JUnit Tests");
        final SchemeInterpreter schemeInterpreter = new SchemeInterpreter();

        testBasicArithmatic(schemeInterpreter);
        testDefine(schemeInterpreter);
        testBegin(schemeInterpreter);
        testLambda(schemeInterpreter);
        testLambdaListArg(schemeInterpreter);
        testIf(schemeInterpreter);
        testQuote(schemeInterpreter);
        testCons(schemeInterpreter);
        testCdrExtensions(schemeInterpreter);
        testEmptyList(schemeInterpreter);
        testVariadicPlus(schemeInterpreter);
        testScopingAndClosures(schemeInterpreter);
        testLet(schemeInterpreter);
        testMap(schemeInterpreter);
        testEnvironmentCapture(schemeInterpreter);
        testFactorial(schemeInterpreter);
    }

    private void testBegin(final SchemeInterpreter schemeInterpreter) {
        // I have left this here for documentation.  This is how begin could be implemented in terms of lambda, but actually
        // now lambda is implemented in terms of begin.
        //schemeInterpreter.readAndEval("(define begin (lambda args (if (eq? 1 (length args)) (car args) (apply begin (cdr args)))))");

        IntegerAtom integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(begin 1 2 3)");
        Assert.assertEquals(3, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(begin 2 3 4 5 6)");
        Assert.assertEquals(6, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(begin (define begin-a 111) (define begin-b 222) (define begin-c 333))");
        Assert.assertEquals(333, integerAtom.intValue());
        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("begin-a");
        Assert.assertEquals(111, integerAtom.intValue());
        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("begin-b");
        Assert.assertEquals(222, integerAtom.intValue());
        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("begin-c");
        Assert.assertEquals(333, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(begin (set! begin-a 555) (set! begin-b 666) (set! begin-c 777) begin-b)");
        Assert.assertEquals(666, integerAtom.intValue());
        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("begin-a");
        Assert.assertEquals(555, integerAtom.intValue());
        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("begin-b");
        Assert.assertEquals(666, integerAtom.intValue());
        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("begin-c");
        Assert.assertEquals(777, integerAtom.intValue());
        // todo add fib test

    }

    private void testFactorial(final SchemeInterpreter schemeInterpreter) {
        schemeInterpreter.readAndEval("(define fact (lambda (x)\n" +
                "\t(if (b< x 1)\n" +
                "\t\t1\n" +
                "\t\t(b* x (fact (b- x 1))))))");
        IntegerAtom integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(fact 0)");
        Assert.assertEquals(1, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(fact 1)");
        Assert.assertEquals(1, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(fact 2)");
        Assert.assertEquals(2, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(fact 3)");
        Assert.assertEquals(6, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(fact 4)");
        Assert.assertEquals(24, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(fact 10)");
        Assert.assertEquals(3628800, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(fact -1)");
        Assert.assertEquals(1, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(fact -10)");
        Assert.assertEquals(1, integerAtom.intValue());
    }

    private void testEnvironmentCapture(final SchemeInterpreter schemeInterpreter) {
        schemeInterpreter.readAndEval("(define my-counter (let ((count 0)) (lambda () (set! count (b+ count 1)) count)))");
        IntegerAtom integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(my-counter)");
        Assert.assertEquals(1, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(my-counter)");
        Assert.assertEquals(2, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(my-counter)");
        Assert.assertEquals(3, integerAtom.intValue());


        schemeInterpreter.readAndEval("(define make-counter (lambda (init) (let ((count init)) (lambda () (set! count (b+ count 1)) count))))");
        schemeInterpreter.readAndEval("(define c5 (make-counter 5))");
        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(c5)");
        Assert.assertEquals(6, integerAtom.intValue());
        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(c5)");
        Assert.assertEquals(7, integerAtom.intValue());
        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(c5)");
        Assert.assertEquals(8, integerAtom.intValue());

        schemeInterpreter.readAndEval("(define c10 (make-counter 10))");
        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(c10)");
        Assert.assertEquals(11, integerAtom.intValue());
        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(c10)");
        Assert.assertEquals(12, integerAtom.intValue());
        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(c10)");
        Assert.assertEquals(13, integerAtom.intValue());

    }

    private void testMap(final SchemeInterpreter schemeInterpreter) {
        ConsCell consCell = (ConsCell)schemeInterpreter.readAndEval("(map (lambda (foo) (b+ 100 foo)) '(1 2 3 4))");
        String resultString = printToString(consCell);
        Assert.assertEquals("(101 102 103 104)", resultString);

        schemeInterpreter.readAndEval("(define bar (lambda  (foo) (b+ 300 foo)))");
        consCell = (ConsCell)schemeInterpreter.readAndEval("(map bar '(1 2 3 4))");
        resultString = printToString(consCell);
        Assert.assertEquals("(301 302 303 304)", resultString);

    }

    private void testLet(final SchemeInterpreter schemeInterpreter) {
        schemeInterpreter.readAndEval("(define foo 123)");
        IntegerAtom integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(let ((bar 5)) foo)");
        Assert.assertEquals(123, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(let ((foo 5)) foo)");
        Assert.assertEquals(5, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(let ((foo foo)) foo)");
        Assert.assertEquals(123, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(let ((foo (b+ 10 foo))) foo)");
        Assert.assertEquals(133, integerAtom.intValue());

    }

    private void testScopingAndClosures(final SchemeInterpreter schemeInterpreter) {
        schemeInterpreter.readAndEval("(define foo (lambda (foo) foo))");
        IntegerAtom integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(foo 1234)");
        Assert.assertEquals(1234, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(foo ((lambda (foo) (b+ 2 foo)) 3))");
        Assert.assertEquals(5,integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(foo ((lambda (foo) (b+ ((lambda (foo) (b* 2 foo)) 100) foo)) 10))");
        Assert.assertEquals(210,integerAtom.intValue());

    }

    private void testVariadicPlus(final SchemeInterpreter schemeInterpreter) {
        // type 1 -- a single arg which is a list
        schemeInterpreter.readAndEval("(define + (lambda (list) " +
                "    (if (null? list)" +
                "        0" +
                "        (b+ (car list) (+ (cdr list))))))");
        IntegerAtom integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(+ '(10))");
        Assert.assertEquals(10, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(+ '(6 7))");
        Assert.assertEquals(13, integerAtom.intValue());

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(+ '(2 3 4 5 6))");
        Assert.assertEquals(20, integerAtom.intValue());

        // type 2 -- many args which form a list
        schemeInterpreter.readAndEval("(define + (lambda list" +
                "    (if (null? list)" +
                "        0" +
                "        (b+ (car list) (apply + (cdr list))))))");
        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(+ 2 3 4 5 6)");
        Assert.assertEquals(20, integerAtom.intValue());

        // type 2 again but using length function rather than null?
        schemeInterpreter.readAndEval("(define + (lambda list" +
                "    (if (eq? 0 (length list))" +
                "        0" +
                "        (b+ (car list) (apply + (cdr list))))))");
        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(+ 2 3 4 5 6 7)");
        Assert.assertEquals(27, integerAtom.intValue());

    }

    private void testEmptyList(final SchemeInterpreter schemeInterpreter) {
        schemeInterpreter.readAndEval("(define el '())");
        final ConsCell consCell = (ConsCell)schemeInterpreter.readAndEval("el");
        Assert.assertEquals(0, consCell.length().intValue());

        BooleanAtom booleanAtom = (BooleanAtom)schemeInterpreter.readAndEval("(null? el)");
        Assert.assertTrue(booleanAtom.isTrue());

        booleanAtom = (BooleanAtom)schemeInterpreter.readAndEval("(null? (cdr '(1)))");
        Assert.assertTrue(booleanAtom.isTrue());

        booleanAtom = (BooleanAtom)schemeInterpreter.readAndEval("(null? (cdr (cdr (cdr '()))))");
        Assert.assertTrue(booleanAtom.isTrue());

        booleanAtom = (BooleanAtom)schemeInterpreter.readAndEval("(null? (car '()))");
        Assert.assertTrue(booleanAtom.isTrue());

    }

    private void testCdrExtensions(final SchemeInterpreter schemeInterpreter) {
        schemeInterpreter.readAndEval("(define cadr (lambda (list) (car (cdr list))))");
        schemeInterpreter.readAndEval("(define cddr (lambda (list) (cdr (cdr list))))");
        schemeInterpreter.readAndEval("(define caddr (lambda (list) (car (cdr (cdr list)))))");
        schemeInterpreter.readAndEval("(define alist '(1 2 3 4 5))");

        IntegerAtom integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(cadr alist)");
        Assert.assertEquals(2, integerAtom.intValue());

        final ConsCell consCell = (ConsCell)schemeInterpreter.readAndEval("(cddr alist)");
        final String resultString = printToString(consCell);
        Assert.assertEquals("(3 4 5)", resultString);

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(caddr alist)");
        Assert.assertEquals(3, integerAtom.intValue());
    }

    private void testCons(final SchemeInterpreter schemeInterpreter) {
        ConsCell consCell = (ConsCell)schemeInterpreter.readAndEval("(cons \"hello\" '(\"world\"))");
        String resultString = printToString(consCell);
        Assert.assertEquals("(\"hello\" \"world\")", resultString);

        schemeInterpreter.readAndEval("(define h \"hello\")");
        schemeInterpreter.readAndEval("(define w '(\"world\"))");
        schemeInterpreter.readAndEval("(define hw (cons h w))");
        consCell = (ConsCell)schemeInterpreter.readAndEval("hw");
        resultString = printToString(consCell);
        Assert.assertEquals("(\"hello\" \"world\")", resultString);
    }

    private void testQuote(final SchemeInterpreter schemeInterpreter) {
        schemeInterpreter.readAndEval("(define zz '(b+ 1 2))");
        final ConsCell consCell = (ConsCell)schemeInterpreter.readAndEval("zz");
        final String resultString = printToString(consCell);
        Assert.assertEquals("(b+ 1 2)", resultString);

    }

    private void testIf(final SchemeInterpreter schemeInterpreter) {
        BooleanAtom booleanAtom = (BooleanAtom)schemeInterpreter.readAndEval("(b< 3 4)");
        Assert.assertEquals(BooleanAtom.getValue(true), booleanAtom);

        booleanAtom = (BooleanAtom)schemeInterpreter.readAndEval("(b< 5 4)");
        Assert.assertEquals(BooleanAtom.getValue(false), booleanAtom);

        booleanAtom = (BooleanAtom)schemeInterpreter.readAndEval("(b< 4 4)");
        Assert.assertEquals(BooleanAtom.getValue(false), booleanAtom);

        IntegerAtom integerAtom = (IntegerAtom)schemeInterpreter.readAndEval(
                "(if (b< (b* 3 3) (b* 4 4)) " +
                    "(b+ 3 3) (b+ 4 4))");
        Assert.assertEquals(integerAtom.intValue(), 6);

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval(
                "(if (b< (b* 4 4) (b* 3 3)) " +
                    "(b+ 3 3) " +
                    "(b+ 4 4))");
        Assert.assertEquals(integerAtom.intValue(), 8);

        // tests without "else" expression        
        final ConsCell consCell = (ConsCell)schemeInterpreter.readAndEval(
                "(if (b< (b* 4 4) (b* 3 3)) " +
                    "(b+ 3 3))");
        Assert.assertTrue(consCell == ConsCell.EmptyList);

        integerAtom = (IntegerAtom)schemeInterpreter.readAndEval(
                "(if (b< (b* 3 3) (b* 4 4))" +
                    "(b+ 3 3))");
        Assert.assertEquals(integerAtom.intValue(), 6);

    }

    private void testLambdaListArg(final SchemeInterpreter schemeInterpreter) {
        // tests the var args list form of lambda.

        FunctionClosure functionClosure = (FunctionClosure)schemeInterpreter.readAndEval("(define foo (lambda (a b . c) c))");
        String resultString = printToString(functionClosure);
        // Note this actually ptints out incorrectly as the non-list form of body has a bug.
        Assert.assertEquals("(lambda (a b . c) (c))", resultString);

        schemeInterpreter.readAndEval("(foo 1 2 3 4 5 6)");
        schemeInterpreter.readAndEval("(foo 1 2 3 4 5 6)");
        schemeInterpreter.readAndEval("(foo 1 2 3 4 5 6)");
        ConsCell consCell = (ConsCell)schemeInterpreter.readAndEval("(foo 1 2 3 4 5 6)");
        resultString = printToString(consCell);
        Assert.assertEquals("(3 4 5 6)", resultString);

        functionClosure = (FunctionClosure)schemeInterpreter.readAndEval("(define bar (lambda a a))");
        resultString = printToString(functionClosure);
        Assert.assertEquals("(lambda a (a))", resultString);

        consCell = (ConsCell)schemeInterpreter.readAndEval("(bar 5 6 7 8 9)");
        resultString = printToString(consCell);
        Assert.assertEquals("(5 6 7 8 9)", resultString);

    }

    private void testLambda(final SchemeInterpreter schemeInterpreter) {
        final FunctionClosure functionClosure = (FunctionClosure)schemeInterpreter.readAndEval("(define foo (lambda (x y) x y (b+ x y) x))");
        final String resultString = printToString(functionClosure);
        Assert.assertEquals("(lambda (x y) (x y (b+ x y) x))", resultString);

        IntegerAtom resultIntegerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(foo 20 30)");
        Assert.assertEquals(20, resultIntegerAtom.intValue());

        // run this a couple more times to see what impact it has on the environmentSize: todo remove
        schemeInterpreter.readAndEval("(foo 20 30)");
        schemeInterpreter.readAndEval("(foo 20 30)");
        schemeInterpreter.readAndEval("(foo 20 30)");

        schemeInterpreter.readAndEval("(define foo (lambda (x y) x y (set! x (b+ x y)) x))");

        resultIntegerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(foo 20 30)");
        Assert.assertEquals(50, resultIntegerAtom.intValue());

        schemeInterpreter.readAndEval("(define plus (lambda (x y) (b+ x y)))");
        schemeInterpreter.readAndEval("(define x 3)");
        schemeInterpreter.readAndEval("(define y 5)");
        resultIntegerAtom = (IntegerAtom)schemeInterpreter.readAndEval("(plus (plus x 20) (plus y 100))");
        Assert.assertEquals(128, resultIntegerAtom.intValue());
    }

    private void testDefine(final SchemeInterpreter schemeInterpreter) {
        IntegerAtom intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(define xx 5)");
        Assert.assertEquals(5, intAtom.intValue());

        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("xx");
        Assert.assertEquals(5, intAtom.intValue());

        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(define yy xx)");
        Assert.assertEquals(5, intAtom.intValue());

        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("yy");
        Assert.assertEquals(5, intAtom.intValue());

        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(define yy (b+ 10 20))");
        Assert.assertEquals(30, intAtom.intValue());

        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("yy");
        Assert.assertEquals(30, intAtom.intValue());

        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(b+ xx yy)");
        Assert.assertEquals(35, intAtom.intValue());

        // internal define
        schemeInterpreter.readAndEval("(define foo (lambda (foo) foo (define x foo) x))");
        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(foo 21)");
        Assert.assertEquals(21, intAtom.intValue());

        schemeInterpreter.readAndEval("(define y 0)");
        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("y");
        Assert.assertEquals(0, intAtom.intValue());
        schemeInterpreter.readAndEval("(define foo (lambda (foo) foo (define x foo) (set! x 22) (set! y x) x))");
        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(foo 21)");
        Assert.assertEquals(22, intAtom.intValue());
        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("y");
        Assert.assertEquals(22, intAtom.intValue());

        schemeInterpreter.readAndEval("(define x 20)");
        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(foo 21)");
        Assert.assertEquals(22, intAtom.intValue());
        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("x");
        Assert.assertEquals(20, intAtom.intValue());

    }

    private void testBasicArithmatic(final SchemeInterpreter schemeInterpreter) {
        IntegerAtom intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(b+ 2 3)");
        Assert.assertEquals(5, intAtom.intValue());

        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(b- 2 5)");
        Assert.assertEquals(-3, intAtom.intValue());

        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(b- 5 500)");
        Assert.assertEquals(-495, intAtom.intValue());

        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(b- 500 5)");
        Assert.assertEquals(495, intAtom.intValue());

        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(b* 2 5)");
        Assert.assertEquals(10, intAtom.intValue());

        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(b/ 20 5)");
        Assert.assertEquals(4, intAtom.intValue());

        // test edges of the Integer cache
        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(b+ -130 1)");
        Assert.assertEquals(-129, intAtom.intValue());
        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(b+ -130 2)");
        Assert.assertEquals(-128, intAtom.intValue());
        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(b+ -130 3)");
        Assert.assertEquals(-127, intAtom.intValue());

        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(b+ 125 1)");
        Assert.assertEquals(126, intAtom.intValue());
        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(b+ 125 2)");
        Assert.assertEquals(127, intAtom.intValue());
        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(b+ 125 3)");
        Assert.assertEquals(128, intAtom.intValue());
        intAtom = (IntegerAtom)schemeInterpreter.readAndEval("(b+ 125 4)");
        Assert.assertEquals(129, intAtom.intValue());

        // test some float stuff
        FloatAtom floatAtom = (FloatAtom)schemeInterpreter.readAndEval("(b+ 2.0 3)");
        Assert.assertEquals(5.0, floatAtom.floatValue(), 0.0);
        floatAtom = (FloatAtom)schemeInterpreter.readAndEval("(b+ 2.1 3)");
        Assert.assertEquals(5.1, floatAtom.floatValue(), 0.01);
        floatAtom = (FloatAtom)schemeInterpreter.readAndEval("(b- 2.3 3)");
        Assert.assertEquals(-0.7, floatAtom.floatValue(), 0.01);
        floatAtom = (FloatAtom)schemeInterpreter.readAndEval("(b* 2.1 3)");
        Assert.assertEquals(6.3, floatAtom.floatValue(), 0.01);
        floatAtom = (FloatAtom)schemeInterpreter.readAndEval("(b/ 9.3 3)");
        Assert.assertEquals(3.1, floatAtom.floatValue(), 0.01);
    }

    ////////////////////////
    // Util
    ////////////////////////
    private static String printToString(final Scheme.Atom atom) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArrayOutputStream);
        atom.print(printStream);
        final String resultString = byteArrayOutputStream.toString();
        return resultString;
    }
}
