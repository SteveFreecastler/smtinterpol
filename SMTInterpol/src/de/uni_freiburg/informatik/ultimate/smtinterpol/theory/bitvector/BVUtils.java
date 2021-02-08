package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.bitvector;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.uni_freiburg.informatik.ultimate.logic.Annotation;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.ConstantTerm;
import de.uni_freiburg.informatik.ultimate.logic.FunctionSymbol;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Theory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Clause;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.DPLLAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.DPLLAtom.TrueAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Literal;
import de.uni_freiburg.informatik.ultimate.smtinterpol.proof.IProofTracker;

public class BVUtils {
	private final Theory mTheory;

	public BVUtils(final Theory theory) {
		mTheory = theory;
	}

	public static String getConstAsString(final ConstantTerm ct) {
		if (ct.getSort().getName().equals("BitVec")) {
			String bitString;
			if (ct.getValue() instanceof String) {
				bitString = (String) ct.getValue();
				if (bitString.startsWith("#b")) {
					bitString = (String) ct.getValue();
					return bitString.substring(2);
				} else if (bitString.startsWith("#x")) { // TODO Value > maxrepnumbers
					final String number = new BigInteger(bitString.substring(2), 16).toString(2);
					final int size = Integer.valueOf(ct.getSort().getIndices()[0]);
					final String repeated = new String(new char[size - number.length()]).replace("\0", "0");
					return repeated + number;
				}
			} else if (ct.getValue() instanceof BigInteger) {
				final BigInteger big = (BigInteger) ct.getValue();
				bitString = big.toString(2);
				return bitString;
			}
		}
		throw new UnsupportedOperationException("Can't convert to bitstring: " + ct);
	}

	public boolean isConstRelation(final Term lhs, final Term rhs) {
		if ((lhs instanceof ConstantTerm)) {
			if (rhs == null) {
				return true;
			} else if (rhs instanceof ConstantTerm) {
				return true;
			}
		}
		return false;
	}

	/**
	 * nomralizaiton of bitvec equalities,
	 * elimintes concatinations with perfect match:
	 * a :: b = c :: d eliminated by a = c && c = d
	 *
	 * with a,c and b, d being of same size.
	 */
	public Term eliminateConcatPerfectMatch(final FunctionSymbol fsym, final Term[] params) {
		if (!fsym.getName().equals("=")) {
			throw new UnsupportedOperationException("unknown function symbol");
		}
		if (!params[0].getSort().getName().equals("BitVec")) {
			return null;
		}
		final List<Term> matchresult = new ArrayList<>();
		for (int j = 1; j <= params.length - 1; j++) {
			if (!((params[0] instanceof ApplicationTerm) && (params[j] instanceof ApplicationTerm))) {
				return null;
			}
			final ApplicationTerm aplhs = (ApplicationTerm) params[0];
			final ApplicationTerm aprhs = (ApplicationTerm) params[j];
			if (!(aplhs.getFunction().getName().equals("concat") && aprhs.getFunction().getName().equals("concat"))) {
				return null;
			}
			if (aplhs.getParameters()[0].getSort().getIndices()
					.equals(aprhs.getParameters()[0].getSort().getIndices())) {
				final Term matchConj1 = mTheory.term("=", aplhs.getParameters()[0], aprhs.getParameters()[0]);
				final Term matchConj2 = mTheory.term("=", aplhs.getParameters()[1], aprhs.getParameters()[1]);
				matchresult.add(matchConj1);
				matchresult.add(matchConj2);
			} else {
				return null;
			}
		}
		Term[] result = new Term[matchresult.size()];
		result = matchresult.toArray(result);
		return mTheory.and(result);
	}

	/**
	 * bvadd, bvudiv, bvmul
	 *
	 * @return
	 */
	public Term optimizeArithmetic(final FunctionSymbol fsym, final Term lhs, final Term rhs) {
		final BigInteger lhsInt = new BigInteger(getConstAsString((ConstantTerm) lhs), 2);
		final BigInteger rhsInt = new BigInteger(getConstAsString((ConstantTerm) rhs), 2);
		String calc;
		final int size = Integer.valueOf(lhs.getSort().getIndices()[0]);
		if (fsym.getName().equals("bvadd")) {
			calc = (lhsInt.add(rhsInt).toString(2));
		} else if (fsym.getName().equals("bvudiv")) {
			// truncated integer division
			if (!rhsInt.equals(BigInteger.ZERO)) {
				calc = (lhsInt.divide(rhsInt).toString(2));
			} else {
				final String repeated = new String(new char[size]).replace("\0", "1");
				calc = repeated;
			}
		} else if (fsym.getName().equals("bvurem")) {
			if (!rhsInt.equals(BigInteger.ZERO)) {
				calc = (lhsInt.remainder(rhsInt).toString(2));
			} else {
				// TODO cerstes argument lhsInt
				final String repeated = new String(new char[size]).replace("\0", "1");
				calc = repeated;
			}

		} else if (fsym.getName().equals("bvmul")) {
			calc = (lhsInt.multiply(rhsInt).toString(2));
		} else {
			throw new UnsupportedOperationException("unknown function symbol: " + fsym.getName());
		}

		final String repeated = new String(new char[size - calc.length()]).replace("\0", "0");
		final String resultconst = "#b" + repeated + calc;
		return mTheory.binary(resultconst);
	}

	/**
	 * bvand, bvor
	 *
	 * @return
	 */
	public Term optimizeLogical(final FunctionSymbol fsym, final Term lhs, final Term rhs) {
		String resultconst = "#b";
		final String constRHS = getConstAsString((ConstantTerm) lhs);
		final String constLHS = getConstAsString((ConstantTerm) rhs);
		for (int i = 0; i < constRHS.length(); i++) {
			final char first = constRHS.charAt(i);
			final char second = constLHS.charAt(i);
			if (fsym.getName().equals("bvand")) {
				if ((Character.compare(first, second) == 0) && (Character.compare(first, '1') == 0)) {
					resultconst = resultconst + "1";
				} else {
					resultconst = resultconst + "0";
				}
			} else if (fsym.getName().equals("bvor")) {
				if ((Character.compare(first, second) == 0) && (Character.compare(first, '0') == 0)) {
					resultconst = resultconst + "0";
				} else {
					resultconst = resultconst + "1";
				}
			} else {
				throw new UnsupportedOperationException("unknown function symbol: " + fsym.getName());
			}
		}
		return mTheory.binary(resultconst);
	}

	public Term optimizeConcat(final FunctionSymbol fsym, final Term lhs, final Term rhs) {
		if (!fsym.getName().equals("concat")) {
			throw new UnsupportedOperationException("unknown function symbol: " + fsym.getName());
		}
		final String result = "#b" + getConstAsString((ConstantTerm) lhs)
		.concat(getConstAsString((ConstantTerm) rhs));
		final Term concat = mTheory.binary(result);
		return concat;
	}

	/**
	 * bvshl, bvlshr
	 * Fill's with zero's
	 *
	 * @return
	 */
	public Term optimizeShift(final FunctionSymbol fsym, final Term lhs, final Term rhs) {
		String resultconst = "#b";
		final String lhsString = getConstAsString((ConstantTerm) lhs);
		final BigInteger rhsInt = new BigInteger(getConstAsString((ConstantTerm) rhs), 2);
		final BigInteger lhslenth = new BigInteger(String.valueOf(lhsString.length()));
		final int modRhs = rhsInt.mod(lhslenth).intValue();
		if (fsym.getName().equals("bvshl")) {
			// split lhsString at posi (rhsInt mod size), add overhead
			if (lhsString.length() >= modRhs) {
				// String overhead = lhsString.substring(0, modRhs);
				// TODO fill with overhead or Zero's
				final String repeated = new String(new char[modRhs]).replace("\0", "0");
				resultconst = resultconst + lhsString.substring(modRhs) + repeated;
			} else {
				throw new UnsupportedOperationException();
			}
		} else if (fsym.getName().equals("bvlshr")) {
			final String repeated = new String(new char[modRhs]).replace("\0", "0");
			resultconst = resultconst + repeated + lhsString.substring(0, modRhs);
		} else {
			throw new UnsupportedOperationException("unknown function symbol: " + fsym.getName());
		}
		return mTheory.binary(resultconst);
	}

	// TODO arithmetic negation of the given bitvector value.
	public Term optimizeNEG(final FunctionSymbol fsym, final Term term) {
		final String resultconst = "#b";
		final String termAsString = getConstAsString((ConstantTerm) term);
		if (fsym.getName().equals("bvneg")) {
			for (int i = 0; i < termAsString.length(); i++) {
				if (termAsString.charAt(termAsString.length() - 1 - i) == '1') {

				} else {

				}
			}
		} else {
			throw new UnsupportedOperationException("unknown function symbol: " + fsym.getName());
		}
		return mTheory.binary(resultconst);
	}

	// TODO bitwise negation of the given bitvector value
	public Term optimizeNOT(final FunctionSymbol fsym, final Term term) {
		String resultconst = "#b";
		final String termAsString = getConstAsString((ConstantTerm) term);
		if (fsym.getName().equals("bvnot")) {
			for (int i = 0; i < termAsString.length(); i++) {
				if (termAsString.charAt(termAsString.length() - 1 - i) == '1') {
					resultconst = resultconst + "0";
				} else {
					resultconst = resultconst + "1";
				}
			}
		} else {
			throw new UnsupportedOperationException("unknown function symbol: " + fsym.getName());
		}
		return mTheory.binary(resultconst);
	}

	public Term getProof(final Term optimized, final Term convertedApp, final IProofTracker tracker,
			final Annotation proofconst) {
		final Term lhs = tracker.getProvedTerm(convertedApp);
		final Term rewrite =
				tracker.buildRewrite(lhs, optimized, proofconst);
		// return mTracker.transitivity(mConvertedApp, rewrite);
		return tracker.intern(convertedApp, rewrite); // wenn in einem literal
	}

	/*
	 * (bvult s t) to (bvult (bvsub s t) 0)
	 */
	private Term normalizeBvult(final ApplicationTerm bvult) {
		final Theory theory = bvult.getTheory();
		final int size = Integer.valueOf(bvult.getParameters()[0].getSort().getIndices()[0]);
		final String repeated = new String(new char[size]).replace("\0", "0");
		final String zeroconst = "#b" + repeated;
		return theory.term("bvult", theory.term("bvsub", bvult.getParameters()),
				theory.binary(zeroconst));
	}

	/*
	 * brings every inequality in the form: (bvult (bvsub s t) 0)
	 */
	public Term getBvultTerm(final Term convert) {
		if (convert instanceof ApplicationTerm) {
			final ApplicationTerm appterm = (ApplicationTerm) convert;
			assert appterm.getParameters().length == 2;
			final int size = Integer.valueOf(appterm.getParameters()[0].getSort().getIndices()[0]);
			final FunctionSymbol fsym = appterm.getFunction();
			final Theory theory = convert.getTheory();
			if (fsym.isIntern()) {
				switch (fsym.getName()) {
				case "bvult": {
					return appterm;
				}
				case "bvslt": {
					final String[] asd = new String[2];
					asd[0] = "3";
					asd[1] = "0";
					// (_ extract i j)
					System.out.println(appterm.getParameters()[0].getSort().getIndices()[0]);
					final FunctionSymbol extract =
							mTheory.getFunctionWithResult("extract", asd, null, appterm.getParameters()[0].getSort());
					System.out.println(mTheory.term(extract, appterm.getParameters()[0]));
					final Term equiBvult = theory.or(theory.and(
							theory.term("=",
									theory.term(extract, appterm.getParameters()[0]),
									theory.binary("#b1")),
							theory.term("=",
									theory.term(extract, appterm.getParameters()[1]),
									theory.binary("#b0"))),
							theory.and(theory.term("=",
									theory.term(extract, appterm.getParameters()[0]),
									theory.term(extract, appterm.getParameters()[1]))),

							theory.term("bvult", appterm.getParameters()[0], appterm.getParameters()[1]));
					System.out.println(equiBvult);
					return equiBvult;
				}
				case "bvule": {
					// (bvule s t) abbreviates (or (bvult s t) (= s t))
					final Term bvult =
							theory.term("bvult", appterm.getParameters()[0], appterm.getParameters()[1]);
					return theory.or(bvult, theory.term("=", appterm.getParameters()[0], appterm.getParameters()[1]));
				}
				case "bvsle": {
					final String[] indices = new String[2];
					indices[0] = String.valueOf(size - 1);
					indices[1] = String.valueOf(size - 1);
					final FunctionSymbol extract = mTheory.getFunctionWithResult("extract", indices, null);

					final Term equiBvule = theory.or(
							theory.and(theory.term("=",
									theory.term(extract, appterm.getParameters()[0]),
									theory.binary("#b1")),
									theory.term("=",
											theory.term(extract, appterm.getParameters()[1]),
											theory.binary("#b0"))),
							theory.and(theory.term("=",
									theory.term(extract, appterm.getParameters()[0]),
									theory.term(extract, appterm.getParameters()[1]))),
							theory.term("bvule", appterm.getParameters()[0], appterm.getParameters()[1]));
					System.out.println(equiBvule);
					return equiBvule;
				}
				case "bvugt": {
					// (bvugt s t) abbreviates (bvult t s)
					return theory.term("bvult", appterm.getParameters()[1], appterm.getParameters()[0]);
				}
				case "bvsgt": {
					// (bvsgt s t) abbreviates (bvslt t s)
					return getBvultTerm(theory.term("bvslt", appterm.getParameters()[1], appterm.getParameters()[0]));
				}
				case "bvuge": {
					// (bvuge s t) abbreviates (or (bvult t s) (= s t))
					final Term bvult =
							theory.term("bvult", appterm.getParameters()[1], appterm.getParameters()[0]);
					return theory.or(bvult, theory.term("=", appterm.getParameters()[0], appterm.getParameters()[1]));
				}
				case "bvsge": {
					// (bvsge s t) abbreviates (bvsle t s)
					return getBvultTerm(theory.term("bvsle", appterm.getParameters()[1], appterm.getParameters()[0]));
				}
				default: {
					throw new UnsupportedOperationException("Not a Inequality function symbol: " + fsym.getName());
				}
				}
			}
		}
		throw new UnsupportedOperationException("Not a Inequality");
	}

	public Clause getClause(final Term term, final HashMap<Term, DPLLAtom> boolAtoms, final int stackLevel) {
		// System.out.println("to Clause: " + term);
		final ArrayList<Literal> clause = new ArrayList<>();
		if (term instanceof ApplicationTerm) {
			final ApplicationTerm appterm = (ApplicationTerm) term;
			final FunctionSymbol fsym = appterm.getFunction();
			if (fsym.getName().equals("not")) {
				clause.add(boolAtoms.get(appterm.getParameters()[0]).negate());
			} else if (fsym.getName().equals("or")) {
				for (int i = 0; i < appterm.getParameters().length; i++) {
					if (appterm.getParameters()[i] instanceof ApplicationTerm) {
						final ApplicationTerm disjunct = (ApplicationTerm) appterm.getParameters()[i];
						if (disjunct.getFunction().getName().equals("not")) {
							assert disjunct.getParameters()[0] instanceof TermVariable;
							clause.add(boolAtoms.get(disjunct.getParameters()[0]).negate());
						} else {
							throw new UnsupportedOperationException("Cannot convert to Clausel: " + term);
						}
					} else {
						clause.add(boolAtoms.get(appterm.getParameters()[i]));
					}
				}
			} else if (fsym.getName().equals("true")) {
				clause.add(new TrueAtom());
				// } else if (fsym.getName().equals("and")) {
				// for (final Term t : appterm.getParameters()) {
				// getClause(t, boolAtoms, stackLevel);
				// }
			} else {
				throw new UnsupportedOperationException("Cannot convert to Clausel: " + term);
			}
		} else {
			clause.add(boolAtoms.get(term));
		}
		return new Clause(clause.toArray(new Literal[clause.size()]), stackLevel);
	}

	/*
	 * When a constant occurs in a binary bitwise operation, it is
	 * rewritten into concatenations of maximal sequences of 0�s and 1�s. For example, the
	 * constant 00011101 is split as 000b :: 111b :: 0b :: 1b. Then, similar splitting is applied
	 * to the other term, and then the operator is evaluated. For instance,
	 * t8 AND 00011101
	 * is rewritten into 000b :: t[4:2] :: 0b :: t[0:0].
	 */
	public Term bitMaskElimination(final Term term) {
		final Term btiMask;
		final int[] indices = new int[2];
		indices[1] = -1;

		if (term instanceof ApplicationTerm) {
			System.out.println("AYAYA " + term);
			final ApplicationTerm appterm = (ApplicationTerm) term;
			final FunctionSymbol fsym = appterm.getFunction();
			final Term lhs = appterm.getParameters()[0];
			final Term rhs = appterm.getParameters()[1];
			if ((lhs instanceof ConstantTerm) && ((rhs instanceof TermVariable) || (rhs instanceof ApplicationTerm))) {
				final String lhsString = getConstAsString((ConstantTerm) lhs);
				System.out.println("split " + lhsString.split("0"));
				System.out.println("split " + lhsString.split("1"));
			} else if ((rhs instanceof ConstantTerm)
					&& ((lhs instanceof TermVariable) || (lhs instanceof ApplicationTerm))) {
				final String rhsString = getConstAsString((ConstantTerm) rhs);
				String zeros;
				for (int i = 0; i < rhsString.length(); i++){ //iterates from left to right
					final char ch = rhsString.charAt(i);
					if (ch == '0') {
						// zeros = zeros + ch;
						indices[0]  = rhsString.length() - i; //  + 1
						if(indices[0] <= indices[1]) {
							// indices to string array
							// final inal FunctionSymbol extract = mTheory.getFunctionWithResult("extract", indices,
							// null, BitVec sort);
							final Term select = mTheory.term("extract", term);
							// btiMask = mTheory.term("concat" , select,btiMask);
						}
					}else {
						// btiMask = mTheory.term("concat" , mTheory.binary(zeros),btiMask);
						zeros = "";
						indices[1] = rhsString.length() - i;
					}

				}
				// mTheory.term("concat",);

			}
		}

		// System.out.println(btiMask);
		return term;
	}

}
