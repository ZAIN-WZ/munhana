import com.munhana.graph.math.Equation;
import com.munhana.graph.math.EquationText;
import com.munhana.graph.math.Expression;

import java.util.List;

/** Desktop sanity checks for the pure-Java maths engine. */
public class MathTest {

    static int pass = 0, fail = 0;

    static void check(String label, boolean ok, Object detail) {
        if (ok) { pass++; System.out.println("ok   " + label); }
        else { fail++; System.out.println("FAIL " + label + "  -> " + detail); }
    }

    static double eval(String expr, double x, double y) throws Exception {
        return Expression.parse(expr).eval(x, y);
    }

    public static void main(String[] args) throws Exception {
        check("x^2-4 at 3", Math.abs(eval("x^2-4", 3, 0) - 5) < 1e-9, eval("x^2-4", 3, 0));
        check("implicit 2x", Math.abs(eval("2x", 4, 0) - 8) < 1e-9, eval("2x", 4, 0));
        check("3 sin(x)", Math.abs(eval("3 sin(x)", Math.PI / 2, 0) - 3) < 1e-9, eval("3 sin(x)", Math.PI / 2, 0));
        check("sinx implicit", Math.abs(eval("sinx", Math.PI / 2, 0) - 1) < 1e-9, eval("sinx", 0, 0));
        check("unary minus power", Math.abs(eval("-x^2", 3, 0) + 9) < 1e-9, eval("-x^2", 3, 0));
        check("right assoc power", Math.abs(eval("2^3^2", 0, 0) - 512) < 1e-9, eval("2^3^2", 0, 0));
        check("sqrt", Math.abs(eval("sqrt(16)", 0, 0) - 4) < 1e-9, eval("sqrt(16)", 0, 0));
        check("abs bars", Math.abs(eval("|-5|", 0, 0) - 5) < 1e-9, eval("|-5|", 0, 0));
        check("pi", Math.abs(eval("pi", 0, 0) - Math.PI) < 1e-9, eval("pi", 0, 0));
        check("division by zero", Double.isInfinite(eval("1/x", 0, 0)), eval("1/x", 0, 0));
        check("factorial", Math.abs(eval("5!", 0, 0) - 120) < 1e-9, eval("5!", 0, 0));

        // Extended function library
        check("mean list", Math.abs(eval("mean([1,2,3,4])", 0, 0) - 2.5) < 1e-9, eval("mean([1,2,3,4])", 0, 0));
        check("median even", Math.abs(eval("median([1,2,3,4])", 0, 0) - 2.5) < 1e-9, eval("median([1,2,3,4])", 0, 0));
        check("stdev sample", Math.abs(eval("stdev([1,2,3,4])", 0, 0) - Math.sqrt(5.0 / 3)) < 1e-9,
                eval("stdev([1,2,3,4])", 0, 0));
        check("stdevp population", Math.abs(eval("stdevp([1,2,3,4])", 0, 0) - Math.sqrt(1.25)) < 1e-9,
                eval("stdevp([1,2,3,4])", 0, 0));
        check("percentile 50", Math.abs(eval("percentile([1,2,3,4],50)", 0, 0) - 2.5) < 1e-9,
                eval("percentile([1,2,3,4],50)", 0, 0));
        check("corr perfect", Math.abs(eval("corr([1,2,3],[2,4,6])", 0, 0) - 1) < 1e-9,
                eval("corr([1,2,3],[2,4,6])", 0, 0));
        check("nCr", Math.abs(eval("nCr(10,3)", 0, 0) - 120) < 1e-9, eval("nCr(10,3)", 0, 0));
        check("nPr", Math.abs(eval("nPr(10,3)", 0, 0) - 720) < 1e-9, eval("nPr(10,3)", 0, 0));
        check("gcd", Math.abs(eval("gcd(12,18)", 0, 0) - 6) < 1e-9, eval("gcd(12,18)", 0, 0));
        check("lcm", Math.abs(eval("lcm(4,6)", 0, 0) - 12) < 1e-9, eval("lcm(4,6)", 0, 0));
        check("mod", Math.abs(eval("mod(7,3)", 0, 0) - 1) < 1e-9, eval("mod(7,3)", 0, 0));
        check("nthroot", Math.abs(eval("nthroot(27,3)", 0, 0) - 3) < 1e-9, eval("nthroot(27,3)", 0, 0));
        check("gamma", Math.abs(eval("gamma(5)", 0, 0) - 24) < 1e-6, eval("gamma(5)", 0, 0));
        check("sinc 0", Math.abs(eval("sinc(0)", 0, 0) - 1) < 1e-9, eval("sinc(0)", 0, 0));
        check("derivative at x", Math.abs(eval("nderiv(x^3,x)", 2, 0) - 12) < 1e-3,
                eval("nderiv(x^3,x)", 2, 0));
        check("integral sin", Math.abs(eval("integral(sin(x),0)", Math.PI, 0) - 2) < 1e-6,
                eval("integral(sin(x),0)", Math.PI, 0));
        Expression.setDegrees(true);
        check("sin degrees", Math.abs(eval("sin(90)", 0, 0) - 1) < 1e-9, eval("sin(90)", 0, 0));
        check("asin degrees", Math.abs(eval("asin(1)", 0, 0) - 90) < 1e-6, eval("asin(1)", 0, 0));
        Expression.setDegrees(false);
        check("radians restored", Math.abs(eval("sin(pi/2)", 0, 0) - 1) < 1e-9, eval("sin(pi/2)", 0, 0));

        Equation parabola = Equation.parse("y = x^2 - 4");
        check("y= kind", parabola.kind == Equation.Kind.FUNCTION, parabola.kind);
        check("y= value", Math.abs(parabola.fn.eval(3, 0) - 5) < 1e-9, parabola.fn.eval(3, 0));

        Equation circle = Equation.parse("x^2 + y^2 = 25");
        check("circle kind", circle.kind == Equation.Kind.IMPLICIT, circle.kind);
        check("circle on curve", Math.abs(circle.g.eval(3, 4)) < 1e-9, circle.g.eval(3, 4));
        check("circle inside", circle.g.eval(0, 0) < 0, circle.g.eval(0, 0));

        Equation vertical = Equation.parse("x = 2");
        check("vertical kind", vertical.kind == Equation.Kind.VERTICAL, vertical.kind);

        Equation ineq = Equation.parse("y > x^2");
        check("inequality kind", ineq.kind == Equation.Kind.INEQUALITY, ineq.kind);
        check("inequality relation", ">".equals(ineq.relation), ineq.relation);

        Equation bare = Equation.parse("x^2 + 1");
        check("bare expression becomes y=", bare.kind == Equation.Kind.FUNCTION, bare.kind);

        Equation fOfX = Equation.parse("f(x) = 2x + 1");
        check("f(x) form", fOfX.kind == Equation.Kind.FUNCTION, fOfX.kind);
        check("f(x) value", Math.abs(fOfX.fn.eval(3, 0) - 7) < 1e-9, fOfX.fn.eval(3, 0));

        check("no list-marker damage", Equation.parse("1.5x = 3").text.contains("1.5"),
                Equation.parse("1.5x = 3").text);

        // OCR repairs
        check("superscript 2", "y=x^2-4".equals(EquationText.repair("y = x\u00B2 - 4")), EquationText.repair("y = x\u00B2 - 4"));
        check("arabic digits", "3x+1=7".equals(EquationText.repair("\u0663x+\u0661=\u0667")),
                EquationText.repair("\u0663x+\u0661=\u0667"));
        check("times sign", EquationText.repair("2\u00D73=6") != null, EquationText.repair("2\u00D73=6"));
        check("arabic prose rejected", EquationText.repair("\u0627\u0644\u0645\u0639\u0627\u062F\u0644\u0629 \u0627\u0644\u0623\u0648\u0644\u0649") == null,
                EquationText.repair("\u0627\u0644\u0645\u0639\u0627\u062F\u0644\u0629 \u0627\u0644\u0623\u0648\u0644\u0649"));
        check("word rejected", EquationText.repair("graphing calculator") == null,
                EquationText.repair("graphing calculator"));
        check("page number rejected", EquationText.repair("12") == null, EquationText.repair("12"));

        List<String> many = EquationText.fromOcrText(
                "1) y = x^2 + 2x - 3\n2) y = 1/x\nsome caption text here\nx^2 + y^2 = 9");
        check("multi line count", many.size() == 3, many);
        System.out.println("     extracted: " + many);

        System.out.println();
        System.out.println(fail == 0 ? "ALL TESTS PASSED (" + pass + ")" : fail + " FAILED / " + pass + " passed");
        if (fail > 0) System.exit(1);
    }
}
