package de.upb.mike.amt;

import de.foellix.aql.datastructure.Hash;
import de.foellix.aql.helper.EqualsHelper;

public class SootObjHashed {
	private String classname;
	private Hash hash;

	public SootObjHashed(String sootObjectName, Hash hash) {
		super();
		this.classname = sootObjectName;
		this.hash = hash;
	}

	public String getClassname() {
		return classname;
	}

	public void setClassname(String classname) {
		this.classname = classname;
	}

	public Hash getHash() {
		return hash;
	}

	public void setHash(Hash hash) {
		this.hash = hash;
	}

	@Override
	public int hashCode() {
		String toHash = classname + hash.getValue();
		return toHash.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		SootObjHashed other = (SootObjHashed) obj;
		if (classname == null) {
			if (other.classname != null) {
				return false;
			}
		} else if (!classname.equals(other.classname)) {
			return false;
		}
		if (hash == null) {
			if (other.hash != null) {
				return false;
			}
		} else if (!EqualsHelper.equals(hash, other.hash)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return classname + ", " + hash.getValue() + " (" + hash.getType() + ")";
	}
}