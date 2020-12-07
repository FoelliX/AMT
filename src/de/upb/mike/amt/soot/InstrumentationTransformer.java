package de.upb.mike.amt.soot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.upb.mike.amt.AMT;
import de.upb.mike.amt.Config;
import de.upb.mike.amt.Data;
import de.upb.mike.amt.Log;
import de.upb.mike.amt.conflicresolution.ConflictResolution;
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
		final Chain<SootClass> appClasses = Scene.v().getApplicationClasses();

		// Handle launcher classes
		for (final SootClass sc : appClasses) {
			if (sc.toString().contains(SootObject.getInstance().getLauncherActivity()) && hasActivitySuperClass(sc)
					&& !sc.isInterface()) {
				this.launcherActivityClass = sc;
				for (int i = 0; i < sc.getMethods().size(); i++) {
					final SootMethod m = sc.getMethods().get(i);
					if (ACTIVITY_LIFECYCLE_METHODS.contains(m.getName()) && !m.getName().contains("Original")) {
						if (!this.lcMethodsToCreate.contains(m.getName())) {
							this.lcMethodsToCreate.add(m.getName());
						}
						this.lifecycleMethodsSet.add(m);
						i = 0;

						// Rename method
						final String originalSignature = m.getSignature();
						m.setName("Original" + m.getName());
						Data.getInstance().getLifecycleMethodSignaturesChanged().put(originalSignature,
								m.getSignature());
						Log.log("Method " + originalSignature + " modified to " + m.getSignature(),
								Log.LOG_LEVEL_DETAILED);
					} else if (m.getName().equals(SootMethod.constructorName)) {
						initMethodMap.put(sc.toString(), m);
						this.launcherActivityInitMethod = m;
					}
				}
				break;
			}
		}
		if (this.launcherActivityClass == null) {
			Log.log("Could not find launcher Activity!", Log.LOG_LEVEL_ERROR);
		}

		// Conflict Handling for classes (NEW)
		final ConflictResolution conflictResolution = new ConflictResolution(appClasses);
		for (final SootClass sc : Data.getInstance().getSootClassMap().keySet()) {
			if (!sc.hasOuterClass()) {
				final Set<SootClass> subClasses = new HashSet<>();
				for (final SootClass scInner : Data.getInstance().getSootClassMap().keySet()) {
					if (scInner.hasOuterClass() && getOuterClasses(scInner).contains(sc)) {
						subClasses.add(scInner);
					}
				}
				conflictResolution.resolveConflictOuter(sc, subClasses);
			}
		}

		// Handle other classes
		for (final SootClass sc : Data.getInstance().getSootClassMap().keySet()) {
			if (!sc.equals(this.launcherActivityClass) && hasActivitySuperClass(sc) && !sc.isInterface()) {
				for (final SootMethod m : sc.getMethods()) {
					if (ACTIVITY_LIFECYCLE_METHODS.contains(m.getName())) {
						if (!this.lcMethodsToCreate.contains(m.getName())) {
							this.lcMethodsToCreate.add(m.getName());
						}
						Log.log("Applying modifier on: " + m.getName(), Log.LOG_LEVEL_DETAILED);
						m.setModifiers(Modifier.PUBLIC);
						this.lifecycleMethodsSet.add(m);
					} else if (m.getName().equals(SootMethod.constructorName)) {
						initMethodMap.put(sc.toString(), m);
					}
				}
			}
		}

		// Instantiate field objects
		for (final SootClass sc : Data.getInstance().getSootClassMap().keySet()) {
			if (!sc.equals(this.launcherActivityClass) && hasActivitySuperClass(sc) && !sc.isInterface()) {
				final String name = sc.getName().replace(".", "_");

				// Create and initialize fields
				final SootField tmpField = new SootField(name, sc.getType(), Modifier.PRIVATE);
				this.launcherActivityClass.addField(tmpField);

				// Add local representation
				final Body bodyInit = this.launcherActivityInitMethod.retrieveActiveBody();
				final Local tmpLocalField = Jimple.v().newLocal(name + "_local", sc.getType());
				bodyInit.getLocals().add(tmpLocalField);

				// Call "new" statement
				final AssignStmt u1 = new JAssignStmt(tmpLocalField, new JNewExpr(sc.getType()));

				// Call Constructor
				final SootMethod init = initMethodMap.get(sc.toString());
				final InvokeStmt u2 = new JInvokeStmt(
						Jimple.v().newSpecialInvokeExpr(tmpLocalField, new SootMethodRefImpl(init.getDeclaringClass(),
								init.getName(), init.getParameterTypes(), init.getReturnType(), false)));

				// Connect local to field
				final FieldRef fieldRef = Jimple.v().newInstanceFieldRef(bodyInit.getThisLocal(), tmpField.makeRef());
				final AssignStmt u3 = Jimple.v().newAssignStmt(fieldRef, tmpLocalField);

				// Add statements before return
				final List<Unit> unitsToAdd = new ArrayList<>();
				unitsToAdd.add(u1);
				unitsToAdd.add(u2);
				unitsToAdd.add(u3);
				bodyInit.getUnits().insertBefore(unitsToAdd, findReturnStmt(bodyInit));
			}
		}

		// Instrument dummy functions
		for (final SootClass sc : appClasses) {
			if (sc.toString().equals(SootObject.getInstance().getLauncherActivity())) {
				for (final String dummyMethodName : this.lcMethodsToCreate) {
					// Create new life-cycle method
					Log.log("Create lifecycle method for: " + dummyMethodName, Log.LOG_LEVEL_DETAILED);
					SootMethod dummyMethod = null;
					if (dummyMethodName.contains("onCreate") && hasActivitySuperClass(sc)) {
						final List<Type> parameterTypes = new ArrayList<>();
						parameterTypes.add(Scene.v().getSootClass("android.os.Bundle").getType());
						dummyMethod = new SootMethod(dummyMethodName, parameterTypes, VoidType.v(), Modifier.PUBLIC);
					} else {
						dummyMethod = new SootMethod(dummyMethodName, new ArrayList<Type>(), VoidType.v(),
								Modifier.PUBLIC);
					}
					sc.addMethod(dummyMethod);

					// Create body
					this.dummyMethodBody = Jimple.v().newBody(dummyMethod);
					dummyMethod.setActiveBody(this.dummyMethodBody);

					// This local
					final Local thisLocal = getThisLocal();

					// Random local
					final Local randomValueLocal = getRandom();

					// Create switch case
					createSwitchCase(dummyMethodName, dummyMethod, thisLocal, randomValueLocal);
				}
			}
		}

		Log.log("successful!\n\n", Log.LOG_LEVEL_NORMAL);
		Log.log("*** Step 4/" + AMT.steps + ": Building Merged App ***", Log.LOG_LEVEL_NORMAL);
	}

	private Set<SootClass> getOuterClasses(SootClass scInner) {
		final Set<SootClass> outerClasses = new HashSet<>();
		if (scInner.hasOuterClass()) {
			outerClasses.add(scInner.getOuterClass());
			outerClasses.addAll(getOuterClasses(scInner.getOuterClass()));
		}
		return outerClasses;
	}

	private Local getThisLocal() {
		final Local thisLocal = Jimple.v().newLocal("this", this.launcherActivityClass.getType());
		this.dummyMethodBody.getLocals().addFirst(thisLocal);
		final IdentityStmt thisStmt = Jimple.v().newIdentityStmt(thisLocal,
				Jimple.v().newThisRef(this.launcherActivityClass.getType()));
		this.dummyMethodBody.getUnits().add(thisStmt);

		return thisLocal;
	}

	private Local getRandom() {
		final Local randomGeneratorLocal = Jimple.v().newLocal("randomGenerator",
				Scene.v().getSootClass("java.util.Random").getType());
		this.dummyMethodBody.getLocals().add(randomGeneratorLocal);
		final AssignStmt randomNewAssign = new JAssignStmt(randomGeneratorLocal,
				new JNewExpr(Scene.v().getSootClass("java.util.Random").getType()));
		final InvokeStmt randomGeneratorConstructorCall = Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(
				randomGeneratorLocal, Scene.v().getSootClass("java.util.Random").getMethods().get(0).makeRef()));
		final Local randomValueLocal = Jimple.v().newLocal("randomValue", RefType.v("java.lang.Integer"));
		this.dummyMethodBody.getLocals().add(randomValueLocal);
		SootMethod nextInt = null;
		for (final SootMethod m : Scene.v().getSootClass("java.util.Random").getMethods()) {
			if (m.getSignature().toString().contains("java.util.Random: int next(int)")) {
				nextInt = m;
				break;
			}
		}
		final InvokeExpr randomGeneratorNextIntCall = Jimple.v().newVirtualInvokeExpr(randomGeneratorLocal,
				nextInt.makeRef(), IntConstant.v(Config.getInstance().getAppPathList().size() + 1));
		final AssignStmt randomValueAssign = Jimple.v().newAssignStmt(randomValueLocal, randomGeneratorNextIntCall);

		this.dummyMethodBody.getUnits().add(randomNewAssign);
		this.dummyMethodBody.getUnits().add(randomGeneratorConstructorCall);
		this.dummyMethodBody.getUnits().add(randomValueAssign);

		return randomValueLocal;
	}

	private void createSwitchCase(String dummyMethodName, SootMethod dummyMethod, Local thisLocal,
			Local randomValueLocal) {
		InvokeStmt defaultInvokeStmt = null;
		final List<InvokeStmt> switchStmtList = new ArrayList<>();

		// Bundle for onCreate
		Local bundle = null;
		if (dummyMethodName.equals("onCreate")) {
			bundle = Jimple.v().newLocal("bundle", Scene.v().getSootClass("android.os.Bundle").getType());
			if (!this.dummyMethodBody.getLocals().contains(bundle)) {
				this.dummyMethodBody.getLocals().addLast(bundle);
				final JIdentityStmt parameterIdentityStmt = (JIdentityStmt) Jimple.v().newIdentityStmt(bundle,
						new ParameterRef(dummyMethod.getParameterType(0), 0));
				this.dummyMethodBody.getUnits().insertBefore(parameterIdentityStmt,
						this.dummyMethodBody.getUnits().getFirst());
			}
		}

		for (final SootMethod lifecycleMethod : this.lifecycleMethodsSet) {
			if (lifecycleMethod.toString().contains(dummyMethodName)) {
				// Get local-instance-field or this-variable
				final String localName = lifecycleMethod.getDeclaringClass().toString().replace(".", "_") + "_local";
				Local tmpLocal;
				if (lifecycleMethod.getDeclaringClass().equals(this.launcherActivityClass)) {
					tmpLocal = thisLocal;
				} else {
					tmpLocal = Jimple.v().newLocal(localName, lifecycleMethod.getDeclaringClass().getType());
					this.dummyMethodBody.getLocals().addFirst(tmpLocal);

					final String name = lifecycleMethod.getDeclaringClass().getName().replace(".", "_");
					final FieldRef fieldRef = Jimple.v().newInstanceFieldRef(thisLocal,
							this.launcherActivityClass.getFieldByName(name).makeRef());
					final AssignStmt assignStmt = Jimple.v().newAssignStmt(tmpLocal, fieldRef);
					this.dummyMethodBody.getUnits().add(assignStmt);
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
				if (lifecycleMethod.getDeclaringClass().equals(this.launcherActivityClass)) {
					defaultInvokeStmt = invokeStmt;
				}
				switchStmtList.add(invokeStmt);
			}
		}

		if (switchStmtList.size() > 1) {
			if (defaultInvokeStmt == null) {
				defaultInvokeStmt = switchStmtList.get(switchStmtList.size() - 1);
			}
			final TableSwitchStmt tableSwitch = Jimple.v().newTableSwitchStmt(randomValueLocal, 1,
					switchStmtList.size(), switchStmtList, defaultInvokeStmt);
			this.dummyMethodBody.getUnits().add(tableSwitch);
			for (final Unit stmt : switchStmtList) {
				this.dummyMethodBody.getUnits().add(stmt);
				this.dummyMethodBody.getUnits().add(Jimple.v().newReturnVoidStmt());
			}
		} else {
			this.dummyMethodBody.getUnits().addAll(switchStmtList);
		}
		this.dummyMethodBody.getUnits().add(Jimple.v().newReturnVoidStmt());
	}

	private ReturnVoidStmt findReturnStmt(Body bodyInit) {
		for (final Unit u : bodyInit.getUnits()) {
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
}