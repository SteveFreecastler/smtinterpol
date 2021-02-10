package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.cclosure;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.DataType;
import de.uni_freiburg.informatik.ultimate.logic.FunctionSymbol;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.DataType.Constructor;
import de.uni_freiburg.informatik.ultimate.smtinterpol.LogProxy;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.Clausifier;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.SymmetricPair;

public class DTReverseTrigger extends ReverseTrigger {
	
	final CCTerm mArg;
	int mArgPos;
	final FunctionSymbol mFunctionSymbol;
	final Clausifier mClausifier;
	final DataTypeTheory mDTTheory;
	
	public DTReverseTrigger(DataTypeTheory dtTheory, Clausifier clausifier, FunctionSymbol fs, CCTerm arg) {
		mDTTheory = dtTheory;
		mClausifier = clausifier;
		mFunctionSymbol = fs;
		mArg = arg;
	}

	@Override
	public CCTerm getArgument() {
		// TODO Auto-generated method stub
		return mArg;
	}

	@Override
	public int getArgPosition() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public FunctionSymbol getFunctionSymbol() {
		// TODO Auto-generated method stub
		return mFunctionSymbol;
	}

	@Override
	public void activate(CCAppTerm appTerm) {
		// TODO: merke Knoten für backtrackCompleteCheck
//				LogProxy logger = mCClosure.getLogger();
//				logger.info("DTReverseTrigger activated: %s", appTerm);
		
		ApplicationTerm argAT = (ApplicationTerm) mArg.mFlatTerm;
		ApplicationTerm appTermAT = (ApplicationTerm) mArg.mFlatTerm;
		if (mFunctionSymbol.getName() == "is") {
			// Just a workaround, is there a cleaner solution?
			FunctionSymbol fs = ((CCBaseTerm) appTerm.mFunc).getFunctionSymbol();
			if (fs.getIndices()[0].equals(argAT.getFunction().getName())) {
				mDTTheory.addPendingEquality(new SymmetricPair<CCTerm>(appTerm, mClausifier.getCCTerm(mClausifier.getTheory().mTrue) ));
			} else {
				mDTTheory.addPendingEquality(new SymmetricPair<CCTerm>(appTerm, mClausifier.getCCTerm(mClausifier.getTheory().mFalse)));
			}
		} else {
			FunctionSymbol fs = argAT.getFunction();
			if (mFunctionSymbol.isConstructor()) {
				if (fs.getName().equals(mFunctionSymbol.getName())) {
					for (int i = 0; i < argAT.getParameters().length; i++) {
						mDTTheory.addPendingEquality(new SymmetricPair<CCTerm>(mClausifier.getCCTerm(argAT.getParameters()[i]), mClausifier.getCCTerm(appTermAT.getParameters()[i])));
					}
				} else {
					// TODO: build conflict clause and add it to DataTypeTheory.mConflicts
				}
			} else {
				DataType argDT = (DataType) fs.getReturnSort().getSortSymbol();
				Constructor c = argDT.findConstructor(argAT.getFunction().getName());
				for (int i = 0; i < c.getSelectors().length; i++) {
					if (mFunctionSymbol.getName() == c.getSelectors()[i]) {
						mDTTheory.addPendingEquality(new SymmetricPair<CCTerm>(appTerm, mClausifier.getCCTerm(argAT.getParameters()[i])));
						return;
					}
				}
			}
			
			assert false :"selector function not part of constructor";
		}
	}

}