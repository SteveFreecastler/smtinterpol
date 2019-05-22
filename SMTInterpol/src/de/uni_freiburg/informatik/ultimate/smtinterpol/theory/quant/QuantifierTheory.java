/*
 * Copyright (C) 2018 University of Freiburg
 *
 * This file is part of SMTInterpol.
 *
 * SMTInterpol is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMTInterpol is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with SMTInterpol.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.quant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.FunctionSymbol;
import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Theory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.LogProxy;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.Clausifier;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.SMTAffineTerm;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.SharedTerm;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Clause;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.DPLLAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.DPLLEngine;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.ILiteral;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.ITheory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Literal;
import de.uni_freiburg.informatik.ultimate.smtinterpol.proof.SourceAnnotation;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.cclosure.CCTerm;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.cclosure.CClosure;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.linar.LinArSolve;
import de.uni_freiburg.informatik.ultimate.util.datastructures.ScopedArrayList;

/**
 * Solver for quantified formulas within the almost uninterpreted fragment (Restrictions on terms and literals are
 * explained in the corresponding classes. For reference, see Ge & de Moura, 2009).
 *
 * This may be merged with the EPR solver implementation by Alexander Nutz in the future; for now, we keep it separate.
 *
 * @author Tanja Schindler
 */
public class QuantifierTheory implements ITheory {

	private final Clausifier mClausifier;
	private final LogProxy mLogger;
	private final Theory mTheory;
	private final DPLLEngine mEngine;

	final CClosure mCClosure;
	final LinArSolve mLinArSolve;

	private final InstantiationManager mInstantiationManager;
	private final Map<Sort, SharedTerm> mLambdas;

	/**
	 * The quantified literals built so far.
	 */
	private Map<Term, QuantLiteral> mQuantLits;

	/**
	 * Clauses that only the QuantifierTheory knows, i.e. that contain at least one literal with an (implicitly)
	 * universally quantified variable.
	 */
	private final ScopedArrayList<QuantClause> mQuantClauses;

	/**
	 * Literals (not atoms!) mapped to potential conflict and unit clauses that they are contained in. At creation, the
	 * clauses would have been conflicts or unit clauses if the corresponding theories had already known the contained
	 * literals. In the next checkpoint, false literals should have been propagated by the other theories, but we might
	 * still have one undefined literal (and is a unit clause). If not, it is a conflict then.
	 */
	private final Map<Literal, Set<InstClause>> mPotentialConflictAndUnitClauses;

	// Statistics
	private long mDERGroundCount, mConflictCount, mPropCount, mFinalCount;

	public QuantifierTheory(final Theory th, final DPLLEngine engine, final Clausifier clausifier) {
		mClausifier = clausifier;
		mLogger = clausifier.getLogger();
		mTheory = th;
		mEngine = engine;

		mCClosure = clausifier.getCClosure();
		mLinArSolve = clausifier.getLASolver();

		mInstantiationManager = new InstantiationManager(mClausifier, this);
		mLambdas = new HashMap<Sort, SharedTerm>();

		mQuantLits = new HashMap<Term, QuantLiteral>();
		mQuantClauses = new ScopedArrayList<QuantClause>();

		mPotentialConflictAndUnitClauses = new LinkedHashMap<>();

		mDERGroundCount = mConflictCount = mPropCount = mFinalCount = 0;
	}

	@Override
	public Clause startCheck() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void endCheck() {
		// TODO Auto-generated method stub

	}

	@Override
	public Clause setLiteral(Literal literal) {
		// Mark clauses that are true due to this literal.
		for (final QuantClause quantClause : mQuantClauses) {
			if (Arrays.asList(quantClause.getGroundLits()).contains(literal)) {
				quantClause.incrNumCurrentTrueLits();
			}
		}
		if (mPotentialConflictAndUnitClauses.containsKey(literal)) {
			mPotentialConflictAndUnitClauses.remove(literal);
		}
		final Iterator<Literal> litIt = mPotentialConflictAndUnitClauses.keySet().iterator();
		while (litIt.hasNext()) {
			final Literal keyLit = litIt.next();
			final Iterator<InstClause> clauseIt = mPotentialConflictAndUnitClauses.get(keyLit).iterator();
			while (clauseIt.hasNext()) {
				final InstClause clause = clauseIt.next();
				if (clause.mLits.contains(literal.negate())) {
					clauseIt.remove();
				}
			}
			if (mPotentialConflictAndUnitClauses.get(keyLit).isEmpty()) {
				litIt.remove();
			}
		}
		if (mPotentialConflictAndUnitClauses.containsKey(literal.negate())) {
			for (final InstClause instClause : mPotentialConflictAndUnitClauses.get(literal.negate())) {
				assert instClause.mNumUndefLits > 0;
				instClause.mNumUndefLits -= 1;
				if (instClause.isConflict()) {
					mConflictCount++;
					return new Clause(instClause.mLits.toArray(new Literal[instClause.mLits.size()]));
				}
			}
		}
		return null;
	}

	@Override
	public void backtrackLiteral(Literal literal) {
		for (final QuantClause clause : mQuantClauses) {
			if (Arrays.asList(clause.getGroundLits()).contains(literal)) {
				clause.decrNumCurrentTrueLits();
			}
		}
		for (final Literal lit : mPotentialConflictAndUnitClauses.keySet()) {
			for (final InstClause clause : mPotentialConflictAndUnitClauses.get(lit)) {
				if (clause.mLits.contains(literal.negate())) {
					clause.mNumUndefLits += 1;
				}
			}
		}
	}

	@Override
	public Clause checkpoint() {
		for (final QuantClause clause : mQuantClauses) {
			if (mEngine.isTerminationRequested())
				return null;
			clause.updateInterestingTermsAllVars();
		}
		final Clause conflict =
				addPotentialConflictAndUnitClauses(mInstantiationManager.findConflictAndUnitInstances());
		if (conflict != null) {
			mEngine.learnClause(conflict);
			mConflictCount++;
		}
		return conflict;
	}

	@Override
	public Clause computeConflictClause() {
		mFinalCount++;
		Clause conflict = checkpoint();
		if (conflict != null) {
			return conflict;
		}
		assert mPotentialConflictAndUnitClauses.isEmpty();
		conflict = mInstantiationManager.instantiateAll();
		if (conflict != null) {
			mConflictCount++;
			mEngine.learnClause(conflict);
			return conflict;
		}
		checkCompleteness();
		return null;
	}

	@Override
	public Literal getPropagatedLiteral() {
		for (final Literal lit : mPotentialConflictAndUnitClauses.keySet()) {
			for (final InstClause clause : mPotentialConflictAndUnitClauses.get(lit)) {
				if (clause.isUnit()) {
					final Clause expl = new Clause(clause.mLits.toArray(new Literal[clause.mLits.size()]));
					lit.getAtom().mExplanation = expl;
					mEngine.learnClause(expl);
					mPropCount++;
					if (mLogger.isDebugEnabled()) {
						mLogger.debug("Quant Prop: " + lit + " reason: " + lit.getAtom().mExplanation);
					}
					return lit;
				}
			}
		}
		return null;
	}

	@Override
	public Clause getUnitClause(Literal literal) {
		assert false : "Should never be called.";
		return null;
	}

	@Override
	public Literal getSuggestion() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void printStatistics(LogProxy logger) {
		logger.info("Quant: DER produced " + mDERGroundCount + " ground clause(s).");
		logger.info("Quant: Conflicts: " + mConflictCount + " Props: " + mPropCount + " Final Checks: " + mFinalCount);

	}

	@Override
	public void dumpModel(LogProxy logger) {
		// TODO Auto-generated method stub

	}

	@Override
	public void increasedDecideLevel(int currentDecideLevel) {
		// TODO Auto-generated method stub

	}

	@Override
	public void decreasedDecideLevel(int currentDecideLevel) {
		// TODO Auto-generated method stub

	}

	@Override
	public Clause backtrackComplete() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void restart(int iteration) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeAtom(DPLLAtom atom) {
		// TODO Auto-generated method stub

	}

	@Override
	public Object push() {
		mQuantClauses.beginScope();
		for (final QuantClause qClause : mQuantClauses) {
			qClause.push();
		}
		return null;
	}

	@Override
	public void pop(Object object, int targetlevel) {
		mQuantClauses.endScope();
		for (final QuantClause qClause : mQuantClauses) {
			qClause.pop();
		}

	}

	@Override
	public Object[] getStatistics() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * This method builds new QuantEqualities and simultaneously checks if they lie in the almost uninterpreted
	 * fragment, i.e., if they are of the form (i) (euEUTerm = euTerm), pos. and neg. or (ii) (var = ground), integer
	 * only, or if they can be used for DER, i.e. (var != term[withoutthisvar])
	 * <p>
	 * This method also brings equality atoms in the form (var = term), if there exists a TermVariable at top level. For
	 * integers, only if the variable has factor ±1; for reals always.
	 */
	public QuantLiteral getQuantEquality(final Term term, final boolean positive, final SourceAnnotation source,
			final Term lhs, final Term rhs) {
		QuantLiteral atom = mQuantLits.get(term);

		if (atom == null) {
			// Bring atom to form (var = term) if there exists a variable at "top level".
			Term newLhs = lhs;
			Term newRhs = rhs;
			if (!lhs.getSort().isNumericSort()) {
				final TermVariable leftVar = lhs instanceof TermVariable ? (TermVariable) lhs : null;
				final TermVariable rightVar = rhs instanceof TermVariable ? (TermVariable) rhs : null;
				if (leftVar == null && rightVar != null) {
					newLhs = rightVar;
					newRhs = lhs;
				}
			} else {
				SMTAffineTerm linAdded = SMTAffineTerm.create(lhs);
				linAdded.add(Rational.MONE, SMTAffineTerm.create(rhs));
				Rational fac = Rational.ONE;
				for (final Term smd : linAdded.getSummands().keySet()) {
					if (smd instanceof TermVariable) {
						fac = linAdded.getSummands().get(smd);
						if (smd.getSort().getName() == "Real") {
							newLhs = smd;
							linAdded.add(fac.negate(), smd);
							linAdded.mul(fac.negate());
							newRhs = linAdded.toTerm(lhs.getSort());
							break;
						} else {
							if (fac.abs() == Rational.ONE) {
								// Isolate first found variable (if exists).
								newLhs = smd;
								linAdded.add(fac.negate(), smd);
								if (fac == Rational.ONE) {
									linAdded.negate();
								}
								newRhs = linAdded.toTerm(lhs.getSort());
								break;
							}
						}
					}
				}
			}
			final Term newTerm = mTheory.term("=", newLhs, newRhs);
			atom = new QuantEquality(newTerm, newLhs, newRhs);
			
			// Check if the atom is almost uninterpreted or can be used for DER.
			if (!(newLhs instanceof TermVariable)) { // (euEUTerm = euTerm) is almost uninterpreted
				if (!isEssentiallyUninterpreted(newLhs) || !isEssentiallyUninterpreted(newRhs)) {
					atom.mIsAlmostUninterpreted = false;
					atom.negate().mIsAlmostUninterpreted = false;
				}
			} else if (!(newRhs instanceof TermVariable)) { // (x != termwithoutx) can be used for DER
				if (!Arrays.asList(newRhs.getFreeVars()).contains((TermVariable) newLhs)) {
					atom.mIsAlmostUninterpreted = false;
					atom.negate().mIsAlmostUninterpreted = false;
					atom.negate().mIsDERUsable = true;
				}
			} else { // (iv) (var = var)
				atom.mIsAlmostUninterpreted = false; // Positive form is not almost uninterpreted.
				atom.negate().mIsDERUsable = true; // The negated form is used for DER.
			}
		}
		mQuantLits.put(term, atom);
		return atom;
	}

	/**
	 * This method builds new QuantInequalities and simultaneously checks if they lie in the almost uninterpreted
	 * fragment, i.e., if they are of the form (i) (eu <= 0), pos. and neg., (ii) (var < var), (iii) (var < ground), or
	 * (iv) (ground < var).
	 * <p>
	 * For integers x, positive (x-t<=0) are rewritten into ~(t+1<=x), or more precisely, ~(-x+t+1<=0). For reals x, we
	 * normalize atoms (kx-t<= 0) to get (±x-t<=0).
	 * <p>
	 * TODO Offsets? (See paper)
	 */
	public QuantLiteral getQuantInequality(final Term term, final boolean positive, final SourceAnnotation source,
			final Term lhs) {
		QuantLiteral atom = mQuantLits.get(term);

		boolean rewrite = false; // Set to true when rewriting positive (x-t<=0) into ~(t+1<=x) for x integer
		if (atom == null) {
			final SMTAffineTerm linTerm = SMTAffineTerm.create(lhs);
			TermVariable var = null;
			Rational fac = Rational.ONE;
			boolean hasUpperBound = false;
			for (final Term smd : linTerm.getSummands().keySet()) {
				if (smd instanceof TermVariable) {
					fac = linTerm.getSummands().get(smd);
					if (smd.getSort().getName() == "Real") { // In the real case, we normalize the term for this var.
						var = (TermVariable) smd;
						if (linTerm.getSummands().get(smd).isNegative()) {
							hasUpperBound = true;
						} else {
							hasUpperBound = false;
						}
						break;
					} else {
						if (fac == Rational.MONE) {
							var = (TermVariable) smd;
							hasUpperBound = true;
							break;
						} else if (fac == Rational.ONE) {
							var = (TermVariable) smd;
							hasUpperBound = false;
							break;
						}
					}
				}
			}
			if (positive && var != null && lhs.getSort().getName() == "Int") {
				// Rewrite positive (x-t<=0) into ~(t+1<=x), or more precisely, ~(-x+t+1<=0) for x integer.
				// Similarly (t-x<=0) into ~(x-t+1<=0)
				rewrite = true;
				linTerm.negate();
				linTerm.add(Rational.ONE);
			} else if (var != null && lhs.getSort().getName() == "Real") {
				// var should have coefficient 1 or -1.
				linTerm.div(fac.abs());
			}

			final Term newTerm = mTheory.term("<=", linTerm.toTerm(lhs.getSort()), Rational.ZERO.toTerm(lhs.getSort()));
			atom = new QuantBoundConstraint(newTerm, linTerm);

			// Check if the atom is almost uninterpreted.
			if (var == null) { // (euTerm <= 0), pos. and neg., is almost uninterpreted.
				for (final Term smd : linTerm.getSummands().keySet()) {
					if (!isEssentiallyUninterpreted(smd)) {
						atom.mIsAlmostUninterpreted = false;
						atom.negate().mIsAlmostUninterpreted = false;
					}
				}
			} else { // (var < var), (var < ground), or (ground < var) are almost uninterpreted
				atom.mIsAlmostUninterpreted = false;
				final SMTAffineTerm remainderAff = new SMTAffineTerm();
				remainderAff.add(linTerm);
				remainderAff.add(hasUpperBound ? Rational.ONE : Rational.MONE, var);
				if (!hasUpperBound) {
					remainderAff.negate();
				}
				final Term remainder = remainderAff.toTerm(lhs.getSort());
				if (!(remainder instanceof TermVariable) && !(remainder.getFreeVars().length == 0)) {
					atom.negate().mIsAlmostUninterpreted = false;
				}
			}
		}
		mQuantLits.put(atom.getTerm(), atom);
		return rewrite ? atom.negate() : atom;
	}

	/**
	 * Perform destructive equality reasoning.
	 * 
	 * @param groundLits
	 *            The ground literals of the clause.
	 * @param quantLits
	 *            The quantified literals of the clause.
	 * @param source
	 *            The source of the clause.
	 * @return an array of ILiteral containing all literals after DER; null if the clause became true.
	 */
	public ILiteral[] performDestructiveEqualityReasoning(final Literal[] groundLits, final QuantLiteral[] quantLits,
			final SourceAnnotation source) {
		final DestructiveEqualityReasoning der =
				new DestructiveEqualityReasoning(this, groundLits, quantLits, source);
		final ArrayList<ILiteral> litsAfterDER = new ArrayList<>();
		if (der.applyDestructiveEqualityReasoning()) {
			if (der.isTriviallyTrue()) {
				return null; // Don't add trivially true clauses.
			}
			final Literal[] groundLitsAfterDER = der.getGroundLitsAfterDER();
			final QuantLiteral[] quantLitsAfterDER = der.getQuantLitsAfterDER();
			if (quantLitsAfterDER.length == 0 && mLogger.isDebugEnabled()) {
				mLogger.debug("Quant: DER returned ground clause.");
				mDERGroundCount++;
			}
			litsAfterDER.addAll(Arrays.asList(groundLitsAfterDER));
			litsAfterDER.addAll(Arrays.asList(quantLitsAfterDER));
		} else {
			litsAfterDER.addAll(Arrays.asList(groundLits));
			litsAfterDER.addAll(Arrays.asList(quantLits));
		}
		return litsAfterDER.toArray(new ILiteral[litsAfterDER.size()]);
	}

	/**
	 * Add a QuantClause for a given set of literals and quantified literals.
	 *
	 * Call this only after performing DER.
	 *
	 * @param iLits
	 *            ground and quantified literals of the clause to add.
	 * @param source
	 *            the source of the clause
	 */
	public void addQuantClause(final ILiteral[] iLits, SourceAnnotation source) {
		boolean isQuant = false;
		for (ILiteral lit : iLits) {
			if (lit instanceof QuantLiteral) {
				isQuant = true;
			}
		}
		if (!isQuant) {
			throw new IllegalArgumentException("Cannot add clause to QuantifierTheory: No quantified literal!");
		}

		final ArrayList<Literal> groundLits = new ArrayList<>();
		final ArrayList<QuantLiteral> quantLits = new ArrayList<>();
		for (ILiteral lit : iLits) {
			if (lit instanceof Literal) {
				groundLits.add((Literal) lit);
			} else {
				final QuantLiteral qLit = (QuantLiteral) lit;
				if (!qLit.mIsAlmostUninterpreted) {
					mLogger.warn("Quant: Clause contains literal that is not almost uninterpreted: " + qLit);
				} else if (qLit.isNegated() && qLit.mIsDERUsable) {
					mLogger.warn("Quant: Clause contains disequality on variable not eliminated by DER: " + qLit);
				}
				quantLits.add((QuantLiteral) lit);
			}
		}

		final QuantClause clause = new QuantClause(groundLits.toArray(new Literal[groundLits.size()]),
				quantLits.toArray(new QuantLiteral[quantLits.size()]), this, source);
		mQuantClauses.add(clause);
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Quant: Added clause: " + clause);
		}
	}

	public Clausifier getClausifier() {
		return mClausifier;
	}

	public CClosure getCClosure() {
		return mCClosure;
	}

	public LinArSolve getLinAr() {
		return mLinArSolve;
	}

	public InstantiationManager getInstantiator() {
		return mInstantiationManager;
	}

	public Collection<QuantClause> getQuantClauses() {
		return mQuantClauses;
	}

	public Map<Term, QuantLiteral> getQuantLits() {
		return mQuantLits;
	}

	public Theory getTheory() {
		return mTheory;
	}

	protected SharedTerm getLambda(Sort sort) {
		if (mLambdas.containsKey(sort)) {
			return mLambdas.get(sort);
		}
		final FunctionSymbol fsym = mTheory.getFunctionWithResult("@0", null, sort, new Sort[0]);
		final SharedTerm lambda =
				mClausifier.getSharedTermAndAddAxioms(mTheory.term(fsym), SourceAnnotation.EMPTY_SOURCE_ANNOT);
		mLambdas.put(sort, lambda);
		return lambda;
	}

	/**
	 * Check if a given term is essentially uninterpreted, i.e., it is ground or variables only appear as arguments of
	 * uninterpreted functions.
	 * 
	 * TODO Offsets? (See Paper. If implemented, change method name.)
	 * 
	 * TODO Nonrecursive.
	 * 
	 * @param term
	 *            The term to check.
	 * @return true if the term is essentially uninterpreted, false otherwise.
	 */
	private boolean isEssentiallyUninterpreted(final Term term) {
		if (term.getFreeVars().length == 0) {
			return true;
		} else if (term instanceof ApplicationTerm) {
			final ApplicationTerm appTerm = (ApplicationTerm) term;
			final FunctionSymbol func = appTerm.getFunction();
			if (!func.isInterpreted()) {
				for (Term arg : appTerm.getParameters()) {
					if (!(arg instanceof TermVariable)) {
						if (!isEssentiallyUninterpreted(arg)) {
							return false;
						}
					}
				}
				return true;
			} else if (func.getName() == "select") {
				final Term[] params = appTerm.getParameters();
				if (params[0] instanceof TermVariable || !isEssentiallyUninterpreted(params[0])) {
					return false; // Quantified arrays are not allowed.
				}
				if (!(params[1] instanceof TermVariable) && !isEssentiallyUninterpreted(params[1])) {
					return false;
				}
				return true;
			} else if (func.getName() == "+" || func.getName() == "-" || func.getName() == "*") {
				final SMTAffineTerm affineTerm = SMTAffineTerm.create(term);
				for (Term summand : affineTerm.getSummands().keySet()) {
					if (!isEssentiallyUninterpreted(summand)) {
						return false;
					}
				}
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	/**
	 * Check if there exists a not yet satisfied clause that contains a literal outside of the almost uninterpreted
	 * fragment. If so, inform the DPLL engine of incompleteness.
	 * 
	 * Should only be called in computeConflict clause.
	 */
	private void checkCompleteness() {
		for (final QuantClause qClause : mQuantClauses) {
			if (qClause.getNumCurrentTrueLits() == 0) {
				for (final QuantLiteral qLit : qClause.getQuantLits()) {
					if (!qLit.isAlmostUninterpreted()) {
						mEngine.setCompleteness(2);
						return;
					}
				}
				for (final SharedTerm lambda : mLambdas.values()) {
					if (!lambda.getSort().isNumericSort()) {
						final CCTerm lambdaCC = lambda.getCCTerm();
						if (lambdaCC.getNumMembers() > 1) {
							mEngine.setCompleteness(2);
							return;
						}
					}
				}
			}
		}
	}

	/**
	 * Add potential conflict and unit clauses to the map from undefined literals to clauses. We stop as soon as we find
	 * an actual conflict.
	 * 
	 * @param instances
	 *            a set of potential conflict and unit clauses
	 * @return a conflict
	 */
	private Clause addPotentialConflictAndUnitClauses(final Set<List<Literal>> instances) {
		if (instances == null) {
			return null;
		}
		boolean isConflict = true;
		for (List<Literal> clause : instances) {
			if (mEngine.isTerminationRequested())
				return null;
			boolean isTrueInst = false;
			int numUndef = 0;
			// Count the number of undefined literals
			for (final Literal lit : clause) {
				if (lit.getAtom().getDecideStatus() == lit) {
					isTrueInst = true;
					continue;
				}
				if (lit.getAtom().getDecideStatus() == null) {
					numUndef++;
				}
			}
			if (isTrueInst) {
				continue; // Don't add true instances.
				// TODO They should be detected during literal evaluation, but it doesn't work as expected, see below.
			}
			final InstClause instClause = new InstClause(clause, numUndef);
			for (final Literal lit : clause) {
				// assert lit.getAtom().getDecideStatus() != lit;
				// TODO It happens that the assertion is violated. Not sure whether evaluation of literals (as terms)
				// works correctly, even with CC.
				if (lit.getAtom().getDecideStatus() == null) {
					isConflict = false;
					if (!mPotentialConflictAndUnitClauses.containsKey(lit)) {
						mPotentialConflictAndUnitClauses.put(lit, new LinkedHashSet<>());
					}
					mPotentialConflictAndUnitClauses.get(lit).add(instClause);
				}
			}
			if (isConflict) {
				return new Clause(clause.toArray(new Literal[clause.size()]));
			}
		}
		return null;
	}

	private class InstClause {
		private final List<Literal> mLits;
		protected int mNumUndefLits;

		InstClause(final List<Literal> lits, final int numUndefLits) {
			mLits = lits;
			mNumUndefLits = numUndefLits;
		}

		boolean isConflict() {
			return mNumUndefLits == 0;
		}

		boolean isUnit() {
			return mNumUndefLits == 1;
		}

		@Override
		public boolean equals(final Object other) {
			if (other instanceof InstClause) {
				if (mLits == ((InstClause) other).mLits) {
					return true;
				}
			}
			return false;
		}
	}
}
