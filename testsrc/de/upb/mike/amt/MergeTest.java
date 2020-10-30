package de.upb.mike.amt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

public class MergeTest {
	@Test
	public void test01() {
		final File apk1 = new File("test/ArrayAccess1.apk");
		final File apk2 = new File("test/ArrayAccess2.apk");

		boolean noException = true;
		try {
			AMT.main(new String[] { apk1.getAbsolutePath(), apk2.getAbsolutePath() });
		} catch (final Exception e) {
			noException = false;
		}

		assertTrue(noException);
	}

	// @Test
	public void test02() {
		final File apk1 = new File("test/ArrayAccess1.apk");
		final File apk2 = new File("test/ArrayAccess2.apk");

		boolean noException = true;
		try {
			AMT.main(new String[] { "-l", "verbose", apk1.getAbsolutePath(), apk2.getAbsolutePath() });
		} catch (final Exception e) {
			noException = false;
		}

		assertTrue(noException);
	}
}
