package spins.promela.compiler.ltsmin.state;

import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.ltsmin.LTSminDMWalker.IdMarker;
import spins.promela.compiler.ltsmin.LTSminDMWalker.MarkAction;
import spins.promela.compiler.ltsmin.util.PArrayIndexOutOfBoundsException;
import spins.promela.compiler.parser.ParseException;



public class LTSminSubVectorArray extends LTSminSubVector {
	private LTSminVariable var;

	protected LTSminSubVectorArray(LTSminSubVector sv, LTSminVariable var, int offset) {
		super(sv, offset);
		this.var = var;
	}

	protected LTSminSubVector getSubVector(int index) {
		if (index >= (var.array() > -1 ? var.array() : 1))
			throw new AssertionError("Array index out of bound for: "+ var);
		return follow(index);
	}

	@Override
	protected LTSminSubVector follow() {
		if (var.array() > -1)
			throw new AssertionError("Array variable requires index: "+ var);
		return follow(0);
	}
	
	private LTSminSubVector follow(int index) {
		if (var.getType() instanceof LTSminTypeNative)
			return slot(index);
        int max = (var.array() > -1 ? var.array() : 1);
		if (index >= max)
            throw new PArrayIndexOutOfBoundsException(var, index);
		int offset = index * var.getType().length();
		return new LTSminSubVectorStruct(this, var.getType(), offset);
	}

	@Override
	public int length() {
		return var.length();
	}
	
	@Override
	public void mark(IdMarker idMarker, Identifier id) {
		Expression arrayExpr = id.getArrayExpr();
		int first = 0;
		int last = 0;
		if (arrayExpr != null) {
			new IdMarker(idMarker, MarkAction.READ).mark(arrayExpr); // array expr is only read!
			if (-1 == id.getVariable().getArraySize()) throw new AssertionError("Index a non-array: "+ var);
			last = id.getVariable().getArraySize() - 1;
			try {
				first = last = arrayExpr.getConstantValue();
			} catch(ParseException pe) {
			    if (idMarker.isMayMustWrite() && first != last) {			        
			        if (idMarker.params.opts.must_write) {
    			        // Write may not be over-approximated!
    			        if (idMarker.isStrict()) {
    			            idMarker = new IdMarker(idMarker, MarkAction.EBOTH);
    			        } else {
    			            idMarker = new IdMarker(idMarker, MarkAction.BOTH);
    			        }
			        } else {
						if (idMarker.isStrict()) {
							idMarker = new IdMarker(idMarker, MarkAction.EMAY_WRITE);
						} else {
							idMarker = new IdMarker(idMarker, MarkAction.MAY_WRITE);
						}        
			        }
			    }
			}
		}
		for (int i = first; i <= last; i++) {
			LTSminSubVector sub = follow(i);
			sub.mark(idMarker, id.getSub());
		}
	}
}
