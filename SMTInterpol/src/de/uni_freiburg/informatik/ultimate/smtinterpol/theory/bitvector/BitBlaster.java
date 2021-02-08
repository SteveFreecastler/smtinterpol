package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.bitvector;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.ConstantTerm;
import de.uni_freiburg.informatik.ultimate.logic.FunctionSymbol;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Theory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.BooleanVarAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Clause;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.DPLLAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Literal;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.util.Pair;
import de.uni_freiburg.informatik.ultimate.util.datastructures.ScopedArrayList;

public class BitBlaster {

	private final Theory mTheory;
	private final ScopedArrayList<Literal> mLiterals;
	private final LinkedHashSet<Term> mAllTerms;
	private final HashMap<String, TermVariable> mVarPrefix;
	private final HashMap<Term, DPLLAtom> mBoolAtoms;
	private final ScopedArrayList<Clause> mClauses;
	private final HashMap<Term, Term[]> mEncTerms; // Term[0] is the least bit, the most right bit of Term
	private final HashMap<String, Term> mEncAtoms;
	private final BVUtils mBVUtils;
	private final int mStackLevel;

	public BitBlaster(final Theory theory, final int engineStackLevel, final ScopedArrayList<Literal> allLiterals,
			final LinkedHashSet<Term> allTerms) {
		mTheory = theory;
		mLiterals = allLiterals;
		mAllTerms = allTerms;
		mVarPrefix = new HashMap<>();
		mEncTerms = new HashMap<>();
		mEncAtoms = new HashMap<>();
		mBVUtils = new BVUtils(mTheory);
		mBoolAtoms = new HashMap<>();
		mClauses = new ScopedArrayList<>();
		mStackLevel = engineStackLevel;
	}

	public Term bitBlasting() {
		Term equisatProp;
		final Term[] propSkeleton = new Term[mLiterals.size()];
		for (int i = 0; i < mLiterals.size(); i++) {
			// TODO atom merken f�r zur�ck �bersetztung
			final String atomPrefix = "At_" + i;
			final TermVariable boolVar = mTheory.createFreshTermVariable(atomPrefix, mTheory.getSort("Bool"));
			mBoolAtoms.put(boolVar, new BooleanVarAtom(boolVar, mStackLevel));
			mEncAtoms.put(atomPrefix, boolVar);
			if (mLiterals.get(i).getSign() == -1) {
				propSkeleton[i] = mTheory.not(boolVar);
			} else {
				propSkeleton[i] = boolVar;
			}
			mVarPrefix.put(atomPrefix, boolVar);
		}
		for (final Term term : mAllTerms) {
			// e(t), t in terms. Terms Size long Array of bool vars with e(t)_i being var at position i
			mEncTerms.put(term, getEncodedTerm(term));
		}
		// Initial propositional configuration
		equisatProp = mTheory.and(propSkeleton);
		for (final Term t : propSkeleton) {
			addClause(t);
		}

		// add BVConstraint of Atoms as conjunct
		for (int i = 0; i < mLiterals.size(); i++) {
			final DPLLAtom atom = mLiterals.get(i).getAtom();
			final Term encAtom = mEncAtoms.get("At_" + i);
			final Term bvConstraint = getBvConstraintAtom(encAtom, atom);
			equisatProp = mTheory.and(equisatProp, bvConstraint);
		}
		// add BVConstraint of all subterms as conjunct
		for (final Term term : mAllTerms) {
			final Term bvConstraint = getBvConstraintTerm(term);
			equisatProp = mTheory.and(equisatProp, bvConstraint);
		}
		return equisatProp;
	}

	/*
	 * Encodes bitvector Term in an Array of same lenth as the size of the bitvector Term.
	 * The Array contains Fresh Boolean Variables with name:
	 * "e(term)_i" where e stands for encoded, term is the input term and i the current position in the Array
	 */
	private Term[] getEncodedTerm(final Term term) {
		final BigInteger sizeBig = mTheory.toNumeral(term.getSort().getIndices()[0]);
		final int size = sizeBig.intValue();
		final Term[] boolvector = new Term[size];
		for (int i = 0; i < size; i++) {
			final String termPrefix = "e(" + term + ")_" + i;
			final TermVariable tv = mVarPrefix.get(termPrefix);
			final TermVariable boolVar;
			if (tv != null) {
				boolVar = tv;
			} else {
				boolVar = mTheory.createFreshTermVariable(termPrefix, mTheory.getSort("Bool"));
				mBoolAtoms.put(boolVar, new BooleanVarAtom(boolVar, mStackLevel));
				mVarPrefix.put(termPrefix, boolVar);
			}
			boolvector[i] = boolVar;
		}
		return boolvector;
	}

	// returns the boolean variable representing term
	private TermVariable getNewBoolVar(final Term term, final int bit) {
		final String prefix = "e(" + term.toString() + ")_" + bit;
		final TermVariable tv = mVarPrefix.get(prefix);
		if (tv == null) {
			throw new UnsupportedOperationException("Boolean variable was not genereated: " + term);
		} else {
			return tv;
		}
	}

	/*
	 * Creates BVConstraint for Atom's
	 * For equals:
	 * (AND lhs_i = rhs_i) <=> encAtom
	 * For bvult:
	 * TODO
	 */
	private Term getBvConstraintAtom(final Term encAtom, final DPLLAtom atom) {
		if (atom instanceof BVEquality) {
			// (AND lhs_i = rhs_i) <=> encAtom
			final BVEquality bveqatom = (BVEquality) atom;
			final BigInteger sizeBig = mTheory.toNumeral(bveqatom.getLHS().getSort().getIndices()[0]);
			final int size = sizeBig.intValue();
			final Term[] eqconj = new Term[size + size];
			for (int i = 0; i < size; i++) {
				// TODO check effizienz bei gro�en BITVECS
				// getNewBoolVar(bveqatom.getLHS(), i) gets the encoded term on each side of ther relation
				eqconj[i] =
						mTheory.or(mTheory.not(getNewBoolVar(bveqatom.getLHS(), i)),
								getNewBoolVar(bveqatom.getRHS(), i));
				eqconj[i + size] =
						mTheory.or(mTheory.not(getNewBoolVar(bveqatom.getRHS(), i)),
								getNewBoolVar(bveqatom.getLHS(), i));
			}
			final Term eqconjunction = mTheory.and(eqconj);
			final Term iff = mTheory.and(mTheory.or(mTheory.not(encAtom), eqconjunction),
					mTheory.or(mTheory.not(eqconjunction), encAtom));
			System.out.println("ATOM CNF:");
			final Term cnfTerm = toCNF(iff);
			cnfToClause((ApplicationTerm) cnfTerm);
			return iff;
		} else if (atom instanceof BVInEquality) {
			final BVInEquality bveqatom = (BVInEquality) atom;
			// unsigned operants, holds if cout is false
			final Term bvult =
					mTheory.not(adder(mEncTerms.get(bveqatom.getLHS()), negate(mEncTerms.get(bveqatom.getRHS())),
							mTheory.mTrue).getSecond());

			// TODO signed
			final Term iff = mTheory.and(mTheory.or(mTheory.not(encAtom), bvult),
					mTheory.or(mTheory.not(bvult), encAtom));
			final Term cnfTerm = toCNF(iff);
			cnfToClause((ApplicationTerm) cnfTerm);
			return iff;
		}
		throw new UnsupportedOperationException("Unknown Atom");
	}

	private Term getBvConstraintTerm(final Term term) {
		final Term[] encTerm = mEncTerms.get(term);
		if (term instanceof TermVariable) {
			return mTheory.mTrue;
		}
		if (term instanceof ConstantTerm) {
			final Term[] constresult = new Term[encTerm.length];
			for (int i = 0; i < encTerm.length; i++) {
				Term boolVar;
				final String termstring = BVUtils.getConstAsString((ConstantTerm) term);
				if (termstring.charAt(termstring.length() - 1 - i) == '1') {
					boolVar = mTheory.mTrue;
				} else {
					boolVar = mTheory.mFalse;
				}
				mBoolAtoms.put(boolVar, new BooleanVarAtom(boolVar, mStackLevel));
				final Term iff = mTheory.and(mTheory.or(mTheory.not(encTerm[i]), boolVar),
						mTheory.or(mTheory.not(boolVar), encTerm[i]));
				equivalenceAsClausel(encTerm[i], boolVar);
				constresult[i] = iff;
			}
			return mTheory.and(constresult);
		}
		if (term instanceof ApplicationTerm) {
			final ApplicationTerm appterm = (ApplicationTerm) term;
			final FunctionSymbol fsym = appterm.getFunction();
			if (appterm.getParameters().length == 0) {
				// Variable but not instanceof TermVariable
				return mTheory.mTrue;
			}
			// Appterm, not a Variable:
			if (fsym.isIntern()) {
				Term[] conjunction = new Term[encTerm.length];
				final Term[] constraint = new Term[encTerm.length];
				switch (fsym.getName()) {
				case "bvand": {
					for (int i = 0; i < encTerm.length; i++) {
						final Term and = mTheory.or(mTheory.not(getNewBoolVar(appterm.getParameters()[0], i)),
								mTheory.not(getNewBoolVar(appterm.getParameters()[1], i)));
						conjunction[i] = and;
					}
					break;
				}
				case "bvor": {
					for (int i = 0; i < encTerm.length; i++) {
						final Term or = mTheory.or(getNewBoolVar(appterm.getParameters()[0], i),
								getNewBoolVar(appterm.getParameters()[1], i));
						conjunction[i] = or;
					}
					break;
				}
				case "bvnot": {
					for (int i = 0; i < encTerm.length; i++) {
						final Term not = mTheory.not(getNewBoolVar(appterm.getParameters()[0], i));
						conjunction[i] = not;
					}
					break;
				}
				case "bvadd": {
					conjunction =
							adder(mEncTerms.get(appterm.getParameters()[0]), mEncTerms.get(appterm.getParameters()[1]),
									mTheory.mFalse).getFirst();
					break;
				}
				case "bvsub": {
					conjunction =
							adder(mEncTerms.get(appterm.getParameters()[0]),
									negate(mEncTerms.get(appterm.getParameters()[1])),
									mTheory.mTrue).getFirst();
					break;
				}
				case "bvshl": {
					final int stage =
							mTheory.toNumeral(appterm.getParameters()[1].getSort().getIndices()[0]).intValue() - 1;
					conjunction = shift(appterm.getParameters()[0], appterm.getParameters()[1], stage, appterm, true);
					break;
				}
				case "bvmul": {
					final int stage =
							mTheory.toNumeral(appterm.getParameters()[1].getSort().getIndices()[0]).intValue() - 1;
					conjunction = multiplier(appterm.getParameters()[0], appterm.getParameters()[1], stage);
					break;
				}
				case "bvudiv": {
					final int stage =
							mTheory.toNumeral(appterm.getParameters()[1].getSort().getIndices()[0]).intValue() - 1;
					conjunction = multiplier(appterm.getParameters()[0], appterm.getParameters()[1], stage);
					// conjunction extended by 2 Constraints
					// encTerm * b + r = a
					// r < b
					break;
				}
				case "bvlshr": {
					final int stage =
							mTheory.toNumeral(appterm.getParameters()[1].getSort().getIndices()[0]).intValue() - 1;
					conjunction = shift(appterm.getParameters()[0], appterm.getParameters()[1], stage, appterm, false);
					break;
				}
				case "bvurem":
				case "bvneg":
				case "concat":
				default:
					throw new UnsupportedOperationException(
							"Unsupported functionsymbol for bitblasting: " + fsym.getName());
				}
				for (int i = 0; i < conjunction.length; i++) {
					termConstraintToClausel(conjunction, encTerm);
					final Term iff = mTheory.and(mTheory.or(mTheory.not(encTerm[i]), conjunction[i]),
							mTheory.or(mTheory.not(conjunction[i]), encTerm[i]));
					constraint[i] = iff;
				}

				return mTheory.and(constraint);
			}
		}

		throw new UnsupportedOperationException("Unknown BVConstraint for term: " + term);
	}

	// negates each term in terms
	private Term[] negate(final Term[] terms) {
		final Term[] negateresult = new Term[terms.length];
		for (int i = 0; i < terms.length; i++) {
			negateresult[i] = mTheory.not(terms[i]);
		}
		return negateresult;
	}

	// returns a xor b xor cin in CNF
	private Term sumAdder(final Term a, final Term b, final Term cin) {
		if (cin.equals(mTheory.mFalse)) {
			return mTheory.and(mTheory.or(mTheory.not(a), mTheory.not(b)),
					mTheory.or(a, b));
		} else {
			return mTheory.and(mTheory.or(mTheory.not(a), mTheory.not(b), cin),
					mTheory.or(mTheory.not(a), b, mTheory.not(cin)),
					mTheory.or(a, mTheory.not(b), mTheory.not(cin)),
					mTheory.or(a, b, cin));
		}
	}

	/*
	 * return true, if term is either an atom or a negated atom
	 */
	private boolean isTermLiteral(final Term t) {
		if (t instanceof TermVariable) {
			return true;
		} else if (t instanceof ApplicationTerm) {
			final ApplicationTerm ap = (ApplicationTerm) t;
			if (ap.getFunction().getName().equals("not")) {
				if ((ap.getParameters().length == 1) && (ap.getParameters()[0] instanceof TermVariable)) {
					return true;
				}
			}
		}
		return false;
	}

	// returns ((a and b) or (mTheory.(a xor b) and cin)) in CNF
	private Term carryAdder(final Term a, final Term b, final Term cin) {
		if (cin.equals(mTheory.mFalse)) {
			return mTheory.and(a, b);
		} else if (cin.equals(mTheory.mTrue)) {
			return mTheory.or(a, b);
			// throw new UnsupportedOperationException("TODO nicht in CNF");
		}
		return mTheory.and(mTheory.or(a, b), mTheory.or(a, cin), mTheory.or(b, cin));
	}

	private Term[] carryBits(final Term[] encA, final Term[] encB, final Term cin) {
		assert encA.length == encB.length;
		final Term[] carryBits = new Term[encA.length];
		carryBits[0] = cin;
		for (int i = 1; i < encA.length; i++) {
			carryBits[i] = carryAdder(encA[i - 1], encB[i - 1], carryBits[i - 1]);
		}
		return carryBits;
	}

	private Pair<Term[], Term> adder(final Term[] encA, final Term[] encB, final Term cin) {
		assert encA.length == encB.length;
		final Term[] sumResult = new Term[encA.length];
		final Term[] carryBits = carryBits(encA, encB, cin);
		for (int i = 0; i < encA.length; i++) {
			// System.out.println("IN: " + mTheory.xor(encA[i], mTheory.xor(encB[i], carryBits[i])).toStringDirect());
			sumResult[i] = sumAdder(encA[i], encB[i], carryBits[i]);
			// System.out.println("OUT: " + sumResult[i].toStringDirect());
		}
		final Term cout = carryBits[carryBits.length - 1];
		return new Pair<>(sumResult, cout);
	}

	private Term[][] createBoolVarMap(final int stage, final int indices) {
		// create recursion vars
		final Term[][] boolvarmap = new Term[stage][indices];
		for (int s = 0; s < stage; s++) {
			for (int i = 0; i < indices; i++) {
				final String stageRec = "rec_" + i + "_" + s;
				final TermVariable boolVar = mTheory.createFreshTermVariable(stageRec, mTheory.getSort("Bool"));
				mBoolAtoms.put(boolVar, new BooleanVarAtom(boolVar, mStackLevel));
				boolvarmap[s][i] = boolVar;
			}
		}
		return boolvarmap;
	}

	/*
	 * TODO Optimize: a<<b = ite(b3 \/ b4, (0,0,0,0), ls(a,b,2))
	 * For i :
	 * ite(b3 \/ b4, enc[i] = 0, ls(a,b,2)[i])
	 * Check case b = 000110 and b = 00101
	 * TODO Optimize, if encB True, and second case recommend DPLL to set auxVar to false
	 * leftshift, true if bvshl. False if bvlshr
	 */
	private Term[] shift(final Term a, final Term b, final int stage, final Term shift, final boolean leftshift) {
		final Term[] encShift = mEncTerms.get(shift);
		final Term[] encA = mEncTerms.get(a);
		final Term[] encB = mEncTerms.get(b);
		final Term[] shiftResult = new Term[encA.length];
		final Term[][] boolvarmap = createBoolVarMap(stage, encA.length);

		for (int s = 0; s < stage; s++) {
			for (int i = 0; i < encA.length; i++) {
				final int pow = (int) Math.pow(2, s);
				Term ifte;
				if (s == 0) {
					Term thenTerm;
					if ((i + pow < encA.length) && !leftshift) { // TODO richtig
						// ifthenelse in CNF (not a or b) and (a or c)
						thenTerm = encA[i + pow];
					} else if (i >= pow && leftshift) {
						thenTerm = encA[i - pow];
					} else {
						thenTerm = boolvarmap[s][i];
					}
					ifte = mTheory.and(
							mTheory.or(mTheory.not(encB[0]), thenTerm),
							mTheory.or(encB[0], encA[i]));
				} else {
					Term thenTerm;
					if ((i + pow < encA.length) && !leftshift) {
						// ifthenelse in CNF (not a or b) and (a or c)
						thenTerm = boolvarmap[s - 1][i + pow];
					} else if (i >= pow && leftshift) {
						thenTerm = boolvarmap[s - 1][i - pow];
					} else {
						thenTerm = boolvarmap[s][i];
					}

					// ifthenelse in CNF (not a or b) and (a or c)
					ifte = mTheory.and(mTheory.or(mTheory.not(encB[s]), thenTerm),
							mTheory.or(encB[s], boolvarmap[s - 1][i]));
				}
				// Add Auxiliary variables and their represented term (ifte), as clauses
				// Save in Set to prevent douplicats?
				toCnfIfte(boolvarmap[s][i], ifte);
			}
		}
		// Last Stage
		for (int i = 0; i < encA.length; i++) {
			final int pow = (int) Math.pow(2, stage);
			Term thenTerm;
			if ((i + pow < encA.length) && !leftshift) {
				thenTerm = boolvarmap[stage - 1][i + pow];
			} else if (i >= pow && leftshift) {
				thenTerm = boolvarmap[stage - 1][i - pow];
			}else {
				// If B than 0
				// 0 can't be written as "False" Term, instead we say encShift[i] <=> encShift[i]
				thenTerm = encShift[i];
			}
			// ifthenelse in CNF (not a or b) and (a or c)
			shiftResult[i] = mTheory.and(mTheory.or(mTheory.not(encB[stage]), thenTerm),
					mTheory.or(encB[stage], boolvarmap[stage - 1][i]));
			// final int half = (int) Math.ceil((float) encA.length / 2);
			// final List<Term> disj = new ArrayList<>(); // if lenth = 5, dish has 2 elements
			// for (int k = half; k < encB.length; k++) {
			// disj.add(encB[k]);
			// }
			// final Term disjunction = listToDisjunction(disj, false);
			// System.out.println(disjunction);
			// ifte = mTheory.and(mTheory.or(mTheory.not(disjunction), mTheory.mFalse),
			// mTheory.or(mTheory.or(disjunction), boolvarmap[half][i]));

		}
		return shiftResult;
	}

	/*
	 * atom <=> ifte into cnf
	 * Add Clauses of (boolVar <=> ifte) to dpll
	 */
	private void toCnfIfte(final Term atom, final Term ifteTerm) {
		if (ifteTerm instanceof ApplicationTerm) {
			final ApplicationTerm appifteTerm = (ApplicationTerm) ifteTerm;
			// and
			final ApplicationTerm conj1 = (ApplicationTerm) appifteTerm.getParameters()[0];
			final ApplicationTerm conj2 = (ApplicationTerm) appifteTerm.getParameters()[1];
			final Term ifTerm = conj2.getParameters()[0];
			final Term thenTerm;
			if (conj1.getParameters().length > 1) {
				thenTerm = conj1.getParameters()[1];
			} else {
				thenTerm = mTheory.mFalse;
			}
			final Term elseTerm = conj2.getParameters()[1];
			addClause(mTheory.or(atom, mTheory.not(ifTerm), mTheory.not(thenTerm)));
			addClause(mTheory.or(atom, ifTerm, mTheory.not(elseTerm)));
			addClause(mTheory.or(mTheory.not(atom), ifTerm, thenTerm));
			addClause(mTheory.or(mTheory.not(atom), mTheory.not(ifTerm), thenTerm));
		} else {
			addClause(mTheory.or(atom, mTheory.not(ifteTerm)));
			addClause(mTheory.or(ifteTerm, mTheory.not(atom)));
		}

	}

	/*
	 * Barrel Shifter
	 * TODO optimize, as soon as b[log_2 size] is 1, return zerobitvec
	 */
	private Term[] leftshift(final Term a, final Term b, final int stage, final Term shift) {
		final Term[] encShift = mEncTerms.get(shift);
		final Term[] encA = mEncTerms.get(a);
		final Term[] encB = mEncTerms.get(b);
		final Term[] shiftResult = new Term[encA.length];
		final Term[][] boolvarmap = createBoolVarMap(stage, encA.length);
		// Add Clauses of boolVar <=> ifte to dpll
		for (int s = 0; s < stage; s++) {
			for (int i = 0; i < encA.length; i++) {
				Term ifte;
				final int pow = (int) Math.pow(2, s);
				if (s == 0) {
					if (i >= 1) {
						ifte = mTheory.and(
								mTheory.or(mTheory.not(encB[0]), encA[i - pow]),
								mTheory.or(encB[0], encA[i]));
					} else {
						ifte = mTheory.and(mTheory.or(mTheory.not(encB[0]), boolvarmap[s][i]),
								mTheory.or(encB[0], encA[i]));
					}
				} else {
					if (i >= pow) {
						ifte = mTheory.and(
								mTheory.or(mTheory.not(encB[s]), boolvarmap[s - 1][i - pow]),
								mTheory.or(encB[s], boolvarmap[s - 1][i]));
					} else {
						ifte = mTheory.and(mTheory.or(mTheory.not(encB[s]), boolvarmap[s][i]),
								mTheory.or(encB[s], boolvarmap[s - 1][i]));
					}

				}
				System.out.println(mTheory.equals(boolvarmap[s][i], ifte));
				toCnfIfte(boolvarmap[s][i], ifte);
			}
		}
		for (int i = 0; i < encA.length; i++) {
			// optimization, get rid of ifthenelse term, if b is constant
			if (i >= (int) Math.pow(2, stage)) {
				// ifthenelse in CNF (not a or b) and (a or c)
				shiftResult[i] = mTheory.and(
						mTheory.or(mTheory.not(encB[stage]),
								boolvarmap[stage - 1][i - (int) Math.pow(2, stage)]),
						mTheory.or(encB[stage], boolvarmap[stage - 1][i]));
			} else {
				shiftResult[i] = mTheory.and(mTheory.or(mTheory.not(encB[stage]), encShift[i]),
						mTheory.or(encB[stage], boolvarmap[stage - 1][i]));
			}
			System.out.println(shiftResult[i]);
		}

		return shiftResult;
	}

	/*
	 * Used, when b is not a term in the orginial formula, therefore mEncTerms.get(b) would be null
	 * TODO testen, pc laufzeit etc achtung
	 */
	private Term[] leftshiftMul(final Term a, final Term b, final int stage) {
		final Term[] encA = mEncTerms.get(a);
		final Term[] shiftResult = new Term[encA.length];
		if (stage == -1) {
			return encA;
		} else {
			for (int i = 0; i < encA.length; i++) {
				if (b instanceof ConstantTerm) {
					final String bAsString = BVUtils.getConstAsString((ConstantTerm) b);
					if (bAsString.charAt(bAsString.length() - 1 - stage) == '1') {
						if (i >= Math.pow(2, stage)) {
							shiftResult[i] =
									leftshiftMul(a, b, stage - 1)[i - (int) Math.pow(2, stage)];
						} else {
							shiftResult[i] = mTheory.mFalse;
						}
					} else {
						shiftResult[i] = leftshiftMul(a, b, stage - 1)[i];
					}
				} else {
					throw new UnsupportedOperationException(
							" Can only be used in multiplier, second argument needs to be ConstantTerm");
				}
			}
		}
		return shiftResult;
	}

	/*
	 * todo teilformeln statt rekursion durch hilfsvariablen ersetzten
	 */
	private Term[] multiplier(final Term a, final Term b, final int stage) {
		final Term[] encA = mEncTerms.get(a);
		final Term[] encB = mEncTerms.get(b);
		final int size = encA.length;
		final Term[] zeroVec = new Term[size];
		Arrays.fill(zeroVec, mTheory.mFalse);
		if (stage == -1) {
			return zeroVec;
		} else {
			final Term[] mul = multiplier(a, b, stage - 1);
			final Term[] shift;
			if (stage != 0) {
				final String s = Integer.toString(stage, 2);
				final String stageAsBits = "#b" + new String(new char[size - s.length()]).replace("\0", "0") + s;
				shift = leftshiftMul(a, mTheory.binary(stageAsBits), size - 1); // durch neue var erstzten
			} else {
				// a shifted by 0 = a
				shift = encA;
			}
			final Term[] ite = new Term[size];
			for (int i = 0; i < size; i++) {
				if (b instanceof ConstantTerm) {
					final String bString = BVUtils.getConstAsString((ConstantTerm) b);
					if (bString.charAt(bString.length() - stage - 1) == '1') {
						ite[i] = mTheory.or(mTheory.not(encB[stage]), shift[i]);
					} else {
						ite[i] = mTheory.or(encB[stage], mTheory.mFalse);
					}
				} else {
					// mTheory.ifthenelse(encB[stage], shift[i], mTheory.mFalse);
					ite[i] = mTheory.and(
							mTheory.or(mTheory.not(encB[stage]), shift[i]),
							mTheory.or(encB[stage], mTheory.mFalse));
				}

			}
			final Term[] sum = adder(mul, ite, mTheory.mFalse).getFirst();
			return sum;
		}
	}

	/*
	 * adds clauses for a term of from: lhs <=> rhs
	 * lhs and rhs must be a literal
	 */
	private void equivalenceAsClausel(final Term lhs, final Term rhs) {
		addClause(mTheory.or(mTheory.not(lhs), rhs));
		addClause(mTheory.or(mTheory.not(rhs), lhs));
	}

	private void cnfToClause(final ApplicationTerm term) {
		for (int i = 0; i < term.getParameters().length; i++) {
			addClause(term.getParameters()[i]);
		}
	}

	private Term toCNF(final Term term) {
		final CleanTransfomer cleaner = new CleanTransfomer();
		final NnfTransformer nnf = new NnfTransformer();
		final Term nnfTerm = nnf.transform(term); // TODO FIX RECURSION
		final CnfTransformer cnf = new CnfTransformer();
		final Term cnfTerm = cnf.transform(cnf.transform(nnfTerm));
		final Term cleanTerm = cleaner.transform(cnfTerm);
		// System.out.println("CNF SIZE: " + ((ApplicationTerm) cleanTerm).getParameters().length);
		return cleanTerm;
	}

	/*
	 *
	 */
	private void termConstraintToClausel(final Term[] conjunction, final Term[] encTerm) {
		for (int i = 0; i < conjunction.length; i++) {
			final Term impl1 = toCNF(mTheory.or(mTheory.not(conjunction[i]), encTerm[i]));
			if (impl1 instanceof ApplicationTerm) {
				cnfToClause((ApplicationTerm) impl1);
			} else {
				addClause(impl1);
			}
			final Term impl2 = toCNF(mTheory.or(mTheory.not(encTerm[i]), conjunction[i]));
			if (impl2 instanceof ApplicationTerm) {
				cnfToClause((ApplicationTerm) impl2);
			} else {
				addClause(impl2);
			}
		}
	}

	/*
	 * get's a list of terms,
	 * returns these terms as disjunction
	 * if negated is set to true, each disjunct will be negated
	 * TODO: Doppel Negation vermeiden!! f�r code �bersicht
	 */
	private Term listToDisjunction(final List<Term> list, final boolean negate) {
		assert !list.isEmpty();
		Term[] disjArray = new Term[list.size()];
		disjArray = list.toArray(disjArray);
		for (int i = 0; i < list.size(); i++) {
			if (negate) {
				disjArray = negate(disjArray);
			}
		}
		return mTheory.or(disjArray);
	}

	private void addClause(final Term term) {
		mClauses.add(mBVUtils.getClause(term, mBoolAtoms, mStackLevel));
	}

	public Collection<DPLLAtom> getBoolAtoms() {
		return mBoolAtoms.values();
	}

	public ScopedArrayList<Clause> getClauses() {
		return mClauses;
	}

}
