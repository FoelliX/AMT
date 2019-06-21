package de.upb.mike.amt.soot;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import de.foellix.aql.datastructure.Hash;
import de.upb.mike.amt.Config;
import de.upb.mike.amt.Data;
import de.upb.mike.amt.Log;
import de.upb.mike.amt.SootObjHashed;
import de.upb.mike.amt.conflicresolution.ConflictResolution;
import de.upb.mike.amt.conflicresolution.StatementAdapter;
import soot.Body;
import soot.Local;
import soot.Modifier;
import soot.RefType;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.SootMethodRefImpl;
import soot.Type;
import soot.Unit;
import soot.VoidType;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.IdentityStmt;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.ParameterRef;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.TableSwitchStmt;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JNewExpr;
import soot.util.Chain;

public class InstrumentationTransformer extends SceneTransformer {
	private static final List<String> ACTIVITY_LIFECYCLE_METHODS = Arrays.asList("onCreate", "onResume", "onStart",
			"onRestart", "onPause", "onStop", "onDestory", "OriginalonCreate", "OriginalonResume", "OriginalonStart",
			"OriginalonRestart", "OriginalonPause", "OriginalonStop", "OriginalonDestory");

	private SootClass launcherActivityClass;
	private SootMethod launcherActivityInitMethod;
	private Body dummyMethodBody;

	private List<String> lcMethodsToCreate = new ArrayList<String>();
	private HashSet<SootMethod> lifecycleMethodsSet = new HashSet<SootMethod>();
	private static Map<String, SootMethod> initMethodMap = new HashMap<String, SootMethod>();

	@Override
	protected void internalTransform(String phaseName, Map<String, String> options) {
		Chain<SootClass> appClasses = Scene.v().getApplicationClasses();

		// Handle launcher classes
		for (SootClass sc : appClasses) {
			if (sc.toString().contains(SootObject.getInstance().getLauncherActivity()) && hasActivitySuperClass(sc)
					&& !sc.isInterface()) {
				launcherActivityClass = sc;
				for (int i = 0; i < sc.getMethods().size(); i++) {
					SootMethod m = sc.getMethods().get(i);
					if (ACTIVITY_LIFECYCLE_METHODS.contains(m.getName()) && !m.getName().contains("Original")) {
						if (!lcMethodsToCreate.contains(m.getName())) {
							lcMethodsToCreate.add(m.getName());
						}
						lifecycleMethodsSet.add(m);
						i = 0;

						// Rename method
						String originalSignature = m.getSignature();
						m.setName("Original" + m.getName());
						Data.getInstance().getLifecycleMethodSignaturesChanged().put(originalSignature,
								m.getSignature());
						Log.log("Method " + originalSignature + " modified to " + m.getSignature(),
								Log.LOG_LEVEL_DETAILED);
					} else if (m.getName().equals(SootMethod.constructorName)) {
						initMethodMap.put(sc.toString(), m);
						launcherActivityInitMethod = m;
					}
				}
				break;
			}
		}
		if (launcherActivityClass == null) {
			Log.log("Could not find launcher Activity!", Log.LOG_LEVEL_ERROR);
		}

		// Conflict Handling for classes
		ConflictResolution conflictResolution = new ConflictResolution(appClasses);
		int depth = 0;
		for (SootClass sc : Data.getInstance().getSootClassMap().keySet()) {
			int count = countDepth(sc);
			if (count > depth) {
				depth = count;
			}
		}
		for (int i = 0; i <= depth; i++) {
			for (SootClass sc : Data.getInstance().getSootClassMap().keySet()) {
				int count = countDepth(sc);
				if (i == count) {
					conflictResolution.resolveConflict(sc, Data.getInstance().getSootClassMap().get(sc));
				}
			}
		}

		// Conflict Handling for fields, parameter types, return types and locals
		Map<SootClass, List<String>> removeMap = new HashMap<>();
		for (SootClass sc : Data.getInstance().getSootClassMap().keySet()) {
			Hash hash = Data.getInstance().getSootClassMap().get(sc);

			// Fields
			for (SootField sf : sc.getFields()) {
				String oldType = sf.getType().toString();
				String newType = Data.getInstance().getClassesChanged().get(new SootObjHashed(oldType, hash));
				if (newType != null) {
					sf.setType(RefType.v(newType));
					Log.log("Field-Type: " + oldType + " of " + sf.getName() + " is changed to: " + newType,
							Log.LOG_LEVEL_DETAILED);
				}
			}

			Map<SootMethod, List<Type>> parameterMap = new HashMap<>();
			Map<SootMethod, Type> returnMap = new HashMap<>();
			for (SootMethod sm : sc.getMethods()) {
				// Parameter Types
				List<Type> typeList = new ArrayList<>();
				boolean changed = false;
				for (Type st : sm.getParameterTypes()) {
					String oldType = st.toString();
					String newType = Data.getInstance().getClassesChanged().get(new SootObjHashed(oldType, hash));
					Type type;
					if (newType != null) {
						changed = true;
						type = RefType.v(newType);
						Log.log("Function-Parameter-Type: " + oldType + " of function " + sm.getName()
								+ " is changed to: " + newType, Log.LOG_LEVEL_DETAILED);
					} else {
						type = RefType.v(st.toString());
					}
					type.setNumber(0);
					typeList.add(type);
				}
				if (changed) {
					parameterMap.put(sm, typeList);
				}

				// Return Types
				String oldReturnType = sm.getReturnType().toString();
				String newReturnType = Data.getInstance().getClassesChanged()
						.get(new SootObjHashed(oldReturnType, hash));
				if (newReturnType != null) {
					returnMap.put(sm, RefType.v(newReturnType));
					Log.log("Function-Return-Type: " + oldReturnType + " of function " + sm.getName()
							+ " is changed to: " + newReturnType, Log.LOG_LEVEL_DETAILED);
				}

				// Locals
				if (sm.isConcrete()) {
					Body body = sm.retrieveActiveBody();
					for (Local sl : body.getLocals()) {
						String oldType = sl.getType().toString();
						String newType = Data.getInstance().getClassesChanged().get(new SootObjHashed(oldType, hash));
						if (newType != null) {
							sl.setType(RefType.v(newType));
							Log.log("Local-Type: " + oldType + " of " + sl.getName() + " is changed to: " + newType,
									Log.LOG_LEVEL_DETAILED);
						}
					}
				}
			}
			for (SootMethod sm : parameterMap.keySet()) {
				fixMethodNumberer(sm);
				String oldSignature = new String(sm.getSubSignature());
				sm.setParameterTypes(parameterMap.get(sm));
				if (!removeMap.containsKey(sc)) {
					removeMap.put(sc, new ArrayList<>());
				}
				removeMap.get(sc).add(oldSignature);
			}
			for (SootMethod sm : returnMap.keySet()) {
				fixMethodNumberer(sm);
				sm.setReturnType(returnMap.get(sm));
			}
		}

		// Conflict Handling for Statements
		boolean changed = true;
		while (changed) {
			changed = false;
			for (SootClass sc : Data.getInstance().getSootClassMap().keySet()) {
				Hash hash = Data.getInstance().getSootClassMap().get(sc);
				for (int i = 0; i < sc.getMethods().size(); i++) {
					SootMethod sm = sc.getMethods().get(i);
					if (sm.isConcrete()) {
						Body body = sm.retrieveActiveBody();
						StatementAdapter statementAdapter = new StatementAdapter(
								Data.getInstance().getSootClassMap().keySet(), hash, sc, body);
						List<Unit> removeList = new ArrayList<>();
						Iterator<Unit> iterator = body.getUnits().snapshotIterator();
						while (iterator.hasNext()) {
							Unit su = null;
							try {
								su = iterator.next();
								if (!removeMap.containsKey(sc)) {
									removeMap.put(sc, new ArrayList<>());
								}
								if (statementAdapter.adaptStatement(su, removeList, removeMap.get(sc))) {
									changed = true;
								}
							} catch (Exception e) {
								// do nothing
							}
						}
						body.getUnits().removeAll(removeList);
					}
				}
			}
		}

		// Remove deprecated constructors
		Map<SootClass, List<SootMethod>> removeMap2 = new HashMap<>();
		for (SootClass sc : removeMap.keySet()) {
			for (SootMethod sm : sc.getMethods()) {
				for (String signature : removeMap.get(sc)) {
					if (sm.getSubSignature().equals(signature)) {
						if (!removeMap2.containsKey(sc)) {
							removeMap2.put(sc, new ArrayList<>());
						}
						removeMap2.get(sc).add(sm);
					}
				}
			}
		}
		for (SootClass sc : removeMap2.keySet()) {
			for (SootMethod sm : removeMap2.get(sc)) {
				if (sm.isDeclared()) {
					sc.removeMethod(sm);
				}
			}
		}

		// Handle other classes
		for (SootClass sc : Data.getInstance().getSootClassMap().keySet()) {
			if (!sc.equals(launcherActivityClass) && hasActivitySuperClass(sc) && !sc.isInterface()) {
				for (SootMethod m : sc.getMethods()) {
					if (ACTIVITY_LIFECYCLE_METHODS.contains(m.getName())) {
						if (!lcMethodsToCreate.contains(m.getName())) {
							lcMethodsToCreate.add(m.getName());
						}
						Log.log("Applying modifier on: " + m.getName(), Log.LOG_LEVEL_DETAILED);
						m.setModifiers(Modifier.PUBLIC);
						lifecycleMethodsSet.add(m);
					} else if (m.getName().equals(SootMethod.constructorName)) {
						initMethodMap.put(sc.toString(), m);
					}
				}
			}
		}

		// Instantiate field objects
		for (SootClass sc : Data.getInstance().getSootClassMap().keySet()) {
			if (!sc.equals(launcherActivityClass) && hasActivitySuperClass(sc) && !sc.isInterface()) {
				String name = sc.getName().replace(".", "_");

				// Create and initialize fields
				SootField tmpField = new SootField(name, sc.getType(), Modifier.PRIVATE);
				launcherActivityClass.addField(tmpField);

				// Add local representation
				Body bodyInit = launcherActivityInitMethod.retrieveActiveBody();
				Local tmpLocalField = Jimple.v().newLocal(name + "_local", sc.getType());
				bodyInit.getLocals().add(tmpLocalField);

				// Call "new" statement
				AssignStmt u1 = new JAssignStmt(tmpLocalField, new JNewExpr(sc.getType()));

				// Call Constructor
				SootMethod init = initMethodMap.get(sc.toString());
				InvokeStmt u2 = new JInvokeStmt(
						Jimple.v().newSpecialInvokeExpr(tmpLocalField, new SootMethodRefImpl(init.getDeclaringClass(),
								init.getName(), init.getParameterTypes(), init.getReturnType(), false)));

				// Connect local to field
				FieldRef fieldRef = Jimple.v().newInstanceFieldRef(bodyInit.getThisLocal(), tmpField.makeRef());
				AssignStmt u3 = Jimple.v().newAssignStmt(fieldRef, tmpLocalField);

				// Add statements before return
				List<Unit> unitsToAdd = new ArrayList<>();
				unitsToAdd.add(u1);
				unitsToAdd.add(u2);
				unitsToAdd.add(u3);
				bodyInit.getUnits().insertBefore(unitsToAdd, findReturnStmt(bodyInit));
			}
		}

		// Instrument dummy functions
		for (SootClass sc : appClasses) {
			if (sc.toString().equals(SootObject.getInstance().getLauncherActivity())) {
				for (String dummyMethodName : lcMethodsToCreate) {
					// Create new life-cycle method
					Log.log("Create lifecycle method for: " + dummyMethodName, Log.LOG_LEVEL_DETAILED);
					SootMethod dummyMethod = null;
					if (dummyMethodName.contains("onCreate") && hasActivitySuperClass(sc)) {
						List<Type> parameterTypes = new ArrayList<>();
						parameterTypes.add(Scene.v().getSootClass("android.os.Bundle").getType());
						dummyMethod = new SootMethod(dummyMethodName, parameterTypes, VoidType.v(), Modifier.PUBLIC);
					} else {
						dummyMethod = new SootMethod(dummyMethodName, new ArrayList<Type>(), VoidType.v(),
								Modifier.PUBLIC);
					}
					sc.addMethod(dummyMethod);

					// Create body
					dummyMethodBody = Jimple.v().newBody(dummyMethod);
					dummyMethod.setActiveBody(dummyMethodBody);

					// This local
					Local thisLocal = getThisLocal();

					// Random local
					Local randomValueLocal = getRandom();

					// Create switch case
					createSwitchCase(dummyMethodName, dummyMethod, thisLocal, randomValueLocal);
				}
			}
		}

		new File(Config.getInstance().getSootOutputPath()).mkdirs();
		for (SootClass sc : Scene.v().getApplicationClasses()) {
			SootObject.print(sc, new File(Config.getInstance().getSootOutputPath(), sc.getName() + ".jimple"));
		}
	}

	private Local getThisLocal() {
		Local thisLocal = Jimple.v().newLocal("this", launcherActivityClass.getType());
		dummyMethodBody.getLocals().addFirst(thisLocal);
		IdentityStmt thisStmt = Jimple.v().newIdentityStmt(thisLocal,
				Jimple.v().newThisRef(launcherActivityClass.getType()));
		dummyMethodBody.getUnits().add(thisStmt);

		return thisLocal;
	}

	private Local getRandom() {
		Local randomGeneratorLocal = Jimple.v().newLocal("randomGenerator",
				Scene.v().getSootClass("java.util.Random").getType());
		dummyMethodBody.getLocals().add(randomGeneratorLocal);
		AssignStmt randomNewAssign = new JAssignStmt(randomGeneratorLocal,
				new JNewExpr(Scene.v().getSootClass("java.util.Random").getType()));
		InvokeStmt randomGeneratorConstructorCall = Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(
				randomGeneratorLocal, Scene.v().getSootClass("java.util.Random").getMethods().get(0).makeRef()));
		Local randomValueLocal = Jimple.v().newLocal("randomValue", RefType.v("java.lang.Integer"));
		dummyMethodBody.getLocals().add(randomValueLocal);
		SootMethod nextInt = null;
		for (SootMethod m : Scene.v().getSootClass("java.util.Random").getMethods()) {
			if (m.getSignature().toString().contains("java.util.Random: int next(int)")) {
				nextInt = m;
				break;
			}
		}
		InvokeExpr randomGeneratorNextIntCall = Jimple.v().newVirtualInvokeExpr(randomGeneratorLocal, nextInt.makeRef(),
				IntConstant.v(Config.getInstance().getAppPathList().size() + 1));
		AssignStmt randomValueAssign = Jimple.v().newAssignStmt(randomValueLocal, randomGeneratorNextIntCall);

		dummyMethodBody.getUnits().add(randomNewAssign);
		dummyMethodBody.getUnits().add(randomGeneratorConstructorCall);
		dummyMethodBody.getUnits().add(randomValueAssign);

		return randomValueLocal;
	}

	private void createSwitchCase(String dummyMethodName, SootMethod dummyMethod, Local thisLocal,
			Local randomValueLocal) {
		InvokeStmt defaultInvokeStmt = null;
		List<InvokeStmt> switchStmtList = new ArrayList<>();

		// Bundle for onCreate
		Local bundle = null;
		if (dummyMethodName.equals("onCreate")) {
			bundle = Jimple.v().newLocal("bundle", Scene.v().getSootClass("android.os.Bundle").getType());
			if (!dummyMethodBody.getLocals().contains(bundle)) {
				dummyMethodBody.getLocals().addLast(bundle);
				JIdentityStmt parameterIdentityStmt = (JIdentityStmt) Jimple.v().newIdentityStmt(bundle,
						new ParameterRef(dummyMethod.getParameterType(0), 0));
				dummyMethodBody.getUnits().insertBefore(parameterIdentityStmt, dummyMethodBody.getUnits().getFirst());
			}
		}

		for (SootMethod lifecycleMethod : lifecycleMethodsSet) {
			if (lifecycleMethod.toString().contains(dummyMethodName)) {
				// Get local-instance-field or this-variable
				String localName = lifecycleMethod.getDeclaringClass().toString().replace(".", "_") + "_local";
				Local tmpLocal;
				if (lifecycleMethod.getDeclaringClass().equals(launcherActivityClass)) {
					tmpLocal = thisLocal;
				} else {
					tmpLocal = Jimple.v().newLocal(localName, lifecycleMethod.getDeclaringClass().getType());
					dummyMethodBody.getLocals().addFirst(tmpLocal);

					String name = lifecycleMethod.getDeclaringClass().getName().replace(".", "_");
					FieldRef fieldRef = Jimple.v().newInstanceFieldRef(thisLocal,
							launcherActivityClass.getFieldByName(name).makeRef());
					AssignStmt assignStmt = Jimple.v().newAssignStmt(tmpLocal, fieldRef);
					dummyMethodBody.getUnits().add(assignStmt);
				}

				// Create life-cycle method call
				InvokeStmt invokeStmt;
				if (dummyMethodName.equals("onCreate")) {
					// Add bundle for onCreate
					invokeStmt = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(tmpLocal,
							new SootMethodRefImpl(lifecycleMethod.getDeclaringClass(), lifecycleMethod.getName(),
									lifecycleMethod.getParameterTypes(), lifecycleMethod.getReturnType(), false),
							bundle));
				} else {
					// No bundle needed for other life-cycle methods
					invokeStmt = Jimple.v()
							.newInvokeStmt(Jimple.v().newVirtualInvokeExpr(tmpLocal,
									new SootMethodRefImpl(lifecycleMethod.getDeclaringClass(),
											lifecycleMethod.getName(), lifecycleMethod.getParameterTypes(),
											lifecycleMethod.getReturnType(), false)));
				}
				if (lifecycleMethod.getDeclaringClass().equals(launcherActivityClass)) {
					defaultInvokeStmt = invokeStmt;
				}
				switchStmtList.add(invokeStmt);
			}
		}

		if (switchStmtList.size() > 1) {
			if (defaultInvokeStmt == null) {
				defaultInvokeStmt = switchStmtList.get(switchStmtList.size() - 1);
			}
			TableSwitchStmt tableSwitch = Jimple.v().newTableSwitchStmt(randomValueLocal, 1, switchStmtList.size(),
					switchStmtList, defaultInvokeStmt);
			dummyMethodBody.getUnits().add(tableSwitch);
			for (Unit stmt : switchStmtList) {
				dummyMethodBody.getUnits().add(stmt);
				dummyMethodBody.getUnits().add(Jimple.v().newReturnVoidStmt());
			}
		} else {
			dummyMethodBody.getUnits().addAll(switchStmtList);
		}
		dummyMethodBody.getUnits().add(Jimple.v().newReturnVoidStmt());
	}

	private ReturnVoidStmt findReturnStmt(Body bodyInit) {
		for (Unit u : bodyInit.getUnits()) {
			if (u instanceof ReturnVoidStmt) {
				return (ReturnVoidStmt) u;
			}
		}
		return null;
	}

	private boolean hasActivitySuperClass(SootClass sc) {
		if (sc.hasSuperclass()) {
			sc = sc.getSuperclass();
			if (sc.getName().equals("android.app.Activity")) {
				return true;
			} else {
				return hasActivitySuperClass(sc);
			}
		} else {
			return false;
		}
	}

	private int countDepth(SootClass sc) {
		String classname = sc.getName();
		int depth = classname.length() - classname.replace("$", "").length();
		return depth;
	}

	private void fixMethodNumberer(SootMethod sm) {
		if (Scene.v().getMethodNumberer().size() < sm.getNumber()) {
			try {
				Method m = Scene.v().getMethodNumberer().getClass().getDeclaredMethod("resize",
						new Class[] { int.class });
				m.setAccessible(true);
				m.invoke(Scene.v().getMethodNumberer(), sm.getNumber() * 2);
			} catch (Exception e) {
				Log.log("Could not increase the size of Soot's method numberer. (" + e.getClass().getSimpleName() + ": "
						+ e.getMessage() + ")", Log.LOG_LEVEL_ERROR);
			}
		}
	}
}