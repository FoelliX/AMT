package de.upb.mike.amt.conflicresolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.foellix.aql.datastructure.Hash;
import de.foellix.aql.helper.Helper;
import de.upb.mike.amt.Data;
import de.upb.mike.amt.Log;
import de.upb.mike.amt.SootObjHashed;
import soot.Body;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.Jimple;
import soot.jimple.ParameterRef;
import soot.jimple.Ref;
import soot.jimple.ThisRef;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JInstanceFieldRef;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JNewExpr;
import soot.jimple.internal.JSpecialInvokeExpr;
import soot.jimple.internal.JStaticInvokeExpr;

public class StatementAdapter {
	private Set<SootClass> classes;
	private Hash hash;
	private SootClass sc;
	private Body body;

	public StatementAdapter(Set<SootClass> classes, Hash hash, SootClass sc, Body body) {
		super();
		this.classes = classes;
		this.hash = hash;
		this.sc = sc;
		this.body = body;
	}

	public boolean adaptStatement(Unit su, List<Unit> removeList, List<String> removeMethodSignatureList) {
		// Parameters and this
		if (su instanceof JIdentityStmt) {
			IdentityStmt si = (JIdentityStmt) su;
			if (si.getRightOp() instanceof ThisRef || si.getRightOp() instanceof ParameterRef) {
				if (si.getRightOp() instanceof ThisRef) {
					Ref newRef = Jimple.v().newThisRef(RefType.v(sc));
					IdentityStmt newSi = Jimple.v().newIdentityStmt(si.getLeftOp(), newRef);
					if (insert(newSi, si)) {
						removeList.add(si);
						return true;
					}
				} else if (si.getRightOp() instanceof ParameterRef) {
					Ref oldRef = (ParameterRef) si.getRightOp();
					Ref newRef = Jimple.v().newParameterRef(
							RefType.v(Data.getInstance().getClassesChanged()
									.get(new SootObjHashed(((ParameterRef) oldRef).getType().toString(), hash))),
							((ParameterRef) oldRef).getIndex());
					IdentityStmt newSi = Jimple.v().newIdentityStmt(si.getLeftOp(), newRef);
					if (insert(newSi, si)) {
						removeList.add(si);
						return true;
					}
				}
			}
		}

		// Assignments
		if (su instanceof JAssignStmt) {
			AssignStmt si = (JAssignStmt) su;
			if (si.getLeftOp() instanceof JInstanceFieldRef) {
				Ref oldRef = (JInstanceFieldRef) si.getLeftOp();
				Ref newRef = Jimple.v().newInstanceFieldRef(((JInstanceFieldRef) oldRef).getBase(),
						getFieldByName(((JInstanceFieldRef) oldRef).getBase().getType().toString(),
								((JInstanceFieldRef) oldRef).getFieldRef().name()).makeRef());
				AssignStmt newSi = Jimple.v().newAssignStmt(newRef, si.getRightOp());
				if (insert(newSi, si)) {
					removeList.add(si);
					return true;
				}
			} else if (si.getRightOp() instanceof JInstanceFieldRef) {
				Ref oldRef = (JInstanceFieldRef) si.getRightOp();
				Ref newRef = Jimple.v().newInstanceFieldRef(((JInstanceFieldRef) oldRef).getBase(),
						getFieldByName(((JInstanceFieldRef) oldRef).getBase().getType().toString(),
								((JInstanceFieldRef) oldRef).getFieldRef().name()).makeRef());
				AssignStmt newSi = Jimple.v().newAssignStmt(si.getLeftOp(), newRef);
				if (insert(newSi, si)) {
					removeList.add(si);
					return true;
				}
			} else if (si.getRightOp() instanceof JNewExpr) {
				Type oldType = ((JNewExpr) si.getRightOp()).getType();
				RefType newType = RefType
						.v(Data.getInstance().getClassesChanged().get(new SootObjHashed(oldType.toString(), hash)));
				AssignStmt newSi = Jimple.v().newAssignStmt(si.getLeftOp(), Jimple.v().newNewExpr(newType));
				if (insert(newSi, si)) {
					removeList.add(si);
					return true;
				}
			} else if (si.getRightOp() instanceof JStaticInvokeExpr) {
				JStaticInvokeExpr sp = (JStaticInvokeExpr) si.getRightOp();
				SootMethod oldMethod = sp.getMethod();
				SootMethod newMethod = oldMethod.getDeclaringClass().getMethod(oldMethod.getName(),
						refreshedParameterTypes(oldMethod));
				AssignStmt newSi = Jimple.v().newAssignStmt(si.getLeftOp(),
						Jimple.v().newStaticInvokeExpr(newMethod.makeRef(), sp.getArgs()));
				if (insert(newSi, si)) {
					removeList.add(si);
					return true;
				}
			} else if (si.getRightOp() instanceof JSpecialInvokeExpr) {
				JSpecialInvokeExpr sp = (JSpecialInvokeExpr) si.getRightOp();
				SootMethod oldMethod = sp.getMethod();
				Type returnType = RefType.v(Data.getInstance().getClassesChanged()
						.get(new SootObjHashed(oldMethod.getReturnType().toString(), hash)));
				SootMethod newMethod = oldMethod.getDeclaringClass().getMethod(oldMethod.getName(),
						refreshedParameterTypes(oldMethod), returnType);
				AssignStmt newSi = Jimple.v().newAssignStmt(si.getLeftOp(), Jimple.v().newSpecialInvokeExpr(
						getLocalByName(sp.getBase().toString()), newMethod.makeRef(), sp.getArgs()));
				if (insert(newSi, si)) {
					removeList.add(si);
					return true;
				}
			}
		}

		// Special-invokes
		if (su instanceof JInvokeStmt) {
			JInvokeStmt oldSi = (JInvokeStmt) su.clone();
			JInvokeStmt si = (JInvokeStmt) su;
			if (si.getInvokeExpr() instanceof JSpecialInvokeExpr) {
				JSpecialInvokeExpr sp = (JSpecialInvokeExpr) si.getInvokeExpr();
				SootMethod oldMethod = sp.getMethod();
				String oldSignature = new String(oldMethod.getSubSignature());
				String oldName = Helper.cut(oldSi.toString(), "<", ":");
				String newClassName = Data.getInstance().getClassesChanged().get(new SootObjHashed(oldName, hash));
				SootMethod newMethod;
				if (newClassName != null) {
					newMethod = Scene.v().getSootClass(newClassName).getMethod(oldMethod.getName(),
							refreshedParameterTypes(oldMethod));
				} else {
					if (oldName.equals(oldMethod.getDeclaringClass().getName())) {
						newMethod = oldMethod.getDeclaringClass().getMethod(oldMethod.getName(),
								refreshedParameterTypes(oldMethod));
					} else {
						newMethod = Scene.v().getSootClass(oldName).getMethod(oldMethod.getName(),
								refreshedParameterTypes(oldMethod));
					}
				}
				si.setInvokeExpr(Jimple.v().newSpecialInvokeExpr(getLocalByName(sp.getBase().toString()),
						newMethod.makeRef(), sp.getArgs()));
				if (!si.toString().equals(oldSi.toString())) {
					removeMethodSignatureList.add(oldSignature);
					Log.log("Statement: \"" + oldSi + "\" adapted to: \"" + si + "\"", Log.LOG_LEVEL_DETAILED);
				}
			}
		}

		return false;
	}

	private List<Type> refreshedParameterTypes(SootMethod method) {
		List<Type> types = new ArrayList<>();
		for (Type st : method.getParameterTypes()) {
			String oldType = st.toString();
			String newType = Data.getInstance().getClassesChanged().get(new SootObjHashed(oldType, hash));
			if (newType != null) {
				types.add(RefType.v(newType));
			} else {
				types.add(st);
			}
		}
		return types;
	}

	private Local getLocalByName(String name) {
		for (Local local : body.getLocals()) {
			if (local.getName().equals(name)) {
				return local;
			}
		}
		return null;
	}

	private SootField getFieldByName(String classname, String fieldname) {
		for (SootClass temp : classes) {
			if (temp.getName().equals(classname)) {
				return temp.getFieldByName(fieldname);
			}
		}
		return null;
	}

	private boolean insert(Unit newSi, Unit si) {
		if (!newSi.toString().equals(si.toString())) {
			body.getUnits().insertAfter(newSi, si);
			Log.log("Statement: \"" + si + "\" adapted to: \"" + newSi + "\"", Log.LOG_LEVEL_DETAILED);
			return true;
		}
		return false;
	}
}