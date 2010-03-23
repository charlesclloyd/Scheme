package pmilne;

import java.io.*;

/**
 * @version %I% %G%
 * @author Philip Milne
 */

public class SchemeTokenizer {
//    private InputStream input = null;  todo remove
    private Reader reader = null;

    private char buffer[] = new char[20];
    protected int nextchar;
    protected State state;

    private final State startState = new DefaultState();
    private final State tokenState = new DefaultState();
    private final State doubleState = new DefaultState();
    private final State floatState = new DefaultState();
    private final State intState = new DefaultState();
    private final State numberState = new DefaultState();
    private final State exponentState = new DefaultState();
    private final State signExponentState = new DefaultState();
    private final State stringState = new DefaultState();
    private final State escapeCharState = new DefaultState();
    private final State spaceState = new DefaultState();
    private final State specialCharState = new DefaultState();
    private final State signState = new DefaultState();

    public void init() {
        nextchar = -1;

        startState.addTransition(0, 255, specialCharState);
        startState.addTransition('#', tokenState);
        startState.addTransition('!', tokenState);
        startState.addTransition('=', tokenState);
        startState.addTransition('<', tokenState);
        startState.addTransition('>', tokenState);
        startState.addTransition('a', 'z', tokenState);
        startState.addTransition('A', 'Z', tokenState);
        startState.addTransition('0', '9', intState);
        startState.addTransition('+', signState);
        startState.addTransition('-', signState);
        startState.addTransition('\"', stringState);
        startState.addTransition(' ', spaceState);
        startState.addTransition('\t', spaceState);
        startState.addTransition('\n', spaceState);
        startState.addTransition('\r', spaceState);

        signState.addTransition('0', '9', intState);

        spaceState.addTransition(' ', spaceState);
        spaceState.addTransition('\t', spaceState);
        spaceState.addTransition('\n', spaceState);
        spaceState.addTransition('\r', spaceState);

        tokenState.addTransition('a', 'z', tokenState);
        tokenState.addTransition('A', 'Z', tokenState);
        tokenState.addTransition('0', '9', tokenState);
        tokenState.addTransition('?', tokenState);
        tokenState.addTransition('<', tokenState);
        tokenState.addTransition('+', tokenState);
        tokenState.addTransition('*', tokenState);
        tokenState.addTransition('/', tokenState);
        tokenState.addTransition('_', tokenState);
        tokenState.addTransition('-', tokenState);
        tokenState.addTransition('=', tokenState);
        tokenState.addTransition('!', tokenState);

        intState.addTransition('0', '9', intState);
        intState.addTransition('d', doubleState);
        intState.addTransition('f', floatState);
        intState.addTransition('e', signExponentState);
        intState.addTransition('.', numberState);

        numberState.addTransition('0', '9', numberState);
        numberState.addTransition('d', doubleState);
        numberState.addTransition('f', floatState);
        numberState.addTransition('e', signExponentState);

        signExponentState.addTransition('0', '9', exponentState);
        signExponentState.addTransition('+', exponentState);
        signExponentState.addTransition('-', exponentState);

        exponentState.addTransition('0', '9', exponentState);
        exponentState.addTransition('d', doubleState);
        exponentState.addTransition('f', floatState);

        stringState.addTransition(0, 255, stringState);
        stringState.addTransition('\"', specialCharState);
        stringState.addTransition('\\', escapeCharState);
        escapeCharState.addTransition(0, 255, stringState);
        state = startState;
    }

    public static interface State {
        public void addTransition(int startChar, int endChar, State toState);
        public void addTransition(int character, State toState);
        public State nextState(int character);
    }

    public static class DefaultState implements State {
        State[] states = new State[0];
        int lowestCharacter; // This will be initialised to the first entry.

        public void addTransition(final int startChar, final int endChar, final State toState) {
            // Do both ends first so that to avoid N^2
            // characteristics due to array copying.
            addTransition(endChar, toState);
            for(int i = startChar; i < endChar; i++) {
                addTransition(i, toState);
            }
        }

        // Beware of using System.out.println in these routines,
        // strange things happen.
        public void addTransition(final int character, final State toState) {
            final int n = states.length;
            if (n == 0) { // First addition
                lowestCharacter = character;
            }
            int c = character - lowestCharacter;
            if (c < 0) {
                final State[] newStates = new State[n - c];
                System.arraycopy(states, 0, newStates, -c, n);
                states = newStates;
                lowestCharacter = character;
                c = 0;
            }
            else if (c >= n) {
                final State[] newStates = new State[c + 1];
                System.arraycopy(states, 0, newStates, 0, n);
                states = newStates;
            }
            states[c] = toState;
        }

        public State nextState(final int character) {
            final int c = character - lowestCharacter;
            return (c >= 0 && c < states.length) ? states[c] : null;
        }
    }

    private int reallyRead() throws IOException {
        return reader.read();
//        /** Read the next character */ todo remove
//        if (reader != null) {
//            return reader.read();
//        }
//        else if (input != null) {
//            int val = input.read();
//            return val;
//        }
//        else {
//            throw new IllegalStateException();
//        }
    }

    private int read() throws IOException {
        if (nextchar != -1) {
            final int tmp = nextchar;
            nextchar = -1;
            return tmp;
        }
        return reallyRead();
    }

    private int peek() throws IOException {
        if (nextchar == -1) {
            nextchar = reallyRead();
        }
        return nextchar;
    }

//    public SchemeTokenizer(final InputStream input) {  todo remove
//        this.input = input;
//        init();
//    }

    public SchemeTokenizer(final Reader reader) {
        this.reader = reader;
        init();
    }

    public State getState() {
         return state;
    }

    public String readToken() throws IOException {
        State nextState = startState;
        int i = 0;
        while (nextState != null) {
            state = nextState;
            if (i >= buffer.length) {
                final char nb[] = new char[buffer.length * 2];
                System.arraycopy(buffer, 0, nb, 0, buffer.length);
                buffer = nb;
            }
            final int c = peek();
            buffer[i] = (char)c;
            nextState = state.nextState(c);
            if (nextState != null) {
                read();
            }
            i = i+1;
        }
        return String.copyValueOf(buffer, 0, i-1);
    }

    public boolean isInt ()
    {
        return state == intState;
    }

    public boolean isFloat ()
    {
        return state == floatState;
    }

    public boolean isNumber ()
    {
        return state == numberState;
    }

    public boolean isWhiteSpace ()
    {
        return state == spaceState;
    }
}
