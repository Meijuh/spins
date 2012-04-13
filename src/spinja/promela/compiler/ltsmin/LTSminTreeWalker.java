package spinja.promela.compiler.ltsmin;

import static spinja.promela.compiler.ltsmin.model.LTSminUtil.assign;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.chanContentsGuard;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.chanEmptyGuard;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.compare;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.constant;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.dieGuard;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.error;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.id;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.inAtomicGuard;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.pcGuard;
import static spinja.promela.compiler.ltsmin.state.LTSminStateVector._NR_PR;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeChanStruct.bufferVar;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeChanStruct.elemVar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import spinja.promela.compiler.ProcInstance;
import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.Specification;
import spinja.promela.compiler.actions.Action;
import spinja.promela.compiler.actions.AssertAction;
import spinja.promela.compiler.actions.AssignAction;
import spinja.promela.compiler.actions.BreakAction;
import spinja.promela.compiler.actions.ChannelReadAction;
import spinja.promela.compiler.actions.ChannelSendAction;
import spinja.promela.compiler.actions.ElseAction;
import spinja.promela.compiler.actions.ExprAction;
import spinja.promela.compiler.actions.OptionAction;
import spinja.promela.compiler.actions.PrintAction;
import spinja.promela.compiler.actions.Sequence;
import spinja.promela.compiler.automaton.ActionTransition;
import spinja.promela.compiler.automaton.ElseTransition;
import spinja.promela.compiler.automaton.EndTransition;
import spinja.promela.compiler.automaton.GotoTransition;
import spinja.promela.compiler.automaton.NeverEndTransition;
import spinja.promela.compiler.automaton.State;
import spinja.promela.compiler.automaton.Transition;
import spinja.promela.compiler.automaton.UselessTransition;
import spinja.promela.compiler.expression.AritmicExpression;
import spinja.promela.compiler.expression.BooleanExpression;
import spinja.promela.compiler.expression.ChannelLengthExpression;
import spinja.promela.compiler.expression.ChannelOperation;
import spinja.promela.compiler.expression.ChannelReadExpression;
import spinja.promela.compiler.expression.CompareExpression;
import spinja.promela.compiler.expression.ConstantExpression;
import spinja.promela.compiler.expression.EvalExpression;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.expression.RemoteRef;
import spinja.promela.compiler.expression.RunExpression;
import spinja.promela.compiler.ltsmin.LTSminDebug.MessageKind;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardAnd;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardContainer;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardNor;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardOr;
import spinja.promela.compiler.ltsmin.model.LTSminModel;
import spinja.promela.compiler.ltsmin.model.LTSminTransition;
import spinja.promela.compiler.ltsmin.model.ReadAction;
import spinja.promela.compiler.ltsmin.model.ReadersAndWriters;
import spinja.promela.compiler.ltsmin.model.ResetProcessAction;
import spinja.promela.compiler.ltsmin.model.SendAction;
import spinja.promela.compiler.ltsmin.model.TimeoutTransition;
import spinja.promela.compiler.ltsmin.state.LTSminStateVector;
import spinja.promela.compiler.optimizer.RenumberAll;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.Preprocessor;
import spinja.promela.compiler.parser.Preprocessor.DefineMapping;
import spinja.promela.compiler.parser.Promela;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;

/**
 * Constructs the LTSminModel by walking over the SpinJa {@link Specification}.
 * First processes are instantiated by copying their CST.
 * 
 * TODO: introduce LTSminState and precisely calculate the crossproduct
 * TODO: get rid of LTSminTransitionCombo
 * 
 * @author Freark van der Berg, Alfons Laarman
 */
public class LTSminTreeWalker {

	// The specification of which the model is created,
	// initialized by constructor
	private final Specification spec;

	private LTSminDebug debug;

	private LTSminModel model = null;
	
	// For each channel, a list of read actions and send actions is kept for later processing
	private HashMap<ChannelVariable,ReadersAndWriters> channels;

	// Maintain transition
	private HashMap<Transition, Set<LTSminTransition>> t2t;

	// List of transition with a TimeoutExpression
    List<TimeoutTransition> timeout_transitions;

	/**
	 * Creates a new LTSMinPrinter using the specified Specification.
	 * @param spec The specification.
	 * @param name The name to give the model.
	 */
	public LTSminTreeWalker(Specification spec) {
		this.spec = spec;
        timeout_transitions = new ArrayList<TimeoutTransition>();
		channels = new HashMap<ChannelVariable,ReadersAndWriters>();
		t2t = new HashMap<Transition, Set<LTSminTransition>>();
	}
		
	/**
	 * generates and returns an LTSminModel to the Specification provided
	 * when creating this LTSMinPrinter instance.
	 * @return The LTSminModel according to the Specification.
	 */
	public LTSminModel createLTSminModel(String name, boolean verbose) {
		//long start_t = System.currentTimeMillis();
		
		this.debug = new LTSminDebug(verbose);
		LTSminStateVector sv = new LTSminStateVector();
		instantiate();
		sv.createVectorStructs(spec, debug);
		bindByReferenceCalls();
		model = new LTSminModel(name, sv, spec);
		addAcceptingConditions();
		createModelTransitions();
		LTSminDMWalker.walkModel(model, debug);
		LTSminGMWalker.walkModel(model, debug);
		
		//long end_t = System.currentTimeMillis();
		return model;
	}

	/**
	 * Set accepting state conditions for this model. 
	 * Accepting condition semantics are overloaded with valid end state semantics.
	 */
    private void addAcceptingConditions() {
		if (null != spec.getNever()) {
			Proctype never = spec.getNever();
			Variable pc = model.sv.getPC(never);
			Expression g = compare(PromelaConstants.EQ, id(pc), constant(-1));
			model.getAcceptingConditions().addGuard(g); // Or
			for (State s : never.getAutomaton()) {
				if (s.isAcceptState()) {
					g = pcGuard(model, s, never);
					model.getAcceptingConditions().addGuard(g);
				}
			}
		} else {
			Expression e = compare(PromelaConstants.EQ, id(_NR_PR), constant(0));
			model.getAcceptingConditions().addGuard(e); // Or
			LTSminGuardAnd and = new LTSminGuardAnd();
	    	for (ProcInstance instance : spec) {
	    		LTSminGuardOr or = new LTSminGuardOr();
				Variable pc = model.sv.getPC(instance);
				Expression g = compare(PromelaConstants.EQ, id(pc), constant(-1));
	    		or.addGuard(g);
		    	for (State s : instance.getAutomaton()) {
			    	if (s.hasLabelPrefix("end")) {
			    		or.addGuard(pcGuard(model, s, instance));
			    	}
		    	}
		    	and.addGuard(or);
	    	}
			model.getAcceptingConditions().addGuard(and);
		}
	}

	private List<RunExpression> runs = new ArrayList<RunExpression>();
    private List<String> iCount = new ArrayList<String>();

    private int getInstanceCount(Proctype p) {
    	DefineMapping nrInstances, original;
    	nrInstances = original = Preprocessor.defines("__instances_"+ p.getName());
		if (null != nrInstances) {
			int count = -1;
			while (-1 == count) try {
				count = Integer.parseInt(nrInstances.defineText.trim());
			} catch (NumberFormatException nf) {
				nrInstances = Preprocessor.defines(nrInstances.defineText.trim());
				if (null == nrInstances) break; 
			}
			if (-1 == count) throw new AssertionError("Cannot parse "+ original);
			return count;
		}
		// query instantiation count from user
		System.out.print("Provide instantiation number for proctype "+ p.getName() +": ");
		InputStreamReader converter = new InputStreamReader(System.in);
		BufferedReader in = new BufferedReader(converter);
		String number;
		try {
			number = in.readLine();
		} catch (IOException e) {throw new AssertionError(e);}
		int num = Integer.parseInt(number);
		iCount.add(p.getName() +" "+ num);
		return num;
    }
    
	/** Active processes can be differentiated from each other by the value of
	 * their process instantiation number, which is available in the predefined
	 * local variable _pid . Active processes are always instantiated in the
	 * order in which they appear in the model, so that the first such process
	 * (whether it is declared as an active process or as an init process) will
	 * receive the lowest instantiation number, which is zero. */
	private void instantiate() {
		List<ProcInstance> instances = new ArrayList<ProcInstance>();
		List<ProcInstance> active = new ArrayList<ProcInstance>();

		int id = 0;
		for (Proctype p : spec.getProcs()) { // add active processes (including init)
			for (int i = 0; i < p.getNrActive(); i++) {
				ProcInstance instance = instantiate(p, id, i);
				p.addInstance(instance);
				active.add(instance);
				id++;
			}
		}

		// set number of processes to initial number of active processes.
		try {
			LTSminStateVector._NR_PR.setInitExpr(constant(id));
		} catch (ParseException e) { assert (false); }

		for (Proctype p : spec.getProcs()) {
			if (0 != p.getNrActive())
				continue;
			int instanceCount = getInstanceCount(p);
			for (int i = 0; i < instanceCount; i++) {
				ProcInstance instance = instantiate(p, id, i);
				p.addInstance(instance);
				instances.add(instance);
				id++;
			}
		}
		if (null != spec.getNever()) {
			Proctype never = spec.getNever();
			ProcInstance n = instantiate(never, -1, -1);
			try {
				spec.setNever(n);
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
		for (String binding : iCount)
			debug.say(MessageKind.NORMAL, "#define __instances_"+ binding);
		for (ProcInstance instance : active)
			instances.add(instance);
		spec.setInstances(instances);
	}

	/**
	 * Copies proctype to an instance.
	 */
	private ProcInstance instantiate(Proctype p, int id, int index) {
		ProcInstance instance = new ProcInstance(p, index, id);
		Expression e = instantiate(p.getEnabler(), instance);
		instance.setEnabler(e);
		for (Variable var : p.getVariables()) {
			Variable newvar = instantiate(var, instance);
			instance.addVariable(newvar, p.getArguments().contains(var));
		}
		instance.lastArgument();
		for (String mapped : p.getVariableMappings().keySet()) {
			String to = p.getVariableMapping(mapped);
			instance.addVariableMapping(mapped, to);
		}
		HashMap<State, State> seen = new HashMap<State, State>();
		instantiate(p.getStartState(), instance.getStartState(), seen, instance);
		new RenumberAll().optimize(instance.getAutomaton());
		return instance;
	}
	
	/**
	* Copy the automaton
	*/
	private void instantiate(State state, State newState,
							 HashMap<State, State> seen, ProcInstance p) {
		if (null == state || null != seen.put(state, newState))
			return;
		newState.setLabels(state.getLabels());
		for (Transition trans : state.output) {
			State next = trans.getTo();
			State newNextState = null;
			if (null != next) if (seen.containsKey(next))
				newNextState = seen.get(next);
			else
				newNextState = new State(p.getAutomaton(), next.isInAtomic());
			Transition newTrans =
				(trans instanceof ActionTransition ? new ActionTransition(newState, newNextState) :
				(trans instanceof ElseTransition ? new ElseTransition(newState, newNextState) :
				(trans instanceof EndTransition ? new EndTransition(newState) :
				(trans instanceof NeverEndTransition ? new NeverEndTransition(newState) :
				(trans instanceof GotoTransition ? new GotoTransition(newState, newNextState, trans.getText().substring(5)) :
				(trans instanceof UselessTransition ? new UselessTransition(newState, newNextState, trans.getText()) :
				 null))))));
			for (Action a : trans)
				newTrans.addAction(instantiate(a, p, null));
			instantiate(next, newNextState, seen, p);
		}
	}

	private Variable instantiate(Variable var, ProcInstance p) {
		if (null == var.getOwner()) // global var, no copy required
			return var;
		if (!p.getTypeName().equals(var.getOwner().getName()))
			throw new AssertionError("Expected instance of type "+ var.getOwner().getName() +" not of "+ p.getTypeName());
		Variable newvar = var instanceof ChannelVariable ?
				new ChannelVariable(var.getName(), var.getArraySize()) :
				new Variable(var.getType(), var.getName(), var.getArraySize());
		newvar.setOwner(p);
		newvar.setType(var.getType());
		newvar.setRealName(var.getRealName());
		try {
			if (null != var.getInitExpr())
				newvar.setInitExpr(instantiate(var.getInitExpr(), p));
		} catch (ParseException e1) { throw new AssertionError("Identifier"); }
		if (newvar.getName().equals(Promela.C_STATE_PID)) {
			int initial_pid = (p.getNrActive() == 0 ? -1 : p.getID());
			try { newvar.setInitExpr(constant(initial_pid));
			} catch (ParseException e) { assert (false); }
		}
		return newvar;
	}

	/**
	 * Copy actions
	 */
	private Action instantiate(Action a, ProcInstance p, OptionAction loop) {
		if(a instanceof AssignAction) {
			AssignAction as = (AssignAction)a;
			Identifier id = (Identifier)instantiate(as.getIdentifier(), p);
			Expression e = instantiate(as.getExpr(), p);
			return new AssignAction(as.getToken(), id, e);
		} else if(a instanceof ResetProcessAction) {
			throw new AssertionError("Unexpected ResetProcessAction");
		} else if(a instanceof AssertAction) {
			AssertAction as = (AssertAction)a;
			Expression e = instantiate(as.getExpr(), p);
			return new AssertAction(as.getToken(), e);
		} else if(a instanceof PrintAction) {
			PrintAction pa = (PrintAction)a;
			PrintAction newpa = new PrintAction(pa.getToken(), pa.getString());
			for (final Expression expr : pa.getExprs())
				newpa.addExpression(instantiate(expr, p));
			return newpa;
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			Expression e = instantiate(ea.getExpression(), p);
			return new ExprAction(e);
		} else if(a instanceof OptionAction) { // options in a d_step sequence
			OptionAction oa = (OptionAction)a;
			OptionAction newoa = new OptionAction(oa.getToken(), oa.loops());
			newoa.hasSuccessor(oa.hasSuccessor());
			loop = newoa.loops() ? newoa : null;
			for (Sequence seq : oa)
				newoa.startNewOption((Sequence)instantiate(seq, p, loop)); 
			return newoa;
		} else if(a instanceof Sequence) {
			Sequence seq = (Sequence)a;
			Sequence newseq = new Sequence(seq.getToken());
			for (Action aa : seq) {
				Action sub = instantiate(aa, p, loop);
				newseq.addAction(sub);
			}
			return newseq;
		} else if(a instanceof BreakAction) {
			BreakAction ba = (BreakAction)a;
			BreakAction newba = new BreakAction(ba.getToken(), loop);
			return newba;
		} else if(a instanceof ElseAction) {
			return a; // readonly, hence can be shared
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			Identifier id = (Identifier)instantiate(csa.getIdentifier(), p);
			ChannelSendAction newcsa = new ChannelSendAction(csa.getToken(), id);
			for (Expression e : csa.getExprs())
				newcsa.addExpression(instantiate(e, p));
			return newcsa;
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			Identifier id = (Identifier)instantiate(cra.getIdentifier(), p);
			ChannelReadAction newcra = new ChannelReadAction(cra.getToken(), id, cra.isPoll());
			for (Expression e : cra.getExprs())
				newcra.addExpression(instantiate(e, p));
			return newcra;
		} else { // Handle not yet implemented action
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}	
	
	/**
	 * Copy expressions with instantiated processes.
	 */
	private Expression instantiate(Expression e, ProcInstance p) {
		if (null == e) return null;

		if (e instanceof Identifier) { // also: LTSminIdentifier
			Identifier id = (Identifier)e;
			Variable var = id.getVariable();
			if (null != var.getOwner()) {
				if (!p.getTypeName().equals(var.getOwner().getName()))
					throw new AssertionError("Expected instance of type "+ var.getOwner().getName() +" not of "+ p.getTypeName());
				var = p.getVariable(var.getName()); // load copied variable
			}
			Expression arrayExpr = instantiate(id.getArrayExpr(), p);
			Identifier sub = (Identifier)instantiate(id.getSub(), p);
			return new Identifier(id.getToken(), var, arrayExpr, sub);
		} else if (e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = instantiate(ae.getExpr1(), p);
			Expression ex2 = instantiate(ae.getExpr2(), p);
			Expression ex3 = instantiate(ae.getExpr3(), p);
			return new AritmicExpression(ae.getToken(), ex1, ex2, ex3);
		} else if (e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = instantiate(be.getExpr1(), p);
			Expression ex2 = instantiate(be.getExpr2(), p);
			return new BooleanExpression(be.getToken(), ex1, ex2);
		} else if (e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			Expression ex1 = instantiate(ce.getExpr1(), p);
			Expression ex2 = instantiate(ce.getExpr2(), p);
			return new CompareExpression(ce.getToken(), ex1, ex2);
		} else if (e instanceof ChannelLengthExpression) {
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			Identifier newid = (Identifier)instantiate(id, p);
			try {
				return new ChannelLengthExpression(cle.getToken(), newid);
			} catch (ParseException e1) {
				throw new AssertionError(e1);
			}
		} else if (e instanceof ChannelReadExpression) {
			ChannelReadExpression cre = (ChannelReadExpression)e;
			Identifier id = (Identifier)instantiate(cre.getIdentifier(), p);
			ChannelReadExpression res = new ChannelReadExpression(cre.getToken(), id);
			for (Expression expr : cre.getExprs())
				res.addExpression(instantiate(expr, p));
			return res;
		} else if (e instanceof ChannelOperation) {
			ChannelOperation co = (ChannelOperation)e;
			Identifier id = (Identifier)instantiate(co.getExpression(), p);
			try {
				return new ChannelOperation(co.getToken(), id);
			} catch (ParseException e1) {
				throw new AssertionError("ChanOp");
			}
		} else if (e instanceof RunExpression) {
			RunExpression re = (RunExpression)e;
			RunExpression newre = new RunExpression(e.getToken(), spec.getProcess(re.getId())); 
			try {
				for (Expression expr : re.getExpressions())
					newre.addExpression(instantiate(expr, p));
			} catch (ParseException e1) {
				throw new AssertionError("RunExpression");
			}
			runs.add(newre); // add runexpression to a list
			return newre;
		} else if (e instanceof EvalExpression) {
			EvalExpression eval = (EvalExpression)e;
			Expression ex = instantiate(eval.getExpression(), p);
			return new EvalExpression(e.getToken(), ex);
		} else if (e instanceof ConstantExpression) {
			return e; // readonly, hence can be shared
		} else if (e instanceof RemoteRef) {
			RemoteRef rr = (RemoteRef)e;
			Expression ex = instantiate(rr.getExpr(), p);
			Proctype proc = spec.getProcess(rr.getProcessName());
			if (null == proc) throw new AssertionError("Wrong process: "+ rr);
			return new RemoteRef(rr.getToken(), proc, rr.getLabel(), ex);
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		}
	}

	/**
	 * Binds any channel type arguments of all RunExpressions by reference.
	 */
	private void bindByReferenceCalls() {
		debug.say(MessageKind.DEBUG, "");
		for (Proctype p : spec.getProcs()) {
			if (p.getNrActive() > 0) continue;
			List<RunExpression> rr = new ArrayList<RunExpression>();
			for (RunExpression re : runs)
				if (re.getProctype().equals(p)) rr.add(re);
			if (rr.size() == 0) {
				debug.say(MessageKind.WARNING, "Process "+ p.getName() +" is inactive.");
				continue;
			}
			if (rr.size() == 1 && p.getInstances().size() > 1) {
				for (ProcInstance target : p.getInstances()) {
					bindArguments(rr.get(0), target, true);
				}
			} else if (rr.size() == p.getInstances().size()) {
				Iterator<ProcInstance> it = p.getInstances().iterator();
				for (RunExpression re : rr) {
					ProcInstance target = it.next();
					re.setInstance(target);
					debug.say(MessageKind.NORMAL, "Statically binding chans of procinstance "+ target +" to run expression at l."+ re.getToken().beginLine);
					bindArguments(re, target, false);
				}
			} else {
				for (ProcInstance target : p.getInstances()) {
					bindArguments(rr.get(0), target, true);
				}
			}
		}
	}

	private void bindArguments(RunExpression re, ProcInstance target,
							   boolean dynamic) {
		if (null == target) throw new AssertionError("Target of run expression is not found: "+ re.getId());
		List<Variable> args = target.getArguments();
		Iterator<Expression> eit = re.getExpressions().iterator();
		if (args.size() != re.getExpressions().size())
			throw error("Run expression's parameters do not match the proc's arguments.", re.getToken());
		//write to the arguments of the target process
		int 			count = 0;
		for (Variable v : args) {
			count++;
			Expression param = eit.next();
			if (v.getType() instanceof ChannelType) {
				if (!(param instanceof Identifier))
					throw error("Run expression's parameter for "+ v +" does not match the proc's argument type.", re.getToken());
				Identifier id = (Identifier)param;
				Variable varParameter = id.getVariable();
				VariableType t = varParameter.getType();
				if (!(t instanceof ChannelType))
					throw error("Parameter "+ count +" of "+ re.getId() +" should be a channeltype.", re.getToken());
				ChannelType ct = (ChannelType)t;
				if (ct.getBufferSize() == -1)
					throw error("Could not deduce channel declaration for parameter "+ count +" of "+ re.getId() +".", re.getToken());
				if (dynamic || varParameter.getArraySize() > -1)
					throw new AssertionError("Cannot dynamically bind "+ target.getTypeName() +" to the run expressions in presence of arguments of type channel.\n" +
							"Change the proctype's arguments or unroll the loop with run expressions in the model.");
				String name = v.getName();
				debug.say(MessageKind.DEBUG, "Binding "+ target +"."+ name +" to "+ varParameter.getOwner() +"."+ varParameter.getName());
				v.setRealName(v.getName()); //TODO: this is also a variable mapping
				v.setType(varParameter.getType());
				v.setOwner(varParameter.getOwner());
				v.setName(varParameter.getName());
			}
		}
		for (Variable v : target.getVariables()) {
			if (null == v.getInitExpr()) continue;
			try {
				v.getInitExpr().getConstantValue();
			} catch (ParseException e) {
				if (dynamic)
					throw new AssertionError("Cannot dynamically bind "+ target.getTypeName() +" to the run expressions in presence of arguments of init expressions that use the arguments.\n" +
							"Change the proctype's arguments or unroll the loop with run expressions in the model.");
				Expression init = v.getInitExpr();
				v.unsetInitExpr();
				re.addAction(new AssignAction(new Token(PromelaConstants.ASSIGN), id(v), init));
			}
		}
	}

	/**
	 * Run expressions usually pass constants to channel variables. If these
	 * variables are never assigned to elsewhere, we can safely mark them
	 * constant.
	 */
	private void createProcessConstantVars() {
		for (RunExpression re : runs){
			for (Proctype p : re.getInstances()) {
				Iterator<Expression> rei = re.getExpressions().iterator();
				for (Variable v : p.getArguments()) {
					Expression next = rei.next();
					if (v.getType() instanceof ChannelType) continue; //passed by reference
					if (v.isNotAssignedTo()) {
						try {
							v.setConstantValue(next.getConstantValue());
						} catch (ParseException e) {} // expected
					}
				}
			}
		}
	}

	private Iterable<State> getNeverAutomatonOrNullSet(boolean forceNullSet) {
		if (forceNullSet || spec.getNever()==null)
			return new HashSet<State>(Arrays.asList((State)null));
		return spec.getNever().getAutomaton();
	}
	
	private Iterable<Transition> getOutTransitionsOrNullSet(State s) {
		if (s==null)
			return new HashSet<Transition>(Arrays.asList((Transition)null));
		return s.output;
	}
	
	/**
	 * Creates the state transitions.
	 */
	private int createModelTransitions() {
		int trans = 0;
		debug.say(MessageKind.DEBUG, "");

		// Create the normal transitions for all processes.
		// This excludes: rendezvous, else, timeout and loss of atomicity
		// Calculate cross product with the never claim when not in atomic state 
		for (ProcInstance p : spec) {
			debug.say(MessageKind.DEBUG, "[Proc] " + p.getName());
			for (State state : p.getAutomaton()) {
				if (0 == state.sizeOut())
					state.newTransition(null);
				for (Transition t : state.output) {
					if (collectRendezVous(p, t, trans)) continue;
					trans = createCrossProduct(p, trans, t);
				}
			}
		}
		
		createProcessConstantVars();
		
		// create the rendezvous transitions
		for (Map.Entry<ChannelVariable,ReadersAndWriters> e : channels.entrySet()) {
			for (SendAction sa : e.getValue().sendActions) {
				for (ReadAction ra : e.getValue().readActions) {
					for (State ns : getNeverAutomatonOrNullSet(false)) {
						for (Transition nt : getOutTransitionsOrNullSet(ns)) {
							trans = createRendezVousTransition(sa,ra,trans,nt);
						}
					}
				}
			}
		}

		// detect atomic sub blocks
		for (LTSminTransition t : model.getTransitions()) {
			if (!(t.isAtomic()))
				continue;
			HashSet<State> seen = new HashSet<State>();
			State state = t.getTransition().getTo();
			Transition other = t.passesControlAtomically();
			if (null != other) state = other.getTo();
			reachability(state, seen, t);
			//System.out.println(tc +" --> "+ tc.transitions);
		}

		// let never automata continue on deadlock FIXME
		if (null != spec.getNever()) {
			for (State ns : spec.getNever().getAutomaton()) {
				for (Transition nt : ns.output) {
					LTSminTransition lt = makeTransition(spec.getNever(), trans, nt, null, null);
					lt.addGuard(compare(PromelaConstants.EQ, id(_NR_PR), 0));
					addNever(lt, nt);
					model.getTransitions().add(lt);
					trans++;
				}
			}
		}

		if (model.getTransitions().size() != trans)
			throw new AssertionError("Transition not set at correct location in the transition array");

		return trans;
	}

	/**
	 * Add all reachable atomic transitions to tc
	 */
	private void reachability(State state, HashSet<State> seen,
							  LTSminTransition t) {
		if (state == null || !state.isInAtomic()) return;
		if (!seen.add(state)) return;
		for (Transition original : state.output) {
			Set<LTSminTransition> set = t2t.get(original);
			if (null == set) { // should be a rendez-vous read transition (loss of atomicity)
				Action a = original.iterator().next();
				if (a instanceof ChannelReadAction) {
					ChannelReadAction send = (ChannelReadAction)a;
					ChannelType ct = (ChannelType)send.getIdentifier().getVariable().getType();
					if (0 == ct.getBufferSize()) continue; // rendez-vous read
				}
				throw new AssertionError("No transition created for "+ original);
			}
			for (LTSminTransition lt : set) {
				t.addTransition(lt);
				Transition other = lt.passesControlAtomically();
				if (null != other) {
					reachability(other.getTo(), seen, t);
				}
			}
			reachability(original.getTo(), seen, t);
		}
	}

	/**
	 * Creates all transitions from the given state. This state should be
	 * in the specified process.
	 */
	private int createCrossProduct (ProcInstance process, int trans, Transition t) {
		++debug.say_indent;
		debug.say(MessageKind.DEBUG, t.toString());

		for (State ns : getNeverAutomatonOrNullSet(t.getFrom().isInAtomic())) {
			if (null != ns && 0 == ns.sizeOut())
				ns.newTransition(null);
			for (Transition never_t : getOutTransitionsOrNullSet(ns)) {
				LTSminTransition lt = createStateTransition(process,trans,t,never_t);
				model.getTransitions().add(lt);
				trans++;
			}
		}
		
		--debug.say_indent;
		return trans; // Return the next free transition ID
	}

	private LTSminTransition createStateTransition(ProcInstance process, int trans,
											Transition t, Transition never_t) {
		++debug.say_indent;
		if(never_t!=null) {
			debug.say(MessageKind.DEBUG, "Handling trans: " + t.getClass().getName() + " || " + never_t.getClass().getName());
		} else {
			debug.say(MessageKind.DEBUG, "Handling trans: " + t.getClass().getName());
		}
		--debug.say_indent;

		LTSminTransition lt = makeTransition(process, trans, t, never_t, null);

        addNever(lt, never_t); // sync with never transition

		lt.addGuard(pcGuard(model, t.getFrom(), process)); // process counter
        createEnabledGuard(t, lt); // enabled action or else transition 
		if (null != process.getEnabler())
			lt.addGuard(process.getEnabler()); // process enabler (provided keyword)
		if (t.getTo() == null)
			lt.addGuard(dieGuard(model, process)); // allowed to die (stack order)
		lt.addGuard(inAtomicGuard(model, process)); // atomic

		// Create actions of the transition, iff never is absent, dying or not atomic
		if  (never_t == null || null == never_t.getTo() || !never_t.getTo().isInAtomic()) {
			if (t.getTo()==null) {
				lt.addAction(new ResetProcessAction(process));
			} else { // Action: PC counter update
				lt.addAction(assign(model.sv.getPC(process), t.getTo().getStateId()));
			}
			// Actions: transition
			for (Action action : t) {
	            lt.addAction(action);
	        }
		}

		return lt;
	}

	private void addNever(LTSminTransition lt, Transition never_t)
			throws AssertionError {
        if (never_t != null) {
        	if (null != never_t.getTo() && (never_t.getTo().isInAtomic()) ||
        									never_t.getFrom().isInAtomic())
        		throw new AssertionError("Atomic in never claim not implemented");
			lt.addGuard(pcGuard(model, never_t.getFrom(), spec.getNever()));
	        createEnabledGuard(never_t, lt);
	        lt.addAction(assign(model.sv.getPC(spec.getNever()),
						never_t.getTo()==null?-1:never_t.getTo().getStateId()));
		}
	}

	private LTSminTransition makeTransition(Proctype process, int trans,
							Transition t, Transition never_t, Transition sync_t) {
		LTSminTransition lt = new LTSminTransition(trans, t, sync_t, never_t, process);
		Set <LTSminTransition> set = t2t.get(t);
		if (null == set) {
			set = new HashSet<LTSminTransition>();
			t2t.put(t, set);
		}
		set.add(lt);
		return lt;
	}
	
	/**
	 * Creates the guard of a transition for its action and for the end states.
	 * @param t The transition of which the guard will be created.
	 * @param trans The transition group ID to use for generation.
	 */
	private void createEnabledGuard(Transition t, LTSminGuardContainer lt) {
        if (t instanceof ElseTransition) {
            ElseTransition et = (ElseTransition)t;
        	LTSminGuardNor nor = new LTSminGuardNor();
            for (Transition ot : t.getFrom().output) {
                if (ot != et) {
                    createEnabledGuard(ot, nor);
                }
            }
            lt.addGuard(nor);
        } else if (t.getActionCount() > 0 ) {
			createEnabledGuard(t.getAction(0), lt);
        }
	}

	/**
	 * Creates the guards denoting when the specified Action is enabled.
	 * The enabledness of rendezvous channel actions can only be determined
	 * after all other transitions have been visited (when seenItAll is true).
	 * 
	 * Also records the assignTo property of identifier, to detect constants later. 
	 * 
	 * @param process The action should be in this process.
	 * @param a The action for which the guard is created.
	 * @param t The transition the action is in.
	 * @param trans The transition group ID to use for generation.
	 * @throws ParseException
	 */
	public static void createEnabledGuard(Action a, LTSminGuardContainer lt) {
		if (a instanceof AssignAction) {
			AssignAction ae = (AssignAction)a;
			ae.getIdentifier().getVariable().setAssignedTo();
		} else if(a instanceof AssertAction) {
		} else if(a instanceof PrintAction) {
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			lt.addGuard(ea.getExpression());
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			ChannelVariable var = (ChannelVariable)csa.getIdentifier().getVariable();
			if(var.getType().getBufferSize()>0) {
				lt.addGuard(chanEmptyGuard(csa.getIdentifier()));
			} else {
				throw new AssertionError("Trying to actionise rendezvous send before all others! "+ var);
			}
		} else if(a instanceof OptionAction) { // options in a d_step sequence
			OptionAction oa = (OptionAction)a;
			LTSminGuardOr orc = new LTSminGuardOr();
			for (Sequence seq : oa) {
				Action act = seq.iterator().next(); // guaranteed by parser
				if (act instanceof ElseAction)
					return; // options with else have a vacuously true guard
				createEnabledGuard(act, orc);
			}
			lt.addGuard(orc);
		} else if(a instanceof ElseAction) {
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			Identifier id = cra.getIdentifier();
			ChannelVariable cv = (ChannelVariable)id.getVariable();
			if(cv.getType().getBufferSize()>0) {
				List<Expression> exprs = cra.getExprs();
				lt.addGuard(chanContentsGuard(id));
				// Compare constant arguments with channel content
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (!(expr instanceof Identifier)) {
						Identifier elem = id(elemVar(i));
						Identifier buf = id(bufferVar(cv), constant(0), elem);
						Identifier next = new Identifier(id, buf);
						lt.addGuard(compare(PromelaConstants.EQ,next,expr));
					} else {
						((Identifier)expr).getVariable().setAssignedTo(); // FIXME: WRONG, only done for first action! use setWritten (avoid arguments)
					}
				}
			} else {
				throw new AssertionError("Trying to actionise rendezvous receive before all others!");
			}
		} else { //unsupported action
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	/**
 	 * Collects else transition or rendezvous enabled action for later processing 
	 * Check only for the normal process, not for the never claim
	 * 
	 * For rendezvous actions we first need to calculate a cross product to
	 * determine enabledness, therefore else transitions have to be processed even later.   
	 * 
	 * The never claim process is not allowed to contain message passing
	 * statements.
	 * "This means that a never claim may not contain assignment or message
	 * passing statements." @ http://spinroot.com/spin/Man/never.html)
	 */
	private boolean collectRendezVous(ProcInstance process, Transition t, int trans) {
		if (t.iterator().hasNext()) {
			Action a = t.iterator().next();
			if (a instanceof ChannelSendAction) {
				ChannelSendAction csa = (ChannelSendAction)a;
				ChannelVariable var = (ChannelVariable)csa.getIdentifier().getVariable();
				if(var.getType().getBufferSize()==0) {
					ReadersAndWriters raw = channels.get(var);
					if (raw == null) {
						raw = new ReadersAndWriters();
						channels.put(var, raw);
					}
					raw.sendActions.add(new SendAction(csa,t,process));
					return true;
				}
			} else if (a instanceof ChannelReadAction) {
				ChannelReadAction cra = (ChannelReadAction)a;
				ChannelVariable var = (ChannelVariable)cra.getIdentifier().getVariable();
				if (var.getType().getBufferSize()==0) {
					if (!cra.isNormal()) debug.say(MessageKind.ERROR, "Abnormal receive on rendez-vous channel.");
					ReadersAndWriters raw = channels.get(var);
					if (raw == null) {
						raw = new ReadersAndWriters();
						channels.put(var, raw);
					}
					raw.readActions.add(new ReadAction(cra,t,process));
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Creates the transition for one rendezvous couple. The specified
	 * transition ID will be used to identify the created transition.
	 * 
	 * "If an atomic sequence contains a rendezvous send statement, control
	 * passes from sender to receiver when the rendezvous handshake completes."
	 */
	private int createRendezVousTransition(SendAction sa, ReadAction ra,
										   int trans, Transition never_t) {
		if (sa.p == ra.p) return trans; // skip impotent matches
		ChannelSendAction csa = sa.csa;
		ChannelReadAction cra = ra.cra;
		List<Expression> csa_exprs = csa.getExprs();
		List<Expression> cra_exprs = cra.getExprs();
		Identifier sendId = sa.csa.getIdentifier();
		Identifier recvId = ra.cra.getIdentifier();
		Expression array1 = null, array2 = null;
		if (sendId.getVariable().getArraySize() > -1) { // array of channels
			assert (recvId.getVariable().getArraySize() > -1);
			array1 = recvId.getArrayExpr();
			array2 = sendId.getArrayExpr();
			if (array1 == null) array1 = constant(0);
			if (array2 == null) array2 = constant(0);
			try { array1 = constant(array1.getConstantValue());
			} catch (ParseException e) {}
			try { array2 = constant(array2.getConstantValue());
			} catch (ParseException e) {}
			try { // we skip creating transitions for impotent matches:
				if (array1.getConstantValue() != array2.getConstantValue())
					return trans;
			} catch (ParseException e) {}
		}
		for (int i = 0; i < cra_exprs.size(); i++) {
			final Expression csa_expr = csa_exprs.get(i);
			final Expression cra_expr = cra_exprs.get(i);
			try { // we skip creating transitions for impotent matches:
				if (csa_expr.getConstantValue() != cra_expr.getConstantValue())
					return trans;
			} catch (ParseException pe) {}
		}
		LTSminTransition lt = makeTransition(ra.p, trans, sa.t, never_t, ra.t);

        addNever(lt, never_t);

		lt.addGuard(pcGuard(model, sa.t.getFrom(), sa.p));
		lt.addGuard(pcGuard(model, ra.t.getFrom(), ra.p));
		if (sendId.getVariable().getArraySize() > -1) { // array of channels
			lt.addGuard(compare(PromelaConstants.EQ, array1, array2));
		}

		/* Channel matches */
		for (int i = 0; i < cra_exprs.size(); i++) {
			final Expression csa_expr = csa_exprs.get(i);
			final Expression cra_expr = cra_exprs.get(i);
			if (cra_expr instanceof Identifier) {
				lt.addAction(assign((Identifier)cra_expr,csa_expr));
			} else {
				lt.addGuard(compare(PromelaConstants.EQ,csa_expr,cra_expr));
			}
		}

		// Change process counter of sender
		lt.addAction(assign(model.sv.getPC(sa.p), sa.t.getTo().getStateId()));
		// Change process counter of receiver
		lt.addAction(assign(model.sv.getPC(ra.p), ra.t.getTo().getStateId()));

		lt.addGuard(inAtomicGuard(model, sa.p));
		if (ra.t.getTo().isInAtomic()) {
			lt.passesControlAtomically(ra.t); // control passes from sender to receiver
		}
		model.getTransitions().add(lt);
		return trans + 1;
	}
}
