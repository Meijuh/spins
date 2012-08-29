package spinja.promela.compiler.ltsmin.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import spinja.promela.compiler.Specification;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.ltsmin.LTSminTreeWalker.Pair;
import spinja.promela.compiler.ltsmin.matrix.DepMatrix;
import spinja.promela.compiler.ltsmin.matrix.GuardInfo;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardOr;
import spinja.promela.compiler.ltsmin.state.LTSminStateVector;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;

/**
 * An LTSmin model consists is derived from a SpinJa Specification and
 * encapsulates transitions (which are transition groups), a state vector
 * consisting physically of slots and dependency information. 
 * 
 * Transitions of the model are mapped to transition groups (LTSminTransition)
 * with guard and action expressions.
 * Variables of the model are mapped to state vector slots.
 * The dependency metric records the dependencies between transition groups
 * state vector slots, where the action can be write dependencies and the guards
 * represent only read dependencies.
 * Finally, the guard info class stores dependencies amongst guards and between
 * guards and transitions that are needed for partial-order reduction. 
 * 
 * @see LTSminStateVector
 * 
 * @author Freark van der Berg, Alfons Laarman
 */
public class LTSminModel implements Iterable<LTSminTransition> {

	private String name;
	private LTSminGuardOr accepting_conditions;
	public LTSminStateVector sv;
	private DepMatrix depMatrix;
	private GuardInfo guardInfo;
	private List<String> mtypes;
	public final Variable index = new Variable(VariableType.INT, "i", -1);
	public final Variable jndex = new Variable(VariableType.INT, "j", -1);
	private List<LTSminTransition> transitions = new ArrayList<LTSminTransition>();
	private List<Variable> locals = Arrays.asList(index, jndex);
	Map<LTSminState, LTSminState> states = new HashMap<LTSminState, LTSminState>();
    public List<Pair<Expression,String>> assertions = new LinkedList<Pair<Expression,String>>();

	public LTSminModel(String name, LTSminStateVector sv, Specification spec) {
		this.name = name;
		mtypes = spec.getMTypes();
		this.sv = sv;
		this.accepting_conditions = new LTSminGuardOr();
	}

	public List<Variable> getLocals() {
		return locals;
	}

	public List<String> getMTypes() {
		return mtypes;
	}

	public LTSminGuardOr getAcceptingConditions() {
		return accepting_conditions;
	}

	public String getName() {
		return name;
	}

	public DepMatrix getDepMatrix() {
		return depMatrix;
	}

	public void setDepMatrix(DepMatrix depMatrix) {
		this.depMatrix = depMatrix;
	}

	public GuardInfo getGuardInfo() {
		return guardInfo;
	}

	public void setGuardInfo(GuardInfo guardMatrix) {
		this.guardInfo = guardMatrix;
	}

	public boolean hasAtomic() {
		for (LTSminTransition t : this)
			if (t.isAtomic()) return true;
		return false;
	}

	public LTSminState getOrAddState(LTSminState state) {
		LTSminState begin = states.get(state);
		if (null != begin) {
			return begin;
		} else {
			states.put(state, state);
			return state;
		}
	}

	public List<LTSminTransition> getTransitions() {
		return transitions;
	}

	@Override
	public Iterator<LTSminTransition> iterator() {
		return transitions.iterator();
	}

	public void addTransition(LTSminTransition lt) {
	    lt.setGroup(transitions.size());
		transitions.add(lt);
	}
}
