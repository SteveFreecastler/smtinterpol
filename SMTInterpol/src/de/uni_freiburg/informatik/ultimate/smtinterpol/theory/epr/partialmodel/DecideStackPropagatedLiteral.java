/*
 * Copyright (C) 2016-2017 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2016-2017 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.partialmodel;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.EprPredicate;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.clauses.ClauseEprLiteral;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.dawgstates.DawgState;

/**
 * Represents a literal on the epr decide stack that was set because of a unit propagation.
 *
 * @author Alexander Nutz
 *
 */
public class DecideStackPropagatedLiteral extends DecideStackLiteral {

	/**
	 * The clause literal whose clause, together with the prefix of the decide stack is responsible
	 * for the setting of this literal
	 * It is always an Epr literal because it contradicts something on the epr decide stack
	 */
	private final ClauseEprLiteral mReasonUnitClauseLiteral;

	private final DawgState<ApplicationTerm, Boolean> mReasonClauseDawg;

	public DecideStackPropagatedLiteral(final boolean polarity, final EprPredicate eprPred,
			final DawgState<ApplicationTerm, Boolean> predDawg,
			final ClauseEprLiteral unitClauseLiteral, final DawgState<ApplicationTerm, Boolean> clauseDawg,
			final int index) {
		super(polarity, eprPred, predDawg, index);
		mReasonUnitClauseLiteral = unitClauseLiteral;
		mReasonClauseDawg = clauseDawg;
	}

	/**
	 * Returns the dawg that contains those groundings of the clause that was the reason for propagation of this literal, which
	 * correspond to the point where this dsl sets its predicate.
	 *  -- i.e. the dawg that was the preimage of the renameProjectAndSelect operation that yielded this dsl's dawg.
	 */
	public DawgState<ApplicationTerm, Boolean> getClauseDawg() {
		return mReasonClauseDawg;
	}

	/**
	 * Returns the unit clause that was the reason for setting this propagated decide stack literal.
	 * @return unit clause
	 */
	public ClauseEprLiteral getReasonClauseLit() {
		return mReasonUnitClauseLiteral;
	}

	@Override
	public String toString() {
		return String.format("(DSProp (%d): %c%s %s)",
				nr, (mPolarity ? ' ' : '~'),  mPred, mDawg);
	}
}
