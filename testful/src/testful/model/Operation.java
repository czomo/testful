/*
 * TestFul - http://code.google.com/p/testful/
 * Copyright (C) 2010  Matteo Miraz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package testful.model;

import java.io.Serializable;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import jmetal.base.Variable;
import testful.utils.ElementManager;
import ec.util.MersenneTwisterFast;

public abstract class Operation implements Serializable, Cloneable, Variable {

	private static final long serialVersionUID = -4200667624940186523L;

	protected final int hashCode;

	protected Operation(int hashCode) {
		this.hashCode = hashCode;
	}

	private ElementManager<String, OperationInformation> infos;

	/**
	 * Adds the given operationInformation.
	 * If the operation already contains an operation information with the same key, this operation does not do anything.
	 * @param info the operation information to add
	 */
	public void addInfo(OperationInformation info) {
		if(infos == null) infos = new ElementManager<String, OperationInformation>();
		infos.put(info);
	}

	/**
	 * Adds all the information contained in the iterator
	 * @param iter the iterator containing the information to copy
	 */
	public void addInfo(Iterator<OperationInformation> iter) {
		while(iter.hasNext())
			addInfo(iter.next().clone());
	}

	/**
	 * Adds the given operationInformation.
	 * This operation removes any pre-existent operation information with the same key.
	 * @param info the operation information to add
	 */
	public void setInfo(OperationInformation info) {
		if(infos == null) infos = new ElementManager<String, OperationInformation>();
		infos.putAndReplace(info);
	}

	public OperationInformation removeInfo(String key) {
		if(infos == null || key == null) return null;

		OperationInformation ret = infos.remove(key);
		if(infos.isEmpty()) infos = null;
		return ret;
	}

	public OperationInformation getInfo(String key) {
		if(infos == null || key == null) return null;
		return infos.get(key);
	}

	public Iterator<OperationInformation> getInfos() {
		if(infos == null) return ElementManager.getEmptyIterator();
		return infos.iterator();
	}

	/** Returns the textual form of the operation, usable in a java program */
	@Override
	public abstract String toString();

	/**
	 * Returns the hash of the current Operation.
	 * Other parts of Testful requires that the hash code calculus is deterministic
	 * (i.e., it does not depend on random variation such as the location in memory, like the
	 * Object.hashCode).<br/>
	 * Each subclass must calculate its hash code in the constructor, and set the hashCode property.
	 */
	@Override
	public final int hashCode() {
		return hashCode;
	}

	@Override
	public abstract boolean equals(Object o);

	@Override
	public abstract Operation clone();

	protected static final transient Set<Reference> emptyRefsSet = new HashSet<Reference>();

	private transient Set<Reference> defs = null;
	protected abstract Set<Reference> calculateDefs();
	public Set<Reference> getDefs() {
		if(defs == null) defs = calculateDefs();
		return defs;
	}
	private transient BitSet defsBitset = null;
	public BitSet getDefsBitset() {
		if(defsBitset == null) {
			defsBitset = new BitSet();
			for(Reference u : getDefs())
				defsBitset.set(u.getId());
		}

		return defsBitset;
	}

	private transient Set<Reference> uses = null;
	protected abstract Set<Reference> calculateUses();
	public Set<Reference> getUses() {
		if(uses == null) uses = calculateUses();
		return uses;
	}
	private transient BitSet usesBitset = null;
	public BitSet getUsesBitset() {
		if(usesBitset == null) {
			usesBitset = new BitSet();
			for(Reference u : getUses())
				usesBitset.set(u.getId());
		}

		return usesBitset;
	}

	/**
	 * Determine if this operation can be swapped with the other operation
	 * (possible only if they are independent)
	 * @param other the other operation
	 * @return true if the two operations can be swapped safely
	 */
	public boolean swappable(Operation other) {
		if(this instanceof ResetRepository || other instanceof ResetRepository)
			return false;

		Set<Reference> use1 = getUses();
		Set<Reference> def1 = getDefs();

		Set<Reference> use2 = other.getUses();
		Set<Reference> def2 = other.getDefs();

		for(Reference d : def1) {
			if(use2.contains(d)) return false;
			if(def2.contains(d)) return false;
		}

		for(Reference d : def2) {
			if(use1.contains(d)) return false;
			// useless
			// if(def1.contains(d)) return false;
		}

		return true;
	}

	/**
	 * Determine if there is a def-use
	 * @param defs the set of defs
	 * @param uses the set of uses
	 * @return true if exists a def which sets a reference,
	 * 							and exists a use which reads the same reference
	 */
	public static boolean existDefUse(Set<Reference> defs, Set<Reference> uses) {
		for(Reference d : defs)
			if(uses.contains(d))
				return true;

		return false;
	}

	// generation probabilities
	public static float WORK_ON_CUT = .28f;
	public static float SELECT_SUBCLASS = .35f;
	public static float GEN_NEW = 0.35f;

	public static float USE_CONSTANTS = 0.15f;
	public static float SET_TO_NULL = 0.05f;

	public static float GEN_BASIC_VALUES = .40f;
	public static float LIMITED_VALUES = .50f;

	private static final AtomicLong idGenerator = new AtomicLong();

	public static Operation randomlyGenerate(TestCluster cluster, ReferenceFactory refFactory, MersenneTwisterFast random) {
		while(true) {
			Operation op = null;
			try {
				Clazz c;
				if(random.nextBoolean(WORK_ON_CUT)) c = cluster.getCut();
				else c = cluster.getCluster(random.nextInt(cluster.getClusterSize()));

				if(random.nextBoolean(GEN_NEW)) {
					if(random.nextBoolean(USE_CONSTANTS)) op = CreateObject.generate(idGenerator, c, cluster, refFactory, random);
					else if(c instanceof PrimitiveClazz || c.getClassName().equals("java.lang.String")) op = AssignPrimitive.generate(c, cluster, refFactory, random);
					else op = AssignConstant.generate(c, cluster, refFactory, random);
				} else op = Invoke.generate(c, cluster, refFactory, random);
				if(op != null) return op;
			} catch(Throwable e) {
				Logger.getLogger("testful.model").log(Level.WARNING, "Cannot create a random element: " + e.getMessage(), e);
			}
		}
	}

	protected static Reference generateRef(Clazz c, TestCluster cluster, ReferenceFactory refFactory, MersenneTwisterFast random) {
		while(random.nextFloat() < SELECT_SUBCLASS && c.getSubClasses().length > 0)
			c = c.getSubClasses()[random.nextInt(c.getSubClasses().length)];

		return refFactory.getReference(c, random);
	}

	/**
	 * Adapts the operation on cluster and refFactory
	 *
	 * @return the new operation
	 */
	public abstract Operation adapt(TestCluster cluster, ReferenceFactory refFactory);

	public static Operation[] adapt(Operation[] ops, TestCluster cluster, ReferenceFactory refFactory) {
		Operation[] ret = new Operation[ops.length];
		for (int i = 0; i < ops.length; i++)
			ret[i] = ops[i].adapt(cluster, refFactory);
		return ret;
	}
}
