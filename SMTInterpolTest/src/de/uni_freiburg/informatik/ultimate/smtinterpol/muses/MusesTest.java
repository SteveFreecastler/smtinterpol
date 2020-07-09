package de.uni_freiburg.informatik.ultimate.smtinterpol.muses;

import java.util.ArrayList;
import java.util.BitSet;

import org.junit.Assert;
import org.junit.Test;

import de.uni_freiburg.informatik.ultimate.logic.AnnotatedTerm;
import de.uni_freiburg.informatik.ultimate.logic.Annotation;
import de.uni_freiburg.informatik.ultimate.logic.Logics;
import de.uni_freiburg.informatik.ultimate.logic.SMTLIBException;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.smtinterpol.smtlib2.SMTInterpol;

/**
 * Tests for everything that has to do with MUSes.
 *
 * @author LeonardFichtner
 *
 */
public class MusesTest {

	private Script setupScript(final Logics logic) {
		final Script script = new SMTInterpol();
		script.setOption(":produce-models", true);
		script.setOption(":produce-proofs", true);
		script.setOption(":interactive-mode", true);
		script.setOption(":produce-unsat-cores", true);
		script.setLogic(logic);
		return script;
	}

	/**
	 * Note that the constraints should be declared in the solver, but no constraints should be asserted!
	 */
	private void checkWhetherSetIsMus(final BitSet supposedMus, final ConstraintAdministrationSolver solver) {
		checkUnsat(supposedMus, solver);
		checkMinimality(supposedMus, solver);
	}

	private void checkUnsat(final BitSet supposedMus, final ConstraintAdministrationSolver solver) {
		solver.pushRecLevel();
		for (int i = supposedMus.nextSetBit(0); i >= 0; i = supposedMus.nextSetBit(i + 1)) {
			solver.assertCriticalConstraint(i);
		}
		Assert.assertTrue(solver.checkSat() == LBool.UNSAT);
		solver.popRecLevel();
	}

	private void checkMinimality(final BitSet supposedMus, final ConstraintAdministrationSolver solver) {
		solver.pushRecLevel();
		for (int i = supposedMus.nextSetBit(0); i >= 0; i = supposedMus.nextSetBit(i + 1)) {
			solver.pushRecLevel();
			for (int j = supposedMus.nextSetBit(0); j >= 0; j = supposedMus.nextSetBit(j + 1)) {
				if (i == j) {
					continue;
				}
				solver.assertCriticalConstraint(i);
			}
			Assert.assertTrue(solver.checkSat() == LBool.SAT);
			solver.popRecLevel();
		}
		solver.popRecLevel();
	}

	private ConstraintAdministrationSolver setupUnsatSet1(final Script script) {
		final ArrayList<String> names = new ArrayList<>();
		final ArrayList<Annotation> annots = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			names.add("c" + String.valueOf(i));
		}
		for (int i = 0; i < names.size(); i++) {
			annots.add(new Annotation(":named", names.get(i)));
		}
		final Sort intSort = script.sort("Int");
		script.declareFun("x", Script.EMPTY_SORT_ARRAY, intSort);
		script.declareFun("y", Script.EMPTY_SORT_ARRAY, intSort);
		script.declareFun("z", Script.EMPTY_SORT_ARRAY, intSort);
		final Term x = script.term("x");
		final Term y = script.term("y");
		final Term z = script.term("z");
		final Term c0 = script.term(">=", x, script.numeral("30"));
		final Term c1 = script.term(">=", x, script.numeral("101"));
		final Term c2 = script.term("<", x, z);
		final Term c3 = script.term("<=", z, script.numeral("101"));
		final Term c4 = script.term("=", y, script.numeral("2"));
		final Translator translator = new Translator();
		declareConstraint(script, translator, c0, annots.get(0));
		declareConstraint(script, translator, c1, annots.get(1));
		declareConstraint(script, translator, c2, annots.get(2));
		declareConstraint(script, translator, c3, annots.get(3));
		declareConstraint(script, translator, c4, annots.get(4));
		return new ConstraintAdministrationSolver(script, translator);
	}

	private ConstraintAdministrationSolver setupUnsatSet2(final Script script) {
		final ArrayList<String> names = new ArrayList<>();
		final ArrayList<Annotation> annots = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			names.add("c" + String.valueOf(i));
		}
		for (int i = 0; i < names.size(); i++) {
			annots.add(new Annotation(":named", names.get(i)));
		}
		final Sort intSort = script.sort("Int");
		script.declareFun("x", Script.EMPTY_SORT_ARRAY, intSort);
		script.declareFun("y", Script.EMPTY_SORT_ARRAY, intSort);
		script.declareFun("z", Script.EMPTY_SORT_ARRAY, intSort);
		final Term x = script.term("x");
		final Term y = script.term("y");
		final Term z = script.term("z");
		final Term c0 = script.term("=", x, script.numeral("53"));
		final Term c1 = script.term(">", x, script.numeral("23"));
		final Term c2 = script.term("<", x, z);
		final Term c3 = script.term("=", x, script.numeral("535"));
		final Term c4 = script.term("=", y, script.numeral("1234"));
		final Term c5 = script.term("<=", z, script.numeral("23"));
		final Term c6 = script.term(">=", x, script.numeral("34"));
		final Term c7 = script.term("=", y, script.numeral("4321"));
		final Term c8 = script.term("=", x, script.numeral("23"));
		final Term c9 = script.term("=", x, script.numeral("5353"));
		final Translator translator = new Translator();
		declareConstraint(script, translator, c0, annots.get(0));
		declareConstraint(script, translator, c1, annots.get(1));
		declareConstraint(script, translator, c2, annots.get(2));
		declareConstraint(script, translator, c3, annots.get(3));
		declareConstraint(script, translator, c4, annots.get(4));
		declareConstraint(script, translator, c5, annots.get(5));
		declareConstraint(script, translator, c6, annots.get(6));
		declareConstraint(script, translator, c7, annots.get(7));
		declareConstraint(script, translator, c8, annots.get(8));
		declareConstraint(script, translator, c9, annots.get(9));
		return new ConstraintAdministrationSolver(script, translator);
	}

	private void declareConstraint(final Script script, final Translator translator, final Term constraint,
			final Annotation... annotation) throws SMTLIBException {
		final AnnotatedTerm annotatedConstraint = (AnnotatedTerm) script.annotate(constraint, annotation);
		translator.declareConstraint(annotatedConstraint);
	}

	@Test
	public void testShrinkerNormal() {
		final Script script = setupScript(Logics.ALL);
		final ConstraintAdministrationSolver solver =	setupUnsatSet2(script);
		final BitSet workingSet = new BitSet();
		workingSet.flip(0, 10);
		final MusContainer container = Shrinking.shrinkWithoutMap(solver, workingSet);
		System.out.println("Shrinker returned: " + container.getMus().toString());
		checkWhetherSetIsMus(container.getMus(), solver);
	}

	@Test
	public void testShrinkerRestrictedSet() {
		final Script script = setupScript(Logics.ALL);
		final ConstraintAdministrationSolver solver = setupUnsatSet2(script);
		final BitSet workingSet = new BitSet();
		workingSet.set(0);
		workingSet.set(1);
		workingSet.set(3);
		workingSet.set(8);
		workingSet.set(9);
		workingSet.flip(0, 10);
		final MusContainer container = Shrinking.shrinkWithoutMap(solver, workingSet);
		System.out.println("Shrinker returned: " + container.getMus().toString());
		checkWhetherSetIsMus(container.getMus(), solver);
	}

	@Test
	public void testShrinkerWorkingSetIsMus() {
		final Script script = setupScript(Logics.ALL);
		final ConstraintAdministrationSolver solver = setupUnsatSet2(script);
		final BitSet workingSet = new BitSet();
		workingSet.set(1);
		workingSet.set(2);
		workingSet.set(5);
		final MusContainer container = Shrinking.shrinkWithoutMap(solver, workingSet);
		System.out.println("Shrinker returned: " + container.getMus().toString());
		checkWhetherSetIsMus(container.getMus(), solver);
	}

	@Test
	public void testShrinkerMusAssertedBefore() {
		final Script script = setupScript(Logics.ALL);
		final ConstraintAdministrationSolver solver = setupUnsatSet2(script);
		solver.pushRecLevel();
		solver.assertCriticalConstraint(4);
		solver.assertCriticalConstraint(7);
		final BitSet workingSet = new BitSet();
		workingSet.set(1);
		workingSet.set(2);
		workingSet.set(4);
		workingSet.set(7);
		final MusContainer container = Shrinking.shrinkWithoutMap(solver, workingSet);
		System.out.println("Shrinker returned: " + container.getMus().toString());
		Assert.assertTrue(solver.checkSat() == LBool.UNSAT);
		solver.popRecLevel();
		checkWhetherSetIsMus(container.getMus(), solver);
	}

	@Test
	public void testShrinkerSatSetAssertedBefore() {
		final Script script = setupScript(Logics.ALL);
		final ConstraintAdministrationSolver solver = setupUnsatSet2(script);
		solver.pushRecLevel();
		solver.assertCriticalConstraint(1);
		solver.assertCriticalConstraint(2);
		final BitSet workingSet = new BitSet();
		workingSet.set(5);
		workingSet.set(1);
		workingSet.set(2);
		final MusContainer container = Shrinking.shrinkWithoutMap(solver, workingSet);
		System.out.println("Shrinker returned: " + container.getMus().toString());
		solver.popRecLevel();
		checkWhetherSetIsMus(container.getMus(), solver);
		Assert.assertTrue(solver.checkSat() == LBool.SAT);
	}

	@Test(expected = SMTLIBException.class)
	public void testShrinkerWorkingSetDoesNotContainCrits() {
		final Script script = setupScript(Logics.ALL);
		final ConstraintAdministrationSolver solver = setupUnsatSet2(script);
		solver.pushRecLevel();
		solver.assertCriticalConstraint(1);
		solver.assertCriticalConstraint(2);
		final BitSet workingSet = new BitSet();
		workingSet.set(5);
		final MusContainer container = Shrinking.shrinkWithoutMap(solver, workingSet);
		System.out.println("Shrinker returned: " + container.getMus().toString());
		solver.popRecLevel();
		checkWhetherSetIsMus(container.getMus(), solver);
		Assert.assertTrue(solver.checkSat() == LBool.SAT);
	}

	@Test(expected = SMTLIBException.class)
	public void testShrinkerEmptySet() {
		final Script script = setupScript(Logics.ALL);
		final ConstraintAdministrationSolver solver = setupUnsatSet2(script);
		final BitSet workingSet = new BitSet();
		Shrinking.shrinkWithoutMap(solver, workingSet);
	}

	@Test(expected = SMTLIBException.class)
	public void testShrinkerSatSet() {
		final Script script = setupScript(Logics.ALL);
		final ConstraintAdministrationSolver solver = setupUnsatSet2(script);
		final BitSet workingSet = new BitSet();
		workingSet.set(0);
		workingSet.set(1);
		workingSet.set(7);
		workingSet.set(5);
		Shrinking.shrinkWithoutMap(solver, workingSet);
	}

	@Test
	public void testExtensionLightDemand() {
		final Script script = setupScript(Logics.ALL);
		final ConstraintAdministrationSolver solver = setupUnsatSet1(script);
		solver.assertUnknownConstraint(1);
		final BitSet extension = solver.getSatExtension();
		System.out.println(extension.toString());
		Assert.assertTrue(extension.get(0));
	}

	@Test
	public void testExtensionMediumDemand() {
		final Script script = setupScript(Logics.ALL);
		final ConstraintAdministrationSolver solver = setupUnsatSet1(script);
		final BitSet extension = solver.getSatExtensionMoreDemanding();
		System.out.println(extension.toString());
		Assert.assertTrue(extension.get(0));
		Assert.assertTrue(extension.get(1));
		Assert.assertTrue(extension.get(2));
		Assert.assertFalse(extension.get(3));
		Assert.assertFalse(extension.get(4));
	}

	@Test
	public void testExtensionHeavyDemand() {
		final Script script = setupScript(Logics.ALL);
		final ConstraintAdministrationSolver solver = setupUnsatSet1(script);
		final BitSet extension = solver.getSatExtensionMaximalDemanding();
		System.out.println(extension.toString());
		Assert.assertTrue(extension.get(0));
		Assert.assertTrue(extension.get(1));
		Assert.assertTrue(extension.get(2));
		Assert.assertFalse(extension.get(3));
		Assert.assertTrue(extension.get(4));
	}
}