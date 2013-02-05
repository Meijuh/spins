// Copyright 2010, University of Twente, Formal Methods and Tools group
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package spins.promela.compiler.expression;

import java.util.Set;

import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.VariableAccess;
import spins.promela.compiler.variable.VariableType;

public class EvalExpression extends Expression {
	private final Expression expr;

	public EvalExpression(final Token token, final Expression expr) {
		super(token);
		this.expr = expr;
	}

	public Expression getExpression() {
		return expr;
	}

	@Override
	public String getBoolExpression() throws ParseException {
		return expr.getBoolExpression();
	}

	@Override
	public int getConstantValue() throws ParseException {
		return expr.getConstantValue();
	}

	@Override
	public String getIntExpression() throws ParseException {
		return expr.getIntExpression();
	}

	@Override
	public VariableType getResultType() {
		return expr.getResultType();
	}

	@Override
	public String getSideEffect() {
		return expr.getSideEffect();
	}

	@Override
	public Set<VariableAccess> readVariables() {
		return expr.readVariables();
	}

	@Override
	public String toString() {
		return "eval(" + expr.toString() + ")";
	}
}
