package de.upb.mike.amt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

public class MergeTwoAppsTest {
	@Test
	public void mergeTwoAppsTest() {
		File apk1 = new File("test/ArrayAccess1.apk");
		File apk2 = new File("test/ArrayAccess2.apk");

		boolean noException = true;
		try {
			AMT.main(new String[] { apk1.getAbsolutePath(), apk2.getAbsolutePath() });
		} catch (Exception e) {
			noException = false;
		}

		assertTrue(noException);
	}
}
